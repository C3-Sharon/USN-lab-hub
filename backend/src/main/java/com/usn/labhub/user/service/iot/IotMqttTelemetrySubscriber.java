package com.usn.labhub.user.service.iot;

import com.usn.labhub.user.config.IotMqttProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class IotMqttTelemetrySubscriber {

    private final IotMqttProperties properties;
    private final IotTelemetryService telemetryService;
    private final IotOperationsService operationsService;
    private MqttClient client;

    public IotMqttTelemetrySubscriber(IotMqttProperties properties, IotTelemetryService telemetryService,
                                      IotOperationsService operationsService) {
        this.properties = properties;
        this.telemetryService = telemetryService;
        this.operationsService = operationsService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void subscribeTelemetry() {
        if (!properties.isEnabled()) {
            log.info("IoT MQTT subscriber is disabled.");
            return;
        }

        try {
            client = new MqttClient(properties.getBrokerUrl(), properties.getClientId(), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(3);
            options.setKeepAliveInterval(30);

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("IoT MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    try {
                        if (properties.getAckTopic().equals(topic)) {
                            boolean updated = operationsService.ingestAck(payload);
                            log.info("PM-001 command ACK received, stateUpdated={}", updated);
                        } else {
                            telemetryService.ingestMqttPayload(topic, payload);
                            log.info("PM-001 telemetry ingested from MQTT topic {}", topic);
                        }
                    } catch (IllegalArgumentException e) {
                        log.warn("PM-001 MQTT message ignored: {}", e.getMessage());
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Subscriber only; no publish delivery handling needed in week 2.
                }
            });

            client.connect(options);
            client.subscribe(new String[]{properties.getTelemetryTopic(), properties.getAckTopic()});
            log.info("IoT MQTT subscriber connected to {} and subscribed {}, {}",
                    properties.getBrokerUrl(), properties.getTelemetryTopic(), properties.getAckTopic());
        } catch (MqttException e) {
            log.warn("IoT MQTT subscriber not connected. Start local broker before backend for MQTT integration. reason={}", e.getMessage());
        }
    }

    @PreDestroy
    public void disconnect() {
        if (client == null || !client.isConnected()) {
            return;
        }
        try {
            client.disconnect();
            client.close();
        } catch (MqttException e) {
            log.warn("IoT MQTT subscriber disconnect failed: {}", e.getMessage());
        }
    }
}
