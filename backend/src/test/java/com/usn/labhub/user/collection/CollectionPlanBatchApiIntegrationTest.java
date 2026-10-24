package com.usn.labhub.user.collection;

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
                "spring.datasource.url=jdbc:h2:mem:w43-collection-plan-batch;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class CollectionPlanBatchApiIntegrationTest {
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private IAttendanceService attendanceService;

    private Long projectId;

    @BeforeEach
    void setUp() {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayStatus(0);
        attendance.setTodayRecords(List.of());
        attendance.setWeekHours(0.0);
        attendance.setSemesterHours(0.0);
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);

        jdbc.update("DELETE FROM lab_experiment_audit_log");
        jdbc.update("DELETE FROM lab_request_idempotency");
        jdbc.update("DELETE FROM lab_collection_record");
        jdbc.update("DELETE FROM lab_data_import_revision");
        jdbc.update("DELETE FROM lab_data_import");
        jdbc.update("DELETE FROM lab_collection_batch");
        jdbc.update("DELETE FROM lab_collection_plan");
        jdbc.update("DELETE FROM lab_project_member WHERE project_id IN (SELECT id FROM lab_project WHERE project_code='W43-COLLECT')");
        jdbc.update("DELETE FROM lab_project WHERE project_code='W43-COLLECT'");
        jdbc.update("""
                INSERT INTO lab_project(project_code, project_name, owner_id, status, public_visible, category)
                VALUES ('W43-COLLECT', 'W43 采集测试项目', 1, 'ACTIVE', 0, 'learning')
                """);
        projectId = jdbc.queryForObject("SELECT id FROM lab_project WHERE project_code='W43-COLLECT'", Long.class);
        jdbc.update("INSERT INTO lab_project_member(project_id,user_id,project_role) VALUES (?,1,'OWNER')", projectId);
        jdbc.update("INSERT INTO lab_project_member(project_id,user_id,project_role) VALUES (?,2,'MEMBER')", projectId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void completesPlanAndFileBatchLifecycleWithExactReplay() {
        String admin = token(login("admin", "admin123"));
        String member = token(login("20260001", "20260001"));
        Map<String, Object> planBody = planBody(2L, "FILE");

        Map<String, Object> created = data(post(projectPlans(), admin, "plan-create-1", planBody));
        Long planId = number(created.get("id"));
        assertEquals("DRAFT", created.get("status"));
        assertEquals(planId, number(data(post(projectPlans(), admin, "plan-create-1", planBody)).get("id")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lab_collection_plan", Integer.class));

        Map<String, Object> updated = data(put("/api/collection-plans/" + planId, admin,
                planUpdateBody(1, 2L)));
        assertEquals(2, updated.get("version"));
        assertEquals(planId, number(data(get("/api/collection-plans/" + planId, member)).get("id")));
        assertEquals(1, data(get(projectPlans(), member)).get("total"));

        ResponseEntity<Map> draftBatch = post(planBatches(planId), member, "batch-too-early",
                Map.of("planVersion", 2, "sourceType", "FILE"));
        assertEquals(HttpStatus.CONFLICT, draftBatch.getStatusCode());
        assertEquals("COLLECTION_PLAN_NOT_READY", draftBatch.getBody().get("reason"));

        Map<String, Object> ready = data(post("/api/collection-plans/" + planId + "/ready", admin,
                "plan-ready-1", Map.of("version", 2)));
        assertEquals("READY", ready.get("status"));

        Map<String, Object> batch = data(post(planBatches(planId), member, "batch-create-1",
                Map.of("planVersion", 3, "sourceType", "FILE")));
        Long batchId = number(batch.get("id"));
        assertEquals("CREATED", batch.get("status"));
        assertEquals(batchId, number(data(post(planBatches(planId), member, "batch-create-1",
                Map.of("planVersion", 3, "sourceType", "FILE"))).get("id")));

        ResponseEntity<Map> premature = post(batchPath(batchId) + "/complete", member, "complete-early",
                Map.of("version", 1, "completionReason", "尚无数据"));
        assertEquals(HttpStatus.CONFLICT, premature.getStatusCode());
        assertEquals("BATCH_INVALID_TRANSITION", premature.getBody().get("reason"));

        seedConfirmedFileFacts(batchId);
        Map<String, Object> completeBody = Map.of("version", 2, "completionReason", "CSV 已确认");
        Map<String, Object> completed = data(post(batchPath(batchId) + "/complete", member,
                "complete-1", completeBody));
        assertEquals("COMPLETED", completed.get("status"));
        assertEquals(1, ((Number) completed.get("recordCount")).intValue());
        assertNotNull(completed.get("checksum"));
        assertEquals("COMPLETED", data(post(batchPath(batchId) + "/complete", member,
                "complete-1", completeBody)).get("status"));

        Map<String, Object> archived = data(post(batchPath(batchId) + "/archive", member,
                "archive-batch-1", Map.of("version", 3)));
        assertEquals("ARCHIVED", archived.get("status"));
        assertEquals("ARCHIVED", data(get(batchPath(batchId), member)).get("status"));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM lab_experiment_audit_log", Integer.class));
    }

    @Test
    void enforcesSourceOwnerProjectStateAndOptimisticLockBoundaries() {
        String admin = token(login("admin", "admin123"));
        String member = token(login("20260001", "20260001"));
        Map<String, Object> created = data(post(projectPlans(), admin, "plan-create-2", planBody(1L, "MANUAL")));
        Long planId = number(created.get("id"));

        ResponseEntity<Map> stale = put("/api/collection-plans/" + planId, admin,
                planUpdateBody(2, 1L));
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());
        assertEquals("VERSION_CONFLICT", stale.getBody().get("reason"));

        data(post("/api/collection-plans/" + planId + "/ready", admin, "plan-ready-2", Map.of("version", 1)));
        ResponseEntity<Map> denied = post(planBatches(planId), member, "member-not-owner",
                Map.of("planVersion", 2, "sourceType", "MANUAL"));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("COLLECTION_OPERATION_DENIED", denied.getBody().get("reason"));

        ResponseEntity<Map> wrongSource = post(planBatches(planId), admin, "wrong-source",
                Map.of("planVersion", 2, "sourceType", "FILE"));
        assertEquals(HttpStatus.BAD_REQUEST, wrongSource.getStatusCode());
        assertEquals("INVALID_PARAMETER", wrongSource.getBody().get("reason"));

        jdbc.update("UPDATE lab_project SET status='PAUSED' WHERE id=?", projectId);
        ResponseEntity<Map> paused = post(planBatches(planId), admin, "paused-project",
                Map.of("planVersion", 2, "sourceType", "MANUAL"));
        assertEquals(HttpStatus.CONFLICT, paused.getStatusCode());
        assertEquals("PROJECT_PAUSED", paused.getBody().get("reason"));
        assertNotNull(((Map<?, ?>) paused.getBody().get("data")).get("traceId"));
    }

    private void seedConfirmedFileFacts(Long batchId) {
        jdbc.update("UPDATE lab_collection_batch SET status='REVIEW_REQUIRED', version=2 WHERE id=?", batchId);
        jdbc.update("""
                INSERT INTO lab_data_import(batch_id,object_key,original_file_name,mime_type,file_size,
                    file_sha256,status,version,uploaded_by)
                VALUES (?,'w43/test.csv','test.csv','text/csv',10,?,'CONFIRMED',2,2)
                """, batchId, "a".repeat(64));
        Long importId = jdbc.queryForObject("SELECT id FROM lab_data_import WHERE batch_id=?", Long.class, batchId);
        jdbc.update("""
                INSERT INTO lab_data_import_revision(data_import_id,import_revision_no,mapping_json,mapping_digest,
                    status,record_count,rejected_count,created_by,confirmed_by,confirmed_at)
                VALUES (?,1,'{}',?,'CONFIRMED',1,0,2,2,CURRENT_TIMESTAMP)
                """, importId, "b".repeat(64));
        Long revisionId = jdbc.queryForObject(
                "SELECT id FROM lab_data_import_revision WHERE data_import_id=?", Long.class, importId);
        jdbc.update("UPDATE lab_data_import SET current_revision_id=? WHERE id=?", revisionId, importId);
        jdbc.update("""
                INSERT INTO lab_collection_record(batch_id,data_import_revision_id,source_type,metric_code,
                    value_type,value_text,unit,observed_at,quality_status,parser_version)
                VALUES (?,?,'FILE','voltage','DECIMAL','5.02','V',CURRENT_TIMESTAMP,'GOOD','test')
                """, batchId, revisionId);
    }

    private Map<String, Object> planBody(Long ownerId, String sourceType) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "5V 输出采集");
        body.put("purpose", "记录负载变化下的输出");
        body.put("ownerId", ownerId);
        body.put("sourceType", sourceType);
        body.put("metricDefinitions", List.of(Map.of("code", "voltage", "name", "电压",
                "valueType", "DECIMAL", "unit", "V", "required", true)));
        body.put("retentionNote", "随实验长期保留");
        return body;
    }

    private Map<String, Object> planUpdateBody(int version, Long ownerId) {
        Map<String, Object> body = planBody(ownerId, "MANUAL");
        body.remove("sourceType");
        body.put("version", version);
        return body;
    }

    private String projectPlans() {
        return "/api/projects/" + projectId + "/collection-plans";
    }

    private String planBatches(Long planId) {
        return "/api/collection-plans/" + planId + "/batches";
    }

    private String batchPath(Long batchId) {
        return "/api/collection-batches/" + batchId;
    }

    private ResponseEntity<Map> login(String memberId, String password) {
        return rest.postForEntity("/usnhub/user/login",
                Map.of("memberId", memberId, "password", password), Map.class);
    }

    @SuppressWarnings("unchecked")
    private String token(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return ((Map<String, Object>) response.getBody().get("data")).get("token").toString();
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, authorized(token, null, null), Map.class);
    }

    private ResponseEntity<Map> post(String path, String token, String key, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.POST, authorized(token, key, body), Map.class);
    }

    private ResponseEntity<Map> put(String path, String token, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.PUT, authorized(token, null, body), Map.class);
    }

    private HttpEntity<?> authorized(String token, String key, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode(), String.valueOf(response.getBody()));
        return (Map<String, Object>) response.getBody().get("data");
    }

    private Long number(Object value) {
        return ((Number) value).longValue();
    }
}
