# Week 3 Backend Plan / 第三周后端计划

## Goal / 目标

Move the PM-001 telemetry path from week 2 in-memory storage to formal MySQL persistence and provide history data for the frontend chart.

```text
PM-001 MQTT/HTTP telemetry
  -> iot_telemetry_raw
  -> iot_metric_data
  -> latest API + history API
```

The original roadmap placed real MQTT ingest in week 3. The team completed that work in week 2, so week 3 advances the same vertical slice with persistence and history instead of repeating MQTT integration.

## Scope / 范围

- Add Flyway migration `V2__create_iot_telemetry_tables.sql`.
- Persist every successful raw payload in `iot_telemetry_raw`.
- Persist voltage/current/power as separate rows in `iot_metric_data`.
- Keep failed MQTT payloads as raw records with `parse_status=FAILED` when the database is available.
- Read `GET /api/iot/devices/{id}/latest` from MySQL.
- Implement `GET /api/iot/devices/{id}/metrics/history`.
- Keep the PM-001-only boundary for week 3.

Not included this week:

- Alert rules and recommendations
- Commands and ACK handling
- Operation logs
- Multiple device onboarding
- Redis latest cache
- WebSocket/SSE

## Database Preparation / 数据库准备

Create an empty database if it does not exist:

```sql
CREATE DATABASE usn_hub CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Set database environment variables when defaults are not suitable:

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/usn_hub?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='your-password'
$env:FLYWAY_ENABLED='true'
```

Start the backend:

```powershell
cd backend
mvn spring-boot:run
```

Flyway applies V1 for a new database and V2 for the telemetry tables. `baseline-on-migrate` allows an existing pre-Flyway student-management database to adopt the migrations safely.

## API Contract / 接口契约

Latest API remains unchanged:

```text
GET /api/iot/devices/1/latest
```

History API:

```text
GET /api/iot/devices/1/metrics/history?metricKey=power
GET /api/iot/devices/1/metrics/history?metricKey=power&startTime=2026-07-13%2009:00:00&endTime=2026-07-13%2010:00:00
```

History response:

```json
{
  "code": 200,
  "data": {
    "deviceId": 1,
    "metricKey": "power",
    "unit": "W",
    "points": [
      {"time": "2026-07-13 09:00:00", "value": 80.1},
      {"time": "2026-07-13 09:05:00", "value": 92.5}
    ]
  }
}
```

Rules:

- `metricKey` is required and accepts voltage/current/power.
- Optional start/end times use `yyyy-MM-dd HH:mm:ss` and are inclusive.
- `startTime` must not be after `endTime`.
- Points are returned in ascending time order.

## Verification / 验证

Automated tests without MySQL:

```powershell
cd backend
mvn -Dtest=IotTelemetryControllerTest test
mvn test
mvn -DskipTests package
```

Database checks after reporting one payload:

```sql
SELECT device_code, topic, parse_status, received_at
FROM iot_telemetry_raw
ORDER BY id DESC
LIMIT 1;

SELECT device_code, metric_key, metric_value, unit, reported_at
FROM iot_metric_data
ORDER BY id DESC
LIMIT 3;
```

Expected result: one successful raw row and three metric rows are inserted. The latest and history APIs return the same report values.

## Coordination / 协作交接

- Product confirms the default chart metric and default time range.
- Frontend consumes the history contract without renaming fields.
- Backend provides a repeatable report payload and database query evidence.
- Hardware continues using the week 2 MQTT topic and payload; no hardware protocol change is required.
