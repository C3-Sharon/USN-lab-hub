package com.usn.labhub.user.learning;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:week5-learning-roadmaps;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class LearningRoadmapApiIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private IAttendanceService attendanceService;

    private Long seedRoadmapId;

    @BeforeEach
    void cleanData() {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayStatus(0);
        attendance.setTodayRecords(List.of());
        attendance.setWeekHours(0.0);
        attendance.setSemesterHours(0.0);
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);

        seedRoadmapId = jdbc.queryForObject("""
                SELECT MIN(id) FROM lab_learning_roadmap
                WHERE title = '嵌入式硬件入门'
                """, Long.class);
        jdbc.update("DELETE FROM lab_learning_unit_completion");
        jdbc.update("DELETE FROM lab_learning_record");
        jdbc.update("DELETE FROM lab_learning_unit WHERE stage_id IN "
                + "(SELECT id FROM lab_learning_stage WHERE roadmap_id != ?)", seedRoadmapId);
        jdbc.update("DELETE FROM lab_learning_stage WHERE roadmap_id != ?", seedRoadmapId);
        jdbc.update("DELETE FROM lab_learning_roadmap WHERE id != ?", seedRoadmapId);
        jdbc.update("""
                UPDATE lab_learning_roadmap
                SET title = '嵌入式硬件入门', description = '从零开始学习嵌入式硬件开发',
                    status = 'PUBLISHED', difficulty = 'BEGINNER', estimated_hours = 28,
                    cover_media_id = NULL, sort_order = 0, created_by = 1
                WHERE id = ?
                """, seedRoadmapId);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 2 AND role_id IN (2, 3, 4)");
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2, 2)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void managerCompletesRoadmapCrudAndForwardOnlyStatusFlow() {
        String adminToken = token(login("admin", "admin123"));
        Long roadmapId = createRoadmap(adminToken, "后端学习路线");

        ResponseEntity<Map> draftList = get("/api/learning/roadmaps?status=DRAFT", adminToken);
        assertEquals(HttpStatus.OK, draftList.getStatusCode());
        Map<String, Object> page = (Map<String, Object>) draftList.getBody().get("data");
        assertEquals(1, ((Number) page.get("total")).intValue());
        Map<String, Object> listItem = ((List<Map<String, Object>>) page.get("list")).get(0);
        assertEquals("后端学习路线", listItem.get("title"));
        assertEquals("系统管理员", listItem.get("createdByName"));

        ResponseEntity<Map> detailResponse = get(roadmapPath(roadmapId), adminToken);
        assertEquals(HttpStatus.OK, detailResponse.getStatusCode());
        Map<String, Object> detail = (Map<String, Object>) detailResponse.getBody().get("data");
        assertEquals(List.of(), detail.get("stages"));

        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("title", "后端学习路线 2026");
        updateBody.put("difficulty", "INTERMEDIATE");
        updateBody.put("estimatedHours", 36);
        updateBody.put("sortOrder", 5);
        ResponseEntity<Map> updated = put(roadmapPath(roadmapId), adminToken, updateBody);
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        Map<String, Object> updatedData = (Map<String, Object>) updated.getBody().get("data");
        assertEquals("INTERMEDIATE", updatedData.get("difficulty"));
        assertEquals(36, updatedData.get("estimatedHours"));
        assertEquals(0, updatedData.get("stageCount"));
        assertEquals(0, updatedData.get("learnerCount"));

        Map<String, Object> clearOptionalFields = new LinkedHashMap<>();
        clearOptionalFields.put("description", null);
        clearOptionalFields.put("coverMediaId", null);
        ResponseEntity<Map> cleared = put(roadmapPath(roadmapId), adminToken, clearOptionalFields);
        assertEquals(HttpStatus.OK, cleared.getStatusCode());
        Map<String, Object> clearedData = (Map<String, Object>) cleared.getBody().get("data");
        assertEquals(null, clearedData.get("description"));
        assertEquals(null, clearedData.get("coverMediaId"));

        ResponseEntity<Map> published = updateStatus(roadmapId, adminToken, "PUBLISHED");
        assertEquals(HttpStatus.OK, published.getStatusCode());
        assertEquals("PUBLISHED", ((Map<?, ?>) published.getBody().get("data")).get("status"));

        ResponseEntity<Map> backward = updateStatus(roadmapId, adminToken, "DRAFT");
        assertEquals(HttpStatus.CONFLICT, backward.getStatusCode());
        assertEquals("LEARNING_INVALID_TRANSITION", backward.getBody().get("reason"));

        assertEquals(HttpStatus.OK,
                updateStatus(roadmapId, adminToken, "ARCHIVED").getStatusCode());
        ResponseEntity<Map> archivedEdit = put(
                roadmapPath(roadmapId), adminToken, Map.of("title", "不可编辑路线"));
        assertEquals(HttpStatus.CONFLICT, archivedEdit.getStatusCode());
        assertEquals("LEARNING_ARCHIVED", archivedEdit.getBody().get("reason"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void memberSeesOnlyPublishedRoadmapsAndCannotManage() {
        String adminToken = token(login("admin", "admin123"));
        Long draftId = createRoadmap(adminToken, "成员不可见草稿");
        String memberToken = token(login("20260001", "20260001"));

        ResponseEntity<Map> listResponse = get("/api/learning/roadmaps", memberToken);
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        Map<String, Object> page = (Map<String, Object>) listResponse.getBody().get("data");
        List<Map<String, Object>> list = (List<Map<String, Object>>) page.get("list");
        assertFalse(list.stream().anyMatch(item -> draftId.equals(((Number) item.get("id")).longValue())));
        assertEquals(List.of("PUBLISHED"), list.stream().map(item -> item.get("status").toString()).distinct().toList());

        ResponseEntity<Map> filteredDrafts = get(
                "/api/learning/roadmaps?status=DRAFT", memberToken);
        assertEquals(0, ((Number) ((Map<?, ?>) filteredDrafts.getBody().get("data")).get("total")).intValue());

        ResponseEntity<Map> hidden = get(roadmapPath(draftId), memberToken);
        assertEquals(HttpStatus.NOT_FOUND, hidden.getStatusCode());
        assertEquals("LEARNING_ROADMAP_NOT_FOUND", hidden.getBody().get("reason"));

        ResponseEntity<Map> denied = post("/api/learning/roadmaps", memberToken,
                createBody("成员越权路线"));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("LEARNING_OPERATION_DENIED", denied.getBody().get("reason"));
    }

    @Test
    void teacherCanManageRoadmapsCreatedByAnotherUser() {
        String adminToken = token(login("admin", "admin123"));
        Long roadmapId = createRoadmap(adminToken, "教师全局维护路线");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 2");
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2, 3)");
        String teacherToken = token(login("20260001", "20260001"));

        ResponseEntity<Map> response = put(
                roadmapPath(roadmapId), teacherToken, Map.of("estimatedHours", 24));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(24, ((Map<?, ?>) response.getBody().get("data")).get("estimatedHours"));
    }

    @Test
    void invalidParametersAndGuestAccessReturnStableReasons() {
        String adminToken = token(login("admin", "admin123"));
        assertReason(get("/api/learning/roadmaps?page=0", adminToken),
                HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        assertReason(get("/api/learning/roadmaps?sortBy=title", adminToken),
                HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        assertReason(post("/api/learning/roadmaps", adminToken,
                        Map.of("title", "无效状态路线", "status", "PUBLISHED")),
                HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        assertReason(post("/api/learning/roadmaps", adminToken, Map.of("title", " ")),
                HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");

        String memberToken = token(login("20260001", "20260001"));
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 2");
        assertReason(get("/api/learning/roadmaps", memberToken),
                HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    private Long createRoadmap(String token, String title) {
        ResponseEntity<Map> response = post("/api/learning/roadmaps", token, createBody(title));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("DRAFT", data.get("status"));
        assertEquals(0, data.get("stageCount"));
        assertNotNull(data.get("createTime"));
        return ((Number) data.get("id")).longValue();
    }

    private Map<String, Object> createBody(String title) {
        return Map.of(
                "title", title,
                "description", "用于验证冻结的学习路线接口",
                "difficulty", "BEGINNER",
                "estimatedHours", 12,
                "sortOrder", 10);
    }

    private ResponseEntity<Map> updateStatus(Long roadmapId, String token, String status) {
        return put(roadmapPath(roadmapId) + "/status", token, Map.of("status", status));
    }

    private String roadmapPath(Long roadmapId) {
        return "/api/learning/roadmaps/" + roadmapId;
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

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, authorized(token), Map.class);
    }

    private ResponseEntity<Map> post(String path, String token, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.POST, authorized(token, body), Map.class);
    }

    private ResponseEntity<Map> put(String path, String token, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.PUT, authorized(token, body), Map.class);
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

    private void assertReason(ResponseEntity<Map> response, HttpStatus status, String reason) {
        assertEquals(status, response.getStatusCode());
        assertEquals(reason, response.getBody().get("reason"));
    }
}
