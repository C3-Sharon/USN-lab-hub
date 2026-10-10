# PM-001 功耗监测设备接入指南

> 本文件由产品/硬件组维护，描述 PM-001 功耗监测设备接入 USN Lab Hub 的技术要求。
> 第三周仍以 `scripts/pm001_simulator.py` 作为验证手段，真实硬件接入留到后续迭代。

## 设备信息

| 项 | 值 |
|---|---|
| 设备编号 | PM-001 |
| 设备名称 | 实验室功耗监测仪 #1 |
| 项目编码 | power-monitor |
| 项目名称 | 实验室功耗监测 |
| 通信协议 | MQTT over TCP |
| 默认上报频率 | 5 秒/次 |

## MQTT 连接参数

| 参数 | 说明 |
|---|---|
| Broker 地址 | 可配置，本地开发默认 `localhost:1883` |
| 客户端 ID | 建议 `pm001-<随机后缀>`，避免与模拟器冲突 |
| 用户名/密码 | 第三周暂不启用认证，后续按项目安全规范补充 |
| 连接超时 | 10 秒 |
| 保持连接 | 60 秒 |
| QoS | 0（第三周）/ 1（生产环境建议） |
| 遗愿消息 | 可选，Topic 与 telemetry 一致，payload `status` 为 `offline` |

## 上报 Topic

```text
iot/power-monitor/PM-001/telemetry
```

> 必须与 `docs/agent-guides/05_MQTT_CONTRACT.md` 保持一致。`docs/agent-guides/06_VERTICAL_SLICE_PLAN.md` 中历史遗留的 `usn/...` 写法已废弃。

## 上报 Payload

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

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| projectCode | string | 是 | 项目编码，固定 `power-monitor` |
| deviceCode | string | 是 | 设备编号，固定 `PM-001` |
| reportTime | string | 是 | 设备端产生数据的时间，格式 `yyyy-MM-dd HH:mm:ss` |
| metrics | object | 是 | 指标对象，至少包含 voltage、current、power |
| status | string | 否 | 设备自身状态，`online` / `offline` / `alert` / `maintenance` |

### 指标说明

| 指标键 | 指标名 | 单位 | 数据类型 | 典型范围 |
|---|---|---|---|---|
| voltage | 电压 | V | float | 200.0 - 240.0 |
| current | 电流 | A | float | 0.0 - 10.0 |
| power | 功率 | W | float | 0.0 - 2000.0 |

## 接入检查清单

真实硬件接入前，请确认：

- [ ] 硬件支持 MQTT 客户端或可通过网关转发。
- [ ] 已配置 Broker 地址和端口。
- [ ] 上报 Topic 严格为 `iot/power-monitor/PM-001/telemetry`。
- [ ] Payload 包含 `projectCode`、`deviceCode`、`reportTime`、`metrics`。
- [ ] `reportTime` 使用设备本地时间，格式为 `yyyy-MM-dd HH:mm:ss`。
- [ ] 上报频率稳定，建议 5 秒一次。
- [ ] 电压、电流、功率数值在合理物理范围内。

## 异常场景处理

| 异常 | 现象 | 处理建议 |
|---|---|---|
| 时间字段格式错误 | 后端解析失败，latest 接口无数据 | 统一使用 `yyyy-MM-dd HH:mm:ss` |
| 缺少 metrics 字段 | 指标无法解析 | 确保每次上报都包含三个指标 |
| 设备离线 | 15 秒无上报，页面状态变为 OFFLINE | 检查网络连接和 Broker 状态 |
| Topic 拼写错误 | 后端订阅不到消息 | 严格核对 `iot/power-monitor/PM-001/telemetry` |

## 开发期验证方式

第三周使用模拟器代替真实硬件：

```bash
python scripts/pm001_simulator.py
```

模拟器默认每 5 秒上报一次，可用于验证后端持久化、latest 接口和历史曲线。
