# M01-W03 项目工作台接口级联调记录

> 执行日期：2026-09-28
>
> 环境：第 3 周后端（`feature/m01-w03-backend-project-members`）+ MySQL `usn_hub`（Flyway v7）+ Redis 7.0.8，`http://localhost:8080`
>
> 契约：`docs/contracts/PROJECT_WORKSPACE_API.md`（FROZEN）
>
> 执行方式：`POST /usnhub/user/login` 取得 Bearer Token 后按 `docs/backend/2026-W39-project-workspace-handoff.md` 第 3 节顺序调用

## 结果：15 / 15 通过

| # | 断言 | 期望 | 实测 |
| --- | --- | --- | --- |
| 1 | admin 登录 | 200 + SYSTEM_ADMIN | 200，`primaryRoleKey=SYSTEM_ADMIN` |
| 2 | 创建项目 | 200，myRole=OWNER，status=PREPARING | 200，id=2，`myRole=OWNER`，`PREPARING` |
| 3 | 添加成员为 MEMBER | 200，projectRole=MEMBER | 200，userId=2，MEMBER |
| 4 | 同角色重复添加（幂等） | 200，成员关系 id 不变 | 200，userId=2，与首次一致 |
| 5 | 异角色重复添加 | 409 `ALREADY_MEMBER_DIFFERENT_ROLE` | 409，reason 一致 |
| 6 | 重复项目编号 | 409 `PROJECT_CODE_DUPLICATE` | 409，reason 一致 |
| 7 | 添加不存在用户 | 404 `USER_NOT_FOUND` | 404，reason 一致 |
| 8 | 学生 20260001 登录 | 200 + MEMBER | 200，`primaryRoleKey=MEMBER` |
| 9 | 工作台 projects 区域 | `state=READY` | 200，`state=READY`，total=1 |
| 10 | 工作台包含新项目 | 新项目出现在 list | 可见 |
| 11 | 学生项目列表 | 200，新项目可见，myRole=MEMBER | 200，total=1，myRole=MEMBER |
| 12 | 学生项目详情 | 200，含 2 名成员 | 200，myRole=MEMBER，members=2 |
| 13 | 非成员访问他人项目 | 404 `PROJECT_NOT_FOUND` | 404，reason 一致 |
| 14 | MEMBER 添加成员 | 403 `PROJECT_OPERATION_DENIED` | 403，reason 一致 |
| 15 | 无 Token 访问 | 401 | 401 |

## 闭环结论

「admin 登录 → 创建项目 → OWNER 自动关联 → 添加成员 → 成员工作台可见」在真实后端上形成完整闭环；
幂等、异角色冲突、编号冲突、用户不存在、非成员隐藏、角色权限不足、未鉴权七类边界均符合冻结契约。

## 环境注意事项（非契约问题）

- 登录接口会读取考勤概览（`IAttendanceServiceImpl.getOverview`），该路径依赖 Redis 且未做降级；本地联调必须先启动 Redis，否则登录返回 `{"code":500,"msg":"Unable to connect to Redis"}`。第 3 周交接文档「启动前准备」未列出 Redis，建议后续补充。
- 项目工作台相关接口（`/api/projects*`、`/api/workbench/overview`）本身不依赖 Redis。

## 前端真实 API 模式证据

见 `frontend-real/README.md`。