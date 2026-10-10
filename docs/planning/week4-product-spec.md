# Week 4 产品冻结文档 / PM-001 告警-建议-指令-日志最小闭环

> 本文件由产品负责维护，作为第四周前后端和硬件组联调的唯一验收口径。
> 对应分支：`feature/week4-iot-product`

## 1. 第四周目标

第三周已经把 `latest` / `history` 链路落到 MySQL。第四周目标是在 PM-001 上跑通最小业务闭环：

```text
功率上报 → power > 100W → WARNING 告警 → 规则建议 → 下发 SET_SAMPLE_INTERVAL → MQTT ACK → 操作日志
```

第四周核心链路：

```text
模拟器上报 power=110W
        |
        v
后端创建 OPEN 告警（iot_alert_record）
        |
        v
后端/规则创建 PENDING 建议（iot_recommendation）：缩短采样间隔到 5 秒
        |
        v
前端 PM-001 页面展示告警 + 建议，用户点击“下发指令”
        |
        v
后端创建 PENDING 指令（iot_command_record），publish 到 MQTT command Topic
        |
        v
模拟器收到指令，执行 SET_SAMPLE_INTERVAL=5，publish 到 MQTT ack Topic
        |
        v
后端更新指令状态为 ACKED，写操作日志
        |
        v
前端更新指令状态和日志列表
```

## 2. 第四周验收口径

验收标准共 9 条：

1. 当 `power > 100W` 时，后端创建一条 `WARNING` 级别、状态为 `OPEN` 的告警记录。
2. 同一设备同一指标在连续上报期间不会无限生成新的 `OPEN` 告警（告警去重）。
3. 告警支持 `OPEN` / `HANDLED` / `IGNORED` 三种状态，用户可在页面操作。
4. 告警触发后自动生成一条 `PENDING` 建议；建议支持 `PENDING` / `CONFIRMED` / `IGNORED` 三种状态。
5. 本周唯一控制指令为 `SET_SAMPLE_INTERVAL`，固定参数 `intervalSeconds=5`。
6. 指令支持 `PENDING` / `SENT` / `ACKED` / `FAILED` / `TIMEOUT` 五种状态；ACK 超时时间为 10 秒。
7. 后端 publish 指令到 `iot/power-monitor/PM-001/command`，接收 ACK 从 `iot/power-monitor/PM-001/ack`。
8. 操作日志记录：处理告警、确认建议、发送指令、收到 ACK。
9. 前端 PM-001 页面新增告警卡片、建议卡片、指令按钮、操作日志列表，并提供完整演示脚本。

第四周不验收：多设备告警规则、用户自定义规则引擎、短信/邮件/微信通知、任意设备控制、AI 自动控制、独立工单系统、复杂权限矩阵。

## 3. 告警规则

### 3.1 触发条件

| 设备 | 指标 | 触发条件 | 级别 | 默认消息 |
|---|---|---|---|---|
| PM-001 | power | `power > 100` | WARNING | 功率超过 100W，当前值为 {value}W |

### 3.2 去重规则

- 同一设备、同一指标、同一级别，若已存在状态为 `OPEN` 的告警，新的上报不再创建新的 `OPEN` 告警。
- 已存在的 `OPEN` 告警只更新 `trigger_value` 和最后触发时间。
- 当指标恢复到阈值以下（`power <= 100W`）时，已存在的 `OPEN` 告警不会自动关闭，仍需用户手动处理为 `HANDLED` 或 `IGNORED`。
- 只有当该指标的 `OPEN` 告警被处理为 `HANDLED` 或 `IGNORED` 后，下一次超过阈值才会再次创建新的 `OPEN` 告警。

### 3.3 告警状态

| 状态 | 含义 | 可转移来源 |
|---|---|---|
| OPEN | 告警未处理 | 初始状态 |
| HANDLED | 用户已处理告警 | OPEN |
| IGNORED | 用户忽略告警 | OPEN |

