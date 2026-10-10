package com.usn.labhub.user.controller.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.service.iot.IotTelemetryService;
import com.usn.labhub.user.service.iot.InMemoryIotTelemetryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IotTelemetryControllerTest {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MockMvc mockMvc;
    private InMemoryIotTelemetryStore telemetryStore;

    @BeforeEach
    void setUp() {
        telemetryStore = new InMemoryIotTelemetryStore();
        IotTelemetryService telemetryService = new IotTelemetryService(new ObjectMapper(), telemetryStore);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new IotDeviceController(telemetryService), new IotTelemetryController(telemetryService))
                .build();
    }

    @Test
    void latestReturnsOfflineEmptyMetricsBeforeTelemetryArrives() throws Exception {
        mockMvc.perform(get("/api/iot/devices/1/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deviceCode").value("PM-001"))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"))
                .andExpect(jsonPath("$.data.reportTime").doesNotExist())
                .andExpect(jsonPath("$.data.metrics[0].metricKey").value("voltage"))
                .andExpect(jsonPath("$.data.metrics[0].value").doesNotExist())
                .andExpect(jsonPath("$.data.metrics[1].metricKey").value("current"))
                .andExpect(jsonPath("$.data.metrics[2].metricKey").value("power"));
    }

    @Test
    void mockReportDrivesLatestMetrics() throws Exception {
        String reportTime = LocalDateTime.now().format(FORMATTER);
        String payload = """
                {
                  "projectCode": "power-monitor",
                  "deviceCode": "PM-001",
                  "reportTime": "%s",
                  "metrics": {
                    "voltage": 221.6,
                    "current": 0.45,
                    "power": 99.7
                  },
                  "status": "online"
                }
                """.formatted(reportTime);

        mockMvc.perform(post("/api/iot/telemetry/mock-report")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deviceCode").value("PM-001"))
                .andExpect(jsonPath("$.data.reportTime").value(reportTime))
                .andExpect(jsonPath("$.data.metricCount").value(3))
                .andExpect(jsonPath("$.data.latestUpdated").value(true));

        mockMvc.perform(get("/api/iot/devices/1/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deviceCode").value("PM-001"))
                .andExpect(jsonPath("$.data.deviceName").value("实验室功耗监测仪 #1"))
                .andExpect(jsonPath("$.data.projectName").value("实验室功耗监测"))
                .andExpect(jsonPath("$.data.status").value("ONLINE"))
                .andExpect(jsonPath("$.data.reportTime").value(reportTime))
                .andExpect(jsonPath("$.data.metrics[0].metricKey").value("voltage"))
                .andExpect(jsonPath("$.data.metrics[0].value").value(221.6))
                .andExpect(jsonPath("$.data.metrics[1].metricKey").value("current"))
                .andExpect(jsonPath("$.data.metrics[1].value").value(0.45))
                .andExpect(jsonPath("$.data.metrics[2].metricKey").value("power"))
                .andExpect(jsonPath("$.data.metrics[2].value").value(99.7));

        org.junit.jupiter.api.Assertions.assertEquals(1, telemetryStore.rawRecords().size());
        org.junit.jupiter.api.Assertions.assertEquals(3, telemetryStore.metricRecords().size());
    }

    @Test
    void historyReturnsOrderedFilteredPowerPoints() throws Exception {
        report("2026-07-13 09:00:00", 80.1);
        report("2026-07-13 09:05:00", 92.5);
        report("2026-07-13 09:10:00", 101.2);

        mockMvc.perform(get("/api/iot/devices/1/metrics/history")
                        .param("metricKey", "power")
                        .param("startTime", "2026-07-13 09:05:00")
                        .param("endTime", "2026-07-13 09:10:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deviceId").value(1))
                .andExpect(jsonPath("$.data.metricKey").value("power"))
                .andExpect(jsonPath("$.data.unit").value("W"))
                .andExpect(jsonPath("$.data.points.length()").value(2))
                .andExpect(jsonPath("$.data.points[0].time").value("2026-07-13 09:05:00"))
                .andExpect(jsonPath("$.data.points[0].value").value(92.5))
                .andExpect(jsonPath("$.data.points[1].value").value(101.2));
    }

    @Test
    void historyRejectsUnsupportedMetricAndInvalidRange() throws Exception {
        mockMvc.perform(get("/api/iot/devices/1/metrics/history")
                        .param("metricKey", "temperature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("metricKey must be voltage, current, or power"));

        mockMvc.perform(get("/api/iot/devices/1/metrics/history")
                        .param("metricKey", "power")
                        .param("startTime", "2026-07-13 10:00:00")
                        .param("endTime", "2026-07-13 09:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("startTime must not be after endTime"));
    }

    private void report(String reportTime, double power) throws Exception {
        String payload = """
                {
                  "deviceCode": "PM-001",
                  "reportTime": "%s",
                  "metrics": {
                    "voltage": 220.0,
                    "current": 0.40,
                    "power": %s
                  }
                }
                """.formatted(reportTime, power);

        mockMvc.perform(post("/api/iot/telemetry/mock-report")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void oldTelemetryMakesLatestOffline() throws Exception {
        String reportTime = LocalDateTime.now().minusSeconds(30).format(FORMATTER);
        String payload = """
                {
                  "projectCode": "power-monitor",
                  "deviceCode": "PM-001",
                  "reportTime": "%s",
                  "metrics": {
                    "voltage": 220.0,
                    "current": 0.40,
                    "power": 88.0
                  }
                }
                """.formatted(reportTime);

        mockMvc.perform(post("/api/iot/telemetry/mock-report")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/iot/devices/1/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFLINE"))
                .andExpect(jsonPath("$.data.reportTime").value(reportTime));
    }

    @Test
    void latestRejectsOtherDeviceIdInWeekTwo() throws Exception {
        mockMvc.perform(get("/api/iot/devices/2/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("Week 2 latest API only supports device id 1 (PM-001)"));
    }
}
