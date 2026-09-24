# USN Lab Hub 当前状态

> 事实基线：`origin/dev` commit `5ea466d`
> 更新规则：仅在功能合入 `dev` 且完成验证后更新
> 禁止：把计划、mock、未合并 PR 或口头完成写成已完成

## 已验证能力

### 人员与考勤

- 用户登录和 JWT 鉴权（Authorization Bearer + 旧 token Header 兼容）。
- 五档全局角色：SYSTEM_ADMIN、TEACHER、STOCK_KEEPER、MEMBER（GUEST 为逻辑角色）。
- 管理员成员查询、新增、更新和状态管理；TEACHER 只读查看成员和考勤。
- 学生签到、签退、今日记录、时长统计。
- 管理员考勤分页和导出。
- 禁用用户 Token 立即失效（每次鉴权查库）。
- 默认演示账号及 Flyway V6 迁移。
- 401/403 统一响应格式 + reason 枚举（TOKEN_MISSING/INVALID/EXPIRED、ACCOUNT_DISABLED、ACCESS_DENIED）。

### 个人工作台

- `GET /api/workbench/overview` 首页聚合接口（六区域 + state 三态）。
- 角色化首页：不同全局角色看到正确的区域和数据范围。
- 今日考勤、设备提醒为真实数据；项目、任务、学习、待办为 NOT_AVAILABLE 占位。
- loading / success / empty / error / permission / offline 六态。
- 三视口布局：1440x900、1280x800、390x844。
- UI_SPEC 设计 Token 落地，移除 Unsplash 外链。

### 单项目、单设备 IoT 闭环

- PM-001 使用 `iot/power-monitor/PM-001/telemetry` 上报 MQTT telemetry。
- 保存原始 payload 与 voltage/current/power 结构化指标。
- latest、history 和 15 秒 ONLINE/OFFLINE 状态。
- 阈值告警、规则建议、建议确认或忽略。
- 指令创建、MQTT 发布、ACK、超时和操作日志。
- 设备健康评分、公开项目展示和登录后 IoT 总览。
- SSE 最新遥测推送、断线重连和轮询降级（公开接口保留）。
- Python PM-001 模拟器用于契约和联调，不是生产接口的事实源。

### 工程基础

- Spring Boot 3.1.5、JDK 17、MyBatis-Plus、MySQL 8、Flyway。
- Vue 3、Vite、Element Plus、Vue Router、Axios。
- 后端单元/集成测试和前端 SSE Node 测试已有基础。
- GitHub `dev` 集成、PR 协作和 GitLab 单向归档约定。

## 已知局限

- `lab_project` 表仍服务首轮 IoT 演示，尚未成为正式项目域（无项目成员、项目角色、里程碑、任务）。
- 项目工作台首页 projects 区域仍为 NOT_AVAILABLE 占位，待第 3 周切换为真实数据。
- 硬件指令暂时仅限 SYSTEM_ADMIN（历史演示兼容，待第 16-17 周平台化后重新设计）。
- 学习实验、采购库存、资产、自研硬件、知识/RAG、Agent 尚未实现。
- MinIO、OpenSearch、双实例、Outbox、监控和恢复体系尚未进入正式实现。
- 前端自动化测试仍以 Node 契约测试为主，组件级 Vitest 待建立。
- 旧 README 的长期 `feature/iot-*` 分支规则已经过期，下一阶段使用每周短分支。

## 当前阶段

26 周路线第 2 周（2026-W38）已验收通过，状态为 VERIFIED。五档全局角色、鉴权统一、个人工作台首页聚合已验证通过。四种角色（SYSTEM_ADMIN/TEACHER/STOCK_KEEPER/MEMBER）登录后权限边界正确，401/403 各 reason 行为符合预期，教师成员只读页面真实联调通过。

当前进入第 3 周：正式项目档案、项目成员与项目权限契约。目标是让成员可以从个人工作台进入自己参与的项目，建立项目状态和项目角色的基础模型。

## 更新格式

每周总联调通过后，由产品负责人或当周集成人更新：

```markdown
### 2026-WNN
- 合入 PR：#<product>、#<backend>、#<frontend>
- 新增已验证能力：
- 验证证据：命令、截图或报告路径
- 新增限制或债务：
- 下一周输入：
```

### 2026-W37

- 合入 PR：#24（产品）、#25（后端）、#26（前端）
- 新增已验证能力：无（本周为现状清点周，不新增业务能力）
- 验证证据：
  - `docs/audits/2026-W37-backend-baseline.md`：85 个 Java 文件、9 个 Controller、16 张表、9 个测试套件，`mvn test` 27/27 通过
  - `docs/audits/2026-W37-frontend-baseline.md`：14 个业务页面、10 张 1440x900 基线截图，`npm run build` 成功，SSE 测试 23/23 通过
  - `docs/evidence/2026-W37/frontend/`：03-dashboard 至 12-admin-attendance 共 10 张截图
- 新增限制或债务：
  - 后端：鉴权范围不完整、JWT 默认密钥、无项目角色、人员/考勤无回归测试、`mvnw.cmd` 不可用
  - 前端：UI_SPEC Token 未落地、Unsplash 外链、10 处 API 数据取值错误、详情页未截图、主 chunk > 2.4 MB
- 下一周输入：
  - 产品：冻结首页字段、全局角色枚举、考勤查看范围、Token Header 名称和 401/403 响应格式
  - 后端：补登录/拦截/角色/考勤读取测试，实现角色权限和首页聚合契约
  - 前端：落地 UI_SPEC Token、修复 API-1~API-10、移除 Unsplash 外链、抽取公共组件
