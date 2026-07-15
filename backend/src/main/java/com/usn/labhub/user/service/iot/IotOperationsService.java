package com.usn.labhub.user.service.iot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.config.IotMqttProperties;
import com.usn.labhub.user.domain.dto.iot.IotCommandAckDTO;
import com.usn.labhub.user.domain.dto.iot.IotCommandRequestDTO;
import com.usn.labhub.user.domain.entity.iot.IotAlertRecord;
import com.usn.labhub.user.domain.entity.iot.IotCommandRecord;
import com.usn.labhub.user.domain.entity.iot.IotOperationLogRecord;
import com.usn.labhub.user.domain.entity.iot.IotRecommendationRecord;
import com.usn.labhub.user.domain.vo.iot.IotAlertVO;
import com.usn.labhub.user.domain.vo.iot.IotCommandVO;
import com.usn.labhub.user.domain.vo.iot.IotOperationLogVO;
import com.usn.labhub.user.domain.vo.iot.IotPageVO;
import com.usn.labhub.user.domain.vo.iot.IotRecommendationVO;
import com.usn.labhub.user.mapper.IotOperationsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class IotOperationsService {

    private static final BigDecimal POWER_THRESHOLD = new BigDecimal("100");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ALERT_STATUSES = Set.of("OPEN", "HANDLED", "IGNORED");
    private static final Set<String> RECOMMENDATION_STATUSES = Set.of("PENDING", "CONFIRMED", "IGNORED");
    private static final Set<String> COMMAND_STATUSES = Set.of("PENDING", "SENT", "ACKED", "FAILED", "TIMEOUT");
    private static final Set<String> LOG_ACTIONS = Set.of("HANDLE_ALERT", "CONFIRM_RECOMMENDATION", "SEND_COMMAND", "RECEIVE_ACK");
    private static final Set<String> TARGET_TYPES = Set.of("ALERT", "RECOMMENDATION", "COMMAND");

    private final IotOperationsMapper mapper;
    private final ObjectMapper objectMapper;
    private final IotCommandPublisher commandPublisher;
    private final IotMqttProperties mqttProperties;

    public IotOperationsService(IotOperationsMapper mapper,
                                ObjectMapper objectMapper,
                                IotCommandPublisher commandPublisher,
                                IotMqttProperties mqttProperties) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.commandPublisher = commandPublisher;
        this.mqttProperties = mqttProperties;
    }

    @Transactional
    public void evaluatePower(BigDecimal power, LocalDateTime triggeredAt) {
        if (power == null || power.compareTo(POWER_THRESHOLD) <= 0) {
            return;
        }
        String value = decimalText(power);
        String message = "功率超过 100W，当前值为 " + value + "W";
        IotAlertRecord open = mapper.selectOpenAlert(
                IotTelemetryService.PM001_DEVICE_ID, "power", "WARNING"
        );
        if (open != null) {
            mapper.updateOpenAlert(open.getId(), power, message, triggeredAt);
            return;
        }

        IotAlertRecord alert = new IotAlertRecord(
                null,
                IotTelemetryService.PM001_DEVICE_ID,
                IotTelemetryService.PM001_DEVICE_CODE,
                "power",
                "WARNING",
                message,
                power,
                POWER_THRESHOLD,
                "OPEN",
                triggeredAt,
                triggeredAt,
                null,
                null
        );
        mapper.insertAlert(alert);
        IotRecommendationRecord recommendation = new IotRecommendationRecord(
                null,
                IotTelemetryService.PM001_DEVICE_ID,
                IotTelemetryService.PM001_DEVICE_CODE,
                alert.getId(),
                "RULE",
                "缩短采样间隔",
                "当前功率 " + value + "W 超过 100W，建议将采样间隔调整为 5 秒，以便更密集地观察负载变化。",
                "PENDING",
                triggeredAt
        );
        mapper.insertRecommendation(recommendation);
    }

    public IotPageVO<IotAlertVO> listAlerts(Long deviceId, String status, String metricKey, int page, int size) {
        ensureDevice(deviceId);
        validateOptional(status, ALERT_STATUSES, "非法的告警状态");
        PageSlice slice = pageSlice(page, size);
        return new IotPageVO<>(
                mapper.countAlerts(deviceId, blankToNull(status), blankToNull(metricKey)),
                mapper.selectAlerts(deviceId, blankToNull(status), blankToNull(metricKey), slice.offset(), slice.limit())
                        .stream().map(this::toAlertVO).toList()
        );
    }

    @Transactional
    public IotAlertVO handleAlert(Long alertId, String status) {
        if (!Set.of("HANDLED", "IGNORED").contains(status)) {
            throw new IotApiException(400, "非法的告警状态");
        }
        IotAlertRecord alert = mapper.selectAlert(alertId);
        if (alert == null) {
            throw new IotApiException(404, "告警不存在");
        }
        if (!"OPEN".equals(alert.getStatus())) {
            throw new IotApiException(400, "只能处理 OPEN 状态的告警");
        }
        LocalDateTime now = LocalDateTime.now();
        long operatorId = operatorId();
        if (mapper.handleAlert(alertId, status, operatorId, now) != 1) {
            throw new IotApiException(409, "告警状态已发生变化，请刷新后重试");
        }
        saveLog(alert.getDeviceId(), operatorId, operatorName(operatorId), "HANDLE_ALERT", "ALERT",
                String.valueOf(alertId), "告警已更新为 " + status, now);
        return toAlertVO(mapper.selectAlert(alertId));
    }

    public IotPageVO<IotRecommendationVO> listRecommendations(Long deviceId, String status, int page, int size) {
        ensureDevice(deviceId);
        validateOptional(status, RECOMMENDATION_STATUSES, "非法的建议状态");
        PageSlice slice = pageSlice(page, size);
        return new IotPageVO<>(
                mapper.countRecommendations(deviceId, blankToNull(status)),
                mapper.selectRecommendations(deviceId, blankToNull(status), slice.offset(), slice.limit())
                        .stream().map(this::toRecommendationVO).toList()
        );
    }

    @Transactional
    public IotRecommendationVO updateRecommendation(Long recommendationId, String targetStatus) {
        IotRecommendationRecord recommendation = mapper.selectRecommendation(recommendationId);
        if (recommendation == null) {
            throw new IotApiException(404, "建议不存在");
        }
        if (!"PENDING".equals(recommendation.getStatus())) {
            throw new IotApiException(400, "只能操作 PENDING 状态的建议");
        }
        if (mapper.updateRecommendationStatus(recommendationId, targetStatus) != 1) {
            throw new IotApiException(409, "建议状态已发生变化，请刷新后重试");
        }
        if ("CONFIRMED".equals(targetStatus)) {
            long operatorId = operatorId();
            saveLog(recommendation.getDeviceId(), operatorId, operatorName(operatorId),
                    "CONFIRM_RECOMMENDATION", "RECOMMENDATION", String.valueOf(recommendationId),
                    "确认建议：" + recommendation.getTitle(), LocalDateTime.now());
        }
        return toRecommendationVO(mapper.selectRecommendation(recommendationId));
    }

    @Transactional(noRollbackFor = IotApiException.class)
    public IotCommandVO sendCommand(Long deviceId, IotCommandRequestDTO request) {
        ensureDevice(deviceId);
        validateCommand(request);
        LocalDateTime createdAt = LocalDateTime.now();
        String commandId = "cmd-" + createdAt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().substring(0, 4);
        String paramsJson = writeJson(Map.of("intervalSeconds", 5));
        IotCommandRecord command = new IotCommandRecord(
                null, commandId, deviceId, IotTelemetryService.PM001_DEVICE_CODE,
                "SET_SAMPLE_INTERVAL", paramsJson, "PENDING", null,
                operatorId(), createdAt, null, null
        );
        mapper.insertCommand(command);
        IotCommandVO pendingResponse = toCommandVO(command);

        LocalDateTime sentAt = LocalDateTime.now();
        String payload = writeJson(Map.of(
                "commandId", commandId,
                "command", "SET_SAMPLE_INTERVAL",
                "params", Map.of("intervalSeconds", 5),
                "sentAt", format(sentAt)
        ));
        try {
            commandPublisher.publish(mqttProperties.getCommandTopic(), payload);
            mapper.markCommandSent(commandId, sentAt);
            long operatorId = command.getCreatedBy() == null ? 0L : command.getCreatedBy();
            saveLog(deviceId, operatorId, operatorName(operatorId), "SEND_COMMAND", "COMMAND", commandId,
                    "下发 SET_SAMPLE_INTERVAL，intervalSeconds=5", sentAt);
        } catch (RuntimeException e) {
            mapper.markCommandPublishFailed(commandId, abbreviate(e.getMessage(), 500));
            throw new IotApiException(500, "指令发布失败：" + e.getMessage());
        }
        return pendingResponse;
    }

    public IotCommandVO getCommand(String commandId) {
        IotCommandRecord command = mapper.selectCommand(commandId);
        if (command == null) {
            throw new IotApiException(404, "指令不存在");
        }
        return toCommandVO(command);
    }

    public IotPageVO<IotCommandVO> listCommands(Long deviceId, String status, int page, int size) {
        ensureDevice(deviceId);
        validateOptional(status, COMMAND_STATUSES, "非法的指令状态");
        PageSlice slice = pageSlice(page, size);
        return new IotPageVO<>(
                mapper.countCommands(deviceId, blankToNull(status)),
                mapper.selectCommands(deviceId, blankToNull(status), slice.offset(), slice.limit())
                        .stream().map(this::toCommandVO).toList()
        );
    }

    @Transactional
    public boolean ingestAck(String payload) {
        IotCommandAckDTO ack;
        try {
            ack = objectMapper.readValue(payload, IotCommandAckDTO.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("MQTT ACK payload parse failed: " + e.getMessage(), e);
        }
        if (!StringUtils.hasText(ack.getCommandId()) || !"SET_SAMPLE_INTERVAL".equals(ack.getCommand())
                || !Set.of("ACKED", "FAILED").contains(ack.getStatus())) {
            throw new IllegalArgumentException("Invalid PM-001 command ACK payload");
        }
        IotCommandRecord command = mapper.selectCommand(ack.getCommandId());
        if (command == null || !"SENT".equals(command.getStatus())) {
            return false;
        }
        LocalDateTime ackedAt = parseTimeOrNow(ack.getAckedAt());
        String message = "ACKED".equals(ack.getStatus()) ? null : "device returned FAILED";
        if (mapper.applyCommandAck(ack.getCommandId(), ack.getStatus(), message, ackedAt) != 1) {
            return false;
        }
        saveLog(command.getDeviceId(), 0L, "system", "RECEIVE_ACK", "COMMAND", ack.getCommandId(),
                "收到 SET_SAMPLE_INTERVAL " + ack.getStatus() + " 回执", ackedAt);
        return true;
    }

    public void expireTimedOutCommands() {
        expireTimedOutCommands(LocalDateTime.now());
    }

    public int expireTimedOutCommands(LocalDateTime now) {
        return mapper.markTimedOutCommands(now.minusSeconds(10));
    }

    public IotPageVO<IotOperationLogVO> listOperationLogs(Long deviceId, String action,
                                                           String targetType, int page, int size) {
        ensureDevice(deviceId);
        validateOptional(action, LOG_ACTIONS, "非法的日志行为");
        validateOptional(targetType, TARGET_TYPES, "非法的目标类型");
        PageSlice slice = pageSlice(page, size);
        return new IotPageVO<>(
                mapper.countOperationLogs(deviceId, blankToNull(action), blankToNull(targetType)),
                mapper.selectOperationLogs(deviceId, blankToNull(action), blankToNull(targetType),
                                slice.offset(), slice.limit())
                        .stream().map(this::toOperationLogVO).toList()
        );
    }

    private void validateCommand(IotCommandRequestDTO request) {
        if (request == null || !"SET_SAMPLE_INTERVAL".equals(request.getCommand())) {
            throw new IotApiException(400, "第四周仅支持 SET_SAMPLE_INTERVAL");
        }
        Object value = request.getParams() == null ? null : request.getParams().get("intervalSeconds");
        if (!(value instanceof Number number) || number.intValue() != 5) {
            throw new IotApiException(400, "intervalSeconds 必须为 5");
        }
    }

    private void ensureDevice(Long deviceId) {
        if (deviceId == null || deviceId != IotTelemetryService.PM001_DEVICE_ID) {
            throw new IotApiException(404, "设备不存在");
        }
    }

    private PageSlice pageSlice(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IotApiException(400, "page 必须大于 0，size 必须在 1 到 100 之间");
        }
        return new PageSlice((page - 1) * size, size);
    }

    private void validateOptional(String value, Set<String> allowed, String message) {
        if (StringUtils.hasText(value) && !allowed.contains(value)) {
            throw new IotApiException(400, message);
        }
    }

    private void saveLog(Long deviceId, Long operatorId, String operatorName, String action,
                         String targetType, String targetId, String summary, LocalDateTime createdAt) {
        mapper.insertOperationLog(new IotOperationLogRecord(
                null, deviceId, operatorId, operatorName, action, targetType, targetId, summary, createdAt
        ));
    }

    private IotAlertVO toAlertVO(IotAlertRecord record) {
        IotAlertVO vo = new IotAlertVO();
        vo.setId(record.getId());
        vo.setDeviceId(record.getDeviceId());
        vo.setDeviceCode(record.getDeviceCode());
        vo.setMetricKey(record.getMetricKey());
        vo.setLevel(record.getLevel());
        vo.setMessage(record.getMessage());
        vo.setTriggerValue(record.getTriggerValue());
        vo.setThresholdValue(record.getThresholdValue());
        vo.setStatus(record.getStatus());
        vo.setCreatedAt(format(record.getCreatedAt()));
        vo.setHandledBy(record.getHandledBy());
        vo.setHandledAt(format(record.getHandledAt()));
        return vo;
    }

    private IotRecommendationVO toRecommendationVO(IotRecommendationRecord record) {
        IotRecommendationVO vo = new IotRecommendationVO();
        vo.setId(record.getId());
        vo.setDeviceId(record.getDeviceId());
        vo.setDeviceCode(record.getDeviceCode());
        vo.setAlertId(record.getAlertId());
        vo.setTitle(record.getTitle());
        vo.setContent(record.getContent());
        vo.setStatus(record.getStatus());
        vo.setCreatedAt(format(record.getCreatedAt()));
        return vo;
    }

    private IotCommandVO toCommandVO(IotCommandRecord record) {
        IotCommandVO vo = new IotCommandVO();
        vo.setCommandId(record.getCommandId());
        vo.setDeviceId(record.getDeviceId());
        vo.setCommand(record.getCommand());
        try {
            vo.setParams(objectMapper.readValue(record.getParamsJson(), new TypeReference<>() { }));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored command params JSON is invalid", e);
        }
        vo.setStatus(record.getStatus());
        vo.setMessage(record.getMessage());
        vo.setCreatedAt(format(record.getCreatedAt()));
        vo.setSentAt(format(record.getSentAt()));
        vo.setAckedAt(format(record.getAckedAt()));
        return vo;
    }

    private IotOperationLogVO toOperationLogVO(IotOperationLogRecord record) {
        IotOperationLogVO vo = new IotOperationLogVO();
        vo.setId(record.getId());
        vo.setOperatorId(record.getOperatorId());
        vo.setOperatorName(record.getOperatorName());
        vo.setAction(record.getAction());
        vo.setTargetType(record.getTargetType());
        vo.setTargetId(record.getTargetId());
        vo.setSummary(record.getSummary());
        vo.setCreatedAt(format(record.getCreatedAt()));
        return vo;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    private LocalDateTime parseTimeOrNow(String value) {
        if (!StringUtils.hasText(value)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("ackedAt must use yyyy-MM-dd HH:mm:ss");
        }
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(TIME_FORMAT);
    }

    private long operatorId() {
        Long userId = UserContext.getUserId();
        return userId == null ? 0L : userId;
    }

    private String operatorName(long operatorId) {
        return operatorId == 0L ? "system" : "管理员";
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String decimalText(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record PageSlice(int offset, int limit) { }
}
