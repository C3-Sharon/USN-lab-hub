package com.usn.labhub.user.project;

import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.mapper.ProjectMapper;
import com.usn.labhub.user.service.IAttendanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:week3-project-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ProjectWorkspaceApiIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private IAttendanceService attendanceService;

    @SpyBean
    private ProjectMapper projectMapper;

    @BeforeEach
    void cleanProjects() {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayStatus(0);
        attendance.setTodayRecords(List.of());
        attendance.setWeekHours(0.0);
        attendance.setSemesterHours(0.0);
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);

        jdbc.update("DELETE FROM lab_project_member WHERE project_id > 1");
        jdbc.update("DELETE FROM lab_project WHERE id > 1");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 2 AND role_id IN (3, 4)");
        jdbc.update("INSERT IGNORE INTO sys_user_role(user_id, role_id) VALUES (2, 2)");
    }

    @AfterEach
    void restoreMemberRole() {
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 2 AND role_id IN (3, 4)");
        jdbc.update("INSERT IGNORE INTO sys_user_role(user_id, role_id) VALUES (2, 2)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void memberCreatesProjectAndBecomesOwnerInTheSameResult() {
        String token = token(login("20260001", "20260001"));

        ResponseEntity<Map> response = postProject(token, Map.of(
                "code", "STUDENT-001",
                "name", "学生硬件项目",
                "summary", "用于项目工作台契约测试",
                "category", "hardware_project"
        ));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("STUDENT-001", data.get("code"));
        assertEquals("PREPARING", data.get("status"));
        assertEquals("OWNER", data.get("myRole"));
        assertEquals(1, data.get("memberCount"));
        assertNull(data.get("coverUrl"));

        Long projectId = ((Number) data.get("id")).longValue();
        assertEquals("OWNER", jdbc.queryForObject("""
                SELECT project_role FROM lab_project_member
                WHERE project_id = ? AND user_id = 2
                """, String.class, projectId));
    }

    @Test
    void duplicateProjectCodeReturnsHttp409WithStableReason() {
        String token = token(login("admin", "admin123"));
        Map<String, Object> request = Map.of("code", "DUP-001", "name", "重复编号项目");

        assertEquals(HttpStatus.OK, postProject(token, request).getStatusCode());
        ResponseEntity<Map> duplicate = postProject(token, request);

        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        assertEquals("PROJECT_CODE_DUPLICATE", duplicate.getBody().get("reason"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_project WHERE project_code = 'DUP-001'", Integer.class));
    }

    @Test
    void ownerInsertFailureRollsBackProjectCreation() {
        doThrow(new IllegalStateException("forced member insert failure"))
                .when(projectMapper).insertMember(anyLong(), anyLong(), eq("OWNER"), any(LocalDateTime.class));
        String token = token(login("admin", "admin123"));

        ResponseEntity<Map> response = postProject(token,
                Map.of("code", "ROLLBACK-001", "name", "事务回滚项目"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(500, response.getBody().get("code"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_project WHERE project_code = 'ROLLBACK-001'", Integer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAndDetailHideProjectsFromNonMembersButTeacherCanReadAll() {
        String adminToken = token(login("admin", "admin123"));
        Map<String, Object> created = data(postProject(adminToken,
                Map.of("code", "ADMIN-ONLY", "name", "管理员项目")));
        Long projectId = ((Number) created.get("id")).longValue();

        String memberToken = token(login("20260001", "20260001"));
        ResponseEntity<Map> memberList = get("/api/projects", memberToken);
        List<Map<String, Object>> memberProjects = (List<Map<String, Object>>)
                ((Map<String, Object>) memberList.getBody().get("data")).get("list");
        assertTrue(memberProjects.isEmpty());

        ResponseEntity<Map> hidden = get("/api/projects/" + projectId, memberToken);
        assertEquals(HttpStatus.NOT_FOUND, hidden.getStatusCode());
        assertEquals("PROJECT_NOT_FOUND", hidden.getBody().get("reason"));

        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2, 3)");
        ResponseEntity<Map> teacherList = get("/api/projects?sortBy=code&sortOrder=asc", memberToken);
        List<Map<String, Object>> teacherProjects = (List<Map<String, Object>>)
                ((Map<String, Object>) teacherList.getBody().get("data")).get("list");
        assertFalse(teacherProjects.isEmpty());
        assertTrue(teacherProjects.stream().anyMatch(project -> "ADMIN-ONLY".equals(project.get("code"))));

        ResponseEntity<Map> visible = get("/api/projects/" + projectId, memberToken);
        assertEquals(HttpStatus.OK, visible.getStatusCode());
        assertNull(((Map<String, Object>) visible.getBody().get("data")).get("myRole"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void archivedProjectsRequireAnExplicitStatusFilter() {
        String adminToken = token(login("admin", "admin123"));
        Map<String, Object> created = data(postProject(adminToken,
                Map.of("code", "ARCHIVED-001", "name", "归档项目")));
        Long projectId = ((Number) created.get("id")).longValue();
        jdbc.update("UPDATE lab_project SET status = 'ARCHIVED' WHERE id = ?", projectId);

        List<Map<String, Object>> defaultProjects = (List<Map<String, Object>>)
                ((Map<String, Object>) get("/api/projects", adminToken).getBody().get("data")).get("list");
        assertFalse(defaultProjects.stream().anyMatch(project -> projectId.equals(
                ((Number) project.get("id")).longValue())));

        List<Map<String, Object>> archivedProjects = (List<Map<String, Object>>)
                ((Map<String, Object>) get("/api/projects?status=ARCHIVED", adminToken)
                        .getBody().get("data")).get("list");
        assertTrue(archivedProjects.stream().anyMatch(project -> projectId.equals(
                ((Number) project.get("id")).longValue())));
    }

    @Test
    void stockKeeperCannotCreateAndInvalidPagingReturnsBadRequest() {
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 2 AND role_id = 2");
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2, 4)");
        String stockToken = token(login("20260001", "20260001"));

        ResponseEntity<Map> denied = postProject(stockToken,
                Map.of("code", "STOCK-001", "name", "库存项目"));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("ACCESS_DENIED", denied.getBody().get("reason"));

        String adminToken = token(login("admin", "admin123"));
        ResponseEntity<Map> invalidPage = get("/api/projects?pageSize=51", adminToken);
        assertEquals(HttpStatus.BAD_REQUEST, invalidPage.getStatusCode());
        assertEquals("INVALID_PROJECT_REQUEST", invalidPage.getBody().get("reason"));
    }

    private ResponseEntity<Map> postProject(String token, Map<String, Object> body) {
        return rest.exchange("/api/projects", HttpMethod.POST, authorized(token, body), Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, authorized(token), Map.class);
    }

    private ResponseEntity<Map> login(String memberId, String password) {
        return rest.postForEntity("/usnhub/user/login",
                Map.of("memberId", memberId, "password", password), Map.class);
    }

    @SuppressWarnings("unchecked")
    private String token(ResponseEntity<Map> loginResponse) {
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        return ((Map<String, Object>) loginResponse.getBody().get("data")).get("token").toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return (Map<String, Object>) response.getBody().get("data");
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
