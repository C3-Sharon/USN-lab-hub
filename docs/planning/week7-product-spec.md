# Week 7 Product Spec / 第七周产品冻结文档

USN Lab Hub IoT · PM-001 SSE 实时遥测推送最小切片

主题：MQTT telemetry 到达后，后端主动通过 SSE 推送到 PM-001 页面，同时保留轮询降级。

---

## 1. 本周目标

把 `/iot/pm001` 页面现有的 5 秒 `latest` 轮询，升级为 SSE 实时推送；当 SSE 不可用时，前端自动降级到轮询。本周只冻结 telemetry 推送的最小切片，不扩展告警、指令、日志和历史曲线的实时推送。

---

## 2. 冻结范围

### 2.1 纳入本周

```text
SSE 端点：GET /api/iot/public/devices/1/telemetry/stream
Content-Type：text/event-stream
事件名：telemetry
data：复用 latest 数据结构（deviceId / deviceCode / deviceName / projectName / status / reportTime / metrics）
触发时机：连接建立时发送当前快照；MQTT telemetry 到达并写入后端后推送；在线转离线时补发一次
心跳：服务端可发送，但心跳不表示设备在线
前端三态：实时连接 / 重新连接 / 轮询降级
SSE 断线约 10 秒后恢复 latest 轮询
EventSource 自动重连成功后停止轮询
设备 OFFLINE 仍由后端 15 秒无上报规则判定
SSE 只读公开数据，仅返回 PM-001 白名单字段
```

### 2.2 不纳入本周

```text
通过 SSE 推送告警、建议、指令、日志
通过 SSE 下发控制指令或修改设备配置
多设备 SSE 推送
历史曲线实时推送
WebSocket 方案
用户身份鉴权与连接限流（仅使用公开端点）
```

---

## 3. SSE 端点契约

### 3.1 基本定义

```text
GET /api/iot/public/devices/1/telemetry/stream
Content-Type: text/event-stream; charset=UTF-8
Cache-Control: no-cache
Connection: keep-alive
事件名：telemetry
```

### 3.2 事件格式

服务端发送的事件遵循 SSE 规范，事件名为 `telemetry`，`data` 字段为 JSON 字符串。

```text
event: telemetry
data: {"deviceId":1,"deviceCode":"PM-001","deviceName":"实验室功耗监测仪 #1","projectName":"实验室功耗监测","status":"ONLINE","reportTime":"2026-07-16 20:00:00","metrics":[{"metricKey":"voltage","metricName":"电压","value":220.3,"unit":"V"},{"metricKey":"current","metricName":"电流","value":0.42,"unit":"A"},{"metricKey":"power","metricName":"功率","value":92.5,"unit":"W"}]}

```

### 3.3 data 字段结构

`data` 直接使用 `GET /api/iot/devices/{id}/latest` 的数据结构：

```json
{
  "deviceId": 1,
  "deviceCode": "PM-001",
  "deviceName": "实验室功耗监测仪 #1",
  "projectName": "实验室功耗监测",
  "status": "ONLINE",
  "reportTime": "2026-07-16 20:00:00",
  "metrics": [
    {"metricKey": "voltage", "metricName": "电压", "value": 220.3, "unit": "V"},
    {"metricKey": "current", "metricName": "电流", "value": 0.42, "unit": "A"},
    {"metricKey": "power", "metricName": "功率", "value": 92.5, "unit": "W"}
  ]
}
```

字段冻结：

- `deviceId`：固定为 `1`。
- `deviceCode`：固定为 `PM-001`。
- `deviceName`、`projectName`：与 latest 接口保持一致。
- `status`：`ONLINE` 或 `OFFLINE`，由后端 15 秒规则计算。
- `reportTime`：格式 `yyyy-MM-dd HH:mm:ss`，使用本次 telemetry 的 `reportTime`。
- `metrics`：只包含 `voltage`、`current`、`power`，单位和名称不变。

### 3.4 心跳事件

服务端可在无 telemetry 时发送心跳以维持连接，心跳事件不使用 `telemetry` 事件名。推荐格式：

```text
event: heartbeat
data: {"ts":"2026-07-16 20:00:05"}

```

心跳仅用于保持 TCP/HTTP 连接活跃，**不代表设备在线**，前端不得用心跳刷新 `status` 或 `reportTime`。

### 3.5 触发时机

后端在以下动作完成后，向所有订阅该设备的 SSE 连接推送 `telemetry` 事件：

1. 收到 MQTT `iot/power-monitor/PM-001/telemetry` 消息。
2. 解析并持久化原始 payload 与指标数据。
3. 计算设备在线状态（15 秒规则）。
4. 组装 latest 结构。

连接建立时先发送一条当前 latest 快照。若之后 15 秒内没有新上报、状态由 `ONLINE` 转为 `OFFLINE`，后端使用同一 `telemetry` 结构主动补发一次离线快照；保持离线期间不重复推送。

