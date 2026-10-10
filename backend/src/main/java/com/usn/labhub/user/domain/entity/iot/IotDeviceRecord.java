package com.usn.labhub.user.domain.entity.iot;

import lombok.Data;

@Data
public class IotDeviceRecord {
    private Long id;
    private Long projectId;
    private String deviceCode;
    private String deviceName;
}
