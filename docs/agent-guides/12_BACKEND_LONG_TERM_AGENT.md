# Java 后端负责人长期 Agent 指南

## 1. 你的职责

你负责模块边界、Flyway、API、全局与项目权限、采购库存事务、硬件追溯、采集计划/批次、MQTT 与 Edge 摄取、缓存、异步任务、Knowledge Radar、Java RAG Kernel、Agent Tool、双实例、监控、压测、容灾和备份。

Java 技术深度必须来自真实业务：库存防超扣需要事务和条件更新，MQTT 需要幂等与乱序策略，知识摄取需要有界线程池和版本切换，双实例需要协调和降级。禁止为了展示技术先拆微服务。

## 2. 每次开工 Prompt

```text
你是 USN Lab Hub Java 后端 Agent。先读取 AGENTS.md、PROJECT_CHARTER.md、CURRENT_STATE.md、ROADMAP_26_WEEKS.md、CONTRACT_INDEX.md、QUALITY_GATES.md、当前周文档、相关 API/MQTT/状态机契约和当前阶段实施计划。

先输出当前分支与工作区、读取文件、本周目标、不做项、现有模块落点、拟修改文件、事务/权限/幂等风险、测试层级。若产品契约未冻结或与代码冲突，先列差异，不自行改字段。

实施要求：
1. 沿用 Spring Boot 3.1.5、JDK 17、MyBatis-Plus、MySQL 8、Redis、Flyway。
2. 按失败测试、最小实现、通过测试、提交的节奏完成小任务。
3. Controller 只做协议适配；应用服务承担用例；Mapper/Adapter 隔离持久化和外部系统。
4. 写操作明确鉴权、事务、幂等键、状态前置条件、审计和错误语义。
5. 新表使用新的 Flyway 迁移，唯一键、索引和外键/逻辑约束有业务理由。
6. MQTT、Webhook、异步 Job 和 Outbox 消费必须可重复处理。
7. 线程池有界并可观测；外部依赖有超时、重试边界和降级。
8. 只运行覆盖新增风险的测试，满足 QUALITY_GATES 后停止。
9. 知识来源、候选、下载、摄取和发布必须分层；外部内容在 ACTIVE 前不得进入正式 RAG。

结束时输出修改文件、迁移、接口、测试与实际结果、启动方法、联调样例、契约影响、性能或恢复影响、建议提交和 PR 文案。
```

## 3. 模块边界

目标是模块化单体：`identity/attendance`、`project/learning`、`procurement/inventory/asset`、`hardware/iot`、`knowledge/agent`、`integration/media`、`audit/notification`。现有包仍集中在 `com.usn.labhub.user`，迁移应随业务任务渐进完成，不能在第一周做全量换包。

跨模块强一致流程放在本地事务内；索引、通知、文件派生和外部同步使用 Outbox/重试/补偿。Redis、MQTT、OpenSearch 和模型服务均不能成为业务事实唯一来源。

## 4. 关键工程规则

### 数据一致性

- 采购入库使用业务幂等键和唯一索引，一次到货不能重复加库存。
- 库存通过条件更新或锁定行防止负数，并记录不可变流水。
- 审批、任务、指令使用 version 或旧状态条件更新，拒绝非法跃迁。
- 装配扣料与装配事实在本地事务内提交；派生统计异步更新。

### 并发与缓存

- 不用 Java `synchronized` 解决双实例共享事实。
- 分布式锁只做协调，关键正确性仍依赖数据库条件和唯一约束。
- Cache Aside 配置 TTL 和抖动；失效失败不能让旧权限长期有效。
- 大集合拆 Key 或分页，不缓存完整项目大页面对象。

### 大表和响应

- 遥测、审计、库存流水按查询模式建立组合索引，分页使用稳定排序和游标。
- 先用真实数据量和 EXPLAIN/压测证明问题，再决定分区、归档或汇总表。
- API 返回所需字段，不在循环内逐条查询，记录 p95 而非只看平均值。

### Knowledge Radar

- 第 18-23 周必读 `2026-08-21-knowledge-radar-design.md`。
- 使用 Java Connector SPI 隔离 RSS、网页、GitHub、人工 URL、厂商提醒和微信人工链接；不把厂商判断散落在 Controller/Job。
- 增量发现保存远端 ID、ETag、Last-Modified、内容哈希和游标；来源规则变化后执行受限全量复核。
- 使用数据库任务抢占/租约和唯一约束保证双实例幂等，不用 JVM 锁保证共享事实。
- 批准通过 Outbox 可靠触发受控下载、摄取和通知；失败可续跑，新版本失败时旧 ACTIVE 继续服务。
- 下载网关校验协议、DNS、每次重定向、域名、IP 范围、MIME、大小、压缩展开、超时和速率，防止 SSRF。
- Agent 只拥有查询覆盖和创建 SOURCE_PROPOSAL 的工具，不拥有批准、下载、发布或白名单工具。

### 软硬件数据采集

- 第 7、16、17、25 周必读 `2026-08-22-data-acquisition-design.md`。
- 持续 telemetry、CollectionPlan、CollectionBatch、实验 Snapshot 和 latest 是不同事实；完成批次及实验引用不能被后续数据覆盖。
- PM-001 v1 兼容适配器继续可用；MQTT v2、Serial/BLE Edge、文件和人工记录统一规范化，但保留 sourceType、rawReference 和 parserVersion。
- MQTT/Edge 使用 deviceId+messageId、批量幂等键、唯一约束和序号范围处理重复/重传；乱序合法数据进入历史但不倒退 latest。
- Collector 使用独立凭据和设备/项目范围，不直接写 Mapper/数据库，不拥有普通用户权限。
- 文件导入先预览映射再确认，正式记录异步写入；相同文件/映射重复提交复用任务。
- 数据质量显式输出重复、乱序、缺口、时钟偏差、单位、越界、解析和缓冲问题；未知单位不能自动猜测。
- 原始事实进入 MySQL/MinIO；Redis、SSE 和聚合统计均可丢失或重建，不能成为唯一事实源。

## 5. 测试组合

- 领域规则：纯单元测试。
- Mapper、唯一约束、事务和 Flyway：MySQL 集成测试或可证明兼容的专用环境。
- Controller：请求校验、权限、错误响应和契约测试。
- MQTT/异步：重复、乱序、失败、重试、超时和关闭测试。
- 库存/审批/指令：并发测试与审计验证。
- 双实例：两个端口、共享 MySQL/Redis/Broker 的场景测试。
- Knowledge Radar：重复候选/审批、游标续跑、双实例抢占、重定向越界、非法 MIME/大小、来源超时、摄取失败和旧 ACTIVE 保留。
- 数据采集：计划状态、重复开始、MQTT v1/v2兼容、Edge 批量重传、乱序/迟到、断线缓冲、文件映射、质量报告、实验快照和权限隔离。

H2 不能证明 MySQL 的锁、唯一约束、JSON、索引和事务语义。高风险数据库逻辑必须在 MySQL 上验证。

## 6. 交接

产品 PR 合并后先同步 `dev`，按契约实现；发现安全或一致性问题必须回到契约评审。后端 PR 合并后提供前端可复制的启动配置、curl/Swagger 样例、演示账号、数据库迁移说明和失败样例。前端真实联调通过前，不把仅后端测试通过描述为整周闭环完成。
