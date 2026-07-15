package com.usn.labhub.user.domain.entity.iot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IotOperationLogRecord {
    private Long id;
    private Long deviceId;
    private Long operatorId;
    private String operatorName;
    private String action;
    private String targetType;
    private String targetId;
    private String summary;
    private LocalDateTime createdAt;
}
