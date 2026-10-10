package com.usn.labhub.user.project;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectWorkspaceMigrationTest {

    @Test
    void upgradesV6DataWithoutBreakingLegacyProjectAndBackfillsOwnerMembership() {
        String url = "jdbc:h2:mem:project-workspace-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target("6")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        assertEquals("power-monitor", jdbc.queryForObject(
                "SELECT project_code FROM lab_project WHERE id = 1", String.class));

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Map<String, Object> project = jdbc.queryForMap("""
                SELECT project_code, project_name, status, category, cover_media_id
                FROM lab_project
                WHERE id = 1
                """);
        assertEquals("power-monitor", project.get("PROJECT_CODE"));
        assertEquals("实验室功耗监测", project.get("PROJECT_NAME"));
        assertEquals("ACTIVE", project.get("STATUS"));
        assertEquals("iot_demo", project.get("CATEGORY"));
        assertNull(project.get("COVER_MEDIA_ID"));

        Map<String, Object> membership = jdbc.queryForMap("""
                SELECT project_id, user_id, project_role
                FROM lab_project_member
                WHERE project_id = 1 AND user_id = 1
                """);
        assertEquals(1L, ((Number) membership.get("PROJECT_ID")).longValue());
        assertEquals(1L, ((Number) membership.get("USER_ID")).longValue());
        assertEquals("OWNER", membership.get("PROJECT_ROLE"));

        assertThrows(DuplicateKeyException.class, () -> jdbc.update("""
                INSERT INTO lab_project_member(project_id, user_id, project_role)
                VALUES (1, 1, 'OWNER')
                """));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void migratesOnRealMysqlWithExpectedIndexesAndLegacyData() {
        String url = System.getenv("USNHUB_TEST_MYSQL_URL");
        String username = System.getenv("USNHUB_TEST_MYSQL_USERNAME");
        String password = System.getenv("USNHUB_TEST_MYSQL_PASSWORD");

        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        assertEquals("power-monitor:iot_demo:ACTIVE", jdbc.queryForObject("""
                SELECT CONCAT(project_code, ':', category, ':', status)
                FROM lab_project
                WHERE id = 1
                """, String.class));
        assertEquals("1:1:OWNER", jdbc.queryForObject("""
                SELECT CONCAT(project_id, ':', user_id, ':', project_role)
                FROM lab_project_member
                WHERE project_id = 1 AND user_id = 1
                """, String.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'lab_project_member'
                  AND index_name = 'uk_project_member_user'
                  AND non_unique = 0
                """, Integer.class));
        assertThrows(DuplicateKeyException.class, () -> jdbc.update("""
                INSERT INTO lab_project_member(project_id, user_id, project_role)
                VALUES (1, 1, 'OWNER')
                """));
    }
}
