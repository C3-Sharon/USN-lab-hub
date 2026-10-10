# 2026-W40 里程碑与任务后端联调交接

## 1. 可联调能力

- OWNER、MAINTAINER、SYSTEM_ADMIN 可创建里程碑和任务，任务可关联同项目里程碑并分配给项目成员。
- 项目成员可查看里程碑、任务列表与详情；TEACHER 全局只读。
- OWNER、MAINTAINER、SYSTEM_ADMIN 可编辑任务和执行合法状态转换；MEMBER 只能转换自己负责的任务且不可取消。
- 任务状态支持 `TODO → IN_PROGRESS → BLOCKED/DONE`、阻塞恢复和管理者取消；DONE、CANCELED 为终态。
- 编辑和状态变更必须携带 `version`；过期版本返回 `409 VERSION_CONFLICT` 及最新 `currentVersion`。
- 首页 `tasks.state=READY`，返回当前用户负责的 todo、inProgress、blocked、doneThisWeek 和最近 5 条任务。

本周不包含拖拽、子任务、评论、附件、通知、Git 同步、缓存或新中间件。

## 2. 启动准备

使用 JDK 17、MySQL 8，从最新分支启动后端。Flyway 自动执行至 V8，创建 `lab_milestone` 和 `lab_task`；不得修改既有迁移。

```powershell
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/usn_hub?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="<本机密码>"
$env:IOT_MQTT_ENABLED="false"
cd backend
.\mvnw.cmd spring-boot:run
```

## 3. 最小真实联调顺序

1. 使用 `admin/admin123` 登录，取得 Bearer Token。
2. 创建项目，并把 `20260001` 添加为 MEMBER。
3. 创建里程碑：`POST /api/projects/{projectId}/milestones`。
4. 创建任务并把 `assigneeUserId` 指向该成员：`POST /api/projects/{projectId}/tasks`。
5. 使用 `20260001/20260001` 登录，读取任务详情并记录 `version=1`。
6. 依次调用状态接口执行 `TODO → IN_PROGRESS → DONE`，每次使用上一次响应的新 version。
7. 刷新 `GET /api/workbench/overview`，确认 todo、inProgress 归零且 doneThisWeek 增加。
8. 用旧 version 再次更新，确认返回 `409 VERSION_CONFLICT`，刷新后不得静默覆盖。

请求与响应字段以 `docs/contracts/PROJECT_WORKSPACE_API.md` §8-§13 为唯一依据。

## 4. 前端接入重点

- 默认任务列表隐藏 CANCELED；查看取消历史时显式传 `status=CANCELED`。
- BLOCKED 必须提供 2-500 字符 blockReason；恢复后仍展示最后一次原因。
- 根据项目角色和 assigneeUserId 控制操作入口，但后端仍会做最终权限校验。
- PAUSED、COMPLETED、ARCHIVED 项目只读；根据 `reason` 展示提示。
- 工作台任务区独立降级：`TASKS_LOAD_FAILED` 不影响考勤、项目和设备区块。
- `doneThisWeek` 按 Asia/Shanghai 自然周统计。V8 未设独立完成时间，本周以 DONE 任务的 updateTime 作为完成时刻；基本信息编辑也会更新该字段，因此完成后编辑会改变周归属。联调时需记录这一冻结模型限制，后续契约应新增 completedAt。

## 5. 已验证结果

- B4 定向与相邻回归：21/21 通过。
- B5 工作台定向与相邻回归：12/12 通过。
- 完整后端测试：79 项，0 失败、0 错误；3 项真实 MySQL 环境门测试在全量运行中按设计跳过。
- 真实 MySQL 已单独验证 V1-V8 迁移，以及同一 version 两次条件更新仅第一次成功。
- `mvnw -DskipTests package` 通过。

三方真实联调前不更新 `CURRENT_STATE.md` 为已完成。
