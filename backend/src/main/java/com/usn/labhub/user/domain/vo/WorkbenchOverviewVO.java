package com.usn.labhub.user.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.usn.labhub.user.domain.vo.project.ProjectWorkbenchItemVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "个人工作台首页聚合结果")
public class WorkbenchOverviewVO {

    private AttendanceSection attendance;
    private ProjectSection projects;
    private TaskSection tasks;
    private LearningSection learning;
    private NotificationSection notifications;
    private DeviceReminderSection deviceReminder;

    @Data
    public static class AttendanceSection {
        private String state;
        private Integer todayStatus;
        private String checkInTime;
        private String checkOutTime;
        private Double weekHours;
        private Double semesterHours;
        private String checkInDate;
        private List<LoginVO.RecordDetail> todayRecords = new ArrayList<>();
    }

    @Data
    public static class ProjectSection {
        private String state;
        private Integer total = 0;
        private Integer active = 0;
        private List<ProjectWorkbenchItemVO> list = new ArrayList<>();
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String errorCode;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String message;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Boolean retryable;
    }

    @Data
    public static class TaskSection {
        private String state;
        private Integer todo = 0;
        private Integer inProgress = 0;
        private Integer review = 0;
        private Integer done = 0;
        private List<Object> list = new ArrayList<>();
    }

    @Data
    public static class LearningSection {
        private String state;
        private Integer inProgress = 0;
        private List<Object> list = new ArrayList<>();
    }

    @Data
    public static class NotificationSection {
        private String state;
        private Integer pendingApproval = 0;
        private Integer alerts = 0;
        private List<Object> list = new ArrayList<>();
    }

    @Data
    public static class DeviceReminderSection {
        private String state;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Integer onlineCount;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Integer alertCount;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String errorCode;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String message;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Boolean retryable;
    }
}
