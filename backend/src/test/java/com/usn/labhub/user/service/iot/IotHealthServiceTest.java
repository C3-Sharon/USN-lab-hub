package com.usn.labhub.user.service.iot;

import com.usn.labhub.user.domain.vo.iot.IotHealthVO;
import com.usn.labhub.user.domain.vo.iot.IotLatestMetricsVO;
import com.usn.labhub.user.mapper.IotOperationsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IotHealthServiceTest {

    private IotTelemetryService telemetryService;
    private IotOperationsMapper operationsMapper;
    private IotHealthService healthService;

    @BeforeEach
    void setUp() {
        telemetryService = mock(IotTelemetryService.class);
        operationsMapper = mock(IotOperationsMapper.class);
        healthService = new IotHealthService(telemetryService, operationsMapper);
    }

    @Test
    void onlineWithoutOpenWarningIsHealthy() {
        stub("ONLINE", 0);

        IotHealthVO health = healthService.calculate(1L);

        assertHealth(health, 100, "HEALTHY");
        assertTrue(health.getReasons().isEmpty());
    }

    @Test
    void onlineWithOpenWarningNeedsAttention() {
        stub("ONLINE", 1);

        IotHealthVO health = healthService.calculate(1L);

        assertHealth(health, 70, "ATTENTION");
        assertEquals("存在未处理的功率告警", health.getReasons().get(0));
    }

    @Test
    void offlineWithoutOpenWarningNeedsAttention() {
        stub("OFFLINE", 0);

        IotHealthVO health = healthService.calculate(1L);

        assertHealth(health, 60, "ATTENTION");
        assertEquals("设备离线或超过 15 秒未上报", health.getReasons().get(0));
    }

    @Test
    void offlineWithOpenWarningIsRisk() {
        stub("OFFLINE", 2);

        IotHealthVO health = healthService.calculate(1L);

        assertHealth(health, 30, "RISK");
        assertEquals(2, health.getReasons().size());
    }

    private void stub(String status, long openWarnings) {
        IotLatestMetricsVO latest = new IotLatestMetricsVO();
        latest.setStatus(status);
        when(telemetryService.latest(1L)).thenReturn(latest);
        when(operationsMapper.countOpenWarningAlerts(1L)).thenReturn(openWarnings);
    }

    private void assertHealth(IotHealthVO health, int score, String level) {
        assertEquals(score, health.getScore());
        assertEquals(level, health.getLevel());
        assertTrue(health.getCalculatedAt().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }
}
