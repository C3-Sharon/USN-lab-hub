package com.usn.labhub.user.controller.iot;

import com.usn.labhub.user.UsnHubApplication;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:week6-readiness;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class IotWeek6ReadinessIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private Flyway flyway;

    @Test
    void allMvpMigrationsAreAppliedInOrder() {
        List<String> versions = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(Object::toString)
                .toList();

        assertEquals(List.of("1", "2", "4", "5"), versions);
    }

    @Test
    @SuppressWarnings("unchecked")
    void readinessAndPublicApiAreAvailableWithoutLogin() {
        ResponseEntity<Map> health = rest.getForEntity("/actuator/health", Map.class);
        assertEquals(HttpStatus.OK, health.getStatusCode());
        assertNotNull(health.getBody());
        assertEquals("UP", health.getBody().get("status"));
        assertEquals(1, health.getBody().size());

        ResponseEntity<Map> response = rest.getForEntity(
                "/api/iot/public/projects/power-monitor", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().get("code"));

        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data);
        assertEquals("power-monitor", data.get("projectCode"));
        assertFalse(data.toString().contains("payload"));
        assertFalse(data.toString().contains("broker"));
        assertFalse(data.toString().contains("operatorName"));
    }
}
