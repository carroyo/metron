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

import org.apache.log4j.Level;
import org.apache.metron.parsers.syslog.BasicSyslogParser;
import org.apache.metron.test.utils.UnitTestHelper;
import org.json.simple.JSONObject;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.*;

public class BasicSyslogParserTest {

    private static BasicSyslogParser syslogParser;

    @BeforeClass
    public static void setUpOnce() throws Exception {
        Map<String, Object> parserConfig = new HashMap<>();
        syslogParser = new BasicSyslogParser();
        syslogParser.configure(parserConfig);
        syslogParser.init();
    }

    @Test
    public void testConfigureDefault() {
        Map<String, Object> parserConfig = new HashMap<>();
        BasicSyslogParser testParser = new BasicSyslogParser();
        testParser.configure(parserConfig);
        testParser.init();
        assertTrue(testParser.deviceClock.getZone().equals(ZoneOffset.UTC));
    }

    @Test
    public void testConfigureTimeZoneOffset() {
        Map<String, Object> parserConfig = new HashMap<>();
        parserConfig.put("deviceTimeZone", "UTC-05:00");
        BasicSyslogParser testParser = new BasicSyslogParser();
        testParser.configure(parserConfig);
        testParser.init();
        ZonedDateTime deviceTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(1475323200), testParser.deviceClock.getZone());
        ZonedDateTime referenceTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(1475323200), ZoneOffset.ofHours(-5));
        assertTrue(deviceTime.isEqual(referenceTime));
    }

    @Test
    public void testConfigureTimeZoneText() {
        Map<String, Object> parserConfig = new HashMap<>();
        parserConfig.put("deviceTimeZone", "America/New_York");
        BasicSyslogParser testParser = new BasicSyslogParser();
        testParser.configure(parserConfig);
        testParser.init();
        ZonedDateTime deviceTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(1475323200), testParser.deviceClock.getZone());
        ZonedDateTime referenceTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(1475323200), ZoneOffset.ofHours(-5));
        assertTrue(deviceTime.isEqual(referenceTime));
    }

    @Test
    public void testSU() {
        String rawMessage = "<164>Nov 22 20:39:23 metron su: pam_unix(su:session): session closed for user root";
        JSONObject asaJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(asaJson.get("original_string"), rawMessage);
        assertTrue(asaJson.get("user_su").equals("root"));
        assertTrue(asaJson.get("action").equals("session closed"));
        assertTrue((long) asaJson.get("timestamp") == 1511383163000L);
    }

    @Test
    public void testSSH() {
        String rawMessage = "<162>Nov 22 20:39:23 metron sshd[7727]: Accepted publickey for centos from 172.24.4.1 port 43326 ssh2: RSA SHA256:iRj5Z9wt713JtJZdiMBtdvqCYUEQBZfiyDJECyw16aM";
        JSONObject asaJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(asaJson.get("original_string"), rawMessage);
        assertTrue(asaJson.get("ip_src_addr").equals("172.24.4.1"));
        assertTrue(asaJson.get("ip_src_port").equals(43326));
        assertTrue((long) asaJson.get("timestamp") == 1511383163000L);
    }


    
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testUnexpectedMessage() {
        String rawMessage = "-- MARK --";
        UnitTestHelper.setLog4jLevel(BasicSyslogParser.class, Level.FATAL);
        thrown.expect(RuntimeException.class);
        thrown.expectMessage(startsWith("[Metron] Message '-- MARK --'"));
        JSONObject asaJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        UnitTestHelper.setLog4jLevel(BasicSyslogParser.class, Level.ERROR);
    }
}
