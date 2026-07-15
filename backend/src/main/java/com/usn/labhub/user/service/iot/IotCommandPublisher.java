package com.usn.labhub.user.service.iot;

public interface IotCommandPublisher {
    void publish(String topic, String payload);
}
