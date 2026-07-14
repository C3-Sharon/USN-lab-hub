package com.usn.labhub.user.controller.iot;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.iot.IotAlertHandleDTO;
import com.usn.labhub.user.domain.dto.iot.IotCommandRequestDTO;
import com.usn.labhub.user.domain.vo.iot.IotAlertVO;
import com.usn.labhub.user.domain.vo.iot.IotCommandVO;
import com.usn.labhub.user.domain.vo.iot.IotOperationLogVO;
import com.usn.labhub.user.domain.vo.iot.IotPageVO;
import com.usn.labhub.user.domain.vo.iot.IotRecommendationVO;
import com.usn.labhub.user.service.iot.IotOperationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iot")
@Tag(name = "IoT 运维闭环", description = "PM-001 告警、建议、指令和操作日志")
public class IotOperationsController {

    private final IotOperationsService service;

    public IotOperationsController(IotOperationsService service) {
        this.service = service;
    }

    @GetMapping("/devices/{deviceId}/alerts")
    @Operation(summary = "查询设备告警")
    public Result<IotPageVO<IotAlertVO>> alerts(
            @PathVariable Long deviceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String metricKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.listAlerts(deviceId, status, metricKey, page, size));
    }

    @PostMapping("/alerts/{alertId}/handle")
    @Operation(summary = "处理或忽略告警")
    public Result<IotAlertVO> handleAlert(@PathVariable Long alertId, @RequestBody IotAlertHandleDTO request) {
        return Result.success(service.handleAlert(alertId, request == null ? null : request.getStatus()));
    }

    @GetMapping("/devices/{deviceId}/recommendations")
    @Operation(summary = "查询设备建议")
    public Result<IotPageVO<IotRecommendationVO>> recommendations(
            @PathVariable Long deviceId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.listRecommendations(deviceId, status, page, size));
    }

    @PostMapping("/recommendations/{recommendationId}/confirm")
    @Operation(summary = "确认建议")
    public Result<IotRecommendationVO> confirmRecommendation(@PathVariable Long recommendationId) {
        return Result.success(service.updateRecommendation(recommendationId, "CONFIRMED"));
    }

    @PostMapping("/recommendations/{recommendationId}/ignore")
    @Operation(summary = "忽略建议")
    public Result<IotRecommendationVO> ignoreRecommendation(@PathVariable Long recommendationId) {
        return Result.success(service.updateRecommendation(recommendationId, "IGNORED"));
    }

    @PostMapping("/devices/{deviceId}/commands")
    @Operation(summary = "向 PM-001 下发固定采样间隔指令")
    public Result<IotCommandVO> sendCommand(@PathVariable Long deviceId,
                                             @RequestBody IotCommandRequestDTO request) {
        return Result.success(service.sendCommand(deviceId, request));
    }

    @GetMapping("/commands/{commandId}")
    @Operation(summary = "查询指令状态")
    public Result<IotCommandVO> command(@PathVariable String commandId) {
        return Result.success(service.getCommand(commandId));
    }

    @GetMapping("/devices/{deviceId}/commands")
    @Operation(summary = "查询设备指令记录")
    public Result<IotPageVO<IotCommandVO>> commands(
            @PathVariable Long deviceId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.listCommands(deviceId, status, page, size));
    }

    @GetMapping("/devices/{deviceId}/operation-logs")
    @Operation(summary = "查询设备操作日志")
    public Result<IotPageVO<IotOperationLogVO>> operationLogs(
            @PathVariable Long deviceId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.listOperationLogs(deviceId, action, targetType, page, size));
    }
}
