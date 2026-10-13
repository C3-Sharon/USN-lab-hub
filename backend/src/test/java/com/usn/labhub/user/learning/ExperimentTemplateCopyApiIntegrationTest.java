package com.usn.labhub.user.learning;

import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.common.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:week6-template-copy-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ExperimentTemplateCopyApiIntegrationTest {
    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtils jwtUtils;

    @BeforeEach
    void cleanData() {
        jdbc.update("DELETE FROM lab_experiment_template_version");
        jdbc.update("DELETE FROM lab_experiment_template");
        jdbc.update("DELETE FROM lab_project_member WHERE project_id > 1");
        jdbc.update("DELETE FROM lab_project WHERE id > 1");
    }

    @Test
    void ownerCopiesLatestPublishedSchemaIntoIndependentProjectDraft() {
        String adminToken = token(1L, "admin");
        String memberToken = token(2L, "20260001");
        Long sourceId = createTemplate(adminToken, "系统电压模板", "SYSTEM", null,
                "系统说明", "电路测试");
        publishSource(sourceId, "{\"schemaVersion\":\"1.0\",\"type\":\"object\","
                + "\"properties\":{\"voltage\":{\"type\":\"number\",\"title\":\"电压\",\"displayOrder\":1}}}");
        Long projectId = createProject(memberToken, "TPL-COPY-OWNER");

        ResponseEntity<Map> response = copy(memberToken, sourceId,
                Map.of("targetProjectId", projectId, "description", "项目校准说明"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("复制成功", response.getBody().get("msg"));
        Map<String, Object> copied = data(response);
        Long copiedId = number(copied.get("id"));
        assertEquals("系统电压模板（TPL-COPY-OWNER 项目）", copied.get("title"));
        assertEquals("项目校准说明", copied.get("description"));
        assertEquals("PROJECT", copied.get("scope"));
        assertEquals(projectId, number(copied.get("projectId")));
        assertEquals("电路测试", copied.get("category"));
        assertEquals(0, copied.get("publishedVersionCount"));
        assertNull(copied.get("latestVersion"));
        assertFalse(copied.containsKey("hasDraft"));

        String sourceSchema = schemaOf(sourceId);
        assertEquals(sourceSchema, schemaOf(copiedId));
        jdbc.update("UPDATE lab_experiment_template_version SET schema_json = '{}' WHERE template_id = ?",
                copiedId);
        assertEquals(sourceSchema, schemaOf(sourceId));
    }

    @Test
    void globalManagerCopiesUnpublishedSystemTemplateAsEmptyDraft() {
        String adminToken = token(1L, "admin");
        Long sourceId = createTemplate(adminToken, "未发布系统模板", "SYSTEM", null,
                "继承说明", "入门");
        Long projectId = createProject(token(2L, "20260001"), "TPL-COPY-GLOBAL");

        ResponseEntity<Map> response = copy(adminToken, sourceId,
                Map.of("targetProjectId", projectId, "title", "管理员项目副本"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> copied = data(response);
        assertEquals("管理员项目副本", copied.get("title"));
        assertEquals("继承说明", copied.get("description"));
        assertEquals("{}", schemaOf(number(copied.get("id"))));
    }

    @Test
    void copyRejectsInvisibleSourceInsufficientTargetRoleAndProjectSource() {
        String adminToken = token(1L, "admin");
        String memberToken = token(2L, "20260001");
        Long unpublishedSourceId = createTemplate(adminToken, "隐藏系统模板", "SYSTEM", null,
                null, null);
        Long ownerProjectId = createProject(memberToken, "TPL-COPY-HIDDEN");
        ResponseEntity<Map> hidden = copy(memberToken, unpublishedSourceId,
                Map.of("targetProjectId", ownerProjectId));
        assertEquals(HttpStatus.NOT_FOUND, hidden.getStatusCode());
        assertEquals("TEMPLATE_NOT_FOUND", hidden.getBody().get("reason"));

        Long publishedSourceId = createTemplate(adminToken, "公开系统模板", "SYSTEM", null,
                null, null);
        publishSource(publishedSourceId, "{\"schemaVersion\":\"1.0\",\"type\":\"object\","
                + "\"properties\":{\"ok\":{\"type\":\"boolean\",\"title\":\"通过\",\"displayOrder\":1}}}");
        Long adminProjectId = createProject(adminToken, "TPL-COPY-DENIED");
        jdbc.update("""
                INSERT INTO lab_project_member(project_id, user_id, project_role, joined_at)
                VALUES (?, 2, 'MEMBER', CURRENT_TIMESTAMP)
                """, adminProjectId);
        ResponseEntity<Map> denied = copy(memberToken, publishedSourceId,
                Map.of("targetProjectId", adminProjectId));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("TEMPLATE_OPERATION_DENIED", denied.getBody().get("reason"));

        Long projectSourceId = createTemplate(memberToken, "项目源模板", "PROJECT", ownerProjectId,
                null, null);
        ResponseEntity<Map> wrongScope = copy(memberToken, projectSourceId,
                Map.of("targetProjectId", ownerProjectId));
        assertEquals(HttpStatus.BAD_REQUEST, wrongScope.getStatusCode());
        assertEquals("TEMPLATE_NOT_SYSTEM", wrongScope.getBody().get("reason"));
    }

    private Long createTemplate(String token, String title, String scope, Long projectId,
                                String description, String category) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("scope", scope);
        body.put("projectId", projectId);
        body.put("description", description);
        body.put("category", category);
        return number(data(post("/api/experiment/templates", token, body)).get("id"));
    }

    private Long createProject(String token, String code) {
        return number(data(post("/api/projects", token,
                Map.of("code", code, "name", code + " 项目"))).get("id"));
    }

    private void publishSource(Long templateId, String schemaJson) {
        Long versionId = jdbc.queryForObject("""
                SELECT id FROM lab_experiment_template_version
                WHERE template_id = ? AND status = 'DRAFT'
                """, Long.class, templateId);
        jdbc.update("""
                UPDATE lab_experiment_template_version
                SET version = '1.0.0', version_code = 10000, status = 'PUBLISHED', draft_slot = NULL,
                    schema_json = ?, field_count = 1, published_by = 1, published_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, schemaJson, versionId);
        jdbc.update("""
                UPDATE lab_experiment_template
                SET latest_version_id = ?, latest_version = '1.0.0', published_version_count = 1
                WHERE id = ?
                """, versionId, templateId);
    }

    private String schemaOf(Long templateId) {
        return jdbc.queryForObject("""
                SELECT schema_json FROM lab_experiment_template_version
                WHERE template_id = ? ORDER BY id DESC LIMIT 1
                """, String.class, templateId);
    }

    private ResponseEntity<Map> copy(String token, Long templateId, Map<String, Object> body) {
        return post("/api/experiment/templates/" + templateId + "/copy", token, body);
    }

    private ResponseEntity<Map> post(String path, String token, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.POST, authorized(token, body), Map.class);
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

    private HttpEntity<Map<String, Object>> authorized(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
