# 2026-W37 后端基线清点

> 路线周次：第 1 周
> 清点基线：`origin/dev` commit `78a83b68c1f79b286a81772debe8a9cc3c409feb`
> 清点分支：`feature/m01-w01-backend-baseline`
> 清点日期：2026-09-07
> 契约影响：API、MQTT、数据库、权限均无变化

## 1. 结论

当前后端已经形成“人员与考勤基础 + PM-001 单设备 IoT 闭环”，可以继续作为 26 周路线的运行基线。工程使用 Spring Boot 模块化单体，但 Java 包仍集中在 `com.usn.labhub.user`，正式项目域、项目内角色、学习实验、采购库存、资产、自研硬件、知识/RAG 和 Agent 尚未实现。

本次共清点 85 个主 Java 文件、9 个 Controller、17 个 service 包文件、6 个 Mapper、15 个数据记录类、4 个 Flyway 文件和 9 个测试套件。系统 Maven 下 27 个测试全部通过，跳过测试的打包成功；仓库自带 `mvnw.cmd` 在当前 Windows PowerShell 环境中无法启动，需作为工程债务修复。

最优先风险不是增加新业务，而是第 2 周必须建立真实的鉴权和角色边界。目前 JWT 拦截只覆盖 `/usnhub/**`，`/admin/**` 与多数 `/api/iot/**` 没有统一身份校验或角色授权，接口名称中的 `admin` 不能代表服务端已实施管理员权限。

## 2. 技术栈与运行配置

| 类别 | 当前基线 | 说明 |
|---|---|---|
| Java | JDK 17 | 本机验证使用 Oracle JDK 17.0.18 |
| Web | Spring Boot 3.1.5、Spring MVC | 模块化单体，未使用完整 Spring Security |
| 数据访问 | MyBatis-Plus 3.5.5、MyBatis XML/注解 SQL | 人员/考勤偏 MyBatis-Plus，IoT 偏手写 Mapper |
| 数据库 | MySQL 8、Flyway | 自动测试主要使用 H2 兼容模式 |
| 缓存 | Redis、Lettuce 连接池 | 考勤概览和短时操作锁使用 Redis |
| IoT | Eclipse Paho MQTT 1.2.5、SSE | PM-001 telemetry、command、ACK 与页面实时推送 |
| 鉴权 | JJWT 0.12.5、自定义 HandlerInterceptor | Token Header 实际读取名与配置项存在分离 |
| 文档 | springdoc-openapi 2.2.0 | Swagger UI 路径 `/swagger-ui.html` |
| 导出 | EasyExcel 3.3.4 | 管理端考勤导出 |
| 构建 | Maven | 系统 Maven 3.9.12 可用；Wrapper 3.3.4 当前启动失败 |

配置现状：

- 默认激活 `dev` profile，数据库默认为本机 `usn_hub`。
- MQTT 默认启用并连接 `tcp://127.0.0.1:1883`；Broker 不可用时记录警告但不阻止应用启动。
- Redis 健康检查关闭，连接信息可由环境变量覆盖。
- `application.yml` 提供了固定 JWT 默认密钥，不能用于共享或生产环境。
- dev 环境启用 MyBatis SQL 标准输出，测试日志量较大。

## 3. 模块清点

### 3.1 通用基础

| 模块 | 主要文件 | 当前职责 |
|---|---|---|
| 启动 | `UsnHubApplication` | Spring Boot 启动、定时任务启用 |
| Web 配置 | `WebConfig` | 仅为 `/usnhub/**` 注册 JWT 拦截器，排除登录 |
| 鉴权上下文 | `JwtInterceptor`、`JwtUtils`、`UserContext` | 校验 Token，并用 ThreadLocal 保存 userId/memberId |
| 返回与异常 | `Result`、`GlobalExceptionHandler` | 统一业务响应体；多数异常仍返回 HTTP 200 |
| Redis | `RedisConfig`、`RedisUtils` | JSON 序列化、缓存读写、简单 `SET NX` |
| 配置 | `JwtProperties`、`IotMqttProperties` | JWT 与 MQTT 参数绑定 |
| 工程配置 | `MybatisPlusConfig`、`OpenApiConfig`、`PasswordConfig` | 分页、OpenAPI、BCrypt |

