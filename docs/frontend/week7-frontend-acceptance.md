# Week 7 前端验收记录 / SSE 实时遥测推送最小切片

> 分支：`feature/week7-iot-frontend`  
> 基准：`dev`（commit d0ae9a5，已合入 week7 产品 PR）  
> 日期：2026-07-16  
> 状态：前端代码完成，等待后端 SSE PR 合入临时联调分支后联调

---

## 1. 改动文件

| 文件 | 动作 | 说明 |
|---|---|---|
| `frontend/src/api/sse.js` | 新增 | `DeviceTelemetrySSE` 统一封装 EventSource，管理 telemetry/heartbeat 事件、三态转换、降级计时 |
| `frontend/src/views/iot/Pm001Live.vue` | 修改 | 集成 SSE，新增连接状态指示器 UI，提取 `applyLatestPayload` 供 SSE 和轮询共用，修改 `isOnline` 优先信任后端 `status` |
| `frontend/src/api/__tests__/sse-test-node.mjs` | 新增 | Node.js Mock 测试脚本，覆盖 11 个场景共 23 个断言 |
| `docs/frontend/week7-frontend-acceptance.md` | 新增 | 本文档 |

---

## 2. SSE 事件字段映射

| 来源 | 字段 | 映射到 Vue 状态 | 说明 |
|---|---|---|---|
| SSE `telemetry` event | `deviceCode` | `latestData.deviceCode` | 固定 `PM-001` |
| | `deviceName` | `latestData.deviceName` | 后端返回 |
| | `projectName` | `latestData.projectName` | 后端返回 |
| | `status` | `latestData.status` | `ONLINE` / `OFFLINE`，由后端 15 秒规则计算 |
| | `reportTime` | `latestData.reportTime` | `yyyy-MM-dd HH:mm:ss` |
| | `metrics[].metricKey` | 指标卡片 key | `voltage` / `current` / `power` |
| | `metrics[].metricName` | 指标卡片名称 | 中文显示名 |
| | `metrics[].value` | 指标卡片数值 | 保留两位小数 |
| | `metrics[].unit` | 指标卡片单位 | `V` / `A` / `W` |
| SSE `heartbeat` event | `data.ts` | 不映射 | 仅维持连接，不刷新 UI |
| HTTP `GET /api/iot/devices/1/latest` | 同上结构 | 同上 | SSE 降级时由 `loadLatest()` 解析并调用 `applyLatestPayload` |

---

## 3. 如何运行

### 3.1 开发环境

```bash
cd frontend
npm run dev
```

打开浏览器访问 `http://localhost:5173/iot/pm001`，页面将自动：
1. 调用 `GET /api/iot/devices/1/latest` 获取初始数据
2. 建立 `EventSource` 连接 `/api/iot/public/devices/1/telemetry/stream`

### 3.2 自动化测试

```bash
cd frontend
node src/api/__tests__/sse-test-node.mjs
```

预期结果：`23/23 通过`

### 3.3 生产构建

```bash
cd frontend
npm run build
```

预期结果：`vite build` 成功，exit code 0，约 2290 modules transformed。

---

## 4. 测试方法与预期结果

### 4.1 正常场景

| 场景 | 测试方法 | 预期结果 |
|---|---|---|
| 首次连接成功 | 刷新 `/iot/pm001`，观察状态条 | 状态条显示绿色圆点 + "实时推送中" |
| telemetry 连续推送 | 后端/MQTT 模拟器每 5 秒上报 | 最新卡片数值平滑更新，无闪烁；`lastUpdateTime` 刷新 |
| 重复事件 | 模拟器在同一秒内重复上报 | 页面接收并展示最新值（后端按实际落库推送） |

### 4.2 异常与降级场景

| 场景 | 测试方法 | 预期结果 |
|---|---|---|
| 非法 JSON | 在 Mock 测试中注入 `not-json{{` | `console.error` 输出解析失败，页面数据不被清空，保留上一次有效值 |
| SSE 断线 | 关闭后端服务或断开网络 | 状态条变为黄色圆点 + "SSE 重连中…"；约 10 秒后变为灰色圆点 + "已降级为轮询" |
| 自动重连 | 恢复后端服务或网络 | 状态条恢复 "实时推送中"，轮询停止 |
| 轮询降级 | 在断线 10 秒后观察 Network 面板 | 恢复 `GET /api/iot/devices/1/latest` 每 5 秒请求 |
| SSE 恢复后停止轮询 | 恢复网络后观察 Network 面板 | latest 轮询请求停止，不再重复请求 |
| 页面卸载资源清理 | 从 `/iot/pm001` 跳转到其他页面再返回 | EventSource 关闭，无残留连接；重新进入页面后重新建立 SSE |
| 后端关闭时仍显示最后数据 | 断开 SSE 后查看页面 | 最新卡片仍显示最后收到的 telemetry 数据，不显示空状态 |
| 心跳不影响数据 | 后端发送 `event: heartbeat` | 页面状态、卡片数值、`reportTime` 均不变化 |

