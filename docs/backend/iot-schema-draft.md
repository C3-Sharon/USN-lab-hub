# IoT Schema Draft / IoT 数据库表草案

> 第一周仅作为后端设计草案，不立即创建 Flyway 迁移。等产品字段、前端页面和硬件指标确认后，再整理为正式 SQL。

## 1. lab_project

实验室硬件项目表。

| Field | Type | Note |
|---|---|---|
| id | bigint | 主键 |
| project_code | varchar(64) | 项目编号，唯一 |
| project_name | varchar(100) | 项目名称 |
| description | varchar(500) | 项目说明 |
| owner_id | bigint | 项目负责人 |
| status | varchar(32) | ACTIVE / ARCHIVED |
| public_visible | tinyint | 是否公开展示 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

## 2. iot_device

设备资产表。

| Field | Type | Note |
|---|---|---|
| id | bigint | 主键 |
| project_id | bigint | 所属项目 |
| device_code | varchar(64) | 设备编号，唯一 |
| device_name | varchar(100) | 设备名称 |
| device_type | varchar(64) | POWER_METER 等 |
| protocol | varchar(32) | MQTT / HTTP |
| location | varchar(100) | 安装位置 |
| owner_id | bigint | 设备负责人 |
| status | varchar(32) | ONLINE / OFFLINE / ALERT / MAINTENANCE |
| last_seen_at | datetime | 最后上报时间 |
| description | varchar(500) | 设备说明 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

## 3. iot_device_metric

设备指标定义表。

| Field | Type | Note |
|---|---|---|
| id | bigint | 主键 |
| device_id | bigint | 设备 ID |
| metric_key | varchar(64) | voltage/current/power |
| metric_name | varchar(100) | 电压/电流/功率 |
| unit | varchar(32) | V/A/W |
| warning_threshold | decimal(18,4) | 告警阈值 |
| enabled | tinyint | 是否启用 |
| sort_order | int | 展示顺序 |

## 4. iot_telemetry_raw

原始 MQTT/HTTP 上报数据表。用于排错和追溯。

| Field | Type | Note |
|---|---|---|
| id | bigint | 主键 |
| device_code | varchar(64) | 设备编号 |
| topic | varchar(255) | MQTT topic |
| payload | text | 原始 JSON |
| parse_status | varchar(32) | SUCCESS / FAILED |
| error_message | varchar(500) | 解析错误 |
| received_at | datetime | 平台接收时间 |

## 5. iot_metric_data

解析后的结构化指标数据表。

| Field | Type | Note |
|---|---|---|
| id | bigint | 主键 |
| device_id | bigint | 设备 ID |
| device_code | varchar(64) | 稳定设备编号，供接入和 latest 查询使用 |
| metric_key | varchar(64) | 指标键 |
| metric_value | decimal(18,4) | 指标值 |
| unit | varchar(32) | 单位 |
| reported_at | datetime | 设备上报时间 |
| received_at | datetime | 平台接收时间 |

## 6. iot_alert_record

告警记录表。

| Field | Type | Note |
|---|---|---|
| id | bigint | 主键 |
| device_id | bigint | 设备 ID |
| metric_key | varchar(64) | 指标键 |
| level | varchar(32) | INFO / WARNING / CRITICAL |
| message | varchar(500) | 告警信息 |
| trigger_value | decimal(18,4) | 触发值 |
| threshold_value | decimal(18,4) | 阈值 |
| status | varchar(32) | OPEN / HANDLED / IGNORED |
| created_at | datetime | 创建时间 |
| handled_by | bigint | 处理人 |
| handled_at | datetime | 处理时间 |

## 7. iot_recommendation

规则建议表。

| Field | Type | Note |
|---|---|---|
| id | bigint | 主键 |
| source | varchar(32) | RULE / AGENT |
| device_id | bigint | 设备 ID |
| alert_id | bigint | 告警 ID |
| title | varchar(100) | 建议标题 |
| content | varchar(1000) | 建议内容 |
| status | varchar(32) | PENDING / CONFIRMED / IGNORED |
| created_at | datetime | 创建时间 |

## 8. iot_command_record

控制指令记录表。

| Field | Type | Note |
|---|---|---|
| id | bigint | 主键 |
| command_id | varchar(64) | 指令编号，全局唯一 |
| device_id | bigint | 设备 ID |
| command | varchar(100) | 本周固定为 `SET_SAMPLE_INTERVAL` |
| params_json | text | 指令参数，本周固定为 `{ "intervalSeconds": 5 }` |
| status | varchar(32) | PENDING / SENT / ACKED / FAILED / TIMEOUT |
| message | varchar(500) | ACK 或失败说明 |
| created_by | bigint | 创建人 |
| create_time | datetime | 创建时间 |
| sent_at | datetime | 发送时间 |
| acked_at | datetime | 回执时间 |

## 9. iot_operation_log

业务操作日志表。

| Field | Type | Note |
|---|---|---|
| id | bigint | 主键 |
| operator_id | bigint | 操作人；系统触发可记录为 0 |
| operator_name | varchar(100) | 操作人名称 |
| action | varchar(64) | HANDLE_ALERT / CONFIRM_RECOMMENDATION / SEND_COMMAND / RECEIVE_ACK |
| target_type | varchar(64) | ALERT / RECOMMENDATION / COMMAND |
| target_id | varchar(64) | 目标 ID；告警/建议用数值 ID，指令用 `commandId` |
| summary | varchar(500) | 摘要 |
| created_at | datetime | 创建时间 |

## First Formal Migration Suggestion / 后续正式迁移建议

正式 Flyway 迁移建议拆成：

```text
V2__create_iot_project_device_tables.sql
V3__create_iot_telemetry_tables.sql
V4__create_iot_alert_command_log_tables.sql
```

避免一次迁移过大，也方便前后端逐步联调。
