# Week 4 Backend Delivery / 第四周后端交付说明

## 1. Goal / 目标

Complete the PM-001 operations loop:

```text
telemetry -> power alert -> recommendation -> command -> ACK -> operation log
```

中文：在第三周遥测持久化基础上，完成 PM-001 告警、建议、固定控制指令、ACK 回执与操作日志闭环。

## 2. Implemented Scope / 已实现范围

- `power > 100W` creates a `WARNING / OPEN` alert.
- Repeated high-power reports update the existing open alert instead of creating duplicates.
- A new alert creates one `PENDING` rule recommendation.
- Alert transitions: `OPEN -> HANDLED / IGNORED`.
- Recommendation transitions: `PENDING -> CONFIRMED / IGNORED`.
- The only command is `SET_SAMPLE_INTERVAL` with `intervalSeconds=5`.
- Command transitions: `PENDING -> SENT -> ACKED / FAILED / TIMEOUT`.
- Commands are published to `iot/power-monitor/PM-001/command`.
- ACK is consumed from `iot/power-monitor/PM-001/ack`.
- `SENT` commands become `TIMEOUT` after 10 seconds; late ACK does not change them.
- Operation logs cover alert handling, recommendation confirmation, command sending, and ACK receipt.

## 3. Database / 数据库

Flyway migration:

```text
backend/src/main/resources/db/migration/V4__create_iot_alert_command_log_tables.sql
```

Tables:

- `iot_alert_record`
- `iot_recommendation`
- `iot_command_record`
- `iot_operation_log`

## 4. APIs / 接口

```text
GET  /api/iot/devices/{deviceId}/alerts
POST /api/iot/alerts/{alertId}/handle
GET  /api/iot/devices/{deviceId}/recommendations
POST /api/iot/recommendations/{recommendationId}/confirm
POST /api/iot/recommendations/{recommendationId}/ignore
POST /api/iot/devices/{deviceId}/commands
GET  /api/iot/commands/{commandId}
GET  /api/iot/devices/{deviceId}/commands
GET  /api/iot/devices/{deviceId}/operation-logs
```

All responses use `Result<T>` with `code/msg/data`. Contract errors use business codes `400`, `404`, or `409` inside the response body.

## 5. Run / 运行

Prepare MySQL credentials and start the broker first:

```text
cd backend
mvn spring-boot:run
```

MQTT settings can be overridden with Spring configuration. Defaults:

```text
broker:  tcp://127.0.0.1:1883
command: iot/power-monitor/PM-001/command
ack:     iot/power-monitor/PM-001/ack
```

Simulator fallback:

```text
python scripts/start_mqtt_broker.py
python scripts/pm001_simulator.py
```

## 6. Verification / 验证

```text
cd backend
mvn test
mvn -DskipTests package
```

Automated coverage includes:

- alert threshold and deduplication
- recommendation generation
- fixed command validation
- command publish state transition
- ACK success and late ACK rejection
- 10-second timeout cutoff
- V4 migration table creation
- full HTTP -> MyBatis -> H2 -> ACK -> log integration loop

## 7. Coordination / 联调交接

Frontend should use the endpoints and exact status values in `04_API_CONTRACT.md`. The simulator now consumes `command` rather than the old `action` field and returns `ACKED/FAILED` using the frozen Week 4 payload.

The backend keeps HTTP telemetry reporting as a test fallback. Real integration should still verify MySQL, the local broker, simulator command receipt, ACK receipt, and the frontend status refresh.

## 8. Out of Scope / 不在本周范围

- multiple devices
- custom alert rules
- arbitrary commands
- notification channels
- automatic AI control
- WebSocket or SSE
