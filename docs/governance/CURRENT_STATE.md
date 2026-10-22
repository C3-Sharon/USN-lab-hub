# USN Lab Hub 当前状态

> 事实基线：`origin/dev` commit `96e153f`
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
- 今日考勤、设备提醒、项目、任务和学习为真实数据；待办为 NOT_AVAILABLE 占位。
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

### 学习实验与实验模板

- 入门学习路线、阶段、单元、成员加入、完成进度和首页 learning 聚合已实现。
- 系统模板和项目模板已实现创建、编辑、预览、复制、发布与废弃；项目模板受项目成员权限约束。
- 模板 Schema 使用受控 JSON Schema 子集：扁平对象、string/number/integer/boolean、enum、required、default 和基础范围/长度约束，禁止脚本、数组、嵌套对象和 `$ref`。
- 模板版本状态为 DRAFT / PUBLISHED / DEPRECATED；已发布和已废弃版本内容不可修改、不可物理删除，发布后自动滚动下一份 DRAFT。
- 学习单元固定关联具体 `templateVersionId`；模板发布新版本不会自动升级旧单元，版本废弃也不会破坏既有引用。

## 已知局限

- `lab_project` 表已扩展为正式项目表（状态、分类、封面等字段）；已有 lab_project_member 多对多关系表承载项目角色。
- 项目工作台首页 projects 区域已切换为 state=READY，返回用户参与的真实项目列表。
- 项目创建、成员添加、幂等处理、权限校验已通过真实 MySQL 联调。
- 里程碑和任务状态机已实现：PLANNED/IN_PROGRESS/COMPLETED（里程碑）、TODO/IN_PROGRESS/BLOCKED/DONE/CANCELED（任务）。
- 乐观锁 version 机制已实现，过期更新返回 409 VERSION_CONFLICT。
- 首页 tasks 区域已切换为 state=READY，返回当前用户负责的任务统计和最近 5 条。
- doneThisWeek 按 Asia/Shanghai 自然周统计；V8 未设独立 completedAt，以 DONE 任务 updateTime 判断（已知限制）。
- 旧 `/iot/projects` 路由仍存在但真实请求 `/api/iot/projects` 返回 404（首轮 IoT 遗留缺口，不是回归；正式路径为 `/projects` + `/api/projects`）。
- 硬件指令暂时仅限 SYSTEM_ADMIN（历史演示兼容，待第 16-17 周平台化后重新设计）。
- 实验记录、内容修订、导师反馈、采集计划/批次、文件/人工导入、冻结快照和转项目草案仍待第 7 周实现。
- W42 前端保留主 JS chunk size 告警；学习单元的废弃版本提示依赖模板版本查询，学习单元响应本身不增加 `templateVersionStatus`。
- 采购库存、资产、自研硬件、知识/RAG、Agent 尚未实现。
- MinIO、OpenSearch、双实例、Outbox、监控和恢复体系尚未进入正式实现。
- 前端自动化测试仍以 Node 契约测试为主，组件级 Vitest 待建立。
- 旧 README 的长期 `feature/iot-*` 分支规则已经过期，下一阶段使用每周短分支。

## 当前阶段

26 周路线第 6 周（2026-W42）已验收通过，状态为 VERIFIED。实验模板、受控 JSON Schema、不可变版本、系统模板复制为项目模板、发布/废弃，以及学习单元固定模板版本关联已经合入 `dev`。

Phase 1（第 1-4 周）全部 VERIFIED。原 v0.2 发布 PR 因 `dev` 已继续纳入第 5 周内容而过期，不得按旧说明直接合入 `main`；稳定版本发布应使用与说明一致的冻结提交或后续阶段版本。

当前进入第 7 周：实验记录、FILE/MANUAL 采集批次、导师反馈、冻结快照和轻量转项目草案。MQTT v2、Serial/Bluetooth、Edge Collector、完整质量引擎和大波形处理仍按第 16-17 周实施。

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

### 2026-W41

- 合入 PR：#41、#43（产品）、#44（后端）、#42（前端）
- 新增已验证能力：学习路线管理与可见性、阶段/单元结构、成员加入与完成进度、首页 learning 真实聚合、学习实验台列表与详情。
- 验证证据：
  - 后端 V9 迁移、状态机、权限、事务幂等和首页独立降级均有定向测试；迁移与并发路径通过真实 MySQL 8 验证。
  - 前端 W41 62/62、W40 72/72、W39 32/32、W38 36/36，非 mock 构建通过。
  - `docs/evidence/2026-W41/frontend/`：三视口 24 张 PNG，无横向溢出。
  - `docs/evidence/2026-W41/real-api-integration.md`：真实登录、路线浏览、加入、完成/取消和首页进度同步通过。
- 新增限制或债务：详情契约不含 `learnerCount`，前端显示“暂未统计”；主 JS 仍有既有 chunk size 告警；实验模板、实验记录和反馈尚未进入实现。
- 下一周输入：产品冻结模板字段与版本语义；后端实现 JSON Schema 和模板版本；前端实现模板编辑、预览及版本展示。

### 2026-W42

- 合入 PR：#47（产品）、#48（后端）、#49（前端）
- 新增已验证能力：系统/项目实验模板、受控 JSON Schema、不可变版本发布与废弃、SYSTEM→PROJECT 复制、学习单元固定关联 PUBLISHED 版本、模板列表/详情/编辑/预览和版本历史。
- 验证证据：
  - 后端完整回归总计 124 项，实际执行 118 项全部通过，6 项真实环境条件测试按设计跳过；跳过测试打包成功。
  - 真实 MySQL 8 的 V1→V10 迁移与约束 2 项、并发发布 1 项以及登录→模板创建→Schema 编辑→发布→学习单元关联→项目模板复制主链路通过。
  - 前端 W42 契约测试 90/90，W41/W40/W39/W38 回归 62/62、72/72、32/32、36/36；生产构建通过。
  - `docs/evidence/2026-W42/frontend/`：三视口 45 张 PNG，横向溢出 0 处。
- 新增限制或债务：前端主 JS 仍有既有 chunk size 告警；学习单元响应不包含模板版本状态，废弃提示通过模板版本接口获取；实验记录和采集闭环进入 W43。
- 下一周输入：冻结实验记录/修订状态机、导师反馈、FILE/MANUAL 批次、冻结快照、幂等/乐观锁以及轻量转项目草案契约。
