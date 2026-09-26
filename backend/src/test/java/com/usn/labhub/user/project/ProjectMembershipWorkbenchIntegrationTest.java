package com.usn.labhub.user.project;

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
                "spring.datasource.url=jdbc:h2:mem:week3-members-workbench;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ProjectMembershipWorkbenchIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private IAttendanceService attendanceService;

    @BeforeEach
    void cleanData() {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayStatus(0);
        attendance.setTodayRecords(List.of());
        attendance.setWeekHours(0.0);
        attendance.setSemesterHours(0.0);
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);

        jdbc.update("DELETE FROM lab_project_member WHERE project_id > 1");
        jdbc.update("DELETE FROM lab_project WHERE id > 1");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE member_id = '20260002')");
        jdbc.update("DELETE FROM sys_user WHERE member_id = '20260002'");
    }

    @Test
    @SuppressWarnings("unchecked")
    void systemAdminAddsMemberAndRetriesIdempotently() {
        String adminToken = token(login("admin", "admin123"));
        Long projectId = createProject(adminToken, "MEMBER-001");

        ResponseEntity<Map> added = addMember(adminToken, projectId, "20260001", "MEMBER");
        assertEquals(HttpStatus.OK, added.getStatusCode());
        assertEquals("MEMBER", ((Map<String, Object>) added.getBody().get("data")).get("projectRole"));

        ResponseEntity<Map> retried = addMember(adminToken, projectId, "20260001", "MEMBER");
        assertEquals(HttpStatus.OK, retried.getStatusCode());
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM lab_project_member WHERE project_id = ? AND user_id = 2
                """, Integer.class, projectId));

        ResponseEntity<Map> conflict = addMember(adminToken, projectId, "20260001", "OBSERVER");
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("ALREADY_MEMBER_DIFFERENT_ROLE", conflict.getBody().get("reason"));
    }

    @Test
    void maintainerCanAddMemberButCannotGrantMaintainer() {
        insertTemporaryUser();
        String adminToken = token(login("admin", "admin123"));
        Long projectId = createProject(adminToken, "MAINTAINER-001");
        assertEquals(HttpStatus.OK,
                addMember(adminToken, projectId, "20260001", "MAINTAINER").getStatusCode());

        String maintainerToken = token(login("20260001", "20260001"));
        assertEquals(HttpStatus.OK,
                addMember(maintainerToken, projectId, "20260002", "MEMBER").getStatusCode());

        ResponseEntity<Map> denied = addMember(
                maintainerToken, projectId, "20260002", "MAINTAINER");
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("PROJECT_OPERATION_DENIED", denied.getBody().get("reason"));
    }

    @Test
    void ownerCanGrantNonOwnerRoleButCannotAddAnotherOwner() {
        String ownerToken = token(login("20260001", "20260001"));
        Long projectId = createProject(ownerToken, "OWNER-001");

        assertEquals(HttpStatus.OK,
                addMember(ownerToken, projectId, "admin", "OBSERVER").getStatusCode());

        ResponseEntity<Map> denied = addMember(ownerToken, projectId, "admin", "OWNER");
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("PROJECT_OPERATION_DENIED", denied.getBody().get("reason"));
    }

    @Test
    void memberIsDeniedAndArchivedOrUnknownTargetsReturnStableReasons() {
        String adminToken = token(login("admin", "admin123"));
        Long projectId = createProject(adminToken, "GUARD-001");
        addMember(adminToken, projectId, "20260001", "MEMBER");

        String memberToken = token(login("20260001", "20260001"));
        ResponseEntity<Map> denied = addMember(memberToken, projectId, "admin", "OBSERVER");
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("PROJECT_OPERATION_DENIED", denied.getBody().get("reason"));

        Long secondProjectId = createProject(adminToken, "ARCHIVE-002");
        jdbc.update("UPDATE lab_project SET status = 'ARCHIVED' WHERE id = ?", secondProjectId);
        ResponseEntity<Map> archived = addMember(adminToken, secondProjectId, "20260001", "MEMBER");
        assertEquals(HttpStatus.CONFLICT, archived.getStatusCode());
        assertEquals("PROJECT_ARCHIVED", archived.getBody().get("reason"));

        Long thirdProjectId = createProject(adminToken, "UNKNOWN-003");
        ResponseEntity<Map> unknown = addMember(adminToken, thirdProjectId, "NOT-EXISTS", "MEMBER");
        assertEquals(HttpStatus.NOT_FOUND, unknown.getStatusCode());
        assertEquals("USER_NOT_FOUND", unknown.getBody().get("reason"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void addedMemberSeesTheProjectOnTheWorkbench() {
        String adminToken = token(login("admin", "admin123"));
        Long projectId = createProject(adminToken, "WORKBENCH-001");
        addMember(adminToken, projectId, "20260001", "MEMBER");

        String memberToken = token(login("20260001", "20260001"));
        ResponseEntity<Map> response = rest.exchange(
                "/api/workbench/overview", HttpMethod.GET, authorized(memberToken), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> projects = (Map<String, Object>)
                ((Map<String, Object>) response.getBody().get("data")).get("projects");
        assertEquals("READY", projects.get("state"));
        assertEquals(1, projects.get("total"));
        List<Map<String, Object>> list = (List<Map<String, Object>>) projects.get("list");
        assertTrue(list.stream().anyMatch(project -> "WORKBENCH-001".equals(project.get("code"))));
    }

    private void insertTemporaryUser() {
        jdbc.update("""
                INSERT INTO sys_user(username, member_id, password, identity_id, group_id, faculty_id, status)
                SELECT '测试成员', '20260002', password, identity_id, group_id, faculty_id, 1
                FROM sys_user WHERE id = 2
                """);
    }

    @SuppressWarnings("unchecked")
    private Long createProject(String token, String code) {
        ResponseEntity<Map> response = rest.exchange("/api/projects", HttpMethod.POST,
                authorized(token, Map.of("code", code, "name", code + " 项目")), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return ((Number) ((Map<String, Object>) response.getBody().get("data")).get("id")).longValue();
    }

    private ResponseEntity<Map> addMember(String token, Long projectId, String memberId, String projectRole) {
        return rest.exchange("/api/projects/" + projectId + "/members", HttpMethod.POST,
                authorized(token, Map.of("memberId", memberId, "projectRole", projectRole)), Map.class);
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
