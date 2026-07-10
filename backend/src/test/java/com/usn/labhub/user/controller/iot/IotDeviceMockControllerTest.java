package com.usn.labhub.user.controller.iot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IotDeviceMockControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new IotDeviceMockController()).build();
    }

    @Test
    void latestReturnsPm001MockMetrics() throws Exception {
        mockMvc.perform(get("/api/iot/devices/1/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("操作成功"))
                .andExpect(jsonPath("$.data.deviceId").value(1))
                .andExpect(jsonPath("$.data.deviceCode").value("PM-001"))
                .andExpect(jsonPath("$.data.deviceName").value("实验室功耗监测仪 #1"))
                .andExpect(jsonPath("$.data.projectName").value("实验室功耗监测"))
                .andExpect(jsonPath("$.data.status").value("ONLINE"))
                .andExpect(jsonPath("$.data.reportTime").value("2026-07-09 20:00:00"))
                .andExpect(jsonPath("$.data.metrics[0].metricKey").value("voltage"))
                .andExpect(jsonPath("$.data.metrics[0].metricName").value("电压"))
                .andExpect(jsonPath("$.data.metrics[0].unit").value("V"))
                .andExpect(jsonPath("$.data.metrics[1].metricKey").value("current"))
                .andExpect(jsonPath("$.data.metrics[1].metricName").value("电流"))
                .andExpect(jsonPath("$.data.metrics[1].unit").value("A"))
                .andExpect(jsonPath("$.data.metrics[2].metricKey").value("power"))
                .andExpect(jsonPath("$.data.metrics[2].metricName").value("功率"))
                .andExpect(jsonPath("$.data.metrics[2].unit").value("W"));
    }

    @Test
    void latestRejectsOtherDeviceIdInWeekOneMock() throws Exception {
        mockMvc.perform(get("/api/iot/devices/2/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("Week 1 mock only supports device id 1 (PM-001)"));
    }
}
