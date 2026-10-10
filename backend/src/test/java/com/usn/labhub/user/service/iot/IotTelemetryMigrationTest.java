package com.usn.labhub.user.service.iot;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IotTelemetryMigrationTest {

    @Test
    void v2MigrationCreatesTelemetryTables() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:migration-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V2__create_iot_telemetry_tables.sql")
        );
        populator.execute(dataSource);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'IOT_TELEMETRY_RAW'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'IOT_METRIC_DATA'",
                Integer.class
        ));
    }

    @Test
    void v4MigrationCreatesOperationsTables() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:operations-migration-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V4__create_iot_alert_command_log_tables.sql")
        );
        populator.execute(dataSource);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String table : new String[]{
                "IOT_ALERT_RECORD", "IOT_RECOMMENDATION", "IOT_COMMAND_RECORD", "IOT_OPERATION_LOG"
        }) {
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?",
                    Integer.class,
                    table
            ));
        }
    }
}
