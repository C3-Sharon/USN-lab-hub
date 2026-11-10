# 2026-W45 后端真实验收证据

## 验收边界

- 日期：2026-11-09 至 2026-11-10（Asia/Shanghai）
- 分支：`feature/m03-w02-backend-procurement-receiving`
- 数据库：本机 MySQL 8.0.45，独立数据库 `usn_hub_w45_b6_test`
- 服务：test profile，临时端口 8082；MQTT 与指令超时任务关闭
- 账号：SYSTEM_ADMIN、TEACHER、STOCK_KEEPER、MEMBER 四个独立角色
- 安全边界：未连接或修改 `usn_hub` 业务库；文档不记录密码、Token 或个人绝对媒体路径

## 真实联调结果

执行 `scripts/acceptance/w45-procurement-receiving-integration.ps1`，启用 `EnableTestFaultInjection`，结果：

```text
W45 REAL INTEGRATION PASSED: order=14 firstReceipt=13 finalReceipt=15 media=3
```

已验证：

- 采购创建、金额计算、提交、人工审批、登记下单与完成。
- 两次到货从部分到全部完成，确认动作原子写入到货、批次、流水、余额和采购进度。
- 同一幂等键精确重放不重复入库；超量请求不改变库存事实。
- test profile 首条流水后的受控故障返回 HTTP 500、`PROCUREMENT_TRANSACTION_FAILED`，全部写入回滚。
- SYSTEM_ADMIN、TEACHER、STOCK_KEEPER、MEMBER 权限边界与附件零泄露。
- PNG 上传、媒体元数据和内容读取；匿名访问返回 401、`TOKEN_MISSING` 与 `data.traceId`。

## 自动化质量门

```text
JwtInterceptorTest + ProcurementReceiptFaultRollbackIntegrationTest
11 tests, 0 failures, 0 errors, 0 skipped

IotWeek6ReadinessIntegrationTest
2 tests, 0 failures, 0 errors, 0 skipped

Full backend test suite
183 tests, 0 failures, 0 errors, 10 skipped

Package
BUILD SUCCESS
```

10 项跳过是既有的外部 MySQL 条件测试，不作为通过证据；V13 迁移、旧数据保留、采购 API、首次余额双并发和本周四角色主流程已分别在隔离 MySQL 8 中执行并通过。

## 复现与恢复

1. 创建独立测试数据库，不复用业务库；配置 MySQL URL、用户名和密码到环境变量。
2. 使用 `test` profile 启动后端到空闲端口，并关闭 MQTT 与 IoT 指令超时任务。
3. 准备四个独立角色账号和两个 ACTIVE 元器件，运行 W45 验收脚本并启用故障注入。
4. 验收后以 `Ctrl+C` 正常停止服务，确认临时端口不再监听。
5. 应用回滚只切回上一可用 JAR；V13 为 additive 迁移，保留表和证据，不执行 down migration。
6. 测试数据库和测试媒体目录可在确认不再需要复核后人工清理，不纳入应用自动清理。

前端联调时必须关闭 mock，使用真实 API 重走权限、重复确认、超量、事务失败和附件 ACL；本文件不代替三视口 UI 验收。
