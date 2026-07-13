# Week 3 产品冻结文档 / PM-001 数据持久化与历史曲线

> 本文件由产品负责维护，作为第三周前后端和硬件组联调的唯一验收口径。
> 对应分支：`feature/week3-iot-product`

## 1. 第三周目标

第二周已经把 `pm001_simulator.py -> MQTT -> 后端 -> latest 接口 -> 前端` 的实时链路跑通。第三周目标是把数据从内存落到数据库，并补上前端历史曲线，使系统更接近真实生产状态。

第三周核心链路：

```text
模拟器/真实硬件 -> MQTT -> 后端解析 -> MySQL 持久化
                                          |
                                          v
                              latest 接口（读库）
                                          |
                                          v
                              前端实时卡片 + 历史曲线
```

## 2. 第三周验收口径

验收标准共 6 条：

1. 后端把每次 MQTT telemetry 的原始 payload 保存到 `iot_telemetry_raw` 表。
2. 后端把解析后的 `voltage / current / power` 保存到 `iot_metric_data` 表。
3. `GET /api/iot/devices/1/latest` 从数据库读取最近一次上报，不再使用内存快照。
4. 前端新增历史曲线页面/卡片，能展示最近 1 小时的 voltage / current / power 变化趋势。
5. 提供 `GET /api/iot/devices/{deviceId}/metrics/history` 接口，支持按指标键和时间范围查询。
6. 演示脚本包含：启动 Broker -> 启动后端 -> 启动模拟器 -> 前端查看实时卡片和历史曲线。

第三周不验收：告警规则、指令下发、多设备接入、设备/项目 CRUD、用户权限、访客展示页。

## 3. MQTT Topic 与 Payload（延续并澄清）

### 3.1 Topic 最终形态

已冻结，第三周继续沿用：

```text
iot/power-monitor/PM-001/telemetry
```

> 注意：`docs/agent-guides/06_VERTICAL_SLICE_PLAN.md` 中早期写的是 `usn/{projectCode}/{deviceCode}/telemetry`，与当前实际实现不一致。第三周以本文件和 `docs/agent-guides/05_MQTT_CONTRACT.md` 中的 `iot/...` 为准，后续统一修正 06 文档。

### 3.2 Payload 字段

与第二周保持一致，不再扩展：

```json
{
  "projectCode": "power-monitor",
  "deviceCode": "PM-001",
  "reportTime": "2026-07-10 14:30:00",
  "metrics": {
    "voltage": 220.3,
    "current": 0.42,
    "power": 92.5
  },
  "status": "online"
}
```

字段说明见 `docs/planning/week2-product-spec.md` 第 2 节。第三周不新增 payload 字段。

## 4. 数据持久化范围

第三周要求后端从内存切到 MySQL 持久化，使用 Flyway 管理表结构。

### 4.1 必须建的两张表

| 表名 | 作用 | 核心字段 |
|---|---|---|
| `iot_telemetry_raw` | 保存每次上报的原始 payload | `id`, `project_code`, `device_code`, `topic`, `payload_json`, `report_time`, `received_time`, `created_at` |
| `iot_metric_data` | 保存解析后的指标 | `id`, `project_code`, `device_code`, `metric_key`, `metric_value`, `report_time`, `created_at` |

### 4.2 不做的表

第三周不建：

- 告警表 / 告警规则表
- 指令表 / 指令历史表
- 设备表 / 项目表（继续用硬编码或内存映射）
- 用户/权限相关表

## 5. API 契约冻结

### 5.1 latest 接口

沿用第二周契约，但数据源改为数据库：

```text
GET /api/iot/devices/1/latest
```

