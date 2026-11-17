# 库存领用、预占与受控库存流水 API 契约 V1

> 状态：REVIEW（D1-D12 推荐方案待负责人及前后端可实现性确认；禁止提前标记 FROZEN）
> 版本：v1.0-draft
> 目标周：第 10 周（2026-W46 / M03-W03）
> 影响范围：API、V14 数据库规划、权限、事务、审计、库存流水、前端字段与页面状态
> 关联周文档：`docs/weekly/2026-W46.md`
> 前置契约：`PROCUREMENT_INVENTORY_API.md` v1.0、`AUTH_WORKBENCH_V1.md`、`PROJECT_WORKSPACE_API.md`
> 总体约束：总体规格 §6.3、§6.4、第 10 周验收矩阵与 §19

---

## 1. 目标与不可突破边界

建立以下最小但完整的库存领用闭环：

```text
成员创建领用申请
→ 服务端选择可追溯库存批次并校验可用量
→ 提交时生成库存预占
→ 普通、低价值且不触发库存紧张规则：AUTO_APPROVED
→ 贵重、超阈值、指定类别或库存紧张：PENDING_APPROVAL
→ 批准后保留预占，拒绝/取消/超时释放预占
→ 库存管理员确认发放
→ 同一事务扣减批次和库存余额、消费预占并写 ISSUE_OUT 流水
→ 未使用物料可按原发放批次受控退回并写 RETURN_IN
```

本契约必须区分：领用申请、领用明细、审批记录、库存预占、批次分配、实际发放、库存流水。页面状态、申请状态、预占状态和库存数量不得合并成一个模糊字段。

库存变化只能由受控业务命令产生不可变流水。任何页面、管理员接口、SQL 运维入口都不得直接填写或覆盖 `onHandQuantity`、批次 `currentQuantity` 或历史流水。

### 1.1 明确不做

- 不做资产借还、资产维护和独立资产编号。
- 不做 BOM、装配工单、板卡实例或装配扣料。
- 不做采购退货、退款、财务红字和财务对账。
- 不做完整 ERP、微服务、Kafka、Redis 分布式锁或新中间件。
- 不用缓存替代 MySQL 库存、预占、审批和流水事实。
- 不修改、删除或覆盖 W45 `PROCUREMENT_RECEIPT_IN` 流水。
- 不做无来源的正向库存调整，也不把“盘点差异”实现成直接编辑余额。

## 2. D1-D12 高风险决策（推荐方案，待确认）

### D1 直接领用与人工审批规则

**推荐：所有提交成功的申请先预占；满足全部直接领用条件时写 `AUTO_APPROVED` 并进入 `APPROVED`，否则进入 `PENDING_APPROVAL`。**

人工审批触发条件采用 OR：

- 服务端估算总价值 `estimatedAmount >= CNY 100.00`；
- 任一分配批次 `unitCost >= CNY 500.0000`；
- 任一明细分类命中强制审批分类（包含后代分类）；
- 任一明细数量超过策略默认直接领用上限 `10.000`，或超过该 component 的覆盖值；
- 预占后任一 component 的 `availableQuantity / onHandQuantity < 20%`；
- 策略明确要求人工审批。

金额和成本只由服务端根据冻结批次分配计算。MEMBER 与项目角色不接收批次成本字段。

### D2 策略配置与历史快照

**推荐：使用追加写、版本化 `InventoryIssuePolicyVersion`；只有 SYSTEM_ADMIN 可发布。**

- 默认阈值见 D1；发布新版本只影响后续提交。
- 提交时冻结 policyVersion、触发原因、分类路径、成本估算、数量阈值和库存紧张计算结果。
- 已提交申请不因策略、分类、批次成本或库存变化追溯重算。

### D3 申请状态机

**推荐状态：`DRAFT / PENDING_APPROVAL / APPROVED / REJECTED / ISSUED / CANCELED / EXPIRED`。**

```text
DRAFT --submit(auto)--> APPROVED（写 AUTO_APPROVED + ACTIVE 预占）
DRAFT --submit(manual)--> PENDING_APPROVAL（写 ACTIVE 预占）
PENDING_APPROVAL --approve--> APPROVED（保留原预占）
PENDING_APPROVAL --reject--> REJECTED（释放预占）
DRAFT --cancel--> CANCELED
PENDING_APPROVAL/APPROVED --cancel--> CANCELED（释放预占）
PENDING_APPROVAL/APPROVED --expire--> EXPIRED（释放预占）
APPROVED --confirm issue--> ISSUED（扣减库存、消费预占、写 ISSUE_OUT）
```

终态：REJECTED、ISSUED、CANCELED、EXPIRED。ISSUED 后不能取消；未使用物料走独立 RETURN_IN 命令。

### D4 批准与实际发放是否分离

**推荐：分离。审批只授权，实际扣减由 STOCK_KEEPER/SYSTEM_ADMIN 的 `confirm-issue` 完成。**

- 审批通过后申请为 APPROVED，预占保持 ACTIVE。
- 实际发放成功后才生成 `InventoryFulfillment` 和 `ISSUE_OUT`。
- 这样避免“审批成功但实物尚未交付”被伪记为已发放。

### D5 部分发放与部分取消

**推荐：W46 不允许部分发放或部分取消；一张申请全量原子发放或整体失败。**

