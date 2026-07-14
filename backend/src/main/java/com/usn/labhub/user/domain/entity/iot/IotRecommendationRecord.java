package com.usn.labhub.user.domain.entity.iot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IotRecommendationRecord {
    private Long id;
    private Long deviceId;
    private String deviceCode;
    private Long alertId;
    private String source;
    private String title;
    private String content;
    private String status;
    private LocalDateTime createdAt;
}
