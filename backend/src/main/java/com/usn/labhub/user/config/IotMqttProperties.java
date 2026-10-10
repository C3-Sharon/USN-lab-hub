package com.usn.labhub.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "usnhub.iot.mqtt")
public class IotMqttProperties {

    private boolean enabled = true;

    private String brokerUrl = "tcp://127.0.0.1:1883";

    private String clientId = "usn-lab-hub-backend";

    private String telemetryTopic = "iot/power-monitor/PM-001/telemetry";

    private String commandTopic = "iot/power-monitor/PM-001/command";

    private String ackTopic = "iot/power-monitor/PM-001/ack";
}
