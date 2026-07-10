# Week 2 产品冻结文档 / PM-001 第二周最小闭环

> 本文件由产品负责维护，作为第二周前后端和硬件组联调的唯一验收口径。
> 对应分支：`feature/week2-iot-product`

## 1. PM-001 第二周验收口径

第二周只验收一条链路是否跑通：

```text
pm001_simulator.py -> MQTT Broker -> 后端订阅/保存 -> latest 接口 -> 前端页面
```

验收标准共 5 条：

1. 后端能收到 PM-001 模拟器上报的数据。
2. 后端 `GET /api/iot/devices/1/latest` 返回最近一次上报值，而不是固定 mock。
3. 前端页面能看到 voltage / current / power 随模拟器变化。
4. 15 秒无新上报后，设备状态变为 `OFFLINE`。
5. README 或 docs 里包含完整启动步骤（Broker -> 后端 -> 模拟器 -> 前端）。

第二周不验收：告警、指令下发、历史曲线、设备 CRUD、多设备支持。

## 2. MQTT Payload 最终字段

Topic（已冻结）：

```text
iot/power-monitor/PM-001/telemetry
```

Payload 字段（第二周最终版）：

```json
{
  "projectCode": "power-monitor",
  "deviceCode": "PM-001",
  "reportTime": "2026-07-10 14:30:00",
  "metrics": {
    "voltage": 220.3,
    "current": 0.42,
    "power": 92.5
  },
  "status": "online"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| projectCode | string | 是 | 项目编码，固定为 `power-monitor`，与 Topic 中的项目段保持一致 |
| deviceCode | string | 是 | 设备编号，固定为 `PM-001` |
| reportTime | string | 是 | 设备端上报时间，格式 `yyyy-MM-dd HH:mm:ss` |
| metrics | object | 是 | 指标键值对，第二周只包含 `voltage`、`current`、`power` |
| status | string | 否 | 设备自身状态，可选 `online`/`offline`/`alert`/`maintenance`；第二周后端以 `reportTime` 超时判断为准 |

指标单位：

| 指标 | 单位 | 示例 |
|---|---|---|
| voltage | V | 220.3 |
| current | A | 0.42 |
| power | W | 92.5 |

> 详细 MQTT 契约参见 `docs/agent-guides/05_MQTT_CONTRACT.md`。本文件只冻结第二周实际使用的字段，不扩展契约。

## 3. 在线/离线判断规则

规则已冻结：

- 设备最近一次上报时间 `reportTime` 距当前时间不超过 **15 秒**，状态显示为 `ONLINE`。
- 超过 **15 秒** 未收到新上报，状态显示为 `OFFLINE`。
- 判断逻辑由后端在 `latest` 接口中实时计算，前端直接展示后端返回的 `status` 字段。
- 第二周不引入额外心跳 Topic，也不依赖 `status` payload 字段做在线判断。

## 4. 第二周范围确认

产品明确：第二周只做 latest 数据链路，不做以下功能：

- 告警与告警规则（如 `power > 100W`）
- 指令下发与 ACK（如 `SET_SAMPLE_INTERVAL`）
- 历史曲线与数据查询
- 设备/项目的完整 CRUD
- 多设备接入
- 访客展示页

这些功能属于后续周计划，第二周若提前实现，需经产品确认不影响主线验收。

## 5. 演示脚本

答辩或周验收时，按以下顺序启动并演示：

### 5.1 启动 MQTT Broker

以 Mosquitto 为例：

```bash
# Windows
mosquitto -v

# 或使用 Docker
docker run -it --rm -p 1883:1883 -p 9001:9001 eclipse-mosquitto
```

### 5.2 启动后端

```bash
cd backend
mvnw.cmd spring-boot:run
```

确认后端日志输出 `Subscribed to topic: iot/power-monitor/PM-001/telemetry`。

### 5.3 启动前端

```bash
cd frontend
npm run dev
```

打开浏览器访问 `http://localhost:5173/iot/pm001`。

### 5.4 启动模拟器

```bash
python pm001_simulator.py
```

### 5.5 验证演示

1. 页面显示 PM-001 实时数据。
2. 电压/电流/功率数值随模拟器上报而变化。
3. 状态显示为 `在线`。
4. 停止模拟器，等待 15 秒后，页面状态变为 `离线`。
5. 重新启动模拟器，状态恢复为 `在线`，数值继续更新。

---

## 影响范围

- API 契约：不变，沿用 `GET /api/iot/devices/1/latest`。
- MQTT 契约：不变，Topic 和 payload 字段已冻结。
- 前端字段：不变，继续展示 `deviceCode`、`deviceName`、`projectName`、`status`、`reportTime`、`metrics`。

## 需要配合

- 后端：按本口径实现 MQTT 订阅、latest 数据源改造、15 秒离线判断。
- 前端：保持页面不变，3-5 秒轮询 latest 接口，展示空状态。
- 硬件组/模拟器：确保 `pm001_simulator.py` 按本文件 payload 字段和 Topic 上报。
