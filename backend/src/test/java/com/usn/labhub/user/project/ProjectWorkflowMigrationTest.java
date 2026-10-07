package com.usn.labhub.user.project;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectWorkflowMigrationTest {

    @Test
    void upgradesV7DataAndCreatesMilestoneTaskDefaults() {
        String url = "jdbc:h2:mem:project-workflow-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target("7")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        String legacyProject = jdbc.queryForObject("""
                SELECT CONCAT(project_code, ':', category, ':', status)
                FROM lab_project
                WHERE id = 1
                """, String.class);
        String legacyMembership = jdbc.queryForObject("""
                SELECT CONCAT(project_id, ':', user_id, ':', project_role)
                FROM lab_project_member
                WHERE project_id = 1 AND user_id = 1
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
        assertEquals(legacyMembership, jdbc.queryForObject("""
                SELECT CONCAT(project_id, ':', user_id, ':', project_role)
                FROM lab_project_member
                WHERE project_id = 1 AND user_id = 1
                """, String.class));
        assertEquals(legacyDevice, jdbc.queryForObject("""
                SELECT CONCAT(device_code, ':', project_id, ':', status)
                FROM iot_device
                WHERE id = 1
                """, String.class));

        jdbc.update("""
                INSERT INTO lab_milestone(project_id, name)
                VALUES (1, 'W40 migration milestone')
                """);
        Long milestoneId = jdbc.queryForObject("""
                SELECT id FROM lab_milestone
                WHERE project_id = 1 AND name = 'W40 migration milestone'
                """, Long.class);
        Map<String, Object> milestone = jdbc.queryForMap("""
                SELECT status, sort_order
                FROM lab_milestone
                WHERE id = ?
                """, milestoneId);
        assertEquals("PLANNED", milestone.get("STATUS"));
        assertEquals(0, ((Number) milestone.get("SORT_ORDER")).intValue());

        jdbc.update("""
                INSERT INTO lab_task(project_id, milestone_id, title, created_by)
                VALUES (1, ?, 'W40 migration task', 1)
                """, milestoneId);
        Map<String, Object> task = jdbc.queryForMap("""
                SELECT status, priority, version
                FROM lab_task
                WHERE project_id = 1 AND title = 'W40 migration task'
                """);
        assertEquals("TODO", task.get("STATUS"));
        assertEquals("MEDIUM", task.get("PRIORITY"));
        assertEquals(1, ((Number) task.get("VERSION")).intValue());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void migratesOnRealMysqlWithExpectedIndexesAndPreservedLegacyData() {
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
        assertEquals("PM-001:1:OFFLINE", jdbc.queryForObject("""
                SELECT CONCAT(device_code, ':', project_id, ':', status)
                FROM iot_device
                WHERE id = 1
                """, String.class));
        assertEquals(2, indexCount(jdbc, "lab_milestone"));
        assertEquals(4, indexCount(jdbc, "lab_task"));

        jdbc.update("DELETE FROM lab_task WHERE project_id = 1 AND title = 'W40 MySQL migration probe'");
        jdbc.update("DELETE FROM lab_milestone WHERE project_id = 1 AND name = 'W40 MySQL migration probe'");
        jdbc.update("""
                INSERT INTO lab_milestone(project_id, name)
                VALUES (1, 'W40 MySQL migration probe')
                """);
        Long milestoneId = jdbc.queryForObject("""
                SELECT id FROM lab_milestone
                WHERE project_id = 1 AND name = 'W40 MySQL migration probe'
                ORDER BY id DESC LIMIT 1
                """, Long.class);
        jdbc.update("""
                INSERT INTO lab_task(project_id, milestone_id, title, created_by)
                VALUES (1, ?, 'W40 MySQL migration probe', 1)
                """, milestoneId);
        assertEquals("TODO:MEDIUM:1", jdbc.queryForObject("""
                SELECT CONCAT(status, ':', priority, ':', version)
                FROM lab_task
                WHERE project_id = 1 AND title = 'W40 MySQL migration probe'
                """, String.class));
        int firstUpdate = jdbc.update("""
                UPDATE lab_task
                SET title = 'W40 MySQL optimistic winner', version = version + 1
                WHERE project_id = 1 AND id = ? AND version = 1
                """, jdbc.queryForObject("""
                SELECT id FROM lab_task
                WHERE project_id = 1 AND title = 'W40 MySQL migration probe'
                """, Long.class));
        int staleUpdate = jdbc.update("""
                UPDATE lab_task
                SET title = 'W40 MySQL stale overwrite', version = version + 1
                WHERE project_id = 1 AND title = 'W40 MySQL optimistic winner' AND version = 1
                """);
        assertEquals(1, firstUpdate);
        assertEquals(0, staleUpdate);
        assertEquals("W40 MySQL optimistic winner:2", jdbc.queryForObject("""
                SELECT CONCAT(title, ':', version)
                FROM lab_task
                WHERE project_id = 1 AND title = 'W40 MySQL optimistic winner'
                """, String.class));
        jdbc.update("DELETE FROM lab_task WHERE project_id = 1 AND title = 'W40 MySQL optimistic winner'");
        jdbc.update("DELETE FROM lab_milestone WHERE id = ?", milestoneId);
    }

    private int indexCount(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForObject("""
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name <> 'PRIMARY'
                """, Integer.class, tableName);
    }
}
