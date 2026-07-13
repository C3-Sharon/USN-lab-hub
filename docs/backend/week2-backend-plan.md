# Week 2 Backend Plan / 第二周后端计划

## Goal / 目标

第二周后端目标是把第一周写死的 PM-001 latest mock，推进为“上报数据驱动”的 latest 接口：

```text
PM-001 telemetry -> backend ingest -> raw/metric storage -> latest API -> frontend refresh
```

## Scope / 范围

本周后端交付：

- 统一 PM-001 Topic：`iot/power-monitor/PM-001/telemetry`
- 支持 HTTP 遥测模拟上报：`POST /api/iot/telemetry/mock-report`
- 支持 MQTT 遥测订阅：`iot/power-monitor/PM-001/telemetry`
- 保存原始 payload 和解析后的 `voltage/current/power`
- `GET /api/iot/devices/1/latest` 返回最近一次上报
- `reportTime` 距当前时间 15 秒内为 `ONLINE`，否则为 `OFFLINE`
- 后端测试覆盖“上报后 latest 返回新值”

本周暂不做：

- 告警规则
- 指令下发
- 历史曲线
- 复杂设备管理
- 多设备接入
- 正式 MySQL/Flyway 持久化

## Current Storage / 当前保存方式

第二周为了优先跑通联调链路，后端使用内存保存：

- `IotTelemetryRawRecord`：原始 payload
- `IotMetricDataRecord`：解析后的指标记录

后续正式持久化时，再按 `docs/backend/iot-schema-draft.md` 迁移到：

- `iot_telemetry_raw`
- `iot_metric_data`

## HTTP Test Flow / HTTP 测试流程

Start backend:

```bash
cd backend
mvn spring-boot:run
```

Before telemetry arrives, latest returns `OFFLINE` with empty metric values:

```bash
curl http://localhost:8080/api/iot/devices/1/latest
```

Report telemetry:

```bash
curl -X POST http://localhost:8080/api/iot/telemetry/mock-report ^
  -H "Content-Type: application/json" ^
  -d "{\"projectCode\":\"power-monitor\",\"deviceCode\":\"PM-001\",\"reportTime\":\"2026-07-10 10:45:00\",\"metrics\":{\"voltage\":221.6,\"current\":0.45,\"power\":99.7},\"status\":\"online\"}"
```

Then latest should return the reported values:

```bash
curl http://localhost:8080/api/iot/devices/1/latest
```

## MQTT Test Flow / MQTT 测试流程

Install simulator dependencies once:

```bash
pip install -r scripts/requirements-iot.txt
```

Terminal 1, start broker:

```bash
python scripts/start_mqtt_broker.py
```

Terminal 2, start backend:

```bash
cd backend
mvn spring-boot:run
```

Terminal 3, start PM-001 simulator:

```bash
python scripts/pm001_simulator.py
```

Backend should subscribe:

```text
iot/power-monitor/PM-001/telemetry
```

Then latest should change every simulator report interval:

```bash
curl http://localhost:8080/api/iot/devices/1/latest
```

## Acceptance / 验收

- `mvn -Dtest=IotTelemetryControllerTest test` passes.
- `mvn -DskipTests package` passes.
- HTTP telemetry report can drive latest data.
- MQTT simulator can drive latest data when broker starts before backend.
- `reportTime` older than 15 seconds returns `OFFLINE`.
