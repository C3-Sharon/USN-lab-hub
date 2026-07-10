package com.usn.labhub.user.service.iot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.domain.dto.iot.IotTelemetryReportDTO;
import com.usn.labhub.user.domain.entity.iot.IotMetricDataRecord;
import com.usn.labhub.user.domain.entity.iot.IotTelemetryRawRecord;
import com.usn.labhub.user.domain.vo.iot.IotLatestMetricsVO;
import com.usn.labhub.user.domain.vo.iot.IotTelemetryIngestResultVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class IotTelemetryService {

    public static final long PM001_DEVICE_ID = 1L;
    public static final String PM001_DEVICE_CODE = "PM-001";
    public static final String PM001_DEVICE_NAME = "实验室功耗监测仪 #1";
    public static final String PM001_PROJECT_NAME = "实验室功耗监测";
    public static final String PM001_TELEMETRY_TOPIC = "iot/power-monitor/PM-001/telemetry";

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, MetricMeta> METRIC_META = new LinkedHashMap<>();

    static {
        METRIC_META.put("voltage", new MetricMeta("电压", "V"));
        METRIC_META.put("current", new MetricMeta("电流", "A"));
        METRIC_META.put("power", new MetricMeta("功率", "W"));
    }

    private final ObjectMapper objectMapper;
    private final List<IotTelemetryRawRecord> rawRecords = new CopyOnWriteArrayList<>();
    private final List<IotMetricDataRecord> metricRecords = new CopyOnWriteArrayList<>();

    public IotTelemetryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IotTelemetryIngestResultVO ingestHttpReport(IotTelemetryReportDTO report) {
        try {
            return ingest(report, "HTTP:/api/iot/telemetry/mock-report", objectMapper.writeValueAsString(report));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Telemetry payload JSON serialization failed", e);
        }
    }

    public IotTelemetryIngestResultVO ingestMqttPayload(String topic, String payload) {
        try {
            IotTelemetryReportDTO report = objectMapper.readValue(payload, IotTelemetryReportDTO.class);
            return ingest(report, topic, payload);
        } catch (Exception e) {
            rawRecords.add(new IotTelemetryRawRecord(null, topic, payload, "FAILED", e.getMessage(), LocalDateTime.now()));
            throw new IllegalArgumentException("MQTT telemetry payload parse failed: " + e.getMessage(), e);
        }
    }

    public IotLatestMetricsVO latest(Long deviceId) {
        ensurePm001Device(deviceId);

        Optional<IotMetricDataRecord> latestMetric = metricRecords.stream()
                .filter(record -> PM001_DEVICE_CODE.equals(record.getDeviceCode()))
                .max(Comparator.comparing(IotMetricDataRecord::getReportedAt));

        IotLatestMetricsVO latest = baseLatest();
        if (latestMetric.isEmpty()) {
            latest.setStatus("OFFLINE");
            latest.setReportTime(null);
            latest.setMetrics(emptyMetrics());
            return latest;
        }

        LocalDateTime reportTime = latestMetric.get().getReportedAt();
        latest.setReportTime(formatTime(reportTime));
        latest.setStatus(isOnline(reportTime) ? "ONLINE" : "OFFLINE");
        latest.setMetrics(metricValuesAt(reportTime));
        return latest;
    }

    public List<IotTelemetryRawRecord> rawRecordsSnapshot() {
        return List.copyOf(rawRecords);
    }

    public List<IotMetricDataRecord> metricRecordsSnapshot() {
        return List.copyOf(metricRecords);
    }

    private IotTelemetryIngestResultVO ingest(IotTelemetryReportDTO report, String topic, String rawPayload) {
        validatePm001Report(report);
        LocalDateTime receivedAt = LocalDateTime.now();
        LocalDateTime reportedAt = parseReportTime(report);
        rawRecords.add(new IotTelemetryRawRecord(report.getDeviceCode(), topic, rawPayload, "SUCCESS", null, receivedAt));

        int metricCount = 0;
        for (String metricKey : METRIC_META.keySet()) {
            BigDecimal value = report.getMetrics().get(metricKey);
            if (value == null) {
                continue;
            }
            MetricMeta meta = METRIC_META.get(metricKey);
            metricRecords.add(new IotMetricDataRecord(
                    PM001_DEVICE_ID,
                    PM001_DEVICE_CODE,
                    metricKey,
                    value,
                    meta.unit(),
                    reportedAt,
                    receivedAt
            ));
            metricCount++;
        }

        return new IotTelemetryIngestResultVO(PM001_DEVICE_CODE, formatTime(reportedAt), metricCount, metricCount > 0);
    }

    private void validatePm001Report(IotTelemetryReportDTO report) {
        if (report == null) {
            throw new IllegalArgumentException("Telemetry report must not be null");
        }
        if (!PM001_DEVICE_CODE.equals(report.getDeviceCode())) {
            throw new IllegalArgumentException("Week 2 telemetry only supports PM-001");
        }
        if (report.getMetrics() == null || report.getMetrics().isEmpty()) {
            throw new IllegalArgumentException("Telemetry metrics must not be empty");
        }
    }

    private void ensurePm001Device(Long deviceId) {
        if (deviceId == null || deviceId != PM001_DEVICE_ID) {
            throw new IllegalArgumentException("Week 2 latest API only supports device id 1 (PM-001)");
        }
    }

    private LocalDateTime parseReportTime(IotTelemetryReportDTO report) {
        String value = StringUtils.hasText(report.getReportTime()) ? report.getReportTime() : report.getTimestamp();
        if (!StringUtils.hasText(value)) {
            return LocalDateTime.now();
        }

        try {
            return LocalDateTime.parse(value, DISPLAY_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // Try ISO offset format from pm001_simulator.py.
        }

        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(CHINA_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Try plain instant format next.
        }

        try {
            return Instant.parse(value).atZone(CHINA_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.now();
        }
    }

    private IotLatestMetricsVO baseLatest() {
        IotLatestMetricsVO latest = new IotLatestMetricsVO();
        latest.setDeviceId(PM001_DEVICE_ID);
        latest.setDeviceCode(PM001_DEVICE_CODE);
        latest.setDeviceName(PM001_DEVICE_NAME);
        latest.setProjectName(PM001_PROJECT_NAME);
        return latest;
    }

    private List<IotLatestMetricsVO.MetricValueVO> metricValuesAt(LocalDateTime reportTime) {
        List<IotLatestMetricsVO.MetricValueVO> values = new ArrayList<>();
        for (Map.Entry<String, MetricMeta> entry : METRIC_META.entrySet()) {
            String metricKey = entry.getKey();
            MetricMeta meta = entry.getValue();
            BigDecimal value = metricRecords.stream()
                    .filter(record -> PM001_DEVICE_CODE.equals(record.getDeviceCode()))
                    .filter(record -> metricKey.equals(record.getMetricKey()))
                    .filter(record -> reportTime.equals(record.getReportedAt()))
                    .reduce((first, second) -> second)
                    .map(IotMetricDataRecord::getMetricValue)
                    .orElse(null);
            values.add(new IotLatestMetricsVO.MetricValueVO(metricKey, meta.metricName(), value, meta.unit()));
        }
        return values;
    }

    private List<IotLatestMetricsVO.MetricValueVO> emptyMetrics() {
        return METRIC_META.entrySet().stream()
                .map(entry -> new IotLatestMetricsVO.MetricValueVO(
                        entry.getKey(),
                        entry.getValue().metricName(),
                        null,
                        entry.getValue().unit()
                ))
                .toList();
    }

    private boolean isOnline(LocalDateTime reportTime) {
        return Duration.between(reportTime, LocalDateTime.now()).abs().getSeconds() <= 15;
    }

    private String formatTime(LocalDateTime time) {
        return time.format(DISPLAY_TIME_FORMATTER);
    }

    private record MetricMeta(String metricName, String unit) {
    }
}
