# M01-W04 前端三视口视觉证据

> 分支：`feature/m01-w04-frontend-task-kanban`
>
> 检查日期：2026-10-02
>
> 渲染方式：Playwright 驱动本机 Microsoft Edge，页面由实际 Vue 应用渲染（`VITE_USE_MOCK=true` 构建）

## 检查范围

本次保留 30 张真实 PNG（3 视口 × 10 场景），验证第 4 周新增的里程碑与任务看板功能：

| 视口 | 首页 tasks | 概览 | 里程碑 | 创建里程碑 | 任务看板 | 状态菜单 | 任务详情 | 创建任务 | 乐观锁冲突 | 成员 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1440×900 | `1440x900/workbench.png` | `1440x900/project-overview.png` | `1440x900/project-milestones.png` | `1440x900/project-milestone-create.png` | `1440x900/project-kanban.png` | `1440x900/project-kanban-status-menu.png` | `1440x900/project-task-detail.png` | `1440x900/project-task-create.png` | `1440x900/project-version-conflict.png` | `1440x900/project-members.png` |
| 1280×800 | `1280x800/workbench.png` | `1280x800/project-overview.png` | `1280x800/project-milestones.png` | `1280x800/project-milestone-create.png` | `1280x800/project-kanban.png` | `1280x800/project-kanban-status-menu.png` | `1280x800/project-task-detail.png` | `1280x800/project-task-create.png` | `1280x800/project-version-conflict.png` | `1280x800/project-members.png` |
| 390×844 | `390x844/workbench.png` | `390x844/project-overview.png` | `390x844/project-milestones.png` | `390x844/project-milestone-create.png` | `390x844/project-kanban.png` | `390x844/project-kanban-status-menu.png` | `390x844/project-task-detail.png` | `390x844/project-task-create.png` | `390x844/project-version-conflict.png` | `390x844/project-members.png` |

## 数据与权限条件

- 构建时注入 `VITE_USE_MOCK=true`，前端走 mock 分支，不依赖真实后端。
- 登录态由浏览器 init script 注入 localStorage，模拟项目 `myRole=OWNER`（用户 id=2「张同学」），因此看板与里程碑的创建/编辑/状态变更入口均可见。
- mock 数据与 `PROJECT_WORKSPACE_API.md` §8–§13 字段完全一致（含任务 `version`、`assigneeName`、`milestoneName` 冗余字段），便于后续无缝切换真实接口。
- 截图用于布局、权限与交互可见性验证，不冒充真实后端联调证据。

## 自动检查结果

- 三个视口下 30 张截图均无横向溢出（`e2e-result.json` 中 `horizontalOverflow: 0`）。
- 首页 tasks 区域：`state=READY`，展示待开始/进行中/已阻塞/本周完成统计与最近 5 条任务。
- 项目详情页：四个 Tab（概览/里程碑/任务看板/成员）正常切换，支持 `?tab=` 深链定位。
- 里程碑 Tab：卡片列表 + 进度条 + 创建弹窗，进度由 `taskCount/taskDone` 计算。
- 任务看板：桌面端四列（TODO/IN_PROGRESS/BLOCKED/DONE）布局；390×844 下改为纵向分组并默认折叠。
- 状态菜单：卡片操作菜单仅展示合法转换，点击菜单不误触任务详情。
- 任务详情抽屉：展示负责人、里程碑、优先级、截止日期与阻塞原因。
- 乐观锁：编辑时持有旧 `version`，提交后触发 409 VERSION_CONFLICT，弹出「版本冲突（409）」提示并可刷新数据。
- 成员 Tab：复用第 3 周成员列表，写操作按角色可见。

## 契约测试

```bash
cd frontend
node src/api/__tests__/w40-contract-test.mjs
# 72 passed, 0 failed
```

回归：

```bash
node src/api/__tests__/w39-contract-test.mjs   # 32 passed, 0 failed
node src/api/__tests__/w38-contract-test.mjs   # 36 passed, 0 failed
```

## 复现方式

```bash
cd frontend
VITE_USE_MOCK=true npm run build
npm run preview          # http://localhost:4173
node .w40-e2e.mjs        # 输出到本目录，并写入 e2e-result.json
```

真实前后端联调仍按 `docs/weekly/2026-W40.md` §10 的 10 步验收脚本执行。