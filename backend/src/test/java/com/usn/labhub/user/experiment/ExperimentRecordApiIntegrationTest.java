package com.usn.labhub.user.experiment;

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
                "spring.datasource.url=jdbc:h2:mem:w43-experiment-records;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ExperimentRecordApiIntegrationTest {
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private IAttendanceService attendanceService;

    private Long templateVersionId;
    private Long learningUnitId;

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
        jdbc.update("DELETE FROM lab_experiment_feedback");
        jdbc.update("DELETE FROM lab_experiment_revision");
        jdbc.update("DELETE FROM lab_experiment_record");
        jdbc.update("UPDATE lab_learning_unit SET template_id=NULL, template_version_id=NULL");
        jdbc.update("DELETE FROM lab_experiment_template_version");
        jdbc.update("DELETE FROM lab_experiment_template");

        jdbc.update("""
                INSERT INTO lab_experiment_template(title, scope, created_by)
                VALUES ('W43 system template', 'SYSTEM', 1)
                """);
        Long templateId = jdbc.queryForObject(
                "SELECT id FROM lab_experiment_template WHERE title='W43 system template'", Long.class);
        String schema = """
                {"schemaVersion":"1.0","type":"object","properties":{
                  "voltage":{"type":"number","title":"电压","unit":"V","displayOrder":1,
                             "minimum":0,"maximum":24,"widget":"input-number"}
                },"required":["voltage"]}
                """;
        jdbc.update("""
                INSERT INTO lab_experiment_template_version(
                    template_id, version, version_code, status, draft_slot,
                    schema_json, field_count, created_by, published_by, published_at
                ) VALUES (?, '1.0.0', 10000, 'PUBLISHED', NULL, ?, 1, 1, 1, CURRENT_TIMESTAMP)
                """, templateId, schema);
        templateVersionId = jdbc.queryForObject(
                "SELECT id FROM lab_experiment_template_version WHERE template_id=?", Long.class, templateId);
        learningUnitId = jdbc.queryForObject("SELECT MIN(id) FROM lab_learning_unit", Long.class);
        jdbc.update("UPDATE lab_learning_unit SET template_id=?, template_version_id=? WHERE id=?",
                templateId, templateVersionId, learningUnitId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void completesRevisionFeedbackPublishAndReviseFlowWithIdempotentReplay() {
        String member = token(login("20260001", "20260001"));
        String admin = token(login("admin", "admin123"));
        Map<String, Object> createBody = createBody(5.12);

        ResponseEntity<Map> created = post("/api/experiment-records", member, "create-1", createBody);
        assertEquals(HttpStatus.OK, created.getStatusCode());
        Map<String, Object> record = data(created);
        Long recordId = number(record.get("id"));
        Map<String, Object> revision = (Map<String, Object>) record.get("currentRevision");
        Long revisionId = number(revision.get("id"));
        assertEquals("DRAFT", record.get("status"));

        Map<String, Object> page = data(get("/api/experiment-records?page=1&pageSize=20", member));
        Map<String, Object> summary = ((List<Map<String, Object>>) page.get("list")).get(0);
        assertEquals("W43 system template", summary.get("templateName"));
        assertEquals(1, summary.get("currentRevisionNo"));
        assertEquals("EDITABLE", summary.get("currentRevisionStatus"));

        Map<String, Object> initialDetail = data(get(path(recordId), member));
        Map<String, Object> detailRevision = (Map<String, Object>) initialDetail.get("currentRevision");
        assertNotNull(initialDetail.get("schemaJson"));
        assertEquals(5.12, ((Number) ((Map<String, Object>) detailRevision.get("contentJson")).get("voltage")).doubleValue());

        Map<String, Object> replay = data(post("/api/experiment-records", member, "create-1", createBody));
        assertEquals(recordId, number(replay.get("id")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lab_experiment_record", Integer.class));

        Map<String, Object> changedCreate = createBody(5.12);
        changedCreate.put("title", "不同请求");
        ResponseEntity<Map> reused = post("/api/experiment-records", member, "create-1", changedCreate);
        assertEquals(HttpStatus.CONFLICT, reused.getStatusCode());
        assertEquals("IDEMPOTENCY_KEY_REUSED", reused.getBody().get("reason"));

        Map<String, Object> update = revisionBody(5.08, 1, 1, null);
        Map<String, Object> saved = data(put(path(recordId) + "/revisions/" + revisionId, member, update));
        assertEquals(2, saved.get("recordVersion"));
        assertEquals(2, saved.get("revisionVersion"));

        Map<String, Object> submitted = data(post(path(recordId) + "/submit", member, "submit-1",
                Map.of("recordVersion", 2, "revisionId", revisionId, "revisionVersion", 2)));
        assertEquals("IN_REVIEW", submitted.get("status"));
        Map<String, Object> submitReplay = data(post(path(recordId) + "/submit", member, "submit-1",
                Map.of("recordVersion", 2, "revisionId", revisionId, "revisionVersion", 2)));
        assertEquals(3, submitReplay.get("recordVersion"));

        ResponseEntity<Map> adminEdit = put(path(recordId) + "/revisions/" + revisionId, admin,
                revisionBody(5.2, 3, 3, null));
        assertEquals(HttpStatus.FORBIDDEN, adminEdit.getStatusCode());
        assertEquals("EXPERIMENT_OPERATION_DENIED", adminEdit.getBody().get("reason"));

        ResponseEntity<Map> selfReview = post(path(recordId) + "/feedback", member, "self-review",
                feedback(3, revisionId, "APPROVAL", "自审"));
        assertEquals(HttpStatus.FORBIDDEN, selfReview.getStatusCode());
        assertEquals("FEEDBACK_OPERATION_DENIED", selfReview.getBody().get("reason"));

        Map<String, Object> requested = data(post(path(recordId) + "/feedback", admin, "changes-1",
                feedback(3, revisionId, "CHANGE_REQUEST", "补充第二次测量")));
        assertEquals("CHANGES_REQUESTED", requested.get("status"));
        Map<String, Object> firstFeedbackPage = data(get(path(recordId) + "/feedback?page=1&pageSize=20", member));
        Map<String, Object> feedbackItem = ((List<Map<String, Object>>) firstFeedbackPage.get("list")).get(0);
        assertEquals(1, feedbackItem.get("revisionNo"));
        assertEquals("changes-1", feedbackItem.get("requestId"));
        Map<String, Object> next = (Map<String, Object>) requested.get("newRevision");
        Long nextRevisionId = number(next.get("id"));

        Map<String, Object> revised = data(put(path(recordId) + "/revisions/" + nextRevisionId, member,
                revisionBody(5.06, 4, 1, "补充第二次测量")));
        assertEquals(5, revised.get("recordVersion"));
        data(post(path(recordId) + "/submit", member, "submit-2",
                Map.of("recordVersion", 5, "revisionId", nextRevisionId, "revisionVersion", 2)));
        Map<String, Object> approved = data(post(path(recordId) + "/feedback", admin, "approve-1",
                feedback(6, nextRevisionId, "APPROVAL", "复审通过")));
        assertEquals("APPROVED", approved.get("status"));
        Map<String, Object> published = data(post(path(recordId) + "/publish", admin, "publish-1",
                Map.of("recordVersion", 7, "revisionId", nextRevisionId)));
        assertEquals("PUBLISHED", published.get("status"));

        Map<String, Object> detail = data(get(path(recordId), member));
        assertEquals(nextRevisionId, number(detail.get("publishedRevisionId")));
        Map<String, Object> history = data(get(path(recordId) + "/revisions", member));
        assertEquals(2, history.get("total"));
        Map<String, Object> feedbackPage = data(get(path(recordId) + "/feedback", member));
        assertEquals(2, feedbackPage.get("total"));

        Map<String, Object> revising = data(post(path(recordId) + "/revisions", member, "revise-1",
                Map.of("recordVersion", 8, "changeSummary", "增加负载测试")));
        assertEquals("REVISING", revising.get("status"));
        assertEquals(nextRevisionId, number(data(get(path(recordId), member)).get("publishedRevisionId")));
        assertEquals(7, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_experiment_audit_log", Integer.class));
    }

    @Test
    void rejectsInvalidContentAndStaleVersionWithoutOverwriting() {
        String member = token(login("20260001", "20260001"));
        ResponseEntity<Map> invalid = post("/api/experiment-records", member, "invalid-content", createBody(30));
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        assertEquals("EXPERIMENT_CONTENT_INVALID", invalid.getBody().get("reason"));

        Map<String, Object> created = data(post("/api/experiment-records", member, "create-stale", createBody(5)));
        Long recordId = number(created.get("id"));
        Long revisionId = number(((Map<?, ?>) created.get("currentRevision")).get("id"));
        assertEquals(HttpStatus.OK, put(path(recordId) + "/revisions/" + revisionId, member,
                revisionBody(5.1, 1, 1, null)).getStatusCode());
        ResponseEntity<Map> stale = put(path(recordId) + "/revisions/" + revisionId, member,
                revisionBody(5.2, 1, 1, null));
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());
        assertEquals("VERSION_CONFLICT", stale.getBody().get("reason"));
        assertNotNull(((Map<?, ?>) stale.getBody().get("data")).get("traceId"));

        Map<String, Object> revision = data(get(path(recordId) + "/revisions/" + revisionId, member));
        assertEquals(5.1, ((Number) ((Map<?, ?>) revision.get("contentJson")).get("voltage")).doubleValue());
    }

    private Map<String, Object> createBody(double voltage) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "直流电压稳定性实验");
        body.put("summary", "验证 5V 输出稳定性");
        body.put("learningUnitId", learningUnitId);
        body.put("templateVersionId", templateVersionId);
        body.put("contentJson", Map.of("voltage", voltage));
        body.put("purpose", "验证供电稳定性");
        body.put("result", "波动小于 1%");
        return body;
    }

    private Map<String, Object> revisionBody(double voltage, int recordVersion,
                                             int revisionVersion, String changeSummary) {
        Map<String, Object> body = new HashMap<>();
        body.put("recordVersion", recordVersion);
        body.put("revisionVersion", revisionVersion);
        body.put("contentJson", Map.of("voltage", voltage));
        body.put("purpose", "验证供电稳定性");
        body.put("result", "波动小于 1%");
        body.put("changeSummary", changeSummary);
        return body;
    }

    private Map<String, Object> feedback(int recordVersion, Long revisionId, String type, String content) {
        return Map.of("recordVersion", recordVersion, "revisionId", revisionId,
                "type", type, "content", content, "visibility", "AUTHOR_AND_REVIEWERS");
    }

    private String path(Long recordId) {
        return "/api/experiment-records/" + recordId;
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
