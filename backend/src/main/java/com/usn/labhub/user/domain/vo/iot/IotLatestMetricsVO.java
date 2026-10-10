package com.usn.labhub.user.domain.vo.iot;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "IoT 设备最新指标")
public class IotLatestMetricsVO {

    @Schema(description = "设备 ID")
    private Long deviceId;

    @Schema(description = "设备编号")
    private String deviceCode;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "设备状态")
    private String status;

    @Schema(description = "上报时间")
    private String reportTime;

    @Schema(description = "最新指标列表")
    private List<MetricValueVO> metrics;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "IoT 指标值")
    public static class MetricValueVO {

        @Schema(description = "指标键")
        private String metricKey;

        @Schema(description = "指标名称")
        private String metricName;

        @Schema(description = "指标值")
        private BigDecimal value;

        @Schema(description = "单位")
        private String unit;
    }
}
