package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.domain.vo.WorkbenchOverviewVO;
import com.usn.labhub.user.domain.vo.iot.IotLatestMetricsVO;
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
    private WorkbenchService service;

    @BeforeEach
    void setUp() {
        attendanceService = mock(IAttendanceService.class);
        telemetryService = mock(IotTelemetryService.class);
        operationsService = mock(IotOperationsService.class);
        service = new WorkbenchService(attendanceService, telemetryService, operationsService);
    }

    @Test
    void buildsSixRegionOverviewFromCurrentFacts() {
        when(attendanceService.getOverview(2L)).thenReturn(attendance());
        IotLatestMetricsVO latest = new IotLatestMetricsVO();
        latest.setStatus("ONLINE");
        when(telemetryService.latest(1L)).thenReturn(latest);
        when(operationsService.countOpenWarningAlerts(1L)).thenReturn(2L);

        WorkbenchOverviewVO result = service.getOverview(2L);

        assertEquals("READY", result.getAttendance().getState());
        assertEquals("NOT_AVAILABLE", result.getProjects().getState());
        assertEquals("NOT_AVAILABLE", result.getTasks().getState());
        assertEquals("NOT_AVAILABLE", result.getLearning().getState());
        assertEquals("NOT_AVAILABLE", result.getNotifications().getState());
        assertEquals("READY", result.getDeviceReminder().getState());
        assertEquals(1, result.getDeviceReminder().getOnlineCount());
        assertEquals(2, result.getDeviceReminder().getAlertCount());
    }

    @Test
    void degradesOnlyDeviceReminderWhenIotQueryFails() {
        when(attendanceService.getOverview(2L)).thenReturn(attendance());
        when(telemetryService.latest(1L)).thenThrow(new IllegalStateException("database unavailable"));

        WorkbenchOverviewVO result = service.getOverview(2L);

        assertEquals("READY", result.getAttendance().getState());
        assertEquals("ERROR", result.getDeviceReminder().getState());
        assertEquals("DEVICE_REMINDER_LOAD_FAILED", result.getDeviceReminder().getErrorCode());
        assertEquals(true, result.getDeviceReminder().getRetryable());
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
}
