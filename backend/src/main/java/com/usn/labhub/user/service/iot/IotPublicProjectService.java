package com.usn.labhub.user.service.iot;

import com.usn.labhub.user.domain.vo.iot.IotHealthVO;
import com.usn.labhub.user.domain.vo.iot.IotLatestMetricsVO;
import com.usn.labhub.user.domain.vo.iot.IotMetricHistoryVO;
import com.usn.labhub.user.domain.vo.iot.IotPublicProjectVO;
import com.usn.labhub.user.domain.entity.iot.IotDeviceRecord;
import com.usn.labhub.user.domain.entity.iot.LabProjectRecord;
import com.usn.labhub.user.mapper.IotAssetCatalogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class IotPublicProjectService {

    public static final String PROJECT_CODE = "power-monitor";
    private static final int DEFAULT_TREND_MINUTES = 30;
    private static final int DEFAULT_POINT_COUNT = 30;
    private static final int MAX_QUERY_VALUE = 60;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IotTelemetryService telemetryService;
    private final IotHealthService healthService;
    private final IotAssetCatalogMapper assetCatalogMapper;

    public IotPublicProjectService(IotTelemetryService telemetryService,
                                   IotHealthService healthService,
                                   IotAssetCatalogMapper assetCatalogMapper) {
        this.telemetryService = telemetryService;
        this.healthService = healthService;
        this.assetCatalogMapper = assetCatalogMapper;
    }

    public IotPublicProjectVO getProject(String projectCode, Integer trendMinutes, Integer pointCount) {
        LabProjectRecord projectRecord = assetCatalogMapper.selectPublicProject(projectCode);
        if (projectRecord == null) {
            throw new IotApiException(404, "项目不存在");
        }
        IotDeviceRecord deviceRecord = assetCatalogMapper.selectDevice(
                projectRecord.getId(), IotTelemetryService.PM001_DEVICE_CODE
        );
        if (deviceRecord == null) {
            throw new IotApiException(404, "设备不存在");
        }

        int normalizedMinutes = normalize(trendMinutes, DEFAULT_TREND_MINUTES);
        int normalizedPointCount = normalize(pointCount, DEFAULT_POINT_COUNT);
        LocalDateTime now = LocalDateTime.now();
        IotLatestMetricsVO latest = telemetryService.latest(IotTelemetryService.PM001_DEVICE_ID);
        IotHealthVO health = healthService.calculate(IotTelemetryService.PM001_DEVICE_ID, latest.getStatus());
        IotMetricHistoryVO history = telemetryService.history(
                IotTelemetryService.PM001_DEVICE_ID,
                "power",
                now.minusMinutes(normalizedMinutes).format(TIME_FORMAT),
                now.format(TIME_FORMAT)
        );

        IotPublicProjectVO.PublicDeviceVO device = new IotPublicProjectVO.PublicDeviceVO();
        device.setId(deviceRecord.getId());
        device.setDeviceCode(deviceRecord.getDeviceCode());
        device.setDeviceName(deviceRecord.getDeviceName());
        device.setStatus(latest.getStatus());
        device.setReportTime(latest.getReportTime());
        device.setMetrics(latest.getReportTime() == null ? List.of() : latest.getMetrics());
        device.setHealth(health);

        IotPublicProjectVO project = new IotPublicProjectVO();
        project.setProjectCode(projectRecord.getProjectCode());
        project.setProjectName(projectRecord.getProjectName());
        project.setDescription(projectRecord.getDescription());
        project.setStatus(projectRecord.getStatus());
        project.setDeviceCount(assetCatalogMapper.countDevices(projectRecord.getId()));
        project.setOnlineDeviceCount("ONLINE".equals(latest.getStatus()) ? 1 : 0);
        project.setDevice(device);
        project.setPowerTrend(sample(history.getPoints(), normalizedPointCount));
        project.setUpdatedAt(now.format(TIME_FORMAT));
        return project;
    }

    private int normalize(Integer value, int defaultValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return Math.min(value, MAX_QUERY_VALUE);
    }

    private List<IotMetricHistoryVO.MetricPointVO> sample(
            List<IotMetricHistoryVO.MetricPointVO> points, int pointCount) {
        if (points == null || points.size() <= pointCount) {
            return points == null ? List.of() : points;
        }
        if (pointCount == 1) {
            return List.of(points.get(points.size() - 1));
        }

        List<IotMetricHistoryVO.MetricPointVO> sampled = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            int index = (int) Math.round((double) i * (points.size() - 1) / (pointCount - 1));
            sampled.add(points.get(index));
        }
        return sampled;
    }
}
