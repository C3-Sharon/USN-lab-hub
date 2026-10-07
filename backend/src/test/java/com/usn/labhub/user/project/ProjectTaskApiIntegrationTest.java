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

import java.util.HashMap;
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
                "spring.datasource.url=jdbc:h2:mem:week4-tasks;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ProjectTaskApiIntegrationTest {

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
    void managerCompletesCreateListDetailAndEditFlow() {
        String adminToken = token(login("admin", "admin123"));
        Long projectId = createProject(adminToken, "TASK-MANAGER");
        addMember(adminToken, projectId, "20260001", "MEMBER");
        Long milestoneId = createMilestone(adminToken, projectId, "硬件原型");

        Map<String, Object> task = createTask(adminToken, projectId,
                taskBody("完成原理图", milestoneId, 2L));
        assertEquals("TODO", task.get("status"));
        assertEquals(1, task.get("version"));
        assertEquals("MEDIUM", task.get("priority"));
        assertNotNull(task.get("assigneeName"));

        ResponseEntity<Map> listResponse = get(tasksPath(projectId)
                + "?milestoneId=" + milestoneId + "&assigneeUserId=2", adminToken);
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        Map<String, Object> page = (Map<String, Object>) listResponse.getBody().get("data");
        assertEquals(1, page.get("total"));
        assertEquals(1, ((List<?>) page.get("list")).size());

        Long taskId = ((Number) task.get("id")).longValue();
        Map<String, Object> update = new HashMap<>();
        update.put("title", "完成 PCB 原理图");
        update.put("description", "通过评审后提交");
        update.put("milestoneId", milestoneId);
        update.put("assigneeUserId", 2);
        update.put("priority", "HIGH");
        update.put("dueDate", "2026-10-20");
        update.put("version", 1);
        ResponseEntity<Map> updated = put(tasksPath(projectId) + "/" + taskId, adminToken, update);
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        Map<String, Object> updatedTask = (Map<String, Object>) updated.getBody().get("data");
        assertEquals("完成 PCB 原理图", updatedTask.get("title"));
        assertEquals("HIGH", updatedTask.get("priority"));
        assertEquals(2, updatedTask.get("version"));
        assertEquals(HttpStatus.OK, get(tasksPath(projectId) + "/" + taskId, adminToken).getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void assignedMemberMovesOwnTaskButCannotCancelAndBlockReasonIsPreserved() {
        String adminToken = token(login("admin", "admin123"));
        String memberToken = token(login("20260001", "20260001"));
        Long projectId = createProject(adminToken, "TASK-MEMBER");
        addMember(adminToken, projectId, "20260001", "MEMBER");
        Map<String, Object> task = createTask(adminToken, projectId, taskBody("焊接验证板", null, 2L));
        Long taskId = ((Number) task.get("id")).longValue();

        assertEquals(HttpStatus.OK, updateStatus(memberToken, projectId, taskId, "IN_PROGRESS", 1, null)
                .getStatusCode());
        ResponseEntity<Map> noReason = updateStatus(memberToken, projectId, taskId, "BLOCKED", 2, " ");
        assertEquals(HttpStatus.BAD_REQUEST, noReason.getStatusCode());
        assertEquals("BLOCK_REASON_REQUIRED", noReason.getBody().get("reason"));
        assertEquals(HttpStatus.OK, updateStatus(memberToken, projectId, taskId, "BLOCKED", 2, "等待元器件")
                .getStatusCode());
        assertEquals(HttpStatus.OK, updateStatus(memberToken, projectId, taskId, "IN_PROGRESS", 3, null)
                .getStatusCode());

        Map<String, Object> detail = (Map<String, Object>) get(tasksPath(projectId) + "/" + taskId, memberToken)
                .getBody().get("data");
        assertEquals("等待元器件", detail.get("blockReason"));
        ResponseEntity<Map> denied = updateStatus(memberToken, projectId, taskId, "CANCELED", 4, null);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("PROJECT_OPERATION_DENIED", denied.getBody().get("reason"));

        assertEquals(HttpStatus.OK, updateStatus(adminToken, projectId, taskId, "CANCELED", 4, null)
                .getStatusCode());
        Map<String, Object> defaultPage = (Map<String, Object>) get(tasksPath(projectId), adminToken)
                .getBody().get("data");
        assertEquals(0, defaultPage.get("total"));
        Map<String, Object> canceledPage = (Map<String, Object>) get(
                tasksPath(projectId) + "?status=CANCELED", adminToken).getBody().get("data");
        assertEquals(1, canceledPage.get("total"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void staleVersionReturnsCurrentVersionWithoutOverwriting() {
        String adminToken = token(login("admin", "admin123"));
        Long projectId = createProject(adminToken, "TASK-VERSION");
        Map<String, Object> task = createTask(adminToken, projectId, taskBody("并发修改任务", null, null));
        Long taskId = ((Number) task.get("id")).longValue();

        Map<String, Object> first = updateBody("第一次修改", 1);
        assertEquals(HttpStatus.OK, put(tasksPath(projectId) + "/" + taskId, adminToken, first).getStatusCode());
        ResponseEntity<Map> stale = put(tasksPath(projectId) + "/" + taskId, adminToken,
                updateBody("过期修改", 1));
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());
        assertEquals("VERSION_CONFLICT", stale.getBody().get("reason"));
        Map<String, Object> conflictData = (Map<String, Object>) stale.getBody().get("data");
        assertEquals(2, conflictData.get("currentVersion"));
        assertEquals(taskId, ((Number) conflictData.get("taskId")).longValue());

        Map<String, Object> detail = (Map<String, Object>) get(tasksPath(projectId) + "/" + taskId, adminToken)
                .getBody().get("data");
        assertEquals("第一次修改", detail.get("title"));
    }

    @Test
    void rejectsInvalidReferencesUnauthorizedWritesAndFrozenProjects() {
        String adminToken = token(login("admin", "admin123"));
        String memberToken = token(login("20260001", "20260001"));
        Long projectId = createProject(adminToken, "TASK-BOUNDARY-A");
        Long anotherProjectId = createProject(adminToken, "TASK-BOUNDARY-B");
        Long foreignMilestone = createMilestone(adminToken, anotherProjectId, "其他项目里程碑");

        ResponseEntity<Map> foreign = post(tasksPath(projectId), adminToken,
                taskBody("错误里程碑", foreignMilestone, null));
        assertEquals(HttpStatus.BAD_REQUEST, foreign.getStatusCode());
        assertEquals("MILESTONE_NOT_FOUND", foreign.getBody().get("reason"));
        ResponseEntity<Map> nonMember = post(tasksPath(projectId), adminToken,
                taskBody("错误负责人", null, 2L));
        assertEquals(HttpStatus.BAD_REQUEST, nonMember.getStatusCode());
        assertEquals("ASSIGNEE_NOT_MEMBER", nonMember.getBody().get("reason"));

        addMember(adminToken, projectId, "20260001", "MEMBER");
        ResponseEntity<Map> memberCreate = post(tasksPath(projectId), memberToken,
                taskBody("成员越权创建", null, 2L));
        assertEquals(HttpStatus.FORBIDDEN, memberCreate.getStatusCode());
        assertEquals("PROJECT_OPERATION_DENIED", memberCreate.getBody().get("reason"));

        jdbc.update("UPDATE lab_project SET status='PAUSED' WHERE id=?", projectId);
        ResponseEntity<Map> paused = post(tasksPath(projectId), adminToken,
                taskBody("暂停项目任务", null, 2L));
        assertEquals(HttpStatus.CONFLICT, paused.getStatusCode());
        assertEquals("PROJECT_PAUSED", paused.getBody().get("reason"));
        assertEquals(HttpStatus.OK, get(tasksPath(projectId), memberToken).getStatusCode());
    }

    @Test
    void validatesListAndTerminalTransition() {
        String adminToken = token(login("admin", "admin123"));
        Long projectId = createProject(adminToken, "TASK-VALIDATION");
        Map<String, Object> task = createTask(adminToken, projectId, taskBody("终态任务", null, null));
        Long taskId = ((Number) task.get("id")).longValue();

        assertEquals(HttpStatus.BAD_REQUEST,
                get(tasksPath(projectId) + "?pageSize=101", adminToken).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                get(tasksPath(projectId) + "?sortBy=title", adminToken).getStatusCode());
        ResponseEntity<Map> invalidDueDate = rawPut(tasksPath(projectId) + "/" + taskId, adminToken,
                "{\"title\":\"非法日期\",\"priority\":\"MEDIUM\",\"dueDate\":\"2026-02-30\",\"version\":1}");
        assertEquals(HttpStatus.BAD_REQUEST, invalidDueDate.getStatusCode());
        assertEquals("INVALID_DUE_DATE", invalidDueDate.getBody().get("reason"));
        assertEquals(HttpStatus.OK, updateStatus(adminToken, projectId, taskId, "IN_PROGRESS", 1, null)
                .getStatusCode());
        assertEquals(HttpStatus.OK, updateStatus(adminToken, projectId, taskId, "DONE", 2, null)
                .getStatusCode());
        ResponseEntity<Map> terminal = updateStatus(adminToken, projectId, taskId, "IN_PROGRESS", 3, null);
        assertEquals(HttpStatus.CONFLICT, terminal.getStatusCode());
        assertEquals("TASK_INVALID_TRANSITION", terminal.getBody().get("reason"));
    }

    private Long createProject(String token, String code) {
        ResponseEntity<Map> response = post("/api/projects", token, Map.of("code", code, "name", code + " 项目"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return ((Number) ((Map<?, ?>) response.getBody().get("data")).get("id")).longValue();
    }

    private Long createMilestone(String token, Long projectId, String name) {
        ResponseEntity<Map> response = post("/api/projects/" + projectId + "/milestones", token,
                Map.of("name", name, "status", "PLANNED", "sortOrder", 0));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return ((Number) ((Map<?, ?>) response.getBody().get("data")).get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createTask(String token, Long projectId, Map<String, Object> body) {
        ResponseEntity<Map> response = post(tasksPath(projectId), token, body);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return (Map<String, Object>) response.getBody().get("data");
    }

    private Map<String, Object> taskBody(String title, Long milestoneId, Long assigneeUserId) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("milestoneId", milestoneId);
        body.put("assigneeUserId", assigneeUserId);
        body.put("priority", "MEDIUM");
        return body;
    }

    private Map<String, Object> updateBody(String title, int version) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("priority", "MEDIUM");
        body.put("version", version);
        return body;
    }

    private void addMember(String token, Long projectId, String memberId, String projectRole) {
        ResponseEntity<Map> response = post("/api/projects/" + projectId + "/members", token,
                Map.of("memberId", memberId, "projectRole", projectRole));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private ResponseEntity<Map> updateStatus(String token, Long projectId, Long taskId, String status,
                                               int version, String blockReason) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("version", version);
        body.put("blockReason", blockReason);
        return put(tasksPath(projectId) + "/" + taskId + "/status", token, body);
    }

    private String tasksPath(Long projectId) {
        return "/api/projects/" + projectId + "/tasks";
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, authorized(token), Map.class);
    }

    private ResponseEntity<Map> post(String path, String token, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.POST, authorized(token, body), Map.class);
    }

    private ResponseEntity<Map> put(String path, String token, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.PUT, authorized(token, body), Map.class);
    }

    private ResponseEntity<Map> rawPut(String path, String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers), Map.class);
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
