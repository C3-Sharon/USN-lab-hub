# M02-W01 前端三视口视觉证据（学习实验域）

> 分支：`feature/m02-w01-frontend-learning-roadmap`
>
> 检查日期：2026-10-09
>
> 渲染方式：Playwright 驱动本机 Microsoft Edge，页面由实际 Vue 应用渲染（`VITE_USE_MOCK=true` 构建）

## 检查范围

本次保留 24 张真实 PNG（3 视口 × 8 场景），验证第 5 周新增的学习实验域功能：

| 视口 | 首页学习区域 | 路线列表 | 侧栏入口 | 列表空状态 | 路线详情 | 单元完成交互 | 阶段折叠 | 详情空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1440×900 | `1440x900/workbench.png` | `1440x900/learning-list.png` | `1440x900/learning-nav.png` | `1440x900/learning-list-empty.png` | `1440x900/learning-detail.png` | `1440x900/learning-detail-unit-done.png` | `1440x900/learning-timeline-toggle.png` | `1440x900/learning-detail-empty.png` |
| 1280×800 | `1280x800/workbench.png` | `1280x800/learning-list.png` | `1280x800/learning-nav.png` | `1280x800/learning-list-empty.png` | `1280x800/learning-detail.png` | `1280x800/learning-detail-unit-done.png` | `1280x800/learning-timeline-toggle.png` | `1280x800/learning-detail-empty.png` |
| 390×844 | `390x844/workbench.png` | `390x844/learning-list.png` | `390x844/learning-nav.png` | `390x844/learning-list-empty.png` | `390x844/learning-detail.png` | `390x844/learning-detail-unit-done.png` | `390x844/learning-timeline-toggle.png` | `390x844/learning-detail-empty.png` |

## 数据与权限条件

- 构建时注入 `VITE_USE_MOCK=true`，前端走 mock 分支，不依赖真实后端。
- 登录态由浏览器 init script 注入 localStorage，模拟用户 id=2「张同学」、`primaryRoleKey=TEACHER`：
  - 角色为管理角色，列表可见全部路线（含草稿/归档），便于一次呈现难度标签（入门/进阶/高阶）与状态标签（草稿/已归档），并展示状态筛选。
  - 该用户已加入路线 1（进行中）、2（已完成），故进度条、阶段完成计数与单元勾选态可渲染。
- mock 数据与 `LEARNING_EXPERIMENT_API.md` v1.0 §11 字段一致；`progress` 由「后端」（mock）计算，前端只消费展示。
- 截图用于布局、权限与交互可见性验证，不冒充真实后端联调证据。

## 自动检查结果

- 三个视口下 24 张截图均无横向溢出（`e2e-result.json` 中 `horizontalOverflow: 0`）。
- 首页学习区域：`state=READY`，展示进行中/已完成统计与最近 3 条路线；区域紫色顶边 + 紫色统计数字，与「本周任务」蓝色区域形成学习/项目视觉区分。
- 路线列表：三列卡片（1280 两列、390 单列），含封面占位（标题首字）、难度标签、状态标签、进度条与「继续学习 / 回顾路线 / 未发布 / 已归档不可开始」动作。
- 侧栏入口：一级菜单「学习实验台」使用 Reading 图标并高亮；390×844 下由抽屉菜单呈现。
- 列表空状态：筛选无结果显示「没有符合筛选条件的学习路线」。
- 路线详情：左概览（封面、难度、状态、时长/阶段数/人数、进度条、继续学习）+ 右阶段时间线；已完成阶段节点为紫色实心并显示对勾，已完成单元标题删除线。
- 单元完成交互：勾选「手工焊接」后进度由 5/21（23%）更新为 6/21（28%），阶段计数由 2/4 更新为 3/4。
- 阶段折叠：点击阶段头可折叠/展开；390×844 下各阶段默认折叠。
- 详情空状态：访问不存在路线（/learning/9999）显示「学习路线不存在或不可见」。

## 契约测试

```bash
cd frontend
node src/api/__tests__/w41-contract-test.mjs
# 62 passed, 0 failed
```

回归：

```bash
node src/api/__tests__/w40-contract-test.mjs   # 72 passed, 0 failed
node src/api/__tests__/w39-contract-test.mjs   # 32 passed, 0 failed
node src/api/__tests__/w38-contract-test.mjs   # 36 passed, 0 failed
```

## 复现方式

```bash
cd frontend
$env:VITE_USE_MOCK='true'; npm run build
npm run preview          # http://localhost:4173
$env:W41_BASE_URL='http://localhost:4173'; node .w41-e2e.mjs   # 输出到本目录，并写入 e2e-result.json
```

真实前后端联调结果见 `docs/evidence/2026-W41/real-api-integration.md`；本目录 PNG 仍只作为 mock 模式的视觉布局证据。
