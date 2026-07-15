# 第六周前端验收报告

**分支**: `feature/week6-iot-frontend`
**Base**: `dev`
**日期**: 2026-07-15
**验收人**: 前端 Agent
**测试模式**: Mock 数据 (`USE_MOCK = true`) + 代码审查 + 生产构建验证

---

## 1. 构建结果

| 项目 | 结果 |
|------|------|
| `npm run build` | 通过 (2289 modules, exit code 0) |
| 产物大小 | JS: 2,456 kB (gzip 804 kB), CSS: 376 kB (gzip 51 kB) |
| chunk 超大警告 | 有 (非阻塞，建议后续 code-split) |
| 编译错误 | 无 |

---

## 2. 页面路径与接口映射

### 2.1 `/iot/public` - 公开展示页

| 项目 | 详情 |
|------|------|
| 文件 | `frontend/src/views/iot/PublicDisplay.vue` |
| 路由 | `meta: { public: true }` |
| 接口 | `getPublicProject('power-monitor')` |
| 接口路径 | `GET /api/iot/public/projects/power-monitor` |
| 自动刷新 | 30 秒 |
| 无需登录 | 是 |

### 2.2 `/iot/overview` - IoT 总览页

| 项目 | 详情 |
|------|------|
| 文件 | `frontend/src/views/iot/Overview.vue` |
| 路由 | Layout 子路由 |
| 接口 | `listProjects()`, `listDevices()`, `listAlerts(1)`, `getPublicProject('power-monitor')` |
| 自动刷新 | 30 秒 |
| 需要登录 | 是 |

### 2.3 `/iot/pm001` - PM-001 实时数据页

| 项目 | 详情 |
|------|------|
| 文件 | `frontend/src/views/iot/Pm001Live.vue` |
| 路由 | Layout 子路由 |
| 接口 | `getLatestMetrics(1)`, `getMetricHistory(1)`, `listAlerts(1)`, `listRecommendations(1)`, `listCommands(1)`, `listOperationLogs(1)` |
| 刷新频率 | latest: 5s, history: 30s, alerts/rec/cmds/logs: 10s |
| 需要登录 | 是 |

---

## 3. 代码审查结果

### 3.1 Loading 状态

| 页面 | 实现 | 审查结果 |
|------|------|----------|
| PublicDisplay | `v-loading="loading && !loaded"` 首次加载骨架屏 | 通过 |
| Overview 统计 | `v-loading="loading"` | 通过 |
| Overview PM-001 | `v-loading="pmLoading"` | 通过 |
| Pm001Live latest | `v-loading="latestLoading && !latestLoaded"` | 通过 |
| Pm001Live history | `v-loading="historyLoading"` | 通过 |
| Pm001Live alerts/recs/logs | `v-loading="pollLoading"` | 通过 |

### 3.2 空数据状态

| 页面 | 实现 | 审查结果 |
|------|------|----------|
| PublicDisplay | `v-else-if="!loading && !project.projectName"` → el-empty | 通过 |
| Overview PM-001 | `v-else-if="!pmLoading && !pmData?.device"` → el-empty | 通过 |
| Pm001Live latest | `v-else-if="!latestLoading && !hasLatestData"` → el-empty + 等待提示 | 通过 |
| Pm001Live history | `v-else-if="!historyLoading && !chartData.length"` → el-empty | 通过 |
| Pm001Live alerts | `el-empty v-else description="暂无告警"` | 通过 |
| Pm001Live recs | `el-empty v-else description="暂无建议"` | 通过 |
| Pm001Live logs | `el-empty v-else description="暂无操作日志"` | 通过 |

### 3.3 错误与重试

| 页面 | 实现 | 审查结果 |
|------|------|----------|
| PublicDisplay | error + trendError, el-alert + 重试按钮 | 通过 |
| Overview PM-001 | pmError, el-alert + 重试按钮 | 通过 |
| Pm001Live latest | latestError, el-alert + 重试按钮 | 通过 |
| Pm001Live history | historyError, el-alert + 重试按钮 | 通过 |
| Pm001Live alerts | pollError, el-alert + pollData 重试 | 通过 |
| Pm001Live recs | pollError, el-alert + pollData 重试 | 通过 |
| Pm001Live logs | pollError, el-alert + pollData 重试 | 通过 |

### 3.4 离线状态

| 页面 | 实现 | 审查结果 |
|------|------|----------|
| PublicDisplay | `isOnline` 基于 `reportTime` 15 秒阈值, tag 切换 在线/离线 | 通过 |
| Overview | `pmOnline` 基于 `reportTime` 15 秒阈值, tag 切换 | 通过 |
| Pm001Live | `isOnline` 基于 `reportTime` 15 秒阈值, tag + 离线原因提示 | 通过 |

