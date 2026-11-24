package com.usn.labhub.user.inventory;

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

class InventoryIssuanceMigrationTest {

    private static final List<String> V14_TABLES = List.of(
            "LAB_INVENTORY_ISSUE_POLICY_VERSION",
            "LAB_INVENTORY_ISSUE_POLICY_FORCED_CATEGORY",
            "LAB_INVENTORY_ISSUE_POLICY_COMPONENT_OVERRIDE",
            "LAB_INVENTORY_ISSUE_REQUEST",
            "LAB_INVENTORY_ISSUE_LINE",
            "LAB_INVENTORY_ISSUE_APPROVAL_RECORD",
            "LAB_INVENTORY_RESERVATION",
            "LAB_INVENTORY_RESERVATION_ALLOCATION",
            "LAB_INVENTORY_FULFILLMENT",
            "LAB_INVENTORY_FULFILLMENT_LINE",
            "LAB_INVENTORY_RETURN",
            "LAB_INVENTORY_RETURN_LINE",
            "LAB_INVENTORY_WRITE_OFF_COMMAND"
    );

    @Test
    void upgradesV13PreservesW45FactsAndAddsIssuanceConstraints() {
        String url = "jdbc:h2:mem:inventory-issuance-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target("13")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        long componentId = insertW45InventoryFacts(jdbc);
        String factsBefore = inventoryFacts(jdbc, componentId);
        String pm001Before = pm001Fact(jdbc);

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        for (String table : V14_TABLES) {
            assertEquals(1, tableCount(jdbc, table), table + " should exist");
        }
        assertEquals(factsBefore, inventoryFacts(jdbc, componentId));
        assertEquals(pm001Before, pm001Fact(jdbc));
        assertEquals("8.000:0.000:3", jdbc.queryForObject("""
                SELECT CONCAT(on_hand_quantity, ':', reserved_quantity, ':', version)
                FROM lab_component_inventory_balance WHERE component_id=?
                """, String.class, componentId));
        assertEquals("8.000:0.000", jdbc.queryForObject("""
                SELECT CONCAT(current_quantity, ':', reserved_quantity)
                FROM lab_inventory_batch WHERE component_id=?
                """, String.class, componentId));
        assertEquals(0, columnCount(jdbc, "LAB_INVENTORY_BATCH", "VERSION"));
        assertEquals(1, columnCount(jdbc, "LAB_INVENTORY_MOVEMENT", "REASON"));
        assertUniqueIndex(jdbc, "LAB_INVENTORY_MOVEMENT", "UK_INVENTORY_MOVEMENT_SOURCE");
        assertUniqueIndex(jdbc, "LAB_INVENTORY_ISSUE_REQUEST", "UK_INVENTORY_ISSUE_REQUEST_NO");
        assertUniqueIndex(jdbc, "LAB_INVENTORY_RESERVATION", "UK_INVENTORY_RESERVATION_REQUEST_LINE");
        assertUniqueIndex(jdbc, "LAB_INVENTORY_FULFILLMENT", "UK_INVENTORY_FULFILLMENT_REQUEST");

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                UPDATE lab_component_inventory_balance
                SET reserved_quantity=on_hand_quantity + 1
                WHERE component_id=?
                """, componentId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                UPDATE lab_inventory_batch
                SET reserved_quantity=current_quantity + 1
                WHERE component_id=?
                """, componentId));
    }

    @Test
    void migrationIsAdditiveAndDeclaresExactMovementCompatibility() throws IOException {
        String sql = new String(new ClassPathResource(
                "db/migration/V14__create_inventory_issuance_tables.sql"
        ).getInputStream().readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
        String normalized = sql.replace(String.valueOf((char) 96), "");

        assertFalse(normalized.contains("DROP TABLE"));
        assertFalse(normalized.contains("TRUNCATE TABLE"));
        assertFalse(normalized.contains("RENAME TABLE"));
        assertFalse(normalized.contains("DELETE FROM LAB_INVENTORY_MOVEMENT"));
        assertFalse(normalized.contains("UPDATE LAB_INVENTORY_MOVEMENT"));
        assertFalse(normalized.contains("CREATE TABLE LAB_REQUEST_IDEMPOTENCY"));
        assertFalse(normalized.contains("CREATE TABLE LAB_PROCUREMENT_AUDIT_LOG"));
        assertTrue(normalized.contains("ADD COLUMN RESERVED_QUANTITY DECIMAL(19,3) NOT NULL DEFAULT 0.000"));
        assertTrue(normalized.contains("DROP CONSTRAINT CK_INVENTORY_MOVEMENT_TYPE"));
        assertTrue(normalized.contains("DROP CONSTRAINT CK_INVENTORY_MOVEMENT_SOURCE"));
        assertTrue(normalized.contains("DROP CONSTRAINT CK_INVENTORY_MOVEMENT_QUANTITIES"));
        assertTrue(sql.contains("'PROCUREMENT_RECEIPT_IN'"));
        assertTrue(sql.contains("'ISSUE_OUT'"));
        assertTrue(sql.contains("'RETURN_IN'"));
        assertTrue(sql.contains("'LOSS_OUT'"));
        assertTrue(sql.contains("'SCRAP_OUT'"));
        assertFalse(sql.contains("'STOCKTAKE_ADJUSTMENT'"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void migratesV13ToV14OnRealMysqlAndPreservesW45Facts() {
        String url = System.getenv("USNHUB_TEST_MYSQL_URL");
        String username = System.getenv("USNHUB_TEST_MYSQL_USERNAME");
        String password = System.getenv("USNHUB_TEST_MYSQL_PASSWORD");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("13")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("13")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long componentId = insertW45InventoryFacts(jdbc);
        String factsBefore = inventoryFacts(jdbc, componentId);
        String pm001Before = pm001Fact(jdbc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertEquals(factsBefore, inventoryFacts(jdbc, componentId));
        assertEquals(pm001Before, pm001Fact(jdbc));
        for (String table : V14_TABLES) {
            assertEquals(1, tableCount(jdbc, table), table + " should exist on MySQL");
        }
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM lab_component_inventory_balance WHERE reserved_quantity<>0
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM lab_inventory_batch WHERE reserved_quantity<>0
                """, Integer.class));
    }

    private long insertW45InventoryFacts(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO lab_component_category(
                    code, name, normalized_name, parent_scope_id, level, path,
                    created_by, updated_by
                ) VALUES ('ISS_TEST', '领用迁移测试', '领用迁移测试', 0, 1, '/ISS_TEST/', 1, 1)
                """);
        long categoryId = jdbc.queryForObject(
                "SELECT id FROM lab_component_category WHERE code='ISS_TEST'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component_manufacturer(
                    code, name, normalized_name, created_by, updated_by
                ) VALUES ('ISS_TEST_MFR', 'Issuance Test', 'ISSUANCE TEST', 1, 1)
                """);
        long manufacturerId = jdbc.queryForObject(
                "SELECT id FROM lab_component_manufacturer WHERE code='ISS_TEST_MFR'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component(
                    catalog_code, name, manufacturer_id, mpn, normalized_mpn,
                    category_id, package_type, base_unit, status, created_by, updated_by
                ) VALUES ('CMP-91000001', '领用迁移元器件', ?, 'ISS-001', 'ISS-001',
                          ?, 'SMD', 'PCS', 'ACTIVE', 1, 1)
                """, manufacturerId, categoryId);
        long componentId = jdbc.queryForObject(
                "SELECT id FROM lab_component WHERE catalog_code='CMP-91000001'", Long.class);

        jdbc.update("""
                INSERT INTO lab_procurement_order(
                    purchase_no, title, requester_id, requester_name, currency,
                    item_subtotal, shipping_amount, discount_amount, payable_amount,
                    status, approval_required, approval_policy_version,
                    created_by, updated_by
                ) VALUES ('PUR-ISS-MIGRATION', '领用迁移采购事实', 1, 'system', 'CNY',
                          80.00, 0.00, 0.00, 80.00, 'RECEIVED', 0, 1, 1, 1)
                """);
        long orderId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_order WHERE purchase_no='PUR-ISS-MIGRATION'", Long.class);
        jdbc.update("""
                INSERT INTO lab_procurement_order_line(
                    order_id, line_no, component_id, component_name, unit,
                    ordered_quantity, unit_price, line_subtotal, allocated_net_amount,
                    effective_unit_cost, received_quantity, version
                ) VALUES (?, 1, ?, '领用迁移元器件', 'PCS',
                          8.000, 10.0000, 80.00, 80.00, 10.0000, 8.000, 1)
                """, orderId, componentId);
        long orderLineId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_order_line WHERE order_id=?", Long.class, orderId);
        jdbc.update("""
                INSERT INTO lab_procurement_receipt(
                    receipt_no, order_id, status, received_at, receiver_id,
                    receiver_name, confirmed_at, version, created_by, updated_by,
                    active_draft_slot
                ) VALUES ('REC-ISS-MIGRATION', ?, 'CONFIRMED', CURRENT_TIMESTAMP,
                          1, 'system', CURRENT_TIMESTAMP, 1, 1, 1, NULL)
                """, orderId);
        long receiptId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_receipt WHERE receipt_no='REC-ISS-MIGRATION'", Long.class);
        jdbc.update("""
                INSERT INTO lab_procurement_receipt_line(
                    receipt_id, order_line_id, component_id, quantity, unit, version
                ) VALUES (?, ?, ?, 8.000, 'PCS', 1)
                """, receiptId, orderLineId, componentId);
        long receiptLineId = jdbc.queryForObject(
                "SELECT id FROM lab_procurement_receipt_line WHERE receipt_id=?", Long.class, receiptId);

        jdbc.update("""
                INSERT INTO lab_component_inventory_balance(
                    component_id, on_hand_quantity, unit, version
                ) VALUES (?, 8.000, 'PCS', 3)
                """, componentId);
        jdbc.update("""
                INSERT INTO lab_inventory_batch(
                    batch_no, component_id, source_receipt_line_id,
                    initial_quantity, current_quantity, unit, unit_cost,
                    status, created_by
                ) VALUES ('BAT-ISS-MIGRATION', ?, ?, 8.000, 8.000,
                          'PCS', 10.0000, 'AVAILABLE', 1)
                """, componentId, receiptLineId);
        long batchId = jdbc.queryForObject(
                "SELECT id FROM lab_inventory_batch WHERE batch_no='BAT-ISS-MIGRATION'", Long.class);
        jdbc.update("""
                INSERT INTO lab_inventory_movement(
                    movement_no, movement_type, component_id, batch_id,
                    source_type, source_id, quantity_delta, balance_before,
                    balance_after, unit, unit_cost, operator_id, occurred_at, request_id
                ) VALUES ('MOV-ISS-MIGRATION', 'PROCUREMENT_RECEIPT_IN', ?, ?,
                          'RECEIPT_LINE', ?, 8.000, 0.000, 8.000,
                          'PCS', 10.0000, 1, CURRENT_TIMESTAMP, 'migration-w45-fact')
                """, componentId, batchId, receiptLineId);
        return componentId;
    }

    private String inventoryFacts(JdbcTemplate jdbc, long componentId) {
        return jdbc.queryForObject("""
                SELECT CONCAT(
                    b.on_hand_quantity, ':', b.unit, ':', b.version, ':',
                    ib.batch_no, ':', ib.initial_quantity, ':', ib.current_quantity, ':',
                    m.movement_no, ':', m.movement_type, ':', m.quantity_delta, ':',
                    m.balance_before, ':', m.balance_after
                )
                FROM lab_component_inventory_balance b
                JOIN lab_inventory_batch ib ON ib.component_id=b.component_id
                JOIN lab_inventory_movement m ON m.batch_id=ib.id
                WHERE b.component_id=?
                """, String.class, componentId);
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

    private int columnCount(JdbcTemplate jdbc, String tableName, String columnName) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE UPPER(table_schema)=UPPER(SCHEMA())
                  AND UPPER(table_name)=UPPER(?) AND UPPER(column_name)=UPPER(?)
                """, Integer.class, tableName, columnName);
    }

    private void assertUniqueIndex(JdbcTemplate jdbc, String tableName, String indexName) {
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE UPPER(table_name)=UPPER(?) AND UPPER(constraint_name)=UPPER(?)
                  AND constraint_type='UNIQUE'
                """, Integer.class, tableName, indexName));
    }
}
