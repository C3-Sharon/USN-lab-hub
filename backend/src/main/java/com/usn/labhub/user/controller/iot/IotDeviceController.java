package com.usn.labhub.user.controller.iot;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.vo.iot.IotLatestMetricsVO;
import com.usn.labhub.user.domain.vo.iot.IotMetricHistoryVO;
import com.usn.labhub.user.service.iot.IotTelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/iot/devices")
@Tag(name = "IoT 设备接口", description = "PM-001 最新遥测数据接口")
public class IotDeviceController {

    private final IotTelemetryService telemetryService;

    public IotDeviceController(IotTelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @GetMapping("/{id}/latest")
    @Operation(summary = "查询 PM-001 最新遥测指标")
    public Result<IotLatestMetricsVO> latest(@PathVariable Long id) {
        try {
            return Result.success(telemetryService.latest(id));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}/metrics/history")
    @Operation(summary = "Query PM-001 metric history")
    public Result<IotMetricHistoryVO> history(
            @PathVariable Long id,
            @RequestParam String metricKey,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime
    ) {
        try {
            return Result.success(telemetryService.history(id, metricKey, startTime, endTime));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}
