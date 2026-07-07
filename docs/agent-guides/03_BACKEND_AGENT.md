# Backend Agent Guide / 后端 Agent 指南

> 中文说明：本文件给后端负责人和其 agent 使用。后端实现必须服从现有 Spring Boot 项目结构、API 契约、MQTT 契约和最小纵向切片。

## 1. Role / 角色

You are the backend implementation agent for USN Lab Hub IoT.

中文角色：你负责数据库、API、MQTT 数据接入、告警、建议、指令下发、ACK 处理和操作日志。

## 2. Required Reading / 必读文件

Before coding, read:

- `00_SHARED_AGENTS.md`
- `03_BACKEND_AGENT.md`
- `04_API_CONTRACT.md`
- `05_MQTT_CONTRACT.md`
- `06_VERTICAL_SLICE_PLAN.md`
- `07_TESTING_AND_ACCEPTANCE.md`

## 3. Tech Stack Rule / 技术栈规则

- Use Spring Boot.
- Use MyBatis Plus.
- Use MySQL.
- Use Redis for latest values where needed.
- Use Flyway for database schema changes.
- Use existing `Result` response style if available.
- Use Swagger/OpenAPI annotations where practical.
- Do not introduce microservices in MVP.
- Do not introduce heavy rule engines in MVP.

中文：

- 沿用现有 Spring Boot + MyBatis Plus + MySQL + Redis + Flyway。
- 新增表结构必须通过 Flyway。
- MVP 不拆微服务，不引入重型规则引擎。

## 4. Backend Scope / 后端范围

Required MVP tables/modules:

- `lab_project`
- `iot_device`
- `iot_device_metric`
- `iot_telemetry_raw`
- `iot_metric_data`
- `iot_alert_record`
- `iot_recommendation`
- `iot_command_record`
- `iot_operation_log`

Required backend abilities:

- Project CRUD
- Device CRUD
- Latest telemetry query
- History telemetry query
- MQTT telemetry ingest
- Raw payload storage
- Metric parsing
- Alert generation
- Recommendation generation
- Command issuing
- Command ACK update
- Operation log query

## 5. API Rule / 接口规则

- All new IoT APIs must use `/api/iot/**`.
- Response JSON must match `04_API_CONTRACT.md`.
- If API fields change, update `04_API_CONTRACT.md` in the same change.
- Frontend needs must be considered before finalizing DTO/VO fields.

中文：

- 所有新增 IoT 接口统一 `/api/iot/**`。
- 返回字段必须匹配 API 契约。
- 改字段必须同步契约文档。

## 6. MQTT Rule / MQTT 规则

- Telemetry messages must be stored as raw payload first.
- Parsed metrics should be stored separately.
- Commands must include `commandId`.
- Command ACK must update command status.
- Device offline status should be inferred from `lastSeenAt` if needed.
- Keep a mock/simulator path for demo fallback.

中文：

- MQTT 上报先写 raw，再解析 metric。
- 指令必须有 `commandId`。
- ACK 必须更新指令状态。
- 保留模拟器或 mock 入口兜底。

## 7. Backend Definition of Done / 后端完成标准

A backend task is done only when:

- Flyway migration exists if schema changes.
- Controller/Service/Mapper layers are implemented as needed.
- Swagger or API examples are available.
- Response JSON matches the API contract.
- Test commands are provided.
- Key write actions create operation logs.

## 8. Required Verification / 必须输出测试方法

Every backend coding response must include:

```text
如何运行：
- cd backend
- mvn spring-boot:run

数据库准备：
- Flyway 脚本：
- 测试数据：

API 测试：
- curl 或 Apifox 请求：

预期返回：
- JSON 示例：

异常测试：
1. 参数缺失：
2. 设备不存在：
3. 重复编号：
4. 无权限或未登录：

契约影响：
- API_CONTRACT：是/否
- MQTT_CONTRACT：是/否
- 数据库表：是/否
```

## 9. Do Not / 禁止事项

- Do not create APIs without documenting them in `04_API_CONTRACT.md`.
- Do not change frontend-facing field names silently.
- Do not skip raw telemetry storage.
- Do not rely only on real hardware; keep test fallback.
- 不写无契约接口。
- 不偷偷改前端字段。
- 不跳过 raw 数据存储。
- 不只依赖真实硬件，必须有兜底测试方式。

