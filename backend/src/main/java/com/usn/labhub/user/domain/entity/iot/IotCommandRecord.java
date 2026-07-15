package com.usn.labhub.user.domain.entity.iot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IotCommandRecord {
    private Long id;
    private String commandId;
    private Long deviceId;
    private String deviceCode;
    private String command;
    private String paramsJson;
    private String status;
    private String message;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private LocalDateTime ackedAt;
}
