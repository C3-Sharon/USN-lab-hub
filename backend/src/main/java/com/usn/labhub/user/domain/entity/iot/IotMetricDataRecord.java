package com.usn.labhub.user.domain.entity.iot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IotMetricDataRecord {

    private Long deviceId;

    private String deviceCode;

    private String metricKey;

    private BigDecimal metricValue;

    private String unit;

    private LocalDateTime reportedAt;

    private LocalDateTime receivedAt;
}
