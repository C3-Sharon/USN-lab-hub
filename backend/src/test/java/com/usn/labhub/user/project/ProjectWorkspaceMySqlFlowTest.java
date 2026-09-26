package com.usn.labhub.user.project;

import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.service.IAttendanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=${USNHUB_TEST_MYSQL_URL}",
                "spring.datasource.username=${USNHUB_TEST_MYSQL_USERNAME}",
                "spring.datasource.password=${USNHUB_TEST_MYSQL_PASSWORD}",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
@EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
class ProjectWorkspaceMySqlFlowTest {

    @Autowired
    private TestRestTemplate rest;

    @MockBean
    private IAttendanceService attendanceService;

    @Test
    @SuppressWarnings("unchecked")
    void completesCreateMemberIdempotencyAndWorkbenchFlowOnMysql() {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayStatus(0);
        attendance.setTodayRecords(List.of());
        attendance.setWeekHours(0.0);
        attendance.setSemesterHours(0.0);
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);

        String adminToken = token(login("admin", "admin123"));
        ResponseEntity<Map> created = rest.exchange(
                "/api/projects",
                HttpMethod.POST,
                authorized(adminToken, Map.of(
                        "code", "MYSQL-W39-001",
                        "name", "W39 MySQL 联调项目",
                        "summary", "隔离数据库验收",
                        "category", "hardware_project")),
                Map.class);
        assertEquals(HttpStatus.OK, created.getStatusCode());
        Long projectId = ((Number) data(created).get("id")).longValue();

        ResponseEntity<Map> added = addMember(adminToken, projectId, "20260001", "MEMBER");
        assertEquals(HttpStatus.OK, added.getStatusCode());
        assertEquals("MEMBER", data(added).get("projectRole"));

        ResponseEntity<Map> retried = addMember(adminToken, projectId, "20260001", "MEMBER");
        assertEquals(HttpStatus.OK, retried.getStatusCode());
        assertEquals(data(added).get("id"), data(retried).get("id"));

        ResponseEntity<Map> conflict = addMember(adminToken, projectId, "20260001", "OBSERVER");
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("ALREADY_MEMBER_DIFFERENT_ROLE", conflict.getBody().get("reason"));

        String memberToken = token(login("20260001", "20260001"));
        ResponseEntity<Map> overview = rest.exchange(
                "/api/workbench/overview",
                HttpMethod.GET,
                authorized(memberToken),
                Map.class);
        assertEquals(HttpStatus.OK, overview.getStatusCode());
        Map<String, Object> projects = (Map<String, Object>) data(overview).get("projects");
        assertEquals("READY", projects.get("state"));
        List<Map<String, Object>> list = (List<Map<String, Object>>) projects.get("list");
        assertTrue(list.stream().anyMatch(project -> "MYSQL-W39-001".equals(project.get("code"))));
    }

    private ResponseEntity<Map> addMember(String token, Long projectId, String memberId, String role) {
        return rest.exchange(
                "/api/projects/" + projectId + "/members",
                HttpMethod.POST,
                authorized(token, Map.of("memberId", memberId, "projectRole", role)),
                Map.class);
    }

    private ResponseEntity<Map> login(String memberId, String password) {
        return rest.postForEntity(
                "/usnhub/user/login",
                Map.of("memberId", memberId, "password", password),
                Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private String token(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return data(response).get("token").toString();
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
