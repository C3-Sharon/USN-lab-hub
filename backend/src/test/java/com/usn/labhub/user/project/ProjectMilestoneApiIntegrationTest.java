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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:week4-milestones;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ProjectMilestoneApiIntegrationTest {

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

        jdbc.update("DELETE FROM lab_task");
        jdbc.update("DELETE FROM lab_milestone");
        jdbc.update("DELETE FROM lab_project_member WHERE project_id > 1");
        jdbc.update("DELETE FROM lab_project WHERE id > 1");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 2 AND role_id IN (3, 4)");
        jdbc.update("INSERT IGNORE INTO sys_user_role(user_id, role_id) VALUES (2, 2)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ownerCreatesStableListWithTaskStatsAndForwardOnlyTransitions() {
        String ownerToken = token(login("20260001", "20260001"));
        Long projectId = createProject(ownerToken, "MILESTONE-OWNER");
        Long laterId = createMilestone(ownerToken, projectId, "后排序里程碑", 2);
        Long firstId = createMilestone(ownerToken, projectId, "先排序里程碑", 1);

        jdbc.update("""
                INSERT INTO lab_task(project_id, milestone_id, title, status, created_by)
                VALUES (?, ?, '已完成任务', 'DONE', 2), (?, ?, '待办任务', 'TODO', 2)
                """, projectId, firstId, projectId, firstId);

        ResponseEntity<Map> listResponse = get(milestonePath(projectId), ownerToken);
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        List<Map<String, Object>> milestones = (List<Map<String, Object>>) listResponse.getBody().get("data");
        assertEquals(List.of(firstId, laterId), milestones.stream()
                .map(item -> ((Number) item.get("id")).longValue()).toList());
        assertEquals(2, milestones.get(0).get("taskCount"));
        assertEquals(1, milestones.get(0).get("taskDone"));

        ResponseEntity<Map> started = updateStatus(ownerToken, projectId, firstId, "IN_PROGRESS");
        assertEquals(HttpStatus.OK, started.getStatusCode());
        Map<String, Object> startedData = (Map<String, Object>) started.getBody().get("data");
        assertEquals("IN_PROGRESS", startedData.get("status"));
        assertNotNull(startedData.get("updateTime"));

        assertEquals(HttpStatus.OK,
                updateStatus(ownerToken, projectId, firstId, "COMPLETED").getStatusCode());
        ResponseEntity<Map> rejected = updateStatus(ownerToken, projectId, firstId, "IN_PROGRESS");
        assertEquals(HttpStatus.CONFLICT, rejected.getStatusCode());
        assertEquals("MILESTONE_INVALID_TRANSITION", rejected.getBody().get("reason"));
    }

    @Test
    void memberCannotWriteNonMemberIsHiddenAndTeacherCanOnlyRead() {
        String adminToken = token(login("admin", "admin123"));
        Long projectId = createProject(adminToken, "MILESTONE-ACCESS");
        String memberToken = token(login("20260001", "20260001"));

        ResponseEntity<Map> hidden = get(milestonePath(projectId), memberToken);
        assertEquals(HttpStatus.NOT_FOUND, hidden.getStatusCode());
        assertEquals("PROJECT_NOT_FOUND", hidden.getBody().get("reason"));

        addMember(adminToken, projectId, "20260001", "MEMBER");
        ResponseEntity<Map> denied = postMilestone(memberToken, projectId, milestoneBody("成员不可创建", 0));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("PROJECT_OPERATION_DENIED", denied.getBody().get("reason"));

        jdbc.update("DELETE FROM lab_project_member WHERE project_id = ? AND user_id = 2", projectId);
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2, 3)");
        assertEquals(HttpStatus.OK, get(milestonePath(projectId), memberToken).getStatusCode());
        ResponseEntity<Map> teacherDenied = postMilestone(
                memberToken, projectId, milestoneBody("教师只读里程碑", 0));
        assertEquals(HttpStatus.FORBIDDEN, teacherDenied.getStatusCode());
        assertEquals("PROJECT_OPERATION_DENIED", teacherDenied.getBody().get("reason"));
    }

    @Test
    void pausedCompletedAndArchivedProjectsRejectWritesWithStableReasons() {
        String adminToken = token(login("admin", "admin123"));
        Long projectId = createProject(adminToken, "MILESTONE-STATE");
        Map<String, String> reasons = new LinkedHashMap<>();
        reasons.put("PAUSED", "PROJECT_PAUSED");
        reasons.put("COMPLETED", "PROJECT_COMPLETED");
        reasons.put("ARCHIVED", "PROJECT_ARCHIVED");

        for (Map.Entry<String, String> entry : reasons.entrySet()) {
            jdbc.update("UPDATE lab_project SET status = ? WHERE id = ?", entry.getKey(), projectId);
            ResponseEntity<Map> response = postMilestone(
                    adminToken, projectId, milestoneBody("受限项目里程碑", 0));
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertEquals(entry.getValue(), response.getBody().get("reason"));
        }
    }

    @Test
    void malformedFiltersAndUnknownMilestonesReturnContractReasons() {
        String adminToken = token(login("admin", "admin123"));
        Long projectId = createProject(adminToken, "MILESTONE-ERROR");

        ResponseEntity<Map> invalidFilter = get(milestonePath(projectId) + "?status=UNKNOWN", adminToken);
        assertEquals(HttpStatus.BAD_REQUEST, invalidFilter.getStatusCode());
        assertEquals("INVALID_PARAMETER", invalidFilter.getBody().get("reason"));

        ResponseEntity<Map> invalidBody = postMilestone(
                adminToken, projectId, Map.of("name", "  ", "status", "PLANNED", "sortOrder", 0));
        assertEquals(HttpStatus.BAD_REQUEST, invalidBody.getStatusCode());
        assertEquals("INVALID_PARAMETER", invalidBody.getBody().get("reason"));

        ResponseEntity<Map> missing = updateStatus(adminToken, projectId, 999999L, "IN_PROGRESS");
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals("MILESTONE_NOT_FOUND", missing.getBody().get("reason"));
    }

    private Long createProject(String token, String code) {
        ResponseEntity<Map> response = rest.exchange("/api/projects", HttpMethod.POST,
                authorized(token, Map.of("code", code, "name", code + " 项目")), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        return ((Number) data.get("id")).longValue();
    }

    private Long createMilestone(String token, Long projectId, String name, int sortOrder) {
        ResponseEntity<Map> response = postMilestone(token, projectId, milestoneBody(name, sortOrder));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        return ((Number) data.get("id")).longValue();
    }

    private Map<String, Object> milestoneBody(String name, int sortOrder) {
        return Map.of("name", name, "status", "PLANNED", "sortOrder", sortOrder);
    }

    private String milestonePath(Long projectId) {
        return "/api/projects/" + projectId + "/milestones";
    }

    private ResponseEntity<Map> postMilestone(String token, Long projectId, Map<String, Object> body) {
        return rest.exchange(milestonePath(projectId), HttpMethod.POST, authorized(token, body), Map.class);
    }

    private ResponseEntity<Map> updateStatus(String token, Long projectId, Long milestoneId, String status) {
        return rest.exchange(milestonePath(projectId) + "/" + milestoneId + "/status", HttpMethod.PUT,
                authorized(token, Map.of("status", status)), Map.class);
    }

    private void addMember(String token, Long projectId, String memberId, String projectRole) {
        ResponseEntity<Map> response = rest.exchange("/api/projects/" + projectId + "/members",
                HttpMethod.POST, authorized(token, Map.of("memberId", memberId, "projectRole", projectRole)),
                Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
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
