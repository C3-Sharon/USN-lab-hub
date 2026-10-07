package com.usn.labhub.user.project;

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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:week4-task-workbench;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ProjectTaskWorkbenchIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private IAttendanceService attendanceService;

    @BeforeEach
    void cleanData() {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayStatus(0);
        attendance.setTodayRecords(List.of());
        attendance.setWeekHours(0.0);
        attendance.setSemesterHours(0.0);
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);

        jdbc.update("DELETE FROM lab_task");
        jdbc.update("DELETE FROM lab_milestone");
        jdbc.update("DELETE FROM lab_project_member WHERE project_id > 1");
        jdbc.update("DELETE FROM lab_project WHERE id > 1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsOnlyCurrentAssigneesCountsNaturalWeekAndFiveRecentTasks() {
        LocalDate monday = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime weekStart = monday.atStartOfDay();
        Long projectId = insertProject("WB-TASKS");

        insertTask(projectId, "本周完成", "DONE", 2L, weekStart.plusHours(1));
        insertTask(projectId, "上周完成", "DONE", 2L, weekStart.minusSeconds(1));
        insertTask(projectId, "下周边界", "DONE", 2L, weekStart.plusWeeks(1));
        insertTask(projectId, "待开始", "TODO", 2L, weekStart.plusDays(1));
        insertTask(projectId, "进行中", "IN_PROGRESS", 2L, weekStart.plusDays(2));
        insertTask(projectId, "已阻塞", "BLOCKED", 2L, weekStart.plusDays(3));
        insertTask(projectId, "已取消", "CANCELED", 2L, weekStart.plusDays(4));
        insertTask(projectId, "他人任务", "TODO", 1L, weekStart.plusDays(5));

        String token = token(login("20260001", "20260001"));
        ResponseEntity<Map> response = rest.exchange("/api/workbench/overview", HttpMethod.GET,
                authorized(token), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> overview = (Map<String, Object>) response.getBody().get("data");
        Map<String, Object> tasks = (Map<String, Object>) overview.get("tasks");
        assertEquals("READY", tasks.get("state"));
        assertEquals(1, tasks.get("todo"));
        assertEquals(1, tasks.get("inProgress"));
        assertEquals(1, tasks.get("blocked"));
        assertEquals(1, tasks.get("doneThisWeek"));
        List<Map<String, Object>> list = (List<Map<String, Object>>) tasks.get("list");
        assertEquals(5, list.size());
        assertEquals("下周边界", list.get(0).get("title"));
        assertEquals("WB-TASKS", list.get(0).get("projectCode"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsReadyEmptyStateWhenUserHasNoAssignedTasks() {
        String token = token(login("20260001", "20260001"));
        ResponseEntity<Map> response = rest.exchange("/api/workbench/overview", HttpMethod.GET,
                authorized(token), Map.class);

        Map<String, Object> overview = (Map<String, Object>) response.getBody().get("data");
        Map<String, Object> tasks = (Map<String, Object>) overview.get("tasks");
        assertEquals("READY", tasks.get("state"));
        assertEquals(0, tasks.get("todo"));
        assertEquals(0, tasks.get("doneThisWeek"));
        assertEquals(List.of(), tasks.get("list"));
    }

    private Long insertProject(String code) {
        jdbc.update("""
                INSERT INTO lab_project(project_code, project_name, owner_id, status, public_visible)
                VALUES (?, ?, 1, 'ACTIVE', FALSE)
                """, code, code + " 项目");
        return jdbc.queryForObject("SELECT id FROM lab_project WHERE project_code=?", Long.class, code);
    }

    private void insertTask(Long projectId, String title, String status, Long assignee, LocalDateTime updateTime) {
        jdbc.update("""
                INSERT INTO lab_task(project_id, title, status, assignee_user_id, created_by,
                    create_time, update_time)
                VALUES (?, ?, ?, ?, 1, ?, ?)
                """, projectId, title, status, assignee, updateTime.minusHours(1), updateTime);
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

    private HttpEntity<Void> authorized(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
