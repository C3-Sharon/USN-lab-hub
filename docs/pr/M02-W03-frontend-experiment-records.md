# M02-W03 Frontend：实验记录、采集批次与导师反馈

## 改了什么

- 按冻结契约 `EXPERIMENT_RECORD_ACQUISITION_API.md` v1.0 接入实验记录域 13 个端点与数据采集域 20 个端点，mock 字段、状态、reason 与真实接口一致，切换真实后端时不改页面。
- 新增 API 层：
  - `frontend/src/api/experimentRecords.js`：记录聚合状态 `DRAFT / IN_REVIEW / CHANGES_REQUESTED / APPROVED / PUBLISHED / REVISING` 与修订状态 `EDITABLE / SUBMITTED / CHANGES_REQUESTED / APPROVED / PUBLISHED / SUPERSEDED` 分离；导出状态机 `RECORD_TRANSITIONS` / `REVISION_TRANSITIONS`、`canTransitionRecord` / `canTransitionRevision`、状态 meta 与权限判定；13 个端点覆盖记录创建/列表/详情、修订保存/列表/详情/新建、提交、导师反馈、发布、快照绑定和转项目。
  - `frontend/src/api/collection.js`：计划状态 `DRAFT / READY / ARCHIVED / CANCELLED`、批次状态 `PENDING / IMPORTING / REVIEW_REQUIRED / COMPLETED / FAILED / CANCELLED / ARCHIVED`、导入状态与来源类型 meta；计划/批次状态机、不可变判定、`canCreateSnapshot` 与权限判定；20 个端点覆盖计划创建/列表/详情/编辑/就绪/归档/取消、批次创建/列表/详情/记录分页/完成/取消/归档、CSV 上传/详情/预览/映射/确认、人工观测新增/更正/修订历史、快照创建。
  - `frontend/src/utils/idempotency.js`：`createIdempotencyKey`、`idempotencyHeaders`、`requestDigest`；创建记录、提交、反馈、发布、确认导入、完成批次、生成快照、转项目均携带 `Idempotency-Key`，更新与状态动作携带 `version`。
  - `frontend/src/utils/mockScenario.js`：`?mockState=loading|empty|error|permission|conflict|immutable` 状态注入，仅在 mock 构建生效；`mockTraceId` 保证错误态展示 traceId。
- 错误码归一化：`frontend/src/utils/apiError.js` 新增实验记录/采集域 reason 判定，含 `isExperimentNotFound`、`isExperimentDenied`、`isFeedbackDenied`、`isCollectionNotFound`、`isCollectionDenied`、`isProjectConversionDenied`、`isExperimentInvalidTransition`、`isRevisionImmutable`、`isExperimentContentInvalid`、`isProjectAlreadyConverted`、`isBatchInvalidTransition`、`isBatchImmutable`、`isBatchNotCompleted`、`isSnapshotAlreadyBound`、`isImportAlreadyConfirmed`、`isIdempotencyKeyReused`、`isTemplateVersionMismatch`、`isFileUnsupported`、`isImportMappingInvalid`、`isManualRecordInvalid`，以及 `errorTraceId` / `experimentContentErrors`。
- 新增实验记录域组件：`RecordStatusTag`、`RevisionStatusTag`、`SchemaFieldForm`（按固定模板版本渲染结构化字段）、`FeedbackDialog`（COMMENT / CHANGE_REQUEST / APPROVAL 三种动作与目标 revision 确认）、`SnapshotBindDialog`（仅 COMPLETED 批次）、`ConvertProjectDialog`（复用项目 code/name/summary/category 规则）。
- 新增采集域组件：`CollectionPlanDialog`（FILE / MANUAL 计划与指标定义）、`ManualRecordDialog`（方法/仪器/单位/观测时间/值/备注，更正必填 changeReason）。
- 新增页面：
  - `/experiment/records` 记录列表：范围（我的/项目/学习）、状态、项目、关键字筛选与分页；卡片展示状态、固定模板版本、作者、更新时间；空/错误/权限态。
  - `/experiment/records/new` 与 `/experiment/records/:id/edit/:revisionId`：归属选择、仅 PUBLISHED 模板版本、结构化字段、实验上下文；仅 EDITABLE 修订可保存；409 冲突弹出刷新对比；不可编辑修订显示只读守卫。
  - `/experiment/records/:id` 记录详情：当前发布版、当前工作修订、修订时间线、导师反馈、采集快照、项目来源/去向；按状态与角色收敛提交、要求修改、批准、发布、绑定快照、转项目动作。
  - `/projects/:id/collection` 项目数据采集：计划列表、来源、负责人、状态、最近批次；支持新建计划、就绪、归档、取消。
  - `/collection/plans/:id` 计划详情：指标定义、批次列表、状态动作与空/错误/权限态。
  - `/collection/batches/:id` 批次详情：来源、处理状态、原件 checksum、人工观测、记录摘要、错误样本；完成批次、生成快照、取消/归档；COMPLETED/ARCHIVED 显示冻结提示并隐藏写操作。
  - `/collection/imports/:id` CSV 导入：上传原件、前 50 行预览、列/单位/时间映射、确认导入；步骤条区分上传 / 映射 / 确认，映射错误定位到列。
