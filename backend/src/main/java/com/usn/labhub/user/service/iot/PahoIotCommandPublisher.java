package com.usn.labhub.user.service.iot;

import com.usn.labhub.user.config.IotMqttProperties;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class PahoIotCommandPublisher implements IotCommandPublisher {

    private final IotMqttProperties properties;

    public PahoIotCommandPublisher(IotMqttProperties properties) {
        this.properties = properties;
    }

    @Override
    public void publish(String topic, String payload) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("MQTT command publishing is disabled");
        }
        String clientId = properties.getClientId() + "-publisher-" + UUID.randomUUID();
        try (MqttClient client = new MqttClient(properties.getBrokerUrl(), clientId, new MemoryPersistence())) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(3);
            client.connect(options);
            client.publish(topic, new MqttMessage(payload.getBytes(StandardCharsets.UTF_8)));
            client.disconnect();
        } catch (MqttException e) {
            throw new IllegalStateException("MQTT command publish failed: " + e.getMessage(), e);
        }
    }
}