- 一个申请可有多明细，每条明细可跨多个批次。
- 任一明细、批次或预占不满足条件，整次发放回滚。
- 需要改变数量时，在 DRAFT 编辑；提交后取消整单并重新申请。
- `PARTIALLY_ISSUED` 和部分预占释放不进入 W46，避免制造难以解释的半完成状态。

### D6 批次选择与人工替换

**推荐：服务端确定性 FEFO + FIFO；允许库存管理员在发放前原子重分配。**

默认顺序：

1. 排除已过期、不可用或 `currentQuantity - reservedQuantity <= 0` 的批次；
2. 有 expiryDate 的批次按 expiryDate ASC（FEFO）；
3. 其余按到货/创建时间 ASC（FIFO）；
4. 同值按 batchId ASC；
5. 可跨批次满足一条明细。

STOCK_KEEPER/SYSTEM_ADMIN 可在 APPROVED 且未发放时通过 `reallocate` 改批次，必须填写原因；旧分配释放与新分配预占在同一事务完成，不能短暂超占或丢失预占。

### D7 预占时点、期限和超时

**推荐：提交成功时预占，默认 `expiresAt = submittedAt + 72h`；审批不延长期限。**

- 库存不足时提交整体失败，申请保持 DRAFT，不产生 ACTIVE 预占。
- 预占到期由数据库安全的定时扫描释放；任何状态命令执行前同时做惰性过期检查。
- 多实例下不得依赖 JVM 单机锁；后端可选择 `FOR UPDATE SKIP LOCKED` 或条件更新，但必须保持固定锁序和一次性释放。
- 超时只改变 reserved 和申请/预占状态，不生成库存数量流水，因为 onHand 未变化。

### D8 库存紧张判定

**推荐：W46 采用策略版本中的全局 `lowStockRatio=20%`，并允许 component 级直接数量上限覆盖；不新增可直接填写库存的“安全库存调整”。**

- 判定使用本次预占后的 available/onHand。
- `onHand=0` 直接视为库存不足；不能进入审批后等待未知库存。
- component 级覆盖只影响审批判断，不改变实际库存事实。

### D9 权限与职责分离

**推荐：TEACHER/SYSTEM_ADMIN 审批，STOCK_KEEPER/SYSTEM_ADMIN 发放；项目角色只扩展可见性。**

- 申请人不能审批自己的申请。
- 发放人不能是申请人；SYSTEM_ADMIN 不绕过该规则。
- TEACHER 不能确认发放，STOCK_KEEPER 不能人工审批。
- 项目 OWNER/MAINTAINER 可查看关联项目申请，但不能仅凭项目角色审批、发放或查看成本。

### D10 RETURN_IN、LOSS_OUT、SCRAP_OUT 本周范围

**推荐：W46 实现三类受控命令；均必须关联原批次并写不可变流水。**

- `RETURN_IN`：成员针对已发放明细发起退回，STOCK_KEEPER/SYSTEM_ADMIN 确认；累计退回不得超过原发放未退数量。
- `LOSS_OUT`：STOCK_KEEPER/SYSTEM_ADMIN 登记遗失/非正常损耗，reason 必填，只能扣减未预占可用量。
- `SCRAP_OUT`：STOCK_KEEPER/SYSTEM_ADMIN 登记报废，reason 必填，只能扣减未预占可用量。
- 本周不实现采购退款或把退回写回采购状态。

### D11 盘点修正范围

**推荐：W46 冻结 `STOCKTAKE_ADJUSTMENT` 语义但不实现写 API；第 12 周恢复。**

第 12 周必须新增盘点单、账面量/实盘量、差异复核、正负调整、双人确认和恢复证据。W46 只保留 movementType，不允许通用 adjustment 接口，以免变相直接改库存。

### D12 项目关联与可见性

**推荐：projectId 可选；申请人必须是项目成员，或由 SYSTEM_ADMIN 代办。**

- 申请人、全局 TEACHER/STOCK_KEEPER/SYSTEM_ADMIN 可查看。
- 关联项目 OWNER/MAINTAINER 可查看业务字段和进度；项目 MEMBER/OBSERVER 只看本人申请。
- MEMBER、项目 OWNER/MAINTAINER 不返回 unitCost、estimatedAmount 或采购来源敏感字段。
- 不可见申请统一 404，列表 total 先按权限收敛。

## 3. 库存数量定义与不变量

### 3.1 元器件余额

| 字段 | 类型 | 定义 |
|---|---|---|
| `onHandQuantity` | decimal(19,3) | 已确认入库及退回增加，发放/损耗/报废减少的物理在手量 |
| `reservedQuantity` | decimal(19,3) | 所有 ACTIVE 预占之和；V14 新增，初始 0 |
| `availableQuantity` | decimal(19,3) | 响应计算字段：onHandQuantity - reservedQuantity |
| `unit` | enum | 与 component.baseUnit 一致 |
| `version` | int | 库存事实并发版本 |

必须始终满足：

```text
onHandQuantity >= 0
reservedQuantity >= 0
availableQuantity = onHandQuantity - reservedQuantity >= 0
component reservedQuantity = 该 component 所有 ACTIVE 批次预占之和
component onHandQuantity = 该 component 所有可计量批次 currentQuantity 之和
```

### 3.2 批次数量

V14 为 W45 `InventoryBatch` additive 增加 `reservedQuantity DECIMAL(19,3) DEFAULT 0`；响应增加 `availableQuantity = currentQuantity - reservedQuantity`。

