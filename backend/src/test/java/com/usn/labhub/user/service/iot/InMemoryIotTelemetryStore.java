package com.usn.labhub.user.service.iot;

import com.usn.labhub.user.domain.entity.iot.IotMetricDataRecord;
import com.usn.labhub.user.domain.entity.iot.IotTelemetryRawRecord;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class InMemoryIotTelemetryStore implements IotTelemetryStore {

    private final List<IotTelemetryRawRecord> rawRecords = new ArrayList<>();
    private final List<IotMetricDataRecord> metricRecords = new ArrayList<>();

    @Override
    public void saveRaw(IotTelemetryRawRecord record) {
        rawRecords.add(record);
    }

    @Override
    public void saveTelemetry(IotTelemetryRawRecord rawRecord, List<IotMetricDataRecord> records) {
        rawRecords.add(rawRecord);
        metricRecords.addAll(records);
    }

    @Override
    public Optional<LocalDateTime> findLatestReportedAt(String deviceCode) {
        return metricRecords.stream()
                .filter(record -> deviceCode.equals(record.getDeviceCode()))
                .map(IotMetricDataRecord::getReportedAt)
                .max(Comparator.naturalOrder());
    }

    @Override
    public List<IotMetricDataRecord> findAtReportTime(String deviceCode, LocalDateTime reportedAt) {
        return metricRecords.stream()
                .filter(record -> deviceCode.equals(record.getDeviceCode()))
                .filter(record -> reportedAt.equals(record.getReportedAt()))
                .toList();
    }

    @Override
    public List<IotMetricDataRecord> findHistory(
            Long deviceId,
            String metricKey,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        return metricRecords.stream()
                .filter(record -> deviceId.equals(record.getDeviceId()))
                .filter(record -> metricKey.equals(record.getMetricKey()))
                .filter(record -> startTime == null || !record.getReportedAt().isBefore(startTime))
                .filter(record -> endTime == null || !record.getReportedAt().isAfter(endTime))
                .sorted(Comparator.comparing(IotMetricDataRecord::getReportedAt))
                .toList();
    }

    public List<IotTelemetryRawRecord> rawRecords() {
        return List.copyOf(rawRecords);
    }

    public List<IotMetricDataRecord> metricRecords() {
        return List.copyOf(metricRecords);
    }
}