### 3.2 人员与考勤

| 层次 | 文件 | 当前职责 |
|---|---|---|
| Controller | `SysUserController` | 登录 |
| Controller | `AdminMemberController` | 成员分页、新增、更新、启停；POST/GET 分页及 POST/PUT 状态接口并存 |
| Controller | `AttendanceController` | 当前用户签到、签退 |
| Controller | `AdminAttendanceController` | 考勤分页和 Excel 导出 |
| Service | `ISysUserService`、`ISysUserServiceImpl` | 登录、成员管理、默认学生角色 |
| Service | `IAttendanceService`、`IAttendanceServiceImpl` | 签到、签退、概览、统计、分页和导出 |
| Job | `AttendanceAutoTask` | 每日 00:05 将跨日未签退记录签退到当日 23:59:59 |
| Mapper | `SysUserMapper`、`SysUserMapper.xml` | 用户角色查询和成员分页 |
| Mapper | `AttendanceMapper`、`AttendanceMapper.xml` | 当日记录、统计、管理查询和导出 |
| Entity | `SysUser`、`SysRole`、`SysUserRole`、`SysIdentity`、`SysGroup`、`SysFacultyMajor`、`AttendanceRecord` | 人员、全局角色、身份、组、院系专业和考勤记录 |

### 3.3 PM-001 遥测

| 层次 | 文件 | 当前职责 |
|---|---|---|
| Controller | `IotTelemetryController` | HTTP mock 上报入口 |
| Controller | `IotDeviceController` | latest 和指标历史查询 |
| Service | `IotTelemetryService` | PM-001 校验、时间解析、三项指标转换、latest/历史 |
| Port | `IotTelemetryStore` | 原始报文和指标存储抽象 |
| Adapter | `MybatisIotTelemetryStore` | 事务保存 raw 与 metric 数据 |
| MQTT | `IotMqttTelemetrySubscriber` | 订阅 telemetry 与 ACK Topic 并分流 |
| Mapper | `IotTelemetryRawMapper`、`IotMetricDataMapper` | 原始报文写入、指标写入与时序查询 |
| Record | `IotTelemetryRawRecord`、`IotMetricDataRecord` | 手写 Mapper 的数据载体，不是完整 MyBatis-Plus Entity |

### 3.4 告警、建议、指令与审计

| 层次 | 文件 | 当前职责 |
|---|---|---|
| Controller | `IotOperationsController` | 告警、建议、指令和操作日志 API |
| Service | `IotOperationsService` | 100W 规则、状态条件更新、指令发布、ACK、超时和日志 |
| Publisher | `IotCommandPublisher`、`PahoIotCommandPublisher` | MQTT 指令发布；每次指令建立独立客户端连接 |
| Job | `IotCommandTimeoutJob` | 每秒扫描超过 10 秒未 ACK 的指令 |
| Mapper | `IotOperationsMapper` | 四类运行记录的查询与条件更新 |
| Record | `IotAlertRecord`、`IotRecommendationRecord`、`IotCommandRecord`、`IotOperationLogRecord` | 告警、建议、指令和操作日志载体 |

### 3.5 公共展示、健康与 SSE

| 层次 | 文件 | 当前职责 |
|---|---|---|
| Controller | `IotPublicController` | 公开项目展示聚合接口 |
| Controller | `IotTelemetryStreamController` | PM-001 SSE 订阅入口 |
| Service | `IotPublicProjectService` | 项目、设备、latest、健康和趋势聚合 |
| Service | `IotHealthService` | 离线与未处理 WARNING 的规则评分 |
| Service | `IotTelemetrySseService` | JVM 内维护连接、推送 telemetry/heartbeat |
| Job | `IotTelemetrySseStatusJob` | 检测并推送 ONLINE 到 OFFLINE 的状态变化 |
| Mapper | `IotAssetCatalogMapper` | 查询公开项目、设备和设备数 |
| Record | `LabProjectRecord`、`IotDeviceRecord` | 公共展示查询投影，不是正式项目域模型 |

## 4. Controller 与接口面

