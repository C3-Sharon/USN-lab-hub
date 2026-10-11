package com.usn.labhub.user.learning;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LearningRoadmapMigrationTest {

    @Test
    void upgradesV8DataAndCreatesSeededLearningRoadmap() {
        String url = "jdbc:h2:mem:learning-roadmap-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target("8")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
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
        int legacyMilestoneCount = jdbc.queryForObject("SELECT COUNT(*) FROM lab_milestone", Integer.class);
        int legacyTaskCount = jdbc.queryForObject("SELECT COUNT(*) FROM lab_task", Integer.class);

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
        assertEquals(legacyMilestoneCount,
                jdbc.queryForObject("SELECT COUNT(*) FROM lab_milestone", Integer.class));
        assertEquals(legacyTaskCount,
                jdbc.queryForObject("SELECT COUNT(*) FROM lab_task", Integer.class));

        Map<String, Object> roadmap = jdbc.queryForMap("""
                SELECT id, title, status, difficulty, estimated_hours, cover_media_id,
                       sort_order, created_by
                FROM lab_learning_roadmap
                WHERE title = '嵌入式硬件入门'
                """);
        assertEquals("PUBLISHED", roadmap.get("STATUS"));
        assertEquals("BEGINNER", roadmap.get("DIFFICULTY"));
        assertEquals(28, ((Number) roadmap.get("ESTIMATED_HOURS")).intValue());
        assertNull(roadmap.get("COVER_MEDIA_ID"));
        assertEquals(0, ((Number) roadmap.get("SORT_ORDER")).intValue());
        assertEquals(1L, ((Number) roadmap.get("CREATED_BY")).longValue());

        Long roadmapId = ((Number) roadmap.get("ID")).longValue();
        assertEquals(7, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_learning_stage WHERE roadmap_id = ?", Integer.class, roadmapId));
        assertEquals(7, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM lab_learning_unit u
                INNER JOIN lab_learning_stage s ON s.id = u.stage_id
                WHERE s.roadmap_id = ?
                """, Integer.class, roadmapId));

        List<String> stageNames = jdbc.queryForList("""
                SELECT name
                FROM lab_learning_stage
                WHERE roadmap_id = ?
                ORDER BY sort_order, id
                """, String.class, roadmapId);
        assertEquals(List.of(
                "实验室安全", "电路与焊接", "STM32/ESP32", "传感器", "MQTT", "原理图/PCB", "联网硬件"
        ), stageNames);

        assertLearningUniqueness(jdbc, roadmapId);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
    void migratesOnRealMysqlWithExpectedIndexesAndSeedData() {
        String url = System.getenv("USNHUB_TEST_MYSQL_URL");
        String username = System.getenv("USNHUB_TEST_MYSQL_USERNAME");
        String password = System.getenv("USNHUB_TEST_MYSQL_PASSWORD");

        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        Long roadmapId = jdbc.queryForObject("""
                SELECT id FROM lab_learning_roadmap
                WHERE title = '嵌入式硬件入门'
                """, Long.class);
        assertEquals(7, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_learning_stage WHERE roadmap_id = ?", Integer.class, roadmapId));
        assertEquals(3, indexCount(jdbc, "lab_learning_roadmap"));
        assertEquals(1, indexCount(jdbc, "lab_learning_stage"));
        assertEquals(1, indexCount(jdbc, "lab_learning_unit"));
        assertEquals(2, indexCount(jdbc, "lab_learning_record"));
        assertEquals(2, indexCount(jdbc, "lab_learning_unit_completion"));
        assertLearningUniqueness(jdbc, roadmapId);
    }

    private void assertLearningUniqueness(JdbcTemplate jdbc, long roadmapId) {
        Long unitId = jdbc.queryForObject("""
                SELECT u.id
                FROM lab_learning_unit u
                INNER JOIN lab_learning_stage s ON s.id = u.stage_id
                WHERE s.roadmap_id = ?
                ORDER BY s.sort_order, u.sort_order, u.id
                LIMIT 1
                """, Long.class, roadmapId);

        jdbc.update("DELETE FROM lab_learning_unit_completion WHERE unit_id = ? AND user_id = 2", unitId);
        jdbc.update("DELETE FROM lab_learning_record WHERE roadmap_id = ? AND user_id = 2", roadmapId);
        jdbc.update("INSERT INTO lab_learning_record(roadmap_id, user_id) VALUES (?, 2)", roadmapId);
        assertThrows(DuplicateKeyException.class,
                () -> jdbc.update("INSERT INTO lab_learning_record(roadmap_id, user_id) VALUES (?, 2)", roadmapId));

        jdbc.update("INSERT INTO lab_learning_unit_completion(unit_id, user_id) VALUES (?, 2)", unitId);
        assertThrows(DuplicateKeyException.class,
                () -> jdbc.update("INSERT INTO lab_learning_unit_completion(unit_id, user_id) VALUES (?, 2)", unitId));

        jdbc.update("DELETE FROM lab_learning_unit_completion WHERE unit_id = ? AND user_id = 2", unitId);
        jdbc.update("DELETE FROM lab_learning_record WHERE roadmap_id = ? AND user_id = 2", roadmapId);
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
