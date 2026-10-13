# M02-W02 前端三视口视觉证据（实验模板域）

> 分支：`feature/m02-w02-frontend-experiment-templates`
>
> 检查日期：2026-10-13
>
> 渲染方式：Playwright 驱动本机 Microsoft Edge，页面由实际 Vue 应用渲染（`VITE_USE_MOCK=true` 构建）

## 检查范围

本次保留 45 张真实 PNG（3 视口 × 15 场景），验证第 6 周新增的实验模板域功能：

| 视口 | 侧栏入口 | 系统模板列表 | 项目模板 | 列表空状态 | 模板详情 | 已废弃版本 | 详情空状态 | 发布确认 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1440×900 | `1440x900/template-nav.png` | `1440x900/template-list.png` | `1440x900/template-list-project.png` | `1440x900/template-list-empty.png` | `1440x900/template-detail.png` | `1440x900/template-detail-deprecated.png` | `1440x900/template-detail-empty.png` | `1440x900/template-publish-confirm.png` |
| 1280×800 | `1280x800/template-nav.png` | `1280x800/template-list.png` | `1280x800/template-list-project.png` | `1280x800/template-list-empty.png` | `1280x800/template-detail.png` | `1280x800/template-detail-deprecated.png` | `1280x800/template-detail-empty.png` | `1280x800/template-publish-confirm.png` |
| 390×844 | `390x844/template-nav.png` | `390x844/template-list.png` | `390x844/template-list-project.png` | `390x844/template-list-empty.png` | `390x844/template-detail.png` | `390x844/template-detail-deprecated.png` | `390x844/template-detail-empty.png` | `390x844/template-publish-confirm.png` |

| 视口 | 编辑器字段列表 | Schema 预检 | 预览填写 | 编辑器只读守卫 | 单元模板关联 | 关联废弃版本 | 版本选择器 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1440×900 | `1440x900/template-editor.png` | `1440x900/template-editor-schema.png` | `1440x900/template-editor-preview.png` | `1440x900/template-editor-readonly.png` | `1440x900/learning-unit-template.png` | `1440x900/learning-unit-deprecated.png` | `1440x900/template-picker.png` |
| 1280×800 | `1280x800/template-editor.png` | `1280x800/template-editor-schema.png` | `1280x800/template-editor-preview.png` | `1280x800/template-editor-readonly.png` | `1280x800/learning-unit-template.png` | `1280x800/learning-unit-deprecated.png` | `1280x800/template-picker.png` |
| 390×844 | `390x844/template-editor.png` | `390x844/template-editor-schema.png` | `390x844/template-editor-preview.png` | `390x844/template-editor-readonly.png` | `390x844/learning-unit-template.png` | `390x844/learning-unit-deprecated.png` | `390x844/template-picker.png` |

## 数据与权限条件

- 构建时注入 `VITE_USE_MOCK=true`，前端走 mock 分支，不依赖真实后端。
- 登录态由浏览器 init script 注入 localStorage，模拟用户 id=2「张同学」、`primaryRoleKey=TEACHER`：
  - TEACHER 为系统模板管理角色，可见全部模板（含草稿/废弃版本）与全部路线（含归档路线），便于一次呈现版本状态标签、废弃提示与管理入口。
  - 该用户同时是项目 10 的 OWNER，可展示「复制到项目」入口。
- mock 数据与 `EXPERIMENT_TEMPLATE_API.md` v1.0 §2/§3 字段一致；版本号、`latestVersion`、`publishedVersionCount` 等聚合字段由「后端」（mock）计算，前端只消费展示。
- 截图用于布局、权限与交互可见性验证，不冒充真实后端联调证据。

## 自动检查结果

- 三个视口下 45 张截图均无横向溢出（`e2e-result.json` 中 `horizontalOverflow: 0`）。
- 侧栏入口：「学习实验台」下拆分「学习路线」与「实验模板」两个子菜单，实验模板使用紫色小面积识别色；390×844 下由抽屉菜单呈现。
- 系统模板列表：三列卡片（1280 两列、390 单列），含范围标签、分类标签、最新版本、已发布版本数、创建人/更新时间与「查看 / 复制到项目」动作。
- 列表默认状态筛选：管理者（TEACHER）默认展示「全部」状态，仅含草稿的模板（「电路基础实验」，未发布 / 已发布版本 0）可见；非管理者不下发 `status`，由接口按契约默认 `PUBLISHED` 仅返回已发布版本。
- 项目模板 tab：卡片携带所属项目标签。
- 列表空状态：搜索无结果显示「没有符合筛选条件的实验模板」，并提供「清除筛选条件」按钮。
- 模板详情：左侧版本历史时间线（DRAFT 置顶、状态标签），右侧选中版本的字段结构与只读 Schema；默认选中草稿版本。
- 已废弃版本：时间线节点删除线 + 灰色标签，详情区显示废弃原因与操作信息。
- 详情空状态：访问不存在模板（/experiment/templates/9999）显示「模板不存在或不可见」。
- 发布确认：草稿版本「发布」触发确认对话框，展示发布后不可修改的提示。
- 编辑器：桌面端左侧字段列表 + 右侧属性面板，支持字段增删与拖拽排序；移动端切换为分步编辑（字段 / 预检 / 预览），避免双栏拥挤。
- Schema 预检：实时校验字段配置并以字段路径定位错误，原始 Schema 只读展示。
- 预览填写：按当前 Schema 实时渲染表单，测试数据可即时校验。
- 编辑器只读守卫：访问已发布版本（editor/2）显示不可编辑提示，禁止修改已发布版本。
- 单元模板关联：学习单元展示模板名称与版本号（PUBLISHED）。
- 关联废弃版本：归档路线单元关联的已废弃版本显示「已废弃」提示。
- 版本选择器：单元模板选择器仅列出可见的 PUBLISHED 版本，支持清除关联。

## 契约测试

```bash
cd frontend
node src/api/__tests__/w42-contract-test.mjs
# 90 passed, 0 failed
```

回归：

```bash
node src/api/__tests__/w41-contract-test.mjs   # 62 passed, 0 failed
node src/api/__tests__/w40-contract-test.mjs   # 72 passed, 0 failed
node src/api/__tests__/w39-contract-test.mjs   # 32 passed, 0 failed
node src/api/__tests__/w38-contract-test.mjs   # 36 passed, 0 failed
```

## 复现方式

```bash
cd frontend
$env:VITE_USE_MOCK='true'; npm run build
npm run preview          # http://localhost:4173
$env:W42_BASE_URL='http://localhost:4173'; node .w42-e2e.mjs   # 输出到本目录，并写入 e2e-result.json
```

本目录 PNG 只作为 mock 模式的视觉布局证据；真实前后端联调结果见 `docs/pr/M02-W02-frontend-experiment-templates.md` 的联调重点。
