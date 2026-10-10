package com.usn.labhub.user.domain.entity.iot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IotTelemetryRawRecord {

    private String deviceCode;

    private String topic;

    private String payload;

    private String parseStatus;

    private String errorMessage;

    private LocalDateTime receivedAt;
}
