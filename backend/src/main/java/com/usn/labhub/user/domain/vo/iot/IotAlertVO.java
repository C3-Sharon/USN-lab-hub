package com.usn.labhub.user.domain.vo.iot;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class IotAlertVO {
    private Long id;
    private Long deviceId;
    private String deviceCode;
    private String metricKey;
    private String level;
    private String message;
    private BigDecimal triggerValue;
    private BigDecimal thresholdValue;
    private String status;
    private String createdAt;
    private Long handledBy;
    private String handledAt;
}