- 路由新增 8 条 W43 路由；侧栏「学习实验台」新增「实验记录」入口；项目详情新增「数据采集」tab。
- 新增 W43 契约测试与三视口截图脚本，产出 69 张真实浏览器 PNG 与 `e2e-result.json`。

## 如何验证

~~~powershell
cd frontend
npm ci
node src/api/__tests__/w43-contract-test.mjs
node src/api/__tests__/w42-contract-test.mjs
node src/api/__tests__/w41-contract-test.mjs
node src/api/__tests__/w40-contract-test.mjs
node src/api/__tests__/w39-contract-test.mjs
node src/api/__tests__/w38-contract-test.mjs
$env:VITE_USE_MOCK='true'; npm run build
node .w43-e2e.mjs
~~~

验证结果：

- W43 契约测试：70/70 通过，覆盖状态机、权限、幂等、版本冲突、不可变、CSV 约束、人工修订、快照与项目转换。
- 回归：W42 90/90、W41 62/62、W40 72/72、W39 32/32、W38 36/36 全部通过。
- Vite 生产构建：通过（主 JS 约 2.88 MB，存在 chunk size 警告，与第 4/5/6 周一致）。
- 三视口（1440×900 / 1280×800 / 390×844）共 69 张截图，横向溢出 0 处。

视觉证据见 `docs/evidence/2026-W43/frontend/README.md`。

## 契约影响

- API：按冻结契约消费实验记录 13 个端点（前缀 `/api/experiment-records`）与采集 20 个端点（`/api/projects/{id}/collection-plans`、`/api/collection-plans`、`/api/collection-batches`、`/api/data-imports`、`/api/manual-records`）；不新增接口、状态或错误码。
- 幂等与乐观锁：创建记录、提交、反馈、发布、确认导入、完成批次、生成快照、转项目发送 `Idempotency-Key`；保存修订、提交、反馈、发布、完成批次、快照绑定、计划/批次状态动作发送 `version`。同 key 重放返回原结果，version 过期返回 409 `VERSION_CONFLICT`。
- 不可变性：发布修订、完成批次、原件 checksum、人工修订与快照不提供覆盖入口；完成/归档批次隐藏写操作并显示冻结提示。
- 快照：仅 COMPLETED 批次可生成 FULL_BATCH 快照；每个修订最多绑定 1 个快照；已提交修订不可替换。
- CSV：仅 UTF-8 / UTF-8 BOM，20 MiB / 100,000 行 / 100 列上限；同文件哈希幂等复用；映射变化追加导入修订；未确认前不写正式记录。
- 人工观测：每次更正追加修订，保留 changeReason、操作者、时间与前序修订 ID；完成批次后返回 409 `BATCH_IMMUTABLE`。
- 项目转换：仅 PUBLISHED 记录可转现有 PREPARING 项目；同一记录最多转换一次；重复转换返回原幂等结果或 409 `PROJECT_ALREADY_CONVERTED`。
- 权限：成员可创建/编辑自己的记录；TEACHER 或项目 OWNER/MAINTAINER 可评审；采集管理限 SYSTEM_ADMIN / TEACHER / 项目 OWNER / MAINTAINER；GUEST 不可访问。前端收敛入口，真正鉴权以后端为准。
- 数据库、MQTT、鉴权：无变化；不改动既有考勤、IoT、模板与学习代码。