```text
currentQuantity >= 0
reservedQuantity >= 0
currentQuantity - reservedQuantity >= 0
```

预占只增加 reserved，不减少 current/onHand；确认发放同时减少 current/onHand 和 reserved。释放预占只减少 reserved。

## 4. 领域对象与字段字典

### 4.1 `InventoryIssuePolicyVersion`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| id | long | 自动 | 主键 |
| policyVersion | int | 自动 | 唯一递增 |
| currency | enum | 是 | W46 固定 CNY |
| directAmountThreshold | decimal(19,2) | 是 | 推荐 100.00，>0 |
| valuableUnitCostThreshold | decimal(19,4) | 是 | 推荐 500.0000，>0 |
| defaultDirectQuantityLimit | decimal(19,3) | 是 | 推荐 10.000，>0 |
| lowStockRatio | decimal(5,2) | 是 | 推荐 20.00，范围 0-100 |
| reservationTtlHours | int | 是 | 推荐 72，范围 1-168 |
| forcedCategoryIds | array<long> | 是 | 可为空；包含后代分类 |
| effectiveAt | datetime | 自动 | 发布即生效 |
| createdBy/createTime | long/datetime | 自动 | 审计 |

策略追加写、不可覆盖和物理删除。component 级直接数量上限覆盖单独版本化审计，提交时写入触发快照。

### 4.2 `InventoryIssueRequest`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| id | long | 自动 | 主键 |
| requestNo | string | 自动 | `ISS-YYYYMMDD-XXXXXX`，全局唯一 |
| title | string | 是 | 2-120 字符 |
| purpose | string | 是 | 2-500 字符 |
| requesterId/requesterName | long/string | 自动 | 申请人快照 |
| projectId/projectName | long/string | 否 | 可选项目快照 |
| status | enum | 自动 | D3 七态 |
| approvalRequired | boolean | 自动 | 提交时冻结 |
| approvalReasons | array<string> | 自动 | 见 6.2 |
| policyVersion | int | 条件 | 提交后必有 |
| estimatedAmount | decimal(19,2) | 条件 | 服务端计算；普通成员响应隐藏 |
| submittedAt/approvedAt/issuedAt | datetime | 条件 | 状态时间 |
| expiresAt | datetime | 条件 | 提交后必有 |
| version | int | 自动 | 从 1 开始 |
| 审计字段 | — | 自动 | createdBy/createTime/updatedBy/updateTime |

### 4.3 `InventoryIssueLine`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| id/requestId | long | 自动/是 | 主键与申请 |
| lineNo | int | 自动 | (requestId,lineNo) 唯一 |
| componentId | long | 是 | ACTIVE 元器件 |
| componentName/manufacturerName/mpn | string | 自动 | 提交时冻结快照 |
| unit | enum | 自动 | component.baseUnit |
| requestedQuantity | decimal(19,3) | 是 | >0 |
| reservedQuantity | decimal(19,3) | 自动 | 提交后等于 requested，终态释放/消费后为 0 |
| issuedQuantity | decimal(19,3) | 自动 | W46 为 0 或 requested |
| returnedQuantity | decimal(19,3) | 自动 | 累计确认退回，不得超过 issued |
| estimatedAmount | decimal(19,2) | 自动 | 普通成员响应隐藏 |
| version | int | 自动 | 并发保护 |

同一申请同一 componentId 最多一行。

### 4.4 `InventoryIssueApprovalRecord`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| id/requestId | long | 自动 | 主键与申请 |
| decision | enum | 是 | AUTO_APPROVED / APPROVED / REJECTED |
| actorType | enum | 自动 | SYSTEM / USER |
| decidedBy/decidedAt | long/datetime | 条件 | 自动批准 decidedBy=null |
| policyVersion | int | 是 | 冻结策略 |
| triggerSnapshot | object | 是 | 阈值、成本、分类、数量、库存紧张快照 |
| comment | string | 条件 | 拒绝必填 1-500；批准可空 |
| requestIdempotencyId | string | 自动 | 追踪，不保存 Token |

审批记录追加写、不可修改或删除。

### 4.5 `InventoryReservation` 与 `InventoryReservationAllocation`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| reservationId/reservationNo | long/string | 自动 | `RSV-YYYYMMDD-XXXXXX`，唯一 |
| requestId/requestLineId | long | 是 | 一条提交明细一份预占 |
| componentId | long | 是 | 元器件 |
| quantity | decimal(19,3) | 是 | 等于申请明细数量 |
| status | enum | 自动 | ACTIVE / CONSUMED / RELEASED / EXPIRED |
| expiresAt | datetime | 是 | D7 |
| releaseReason | enum | 条件 | REJECTED/CANCELED/EXPIRED/REALLOCATED |
| version | int | 自动 | 并发保护 |

Allocation 字段：`id/reservationId/batchId/quantity/unitCostSnapshot/version`；同一 reservation+batch 唯一。成本仅授权角色可见。

### 4.6 `InventoryFulfillment` 与 `InventoryFulfillmentLine`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| id/fulfillmentNo | long/string | 自动 | `FUL-YYYYMMDD-XXXXXX`，唯一 |
| requestId | long | 是 | 每申请最多一条确认发放记录 |
| operatorId/operatorName | long/string | 自动 | 实物发放确认人 |
| recipientId/recipientName | long/string | 自动 | 默认申请人 |
| issuedAt | datetime | 自动 | 服务端时间 |
| note | string | 否 | 最多 500 |
| requestIdempotencyId | string | 自动 | 追踪 |

