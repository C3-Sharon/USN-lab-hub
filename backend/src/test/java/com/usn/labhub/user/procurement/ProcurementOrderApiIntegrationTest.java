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
                "spring.datasource.url=${USNHUB_TEST_MYSQL_URL:jdbc:h2:mem:w45-procurement-order;MODE=MySQL;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${USNHUB_TEST_MYSQL_USERNAME:sa}",
                "spring.datasource.password=${USNHUB_TEST_MYSQL_PASSWORD:}",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ProcurementOrderApiIntegrationTest {
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
        jdbc.update("DELETE FROM lab_procurement_order_line");
        jdbc.update("DELETE FROM lab_procurement_order");
        jdbc.update("DELETE FROM lab_request_idempotency WHERE operation LIKE '%procurement-orders%'");
        jdbc.update("DELETE FROM lab_component_spec_snapshot");
        jdbc.update("DELETE FROM lab_component");
        jdbc.update("DELETE FROM lab_component_manufacturer");
        jdbc.update("DELETE FROM lab_component_category");
        jdbc.update("DELETE FROM lab_project_member WHERE project_id NOT IN (SELECT id FROM lab_project WHERE project_code='IOT-POWER-001')");
        jdbc.update("DELETE FROM lab_project WHERE project_code LIKE 'PROC-%'");
        seedComponent();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsCalculatesReplaysListsAndUpdatesDraft() {
        String member = token(login("20260001", "20260001"));
        Map<String, Object> body = orderBody();
        String maxLengthKey = "k".repeat(128);

        Map<String, Object> created = data(post("/api/procurement-orders", member, maxLengthKey, body));
        assertTrue(created.get("purchaseNo").toString().matches("PO-\\d{8}-\\d{6}"));
        assertEquals("DRAFT", created.get("status"));
        assertMoney("470.0", created.get("itemSubtotal"));
        assertMoney("477.0", created.get("payableAmount"));
        assertEquals(2, ((List<?>) created.get("lines")).size());
        assertEquals(1, ((Number) created.get("version")).intValue());

        Map<String, Object> replay = data(post("/api/procurement-orders", member, maxLengthKey, body));
        assertEquals(created.get("id"), replay.get("id"));
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lab_procurement_order", Integer.class));

        Map<String, Object> page = data(get("/api/procurement-orders?sortBy=payableAmount&sortOrder=asc", member));
        assertEquals(1, ((Number) page.get("total")).intValue());
        assertEquals(1, ((List<?>) page.get("list")).size());

        Map<String, Object> update = orderBody();
        update.put("title", "更新后的采购草稿");
        update.put("shippingAmount", new BigDecimal("0.00"));
        update.put("discountAmount", new BigDecimal("20.00"));
        update.put("version", 1);
        Map<String, Object> updated = data(put("/api/procurement-orders/" + created.get("id"), member, update));
        assertEquals("更新后的采购草稿", updated.get("title"));
        assertMoney("450.0", updated.get("payableAmount"));
        assertEquals(2, ((Number) updated.get("version")).intValue());

        ResponseEntity<Map> stale = put("/api/procurement-orders/" + created.get("id"), member, update);
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());
        assertEquals("VERSION_CONFLICT", stale.getBody().get("reason"));
        assertNotNull(((Map<?, ?>) stale.getBody().get("data")).get("traceId"));
    }

    @Test
    void enforcesBusinessValidationAndDraftBoundary() {
        String member = token(login("20260001", "20260001"));

        Map<String, Object> noLines = orderBody();
        noLines.put("lines", List.of());
        assertReason(post("/api/procurement-orders", member, "no-lines-0001", noLines),
                HttpStatus.BAD_REQUEST, "PROCUREMENT_LINES_REQUIRED");

        Map<String, Object> duplicate = orderBody();
        duplicate.put("lines", List.of(line(componentId, "1.000", "2.0000"),
                line(componentId, "2.000", "3.0000")));
        assertReason(post("/api/procurement-orders", member, "duplicate-lines-01", duplicate),
                HttpStatus.BAD_REQUEST, "PROCUREMENT_COMPONENT_INVALID");

        Map<String, Object> badQuantity = orderBody();
        badQuantity.put("lines", List.of(line(componentId, "1.0001", "2.0000")));
        assertReason(post("/api/procurement-orders", member, "bad-quantity-001", badQuantity),
                HttpStatus.BAD_REQUEST, "PROCUREMENT_QUANTITY_INVALID");

        Map<String, Object> negativePayable = orderBody();
        negativePayable.put("discountAmount", new BigDecimal("9999.00"));
        assertReason(post("/api/procurement-orders", member, "bad-amount-00001", negativePayable),
                HttpStatus.BAD_REQUEST, "PROCUREMENT_AMOUNT_INVALID");

        assertReason(post("/api/procurement-orders", member, "short", orderBody()),
                HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");

        Map<String, Object> created = data(post("/api/procurement-orders", member,
                "draft-boundary-01", orderBody()));
        jdbc.update("UPDATE lab_procurement_order SET status='APPROVED' WHERE id=?", created.get("id"));
        Map<String, Object> update = orderBody();
        update.put("version", 1);
        assertReason(put("/api/procurement-orders/" + created.get("id"), member, update),
                HttpStatus.CONFLICT, "PROCUREMENT_INVALID_TRANSITION");
    }

    @Test
    @SuppressWarnings("unchecked")
    void scopesVisibilityAndRequiresProjectOwnerForAssociation() {
        String admin = token(login("admin", "admin123"));
        String member = token(login("20260001", "20260001"));
        Map<String, Object> adminOrder = data(post("/api/procurement-orders", admin,
                "admin-order-0001", orderBody()));
        assertEquals(0, ((Number) data(get("/api/procurement-orders", member)).get("total")).intValue());
        assertReason(get("/api/procurement-orders/" + adminOrder.get("id"), member),
                HttpStatus.NOT_FOUND, "PROCUREMENT_ORDER_NOT_FOUND");
        assertEquals(1, ((Number) data(get("/api/procurement-orders", admin)).get("total")).intValue());

        Long projectId = seedProject();
        Map<String, Object> linked = orderBody();
        linked.put("projectId", projectId);
        assertReason(post("/api/procurement-orders", member, "project-denied-01", linked),
                HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND");

        jdbc.update("UPDATE lab_project_member SET project_role='OWNER' WHERE project_id=? AND user_id=2", projectId);
        Map<String, Object> ownProjectOrder = data(post("/api/procurement-orders", member,
                "project-owner-001", linked));
        assertEquals(projectId.longValue(), ((Number) ownProjectOrder.get("projectId")).longValue());
        assertEquals("采购权限测试项目", ownProjectOrder.get("projectName"));
    }

    private void seedComponent() {
        jdbc.update("""
                INSERT INTO lab_component_category(
                    code,name,normalized_name,parent_scope_id,level,path,created_by,updated_by
                ) VALUES('PROC_API','采购测试','采购测试',0,1,'/PROC_API/',1,1)
                """);
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM lab_component_category WHERE code='PROC_API'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component_manufacturer(code,name,normalized_name,created_by,updated_by)
                VALUES('PROC_MFR','采购制造商','采购制造商',1,1)
                """);
        Long manufacturerId = jdbc.queryForObject(
                "SELECT id FROM lab_component_manufacturer WHERE code='PROC_MFR'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component(
                    catalog_code,name,manufacturer_id,mpn,normalized_mpn,category_id,
                    package_type,base_unit,status,created_by,updated_by
                ) VALUES('CMP-PROC-001','测试电阻',?,'R-001','R-001',?,'0603','PCS','ACTIVE',1,1)
                """, manufacturerId, categoryId);
        componentId = jdbc.queryForObject(
                "SELECT id FROM lab_component WHERE catalog_code='CMP-PROC-001'", Long.class);
    }

    private Long seedProject() {
        jdbc.update("""
                INSERT INTO lab_project(
                    project_code,project_name,description,owner_id,status,public_visible,category,
                    create_time,update_time
                ) VALUES('PROC-ACCESS','采购权限测试项目','B2',1,'ACTIVE',0,'HARDWARE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        Long projectId = jdbc.queryForObject(
                "SELECT id FROM lab_project WHERE project_code='PROC-ACCESS'", Long.class);
        jdbc.update("""
                INSERT INTO lab_project_member(project_id,user_id,project_role,joined_at)
                VALUES(?,2,'MEMBER',CURRENT_TIMESTAMP)
                """, projectId);
        return projectId;
    }

    private Map<String, Object> orderBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "实验室元器件采购");
        body.put("fundingSource", "实验室开放基金");
        body.put("shippingAmount", new BigDecimal("12.00"));
        body.put("discountAmount", new BigDecimal("5.00"));
        body.put("lines", List.of(line(componentId, "20.000", "8.5000"),
                line(seedSecondComponent(), "5.000", "60.0000")));
        return body;
    }

    private Long seedSecondComponent() {
        Long existing = jdbc.query("SELECT id FROM lab_component WHERE catalog_code='CMP-PROC-002'",
                result -> result.next() ? result.getLong(1) : null);
        if (existing != null) {
            return existing;
        }
        Long manufacturerId = jdbc.queryForObject(
                "SELECT id FROM lab_component_manufacturer WHERE code='PROC_MFR'", Long.class);
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM lab_component_category WHERE code='PROC_API'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component(
                    catalog_code,name,manufacturer_id,mpn,normalized_mpn,category_id,
                    package_type,base_unit,status,created_by,updated_by
                ) VALUES('CMP-PROC-002','测试模块',?,'M-002','M-002',?,'MODULE','PCS','ACTIVE',1,1)
                """, manufacturerId, categoryId);
        return jdbc.queryForObject("SELECT id FROM lab_component WHERE catalog_code='CMP-PROC-002'", Long.class);
    }

    private Map<String, Object> line(Long id, String quantity, String price) {
        return Map.of("componentId", id, "orderedQuantity", new BigDecimal(quantity),
                "unitPrice", new BigDecimal(price));
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

    private void assertMoney(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));
    }
}
