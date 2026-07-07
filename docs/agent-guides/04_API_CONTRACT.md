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
  "message": "success",
  "data": {}
}
```

If the current project uses different field names, update this section before implementation.

如果现有 `Result` 结构与上述不一致，以现有项目为准，并在实现前更新此处。

## 2. Common Page Response / 通用分页结构

```json
{
  "code": 200,
  "message": "success",
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
Status: draft
Used by: /iot/devices/:id
```

Response data:

```json
{
  "deviceId": 1,
  "deviceCode": "PM-001",
  "status": "ONLINE",
  "reportedAt": "2026-07-06 20:05:00",
  "metrics": [
    {"metricKey": "voltage", "metricName": "电压", "value": 220.3, "unit": "V"},
    {"metricKey": "current", "metricName": "电流", "value": 0.42, "unit": "A"},
    {"metricKey": "power", "metricName": "功率", "value": 92.5, "unit": "W"}
  ]
}
```

### 5.2 Metric History / 历史数据

```text
GET /api/iot/devices/{id}/metrics/history
Status: draft
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

### 5.3 Mock Telemetry Report / HTTP 模拟上报

```text
POST /api/iot/telemetry/mock-report
Status: draft
Used by: backend/frontend testing fallback
```

Request body:

```json
{
  "deviceCode": "PM-001",
  "timestamp": 1783333800000,
  "metrics": {
    "voltage": 220.3,
    "current": 0.42,
    "power": 92.5
  },
  "status": "online"
}
```

## 6. Alert APIs / 告警接口

### 6.1 List Alerts / 告警列表

```text
GET /api/iot/alerts
Status: draft
Used by: /iot/alerts, /iot/devices/:id
```

Response data record:

```json
{
  "id": 1,
  "deviceId": 1,
  "deviceCode": "PM-001",
  "deviceName": "功耗检测设备 001",
  "metricKey": "power",
  "level": "WARNING",
  "message": "功率超过阈值",
  "triggerValue": 120.0,
  "thresholdValue": 100.0,
  "status": "OPEN",
  "createdAt": "2026-07-06 20:06:00"
}
```

### 6.2 Handle Alert / 处理告警

```text
POST /api/iot/alerts/{id}/handle
Status: draft
```

Request body:

```json
{
  "status": "HANDLED",
  "note": "已检查供电并下发采样周期调整指令"
}
```

## 7. Recommendation APIs / 建议接口

```text
GET /api/iot/recommendations
POST /api/iot/recommendations/{id}/confirm
Status: draft
```

Recommendation record:

```json
{
  "id": 1,
  "source": "RULE",
  "deviceId": 1,
  "alertId": 1,
  "title": "建议检查设备功耗",
  "content": "当前功率超过阈值，建议降低负载或检查供电。",
  "status": "PENDING",
  "createdAt": "2026-07-06 20:06:10"
}
```

## 8. Command APIs / 指令接口

### 8.1 Send Command / 下发指令

```text
POST /api/iot/devices/{id}/commands
Status: draft
Used by: /iot/devices/:id, /iot/commands
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

Response data:

```json
{
  "commandId": "CMD-20260706-0001",
  "deviceId": 1,
  "deviceCode": "PM-001",
  "command": "SET_SAMPLE_INTERVAL",
  "status": "SENT",
  "createdAt": "2026-07-06 20:07:00"
}
```

### 8.2 List Commands / 指令记录

```text
GET /api/iot/commands
Status: draft
```

Command status:

```text
PENDING / SENT / ACKED / FAILED / TIMEOUT
```

## 9. Operation Log APIs / 操作日志接口

```text
GET /api/iot/operation-logs
Status: draft
Used by: /iot/logs, /iot/devices/:id
```

Response data record:

```json
{
  "id": 1,
  "operatorId": 1,
  "operatorName": "管理员",
  "action": "SEND_COMMAND",
  "targetType": "DEVICE",
  "targetId": 1,
  "summary": "向 PM-001 下发 SET_SAMPLE_INTERVAL 指令",
  "createdAt": "2026-07-06 20:07:00"
}
```

## 10. Change Log / 契约变更记录

| Date | Change | Owner | Impact |
|---|---|---|---|
| 2026-07-06 | Initial API contract draft | Codex | Frontend/backend initial alignment |

