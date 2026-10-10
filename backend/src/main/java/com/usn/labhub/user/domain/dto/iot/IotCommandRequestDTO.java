package com.usn.labhub.user.domain.dto.iot;

import lombok.Data;

import java.util.Map;

@Data
public class IotCommandRequestDTO {
    private String command;
    private Map<String, Object> params;
}
