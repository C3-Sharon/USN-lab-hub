package com.usn.labhub.user.service.iot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.domain.dto.iot.IotTelemetryReportDTO;
import com.usn.labhub.user.domain.entity.iot.IotMetricDataRecord;
import com.usn.labhub.user.domain.entity.iot.IotTelemetryRawRecord;
import com.usn.labhub.user.domain.vo.iot.IotLatestMetricsVO;
import com.usn.labhub.user.domain.vo.iot.IotMetricHistoryVO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final IotTelemetryStore telemetryStore;

    public IotTelemetryService(ObjectMapper objectMapper, IotTelemetryStore telemetryStore) {
        this.objectMapper = objectMapper;
        this.telemetryStore = telemetryStore;
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
            telemetryStore.saveRaw(new IotTelemetryRawRecord(
                    null, topic, payload, "FAILED", abbreviate(e.getMessage(), 500), LocalDateTime.now()
            ));
            throw new IllegalArgumentException("MQTT telemetry payload parse failed: " + e.getMessage(), e);
        }
    }

    public IotLatestMetricsVO latest(Long deviceId) {
        ensurePm001Device(deviceId);

        Optional<LocalDateTime> latestReportTime = telemetryStore.findLatestReportedAt(PM001_DEVICE_CODE);

        IotLatestMetricsVO latest = baseLatest();
        if (latestReportTime.isEmpty()) {
            latest.setStatus("OFFLINE");
            latest.setReportTime(null);
            latest.setMetrics(emptyMetrics());
            return latest;
        }

        LocalDateTime reportTime = latestReportTime.get();
        latest.setReportTime(formatTime(reportTime));
        latest.setStatus(isOnline(reportTime) ? "ONLINE" : "OFFLINE");
        latest.setMetrics(metricValuesAt(reportTime));
        return latest;
    }

    public IotMetricHistoryVO history(Long deviceId, String metricKey, String startTime, String endTime) {
        ensurePm001Device(deviceId);
        MetricMeta metricMeta = METRIC_META.get(metricKey);
        if (metricMeta == null) {
            throw new IllegalArgumentException("metricKey must be voltage, current, or power");
        }

        LocalDateTime parsedStart = parseOptionalQueryTime(startTime, "startTime");
        LocalDateTime parsedEnd = parseOptionalQueryTime(endTime, "endTime");
        if (parsedStart != null && parsedEnd != null && parsedStart.isAfter(parsedEnd)) {
            throw new IllegalArgumentException("startTime must not be after endTime");
        }

        List<IotMetricHistoryVO.MetricPointVO> points = telemetryStore
                .findHistory(deviceId, metricKey, parsedStart, parsedEnd)
                .stream()
                .map(record -> new IotMetricHistoryVO.MetricPointVO(
                        formatTime(record.getReportedAt()), record.getMetricValue()
                ))
                .toList();

        IotMetricHistoryVO history = new IotMetricHistoryVO();
        history.setDeviceId(deviceId);
        history.setMetricKey(metricKey);
        history.setUnit(metricMeta.unit());
        history.setPoints(points);
        return history;
    }

    private IotTelemetryIngestResultVO ingest(IotTelemetryReportDTO report, String topic, String rawPayload) {
        validatePm001Report(report);
        LocalDateTime receivedAt = LocalDateTime.now();
        LocalDateTime reportedAt = parseReportTime(report);
        IotTelemetryRawRecord rawRecord = new IotTelemetryRawRecord(
                report.getDeviceCode(), topic, rawPayload, "SUCCESS", null, receivedAt
        );

        List<IotMetricDataRecord> metricRecords = new ArrayList<>();
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
        }

        telemetryStore.saveTelemetry(rawRecord, metricRecords);
        int metricCount = metricRecords.size();

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
        Map<String, BigDecimal> storedValues = telemetryStore.findAtReportTime(PM001_DEVICE_CODE, reportTime)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        IotMetricDataRecord::getMetricKey,
                        IotMetricDataRecord::getMetricValue,
                        (first, second) -> second
                ));
        List<IotLatestMetricsVO.MetricValueVO> values = new ArrayList<>();
        for (Map.Entry<String, MetricMeta> entry : METRIC_META.entrySet()) {
            String metricKey = entry.getKey();
            MetricMeta meta = entry.getValue();
            BigDecimal value = storedValues.get(metricKey);
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

    private LocalDateTime parseOptionalQueryTime(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DISPLAY_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + " must use yyyy-MM-dd HH:mm:ss");
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record MetricMeta(String metricName, String unit) {
    }
}
