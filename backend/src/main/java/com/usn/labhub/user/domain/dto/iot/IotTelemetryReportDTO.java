package com.usn.labhub.user.domain.dto.iot;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Schema(description = "PM-001 遥测上报请求")
public class IotTelemetryReportDTO {

    @Schema(description = "项目编号")
    private String projectCode;

    @Schema(description = "设备编号")
    private String deviceCode;

    @Schema(description = "上报时间，推荐格式 yyyy-MM-dd HH:mm:ss")
    private String reportTime;

    @Schema(description = "兼容模拟器旧字段，ISO 时间字符串")
    private String timestamp;

    @Schema(description = "指标键值对")
    private Map<String, BigDecimal> metrics;

    @Schema(description = "设备状态")
    private String status;
}
