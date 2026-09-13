# 2026-W38 后端鉴权与个人工作台交接

## 1. 交付范围

- Flyway V6 将旧 `admin/student` 角色迁移为 `SYSTEM_ADMIN/MEMBER`，保留角色 ID 和已有用户角色关系，并新增 `TEACHER/STOCK_KEEPER`。
- 登录响应新增 `roles`、`primaryRoleKey`、`primaryRoleName`，保留旧 `role/roleKey` 字段供前端迁移。
- 受保护接口支持 `Authorization: Bearer <token>` 和旧 `token` 请求头；两者同时存在时必须相同。
- 每个受保护请求实时查询账号状态和角色，账号禁用与角色变更在下一次请求生效。
- 统一返回带 `reason` 的 HTTP 401/403；成功响应不包含 `reason`。
- 新增 `GET /api/workbench/overview`，固定返回六个区域；考勤和设备提醒读取真实数据，其余区域明确为 `NOT_AVAILABLE`。
- 收紧成员写操作和硬件指令权限，同时保留 `/api/iot/public/**` 公开只读接口。

本周未实现项目成员、项目角色、项目/任务/学习真实数据、库存业务、动态二维码、正式 SSE 鉴权、Redis Token 黑名单或新 IoT 设备接入。

## 2. 数据库迁移

启动后端时 Flyway 自动执行 `V6__expand_global_roles.sql`。启动前请通过环境变量提供本机 MySQL 凭据，不要把密码写入仓库：

```powershell
$env:DB_USERNAME = "你的数据库用户名"
$env:DB_PASSWORD = "你的数据库密码"
mvn spring-boot:run
```

迁移后执行只读核验：

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT id, role_key, role_name
FROM sys_role
ORDER BY id;

SELECT user_id, role_id
FROM sys_user_role
ORDER BY user_id, role_id;
```

预期 V6 成功；角色 ID 1-4 依次为 `SYSTEM_ADMIN`、`MEMBER`、`TEACHER`、`STOCK_KEEPER`，旧用户关联仍存在。`GUEST` 是未登录逻辑角色，不写入数据库。

本次自动化已在 H2 MySQL 兼容模式验证完整 V1-V6 迁移。开发机 MySQL 服务可达，但默认 `root` 空密码认证失败，因此没有伪造或提交真实库验证结果；持有本机凭据的开发者应在 PR 合并前完成上述启动与查询。

## 3. 前端联调

登录：

```http
POST /usnhub/user/login
Content-Type: application/json

{"memberId":"admin","password":"admin123"}
```

登录成功后以 `data.user.roles` 作为权限判断依据，以 `data.user.primaryRoleKey` 展示主角色。第 2 周前端可同时发送以下两个相同值的请求头；第 4 周移除旧 `token`：

```http
Authorization: Bearer <token>
token: <token>
```

首页：

```http
GET /api/workbench/overview
Authorization: Bearer <token>
```

响应中的六个固定区域为 `attendance`、`projects`、`tasks`、`learning`、`notifications`、`deviceReminder`。区域状态只使用 `READY`、`NOT_AVAILABLE`、`ERROR`，不要读取或自行补充 `available` 字段。设备提醒失败只降级该区域；考勤失败则整个接口失败。

错误处理：

| HTTP | reason | 前端动作 |
|---|---|---|
| 401 | `TOKEN_MISSING` / `TOKEN_INVALID` / `TOKEN_EXPIRED` | 清除登录态并返回登录页 |
| 401 | `ACCOUNT_DISABLED` | 清除登录态并显示账号禁用提示 |
| 403 | `ACCESS_DENIED` | 保留登录态并显示无权访问 |

## 4. 权限核验重点

- `SYSTEM_ADMIN`：成员读写、考勤读取、告警/建议操作、硬件指令下发。
- `TEACHER`：成员和考勤只读、告警/建议操作；不能新增或停用成员，不能下发硬件指令。
- `STOCK_KEEPER`、`MEMBER`：本周不获得 `/admin/**` 或硬件指令权限；仍可访问已登录的 IoT 只读接口。
- 任意角色变化或账号禁用都不需要重新签发 Token，下一次请求按数据库当前状态判定。
- `/api/iot/public/**` 无需 Token，SSE 继续使用公开 `EventSource`。

## 5. 已执行验证

```powershell
cd backend
mvn '-Dtest=JwtInterceptorTest,UserLoginServiceTest,WorkbenchServiceTest,Week2AuthWorkbenchIntegrationTest,IotWeek4IntegrationTest,IotWeek5PublicIntegrationTest,GlobalRoleMigrationTest' test
mvn '-Dtest=JwtInterceptorTest,Week2AuthWorkbenchIntegrationTest' test
```

结果：定向回归 24 项通过；权限补测 15 项通过。覆盖迁移、登录、多角色优先级、两种请求头、双头一致与冲突、无效/过期 Token、账号禁用、角色即时变更、401/403、首页六区、区域降级、公开 IoT 以及旧 IoT 写操作回归。

完整 `mvn test` 已通过：49 项测试、0 失败、0 错误、0 跳过。`mvn -DskipTests package` 已通过，并生成可执行 Jar。真实 MySQL 迁移验证仍需使用开发者本机凭据完成。

## 6. 三方交接

1. 后端 PR 合入 `dev` 后，前端从最新 `dev` 同步，删除 mock 首页响应并连接真实登录与工作台接口。
2. 前端按 `roles` 数组过滤菜单，不以 JWT 内旧角色声明做权限判断；后端始终拥有最终授权权威。
3. 产品、后端、前端共同执行 `docs/weekly/2026-W38.md` 第 8、9 节；前端提交三视口证据。
4. 三方验收完成前不更新 `CURRENT_STATE` 为已完成；周末收口 PR 再填写实际 PR、测试、截图、风险与遗留项。
