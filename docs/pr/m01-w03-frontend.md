## M01-W03 前端：项目工作台

### 实现范围

| 模块 | 内容 |
| --- | --- |
| API 层 | `projects.js`：创建/列表/详情/添加成员 4 个端点 + mock 数据 |
| 项目列表 | `/projects`：卡片布局、分页、状态过滤、搜索、空状态 |
| 项目详情 | `/projects/:id`：基本信息、成员列表、状态/角色标签、添加成员按钮（OWNER/MAINTAINER 可见） |
| 创建项目 | `CreateProjectDialog.vue`：编号/名称/简介/分类表单 + 字段验证 |
| 添加成员 | `AddMemberDialog.vue`：学工号输入 + 角色选择（维护者/成员/观察者，不含 OWNER） |
| 工作台 | Dashboard projects 区域从 `NOT_AVAILABLE` 切换为 `READY`，展示参与项目列表 |
| 路由/菜单 | 注册 `/projects`、`/projects/:id`；侧栏新增"项目工作台"入口；旧 `/iot/projects` 保留 |

### 契约对齐

- 严格匹配 `docs/contracts/PROJECT_WORKSPACE_API.md` 字段定义
- mock 响应直接 resolve data（与 axios 拦截器解包行为一致）
- 错误响应 reject `{ code, msg, reason }`

### 验证

- `node src/api/__tests__/w39-contract-test.mjs`：30 项通过
- `npm run build`：构建成功
- mock 模式三视口截图：1440×900 / 1280×800 / 390×844 各 6 张 → `docs/evidence/2026-W39/frontend/`

### 真实后端联调结论（2026-09-28）

- 关闭默认业务 mock（`VITE_USE_MOCK=false`），经 Vite `/api` 代理连接第 3 周真实后端（MySQL `usn_hub` + Flyway v7 + Redis 7.0.8）。
- 接口级：15/15 断言通过，覆盖幂等、异角色冲突、编号冲突、用户不存在、非成员隐藏、角色权限不足、未鉴权七类边界 → `docs/evidence/2026-W39/integration-real-api.md`
- UI 级：8/8 检查通过，「admin 登录 → 创建项目 → 添加成员 → 成员工作台可见」闭环成立 → `docs/evidence/2026-W39/frontend-real/`（15 张真实模式截图 + `e2e-result.json`）
- 真实 API 模式 `npm run build` 通过。
- 复现脚本：`frontend/.w39-e2e.mjs`（需 `playwright-core` 与本机 Microsoft Edge）。

> 说明：PR #34 已合并进 `dev`（merge commit `9b14d7e`），故原「转为 Ready for review」一步不再适用；本文件随真实联调证据一并补齐。