FulfillmentLine 固定引用 requestLineId、reservationAllocationId、batchId、quantity、unitCostSnapshot 和 movementId；一条分配生成一条 `ISSUE_OUT`。

### 4.7 `InventoryReturn`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| id/returnNo | long/string | 自动 | `RTN-YYYYMMDD-XXXXXX`，唯一 |
| fulfillmentId/requestId | long | 是 | 原发放事实 |
| status | enum | 自动 | PENDING_CONFIRMATION / CONFIRMED / REJECTED / CANCELED |
| reason | string | 是 | 2-500 字符 |
| requestedBy/confirmedBy | long | 自动/条件 | 审计 |
| version | int | 自动 | 乐观锁 |

ReturnLine 引用原 fulfillmentLineId、batchId、quantity；累计确认退回不得超过原发放数量减既有确认退回。

### 4.8 损耗/报废命令

`InventoryWriteOffCommand` 保存 commandNo、type=LOSS/SCRAP、componentId、batchId、quantity、reason、operator、occurredAt、movementId、requestId 和审计字段。命令与流水均不可修改或删除。

### 4.9 `InventoryMovement` additive 扩展

| movementType | delta | 本周语义 |
|---|---:|---|
| PROCUREMENT_RECEIPT_IN | 正 | W45 已生效，不修改 |
| ISSUE_OUT | 负 | 确认发放，一条批次分配一条流水 |
| RETURN_IN | 正 | 确认退回，固定回原批次 |
| LOSS_OUT | 负 | 受控损耗，不能扣预占数量 |
| SCRAP_OUT | 负 | 受控报废，不能扣预占数量 |
| STOCKTAKE_ADJUSTMENT | 正或负 | 仅冻结枚举；W46 无写 API，第 12 周恢复 |

流水继续追加写、不可修改或删除，保存 balanceBefore/After、batchBefore/After、operator、sourceType/sourceId、reason 和 requestId。

## 5. 状态动作与权限前置

| 当前状态 | 动作 | 操作者 | 结果 | 库存效果 |
|---|---|---|---|---|
| DRAFT | edit | 申请人；SYSTEM_ADMIN | DRAFT | 无 |
| DRAFT | submit(auto) | 申请人；SYSTEM_ADMIN 代办 | APPROVED | 创建 ACTIVE 预占 |
| DRAFT | submit(manual) | 同上 | PENDING_APPROVAL | 创建 ACTIVE 预占 |
| PENDING_APPROVAL | approve | 非本人 TEACHER/SYSTEM_ADMIN | APPROVED | 预占不变 |
| PENDING_APPROVAL | reject | 同上 | REJECTED | 全量释放预占 |
| APPROVED | reallocate | STOCK_KEEPER/SYSTEM_ADMIN | APPROVED | 原子替换批次预占 |
| APPROVED | confirm-issue | 非本人 STOCK_KEEPER/SYSTEM_ADMIN | ISSUED | 扣减 onHand/current/reserved，写 ISSUE_OUT |
| DRAFT | cancel | 申请人/SYSTEM_ADMIN | CANCELED | 无 |
| PENDING_APPROVAL/APPROVED | cancel | 申请人/SYSTEM_ADMIN | CANCELED | 全量释放预占 |
| PENDING_APPROVAL/APPROVED | expire | SYSTEM | EXPIRED | 全量释放预占 |

到期与发放竞争时只有一个事务成功；失败方必须读取终态并返回稳定 409，不得重复释放或超扣。

## 6. 审批判定、批次与金额

### 6.1 提交顺序

1. 锁申请及明细，确认 DRAFT/version。
2. 读取并冻结当前策略版本。
3. 按固定批次规则计算分配和成本估算。
4. 在锁内验证每个 component/batch available 足够。
5. 创建 ACTIVE 预占并增加 component/batch reserved。
6. 计算审批触发原因；写 AUTO_APPROVED 或进入 PENDING_APPROVAL。
7. 写审计、幂等响应并提交。

### 6.2 `approvalReasons`

枚举：`AMOUNT_THRESHOLD / VALUABLE_BATCH / FORCED_CATEGORY / QUANTITY_THRESHOLD / LOW_STOCK / POLICY_REQUIRED / EXEMPT`。

前端只显示业务文案；普通成员可看触发类型但不能看到批次成本或 estimatedAmount。

## 7. 权限矩阵

| 操作 | SYSTEM_ADMIN | TEACHER | STOCK_KEEPER | MEMBER | 项目 OWNER/MAINTAINER | GUEST |
|---|---|---|---|---|---|---|
| 创建本人申请 | 可 | 可 | 可 | 可 | 可 | 不可 |
| 查看本人申请 | 可 | 可 | 可 | 可 | 可 | 不可 |
| 查看全部申请 | 可 | 可 | 可 | 不可 | 不可 | 不可 |
| 查看关联项目申请 | 可 | 可 | 可 | 仅本人 | 可（无成本） | 不可 |
| 编辑/取消本人未发放申请 | 可 | 可 | 可 | 可 | 仅本人 | 不可 |
| 人工批准/拒绝 | 可，且非本人 | 可，且非本人 | 不可 | 不可 | 不可 | 不可 |
| 批次重分配 | 可 | 不可 | 可 | 不可 | 不可 | 不可 |
| 确认发放 | 可，且非本人 | 不可 | 可，且非本人 | 不可 | 不可 | 不可 |
| 发起本人退回 | 可 | 可 | 可 | 可 | 仅本人 | 不可 |
| 确认退回 | 可 | 不可 | 可 | 不可 | 不可 | 不可 |
| 登记损耗/报废 | 可 | 不可 | 可 | 不可 | 不可 | 不可 |
| 查看成本/采购来源 | 可 | 可 | 可 | 不可 | 不可 | 不可 |
| 发布领用策略 | 可 | 不可 | 不可 | 不可 | 不可 | 不可 |
| 查看库存流水 | 可 | 可 | 可 | 仅本人领用业务时间线 | 同 MEMBER | 不可 |