### 3.4 页面按钮文案

- OPEN 告警显示按钮：
  - 「标为已处理」→ HANDLED
  - 「忽略」→ IGNORED

## 4. 建议规则

### 4.1 生成规则

- 当 PM-001 的 `power` 触发 `WARNING` 告警时，系统自动生成一条 PENDING 建议。
- 建议内容（固定）：
  - 标题：缩短采样间隔
  - 内容：当前功率 {value}W 超过 100W，建议将采样间隔调整为 5 秒，以便更密集地观察负载变化。

### 4.2 建议状态

| 状态 | 含义 | 可转移来源 |
|---|---|---|
| PENDING | 待确认 | 初始状态 |
| CONFIRMED | 用户确认建议 | PENDING |
| IGNORED | 用户忽略建议 | PENDING |

### 4.3 确认后的行为

- 用户确认建议后，前端展示「下发 SET_SAMPLE_INTERVAL」按钮。
- 点击后调用后端指令接口，参数固定为 `intervalSeconds=5`。
- 确认建议本身不会直接下发指令，必须再点一次指令按钮，给用户二次确认。

### 4.4 页面按钮文案

- PENDING 建议显示按钮：
  - 「确认并下发指令」→ CONFIRMED + 弹出确认框 + 调用指令接口
  - 「忽略建议」→ IGNORED

## 5. 控制指令

### 5.1 指令范围

第四周只支持一条指令：

| 指令 | 编码 | 参数 | 说明 |
|---|---|---|---|
| 设置采样间隔 | SET_SAMPLE_INTERVAL | `intervalSeconds=5` | 固定值 5 秒，本周不支持其他值 |

### 5.2 指令状态

| 状态 | 含义 |
|---|---|
| PENDING | 后端已创建指令，尚未 publish |
| SENT | 后端已 publish 到 MQTT command Topic |
| ACKED | 已收到设备 ACK |
| FAILED | 设备明确返回执行失败 |
| TIMEOUT | 发送后 10 秒内未收到 ACK |

### 5.3 ACK 超时

- 从状态变为 `SENT` 开始计时，10 秒内未收到 ACK，则状态自动变为 `TIMEOUT`。
- 超时后若再收到 ACK，不再更新该指令状态。

### 5.4 MQTT 指令报文

Topic：`iot/power-monitor/PM-001/command`

Payload：

```json
{
  "commandId": "cmd-20260714123045001",
  "command": "SET_SAMPLE_INTERVAL",
  "params": {
    "intervalSeconds": 5
  },
  "sentAt": "2026-07-14 12:30:45"
}
```

### 5.5 MQTT ACK 报文

Topic：`iot/power-monitor/PM-001/ack`

Payload：

```json
{
  "commandId": "cmd-20260714123045001",
  "command": "SET_SAMPLE_INTERVAL",
  "status": "ACKED",
  "result": {
    "intervalSeconds": 5
  },
  "ackedAt": "2026-07-14 12:30:46"
}
```

ACK status 只允许 `ACKED` 或 `FAILED`。

## 6. 操作日志

### 6.1 记录范围

第四周操作日志记录以下四类行为：

| 行为 | action 值 | target_type | target_id |
|---|---|---|---|
| 处理告警 | HANDLE_ALERT | ALERT | 告警 ID |
| 确认建议 | CONFIRM_RECOMMENDATION | RECOMMENDATION | 建议 ID |
| 发送指令 | SEND_COMMAND | COMMAND | 指令 ID |
| 收到 ACK | RECEIVE_ACK | COMMAND | 指令 ID |

### 6.2 日志字段

| 字段 | 说明 |
|---|---|
| operator_id | 操作人 ID；系统触发行为可记录为 0 或系统账号 |
| operator_name | 操作人名称 |
| action | 行为编码 |
| target_type | 目标类型 |
| target_id | 目标 ID |
| summary | 行为摘要，例如：确认建议并下发 SET_SAMPLE_INTERVAL，intervalSeconds=5 |
| created_at | 日志时间 |