当前共有 9 个 Controller、24 个映射方法：

| Controller | 路径与能力 | 鉴权现状 |
|---|---|---|
| `SysUserController` | `POST /usnhub/user/login` | 公开登录 |
| `AttendanceController` | `POST /usnhub/attendance/action` | JWT 拦截 |
| `AdminMemberController` | `/admin/member` 下分页、保存、更新、状态 | 未被当前 JWT 拦截器覆盖 |
| `AdminAttendanceController` | `/admin/attendance/page`、`/export` | 未被当前 JWT 拦截器覆盖 |
| `IotTelemetryController` | `POST /api/iot/telemetry/mock-report` | 未被当前 JWT 拦截器覆盖 |
| `IotDeviceController` | latest、history | 未被当前 JWT 拦截器覆盖 |
| `IotOperationsController` | 告警、建议、指令、ACK 查询和日志 | 未被当前 JWT 拦截器覆盖 |
| `IotPublicController` | 公开项目聚合 | 按设计公开 |
| `IotTelemetryStreamController` | 公开 SSE | 按当前演示设计公开 |

`GlobalExceptionHandler` 将业务错误码放在 `Result.code` 中，但通常不设置相应 HTTP 状态。调用方必须同时理解 HTTP 状态和业务状态，后续契约冻结时需要统一。

## 5. Mapper 清单

| Mapper | 访问对象 | 实现方式 |
|---|---|---|
| `SysUserMapper` | 用户、角色、成员分页 | MyBatis-Plus BaseMapper + XML + 注解 |
| `AttendanceMapper` | 考勤记录、统计、分页、导出 | BaseMapper + XML + 注解 |
| `IotTelemetryRawMapper` | `iot_telemetry_raw` | 注解 SQL |
| `IotMetricDataMapper` | `iot_metric_data` | 注解 SQL |
| `IotOperationsMapper` | 告警、建议、指令、日志 | 集中式注解 SQL，文件职责偏大 |
| `IotAssetCatalogMapper` | `lab_project`、`iot_device` | 注解 SQL，只服务公开演示聚合 |

Controller 当前没有直接注入 Mapper；这一点符合质量门禁。`AttendanceAutoTask` 直接操作 Mapper，后续需要在处理考勤一致性时迁入应用服务或领域服务。

## 6. 数据模型与迁移

Flyway 当前实际应用 4 个迁移：V1、V2、V4、V5。V3 缺号不会阻止 Flyway 执行，但应保留说明，后续不得补写一个新的 V3 插入已共享的历史序列。

### 6.1 表与索引

| 迁移 | 表 | 核心字段 | 主键、唯一键与主要索引 |
|---|---|---|---|
| V1 | `sys_role` | role_name、role_key | PK id；UK role_key |
| V1 | `sys_identity` | identity_name | PK id |
| V1 | `sys_group` | group_name、description | PK id |
| V1 | `sys_faculty_major` | college_name、major_name、时间 | PK id；idx_college |
| V1 | `sys_user` | username、member_id、password、identity/group/faculty、status | PK id；UK member_id；identity/group/faculty 单列索引 |
| V1 | `sys_user_role` | user_id、role_id | 复合 PK `(user_id, role_id)` |
| V1 | `attendance_record` | user、签到日期/时间、签退、时长、学期、来源、操作人 | PK id；idx_user_date；idx_semester；idx_check_in_time |
| V2 | `iot_telemetry_raw` | device_code、topic、payload、解析状态、错误、接收时间 | PK id；idx_iot_raw_device_received |
| V2 | `iot_metric_data` | device、metric、value、unit、上报/接收时间 | PK id；idx_iot_metric_latest；idx_iot_metric_history |
| V4 | `iot_alert_record` | device、metric、level、阈值、状态、处理信息 | PK id；device/status/time 与告警查重普通索引 |
| V4 | `iot_recommendation` | device、alert、source、内容、状态 | PK id；UK alert_id；device/status/time 索引 |
| V4 | `iot_command_record` | command_id、device、command、params、状态、操作者、时间 | PK id；UK command_id；device/status/time 索引 |
| V4 | `iot_operation_log` | device、operator、action、target、summary、time | PK id；device/time 与 action/target 索引 |
| V5 | `lab_project` | code、name、owner、status、public_visible | PK id；UK project_code |
| V5 | `iot_device` | project、code、name、type、protocol、owner、status、last_seen | PK id；UK device_code；idx project_id |
| V5 | `iot_device_metric` | device、metric、unit、warning_threshold、enabled、sort | PK id；UK `(device_id, metric_key)` |

