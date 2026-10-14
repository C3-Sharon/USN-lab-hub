# M02-W02 Frontend：实验模板与版本管理（模板列表、详情时间线、编辑器、学习单元模板关联）

## 改了什么

- 按冻结契约 `EXPERIMENT_TEMPLATE_API.md` v1.0 接入实验模板域 12 个端点，mock 字段与真实接口完全一致，便于后续切换。
- 新增 API 层 `frontend/src/api/experimentTemplates.js`：
  - 枚举与 meta：模板范围（SYSTEM/PROJECT）、版本状态（DRAFT/PUBLISHED/DEPRECATED）、字段类型（string/number/integer/boolean）、控件（input/input-number/select/switch/textarea）。
  - 版本状态机：`DRAFT → PUBLISHED → DEPRECATED`（DEPRECATED 终态），含 `canTransitionTemplateVersion` / `isTerminalTemplateVersionStatus`。
  - 版本号规则：DRAFT 使用 `0.X.0`，PUBLISHED 固定 `1.Y.0`；发布时自动滚动下一个 DRAFT。
  - JSON Schema 受控子集校验（§4）：仅扁平对象，支持 enum/required/default/minimum/maximum/minLength/maxLength 与扩展元数据 title/description/unit/displayOrder/widget；明确拒绝嵌套对象、数组、`$ref`、`oneOf/anyOf/allOf`、条件 Schema、pattern、format、脚本执行；单版本最多 50 字段；字段 key 限小写字母/数字/下划线。另提供数据校验 `validateDataAgainstSchema`（预览用）。
  - mock 存储与权限收敛：`canAccessTemplates` / `canManageSystemTemplates` / `canManageProjectTemplates` / `canManageTemplate`。
  - 12 个接口函数：创建、列表、详情、编辑、复制；版本列表、版本详情、编辑 DRAFT、发布、废弃、已发布版本列表；数据校验。
- 错误码归一化：`frontend/src/utils/apiError.js` 新增 `isTemplateNotFound` / `isTemplateVersionNotFound` / `isTemplateDenied` / `isTemplateSchemaInvalid`（含 `extractSchemaErrors`）/ `isTemplateNotSystem` / `isTemplateScopeImmutable` / `isTemplateVersionNotEditable` / `isTemplateVersionNotPublishable` / `isTemplateVersionNotDeprecatable` / `isTemplateVersionConflict`，供页面按 reason 分流空态/权限态/冲突态。
- 新增实验模板域组件：`TemplateFormDialog`（创建/编辑基本信息）、`CopyTemplateDialog`（复制到项目）、`VersionTimeline`（版本历史时间线，DRAFT 置顶、支持选中/编辑/发布/废弃）、`VersionStatusTag`（状态标签，废弃态灰色删除线）、`SchemaPreview`（按 Schema 动态渲染表单，支持测试数据实时校验）。
- 新增模板列表页 `/experiment/templates`：系统/项目 tab 切换、关键字搜索、项目/分类/状态筛选与排序、分页、卡片网格（1440 三列 / 1280 两列 / 390 单列）、创建入口、「复制到项目」入口与空/权限态。
  - 管理者（SYSTEM_ADMIN/TEACHER）默认状态筛选为「全部」，保证仅含草稿的模板可见；非管理者不下发 `status`，由接口按契约默认 `PUBLISHED` 仅返回已发布版本。
- 新增模板详情页 `/experiment/templates/:id`：左侧版本历史时间线（含废弃版本删除线与废弃原因）+ 右侧选中版本的字段结构、只读原始 Schema、预览；支持编辑基本信息、复制、发布确认对话框、废弃确认对话框、进入草稿编辑器；含模板不存在/不可见的空态。
- 新增模板编辑器 `/experiment/templates/:id/editor/:versionId`：桌面端左侧字段列表 + 右侧属性面板，支持字段增删、拖拽排序与类型/控件/约束配置；Schema 实时预检（按字段路径定位错误）与原始 Schema 只读；预览填写与测试数据校验；保存草稿与发布。移动端切换为分步编辑（字段 / Schema 预检 / 预览），避免双栏拥挤。已发布版本进入编辑器显示只读守卫，禁止修改。
- 学习单元模板关联：新增 `UnitTemplatePicker`（仅列出可见的 PUBLISHED 版本，支持清除关联）；`LearningDetail.vue` 接入关联选择器，单元展示模板名称与版本号，并对关联的已废弃版本显示提示；`learning.js` 为单元补充 additive 的 `templateVersionId` / `templateVersion` / `templateVersionStatus` 字段与关联校验（仅允许 PUBLISHED 版本）。
- 路由新增 `/experiment/templates` 系列；侧栏「学习实验台」拆分为「学习路线」与「实验模板」两个子菜单，实验模板使用紫色小面积识别色；顶栏副标题区分「实验模板与不可变版本管理」。
- 新增 W42 契约测试与三视口截图脚本，产出 45 张真实浏览器 PNG 与 `e2e-result.json`。

## 如何验证

