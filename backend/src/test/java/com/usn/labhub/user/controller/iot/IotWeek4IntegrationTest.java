package com.usn.labhub.user.controller.iot;

import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.service.iot.IotCommandPublisher;
import com.usn.labhub.user.service.iot.IotOperationsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:week4-integration;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=false",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/migration/V2__create_iot_telemetry_tables.sql,classpath:db/migration/V4__create_iot_alert_command_log_tables.sql",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class IotWeek4IntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private IotOperationsService operationsService;

    @MockBean
    private IotCommandPublisher commandPublisher;

    @Test
    @SuppressWarnings("unchecked")
    void telemetryToAlertRecommendationCommandAckAndLogLoop() {
        ResponseEntity<Map> telemetry = post("/api/iot/telemetry/mock-report", Map.of(
                "projectCode", "power-monitor",
                "deviceCode", "PM-001",
                "reportTime", "2026-07-14 12:30:00",
                "metrics", Map.of("voltage", 220.0, "current", 0.5, "power", 110.5),
                "status", "online"
        ));
        assertEquals(200, telemetry.getBody().get("code"));

        Map<String, Object> alertsData = data(get("/api/iot/devices/1/alerts?status=OPEN"));
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) alertsData.get("list");
        assertEquals(1, alerts.size());
        assertEquals("WARNING", alerts.get(0).get("level"));

        Map<String, Object> recommendationsData = data(get("/api/iot/devices/1/recommendations?status=PENDING"));
        List<Map<String, Object>> recommendations =
                (List<Map<String, Object>>) recommendationsData.get("list");
        assertEquals(1, recommendations.size());
        Integer recommendationId = (Integer) recommendations.get(0).get("id");
        post("/api/iot/recommendations/" + recommendationId + "/confirm", Map.of());

        ResponseEntity<Map> commandResponse = post("/api/iot/devices/1/commands", Map.of(
                "command", "SET_SAMPLE_INTERVAL",
                "params", Map.of("intervalSeconds", 5)
        ));
        Map<String, Object> pendingCommand = data(commandResponse);
        assertEquals("PENDING", pendingCommand.get("status"));
        String commandId = (String) pendingCommand.get("commandId");
        assertNotNull(commandId);
        verify(commandPublisher).publish(anyString(), anyString());

        Map<String, Object> sentCommand = data(get("/api/iot/commands/" + commandId));
        assertEquals("SENT", sentCommand.get("status"));

        assertFalse(operationsService.ingestAck("""
                {"commandId":"unknown","command":"SET_SAMPLE_INTERVAL","status":"ACKED"}
                """));
        operationsService.ingestAck("""
                {
                  "commandId":"%s",
                  "command":"SET_SAMPLE_INTERVAL",
                  "status":"ACKED",
                  "result":{"intervalSeconds":5},
                  "ackedAt":"2026-07-14 12:30:01"
                }
                """.formatted(commandId));

        Map<String, Object> ackedCommand = data(get("/api/iot/commands/" + commandId));
        assertEquals("ACKED", ackedCommand.get("status"));

        Map<String, Object> logsData = data(get("/api/iot/devices/1/operation-logs"));
        assertEquals(3, ((Number) logsData.get("total")).intValue());
    }

    private ResponseEntity<Map> get(String path) {
        return rest.getForEntity(path, Map.class);
    }

    private ResponseEntity<Map> post(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(path, new HttpEntity<>(body, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().get("code"), String.valueOf(response.getBody().get("msg")));
        return (Map<String, Object>) response.getBody().get("data");
    }
}
