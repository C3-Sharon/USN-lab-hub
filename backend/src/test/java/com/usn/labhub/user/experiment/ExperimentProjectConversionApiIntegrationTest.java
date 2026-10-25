package com.usn.labhub.user.experiment;

import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.mapper.ProjectMapper;
import com.usn.labhub.user.service.IAttendanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:w43-project-conversion;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ExperimentProjectConversionApiIntegrationTest {
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private IAttendanceService attendanceService;
    @SpyBean
    private ProjectMapper projectMapper;

    private Long templateId;
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
        jdbc.update("DELETE FROM lab_experiment_revision");
        jdbc.update("DELETE FROM lab_experiment_record");
        jdbc.update("DELETE FROM lab_project_member WHERE project_id IN "
                + "(SELECT id FROM lab_project WHERE project_code LIKE 'W43-CONV-%')");
        jdbc.update("DELETE FROM lab_project WHERE project_code LIKE 'W43-CONV-%'");
        jdbc.update("DELETE FROM lab_project_member WHERE project_id=1 AND user_id=2");

        jdbc.update("INSERT INTO lab_experiment_template(title, scope, created_by) "
                + "VALUES ('W43 conversion template', 'SYSTEM', 1)");
        templateId = jdbc.queryForObject("SELECT MAX(id) FROM lab_experiment_template", Long.class);
        jdbc.update("""
                INSERT INTO lab_experiment_template_version(
                    template_id, version, version_code, status, draft_slot,
                    schema_json, field_count, created_by, published_by, published_at
                ) VALUES (?, '1.0.0', 10000, 'PUBLISHED', NULL,
                    '{"schemaVersion":"1.0","type":"object","properties":{}}',
                    0, 1, 1, CURRENT_TIMESTAMP)
                """, templateId);
        templateVersionId = jdbc.queryForObject(
                "SELECT MAX(id) FROM lab_experiment_template_version", Long.class);
        learningUnitId = jdbc.queryForObject("SELECT MIN(id) FROM lab_learning_unit", Long.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void authorConvertsExactlyOnceAndProjectDetailKeepsImmutableSource() {
        SeededRecord source = seedRecord("W43-ER-CONV-1", 2L, null, "PUBLISHED", 8);
        String member = token(login("20260001", "20260001"));
        Map<String, Object> request = conversionBody(8, "W43-CONV-ONE");

        Map<String, Object> created = data(post(source.recordId(), member, "convert-one", request));
        assertEquals("PREPARING", created.get("status"));
        assertEquals("OWNER", created.get("myRole"));
        assertEquals(1, created.get("memberCount"));
        assertEquals(source.recordId(), number(created.get("sourceExperimentRecordId")));
        assertEquals(source.revisionId(), number(created.get("sourcePublishedRevisionId")));
        Long projectId = number(created.get("id"));

        Map<String, Object> replay = data(post(source.recordId(), member, "convert-one", request));
        assertEquals(projectId, number(replay.get("id")));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_project WHERE source_experiment_record_id=?", Integer.class,
                source.recordId()));

        Map<String, Object> detail = data(get("/api/projects/" + projectId, member));
        assertEquals(source.recordId(), number(detail.get("sourceExperimentRecordId")));
        assertEquals(source.revisionId(), number(detail.get("sourcePublishedRevisionId")));
        assertEquals(1, ((List<Map<String, Object>>) detail.get("members")).size());

        ResponseEntity<Map> repeated = post(source.recordId(), member, "convert-other", request);
        assertEquals(HttpStatus.CONFLICT, repeated.getStatusCode());
        assertEquals("PROJECT_ALREADY_CONVERTED", repeated.getBody().get("reason"));
    }

    @Test
    void rejectsUnpublishedStaleAndUnauthorizedConversion() {
        String member = token(login("20260001", "20260001"));

        SeededRecord draft = seedRecord("W43-ER-DRAFT", 2L, null, "DRAFT", 1);
        ResponseEntity<Map> unpublished = post(draft.recordId(), member, "convert-draft",
                conversionBody(1, "W43-CONV-DRAFT"));
        assertEquals(HttpStatus.CONFLICT, unpublished.getStatusCode());
        assertEquals("EXPERIMENT_INVALID_TRANSITION", unpublished.getBody().get("reason"));

        SeededRecord published = seedRecord("W43-ER-STALE", 2L, null, "PUBLISHED", 4);
        ResponseEntity<Map> stale = post(published.recordId(), member, "convert-stale",
                conversionBody(3, "W43-CONV-STALE"));
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());
        assertEquals("VERSION_CONFLICT", stale.getBody().get("reason"));

        jdbc.update("INSERT INTO lab_project_member(project_id,user_id,project_role,joined_at) "
                + "VALUES (1,2,'OBSERVER',CURRENT_TIMESTAMP)");
        SeededRecord observed = seedRecord("W43-ER-OBSERVED", 1L, 1L, "PUBLISHED", 2);
        ResponseEntity<Map> denied = post(observed.recordId(), member, "convert-observed",
                conversionBody(2, "W43-CONV-DENIED"));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("PROJECT_CONVERSION_DENIED", denied.getBody().get("reason"));
    }

    @Test
    void duplicateProjectCodeRollsBackOwnerAndExperimentLink() {
        SeededRecord source = seedRecord("W43-ER-ROLLBACK", 2L, null, "PUBLISHED", 5);
        jdbc.update("""
                INSERT INTO lab_project(project_code,project_name,owner_id,status,public_visible,category)
                VALUES ('W43-CONV-DUP','existing conversion code',1,'PREPARING',0,'hardware_project')
                """);
        Long existingProjectId = jdbc.queryForObject(
                "SELECT id FROM lab_project WHERE project_code='W43-CONV-DUP'", Long.class);
        String member = token(login("20260001", "20260001"));

        ResponseEntity<Map> response = post(source.recordId(), member, "convert-rollback",
                conversionBody(5, "W43-CONV-DUP"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("PROJECT_CODE_DUPLICATE", response.getBody().get("reason"));
        assertNull(jdbc.queryForObject("SELECT converted_project_id FROM lab_experiment_record WHERE id=?",
                Long.class, source.recordId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_project_member WHERE project_id=? AND user_id=2", Integer.class,
                existingProjectId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_request_idempotency WHERE operation=?", Integer.class,
                "CONVERT_EXPERIMENT:" + source.recordId()));
    }

    @Test
    void ownerInsertFailureRollsBackCreatedProjectAndExperimentLink() {
        SeededRecord source = seedRecord("W43-ER-OWNER-FAIL", 2L, null, "PUBLISHED", 6);
        doThrow(new IllegalStateException("forced owner insert failure"))
                .when(projectMapper).insertMember(anyLong(), eq(2L), eq("OWNER"), any(LocalDateTime.class));
        String member = token(login("20260001", "20260001"));

        ResponseEntity<Map> response = post(source.recordId(), member, "convert-owner-fail",
                conversionBody(6, "W43-CONV-OWNER-FAIL"));

        assertEquals(500, response.getBody().get("code"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_project WHERE project_code='W43-CONV-OWNER-FAIL'", Integer.class));
        assertNull(jdbc.queryForObject("SELECT converted_project_id FROM lab_experiment_record WHERE id=?",
                Long.class, source.recordId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_request_idempotency WHERE operation=?", Integer.class,
                "CONVERT_EXPERIMENT:" + source.recordId()));
    }

    private SeededRecord seedRecord(String code, Long authorId, Long projectId, String status, int version) {
        jdbc.update("""
                INSERT INTO lab_experiment_record(
                    record_code,title,author_id,project_id,learning_unit_id,template_id,
                    template_version_id,status,version)
                VALUES (?,?,?,?,?,?,?, ?,?)
                """, code, code + " title", authorId, projectId,
                projectId == null ? learningUnitId : null, templateId, templateVersionId, status, version);
        Long recordId = jdbc.queryForObject(
                "SELECT id FROM lab_experiment_record WHERE record_code=?", Long.class, code);
        String revisionStatus = "PUBLISHED".equals(status) ? "PUBLISHED" : "EDITABLE";
        jdbc.update("""
                INSERT INTO lab_experiment_revision(
                    record_id,revision_no,status,content_json,created_by,published_by,published_at,version)
                VALUES (?,1,?,'{}',?,?,CASE WHEN ?='PUBLISHED' THEN CURRENT_TIMESTAMP ELSE NULL END,1)
                """, recordId, revisionStatus, authorId,
                "PUBLISHED".equals(revisionStatus) ? 1L : null, revisionStatus);
        Long revisionId = jdbc.queryForObject(
                "SELECT id FROM lab_experiment_revision WHERE record_id=?", Long.class, recordId);
        jdbc.update("UPDATE lab_experiment_record SET current_revision_id=?, published_revision_id=? WHERE id=?",
                revisionId, "PUBLISHED".equals(status) ? revisionId : null, recordId);
        return new SeededRecord(recordId, revisionId);
    }

    private Map<String, Object> conversionBody(int version, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recordVersion", version);
        body.put("code", code);
        body.put("name", "实验转换项目");
        body.put("summary", "由已发布实验形成的项目草案");
        body.put("category", "hardware_project");
        return body;
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

    private ResponseEntity<Map> post(Long recordId, String token, String key, Map<String, Object> body) {
        return rest.exchange("/api/experiment-records/" + recordId + "/project-draft", HttpMethod.POST,
                authorized(token, key, body), Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, authorized(token, null, null), Map.class);
    }

    private HttpEntity<?> authorized(String token, String key, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return new HttpEntity<>(body, headers);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return (Map<String, Object>) response.getBody().get("data");
    }

    private Long number(Object value) {
        return ((Number) value).longValue();
    }

    private record SeededRecord(Long recordId, Long revisionId) {
    }
}
