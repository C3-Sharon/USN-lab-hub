# Week 6 Frontend Acceptance / 第六周前端验收报告

## 1. 验收基线

- 分支：`feature/week6-iot-frontend`
- 目标分支：`dev`
- 页面：`/iot/public`、`/iot/overview`、`/iot/pm001`
- 数据模式：真实接口，`frontend/src/api/iot.js` 中 `USE_MOCK=false`
- 第六周范围：只做稳定性验收，不新增业务功能、不修改 API/MQTT 契约

前端分支已同步第五周前后端成果和第六周产品文档。同步时发生冲突的
`iot.js`、`Overview.vue`、`PublicDisplay.vue` 均以最新 `dev` 中已完成契约联调的版本为准。

## 2. 页面与接口

| 页面 | 权限 | 主要接口 |
|---|---|---|
| `/iot/public` | 未登录可访问 | `GET /api/iot/public/projects/power-monitor` |
| `/iot/overview` | 登录后访问 | 公开项目聚合接口、告警接口 |
| `/iot/pm001` | 登录后访问 | latest、history、告警、建议、指令、日志接口 |

公开页和总览页直接读取后端返回的：

```text
projectCode / projectName / status
device.status / device.reportTime / device.metrics / device.health
powerTrend / updatedAt
```

健康评分和设备在线状态均以后端结果为准，前端不重复计算业务规则。

## 3. 静态验收

- `USE_MOCK=false`，不会用本地 mock 掩盖接口问题。
- `/iot/public` 对未登录和已登录用户均保持公开。
- `/iot/overview`、`/iot/pm001` 仍需登录。
- 页面包含 loading、空数据、离线、错误和重试状态。
- 公开页没有管理入口、控制按钮、MQTT 凭证或操作日志。
- API 调用统一位于 `frontend/src/api/iot.js`。
- 第四周 PM-001 告警、建议、指令和日志页面未被第六周重写。

## 4. 构建验证

在项目根目录执行：

```bash
cd frontend
npm ci
npm run build
```

验收要求：

- 构建退出码为 0。
- 不出现 Vue 模板、导入或类型错误。
- 若只有现有 chunk 体积警告，记录为非阻塞风险，不在第六周重构。

## 5. 真实接口验收步骤

启动顺序：

```text
MQTT Broker -> MySQL -> 后端 -> PM-001 模拟器/硬件 -> 前端
```

1. 确认 `GET /actuator/health` 返回 `UP`。
2. 启动 PM-001 模拟器，确认每 5 秒上报 telemetry。
3. 无痕窗口访问 `/iot/public`，核对项目、指标、趋势和健康评分。
4. 登录后访问 `/iot/overview`，核对项目数、在线设备数和健康摘要。
5. 进入 `/iot/pm001`，核对 latest 和 history。
6. 上报 `power=110.5`，确认出现 OPEN WARNING 和 PENDING 建议。
7. 确认建议并下发 `SET_SAMPLE_INTERVAL`，确认最终状态为 `ACKED`。
8. 核对操作日志包含 `SEND_COMMAND` 和 `RECEIVE_ACK`。
9. 返回公开页，确认健康评分变为 `ATTENTION`。

## 6. 状态场景

| 场景 | 验收方法 | 预期结果 |
|---|---|---|
| 正常 | 模拟器持续上报 | 最新指标、趋势和在线状态正常显示 |
| 空数据 | 使用空库且不启动模拟器 | 页面显示等待上报或空数据，不报 500 |
| 离线 | 停止上报超过 15 秒 | 后端返回 OFFLINE，页面显示离线 |
| 接口错误 | 停止后端后刷新 | 页面显示错误提示和重试按钮 |
| 未登录公开页 | 无痕窗口访问 `/iot/public` | 不跳转登录页 |
| 未登录管理页 | 无痕窗口访问 `/iot/overview` | 跳转登录页 |

## 7. 验收记录

| 检查项 | 当前结果 | 说明 |
|---|---|---|
| 最新 `dev` 基线同步 | 通过 | 已包含第五周前后端与第六周产品成果 |
| API 契约静态核对 | 通过 | 页面使用公开接口实际返回结构 |
| Mock 开关 | 通过 | `USE_MOCK=false` |
| 前端生产构建 | 通过 | 2289 modules，退出码 0；仅有既有 chunk 体积警告 |
| 后端自动化回归 | 由后端 PR 提供 | 第六周后端分支执行干净回归 |
| MySQL + MQTT 真实闭环 | 待三方总联调 | 需要 Broker、数据库凭证和模拟器 |
| 桌面/手机截图 | 待人工或浏览器验收 | 不以代码审查代替视觉验收 |

## 8. 已知风险

- 前端主包约 2.4 MB，现阶段仅记录，不在第六周做拆包重构。
- 真实闭环依赖本机 MySQL 密码和 MQTT Broker；缺少任一条件时不能宣称联调通过。
- 截图和录屏必须在实际浏览器中补齐，不能用 Mock 截图充当最终证据。

## 9. 契约影响

- API_CONTRACT：无变化。
- MQTT_CONTRACT：无变化。
- 前端字段：无变化。
- 路由：无变化。