当前共 16 张表。数据库未声明外键，关联完整性依赖应用代码；正式项目、库存和硬件追溯阶段需要针对业务决定外键或明确的逻辑约束，不能沿用“全部无外键”作为默认答案。

### 6.2 模型边界

- 人员模型包含全局角色关系，但没有项目成员和项目角色。
- `lab_project` 是第 5 周 IoT 演示目录，只含少量项目字段，不能直接视为第 3 周正式项目域已经完成。
- 设备和指标表支持 PM-001 目录展示，但采集服务仍以常量绑定设备 1、`PM-001` 和 voltage/current/power。
- telemetry 没有 messageId、幂等唯一键、序列号或解析器版本，重复和乱序治理尚未实现。
- 考勤表允许同一用户存在多条未签退记录，正确性当前主要依赖 Redis 短锁和先查后写。

## 7. 测试覆盖现状

系统 Maven `mvn test` 实际结果：27 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS，总耗时约 1 分 36 秒。

| 测试套件 | 数量 | 覆盖内容 |
|---|---:|---|
| `BackendApplicationTests` | 1 | Spring 上下文；关闭 Flyway/Redis/MQTT 等外部行为 |
| `IotTelemetryControllerTest` | 6 | 空 latest、HTTP 上报、history、非法参数、离线、设备限制 |
| `IotWeek4IntegrationTest` | 1 | telemetry 到告警、建议、指令、ACK、日志闭环 |
| `IotWeek5PublicIntegrationTest` | 2 | 公开项目空/健康/告警/采样状态与 404 业务体 |
| `IotWeek6ReadinessIntegrationTest` | 2 | 4 个迁移顺序、健康检查和公开 API |
| `IotHealthServiceTest` | 4 | ONLINE/OFFLINE 与 WARNING 组合评分 |
| `IotOperationsServiceTest` | 7 | 阈值、告警去重、建议、指令校验、ACK、超时 |
| `IotTelemetryMigrationTest` | 2 | V2 与 V4 SQL 基本结构文本检查 |
| `IotWeek7SseIntegrationTest` | 2 | SSE 初始/遥测推送、heartbeat 与单次离线转换 |

覆盖空白：

- 登录、JWT 解析、拦截路径和角色授权没有自动化测试。
- 成员新增、更新、状态变更和分页没有测试。
- 签到、签退、缓存、并发、跨日自动签退、统计、分页和导出没有测试。
- 未使用真实 MySQL 验证唯一约束、事务、Flyway 和 SQL 方言；集成测试以 H2 为主。
- 未连接真实 MQTT Broker 验证订阅重连、重复消息、发布失败和 ACK。
- SSE 测试为单 JVM，不证明双实例广播一致。
- 没有覆盖 Redis 不可用、缓存反序列化、锁过期与误删场景。

## 8. 技术债务与处理窗口

### 8.1 第 2 周必须进入设计或实现

1. **鉴权范围不完整**：`/admin/**` 和多数 `/api/iot/**` 未进入 JWT 拦截，且没有全局角色校验。
2. **默认密钥入库**：JWT 固定默认密钥存在于配置文件；共享环境必须通过环境变量注入，并在缺失时安全失败。
3. **权限事实不足**：Token 只表达全局 roleKey，尚无项目角色；`operatorId=0/system` 会掩盖未鉴权操作。
4. **人员/考勤无回归测试**：第 2 周首页聚合前，至少为登录、权限和考勤读取建立测试保护。
5. **Wrapper 不可复现**：`mvnw.cmd` 3.3.4 在当前 PowerShell 对 Maven 目录读取 `.Target[0]` 时抛出空数组错误，干净机器无法按文档命令复现。

### 8.2 第 3 至 4 周随项目域处理

