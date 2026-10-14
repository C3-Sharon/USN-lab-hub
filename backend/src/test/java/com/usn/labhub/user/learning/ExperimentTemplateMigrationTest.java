package com.usn.labhub.user.learning;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExperimentTemplateMigrationTest {

    @Test
    void upgradesV9DataAndEnforcesTemplateVersionConstraints() {
        String url = "jdbc:h2:mem:experiment-template-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target("9")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        Map<String, Object> legacyUnit = jdbc.queryForMap("""
                SELECT id, title, template_id
                FROM lab_learning_unit
                ORDER BY id
                LIMIT 1
                """);
        String legacyProject = jdbc.queryForObject("""
                SELECT CONCAT(project_code, ':', category, ':', status)
                FROM lab_project
                WHERE id = 1
                """, String.class);
        String legacyDevice = jdbc.queryForObject("""
                SELECT CONCAT(device_code, ':', project_id, ':', status)
                FROM iot_device
                WHERE id = 1
                """, String.class);

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertEquals(legacyProject, jdbc.queryForObject("""
                SELECT CONCAT(project_code, ':', category, ':', status)
                FROM lab_project
                WHERE id = 1
                """, String.class));
        assertEquals(legacyDevice, jdbc.queryForObject("""
                SELECT CONCAT(device_code, ':', project_id, ':', status)
                FROM iot_device
                WHERE id = 1
                """, String.class));

        Map<String, Object> migratedUnit = jdbc.queryForMap("""
                SELECT id, title, template_id, template_version_id
                FROM lab_learning_unit
                WHERE id = ?
                """, legacyUnit.get("ID"));
        assertEquals(legacyUnit.get("TITLE"), migratedUnit.get("TITLE"));
        assertEquals(legacyUnit.get("TEMPLATE_ID"), migratedUnit.get("TEMPLATE_ID"));
        assertNull(migratedUnit.get("TEMPLATE_VERSION_ID"));

        assertEquals(1, tableCount(jdbc, "LAB_EXPERIMENT_TEMPLATE"));
        assertEquals(1, tableCount(jdbc, "LAB_EXPERIMENT_TEMPLATE_VERSION"));
        assertEquals(1, columnCount(jdbc, "LAB_LEARNING_UNIT", "TEMPLATE_VERSION_ID"));

        long templateId = insertSystemTemplate(jdbc, "迁移约束测试模板");
        insertVersion(jdbc, templateId, "0.1.0", null, "DRAFT", 1);

        assertThrows(DuplicateKeyException.class,
                () -> insertVersion(jdbc, templateId, "0.1.0", null, "DRAFT", 1));
        assertThrows(DuplicateKeyException.class,
                () -> insertVersion(jdbc, templateId, "0.2.0", null, "DRAFT", 1));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertVersion(jdbc, templateId, "0.2.0", 10100, "DRAFT", null));

        insertVersion(jdbc, templateId, "1.0.0", 10000, "PUBLISHED", null);
        insertVersion(jdbc, templateId, "1.1.0", 10100, "PUBLISHED", null);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void migratesOnRealMysqlWithExpectedConstraints() {
        String url = System.getenv("USNHUB_TEST_MYSQL_URL");
        String username = System.getenv("USNHUB_TEST_MYSQL_USERNAME");
        String password = System.getenv("USNHUB_TEST_MYSQL_PASSWORD");

        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        assertEquals(1, columnCount(jdbc, "lab_learning_unit", "template_version_id"));
        assertEquals(2, uniqueIndexCount(jdbc, "lab_experiment_template_version"));

        long templateId = insertSystemTemplate(jdbc, "MySQL 迁移约束测试模板");
        try {
            insertVersion(jdbc, templateId, "0.1.0", null, "DRAFT", 1);
            assertThrows(DuplicateKeyException.class,
                    () -> insertVersion(jdbc, templateId, "0.2.0", null, "DRAFT", 1));
            insertVersion(jdbc, templateId, "1.0.0", 10000, "PUBLISHED", null);
        } finally {
            jdbc.update("DELETE FROM lab_experiment_template_version WHERE template_id = ?", templateId);
            jdbc.update("DELETE FROM lab_experiment_template WHERE id = ?", templateId);
        }
    }

    private long insertSystemTemplate(JdbcTemplate jdbc, String title) {
        jdbc.update("""
                INSERT INTO lab_experiment_template(title, scope, created_by)
                VALUES (?, 'SYSTEM', 1)
                """, title);
        return jdbc.queryForObject("""
                SELECT id FROM lab_experiment_template
                WHERE title = ?
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, title);
    }

    private void insertVersion(JdbcTemplate jdbc,
                               long templateId,
                               String version,
                               Integer versionCode,
                               String status,
                               Integer draftSlot) {
        jdbc.update("""
                INSERT INTO lab_experiment_template_version(
                    template_id, version, version_code, status, draft_slot,
                    schema_json, field_count, created_by
                ) VALUES (?, ?, ?, ?, ?, '{}', 0, 1)
                """, templateId, version, versionCode, status, draftSlot);
    }

    private int tableCount(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE UPPER(table_name) = UPPER(?)
                """, Integer.class, tableName);
    }

    private int columnCount(JdbcTemplate jdbc, String tableName, String columnName) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE UPPER(table_name) = UPPER(?)
                  AND UPPER(column_name) = UPPER(?)
                """, Integer.class, tableName, columnName);
    }

    private int uniqueIndexCount(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForObject("""
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE UPPER(table_name) = UPPER(?)
                  AND index_name <> 'PRIMARY'
                  AND non_unique = 0
                """, Integer.class, tableName);
    }
}
