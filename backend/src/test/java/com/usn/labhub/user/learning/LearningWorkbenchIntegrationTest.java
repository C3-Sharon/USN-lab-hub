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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=${USNHUB_TEST_DB_URL:jdbc:h2:mem:week5-learning-workbench;MODE=MySQL;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${USNHUB_TEST_DB_USERNAME:sa}",
                "spring.datasource.password=${USNHUB_TEST_DB_PASSWORD:}",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class LearningWorkbenchIntegrationTest {

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
        jdbc.update("DELETE FROM lab_learning_unit WHERE stage_id IN "
                + "(SELECT id FROM lab_learning_stage WHERE roadmap_id != ?)", roadmapId);
        jdbc.update("DELETE FROM lab_learning_stage WHERE roadmap_id != ?", roadmapId);
        jdbc.update("DELETE FROM lab_learning_roadmap WHERE id != ?", roadmapId);
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
    void returnsReadyEmptyLearningRegion() {
        Map<String, Object> learning = learningOverview(memberToken());

        assertEquals("READY", learning.get("state"));
        assertEquals(0, intValue(learning.get("inProgressCount")));
        assertEquals(0, intValue(learning.get("completedCount")));
        assertEquals(List.of(), learning.get("list"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void learningRegionTracksEnrollmentProgressAndCompletion() {
        String token = memberToken();
        assertEquals(HttpStatus.OK, post("/api/learning/roadmaps/" + roadmapId + "/enroll", token)
                .getStatusCode());
        assertEquals(HttpStatus.OK, post("/api/learning/units/" + unitIds.get(0) + "/complete", token)
                .getStatusCode());

        Map<String, Object> inProgress = learningOverview(token);
        assertEquals(1, intValue(inProgress.get("inProgressCount")));
        assertEquals(0, intValue(inProgress.get("completedCount")));
        List<Map<String, Object>> inProgressList = (List<Map<String, Object>>) inProgress.get("list");
        assertEquals(1, inProgressList.size());
        assertEquals("IN_PROGRESS", inProgressList.get(0).get("status"));
        assertEquals(1, intValue(inProgressList.get(0).get("completedUnitCount")));
        assertEquals(unitIds.size(), intValue(inProgressList.get(0).get("totalUnitCount")));

        for (int index = 1; index < unitIds.size(); index++) {
            assertEquals(HttpStatus.OK,
                    post("/api/learning/units/" + unitIds.get(index) + "/complete", token).getStatusCode());
        }
        Map<String, Object> completed = learningOverview(token);
        assertEquals(0, intValue(completed.get("inProgressCount")));
        assertEquals(1, intValue(completed.get("completedCount")));
        List<Map<String, Object>> completedList = (List<Map<String, Object>>) completed.get("list");
        assertEquals("COMPLETED", completedList.get(0).get("status"));
        assertEquals(100, intValue(completedList.get(0).get("progress")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void learningRegionReturnsOnlyThreeMostRecentlyUpdatedRoadmaps() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 10, 10, 0);
        for (int index = 1; index <= 4; index++) {
            jdbc.update("""
                    INSERT INTO lab_learning_roadmap
                        (title, status, difficulty, sort_order, created_by, create_time, update_time)
                    VALUES (?, 'PUBLISHED', 'BEGINNER', ?, 1, ?, ?)
                    """, "最近路线 " + index, index, base.plusMinutes(index), base.plusMinutes(index));
            Long id = jdbc.queryForObject(
                    "SELECT id FROM lab_learning_roadmap WHERE title = ?", Long.class, "最近路线 " + index);
            jdbc.update("""
                    INSERT INTO lab_learning_record
                        (roadmap_id, user_id, status, started_at, create_time, update_time)
                    VALUES (?, 2, 'NOT_STARTED', ?, ?, ?)
                    """, id, base.plusMinutes(index), base.plusMinutes(index), base.plusMinutes(index));
        }

        Map<String, Object> learning = learningOverview(memberToken());
        List<Map<String, Object>> list = (List<Map<String, Object>>) learning.get("list");
        assertEquals(3, list.size());
        assertEquals(List.of("最近路线 4", "最近路线 3", "最近路线 2"),
                list.stream().map(item -> item.get("roadmapTitle")).toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> learningOverview(String token) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/workbench/overview", HttpMethod.GET, authorized(token), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> overview = (Map<String, Object>) response.getBody().get("data");
        return (Map<String, Object>) overview.get("learning");
    }

    private String memberToken() {
        ResponseEntity<Map> response = rest.postForEntity("/usnhub/user/login",
                Map.of("memberId", "20260001", "password", "20260001"), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        return data.get("token").toString();
    }

    private ResponseEntity<Map> post(String path, String token) {
        return rest.exchange(path, HttpMethod.POST, authorized(token), Map.class);
    }

    private HttpEntity<Void> authorized(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private int intValue(Object value) {
        return ((Number) value).intValue();
    }
}
