package com.usn.labhub.user.service.iot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.domain.vo.iot.IotLatestMetricsVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = UsnHubApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:week7-sse;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=false",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/migration/V2__create_iot_telemetry_tables.sql,classpath:db/migration/V4__create_iot_alert_command_log_tables.sql,classpath:db/migration/V5__create_iot_asset_catalog.sql",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false",
                "usnhub.iot.sse.heartbeat-delay-ms=3600000",
                "usnhub.iot.sse.status-check-delay-ms=3600000"
        }
)
@AutoConfigureMockMvc
class IotWeek7SseIntegrationTest {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IotTelemetryService telemetryService;

    @Autowired
    private IotTelemetrySseService sseService;

    @AfterEach
    void disconnectStreams() {
        sseService.completeAll();
    }

    @Test
    void streamSendsInitialSnapshotThenMqttDrivenTelemetryWithLatestContract() throws Exception {
        MvcResult stream = mockMvc.perform(get("/api/iot/public/devices/1/telemetry/stream"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(header().string("Connection", "keep-alive"))
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andReturn();

        assertEquals(1, sseService.subscriberCount());
        String reportTime = LocalDateTime.now().format(TIME_FORMAT);
        telemetryService.ingestMqttPayload(IotTelemetryService.PM001_TELEMETRY_TOPIC,
                objectMapper.writeValueAsString(Map.of(
                        "projectCode", "power-monitor",
                        "deviceCode", "PM-001",
                        "reportTime", reportTime,
                        "metrics", Map.of("voltage", 220.3, "current", 0.42, "power", 92.5),
                        "status", "online"
                )));

        sseService.completeAll();
        MvcResult completed = mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andReturn();
        String body = completed.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertEquals(2, count(body, "event:telemetry"));
        JsonNode pushed = lastData(body);
        assertEquals(Set.of("deviceId", "deviceCode", "deviceName", "projectName", "status", "reportTime", "metrics"),
                fieldNames(pushed));
        assertEquals("PM-001", pushed.get("deviceCode").asText());
        assertEquals("ONLINE", pushed.get("status").asText());
        assertEquals(reportTime, pushed.get("reportTime").asText());
        assertEquals(List.of("voltage", "current", "power"),
                pushed.get("metrics").findValuesAsText("metricKey"));
        assertFalse(body.contains("payload"));
        assertFalse(body.contains("broker"));
        assertFalse(body.contains("operatorName"));
    }

    @Test
    void heartbeatDoesNotChangeStatusAndOfflineTransitionIsBroadcastOnlyOnce() {
        IotLatestMetricsVO online = snapshot("ONLINE");
        IotLatestMetricsVO offline = snapshot("OFFLINE");
        sseService.subscribe(online);
        sseService.heartbeat();

        assertTrue(sseService.publishOfflineTransition(offline));
        assertFalse(sseService.publishOfflineTransition(offline));
    }

    private IotLatestMetricsVO snapshot(String status) {
        IotLatestMetricsVO snapshot = new IotLatestMetricsVO();
        snapshot.setDeviceId(1L);
        snapshot.setDeviceCode("PM-001");
        snapshot.setDeviceName("PM-001");
        snapshot.setProjectName("power-monitor");
        snapshot.setStatus(status);
        snapshot.setReportTime(LocalDateTime.now().format(TIME_FORMAT));
        snapshot.setMetrics(List.of(
                new IotLatestMetricsVO.MetricValueVO("voltage", "voltage", new BigDecimal("220.3"), "V"),
                new IotLatestMetricsVO.MetricValueVO("current", "current", new BigDecimal("0.42"), "A"),
                new IotLatestMetricsVO.MetricValueVO("power", "power", new BigDecimal("92.5"), "W")
        ));
        return snapshot;
    }

    private JsonNode lastData(String body) throws Exception {
        String json = body.lines()
                .filter(line -> line.startsWith("data:"))
                .reduce((first, second) -> second)
                .orElseThrow()
                .substring("data:".length());
        return objectMapper.readTree(json);
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private int count(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
