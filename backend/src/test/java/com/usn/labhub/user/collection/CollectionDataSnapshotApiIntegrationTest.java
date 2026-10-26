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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:w43-collection-data;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false",
                "usnhub.collection.storage-root=target/test-collection-originals"
        })
class CollectionDataSnapshotApiIntegrationTest {
    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private IAttendanceService attendanceService;
    private Long projectId;
    private String token;

    @BeforeEach
    void setUp() {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayStatus(0);
        attendance.setTodayRecords(List.of());
        attendance.setWeekHours(0.0);
        attendance.setSemesterHours(0.0);
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);

        jdbc.update("DELETE FROM lab_snapshot_manual_revision");
        jdbc.update("DELETE FROM lab_collection_snapshot");
        jdbc.update("UPDATE lab_experiment_revision SET collection_snapshot_id=NULL");
        jdbc.update("DELETE FROM lab_experiment_audit_log");
        jdbc.update("DELETE FROM lab_request_idempotency");
        jdbc.update("DELETE FROM lab_collection_record");
        jdbc.update("DELETE FROM lab_manual_record_revision");
        jdbc.update("DELETE FROM lab_manual_record");
        jdbc.update("DELETE FROM lab_data_import_revision");
        jdbc.update("DELETE FROM lab_data_import");
        jdbc.update("DELETE FROM lab_collection_batch");
        jdbc.update("DELETE FROM lab_collection_plan");
        jdbc.update("DELETE FROM lab_project_member WHERE project_id IN (SELECT id FROM lab_project WHERE project_code='W43-DATA')");
        jdbc.update("DELETE FROM lab_project WHERE project_code='W43-DATA'");
        jdbc.update("INSERT INTO lab_project(project_code,project_name,owner_id,status,public_visible,category) "
                + "VALUES ('W43-DATA','W43 数据测试项目',1,'ACTIVE',0,'learning')");
        projectId = jdbc.queryForObject("SELECT id FROM lab_project WHERE project_code='W43-DATA'", Long.class);
        jdbc.update("INSERT INTO lab_project_member(project_id,user_id,project_role) VALUES (?,1,'OWNER')", projectId);
        token = token(login("admin", "admin123"));
    }

    @Test
    void importsImmutableCsvCompletesBatchAndCreatesSnapshot() {
        Long batchId = createBatch("FILE", "file");
        byte[] csv = ("observed_at,voltage,note\n2026-10-24 14:30:00.000,5.12,first\n"
                + "2026-10-24 14:31:00.000,5.08,second\n")
                .getBytes(StandardCharsets.UTF_8);
        Map<String, Object> uploaded = data(upload(batchId, "upload-file-1", "readings.csv", csv));
        assertTrue(!uploaded.toString().contains("objectKey") && !uploaded.toString().contains("test-collection-originals"));
        Long importId = number(uploaded.get("id"));
        assertEquals(2, uploaded.get("batchVersion"));
        Map<String, Object> replay = data(upload(batchId, "upload-file-1", "readings.csv", csv));
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lab_data_import", Integer.class));

        Map<String, Object> preview = data(get("/api/data-imports/" + importId + "/preview"));
        assertEquals(2, ((List<?>) preview.get("rows")).size());
        Map<String, Object> mapped = data(put("/api/data-imports/" + importId + "/mapping",
                Map.of("version", 1, "importVersion", 0, "mapping", mapping())));
        assertEquals("MAPPING_REQUIRED", mapped.get("status"));
        Map<String, Object> confirmed = data(post("/api/data-imports/" + importId + "/confirm",
                "confirm-file-1", Map.of("version", 2, "importVersion", 1, "batchVersion", 2)));
        assertEquals(4, ((Number) confirmed.get("recordCount")).intValue());
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM lab_collection_record", Integer.class));

        Map<String, Object> completed = data(post("/api/collection-batches/" + batchId + "/complete",
                "complete-file-1", Map.of("version", 3, "completionReason", "CSV 已确认")));
        assertEquals("COMPLETED", completed.get("status"));
        assertNotNull(completed.get("checksum"));
        Map<String, Object> snapshot = data(post("/api/collection-batches/" + batchId + "/snapshots",
                "snapshot-file-1", Map.of("batchVersion", 4, "snapshotType", "FULL_BATCH")));
        assertEquals(4, ((Number) snapshot.get("recordCount")).intValue());
        assertNotNull(snapshot.get("mappingDigest"));
        assertEquals(4, ((List<?>) data(get("/api/collection-batches/" + batchId + "/records?limit=100"))
                .get("list")).size());
        Map<String, Object> fileBatchDetail = data(get("/api/collection-batches/" + batchId));
        assertEquals(1, ((List<?>) fileBatchDetail.get("imports")).size());
        assertEquals(4, ((List<?>) fileBatchDetail.get("recordSamples")).size());
        assertNotNull(fileBatchDetail.get("snapshot"));

        Long revisionId = seedEditableExperimentRevision();
        Map<String, Object> bound = data(put("/api/experiment-records/" + experimentRecordId(revisionId)
                + "/collection-snapshot", Map.of("recordVersion", 1, "revisionId", revisionId,
                "revisionVersion", 1, "snapshotId", number(snapshot.get("id")), "replaceExisting", false)));
        assertEquals(2, bound.get("recordVersion"));
        Map<String, Object> same = data(put("/api/experiment-records/" + experimentRecordId(revisionId)
                + "/collection-snapshot", Map.of("recordVersion", 1, "revisionId", revisionId,
                "revisionVersion", 1, "snapshotId", number(snapshot.get("id")), "replaceExisting", false)));
        assertEquals(2, same.get("recordVersion"));
        Map<String, Object> unbound = new java.util.HashMap<>();
        unbound.put("recordVersion", 2);
        unbound.put("revisionId", revisionId);
        unbound.put("revisionVersion", 2);
        unbound.put("snapshotId", null);
        unbound.put("replaceExisting", false);
        assertEquals(null, data(put("/api/experiment-records/" + experimentRecordId(revisionId)
                + "/collection-snapshot", unbound)).get("collectionSnapshotId"));

        ResponseEntity<Map> immutable = upload(batchId, "upload-after-complete", "readings.csv", csv);
        assertEquals(HttpStatus.CONFLICT, immutable.getStatusCode());
        assertEquals("BATCH_IMMUTABLE", immutable.getBody().get("reason"));
    }

    @Test
    void appendsManualRevisionsAndFreezesCurrentRevisionSet() {
        Long batchId = createBatch("MANUAL", "manual");
        Map<String, Object> created = data(post("/api/collection-batches/" + batchId + "/manual-records",
                "manual-create-1", manualBody(5.12)));
        Long recordId = number(created.get("id"));
        assertEquals(2, created.get("batchVersion"));
        Map<String, Object> corrected = data(put("/api/manual-records/" + recordId,
                Map.of("version", 1, "batchVersion", 2, "valueType", "DECIMAL", "value", 5.08,
                        "unit", "V", "observedAt", "2026-10-24T14:31:00+08:00",
                        "method", "万用表直流档", "instrument", "UT61E+ / LAB-A-017",
                        "note", "复测", "changeReason", "修正抄录值")));
        assertEquals(2, corrected.get("revisionNo"));
        assertEquals(2, ((List<?>) data(get("/api/manual-records/" + recordId + "/revisions"))
                .get("revisions")).size());
        assertEquals(1, ((List<?>) data(get("/api/collection-batches/" + batchId + "/records"))
                .get("list")).size());

        data(post("/api/collection-batches/" + batchId + "/complete", "complete-manual-1",
                Map.of("version", 3, "completionReason", "人工记录复核完成")));
        Map<String, Object> snapshot = data(post("/api/collection-batches/" + batchId + "/snapshots",
                "snapshot-manual-1", Map.of("batchVersion", 4, "snapshotType", "FULL_BATCH")));
        assertEquals(1, ((Number) snapshot.get("recordCount")).intValue());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lab_snapshot_manual_revision", Integer.class));
        Map<String, Object> manualBatchDetail = data(get("/api/collection-batches/" + batchId));
        assertEquals(1, ((List<?>) manualBatchDetail.get("manualRecords")).size());
        assertEquals(2, ((Map<?, ?>) ((List<?>) manualBatchDetail.get("manualRecords")).get(0)).get("revisionNo"));
        Long frozenRevision = jdbc.queryForObject("SELECT manual_revision_id FROM lab_snapshot_manual_revision", Long.class);
        assertEquals(number(corrected.get("revisionId")), frozenRevision);

        ResponseEntity<Map> immutable = put("/api/manual-records/" + recordId,
                Map.of("version", 2, "batchVersion", 4, "valueType", "DECIMAL", "value", 5.07,
                        "unit", "V", "observedAt", "2026-10-24T14:32:00+08:00",
                        "method", "万用表直流档", "instrument", "UT61E+ / LAB-A-017",
                        "changeReason", "不应成功"));
        assertEquals(HttpStatus.CONFLICT, immutable.getStatusCode());
        assertEquals("BATCH_IMMUTABLE", immutable.getBody().get("reason"));
    }

    private Long createBatch(String sourceType, String suffix) {
        Map<String, Object> plan = data(post("/api/projects/" + projectId + "/collection-plans",
                "plan-" + suffix, Map.of("name", "5V " + sourceType + " 采集", "purpose", "测试",
                        "ownerId", 1, "sourceType", sourceType, "metricDefinitions", List.of(
                                Map.of("code", "voltage", "name", "电压", "valueType", "DECIMAL",
                                        "unit", "V", "required", true),
                                Map.of("code", "note", "name", "备注", "valueType", "STRING",
                                        "unit", "", "required", false)))));
        Long planId = number(plan.get("id"));
        data(post("/api/collection-plans/" + planId + "/ready", "ready-" + suffix, Map.of("version", 1)));
        return number(data(post("/api/collection-plans/" + planId + "/batches", "batch-" + suffix,
                Map.of("planVersion", 2, "sourceType", sourceType))).get("id"));
    }

    private Long seedEditableExperimentRevision() {
        jdbc.update("INSERT INTO lab_experiment_record(record_code,title,author_id,project_id,template_id,"
                + "template_version_id,status,version) VALUES ('ER-BIND-1','绑定测试',1,?,1,1,'DRAFT',1)", projectId);
        Long recordId = jdbc.queryForObject("SELECT id FROM lab_experiment_record WHERE record_code='ER-BIND-1'", Long.class);
        jdbc.update("INSERT INTO lab_experiment_revision(record_id,revision_no,status,content_json,created_by,version) "
                + "VALUES (?,1,'EDITABLE','{}',1,1)", recordId);
        Long revisionId = jdbc.queryForObject("SELECT id FROM lab_experiment_revision WHERE record_id=?", Long.class, recordId);
        jdbc.update("UPDATE lab_experiment_record SET current_revision_id=? WHERE id=?", revisionId, recordId);
        return revisionId;
    }

    private Long experimentRecordId(Long revisionId) {
        return jdbc.queryForObject("SELECT record_id FROM lab_experiment_revision WHERE id=?", Long.class, revisionId);
    }

    private Map<String, Object> mapping() {
        return Map.of("delimiter", ",", "encoding", "UTF-8", "headerRow", 1,
                "timeMapping", Map.of("sourceColumn", "observed_at", "format", "yyyy-MM-dd HH:mm:ss.SSS",
                        "timezone", "Asia/Shanghai"),
                "columns", List.of(
                        Map.of("sourceColumn", "voltage", "metricCode", "voltage",
                                "valueType", "DECIMAL", "sourceUnit", "V", "targetUnit", "V"),
                        Map.of("sourceColumn", "note", "metricCode", "note",
                                "valueType", "STRING", "sourceUnit", "", "targetUnit", "")),
                "ignoredColumns", List.of());
    }

    private Map<String, Object> manualBody(double value) {
        return Map.of("metricCode", "voltage", "valueType", "DECIMAL", "value", value, "unit", "V",
                "observedAt", "2026-10-24T14:30:00+08:00", "method", "万用表直流档",
                "instrument", "UT61E+ / LAB-A-017", "note", "室温 24℃");
    }

    private ResponseEntity<Map> upload(Long batchId, String key, String filename, byte[] content) {
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override public String getFilename() { return filename; }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType("text/csv"));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, partHeaders));
        HttpHeaders headers = headers(key, MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/collection-batches/" + batchId + "/file-imports",
                HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> login(String memberId, String password) {
        return rest.postForEntity("/usnhub/user/login", Map.of("memberId", memberId, "password", password), Map.class);
    }

    @SuppressWarnings("unchecked")
    private String token(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return ((Map<String, Object>) response.getBody().get("data")).get("token").toString();
    }

    private ResponseEntity<Map> get(String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(null, null)), Map.class);
    }

    private ResponseEntity<Map> post(String path, String key, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(key, MediaType.APPLICATION_JSON)), Map.class);
    }

    private ResponseEntity<Map> put(String path, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers(null, MediaType.APPLICATION_JSON)), Map.class);
    }

    private HttpHeaders headers(String key, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (key != null) headers.set("Idempotency-Key", key);
        if (contentType != null) headers.setContentType(contentType);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode(), String.valueOf(response.getBody()));
        return (Map<String, Object>) response.getBody().get("data");
    }

    private Long number(Object value) {
        assertNotNull(value);
        assertTrue(value instanceof Number);
        return ((Number) value).longValue();
    }
}
