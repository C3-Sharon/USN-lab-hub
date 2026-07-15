package com.usn.labhub.user.service.iot;

import com.usn.labhub.user.domain.vo.iot.IotHealthVO;
import com.usn.labhub.user.domain.vo.iot.IotLatestMetricsVO;
import com.usn.labhub.user.mapper.IotOperationsMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class IotHealthService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int OFFLINE_DEDUCTION = 40;
    private static final int OPEN_WARNING_DEDUCTION = 30;

    private final IotTelemetryService telemetryService;
    private final IotOperationsMapper operationsMapper;

    public IotHealthService(IotTelemetryService telemetryService, IotOperationsMapper operationsMapper) {
        this.telemetryService = telemetryService;
        this.operationsMapper = operationsMapper;
    }

    public IotHealthVO calculate(Long deviceId) {
        IotLatestMetricsVO latest = telemetryService.latest(deviceId);
        return calculate(deviceId, latest.getStatus());
    }

    IotHealthVO calculate(Long deviceId, String deviceStatus) {
        if (deviceId == null || deviceId != IotTelemetryService.PM001_DEVICE_ID) {
            throw new IotApiException(404, "设备不存在");
        }

        int score = 100;
        List<String> reasons = new ArrayList<>();
        if (!"ONLINE".equals(deviceStatus)) {
            score -= OFFLINE_DEDUCTION;
            reasons.add("设备离线或超过 15 秒未上报");
        }
        if (operationsMapper.countOpenWarningAlerts(deviceId) > 0) {
            score -= OPEN_WARNING_DEDUCTION;
            reasons.add("存在未处理的功率告警");
        }
        score = Math.max(0, score);

        return new IotHealthVO(score, level(score), reasons, LocalDateTime.now().format(TIME_FORMAT));
    }

    private String level(int score) {
        if (score >= 80) {
            return "HEALTHY";
        }
        if (score >= 60) {
            return "ATTENTION";
        }
        return "RISK";
    }
}
