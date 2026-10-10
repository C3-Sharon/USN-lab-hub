package com.usn.labhub.user.domain.vo.iot;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "公开项目展示聚合数据")
public class IotPublicProjectVO {

    private String projectCode;
    private String projectName;
    private String description;
    private String status;
    private int deviceCount;
    private int onlineDeviceCount;
    private PublicDeviceVO device;
    private List<IotMetricHistoryVO.MetricPointVO> powerTrend;
    private String updatedAt;

    @Data
    @Schema(description = "公开设备摘要")
    public static class PublicDeviceVO {
        private Long id;
        private String deviceCode;
        private String deviceName;
        private String status;
        private String reportTime;
        private List<IotLatestMetricsVO.MetricValueVO> metrics;
        private IotHealthVO health;
    }
}
