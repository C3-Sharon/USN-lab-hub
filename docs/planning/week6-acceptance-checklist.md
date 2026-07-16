# Week 6 Acceptance Checklist / 第六周最终验收清单

USN Lab Hub IoT · 一个半月 MVP 验收

---

## 1. 验收说明

本清单覆盖一个半月 MVP 的完整链路。每项验收必须给出操作、测试数据和预期结果。验收通过标准为：操作可复现、数据可核对、结果与预期一致。

验收环境：

```text
MQTT Broker: localhost:1883
MySQL: localhost:3306/usn_hub
后端: http://localhost:8080
前端: http://localhost:5173
模拟器: scripts/pm001_simulator.py
```

---

## 2. MQTT telemetry 上报

### 2.1 模拟器正常上报

| 项 | 内容 |
|---|---|
| 操作 | 启动 `python scripts/pm001_simulator.py` |
| 测试数据 | Topic: `iot/power-monitor/PM-001/telemetry`，Payload 包含 `projectCode`、`deviceCode`、`reportTime`、`metrics{voltage,current,power}`、`status` |
| 预期结果 | 后端控制台或日志显示收到消息；MySQL `iot_telemetry_raw` 表新增记录；`iot_metric_data` 表新增 voltage/current/power 三条记录 |

### 2.2 真实硬件上报（可选）

| 项 | 内容 |
|---|---|
| 操作 | 使用真实硬件按接入指南配置 Broker 和 Topic |
| 测试数据 | 同上 |
| 预期结果 | 与模拟器一致，数据落入 MySQL |

---

## 3. MySQL 数据落库

### 3.1 原始 payload 落库

| 项 | 内容 |
|---|---|
| 操作 | 模拟器上报一条 telemetry 后查询数据库 |
| 测试数据 | `SELECT * FROM iot_telemetry_raw ORDER BY id DESC LIMIT 1;` |
| 预期结果 | 可看到完整 JSON payload、`projectCode=power-monitor`、`deviceCode=PM-001`、`reportTime` 格式为 `yyyy-MM-dd HH:mm:ss` |

### 3.2 指标数据落库

| 项 | 内容 |
|---|---|
| 操作 | 查询 `iot_metric_data` 表 |
| 测试数据 | `SELECT metric_key, value, unit FROM iot_metric_data WHERE device_id = 1 ORDER BY report_time DESC LIMIT 3;` |
| 预期结果 | 返回 `voltage/V`、`current/A`、`power/W` 三条最新记录 |

---

## 4. latest 接口

### 4.1 正常返回

| 项 | 内容 |
|---|---|
| 操作 | 调用 `GET /api/iot/devices/1/metrics/latest` |
| 测试数据 | 无 |
| 预期结果 | 返回 `code=200`，`data` 包含 `deviceCode=PM-001`、`status=ONLINE`、`reportTime`、三项指标值 |

### 4.2 离线返回

| 项 | 内容 |
|---|---|
| 操作 | 停止模拟器 15 秒后调用 latest 接口 |
| 测试数据 | 无 |
| 预期结果 | `status=OFFLINE`，`reportTime` 为最后一次上报时间 |

---

## 5. history 接口

### 5.1 功率历史曲线

| 项 | 内容 |
|---|---|
| 操作 | 调用 `GET /api/iot/devices/1/metrics/history?metricKey=power&startTime=2026-07-15 10:00:00&endTime=2026-07-15 11:00:00` |
| 测试数据 | 时间范围包含最近上报 |
| 预期结果 | 返回 `code=200`，`data.metricKey=power`、`unit=W`、`points` 按时间升序排列，每个点包含 `time` 和 `value` |

### 5.2 切换电压/电流

| 项 | 内容 |
|---|---|
| 操作 | 将 `metricKey` 改为 `voltage` 或 `current` |
| 测试数据 | 同上 |
| 预期结果 | 返回对应指标的数据，单位分别为 `V` 或 `A` |

---

## 6. 告警与规则建议

### 6.1 触发告警

| 项 | 内容 |
|---|---|
| 操作 | 修改模拟器使 `power=110.5`，等待 5 - 10 秒 |
| 测试数据 | `{"metrics":{"voltage":220.1,"current":0.50,"power":110.5}}` |
| 预期结果 | `iot_alert_record` 新增一条 `level=WARNING`、`status=OPEN`、`metricKey=power` 的记录 |

### 6.2 告警去重

| 项 | 内容 |
|---|---|
| 操作 | 在告警仍为 OPEN 时，继续上报 `power=110.5` 多次 |
| 测试数据 | 同上 |
| 预期结果 | 同一设备、同一指标不会新增多条 OPEN 告警 |

### 6.3 生成建议

| 项 | 内容 |
|---|---|
| 操作 | 触发告警后查询 `iot_recommendation` 表或前端建议区 |
| 测试数据 | 无 |
| 预期结果 | 新增一条 `alertId` 关联告警、`status=PENDING` 的建议 |

### 6.4 确认/忽略建议

