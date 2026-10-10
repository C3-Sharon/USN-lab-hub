# M01-W02 前端三视口视觉证据

> 分支：`feature/m01-w02-frontend-auth-dashboard`
>
> 检查日期：2026-09-17
>
> 渲染方式：Playwright 驱动本机 Microsoft Edge，页面由实际 Vue 应用渲染

## 检查范围

本次保留 9 张真实 PNG，集中验证第 2 周改动涉及的页面，不再使用人工生成的 SVG 展示卡：

| 视口 | 登录页 | 个人工作台 | TEACHER 成员只读页 |
| --- | --- | --- | --- |
| 1440×900 | `1440x900/login.png` | `1440x900/workbench.png` | `1440x900/teacher-members-readonly.png` |
| 1280×800 | `1280x800/login.png` | `1280x800/workbench.png` | `1280x800/teacher-members-readonly.png` |
| 390×844 | `390x844/login.png` | `390x844/workbench.png` | `390x844/teacher-members-readonly.png` |

## 数据与权限条件

- 工作台请求由浏览器测试拦截，返回与 `AUTH_WORKBENCH_V1.md` 和后端 `WorkbenchOverviewVO` 一致的扁平区域结构。
- 登录用户的 `roles` 使用后端真实对象数组形态：`[{ roleKey, roleName }]`。
- TEACHER 成员页使用分页接口同形响应，检查页面可查看成员，但不存在新增、编辑、启用或禁用按钮。
- 这些截图用于布局和权限可见性验证，不冒充真实后端联调证据。

## 自动检查结果

- 三个视口的登录页、个人工作台和老师成员页均无横向溢出。
- TEACHER 三个视口下的写操作按钮数量均为 0。
- 工作台正确展示 READY、NOT_AVAILABLE 和设备提醒区域。
- 390×844 下页面按移动布局展示，未发现内容越界。

真实前后端联调仍按周任务中的登录、角色、401/403、工作台聚合和公开 SSE 清单执行。