1. `lab_project` 只是 IoT 演示模型；需要按正式项目、成员、项目角色、里程碑和任务契约渐进扩展。
2. 所有代码集中在 `com.usn.labhub.user`，应按业务任务迁移模块，不能做一次性全量换包。
3. 多数依赖采用字段注入，新增模块优先构造器注入，旧代码随触及迁移。
4. `GlobalExceptionHandler` 未统一 HTTP 状态、业务码和校验异常语义，应随正式 API 契约处理。
5. `AdminMemberController` 存在重复语义的 POST/GET page 与 POST/PUT status，需要在兼容策略明确后收敛。

### 8.3 第 7、16、17 周随采集和通用 IoT 处理

1. telemetry 写入没有 messageId/唯一键，重传会重复保存；latest 没有明确乱序保护。
2. 非法上报时间会静默替换为服务器当前时间，可能污染实验事实。
3. online 判断使用绝对时间差，未来时间在 15 秒内也会被视为在线。
4. 告警先查后插，查重索引不是唯一键，并发时可能创建重复 OPEN 告警。
5. Topic、设备 ID、指标和阈值仍硬编码为 PM-001 演示值。
6. MQTT 订阅使用内存会话，指令发布每次新建连接；尚无持久会话、受控重试或多实例协调。
7. SSE 连接保存在单 JVM 内存中，双实例之间不会共享事件。

### 8.4 第 10、12、25 周前必须建立的工程能力

1. 考勤 Redis 锁没有随机所有权值和 compare-delete；锁过期后旧请求可能删除新锁，数据库也没有最终唯一约束。
2. 考勤概览缓存未设置 TTL，会按用户和日期持续累积；缓存写入失败仅返回 false，调用方无法观测。
3. `AttendanceAutoTask` 直接访问 Mapper、逐条更新且无批次边界，多实例会重复扫描。
4. 数据库关联目前无外键，关键库存、装配和审计模型需要明确完整性策略。
5. H2 不能证明 MySQL 锁、唯一约束、JSON、索引和事务语义，需要建立真实 MySQL 集成测试入口。
6. 暂无测试覆盖率、静态检查、依赖漏洞扫描和可重复性能基线。
7. MyBatis Generator 与 Velocity 位于常规编译依赖，需评估改为开发工具或独立 profile。
8. Spring Boot 3.1.5 等依赖版本较早，应安排兼容性和安全维护评估，但不能在业务周无计划升级。

## 9. 第 2 周后端输入建议

产品冻结首页和角色契约时，后端需要产品明确：

1. 全局角色枚举及每个角色能访问的首页区域。
2. 管理员、老师、普通成员的考勤查看范围。
3. 首页聚合是单接口还是可独立失败的多个区域，以及部分失败的展示语义。
4. Token Header 的唯一名称、401/403 与业务错误响应格式。
5. 被禁用用户的已有 Token 是否立即失效。
6. IoT 提醒是否只显示已有 PM-001 数据，及其权限范围。

第 2 周后端实现前，应优先补登录/拦截/角色/考勤读取测试，再实现角色权限和首页聚合；不要在同一周顺带平台化多设备 IoT。

## 10. 验证记录

| 动作 | 结果 |
|---|---|
| `git diff --check` | PASS，无空白错误 |
| `backend\\mvnw.cmd test` | FAIL，Maven Wrapper PowerShell 脚本 `Cannot index into a null array`，未进入 Maven |
| `mvn test` | PASS，27/27，通过；0 failure、0 error、0 skipped |
| `mvn -DskipTests package` | PASS，生成 `target/lab-hub-0.0.1-SNAPSHOT.jar` |
| 人工静态清点 | 完成：Controller、Service、Mapper、Entity/Record、Flyway、配置、测试 |

测试生成的 `backend/target/` 为构建产物，不纳入提交。

## 11. 本周边界

- 没有修改业务代码、迁移、配置或生效契约。
- 没有把未来 26 周能力描述为已经实现。
- 没有验证真实 MySQL、Redis、MQTT、浏览器前端或真实硬件联调。
- 本报告是第 2 周产品契约和后端计划的输入，不等同于第 2 周实现方案。
