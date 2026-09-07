# 2026-W37 前端基线清点报告

> 路线周次：第 1 周
> 范围：M01-W01 现状清点（不新增业务功能，不重构已有考勤/IoT 代码）
> 分支：`feature/m01-w01-frontend-baseline`
> 基准 commit：`78a83b6`（产品 PR：week 1 baseline）
> 日期：2026-09-07
> 状态：清点完成，等待后端基线 PR 合入 dev 后建立前端基线 PR

---

## 0. 概览

| 维度 | 现状 |
|---|---|
| 框架 | Vue 3.5 + Vite 6.4 + Vue Router 4.5 + Element Plus 2.9 |
| 包管理 | `package.json` 已锁，无 `package-lock.json` 提交争议（已恢复原始 lock） |
| API 封装 | `axios` + 统一拦截器，响应自动解包到 `payload.data`（已在 `utils/request.js`） |
| 状态管理 | 自研 `store/user.js`（reactive + localStorage/sessionStorage 持久化） |
| 图表 | `echarts 6.1`，当前以 `* as echarts` 方式在 3 个页面中按需初始化 |
| 真实接口开关 | `frontend/src/api/iot.js` 中 `USE_MOCK = false`（已切换到真实后端，页面内 mock 数据保留为兜底常量） |
| 页面总数 | 13 个路由（2 个公开 + 1 个登录 + 10 个受保护 + 1 个 404 重定向） |
| 视图组件 | 12 个 SFC（`views/` 下），均未抽取为可复用公共组件 |
| 已实现 | 考勤闭环、IoT 单设备闭环（latest/history/alerts/recommendations/commands/logs）、PM-001 SSE 第七周集成、IoT 总览 + 公开项目展示 |
| 未实现 | 个人工作台聚合、项目工作台、学习实验台、资源管理、自研硬件、知识与 Agent、PM-001 之外的设备接入 |

---

## 1. 页面清单（路由 → 组件 → 状态）

> 全部路由定义于 `frontend/src/router/index.js`。
> 视图组件集中于 `frontend/src/views/`，按模块拆为 `views/`（顶层）、`views/admin/`、`views/iot/` 三个子目录。

### 1.1 公开页面（不要求登录）

| # | 路由 | 组件 | 状态 | 关键职责 | 截图 |
|---|---|---|---|---|---|
| 1 | `/login` | `views/Login.vue` | 已有 UI + 真实登录 | 学工号 + 密码登录、记住登录态、按角色跳转 | ❌ 截图待补（基线采集时未带 auth token，公开页需重抓） |
| 2 | `/iot/public` | `views/iot/PublicDisplay.vue` | 已有 UI + 真实接口 | 公开项目展示（项目名、状态、PM-001 状态、指标卡片、功率趋势、健康评分、刷新） | ❌ 截图待补（基线采集时未带 mock token） |
| — | `/:pathMatch(.*)*` | 重定向到 `/dashboard` | 默认兜底 | 404 路由，命中后跳个人工作台 | — |

### 1.2 受保护页面（需登录后访问）