同一 telemetry 只推送一次；如果同一秒存在重复上报，后端按实际落库结果推送。

---

## 4. 前端状态与降级策略

### 4.1 三种连接状态

页面顶部或最新卡片附近必须展示以下三种状态之一：

| 状态 | 文案建议 | 视觉提示 |
|---|---|---|
| 实时连接 | 实时推送中 | 绿色圆点 + "实时" 标签 |
| 重新连接 | SSE 重连中… | 黄色圆点 + 重连倒计时或旋转图标 |
| 轮询降级 | 已降级为轮询 | 灰色/橙色圆点 + "轮询" 标签 |

### 4.2 状态转换

```text
页面初始化
    │
    ▼
尝试建立 EventSource ──成功──> 实时连接
    │                              │
    │                              ▼
    │                        收到 telemetry 事件 ──> 刷新最新卡片
    │                              │
    │                              ▼
    │                        连接异常 / onerror ──> 重新连接
    │                              │
    │                              ▼
    │                        EventSource 自动重连成功 ──> 回到实时连接
    │                              │
    │                              ▼
    │                        重连持续超过约 10 秒仍未成功 ──> 轮询降级
    │                                                            │
    │                                                            ▼
    │                                                   恢复 5 秒 latest 轮询
    │                                                            │
    │                                                            ▼
    └───────────────── EventSource 恢复成功 ─────── 停止轮询，回到实时连接
```

### 4.3 降级计时规则

- SSE 连接异常后，EventSource 会自动尝试重连。
- 从首次 `onerror` 开始计时，约 10 秒内若未恢复 `onopen`，则启动 `latest` 轮询。
- 轮询间隔保持原 5 秒。
- 一旦 EventSource 重新 `onopen`，立即停止轮询并切换回实时连接状态。
- 页面卸载时关闭 EventSource 并清除轮询定时器。

### 4.4 最新卡片更新逻辑

- SSE 实时连接状态下，最新卡片仅由 `telemetry` 事件更新。
- 轮询降级状态下，最新卡片由 `GET /api/iot/devices/1/latest` 轮询更新。
- 从 SSE 恢复为轮询、或从轮询恢复为 SSE 时，不需要额外全量刷新，下一次事件/轮询结果自然覆盖。

---

## 5. 安全与权限

- 该端点属于公开接口，不校验登录态。
- SSE 只返回设备基本遥测信息，不得包含：
  - 用户隐私（ownerId、ownerName、操作人）
  - 原始 MQTT payload
  - 内部操作日志
  - 告警处理人信息
  - 指令控制入口
  - MQTT Broker 凭证
- 不允许通过 SSE 接收任何写指令或控制参数。
- 本周仅服务固定设备 `PM-001`（deviceId=1）。

---

## 6. 验收标准

### 6.1 后端验收

- `GET /api/iot/public/devices/1/telemetry/stream` 返回 `text/event-stream`。
- 使用 `curl` 或浏览器 EventSource 可建立连接。
- MQTT simulator 每 5 秒上报一次，SSE 约每 5 秒收到一次 `telemetry` 事件。
- `telemetry` 事件 data 字段与 `GET /api/iot/devices/1/latest` 返回的 data 结构一致。
- 心跳事件不影响前端状态与卡片内容。
- 断开 simulator 超过 15 秒后，SSE 主动收到一条 `status=OFFLINE` 的 `telemetry` 事件，且保持离线期间不重复发送该转换事件。

### 6.2 前端验收

- `/iot/pm001` 页面加载后自动连接 SSE。
- 状态指示器正确显示：实时连接 / 重新连接 / 轮询降级。
- SSE 推送到达时，最新卡片无闪烁刷新，数值平滑更新。
- 关闭后端或断开网络约 10 秒后，状态变为轮询降级并恢复 5 秒轮询。
- 恢复后端或网络后，状态恢复实时连接，轮询停止。
- 刷新页面后行为一致。

### 6.3 产品验收

- `week7-product-spec.md` 与 `04_API_CONTRACT.md` 中 SSE 契约一致。
- 本周未通过 SSE 引入告警、指令、日志等额外实时数据。
- 公开接口白名单与第五周 `/api/iot/public/projects/power-monitor` 的安全约束保持一致。

---

## 7. 对现有契约的影响

- `GET /api/iot/devices/1/latest` 继续保留，作为降级轮询和第三方调用入口。
- MQTT telemetry payload 与 Topic 不变。
- 设备在线状态规则不变（15 秒）。
- 健康评分、告警、建议、指令、日志、历史曲线接口本周均不改变。
- 新增公开 SSE 端点需在 `04_API_CONTRACT.md` 中登记。

---

## 8. 变更记录

| Date | Change | Owner |
|---|---|---|
| 2026-07-16 | 冻结第七周 PM-001 SSE 实时遥测推送最小切片 | Product |
