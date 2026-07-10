package com.usn.labhub.user.controller.iot;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.vo.iot.IotLatestMetricsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/iot/devices")
@Tag(name = "IoT 设备 Mock 接口", description = "Week 1 PM-001 最小纵向切片联调接口")
public class IotDeviceMockController {

    @GetMapping("/{id}/latest")
    @Operation(summary = "查询 PM-001 最新 Mock 指标")
    public Result<IotLatestMetricsVO> latest(@PathVariable Long id) {
        if (id == null || id != 1L) {
            return Result.error("Week 1 mock only supports device id 1 (PM-001)");
        }

        IotLatestMetricsVO latest = new IotLatestMetricsVO();
        latest.setDeviceId(1L);
        latest.setDeviceCode("PM-001");
        latest.setDeviceName("实验室功耗监测仪 #1");
        latest.setProjectName("实验室功耗监测");
        latest.setStatus("ONLINE");
        latest.setReportTime("2026-07-09 20:00:00");
        latest.setMetrics(List.of(
                new IotLatestMetricsVO.MetricValueVO("voltage", "电压", new BigDecimal("220.3"), "V"),
                new IotLatestMetricsVO.MetricValueVO("current", "电流", new BigDecimal("0.42"), "A"),
                new IotLatestMetricsVO.MetricValueVO("power", "功率", new BigDecimal("92.5"), "W")
        ));
        return Result.success(latest);
    }
}
