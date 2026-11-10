package com.usn.labhub.user.procurement;

import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.service.IAttendanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=${USNHUB_TEST_MYSQL_URL:jdbc:h2:mem:w45-procurement-receipt;MODE=MySQL;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${USNHUB_TEST_MYSQL_USERNAME:sa}",
                "spring.datasource.password=${USNHUB_TEST_MYSQL_PASSWORD:}",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ProcurementReceiptApiIntegrationTest {
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Environment environment;
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
        jdbc.update("DELETE FROM lab_inventory_movement");
        jdbc.update("DELETE FROM lab_inventory_batch");
        jdbc.update("DELETE FROM lab_component_inventory_balance");
        jdbc.update("DELETE FROM lab_procurement_audit_log");
        jdbc.update("DELETE FROM lab_procurement_receipt_line");
        jdbc.update("DELETE FROM lab_procurement_receipt");
        jdbc.update("DELETE FROM lab_procurement_approval_record");
        jdbc.update("DELETE FROM lab_procurement_order_line");
        jdbc.update("DELETE FROM lab_procurement_order");
        jdbc.update("DELETE FROM lab_request_idempotency WHERE operation LIKE 'POST:/api/procurement%'");
        jdbc.update("DELETE FROM lab_procurement_policy_forced_category");
        jdbc.update("DELETE FROM lab_procurement_policy_version");
        jdbc.update("DELETE FROM lab_component_spec_snapshot");
        jdbc.update("DELETE FROM lab_component");
        jdbc.update("DELETE FROM lab_component_manufacturer");
        jdbc.update("DELETE FROM lab_component_category");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=2 AND role_id IN (3,4)");
        jdbc.update("INSERT IGNORE INTO sys_user_role(user_id,role_id) VALUES(2,2)");
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
    void confirmsPartialThenFullReceiptAndSupportsExactReplay() {
        String admin = token(login("admin", "admin123"));
        Map<String, Object> order = createOrdered(admin, "receipt-flow-01", "2.000");
        Map<String, Object> receipt = createReceipt(admin, order, "receipt-create-01", "1.000");

        Map<String, Object> confirmed = data(post(receiptPath(receipt, "confirm"), admin,
                "receipt-confirm-01", command(receipt)));
        assertEquals("PARTIALLY_RECEIVED", confirmed.get("orderStatus"));
        assertEquals(1, ((List<?>) confirmed.get("batches")).size());
        assertEquals(1, ((List<?>) confirmed.get("movements")).size());
        assertMoney("1.000", jdbc.queryForObject(
                "SELECT on_hand_quantity FROM lab_component_inventory_balance WHERE component_id=?",
                BigDecimal.class, componentId));

        Map<String, Object> replay = data(post(receiptPath(receipt, "confirm"), admin,
                "receipt-confirm-01", command(receipt)));
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        assertEquals(1, count("lab_inventory_batch"));
        assertEquals(1, count("lab_inventory_movement"));
        assertReason(post(receiptPath(receipt, "confirm"), admin,
                "receipt-confirm-new-key", Map.of("receiptVersion", 2, "orderVersion", 5)),
                HttpStatus.CONFLICT, "RECEIPT_ALREADY_CONFIRMED");

        Map<String, Object> refreshed = data(get("/api/procurement-orders/" + order.get("id"), admin));
        Map<String, Object> second = createReceipt(admin, refreshed, "receipt-create-02", "1.000");
        Map<String, Object> full = data(post(receiptPath(second, "confirm"), admin,
                "receipt-confirm-02", command(second)));
        assertEquals("RECEIVED", full.get("orderStatus"));
        assertMoney("2.000", jdbc.queryForObject(
                "SELECT on_hand_quantity FROM lab_component_inventory_balance WHERE component_id=?",
                BigDecimal.class, componentId));
        assertEquals(2, count("lab_inventory_batch"));
        assertEquals(2, count("lab_inventory_movement"));
    }

    @Test
    void rejectsOverQuantityWithoutInventoryFacts() {
        String admin = token(login("admin", "admin123"));
        Map<String, Object> order = createOrdered(admin, "receipt-over-01", "2.000");
        Map<String, Object> receipt = createReceipt(admin, order, "receipt-over-create", "2.000");
        Long orderLineId = number(firstLine(order).get("id"));
        jdbc.update("UPDATE lab_procurement_order_line SET received_quantity=1.000 WHERE id=?", orderLineId);

        assertReason(post(receiptPath(receipt, "confirm"), admin, "receipt-over-confirm", command(receipt)),
                HttpStatus.CONFLICT, "RECEIPT_OVER_QUANTITY");
        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT status FROM lab_procurement_receipt WHERE id=?", String.class, receipt.get("id")));
        assertEquals(0, count("lab_inventory_batch"));
        assertEquals(0, count("lab_inventory_movement"));
        assertEquals(0, count("lab_component_inventory_balance"));
    }

    void verifyRollbackAtControlledFailurePoint() {
        assertEquals("AFTER_FIRST_MOVEMENT",
                environment.getProperty("usnhub.procurement.test-fault-point"));
        String admin = token(login("admin", "admin123"));
        Map<String, Object> order = createOrdered(admin, "receipt-fault-01", "1.000");
        Map<String, Object> receipt = createReceipt(admin, order, "receipt-fault-create", "1.000");
        ResponseEntity<Map> failed = post(receiptPath(receipt, "confirm"), admin,
                "receipt-fault-confirm", command(receipt));
        assertReason(failed, HttpStatus.INTERNAL_SERVER_ERROR, "PROCUREMENT_TRANSACTION_FAILED");
        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT status FROM lab_procurement_receipt WHERE id=?", String.class, receipt.get("id")));
        assertEquals("ORDERED", jdbc.queryForObject(
                "SELECT status FROM lab_procurement_order WHERE id=?", String.class, order.get("id")));
        assertMoney("0.000", jdbc.queryForObject(
                "SELECT received_quantity FROM lab_procurement_order_line WHERE order_id=?",
                BigDecimal.class, order.get("id")));
        assertEquals(0, count("lab_inventory_batch"));
        assertEquals(0, count("lab_inventory_movement"));
        assertEquals(0, count("lab_component_inventory_balance"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void enforcesReceiptWriteAndInventoryDetailPermissions() {
        String admin = token(login("admin", "admin123"));
        String member = token(login("20260001", "20260001"));
        Map<String, Object> order = createOrdered(admin, "receipt-acl-01", "1.000");
        assertReason(post("/api/procurement-orders/" + order.get("id") + "/receipts", member,
                "receipt-member-denied", receiptBody(order, "1.000")),
                HttpStatus.FORBIDDEN, "PROCUREMENT_RECEIPT_DENIED");

        Map<String, Object> receipt = createReceipt(admin, order, "receipt-acl-create", "1.000");
        data(post(receiptPath(receipt, "confirm"), admin, "receipt-acl-confirm", command(receipt)));
        Map<String, Object> summary = data(get("/api/inventory/components/" + componentId, member));
        assertMoney("1.000", new BigDecimal(summary.get("onHandQuantity").toString()));
        assertReason(get("/api/inventory/components/" + componentId + "/batches", member),
                HttpStatus.FORBIDDEN, "PROCUREMENT_OPERATION_DENIED");
        Map<String, Object> batches = data(get(
                "/api/inventory/components/" + componentId + "/batches", admin));
        assertEquals(1, ((Number) batches.get("total")).intValue());
        assertNotNull(((List<Map<String, Object>>) batches.get("list")).get(0).get("unitCost"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void serializesConcurrentFirstBalanceCreationOnMySql() throws Exception {
        String admin = token(login("admin", "admin123"));
        Map<String, Object> firstOrder = createOrdered(admin, "mysql-concurrent-01", "1.000");
        Map<String, Object> secondOrder = createOrdered(admin, "mysql-concurrent-02", "1.000");
        Map<String, Object> firstReceipt = createReceipt(admin, firstOrder, "mysql-receipt-01", "1.000");
        Map<String, Object> secondReceipt = createReceipt(admin, secondOrder, "mysql-receipt-02", "1.000");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map>> first = executor.submit(() -> concurrentConfirm(
                    firstReceipt, admin, "mysql-confirm-01", ready, start));
            Future<ResponseEntity<Map>> second = executor.submit(() -> concurrentConfirm(
                    secondReceipt, admin, "mysql-confirm-02", ready, start));
            ready.await();
            start.countDown();
            ResponseEntity<Map> firstResponse = first.get();
            ResponseEntity<Map> secondResponse = second.get();
            assertEquals(HttpStatus.OK, firstResponse.getStatusCode());
            assertEquals(HttpStatus.OK, secondResponse.getStatusCode());
            assertEquals(200, ((Number) firstResponse.getBody().get("code")).intValue());
            assertEquals(200, ((Number) secondResponse.getBody().get("code")).intValue());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, count("lab_component_inventory_balance"));
        assertMoney("2.000", jdbc.queryForObject(
                "SELECT on_hand_quantity FROM lab_component_inventory_balance WHERE component_id=?",
                BigDecimal.class, componentId));
        assertEquals(2, count("lab_inventory_batch"));
        assertEquals(2, count("lab_inventory_movement"));
    }

    private ResponseEntity<Map> concurrentConfirm(Map<String, Object> receipt, String token, String key,
                                                   CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return post(receiptPath(receipt, "confirm"), token, key, command(receipt));
    }

    private void seedComponent() {
        jdbc.update("""
                INSERT INTO lab_component_category(
                    code,name,normalized_name,parent_scope_id,level,path,created_by,updated_by
                ) VALUES('RCPT_ROOT','到货测试类','到货测试类',0,1,'',1,1)
                """);
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM lab_component_category WHERE code='RCPT_ROOT'", Long.class);
        jdbc.update("UPDATE lab_component_category SET path=? WHERE id=?", "/" + categoryId + "/", categoryId);
        jdbc.update("""
                INSERT INTO lab_component_manufacturer(code,name,normalized_name,created_by,updated_by)
                VALUES('RCPT_MFR','到货制造商','到货制造商',1,1)
                """);
        Long manufacturerId = jdbc.queryForObject(
                "SELECT id FROM lab_component_manufacturer WHERE code='RCPT_MFR'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component(
                    catalog_code,name,manufacturer_id,mpn,normalized_mpn,category_id,
                    package_type,base_unit,status,created_by,updated_by
                ) VALUES('CMP-RCPT-001','到货测试元件',?,'RCPT-001','RCPT-001',?,'0603','PCS','ACTIVE',1,1)
                """, manufacturerId, categoryId);
        componentId = jdbc.queryForObject(
                "SELECT id FROM lab_component WHERE catalog_code='CMP-RCPT-001'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component_spec_snapshot(
                    component_id,snapshot_version,source_type,source_note,raw_specs_json,
                    specifications_json,change_summary,checksum,confirmed_by
                ) VALUES(?,1,'MANUAL','测试规格','{}','{}','初始规格',?,1)
                """, componentId, "b".repeat(64));
        Long snapshotId = jdbc.queryForObject(
                "SELECT id FROM lab_component_spec_snapshot WHERE component_id=?", Long.class, componentId);
        jdbc.update("UPDATE lab_component SET current_spec_snapshot_id=?,spec_version=1 WHERE id=?",
                snapshotId, componentId);
    }

    private Map<String, Object> createOrdered(String token, String key, String quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "到货测试采购单");
        body.put("shippingAmount", BigDecimal.ZERO);
        body.put("discountAmount", BigDecimal.ZERO);
        body.put("lines", List.of(Map.of("componentId", componentId,
                "orderedQuantity", new BigDecimal(quantity), "unitPrice", new BigDecimal("20.0000"))));
        Map<String, Object> created = data(post("/api/procurement-orders", token, key, body));
        Map<String, Object> approved = data(post(orderPath(created, "submit"), token,
                key + "-submit", Map.of("version", 1)));
        Map<String, Object> orderedBody = new LinkedHashMap<>();
        orderedBody.put("version", approved.get("version"));
        orderedBody.put("buyerId", 2);
        orderedBody.put("supplierName", "到货测试供应商");
        orderedBody.put("platformType", "OTHER");
        orderedBody.put("platformOrderNo", key.toUpperCase());
        orderedBody.put("actualPaidAmount", approved.get("payableAmount"));
        orderedBody.put("paymentDifferenceNote", null);
        return data(post(orderPath(created, "mark-ordered"), token, key + "-ordered", orderedBody));
    }

    private Map<String, Object> createReceipt(String token, Map<String, Object> order,
                                               String key, String quantity) {
        return data(post("/api/procurement-orders/" + order.get("id") + "/receipts",
                token, key, receiptBody(order, quantity)));
    }

    private Map<String, Object> receiptBody(Map<String, Object> order, String quantity) {
        return Map.of("orderVersion", order.get("version"),
                "receivedAt", OffsetDateTime.now().minusMinutes(1).toString(),
                "note", "测试到货",
                "lines", List.of(Map.of("orderLineId", firstLine(order).get("id"),
                        "quantity", new BigDecimal(quantity), "supplierLotNo", "TEST-LOT")));
    }

    private Map<String, Object> command(Map<String, Object> receipt) {
        return Map.of("receiptVersion", receipt.get("version"), "orderVersion", receipt.get("orderVersion"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstLine(Map<String, Object> order) {
        return ((List<Map<String, Object>>) order.get("lines")).get(0);
    }

    private String orderPath(Map<String, Object> order, String action) {
        return "/api/procurement-orders/" + order.get("id") + "/" + action;
    }

    private String receiptPath(Map<String, Object> receipt, String action) {
        return "/api/procurement-receipts/" + receipt.get("id") + "/" + action;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Long number(Object value) {
        return ((Number) value).longValue();
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

    private void assertReason(ResponseEntity<Map> response, HttpStatus status, String reason) {
        assertEquals(status, response.getStatusCode(), String.valueOf(response.getBody()));
        assertEquals(reason, response.getBody().get("reason"));
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
