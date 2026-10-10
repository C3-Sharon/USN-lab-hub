# M02-W01 Frontend：学习实验域（路线列表、详情时间线、首页学习区域）

## 改了什么

- 按冻结契约 `LEARNING_EXPERIMENT_API.md` v1.0 §11 接入学习实验域 14 个端点，mock 字段与真实接口完全一致，便于后续切换。
- 新增 API 层 `frontend/src/api/learning.js`：路线状态（DRAFT/PUBLISHED/ARCHIVED）、难度（BEGINNER/INTERMEDIATE/ADVANCED）、学习状态（NOT_STARTED/IN_PROGRESS/COMPLETED）三组枚举与 meta，路线状态机（DRAFT→PUBLISHED/ARCHIVED，PUBLISHED→ARCHIVED，ARCHIVED 终态），mock 存储、参数校验守卫与 14 个接口函数；权限收敛到 `canManageLearning`（SYSTEM_ADMIN/TEACHER）与 `canLearn`。
- 错误码归一化：`frontend/src/utils/apiError.js` 新增 `isLearningNotFound` / `isLearningArchived` / `isLearningNotEnrolled` / `isLearningDenied` / `isLearningEmptyRoadmap` / `isLearningInvalidTransition`，供页面按 reason 分流空态/权限态。
- 新增学习域组件：`RoadmapCover`（封面占位，标题首字 + 紫色系）、`ProgressBar`（线性进度条）、`DifficultyTag`（难度标签，紫色系小面积识别）。
- 新增路线列表页 `/learning`：三列卡片（1280 两列、移动端单列），含封面占位、难度标签、状态标签、进度条、搜索/难度/状态筛选、排序、分页与空状态。
- 新增路线详情页 `/learning/:id`：左概览（封面、难度、状态、时长/阶段数/人数、进度条、开始/继续学习）+ 右阶段时间线；单元勾选完成/取消完成（幂等），进度条与阶段计数同步；桌面端阶段默认全部展开，移动端默认折叠，点击阶段头折叠/展开；「继续学习」滚动定位到首个未完成阶段。
- 路由新增 `/learning` 与 `/learning/:id`；侧栏新增一级入口「学习实验台」（`Reading` 图标）；顶栏副标题区分学习域。
- 首页 learning 区域由 `NOT_AVAILABLE` 切换为 `READY`：统计（进行中/已完成）+ 最近 3 条路线，区域紫色顶边与紫色统计数字，与项目/任务蓝色区域形成学习/项目视觉区分。
- 新增 W41 契约测试与三视口截图脚本，产出 24 张真实浏览器 PNG 与 `e2e-result.json`。

## 如何验证

~~~powershell
cd frontend
npm ci
node src/api/__tests__/w41-contract-test.mjs
node src/api/__tests__/w40-contract-test.mjs
node src/api/__tests__/w39-contract-test.mjs
node src/api/__tests__/w38-contract-test.mjs
$env:VITE_USE_MOCK='true'; npm run build
npm run preview
$env:W41_BASE_URL='http://localhost:4173'; node .w41-e2e.mjs
~~~

验证结果：

- W41 契约测试：62/62 通过。
- W40 回归：72/72 通过；W39 回归：32/32 通过；W38 回归：36/36 通过。
- Vite 生产构建：通过（主 JS 约 2.57 MB，存在 chunk size 警告，与第 4 周一致）。
- 三视口（1440×900 / 1280×800 / 390×844）共 24 张截图，横向溢出 0 处。

视觉证据见 `docs/evidence/2026-W41/frontend/README.md`。

## 契约影响

- API：不新增接口；按冻结契约消费 §11 的路线 5 个 + 阶段 3 个 + 单元 2 个 + 成员学习 4 个，共 14 个端点。
- 首页：`GET /api/workbench/overview` 的 learning 区域由 `NOT_AVAILABLE` 切换为 `READY`，字段为 `inProgressCount` / `completedCount` / `list`（最近 3 条）。
- 错误码：按 reason 处理 400（INVALID_PARAMETER / LEARNING_NOT_ENROLLED）、403（LEARNING_OPERATION_DENIED / ACCESS_DENIED）、404（LEARNING_ROADMAP_NOT_FOUND / LEARNING_STAGE_NOT_FOUND / LEARNING_UNIT_NOT_FOUND）、409（LEARNING_INVALID_TRANSITION / LEARNING_ARCHIVED / LEARNING_EMPTY_ROADMAP）。
- 进度：`progress` 只由后端计算，前端仅消费展示，不在前端拼接百分比。
- 权限：学习域独立矩阵；SYSTEM_ADMIN/TEACHER 管理路线、阶段、单元与状态；MEMBER/STOCK_KEEPER 查看已发布路线、开始学习、标记/取消单元完成；GUEST 不可访问。
- 数据库、MQTT、鉴权：无变化；不改动既有项目、考勤与 IoT 代码。

## 联调重点

1. 老师（TEACHER）确认预置路线为 PUBLISHED，成员列表中可见「嵌入式硬件入门」。
2. 成员列表不含 DRAFT 路线；直接访问 DRAFT 路线返回 404，前端展示「学习路线不存在或不可见」。
3. 成员查看路线详情，阶段与单元结构完整，未开始时单元勾选框禁用。
4. 成员点击「开始学习」→ 返回 200 且 `progress=0`；再次点击仍返回 200，不产生重复记录（幂等）。
5. 成员勾选单元完成 → `roadmapProgress.progress` 更新，进度条与阶段计数同步；重复标记/取消均返回 200（幂等）。
6. 首页 learning 区域同步更新：`state=READY`，`list` 包含该路线，统计数字随之变化。
7. 归档路线：不可开始新学习（409 LEARNING_ARCHIVED），已有学习记录保留，前端动作区显示「已归档，不可开始」。
8. 三视口下列表与详情无溢出；390×844 为单列布局、阶段默认折叠；学习入口与项目工作台视觉区分清晰。

## 已知风险

- 构建产物主 JS 约 2.57 MB，Vite 有 chunk size 警告；不阻塞本周契约联调，后续按页面拆分异步路由。
- 截图使用 mock 构建（`VITE_USE_MOCK=true`）与契约同形的注入登录态（TEACHER），只证明布局、权限与交互可见性；真实后端联调结果见 `docs/evidence/2026-W41/real-api-integration.md`。
- 详情契约不包含列表摘要字段 learnerCount；真实模式下详情页缺失时显示“暂未统计”，不由前端伪造人数。
- 列表页空状态复用全局 `RegionState`（el-empty），未透传「清除筛选条件」按钮；与既有页面一致，如需可后续统一增强。
- 真实模式已使用 `VITE_USE_MOCK=false` 完成登录、路线浏览、加入、完成/取消和首页进度同步联调。