## 7. API 契约

### 7.1 告警列表

```text
GET /api/iot/devices/{deviceId}/alerts
```

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| status | 否 | OPEN / HANDLED / IGNORED；不传返回全部 |
| metricKey | 否 | 指标键，例如 `power` |
| page | 否 | 页码，默认 1 |
| size | 否 | 每页条数，默认 20 |

响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 1,
    "list": [
      {
        "id": 1,
        "deviceId": 1,
        "deviceCode": "PM-001",
        "metricKey": "power",
        "level": "WARNING",
        "message": "功率超过 100W，当前值为 110.5W",
        "triggerValue": 110.5,
        "thresholdValue": 100.0,
        "status": "OPEN",
        "createdAt": "2026-07-14 12:30:00",
        "handledBy": null,
        "handledAt": null
      }
    ]
  }
}
```

空状态：`list` 为空数组。

异常状态：

- 设备不存在：`code=404`，`msg=设备不存在`
- 非法 status：`code=400`，`msg=非法的告警状态`

### 7.2 处理告警

```text
POST /api/iot/alerts/{alertId}/handle
```

请求体：

```json
{
  "status": "HANDLED"
}
```

status 只允许 `HANDLED` 或 `IGNORED`。

响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "status": "HANDLED",
    "handledAt": "2026-07-14 12:35:00"
  }
}
```

异常状态：

- 告警不存在：`code=404`
- 非 OPEN 告警不可处理：`code=400`，`msg=只能处理 OPEN 状态的告警`
- 非法 status：`code=400`

### 7.3 建议列表

```text
GET /api/iot/devices/{deviceId}/recommendations
```

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| status | 否 | PENDING / CONFIRMED / IGNORED；不传返回全部 |
| page | 否 | 默认 1 |
| size | 否 | 默认 20 |

响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 1,
    "list": [
      {
        "id": 1,
        "deviceId": 1,
        "deviceCode": "PM-001",
        "alertId": 1,
        "title": "缩短采样间隔",
        "content": "当前功率 110.5W 超过 100W，建议将采样间隔调整为 5 秒，以便更密集地观察负载变化。",
        "status": "PENDING",
        "createdAt": "2026-07-14 12:30:01"
      }
    ]
  }
}
```

### 7.4 确认/忽略建议

```text
POST /api/iot/recommendations/{recommendationId}/confirm
```

```text
POST /api/iot/recommendations/{recommendationId}/ignore
```

确认响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "status": "CONFIRMED"
  }
}
```

忽略响应结构相同，`status` 为 `IGNORED`。

异常状态：

- 建议不存在：`code=404`
- 非 PENDING 建议不可操作：`code=400`

### 7.5 发送指令

```text
POST /api/iot/devices/{deviceId}/commands
```

请求体（第四周固定参数）：

```json
{
  "command": "SET_SAMPLE_INTERVAL",
  "params": {
    "intervalSeconds": 5
  }
}
```

参数校验：

- `command` 必须为 `SET_SAMPLE_INTERVAL`。
- `params.intervalSeconds` 必须为 `5`。

响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "commandId": "cmd-20260714123045001",
    "deviceId": 1,
    "command": "SET_SAMPLE_INTERVAL",
    "params": {
      "intervalSeconds": 5
    },
    "status": "PENDING",
    "message": null,
    "createdAt": "2026-07-14 12:30:45",
    "sentAt": null,
    "ackedAt": null
  }
}
```

异常状态：

- 非法 command：`code=400`，`msg=第四周仅支持 SET_SAMPLE_INTERVAL`
- 非法参数：`code=400`，`msg=intervalSeconds 必须为 5`
- 设备不存在：`code=404`

### 7.6 指令详情/状态查询

```text
GET /api/iot/commands/{commandId}
```

响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "commandId": "cmd-20260714123045001",
    "deviceId": 1,
    "command": "SET_SAMPLE_INTERVAL",
    "params": {
      "intervalSeconds": 5
    },
    "status": "ACKED",
    "message": null,
    "createdAt": "2026-07-14 12:30:45",
    "sentAt": "2026-07-14 12:30:45",
    "ackedAt": "2026-07-14 12:30:46"
  }
}
```

