# M02-W03 前端三视口视觉证据（实验记录、采集批次与导师反馈）

> 分支：`feature/m02-w03-frontend-experiment-records`
>
> 检查日期：2026-10-22
>
> 渲染方式：Playwright 驱动本机 Microsoft Edge，页面由实际 Vue 应用渲染（`VITE_USE_MOCK=true` 构建）

## 检查范围

本次保留 69 张真实 PNG（3 视口 × 23 场景），覆盖第 7 周新增的实验记录域与数据采集域：

| 场景 | 验证内容 | 文件（每个视口同名） |
| --- | --- | --- |
| `record-nav` | 侧栏「学习实验台 > 实验记录」入口；移动端为抽屉菜单 | `1440x900/record-nav.png` |
| `record-list` | 记录列表：范围/状态/项目/关键字筛选、卡片、分页 | `1440x900/record-list.png` |
| `record-list-loading` | 列表 loading 骨架屏 | `1440x900/record-list-loading.png` |
| `record-list-empty` | 列表空状态与清除筛选入口 | `1440x900/record-list-empty.png` |
| `record-list-error` | 加载失败：reason、traceId 与重试 | `1440x900/record-list-error.png` |
| `record-detail-published` | 已发布记录：发布修订、反馈时间线、快照与转项目入口 | `1440x900/record-detail-published.png` |
| `record-detail-changes` | 要求修改：不可变原修订 + 自动复制的 EDITABLE 修订 | `1440x900/record-detail-changes.png` |
| `record-detail-revising` | 修订中：旧发布版仍有效 + 当前可编辑修订 + 绑定快照 | `1440x900/record-detail-revising.png` |
| `record-detail-permission` | 无权查看：404 隐藏资源与只读说明 | `1440x900/record-detail-permission.png` |
| `record-editor` | 编辑器：固定模板版本、结构化字段、实验上下文、版本号 | `1440x900/record-editor.png` |
| `record-create` | 新建记录：归属选择、模板版本选择、revision 1 | `1440x900/record-create.png` |
| `record-save-conflict` | 保存冲突：409 `VERSION_CONFLICT` 提示刷新对比 | `1440x900/record-save-conflict.png` |
| `record-convert-dialog` | 转项目草案确认对话框（复用项目 code/name 规则） | `1440x900/record-convert-dialog.png` |
| `record-snapshot-dialog` | 绑定采集快照：仅 COMPLETED 批次、每修订最多 1 个 | `1440x900/record-snapshot-dialog.png` |
| `collection-plans` | 项目「数据采集」计划列表：来源、负责人、状态、最近批次 | `1440x900/collection-plans.png` |
| `collection-plans-empty` | 计划列表空状态 | `1440x900/collection-plans-empty.png` |
| `collection-plan-detail` | 计划详情：指标定义、批次列表、状态动作 | `1440x900/collection-plan-detail.png` |
| `collection-batch-review` | 批次待复核：CSV 原件、记录摘要、错误样本、完成入口 | `1440x900/collection-batch-review.png` |
| `collection-batch-completed` | 已完成批次：checksum 冻结、只读提示、FULL_BATCH 快照 | `1440x900/collection-batch-completed.png` |
| `collection-batch-manual` | 人工观测批次：方法/仪器/单位/时间与只读证据 | `1440x900/collection-batch-manual.png` |
| `collection-manual-history` | 人工观测修改历史：旧值/新值/原因/操作者/时间 | `1440x900/collection-manual-history.png` |
| `collection-import-mapping` | CSV 导入：前 50 行预览、列/单位/时间映射、确认 | `1440x900/collection-import-mapping.png` |
| `collection-manual-dialog` | 新增人工观测对话框（MANUAL 批次） | `1440x900/collection-manual-dialog.png` |

同一组文件名分别位于 `1440x900/`、`1280x800/`、`390x844/`。

## 数据与权限条件

- 构建时注入 `VITE_USE_MOCK=true`，前端走契约同形 mock 分支，不依赖真实后端。
- 登录态由浏览器 init script 注入 localStorage，模拟用户 id=2「张同学」、`primaryRoleKey=TEACHER`：
  - TEACHER 可评审实验记录，也是项目 10 的 OWNER，可管理采集计划/批次并执行快照与转项目。
  - 该用户是记录 301/308 的作者，可编辑草稿与新建记录。
- mock 锚点：记录 301 DRAFT、302 IN_REVIEW、303 CHANGES_REQUESTED、305 PUBLISHED、306 已转项目、307 REVISING、308 学习草稿；计划 401 FILE READY、402 MANUAL READY、404 ARCHIVED；批次 601 COMPLETED、602 REVIEW_REQUIRED、603 MANUAL COMPLETED、604 FAILED；导入 701 CONFIRMED、702 MAPPING_REQUIRED。
- 状态注入通过 `?mockState=loading|empty|error|permission|conflict|immutable` 实现，真实接口构建完全忽略该参数。
- 截图用于布局、权限与交互可见性验证，不冒充真实后端联调证据。

## 自动检查结果

- 三个视口下 69 张截图均无横向溢出（`e2e-result.json` 中 `horizontalOverflow: 0`）。
- 记录列表：1440 三列卡片、1280 两列、390 单列；筛选区在移动端折叠，卡片保留状态、模板版本、作者与更新时间。
- 记录详情：发布版、工作修订、修订时间线、导师反馈、采集快照与项目来源/去向同时可见；不可变修订只读并显示原因。
- 编辑器：桌面双栏（字段 + 上下文），移动端纵向排列；固定模板版本与 revision 版本号常驻；保存失败弹出 409 刷新提示。
- 采集批次：完成批次显示 checksum、计数与时间范围冻结提示；待复核批次保留完成入口；FAILED 批次显示失败原因且不提供重试覆盖。
- CSV 导入：步骤条区分上传 / 预览 / 映射 / 确认；映射错误定位到列；未确认前不写正式记录。
- 人工观测：修改历史以追加修订呈现，旧值保留；移动端历史对话框可滚动。
- 权限与错误：`record-detail-permission` 为 404 隐藏资源态；`record-list-error` 展示 reason 与 traceId。

## 契约测试

```bash
cd frontend
node src/api/__tests__/w43-contract-test.mjs
# 70 passed, 0 failed
```

回归：

```bash
node src/api/__tests__/w42-contract-test.mjs   # 90 passed, 0 failed
node src/api/__tests__/w41-contract-test.mjs   # 62 passed, 0 failed
node src/api/__tests__/w40-contract-test.mjs   # 72 passed, 0 failed
node src/api/__tests__/w39-contract-test.mjs   # 32 passed, 0 failed
node src/api/__tests__/w38-contract-test.mjs   # 36 passed, 0 failed
```

## 复现方式

```bash
cd frontend
$env:VITE_USE_MOCK='true'; npm run build
node .w43-e2e.mjs        # 内置静态服务器监听 127.0.0.1:4173，输出到本目录并写入 e2e-result.json
```

脚本默认使用 `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`；可通过 `W43_EDGE`、`W43_BASE_URL`、`W43_OUT` 覆盖。

本目录 PNG 只作为 mock 模式的视觉布局证据；真实前后端联调结果见 `docs/pr/M02-W03-frontend-experiment-records.md` 的联调重点。
