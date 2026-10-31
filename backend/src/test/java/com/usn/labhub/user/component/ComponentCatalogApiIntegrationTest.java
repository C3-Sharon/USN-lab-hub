package com.usn.labhub.user.component;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:w44-component-catalog;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ComponentCatalogApiIntegrationTest {
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private IAttendanceService attendanceService;

    @BeforeEach
    void setUp() {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayStatus(0);
        attendance.setTodayRecords(List.of());
        attendance.setWeekHours(0.0);
        attendance.setSemesterHours(0.0);
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);

        jdbc.update("DELETE FROM lab_component_catalog_audit_log");
        jdbc.update("DELETE FROM lab_request_idempotency WHERE operation LIKE '%COMPONENT%'");
        jdbc.update("DELETE FROM lab_media_object");
        jdbc.update("DELETE FROM lab_component_spec_candidate");
        jdbc.update("DELETE FROM lab_component_spec_snapshot");
        jdbc.update("DELETE FROM lab_component");
        jdbc.update("DELETE FROM lab_component_manufacturer");
        jdbc.update("DELETE FROM lab_component_category");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=2 AND role_id IN (3,4)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsCatalogWithNormalizedModelPlaceholderAndExactReplay() {
        String admin = token(login("admin", "admin123"));
        Map<String, Object> category = data(post("/api/component-categories", admin, "cat-create-1",
                Map.of("code", "SEMICONDUCTOR", "name", "半导体", "sortOrder", 10)));
        Map<String, Object> child = data(post("/api/component-categories", admin, "cat-create-child",
                Map.of("code", "ANALOG_IC", "name", "模拟芯片", "parentId", category.get("id"), "sortOrder", 1)));
        Map<String, Object> grandchild = data(post("/api/component-categories", admin, "cat-create-grandchild",
                Map.of("code", "AMPLIFIER", "name", "放大器", "parentId", child.get("id"), "sortOrder", 1)));
        ResponseEntity<Map> tooDeep = post("/api/component-categories", admin, "cat-create-too-deep",
                Map.of("code", "OP_AMP", "name", "运算放大器", "parentId", grandchild.get("id"), "sortOrder", 1));
        assertEquals(HttpStatus.CONFLICT, tooDeep.getStatusCode());
        assertEquals("CATEGORY_DEPTH_EXCEEDED", tooDeep.getBody().get("reason"));
        Map<String, Object> manufacturer = data(post("/api/component-manufacturers", admin, "mfr-create-1",
                Map.of("code", "TI", "name", "Texas Instruments", "website", "https://www.ti.com")));

        Map<String, Object> body = componentBody(number(category.get("id")), number(manufacturer.get("id")),
                " OPA-333 AIDBVR ");
        Map<String, Object> component = data(post("/api/components", admin, "component-create-1", body));
        assertEquals("DRAFT", component.get("status"));
        assertEquals("OPA-333 AIDBVR", component.get("normalizedMpn"));
        assertEquals("PLACEHOLDER", component.get("imageState"));
        assertEquals("component:SEMICONDUCTOR:SOT:O", component.get("placeholderKey"));
        assertTrue(component.get("catalogCode").toString().matches("CMP-\\d{8}"));

        Map<String, Object> replay = data(post("/api/components", admin, "component-create-1", body));
        assertEquals(component.get("id"), replay.get("id"));
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));

        Map<String, Object> page = data(get("/api/components?status=DRAFT&sortBy=mpn&sortOrder=asc", admin));
        assertEquals(1, ((Number) page.get("total")).intValue());
        assertEquals(1, ((List<?>) page.get("list")).size());
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM lab_component_catalog_audit_log", Integer.class));

        ResponseEntity<Map> duplicate = post("/api/components", admin, "component-create-2", body);
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        assertEquals("COMPONENT_DUPLICATE_MODEL", duplicate.getBody().get("reason"));
        assertNotNull(((Map<?, ?>) duplicate.getBody().get("data")).get("existingComponentId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void enforcesMemberTeacherAndManagerVisibilityAndWritePermissions() {
        String admin = token(login("admin", "admin123"));
        String member = token(login("20260001", "20260001"));
        ResponseEntity<Map> unauthenticated = rest.getForEntity("/api/component-categories", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
        assertEquals("TOKEN_MISSING", unauthenticated.getBody().get("reason"));
        Map<String, Object> category = data(post("/api/component-categories", admin, "cat-create-2",
                Map.of("code", "MODULE", "name", "功能模块", "sortOrder", 1)));
        Map<String, Object> manufacturer = data(post("/api/component-manufacturers", admin, "mfr-create-2",
                Map.of("code", "USN", "name", "USN Lab")));
        Map<String, Object> component = data(post("/api/components", admin, "component-create-3",
                componentBody(number(category.get("id")), number(manufacturer.get("id")), "USN-MOD-01")));
        Long componentId = number(component.get("id"));

        assertEquals(0, ((Number) data(get("/api/components", member)).get("total")).intValue());
        ResponseEntity<Map> hidden = get("/api/components/" + componentId, member);
        assertEquals(HttpStatus.NOT_FOUND, hidden.getStatusCode());
        assertEquals("COMPONENT_NOT_FOUND", hidden.getBody().get("reason"));

        ResponseEntity<Map> denied = post("/api/component-manufacturers", member, "member-write",
                Map.of("code", "NOPE", "name", "Denied"));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("COMPONENT_OPERATION_DENIED", denied.getBody().get("reason"));

        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2,3)");
        try {
            assertEquals(componentId, number(data(get("/api/components/" + componentId, member)).get("id")));
            ResponseEntity<Map> teacherDenied = put("/api/components/" + componentId, member,
                    componentUpdateBody(component, 1));
            assertEquals(HttpStatus.FORBIDDEN, teacherDenied.getStatusCode());
            assertEquals("COMPONENT_OPERATION_DENIED", teacherDenied.getBody().get("reason"));
        } finally {
            jdbc.update("DELETE FROM sys_user_role WHERE user_id=2 AND role_id=3");
        }

        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2,4)");
        try {
            Map<String, Object> stockCreated = data(post("/api/component-manufacturers", member,
                    "stock-write", Map.of("code", "STOCK_OK", "name", "库存管理员可写")));
            assertEquals("STOCK_OK", stockCreated.get("code"));
        } finally {
            jdbc.update("DELETE FROM sys_user_role WHERE user_id=2 AND role_id=4");
        }
    }

    @Test
    void enforcesSpecificationVersionAndReferenceStateBoundaries() {
        String admin = token(login("admin", "admin123"));
        Map<String, Object> category = data(post("/api/component-categories", admin, "cat-create-3",
                Map.of("code", "SENSOR", "name", "传感器", "sortOrder", 1)));
        Map<String, Object> manufacturer = data(post("/api/component-manufacturers", admin, "mfr-create-3",
                Map.of("code", "BOSCH", "name", "Bosch")));
        Map<String, Object> component = data(post("/api/components", admin, "component-create-4",
                componentBody(number(category.get("id")), number(manufacturer.get("id")), "BME280")));
        Long componentId = number(component.get("id"));

        ResponseEntity<Map> noSpec = post("/api/components/" + componentId + "/activate", admin,
                "activate-no-spec", Map.of("version", 1));
        assertEquals(HttpStatus.CONFLICT, noSpec.getStatusCode());
        assertEquals("COMPONENT_SPEC_REQUIRED", noSpec.getBody().get("reason"));

        seedSnapshot(componentId);
        Map<String, Object> active = data(post("/api/components/" + componentId + "/activate", admin,
                "activate-with-spec", Map.of("version", 1)));
        assertEquals("ACTIVE", active.get("status"));
        assertEquals(2, ((Number) active.get("version")).intValue());

        ResponseEntity<Map> stale = put("/api/components/" + componentId, admin,
                componentUpdateBody(active, 1));
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());
        assertEquals("VERSION_CONFLICT", stale.getBody().get("reason"));

        ResponseEntity<Map> categoryInUse = post("/api/component-categories/" + category.get("id")
                        + "/deactivate", admin, "category-in-use",
                Map.of("version", 1, "reason", "测试引用保护"));
        assertEquals(HttpStatus.CONFLICT, categoryInUse.getStatusCode());
        assertEquals("CATEGORY_IN_USE", categoryInUse.getBody().get("reason"));

        ResponseEntity<Map> invalidSort = get("/api/components?sortBy=drop_table", admin);
        assertEquals(HttpStatus.BAD_REQUEST, invalidSort.getStatusCode());
        assertEquals("INVALID_PARAMETER", invalidSort.getBody().get("reason"));
    }

    private void seedSnapshot(Long componentId) {
        jdbc.update("""
                INSERT INTO lab_component_spec_snapshot(
                    component_id,snapshot_version,source_type,source_note,raw_specs_json,
                    specifications_json,change_summary,checksum,confirmed_by,confirmed_at)
                VALUES (?,1,'MANUAL','B2 activation fixture','{}','[]','Initial fixture',?,1,CURRENT_TIMESTAMP)
                """, componentId, "a".repeat(64));
        Long snapshotId = jdbc.queryForObject(
                "SELECT id FROM lab_component_spec_snapshot WHERE component_id=?", Long.class, componentId);
        jdbc.update("UPDATE lab_component SET current_spec_snapshot_id=?, spec_version=1 WHERE id=?",
                snapshotId, componentId);
    }

    private Map<String, Object> componentBody(Long categoryId, Long manufacturerId, String mpn) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "测试元器件");
        body.put("manufacturerId", manufacturerId);
        body.put("mpn", mpn);
        body.put("categoryId", categoryId);
        body.put("packageType", "SOT-23-5");
        body.put("baseUnit", "PCS");
        body.put("description", "B2 集成测试");
        return body;
    }

    private Map<String, Object> componentUpdateBody(Map<String, Object> current, int version) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", current.get("name"));
        body.put("manufacturerId", current.get("manufacturerId"));
        body.put("mpn", current.get("mpn"));
        body.put("categoryId", current.get("categoryId"));
        body.put("packageType", current.get("packageType"));
        body.put("baseUnit", current.get("baseUnit"));
        body.put("description", "更新后的描述");
        body.put("version", version);
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
