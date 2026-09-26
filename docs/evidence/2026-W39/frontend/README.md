# M01-W03 前端三视口视觉证据

> 分支：`feature/m01-w03-frontend-project-workspace`
>
> 检查日期：2026-09-26
>
> 渲染方式：Playwright 驱动本机 Microsoft Edge，页面由实际 Vue 应用渲染（VITE_USE_MOCK=true 构建）

## 检查范围

本次保留 18 张真实 PNG，验证第 3 周新增的项目工作台页面：

| 视口 | 个人工作台 | 项目列表 | 项目详情 | 创建项目弹窗 | 添加成员弹窗 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- |
| 1440×900 | `1440x900/workbench.png` | `1440x900/project-list.png` | `1440x900/project-detail.png` | `1440x900/project-create-dialog.png` | `1440x900/project-add-member.png` | `1440x900/project-list-empty.png` |
| 1280×800 | `1280x800/workbench.png` | `1280x800/project-list.png` | `1280x800/project-detail.png` | `1280x800/project-create-dialog.png` | `1280x800/project-add-member.png` | `1280x800/project-list-empty.png` |
| 390×844 | `390x844/workbench.png` | `390x844/project-list.png` | `390x844/project-detail.png` | `390x844/project-create-dialog.png` | `390x844/project-add-member.png` | `390x844/project-list-empty.png` |

## 数据与权限条件

- 构建时注入 `VITE_USE_MOCK=true`，前端走 mock 分支，不依赖真实后端。
- 登录态由浏览器 init script 注入 localStorage，模拟 SYSTEM_ADMIN 角色。
- mock 数据与 `PROJECT_WORKSPACE_API.md` §5 字段完全一致（mock 响应直接 resolve data，与 axios 拦截器解包行为一致）。
- 截图用于布局和权限可见性验证，不冒充真实后端联调证据。

## 自动检查结果

- 三个视口下所有页面无横向溢出。
- 项目列表页：卡片布局、分页、状态过滤、搜索均正常渲染。
- 项目详情页：基本信息、成员列表、状态标签、添加成员按钮（OWNER 可见）。
- 创建项目弹窗：字段验证提示、提交按钮。
- 添加成员弹窗：学工号输入、角色选择（维护者/成员/观察者，无 OWNER）。
- 空状态：搜索无结果时显示"暂无参与的项目" + 创建入口。
- 工作台 projects 区域：state=READY，显示 2 个项目卡片，点击跳转 `/projects/:id`。
- 390×844 下侧边栏折叠为图标模式，内容区自适应。

## 契约测试

```bash
cd frontend
node src/api/__tests__/w39-contract-test.mjs
# 30 passed, 0 failed
```

真实前后端联调仍按周任务中的创建项目 → 添加成员 → 幂等 → 权限边界清单执行。
