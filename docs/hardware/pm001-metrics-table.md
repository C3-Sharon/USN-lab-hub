# PM-001 数据指标表

> 本文件由产品/硬件组维护，定义 PM-001 功耗监测设备产生的数据指标及其存储方式。

## 指标列表

| 指标键 | 指标名 | 单位 | 数据类型 | 精度 | 典型范围 | 存储表 |
|---|---|---|---|---|---|---|
| voltage | 电压 | V | DECIMAL(10,3) | 0.001 | 200.000 - 240.000 | iot_metric_data |
| current | 电流 | A | DECIMAL(10,3) | 0.001 | 0.000 - 10.000 | iot_metric_data |
| power | 功率 | W | DECIMAL(10,3) | 0.001 | 0.000 - 2000.000 | iot_metric_data |

## 指标来源

所有指标来自 PM-001 设备上报的 MQTT telemetry payload：

```json
{
  "metrics": {
    "voltage": 220.3,
    "current": 0.42,
    "power": 92.5
  }
}
```

## 存储方式

### 原始报文

每次上报的完整 JSON 保存到 `iot_telemetry_raw` 表：

| 字段 | 说明 |
|---|---|
| id | 自增主键 |
| project_code | `power-monitor` |
| device_code | `PM-001` |
| topic | `iot/power-monitor/PM-001/telemetry` |
| payload_json | 完整 payload JSON |
| report_time | payload 中的 `reportTime` |
| received_time | 后端收到时间 |
| created_at | 入库时间 |

### 解析指标

从原始报文解析出的指标保存到 `iot_metric_data` 表：

| 字段 | 说明 |
|---|---|
| id | 自增主键 |
| project_code | `power-monitor` |
| device_code | `PM-001` |
| metric_key | `voltage` / `current` / `power` |
| metric_value | 指标数值 |
| report_time | payload 中的 `reportTime` |
| created_at | 入库时间 |

## 查询场景

| 场景 | 使用表 | 查询条件 |
|---|---|---|
| 展示最新数据 | `iot_metric_data` | `device_code = 'PM-001' ORDER BY report_time DESC LIMIT 1` |
| 展示历史曲线 | `iot_metric_data` | `device_code = 'PM-001' AND metric_key = ? AND report_time BETWEEN ? AND ?` |
| 排查原始报文 | `iot_telemetry_raw` | `device_code = 'PM-001' ORDER BY report_time DESC` |

## 未来扩展

后续周可能新增指标，如：

- `power_factor` 功率因数
- `frequency` 电网频率
- `energy` 累计电能

新增指标时，只需按相同格式写入 `iot_metric_data`，前端历史曲线可动态识别。
