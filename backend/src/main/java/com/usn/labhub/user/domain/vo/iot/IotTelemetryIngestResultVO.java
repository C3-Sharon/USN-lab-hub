package com.usn.labhub.user.domain.vo.iot;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "遥测接收结果")
public class IotTelemetryIngestResultVO {

    @Schema(description = "设备编号")
    private String deviceCode;

    @Schema(description = "上报时间")
    private String reportTime;

    @Schema(description = "解析后的指标数量")
    private int metricCount;

    @Schema(description = "是否更新最新值")
    private boolean latestUpdated;
}