### 3.5 定时器清理

| 页面 | 实现 | 审查结果 |
|------|------|----------|
| PublicDisplay | `onUnmounted`: stopRefresh + removeEventListener + dispose | 通过 |
| Overview | `onUnmounted`: stopRefresh + removeEventListener + dispose | 通过 |
| Pm001Live | `onUnmounted`: stopLatestPoll + stopHistoryPoll + stopPoll + removeEventListener + dispose | 通过 |

### 3.6 健康评分

| 页面 | 实现 | 审查结果 |
|------|------|----------|
| PublicDisplay | `healthScore.score/level/reasons/calculatedAt` 从 `device.health` 读取, 显示分数 + 标签 + 原因列表 | 通过 |
| Overview | `healthScore` 从 `pmData.device.health` 读取, 显示分数 + 标签 + 原因 + 计算时间 | 通过 |
| 前端自行计算 | 无。所有健康评分均从接口返回, 前端仅做展示映射 | 通过 |

### 3.7 指令下发与 ACK

| 项目 | 实现 | 审查结果 |
|------|------|----------|
| 防重复点击 | `if (commandSending.value) return` + `:loading` + `:disabled` | 通过 |
| ACKED | `commandStatusType.ACKED = 'success'` + `success` timeline type | 通过 |
| FAILED | `commandStatusType.FAILED = 'danger'` + `danger` timeline type | 通过 |
| TIMEOUT | `commandStatusType.TIMEOUT = 'warning'` + `warning` timeline type | 通过 |
| 状态文本 | ACKED→已确认, FAILED→失败, TIMEOUT→超时 | 通过 |

### 3.8 告警操作防重复

| 项目 | 实现 | 审查结果 |
|------|------|----------|
| 处理告警 | `if (alertActionLoading.value[id]) return` + per-id loading | 通过 |
| 忽略告警 | 同上 | 通过 |
| 确认建议 | `if (recActionLoading.value[id]) return` + per-id loading | 通过 |
| 忽略建议 | 同上 | 通过 |

### 3.9 路由守卫

| 路由 | 预期 | 实现 | 审查结果 |
|------|------|------|----------|
| `/iot/public` | 无需登录可访问 | `meta: { public: true }` → `return true` | 通过 |
| `/iot/overview` | 需登录 | 非公开 → `!hasLogin()` → redirect `/login` | 通过 |
| `/iot/pm001` | 需登录 | 非公开 → redirect `/login` | 通过 |
| 登录后访问 public | 不重定向 | `return true` for `meta.public` | 通过 |

### 3.10 响应式布局

| 页面 | 实现 | 审查结果 |
|------|------|----------|
| PublicDisplay | `@media (max-width: 768px)`: header-nav hidden, 字体缩放, chart 高度降低 | 通过 |
| Overview | `@media (max-width: 768px)`: stat-value 缩放, health-block 居中, chart 高度降低 | 通过 |
| Pm001Live | `@media (max-width: 768px)`: metric-value 缩放, metric-box padding 缩小, chart 高度降低 | 通过 |

---

## 4. Mock 数据字段与 API_CONTRACT 一致性

| API | Mock 字段 | 契约字段 | 审查结果 |
|-----|----------|----------|----------|
| getPublicProject | `projectName`, `projectCode`, `description`, `status`, `deviceCount`, `onlineDeviceCount` | 一致 | 通过 |
| getPublicProject.device | `id`, `deviceCode`, `deviceName`, `status`, `reportTime`, `metrics[]` | 一致 | 通过 |
| getPublicProject.device.health | `score`, `level`, `reasons[]`, `calculatedAt` | 一致 | 通过 |
| getPublicProject.powerTrend | `[{time, value}]` | 一致 | 通过 |
| getLatestMetrics | `deviceId`, `deviceCode`, `deviceName`, `projectName`, `status`, `reportTime`, `metrics[]` | 一致 | 通过 |
| getMetricHistory | `deviceId`, `metricKey`, `unit`, `points[]` | 一致 | 通过 |
| listAlerts | `id`, `deviceId`, `deviceCode`, `metricKey`, `level`, `message`, `triggerValue`, `thresholdValue`, `status`, `createdAt` | 一致 | 通过 |
| listRecommendations | `id`, `source`, `deviceId`, `alertId`, `title`, `content`, `status`, `createdAt` | 一致 | 通过 |
| listCommands | `commandId`, `deviceId`, `deviceCode`, `command`, `status`, `createdAt` | 一致 | 通过 |
| listOperationLogs | `id`, `operatorId`, `operatorName`, `action`, `targetType`, `targetId`, `summary`, `createdAt` | 一致 | 通过 |

---

## 5. 第六周修复项

