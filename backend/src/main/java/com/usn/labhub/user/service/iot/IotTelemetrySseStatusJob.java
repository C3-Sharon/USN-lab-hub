package com.usn.labhub.user.service.iot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IotTelemetrySseStatusJob {

    private final IotTelemetryService telemetryService;
    private final IotTelemetrySseService sseService;

    public IotTelemetrySseStatusJob(IotTelemetryService telemetryService, IotTelemetrySseService sseService) {
        this.telemetryService = telemetryService;
        this.sseService = sseService;
    }

    @Scheduled(fixedDelayString = "${usnhub.iot.sse.status-check-delay-ms:1000}")
    public void publishOfflineTransition() {
        if (!sseService.hasSubscribers()) {
            return;
        }
        try {
            sseService.publishOfflineTransition(telemetryService.latest(IotTelemetryService.PM001_DEVICE_ID));
        } catch (RuntimeException e) {
            log.warn("Failed to evaluate PM-001 SSE offline transition", e);
        }
    }
}