返回字段不变：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "deviceCode": "PM-001",
    "deviceName": "实验室功耗监测仪 #1",
    "projectName": "实验室功耗监测",
    "status": "ONLINE",
    "reportTime": "2026-07-10 14:30:00",
    "metrics": [
      { "metricKey": "voltage", "metricName": "电压", "value": 220.3, "unit": "V" },
      { "metricKey": "current", "metricName": "电流", "value": 0.42, "unit": "A" },
      { "metricKey": "power", "metricName": "功率", "value": 92.5, "unit": "W" }
    ]
  }
}
```

### 5.2 history 接口（新增）

```text
GET /api/iot/devices/{deviceId}/metrics/history?metricKey=voltage&startTime=2026-07-10T13:00:00&endTime=2026-07-10T15:00:00
```

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| metricKey | 是 | 指标键：`voltage`、`current`、`power` |
| startTime | 否 | 开始时间，ISO-8601 格式；默认 1 小时前 |
| endTime | 否 | 结束时间，ISO-8601 格式；默认当前时间 |

返回：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "reportTime": "2026-07-10 14:25:00", "metricValue": 220.1 },
    { "reportTime": "2026-07-10 14:26:00", "metricValue": 220.3 },
    { "reportTime": "2026-07-10 14:27:00", "metricValue": 220.2 }
  ]
}
```

## 6. 前端历史曲线需求

第三周前端在 PM-001 页面新增历史曲线区域，要求：

- 使用 ECharts 折线图展示。
- 同时支持展示 voltage / current / power 三条曲线，使用 Y 轴双轴或独立单位提示。
- 默认时间范围：最近 1 小时。
- 刷新机制：每 30 秒自动刷新历史数据。
- 无数据时显示空状态提示。
- 保留第二周的实时卡片和在线状态展示。

## 7. 在线/离线判断规则

延续第二周规则：

- 设备最近一次上报时间 `reportTime` 距当前时间不超过 **15 秒**，状态显示为 `ONLINE`。
- 超过 **15 秒** 未收到新上报，状态显示为 `OFFLINE`。
- 判断逻辑由后端在 `latest` 接口中实时计算。

## 8. 硬件接入说明

PM-001 目前由 `scripts/pm001_simulator.py` 模拟。第三周继续用模拟器跑通链路，真实硬件接入留到后续周。

硬件组若要接入真实设备，需满足：

1. 支持 MQTT 客户端，能连接外部 Broker。
2. 按本文件第 3 节的 Topic 和 Payload 字段上报。
3. 上报频率建议 5 秒一次，与模拟器默认频率一致。
4. 时间字段使用设备本地时间，格式 `yyyy-MM-dd HH:mm:ss`。

## 9. 演示脚本

### 9.1 启动数据库

确保 MySQL 已启动，且后端配置正确：

```bash
cd backend
mvnw.cmd flyway:migrate
```

### 9.2 启动 MQTT Broker

```bash
python scripts/start_mqtt_broker.py
```

### 9.3 启动后端

```bash
cd backend
mvnw.cmd spring-boot:run
```

### 9.4 启动前端

```bash
cd frontend
npm run dev
```

打开 `http://localhost:5173/iot/pm001`。

### 9.5 启动模拟器

```bash
python scripts/pm001_simulator.py
```

### 9.6 验证演示

1. 实时卡片显示最新 voltage / current / power。
2. 状态为 `在线`。
3. 历史曲线区域展示最近 1 小时三条曲线。
4. 停止模拟器 15 秒后，实时卡片状态变为 `离线`。
5. 重新启动模拟器，状态恢复在线，曲线继续更新。

## 10. 范围确认

第三周明确不做：

- 告警规则与告警通知
- 指令下发与 ACK
- 多设备接入与设备管理
- 用户权限与访客页
- 数据导出与复杂报表

这些功能属于第四周及以后计划，若提前实现需经产品确认。

## 11. 依赖与风险

| 风险 | 应对措施 |
|---|---|
| MySQL 表结构与现有内存模型不一致 | 后端先用 Flyway 脚本建表，再迁移逻辑 |
| 历史数据量增大后查询慢 | 第三周数据量小，先按时间范围索引；后续再优化 |
| 前端 ECharts 增加包体积 | 按需引入 ECharts 组件 |

## 12. 需要配合

- 后端：实现 Flyway 脚本、`iot_telemetry_raw` / `iot_metric_data` 存储、`latest` 和 `history` 接口。
- 前端：新增历史曲线组件，调用 history 接口，保留实时卡片。
- 硬件/模拟器：继续按现有 Topic 和 payload 上报，无需改动。
