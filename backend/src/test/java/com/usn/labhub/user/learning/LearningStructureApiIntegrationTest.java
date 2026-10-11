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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:week5-learning-structure;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class LearningStructureApiIntegrationTest {

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
        jdbc.update("""
                DELETE FROM lab_learning_unit
                WHERE title = '发布后新增单元'
                  AND stage_id IN (SELECT id FROM lab_learning_stage WHERE roadmap_id = ?)
                """, seedRoadmapId);
        jdbc.update("DELETE FROM lab_learning_unit WHERE stage_id IN "
                + "(SELECT id FROM lab_learning_stage WHERE roadmap_id != ?)", seedRoadmapId);
        jdbc.update("DELETE FROM lab_learning_stage WHERE roadmap_id != ?", seedRoadmapId);
        jdbc.update("DELETE FROM lab_learning_roadmap WHERE id != ?", seedRoadmapId);
        jdbc.update("UPDATE lab_learning_roadmap SET status = 'PUBLISHED' WHERE id = ?", seedRoadmapId);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 2 AND role_id IN (2, 3, 4)");
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2, 2)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void managerCreatesAndEditsOrderedStagesAndUnits() {
        String adminToken = token(login("admin", "admin123"));
        Long roadmapId = createRoadmap(adminToken, "结构维护路线");
        Long laterStage = createStage(adminToken, roadmapId, "后排序阶段", 20);
        Long firstStage = createStage(adminToken, roadmapId, "先排序阶段", 10);
        Long laterUnit = createUnit(adminToken, firstStage, "后排序单元", 20, 99L);
        Long firstUnit = createUnit(adminToken, firstStage, "先排序单元", 10, null);

        ResponseEntity<Map> response = get(stagesPath(roadmapId), adminToken);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> stages = (List<Map<String, Object>>) response.getBody().get("data");
        assertEquals(List.of(firstStage, laterStage), stages.stream()
                .map(stage -> ((Number) stage.get("id")).longValue()).toList());
        List<Map<String, Object>> units = (List<Map<String, Object>>) stages.get(0).get("units");
        assertEquals(List.of(firstUnit, laterUnit), units.stream()
                .map(unit -> ((Number) unit.get("id")).longValue()).toList());
        assertTrue(units.stream().noneMatch(unit -> Boolean.TRUE.equals(unit.get("completed"))));
        assertTrue(units.stream().allMatch(unit -> unit.get("templateId") == null));
        assertTrue(units.stream().allMatch(unit -> unit.get("templateName") == null));

        Map<String, Object> stageUpdate = new LinkedHashMap<>();
        stageUpdate.put("name", "先排序阶段（已编辑）");
        stageUpdate.put("description", null);
        ResponseEntity<Map> updatedStage = put(
                "/api/learning/stages/" + firstStage, adminToken, stageUpdate);
        assertEquals(HttpStatus.OK, updatedStage.getStatusCode());
        assertNull(((Map<?, ?>) updatedStage.getBody().get("data")).get("description"));

        Map<String, Object> unitUpdate = new LinkedHashMap<>();
        unitUpdate.put("title", "先排序单元（已编辑）");
        unitUpdate.put("description", null);
        unitUpdate.put("templateId", 123L);
        ResponseEntity<Map> updatedUnit = put(
                "/api/learning/units/" + firstUnit, adminToken, unitUpdate);
        assertEquals(HttpStatus.OK, updatedUnit.getStatusCode());
        Map<String, Object> unitData = (Map<String, Object>) updatedUnit.getBody().get("data");
        assertNull(unitData.get("description"));
        assertNull(unitData.get("templateId"));
        assertFalse((Boolean) unitData.get("completed"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void completedProjectionIsIsolatedByCurrentUserAndRequiresEnrollment() {
        String memberToken = token(login("20260001", "20260001"));
        String adminToken = token(login("admin", "admin123"));
        Long firstUnitId = firstSeedUnitId();

        jdbc.update("""
                INSERT INTO lab_learning_record(roadmap_id, user_id, status, started_at)
                VALUES (?, 2, 'IN_PROGRESS', CURRENT_TIMESTAMP)
                """, seedRoadmapId);
        jdbc.update("""
                INSERT INTO lab_learning_unit_completion(unit_id, user_id)
                VALUES (?, 2)
                """, firstUnitId);

        List<Map<String, Object>> memberStages = (List<Map<String, Object>>) get(
                stagesPath(seedRoadmapId), memberToken).getBody().get("data");
        List<Map<String, Object>> memberUnits = flattenUnits(memberStages);
        assertTrue(memberUnits.stream().anyMatch(unit -> firstUnitId.equals(longValue(unit.get("id")))
                && Boolean.TRUE.equals(unit.get("completed"))));
        assertTrue(memberUnits.stream().anyMatch(unit -> !firstUnitId.equals(longValue(unit.get("id")))
                && Boolean.FALSE.equals(unit.get("completed"))));

        List<Map<String, Object>> adminStages = (List<Map<String, Object>>) get(
                stagesPath(seedRoadmapId), adminToken).getBody().get("data");
        assertTrue(flattenUnits(adminStages).stream()
                .noneMatch(unit -> Boolean.TRUE.equals(unit.get("completed"))));

        jdbc.update("DELETE FROM lab_learning_record WHERE roadmap_id = ? AND user_id = 2", seedRoadmapId);
        List<Map<String, Object>> unenrolledStages = (List<Map<String, Object>>) get(
                stagesPath(seedRoadmapId), memberToken).getBody().get("data");
        assertTrue(flattenUnits(unenrolledStages).stream()
                .noneMatch(unit -> Boolean.TRUE.equals(unit.get("completed"))));
    }

    @Test
    void addingUnitToPublishedRoadmapRecalibratesCompletedRecordsAtomically() {
        String adminToken = token(login("admin", "admin123"));
        List<Long> existingUnits = jdbc.queryForList("""
                SELECT u.id
                FROM lab_learning_unit u
                INNER JOIN lab_learning_stage s ON s.id = u.stage_id
                WHERE s.roadmap_id = ?
                """, Long.class, seedRoadmapId);
        Long firstStageId = jdbc.queryForObject("""
                SELECT id FROM lab_learning_stage
                WHERE roadmap_id = ? ORDER BY sort_order, id LIMIT 1
                """, Long.class, seedRoadmapId);
        jdbc.update("""
                INSERT INTO lab_learning_record(roadmap_id, user_id, status, started_at, completed_at)
                VALUES (?, 2, 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, seedRoadmapId);
        for (Long unitId : existingUnits) {
            jdbc.update("INSERT INTO lab_learning_unit_completion(unit_id, user_id) VALUES (?, 2)", unitId);
        }

        Long newUnitId = createUnit(adminToken, firstStageId, "发布后新增单元", 999, null);
        Map<String, Object> record = jdbc.queryForMap("""
                SELECT status, completed_at
                FROM lab_learning_record
                WHERE roadmap_id = ? AND user_id = 2
                """, seedRoadmapId);
        assertEquals("IN_PROGRESS", record.get("STATUS"));
        assertNull(record.get("COMPLETED_AT"));
        assertEquals(existingUnits.size(), jdbc.queryForObject("""
                SELECT COUNT(*) FROM lab_learning_unit_completion WHERE user_id = 2
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM lab_learning_unit_completion
                WHERE user_id = 2 AND unit_id = ?
                """, Integer.class, newUnitId));
    }

    @Test
    void permissionsVisibilityArchivedAndMissingResourcesUseContractReasons() {
        String adminToken = token(login("admin", "admin123"));
        String memberToken = token(login("20260001", "20260001"));
        Long roadmapId = createRoadmap(adminToken, "结构边界路线");
        Long stageId = createStage(adminToken, roadmapId, "边界阶段", 0);
        Long unitId = createUnit(adminToken, stageId, "边界单元", 0, null);

        assertReason(get(stagesPath(roadmapId), memberToken),
                HttpStatus.NOT_FOUND, "LEARNING_ROADMAP_NOT_FOUND");
        assertReason(post(stagesPath(roadmapId), memberToken, stageBody("越权阶段", 0)),
                HttpStatus.FORBIDDEN, "LEARNING_OPERATION_DENIED");
        assertReason(put("/api/learning/stages/999999", adminToken, Map.of("name", "不存在阶段")),
                HttpStatus.NOT_FOUND, "LEARNING_STAGE_NOT_FOUND");
        assertReason(put("/api/learning/units/999999", adminToken, Map.of("title", "不存在单元")),
                HttpStatus.NOT_FOUND, "LEARNING_UNIT_NOT_FOUND");

        assertEquals(HttpStatus.OK, put(
                "/api/learning/roadmaps/" + roadmapId + "/status",
                adminToken, Map.of("status", "ARCHIVED")).getStatusCode());
        assertReason(post(stagesPath(roadmapId), adminToken, stageBody("归档阶段", 0)),
                HttpStatus.CONFLICT, "LEARNING_ARCHIVED");
        assertReason(put("/api/learning/stages/" + stageId, adminToken, Map.of("name", "归档编辑")),
                HttpStatus.CONFLICT, "LEARNING_ARCHIVED");
        assertReason(post("/api/learning/stages/" + stageId + "/units", adminToken,
                        unitBody("归档单元", 0, null)),
                HttpStatus.CONFLICT, "LEARNING_ARCHIVED");
        assertReason(put("/api/learning/units/" + unitId, adminToken, Map.of("title", "归档编辑")),
                HttpStatus.CONFLICT, "LEARNING_ARCHIVED");
    }

    private Long createRoadmap(String token, String title) {
        ResponseEntity<Map> response = post("/api/learning/roadmaps", token,
                Map.of("title", title, "difficulty", "BEGINNER", "sortOrder", 0));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return longValue(((Map<?, ?>) response.getBody().get("data")).get("id"));
    }

    private Long createStage(String token, Long roadmapId, String name, int sortOrder) {
        ResponseEntity<Map> response = post(stagesPath(roadmapId), token, stageBody(name, sortOrder));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return longValue(((Map<?, ?>) response.getBody().get("data")).get("id"));
    }

    private Long createUnit(String token, Long stageId, String title, int sortOrder, Long templateId) {
        ResponseEntity<Map> response = post(
                "/api/learning/stages/" + stageId + "/units",
                token, unitBody(title, sortOrder, templateId));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertFalse((Boolean) data.get("completed"));
        assertNull(data.get("templateId"));
        return longValue(data.get("id"));
    }

    private Map<String, Object> stageBody(String name, int sortOrder) {
        return Map.of("name", name, "description", "阶段说明", "sortOrder", sortOrder);
    }

    private Map<String, Object> unitBody(String title, int sortOrder, Long templateId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("description", "单元说明");
        body.put("sortOrder", sortOrder);
        if (templateId != null) {
            body.put("templateId", templateId);
        }
        return body;
    }

    private Long firstSeedUnitId() {
        return jdbc.queryForObject("""
                SELECT u.id
                FROM lab_learning_unit u
                INNER JOIN lab_learning_stage s ON s.id = u.stage_id
                WHERE s.roadmap_id = ?
                ORDER BY s.sort_order, s.id, u.sort_order, u.id
                LIMIT 1
                """, Long.class, seedRoadmapId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> flattenUnits(List<Map<String, Object>> stages) {
        return stages.stream()
                .flatMap(stage -> ((List<Map<String, Object>>) stage.get("units")).stream())
                .toList();
    }

    private String stagesPath(Long roadmapId) {
        return "/api/learning/roadmaps/" + roadmapId + "/stages";
    }

    private Long longValue(Object value) {
        return ((Number) value).longValue();
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
