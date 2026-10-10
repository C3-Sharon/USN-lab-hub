package com.usn.labhub.user.auth;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalRoleMigrationTest {

    @Test
    void expandsGlobalRolesWithoutChangingExistingRoleIdsOrAssignments() {
        String url = "jdbc:h2:mem:global-role-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));

        assertEquals(
                List.of("1:SYSTEM_ADMIN", "2:MEMBER", "3:TEACHER", "4:STOCK_KEEPER"),
                jdbc.query("SELECT id, role_key FROM sys_role ORDER BY id",
                        (rs, rowNum) -> rs.getInt("id") + ":" + rs.getString("role_key"))
        );
        assertEquals(
                List.of("1:1", "2:2"),
                jdbc.query("SELECT user_id, role_id FROM sys_user_role ORDER BY user_id, role_id",
                        (rs, rowNum) -> rs.getLong("user_id") + ":" + rs.getInt("role_id"))
        );
    }
}