| # | 路由 | 组件 | 状态 | 关键职责 | 截图 |
|---|---|---|---|---|---|
| 3 | `/dashboard` | `views/Dashboard.vue` | 已有 UI + 真实接口 | 个人考勤控制台（欢迎条、姓名/专业/身份/学院、签到/签退、本周/学期学时、今日打卡表） | `docs/evidence/2026-W37/frontend/03-dashboard.png` |
| 4 | `/iot/overview` | `views/iot/Overview.vue` | 已有 UI + 真实接口 | IoT 总览（6 张统计卡、PM-001 摘要、功率趋势 mini 折线、3 个快捷入口；30s 自动刷新 + 手动刷新） | `04-iot-overview.png` |
| 5 | `/iot/projects` | `views/iot/Projects.vue` | 已有 UI + Mock 创建 | 项目列表（搜索/状态筛选、表格、分页、Mock 新建对话框） | `06-iot-projects.png` |
| 6 | `/iot/projects/:id` | `views/iot/ProjectDetail.vue` | 已有 UI + 真实接口 | 项目详情（项目头 + 下属设备表 + 进入设备详情） | （与 `06` 同一截图包含） |
| 7 | `/iot/devices` | `views/iot/DeviceList.vue` | 已有 UI + 真实接口 | 设备列表（4 维筛选 + 关键字搜索、状态标签、详情按钮） | `07-iot-devices.png` |
| 8 | `/iot/devices/:id` | `views/iot/DeviceDetail.vue` | 已有 UI + 真实接口 | 设备详情（设备头、最新指标卡、功率历史曲线、告警、建议、指令控制、操作日志；10s 轮询指标） | （与 `07` 同一截图包含） |
| 9 | `/iot/alerts` | `views/iot/AlertCenter.vue` | UI 极简、调用真实接口 | 告警中心（单表 + 处理按钮，**未用真实接口分页**） | `08-iot-alerts.png` |
| 10 | `/iot/commands` | `views/iot/CommandConsole.vue` | UI 极简、调用真实接口 | 指令控制台（单表展示下发记录，**未用真实接口分页**） | `09-iot-commands.png` |
| 11 | `/iot/logs` | `views/iot/OperationLogs.vue` | UI 简单、调用真实接口 | 操作日志（动作/目标类型筛选 + 表格 + 分页） | `10-iot-logs.png` |
| 12 | `/iot/pm001` | `views/iot/Pm001Live.vue` | 第七周新增 | PM-001 实时监控（最新数据卡 SSE 优先 + 轮询降级、历史曲线 30s 轮询、告警/建议/指令/日志 10s 轮询、SSE 状态条、联调说明） | `05-iot-pm001.png` |
| 13 | `/admin/members` | `views/admin/MemberManage.vue` | 已有 UI + 真实接口 | 实验室成员管理（4 维筛选、新增/编辑/启停用、全套分页器） | `11-admin-members.png` |
| 14 | `/admin/attendance` | `views/admin/AttendanceRecord.vue` | 已有 UI + 真实接口 + 导出 | 考勤检查与导出（4 维筛选 + 日期范围、表格、xlsx 导出） | `12-admin-attendance.png` |

> 说明：截图编号从 03 开始（01 登录、02 公开页待重抓），`/iot/projects/:id` 与 `/iot/devices/:id` 因基线采集时未携带 dev token，详情页统一回退到列表，未单独截图。

### 1.3 应用外壳

| 文件 | 职责 | 备注 |
|---|---|---|
| `src/App.vue` | 单一 `<router-view />` 容器 | 不做主题切换、不挂全局错误边界 |
| `src/layout/Index.vue` | 侧栏 + 顶栏 + 主区 + 路由出口 | 当前仅有考勤/IoT/Admin 三类入口；侧栏品牌色 `#172033`，品牌标识 `#f5c542` |
| `src/router/index.js` | 13 条路由 + 全局 `beforeEach` 守卫 | 已实现登录拦截与管理员角色守卫（`meta.role === 'admin'`） |

---

## 2. 视觉债务清单

> 范围仅记录 **已知、当前可被截图或代码佐证** 的视觉/可用性问题，不做未来推演。

### 2.1 UI_SPEC Token 未落地（全局）

- **现状**：`src/styles/global.css` 只声明 `--usn-…` 的使用文字描述（`color: #172033; background: #f4f7fb;`），**未输出任何 CSS 变量**。`UI_SPEC.md §2` 给出的 `--usn-blue-700: #07598f;`、`--usn-blue-600: #0b6db6;`、`--usn-purple-600: #93559c;`、`--usn-canvas: #f4f6f8;` 等 16 个 Token 全部缺失。
- **佐证**：所有页面、Layout、卡片、按钮均直接写死 `#172033`、`#7c8798`、`#409eff`、`#1d4f91`、`#67c23a`、`#f56c6c`、`#e6a23c` 等十六进制色（见 `Dashboard.vue`、`PublicDisplay.vue`、`layout/Index.vue`、`Pm001Live.vue`）。
- **影响**：① 换主题/暗色模式无解；② 颜色规则被散落到 10+ 文件，后续 PR 改色需手工同步；③ 部分页面颜色与 UI_SPEC 调色板有出入（如 UI_SPEC 主蓝 `#0b6db6`，实际多用 Element Plus 默认 `#409eff` 与 `#1d4f91`）。
- **建议**：在 `src/styles/tokens.css` 中实现全部 UI_SPEC §2 Token，引入 `tokens.css` 至 `main.js`，按 token 替换所有硬编码十六进制色。