| 项 | 内容 |
|---|---|
| 操作 | 调用 `POST /api/iot/recommendations/{id}/confirm` 或 `/ignore` |
| 测试数据 | 无 |
| 预期结果 | 建议状态变为 `CONFIRMED` 或 `IGNORED`；只有 `PENDING` 建议可操作 |

---

## 7. 指令下发与 MQTT ACK

### 7.1 下发指令

| 项 | 内容 |
|---|---|
| 操作 | 调用 `POST /api/iot/devices/1/commands`，body `{"command":"SET_SAMPLE_INTERVAL","params":{"intervalSeconds":5}}` |
| 测试数据 | 同上 |
| 预期结果 | 返回 `commandId`，`status=PENDING`；MQTT `iot/power-monitor/PM-001/command` 收到 command 消息 |

### 7.2 指令状态变为 SENT

| 项 | 内容 |
|---|---|
| 操作 | 观察指令区或调用 `GET /api/iot/commands/{commandId}` |
| 测试数据 | 无 |
| 预期结果 | 指令状态变为 `SENT`，`sentAt` 有值 |

### 7.3 收到 ACK

| 项 | 内容 |
|---|---|
| 操作 | 等待模拟器回复 ack |
| 测试数据 | MQTT `iot/power-monitor/PM-001/ack`，Payload 包含相同 `commandId` 和 `status=ACKED` |
| 预期结果 | 指令状态变为 `ACKED`，`ackedAt` 有值 |

### 7.4 ACK 超时

| 项 | 内容 |
|---|---|
| 操作 | 停止模拟器 ack 回复能力，下发指令 |
| 测试数据 | 无 |
| 预期结果 | 10 秒后指令状态变为 `TIMEOUT` |

---

## 8. 操作日志

### 8.1 日志记录范围

| 项 | 内容 |
|---|---|
| 操作 | 完成告警处理、建议确认、指令下发、ACK 接收后查询 `iot_operation_log` |
| 测试数据 | `SELECT action, target_type, target_id, summary FROM iot_operation_log ORDER BY id DESC LIMIT 10;` |
| 预期结果 | 至少包含 `HANDLE_ALERT`、`CONFIRM_RECOMMENDATION`、`SEND_COMMAND`、`RECEIVE_ACK` 四类记录 |

---

## 9. 健康评分

### 9.1 在线无告警

| 项 | 内容 |
|---|---|
| 操作 | 模拟器在线上报，无 OPEN 告警 |
| 测试数据 | `power=92.5` |
| 预期结果 | 公开接口返回 `health.score=100`、`level=HEALTHY`、`reasons=[]` |

### 9.2 在线有 OPEN 告警

| 项 | 内容 |
|---|---|
| 操作 | 触发 power > 100W 告警后调用公开接口 |
| 测试数据 | `power=110.5` |
| 预期结果 | `health.score=70`、`level=ATTENTION`、`reasons=["存在未处理的功率告警"]` |

### 9.3 离线

| 项 | 内容 |
|---|---|
| 操作 | 停止模拟器 15 秒后调用公开接口 |
| 测试数据 | 无 |
| 预期结果 | `device.status=OFFLINE`、`health.score=60`、`level=ATTENTION`、`reasons=["设备离线或超过 15 秒未上报"]` |

---

## 10. 公开展示页

### 10.1 未登录访问

| 项 | 内容 |
|---|---|
| 操作 | 无痕窗口打开 `http://localhost:5173/iot/public` |
| 测试数据 | 无 |
| 预期结果 | 页面加载成功，展示项目、设备、指标、健康评分、功率趋势 |

### 10.2 白名单验证

| 项 | 内容 |
|---|---|
| 操作 | 打开浏览器开发者工具，查看 `/api/iot/public/projects/power-monitor` 响应 |
| 测试数据 | 无 |
| 预期结果 | 响应中不包含 `operatorId`、`operatorName`、`commandId`、`payload`、`broker`、`handledBy` 等敏感字段 |

---

## 11. 登录后总览页

### 11.1 摘要区展示

| 项 | 内容 |
|---|---|
| 操作 | 登录后打开 `http://localhost:5173/iot/overview` |
| 测试数据 | 无 |
| 预期结果 | 页面展示项目数、在线设备数、PM-001 健康评分、未处理告警提示、快捷入口 |

---

## 12. 集成与稳定性

### 12.1 完整闭环

| 项 | 内容 |
|---|---|
| 操作 | 按 `week6-demo-script.md` 完整执行一次 |
| 测试数据 | 模拟器功率从 92.5W 切换到 110.5W |
| 预期结果 | 告警、建议、指令、ACK、日志、健康评分、公开页全部按预期变化 |

### 12.2 页面刷新

| 项 | 内容 |
|---|---|
| 操作 | 在 `/iot/pm001` 和 `/iot/public` 多次刷新浏览器 |
| 测试数据 | 无 |
| 预期结果 | 数据正常加载，无 500 错误，无长时间白屏 |

---

## 13. 验收结论

| 验收人 | 日期 | 结论 |
|---|---|---|
| 产品 | | |
| 前端 | | |
| 后端 | | |

结论填写：`通过` / `不通过（说明原因）`。
