# Vertical Slice Plan / 最小纵向切片计划

> 中文说明：本文件用于解决“三个人各自 vibecoding 最后连不上”的问题。第一阶段所有人都围绕同一条最小闭环开发，而不是各自做一堆孤立功能。

## 1. Slice Goal / 切片目标

Build one complete end-to-end path:

```text
Project -> Device -> MQTT Telemetry -> Latest Data -> Alert -> Recommendation -> Command -> ACK -> Operation Log
项目 -> 设备 -> MQTT 数据 -> 最新数据 -> 告警 -> 建议 -> 指令 -> 回执 -> 操作日志
```

If this slice works, the project is demonstrable.

这条线跑通后，项目就具备答辩演示的核心价值。

## 2. Fixed MVP Slice / 固定切片内容

Project:

```text
projectCode: power-monitor
projectName: 功耗检测项目
```

Device:

```text
deviceCode: PM-001
deviceName: 功耗检测设备 001
deviceType: POWER_METER
protocol: MQTT
```

Metrics:

```text
voltage: V
current: A
power: W
```

Alert rule:

```text
If power > 100 W, create WARNING alert.
如果 power > 100 W，生成 WARNING 告警。
```

Recommendation:

```text
当前功率超过阈值，建议降低负载、检查供电，或临时提高采样间隔观察波动。
```

Command:

```text
SET_SAMPLE_INTERVAL
params.intervalSeconds = 5
```

## 3. Product Deliverables / 产品交付

Product owner must provide:

- PM-001 device field definition
- Device detail page field list
- Alert copy
- Recommendation copy
- Command button copy
- Demo script
- Acceptance checklist

中文：

- 明确 PM-001 展示字段。
- 明确设备详情页展示内容。
- 明确 power 超阈值告警文案。
- 明确建议文案。
- 明确指令按钮文案。
- 写出演示脚本和验收清单。

## 4. Frontend Deliverables / 前端交付

Frontend owner must implement:

- Device detail page for PM-001
- Latest metric cards
- Power history chart
- Alert area
- Recommendation area
- Command button
- Command status display
- Operation log preview

Frontend can start with mock JSON from `04_API_CONTRACT.md`.

前端可以先用 API 契约里的 mock JSON，不等后端全部完成。

## 5. Backend Deliverables / 后端交付

Backend owner must implement:

- Tables for project/device/metric/raw/metric_data/alert/recommendation/command/log
- Device detail API
- Latest data API
- History data API
- Mock telemetry report API
- MQTT telemetry ingest
- Alert generation when power > 100
- Recommendation generation
- Command send API
- Command ACK handling
- Operation log creation

## 6. MQTT Simulator / MQTT 模拟器

Even if real hardware is available, keep a simulator fallback.

即使真实硬件可用，也必须保留模拟器兜底。

Simulator should publish:

Topic:

```text
iot/power-monitor/PM-001/telemetry
```

Payload:

```json
{
  "projectCode": "power-monitor",
  "deviceCode": "PM-001",
  "reportTime": "2026-07-10 10:45:00",
  "metrics": {
    "voltage": 220.3,
    "current": 0.42,
    "power": 120.5
  },
  "status": "online"
}
```

Simulator or hardware should subscribe:

```text
iot/power-monitor/PM-001/command
```

And publish ACK:

```text
iot/power-monitor/PM-001/ack
```

## 7. Acceptance Test / 验收测试

The vertical slice is accepted only when:

1. Project `power-monitor` exists.
2. Device `PM-001` exists.
3. MQTT telemetry is published.
4. Backend stores raw telemetry.
5. Backend stores parsed metric data.
6. Latest API returns voltage/current/power.
7. Frontend device detail shows latest values.
8. Power history chart shows data points.
9. `power > 100` creates a warning alert.
10. Frontend shows alert and recommendation.
11. User sends `SET_SAMPLE_INTERVAL`.
12. Backend publishes command.
13. Hardware or simulator returns ACK.
14. Command status becomes `ACKED`.
15. Operation log contains the full action record.

中文验收：以上 15 步必须连续跑通，才算 MVP 主链路真正可演示。

## 8. Do Not Expand Before Slice Works / 切片未通前不要扩展

Before this slice works, do not spend major effort on:

- Many device types
- Complex dashboard configuration
- Full visitor portal polish
- Agent assistant
- Advanced time-series database
- Complex permission matrix

中文：最小闭环没通前，不要做大量设备类型、复杂大屏、完整访客门户、Agent、时序数据库或复杂权限矩阵。

## 9. Weekly Checkpoint / 周验收

Every Friday acceptance should ask:

```text
Can PM-001 complete more of the vertical slice this week?
本周 PM-001 这条闭环往前推进了哪一步？
```

Do not accept isolated progress that cannot connect to the slice.

不验收无法接入主链路的孤立功能。
