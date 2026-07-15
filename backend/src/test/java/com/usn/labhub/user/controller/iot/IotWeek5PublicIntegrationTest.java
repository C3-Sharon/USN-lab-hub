package com.usn.labhub.user.controller.iot;

import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.service.iot.IotCommandPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:week5-public;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=false",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/migration/V2__create_iot_telemetry_tables.sql,classpath:db/migration/V4__create_iot_alert_command_log_tables.sql,classpath:db/migration/V5__create_iot_asset_catalog.sql",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class IotWeek5PublicIntegrationTest {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private TestRestTemplate rest;

    @MockBean
    private IotCommandPublisher commandPublisher;

    @Test
    @SuppressWarnings("unchecked")
    void publicProjectCoversEmptyHealthyWarningAndSamplingStatesWithoutLogin() {
        Map<String, Object> empty = data(get("/api/iot/public/projects/power-monitor"));
        Map<String, Object> emptyDevice = (Map<String, Object>) empty.get("device");
        assertEquals("OFFLINE", emptyDevice.get("status"));
        assertEquals(List.of(), emptyDevice.get("metrics"));
        assertEquals(60, ((Number) ((Map<String, Object>) emptyDevice.get("health")).get("score")).intValue());
        assertEquals(List.of(), empty.get("powerTrend"));

        report("92.5");
        Map<String, Object> healthy = data(get("/api/iot/public/projects/power-monitor"));
        Map<String, Object> healthyDevice = (Map<String, Object>) healthy.get("device");
        assertEquals("ONLINE", healthyDevice.get("status"));
        assertEquals(3, ((List<?>) healthyDevice.get("metrics")).size());
        assertEquals(100, ((Number) ((Map<String, Object>) healthyDevice.get("health")).get("score")).intValue());

        report("110.5");
        Map<String, Object> warning = data(get(
                "/api/iot/public/projects/power-monitor?trendMinutes=999&pointCount=1"));
        Map<String, Object> warningHealth = (Map<String, Object>)
                ((Map<String, Object>) warning.get("device")).get("health");
        assertEquals(70, ((Number) warningHealth.get("score")).intValue());
        assertEquals("ATTENTION", warningHealth.get("level"));
        assertEquals(List.of("存在未处理的功率告警"), warningHealth.get("reasons"));
        assertEquals(1, ((List<?>) warning.get("powerTrend")).size());

        String serialized = warning.toString();
        assertFalse(serialized.contains("operatorName"));
        assertFalse(serialized.contains("commandId"));
        assertFalse(serialized.contains("payload"));
        assertFalse(serialized.contains("broker"));
    }

    @Test
    void unknownProjectReturnsHttp404AndContractBody() {
        ResponseEntity<Map> response = get("/api/iot/public/projects/unknown");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().get("code"));
        assertEquals("项目不存在", response.getBody().get("msg"));
    }

    private void report(String power) {
        ResponseEntity<Map> response = post("/api/iot/telemetry/mock-report", Map.of(
                "projectCode", "power-monitor",
                "deviceCode", "PM-001",
                "reportTime", LocalDateTime.now().format(TIME_FORMAT),
                "metrics", Map.of("voltage", 220.0, "current", 0.5, "power", Double.parseDouble(power)),
                "status", "online"
        ));
        assertEquals(200, response.getBody().get("code"));
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
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().get("code"), String.valueOf(response.getBody().get("msg")));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data);
        assertTrue(data.containsKey("updatedAt"));
        return data;
    }
}
