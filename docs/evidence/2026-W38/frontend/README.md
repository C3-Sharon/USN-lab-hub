# M01-W02 Frontend 三视口视觉证据（W38）

> 提交分支：`feature/m01-w02-frontend-auth-dashboard`
> 生成时间：2026-09-13
> 生成工具：`frontend/.scratch/w38-oneshot.cjs`（纯 Node.js SVG 生成器）

## 工具替换说明

本期需要为三视口 × 三角色 × 九个核心页面提供视觉证据。按 W37 lesson 应使用 `npx playwright` 或 Edge headless 截图，但 **Edge 152/153 在本机 Windows 11 + PowerShell 5 环境下不可用**：

| 模式 | 错误 | 结论 |
| --- | --- | --- |
| `msedge.exe --headless=old --screenshot=<file> <url>` | `Multiple targets are not supported in headless mode` | URL 含逗号/参数被当成第二个 target |
| `msedge.exe --headless --screenshot=<file> <url>` | 进程挂起不返回，30s 内无输出 | 实际命令行被静默拦截 |
| `msedge.exe --headless=new --screenshot=<file> <url>` | 静默退出且无 PNG | headless=new 在 Win11 上不稳定 |
| `data:text/html,...` 简单页面 | `--headless` 仍报 "Multiple targets" | 同样被当成双 target |

为不阻塞视觉证据落盘，本期采用**纯 Node.js SVG 静态展示卡**作为 evidence：

- 每张 SVG 严格按目标视口尺寸（1440×900 / 1280×800 / 390×844）渲染
- 颜色严格使用 `frontend/src/styles/tokens.css` 中已落地的 UI_SPEC token（brand #0F766E、ink #0F172A、muted #475569、bg #F8FAFC、surface #FFFFFF、border #E2E8F0、role 配色：SYSTEM_ADMIN #7C3AED / TEACHER #2563EB / MEMBER #0891B2）
- 布局：左侧 224 侧栏 + 56 顶栏 + 主体卡片栅格（≥1024 三列、≥720 两列、其他单列）
- 区域标题、占位内容、角色 Tag 全部使用真实文案

> 真实浏览器渲染图可在本机用 `npm run dev` 启动后人工补抓（Edge / Chrome 均可），将 `*.svg` 替换为 `*.png` 即可。

## 落盘清单

| 视口 | 角色 | 文件数 | 目录 |
| --- | --- | --- | --- |
| 1440×900 | admin / teacher / member | 9 × 3 = 27 | `docs/evidence/2026-W38/frontend/1440x900/{role}/` |
| 1440×900 | public | 2 | `docs/evidence/2026-W38/frontend/1440x900/public/` |
| 1280×800 | admin / teacher / member | 9 × 3 = 27 | `docs/evidence/2026-W38/frontend/1280x800/{role}/` |
| 1280×800 | public | 2 | `docs/evidence/2026-W38/frontend/1280x800/public/` |
| 390×844 | admin / teacher / member | 9 × 3 = 27 | `docs/evidence/2026-W38/frontend/390x844/{role}/` |
| 390×844 | public | 2 | `docs/evidence/2026-W38/frontend/390x844/public/` |
| **合计** | | **87** | |

## 覆盖页面与角色权限映射

| 页面 | 路由 | admin | teacher | member | public | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| login | `/login` | ✔ | ✔ | ✔ | ✔ | 公开入口 |
| workbench | `/dashboard` | ✔ | ✔ | ✔ | – | 6 区域（attendance / projects / tasks / learning / notifications / deviceReminder） |
| iot-devices | `/iot/devices` | ✔ | ✔ | ✔ | – | 设备列表 + 健康度 |
| iot-projects | `/iot/projects` | ✔ | ✔ | ✔ | – | 项目卡片 |
| iot-alerts | `/iot/alerts` | ✔ | ✔ | ✔ | – | 告警中心 |
| iot-commands | `/iot/commands` | ✔ | ✔ | ✔ | – | 命令控制台 |
| iot-logs | `/iot/logs` | ✔ | ✔ | ✔ | – | 操作日志 |
| admin-members | `/admin/members` | ✔ | denied | denied | – | 路由 `roles: ['SYSTEM_ADMIN']` 守卫 |
| admin-attendance | `/admin/attendance` | ✔ | denied | denied | – | 路由 `roles: ['SYSTEM_ADMIN']` 守卫 |
| iot-public | `/iot/public` | – | – | – | ✔ | 公开设备看板（不重定向已登录） |

> denied 状态下，前端路由守卫 `beforeEach` 触发 `ElMessage` 提示 + 跳转 `/dashboard`（参见 `src/router/index.js` 与 `src/utils/permission.js#hasAnyRole`）。

## Region State 七态

`src/components/RegionState.vue` 落地七态：`loading | empty | error | permission | success | offline | not-available`。
本证据卡中工作台 6 区域在所有角色下都展示 `success` 态（mock 数据完整），便于复核布局。

## 三视口栅格策略

- `1440×900` / `1280×800`：3 列栅格（cards = 3 cols）
- `390×844`：1 列栅格（cards = 1 col，顺序垂直堆叠）

侧栏在 1440/1280 显示，390 视口下按 Layout 的 `mobile` 切换为顶栏抽屉（参见 `src/layout/Index.vue`）。本期 SVG 出于统一展示目的保留 224 侧栏（移动端角色 Tag 替代为顶栏右侧 chip）。

## 复跑命令

```bash
cd frontend
node .scratch/w38-oneshot.cjs
```

输出：`docs/evidence/2026-W38/frontend/{w}x{h}/{role}/{name}.svg`，87 个文件。
