package com.usn.labhub.user.domain.vo.iot;

import lombok.Data;

import java.util.Map;

@Data
public class IotCommandVO {
    private String commandId;
    private Long deviceId;
    private String command;
    private Map<String, Object> params;
    private String status;
    private String message;
    private String createdAt;
    private String sentAt;
    private String ackedAt;
}
