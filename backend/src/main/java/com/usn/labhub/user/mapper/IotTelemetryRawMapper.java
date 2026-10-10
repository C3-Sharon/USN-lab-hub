package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.iot.IotTelemetryRawRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IotTelemetryRawMapper {

    @Insert("""
            INSERT INTO iot_telemetry_raw
                (device_code, topic, payload, parse_status, error_message, received_at)
            VALUES
                (#{deviceCode}, #{topic}, #{payload}, #{parseStatus}, #{errorMessage}, #{receivedAt})
            """)
    int insert(IotTelemetryRawRecord record);
}
