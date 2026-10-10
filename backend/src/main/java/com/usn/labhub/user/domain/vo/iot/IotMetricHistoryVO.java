package com.usn.labhub.user.domain.vo.iot;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "IoT metric history")
public class IotMetricHistoryVO {

    private Long deviceId;
    private String metricKey;
    private String unit;
    private List<MetricPointVO> points;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricPointVO {
        private String time;
        private BigDecimal value;
    }
}
