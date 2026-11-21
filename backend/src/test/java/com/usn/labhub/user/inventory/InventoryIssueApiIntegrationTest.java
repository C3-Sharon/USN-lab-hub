package com.usn.labhub.user.inventory;

import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.service.IAttendanceService;
import com.usn.labhub.user.service.InventoryIssueExpiryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
                "usnhub.iot.operations.timeout-enabled=false",
                "usnhub.inventory.issue.expiry-delay-ms=3600000"
        }
)
class InventoryIssueApiIntegrationTest {
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private IAttendanceService attendanceService;
    @Autowired
    private InventoryIssueExpiryService expiryService;
    @Autowired
    private Environment environment;
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
        jdbc.update("DELETE FROM lab_inventory_return_line");
        jdbc.update("DELETE FROM lab_inventory_write_off_command");
        jdbc.update("DELETE FROM lab_inventory_fulfillment_line");
        jdbc.update("DELETE FROM lab_inventory_reservation_allocation");
        jdbc.update("DELETE FROM lab_inventory_return");
        jdbc.update("DELETE FROM lab_inventory_fulfillment");
        jdbc.update("DELETE FROM lab_inventory_reservation");
        jdbc.update("DELETE FROM lab_inventory_issue_approval_record");
        jdbc.update("DELETE FROM lab_inventory_issue_policy_forced_category");
        jdbc.update("DELETE FROM lab_inventory_issue_policy_component_override");
        jdbc.update("DELETE FROM lab_inventory_issue_line");
        jdbc.update("DELETE FROM lab_inventory_issue_request");
        jdbc.update("DELETE FROM lab_inventory_issue_policy_version");
        jdbc.update("DELETE FROM lab_request_idempotency WHERE operation LIKE '%/api/inventory%'");
        jdbc.update("DELETE FROM lab_inventory_movement");
        jdbc.update("DELETE FROM lab_inventory_batch");
        jdbc.update("DELETE FROM lab_component_inventory_balance");
        jdbc.update("DELETE FROM lab_procurement_receipt_line");
        jdbc.update("DELETE FROM lab_procurement_receipt");
        jdbc.update("DELETE FROM lab_procurement_order_line");
        jdbc.update("DELETE FROM lab_procurement_order");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id IN (100,101,102)");
        jdbc.update("DELETE FROM sys_user WHERE id IN (100,101,102)");
        jdbc.update("DELETE FROM lab_component_spec_snapshot");
        jdbc.update("DELETE FROM lab_component");
        jdbc.update("DELETE FROM lab_component_manufacturer");
        jdbc.update("DELETE FROM lab_component_category");
        seedComponent();
        seedInventory();
        seedTeacher();
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

