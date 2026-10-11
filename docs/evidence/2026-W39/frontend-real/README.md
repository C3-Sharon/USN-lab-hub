# M01-W03 前端真实后端联调证据（VITE_USE_MOCK=false）

> 分支：`feature/m01-w03-frontend-project-workspace`
>
> 检查日期：2026-09-28
>
> 渲染方式：Playwright 驱动本机 Microsoft Edge，页面由实际 Vue 应用渲染，请求经 Vite dev server 代理到第 3 周真实后端（`http://localhost:8080`）

## 与 mock 证据的区别

`docs/evidence/2026-W39/frontend/` 的 18 张截图在 `VITE_USE_MOCK=true` 下取得，仅用于布局与权限可见性检查，不构成联调证据。
本目录的截图在 `VITE_USE_MOCK=false`（真实 API 模式）下取得，数据全部来自真实 MySQL + Spring Boot 后端。

## 环境

| 组件 | 版本 / 配置 |
| --- | --- |
| 后端 | 第 3 周分支 `feature/m01-w03-backend-project-members`，端口 8080 |
| 数据库 | MySQL 8，库 `usn_hub`，Flyway 迁移至 v7 |
| 缓存 | Redis 7.0.8（本地 6379，登录读取考勤概览所必需） |
| 前端 | Vite dev server 端口 5173，`VITE_USE_MOCK=false`，`/api` 代理到 8080 |

## 端到端步骤（真实 UI 操作，非接口脚本）

1. `/login` 使用 `admin/admin123` 登录，跳转 `/dashboard`，角色标签显示「系统管理员」。
2. 工作台「我参与的项目」区域由真实 `GET /api/workbench/overview` 渲染。
3. `/projects` 列表由真实 `GET /api/projects` 渲染。
4. 通过「创建项目」弹窗提交，真实 `POST /api/projects` 创建成功并跳转详情页。
5. 通过「添加成员」弹窗提交 `20260001` / 成员，真实 `POST /api/projects/{id}/members` 成功，成员表格出现该成员。
6. 三个固定视口重复检阅工作台、项目列表、项目详情、创建弹窗、添加成员弹窗。

## 截图清单（15 张，真实 API 模式）

| 视口 | 个人工作台 | 项目列表 | 项目详情 | 创建项目弹窗 | 添加成员弹窗 |
| --- | --- | --- | --- | --- | --- |
| 1440×900 | `1440x900/workbench.png` | `1440x900/project-list.png` | `1440x900/project-detail.png` | `1440x900/project-create-dialog.png` | `1440x900/project-add-member.png` |
| 1280×800 | `1280x800/workbench.png` | `1280x800/project-list.png` | `1280x800/project-detail.png` | `1280x800/project-create-dialog.png` | `1280x800/project-add-member.png` |
| 390×844 | `390x844/workbench.png` | `390x844/project-list.png` | `390x844/project-detail.png` | `390x844/project-create-dialog.png` | `390x844/project-add-member.png` |

## 自动检查结果

| 检查 | 结果 |
| --- | --- |
| 真实登录（admin/admin123） | 通过，角色「系统管理员」 |
| 工作台 projects 区域真实数据 | 通过，3 张项目卡 |
| 项目列表真实数据 | 通过，3 张项目卡 |
| 前端创建项目（真实写入） | 通过，`W39-UI-839428`，id=5 |
| 前端添加成员（真实写入） | 通过，`20260001` 出现在成员表 |
| 项目详情成员表 | 通过，显示「项目成员 2 人」 |
| 三视口横向溢出 | 全部无溢出（15/15） |
| `/api/*` HTTP 错误 | 0 条 |

唯一一条浏览器 console error 为 `/favicon.ico` 404（`index.html` 未声明 favicon，属浏览器默认请求），与业务接口无关。

原始结果：`e2e-result.json`。

## 复现方式

```powershell
# 后端（需 MySQL 与 Redis 已启动）
cd backend
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/usn_hub?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
$env:DB_USERNAME="root"; $env:DB_PASSWORD="<本机密码>"; $env:IOT_MQTT_ENABLED="false"
mvn spring-boot:run

# 前端
cd frontend
# .env.local: VITE_USE_MOCK=false
npm run dev   # 5173，/api 代理到 8080
```

真实 API 模式构建：`npm run build`（`dist/` 已通过，仅既有 Rollup 注释与 chunk 体积告警）。

## 接口级联调记录

见 `../integration-real-api.md`。