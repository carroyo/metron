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
        JSONObject testSyslogJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(testSyslogJson.get("original_string"), rawMessage);
        assertTrue(testSyslogJson.get("user_su").equals("root"));
        assertTrue(testSyslogJson.get("action").equals("session closed"));
        assertTrue((long) testSyslogJson.get("timestamp") == 1511383163000L);
    }

    @Test
    public void testSSH1() {
        String rawMessage = "<162>Nov 22 20:39:23 metron sshd[7727]: Accepted publickey for centos from 172.24.4.1 port 43326 ssh2: RSA SHA256:iRj5Z9wt713JtJZdiMBtdvqCYUEQBZfiyDJECyw16aM";
        JSONObject testSyslogJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(testSyslogJson.get("original_string"), rawMessage);
        assertTrue(testSyslogJson.get("ip_src_addr").equals("172.24.4.1"));
        assertTrue(testSyslogJson.get("ip_src_port").equals(43326));
        assertTrue((long) testSyslogJson.get("timestamp") == 1511383163000L);
    }

    @Test
    public void testSSH2() {
        String rawMessage = "<162>Nov 22 20:39:23 sensor01 sshd[6917]: Received disconnect from 172.24.4.1 port 37200:11: disconnected by user";
        JSONObject testSyslogJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(testSyslogJson.get("original_string"), rawMessage);
        assertTrue(testSyslogJson.get("ip_src_addr").equals("172.24.4.1"));
        assertTrue(testSyslogJson.get("ip_src_port").equals(37200));
        assertTrue((long) testSyslogJson.get("timestamp") == 1511383163000L);
    }

    @Test
    public void testSSH3() {
        String rawMessage = "<162>Nov 22 20:39:23 sensor01 sshd[12899]: Disconnected from 172.24.4.1 port 50978";
        JSONObject testSyslogJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(testSyslogJson.get("original_string"), rawMessage);
        assertTrue(testSyslogJson.get("ip_src_addr").equals("172.24.4.1"));
        assertTrue(testSyslogJson.get("ip_src_port").equals(50978));
        assertTrue((long) testSyslogJson.get("timestamp") == 1511383163000L);
    }

    @Test
    public void testSSH4() {
        String rawMessage = "<162>Nov 22 20:39:23 sensor01 sshd[22302]: pam_unix(sshd:session): session closed for user centos";
        JSONObject testSyslogJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(testSyslogJson.get("original_string"), rawMessage);
        assertTrue(testSyslogJson.get("user").equals("centos"));
        assertTrue(testSyslogJson.get("action").equals("closed"));
        assertTrue((long) testSyslogJson.get("timestamp") == 1511383163000L);
    }

    @Test
    public void testSSH5() {
        String rawMessage = "<162>Nov 22 20:39:23 metron sshd[7727]: Invalid user test from 172.24.4.1 port 56654";
        JSONObject testSyslogJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(testSyslogJson.get("original_string"), rawMessage);
        assertTrue(testSyslogJson.get("ip_src_addr").equals("172.24.4.1"));
        assertTrue(testSyslogJson.get("user").equals("test"));
        assertTrue(testSyslogJson.get("ip_src_port").equals(56654));
        assertTrue((long) testSyslogJson.get("timestamp") == 1511383163000L);
    }

    @Test
    public void testSSH6() {
        String rawMessage = "<162>Nov 22 20:39:23 metron sshd[7727]: input_userauth_request: invalid user test [preauth]";
        JSONObject testSyslogJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(testSyslogJson.get("original_string"), rawMessage);
        assertTrue(testSyslogJson.get("ssh_module").equals("input_userauth_request"));
        assertTrue(testSyslogJson.get("user").equals("test"));
        assertTrue((long) testSyslogJson.get("timestamp") == 1511383163000L);
    }

    @Test
    public void testSSH7() {
        String rawMessage = "<162>Nov 22 20:39:23 metron sshd[7727]: pam_unix(sshd:auth): check pass; user unknown";
        JSONObject testSyslogJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(testSyslogJson.get("original_string"), rawMessage);
        assertTrue(testSyslogJson.get("ssh_module").equals("pam_unix"));
        assertTrue(testSyslogJson.get("ssh_submodule").equals("auth"));
        assertTrue(testSyslogJson.get("ssh_daemon").equals("sshd"));
        assertTrue((long) testSyslogJson.get("timestamp") == 1511383163000L);
    }

    @Test
    public void testSSH8() {
        String rawMessage = "<162>Nov 22 20:39:23 metron sshd[7727]: pam_unix(sshd:auth): authentication failure; logname= uid=0 euid=0 tty=ssh ruser= rhost=172.24.4.1";
        JSONObject testSyslogJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(testSyslogJson.get("original_string"), rawMessage);
        assertTrue(testSyslogJson.get("ssh_module").equals("pam_unix"));
        assertTrue(testSyslogJson.get("ssh_submodule").equals("auth"));
        assertTrue(testSyslogJson.get("ssh_daemon").equals("sshd"));
        assertTrue(testSyslogJson.get("action").equals("authentication failure"));
        assertTrue(testSyslogJson.get("ip_src_addr").equals("172.24.4.1"));
        assertTrue((long) testSyslogJson.get("timestamp") == 1511383163000L);
    }

    @Test
    public void testSSH9() {
        String rawMessage = "<162>Nov 22 20:39:23 metron sshd[7727]:  Failed password for invalid user test from 172.24.4.1 port 36354 ssh2";
        JSONObject testSyslogJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(testSyslogJson.get("original_string"), rawMessage);
        assertTrue(testSyslogJson.get("action").equals("failed password"));
        assertTrue(testSyslogJson.get("user").equals("test"));
        assertTrue(testSyslogJson.get("ip_src_port").equals(36354));
        assertTrue(testSyslogJson.get("ip_src_addr").equals("172.24.4.1"));
        assertTrue((long) testSyslogJson.get("timestamp") == 1511383163000L);
    }

    @Test
    public void testSSH10() {
        String rawMessage = "<162>Nov 22 20:39:23 metron sshd[7727]: Failed password for centos from 172.24.4.1 port 42680 ssh2";
        JSONObject testSyslogJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(testSyslogJson.get("original_string"), rawMessage);
        assertTrue(testSyslogJson.get("action").equals("failed password"));
        assertTrue(testSyslogJson.get("user").equals("centos"));
        assertTrue(testSyslogJson.get("ip_src_port").equals(42680));
        assertTrue(testSyslogJson.get("ip_src_addr").equals("172.24.4.1"));
        assertTrue((long) testSyslogJson.get("timestamp") == 1511383163000L);
    }

    @Test
    public void testSSH11() {
        String rawMessage = "<162>Nov 22 20:39:23 metron sshd[7727]:  PAM 2 more authentication failures; logname= uid=0 euid=0 tty=ssh ruser= rhost=172.24.4.1  user=centos";
        JSONObject testSyslogJson = syslogParser.parse(rawMessage.getBytes()).get(0);
        assertEquals(testSyslogJson.get("original_string"), rawMessage);
        assertTrue(testSyslogJson.get("action").equals("authentication failures"));
        assertTrue(testSyslogJson.get("user").equals("centos"));
        assertTrue(testSyslogJson.get("recu").equals(2));
        assertTrue(testSyslogJson.get("ip_src_addr").equals("172.24.4.1"));
        assertTrue((long) testSyslogJson.get("timestamp") == 1511383163000L);
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
