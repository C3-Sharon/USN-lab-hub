# API Contract / API 契约

> 中文说明：本文件是前后端对接的核心契约。前端 mock、后端 DTO/VO、接口联调都必须以本文件为准。任何字段变更必须同步更新本文件。

Status values / 状态：

```text
draft        designed but not implemented / 已设计未实现
implemented  backend implemented / 后端已实现
integrated   frontend integrated / 前端已联调
changed      contract changed, needs sync / 契约已变更需同步
```

## 1. Common Response / 通用返回结构

Use existing backend `Result` style where possible.

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {}
}
```

Current backend `Result<T>` uses `code/msg/data`.

当前后端 `Result<T>` 使用 `code/msg/data`，前端 mock 和联调必须使用 `msg`，不要使用 `message`。

## 2. Common Page Response / 通用分页结构

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "records": [],
    "total": 0,
    "size": 10,
    "current": 1,
    "pages": 0
  }
}
```

## 3. Project APIs / 项目接口

### 3.1 List Projects / 项目列表

```text
GET /api/iot/projects
Status: draft
Used by: /iot/projects, /iot/overview
```

Query params:

| Name | Type | Required | Description |
|---|---|---|---|
| keyword | string | no | 项目名称或编号 |
| status | string | no | ACTIVE / ARCHIVED |
| pageNum | number | no | 页码 |
| pageSize | number | no | 每页数量 |

Response data record:

```json
{
  "id": 1,
  "projectCode": "power-monitor",
  "projectName": "功耗检测项目",
  "description": "功耗检测与功耗表演示项目",
  "ownerId": 1,
  "ownerName": "项目负责人",
  "status": "ACTIVE",
  "deviceCount": 1,
  "onlineDeviceCount": 1,
  "publicVisible": true,
  "createdAt": "2026-07-06 20:00:00"
}
```

### 3.2 Project Detail / 项目详情

```text
GET /api/iot/projects/{id}
Status: draft
Used by: /iot/projects/:id
```

Response data:

```json
{
  "id": 1,
  "projectCode": "power-monitor",
  "projectName": "功耗检测项目",
  "description": "用于演示功耗数据采集、告警、建议和控制闭环",
  "ownerId": 1,
  "ownerName": "项目负责人",
  "status": "ACTIVE",
  "publicVisible": true,
  "devices": []
}
```

### 3.3 Create/Update Project / 新增或更新项目

```text
POST /api/iot/projects
PUT /api/iot/projects/{id}
Status: draft
```

Request body:

```json
{
  "projectCode": "power-monitor",
  "projectName": "功耗检测项目",
  "description": "用于演示功耗闭环",
  "ownerId": 1,
  "status": "ACTIVE",
  "publicVisible": true
}
```

## 4. Device APIs / 设备接口

### 4.1 List Devices / 设备列表

```text
GET /api/iot/devices
Status: draft
Used by: /iot/devices, /iot/overview
```

Query params:

| Name | Type | Required | Description |
|---|---|---|---|
| projectId | number | no | 所属项目 |
| keyword | string | no | 设备名称或编号 |
| status | string | no | ONLINE / OFFLINE / ALERT / MAINTENANCE |
| deviceType | string | no | POWER_METER 等 |
| pageNum | number | no | 页码 |
| pageSize | number | no | 每页数量 |

Response data record:

```json
{
  "id": 1,
  "projectId": 1,
  "projectName": "功耗检测项目",
  "deviceCode": "PM-001",
  "deviceName": "功耗检测设备 001",
  "deviceType": "POWER_METER",
  "protocol": "MQTT",
  "location": "实验室",
  "ownerId": 1,
  "ownerName": "硬件负责人",
  "status": "ONLINE",
  "lastSeenAt": "2026-07-06 20:05:00",
  "alertCount": 0,
  "tags": ["功耗", "演示"]
}
```

### 4.2 Device Detail / 设备详情

```text
GET /api/iot/devices/{id}
Status: draft
Used by: /iot/devices/:id
```

Response data:

```json
{
  "id": 1,
  "projectId": 1,
  "projectName": "功耗检测项目",
  "deviceCode": "PM-001",
  "deviceName": "功耗检测设备 001",
  "deviceType": "POWER_METER",
  "protocol": "MQTT",
  "location": "实验室",
  "ownerName": "硬件负责人",
  "status": "ONLINE",
  "lastSeenAt": "2026-07-06 20:05:00",
  "description": "最小纵向切片演示设备",
  "metrics": [
    {"metricKey": "voltage", "metricName": "电压", "unit": "V"},
    {"metricKey": "current", "metricName": "电流", "unit": "A"},
    {"metricKey": "power", "metricName": "功率", "unit": "W"}
  ]
}
```

