package com.usn.labhub.user.component;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComponentCatalogMigrationTest {

    private static final List<String> V12_TABLES = List.of(
            "LAB_COMPONENT_CATEGORY",
            "LAB_COMPONENT_MANUFACTURER",
            "LAB_COMPONENT",
            "LAB_COMPONENT_SPEC_CANDIDATE",
            "LAB_COMPONENT_SPEC_SNAPSHOT",
            "LAB_MEDIA_OBJECT",
            "LAB_COMPONENT_CATALOG_AUDIT_LOG"
    );

    @Test
    void upgradesV11AndEnforcesCatalogSnapshotAndReadyMediaConstraints() {
        String url = "jdbc:h2:mem:component-catalog-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target("11")
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
        for (String table : V12_TABLES) {
            assertEquals(1, tableCount(jdbc, table), table + " should exist");
        }
        assertEquals(1, tableCount(jdbc, "LAB_REQUEST_IDEMPOTENCY"));
        assertEquals(1, columnCount(jdbc, "LAB_MEDIA_OBJECT", "READY_DEDUP_SLOT"));
        assertEquals(1, columnCount(jdbc, "LAB_MEDIA_OBJECT", "READY_PRIMARY_SLOT"));

        long categoryId = insertCategory(jdbc, "SEMICONDUCTOR", "半导体", "半导体", null, 0, 1, "/1/");
        assertThrows(DuplicateKeyException.class,
                () -> insertCategory(jdbc, "SEMICONDUCTOR_ALT", " 半导体 ", "半导体", null, 0, 1, "/2/"));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertCategory(jdbc, "INVALID_LEVEL", "错误层级", "错误层级", null, 0, 2, "/3/"));

        long manufacturerId = insertManufacturer(jdbc, "TI", "Texas Instruments", "TEXAS INSTRUMENTS");
        assertThrows(DuplicateKeyException.class,
                () -> insertManufacturer(jdbc, "TI_ALT", "texas instruments", "TEXAS INSTRUMENTS"));

        long componentId = insertComponent(jdbc, manufacturerId, categoryId,
                "CMP-00000001", "OPA333AIDBVR", "OPA333AIDBVR", "PCS");
        assertThrows(DuplicateKeyException.class,
                () -> insertComponent(jdbc, manufacturerId, categoryId,
                        "CMP-00000002", " opa333aidbvr ", "OPA333AIDBVR", "PCS"));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertComponent(jdbc, manufacturerId, categoryId,
                        "CMP-00000003", "OPA333-INVALID", "OPA333-INVALID", "BOX"));

        insertSnapshot(jdbc, componentId, 1);
        assertThrows(DuplicateKeyException.class, () -> insertSnapshot(jdbc, componentId, 1));

        long originalMediaId = insertReadyMedia(jdbc, componentId, "PRIMARY", "a".repeat(64), 1);
        assertThrows(DuplicateKeyException.class,
                () -> insertReadyMedia(jdbc, componentId, "GALLERY", "a".repeat(64), 2));
        assertThrows(DuplicateKeyException.class,
                () -> insertReadyMedia(jdbc, componentId, "PRIMARY", "b".repeat(64), 3));

        jdbc.update("""
                UPDATE lab_media_object
                SET status='DELETED', ready_dedup_slot=NULL, ready_primary_slot=NULL,
                    deleted_by=1, deleted_at=CURRENT_TIMESTAMP, version=version+1
                WHERE id=?
                """, originalMediaId);
        long replacementMediaId = insertReadyMedia(jdbc, componentId, "PRIMARY", "a".repeat(64), 4);
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM lab_media_object
                WHERE owner_type='COMPONENT' AND owner_id=? AND sha256=?
                """, Integer.class, componentId, "a".repeat(64)));
        assertEquals("READY", jdbc.queryForObject(
                "SELECT status FROM lab_media_object WHERE id=?", String.class, replacementMediaId));
    }

    @Test
    void migrationIsAdditiveAndDoesNotRedefineExistingTables() throws IOException {
        String sql = new String(new ClassPathResource(
                "db/migration/V12__create_component_catalog_media_tables.sql"
        ).getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains("ALTER TABLE"));
        assertFalse(sql.contains("RENAME TABLE"));
        assertFalse(sql.contains("TRUNCATE TABLE"));
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

        for (String table : V12_TABLES) {
            assertEquals(1, tableCount(jdbc, table), table + " should exist on MySQL");
        }
        assertEquals(existingPm001, jdbc.queryForObject("""
                SELECT CONCAT(device_code, ':', project_id, ':', status)
                FROM iot_device WHERE device_code = 'PM-001'
                """, String.class));
    }

    private long insertCategory(JdbcTemplate jdbc,
                                String code,
                                String name,
                                String normalizedName,
                                Long parentId,
                                long parentScopeId,
                                int level,
                                String path) {
        jdbc.update("""
                INSERT INTO lab_component_category(
                    code, name, normalized_name, parent_id, parent_scope_id,
                    level, path, sort_order, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 10, 1, 1)
                """, code, name, normalizedName, parentId, parentScopeId, level, path);
        return jdbc.queryForObject("""
                SELECT id FROM lab_component_category WHERE code=?
                """, Long.class, code);
    }

    private long insertManufacturer(JdbcTemplate jdbc, String code, String name, String normalizedName) {
        jdbc.update("""
                INSERT INTO lab_component_manufacturer(
                    code, name, normalized_name, created_by, updated_by
                ) VALUES (?, ?, ?, 1, 1)
                """, code, name, normalizedName);
        return jdbc.queryForObject("""
                SELECT id FROM lab_component_manufacturer WHERE code=?
                """, Long.class, code);
    }

    private long insertComponent(JdbcTemplate jdbc,
                                 long manufacturerId,
                                 long categoryId,
                                 String catalogCode,
                                 String mpn,
                                 String normalizedMpn,
                                 String baseUnit) {
        jdbc.update("""
                INSERT INTO lab_component(
                    catalog_code, name, manufacturer_id, mpn, normalized_mpn,
                    category_id, package_type, base_unit, created_by, updated_by
                ) VALUES (?, '低功耗运算放大器', ?, ?, ?, ?, 'SOT-23-5', ?, 1, 1)
                """, catalogCode, manufacturerId, mpn, normalizedMpn, categoryId, baseUnit);
        return jdbc.queryForObject("""
                SELECT id FROM lab_component WHERE catalog_code=?
                """, Long.class, catalogCode);
    }

    private void insertSnapshot(JdbcTemplate jdbc, long componentId, int snapshotVersion) {
        jdbc.update("""
                INSERT INTO lab_component_spec_snapshot(
                    component_id, snapshot_version, source_type, source_note,
                    raw_specs_json, specifications_json, change_summary,
                    checksum, confirmed_by
                ) VALUES (?, ?, 'MANUAL', '实验室手册核验', '[]', '[]',
                          '建立初始规格', ?, 1)
                """, componentId, snapshotVersion, "c".repeat(64));
    }

    private long insertReadyMedia(JdbcTemplate jdbc,
                                  long componentId,
                                  String role,
                                  String sha256,
                                  int suffix) {
        Integer primarySlot = "PRIMARY".equals(role) ? 1 : null;
        jdbc.update("""
                INSERT INTO lab_media_object(
                    owner_type, owner_id, role, status, original_file_name,
                    content_type, size_bytes, width, height, sha256,
                    original_object_key, display_object_key, sort_order,
                    ready_dedup_slot, ready_primary_slot, uploaded_by
                ) VALUES ('COMPONENT', ?, ?, 'READY', ?, 'image/png', 128,
                          64, 64, ?, ?, ?, ?, 1, ?, 1)
                """, componentId, role, "component-" + suffix + ".png", sha256,
                "original/" + suffix, "display/" + suffix, suffix, primarySlot);
        return jdbc.queryForObject("""
                SELECT id FROM lab_media_object WHERE original_object_key=?
                """, Long.class, "original/" + suffix);
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
