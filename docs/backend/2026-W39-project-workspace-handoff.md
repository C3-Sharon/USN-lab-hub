# 2026-W39 项目工作台后端联调交接

## 1. 本周可联调能力

- 创建正式项目，创建者在同一事务中成为 `OWNER`。
- 分页查询项目和查看项目详情；普通用户只看到参与项目，`SYSTEM_ADMIN` 可管理全部，`TEACHER` 可只读查看全部。
- `OWNER` 添加 `MAINTAINER`、`MEMBER`、`OBSERVER`；`MAINTAINER` 只添加 `MEMBER`、`OBSERVER`。
- 同角色重复添加成员返回原成员关系；不同角色重复添加返回 `409 ALREADY_MEMBER_DIFFERENT_ROLE`。
- 工作台 `projects.state=READY`，只返回当前用户参与且未归档的项目。
- 历史 `/iot/projects` 和 `power-monitor` 数据保持兼容。

本周不包含项目编辑、删除、状态变更、成员移除、角色变更、OWNER 转让、图片、里程碑和任务。

## 2. 启动前准备

1. 安装 JDK 17、Maven、MySQL 8。
2. 创建开发数据库，不要使用生产或包含重要数据的库。
3. 通过环境变量配置数据库连接和密码，不把密码写入仓库。
4. MQTT 不参与本周项目工作台联调；本地无 Broker 时设置 `IOT_MQTT_ENABLED=false`。

Windows PowerShell 示例：

```powershell
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/usn_hub?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="<本机密码>"
$env:IOT_MQTT_ENABLED="false"
cd backend
.\mvnw.cmd spring-boot:run
```

若本机使用系统 Maven，可将最后一行替换为 `mvn spring-boot:run`。启动时 Flyway 自动执行 V1-V7；不得手工修改已经执行过的迁移文件。

## 3. 最小联调顺序

1. 使用 `admin/admin123` 登录，取得 Bearer Token。
2. 调用 `POST /api/projects` 创建唯一编号项目，记录返回的项目 `id`。
3. 调用 `POST /api/projects/{id}/members`，把 `20260001` 添加为 `MEMBER`。
4. 原请求重复一次，应返回 `200` 且成员关系 `id` 不变。
5. 把同一用户改为 `OBSERVER` 再请求，应返回 `409 ALREADY_MEMBER_DIFFERENT_ROLE`。
6. 使用 `20260001/20260001` 登录，调用 `GET /api/workbench/overview`，确认 `projects.state=READY` 且列表包含新项目。
7. 调用项目列表与详情，检查普通成员可见、非成员按契约返回 `404 PROJECT_NOT_FOUND`。

完整请求样例以 `docs/weekly/2026-W39.md` 第 9 节和 `docs/contracts/PROJECT_WORKSPACE_API.md` 为准，不从聊天记录猜字段。

## 4. 前端接入要点

- 正式路由使用 `/projects` 与 `/projects/:id`；旧 `/iot/projects` 继续保留。
- 请求必须发送 `Authorization: Bearer <token>`。
- 项目列表、详情、创建和成员接口直接使用冻结契约字段；不要保留默认启用的业务 mock。
- 工作台以 `projects.state` 区分 `READY` 与局部失败；项目查询失败时后端返回 `PROJECTS_LOAD_FAILED`，不影响其他工作台区块。
- 空项目列表是合法成功状态，不能显示为接口错误。
- `403`、`404`、`409` 使用响应中的 `reason` 映射用户提示，不依赖 message 文案做逻辑判断。

## 5. 后端验证命令

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
```

真实 MySQL 流程测试仅在显式提供以下环境变量时启用：

```text
USNHUB_TEST_MYSQL_URL
USNHUB_TEST_MYSQL_USERNAME
USNHUB_TEST_MYSQL_PASSWORD
```

该 URL 必须指向可删除的隔离测试库。测试会执行 Flyway 并写入验收数据，禁止指向现有 `usn_hub`。

## 6. 联调完成标准

- 创建项目、OWNER 自动关联、添加成员和成员工作台可见形成真实闭环。
- 同角色幂等、不同角色冲突、权限不足、非成员隐藏和归档拒绝均符合冻结契约。
- 前端不再默认使用 mock，构建通过，三个固定视口完成检阅。
- 旧鉴权、工作台其他区块和 IoT 演示路径无回归。
- 三方验收后再更新 `CURRENT_STATE.md`、追踪矩阵和周末结果；本后端 PR 不提前宣称总联调完成。
