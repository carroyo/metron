/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.metron.parsers.syslog;

import com.google.common.collect.ImmutableMap;
import oi.thekraken.grok.api.Grok;
import oi.thekraken.grok.api.Match;
import oi.thekraken.grok.api.exception.GrokException;
import org.apache.commons.collections.MultiHashMap;
import org.apache.commons.collections.map.MultiValueMap;

import org.apache.metron.common.Constants;
import org.apache.metron.parsers.BasicParser;
import org.apache.metron.parsers.ParseException;
import org.apache.metron.parsers.utils.SyslogUtils;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.collections.MultiMap;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class BasicSyslogParser extends BasicParser {

  protected static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected Clock deviceClock;
  private String syslogPattern = "%{GENERAL_SYSLOG}";

  private Grok syslogGrok;

  private static final String[] suPatterns = {"SU1", "SU2", "SU3"};
  private static final String[] sudoPatterns = {"SUDO1", "SUDO2", "SUDO3"};
  private static final String[] sshPatterns = {"SSH1", "SSH2", "SSH3"};

  private static final Map<String, String[]> patternMap = ImmutableMap.<String, String[]> builder()
      .put("su", suPatterns)
      .put("sudo", sudoPatterns)
      .put("sshd", sshPatterns)
          .build();

  private MultiMap grokers = new MultiValueMap();

  @Override
  public void configure(Map<String, Object> parserConfig) {
    String timeZone = (String) parserConfig.get("deviceTimeZone");
    if (timeZone != null)
      deviceClock = Clock.system(ZoneId.of(timeZone));
    else {
      deviceClock = Clock.systemUTC();
      LOG.warn("[Metron] No device time zone provided; defaulting to UTC");
    }
  }

  private void addGrok(String key, String pattern) throws GrokException {
    Grok grok = new Grok();
    InputStream patternStream = this.getClass().getResourceAsStream("/patterns/syslog");
    grok.addPatternFromReader(new InputStreamReader(patternStream));
    grok.compile("%{" + pattern + "}");
    grokers.put(key,grok);
  }

  @Override
  public void init() {
    syslogGrok = new Grok();
    InputStream syslogStream = this.getClass().getResourceAsStream("/patterns/syslog");
    try {
      syslogGrok.addPatternFromReader(new InputStreamReader(syslogStream));
      syslogGrok.compile(syslogPattern);
    } catch (GrokException e) {
      LOG.error("[Metron] Failed to load grok patterns from jar", e);
      throw new RuntimeException(e.getMessage(), e);
    }

    for (Entry<String, String[]> patternList : patternMap.entrySet()) {
      try {
          for (String pattern: patternList.getValue()){
        addGrok(patternList.getKey(), pattern);}
      } catch (GrokException e) {
        LOG.error("[Metron] Failed to load grok pattern {} for Syslog  {}", patternList.getValue(), patternList.getKey());
      }
    }

    LOG.info("[Metron] SYSLOG Parser Initialized");
  }

  @Override
  public List<JSONObject> parse(byte[] rawMessage) {
    String logLine = "";
    String messagePattern = "";
    JSONObject metronJson = new JSONObject();
    List<JSONObject> messages = new ArrayList<>();
    Map<String, Object> syslogJson = new HashMap<String, Object>();

    try {
      logLine = new String(rawMessage, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      LOG.error("[Metron] Could not read raw message", e);
      throw new RuntimeException(e.getMessage(), e);
    }

    try {
      LOG.debug("[Metron] Started parsing raw message: {}", logLine);
      Match syslogMatch = syslogGrok.match(logLine);
      syslogMatch.captures();
      if (!syslogMatch.isNull()) {
	syslogJson = syslogMatch.toMap();
	LOG.trace("[Metron] Grok syslog matches: {}", syslogMatch.toJson());

	metronJson.put(Constants.Fields.ORIGINAL.getName(), logLine);
	metronJson.put(Constants.Fields.TIMESTAMP.getName(),
	    SyslogUtils.parseTimestampToEpochMillis((String) syslogJson.get("syslog_timestamp"), deviceClock));
	metronJson.put("syslog_severity", SyslogUtils.getSeverityFromPriority((int) syslogJson.get("syslog_pri")));
	metronJson.put("syslog_facility", SyslogUtils.getFacilityFromPriority((int) syslogJson.get("syslog_pri")));

	if (syslogJson.get("syslog_hostname") != null) {
	  metronJson.put("syslog_hostname", syslogJson.get("syslog_hostname"));
	}
	if (syslogJson.get("syslog_program") != null) {
	  metronJson.put("syslog_program", syslogJson.get("syslog_program"));
	}

      } else
	throw new RuntimeException(
	    String.format("[Metron] Message '%s' does not match pattern '%s'", logLine, syslogPattern));
    } catch (ParseException e) {
      LOG.error("[Metron] Could not parse message timestamp", e);
      throw new RuntimeException(e.getMessage(), e);
    } catch (RuntimeException e) {
      LOG.error(e.getMessage(), e);
      throw new RuntimeException(e.getMessage(), e);
    }

    try {
      messagePattern = (String) syslogJson.get("syslog_program");
      ArrayList<Grok> programGrok = (ArrayList<Grok>) grokers.get(messagePattern);

      if (programGrok == null)
	LOG.info("[Metron] No pattern for syslog '{}'", syslogJson.get("syslog_program"));
      else {

	String messageContent = (String) syslogJson.get("syslog_message");
	Match messageMatch=null;
	for (Grok grokP : programGrok) {
        messageMatch = grokP.match(messageContent);
        messageMatch.captures();
        if (!messageMatch.isNull()) break;
    }
	if (!messageMatch.isNull()) {
	  Map<String, Object> messageJson = messageMatch.toMap();
	  LOG.trace("[Metron] Grok Syslog message matches: {}", messageMatch.toJson());

	  String src_ip = (String) messageJson.get("src_ip");
	  if (src_ip != null)
	    metronJson.put(Constants.Fields.SRC_ADDR.getName(), src_ip);

	  Integer src_port = (Integer) messageJson.get("port");
	  if (src_port != null)
	    metronJson.put(Constants.Fields.SRC_PORT.getName(), src_port);


	  String protocol = (String) messageJson.get("protocol_ssh");
	  if (protocol != null)
	    metronJson.put(Constants.Fields.PROTOCOL.getName(), protocol.toLowerCase());

	  String user_su = (String) messageJson.get("user_su");
	  if (user_su != null)
	    metronJson.put("user_su", user_su.toLowerCase());

	  String action = (String) messageJson.get("action");
	  if (user_su != null)
          metronJson.put("action", action.toLowerCase());
	} else
	  LOG.warn("[Metron] Message '{}' did not match pattern for syslog_program '{}'", logLine,
	      syslogJson.get("syslog_program"));
      }

      LOG.debug("[Metron] Final normalized message: {}", metronJson.toString());

    } catch (RuntimeException e) {
      LOG.error(e.getMessage(), e);
      throw new RuntimeException(e.getMessage(), e);
    }

    messages.add(metronJson);
    return messages;
  }
}