- 不可见申请、退回和批次统一 404；已知资源但动作无权返回 403。
- 列表和 total 必须先按数据范围收敛。
- 项目角色不授予审批、发放、损耗、报废或成本查看权。

## 8. 事务、固定锁序与并发

### 8.1 所有库存写命令的共同锁序

1. 检查或占用幂等命令记录。
2. 锁 `InventoryIssueRequest`（无申请的损耗/报废跳过）。
3. 按 requestLineId ASC 锁申请明细。
4. 按 componentId ASC 锁 `ComponentInventoryBalance`。
5. 按 componentId ASC、batchId ASC 锁 `InventoryBatch`。
6. 按 reservationId ASC 锁预占及 allocation。
7. 锁 fulfillment/return/write-off 当前命令事实。
8. 重算条件并写入事实、审计和幂等响应。

过期任务先无锁读取候选 ID，再按上述顺序逐单进入事务；不得先锁 reservation 再反向锁 request/balance/batch。LOSS/SCRAP 入口没有 request/reservation，但公共锁顺序仍必须是 balance → batch。

### 8.2 提交预占事务

同一事务完成申请状态、审批事实、reservation/allocation、component/batch reserved、审计和幂等响应。任一步失败全部回滚。

### 8.3 确认发放事务

同一事务完成：

- 校验 APPROVED、未过期、version 与 ACTIVE 预占；
- 创建唯一 fulfillment；
- 每个 allocation 扣减 batch current/reserved；
- 扣减 component onHand/reserved；
- 每个 allocation 写唯一 ISSUE_OUT；
- 预占转 CONSUMED，申请转 ISSUED；
- 写审计和精确幂等响应。

任一步失败必须保持申请 APPROVED、预占 ACTIVE、余额/批次/流水/fulfillment 全部未变化。

### 8.4 释放事务

拒绝、取消、超时在同一事务减少 component/batch reserved，将预占置为 RELEASED/EXPIRED，并更新申请状态、审计和幂等响应。onHand/current 不变化，不写 InventoryMovement。

### 8.5 退回、损耗与报废事务

- RETURN_IN：锁原申请/发放、balance、原 batch 和 return；校验可退余额后增加 current/onHand，写唯一 RETURN_IN。
- LOSS_OUT/SCRAP_OUT：锁 balance→batch；只能扣减 `current-reserved`，写负向流水。不得消费别人的 ACTIVE 预占。

### 8.6 唯一约束

至少包括：requestNo、reservationNo、fulfillmentNo、returnNo、writeOff commandNo；(requestId,lineNo)、(requestId,componentId)；每 requestLine 一份主预占；(reservationId,batchId)；每 request 最多一条 fulfillment；(movementType,sourceType,sourceId,batchId)；共享幂等命令键。

### 8.7 幂等与乐观锁

- 所有 POST 命令必须携带 8-128 字符 `Idempotency-Key`。
- 作用域：actorId + method + 规范化端点 + key。
- 同 key/同摘要精确重放首次 HTTP 状态和事实 ID；不得再次预占、扣减、释放或写流水。
- 同 key 换请求返回 409 `IDEMPOTENCY_KEY_REUSED`。
- PUT/状态动作携带 requestVersion；发放另携带 reservationVersion，退回携带 returnVersion。
- 版本冲突不得自动覆盖；前端刷新库存、预占和申请后由用户重新确认。

## 9. API 草案

### 9.1 策略与库存查询

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/inventory/issue-policy` | 当前策略 |
| GET | `/api/inventory/issue-policy/versions` | 历史版本 |
| PUT | `/api/inventory/issue-policy` | SYSTEM_ADMIN 发布新版本 |
| GET | `/api/inventory/components/{componentId}` | 扩展返回 onHand/reserved/available |
| GET | `/api/inventory/components/{componentId}/batches` | 授权角色查看批次可用量 |
| GET | `/api/inventory/movements` | 扩展 movementType/source 筛选 |

### 9.2 领用申请

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/inventory-issue-requests` | 创建 DRAFT |
| GET | `/api/inventory-issue-requests` | 权限收敛后的分页列表 |
| GET | `/api/inventory-issue-requests/{id}` | 详情 |
| PUT | `/api/inventory-issue-requests/{id}` | 携带 version 编辑 DRAFT |
| POST | `/api/inventory-issue-requests/{id}/submit` | 原子预占并判定审批 |
| POST | `/api/inventory-issue-requests/{id}/approve` | 人工批准 |
| POST | `/api/inventory-issue-requests/{id}/reject` | 拒绝并释放 |
| POST | `/api/inventory-issue-requests/{id}/cancel` | 取消并释放 |
| POST | `/api/inventory-issue-requests/{id}/reallocate` | 库存管理员原子换批次 |
| POST | `/api/inventory-issue-requests/{id}/confirm-issue` | 原子发放扣减 |

