package com.usn.labhub.user.domain.dto.iot;

import lombok.Data;

import java.util.Map;

@Data
public class IotCommandAckDTO {
    private String commandId;
    private String command;
    private String status;
    private Map<String, Object> result;
    private String ackedAt;
}
