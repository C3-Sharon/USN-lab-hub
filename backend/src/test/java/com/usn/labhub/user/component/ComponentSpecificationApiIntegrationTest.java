package com.usn.labhub.user.component;

import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.service.IAttendanceService;
import org.junit.jupiter.api.AfterEach;
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

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:w44-component-spec;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ComponentSpecificationApiIntegrationTest {
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private IAttendanceService attendanceService;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayStatus(0);
        attendance.setTodayRecords(List.of());
        attendance.setWeekHours(0.0);
        attendance.setSemesterHours(0.0);
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);

        jdbc.update("DELETE FROM lab_component_catalog_audit_log");
        jdbc.update("DELETE FROM lab_request_idempotency WHERE operation LIKE '%COMPONENT_SPEC%'");
        jdbc.update("DELETE FROM lab_media_object");
        jdbc.update("DELETE FROM lab_component_spec_candidate");
        jdbc.update("DELETE FROM lab_component_spec_snapshot");
        jdbc.update("DELETE FROM lab_component");
        jdbc.update("DELETE FROM lab_component_manufacturer");
        jdbc.update("DELETE FROM lab_component_category");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=2 AND role_id IN (3,4)");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (executor != null) {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmsCandidateCreatesImmutableSnapshotAndReplaysExactly() {
        String admin = token(login("admin", "admin123"));
        Long componentId = createComponent(admin, "CONFIRM");
        Map<String, Object> candidate = data(post(candidatePath(componentId), admin, "candidate-create-1",
                candidateBody(1)));
        Long candidateId = number(candidate.get("id"));
        assertEquals("PENDING", candidate.get("status"));
        assertEquals(2, ((Number) candidate.get("componentVersion")).intValue());

        Map<String, Object> confirmBody = Map.of(
                "componentVersion", 2,
                "candidateVersion", 1,
                "specifications", specifications("5.5"),
                "changeSummary", "首次确认数据手册规格");
        Map<String, Object> confirmed = data(post(confirmPath(candidateId), admin, "candidate-confirm-1",
                confirmBody));
        assertEquals("CONFIRMED", confirmed.get("candidateStatus"));
        assertEquals(1, ((Number) confirmed.get("snapshotVersion")).intValue());
        assertEquals(3, ((Number) confirmed.get("componentVersion")).intValue());

        Map<String, Object> replay = data(post(confirmPath(candidateId), admin, "candidate-confirm-1",
                confirmBody));
        assertEquals(confirmed.get("snapshotId"), replay.get("snapshotId"));
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        Map<String, Object> reusedBody = new HashMap<>(confirmBody);
        reusedBody.put("changeSummary", "复用相同键但修改内容");
        ResponseEntity<Map> reused = post(confirmPath(candidateId), admin, "candidate-confirm-1", reusedBody);
        assertEquals(HttpStatus.CONFLICT, reused.getStatusCode());
        assertEquals("IDEMPOTENCY_KEY_REUSED", reused.getBody().get("reason"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lab_component_spec_snapshot", Integer.class));

        Map<String, Object> snapshot = data(get("/api/component-spec-snapshots/" + confirmed.get("snapshotId"),
                admin));
        assertEquals(64, snapshot.get("checksum").toString().length());
        assertEquals("5.5", ((Map<String, Object>) ((List<?>) snapshot.get("specifications")).get(0))
                .get("normalizedValue").toString());
        assertEquals(3, jdbc.queryForObject("SELECT version FROM lab_component WHERE id=?",
                Integer.class, componentId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsSequentialSnapshotsAndLimitsMemberToCurrentActiveVersion() {
        String admin = token(login("admin", "admin123"));
        String member = token(login("20260001", "20260001"));
        Long componentId = createComponent(admin, "MANUAL");

        Map<String, Object> first = data(post(manualPath(componentId), admin, "manual-snapshot-1",
                manualBody(1, "3.3", "首次线下确认")));
        Map<String, Object> second = data(post(manualPath(componentId), admin, "manual-snapshot-2",
                manualBody(2, "5.0", "补充新的量程确认")));
        assertEquals(1, ((Number) first.get("snapshotVersion")).intValue());
        assertEquals(2, ((Number) second.get("snapshotVersion")).intValue());
        assertNotEquals(first.get("checksum"), second.get("checksum"));

        Map<String, Object> adminPage = data(get(snapshotPath(componentId), admin));
        assertEquals(2, ((Number) adminPage.get("total")).intValue());
        assertEquals(2, ((Number) ((Map<String, Object>) ((List<?>) adminPage.get("list")).get(0))
                .get("snapshotVersion")).intValue());

        data(post("/api/components/" + componentId + "/activate", admin, "activate-manual",
                Map.of("version", 3)));
        Map<String, Object> memberPage = data(get(snapshotPath(componentId), member));
        assertEquals(1, ((Number) memberPage.get("total")).intValue());
        assertEquals(second.get("id"), ((Map<String, Object>) ((List<?>) memberPage.get("list")).get(0)).get("id"));

        ResponseEntity<Map> oldHidden = get("/api/component-spec-snapshots/" + first.get("id"), member);
        assertEquals(HttpStatus.NOT_FOUND, oldHidden.getStatusCode());
        assertEquals("COMPONENT_SPEC_SNAPSHOT_NOT_FOUND", oldHidden.getBody().get("reason"));
        assertTrue(jdbc.queryForObject(
                "SELECT specifications_json FROM lab_component_spec_snapshot WHERE id=?",
                String.class, number(first.get("id"))).contains("3.3"));
    }

    @Test
    void enforcesSourceDecisionAndRoleBoundaries() {
        String admin = token(login("admin", "admin123"));
        String member = token(login("20260001", "20260001"));
        Long componentId = createComponent(admin, "REJECT");

        ResponseEntity<Map> invalidSource = post(candidatePath(componentId), admin, "candidate-future",
                candidateBody(1, OffsetDateTime.now().plusDays(1)));
        assertEquals(HttpStatus.BAD_REQUEST, invalidSource.getStatusCode());
        assertEquals("SOURCE_METADATA_INVALID", invalidSource.getBody().get("reason"));

        Map<String, Object> invalidSpecBody = new HashMap<>(candidateBody(1));
        Map<String, Object> invalidSpec = new HashMap<>(specifications("5.5").get(0));
        invalidSpec.put("normalizedValue", "not-a-decimal");
        invalidSpecBody.put("normalizedProposal", List.of(invalidSpec));
        ResponseEntity<Map> invalidSpecification = post(candidatePath(componentId), admin,
                "candidate-invalid-spec", invalidSpecBody);
        assertEquals(HttpStatus.BAD_REQUEST, invalidSpecification.getStatusCode());
        assertEquals("COMPONENT_SPEC_INVALID", invalidSpecification.getBody().get("reason"));

        ResponseEntity<Map> memberCreate = post(candidatePath(componentId), member, "member-candidate",
                candidateBody(1));
        assertEquals(HttpStatus.FORBIDDEN, memberCreate.getStatusCode());
        assertEquals("COMPONENT_OPERATION_DENIED", memberCreate.getBody().get("reason"));

        Map<String, Object> candidate = data(post(candidatePath(componentId), admin, "candidate-create-2",
                candidateBody(1)));
        Long candidateId = number(candidate.get("id"));
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2,3)");
        try {
            assertEquals(candidateId, number(data(get("/api/component-spec-candidates/" + candidateId,
                    member)).get("id")));
            ResponseEntity<Map> teacherConfirm = post(confirmPath(candidateId), member, "teacher-confirm",
                    Map.of("componentVersion", 2, "candidateVersion", 1,
                            "specifications", specifications("5.5"), "changeSummary", "无权确认"));
            assertEquals(HttpStatus.FORBIDDEN, teacherConfirm.getStatusCode());
            assertEquals("SPEC_CONFIRM_DENIED", teacherConfirm.getBody().get("reason"));
        } finally {
            jdbc.update("DELETE FROM sys_user_role WHERE user_id=2 AND role_id=3");
        }

        Map<String, Object> rejected = data(post("/api/component-spec-candidates/" + candidateId + "/reject",
                admin, "candidate-reject-1", Map.of("candidateVersion", 1,
                        "decisionNote", "来源内容无法核实")));
        assertEquals("REJECTED", rejected.get("status"));
        assertEquals(2, ((Number) rejected.get("componentVersion")).intValue());
        ResponseEntity<Map> decided = post("/api/component-spec-candidates/" + candidateId + "/reject",
                admin, "candidate-reject-2", Map.of("candidateVersion", 1));
        assertEquals(HttpStatus.CONFLICT, decided.getStatusCode());
        assertEquals("SPEC_CANDIDATE_ALREADY_DECIDED", decided.getBody().get("reason"));
    }

    @Test
    void allowsOnlyOneConcurrentConfirmationWithDifferentKeys() throws Exception {
        String admin = token(login("admin", "admin123"));
        Long componentId = createComponent(admin, "RACE");
        Long candidateId = number(data(post(candidatePath(componentId), admin, "candidate-race-create",
                candidateBody(1))).get("id"));
        Map<String, Object> body = Map.of(
                "componentVersion", 2,
                "candidateVersion", 1,
                "specifications", specifications("5.5"),
                "changeSummary", "并发确认测试");

        executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<ResponseEntity<Map>> first = executor.submit(() -> concurrentConfirm(
                candidateId, admin, "race-confirm-a", body, ready, start));
        Future<ResponseEntity<Map>> second = executor.submit(() -> concurrentConfirm(
                candidateId, admin, "race-confirm-b", body, ready, start));
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        ResponseEntity<Map> firstResult = first.get(10, TimeUnit.SECONDS);
        ResponseEntity<Map> secondResult = second.get(10, TimeUnit.SECONDS);

        long success = List.of(firstResult, secondResult).stream()
                .filter(result -> result.getStatusCode() == HttpStatus.OK).count();
        long conflict = List.of(firstResult, secondResult).stream()
                .filter(result -> result.getStatusCode() == HttpStatus.CONFLICT).count();
        assertEquals(1, success);
        assertEquals(1, conflict);
        ResponseEntity<Map> conflictResult = firstResult.getStatusCode() == HttpStatus.CONFLICT
                ? firstResult : secondResult;
        assertTrue(List.of("VERSION_CONFLICT", "SPEC_CANDIDATE_ALREADY_DECIDED")
                .contains(conflictResult.getBody().get("reason")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lab_component_spec_snapshot", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT spec_version FROM lab_component WHERE id=?",
                Integer.class, componentId));
    }

    private ResponseEntity<Map> concurrentConfirm(Long candidateId, String token, String key,
                                                   Map<String, Object> body, CountDownLatch ready,
                                                   CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return post(confirmPath(candidateId), token, key, body);
    }

    private Long createComponent(String token, String suffix) {
        Map<String, Object> category = data(post("/api/component-categories", token, "spec-cat-" + suffix,
                Map.of("code", "SPEC_" + suffix, "name", "规格分类" + suffix, "sortOrder", 1)));
        Map<String, Object> manufacturer = data(post("/api/component-manufacturers", token,
                "spec-mfr-" + suffix, Map.of("code", "MFR_" + suffix, "name", "规格厂商" + suffix)));
        Map<String, Object> component = data(post("/api/components", token, "spec-component-" + suffix,
                Map.of("name", "规格测试元器件", "manufacturerId", manufacturer.get("id"),
                        "mpn", "MPN-" + suffix, "categoryId", category.get("id"),
                        "packageType", "SOT-23-5", "baseUnit", "PCS")));
        return number(component.get("id"));
    }

    private Map<String, Object> candidateBody(int componentVersion) {
        return candidateBody(componentVersion, OffsetDateTime.now().minusDays(1));
    }

    private Map<String, Object> candidateBody(int componentVersion, OffsetDateTime capturedAt) {
        Map<String, Object> body = new HashMap<>();
        body.put("componentVersion", componentVersion);
        body.put("sourceType", "MANUFACTURER_DATASHEET");
        body.put("sourceUrl", "https://example.com/device.pdf");
        body.put("sourceTitle", "Device datasheet");
        body.put("capturedAt", capturedAt.toString());
        body.put("rawSpecs", List.of(Map.of("name", "Supply voltage", "value", "1.8 V to 5.5 V", "unit", "V")));
        body.put("normalizedProposal", specifications("5.5"));
        return body;
    }

    private Map<String, Object> manualBody(int version, String value, String summary) {
        Map<String, Object> body = new HashMap<>();
        body.put("componentVersion", version);
        body.put("sourceType", "MANUAL");
        body.put("sourceNote", "依据实验室标签和老师确认录入");
        body.put("rawSpecs", List.of(Map.of("name", "额定电压", "value", value + " V", "unit", "V")));
        body.put("specifications", specifications(value));
        body.put("changeSummary", summary);
        return body;
    }

    private List<Map<String, Object>> specifications(String value) {
        return List.of(Map.of(
                "specCode", "supply_voltage_max",
                "label", "最大供电电压",
                "valueType", "DECIMAL",
                "rawValue", value + " V",
                "normalizedValue", new BigDecimal(value),
                "unit", "V",
                "sortOrder", 10));
    }

    private String candidatePath(Long componentId) {
        return "/api/components/" + componentId + "/spec-candidates";
    }

    private String confirmPath(Long candidateId) {
        return "/api/component-spec-candidates/" + candidateId + "/confirm";
    }

    private String manualPath(Long componentId) {
        return "/api/components/" + componentId + "/spec-snapshots/manual";
    }

    private String snapshotPath(Long componentId) {
        return "/api/components/" + componentId + "/spec-snapshots";
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