### 4.3 Create/Update Device / 新增或更新设备

```text
POST /api/iot/devices
PUT /api/iot/devices/{id}
Status: draft
```

Request body:

```json
{
  "projectId": 1,
  "deviceCode": "PM-001",
  "deviceName": "功耗检测设备 001",
  "deviceType": "POWER_METER",
  "protocol": "MQTT",
  "location": "实验室",
  "ownerId": 1,
  "description": "最小纵向切片演示设备",
  "tags": ["功耗", "演示"]
}
```

## 5. Telemetry APIs / 数据接口

### 5.1 Latest Device Metrics / 最新数据

```text
GET /api/iot/devices/{id}/latest
Status: implemented
Used by: /iot/devices/:id
```

Response data:

```json
{
  "deviceId": 1,
  "deviceCode": "PM-001",
  "deviceName": "实验室功耗监测仪 #1",
  "projectName": "实验室功耗监测",
  "status": "ONLINE",
  "reportTime": "2026-07-09 20:00:00",
  "metrics": [
    {"metricKey": "voltage", "metricName": "电压", "value": 220.3, "unit": "V"},
    {"metricKey": "current", "metricName": "电流", "value": 0.42, "unit": "A"},
    {"metricKey": "power", "metricName": "功率", "value": 92.5, "unit": "W"}
  ]
}
```

Implementation boundary / 实现边界：

- Week 2 implementation is driven by telemetry ingest.
- `POST /api/iot/telemetry/mock-report` and MQTT topic `iot/power-monitor/PM-001/telemetry` write into the same backend ingest service.
- Week 3 production storage uses MySQL/Flyway tables `iot_telemetry_raw` and `iot_metric_data`.
- Backend automated tests use an isolated in-memory store so tests do not require MySQL.
- Online status rule: `reportTime` within the latest 15 seconds means `ONLINE`; otherwise `OFFLINE`.

### 5.2 Metric History / 历史数据

```text
GET /api/iot/devices/{id}/metrics/history
Status: implemented
Used by: /iot/devices/:id
```

Query params:

| Name | Type | Required | Description |
|---|---|---|---|
| metricKey | string | yes | voltage/current/power |
| startTime | string | no | 开始时间 |
| endTime | string | no | 结束时间 |

Response data:

```json
{
  "deviceId": 1,
  "metricKey": "power",
  "unit": "W",
  "points": [
    {"time": "2026-07-06 20:00:00", "value": 80.1},
    {"time": "2026-07-06 20:05:00", "value": 92.5}
  ]
}
```

Week 3 implementation notes:

- `metricKey` only accepts `voltage`, `current`, or `power`.
- `startTime` and `endTime` use `yyyy-MM-dd HH:mm:ss` and are inclusive.
- Omitting both time parameters returns all stored points for the selected metric.
- Points are ordered by `time` ascending.
- Telemetry is persisted in MySQL tables `iot_telemetry_raw` and `iot_metric_data`.

### 5.3 Mock Telemetry Report / HTTP 模拟上报

```text
POST /api/iot/telemetry/mock-report
Status: implemented
Used by: backend/frontend testing fallback
```

Request body:

```json
{
  "projectCode": "power-monitor",
  "deviceCode": "PM-001",
  "reportTime": "2026-07-10 10:45:00",
  "metrics": {
    "voltage": 220.3,
    "current": 0.42,
    "power": 92.5
  },
  "status": "online"
}
```

Response data:

```json
{
  "deviceCode": "PM-001",
  "reportTime": "2026-07-10 10:45:00",
  "metricCount": 3,
  "latestUpdated": true
}
```

## 6. Alert APIs / 告警接口

Alert status / 告警状态：

```text
OPEN / HANDLED / IGNORED
```

### 6.1 List Alerts / 告警列表

```text
GET /api/iot/devices/{deviceId}/alerts
Status: implemented
Used by: /iot/devices/:id
```

Query params:

| Name | Type | Required | Description |
|---|---|---|---|
| status | string | no | OPEN / HANDLED / IGNORED；不传返回全部 |
| metricKey | string | no | 指标键，例如 `power` |
| page | number | no | 页码，默认 1 |
| size | number | no | 每页条数，默认 20 |

Response:

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

Empty state / 空状态：`list` 为空数组。

Error state / 异常状态：

- 设备不存在：`code=404`，`msg=设备不存在`
- 非法 `status`：`code=400`，`msg=非法的告警状态`