### 9.3 退回、损耗与报废

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/inventory-fulfillments/{id}/returns` | 发起退回 |
| GET | `/api/inventory-fulfillments/{id}/returns` | 权限收敛后的退回列表 |
| POST | `/api/inventory-returns/{id}/confirm` | 确认 RETURN_IN |
| POST | `/api/inventory-returns/{id}/reject` | 拒绝退回 |
| POST | `/api/inventory/batches/{batchId}/losses` | 登记 LOSS_OUT |
| POST | `/api/inventory/batches/{batchId}/scraps` | 登记 SCRAP_OUT |

W46 不提供 `/adjust`、直接 PUT balance 或 `STOCKTAKE_ADJUSTMENT` 写接口。

所有写响应都返回最新业务事实 ID、最新 version、状态与 requestId；所有分页响应统一返回 `items/page/pageSize/total`。状态动作的请求体至少包含对应 owner version，人工审批、重分配、退回、损耗和报废按本契约要求携带 comment 或 reason。业务响应不得返回 SQL、锁信息或媒体/存储基础设施字段。

## 10. 请求与响应样例

### 10.1 创建并提交申请

```http
POST /api/inventory-issue-requests
Idempotency-Key: 8a83...

{
  "title":"气象站原型焊接领料",
  "purpose":"焊接第一版传感器节点",
  "projectId":10,
  "lines":[
    {"componentId":301,"requestedQuantity":5.000},
    {"componentId":302,"requestedQuantity":2.000}
  ]
}
```

```json
{
  "code":200,"msg":"操作成功","data":{
    "id":2001,"requestNo":"ISS-20261116-000001","status":"DRAFT","version":1,
    "lines":[
      {"id":2011,"componentId":301,"unit":"PCS","requestedQuantity":5.000},
      {"id":2012,"componentId":302,"unit":"PCS","requestedQuantity":2.000}
    ]
  }
}
```

```http
POST /api/inventory-issue-requests/2001/submit
Idempotency-Key: c4b1...

{"requestVersion":1}
```

自动通过响应：

```json
{
  "code":200,"msg":"申请已预占，可领取","data":{
    "requestId":2001,"status":"APPROVED","approvalRequired":false,
    "approvalReasons":["EXEMPT"],"policyVersion":1,
    "expiresAt":"2026-11-16T10:00:00+08:00","requestVersion":2,
    "reservationVersion":1,
    "lines":[{"componentId":301,"onHandQuantity":20.000,"reservedQuantity":5.000,"availableQuantity":15.000}]
  }
}
```

### 10.2 确认发放

```http
POST /api/inventory-issue-requests/2001/confirm-issue
Idempotency-Key: e751...

