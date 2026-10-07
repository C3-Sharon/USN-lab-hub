package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.domain.vo.WorkbenchOverviewVO;
import com.usn.labhub.user.domain.vo.iot.IotLatestMetricsVO;
import com.usn.labhub.user.domain.vo.project.ProjectWorkbenchItemVO;
import com.usn.labhub.user.domain.vo.project.TaskWorkbenchItemVO;
import com.usn.labhub.user.domain.vo.project.TaskWorkbenchStatsVO;
import com.usn.labhub.user.domain.vo.project.TaskWorkbenchSummaryVO;
import com.usn.labhub.user.service.iot.IotOperationsService;
import com.usn.labhub.user.service.iot.IotTelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkbenchServiceTest {

    private IAttendanceService attendanceService;
    private IotTelemetryService telemetryService;
    private IotOperationsService operationsService;
    private ProjectService projectService;
    private ProjectTaskService projectTaskService;
    private WorkbenchService service;

    @BeforeEach
    void setUp() {
        attendanceService = mock(IAttendanceService.class);
        telemetryService = mock(IotTelemetryService.class);
        operationsService = mock(IotOperationsService.class);
        projectService = mock(ProjectService.class);
        projectTaskService = mock(ProjectTaskService.class);
        service = new WorkbenchService(attendanceService, telemetryService, operationsService,
                projectService, projectTaskService);
    }

    @Test
    void buildsSixRegionOverviewFromCurrentFacts() {
        when(attendanceService.getOverview(2L)).thenReturn(attendance());
        IotLatestMetricsVO latest = new IotLatestMetricsVO();
        latest.setStatus("ONLINE");
        when(telemetryService.latest(1L)).thenReturn(latest);
        when(operationsService.countOpenWarningAlerts(1L)).thenReturn(2L);
        when(projectService.workbenchProjects(2L)).thenReturn(List.of(project("ACTIVE")));
        when(projectTaskService.workbenchSummary(2L)).thenReturn(taskSummary(2, 1, 1, 3));

        WorkbenchOverviewVO result = service.getOverview(2L);

        assertEquals("READY", result.getAttendance().getState());
        assertEquals("READY", result.getProjects().getState());
        assertEquals(1, result.getProjects().getTotal());
        assertEquals(1, result.getProjects().getActive());
        assertEquals("READY", result.getTasks().getState());
        assertEquals(2, result.getTasks().getTodo());
        assertEquals(3, result.getTasks().getDoneThisWeek());
        assertEquals("NOT_AVAILABLE", result.getLearning().getState());
        assertEquals("NOT_AVAILABLE", result.getNotifications().getState());
        assertEquals("READY", result.getDeviceReminder().getState());
        assertEquals(1, result.getDeviceReminder().getOnlineCount());
        assertEquals(2, result.getDeviceReminder().getAlertCount());
    }

    @Test
    void degradesOnlyDeviceReminderWhenIotQueryFails() {
        when(attendanceService.getOverview(2L)).thenReturn(attendance());
        when(projectService.workbenchProjects(2L)).thenReturn(List.of());
        when(projectTaskService.workbenchSummary(2L)).thenReturn(taskSummary(0, 0, 0, 0));
        when(telemetryService.latest(1L)).thenThrow(new IllegalStateException("database unavailable"));

        WorkbenchOverviewVO result = service.getOverview(2L);

        assertEquals("READY", result.getAttendance().getState());
        assertEquals("ERROR", result.getDeviceReminder().getState());
        assertEquals("DEVICE_REMINDER_LOAD_FAILED", result.getDeviceReminder().getErrorCode());
        assertEquals(true, result.getDeviceReminder().getRetryable());
    }

    @Test
    void degradesOnlyProjectsWhenProjectQueryFails() {
        when(attendanceService.getOverview(2L)).thenReturn(attendance());
        when(projectService.workbenchProjects(2L)).thenThrow(new IllegalStateException("project unavailable"));
        when(projectTaskService.workbenchSummary(2L)).thenReturn(taskSummary(0, 0, 0, 0));
        IotLatestMetricsVO latest = new IotLatestMetricsVO();
        latest.setStatus("OFFLINE");
        when(telemetryService.latest(1L)).thenReturn(latest);
        when(operationsService.countOpenWarningAlerts(1L)).thenReturn(0L);

        WorkbenchOverviewVO result = service.getOverview(2L);

        assertEquals("READY", result.getAttendance().getState());
        assertEquals("ERROR", result.getProjects().getState());
        assertEquals("PROJECTS_LOAD_FAILED", result.getProjects().getErrorCode());
        assertEquals(true, result.getProjects().getRetryable());
        assertEquals("READY", result.getDeviceReminder().getState());
    }

    @Test
    void degradesOnlyTasksWhenTaskQueryFails() {
        when(attendanceService.getOverview(2L)).thenReturn(attendance());
        when(projectService.workbenchProjects(2L)).thenReturn(List.of());
        when(projectTaskService.workbenchSummary(2L)).thenThrow(new IllegalStateException("task unavailable"));
        IotLatestMetricsVO latest = new IotLatestMetricsVO();
        latest.setStatus("OFFLINE");
        when(telemetryService.latest(1L)).thenReturn(latest);
        when(operationsService.countOpenWarningAlerts(1L)).thenReturn(0L);

        WorkbenchOverviewVO result = service.getOverview(2L);

        assertEquals("READY", result.getAttendance().getState());
        assertEquals("READY", result.getProjects().getState());
        assertEquals("ERROR", result.getTasks().getState());
        assertEquals("TASKS_LOAD_FAILED", result.getTasks().getErrorCode());
        assertEquals(true, result.getTasks().getRetryable());
        assertEquals("READY", result.getDeviceReminder().getState());
    }

    @Test
    void attendanceFailureFailsTheWholeOverview() {
        when(attendanceService.getOverview(2L)).thenThrow(new IllegalStateException("attendance unavailable"));

        assertThrows(IllegalStateException.class, () -> service.getOverview(2L));
    }

    private LoginVO.AttendanceInfo attendance() {
        LoginVO.AttendanceInfo info = new LoginVO.AttendanceInfo();
        info.setTodayStatus(1);
        info.setCheckInTime("08:30");
        info.setWeekHours(12.5);
        info.setSemesterHours(48.3);
        info.setCheckInDate("2026-09-14");
        info.setTodayRecords(List.of());
        return info;
    }

    private ProjectWorkbenchItemVO project(String status) {
        ProjectWorkbenchItemVO item = new ProjectWorkbenchItemVO();
        item.setId(10L);
        item.setCode("TEST-001");
        item.setName("测试项目");
        item.setStatus(status);
        item.setMyRole("MEMBER");
        return item;
    }

    private TaskWorkbenchSummaryVO taskSummary(int todo, int inProgress, int blocked, int doneThisWeek) {
        TaskWorkbenchStatsVO stats = new TaskWorkbenchStatsVO();
        stats.setTodo(todo);
        stats.setInProgress(inProgress);
        stats.setBlocked(blocked);
        stats.setDoneThisWeek(doneThisWeek);
        TaskWorkbenchItemVO item = new TaskWorkbenchItemVO();
        item.setId(1L);
        item.setTitle("测试任务");
        return new TaskWorkbenchSummaryVO(stats, List.of(item));
    }
}