~~~powershell
cd frontend
npm ci
node src/api/__tests__/w42-contract-test.mjs
node src/api/__tests__/w41-contract-test.mjs
node src/api/__tests__/w40-contract-test.mjs
node src/api/__tests__/w39-contract-test.mjs
node src/api/__tests__/w38-contract-test.mjs
$env:VITE_USE_MOCK='true'; npm run build
npm run preview
$env:W42_BASE_URL='http://localhost:4173'; node .w42-e2e.mjs
~~~

验证结果：

- W42 契约测试：90/90 通过。
- 回归：W41 62/62、W40 72/72、W39 32/32、W38 36/36 全部通过。
- Vite 生产构建：通过（主 JS 约 2.66 MB，存在 chunk size 警告，与第 4/5 周一致）。
- 三视口（1440×900 / 1280×800 / 390×844）共 45 张截图，横向溢出 0 处。

视觉证据见 `docs/evidence/2026-W42/frontend/README.md`。

## 契约影响

- API：按冻结契约消费 §2/§3 的模板 5 个 + 版本 6 个 + 数据校验 1 个，共 12 个端点，前缀 `/api/experiment/templates`；不新增接口。
- 学习单元接口新增 `templateVersionId` / `templateVersion` 字段（additive，不破坏第 5 周兼容性）；前端额外消费关联版本的 `templateVersionStatus` 以呈现废弃提示。
- 错误码：按 reason 处理 400（INVALID_PARAMETER / SCHEMA_INVALID / SCHEMA_EMPTY）、403（TEMPLATE_OPERATION_DENIED / ACCESS_DENIED）、404（TEMPLATE_NOT_FOUND / TEMPLATE_VERSION_NOT_FOUND）、409（TEMPLATE_VERSION_NOT_EDITABLE / TEMPLATE_VERSION_NOT_PUBLISHABLE / TEMPLATE_VERSION_NOT_DEPRECATABLE / TEMPLATE_SCOPE_IMMUTABLE / TEMPLATE_VERSION_CONFLICT）。
- 版本不可变性：已发布版本的 `schemaJson` 前端不提供修改入口；编辑器只读守卫阻止编辑非 DRAFT 版本。
- 版本号与聚合：`latestVersion` / `publishedVersionCount` 由后端计算，前端只消费展示；发布后自动滚动 DRAFT 由后端保证。
- 权限：SYSTEM_ADMIN/TEACHER 管理系统与项目模板；项目 OWNER/MAINTAINER 管理本项目模板；MEMBER/STOCK_KEEPER 仅查看已发布模板；GUEST 不可访问（前端收敛，真正鉴权以后端为准）。
- 数据库、MQTT、鉴权：无变化；不改动既有项目、考勤与 IoT 代码。

## 联调重点

1. 管理员创建系统模板 → 返回 200，`scope=SYSTEM`、`publishedVersionCount=0`，自动生成 `0.1.0` DRAFT。
2. 编辑 DRAFT Schema → 返回 200；保存含 `array` 类型的 Schema 返回 400 `SCHEMA_INVALID`，`errors` 含字段路径，前端定位到具体字段。
3. 发布 DRAFT → 返回 200，`publishedVersion.version=1.0.0`、`status=PUBLISHED`，`newDraftVersion.version=0.2.0`、`status=DRAFT`。
4. 编辑已发布版本 → 409 `TEMPLATE_VERSION_NOT_EDITABLE`，编辑器显示只读守卫。
5. 成员查看模板列表（`status=PUBLISHED`）→ 仅含已发布模板，看不到 DRAFT 相关信息；直接访问无已发布版本的模板返回 404，前端显示「模板不存在或不可见」。
6. 数据校验（预览）→ 返回 200，`valid` 与字段级错误符合契约。
7. 复制系统模板为项目模板 → 返回 200，`scope=PROJECT`、`projectId=目标项目`；DRAFT Schema 从源模板最新 PUBLISHED 版本复制。
8. 废弃已发布版本 → 返回 200，`status=DEPRECATED`；已关联单元仍可用并显示废弃提示，新关联只能选择 PUBLISHED 版本。
9. 并发发布同一模板 DRAFT → 一个成功，另一个返回 409 `TEMPLATE_VERSION_CONFLICT`，前端提示刷新后使用新 DRAFT 重新编辑。
10. 三视口下列表、详情与编辑器无溢出；390×844 单列布局、编辑器为分步编辑；实验模板与项目工作台视觉区分清晰。

## 已知风险

- 构建产物主 JS 约 2.66 MB，Vite 有 chunk size 警告；不阻塞本周契约联调，后续按页面拆分异步路由。
- 截图使用 mock 构建（`VITE_USE_MOCK=true`）与契约同形的注入登录态（TEACHER），只证明布局、权限与交互可见性；真实后端联调需按 §10 脚本执行。
- 前端只镜像契约冻结的 JSON Schema 受控子集，不实现完整 JSON Schema 引擎；真实模式下 Schema 校验以后端为准。
- 管理者列表默认「全部」状态为前端 UI 选择（契约接口 `status` 默认值为 `PUBLISHED`），用于避免仅含草稿的模板被默认过滤；非管理者行为与契约默认一致。
- 本周无模板/版本删除接口；前端不提供删除入口，已发布版本仅支持废弃。
