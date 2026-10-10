package com.usn.labhub.user.controller.iot;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.iot.IotTelemetryReportDTO;
import com.usn.labhub.user.domain.vo.iot.IotTelemetryIngestResultVO;
import com.usn.labhub.user.service.iot.IotTelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iot/telemetry")
@Tag(name = "IoT 遥测接口", description = "PM-001 遥测接收与联调接口")
public class IotTelemetryController {

    private final IotTelemetryService telemetryService;

    public IotTelemetryController(IotTelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping("/mock-report")
    @Operation(summary = "通过 HTTP 模拟 PM-001 遥测上报")
    public Result<IotTelemetryIngestResultVO> mockReport(@RequestBody IotTelemetryReportDTO report) {
        try {
            return Result.success(telemetryService.ingestHttpReport(report));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}
