package com.usn.labhub.user.domain.entity.iot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IotAlertRecord {
    private Long id;
    private Long deviceId;
    private String deviceCode;
    private String metricKey;
    private String level;
    private String message;
    private BigDecimal triggerValue;
    private BigDecimal thresholdValue;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastTriggeredAt;
    private Long handledBy;
    private LocalDateTime handledAt;
}
