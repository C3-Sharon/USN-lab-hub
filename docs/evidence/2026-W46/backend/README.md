# 2026-W46 后端真实验收证据：库存领用并发、故障回滚与权限

## 验收边界

- 日期：2026-11-20 至 2026-11-21（Asia/Shanghai）
- 分支：`feature/m03-w03-backend-inventory-issuance`
- 数据库：本机 MySQL 8.0.45，独立数据库 `usn_hub_w46_b6_test`
- 服务：`test` profile，RANDOM_PORT；MQTT 与指令超时任务关闭
- 账号：SYSTEM_ADMIN、TEACHER、STOCK_KEEPER、MEMBER、无关 MEMBER 五个独立角色
- 安全边界：未连接或修改 `usn_hub` 业务库；本文档不记录密码、Token 或个人绝对媒体路径

## 真实 MySQL 结果

`InventoryIssueApiIntegrationTest` 在隔离 MySQL 库 `usn_hub_w46_b6_test` 上执行：

```text
Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
```

其中 6 项 MySQL 门控用例全部运行并通过：

- `serializesConcurrentSubmitsForSameAvailableStockOnMySql`：两申请争抢同一可用库存，仅一方预占成功。
- `serializesConfirmIssueVsExpiryOnMySql`：发放与过期竞争，按统一锁序串行化。
- `reallocatesAndWritesOffConcurrentlyWithoutInvariantViolationOnMySql`：批次重分配与报废并发不破坏不变量。
- `confirmsSameIdempotencyKeyReplayOnMySql`：相同 Idempotency-Key 精确重放，不重复扣减。
- `serializesConcurrentReturnInitiationWithinIssuedQuantityOnMySql`：并发发起退回，累计不超过原发放量。
- `verifiesFiveRolePermissionMatrixOnMySql`：五个独立账号的申请、审批、发放、退回、write-off 与成本零泄露边界。

## 实际缺陷与修复

并发退回用例在真实 MySQL 上首先失败，暴露一个仅在可重复读快照下出现的问题：

- 问题：两个并发发起退回的请求各自读到旧的累计退回量，都未超过原发放量，结果两笔退回都被通过。
- 修复：`InventoryIssueMapper.xml` 的 `selectReturnLineSummaries` 增加 `FOR UPDATE` 锁定读，确保读到最新已提交累计量。
- 验证：修复后两个并发退回只有一方成功，另一方返回 `INVENTORY_RETURN_EXCEEDS_ISSUED`。

该修复必须保留，不得在后续整理中回退。

## 故障回滚结果

`InventoryIssueFaultRollbackIntegrationTest` 以 `usnhub.inventory.issue.test-fault-point=AFTER_FIRST_MOVEMENT` 启动，在 H2 定向验证：

```text
Tests run: 25, Failures: 0, Errors: 0, Skipped: 6
```

`rollsBackEveryFactAtControlledFailurePoint` 验证首条 ISSUE_OUT 流水后的受控故障触发完整事务回滚：申请仍为 APPROVED、预占仍 ACTIVE、无 fulfillment/movement 残留。6 项跳过为 MySQL 门控用例，其故障与并发证据由上面的真实 MySQL 运行覆盖。

## 自动化质量门

```text
H2 定向：InventoryIssueApiIntegrationTest
Tests run: 31, Failures: 0, Errors: 0, Skipped: 6

H2 定向：InventoryIssueFaultRollbackIntegrationTest
Tests run: 25, Failures: 0, Errors: 0, Skipped: 6

完整后端测试套件
242 tests, 0 failures, 0 errors, 23 skipped

Package (-DskipTests)
BUILD SUCCESS
```

23 项跳过均为既有的外部 MySQL 条件测试（含本周新增的 6 项 MySQL 门控用例），不作为 H2 通过证据；V14 数据保留、双并发、竞争、故障回滚、幂等重放和五账号权限已分别在隔离 MySQL 8 中执行并通过。

迁移顺序断言 `IotWeek6ReadinessIntegrationTest.allMigrationsAreAppliedInOrder` 已更新为包含 V14 的 `1..14`。

## 复现与恢复

1. 创建独立测试数据库，不复用业务库；将 MySQL URL、用户名和密码写入 `USNHUB_TEST_MYSQL_URL`、`USNHUB_TEST_MYSQL_USERNAME`、`USNHUB_TEST_MYSQL_PASSWORD`。
2. 使用 `test` profile 启动后端到 RANDOM_PORT，并关闭 MQTT 与 IoT 指令超时任务。
3. 故障回滚测试通过 `usnhub.inventory.issue.test-fault-point=AFTER_FIRST_MOVEMENT` 启用受控故障注入。
4. 验收后正常停止服务，确认临时端口不再监听；不执行破坏性 down migration，V14 为 additive 迁移。
5. 测试数据库可在不再需要复核后人工清理，不纳入应用自动清理。

## 前后端联调交接

前端后续必须关闭 mock、使用真实 API 重走五账号权限、库存不足、版本冲突、幂等重放、预占过期、事务失败与附件 ACL，并补齐 1440x900、1280x800、390x844 三视口截图。本文档不代替前端三视口 UI 验收。
