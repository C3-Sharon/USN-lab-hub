package com.usn.labhub.user.controller.iot;

import com.usn.labhub.user.service.iot.IotTelemetryService;
import com.usn.labhub.user.service.iot.IotTelemetrySseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/iot/public/devices/1/telemetry")
@Tag(name = "IoT public telemetry stream", description = "Read-only PM-001 SSE stream")
public class IotTelemetryStreamController {

    private final IotTelemetryService telemetryService;
    private final IotTelemetrySseService sseService;

    public IotTelemetryStreamController(IotTelemetryService telemetryService,
                                        IotTelemetrySseService sseService) {
        this.telemetryService = telemetryService;
        this.sseService = sseService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to PM-001 real-time telemetry")
    public ResponseEntity<SseEmitter> stream() {
        SseEmitter emitter = sseService.subscribe(
                telemetryService.latest(IotTelemetryService.PM001_DEVICE_ID));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .contentType(new MediaType(MediaType.TEXT_EVENT_STREAM, StandardCharsets.UTF_8))
                .body(emitter);
    }
}
