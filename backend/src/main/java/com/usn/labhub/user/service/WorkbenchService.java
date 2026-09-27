package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.domain.vo.WorkbenchOverviewVO;
import com.usn.labhub.user.domain.vo.iot.IotLatestMetricsVO;
import com.usn.labhub.user.domain.vo.project.ProjectWorkbenchItemVO;
import com.usn.labhub.user.service.iot.IotOperationsService;
import com.usn.labhub.user.service.iot.IotTelemetryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class WorkbenchService {

    private static final String READY = "READY";
    private static final String NOT_AVAILABLE = "NOT_AVAILABLE";
    private static final String ERROR = "ERROR";

    private final IAttendanceService attendanceService;
    private final IotTelemetryService telemetryService;
    private final IotOperationsService operationsService;
    private final ProjectService projectService;

    public WorkbenchService(IAttendanceService attendanceService, IotTelemetryService telemetryService,
                            IotOperationsService operationsService, ProjectService projectService) {
        this.attendanceService = attendanceService;
        this.telemetryService = telemetryService;
        this.operationsService = operationsService;
        this.projectService = projectService;
    }

    public WorkbenchOverviewVO getOverview(Long userId) {
        LoginVO.AttendanceInfo attendance = attendanceService.getOverview(userId);

        WorkbenchOverviewVO overview = new WorkbenchOverviewVO();
        overview.setAttendance(attendanceSection(attendance));
        overview.setProjects(projectSection(userId));
        overview.setTasks(taskSection());
        overview.setLearning(learningSection());
        overview.setNotifications(notificationSection());
        overview.setDeviceReminder(deviceReminderSection());
        return overview;
    }

    private WorkbenchOverviewVO.AttendanceSection attendanceSection(LoginVO.AttendanceInfo source) {
        WorkbenchOverviewVO.AttendanceSection section = new WorkbenchOverviewVO.AttendanceSection();
        section.setState(READY);
        section.setTodayStatus(source.getTodayStatus());
        section.setCheckInTime(source.getCheckInTime());
        section.setCheckOutTime(source.getCheckOutTime());
        section.setWeekHours(source.getWeekHours());
        section.setSemesterHours(source.getSemesterHours());
        section.setCheckInDate(source.getCheckInDate());
        section.setTodayRecords(source.getTodayRecords() == null ? new ArrayList<>() : source.getTodayRecords());
        return section;
    }

    private WorkbenchOverviewVO.ProjectSection projectSection(Long userId) {
        WorkbenchOverviewVO.ProjectSection section = new WorkbenchOverviewVO.ProjectSection();
        try {
            List<ProjectWorkbenchItemVO> projects = projectService.workbenchProjects(userId);
            section.setState(READY);
            section.setList(projects);
            section.setTotal(projects.size());
            section.setActive((int) projects.stream()
                    .filter(project -> "ACTIVE".equals(project.getStatus()))
                    .count());
        } catch (RuntimeException e) {
            log.warn("Project summary query failed: {}", e.getMessage());
            section.setState(ERROR);
            section.setErrorCode("PROJECTS_LOAD_FAILED");
            section.setMessage("项目数据加载失败");
            section.setRetryable(true);
        }
        return section;
    }

    private WorkbenchOverviewVO.TaskSection taskSection() {
        WorkbenchOverviewVO.TaskSection section = new WorkbenchOverviewVO.TaskSection();
        section.setState(NOT_AVAILABLE);
        return section;
    }

    private WorkbenchOverviewVO.LearningSection learningSection() {
        WorkbenchOverviewVO.LearningSection section = new WorkbenchOverviewVO.LearningSection();
        section.setState(NOT_AVAILABLE);
        return section;
    }

    private WorkbenchOverviewVO.NotificationSection notificationSection() {
        WorkbenchOverviewVO.NotificationSection section = new WorkbenchOverviewVO.NotificationSection();
        section.setState(NOT_AVAILABLE);
        return section;
    }

    private WorkbenchOverviewVO.DeviceReminderSection deviceReminderSection() {
        WorkbenchOverviewVO.DeviceReminderSection section = new WorkbenchOverviewVO.DeviceReminderSection();
        try {
            IotLatestMetricsVO latest = telemetryService.latest(IotTelemetryService.PM001_DEVICE_ID);
            section.setState(READY);
            section.setOnlineCount("ONLINE".equals(latest.getStatus()) ? 1 : 0);
            section.setAlertCount(Math.toIntExact(
                    operationsService.countOpenWarningAlerts(IotTelemetryService.PM001_DEVICE_ID)));
        } catch (RuntimeException e) {
            log.warn("Device reminder query failed: {}", e.getMessage());
            section.setState(ERROR);
            section.setErrorCode("DEVICE_REMINDER_LOAD_FAILED");
            section.setMessage("设备提醒数据加载失败");
            section.setRetryable(true);
        }
        return section;
    }
}
