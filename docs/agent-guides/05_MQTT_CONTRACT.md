# MQTT Contract / MQTT 契约

> 中文说明：本文件是硬件组和后端对接的核心契约。硬件上报、后端订阅、指令下发、ACK 回执都必须以本文件为准。任何 Topic 或 payload 字段变更都必须同步更新本文件。

## 1. Broker / MQTT Broker

MVP recommended broker:

- EMQX or Mosquitto

中文建议：MVP 阶段推荐 EMQX 或 Mosquitto。EMQX 管理台更适合演示，Mosquitto 更轻量。最终由后端负责人和硬件组根据实际环境确定。

## 2. Topic Rules / Topic 规则

```text
Telemetry:   iot/{projectCode}/{deviceCode}/telemetry
Status:      iot/{projectCode}/{deviceCode}/status
Command:     iot/{projectCode}/{deviceCode}/command
Command ACK: iot/{projectCode}/{deviceCode}/ack
```

Example:

```text
iot/power-monitor/PM-001/telemetry
iot/power-monitor/PM-001/status
iot/power-monitor/PM-001/command
iot/power-monitor/PM-001/ack
```

Week 1 status / 第一周状态：

- Backend HTTP API currently uses code-level mock data.
- Backend does not subscribe to MQTT yet.
- Backend does not read telemetry from database yet.
- Backend does not depend on `pm001_simulator.py` yet.
- Next integration target topic is `iot/power-monitor/PM-001/telemetry`.

## 3. Device Code Rule / 设备编号规则

Recommended examples:

```text
PM-001      power monitor / 功耗检测设备
DAQ-001     data acquisition device / 串行采集设备
MED-001     smart medicine box / 智慧药盒
AUDIO-001   audio inspection device / 音频巡检
```

Rules:

- Device code must be stable.
- Device code in Topic and payload must match.
- Do not reuse a device code for another physical device.
- 设备编号必须稳定。
- Topic 和 payload 中的设备编号必须一致。
- 不要把同一个编号复用给另一台实体设备。

## 4. Telemetry Payload / 数据上报消息

Topic:

```text
iot/{projectCode}/{deviceCode}/telemetry
```

Payload:

```json
{
  "deviceCode": "PM-001",
  "reportTime": "2026-07-09 20:00:00",
  "metrics": {
    "voltage": 220.3,
    "current": 0.42,
    "power": 92.5
  },
  "status": "online"
}
```

Field meaning / 字段含义：

| Field | Type | Required | 中文说明 |
|---|---|---|---|
| deviceCode | string | yes | 设备编号 |
| reportTime | string | yes | 设备上报时间，格式 `yyyy-MM-dd HH:mm:ss` |
| metrics | object | yes | 指标键值对 |
| status | string | no | online/offline/alert/maintenance |

Metric naming rule / 指标命名：

- Use lower camel case or lower snake case consistently.
- MVP vertical slice uses `voltage`, `current`, `power`.
- 指标名保持稳定，不要临时改中文字段。

## 5. Status Payload / 状态消息

Topic:

```text
iot/{projectCode}/{deviceCode}/status
```

Payload:

```json
{
  "deviceCode": "PM-001",
  "status": "online",
  "timestamp": 1783333800000,
  "message": "device online"
}
```

Allowed status:

```text
online / offline / alert / maintenance
```

## 6. Command Payload / 指令消息

Topic:

```text
iot/{projectCode}/{deviceCode}/command
```

Week 4 fixed command / 第四周固定指令：

```text
SET_SAMPLE_INTERVAL，参数固定为 intervalSeconds=5
```

Payload:

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

Field meaning / 字段含义：

| Field | Type | Required | 中文说明 |
|---|---|---|---|
| commandId | string | yes | 指令编号，全局唯一，ACK 必须原样带回 |
| command | string | yes | 指令编码，本周固定为 `SET_SAMPLE_INTERVAL` |
| params | object | yes | 指令参数，本周固定为 `{ "intervalSeconds": 5 }` |
| sentAt | string | yes | 后端发送时间，格式 `yyyy-MM-dd HH:mm:ss` |

Rules:

- Every command must include `commandId`.
- Hardware must include the same `commandId` in ACK.
- MVP should only use low-risk commands.
- 每条指令必须有 `commandId`。
- 硬件 ACK 必须带回同一个 `commandId`。
- MVP 只做低风险控制动作。

## 7. Command ACK Payload / 指令回执消息

Topic:

```text
iot/{projectCode}/{deviceCode}/ack
```

Payload:

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

Field meaning / 字段含义：

| Field | Type | Required | 中文说明 |
|---|---|---|---|
| commandId | string | yes | 对应 command 消息的 `commandId` |
| command | string | yes | 指令编码，与 command 消息一致 |
| status | string | yes | `ACKED` 或 `FAILED` |
| result | object | no | 执行结果，例如 `{ "intervalSeconds": 5 }` |
| ackedAt | string | yes | 设备回执时间，格式 `yyyy-MM-dd HH:mm:ss` |

Allowed ACK status:

```text
ACKED / FAILED
```

Backend command status:

```text
PENDING / SENT / ACKED / FAILED / TIMEOUT
```

ACK timeout / ACK 超时：

```text
10 秒
```

- 后端发布 command 并将指令状态置为 `SENT` 后开始计时。
- 10 秒内未收到 ACK，则状态自动变为 `TIMEOUT`。
- 超时后再收到 ACK，不再更新该指令状态。

## 8. MVP Hardware Confirmation Table / MVP 硬件确认表

| Device | deviceCode | Metrics | Units | Report interval | Control action | ACK support |
|---|---|---|---|---|---|---|
| 功耗检测 | PM-001 | voltage/current/power | V/A/W | 默认 5s（由 SET_SAMPLE_INTERVAL 下发） | SET_SAMPLE_INTERVAL | 已支持 |
| 串行采集数据监测仪 | DAQ-001 | analog_value | 待确认 | 待确认 | 待确认 | 待确认 |

## 9. MQTT Test Checklist / MQTT 测试清单

For every MQTT integration test, record:

- Broker address / Broker 地址
- Topic / 主题
- Payload / 消息体
- Publish tool / 发布工具
- Backend log / 后端日志
- Database result / 数据库结果
- Frontend result / 前端结果

Expected telemetry test result:

```text
1. Backend receives telemetry.
2. iot_telemetry_raw inserts one raw payload.
3. iot_metric_data inserts one row per metric.
4. Redis latest value updates if Redis is enabled.
5. Frontend device detail shows latest data.
```

## 10. Change Log / 变更记录

| Date | Change | Owner | Impact |
|---|---|---|---|
| 2026-07-06 | Initial MQTT contract draft | Codex | Hardware/backend alignment |
| 2026-07-14 | Freeze command/ack payload and SET_SAMPLE_INTERVAL for Week 4 | Product | Command/ack topics aligned with `week4-product-spec.md` |