### 2.2 登录页视觉偏离（UI_SPEC §6 禁止模式）

- **现状**：`views/Login.vue` 的 `.login-visual` 区域使用 Unsplash 在线图片 `https://images.unsplash.com/photo-1581093588401-fbb62a02f120` 作为整页大背景。
- **违反点**：
  1. 引入外网图片资源，UI_SPEC §5 明确要求"占位由前端静态组件生成"且"不向运行时请求 AI/外网图片"。
  2. 与 UI_SPEC §6「蓝紫渐变铺满背景或大标题」属同类风险，渐变叠加 + 实验室人物图占满左半屏。
  3. 标题"实验室考勤与人员管理系统"为大字号营销式 Hero（52px/38px），违反 §6「巨大欢迎来到未来实验室 Hero」精神。
- **截图**：`03-dashboard.png` 同样在 Dashboard 的 `.hero-panel` 使用 `https://images.unsplash.com/photo-1581092160607-ee22621dd758` 背景，亦属同类问题。
- **建议**：移除 Unsplash 外链；改用 UI_SPEC §3「应用外壳」描述的浅色画布 + 实验室标志静态元素（`docs/design/reference/usn-lab-emblem-reference.png` 已存在，可本地化引用）。

### 2.3 布局/排版

| 序号 | 位置 | 现状 | 违反点 | 建议 |
|---|---|---|---|---|
| L-1 | `layout/Index.vue` 侧栏 | 宽度 `232px` | UI_SPEC §2 桌面侧栏规定 `224px` | 改 `224px` |
| L-2 | `layout/Index.vue` 顶栏 | 高度 `72px` | UI_SPEC §2 顶栏规定 `56px` | 改 `56px`，并重算 padding |
| L-3 | `Dashboard.vue` 卡片 | `.info-card/.metric-card/.action-card` 固定 `height: 132px` | UI_SPEC §6「禁止卡片嵌套卡片」精神，单卡片空间被强行塞大数字+标题+角标 | 改为自适应高度；大数字用信息密度而非绝对高度 |
| L-4 | `Dashboard.vue` 签退按钮 | `.attendance-button` 固定 `height: 56px; font-size: 18px` | UI_SPEC §2 正文 14px，标题 22-28px；按钮文字不应大于正文两倍 | 收敛到 `40-44px` 高、字号 14-16px |
| L-5 | `Pm001Live.vue` 指标卡 | `.metric-value` 36px、`.health-score` 56px | 与 §6「不要为每个数字单独做大圆角卡片」重叠；`PublicDisplay.vue` 的 `.health-score` 同样 56px | 收敛到 28-32px，并让 `metric-box` 回到表格/列表密度 |
| L-6 | `Pm001Live.vue` 渐变卡 | `.metric-box.voltage/.current/.power` 使用 `linear-gradient(135deg, …)` 渐变背景 | 接近 UI_SPEC §6「发光边框、玻璃拟态、漂浮渐变球」边缘 | 改用纯色 + 左侧 4px 标识边 |
| L-7 | 全局 | `page-shell` `gap: 18px` 出现于 `Dashboard.vue` 与 `MemberManage.vue` 等 | UI_SPEC §2 间距以 4 为基准，常用 8/12/16/24/32 | 统一为 16 或 24 |
| L-8 | `Overview.vue` 容器 | `.iot-overview { max-width: 1200px; }` | UI_SPEC §2 内容最大宽度 1600px | 放宽到 1600px；`PublicDisplay.vue` 同样 1200px |
| L-9 | `Pm001Live.vue` 容器 | `.pm001-live { max-width: 960px; }` | 同上 | 放宽到 1200-1600px，让数据卡与告警并排 |
| L-10 | `Dashboard.vue` 桌面/移动断点 | `@media (max-width: 768px)` 仅调整 hero 一处 | UI_SPEC §7 验收要求 `1440x900/1280x800/390x844` 三档 | 引入 `1280x800` 与 `390x844` 显式断点；当前移动端只剩 Dashboard 一处样式响应式 |

