package com.usn.labhub.user.service.iot;

import com.usn.labhub.user.domain.vo.iot.IotLatestMetricsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class IotTelemetrySseService {

    private static final long RECONNECT_TIME_MS = 3_000L;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final AtomicReference<String> lastBroadcastStatus = new AtomicReference<>();

    public SseEmitter subscribe(IotLatestMetricsVO initialSnapshot) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        lastBroadcastStatus.set(initialSnapshot.getStatus());
        send(emitter, "telemetry", initialSnapshot);
        return emitter;
    }

    public void publishTelemetry(IotLatestMetricsVO snapshot) {
        lastBroadcastStatus.set(snapshot.getStatus());
        broadcast("telemetry", snapshot);
    }

    public boolean publishOfflineTransition(IotLatestMetricsVO snapshot) {
        if (!"OFFLINE".equals(snapshot.getStatus())) {
            return false;
        }
        String previous = lastBroadcastStatus.getAndSet(snapshot.getStatus());
        if (!"ONLINE".equals(previous)) {
            return false;
        }
        broadcast("telemetry", snapshot);
        return true;
    }

    public boolean hasSubscribers() {
        return !emitters.isEmpty();
    }

    @Scheduled(fixedDelayString = "${usnhub.iot.sse.heartbeat-delay-ms:10000}")
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        broadcast("heartbeat", Map.of("ts", LocalDateTime.now().format(TIME_FORMAT)));
    }

    void completeAll() {
        emitters.forEach(SseEmitter::complete);
        emitters.clear();
    }

    int subscriberCount() {
        return emitters.size();
    }

    private void broadcast(String eventName, Object data) {
        emitters.forEach(emitter -> send(emitter, eventName, data));
    }

    private void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .reconnectTime(RECONNECT_TIME_MS)
                    .data(data));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
            emitter.complete();
            log.debug("Removed closed PM-001 SSE connection: {}", e.getMessage());
        }
    }
}