{"requestVersion":2,"reservationVersion":1,"note":"已交付申请人"}
```

```json
{
  "code":200,"msg":"物料已发放","data":{
    "requestId":2001,"status":"ISSUED","fulfillmentId":2101,"fulfillmentNo":"FUL-20261116-000001",
    "requestVersion":3,
    "movements":[
      {"movementId":2201,"movementType":"ISSUE_OUT","batchId":1101,"quantityDelta":-5.000,"balanceBefore":20.000,"balanceAfter":15.000}
    ]
  }
}
```

精确重放必须返回相同 fulfillmentId/movementId/balanceAfter，且行数和余额不变。

### 10.3 库存不足

```json
{
  "code":409,"msg":"可用库存不足","reason":"INVENTORY_INSUFFICIENT_STOCK",
  "data":{"componentId":301,"requestedQuantity":8.000,"availableQuantity":5.000,"traceId":"..."}
}
```

申请保持 DRAFT，不创建 ACTIVE reservation，不改变任何 reserved/onHand/current 或流水。

### 10.4 并发争抢

同一 component available=5.000，两张申请并发各提交 4.000：只允许一张成功预占；另一张返回 409 `INVENTORY_INSUFFICIENT_STOCK` 或 `INVENTORY_CONCURRENT_CONFLICT`。最终 reserved=4.000、available=1.000，不得出现负数或两张 ACTIVE 预占。

### 10.5 审批拒绝与超时

拒绝响应返回 `REJECTED`、releaseReason=REJECTED；超时返回/查询为 `EXPIRED`、releaseReason=EXPIRED。两者均证明 onHand 不变、reserved 恢复、无新 InventoryMovement。

### 10.6 事务故障

test profile 在第一条 ISSUE_OUT 写入后抛出受控异常：

```json
{"code":500,"msg":"发放失败，库存未变化","reason":"INVENTORY_ISSUE_TRANSACTION_FAILED","data":{"traceId":"..."}}
```

随后必须证明申请仍 APPROVED、预占仍 ACTIVE、无 fulfillment/ISSUE_OUT、余额和批次数量完全未变。

## 11. 错误语义

错误格式沿用 `{code,msg,reason,data}`，错误 `data.traceId` 必须存在。

### 11.1 400

`INVALID_PARAMETER / ISSUE_LINES_REQUIRED / ISSUE_QUANTITY_INVALID / ISSUE_COMPONENT_INVALID / ISSUE_POLICY_INVALID / RETURN_QUANTITY_INVALID / WRITE_OFF_REASON_REQUIRED`。

### 11.2 401/403

| HTTP | reason | 场景 |
|---:|---|---|
| 401 | TOKEN_MISSING/TOKEN_INVALID/TOKEN_EXPIRED/ACCOUNT_DISABLED | 全局鉴权 |
| 403 | INVENTORY_ISSUE_OPERATION_DENIED | 无申请写权限 |
| 403 | INVENTORY_ISSUE_APPROVAL_DENIED | 无审批权限 |
| 403 | INVENTORY_ISSUE_SELF_APPROVAL | 审批本人申请 |
| 403 | INVENTORY_ISSUE_FULFILL_DENIED | 无发放权限或发放本人申请 |
| 403 | INVENTORY_WRITE_OFF_DENIED | 无损耗/报废权限 |
| 403 | INVENTORY_POLICY_DENIED | 非 SYSTEM_ADMIN 发布策略 |

### 11.3 404

`INVENTORY_ISSUE_REQUEST_NOT_FOUND / INVENTORY_RESERVATION_NOT_FOUND / INVENTORY_FULFILLMENT_NOT_FOUND / INVENTORY_RETURN_NOT_FOUND / INVENTORY_BATCH_NOT_FOUND`。资源不存在与不可见使用相同 reason。

### 11.4 409

| reason | 场景 |
|---|---|
| VERSION_CONFLICT | request/reservation/return/policy 版本过期 |
| IDEMPOTENCY_KEY_REUSED | 同 key 换请求 |
| INVENTORY_ISSUE_INVALID_TRANSITION | 非法状态动作 |
| INVENTORY_INSUFFICIENT_STOCK | component 或 batch available 不足 |
| INVENTORY_CONCURRENT_CONFLICT | 条件更新或并发锁后事实变化 |
| INVENTORY_RESERVATION_EXPIRED | 预占已过期 |
| INVENTORY_RESERVATION_NOT_ACTIVE | 预占已消费或释放 |
| INVENTORY_BATCH_ALLOCATION_CONFLICT | 批次替换时库存变化 |
| INVENTORY_ISSUE_ALREADY_CONFIRMED | 新 key 重复发放终态申请 |
| INVENTORY_ISSUE_DUTY_CONFLICT | 申请/审批/发放职责冲突 |
| INVENTORY_RETURN_EXCEEDS_ISSUED | 累计退回超过原发放 |
| INVENTORY_RESERVED_STOCK_PROTECTED | 损耗/报废试图占用已预占库存 |

### 11.5 500

`INVENTORY_RESERVATION_TRANSACTION_FAILED / INVENTORY_ISSUE_TRANSACTION_FAILED / INVENTORY_RELEASE_TRANSACTION_FAILED / INVENTORY_RETURN_TRANSACTION_FAILED / INVENTORY_WRITE_OFF_TRANSACTION_FAILED`。不得暴露 SQL、锁、表名或内部路径。

## 12. 列表、分页与排序

- page 从 1 开始，pageSize 默认 20、最大 100。
- 申请筛选：status、requesterId、projectId、componentId、submittedAt 范围、keyword。
- keyword 搜 requestNo/title/purpose；长度 1-80。
- 排序白名单：createTime/updateTime/submittedAt/expiresAt/requestNo/status；默认 updateTime DESC,id DESC。
- 流水筛选扩展 movementType、sourceType、operatorId、componentId、batchNo、occurredAt 范围。
- 数据范围先收敛再计算 total；不得泄露不可见申请、批次、成本或流水数量。

## 13. 前端信息层级与 UI 状态

### 13.1 路由与复用

- `/inventory/issues`：申请列表、本人申请、待审批/待发放入口。
- `/inventory/issues/new` 与 `/{id}/edit`：复用同一申请表单。
- `/inventory/issues/{id}`：详情主页面，承载状态、明细、审批、预占、批次来源、发放、退回和审计。
- `/inventory/issue-approvals`：TEACHER/SYSTEM_ADMIN 审批队列；动作可复用详情弹窗。
- `/inventory/fulfillments`：STOCK_KEEPER/SYSTEM_ADMIN 待发放队列。
- `/inventory/movements`：授权流水列表。
- `/inventory/issue-settings`：SYSTEM_ADMIN 策略页面。

损耗/报废使用批次详情中的受控对话框，不要求孤立复杂页面。退回发起/确认复用详情组件。

### 13.2 详情顺序

1. requestNo、标题、状态、申请人、项目、到期时间、主要动作。
2. 明细：元器件、单位、申请量、预占量、已发量、已退量。
3. 库存：onHand/reserved/available；成本仅授权角色可见。
4. 审批：触发原因、策略版本、时间线。
5. 批次：batchNo、数量、到期、来源；成本和采购来源按权限隐藏。
6. 发放/退回：事实编号、操作者、时间和流水入口。
7. 审计：状态变化、reason、requestId、时间。

### 13.3 状态文案

| enum | 中文 |
|---|---|
| DRAFT | 草稿 |
| PENDING_APPROVAL | 待审批 |
| APPROVED | 已批准，待发放 |
| REJECTED | 已拒绝 |
| ISSUED | 已发放 |
| CANCELED | 已取消 |
| EXPIRED | 预占已过期 |

### 13.4 完整 UI 状态

| 状态 | 要求 |
|---|---|
| loading | 列表/详情骨架；提交、审批、发放、释放局部 loading |
| empty | 无申请、无待审批、无待发放、无流水分别处理 |
| error | 显示 reason/traceId，可重试且不丢草稿 |
| permission | 无权资源按 404；不渲染按钮或成本/批次数量 |
| success | 显示新状态/version、预占变化、fulfillment/movement ID |
| conflict | 刷新 request/reservation/stock 后比较，禁止自动覆盖 |
| insufficient-stock | 显示申请量与当前 available，引导修改数量 |
| reserved | 显示 onHand/reserved/available、到期倒计时和释放条件 |
| expired | 禁用审批/发放，提示重新申请 |

固定视口：1440×900、1280×800、390×844。三视口覆盖关键页面、审批/发放弹窗、库存不足、冲突和过期状态；必须保存截图证据，不能只做 CSS 静态断言。

## 14. V14 数据保留与兼容边界

- V14 只能 additive 新建领用、审批、预占、分配、发放、退回和 write-off 表，并为 W45 balance/batch 增加 reservedQuantity 默认 0。
- V14 可受控扩展 W45 movementType CHECK/枚举，但不得修改 V1-V13 文件或改写既有 PROCUREMENT_RECEIPT_IN。
- 验证 V1→V14 干净库和含 V13 采购/批次/余额/流水/媒体事实的 V13→V14；迁移前后 W45 主流程事实不变。
- 应用回滚保留 V14 表/列，不执行破坏性 down migration；旧应用忽略新增列。
- W46 不影响 mediaId、采购附件 ACL、MQTT、知识、RAG 或 Agent。
- 第 12 周恢复 STOCKTAKE_ADJUSTMENT 与资源闭环；第 13-15 周 BOM/装配只能调用受控库存命令，不得直接扣表。

## 15. 可独立执行的验收脚本

1. 准备至少五个独立账号：申请 MEMBER、非本人的 TEACHER 审批人、STOCK_KEEPER 发放人、无关 MEMBER、SYSTEM_ADMIN 策略管理员。
2. 准备两个 ACTIVE component、至少三个可用批次，其中一个有较近 expiryDate；记录初始 onHand/reserved/available 和流水行数。
3. 小额普通申请提交：验证 AUTO_APPROVED、批次按 FEFO/FIFO、reserved 增加而 onHand 不变。
4. 贵重/超阈值申请提交：验证 PENDING_APPROVAL、触发快照和预占；本人审批 403，非本人 TEACHER 批准。
5. STOCK_KEEPER 确认发放：验证申请 ISSUED、预占消费、batch/current、balance/onHand/reserved 和 ISSUE_OUT 在同事务一致。
6. 相同 key/相同请求重放：返回首次 fulfillment/movement ID，余额、批次和行数不变；新 key 重复发放返回 `INVENTORY_ISSUE_ALREADY_CONFIRMED`。
7. 并发两张申请争抢同一 available：只一张成功，最终 available 不为负、ACTIVE 预占不超量。
8. 拒绝、取消、72h 超时分别验证：reserved 释放、onHand 不变、无库存流水；过期与发放并发只能一个成功。
9. test profile 在首条 ISSUE_OUT 后故障：申请仍 APPROVED、预占 ACTIVE、无 fulfillment/movement、余额/批次不变。
10. 退回：确认 RETURN_IN 回到原批次；重复退回和超量退回被拒绝。
11. 损耗/报废：只能扣可用未预占数量；尝试扣预占数量返回 `INVENTORY_RESERVED_STOCK_PROTECTED`。
12. 无关 MEMBER 查询申请、批次、成本返回 404 或受控脱敏响应，不泄露 total、成本和采购来源。
13. 前端关闭 mock，完成正常、空、失败、越权、冲突、库存不足、预占、过期和三固定视口截图。

SYSTEM_ADMIN 可用于冒烟，但不能代替全部角色后宣称权限门通过。真实 MySQL 双并发、故障注入和固定视口截图缺一项，W46 不得标记 VERIFIED。

## 16. REVIEW 确认清单

- [ ] D1 直接领用阈值和人工审批触发条件
- [ ] D2 版本化策略与历史快照
- [ ] D3 七态申请状态机
- [ ] D4 审批与实际发放分离
- [ ] D5 W46 全量发放、不做部分发放/部分取消
- [ ] D6 FEFO+FIFO 与受控重分配
- [ ] D7 提交预占、72h 期限和数据库安全过期释放
- [ ] D8 20% 库存紧张规则及 component 数量覆盖
- [ ] D9 全局角色、项目角色与职责分离
- [ ] D10 W46 实现 RETURN_IN/LOSS_OUT/SCRAP_OUT
- [ ] D11 STOCKTAKE_ADJUSTMENT 冻结语义、第 12 周实现
- [ ] D12 项目可选关联、成本与存在性零泄露
- [x] 后端提交 V14、模型、固定锁序、条件更新、幂等、过期释放、真实 MySQL 双并发和故障注入书面结论（2026-11-17；结论为需调整后可实现，见 W46 §8.1）
- [ ] 前端提交页面范围、数量展示、冲突刷新、权限零泄露、全部 UI 状态、mock 与三视口书面结论

两方结论必须记录评审人、日期、逐项“可实现/需调整/阻塞”、遗留风险和建议改约。负责人确认 D1-D12 且两方无阻塞项后，才可把 v1.0-draft 改为 v1.0 FROZEN。

## 17. 变更记录

| 日期 | 变更 | 状态 |
|---|---|---|
| 2026-11-16 | 初始草案：领用申请、条件审批、预占、全量发放、退回、损耗/报废与并发一致性 | REVIEW |
