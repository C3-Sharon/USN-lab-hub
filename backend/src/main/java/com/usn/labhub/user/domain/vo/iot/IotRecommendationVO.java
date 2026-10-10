package com.usn.labhub.user.domain.vo.iot;

import lombok.Data;

@Data
public class IotRecommendationVO {
    private Long id;
    private Long deviceId;
    private String deviceCode;
    private Long alertId;
    private String title;
    private String content;
    private String status;
    private String createdAt;
}
