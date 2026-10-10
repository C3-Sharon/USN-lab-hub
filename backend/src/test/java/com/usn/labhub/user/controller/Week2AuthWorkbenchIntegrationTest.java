package com.usn.labhub.user.controller;

import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.service.IAttendanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:week2-auth-workbench;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class Week2AuthWorkbenchIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private IAttendanceService attendanceService;

    @BeforeEach
    void setUpAttendance() {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayStatus(0);
        attendance.setTodayRecords(java.util.List.of());
        attendance.setWeekHours(0.0);
        attendance.setSemesterHours(0.0);
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loginReturnsFrozenMultiRoleShape() {
        ResponseEntity<Map> response = login("admin", "admin123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        assertEquals("SYSTEM_ADMIN", user.get("primaryRoleKey"));
        assertEquals("SYSTEM_ADMIN", user.get("roleKey"));
        assertTrue(user.get("roles").toString().contains("SYSTEM_ADMIN"));
        assertFalse(response.getBody().containsKey("reason"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void workbenchRequiresBearerAndReturnsSixContractRegions() {
        ResponseEntity<Map> missing = rest.getForEntity("/api/workbench/overview", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, missing.getStatusCode());
        assertEquals("TOKEN_MISSING", missing.getBody().get("reason"));

        String token = token(login("admin", "admin123"));
        ResponseEntity<Map> response = rest.exchange(
                "/api/workbench/overview", HttpMethod.GET, authorized(token), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals(6, data.size());
        assertEquals("READY", ((Map<?, ?>) data.get("attendance")).get("state"));
        assertEquals("READY", ((Map<?, ?>) data.get("projects")).get("state"));
        assertEquals("READY", ((Map<?, ?>) data.get("deviceReminder")).get("state"));
        assertFalse(data.toString().contains("available"));
    }

    @Test
    void legacyHeaderWorksAndConflictingHeadersFail() {
        String token = token(login("admin", "admin123"));
        HttpHeaders legacy = new HttpHeaders();
        legacy.set("token", token);
        ResponseEntity<Map> compatible = rest.exchange(
                "/api/workbench/overview", HttpMethod.GET, new HttpEntity<>(legacy), Map.class);
        assertEquals(HttpStatus.OK, compatible.getStatusCode());

        HttpHeaders matching = new HttpHeaders();
        matching.setBearerAuth(token);
        matching.set("token", token);
        ResponseEntity<Map> matchingHeaders = rest.exchange(
                "/api/workbench/overview", HttpMethod.GET, new HttpEntity<>(matching), Map.class);
        assertEquals(HttpStatus.OK, matchingHeaders.getStatusCode());

        HttpHeaders conflicting = new HttpHeaders();
        conflicting.setBearerAuth(token);
        conflicting.set("token", token + "different");
        ResponseEntity<Map> rejected = rest.exchange(
                "/api/workbench/overview", HttpMethod.GET, new HttpEntity<>(conflicting), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());
        assertEquals("TOKEN_INVALID", rejected.getBody().get("reason"));
    }

    @Test
    void roleChangesAndDisabledStatusTakeEffectOnTheNextRequest() {
        String token = token(login("20260001", "20260001"));

        ResponseEntity<Map> denied = rest.exchange(
                "/admin/member/page?pageNo=1&pageSize=10", HttpMethod.GET, authorized(token), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("ACCESS_DENIED", denied.getBody().get("reason"));

        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2, 3)");
        try {
            ResponseEntity<Map> allowed = rest.exchange(
                    "/admin/member/page?pageNo=1&pageSize=10", HttpMethod.GET, authorized(token), Map.class);
            assertEquals(HttpStatus.OK, allowed.getStatusCode());

            ResponseEntity<Map> memberWriteDenied = rest.exchange(
                    "/admin/member/save", HttpMethod.POST, authorized(token, Map.of()), Map.class);
            assertEquals(HttpStatus.FORBIDDEN, memberWriteDenied.getStatusCode());
            assertEquals("ACCESS_DENIED", memberWriteDenied.getBody().get("reason"));

            ResponseEntity<Map> commandDenied = rest.exchange(
                    "/api/iot/devices/1/commands", HttpMethod.POST,
                    authorized(token, Map.of("command", "SET_SAMPLE_INTERVAL",
                            "params", Map.of("intervalSeconds", 5))), Map.class);
            assertEquals(HttpStatus.FORBIDDEN, commandDenied.getStatusCode());
        } finally {
            jdbc.update("DELETE FROM sys_user_role WHERE user_id=2 AND role_id=3");
        }

        jdbc.update("DELETE FROM sys_user_role WHERE user_id=2 AND role_id=2");
        try {
            ResponseEntity<Map> noRole = rest.exchange(
                    "/api/workbench/overview", HttpMethod.GET, authorized(token), Map.class);
            assertEquals(HttpStatus.FORBIDDEN, noRole.getStatusCode());
            assertEquals("ACCESS_DENIED", noRole.getBody().get("reason"));
        } finally {
            jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2, 2)");
        }

        jdbc.update("UPDATE sys_user SET status=0 WHERE id=2");
        try {
            ResponseEntity<Map> disabled = rest.exchange(
                    "/api/workbench/overview", HttpMethod.GET, authorized(token), Map.class);
            assertEquals(HttpStatus.UNAUTHORIZED, disabled.getStatusCode());
            assertEquals("ACCOUNT_DISABLED", disabled.getBody().get("reason"));
        } finally {
            jdbc.update("UPDATE sys_user SET status=1 WHERE id=2");
        }
    }

    @Test
    void publicIotEndpointRemainsAvailableWithoutToken() {
        ResponseEntity<Map> response = rest.getForEntity(
                "/api/iot/public/projects/power-monitor", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().get("code"));
    }

    private ResponseEntity<Map> login(String memberId, String password) {
        return rest.postForEntity("/usnhub/user/login",
                Map.of("memberId", memberId, "password", password), Map.class);
    }

    @SuppressWarnings("unchecked")
    private String token(ResponseEntity<Map> loginResponse) {
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        Map<String, Object> data = (Map<String, Object>) loginResponse.getBody().get("data");
        return data.get("token").toString();
    }

    private HttpEntity<Void> authorized(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<String, Object>> authorized(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
