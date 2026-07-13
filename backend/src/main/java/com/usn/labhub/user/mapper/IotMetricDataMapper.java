package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.iot.IotMetricDataRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IotMetricDataMapper {

    @Insert("""
            INSERT INTO iot_metric_data
                (device_id, device_code, metric_key, metric_value, unit, reported_at, received_at)
            VALUES
                (#{deviceId}, #{deviceCode}, #{metricKey}, #{metricValue}, #{unit}, #{reportedAt}, #{receivedAt})
            """)
    int insert(IotMetricDataRecord record);

    @Select("""
            SELECT MAX(reported_at)
            FROM iot_metric_data
            WHERE device_code = #{deviceCode}
            """)
    LocalDateTime selectLatestReportedAt(@Param("deviceCode") String deviceCode);

    @Select("""
            SELECT device_id, device_code, metric_key, metric_value, unit, reported_at, received_at
            FROM iot_metric_data
            WHERE device_code = #{deviceCode}
              AND reported_at = #{reportedAt}
            ORDER BY id ASC
            """)
    List<IotMetricDataRecord> selectAtReportTime(
            @Param("deviceCode") String deviceCode,
            @Param("reportedAt") LocalDateTime reportedAt
    );

    @Select("""
            <script>
            SELECT device_id, device_code, metric_key, metric_value, unit, reported_at, received_at
            FROM iot_metric_data
            WHERE device_id = #{deviceId}
              AND metric_key = #{metricKey}
            <if test="startTime != null">
              AND reported_at &gt;= #{startTime}
            </if>
            <if test="endTime != null">
              AND reported_at &lt;= #{endTime}
            </if>
            ORDER BY reported_at ASC, id ASC
            </script>
            """)
    List<IotMetricDataRecord> selectHistory(
            @Param("deviceId") Long deviceId,
            @Param("metricKey") String metricKey,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
