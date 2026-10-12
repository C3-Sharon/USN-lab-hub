package com.usn.labhub.user.learning;

import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.common.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:week6-template-base-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ExperimentTemplateBaseApiIntegrationTest {
    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtils jwtUtils;

    @BeforeEach
    void cleanTemplateData() {
        jdbc.update("DELETE FROM lab_experiment_template_version");
        jdbc.update("DELETE FROM lab_experiment_template");
        jdbc.update("DELETE FROM lab_project_member WHERE project_id > 1");
        jdbc.update("DELETE FROM lab_project WHERE id > 1");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 2 AND role_id IN (3, 4)");
        jdbc.update("INSERT IGNORE INTO sys_user_role(user_id, role_id) VALUES (2, 2)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminCreatesSystemTemplateAndInitialDraftAtomically() {
        String adminToken = token(1L, "admin");
        ResponseEntity<Map> response = create(adminToken, templateBody("系统电压实验", "SYSTEM", null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> data = data(response);
        Long templateId = number(data.get("id"));
        assertEquals("SYSTEM", data.get("scope"));
        assertEquals(0, data.get("publishedVersionCount"));
        assertFalse(data.containsKey("hasDraft"));
        assertNull(data.get("latestVersion"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lab_experiment_template WHERE id = ?",
                Integer.class, templateId));
        assertEquals("0.1.0:DRAFT:{}", jdbc.queryForObject("""
                SELECT CONCAT(version, ':', status, ':', schema_json)
                FROM lab_experiment_template_version WHERE template_id = ?
                """, String.class, templateId));
        assertEquals(Boolean.TRUE,
                data(get("/api/experiment/templates/" + templateId, adminToken)).get("hasDraft"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordinaryMemberCannotSeeDraftButCanSeePublishedWithoutDraftFlag() {
        String adminToken = token(1L, "admin");
        Long templateId = number(data(create(adminToken,
                templateBody("发布可见模板", "SYSTEM", null))).get("id"));
        String memberToken = token(2L, "20260001");

        ResponseEntity<Map> hidden = get("/api/experiment/templates/" + templateId, memberToken);
        assertEquals(HttpStatus.NOT_FOUND, hidden.getStatusCode());
        assertEquals("TEMPLATE_NOT_FOUND", hidden.getBody().get("reason"));

        publishDraftDirectly(templateId);
        Map<String, Object> visible = data(get("/api/experiment/templates/" + templateId, memberToken));
        assertEquals("1.0.0", visible.get("latestVersion"));
        assertFalse(visible.containsKey("hasDraft"));

        Map<String, Object> page = data(get("/api/experiment/templates?status=ALL", memberToken));
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("list");
        assertTrue(items.stream().anyMatch(item -> templateId.equals(number(item.get("id")))));
        assertTrue(items.stream().noneMatch(item -> item.containsKey("hasDraft")));
    }

    @Test
    void ownerCanManageProjectTemplateButMemberAndNonMemberCannot() {
        String adminToken = token(1L, "admin");
        String memberToken = token(2L, "20260001");
        Long ownerProjectId = createProject(memberToken, "TPL-OWNER");

        Long ownedTemplateId = number(data(create(memberToken,
                templateBody("项目负责人模板", "PROJECT", ownerProjectId))).get("id"));
        ResponseEntity<Map> updated = update(memberToken, ownedTemplateId,
                Map.of("title", "项目负责人模板已更新", "category", "校准"));
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals("项目负责人模板已更新", data(updated).get("title"));

        Long adminProjectId = createProject(adminToken, "TPL-ADMIN");
        Long hiddenTemplateId = number(data(create(adminToken,
                templateBody("管理员项目模板", "PROJECT", adminProjectId))).get("id"));
        assertEquals(HttpStatus.NOT_FOUND,
                get("/api/experiment/templates/" + hiddenTemplateId, memberToken).getStatusCode());

        jdbc.update("INSERT INTO lab_project_member(project_id, user_id, project_role, joined_at) VALUES (?, 2, 'MEMBER', CURRENT_TIMESTAMP)",
                adminProjectId);
        ResponseEntity<Map> denied = create(memberToken,
                templateBody("普通成员越权模板", "PROJECT", adminProjectId));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("TEMPLATE_OPERATION_DENIED", denied.getBody().get("reason"));

        jdbc.update("UPDATE lab_project_member SET project_role = 'MAINTAINER' WHERE project_id = ? AND user_id = 2",
                adminProjectId);
        assertEquals(HttpStatus.OK, create(memberToken,
                templateBody("维护者项目模板", "PROJECT", adminProjectId)).getStatusCode());
    }

    @Test
    void teacherCanManageSystemTemplatesAndStockKeeperCannot() {
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2, 3)");
        String teacherToken = token(2L, "20260001");
        assertEquals(HttpStatus.OK,
                create(teacherToken, templateBody("教师系统模板", "SYSTEM", null)).getStatusCode());

        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 2 AND role_id IN (2, 3)");
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2, 4)");
        String stockToken = token(2L, "20260001");
        ResponseEntity<Map> denied = create(stockToken,
                templateBody("库存管理员系统模板", "SYSTEM", null));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("TEMPLATE_OPERATION_DENIED", denied.getBody().get("reason"));

        assertEquals(HttpStatus.UNAUTHORIZED,
                rest.getForEntity("/api/experiment/templates", Map.class).getStatusCode());
    }

    private void publishDraftDirectly(Long templateId) {
        Long versionId = jdbc.queryForObject(
                "SELECT id FROM lab_experiment_template_version WHERE template_id = ?", Long.class, templateId);
        jdbc.update("""
                UPDATE lab_experiment_template_version
                SET version = '1.0.0', version_code = 10000, status = 'PUBLISHED', draft_slot = NULL,
                    published_by = 1, published_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, versionId);
        jdbc.update("""
                UPDATE lab_experiment_template
                SET latest_version_id = ?, latest_version = '1.0.0', published_version_count = 1
                WHERE id = ?
                """, versionId, templateId);
    }

    private Long createProject(String token, String code) {
        ResponseEntity<Map> response = rest.exchange("/api/projects", HttpMethod.POST,
                authorized(token, Map.of("code", code, "name", code + " 项目")), Map.class);
        return number(data(response).get("id"));
    }

    private Map<String, Object> templateBody(String title, String scope, Long projectId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("scope", scope);
        body.put("projectId", projectId);
        body.put("category", "电路测试");
        return body;
    }

    private ResponseEntity<Map> create(String token, Map<String, Object> body) {
        return rest.exchange("/api/experiment/templates", HttpMethod.POST, authorized(token, body), Map.class);
    }

    private ResponseEntity<Map> update(String token, Long templateId, Map<String, Object> body) {
        return rest.exchange("/api/experiment/templates/" + templateId,
                HttpMethod.PUT, authorized(token, body), Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, authorized(token), Map.class);
    }

    private String token(Long userId, String memberId) {
        return jwtUtils.createToken(memberId, "IGNORED", userId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return (Map<String, Object>) response.getBody().get("data");
    }

    private Long number(Object value) {
        return ((Number) value).longValue();
    }

    private HttpEntity<Void> authorized(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<String, Object>> authorized(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