### 2.4 占位与图片

- 现状：除 Login/Dashboard 的 Unsplash 外链外，无任何项目、元器件、硬件版本占位组件。第七周范围内没有图片占位需求，但 UI_SPEC §5 强制规定"图片加载失败自动回退占位，不能撑动布局"。
- 风险：一旦第 3 周起出现项目/元器件图片上传或后端返回图片字段，前端将直接断图。
- 建议：在 W2 之后立即把 `Placeholder`/`MediaWithFallback` 公共组件提到路由层。

### 2.5 响应式

- 全部视图均**未针对 1280x800 与 390x844 做过显式布局验证**，仅有 768px / 900px / 860px 等不统一断点。
- `AlertCenter.vue`/`CommandConsole.vue` 的 `el-table` 缺少 `min-width`，窄屏会被压成竖条。
- `MemberManage.vue` 已用 `el-col :xs :sm :lg` 响应式，可作为后续模板。
- 建议：在 W2 起强制每页带 3 视口截图回归（见 §5 验收清单）。

### 2.6 国际化与时间

- `Dashboard.vue`、`Pm001Live.vue`、`Overview.vue`、`PublicDisplay.vue` 普遍用 `new Date().toLocaleString('zh-CN', { hour12: false })`，部分用 `value.replace('T', ' ')`，风格不一。
- `PublicDisplay.vue` 中 `.updated-at` 显示完整时间戳，未做"刚刚 / N 分钟前"的友好处理。
- 建议：抽取 `utils/time.js`，提供 `formatDateTime`、`fromNow`；与后端约定统一时间字段（`reportTime` / `createdAt` / `calculatedAt`）。

### 2.7 错误与空状态覆盖度

- ✅ 已有：Dashboard 暂无打卡、IoT 总览 / 公开页 / PM-001 的"暂无数据" + "加载失败重试"。
- ❌ 缺失：
  1. `AlertCenter.vue` 与 `CommandConsole.vue` 缺少 el-empty 与错误态（仅 `console.error`），页面 404/500 时一片空白。
  2. `Projects.vue` 新建对话框点击"确认创建"在真实接口下会失败，但代码里只 `console.error`，用户无反馈。
  3. `DeviceList.vue`/`OperationLogs.vue` 的分页器 `v-if="total > 0"` 在真实接口返回 0 条时空状态与 el-empty 共存，重复占用版面。
  4. `MemberManage.vue` 缺少表格加载/错误态提示（仅 `v-loading`）。
- 建议：W2 起按 UI_SPEC §6 全部页面补齐 loading / empty / error / permission 四态。

### 2.8 一致性 / 重复实现

- `DeviceList.vue` / `OperationLogs.vue` / `AlertCenter.vue` / `CommandConsole.vue` / `Projects.vue` 均重复实现「filter-bar + 表格 + 分页」模式，4 份几乎一致代码（见 §3.2）。
- 三个 IoT 页面（`Overview.vue` / `PublicDisplay.vue` / `Pm001Live.vue`）各自实现 ECharts 初始化/销毁/resize 监听，模板重复（见 §3.2）。
- 状态 → Tag 颜色映射在 `DeviceList.vue`、`ProjectDetail.vue`、`DeviceDetail.vue`、`Pm001Live.vue` 中各写一遍，规则不一致：`ONLINE` 偶有映射为 `success`，但 `DeviceDetail.vue` 与 `ProjectDetail.vue` 把 `ONLINE/OFFLINE/ALERT/MAINTENANCE` 四态写死，其他页面只写两态。

---

## 3. 可复用组件清单

### 3.1 已有公共资产