### 6.2 Handle Alert / 处理告警

```text
POST /api/iot/alerts/{alertId}/handle
Status: implemented
```

Request body:

```json
{
  "status": "HANDLED"
}
```

`status` 只允许 `HANDLED` 或 `IGNORED`。

Response:

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

Error state / 异常状态：

- 告警不存在：`code=404`
- 非 `OPEN` 告警不可处理：`code=400`，`msg=只能处理 OPEN 状态的告警`
- 非法 `status`：`code=400`

## 7. Recommendation APIs / 建议接口

Recommendation status / 建议状态：

```text
PENDING / CONFIRMED / IGNORED
```

### 7.1 List Recommendations / 建议列表

```text
GET /api/iot/devices/{deviceId}/recommendations
Status: implemented
Used by: /iot/devices/:id
```

Query params:

| Name | Type | Required | Description |
|---|---|---|---|
| status | string | no | PENDING / CONFIRMED / IGNORED；不传返回全部 |
| page | number | no | 页码，默认 1 |
| size | number | no | 每页条数，默认 20 |

Response:

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

### 7.2 Confirm Recommendation / 确认建议

```text
POST /api/iot/recommendations/{recommendationId}/confirm
Status: implemented
```

Response:

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

### 7.3 Ignore Recommendation / 忽略建议

```text
POST /api/iot/recommendations/{recommendationId}/ignore
Status: implemented
```

Response:

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "status": "IGNORED"
  }
}
```

Error state / 异常状态：

- 建议不存在：`code=404`
- 非 `PENDING` 建议不可操作：`code=400`

## 8. Command APIs / 指令接口

Command status / 指令状态：

```text
PENDING / SENT / ACKED / FAILED / TIMEOUT
```

Week 4 only supports / 本周唯一指令：

```text
SET_SAMPLE_INTERVAL，参数固定为 intervalSeconds=5
```

ACK timeout / ACK 超时时间：

```text
10 秒
```

### 8.1 Send Command / 下发指令

```text
POST /api/iot/devices/{deviceId}/commands
Status: implemented
Used by: /iot/devices/:id
```

Request body:

```json
{
  "command": "SET_SAMPLE_INTERVAL",
  "params": {
    "intervalSeconds": 5
  }
}
```

Parameter validation / 参数校验：

- `command` 必须为 `SET_SAMPLE_INTERVAL`。
- `params.intervalSeconds` 必须为 `5`。

Response:

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

Error state / 异常状态：

- 非法 `command`：`code=400`，`msg=第四周仅支持 SET_SAMPLE_INTERVAL`
- 非法参数：`code=400`，`msg=intervalSeconds 必须为 5`
- 设备不存在：`code=404`

### 8.2 Get Command / 指令详情

```text
GET /api/iot/commands/{commandId}
Status: implemented
```

Response:

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

### 8.3 List Commands / 指令记录

```text
GET /api/iot/devices/{deviceId}/commands
Status: implemented
```

Query params:

| Name | Type | Required | Description |
|---|---|---|---|
| status | string | no | PENDING / SENT / ACKED / FAILED / TIMEOUT |
| page | number | no | 页码，默认 1 |
| size | number | no | 每页条数，默认 20 |

Response data uses the same `{ total, list }` structure as alerts. Record fields are the same as `8.2 Get Command`.

## 9. Operation Log APIs / 操作日志接口

### 9.1 List Operation Logs / 操作日志列表

```text
GET /api/iot/devices/{deviceId}/operation-logs
Status: implemented
Used by: /iot/devices/:id
```

Query params:

| Name | Type | Required | Description |
|---|---|---|---|
| action | string | no | HANDLE_ALERT / CONFIRM_RECOMMENDATION / SEND_COMMAND / RECEIVE_ACK |
| targetType | string | no | ALERT / RECOMMENDATION / COMMAND |
| page | number | no | 页码，默认 1 |
| size | number | no | 每页条数，默认 20 |

Response:

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

`targetId` may be a numeric alert/recommendation ID or a string `commandId`.

Recorded actions / 记录行为：

```text
HANDLE_ALERT / CONFIRM_RECOMMENDATION / SEND_COMMAND / RECEIVE_ACK
```

## 10. Change Log / 契约变更记录

| Date | Change | Owner | Impact |
|---|---|---|---|
| 2026-07-06 | Initial API contract draft | Codex | Frontend/backend initial alignment |
| 2026-07-14 | Freeze PM-001 alert/recommendation/command/log loop for Week 4 | Product | Alert/recommendation/command/log endpoints and statuses aligned with `week4-product-spec.md` |