| 编号 | 问题 | 修复 | 文件 |
|------|------|------|------|
| FIX-1 | router: 已登录用户访问 `/iot/public` 被重定向 | 移除 `return hasLogin() ? '/dashboard' : true`, 改为 `return true` | `router/index.js` |
| FIX-2 | Overview.vue resize 匿名函数导致 removeEventListener 无效 | 提取为命名函数 `onChartResize` | `Overview.vue` |
| FIX-3 | Overview.vue listAlerts 传 `status: 'OPEN'` 参数, mock 不支持 | 移除参数, 改为前端 filter | `Overview.vue` |
| FIX-4 | Overview.vue 使用旧 pie chart 版本, 不符合第五周产品规格 | 完全重写为 stat cards + PM-001 summary + mini trend | `Overview.vue` |
| FIX-5 | PublicDisplay.vue 使用旧 `payload.project/device/latestMetrics` 扁平结构 | 更新为 API_CONTRACT 扁平结构 `payload.projectName/device.powerTrend` | `PublicDisplay.vue` |
| FIX-6 | iot.js MOCK_PUBLIC_PROJECT 结构不匹配 API_CONTRACT | 更新为扁平结构 + device 内嵌 health | `api/iot.js` |

---

## 6. 演示脚本走查（代码审查模式）

由于本地后端不可用 (MySQL/Redis 未启动), 以下为 mock 模式下的预期行为:

### Step 1: 未登录访问 /iot/public
- 预期: 显示公开展示页, 含项目名称、设备状态、指标、健康评分、趋势图
- Mock 数据: projectName="实验室功耗监测", deviceCode="PM-001", status="ONLINE", score=100, level=HEALTHY
- 结果: PASS (代码逻辑正确, mock 路径命中)

### Step 2: 未登录访问 /iot/overview
- 预期: 重定向到 /login?redirect=%2Fiot%2Foverview
- 结果: PASS (路由守卫逻辑正确)

### Step 3: 登录后访问 /iot/overview
- 预期: 统计卡片 (6 个) + PM-001 摘要 + 快捷入口 + 项目列表
- 结果: PASS (数据流: listProjects + listDevices + listAlerts + getPublicProject)

### Step 4: 登录后访问 /iot/pm001
- 预期: 最新数据卡片 + 历史曲线 + 告警 + 建议 + 指令 + 日志
- 结果: PASS (第四周功能完整, 无回归)

### Step 5: 点击"下发指令"
- 预期: loading 期间按钮禁用, 3 秒后 mock 自动 ACK
- 结果: PASS (commandSending guard + :loading + :disabled)

### Step 6: 点击告警"处理"
- 预期: 该行 loading, 完成后刷新列表
- 结果: PASS (per-id loading guard)

---

## 7. 截图清单

由于浏览器工具 (Electron WebView) 无法正常渲染页面, 截图在联调时补充:
- [ ] /iot/public 全景 (桌面宽度)
- [ ] /iot/public 全景 (手机宽度 375px)
- [ ] /iot/overview 全景 (桌面宽度)
- [ ] /iot/pm001 全景 (桌面宽度)
- [ ] /iot/pm001 告警处理操作
- [ ] /iot/pm001 指令下发 + ACKED 状态
- [ ] /iot/public 空数据状态
- [ ] /iot/public 错误状态

---

## 8. 未解决风险

| 风险 | 级别 | 说明 |
|------|------|------|
| 真实后端联调未完成 | 中 | 当前 USE_MOCK=true, 切换真实接口需将 USE_MOCK 改为 false |
| chunk 超大 (2.4MB) | 低 | 非阻塞, 建议后续通过 manualChunks 拆分 echarts |
| /iot/public 趋势图阈值硬编码 100W | 低 | mock 中固定, 真实接口应从配置或告警规则获取 |
| PublicDisplay/Overview 使用 getPublicProject 获取 PM-001 数据 | 信息 | Overview 页面通过公开接口获取 PM-001 数据, 需确认该接口是否需要鉴权 |
| 浏览器自动化测试受限 | 信息 | Electron WebView 缺少 preload 脚本导致白屏, 需人工补充截图 |

---

## 9. 修改文件清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `frontend/src/router/index.js` | 修改 | 公开路由守卫修复 |
| `frontend/src/api/iot.js` | 修改 | 新增 getPublicProject API + mock 数据结构更新 |
| `frontend/src/views/iot/PublicDisplay.vue` | 重写 | 完整公开展示页实现 |
| `frontend/src/views/iot/Overview.vue` | 重写 | 完整 IoT 总览页实现 |
| `frontend/src/views/iot/Pm001Live.vue` | 未修改 | 第四周功能保持不变 |
| `docs/frontend/week6-frontend-acceptance.md` | 新增 | 本验收报告 |
