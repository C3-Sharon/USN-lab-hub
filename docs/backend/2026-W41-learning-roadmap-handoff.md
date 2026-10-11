# 2026-W41 学习路线后端联调交接

## 1. 可联调能力

- SYSTEM_ADMIN、TEACHER 可维护路线、阶段和单元，并按 DRAFT、PUBLISHED、ARCHIVED 状态机管理路线。
- 登录成员可浏览已发布路线；MEMBER、STOCK_KEEPER 可开始学习、完成或取消完成单元。
- 学习进度、完成数和状态全部由后端按当前路线结构计算；重复加入和重复完成请求保持幂等。
- 首页 `learning.state=READY`，返回进行中数、完成数和最近更新的 3 条路线；查询失败只将该区域降级为 `ERROR/LEARNING_LOAD_FAILED`。
- Flyway V9 新增五张学习域表，并预置一条已发布的“嵌入式硬件入门”路线。

本周不包含实验模板、实验记录、导师反馈、采集计划、RAG、Agent、MQTT 或前端代码。

## 2. 启动准备

使用 JDK 17、MySQL 8，从最新后端分支启动。Flyway 自动执行至 V9；不得修改已经共享的 V1-V9 迁移。

```powershell
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/usn_hub?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="<本机密码>"
$env:IOT_MQTT_ENABLED="false"
cd backend
.\mvnw.cmd spring-boot:run
```

登录接口为 `POST /usnhub/user/login`。当前仓库真实预置账号为管理员 `admin/admin123`、成员 `20260001/20260001`。周任务中的 `student01/123456` 不是当前 V1 迁移里的种子账号，联调时不要直接使用。

## 3. 最小真实联调顺序

1. 分别登录管理员和成员账号，后续请求发送 `Authorization: Bearer <token>`。
2. 管理员调用 `GET /api/learning/roadmaps?status=PUBLISHED`，从响应中取得预置路线 `roadmapId`，不要写死 ID。
3. 成员调用 `GET /api/learning/roadmaps/{roadmapId}/stages`，从响应中取得首个 `unitId`，确认所有 `completed=false`。
4. 成员调用 `POST /api/learning/roadmaps/{roadmapId}/enroll`，确认 `status=NOT_STARTED`、`progress=0`；重复调用仍返回 200 且不产生重复记录。
5. 成员调用 `GET /api/workbench/overview`，确认 `learning.state=READY` 且最近路线列表包含该路线。
6. 成员调用 `POST /api/learning/units/{unitId}/complete`，确认 `roadmapProgress.progress` 由后端增加；刷新首页后进度同步。
7. 成员调用 `DELETE /api/learning/units/{unitId}/complete`，确认进度回退；重复取消仍返回 200。
8. 管理员创建 DRAFT 路线，成员读取其详情应返回 404；管理员归档已发布路线后，已有学习记录保留但新成员不可加入。

请求和响应字段以 `docs/contracts/LEARNING_EXPERIMENT_API.md` 为唯一依据。

## 4. 前端接入重点

- 首页空状态仍是 `state=READY`、两个计数为 0、`list=[]`，不能当作加载失败。
- 首页 `list` 按 `updateTime` 倒序且最多 3 条；字段为 roadmapId、roadmapTitle、roadmapDifficulty、status、progress、completedUnitCount、totalUnitCount、updateTime。
- `learning.state=ERROR` 时读取 errorCode、message、retryable，只降级学习区块，不遮挡考勤、项目、任务和设备区块。
- 路线、阶段和单元 ID 均从接口响应取得，不依赖固定自增值。
- `units[].completed` 是当前登录用户维度的数据；切换账号后必须重新请求。
- templateId、templateName 本周保持 null；前端不得伪造实验模板入口。

## 5. 已验证结果

- B5 工作台 H2 定向测试：9 项通过。
- B5 工作台真实 MySQL 测试：3 项通过，临时测试库执行后删除。
- 完整后端回归执行一次：98 项中 1 项因旧迁移清单未包含 V9 而失败，另有 4 项按环境条件跳过；修正预期后，受影响测试类 2 项全部通过。
- 跳过测试打包结果见 `docs/backend/execution/2026-W41.md`。

三方真实页面联调完成前，不把本周能力写入 `CURRENT_STATE.md` 的已完成功能。