| 位置 | 资产 | 复用范围 | 备注 |
|---|---|---|---|
| `src/utils/request.js` | `axios` 实例 + 拦截器 | 全局 | 已统一处理 `code !== 200` → 错误提示；401 → 强制登出；返回值已解包到 `payload.data` |
| `src/store/user.js` | `userStore` + `hasLogin()` + `isAdmin()` | 全局 | localStorage/sessionStorage 持久化、`rememberMe` 控制 |
| `src/constants/dictionaries.js` | `identityOptions` / `groupOptions` / `statusOptions` / `attendanceStatusOptions` | Admin 模块 | 仅考勤/成员页使用；其它模块未引用 |
| `src/utils/format.js` | `formatHours` / `formatMinutes` / `formatDateTime` / `buildQueryWithoutEmpty` | 考勤为主 | 其它页面没引用，时间/数字各自实现 |
| `src/api/iot.js` | `USE_MOCK` 开关 + 全部 IoT 接口 | IoT 模块 | 已按 API_CONTRACT 组织，列表分页使用 `data.list` 字段（与 API 契约一致） |
| `src/api/sse.js` | `DeviceTelemetrySSE` 封装 | `/iot/pm001` 单点 | 第七周新增，已统一 EventSource 生命周期 |
| `src/layout/Index.vue` | 应用外壳 | 全局 | 侧栏 + 顶栏；菜单根据 `isAdminUser` 过滤 |
| `src/router/index.js` | 路由 + 守卫 | 全局 | 登录拦截 + admin 角色守卫 |
| `src/styles/global.css` | 全局重置 + `.page-shell/.page-heading/.page-title/.data-panel/.toolbar` 工具类 | 部分页面（Dashboard/MemberManage/AttendanceRecord） | IoT 页面未使用该工具类 |

### 3.2 可提取的重复 UI 模式

| 模式 | 现有重复点 | 提取建议 |
|---|---|---|
| 筛选 + 表格 + 分页 | `views/iot/DeviceList.vue`、`views/iot/Projects.vue`、`views/iot/OperationLogs.vue`、`views/iot/AlertCenter.vue`（无分页）、`views/iot/CommandConsole.vue`（无分页）、`views/admin/MemberManage.vue`、`views/admin/AttendanceRecord.vue` | 抽取 `<DataFilterBar>`、`<DataTable>`、`<StandardPagination>` 三个公共组件；统一 `loading/empty/error` 占位 |
| ECharts 折线 + 阈值线 + resize 监听 | `views/iot/Overview.vue`（mini）、`views/iot/PublicDisplay.vue`（完整）、`views/iot/Pm001Live.vue`（完整）、`views/iot/DeviceDetail.vue` | 抽取 `<PowerTrendChart :points :threshold :height>` 组件；配置项（颜色/阈值/网格）走 props |
| 设备状态 → Tag 类型映射 | `DeviceList.vue` / `ProjectDetail.vue` / `DeviceDetail.vue` / `Pm001Live.vue` | 抽取 `utils/deviceStatus.js`，导出 `statusTagType` 与 `statusText` |
| 告警等级 / 指令状态 Tag 映射 | `DeviceDetail.vue` / `Pm001Live.vue` / `CommandConsole.vue` | 抽取 `utils/iotEnums.js` |
| 健康等级 → 中文 / 颜色 | `Overview.vue` / `PublicDisplay.vue` / `Pm001Live.vue` 三处复制 | 抽取 `utils/healthLevel.js` |
| 指标卡 | `Pm001Live.vue` 与 `DeviceDetail.vue` 的"电压/电流/功率"卡（图标 + 名 + 数值 + 单位） | 抽取 `<MetricCard :icon :name :value :unit :tone>` 组件 |
| 加载/错误/空态三态 | `Overview.vue` / `PublicDisplay.vue` / `Pm001Live.vue` / `DeviceDetail.vue` 四处分别写 `el-alert + el-button 重试` 与 `el-empty + 等待提示` | 抽取 `<QueryState type="loading|error|empty" :description :onRetry>` 组件 |
| 时间格式化 | `Dashboard.vue` 用 `toLocaleString`、`PublicDisplay.vue` 用 `replace('T', ' ')`、表格列直接渲染 `prop="createdAt"` | 抽取 `utils/time.js` 集中处理 |
| Mock 数据兜底常量 | `api/iot.js` 内部 `MOCK_PROJECTS/DEVICES/…` 共 8 组硬编码 | 移到 `src/api/__fixtures__/iot.js`，与 `USE_MOCK` 解耦，便于团队共享 mock 资产 |

