package com.usn.labhub.user.learning;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@EnabledIfEnvironmentVariable(named = "USNHUB_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
class ExperimentTemplateVersionPublishMySqlConcurrencyTest {

    @Test
    void conditionalPublishAllowsExactlyOneConcurrentWinner() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            String url = System.getenv("USNHUB_TEST_MYSQL_URL");
            String username = System.getenv("USNHUB_TEST_MYSQL_USERNAME");
            String password = System.getenv("USNHUB_TEST_MYSQL_PASSWORD");
            Flyway.configure().dataSource(url, username, password)
                    .locations("classpath:db/migration").load().migrate();

            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long templateId = null;
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                jdbc.update("""
                        INSERT INTO lab_experiment_template(title, scope, created_by)
                        VALUES ('MySQL 并发发布验证', 'SYSTEM', 1)
                        """);
                templateId = jdbc.queryForObject("""
                        SELECT id FROM lab_experiment_template
                        WHERE title = 'MySQL 并发发布验证'
                        ORDER BY id DESC LIMIT 1
                        """, Long.class);
                Long createdTemplateId = templateId;
                jdbc.update("""
                        INSERT INTO lab_experiment_template_version(
                            template_id, version, status, draft_slot, schema_json,
                            field_count, created_by
                        ) VALUES (?, '0.1.0', 'DRAFT', 1,
                                  '{"schemaVersion":"1.0","type":"object","properties":{"voltage":{"type":"number","title":"Voltage","displayOrder":1}}}',
                                  1, 1)
                        """, createdTemplateId);
                Long draftId = jdbc.queryForObject("""
                        SELECT id FROM lab_experiment_template_version
                        WHERE template_id = ? AND status = 'DRAFT'
                        """, Long.class, createdTemplateId);

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> attempts = new ArrayList<>();
                for (int index = 0; index < 2; index++) {
                    attempts.add(executor.submit(() -> publishConditionally(
                            dataSource, createdTemplateId, draftId, ready, start)));
                }
                ready.await(5, TimeUnit.SECONDS);
                start.countDown();

                List<Integer> results = new ArrayList<>();
                for (Future<Integer> attempt : attempts) {
                    results.add(attempt.get(10, TimeUnit.SECONDS));
                }
                Collections.sort(results);
                assertEquals(List.of(0, 1), results);
                assertEquals(1, jdbc.queryForObject("""
                        SELECT COUNT(*) FROM lab_experiment_template_version
                        WHERE template_id = ? AND status = 'PUBLISHED'
                        """, Integer.class, createdTemplateId));
            } finally {
                executor.shutdownNow();
                if (templateId != null) {
                    jdbc.update("DELETE FROM lab_experiment_template_version WHERE template_id = ?", templateId);
                    jdbc.update("DELETE FROM lab_experiment_template WHERE id = ?", templateId);
                }
            }
        });
    }

    private int publishConditionally(DriverManagerDataSource dataSource,
                                     Long templateId,
                                     Long draftId,
                                     CountDownLatch ready,
                                     CountDownLatch start) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE lab_experiment_template_version
                    SET version = '1.0.0', version_code = 10000, status = 'PUBLISHED',
                        draft_slot = NULL, published_by = 1, published_at = CURRENT_TIMESTAMP
                    WHERE template_id = ? AND id = ? AND status = 'DRAFT'
                    """)) {
                statement.setLong(1, templateId);
                statement.setLong(2, draftId);
                int updated = statement.executeUpdate();
                if (updated == 1) {
                    Thread.sleep(200);
                }
                connection.commit();
                return updated;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}
