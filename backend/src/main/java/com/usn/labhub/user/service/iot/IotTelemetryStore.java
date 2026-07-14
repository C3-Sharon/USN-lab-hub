package com.usn.labhub.user.service.iot;

import com.usn.labhub.user.domain.entity.iot.IotMetricDataRecord;
import com.usn.labhub.user.domain.entity.iot.IotTelemetryRawRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IotTelemetryStore {

    void saveRaw(IotTelemetryRawRecord record);

    void saveTelemetry(IotTelemetryRawRecord rawRecord, List<IotMetricDataRecord> metricRecords);

    Optional<LocalDateTime> findLatestReportedAt(String deviceCode);

    List<IotMetricDataRecord> findAtReportTime(String deviceCode, LocalDateTime reportedAt);

    List<IotMetricDataRecord> findHistory(
            Long deviceId,
            String metricKey,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
}
