package com.usn.labhub.user.experiment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExperimentRecordAcquisitionMigrationTest {

    private static final List<String> V11_TABLES = List.of(
            "LAB_EXPERIMENT_RECORD",
            "LAB_EXPERIMENT_REVISION",
            "LAB_EXPERIMENT_FEEDBACK",
            "LAB_COLLECTION_PLAN",
            "LAB_COLLECTION_BATCH",
            "LAB_DATA_IMPORT",
            "LAB_DATA_IMPORT_REVISION",
            "LAB_MANUAL_RECORD",
            "LAB_MANUAL_RECORD_REVISION",
            "LAB_COLLECTION_RECORD",
            "LAB_COLLECTION_SNAPSHOT",
            "LAB_SNAPSHOT_MANUAL_REVISION",
            "LAB_REQUEST_IDEMPOTENCY",
            "LAB_EXPERIMENT_AUDIT_LOG"
    );

    @Test
    void upgradesV10WithoutChangingLegacyFactsAndEnforcesEvidenceConstraints() {
        String url = "jdbc:h2:mem:experiment-acquisition-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target("10")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        String legacyProject = jdbc.queryForObject("""
                SELECT CONCAT(project_code, ':', project_name, ':', status)
                FROM lab_project WHERE id = 1
                """, String.class);
        String legacyDevice = jdbc.queryForObject("""
                SELECT CONCAT(device_code, ':', project_id, ':', status)
                FROM iot_device WHERE id = 1
                """, String.class);

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertEquals(legacyProject, jdbc.queryForObject("""
                SELECT CONCAT(project_code, ':', project_name, ':', status)
                FROM lab_project WHERE id = 1
                """, String.class));
        assertEquals(legacyDevice, jdbc.queryForObject("""
                SELECT CONCAT(device_code, ':', project_id, ':', status)
                FROM iot_device WHERE id = 1
                """, String.class));

        Map<String, Object> source = jdbc.queryForMap("""
                SELECT source_experiment_record_id, source_published_revision_id
                FROM lab_project WHERE id = 1
                """);
        assertNull(source.get("SOURCE_EXPERIMENT_RECORD_ID"));
        assertNull(source.get("SOURCE_PUBLISHED_REVISION_ID"));
        for (String table : V11_TABLES) {
            assertEquals(1, tableCount(jdbc, table), table + " should exist");
        }

        long templateVersionId = insertPublishedTemplateVersion(jdbc);
        long recordId = insertExperimentRecord(jdbc, templateVersionId, "ER-202610-000001", "DRAFT", 1L);
        insertExperimentRevision(jdbc, recordId, 1, "EDITABLE", null);

        assertThrows(DuplicateKeyException.class,
                () -> insertExperimentRecord(jdbc, templateVersionId, "ER-202610-000001", "DRAFT", 1L));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertExperimentRecord(jdbc, templateVersionId, "ER-202610-000002", "UNKNOWN", 1L));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertExperimentRecord(jdbc, templateVersionId, "ER-202610-000003", "DRAFT", null));
        assertThrows(DuplicateKeyException.class,
                () -> insertExperimentRevision(jdbc, recordId, 1, "EDITABLE", null));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertExperimentRevision(jdbc, recordId, 2, "EDITABLE", null));

        jdbc.update("""
                INSERT INTO lab_request_idempotency(
                    actor_id, operation, idempotency_key, request_digest
                ) VALUES (1, 'CREATE_EXPERIMENT', 'same-key', ?)
                """, "a".repeat(64));
        assertThrows(DuplicateKeyException.class, () -> jdbc.update("""
                INSERT INTO lab_request_idempotency(
                    actor_id, operation, idempotency_key, request_digest
                ) VALUES (1, 'CREATE_EXPERIMENT', 'same-key', ?)
                """, "b".repeat(64)));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void migratesOnRealMysqlAndPreservesPm001Demo() {
        String url = System.getenv("USNHUB_TEST_MYSQL_URL");
        String username = System.getenv("USNHUB_TEST_MYSQL_USERNAME");
        String password = System.getenv("USNHUB_TEST_MYSQL_PASSWORD");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String existingPm001 = jdbc.queryForObject("""
                SELECT CONCAT(device_code, ':', project_id, ':', status)
                FROM iot_device WHERE device_code = 'PM-001'
                """, String.class);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        for (String table : V11_TABLES) {
            assertEquals(1, tableCount(jdbc, table), table + " should exist on MySQL");
        }
        assertEquals(1, columnCount(jdbc, "lab_project", "source_experiment_record_id"));
        assertEquals(1, columnCount(jdbc, "lab_project", "source_published_revision_id"));
        assertEquals(existingPm001, jdbc.queryForObject("""
                SELECT CONCAT(device_code, ':', project_id, ':', status)
                FROM iot_device WHERE device_code = 'PM-001'
                """, String.class));
    }

    private long insertPublishedTemplateVersion(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO lab_experiment_template(title, scope, created_by)
                VALUES ('W43 migration template', 'SYSTEM', 1)
                """);
        Long templateId = jdbc.queryForObject("""
                SELECT id FROM lab_experiment_template
                WHERE title = 'W43 migration template'
                ORDER BY id DESC LIMIT 1
                """, Long.class);
        jdbc.update("""
                INSERT INTO lab_experiment_template_version(
                    template_id, version, version_code, status, draft_slot,
                    schema_json, field_count, created_by
                ) VALUES (?, '1.0.0', 10000, 'PUBLISHED', NULL,
                          '{"schemaVersion":"1.0","type":"object","properties":{}}', 0, 1)
                """, templateId);
        return jdbc.queryForObject("""
                SELECT id FROM lab_experiment_template_version
                WHERE template_id = ? AND status = 'PUBLISHED'
                """, Long.class, templateId);
    }

    private long insertExperimentRecord(JdbcTemplate jdbc,
                                        long templateVersionId,
                                        String recordCode,
                                        String status,
                                        Long projectId) {
        Long templateId = jdbc.queryForObject("""
                SELECT template_id FROM lab_experiment_template_version WHERE id = ?
                """, Long.class, templateVersionId);
        jdbc.update("""
                INSERT INTO lab_experiment_record(
                    record_code, title, author_id, project_id,
                    template_id, template_version_id, status
                ) VALUES (?, 'W43 migration record', 1, ?, ?, ?, ?)
                """, recordCode, projectId, templateId, templateVersionId, status);
        return jdbc.queryForObject("""
                SELECT id FROM lab_experiment_record WHERE record_code = ?
                """, Long.class, recordCode);
    }

    private void insertExperimentRevision(JdbcTemplate jdbc,
                                          long recordId,
                                          int revisionNo,
                                          String status,
                                          String changeSummary) {
        jdbc.update("""
                INSERT INTO lab_experiment_revision(
                    record_id, revision_no, status, content_json,
                    change_summary, created_by
                ) VALUES (?, ?, ?, '{}', ?, 1)
                """, recordId, revisionNo, status, changeSummary);
    }

    private int tableCount(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE UPPER(table_name) = UPPER(?)
                """, Integer.class, tableName);
    }

    private int columnCount(JdbcTemplate jdbc, String tableName, String columnName) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE UPPER(table_name) = UPPER(?)
                  AND UPPER(column_name) = UPPER(?)
                """, Integer.class, tableName, columnName);
    }
}