### 7.7 指令列表

```text
GET /api/iot/devices/{deviceId}/commands
```

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| status | 否 | PENDING / SENT / ACKED / FAILED / TIMEOUT |
| page | 否 | 默认 1 |
| size | 否 | 默认 20 |

响应结构与告警列表一致，返回指令数组。

### 7.8 操作日志列表

```text
GET /api/iot/devices/{deviceId}/operation-logs
```

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| action | 否 | HANDLE_ALERT / CONFIRM_RECOMMENDATION / SEND_COMMAND / RECEIVE_ACK |
| targetType | 否 | ALERT / RECOMMENDATION / COMMAND |
| page | 否 | 默认 1 |
| size | 否 | 默认 20 |

响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 4,
    "list": [
      {
        "id": 1,
        "operatorId": 0,
        "operatorName": "system",
        "action": "SEND_COMMAND",
        "targetType": "COMMAND",
        "targetId": "cmd-20260714123045001",
        "summary": "下发 SET_SAMPLE_INTERVAL，intervalSeconds=5",
        "createdAt": "2026-07-14 12:30:45"
      }
    ]
  }
}
```

## 8. MQTT Topic 与 Payload

### 8.1 已有 Topic（不变）

| Topic | 方向 | 说明 |
|---|---|---|
| `iot/power-monitor/PM-001/telemetry` | 设备 -> 后端 | 遥测数据上报 |

### 8.2 新增 Topic

| Topic | 方向 | 说明 |
|---|---|---|
| `iot/power-monitor/PM-001/command` | 后端 -> 设备 | 控制指令下发 |
| `iot/power-monitor/PM-001/ack` | 设备 -> 后端 | 指令执行回执 |

### 8.3 command payload

```json
{
  "commandId": "cmd-20260714123045001",
  "command": "SET_SAMPLE_INTERVAL",
  "params": {
    "intervalSeconds": 5
  },
  "sentAt": "2026-07-14 12:30:45"
}
```

### 8.4 ack payload

```json
{
  "commandId": "cmd-20260714123045001",
  "command": "SET_SAMPLE_INTERVAL",
  "status": "ACKED",
  "result": {
    "intervalSeconds": 5
  },
  "ackedAt": "2026-07-14 12:30:46"
}
```

## 9. 数据库表

第四周新增或使用的表：

| 表名 | 说明 | 来源 |
|---|---|---|
| `iot_telemetry_raw` | 原始遥测报文 | 第三周已创建 |
| `iot_metric_data` | 解析后指标 | 第三周已创建 |
| `iot_alert_record` | 告警记录 | 第四周新增（V4 迁移） |
| `iot_recommendation` | 规则建议 | 第四周新增（V4 迁移） |
| `iot_command_record` | 控制指令 | 第四周新增（V4 迁移） |
| `iot_operation_log` | 操作日志 | 第四周新增（V4 迁移） |

表字段与 `docs/backend/iot-schema-draft.md` 保持一致，详见该文件第 6 - 9 节。

## 10. 前端页面需求

在 `/iot/pm001` 页面新增以下区域：

### 10.1 告警卡片

- 展示当前 `OPEN` 告警列表。
- 每条告警显示：告警时间、指标、触发值、阈值、消息。
- 提供「标为已处理」和「忽略」按钮。

### 10.2 建议卡片

- 展示当前 `PENDING` 建议列表。
- 每条建议显示：标题、内容、关联告警。
- 提供「确认并下发指令」和「忽略建议」按钮。

### 10.3 指令状态区

- 展示最近几条指令及其状态。
- 状态显示为：待发送 / 已发送 / 已确认 / 失败 / 超时。

### 10.4 操作日志列表

- 按时间倒序展示 HANDLE_ALERT、CONFIRM_RECOMMENDATION、SEND_COMMAND、RECEIVE_ACK 四类日志。
- 显示操作人、行为、摘要、时间。

### 10.5 按钮文案汇总

| 区域 | 按钮文案 | 行为 |
|---|---|---|
| 告警卡片 | 标为已处理 | POST /api/iot/alerts/{id}/handle，status=HANDLED |
| 告警卡片 | 忽略 | POST /api/iot/alerts/{id}/handle，status=IGNORED |
| 建议卡片 | 确认并下发指令 | POST /api/iot/recommendations/{id}/confirm，然后 POST /api/iot/devices/1/commands |
| 建议卡片 | 忽略建议 | POST /api/iot/recommendations/{id}/ignore |

## 11. 演示脚本

### 11.1 启动依赖

```bash
# 启动 MySQL 并执行 Flyway 迁移
cd backend
mvnw.cmd flyway:migrate

