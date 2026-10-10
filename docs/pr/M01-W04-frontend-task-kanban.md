# M01-W04 Frontend：项目里程碑、任务看板与首页任务摘要

## 改了什么

- 按冻结契约 `PROJECT_WORKSPACE_API.md` §8–§13 接入里程碑与任务能力，mock 字段与真实接口完全一致，便于后续切换。
- 项目详情页改为 Tab 导航：概览 / 里程碑 / 任务看板 / 成员，支持 `?tab=` 深链定位；第 3 周的项目列表、详情基本信息与成员管理保持不变。
- 新增里程碑 Tab：卡片列表 + 进度条 + 创建弹窗，进度由 `taskCount/taskDone` 计算，状态按 PLANNED/IN_PROGRESS/COMPLETED 展示。
- 新增任务看板：桌面端四列（TODO/IN_PROGRESS/BLOCKED/DONE）+ 任务卡片；390×844 下改为纵向分组并默认折叠，点击分组头展开。
- 状态变更走卡片操作菜单（不做拖拽），菜单项由状态机推导，只展示合法转换。
- 新增任务详情抽屉与创建/编辑任务表单，覆盖负责人、里程碑、优先级、截止日期与阻塞原因。
- 首页 tasks 区域从 `NOT_AVAILABLE` 切换为 `READY`：统计（todo/inProgress/blocked/doneThisWeek）+ 最近 5 条任务，点击跳转对应项目看板。
- 乐观锁冲突：编辑时携带 `version`，冲突返回 409 `VERSION_CONFLICT`，由 `promptVersionConflict` 弹出可操作提示并引导刷新；拦截器对该 reason 静默，避免与页面提示重复。
- 权限收敛到 `projectPermission.js`：项目角色四档 + 任务负责人特殊权限，OBSERVER 全只读、MEMBER 仅能操作自己的任务。
- 新增 W40 契约测试与三视口截图脚本，产出 30 张真实浏览器 PNG 与 `e2e-result.json`。

## 如何验证

~~~powershell
cd frontend
npm ci
node src/api/__tests__/w40-contract-test.mjs
node src/api/__tests__/w39-contract-test.mjs
node src/api/__tests__/w38-contract-test.mjs
VITE_USE_MOCK=true npm run build
npm run preview
node .w40-e2e.mjs
~~~

验证结果：

- W40 契约测试：72/72 通过。
- W39 回归：32/32 通过。
- W38 回归：36/36 通过。
- Vite 生产构建：通过（主 JS 约 2.54 MB，存在 chunk size 警告，与第 3 周一致）。
- 三视口（1440×900 / 1280×800 / 390×844）共 30 张截图，横向溢出 0 处。

视觉证据见 `docs/evidence/2026-W40/frontend/README.md`。

## 契约影响

- API：不新增接口；按冻结契约消费 §8–§13 的里程碑 3 个 + 任务 5 个端点。
- 首页：`GET /api/workbench/overview` 的 tasks 区域由 `NOT_AVAILABLE` 切换为 `READY`，字段为 todo/inProgress/blocked/doneThisWeek + list（最近 5 条）。
- 错误码：按 reason 处理 400（INVALID_PARAMETER / BLOCK_REASON_REQUIRED / ASSIGNEE_NOT_MEMBER / MILESTONE_NOT_FOUND）、404（TASK_NOT_FOUND）、409（VERSION_CONFLICT / TASK_INVALID_TRANSITION）。
- 权限：里程碑与任务操作细化到项目角色与任务负责人；SYSTEM_ADMIN 覆盖 OWNER，TEACHER 覆盖只读。
- 数据库、MQTT、鉴权：无变化；不改动第 3 周已实现的考勤与 IoT 代码。

## 联调重点

1. 老师（OWNER）创建里程碑 → 创建任务并分配给成员 → 成员在看板看到任务。
2. 成员将任务从 TODO 开始为 IN_PROGRESS，再标记 DONE，确认首页任务统计同步更新。
3. 任务进入 BLOCKED 时校验 blockReason 必填（2–500 字符）。
4. 两个客户端用相同 version 更新同一任务，后提交者收到 409 VERSION_CONFLICT 并弹出刷新提示。
5. MEMBER 尝试创建/取消任务被拒（403），OBSERVER 所有写操作入口隐藏。
6. 项目处于 PAUSED/COMPLETED/ARCHIVED 时创建与状态变更被拒。
7. 三视口下看板无溢出，390×844 为纵向分组布局。

## 已知风险

- 构建产物主 JS 约 2.54 MB，Vite 有 chunk size 警告；不阻塞本周契约联调，后续按页面拆分异步路由。
- 截图使用 mock 构建（`VITE_USE_MOCK=true`）与契约同形的注入登录态，只证明布局、权限与交互可见性；真实后端联调仍需按 `docs/weekly/2026-W40.md` §10 的 10 步脚本执行。
- 真实模式切换需在后端合入 dev 后同步分支，将 `VITE_USE_MOCK` 置为 `false` 并联调。