### 3.3 缺少的公共组件

| 组件 | 期望职责 | 优先级（W2） |
|---|---|---|
| `AppPageHeader` | 页面标题 + 副标题 + 右侧主按钮，统一间距与字号 | P1 |
| `AppTable` | 表格 loading / empty / error 三态封装 + 分页插槽 | P1 |
| `AppFilterBar` | 筛选条件 + 搜索/重置按钮，与 `AppPageHeader` 配合 | P1 |
| `AppChart` | ECharts 通用容器（自动 resize、自动 dispose） | P1 |
| `MetricCard` | 单一指标展示（图标 + 名称 + 数值 + 单位 + 颜色调性） | P1 |
| `HealthScoreCard` | 评分 + 等级 + 原因 + 计算时间 | P2 |
| `ConnectionStatusBadge` | SSE 三态色点（绿/黄/灰）+ 文案，复用 `SSE_STATUS` | P2 |
| `EmptyState` / `ErrorState` | 集中管理空/错态插画、提示语和重试入口 | P2 |
| `TimeFromNow` / `TimeAbsolute` | 统一时间渲染 | P2 |
| `Placeholder` | UI_SPEC §5 占位组件（项目、元器件、硬件版本、资产、采购），W3 起预留 | P3 |

### 3.4 设计 Token 待补

> 与 §2.1 重复强调：UI_SPEC §2 给出 16 个 Token，前端尚未落地任何一项；W2 应一次性补齐。

```text
--usn-blue-700   #07598f
--usn-blue-600   #0b6db6
--usn-blue-100   #dceefa
--usn-purple-600 #93559c
--usn-indigo-500 #6365a9
--usn-ink-900    #17212b
--usn-ink-700    #3d4b59
--usn-ink-500    #667482
--usn-line       #d8dee5
--usn-surface    #ffffff
--usn-canvas     #f4f6f8
--usn-success    #1d7a4d
--usn-warning    #a66100
--usn-danger     #b42318
--usn-radius-sm  4px
--usn-radius-md  8px
--usn-shadow-panel 0 1px 2px rgba(23, 33, 43, 0.08)
```

---

## 4. API 契约一致性记录

> 仅记录**前端已发现、与 `docs/agent-guides/04_API_CONTRACT.md` 不一致**的契约使用点。不动代码，纳入 W2 前端契约议题。

| 序号 | 文件 | 实际使用 | 契约规定 | 状态 |
|---|---|---|---|---|
| API-1 | `views/iot/DeviceList.vue` (lines 96, 109) | `res.data.records` | `{ data: { list, total } }` | 真实接口下会拿到 `undefined`；截图 `07-iot-devices.png` 中表格为空即由此而来 |
| API-2 | `views/iot/Projects.vue` (lines 119-120) | `res.data.records` | 同上 | 同上 |
| API-3 | `views/iot/ProjectDetail.vue` (line 79) | `res.data.records` | 同上 | 同上 |
| API-4 | `views/iot/AlertCenter.vue` (line 48) | `res.data.records` | 同上 | 同上 |
| API-5 | `views/iot/CommandConsole.vue` (line 34) | `res.data.records` | 同上 | 同上 |
| API-6 | `views/iot/OperationLogs.vue` (lines 96-97) | `res.data.records` | 同上 | 同上 |
| API-7 | `views/iot/DeviceDetail.vue` (lines 358, 372, 385, 423) | `res.data.records` | 同上 | 同上 |
| API-8 | `views/admin/MemberManage.vue` (lines 176-178) | `result.records`（直接当返回值，绕开响应拦截器） | 需校验后端 `/admin/member/page` 是否已经走 `{code, data:{list,total}}` 包裹 | 待后端确认 |
| API-9 | `views/admin/AttendanceRecord.vue` (lines 125-127) | `result.records` | 同 API-8 | 待后端确认 |
| API-10 | `views/iot/Overview.vue` (line 215) | `payload.list` | `{ data: { list } }` | 与契约一致；但混用 `payload.data` / `payload` 取值 |
| API-11 | `views/iot/PublicDisplay.vue` (line 225) | `res.data \|\| res` | `{ data: { … } }` | 兼容双形态，建议统一为 `res.data` |
| API-12 | `views/iot/Pm001Live.vue` (lines 357, 405, 481-493) | `res.data \|\| res` + `payload.list` | 同 API-11 | 同上 |
| API-13 | `views/iot/DeviceDetail.vue` (lines 271, 285, 299) | `res.data` | `{ data: { … } }` | 与契约一致 |
| API-14 | `views/iot/ProjectDetail.vue` (lines 66-67) | `res.data \|\| res.data.devices` | 需校验 `getProjectDetail` 返回中是否含 `devices` 字段 | 截图显示下属设备为空，需后端确认 |
| API-15 | `views/iot/DeviceDetail.vue` (line 357) | `listAlerts({ deviceId })` | API_CONTRACT 中 `listAlerts(deviceId, params)` 第一个参数为 deviceId | 参数形态不一致，需在 W2 与后端约定后统一 |

