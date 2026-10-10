package com.usn.labhub.user.service.iot;

import com.usn.labhub.user.domain.entity.iot.IotMetricDataRecord;
import com.usn.labhub.user.domain.entity.iot.IotTelemetryRawRecord;
import com.usn.labhub.user.mapper.IotMetricDataMapper;
import com.usn.labhub.user.mapper.IotTelemetryRawMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MybatisIotTelemetryStore implements IotTelemetryStore {

    private final IotTelemetryRawMapper rawMapper;
    private final IotMetricDataMapper metricMapper;

    public MybatisIotTelemetryStore(IotTelemetryRawMapper rawMapper, IotMetricDataMapper metricMapper) {
        this.rawMapper = rawMapper;
        this.metricMapper = metricMapper;
    }

    @Override
    public void saveRaw(IotTelemetryRawRecord record) {
        rawMapper.insert(record);
    }

    @Override
    @Transactional
    public void saveTelemetry(IotTelemetryRawRecord rawRecord, List<IotMetricDataRecord> metricRecords) {
        rawMapper.insert(rawRecord);
        metricRecords.forEach(metricMapper::insert);
    }

    @Override
    public Optional<LocalDateTime> findLatestReportedAt(String deviceCode) {
        return Optional.ofNullable(metricMapper.selectLatestReportedAt(deviceCode));
    }

    @Override
    public List<IotMetricDataRecord> findAtReportTime(String deviceCode, LocalDateTime reportedAt) {
        return metricMapper.selectAtReportTime(deviceCode, reportedAt);
    }

    @Override
    public List<IotMetricDataRecord> findHistory(
            Long deviceId,
            String metricKey,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        return metricMapper.selectHistory(deviceId, metricKey, startTime, endTime);
    }
}