### 4.3 边界场景

| 场景 | 预期结果 |
|---|---|
| 页面加载时 SSE 即不可用 | 先显示 `loadLatest()` 初始数据，10 秒后降级为轮询 |
| SSE 推送时轮询正在进行 | `telemetry` 事件通过 `applyLatestPayload` 覆盖数据；下一次轮询结果再覆盖（无数据竞争风险） |
| `status` 字段为 `OFFLINE` | 设备状态标签显示灰色"离线"，不再由前端根据 reportTime 计算 |

---

## 5. 契约影响

### 5.1 新增契约

- `GET /api/iot/public/devices/1/telemetry/stream`
- `Content-Type: text/event-stream`
- 事件名：`telemetry`、`heartbeat`
- `telemetry` data 结构与 `GET /api/iot/devices/{id}/latest` 完全一致

### 5.2 不变契约

| 接口 | 说明 |
|---|---|
| `GET /api/iot/devices/1/latest` | 保留，作为降级轮询和第三方调用入口 |
| `GET /api/iot/devices/1/metrics/history` | 历史曲线继续使用，不走 SSE |
| `GET /api/iot/devices/1/alerts` | 告警继续使用轮询 |
| `GET /api/iot/devices/1/recommendations` | 建议继续使用轮询 |
| `POST/GET /api/iot/devices/1/commands` | 指令继续使用 HTTP |
| `GET /api/iot/devices/1/operation-logs` | 日志继续使用轮询 |
| MQTT Topic / Payload | 不变 |
| 设备在线状态 15 秒规则 | 不变，由后端计算 |

### 5.3 前端行为变更

- `isOnline` 优先信任后端 `status` 字段，不再主动根据 `reportTime` 计算
- `/iot/pm001` 页面初始化时同时启动 SSE 和首次 `loadLatest()`
- 联调说明文字已更新，加入 SSE 端点信息

---

## 6. 已知风险与需后端配合内容

### 6.1 需后端配合（必须在联调前就绪）

| 项 | 说明 |
|---|---|
| SSE 端点实现 | `GET /api/iot/public/devices/1/telemetry/stream` 需返回 `text/event-stream` |
| telemetry 事件触发 | 后端收到 MQTT `iot/power-monitor/PM-001/telemetry` 并落库后，需向所有订阅连接推送 `event: telemetry` |
| data 字段一致性 | `telemetry` data 必须与 `GET /api/iot/devices/1/latest` 返回的 data 字段一一对应 |
| status 计算 | SSE 推送前需按 15 秒规则计算 `ONLINE`/`OFFLINE` |
| 心跳（可选） | 服务端可在无 telemetry 时发送 `event: heartbeat`，但前端已忽略 |
| CORS / 公开接口 | 该端点为公开接口，不校验登录态，需确保前端可跨域或同域访问 |

### 6.2 已知风险

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| EventSource 浏览器兼容性 | IE 不支持，Edge/Chrome/Firefox/Safari 均支持 | 项目目标浏览器为现代浏览器，已确认可用 |
| 同时存在 SSE + latest 轮询 | 在 SSE 降级边界可能出现短时间内的重复请求 | 10 秒降级延时 + `stopLatestPoll()` 在 `CONNECTED` 时立即执行，已最小化重叠 |
| 首次加载数据与 SSE 推送竞争 | 页面加载时 `loadLatest()` 与 SSE 推送可能几乎同时到达 | `applyLatestPayload` 统一更新，后到的数据自然覆盖，无状态不一致风险 |
| 后端 SSE 未实现时 | 前端会尝试连接并 10 秒后降级为轮询，用户仍可正常使用 | 符合降级设计预期 |

---

## 7. PR 信息

- **Branch**: `feature/week7-iot-frontend`
- **Base**: `dev`
- **禁止**: 在后台 SSE PR 合入临时联调分支前，请勿合并本 PR
- **Commit 范围**: `frontend/src/api/sse.js`、`frontend/src/views/iot/Pm001Live.vue`、`frontend/src/api/__tests__/sse-test-node.mjs`、本文档
