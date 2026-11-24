# 2026-W46 前端真实联调证据（M03-W03 库存领用）

> 日期：2026-11-24  
> 分支：`feature/m03-w03-frontend-inventory-issuance`  
> 模式：`VITE_USE_MOCK=false`（默认关闭 mock），Vite dev 代理到本机后端  
> 结论：五账号真实 API、成本脱敏、状态机与三视口页面均通过；发现并修复两处前端联调缺陷。

## 运行环境

- MySQL 8.0 隔离验收库：`usn_hub_w46_b6_test`（Flyway V14，未触碰业务库 `usn_hub`）。
- 后端：`mvnw spring-boot:run`，`server.port=8080`，`IOT_MQTT_ENABLED=false`，`IOT_COMMAND_TIMEOUT_ENABLED=false`。
- 前端：`npm run dev`，`http://127.0.0.1:5173`，经 Vite `/api`、`/usnhub` 代理访问后端。
- 浏览器：Playwright + 系统 Microsoft Edge，无头模式；视口 `1440×900`、`1280×800`、`390×844`。

## 五账号

| 账号 | 角色 | 密码 |
|---|---|---|
| `admin` | SYSTEM_ADMIN | 管理员工号密码 |
| `teacher001` | TEACHER | 与管理员同测试口令 |
| `stockkeeper001` | STOCK_KEEPER | 与管理员同测试口令 |
| `20260001` | MEMBER | 与学工号相同 |
| `unrelated001` | MEMBER（无关成员） | 与管理员同测试口令 |

## 真实 API 主链路

1. `20260001` 创建 `验收领用-STM32核心板`（元器件 31，数量 5）并提交。
2. 默认策略命中 `EXEMPT`，提交自动批准，申请进入 `APPROVED`，生成 ACTIVE 预占。
3. `stockkeeper001` 确认发放，申请进入 `ISSUED`，生成发放记录 `FUL-20261124-614371` 与 `ISSUE_OUT` 流水。
4. 详情接口返回 `reservations`（含 `allocations`）、`approvalRecords`、`fulfillment`、`returns`。
5. 成本脱敏核验：成员查看 `unitCostSnapshot` 为空；管理员可见 `unitCostSnapshot=1`。
6. 无关成员访问他人申请返回 404 语义。

## 本轮修复

1. **成员详情误触发 403**：`InventoryIssueDetail` 原逻辑对所有角色都调用 `/api/inventory/movements`，成员被 `requireInventoryDetail()` 拒绝并弹出“无采购操作权限”。改为仅 `canViewInventoryMovements()` 时加载。
2. **当前可用为空**：后端申请详情行不携带 `onHandQuantity/availableQuantity`。前端在详情加载后对 `canViewIssueBatches()` 角色按 `componentId` 调用 `/api/inventory/components/{id}` 合并可用量；成员保持脱敏空值。

## 截图

共 30 张：5 个账号 × 3 个视口 × 列表/详情。

- `list-<account>-<viewport>.png`
- `detail-<account>-<viewport>.png`

重点：`detail-admin-1440.png`（成本与可用量可见）、`detail-member-1440.png`（成本脱敏、无 403 弹窗）、`detail-unrelated-1440.png`（不可见申请）。

## 已知后端跟进项

`GET /api/inventory/movements` 当前不接受 `requestId` 参数，前端虽传该参数但被后端忽略，详情页“该申请库存流水”无法按申请过滤。单申请数据下不影响展示，多申请并存时需后端补 `requestId` 过滤条件。
