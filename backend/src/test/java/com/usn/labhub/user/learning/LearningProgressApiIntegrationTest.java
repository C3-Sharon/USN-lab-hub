package com.usn.labhub.user.learning;

import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.service.IAttendanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=${USNHUB_TEST_DB_URL:jdbc:h2:mem:week5-learning-progress;MODE=MySQL;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${USNHUB_TEST_DB_USERNAME:sa}",
                "spring.datasource.password=${USNHUB_TEST_DB_PASSWORD:}",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class LearningProgressApiIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private IAttendanceService attendanceService;

    private Long roadmapId;
    private List<Long> unitIds;

    @BeforeEach
    void cleanData() {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayStatus(0);
        attendance.setTodayRecords(List.of());
        attendance.setWeekHours(0.0);
        attendance.setSemesterHours(0.0);
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);

        roadmapId = jdbc.queryForObject("""
                SELECT MIN(id) FROM lab_learning_roadmap
                WHERE title = '嵌入式硬件入门'
                """, Long.class);
        jdbc.update("DELETE FROM lab_learning_unit_completion");
        jdbc.update("DELETE FROM lab_learning_record");
        jdbc.update("UPDATE lab_learning_roadmap SET status = 'PUBLISHED' WHERE id = ?", roadmapId);
        unitIds = jdbc.queryForList("""
                SELECT u.id
                FROM lab_learning_unit u
                INNER JOIN lab_learning_stage s ON s.id = u.stage_id
                WHERE s.roadmap_id = ?
                ORDER BY s.sort_order, s.id, u.sort_order, u.id
                """, Long.class, roadmapId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void enrollmentIsIdempotentAndMyRoadmapsKeepsArchivedHistory() {
        String memberToken = token(login("20260001", "20260001"));

        Map<String, Object> first = data(post(enrollPath(), memberToken));
        Map<String, Object> repeated = data(post(enrollPath(), memberToken));
        assertEquals(first.get("id"), repeated.get("id"));
        assertEquals("NOT_STARTED", repeated.get("status"));
        assertEquals(0, intValue(repeated.get("progress")));
        assertEquals(unitIds.size(), intValue(repeated.get("totalUnitCount")));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM lab_learning_record WHERE roadmap_id = ? AND user_id = 2
                """, Integer.class, roadmapId));

        jdbc.update("UPDATE lab_learning_roadmap SET status = 'ARCHIVED' WHERE id = ?", roadmapId);
        ResponseEntity<Map> response = get("/api/learning/my-roadmaps?page=1&pageSize=20", memberToken);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> page = data(response);
        assertEquals(1, intValue(page.get("total")));
        List<Map<String, Object>> list = (List<Map<String, Object>>) page.get("list");
        assertEquals(roadmapId, longValue(list.get(0).get("roadmapId")));
    }

    @Test
    void completingAndCancellingUnitsRecalculatesProgressAndState() {
        String memberToken = token(login("20260001", "20260001"));
        post(enrollPath(), memberToken);

        Map<String, Object> first = data(post(completePath(unitIds.get(0)), memberToken));
        Map<String, Object> firstProgress = progress(first);
        assertEquals("IN_PROGRESS", firstProgress.get("status"));
        assertEquals(1, intValue(firstProgress.get("completedUnitCount")));
        assertEquals((int) Math.floor(100.0 / unitIds.size()), intValue(firstProgress.get("progress")));

        Map<String, Object> repeated = data(post(completePath(unitIds.get(0)), memberToken));
        assertEquals(1, intValue(progress(repeated).get("completedUnitCount")));
        assertEquals(1, completionCount());

        Map<String, Object> last = null;
        for (int index = 1; index < unitIds.size(); index++) {
            last = data(post(completePath(unitIds.get(index)), memberToken));
        }
        assertNotNull(last);
        assertEquals("COMPLETED", progress(last).get("status"));
        assertEquals(100, intValue(progress(last).get("progress")));
        assertNotNull(jdbc.queryForObject("""
                SELECT completed_at FROM lab_learning_record WHERE roadmap_id = ? AND user_id = 2
                """, Object.class, roadmapId));

        Map<String, Object> cancelled = data(delete(completePath(unitIds.get(0)), memberToken));
        assertEquals(Boolean.FALSE, cancelled.get("completed"));
        assertEquals("IN_PROGRESS", progress(cancelled).get("status"));
        assertEquals(unitIds.size() - 1, intValue(progress(cancelled).get("completedUnitCount")));
        assertNull(jdbc.queryForObject("""
                SELECT completed_at FROM lab_learning_record WHERE roadmap_id = ? AND user_id = 2
                """, Object.class, roadmapId));

        Map<String, Object> repeatedCancel = data(delete(completePath(unitIds.get(0)), memberToken));
        assertEquals(unitIds.size() - 1, intValue(progress(repeatedCancel).get("completedUnitCount")));
    }

    @Test
    void contractErrorsProtectEnrollmentAndArchivedRoadmaps() {
        String memberToken = token(login("20260001", "20260001"));
        assertReason(post(completePath(unitIds.get(0)), memberToken),
                HttpStatus.BAD_REQUEST, "LEARNING_NOT_ENROLLED");

        jdbc.update("UPDATE lab_learning_roadmap SET status = 'DRAFT' WHERE id = ?", roadmapId);
        assertReason(post(enrollPath(), memberToken),
                HttpStatus.NOT_FOUND, "LEARNING_ROADMAP_NOT_FOUND");

        jdbc.update("UPDATE lab_learning_roadmap SET status = 'ARCHIVED' WHERE id = ?", roadmapId);
        assertReason(post(enrollPath(), memberToken), HttpStatus.CONFLICT, "LEARNING_ARCHIVED");
        assertReason(post(completePath(unitIds.get(0)), memberToken),
                HttpStatus.CONFLICT, "LEARNING_ARCHIVED");
        assertReason(get("/api/learning/my-roadmaps?page=0&pageSize=20", memberToken),
                HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
    }

    @Test
    void concurrentEnrollmentAndCompletionRemainUniqueAndReachCompleted() throws Exception {
        String memberToken = token(login("20260001", "20260001"));
        List<ResponseEntity<Map>> enrollResponses = runConcurrently(8,
                index -> post(enrollPath(), memberToken));
        assertTrue(enrollResponses.stream().allMatch(response -> response.getStatusCode() == HttpStatus.OK));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM lab_learning_record WHERE roadmap_id = ? AND user_id = 2
                """, Integer.class, roadmapId));

        List<ResponseEntity<Map>> completionResponses = runConcurrently(unitIds.size(),
                index -> post(completePath(unitIds.get(index)), memberToken));
        assertTrue(completionResponses.stream().allMatch(response -> response.getStatusCode() == HttpStatus.OK));
        assertEquals(unitIds.size(), completionCount());

        Map<String, Object> page = data(get("/api/learning/my-roadmaps", memberToken));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) page.get("list");
        assertEquals("COMPLETED", list.get(0).get("status"));
        assertEquals(100, intValue(list.get(0).get("progress")));
    }

    private List<ResponseEntity<Map>> runConcurrently(
            int count, ThrowingRequest request) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < count; index++) {
                int requestIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    return request.execute(requestIndex);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<ResponseEntity<Map>> responses = new ArrayList<>();
            for (Future<ResponseEntity<Map>> future : futures) {
                responses.add(future.get(20, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }

    private int completionCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM lab_learning_unit_completion c
                INNER JOIN lab_learning_unit u ON u.id = c.unit_id
                INNER JOIN lab_learning_stage s ON s.id = u.stage_id
                WHERE s.roadmap_id = ? AND c.user_id = 2
                """, Integer.class, roadmapId);
    }

    private String enrollPath() {
        return "/api/learning/roadmaps/" + roadmapId + "/enroll";
    }

    private String completePath(Long unitId) {
        return "/api/learning/units/" + unitId + "/complete";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> progress(Map<String, Object> completion) {
        return (Map<String, Object>) completion.get("roadmapProgress");
    }

    private int intValue(Object value) {
        return ((Number) value).intValue();
    }

    private Long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private ResponseEntity<Map> login(String memberId, String password) {
        return rest.postForEntity("/usnhub/user/login",
                Map.of("memberId", memberId, "password", password), Map.class);
    }

    @SuppressWarnings("unchecked")
    private String token(ResponseEntity<Map> loginResponse) {
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        return ((Map<String, Object>) loginResponse.getBody().get("data")).get("token").toString();
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, authorized(token), Map.class);
    }

    private ResponseEntity<Map> post(String path, String token) {
        return rest.exchange(path, HttpMethod.POST, authorized(token), Map.class);
    }

    private ResponseEntity<Map> delete(String path, String token) {
        return rest.exchange(path, HttpMethod.DELETE, authorized(token), Map.class);
    }

    private HttpEntity<Void> authorized(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private void assertReason(ResponseEntity<Map> response, HttpStatus status, String reason) {
        assertEquals(status, response.getStatusCode());
        assertEquals(reason, response.getBody().get("reason"));
    }

    @FunctionalInterface
    private interface ThrowingRequest {
        ResponseEntity<Map> execute(int index) throws Exception;
    }
}