> **未在本周处理**，仅记录，避免越权修改第三/四/六/七周已完成代码（项目硬约束）。

---

## 5. 测试与基线证据

### 5.1 构建

- 命令：`cd frontend && npm run build`
- 节点：Node.js（`C:\Program Files\nodejs`），npm 调用 Vite 6.4.3
- 结果：**成功**
  - `dist/index.html` 0.41 kB
  - `dist/assets/index-ChW5TJlp.css` 375.57 kB（gzip 51.31 kB）
  - `dist/assets/index-B9g072Y8.js` 2,452.96 kB（gzip 802.66 kB）
  - 2290 modules transformed，耗时约 14.8s
- 警告：单 chunk > 500 kB（主因是 Element Plus 全量引入 + ECharts 全量引入），属可优化项（dynamic import / manualChunks），不在 W1 范围。

### 5.2 1440x900 视口截图

- 工具：Microsoft Edge（Headless）+ `seed-*.html` 临时登录种子（已在采集后清理，`frontend/public/seed-*.html` 全部删除）
- 落点：`docs/evidence/2026-W37/frontend/`
- 现状文件：

| 序号 | 文件 | 备注 |
|---|---|---|
| 03 | `03-dashboard.png` | 完整截图，个人工作台 |
| 04 | `04-iot-overview.png` | IoT 总览（空态：后端 500 / mock 已关闭） |
| 05 | `05-iot-pm001.png` | PM-001 实时页（错误态，提示后端未启） |
| 06 | `06-iot-projects.png` | 项目列表（空态） |
| 07 | `07-iot-devices.png` | 设备列表（空态 + 错误态） |
| 08 | `08-iot-alerts.png` | 告警中心（空态） |
| 09 | `09-iot-commands.png` | 指令控制台（空态） |
| 10 | `10-iot-logs.png` | 操作日志（空态） |
| 11 | `11-admin-members.png` | 成员管理（空态） |
| 12 | `12-admin-attendance.png` | 考勤检查（空态） |

- **缺口**：
  1. `01-login.png` / `02-iot-public.png` 缺失：基线采集时未带登录 token，公开页需在 W2 重新补充截图（可不带 token）。
  2. `iot/projects/:id`、`iot/devices/:id` 详情页未单独截图：基线采集时详情页直接跳转到列表或 500，需在 W2 联调后补抓。
- 建议在 W2 把 `01/02` 与详情页补齐；其余视口（1280x800 / 390x844）一并补齐。

### 5.3 待 W2 验收的固定测试项（已记录，待后续周使用）

- 每个关键页面提交 3 视口截图（1440x900 / 1280x800 / 390x844）。
- 每个页面补齐 loading / empty / error / permission 四态截图。
- 跑 `npm run build` 与 `node src/api/__tests__/sse-test-node.mjs`，记录通过/失败数量。

---

## 6. 状态映射与本地约定

