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
                "spring.datasource.url=jdbc:h2:mem:week6-template-version-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ExperimentTemplateVersionApiIntegrationTest {
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
    @SuppressWarnings("unchecked")
    void managerEditsDraftReadsSchemaAndValidatesPreviewData() {
        String adminToken = token(1L, "admin");
        Long templateId = createSystemTemplate(adminToken, "电压记录模板");
        Long draftId = draftId(templateId);

        ResponseEntity<Map> updated = put(versionPath(templateId, draftId), adminToken,
                Map.of("schemaJson", voltageSchema(), "changelog", "增加电压字段"));
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        Map<String, Object> version = data(updated);
        assertEquals("DRAFT", version.get("status"));
        assertEquals(1, version.get("fieldCount"));
        assertEquals("增加电压字段", version.get("changelog"));
        assertFalse(version.containsKey("schemaJson"));

        Map<String, Object> detail = data(get(versionPath(templateId, draftId), adminToken));
        assertTrue(detail.containsKey("schemaJson"));

        Map<String, Object> page = data(get(versionsPath(templateId), adminToken));
        List<Map<String, Object>> list = (List<Map<String, Object>>) page.get("list");
        assertEquals(1, list.size());
        assertFalse(list.get(0).containsKey("schemaJson"));

        Map<String, Object> valid = data(post(versionPath(templateId, draftId) + "/validate-data",
                adminToken, Map.of("voltage", 5.5)));
        assertEquals(Boolean.TRUE, valid.get("valid"));

        Map<String, Object> invalid = data(post(versionPath(templateId, draftId) + "/validate-data",
                adminToken, Map.of("voltage", 30)));
        assertEquals(Boolean.FALSE, invalid.get("valid"));
        assertFalse(((List<?>) invalid.get("errors")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void invalidSchemaReturnsStablePathsWithoutChangingDraft() {
        String adminToken = token(1L, "admin");
        Long templateId = createSystemTemplate(adminToken, "非法结构模板");
        Long draftId = draftId(templateId);
        Map<String, Object> invalidSchema = Map.of(
                "schemaVersion", "1.0",
                "type", "object",
                "properties", Map.of("samples", Map.of(
                        "type", "array", "title", "样本", "displayOrder", 1)));

        ResponseEntity<Map> response = put(versionPath(templateId, draftId), adminToken,
                Map.of("schemaJson", invalidSchema));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("SCHEMA_INVALID", response.getBody().get("reason"));
        Map<String, Object> errorData = (Map<String, Object>) response.getBody().get("data");
        List<Map<String, Object>> errors = (List<Map<String, Object>>) errorData.get("errors");
        assertTrue(errors.stream().anyMatch(error ->
                "properties.samples.type".equals(error.get("path"))));
        assertEquals("{}", jdbc.queryForObject(
                "SELECT schema_json FROM lab_experiment_template_version WHERE id = ?", String.class, draftId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordinaryMemberSeesFormalVersionsButNeverDraft() {
        String adminToken = token(1L, "admin");
        Long templateId = createSystemTemplate(adminToken, "成员可见模板");
        Long draftId = draftId(templateId);
        put(versionPath(templateId, draftId), adminToken,
                Map.of("schemaJson", voltageSchema(), "changelog", "首版"));
        publishDirectly(templateId, draftId);
        String memberToken = token(2L, "20260001");

        Map<String, Object> page = data(get(versionsPath(templateId), memberToken));
        List<Map<String, Object>> list = (List<Map<String, Object>>) page.get("list");
        assertEquals(1, list.size());
        assertEquals("PUBLISHED", list.get(0).get("status"));
        assertFalse(list.get(0).containsKey("schemaJson"));
        assertEquals(HttpStatus.FORBIDDEN,
                get(versionsPath(templateId) + "?status=DRAFT", memberToken).getStatusCode());

        List<Map<String, Object>> published = data(
                get(versionsPath(templateId) + "/published", memberToken));
        assertEquals(1, published.size());
        ResponseEntity<Map> denied = put(versionPath(templateId, draftId) + "/deprecate",
                memberToken, Map.of("reason", "无权操作"));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("TEMPLATE_OPERATION_DENIED", denied.getBody().get("reason"));
        ResponseEntity<Map> immutable = put(versionPath(templateId, draftId), adminToken,
                Map.of("schemaJson", voltageSchema()));
        assertEquals(HttpStatus.CONFLICT, immutable.getStatusCode());
        assertEquals("TEMPLATE_VERSION_NOT_EDITABLE", immutable.getBody().get("reason"));
    }

    @Test
    void nonProjectMemberCannotReadProjectVersions() {
        String adminToken = token(1L, "admin");
        Long projectId = createProject(adminToken, "TPL-VERSION-HIDDEN");
        Long templateId = createProjectTemplate(adminToken, projectId, "项目私有模板");
        String memberToken = token(2L, "20260001");

        ResponseEntity<Map> hidden = get(versionsPath(templateId), memberToken);
        assertEquals(HttpStatus.NOT_FOUND, hidden.getStatusCode());
        assertEquals("TEMPLATE_NOT_FOUND", hidden.getBody().get("reason"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPromotesDraftCreatesNextDraftAndUpdatesTemplateSummary() {
        String adminToken = token(1L, "admin");
        Long templateId = createSystemTemplate(adminToken, "发布事务模板");
        Long draftId = draftId(templateId);
        put(versionPath(templateId, draftId), adminToken,
                Map.of("schemaJson", voltageSchema(), "changelog", "草稿说明"));

        ResponseEntity<Map> response = put(versionPath(templateId, draftId) + "/publish",
                adminToken, Map.of("changelog", "首次发布"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("发布成功", response.getBody().get("msg"));
        Map<String, Object> result = data(response);
        Map<String, Object> published = (Map<String, Object>) result.get("publishedVersion");
        Map<String, Object> newDraft = (Map<String, Object>) result.get("newDraftVersion");
        assertEquals("1.0.0", published.get("version"));
        assertEquals("PUBLISHED", published.get("status"));
        assertEquals("0.2.0", newDraft.get("version"));
        assertEquals("DRAFT", newDraft.get("status"));
        assertFalse(newDraft.containsKey("publishedBy"));

        Map<String, Object> template = data(get("/api/experiment/templates/" + templateId, adminToken));
        assertEquals("1.0.0", template.get("latestVersion"));
        assertEquals(1, template.get("publishedVersionCount"));
        assertEquals(number(published.get("id")), number(template.get("latestVersionId")));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_experiment_template_version WHERE template_id = ?",
                Integer.class, templateId));
        assertEquals(jdbc.queryForObject(
                        "SELECT schema_json FROM lab_experiment_template_version WHERE id = ?",
                        String.class, number(published.get("id"))),
                jdbc.queryForObject("SELECT schema_json FROM lab_experiment_template_version WHERE id = ?",
                        String.class, number(newDraft.get("id"))));

        ResponseEntity<Map> repeated = put(versionPath(templateId, draftId) + "/publish",
                adminToken, Map.of("changelog", "重复发布"));
        assertEquals(HttpStatus.CONFLICT, repeated.getStatusCode());
        assertEquals("TEMPLATE_VERSION_NOT_PUBLISHABLE", repeated.getBody().get("reason"));
    }

    @Test
    void publishRejectsEmptySchemaAndRollsBackDatabaseConstraintConflict() {
        String adminToken = token(1L, "admin");
        Long emptyTemplateId = createSystemTemplate(adminToken, "空结构模板");
        Long emptyDraftId = draftId(emptyTemplateId);

        ResponseEntity<Map> empty = put(versionPath(emptyTemplateId, emptyDraftId) + "/publish",
                adminToken, Map.of("changelog", "不能发布"));
        assertEquals(HttpStatus.BAD_REQUEST, empty.getStatusCode());
        assertEquals("SCHEMA_EMPTY", empty.getBody().get("reason"));

        Long conflictTemplateId = createSystemTemplate(adminToken, "回滚验证模板");
        Long conflictDraftId = draftId(conflictTemplateId);
        put(versionPath(conflictTemplateId, conflictDraftId), adminToken,
                Map.of("schemaJson", voltageSchema()));
        jdbc.update("""
                INSERT INTO lab_experiment_template_version
                    (template_id, version, version_code, status, draft_slot, schema_json, field_count,
                     created_by, create_time, update_time)
                VALUES (?, '1.0.0', 10000, 'DEPRECATED', NULL, '{}', 0, 1,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, conflictTemplateId);

        ResponseEntity<Map> conflict = put(versionPath(conflictTemplateId, conflictDraftId) + "/publish",
                adminToken, Map.of("changelog", "触发唯一约束"));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("TEMPLATE_VERSION_CONFLICT", conflict.getBody().get("reason"));
        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT status FROM lab_experiment_template_version WHERE id = ?",
                String.class, conflictDraftId));
        assertEquals("0.1.0", jdbc.queryForObject(
                "SELECT version FROM lab_experiment_template_version WHERE id = ?",
                String.class, conflictDraftId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT published_version_count FROM lab_experiment_template WHERE id = ?",
                Integer.class, conflictTemplateId));
    }

    @Test
    void deprecateFallsBackToPreviousPublishedVersionThenClearsLatest() {
        String adminToken = token(1L, "admin");
        Long templateId = createSystemTemplate(adminToken, "废弃回退模板");
        Long firstDraftId = draftId(templateId);
        put(versionPath(templateId, firstDraftId), adminToken,
                Map.of("schemaJson", voltageSchema()));
        Map<String, Object> firstPublish = data(put(versionPath(templateId, firstDraftId) + "/publish",
                adminToken, Map.of("changelog", "第一版")));
        Long firstPublishedId = number(((Map<?, ?>) firstPublish.get("publishedVersion")).get("id"));
        Long secondDraftId = number(((Map<?, ?>) firstPublish.get("newDraftVersion")).get("id"));
        Map<String, Object> secondPublish = data(put(versionPath(templateId, secondDraftId) + "/publish",
                adminToken, Map.of("changelog", "第二版")));
        Long secondPublishedId = number(((Map<?, ?>) secondPublish.get("publishedVersion")).get("id"));

        ResponseEntity<Map> secondDeprecated = put(versionPath(templateId, secondPublishedId) + "/deprecate",
                adminToken, Map.of("reason", "由第一版暂时接替"));
        assertEquals(HttpStatus.OK, secondDeprecated.getStatusCode());
        assertEquals("已废弃", secondDeprecated.getBody().get("msg"));
        Map<String, Object> deprecated = data(secondDeprecated);
        assertEquals("DEPRECATED", deprecated.get("status"));
        assertEquals("由第一版暂时接替", deprecated.get("deprecatedReason"));
        assertEquals("第二版", jdbc.queryForObject(
                "SELECT changelog FROM lab_experiment_template_version WHERE id = ?",
                String.class, secondPublishedId));
        assertTemplateSummary(templateId, firstPublishedId, "1.0.0", 1);

        put(versionPath(templateId, firstPublishedId) + "/deprecate",
                adminToken, Map.of("reason", "停止使用"));
        Map<String, Object> template = jdbc.queryForMap("""
                SELECT latest_version_id, latest_version, published_version_count
                FROM lab_experiment_template WHERE id = ?
                """, templateId);
        assertNull(template.get("LATEST_VERSION_ID"));
        assertNull(template.get("LATEST_VERSION"));
        assertEquals(0, ((Number) template.get("PUBLISHED_VERSION_COUNT")).intValue());

        ResponseEntity<Map> repeated = put(versionPath(templateId, firstPublishedId) + "/deprecate",
                adminToken, Map.of("reason", "重复废弃"));
        assertEquals(HttpStatus.CONFLICT, repeated.getStatusCode());
        assertEquals("TEMPLATE_VERSION_NOT_DEPRECATABLE", repeated.getBody().get("reason"));
    }

    private void publishDirectly(Long templateId, Long draftId) {
        jdbc.update("""
                UPDATE lab_experiment_template_version
                SET version = '1.0.0', version_code = 10000, status = 'PUBLISHED', draft_slot = NULL,
                    published_by = 1, published_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, draftId);
        jdbc.update("""
                INSERT INTO lab_experiment_template_version
                    (template_id, version, status, draft_slot, schema_json, field_count,
                     created_by, create_time, update_time)
                SELECT template_id, '0.2.0', 'DRAFT', 1, schema_json, field_count,
                       created_by, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM lab_experiment_template_version WHERE id = ?
                """, draftId);
        jdbc.update("""
                UPDATE lab_experiment_template
                SET latest_version_id = ?, latest_version = '1.0.0', published_version_count = 1
                WHERE id = ?
                """, draftId, templateId);
    }

    private void assertTemplateSummary(Long templateId, Long latestVersionId,
                                       String latestVersion, int publishedCount) {
        Map<String, Object> template = jdbc.queryForMap("""
                SELECT latest_version_id, latest_version, published_version_count
                FROM lab_experiment_template WHERE id = ?
                """, templateId);
        assertEquals(latestVersionId, ((Number) template.get("LATEST_VERSION_ID")).longValue());
        assertEquals(latestVersion, template.get("LATEST_VERSION"));
        assertEquals(publishedCount, ((Number) template.get("PUBLISHED_VERSION_COUNT")).intValue());
    }

    private Map<String, Object> voltageSchema() {
        return Map.of(
                "schemaVersion", "1.0",
                "type", "object",
                "properties", Map.of("voltage", Map.of(
                        "type", "number",
                        "title", "电压",
                        "unit", "V",
                        "minimum", 0,
                        "maximum", 24,
                        "displayOrder", 1,
                        "widget", "input-number")),
                "required", List.of("voltage"));
    }

    private Long createSystemTemplate(String token, String title) {
        Map<String, Object> created = data(post("/api/experiment/templates", token,
                templateBody(title, "SYSTEM", null)));
        return number(created.get("id"));
    }

    private Long createProjectTemplate(String token, Long projectId, String title) {
        Map<String, Object> created = data(post("/api/experiment/templates", token,
                templateBody(title, "PROJECT", projectId)));
        return number(created.get("id"));
    }

    private Long createProject(String token, String code) {
        Map<String, Object> created = data(post("/api/projects", token,
                Map.of("code", code, "name", code + " 项目")));
        return number(created.get("id"));
    }

    private Map<String, Object> templateBody(String title, String scope, Long projectId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("scope", scope);
        body.put("projectId", projectId);
        return body;
    }

    private Long draftId(Long templateId) {
        return jdbc.queryForObject("""
                SELECT id FROM lab_experiment_template_version
                WHERE template_id = ? AND status = 'DRAFT'
                """, Long.class, templateId);
    }

    private String versionsPath(Long templateId) {
        return "/api/experiment/templates/" + templateId + "/versions";
    }

    private String versionPath(Long templateId, Long versionId) {
        return versionsPath(templateId) + "/" + versionId;
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, authorized(token), Map.class);
    }

    private ResponseEntity<Map> post(String path, String token, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.POST, authorized(token, body), Map.class);
    }

    private ResponseEntity<Map> put(String path, String token, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.PUT, authorized(token, body), Map.class);
    }

    private String token(Long userId, String memberId) {
        return jwtUtils.createToken(memberId, "IGNORED", userId);
    }

    @SuppressWarnings("unchecked")
    private <T> T data(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return (T) response.getBody().get("data");
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