# 启动 MQTT Broker
python scripts/start_mqtt_broker.py

# 启动后端
cd backend
mvnw.cmd spring-boot:run

# 启动前端
cd frontend
npm run dev
```

### 11.2 触发告警

```bash
# 启动模拟器（默认 power 在阈值附近波动，可临时调高）
python scripts/pm001_simulator.py
```

### 11.3 验收步骤

1. 打开 `http://localhost:5173/iot/pm001`，确认实时卡片和历史曲线正常。
2. 等待或手动触发 `power > 100W`，页面出现 OPEN 告警卡片。
3. 告警卡片展示「标为已处理」「忽略」按钮。
4. 同时出现 PENDING 建议卡片，内容为缩短采样间隔。
5. 点击「确认并下发指令」，后端创建指令并 publish 到 MQTT command Topic。
6. 模拟器收到指令后 publish ACK 到 MQTT ack Topic。
7. 指令状态从 PENDING -> SENT -> ACKED。
8. 操作日志列表出现 HANDLE_ALERT、CONFIRM_RECOMMENDATION、SEND_COMMAND、RECEIVE_ACK 记录。
9. 点击「忽略」可将告警置为 IGNORED；点击「标为已处理」可将告警置为 HANDLED。

## 12. 范围确认

第四周明确不做：

- 多设备告警规则
- 用户自定义规则引擎
- 短信、邮件、微信通知
- 任意设备控制（如 restart、set_threshold 等）
- AI 自动控制硬件
- 独立工单系统
- 复杂权限矩阵
- WebSocket / SSE 实时推送

这些功能属于第五周及以后计划。

## 13. 需要配合

- 后端：按本文件实现告警生成/去重、建议生成、指令创建/MQTT 发送/ACK 处理、操作日志记录，并完成 V4 Flyway 迁移。
- 前端：按本文件新增告警/建议/指令/日志四个区域，调用对应 API，处理空状态和异常状态。
- 硬件/模拟器：在 `scripts/pm001_simulator.py` 中增加 `SET_SAMPLE_INTERVAL` 指令解析和 ACK 上报能力。

## 14. 已知风险

| 风险 | 应对措施 |
|---|---|
| ACK 超时与真实网络延迟差异 | 开发期用本地 Broker，10 秒足够；生产环境可调整 |
| 告警去重逻辑遗漏 | 单元测试覆盖连续上报、恢复后再触发两种场景 |
| 前端同时展示四个新区域导致页面拥挤 | 使用折叠卡片或 Tab 分区 |
| 指令参数被扩展 | 后端严格校验，非 `intervalSeconds=5` 直接返回 400 |