- 健康评分等级：`HEALTHY` → 健康（绿 `#67c23a`），`ATTENTION` → 需关注（橙 `#e6a23c`），`RISK` → 风险（红 `#f56c6c`）。三处页面实现一致，OK。
- 告警状态：`OPEN` → 未处理（danger）、`HANDLED` → 已处理（success）、`IGNORED` → 已忽略（info）。
- 指令状态：`PENDING → info`、`SENT → primary`、`ACKED → success`、`FAILED → danger`、`TIMEOUT → warning`。
- 设备状态：`ONLINE → success`、`OFFLINE → info`、`ALERT → danger`、`MAINTENANCE → warning`。
- 设备在线判定：第七周起 PM-001 优先信任后端 `status` 字段；`DeviceDetail.vue` 仍按 `setInterval(loadMetrics, 10000)` 轮询，无客户端时间判定；保留一致。
- 公开页（`/iot/public`）必须不要求登录，登录用户访问也不自动跳走（已实现）。
- `iot` 系列 API 默认走 `axios` 拦截器返回值（已解包），但**Admin 模块的 `member.js` / `attendance.js` 的部分调用**绕开拦截器直接用 `result.records`（见 API-8 / API-9），需在 W2 与后端统一。

---

## 7. 已知技术债务与风险

| 风险 | 影响 | 建议处理周次 |
|---|---|---|
| UI_SPEC Token 未落地 | 后续重构成本大、视觉一致性差 | W2 起冻结 Token，先替换 Layout / Dashboard |
| Login / Dashboard 引入 Unsplash 外链 | 公网资源失效风险、与 UI_SPEC §5/§6 冲突 | W2 必修 |
| 多个 IoT 页面使用 `res.data.records` | 真实接口下分页数据为 `undefined` | W2 前端契约修复 |
| 详情页（`/iot/projects/:id`、`/iot/devices/:id`）在真实接口下未截图、未做错误态 | W2 联调必须补 | W2 |
| ECharts 与 Element Plus 全量打包，主 chunk > 2.4 MB | 首屏白屏时间偏长 | W3 引入 `manualChunks` / `unplugin-vue-components` |
| 应用外壳断点 768/860/900 不统一 | 多视口布局破损 | W2 收敛到 1280/768/390 |
| 业务模块暂无 `ErrorState` / `EmptyState` 组件 | 错误态与空态在多页面重复实现 | W2 抽组件 |
| `member.js` / `attendance.js` 部分调用绕开响应拦截器 | 错误码分支未走 `ElMessage.error` 统一提示 | W2 |

---

## 8. 范围合规自检

- [x] 未新增业务 API、页面或路由
- [x] 未重构现有考勤或 IoT 闭环代码
- [x] 未修改数据库 Schema / Flyway 迁移
- [x] 未修改 MQTT Topic / payload
- [x] 未引入新的前端组件库或后端中间件
- [x] 未把清点结论写入 `CURRENT_STATE.md`（本文件位于 `docs/audits/`，与 `CURRENT_STATE` 解耦）
- [x] 未修改 `04_API_CONTRACT.md`、`05_MQTT_CONTRACT.md`

---

## 9. 待办与移交

- 本报告即 M01-W01 前端清点输出，落地后随 `feature/m01-w01-frontend-baseline` PR 一并提交。
- 后续：等后端基线 PR 合入 dev 后，前端再基于最新 dev 重新对一遍路由与 API 状态，必要时微调。
- W2 输入：W2 周文档冻结后，按本报告 §3.2/§3.3 抽取公共组件 + 落地 UI_SPEC Token + 替换 Unsplash 外链 + 修复 §4 中 API-1~API-7 契约一致性问题。

---

## 10. 关联文档

- `docs/weekly/2026-W37.md` — 本周任务定义
- `docs/design/UI_SPEC.md` — 视觉与 Token 规范
- `docs/agent-guides/04_API_CONTRACT.md` — API 契约
- `docs/agent-guides/02_FRONTEND_AGENT.md` — 前端 Agent 角色指南
- `docs/frontend/week6-frontend-acceptance.md` — W6 验收记录
- `docs/frontend/week7-frontend-acceptance.md` — W7 验收记录
- `docs/governance/CURRENT_STATE.md` — 现状基线（本周不更新）
- `docs/evidence/2026-W37/frontend/` — 基线截图落点