    @Test
    void submitsAndAutoApprovesWithReservationAndReplay() {
        String member = token(login("20260001", "20260001"));
        Map<String, Object> created = data(post("/api/inventory-issue-requests", member,
                "submit-auto-0001", requestBody()));
        long requestId = ((Number) created.get("id")).longValue();

        Map<String, Object> submit = data(post("/api/inventory-issue-requests/" + requestId + "/submit",
                member, "submit-auto-0002", submitBody(1)));
        assertEquals("APPROVED", submit.get("status"));
        assertEquals(Boolean.FALSE, submit.get("approvalRequired"));
        assertEquals(List.of("EXEMPT"), submit.get("approvalReasons"));
        assertEquals(1, ((Number) submit.get("reservationVersion")).intValue());
        assertEquals("5.000:15.000", jdbc.queryForObject(
                "SELECT CONCAT(reserved_quantity,':',on_hand_quantity-reserved_quantity) "
                        + "FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_reservation WHERE request_id=? AND status='ACTIVE'",
                Integer.class, requestId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_issue_approval_record "
                        + "WHERE request_id=? AND decision='AUTO_APPROVED'",
                Integer.class, requestId));

        Map<String, Object> replay = data(post("/api/inventory-issue-requests/" + requestId + "/submit",
                member, "submit-auto-0002", submitBody(1)));
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        assertEquals("APPROVED", replay.get("status"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_reservation WHERE request_id=?", Integer.class, requestId));
    }

    @Test
    void submitsManualApprovalAndTeacherDecides() {
        String member = token(login("20260001", "20260001"));
        String teacher = token(login("teacher001", "admin123"));
        Map<String, Object> body = requestBody();
        body.put("lines", List.of(line(componentId, "15.000")));
        Map<String, Object> created = data(post("/api/inventory-issue-requests", member,
                "submit-manual-001", body));
        long requestId = ((Number) created.get("id")).longValue();

        Map<String, Object> submit = data(post("/api/inventory-issue-requests/" + requestId + "/submit",
                member, "submit-manual-002", submitBody(1)));
        assertEquals("PENDING_APPROVAL", submit.get("status"));
        assertEquals(Boolean.TRUE, submit.get("approvalRequired"));
        assertTrue(((List<?>) submit.get("approvalReasons")).contains("QUANTITY_THRESHOLD"));

        assertReason(post("/api/inventory-issue-requests/" + requestId + "/approve",
                        member, "submit-manual-003", decisionBody(2, null)),
                HttpStatus.FORBIDDEN, "INVENTORY_ISSUE_SELF_APPROVAL");

        Map<String, Object> approved = data(post("/api/inventory-issue-requests/" + requestId + "/approve",
                teacher, "submit-manual-004", decisionBody(2, "同意")));
        assertEquals("APPROVED", approved.get("status"));
        assertEquals("15.000:5.000", jdbc.queryForObject(
                "SELECT CONCAT(reserved_quantity,':',on_hand_quantity-reserved_quantity) "
                        + "FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
    }

    @Test
    void rejectReleasesReservationWithoutMovement() {
        String member = token(login("20260001", "20260001"));
        String teacher = token(login("teacher001", "admin123"));
        Map<String, Object> body = requestBody();
        body.put("lines", List.of(line(componentId, "15.000")));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "reject-0001", body)).get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "reject-0002", submitBody(1)));

        Map<String, Object> rejected = data(post("/api/inventory-issue-requests/" + requestId + "/reject",
                teacher, "reject-0003", decisionBody(2, "超量")));
        assertEquals("REJECTED", rejected.get("status"));
        assertEquals("0.000", jdbc.queryForObject(
                "SELECT reserved_quantity FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
        assertEquals("RELEASED", jdbc.queryForObject(
                "SELECT status FROM lab_inventory_reservation WHERE request_id=?", String.class, requestId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_movement "
                        + "WHERE movement_type IN ('ISSUE_OUT','RETURN_IN','LOSS_OUT','SCRAP_OUT')",
                Integer.class));
    }

    @Test
    void cancelReleasesReservation() {
        String member = token(login("20260001", "20260001"));
        Map<String, Object> body = requestBody();
        body.put("lines", List.of(line(componentId, "15.000")));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "cancel-0001", body)).get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "cancel-0002", submitBody(1)));

        Map<String, Object> canceled = data(post("/api/inventory-issue-requests/" + requestId + "/cancel",
                member, "cancel-0003", submitBody(2)));
        assertEquals("CANCELED", canceled.get("status"));
        assertEquals("0.000", jdbc.queryForObject(
                "SELECT reserved_quantity FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
        assertEquals("RELEASED", jdbc.queryForObject(
                "SELECT status FROM lab_inventory_reservation WHERE request_id=?", String.class, requestId));
    }

    @Test
    void insufficientStockKeepsDraft() {
        String member = token(login("20260001", "20260001"));
        Map<String, Object> body = requestBody();
        body.put("lines", List.of(line(componentId, "30.000")));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "stock-0001", body)).get("id")).longValue();

        assertReason(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                        "stock-0002", submitBody(1)),
                HttpStatus.CONFLICT, "INVENTORY_INSUFFICIENT_STOCK");
        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT status FROM lab_inventory_issue_request WHERE id=?", String.class, requestId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_reservation WHERE request_id=?", Integer.class, requestId));
        assertEquals("0.000", jdbc.queryForObject(
                "SELECT reserved_quantity FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
    }

    @Test
    void expiresAndReleasesReservation() {
        String member = token(login("20260001", "20260001"));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "expire-0001", requestBody())).get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "expire-0002", submitBody(1)));
        jdbc.update("UPDATE lab_inventory_reservation SET expires_at='2000-01-01 00:00:00' "
                + "WHERE request_id=?", requestId);

        expiryService.expireIfNeeded(requestId);

        assertEquals("EXPIRED", jdbc.queryForObject(
                "SELECT status FROM lab_inventory_issue_request WHERE id=?", String.class, requestId));
        assertEquals("EXPIRED", jdbc.queryForObject(
                "SELECT status FROM lab_inventory_reservation WHERE request_id=?", String.class, requestId));
        assertEquals("EXPIRED", jdbc.queryForObject(
                "SELECT release_reason FROM lab_inventory_reservation WHERE request_id=?",
                String.class, requestId));
        assertEquals("0.000", jdbc.queryForObject(
                "SELECT reserved_quantity FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
    }

    @Test
    void confirmIssueConsumesAndWritesMovements() {
        String member = token(login("20260001", "20260001"));
        String admin = token(login("admin", "admin123"));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "confirm-0001", requestBody())).get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "confirm-0002", submitBody(1)));

        Map<String, Object> issued = data(post("/api/inventory-issue-requests/" + requestId
                        + "/confirm-issue", admin, "confirm-0003", confirmBody(2, 1, "已交付")));
        assertEquals("ISSUED", issued.get("status"));
        assertNotNull(issued.get("fulfillmentId"));
        assertTrue(issued.get("fulfillmentNo").toString().matches("FUL-\\d{8}-\\d{6}"));
        assertEquals(3, ((Number) issued.get("requestVersion")).intValue());
        assertEquals(1, ((List<?>) issued.get("movements")).size());

        assertEquals("15.000:0.000", jdbc.queryForObject(
                "SELECT CONCAT(on_hand_quantity,':',reserved_quantity) "
                        + "FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
        assertEquals("15.000:0.000", jdbc.queryForObject(
                "SELECT CONCAT(current_quantity,':',reserved_quantity) "
                        + "FROM lab_inventory_batch WHERE component_id=?",
                String.class, componentId));
        assertEquals("CONSUMED", jdbc.queryForObject(
                "SELECT status FROM lab_inventory_reservation WHERE request_id=?",
                String.class, requestId));
        assertEquals("5.000:0.000", jdbc.queryForObject(
                "SELECT CONCAT(issued_quantity,':',reserved_quantity) "
                        + "FROM lab_inventory_issue_line WHERE request_id=?",
                String.class, requestId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_movement WHERE movement_type='ISSUE_OUT'",
                Integer.class));
    }

    @Test
    void confirmIssueReplaysAndRejectsNewKey() {
        String member = token(login("20260001", "20260001"));
        String admin = token(login("admin", "admin123"));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "confirm-replay-1", requestBody())).get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "confirm-replay-2", submitBody(1)));

        Map<String, Object> issued = data(post("/api/inventory-issue-requests/" + requestId
                + "/confirm-issue", admin, "confirm-replay-3", confirmBody(2, 1, null)));
        Map<String, Object> replay = data(post("/api/inventory-issue-requests/" + requestId
                + "/confirm-issue", admin, "confirm-replay-3", confirmBody(2, 1, null)));
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        assertEquals(issued.get("fulfillmentId"), replay.get("fulfillmentId"));
        assertEquals(issued.get("movements"), replay.get("movements"));

        assertReason(post("/api/inventory-issue-requests/" + requestId + "/confirm-issue",
                        admin, "confirm-replay-4", confirmBody(3, 2, null)),
                HttpStatus.CONFLICT, "INVENTORY_ISSUE_ALREADY_CONFIRMED");
    }

    @Test
    void confirmIssueEnforcesDutySeparation() {
        String member = token(login("20260001", "20260001"));
        String teacher = token(login("teacher001", "admin123"));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "confirm-duty-1", requestBody())).get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "confirm-duty-2", submitBody(1)));

        assertReason(post("/api/inventory-issue-requests/" + requestId + "/confirm-issue",
                        member, "confirm-duty-3", confirmBody(2, 1, null)),
                HttpStatus.FORBIDDEN, "INVENTORY_ISSUE_FULFILL_DENIED");
        assertReason(post("/api/inventory-issue-requests/" + requestId + "/confirm-issue",
                        teacher, "confirm-duty-4", confirmBody(2, 1, null)),
                HttpStatus.FORBIDDEN, "INVENTORY_ISSUE_FULFILL_DENIED");
        assertEquals("APPROVED", jdbc.queryForObject(
                "SELECT status FROM lab_inventory_issue_request WHERE id=?",
                String.class, requestId));
    }

    @Test
    void confirmIssueRejectsStaleReservationVersion() {
        String member = token(login("20260001", "20260001"));
        String admin = token(login("admin", "admin123"));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "confirm-ver-1", requestBody())).get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "confirm-ver-2", submitBody(1)));

        assertReason(post("/api/inventory-issue-requests/" + requestId + "/confirm-issue",
                        admin, "confirm-ver-3", confirmBody(2, 2, null)),
                HttpStatus.CONFLICT, "VERSION_CONFLICT");
    }

    @Test
    void confirmIssueAfterExpiryConflicts() {
        String member = token(login("20260001", "20260001"));
        String admin = token(login("admin", "admin123"));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "confirm-exp-1", requestBody())).get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "confirm-exp-2", submitBody(1)));
        jdbc.update("UPDATE lab_inventory_reservation SET expires_at='2000-01-01 00:00:00' "
                + "WHERE request_id=?", requestId);

        assertReason(post("/api/inventory-issue-requests/" + requestId + "/confirm-issue",
                        admin, "confirm-exp-3", confirmBody(2, 1, null)),
                HttpStatus.CONFLICT, "INVENTORY_RESERVATION_EXPIRED");
    }

    @Test
    void reallocateSwapsBatchReservation() {
        String member = token(login("20260001", "20260001"));
        String admin = token(login("admin", "admin123"));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "realloc-0001", requestBody())).get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "realloc-0002", submitBody(1)));
        long reservationId = jdbc.queryForObject(
                "SELECT id FROM lab_inventory_reservation WHERE request_id=?", Long.class, requestId);
        jdbc.update("""
                INSERT INTO lab_procurement_order(
                    purchase_no,title,requester_id,requester_name,currency,
                    item_subtotal,shipping_amount,discount_amount,payable_amount,
                    status,approval_required,approval_policy_version,created_by,updated_by
                ) VALUES('PUR-ISS-TEST-2','领用库存事实二',1,'system','CNY',
                         10.00,0.00,0.00,10.00,'RECEIVED',0,1,1,1)
                """);
        Long secondOrderId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_order WHERE purchase_no='PUR-ISS-TEST-2'", Long.class);
        jdbc.update("""
                INSERT INTO lab_procurement_order_line(
                    order_id,line_no,component_id,component_name,unit,
                    ordered_quantity,unit_price,line_subtotal,allocated_net_amount,
                    effective_unit_cost,received_quantity,version
                ) VALUES(?,1,?,'领用测试电阻','PCS',10.000,1.0000,10.00,10.00,1.0000,10.000,1)
                """, secondOrderId, componentId);
        Long secondOrderLineId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_order_line WHERE order_id=? AND line_no=1",
                Long.class, secondOrderId);
        jdbc.update("""
                INSERT INTO lab_procurement_receipt(
                    receipt_no,order_id,status,received_at,receiver_id,receiver_name,
                    confirmed_at,version,created_by,updated_by,active_draft_slot
                ) VALUES('REC-ISS-TEST-2',?,'CONFIRMED',CURRENT_TIMESTAMP,1,'system',
                         CURRENT_TIMESTAMP,1,1,1,NULL)
                """, secondOrderId);
        Long secondReceiptId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_receipt WHERE receipt_no='REC-ISS-TEST-2'", Long.class);
        jdbc.update("""
                INSERT INTO lab_procurement_receipt_line(
                    receipt_id,order_line_id,component_id,quantity,unit,version
                ) VALUES(?,?,?,10.000,'PCS',1)
                """, secondReceiptId, secondOrderLineId, componentId);
        Long secondReceiptLineId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_receipt_line WHERE receipt_id=?",
                Long.class, secondReceiptId);
        jdbc.update("""
                INSERT INTO lab_inventory_batch(
                    batch_no,component_id,source_receipt_line_id,
                    initial_quantity,current_quantity,unit,unit_cost,status,created_by
                ) VALUES('BAT-ISS-002',?,?,10.000,10.000,'PCS',1.0000,'AVAILABLE',1)
                """, componentId, secondReceiptLineId);
        long secondBatchId = jdbc.queryForObject(
                "SELECT id FROM lab_inventory_batch WHERE batch_no='BAT-ISS-002'", Long.class);
        jdbc.update("UPDATE lab_component_inventory_balance "
                + "SET on_hand_quantity=on_hand_quantity+10.000, version=version+1 WHERE component_id=?",
                componentId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestVersion", 2);
        body.put("reason", "改用新批次");
        body.put("allocations", List.of(Map.of("reservationId", reservationId,
                "batchId", secondBatchId, "quantity", new BigDecimal("5.000"))));
        Map<String, Object> result = data(post("/api/inventory-issue-requests/" + requestId
                + "/reallocate", admin, "realloc-0003", body));
        assertEquals("APPROVED", result.get("status"));

        assertEquals("0.000", jdbc.queryForObject(
                "SELECT reserved_quantity FROM lab_inventory_batch WHERE batch_no='BAT-ISS-001'",
                String.class));
        assertEquals("5.000", jdbc.queryForObject(
                "SELECT reserved_quantity FROM lab_inventory_batch WHERE batch_no='BAT-ISS-002'",
                String.class));
        assertEquals("30.000:5.000", jdbc.queryForObject(
                "SELECT CONCAT(on_hand_quantity,':',reserved_quantity) "
                        + "FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
        assertEquals(secondBatchId, jdbc.queryForObject(
                "SELECT batch_id FROM lab_inventory_reservation_allocation WHERE reservation_id=?",
                Long.class, reservationId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_reservation_allocation WHERE reservation_id=?",
                Integer.class, reservationId));
    }

    @Test
    void returnConfirmsRestoresOriginalBatchAndBalance() {
        Map<String, Object> ctx = issueOne("full");
        String member = (String) ctx.get("member");
        String admin = (String) ctx.get("admin");
        long fulfillmentId = ((Number) ctx.get("fulfillmentId")).longValue();
        long fulfillmentLineId = ((Number) ctx.get("fulfillmentLineId")).longValue();

        Map<String, Object> created = data(post(
                "/api/inventory-fulfillments/" + fulfillmentId + "/returns", member, "rtn-create-1",
                returnCreateBody("多余物料退回", fulfillmentLineId, "2.000")));
        long returnId = ((Number) created.get("returnId")).longValue();

        data(post("/api/inventory-returns/" + returnId + "/confirm", admin, "rtn-confirm-1",
                returnCommandBody(1)));

        assertEquals("CONFIRMED", jdbc.queryForObject(
                "SELECT status FROM lab_inventory_return WHERE id=?", String.class, returnId));
        assertEquals("17.000:0.000", jdbc.queryForObject(
                "SELECT CONCAT(on_hand_quantity,':',reserved_quantity) "
                        + "FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
        assertEquals("17.000", jdbc.queryForObject(
                "SELECT current_quantity FROM lab_inventory_batch WHERE batch_no='BAT-ISS-001'",
                String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_movement WHERE movement_type='RETURN_IN'",
                Integer.class));
    }

    @Test
    void returnRejectsQuantityExceedingIssued() {
        Map<String, Object> ctx = issueOne("over");
        String member = (String) ctx.get("member");
        long fulfillmentId = ((Number) ctx.get("fulfillmentId")).longValue();
        long fulfillmentLineId = ((Number) ctx.get("fulfillmentLineId")).longValue();

        assertReason(post("/api/inventory-fulfillments/" + fulfillmentId + "/returns", member,
                        "rtn-over-1", returnCreateBody("超量退回", fulfillmentLineId, "6.000")),
                HttpStatus.CONFLICT, "INVENTORY_RETURN_EXCEEDS_ISSUED");
    }

    @Test
    void returnConfirmRejectsStaleVersion() {
        Map<String, Object> ctx = issueOne("stale");
        String member = (String) ctx.get("member");
        String admin = (String) ctx.get("admin");
        long fulfillmentId = ((Number) ctx.get("fulfillmentId")).longValue();
        long fulfillmentLineId = ((Number) ctx.get("fulfillmentLineId")).longValue();

        long returnId = ((Number) data(post("/api/inventory-fulfillments/" + fulfillmentId
                + "/returns", member, "rtn-stale-1",
                returnCreateBody("退回", fulfillmentLineId, "2.000"))).get("returnId")).longValue();
        assertReason(post("/api/inventory-returns/" + returnId + "/confirm", admin,
                        "rtn-stale-2", returnCommandBody(2)),
                HttpStatus.CONFLICT, "VERSION_CONFLICT");
    }

    @Test
    void returnRejectLeavesStockUntouched() {
        Map<String, Object> ctx = issueOne("reject");
        String member = (String) ctx.get("member");
        String admin = (String) ctx.get("admin");
        long fulfillmentId = ((Number) ctx.get("fulfillmentId")).longValue();
        long fulfillmentLineId = ((Number) ctx.get("fulfillmentLineId")).longValue();

        long returnId = ((Number) data(post("/api/inventory-fulfillments/" + fulfillmentId
                + "/returns", member, "rtn-rej-1",
                returnCreateBody("退回", fulfillmentLineId, "2.000"))).get("returnId")).longValue();
        data(post("/api/inventory-returns/" + returnId + "/reject", admin, "rtn-rej-2",
                returnCommandBody(1)));

        assertEquals("REJECTED", jdbc.queryForObject(
                "SELECT status FROM lab_inventory_return WHERE id=?", String.class, returnId));
        assertEquals("15.000", jdbc.queryForObject(
                "SELECT on_hand_quantity FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_movement WHERE movement_type='RETURN_IN'",
                Integer.class));
    }

    @Test
    void memberCannotConfirmReturn() {
        Map<String, Object> ctx = issueOne("member-confirm");
        String member = (String) ctx.get("member");
        long fulfillmentId = ((Number) ctx.get("fulfillmentId")).longValue();
        long fulfillmentLineId = ((Number) ctx.get("fulfillmentLineId")).longValue();

        long returnId = ((Number) data(post("/api/inventory-fulfillments/" + fulfillmentId
                + "/returns", member, "rtn-mc-1",
                returnCreateBody("退回", fulfillmentLineId, "2.000"))).get("returnId")).longValue();
        assertReason(post("/api/inventory-returns/" + returnId + "/confirm", member, "rtn-mc-2",
                        returnCommandBody(1)),
                HttpStatus.FORBIDDEN, "INVENTORY_ISSUE_FULFILL_DENIED");
    }

    @Test
    void writeOffLossDeductsAvailableStock() {
        String admin = token(login("admin", "admin123"));
        long batchId = jdbc.queryForObject(
                "SELECT id FROM lab_inventory_batch WHERE batch_no='BAT-ISS-001'", Long.class);

        Map<String, Object> result = data(post("/api/inventory/batches/" + batchId + "/losses",
                admin, "wo-loss-1", writeOffBody("3.000", "跌落损坏")));
        assertEquals("LOSS", result.get("type"));

        assertEquals("17.000:0.000", jdbc.queryForObject(
                "SELECT CONCAT(on_hand_quantity,':',reserved_quantity) "
                        + "FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
        assertEquals("17.000", jdbc.queryForObject(
                "SELECT current_quantity FROM lab_inventory_batch WHERE id=?", String.class, batchId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_movement WHERE movement_type='LOSS_OUT'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_write_off_command WHERE type='LOSS'",
                Integer.class));
    }

    @Test
    void writeOffRejectsReservedStock() {
        String member = token(login("20260001", "20260001"));
        String admin = token(login("admin", "admin123"));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "wo-rsv-1", requestBody())).get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "wo-rsv-2", submitBody(1)));
        long batchId = jdbc.queryForObject(
                "SELECT id FROM lab_inventory_batch WHERE batch_no='BAT-ISS-001'", Long.class);

        assertReason(post("/api/inventory/batches/" + batchId + "/losses", admin, "wo-rsv-3",
                        writeOffBody("16.000", "试图扣预占")),
                HttpStatus.CONFLICT, "INVENTORY_RESERVED_STOCK_PROTECTED");
        assertEquals("20.000", jdbc.queryForObject(
                "SELECT current_quantity FROM lab_inventory_batch WHERE id=?", String.class, batchId));
    }

    @Test
    void writeOffRequiresReason() {
        String admin = token(login("admin", "admin123"));
        long batchId = jdbc.queryForObject(
                "SELECT id FROM lab_inventory_batch WHERE batch_no='BAT-ISS-001'", Long.class);

        assertReason(post("/api/inventory/batches/" + batchId + "/losses", admin, "wo-reason-1",
                        writeOffBody("3.000", "")),
                HttpStatus.BAD_REQUEST, "WRITE_OFF_REASON_REQUIRED");
    }

    void verifyRollbackAtControlledFailurePoint() {
        assertEquals("AFTER_FIRST_MOVEMENT",
                environment.getProperty("usnhub.inventory.issue.test-fault-point"));
        String member = token(login("20260001", "20260001"));
        String admin = token(login("admin", "admin123"));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "fault-create-1", requestBody())).get("id")).longValue();
        Map<String, Object> submitted = data(post("/api/inventory-issue-requests/" + requestId
                + "/submit", member, "fault-submit-1", submitBody(1)));
        assertEquals("APPROVED", submitted.get("status"));

        assertReason(post("/api/inventory-issue-requests/" + requestId + "/confirm-issue",
                        admin, "fault-confirm-1", confirmBody(2, 1, null)),
                HttpStatus.INTERNAL_SERVER_ERROR, "INVENTORY_ISSUE_TRANSACTION_FAILED");

        assertEquals("APPROVED", jdbc.queryForObject(
                "SELECT status FROM lab_inventory_issue_request WHERE id=?", String.class, requestId));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM lab_inventory_reservation WHERE request_id=?", String.class, requestId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_fulfillment WHERE request_id=?", Integer.class, requestId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_movement WHERE movement_type='ISSUE_OUT'", Integer.class));
        assertEquals("20.000:5.000", jdbc.queryForObject(
                "SELECT CONCAT(on_hand_quantity,':',reserved_quantity) "
                        + "FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
        assertEquals("20.000:5.000", jdbc.queryForObject(
                "SELECT CONCAT(current_quantity,':',reserved_quantity) "
                        + "FROM lab_inventory_batch WHERE component_id=?",
                String.class, componentId));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void serializesConcurrentSubmitsForSameAvailableStockOnMySql() throws Exception {
        String member = token(login("20260001", "20260001"));
        long firstId = ((Number) draftWithQuantity(member, "mysql-submit-a", "15.000")
                .get("id")).longValue();
        long secondId = ((Number) draftWithQuantity(member, "mysql-submit-b", "15.000")
                .get("id")).longValue();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map>> first = executor.submit(() ->
                    concurrentSubmit(firstId, member, "mysql-submit-a-2", ready, start));
            Future<ResponseEntity<Map>> second = executor.submit(() ->
                    concurrentSubmit(secondId, member, "mysql-submit-b-2", ready, start));
            ready.await();
            start.countDown();

            long ok = 0;
            long conflict = 0;
            for (ResponseEntity<Map> response : List.of(first.get(), second.get())) {
                if (HttpStatus.OK.equals(response.getStatusCode())) {
                    ok++;
                } else {
                    assertEquals(HttpStatus.CONFLICT, response.getStatusCode(),
                            String.valueOf(response.getBody()));
                    assertTrue(List.of("INVENTORY_INSUFFICIENT_STOCK",
                            "INVENTORY_CONCURRENT_CONFLICT")
                            .contains(response.getBody().get("reason")));
                    conflict++;
                }
            }
            assertEquals(1, ok);
            assertEquals(1, conflict);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_issue_request WHERE status='PENDING_APPROVAL'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_issue_request WHERE status='DRAFT'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_reservation WHERE status='ACTIVE'",
                Integer.class));
        assertEquals("15.000:5.000", jdbc.queryForObject(
                "SELECT CONCAT(reserved_quantity,':',on_hand_quantity-reserved_quantity) "
                        + "FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void serializesConfirmIssueVsExpiryOnMySql() throws Exception {
        String member = token(login("20260001", "20260001"));
        String admin = token(login("admin", "admin123"));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "mysql-expiry-a", requestBody())).get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "mysql-expiry-b", submitBody(1)));
        jdbc.update("UPDATE lab_inventory_reservation SET expires_at='2000-01-01 00:00:00' "
                + "WHERE request_id=?", requestId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map>> confirm = executor.submit(() ->
                    concurrentConfirmIssue(requestId, admin, "mysql-expiry-confirm", ready, start));
            Future<?> expire = executor.submit(() -> {
                ready.countDown();
                start.await();
                expiryService.expireIfNeeded(requestId);
                return null;
            });
            ready.await();
            start.countDown();
            confirm.get();
            expire.get();
        } finally {
            executor.shutdownNow();
        }

        String status = jdbc.queryForObject(
                "SELECT status FROM lab_inventory_issue_request WHERE id=?", String.class, requestId);
        String reservationStatus = jdbc.queryForObject(
                "SELECT status FROM lab_inventory_reservation WHERE request_id=?", String.class, requestId);
        if ("ISSUED".equals(status)) {
            assertEquals("CONSUMED", reservationStatus);
            assertEquals("15.000:0.000", jdbc.queryForObject(
                    "SELECT CONCAT(on_hand_quantity,':',reserved_quantity) "
                            + "FROM lab_component_inventory_balance WHERE component_id=?",
                    String.class, componentId));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM lab_inventory_fulfillment WHERE request_id=?", Integer.class, requestId));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM lab_inventory_movement WHERE movement_type='ISSUE_OUT'", Integer.class));
        } else {
            assertEquals("EXPIRED", status);
            assertEquals("EXPIRED", reservationStatus);
            assertEquals("20.000:0.000", jdbc.queryForObject(
                    "SELECT CONCAT(on_hand_quantity,':',reserved_quantity) "
                            + "FROM lab_component_inventory_balance WHERE component_id=?",
                    String.class, componentId));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM lab_inventory_fulfillment WHERE request_id=?", Integer.class, requestId));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM lab_inventory_movement WHERE movement_type='ISSUE_OUT'", Integer.class));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void reallocatesAndWritesOffConcurrentlyWithoutInvariantViolationOnMySql() throws Exception {
        long secondBatchId = seedSecondBatch();
        long firstBatchId = jdbc.queryForObject(
                "SELECT id FROM lab_inventory_batch WHERE batch_no='BAT-ISS-001'", Long.class);
        String member = token(login("20260001", "20260001"));
        String admin = token(login("admin", "admin123"));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "mysql-race-a", requestBody())).get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "mysql-race-b", submitBody(1)));
        long reservationId = jdbc.queryForObject(
                "SELECT id FROM lab_inventory_reservation WHERE request_id=?", Long.class, requestId);

        Map<String, Object> reallocate = new LinkedHashMap<>();
        reallocate.put("requestVersion", 2);
        reallocate.put("reason", "并发重分配");
        reallocate.put("allocations", List.of(Map.of("reservationId", reservationId,
                "batchId", secondBatchId, "quantity", new BigDecimal("5.000"))));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map>> reallocateFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return post("/api/inventory-issue-requests/" + requestId + "/reallocate",
                        admin, "mysql-race-reallocate", reallocate);
            });
            Future<ResponseEntity<Map>> writeOffFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return post("/api/inventory/batches/" + firstBatchId + "/scraps",
                        admin, "mysql-race-scrap", writeOffBody("3.000", "并发报废"));
            });
            ready.await();
            start.countDown();
            assertReason(reallocateFuture.get(), HttpStatus.OK, null);
            assertReason(writeOffFuture.get(), HttpStatus.OK, null);
        } finally {
            executor.shutdownNow();
        }

        assertEquals("17.000:0.000", jdbc.queryForObject(
                "SELECT CONCAT(current_quantity,':',reserved_quantity) FROM lab_inventory_batch WHERE id=?",
                String.class, firstBatchId));
        assertEquals("10.000:5.000", jdbc.queryForObject(
                "SELECT CONCAT(current_quantity,':',reserved_quantity) FROM lab_inventory_batch WHERE id=?",
                String.class, secondBatchId));
        assertEquals("27.000:5.000", jdbc.queryForObject(
                "SELECT CONCAT(on_hand_quantity,':',reserved_quantity) "
                        + "FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
        assertEquals(secondBatchId, jdbc.queryForObject(
                "SELECT batch_id FROM lab_inventory_reservation_allocation WHERE reservation_id=?",
                Long.class, reservationId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_movement WHERE movement_type='SCRAP_OUT'", Integer.class));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void confirmsSameIdempotencyKeyReplayOnMySql() {
        String member = token(login("20260001", "20260001"));
        String admin = token(login("admin", "admin123"));
        long requestId = ((Number) data(post("/api/inventory-issue-requests", member,
                "mysql-replay-a", requestBody())).get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "mysql-replay-b", submitBody(1)));

        Map<String, Object> issued = data(post("/api/inventory-issue-requests/" + requestId
                + "/confirm-issue", admin, "mysql-replay-confirm", confirmBody(2, 1, null)));
        Map<String, Object> replay = data(post("/api/inventory-issue-requests/" + requestId
                + "/confirm-issue", admin, "mysql-replay-confirm", confirmBody(2, 1, null)));
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        assertEquals(issued.get("fulfillmentId"), replay.get("fulfillmentId"));
        assertEquals(issued.get("movements"), replay.get("movements"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_fulfillment WHERE request_id=?", Integer.class, requestId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_movement WHERE movement_type='ISSUE_OUT'", Integer.class));

        assertReason(post("/api/inventory-issue-requests/" + requestId + "/confirm-issue",
                        admin, "mysql-replay-new", confirmBody(3, 2, null)),
                HttpStatus.CONFLICT, "INVENTORY_ISSUE_ALREADY_CONFIRMED");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void serializesConcurrentReturnInitiationWithinIssuedQuantityOnMySql() throws Exception {
        Map<String, Object> ctx = issueOne("mysql-return");
        String member = (String) ctx.get("member");
        String admin = (String) ctx.get("admin");
        long fulfillmentId = ((Number) ctx.get("fulfillmentId")).longValue();
        long fulfillmentLineId = ((Number) ctx.get("fulfillmentLineId")).longValue();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map>> first = executor.submit(() -> concurrentReturn(
                    fulfillmentId, member, "mysql-return-1", fulfillmentLineId, "4.000", ready, start));
            Future<ResponseEntity<Map>> second = executor.submit(() -> concurrentReturn(
                    fulfillmentId, member, "mysql-return-2", fulfillmentLineId, "4.000", ready, start));
            ready.await();
            start.countDown();

            long ok = 0;
            long conflict = 0;
            for (ResponseEntity<Map> response : List.of(first.get(), second.get())) {
                if (HttpStatus.OK.equals(response.getStatusCode())) {
                    ok++;
                } else {
                    assertEquals(HttpStatus.CONFLICT, response.getStatusCode(),
                            String.valueOf(response.getBody()));
                    assertEquals("INVENTORY_RETURN_EXCEEDS_ISSUED", response.getBody().get("reason"));
                    conflict++;
                }
            }
            assertEquals(1, ok);
            assertEquals(1, conflict);
        } finally {
            executor.shutdownNow();
        }

        long returnId = jdbc.queryForObject(
                "SELECT id FROM lab_inventory_return WHERE status='PENDING_CONFIRMATION'", Long.class);
        data(post("/api/inventory-returns/" + returnId + "/confirm", admin,
                "mysql-return-confirm", returnCommandBody(1)));
        assertEquals("19.000", jdbc.queryForObject(
                "SELECT on_hand_quantity FROM lab_component_inventory_balance WHERE component_id=?",
                String.class, componentId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_inventory_movement WHERE movement_type='RETURN_IN'", Integer.class));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void verifiesFiveRolePermissionMatrixOnMySql() {
        String admin = token(login("admin", "admin123"));
        String member = token(login("20260001", "20260001"));
        String teacher = token(login("teacher001", "admin123"));
        String stockkeeper = seedUser(101, "stockkeeper001", "库存管理员", 4);
        String unrelated = seedUser(102, "unrelated001", "无关成员", 2);

        assertReason(put("/api/inventory/issue-policy", member, policyBody(0)),
                HttpStatus.FORBIDDEN, "INVENTORY_POLICY_DENIED");
        assertReason(put("/api/inventory/issue-policy", stockkeeper, policyBody(0)),
                HttpStatus.FORBIDDEN, "INVENTORY_POLICY_DENIED");
        data(put("/api/inventory/issue-policy", admin, policyBody(0)));

        long requestId = ((Number) draftWithQuantity(member, "acl-create-1", "15.000")
                .get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "acl-submit-1", submitBody(1)));

        assertReason(post("/api/inventory-issue-requests/" + requestId + "/approve", member,
                        "acl-approve-1", decisionBody(2, "自批")),
                HttpStatus.FORBIDDEN, "INVENTORY_ISSUE_SELF_APPROVAL");
        assertReason(post("/api/inventory-issue-requests/" + requestId + "/approve", stockkeeper,
                        "acl-approve-2", decisionBody(2, "库管审批")),
                HttpStatus.FORBIDDEN, "INVENTORY_ISSUE_APPROVAL_DENIED");
        data(post("/api/inventory-issue-requests/" + requestId + "/approve", teacher,
                "acl-approve-3", decisionBody(2, "同意")));

        assertReason(post("/api/inventory-issue-requests/" + requestId + "/confirm-issue", teacher,
                        "acl-issue-1", confirmBody(3, 1, null)),
                HttpStatus.FORBIDDEN, "INVENTORY_ISSUE_FULFILL_DENIED");
        assertReason(post("/api/inventory-issue-requests/" + requestId + "/confirm-issue", member,
                        "acl-issue-2", confirmBody(3, 1, null)),
                HttpStatus.FORBIDDEN, "INVENTORY_ISSUE_FULFILL_DENIED");
        data(post("/api/inventory-issue-requests/" + requestId + "/confirm-issue", stockkeeper,
                "acl-issue-3", confirmBody(3, 1, null)));

        assertReason(get("/api/inventory-issue-requests/" + requestId, unrelated),
                HttpStatus.NOT_FOUND, "INVENTORY_ISSUE_REQUEST_NOT_FOUND");
    }

    private Map<String, Object> issueOne(String suffix) {
        String member = token(login("20260001", "20260001"));
        String admin = token(login("admin", "admin123"));
        Map<String, Object> created = data(post("/api/inventory-issue-requests", member,
                "rtn-create-" + suffix, requestBody()));
        long requestId = ((Number) created.get("id")).longValue();
        data(post("/api/inventory-issue-requests/" + requestId + "/submit", member,
                "rtn-submit-" + suffix, submitBody(1)));
        data(post("/api/inventory-issue-requests/" + requestId + "/confirm-issue", admin,
                "rtn-issue-" + suffix, confirmBody(2, 1, null)));
        long fulfillmentId = jdbc.queryForObject(
                "SELECT id FROM lab_inventory_fulfillment WHERE request_id=?", Long.class, requestId);
        long fulfillmentLineId = jdbc.queryForObject(
                "SELECT id FROM lab_inventory_fulfillment_line WHERE fulfillment_id=?",
                Long.class, fulfillmentId);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("requestId", requestId);
        ctx.put("fulfillmentId", fulfillmentId);
        ctx.put("fulfillmentLineId", fulfillmentLineId);
        ctx.put("member", member);
        ctx.put("admin", admin);
        return ctx;
    }

    private Map<String, Object> draftWithQuantity(String member, String key, String quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "并发领用申请");
        body.put("purpose", "并发一致性验证");
        body.put("lines", List.of(line(componentId, quantity)));
        return data(post("/api/inventory-issue-requests", member, key, body));
    }

    private ResponseEntity<Map> concurrentSubmit(long requestId, String token, String key,
                                                 CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return post("/api/inventory-issue-requests/" + requestId + "/submit", token, key,
                submitBody(1));
    }

    private ResponseEntity<Map> concurrentConfirmIssue(long requestId, String token, String key,
                                                       CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return post("/api/inventory-issue-requests/" + requestId + "/confirm-issue",
                token, key, confirmBody(2, 1, null));
    }

    private ResponseEntity<Map> concurrentReturn(long fulfillmentId, String token, String key,
                                                 long fulfillmentLineId, String quantity,
                                                 CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return post("/api/inventory-fulfillments/" + fulfillmentId + "/returns", token, key,
                returnCreateBody("并发退回", fulfillmentLineId, quantity));
    }

    private String seedUser(int id, String memberId, String username, int roleId) {
        jdbc.update("""
                INSERT INTO sys_user(
                    id,username,member_id,password,identity_id,group_id,faculty_id,status
                ) VALUES(?,?,?,?,1,1,1,1)
                """, id, username, memberId,
                "$2a$10$.D/2kDKLyVtgEABJGOO4dODj1TRUq9ZSwQ3jlAYSfbbwbR3FhLGLO");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", id, roleId);
        return token(login(memberId, "admin123"));
    }

    private long seedSecondBatch() {
        jdbc.update("""
                INSERT INTO lab_procurement_order(
                    purchase_no,title,requester_id,requester_name,currency,
                    item_subtotal,shipping_amount,discount_amount,payable_amount,
                    status,approval_required,approval_policy_version,created_by,updated_by
                ) VALUES('PUR-ISS-RACE','领用并发事实',1,'system','CNY',
                         10.00,0.00,0.00,10.00,'RECEIVED',0,1,1,1)
                """);
        Long orderId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_order WHERE purchase_no='PUR-ISS-RACE'", Long.class);
        jdbc.update("""
                INSERT INTO lab_procurement_order_line(
                    order_id,line_no,component_id,component_name,unit,
                    ordered_quantity,unit_price,line_subtotal,allocated_net_amount,
                    effective_unit_cost,received_quantity,version
                ) VALUES(?,1,?,'领用测试电阻','PCS',10.000,1.0000,10.00,10.00,1.0000,10.000,1)
                """, orderId, componentId);
        Long orderLineId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_order_line WHERE order_id=?", Long.class, orderId);
        jdbc.update("""
                INSERT INTO lab_procurement_receipt(
                    receipt_no,order_id,status,received_at,receiver_id,receiver_name,
                    confirmed_at,version,created_by,updated_by,active_draft_slot
                ) VALUES('REC-ISS-RACE',?,'CONFIRMED',CURRENT_TIMESTAMP,1,'system',
                         CURRENT_TIMESTAMP,1,1,1,NULL)
                """, orderId);
        Long receiptId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_receipt WHERE receipt_no='REC-ISS-RACE'", Long.class);
        jdbc.update("""
                INSERT INTO lab_procurement_receipt_line(
                    receipt_id,order_line_id,component_id,quantity,unit,version
                ) VALUES(?,?,?,10.000,'PCS',1)
                """, receiptId, orderLineId, componentId);
        Long receiptLineId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_receipt_line WHERE receipt_id=?", Long.class, receiptId);
        jdbc.update("""
                INSERT INTO lab_inventory_batch(
                    batch_no,component_id,source_receipt_line_id,
                    initial_quantity,current_quantity,unit,unit_cost,status,created_by
                ) VALUES('BAT-ISS-RACE',?,?,10.000,10.000,'PCS',1.0000,'AVAILABLE',1)
                """, componentId, receiptLineId);
        jdbc.update("UPDATE lab_component_inventory_balance "
                + "SET on_hand_quantity=on_hand_quantity+10.000, version=version+1 WHERE component_id=?",
                componentId);
        return jdbc.queryForObject(
                "SELECT id FROM lab_inventory_batch WHERE batch_no='BAT-ISS-RACE'", Long.class);
    }

    private Map<String, Object> returnCreateBody(String reason, long fulfillmentLineId,
                                                 String quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        body.put("lines", List.of(Map.of("fulfillmentLineId", fulfillmentLineId,
                "quantity", new BigDecimal(quantity))));
        return body;
    }

    private Map<String, Object> returnCommandBody(int version) {
        return Map.of("returnVersion", version);
    }

    private Map<String, Object> writeOffBody(String quantity, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("quantity", new BigDecimal(quantity));
        body.put("reason", reason);
        return body;
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

    private Map<String, Object> submitBody(int version) {
        return Map.of("requestVersion", version);
    }

    private Map<String, Object> decisionBody(int version, String comment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestVersion", version);
        body.put("comment", comment);
        return body;
    }

    private Map<String, Object> confirmBody(int requestVersion, int reservationVersion, String note) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestVersion", requestVersion);
        body.put("reservationVersion", reservationVersion);
        if (note != null) {
            body.put("note", note);
        }
        return body;
    }

    private void seedInventory() {
        jdbc.update("""
                INSERT INTO lab_procurement_order(
                    purchase_no,title,requester_id,requester_name,currency,
                    item_subtotal,shipping_amount,discount_amount,payable_amount,
                    status,approval_required,approval_policy_version,created_by,updated_by
                ) VALUES('PUR-ISS-TEST','领用库存事实',1,'system','CNY',
                         20.00,0.00,0.00,20.00,'RECEIVED',0,1,1,1)
                """);
        Long orderId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_order WHERE purchase_no='PUR-ISS-TEST'", Long.class);
        jdbc.update("""
                INSERT INTO lab_procurement_order_line(
                    order_id,line_no,component_id,component_name,unit,
                    ordered_quantity,unit_price,line_subtotal,allocated_net_amount,
                    effective_unit_cost,received_quantity,version
                ) VALUES(?,1,?,'领用测试电阻','PCS',20.000,1.0000,20.00,20.00,1.0000,20.000,1)
                """, orderId, componentId);
        Long orderLineId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_order_line WHERE order_id=?", Long.class, orderId);
        jdbc.update("""
                INSERT INTO lab_procurement_receipt(
                    receipt_no,order_id,status,received_at,receiver_id,receiver_name,
                    confirmed_at,version,created_by,updated_by,active_draft_slot
                ) VALUES('REC-ISS-TEST',?,'CONFIRMED',CURRENT_TIMESTAMP,1,'system',
                         CURRENT_TIMESTAMP,1,1,1,NULL)
                """, orderId);
        Long receiptId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_receipt WHERE receipt_no='REC-ISS-TEST'", Long.class);
        jdbc.update("""
                INSERT INTO lab_procurement_receipt_line(
                    receipt_id,order_line_id,component_id,quantity,unit,version
                ) VALUES(?,?,?,20.000,'PCS',1)
                """, receiptId, orderLineId, componentId);
        Long receiptLineId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_receipt_line WHERE receipt_id=?", Long.class, receiptId);
        jdbc.update("""
                INSERT INTO lab_component_inventory_balance(
                    component_id,on_hand_quantity,unit,version,update_time
                ) VALUES(?,20.000,'PCS',1,CURRENT_TIMESTAMP)
                """, componentId);
        jdbc.update("""
                INSERT INTO lab_inventory_batch(
                    batch_no,component_id,source_receipt_line_id,
                    initial_quantity,current_quantity,unit,unit_cost,status,created_by
                ) VALUES('BAT-ISS-001',?,?,20.000,20.000,'PCS',1.0000,'AVAILABLE',1)
                """, componentId, receiptLineId);
        Long batchId = jdbc.queryForObject(
                "SELECT id FROM lab_inventory_batch WHERE batch_no='BAT-ISS-001'", Long.class);
        jdbc.update("""
                INSERT INTO lab_inventory_movement(
                    movement_no,movement_type,component_id,batch_id,source_type,source_id,
                    quantity_delta,balance_before,balance_after,unit,unit_cost,
                    operator_id,occurred_at,request_id
                ) VALUES('MOV-ISS-001','PROCUREMENT_RECEIPT_IN',?,?,'RECEIPT_LINE',?,
                         20.000,0.000,20.000,'PCS',1.0000,1,CURRENT_TIMESTAMP,'iss-test-fact')
                """, componentId, batchId, receiptLineId);
    }

    private void seedTeacher() {
        jdbc.update("""
                INSERT INTO sys_user(
                    id,username,member_id,password,identity_id,group_id,faculty_id,status
                ) VALUES(100,'审批老师','teacher001',
                         '$2a$10$.D/2kDKLyVtgEABJGOO4dODj1TRUq9ZSwQ3jlAYSfbbwbR3FhLGLO',1,1,1,1)
                """);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(100,3)");
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
