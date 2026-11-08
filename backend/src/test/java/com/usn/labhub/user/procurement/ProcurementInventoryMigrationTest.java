package com.usn.labhub.user.procurement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcurementInventoryMigrationTest {

    private static final List<String> V13_TABLES = List.of(
            "LAB_PROCUREMENT_POLICY_VERSION",
            "LAB_PROCUREMENT_POLICY_FORCED_CATEGORY",
            "LAB_PROCUREMENT_ORDER",
            "LAB_PROCUREMENT_ORDER_LINE",
            "LAB_PROCUREMENT_APPROVAL_RECORD",
            "LAB_PROCUREMENT_RECEIPT",
            "LAB_PROCUREMENT_RECEIPT_LINE",
            "LAB_COMPONENT_INVENTORY_BALANCE",
            "LAB_INVENTORY_BATCH",
            "LAB_INVENTORY_MOVEMENT",
            "LAB_PROCUREMENT_ATTACHMENT",
            "LAB_PROCUREMENT_AUDIT_LOG"
    );

    @Test
    void upgradesV12PreservesFactsAndEnforcesProcurementConstraints() {
        String url = "jdbc:h2:mem:procurement-inventory-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target("12")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        long componentId = insertComponentFacts(jdbc);
        long mediaId = insertComponentMedia(jdbc, componentId);
        String componentBefore = componentFact(jdbc, componentId);
        String mediaBefore = mediaFact(jdbc, mediaId);
        String pm001Before = pm001Fact(jdbc);

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        for (String table : V13_TABLES) {
            assertEquals(1, tableCount(jdbc, table), table + " should exist");
        }
        assertEquals(componentBefore, componentFact(jdbc, componentId));
        assertEquals(mediaBefore, mediaFact(jdbc, mediaId));
        assertEquals(pm001Before, pm001Fact(jdbc));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_procurement_policy_version WHERE policy_version=1",
                Integer.class));
        assertEquals("500.00:1000.0000:CNY", jdbc.queryForObject("""
                SELECT CONCAT(approval_threshold, ':', valuable_unit_price_threshold, ':', currency)
                FROM lab_procurement_policy_version WHERE policy_version=1
                """, String.class));

        long procurementMediaId = insertProcurementMedia(jdbc, "PROCUREMENT_ORDER", 901L, 2);
        assertEquals("PROCUREMENT_ORDER", jdbc.queryForObject(
                "SELECT owner_type FROM lab_media_object WHERE id=?", String.class, procurementMediaId));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertProcurementMedia(jdbc, "UNKNOWN_OWNER", 901L, 3));

        upsertBalance(jdbc, componentId, "PCS");
        jdbc.update("""
                UPDATE lab_component_inventory_balance
                SET on_hand_quantity=7.000, version=4
                WHERE component_id=?
                """, componentId);
        upsertBalance(jdbc, componentId, "PCS");
        assertEquals("7.000:4", jdbc.queryForObject("""
                SELECT CONCAT(on_hand_quantity, ':', version)
                FROM lab_component_inventory_balance WHERE component_id=?
                """, String.class, componentId));

        assertUniqueIndex(jdbc, "LAB_PROCUREMENT_ORDER", "UK_PROCUREMENT_PURCHASE_NO");
        assertUniqueIndex(jdbc, "LAB_PROCUREMENT_ORDER", "UK_PROCUREMENT_PLATFORM_ORDER");
        assertUniqueIndex(jdbc, "LAB_PROCUREMENT_ORDER_LINE", "UK_PROCUREMENT_ORDER_LINE_NO");
        assertUniqueIndex(jdbc, "LAB_PROCUREMENT_ORDER_LINE", "UK_PROCUREMENT_ORDER_COMPONENT");
        assertUniqueIndex(jdbc, "LAB_PROCUREMENT_RECEIPT_LINE", "UK_PROCUREMENT_RECEIPT_ORDER_LINE");
        assertUniqueIndex(jdbc, "LAB_INVENTORY_BATCH", "UK_INVENTORY_BATCH_RECEIPT_LINE");
        assertUniqueIndex(jdbc, "LAB_INVENTORY_MOVEMENT", "UK_INVENTORY_MOVEMENT_SOURCE");
        assertEquals(1, primaryKeyColumnCount(jdbc, "LAB_COMPONENT_INVENTORY_BALANCE", "COMPONENT_ID"));
        assertEquals(1, tableCount(jdbc, "LAB_REQUEST_IDEMPOTENCY"),
                "existing shared idempotency table should be reused");
        assertEquals(128, columnLength(jdbc, "LAB_REQUEST_IDEMPOTENCY", "IDEMPOTENCY_KEY"));
    }

    @Test
    void migrationOnlyUsesApprovedCompatibilityAlterAndExactNumericTypes() throws IOException {
        String sql = new String(new ClassPathResource(
                "db/migration/V13__create_procurement_inventory_tables.sql"
        ).getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
        String normalizedSql = sql.replace(String.valueOf((char) 96), "");

        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains("TRUNCATE TABLE"));
        assertFalse(sql.contains("RENAME TABLE"));
        assertFalse(sql.contains(" FLOAT"));
        assertFalse(sql.contains(" DOUBLE"));
        assertEquals(2, occurrences(normalizedSql, "ALTER TABLE LAB_MEDIA_OBJECT"));
        assertTrue(normalizedSql.contains("DROP CONSTRAINT CK_MEDIA_OWNER_TYPE"));
        assertTrue(sql.contains("'PROCUREMENT_ORDER'"));
        assertTrue(sql.contains("'PROCUREMENT_RECEIPT'"));
        assertFalse(normalizedSql.contains("CREATE TABLE LAB_REQUEST_IDEMPOTENCY"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void migratesV12ToV13OnRealMysqlAndPreservesExistingFacts() {
        String url = System.getenv("USNHUB_TEST_MYSQL_URL");
        String username = System.getenv("USNHUB_TEST_MYSQL_USERNAME");
        String password = System.getenv("USNHUB_TEST_MYSQL_PASSWORD");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("12")
                .load()
                .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String pm001Before = pm001Fact(jdbc);
        int componentCountBefore = jdbc.queryForObject("SELECT COUNT(*) FROM lab_component", Integer.class);
        int snapshotCountBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_component_spec_snapshot", Integer.class);
        int mediaCountBefore = jdbc.queryForObject("SELECT COUNT(*) FROM lab_media_object", Integer.class);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        for (String table : V13_TABLES) {
            assertEquals(1, tableCount(jdbc, table), table + " should exist on MySQL");
        }
        assertEquals(128, columnLength(jdbc, "LAB_REQUEST_IDEMPOTENCY", "IDEMPOTENCY_KEY"));
        assertEquals(pm001Before, pm001Fact(jdbc));
        assertEquals(componentCountBefore,
                jdbc.queryForObject("SELECT COUNT(*) FROM lab_component", Integer.class));
        assertEquals(snapshotCountBefore,
                jdbc.queryForObject("SELECT COUNT(*) FROM lab_component_spec_snapshot", Integer.class));
        assertEquals(mediaCountBefore,
                jdbc.queryForObject("SELECT COUNT(*) FROM lab_media_object", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_procurement_policy_version WHERE policy_version=1",
                Integer.class));
    }

    private long insertComponentFacts(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO lab_component_category(
                    code, name, normalized_name, parent_scope_id, level, path,
                    created_by, updated_by
                ) VALUES ('PROC_TEST', '采购测试', '采购测试', 0, 1, '/PROC_TEST/', 1, 1)
                """);
        long categoryId = jdbc.queryForObject(
                "SELECT id FROM lab_component_category WHERE code='PROC_TEST'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component_manufacturer(
                    code, name, normalized_name, created_by, updated_by
                ) VALUES ('PROC_TEST_MFR', 'Procurement Test', 'PROCUREMENT TEST', 1, 1)
                """);
        long manufacturerId = jdbc.queryForObject(
                "SELECT id FROM lab_component_manufacturer WHERE code='PROC_TEST_MFR'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component(
                    catalog_code, name, manufacturer_id, mpn, normalized_mpn,
                    category_id, package_type, base_unit, status, created_by, updated_by
                ) VALUES ('CMP-90000001', '采购测试元器件', ?, 'PROC-001', 'PROC-001',
                          ?, 'SMD', 'PCS', 'ACTIVE', 1, 1)
                """, manufacturerId, categoryId);
        long componentId = jdbc.queryForObject(
                "SELECT id FROM lab_component WHERE catalog_code='CMP-90000001'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component_spec_snapshot(
                    component_id, snapshot_version, source_type, source_note,
                    raw_specs_json, specifications_json, change_summary, checksum, confirmed_by
                ) VALUES (?, 1, 'MANUAL', 'migration test', '[]', '[]',
                          'initial', ?, 1)
                """, componentId, "d".repeat(64));
        return componentId;
    }

    private long insertComponentMedia(JdbcTemplate jdbc, long componentId) {
        jdbc.update("""
                INSERT INTO lab_media_object(
                    owner_type, owner_id, role, status, original_file_name,
                    content_type, size_bytes, width, height, sha256,
                    original_object_key, display_object_key, sort_order,
                    ready_dedup_slot, ready_primary_slot, uploaded_by
                ) VALUES ('COMPONENT', ?, 'PRIMARY', 'READY', 'before.png',
                          'image/png', 32, 8, 8, ?, 'before/original', 'before/display',
                          0, 1, 1, 1)
                """, componentId, "a".repeat(64));
        return jdbc.queryForObject(
                "SELECT id FROM lab_media_object WHERE original_object_key='before/original'", Long.class);
    }

    private long insertProcurementMedia(JdbcTemplate jdbc, String ownerType, long ownerId, int suffix) {
        jdbc.update("""
                INSERT INTO lab_media_object(
                    owner_type, owner_id, role, status, original_file_name,
                    content_type, size_bytes, width, height, sha256,
                    original_object_key, display_object_key, sort_order,
                    ready_dedup_slot, ready_primary_slot, uploaded_by
                ) VALUES (?, ?, 'GALLERY', 'READY', ?, 'image/png', 32, 8, 8, ?,
                          ?, ?, 0, 1, NULL, 1)
                """, ownerType, ownerId, "procurement-" + suffix + ".png",
                Integer.toHexString(suffix).repeat(64).substring(0, 64),
                "procurement/" + suffix + "/original", "procurement/" + suffix + "/display");
        return jdbc.queryForObject(
                "SELECT id FROM lab_media_object WHERE original_object_key=?",
                Long.class, "procurement/" + suffix + "/original");
    }

    private void upsertBalance(JdbcTemplate jdbc, long componentId, String unit) {
        jdbc.update("""
                INSERT INTO lab_component_inventory_balance(
                    component_id, on_hand_quantity, unit, version
                ) VALUES (?, 0.000, ?, 1)
                ON DUPLICATE KEY UPDATE component_id=component_id
                """, componentId, unit);
    }

    private String componentFact(JdbcTemplate jdbc, long componentId) {
        return jdbc.queryForObject("""
                SELECT CONCAT(catalog_code, ':', mpn, ':', base_unit, ':', status)
                FROM lab_component WHERE id=?
                """, String.class, componentId);
    }

    private String mediaFact(JdbcTemplate jdbc, long mediaId) {
        return jdbc.queryForObject("""
                SELECT CONCAT(owner_type, ':', owner_id, ':', role, ':', status, ':', sha256)
                FROM lab_media_object WHERE id=?
                """, String.class, mediaId);
    }

    private String pm001Fact(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT CONCAT(device_code, ':', project_id, ':', status)
                FROM iot_device WHERE device_code='PM-001'
                """, String.class);
    }

    private int tableCount(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE UPPER(table_schema)=UPPER(SCHEMA()) AND UPPER(table_name)=UPPER(?)
                """, Integer.class, tableName);
    }

    private void assertUniqueIndex(JdbcTemplate jdbc, String tableName, String indexName) {
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE UPPER(table_name)=UPPER(?) AND UPPER(constraint_name)=UPPER(?)
                  AND constraint_type='UNIQUE'
                """, Integer.class, tableName, indexName));
    }

    private int primaryKeyColumnCount(JdbcTemplate jdbc, String tableName, String columnName) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_catalog=kcu.constraint_catalog
                 AND tc.constraint_schema=kcu.constraint_schema
                 AND tc.constraint_name=kcu.constraint_name
                 AND tc.table_name=kcu.table_name
                WHERE UPPER(tc.table_name)=UPPER(?) AND UPPER(kcu.column_name)=UPPER(?)
                  AND tc.constraint_type='PRIMARY KEY'
                """, Integer.class, tableName, columnName);
    }

    private int columnLength(JdbcTemplate jdbc, String tableName, String columnName) {
        return jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE UPPER(table_schema)=UPPER(SCHEMA())
                  AND UPPER(table_name)=UPPER(?) AND UPPER(column_name)=UPPER(?)
                """, Integer.class, tableName, columnName);
    }

    private int occurrences(String text, String token) {
        return (text.length() - text.replace(token, "").length()) / token.length();
    }
}
