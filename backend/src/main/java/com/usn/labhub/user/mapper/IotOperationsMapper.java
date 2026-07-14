package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.iot.IotAlertRecord;
import com.usn.labhub.user.domain.entity.iot.IotCommandRecord;
import com.usn.labhub.user.domain.entity.iot.IotOperationLogRecord;
import com.usn.labhub.user.domain.entity.iot.IotRecommendationRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IotOperationsMapper {

    @Select("""
            SELECT * FROM iot_alert_record
            WHERE device_id=#{deviceId} AND metric_key=#{metricKey} AND level=#{level} AND status='OPEN'
            ORDER BY id DESC LIMIT 1
            """)
    IotAlertRecord selectOpenAlert(@Param("deviceId") Long deviceId,
                                   @Param("metricKey") String metricKey,
                                   @Param("level") String level);

    @Insert("""
            INSERT INTO iot_alert_record
              (device_id, device_code, metric_key, level, message, trigger_value, threshold_value,
               status, created_at, last_triggered_at)
            VALUES
              (#{deviceId}, #{deviceCode}, #{metricKey}, #{level}, #{message}, #{triggerValue},
               #{thresholdValue}, #{status}, #{createdAt}, #{lastTriggeredAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAlert(IotAlertRecord record);

    @Update("""
            UPDATE iot_alert_record
            SET trigger_value=#{triggerValue}, message=#{message}, last_triggered_at=#{triggeredAt}
            WHERE id=#{id} AND status='OPEN'
            """)
    int updateOpenAlert(@Param("id") Long id,
                        @Param("triggerValue") BigDecimal triggerValue,
                        @Param("message") String message,
                        @Param("triggeredAt") LocalDateTime triggeredAt);

    @Select("SELECT * FROM iot_alert_record WHERE id=#{id}")
    IotAlertRecord selectAlert(@Param("id") Long id);

    @Select("""
            <script>
            SELECT * FROM iot_alert_record WHERE device_id=#{deviceId}
            <if test="status != null">AND status=#{status}</if>
            <if test="metricKey != null">AND metric_key=#{metricKey}</if>
            ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<IotAlertRecord> selectAlerts(@Param("deviceId") Long deviceId,
                                      @Param("status") String status,
                                      @Param("metricKey") String metricKey,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM iot_alert_record WHERE device_id=#{deviceId}
            <if test="status != null">AND status=#{status}</if>
            <if test="metricKey != null">AND metric_key=#{metricKey}</if>
            </script>
            """)
    long countAlerts(@Param("deviceId") Long deviceId,
                     @Param("status") String status,
                     @Param("metricKey") String metricKey);

    @Update("""
            UPDATE iot_alert_record SET status=#{status}, handled_by=#{handledBy}, handled_at=#{handledAt}
            WHERE id=#{id} AND status='OPEN'
            """)
    int handleAlert(@Param("id") Long id,
                    @Param("status") String status,
                    @Param("handledBy") Long handledBy,
                    @Param("handledAt") LocalDateTime handledAt);

    @Insert("""
            INSERT INTO iot_recommendation
              (device_id, device_code, alert_id, source, title, content, status, created_at)
            VALUES
              (#{deviceId}, #{deviceCode}, #{alertId}, #{source}, #{title}, #{content}, #{status}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRecommendation(IotRecommendationRecord record);

    @Select("SELECT * FROM iot_recommendation WHERE id=#{id}")
    IotRecommendationRecord selectRecommendation(@Param("id") Long id);

    @Select("""
            <script>
            SELECT * FROM iot_recommendation WHERE device_id=#{deviceId}
            <if test="status != null">AND status=#{status}</if>
            ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<IotRecommendationRecord> selectRecommendations(@Param("deviceId") Long deviceId,
                                                         @Param("status") String status,
                                                         @Param("offset") int offset,
                                                         @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM iot_recommendation WHERE device_id=#{deviceId}
            <if test="status != null">AND status=#{status}</if>
            </script>
            """)
    long countRecommendations(@Param("deviceId") Long deviceId, @Param("status") String status);

    @Update("UPDATE iot_recommendation SET status=#{status} WHERE id=#{id} AND status='PENDING'")
    int updateRecommendationStatus(@Param("id") Long id, @Param("status") String status);

    @Insert("""
            INSERT INTO iot_command_record
              (command_id, device_id, device_code, command, params_json, status, message,
               created_by, created_at, sent_at, acked_at)
            VALUES
              (#{commandId}, #{deviceId}, #{deviceCode}, #{command}, #{paramsJson}, #{status}, #{message},
               #{createdBy}, #{createdAt}, #{sentAt}, #{ackedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCommand(IotCommandRecord record);

    @Select("SELECT * FROM iot_command_record WHERE command_id=#{commandId}")
    IotCommandRecord selectCommand(@Param("commandId") String commandId);

    @Select("""
            <script>
            SELECT * FROM iot_command_record WHERE device_id=#{deviceId}
            <if test="status != null">AND status=#{status}</if>
            ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<IotCommandRecord> selectCommands(@Param("deviceId") Long deviceId,
                                           @Param("status") String status,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM iot_command_record WHERE device_id=#{deviceId}
            <if test="status != null">AND status=#{status}</if>
            </script>
            """)
    long countCommands(@Param("deviceId") Long deviceId, @Param("status") String status);

    @Update("UPDATE iot_command_record SET status='SENT', sent_at=#{sentAt} WHERE command_id=#{commandId} AND status='PENDING'")
    int markCommandSent(@Param("commandId") String commandId, @Param("sentAt") LocalDateTime sentAt);

    @Update("UPDATE iot_command_record SET status='FAILED', message=#{message} WHERE command_id=#{commandId} AND status IN ('PENDING','SENT')")
    int markCommandPublishFailed(@Param("commandId") String commandId, @Param("message") String message);

    @Update("""
            UPDATE iot_command_record SET status=#{status}, message=#{message}, acked_at=#{ackedAt}
            WHERE command_id=#{commandId} AND status='SENT'
            """)
    int applyCommandAck(@Param("commandId") String commandId,
                        @Param("status") String status,
                        @Param("message") String message,
                        @Param("ackedAt") LocalDateTime ackedAt);

    @Update("""
            UPDATE iot_command_record SET status='TIMEOUT', message='ACK timeout after 10 seconds'
            WHERE status='SENT' AND sent_at <= #{cutoff}
            """)
    int markTimedOutCommands(@Param("cutoff") LocalDateTime cutoff);

    @Insert("""
            INSERT INTO iot_operation_log
              (device_id, operator_id, operator_name, action, target_type, target_id, summary, created_at)
            VALUES
              (#{deviceId}, #{operatorId}, #{operatorName}, #{action}, #{targetType}, #{targetId}, #{summary}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertOperationLog(IotOperationLogRecord record);

    @Select("""
            <script>
            SELECT * FROM iot_operation_log WHERE device_id=#{deviceId}
            <if test="action != null">AND action=#{action}</if>
            <if test="targetType != null">AND target_type=#{targetType}</if>
            ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<IotOperationLogRecord> selectOperationLogs(@Param("deviceId") Long deviceId,
                                                     @Param("action") String action,
                                                     @Param("targetType") String targetType,
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM iot_operation_log WHERE device_id=#{deviceId}
            <if test="action != null">AND action=#{action}</if>
            <if test="targetType != null">AND target_type=#{targetType}</if>
            </script>
            """)
    long countOperationLogs(@Param("deviceId") Long deviceId,
                            @Param("action") String action,
                            @Param("targetType") String targetType);
}