## 联调重点

1. 创建项目/学习实验 → 返回 200，固定 `templateVersionId` 且生成 revision 1；DRAFT/DEPRECATED 模板返回 404/409，不回退 latest。
2. 保存合规内容 → 200；过期 version 保存 → 409 `VERSION_CONFLICT`，页面提示刷新对比且不覆盖。
3. 提交内容不符合固定版本 Schema → 400 `EXPERIMENT_CONTENT_INVALID`，字段级错误定位到具体字段。
4. 创建 FILE 计划/批次并上传 CSV → 返回原件 SHA-256；预览返回表头与最多 50 行；未确认前正式记录数为 0。
5. 保存映射并确认 → 200；同 `Idempotency-Key` 重放不重复计数；相同文件重传返回原导入任务。
6. 创建 MANUAL 批次并录入观测 → 200；更正未填 `changeReason` → 400；更正后修订历史保留旧值与操作者。
7. 完成批次 → 200；完成后修改映射/人工记录/再次确认 → 409 `BATCH_IMMUTABLE`；未完成批次生成快照 → 409 `BATCH_NOT_COMPLETED`。
8. 从 COMPLETED 批次生成快照并绑定修订 → 200；同一修订重复绑定 → 409 `SNAPSHOT_ALREADY_BOUND`。
9. 提交 → 200；普通成员批准自己记录 → 403 `FEEDBACK_OPERATION_DENIED`。
10. 导师 CHANGE_REQUEST → 200，原修订冻结为 `CHANGES_REQUESTED`，自动出现下一 `EDITABLE` 修订；在旧 revision 上要求修改 → 409 `EXPERIMENT_INVALID_TRANSITION`。
11. 成员修改并重新提交，导师 APPROVAL → 发布 → 200；发布修订只读，旧发布版仍可读。
12. 从 PUBLISHED 记录转项目 → 200，项目状态 `PREPARING`、操作者为 OWNER、来源关系存在；重复转换返回原结果或 409 `PROJECT_ALREADY_CONVERTED`。
13. 两个导师并发评审同一记录 → 一个成功，另一个 409 `VERSION_CONFLICT`；COMMENT 可并列追加。
14. 非项目成员与 GUEST → 404/403/401；审计含操作者、目标、动作、状态、requestId 与时间。
15. 关闭 mock（`VITE_USE_MOCK=false`）后按上述步骤逐步联调，并复查三视口与全部 UI 状态。

## 已知风险

- 后端 PR 尚未合入 `dev`，本 PR 当前基于 mock 契约同形数据；真实接口字段差异需在后端合入后同步 `dev` 并联调修正。
- 构建产物主 JS 约 2.88 MB，Vite 有 chunk size 警告；不阻塞本周契约联调，后续按页面拆分异步路由。
- 截图使用 mock 构建（`VITE_USE_MOCK=true`）与契约同形的注入登录态（TEACHER），只证明布局、权限与交互可见性；真实后端联调需按周文档 §10 脚本执行。
- 前端只镜像契约冻结的状态机与错误码，不自行扩展状态；真实并发与幂等以后端事务为准。
- 本周不做 MQTT v2、Serial/BLE、Edge Collector、完整质量引擎、大波形、RAG/Agent 与新中间件。
