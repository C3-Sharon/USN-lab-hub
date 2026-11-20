package com.usn.labhub.user.inventory;

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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=${USNHUB_TEST_MYSQL_URL:jdbc:h2:mem:w46-inventory-issue;MODE=MySQL;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${USNHUB_TEST_MYSQL_USERNAME:sa}",
                "spring.datasource.password=${USNHUB_TEST_MYSQL_PASSWORD:}",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class InventoryIssueApiIntegrationTest {
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private IAttendanceService attendanceService;
    private Long componentId;

    @BeforeEach
    void setUp() {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayStatus(0);
        attendance.setTodayRecords(List.of());
        attendance.setWeekHours(0.0);
        attendance.setSemesterHours(0.0);
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);

        jdbc.update("DELETE FROM lab_procurement_audit_log");
        jdbc.update("DELETE FROM lab_inventory_issue_policy_forced_category");
        jdbc.update("DELETE FROM lab_inventory_issue_policy_component_override");
        jdbc.update("DELETE FROM lab_inventory_issue_approval_record");
        jdbc.update("DELETE FROM lab_inventory_reservation_allocation");
        jdbc.update("DELETE FROM lab_inventory_reservation");
        jdbc.update("DELETE FROM lab_inventory_fulfillment_line");
        jdbc.update("DELETE FROM lab_inventory_fulfillment");
        jdbc.update("DELETE FROM lab_inventory_return_line");
        jdbc.update("DELETE FROM lab_inventory_return");
        jdbc.update("DELETE FROM lab_inventory_write_off_command");
        jdbc.update("DELETE FROM lab_inventory_issue_line");
        jdbc.update("DELETE FROM lab_inventory_issue_request");
        jdbc.update("DELETE FROM lab_inventory_issue_policy_version");
        jdbc.update("DELETE FROM lab_request_idempotency WHERE operation LIKE '%inventory-issue%'");
        jdbc.update("DELETE FROM lab_component_spec_snapshot");
        jdbc.update("DELETE FROM lab_component");
        jdbc.update("DELETE FROM lab_component_manufacturer");
        jdbc.update("DELETE FROM lab_component_category");
        seedComponent();
    }

    @Test
    void returnsDefaultPolicyThenPublishesAndProtectsVersion() {
        String member = token(login("20260001", "20260001"));
        String admin = token(login("admin", "admin123"));

        Map<String, Object> current = data(get("/api/inventory/issue-policy", member));
        assertEquals(0, ((Number) current.get("policyVersion")).intValue());
        assertMoney("100.00", current.get("directAmountThreshold"));
        assertEquals(0, ((List<?>) current.get("componentOverrides")).size());

        assertReason(put("/api/inventory/issue-policy", member, policyBody(0)),
                HttpStatus.FORBIDDEN, "INVENTORY_POLICY_DENIED");

        Map<String, Object> published = data(put("/api/inventory/issue-policy", admin, policyBody(0)));
        assertEquals(1, ((Number) published.get("policyVersion")).intValue());
        assertEquals(1, ((List<?>) published.get("componentOverrides")).size());
        assertEquals(1, ((List<?>) published.get("forcedCategoryIds")).size());

        assertReason(put("/api/inventory/issue-policy", admin, policyBody(0)),
                HttpStatus.CONFLICT, "VERSION_CONFLICT");
        assertEquals(1, ((Number) data(get("/api/inventory/issue-policy", admin))
                .get("policyVersion")).intValue());
    }

    @Test
    void rejectsDuplicateAndInactiveOverrideComponents() {
        String admin = token(login("admin", "admin123"));
        Map<String, Object> body = policyBody(0);
        Map<String, Object> first = override(componentId, "3.000");
        body.put("componentOverrides", List.of(first, override(componentId, "5.000")));
        assertReason(put("/api/inventory/issue-policy", admin, body),
                HttpStatus.BAD_REQUEST, "ISSUE_POLICY_OVERRIDE_INVALID");
    }

    @Test
    void createsReplaysListsAndUpdatesDraft() {
        String member = token(login("20260001", "20260001"));
        Map<String, Object> body = requestBody();
        String key = "k".repeat(128);

        Map<String, Object> created = data(post("/api/inventory-issue-requests", member, key, body));
        assertTrue(created.get("requestNo").toString().matches("ISS-\\d{8}-\\d{6}"));
        assertEquals("DRAFT", created.get("status"));
        assertEquals(1, ((Number) created.get("version")).intValue());
        assertEquals(1, ((List<?>) created.get("lines")).size());

        Map<String, Object> replay = data(post("/api/inventory-issue-requests", member, key, body));
        assertEquals(created.get("id"), replay.get("id"));
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lab_inventory_issue_request", Integer.class));

        Map<String, Object> page = data(get("/api/inventory-issue-requests?keyword=气象站", member));
        assertEquals(1, ((Number) page.get("total")).intValue());
        assertEquals(1, ((List<?>) page.get("items")).size());

        Map<String, Object> update = requestBody();
        update.put("title", "更新后的领用草稿");
        update.put("version", 1);
        Map<String, Object> updated = data(put("/api/inventory-issue-requests/" + created.get("id"),
                member, update));
        assertEquals("更新后的领用草稿", updated.get("title"));
        assertEquals(2, ((Number) updated.get("version")).intValue());

        ResponseEntity<Map> stale = put("/api/inventory-issue-requests/" + created.get("id"),
                member, update);
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());
        assertEquals("VERSION_CONFLICT", stale.getBody().get("reason"));
        assertNotNull(((Map<?, ?>) stale.getBody().get("data")).get("traceId"));
    }

    @Test
    void enforcesRequestValidation() {
        String member = token(login("20260001", "20260001"));

        Map<String, Object> noLines = requestBody();
        noLines.put("lines", List.of());
        assertReason(post("/api/inventory-issue-requests", member, "no-lines-00001", noLines),
                HttpStatus.BAD_REQUEST, "ISSUE_LINES_REQUIRED");

        Map<String, Object> duplicate = requestBody();
        duplicate.put("lines", List.of(line(componentId, "1.000"), line(componentId, "2.000")));
        assertReason(post("/api/inventory-issue-requests", member, "dup-component-1", duplicate),
                HttpStatus.BAD_REQUEST, "ISSUE_COMPONENT_INVALID");

        Map<String, Object> badQuantity = requestBody();
        badQuantity.put("lines", List.of(line(componentId, "1.0001")));
        assertReason(post("/api/inventory-issue-requests", member, "bad-quantity-01", badQuantity),
                HttpStatus.BAD_REQUEST, "ISSUE_QUANTITY_INVALID");

        assertReason(post("/api/inventory-issue-requests", member, "short", requestBody()),
                HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
    }

    @Test
    void scopesVisibilityAndMasksCost() {
        String admin = token(login("admin", "admin123"));
        String member = token(login("20260001", "20260001"));

        Map<String, Object> created = data(post("/api/inventory-issue-requests", member,
                "visibility-0001", requestBody()));
        long requestId = ((Number) created.get("id")).longValue();
        Object firstLine = ((List<?>) created.get("lines")).get(0);
        long lineId = ((Number) ((Map<?, ?>) firstLine).get("id")).longValue();

        jdbc.update("UPDATE lab_inventory_issue_request SET estimated_amount=123.45 WHERE id=?", requestId);
        jdbc.update("UPDATE lab_inventory_issue_line SET estimated_amount=67.89 WHERE id=?", lineId);

        Map<String, Object> adminView = data(get("/api/inventory-issue-requests/" + requestId, admin));
        assertMoney("123.45", adminView.get("estimatedAmount"));
        assertMoney("67.89", ((Map<?, ?>) ((List<?>) adminView.get("lines")).get(0)).get("estimatedAmount"));

        Map<String, Object> memberView = data(get("/api/inventory-issue-requests/" + requestId, member));
        assertNull(memberView.get("estimatedAmount"));
        assertNull(((Map<?, ?>) ((List<?>) memberView.get("lines")).get(0)).get("estimatedAmount"));
    }

    private Map<String, Object> policyBody(int baseVersion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("basePolicyVersion", baseVersion);
        body.put("directAmountThreshold", new BigDecimal("100.00"));
        body.put("valuableUnitCostThreshold", new BigDecimal("500.0000"));
        body.put("defaultDirectQuantityLimit", new BigDecimal("10.000"));
        body.put("lowStockRatio", new BigDecimal("20.00"));
        body.put("reservationTtlHours", 72);
        Long categoryId = jdbc.queryForObject("SELECT id FROM lab_component_category", Long.class);
        body.put("forcedCategoryIds", List.of(categoryId));
        body.put("componentOverrides", List.of(override(componentId, "3.000")));
        return body;
    }

    private Map<String, Object> override(Long componentId, String limit) {
        return Map.of("componentId", componentId, "directQuantityLimit", new BigDecimal(limit));
    }

    private Map<String, Object> requestBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "气象站原型焊接领料");
        body.put("purpose", "焊接第一版传感器节点");
        body.put("lines", List.of(line(componentId, "5.000")));
        return body;
    }

    private Map<String, Object> line(Long componentId, String quantity) {
        return Map.of("componentId", componentId, "requestedQuantity", new BigDecimal(quantity));
    }

    private void seedComponent() {
        jdbc.update("""
                INSERT INTO lab_component_category(
                    code,name,normalized_name,parent_scope_id,level,path,created_by,updated_by
                ) VALUES('ISS_API','领用测试','领用测试',0,1,'/ISS_API/',1,1)
                """);
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM lab_component_category WHERE code='ISS_API'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component_manufacturer(code,name,normalized_name,created_by,updated_by)
                VALUES('ISS_MFR','领用制造商','领用制造商',1,1)
                """);
        Long manufacturerId = jdbc.queryForObject(
                "SELECT id FROM lab_component_manufacturer WHERE code='ISS_MFR'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component(
                    catalog_code,name,manufacturer_id,mpn,normalized_mpn,category_id,
                    package_type,base_unit,status,created_by,updated_by
                ) VALUES('CMP-ISS-001','领用测试电阻',?,'ISS-R-001','ISS-R-001',?,'0603','PCS','ACTIVE',1,1)
                """, manufacturerId, categoryId);
        componentId = jdbc.queryForObject(
                "SELECT id FROM lab_component WHERE catalog_code='CMP-ISS-001'", Long.class);
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

    private void assertReason(ResponseEntity<Map> response, HttpStatus status, String reason) {
        assertEquals(status, response.getStatusCode(), String.valueOf(response.getBody()));
        if (reason != null) {
            assertEquals(reason, response.getBody().get("reason"));
        }
    }

    private void assertMoney(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));
    }
}
