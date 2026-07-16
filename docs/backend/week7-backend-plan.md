# 第七周后端交付：PM-001 SSE 实时遥测

## 1. 本周目标

在不改变数据库和既有业务接口的前提下，将 PM-001 的 MQTT 遥测数据通过 SSE 主动推送给前端，并保留 `GET /api/iot/devices/1/latest` 作为断线降级接口。

## 2. 已实现范围

- 公开只读端点：`GET /api/iot/public/devices/1/telemetry/stream`
- 响应类型：`text/event-stream; charset=UTF-8`
- 新连接立即收到当前 latest 快照
- MQTT Topic `iot/power-monitor/PM-001/telemetry` 成功解析并落库后，广播 `telemetry` 事件
- `telemetry.data` 复用 latest 数据结构，只包含 `deviceId`、`deviceCode`、`deviceName`、`projectName`、`status`、`reportTime`、`metrics`
- `metrics` 只包含 `voltage`、`current`、`power`
- 默认每 10 秒发送一次 `heartbeat`，心跳不参与在线状态判断
- 最近一次上报超过 15 秒时，主动广播一次 `OFFLINE` 快照，保持离线期间不重复广播状态转换
- 断开的客户端会从订阅集合移除，单个客户端异常不影响 MQTT 消费和其他连接

## 3. 明确不做

- 不推送告警、建议、指令、日志或历史曲线
- 不接收任何 SSE 写操作
- 不扩展多设备订阅
- 不引入 WebSocket、Redis Pub/Sub 或新数据库表
- HTTP `mock-report` 不触发实时广播；正式实时链路以 MQTT 入库为准

## 4. 配置

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `IOT_SSE_HEARTBEAT_DELAY_MS` | `10000` | SSE 心跳间隔（毫秒） |
| `IOT_SSE_STATUS_CHECK_DELAY_MS` | `1000` | 离线状态检查间隔（毫秒） |

MQTT、MySQL 和既有指令超时配置保持第六周约定不变。

## 5. 本地验证

后端目录执行：

```bash
mvn clean test
mvn clean package
```

启动后端后保持 SSE 连接：

```bash
curl -N -H "Accept: text/event-stream" http://localhost:8080/api/iot/public/devices/1/telemetry/stream
```

再启动 `scripts/pm001_simulator.py` 或使用真实 PM-001 发布 MQTT telemetry。预期每次成功上报后出现一条 `event:telemetry`；停止上报超过 15 秒后出现一次 `status=OFFLINE` 的 telemetry。停止后端可供前端验证约 10 秒后的 latest 轮询降级。

## 6. 联调交接

前端只需依赖两条读取链路：

1. 实时：`GET /api/iot/public/devices/1/telemetry/stream`
2. 降级：`GET /api/iot/devices/1/latest`

产品验收时依次检查：初始快照、连续 MQTT 更新、心跳不改卡片、停止上报后的离线转换、后端断开后的轮询降级、恢复连接后的停止轮询。后端 PR 合入 `dev` 后，前端分支先同步最新 `dev` 再做总联调。

## 7. 测试覆盖

`IotWeek7SseIntegrationTest` 覆盖：

- 公开端点、SSE 响应头和异步连接
- 初始快照与 MQTT 入库后的第二次推送
- latest 字段白名单和三项指标顺序
- 敏感字段不出现在 SSE 数据中
- `ONLINE -> OFFLINE` 转换只广播一次
