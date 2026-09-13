# M01-W02 Frontend 交付物（Plan B：frontend-only）

> 分支：`feature/m01-w02-frontend-auth-dashboard`
> base：`4ea5bc1` (W37 baseline)
> HEAD：`754c229 feat(frontend): M01-W02 auth + role-based dashboard (plan B frontend-only)`
> bundle：`m01-w02-frontend-auth-dashboard.bundle`（2.8 MB）

## 一句话总结

第二周按"方案 B（frontend-only）"交付：

- 完成 W37 baseline 验收中的 `/login` 与 `/dashboard` 升级（workbench 6 区域）
- 完成 04_API_CONTRACT 全部依赖（`permission.js` / `userStore` / `request.js` / `mock.js`）
- 完成 UI_SPEC 16 token 全部落地到 `tokens.css` + `global.css`
- 完成三角色（SYSTEM_ADMIN / TEACHER / MEMBER）路由守卫与侧栏菜单派生
- 修复 API-1~10 取值错误，扩展为 `records/total` + `list/total` 双形态
- 7 态 `RegionState` 组件
- Node 单测 12 条全部通过
- 三视口 × 三角色 × 九页面 视觉证据 87 张 SVG

## 主要变更（116 文件 +6264/-468）

### 1. 鉴权与角色（核心）

| 文件 | 内容 |
| --- | --- |
| `frontend/src/utils/permission.js` | `ROLE` 枚举 + `hasAnyRole` 单一来源 |
| `frontend/src/store/user.js` | 用户 store 含 `primaryRoleKey` / `primaryRoleName` / `roles[]` |
| `frontend/src/utils/request.js` | 401 清 token + 跳 login；403 弹提示 + 跳 dashboard；同时发 `X-Token` 与 `Authorization` 兼容 |
| `frontend/src/api/workbench.js` | `getWorkbenchOverview` / `getAttendanceToday` / `getProjects` / `getTasks` / `getLearning` / `getNotifications` / `getDeviceReminder` |
| `frontend/src/utils/mock.js` | mock 数据，符合 `{list|records, total}` 形态 |
| `frontend/src/views/Login.vue` | 本地占位，移除 Unsplash，错误内联提示 |
| `frontend/src/views/errors/Forbidden.vue` + `NotFound.vue` | 错误页 |

### 2. 路由与布局

| 文件 | 内容 |
| --- | --- |
| `frontend/src/router/index.js` | 路由 `meta.roles` + `beforeEach` 守卫；`/iot/public` 不需要登录 |
| `frontend/src/layout/Index.vue` | 224 侧栏 + 56 顶栏 + 移动端折叠；按 `user.roles[]` 派生菜单 |

### 3. 工作台（核心）

| 文件 | 内容 |
| --- | --- |
| `frontend/src/views/Dashboard.vue` | 6 区域：attendance / projects / tasks / learning / notifications / deviceReminder；统一调 `getWorkbenchOverview` |
| `frontend/src/components/RegionState.vue` | 7 态：loading / empty / error / permission / success / offline / not-available |

### 4. UI Token（UI_SPEC 16 项）

`frontend/src/styles/tokens.css` 落地：

- 品牌：brand / brand-soft / brand-strong
- 状态：success / warn / danger / info（含 soft 变体）
- 角色：role-admin / role-teacher / role-keeper / role-member
- 文本：ink / muted / subtle
- 背景：bg / surface / surface-2
- 边框：border / border-strong
- 间距：space-1~space-6

### 5. API 修复（API-1~10 + API-16）

| API | 取值 | 修复 |
| --- | --- | --- |
| getProjects / getDevices | `res.records` | 维持 4ea5bc1 修复 |
| listAlerts / listRecommendations / listCommands / listOperationLogs | `res.list` | 维持 4ea5bc1 修复 |
| mock `listAlerts/listRecommendations/listOperationLogs` | 改用 `mockListPageResponse` | **本次新增** —— 此前返回 `records/total`，与契约 §2 的 `list/total` 不一致 |

### 6. 测试

`frontend/src/api/__tests__/w38-contract-test.mjs`：

- `ROLE` 枚举存在
- `hasAnyRole` 正/反向
- 路由 meta.roles 配置
- `request.js` 401/403 分流函数可调用
- `iot.js` mock 响应字段
- 7 态 RegionState
- 6 区域在 Dashboard 中存在
- 16 token 全部出现在 tokens.css
- `/iot/public` 不在需要登录列表

`node frontend/src/api/__tests__/w38-contract-test.mjs` → 12 pass / 0 fail。

### 7. 视觉证据

`docs/evidence/2026-W38/frontend/`：

- 3 视口（1440×900 / 1280×800 / 390×844）
- 4 角色（含 public 访客）
- 9 页面 + 公开页 `/iot/public`
- 87 张 SVG 静态展示卡

详见 `docs/evidence/2026-W38/frontend/README.md`。

## 硬约束遵守清单

- ✅ 未修改 `/iot/pm001`（W7 硬约束）—— 验证：`git show HEAD:frontend/src/views/iot/Pm001Live.vue` 与 W38 一致
- ✅ Admin 页面维持 `result.records` 用法（W37 修复）
- ✅ 全部请求封装在 `frontend/src/api/{iot,workbench}.js`
- ✅ `getWorkbenchOverview` 在 `unmount` 时 abort（`request.js` 内统一实现）
- ✅ W37 IoT 修复（4ea5bc1）未被覆盖

## 已知限制与遗留

| 项 | 状态 | 处理 |
| --- | --- | --- |
| Edge headless 截图不可用 | 本机限制 | 改 SVG 静态展示卡，README 中说明替换方法 |
| mock 数据 vs 后端真接口 | mock 优先 | 真实后端就绪后仅替换 `USE_MOCK=false` 即可 |
| `/iot/public` 看板真实数据 | 待后端 | 仍走 mock 列表 |
| mobile 390 视口下侧栏抽屉交互 | 基础已实现 | 后续可补动画细节 |
| `/admin/members` 与 `/admin/attendance` 仅 SYSTEM_ADMIN 可见 | 已通过路由守卫实现 | 待产品确认是否需要 403 软降级 |

## 应用方法

详见 `m01-w02-frontend-auth-dashboard.bundle.README.md`。
