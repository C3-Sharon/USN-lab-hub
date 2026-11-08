package com.usn.labhub.user.procurement;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=${USNHUB_TEST_MYSQL_URL:jdbc:h2:mem:w45-procurement-workflow;MODE=MySQL;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${USNHUB_TEST_MYSQL_USERNAME:sa}",
                "spring.datasource.password=${USNHUB_TEST_MYSQL_PASSWORD:}",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ProcurementWorkflowApiIntegrationTest {
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private IAttendanceService attendanceService;
    private Long rootCategoryId;
    private Long childCategoryId;
    private Long componentId;

    @BeforeEach
    void setUp() {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayStatus(0);
        attendance.setTodayRecords(List.of());
        attendance.setWeekHours(0.0);
        attendance.setSemesterHours(0.0);
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);

        jdbc.update("DELETE FROM lab_procurement_approval_record");
        jdbc.update("DELETE FROM lab_procurement_audit_log");
        jdbc.update("DELETE FROM lab_procurement_receipt_line");
        jdbc.update("DELETE FROM lab_procurement_receipt");
        jdbc.update("DELETE FROM lab_procurement_order_line");
        jdbc.update("DELETE FROM lab_procurement_order");
        jdbc.update("DELETE FROM lab_request_idempotency WHERE operation LIKE 'POST:/api/procurement%'");
        jdbc.update("DELETE FROM lab_procurement_policy_forced_category");
        jdbc.update("DELETE FROM lab_procurement_policy_version");
        jdbc.update("DELETE FROM lab_component_spec_snapshot");
        jdbc.update("DELETE FROM lab_component");
        jdbc.update("DELETE FROM lab_component_manufacturer");
        jdbc.update("DELETE FROM lab_component_category");
        jdbc.update("""
                INSERT INTO lab_procurement_policy_version(
                    policy_version,currency,approval_threshold,valuable_unit_price_threshold,
                    effective_at,created_by,create_time
                ) VALUES(1,'CNY',500.00,1000.0000,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP)
                """);
        seedComponent();
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesVersionedPolicyAndFreezesForcedDescendantDecision() {
        String admin = token(login("admin", "admin123"));
        String member = token(login("20260001", "20260001"));
        assertEquals(1, ((Number) data(get("/api/procurement/policy", member)).get("policyVersion")).intValue());

        Map<String, Object> policy = policyBody(1, "600.00", "1200.0000", List.of(rootCategoryId));
        assertReason(put("/api/procurement/policy", member, policy),
                HttpStatus.FORBIDDEN, "PROCUREMENT_POLICY_DENIED");
        Map<String, Object> published = data(put("/api/procurement/policy", admin, policy));
        assertEquals(2, ((Number) published.get("policyVersion")).intValue());
        assertEquals(List.of(rootCategoryId.intValue()), published.get("forcedCategoryIds"));
        assertReason(put("/api/procurement/policy", admin, policy),
                HttpStatus.CONFLICT, "PROCUREMENT_POLICY_CHANGED");

        Map<String, Object> created = createOrder(member, "forced-policy-order", "100.00");
        Map<String, Object> submitted = data(post(command(created, "submit"), member,
                "forced-submit-01", Map.of("version", 1)));
        assertEquals("PENDING_APPROVAL", submitted.get("status"));
        assertTrue(((List<String>) submitted.get("approvalReasons")).contains("FORCED_CATEGORY"));
        String snapshot = jdbc.queryForObject(
                "SELECT approval_trigger_snapshot_json FROM lab_procurement_order WHERE id=?",
                String.class, created.get("id"));
        assertTrue(snapshot.contains("\"matchedCategoryId\":" + rootCategoryId));
    }

    @Test
    void autoApprovesExactlyOnceAndRejectsChangedReplay() {
        String member = token(login("20260001", "20260001"));
        Map<String, Object> created = createOrder(member, "auto-order-001", "20.00");
        String path = command(created, "submit");
        Map<String, Object> request = Map.of("version", 1);

        Map<String, Object> submitted = data(post(path, member, "auto-submit-0001", request));
        assertEquals("APPROVED", submitted.get("status"));
        assertEquals(List.of("EXEMPT"), submitted.get("approvalReasons"));
        assertEquals("AUTO_APPROVED", ((Map<?, ?>) submitted.get("approvalRecord")).get("decision"));

        Map<String, Object> replay = data(post(path, member, "auto-submit-0001", request));
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_procurement_approval_record WHERE order_id=?",
                Integer.class, created.get("id")));
        assertReason(post(path, member, "auto-submit-0001", Map.of("version", 2)),
                HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void enforcesManualApprovalDutySeparationAndFreezesAllocatedCost() {
        String admin = token(login("admin", "admin123"));
        String member = token(login("20260001", "20260001"));
        Map<String, Object> created = createOrder(member, "manual-order-01", "500.00");
        Map<String, Object> pending = data(post(command(created, "submit"), member,
                "manual-submit-01", Map.of("version", 1)));
        assertEquals("PENDING_APPROVAL", pending.get("status"));
        assertReason(post(command(created, "approve"), member, "self-approve-01",
                Map.of("version", 2, "comment", "本人审批")),
                HttpStatus.FORBIDDEN, "PROCUREMENT_SELF_APPROVAL");

        Map<String, Object> approved = data(post(command(created, "approve"), admin,
                "manual-approve-1", Map.of("version", 2, "comment", "预算用途确认")));
        assertEquals("APPROVED", approved.get("status"));
        assertEquals(1, ((List<?>) approved.get("approvalRecords")).size());

        Map<String, Object> orderedRequest = new LinkedHashMap<>();
        orderedRequest.put("version", 3);
        orderedRequest.put("buyerId", 2);
        orderedRequest.put("supplierName", "授权供应商");
        orderedRequest.put("platformType", "1688");
        orderedRequest.put("platformOrderNo", "W45-MANUAL-001");
        orderedRequest.put("actualPaidAmount", new BigDecimal("499.00"));
        orderedRequest.put("paymentDifferenceNote", "优惠一元");
        Map<String, Object> ordered = data(post(command(created, "mark-ordered"), admin,
                "mark-ordered-001", orderedRequest));
        assertEquals("ORDERED", ordered.get("status"));
        List<Map<String, Object>> lines = (List<Map<String, Object>>) ordered.get("lines");
        BigDecimal allocated = lines.stream().map(line -> new BigDecimal(line.get("allocatedNetAmount").toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertMoney("499.00", allocated);

        Map<String, Object> dutyOrder = createOrder(member, "duty-order-001", "500.00");
        data(post(command(dutyOrder, "submit"), member, "duty-submit-001", Map.of("version", 1)));
        data(post(command(dutyOrder, "approve"), admin, "duty-approve-01",
                Map.of("version", 2, "comment", "同意")));
        orderedRequest.put("buyerId", 1);
        orderedRequest.put("platformOrderNo", "W45-DUTY-001");
        assertReason(post(command(dutyOrder, "mark-ordered"), admin,
                "duty-ordered-01", orderedRequest), HttpStatus.CONFLICT, "PROCUREMENT_DUTY_CONFLICT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void validatesRejectCancelCompleteAndInvalidTransitions() {
        String admin = token(login("admin", "admin123"));
        String member = token(login("20260001", "20260001"));
        Map<String, Object> rejectedOrder = createOrder(member, "reject-order-01", "500.00");
        data(post(command(rejectedOrder, "submit"), member, "reject-submit-01", Map.of("version", 1)));
        assertReason(post(command(rejectedOrder, "reject"), admin, "reject-empty-001",
                Map.of("version", 2, "comment", "")), HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        Map<String, Object> rejected = data(post(command(rejectedOrder, "reject"), admin,
                "reject-valid-01", Map.of("version", 2, "comment", "预算信息不足")));
        assertEquals("REJECTED", rejected.get("status"));
        assertReason(post(command(rejectedOrder, "cancel"), member, "reject-cancel-01",
                Map.of("version", 3, "reason", "不应取消")),
                HttpStatus.CONFLICT, "PROCUREMENT_INVALID_TRANSITION");

        Map<String, Object> draft = createOrder(member, "cancel-draft-01", "20.00");
        Map<String, Object> canceled = data(post(command(draft, "cancel"), member,
                "cancel-draft-01", Map.of("version", 1, "reason", "不再采购")));
        assertEquals("CANCELED", canceled.get("status"));

        Map<String, Object> completedSeed = createOrder(admin, "complete-seed-1", "20.00");
        jdbc.update("UPDATE lab_procurement_order SET status='RECEIVED' WHERE id=?", completedSeed.get("id"));
        Map<String, Object> completed = data(post(command(completedSeed, "complete"), admin,
                "complete-order-1", Map.of("version", 1)));
        assertEquals("COMPLETED", completed.get("status"));
        assertNotNull(completed.get("completedAt"));

        Map<String, Object> partialSeed = createOrder(admin, "partial-seed-01", "20.00");
        jdbc.update("UPDATE lab_procurement_order SET status='PARTIALLY_RECEIVED' WHERE id=?", partialSeed.get("id"));
        jdbc.update("UPDATE lab_procurement_order_line SET received_quantity=0.400 WHERE order_id=?",
                partialSeed.get("id"));
        Map<String, Object> closed = data(post(command(partialSeed, "cancel-remaining"), admin,
                "cancel-remain-01", Map.of("version", 1, "reason", "供应商缺货")));
        assertEquals("COMPLETED", closed.get("status"));
        assertMoney("0.600", new BigDecimal(((List<Map<String, Object>>) closed.get("lines"))
                .get(0).get("canceledQuantity").toString()));
    }

    private void seedComponent() {
        jdbc.update("""
                INSERT INTO lab_component_category(
                    code,name,normalized_name,parent_scope_id,level,path,created_by,updated_by
                ) VALUES('PROC_ROOT','采购根类','采购根类',0,1,'',1,1)
                """);
        rootCategoryId = jdbc.queryForObject(
                "SELECT id FROM lab_component_category WHERE code='PROC_ROOT'", Long.class);
        jdbc.update("UPDATE lab_component_category SET path=? WHERE id=?", "/" + rootCategoryId + "/", rootCategoryId);
        jdbc.update("""
                INSERT INTO lab_component_category(
                    code,name,normalized_name,parent_id,parent_scope_id,level,path,created_by,updated_by
                ) VALUES('PROC_CHILD','采购子类','采购子类',?,?,2,?,1,1)
                """, rootCategoryId, rootCategoryId, "/" + rootCategoryId + "/pending/");
        childCategoryId = jdbc.queryForObject(
                "SELECT id FROM lab_component_category WHERE code='PROC_CHILD'", Long.class);
        jdbc.update("UPDATE lab_component_category SET path=? WHERE id=?",
                "/" + rootCategoryId + "/" + childCategoryId + "/", childCategoryId);
        jdbc.update("""
                INSERT INTO lab_component_manufacturer(code,name,normalized_name,created_by,updated_by)
                VALUES('PROC_WF_MFR','流程制造商','流程制造商',1,1)
                """);
        Long manufacturerId = jdbc.queryForObject(
                "SELECT id FROM lab_component_manufacturer WHERE code='PROC_WF_MFR'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component(
                    catalog_code,name,manufacturer_id,mpn,normalized_mpn,category_id,
                    package_type,base_unit,status,created_by,updated_by
                ) VALUES('CMP-WF-001','流程测试元件',?,'WF-001','WF-001',?,'0603','PCS','ACTIVE',1,1)
                """, manufacturerId, childCategoryId);
        componentId = jdbc.queryForObject(
                "SELECT id FROM lab_component WHERE catalog_code='CMP-WF-001'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component_spec_snapshot(
                    component_id,snapshot_version,source_type,source_note,raw_specs_json,
                    specifications_json,change_summary,checksum,confirmed_by
                ) VALUES(?,1,'MANUAL','测试规格','{}','{}','初始规格',?,1)
                """, componentId, "a".repeat(64));
        Long snapshotId = jdbc.queryForObject(
                "SELECT id FROM lab_component_spec_snapshot WHERE component_id=?", Long.class, componentId);
        jdbc.update("UPDATE lab_component SET current_spec_snapshot_id=?,spec_version=1 WHERE id=?",
                snapshotId, componentId);
    }

    private Map<String, Object> createOrder(String token, String key, String amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "采购流程测试单");
        body.put("shippingAmount", BigDecimal.ZERO);
        body.put("discountAmount", BigDecimal.ZERO);
        body.put("lines", List.of(Map.of("componentId", componentId,
                "orderedQuantity", new BigDecimal("1.000"), "unitPrice", new BigDecimal(amount))));
        return data(post("/api/procurement-orders", token, key, body));
    }

    private Map<String, Object> policyBody(int version, String approval, String valuable, List<Long> categories) {
        return Map.of("currentPolicyVersion", version, "approvalThreshold", new BigDecimal(approval),
                "valuableUnitPriceThreshold", new BigDecimal(valuable), "forcedCategoryIds", categories);
    }

    private String command(Map<String, Object> order, String action) {
        return "/api/procurement-orders/" + order.get("id") + "/" + action;
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
        assertEquals(reason, response.getBody().get("reason"));
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
