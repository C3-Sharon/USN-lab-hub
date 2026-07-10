# Week 1 Backend Plan / 第一周后端计划

## Goal / 目标

第一周后端不追求完整 IoT 能力，而是先验证团队能按契约协作：

```text
API_CONTRACT -> backend mock API -> testable JSON -> frontend mock/integration
```

## Scope / 范围

本周后端只交付：

- 现有后端结构分析
- IoT 模块落点建议
- IoT 数据库草案
- PM-001 最新数据 mock 接口
- curl/Swagger 测试方法

本周暂不做：

- MQTT Broker 接入
- 真实硬件上报
- 完整设备 CRUD
- Redis latest 缓存
- 告警规则引擎
- Flyway 正式建表

## Existing Backend Findings / 现有后端观察

- 主包名：`com.usn.labhub.user`
- 现有接口主要位于 `controller`
- 现有统一返回结构为 `Result<T>`，字段是 `code/msg/data`
- JWT 拦截器当前只拦截 `/usnhub/**`
- 第一周 mock 接口使用 `/api/iot/**`，默认不被 JWT 拦截，便于前后端联调
- 数据库迁移使用 Flyway，但当前 `application.yml` 中 `spring.flyway.enabled=false`

## Package Plan / 包规划

第一周采用最小侵入方式：

```text
backend/src/main/java/com/usn/labhub/user/controller/iot
backend/src/main/java/com/usn/labhub/user/domain/vo/iot
```

后续正式实现时再扩展：

```text
backend/src/main/java/com/usn/labhub/user/domain/dto/iot
backend/src/main/java/com/usn/labhub/user/domain/entity/iot
backend/src/main/java/com/usn/labhub/user/mapper/iot
backend/src/main/java/com/usn/labhub/user/service/iot
backend/src/main/resources/mapper/iot
```

## Week 1 Mock API / 第一周 Mock 接口

```text
GET /api/iot/devices/1/latest
```

Purpose / 用途：

- 给前端 PM-001 设备详情页提供稳定 mock 数据
- 验证 API 契约中的 latest data 字段是否可用
- 不依赖数据库、Redis、MQTT 或真实硬件

Expected data:

```text
deviceCode = PM-001
deviceName = 实验室功耗监测仪 #1
projectName = 实验室功耗监测
status = ONLINE
reportTime = 2026-07-09 20:00:00
voltage = 220.3 V
current = 0.42 A
power = 92.5 W
```

Current data source / 当前数据来源：

- 第一周接口返回的是后端代码内置 mock 数据。
- 当前不从数据库读取。
- 当前不消费 MQTT。
- 当前不依赖 `pm001_simulator.py`。
- 第二周目标是让模拟器或真实硬件上报到 `iot/power-monitor/PM-001/telemetry`，后端订阅后写入数据库，再由本接口读取最新数据。
- MQTT 接入后，在线状态按最近 15 秒是否有上报判断：`reportTime` 距当前时间不超过 15 秒为 `ONLINE`，否则为 `OFFLINE`。

## Test / 测试

Start backend:

```bash
cd backend
mvnw.cmd spring-boot:run
```

Call API:

```bash
curl http://localhost:8080/api/iot/devices/1/latest
```

Expected response shape:

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
    "reportTime": "2026-07-09 20:00:00",
    "metrics": [
      {"metricKey": "voltage", "metricName": "电压", "value": 220.3, "unit": "V"},
      {"metricKey": "current", "metricName": "电流", "value": 0.42, "unit": "A"},
      {"metricKey": "power", "metricName": "功率", "value": 92.5, "unit": "W"}
    ]
  }
}
```

## Contract Impact / 契约影响

- `04_API_CONTRACT.md` 的通用返回字段应使用当前项目真实字段 `msg`，不是 `message`
- `GET /api/iot/devices/{id}/latest` 状态更新为 `implemented`
- 产品确认字段以 `deviceCode/deviceName/projectName/status/reportTime/metrics` 为准
- MQTT Topic 暂定并统一为 `iot/power-monitor/PM-001/telemetry`
