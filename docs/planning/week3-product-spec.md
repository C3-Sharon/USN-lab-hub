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
4. 前端新增历史曲线页面/卡片，默认展示最近 1 小时的 **power** 变化趋势。
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

第三周要求后端从内存切到 MySQL 持久化，使用 Flyway 管理表结构。迁移脚本按 `docs/backend/iot-schema-draft.md` 拆分为 `V2__create_iot_project_device_tables.sql` 和 `V3__create_iot_telemetry_tables.sql`。

### 4.1 必须建的两张表

与 `docs/backend/iot-schema-draft.md` 保持一致：

| 表名 | 作用 | 核心字段 |
|---|---|---|
| `iot_telemetry_raw` | 保存每次上报的原始 payload | `id`, `device_code`, `topic`, `payload`, `parse_status`, `error_message`, `received_at` |
| `iot_metric_data` | 保存解析后的指标 | `id`, `device_id`, `metric_key`, `metric_value`, `unit`, `reported_at`, `received_at` |

说明：

- `iot_telemetry_raw` 用于排错和追溯，保留原始 JSON 与解析状态。
- `iot_metric_data` 用于 latest 和 history 接口查询，按指标行存储。
- `device_id` 通过 `PM-001` 等 device_code 映射获得，第三周不做完整设备管理。

### 4.2 不做的表

第三周不建：

- 告警表 / 告警规则表
- 建议表
- 指令表 / 指令历史表
- 操作日志表
- 用户/权限相关表

## 5. API 契约冻结

### 5.1 latest 接口

沿用第二周契约，但数据源改为数据库。字段与 `docs/agent-guides/04_API_CONTRACT.md` 第 5.1 节保持一致：

```text
GET /api/iot/devices/{id}/latest
```

返回：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "deviceId": 1,
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

与 `docs/agent-guides/04_API_CONTRACT.md` 第 5.2 节保持一致：

```text
GET /api/iot/devices/{id}/metrics/history?metricKey=power&startTime=2026-07-13 11:00:00&endTime=2026-07-13 12:00:00
```

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| metricKey | 是 | 指标键，只允许 `voltage`、`current`、`power` |
| startTime | 否 | 开始时间，格式 `yyyy-MM-dd HH:mm:ss` |
| endTime | 否 | 结束时间，格式 `yyyy-MM-dd HH:mm:ss` |

参数规则：

- `startTime` 不得晚于 `endTime`。
- 时间范围包含 `startTime` 和 `endTime`。
- 不传时间范围时，返回当前已保存的全部对应指标数据。
- `points` 按 `time` 升序返回。

返回：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "deviceId": 1,
    "metricKey": "power",
    "unit": "W",
    "points": [
      { "time": "2026-07-13 11:17:44", "value": 88.1 },
      { "time": "2026-07-13 11:22:44", "value": 96.4 }
    ]
  }
}
```

## 6. 前端历史曲线需求

第三周前端在 PM-001 页面新增历史曲线区域，要求：

- 使用 ECharts 折线图展示。
- 默认展示 **power** 一条历史曲线，单位 `W`。
- 支持手动切换指标为 `voltage` 或 `current`，每次只展示一条曲线。
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
3. 历史曲线区域默认展示最近 1 小时 **power** 曲线，可切换为 voltage/current。
4. 停止模拟器 15 秒后，实时卡片状态变为 `离线`。
5. 重新启动模拟器，状态恢复在线，曲线继续更新。

## 10. 范围确认

第三周明确不做：

- 告警规则与告警通知
- 智能建议
- 指令下发与 ACK
- 操作日志
- 多设备接入与设备管理
- 用户权限与访客页
- 数据导出与复杂报表
- WebSocket / SSE 实时推送
- Redis latest 缓存

这些功能属于**第四周**及以后计划。若第三周提前实现，需经产品确认并确保不影响本周主线验收。

## 11. 依赖与风险

| 风险 | 应对措施 |
|---|---|
| MySQL 表结构与现有内存模型不一致 | 后端先用 Flyway 脚本建表，再迁移逻辑 |
| 历史数据量增大后查询慢 | 第三周数据量小，先按时间范围索引；后续再优化 |
| 前端 ECharts 增加包体积 | 按需引入 ECharts 组件 |

## 12. 需要配合

- 后端：按 `docs/backend/iot-schema-draft.md` 实现 Flyway V2/V3 迁移、`iot_telemetry_raw` / `iot_metric_data` 存储、`latest` 和 `history` 接口；`history` 响应使用 `msg`、对象结构和 `time/value` 字段。
- 前端：新增历史曲线组件，默认调用 `metricKey=power`，保留实时卡片；按 `04_API_CONTRACT.md` 使用 `msg` 和新的 history 响应结构。
- 硬件/模拟器：继续按现有 Topic 和 payload 上报，无需改动。
