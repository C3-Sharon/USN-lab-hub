# 采购、分批到货与库存增加 API 契约 V1

> 状态：REVIEW（D1-D10 已于 2026-11-06 由负责人确认；后端/前端可实现性待确认，禁止提前标记 FROZEN）
> 版本：v1.0-draft
> 目标周：第 9 周（2026-W45 / M03-W02）
> 影响范围：API、数据库、权限、事务、审计、媒体、前端字段与页面状态
> 关联周文档：`docs/weekly/2026-W45.md`
> 前置契约：`AUTH_WORKBENCH_V1.md`、`PROJECT_WORKSPACE_API.md`、`COMPONENT_CATALOG_MEDIA_API.md` v1.0.6
> 总体约束：总体规格 §6、§9、§15、§19

---

## 1. 目标与不可突破边界

本契约建立以下最小闭环：

```text
创建采购单及多个元器件明细
→ 提交时按冻结策略判断人工审批或免审
→ 审批通过/自动通过
→ 登记供应商和平台订单事实并标记已下单
→ 创建一批到货草稿
→ 确认到货
→ 同一数据库事务写到货事实、库存批次、库存增加流水和库存余额
→ 多次到货直到全部收到，或显式取消未到剩余数量
→ 完成采购单
```

必须区分：采购单、采购明细、审批记录、到货记录、到货明细、库存批次、库存增加流水、库存余额投影、发票及采购附件。采购单状态只描述采购流程，不能代替库存数量。

只有 `CONFIRMED` 到货才能增加库存。创建/编辑到货草稿、上传附件、提交审批、登记下单或查看页面均不得改变库存。

### 1.1 本周明确不做

- 不做领用、退回、损耗、报废、盘点、预占和库存扣减；它们属于第 10 周。
- 不做实物退货、退款、逆向库存流水和财务红字。
- 不做资产借还、维护、BOM、打板、装配扣料和板卡实例。
- 不做完整 ERP、付款、报销、会计、税务和财务对账。
- 不做供应商主数据、网页抓取、自动询价或自动下单。
- 不引入微服务、Kafka、Kubernetes或新存储中间件。
- 不改变第 8 周 `mediaId`、逻辑对象键、媒体内容接口、供应商中立存储端口和 ACL 继承原则。
- 不支持 PDF/OCR；W45 采购附件沿用已验证的 JPEG/PNG 安全图片能力。

## 2. D1-D10 高风险决策（负责人已确认）

### D1 审批阈值与配置

**推荐：全局版本化采购策略，默认普通采购 `payableAmount < CNY 500.00` 免审；金额等于或超过 500.00 必须人工审批。**

- 仅 SYSTEM_ADMIN 可通过受控设置 API 发布新策略版本；不直接覆盖旧策略。
- 策略立即对后续“提交采购单”生效；已提交采购单保留提交时的策略版本和判断快照，不追溯重算。
- 免审不是没有审批事实：系统写入 `AUTO_APPROVED` 审批记录，记录策略版本、判定金额和原因。
- 审批阈值以服务端计算的 `payableAmount` 判断，不使用客户端传入总额。

### D2 贵重物料和指定类别判定

**推荐：任一明细单价达到 `CNY 1000.00`，或其 component category 出现在策略的强制审批分类清单，整单必须人工审批。**

- 强制审批采用 OR：金额阈值、贵重单价、指定分类任一触发即可。
- 指定分类包含后代分类；策略保存 categoryId，不修改元器件分类表。
- 提交时冻结每个触发原因、分类路径、componentId、单价和策略版本，后续分类或价格变化不改变历史审批结论。
- W45 不实现自动识别“贵重”文本或 Agent 判断。

### D3 采购状态与转换

**推荐状态：`DRAFT / PENDING_APPROVAL / APPROVED / ORDERED / PARTIALLY_RECEIVED / RECEIVED / COMPLETED / REJECTED / CANCELED`。**

```text
DRAFT --submit(manual)--> PENDING_APPROVAL --approve--> APPROVED
DRAFT --submit(exempt)--> APPROVED（同时写 AUTO_APPROVED）
PENDING_APPROVAL --reject--> REJECTED
APPROVED --mark ordered--> ORDERED
ORDERED --first partial receipt--> PARTIALLY_RECEIVED
ORDERED --all quantities received--> RECEIVED
PARTIALLY_RECEIVED --all quantities received--> RECEIVED
RECEIVED --complete--> COMPLETED
DRAFT/PENDING_APPROVAL/APPROVED --cancel--> CANCELED
ORDERED --cancel all before receipt--> CANCELED
PARTIALLY_RECEIVED --cancel remaining--> COMPLETED（closeReason=REMAINDER_CANCELED）
```

- REJECTED、CANCELED、COMPLETED 为终态；需要重办时复制为新 DRAFT，不恢复旧单。
- 状态动作必须保存操作者、前后状态、时间、reason、请求 ID 和 version。

### D4 编号、平台订单号和供应商

**推荐：内部采购编号服务端生成；W45 保存供应商快照，不建设供应商主数据。**

- `purchaseNo = PO-YYYYMMDD-XXXXXX`，全局唯一、创建后不可修改。
- `supplierName` 为下单时必填的 2-120 字符规范化文本；DRAFT 可为空。
- `platformType = TAOBAO / JD / PINDUODUO / 1688 / OFFLINE / OTHER`。
- `platformOrderNo` 可空；非空时 `(platformType, normalizedPlatformOrderNo)` 全局唯一。
- 平台订单号重复返回 409，不自动合并采购单。

### D5 金额、运费、优惠、实付和币种

**推荐：W45 只支持 CNY；数量 3 位小数、单价/批次单位成本 4 位小数、金额 2 位小数，统一 `HALF_UP`。**

- `orderedQuantity DECIMAL(19,3)`，必须大于 0，单位固定为元器件 `baseUnit` 快照。
- `unitPrice DECIMAL(19,4)`，必须大于 0。
- `lineSubtotal = round(orderedQuantity × unitPrice, 2, HALF_UP)`。
- `itemSubtotal = sum(lineSubtotal)`。
- `payableAmount = itemSubtotal + shippingAmount - discountAmount`，不得小于 0。
- `shippingAmount/discountAmount/actualPaidAmount` 使用 DECIMAL(19,2)，不得为负。
- 下单时 `actualPaidAmount` 必填且不得大于 payableAmount；小于 payableAmount 时 `paymentDifferenceNote` 必填。实付增加必须取消并复制新单重新审批，不能绕过原审批上限。这里只记录订单事实，不表示平台执行付款。
- 运费和优惠先形成 payableAmount；下单时按实际实付金额与行小计比例分摊，最后一条明细吸收 0.01 元级尾差。冻结 `allocatedNetAmount/effectiveUnitCost`，到货批次复用该单位成本，不在每批重新分摊。
- 任一 component 首次进入已提交采购或库存批次后，其 `baseUnit` 不允许直接修改；单位变化必须新建元器件型号或通过后续专门迁移，不做静默换算。

### D6 项目关联与可见范围

**推荐：项目关联可选；项目角色提供附加可见性，不授予采购审批或入库权限。**

- `projectId` 可空；非空时创建者必须为该项目 OWNER/MAINTAINER，或具备 SYSTEM_ADMIN 覆盖权限。
- SYSTEM_ADMIN、TEACHER、STOCK_KEEPER 可查看全部采购单。
- 请求人和登记购买人可查看自己的采购单。
- 关联项目 OWNER/MAINTAINER 可查看采购单、发票和附件；项目 MEMBER/OBSERVER 不因项目关联自动获得采购可见性。
- 项目 OWNER/MAINTAINER 不能仅凭项目角色批准采购或确认到货。
- 不可见资源统一返回 404，避免编号枚举。

### D7 部分到货、超量与取消剩余数量

**推荐：允许多批部分到货；超量容差为 0；已确认到货不可撤销，未到剩余量可显式取消。**

- `remainingQuantity = orderedQuantity - receivedQuantity - canceledQuantity`，三者同单位和 3 位小数。
- 确认到货时每条 `receivedQuantity > 0` 且不得超过当时 remainingQuantity。
- 任一明细超量则整次确认失败，其他明细也不得入库。
- 同一采购单同时最多存在一张 DRAFT 到货记录，取消或确认后才能创建下一张。
- PARTIALLY_RECEIVED 可通过 `cancel-remaining` 逐明细取消所有剩余量；必须填写 1-500 字符原因。
- `received + canceled = ordered` 时明细结清；全部实收则采购单 RECEIVED，含取消剩余则直接 COMPLETED 并记录关闭原因。

### D8 库存批次编号、成本和保质字段

**推荐：每条已确认到货明细生成一个独立库存批次，不跨到货或元器件合并。**

- `batchNo = LOT-YYYYMMDD-XXXXXX`，全局唯一。
- 保存 componentId、到货明细 ID、初始/当前数量、单位、effectiveUnitCost、供应商批号、生产日期、到期日期、位置备注和质量备注。
- `expiryDate` 可空；非空时不得早于 productionDate。
- W45 currentQuantity 初始等于 receivedQuantity；本周没有任何合法扣减 API。
- 每个 receiptLineId 只能生成一个库存批次和一条 `PROCUREMENT_RECEIPT_IN` 流水。

### D9 发票、截图、到货和包装照片

**推荐：新增媒体 owner/purpose，不改变 mediaId 与存储边界；附件全部可选。**

- ownerType additive 扩展：`PROCUREMENT_ORDER / PROCUREMENT_RECEIPT`。
- purpose：`ORDER_SCREENSHOT / INVOICE / RECEIPT_PHOTO / PACKAGE_PHOTO`。
- ORDER_SCREENSHOT、INVOICE 归采购单；RECEIPT_PHOTO、PACKAGE_PHOTO 归到货记录。
- 每张采购单最多 5 张订单截图、5 张发票；每张到货记录最多 10 张到货/包装照片合计。
- 继续只允许 JPEG/PNG、单文件 5 MiB、1-4096 像素；上传、替换、逻辑删除及基础设施字段禁止规则复用 v1.0.6。
- 发票可见：SYSTEM_ADMIN、TEACHER、STOCK_KEEPER、请求人、购买人、关联项目 OWNER/MAINTAINER。
- 其他采购附件继承采购单可见范围；知道 mediaId 不代表可以读取。
- 附件不存在、已删除或 owner 不可见统一 404 `MEDIA_NOT_FOUND`。

### D10 退货、退款和采购撤销范围

**推荐：W45 只实现拒绝、下单前取消、无到货的已下单整单取消和部分到货后的剩余数量取消。**

- 不实现已确认到货的撤回、实物退货、退款、PARTIALLY_RETURNED/RETURNED 或负库存流水。
- 已确认到货发现错误时不得改表或删除批次；登记风险并在第 10 周库存修正/退回契约中处理。
- 第 10 周冻结逆向库存动作，第 12 周资源闭环验收再次评审退货/退款是否进入 v0.4。

## 3. 领域对象与字段字典

### 3.1 采购策略版本 `ProcurementPolicyVersion`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `id` | long | 自动 | 主键 |
| `policyVersion` | int | 自动 | 从 1 递增、唯一 |
| `currency` | enum | 是 | W45 固定 CNY |
| `approvalThreshold` | decimal(19,2) | 是 | 默认 500.00，> 0 |
| `valuableUnitPriceThreshold` | decimal(19,4) | 是 | 默认 1000.0000，> 0 |
| `forcedCategoryIds` | array<long> | 是 | 可空数组，引用 ACTIVE 分类 |
| `effectiveAt` | datetime | 自动 | 发布即生效 |
| `createdBy/createTime` | long/datetime | 自动 | 审计 |

策略版本追加写、不可覆盖或删除。更新 API 使用当前 policyVersion 防止并发覆盖。

### 3.2 采购单 `ProcurementOrder`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `id` | long | 自动 | 主键 |
| `purchaseNo` | string | 自动 | `PO-YYYYMMDD-XXXXXX`，全局唯一 |
| `title` | string | 是 | 2-120 字符 |
| `requesterId/requesterName` | long/string | 自动 | 申请人快照 |
| `buyerId/buyerName` | long/string | 条件 | 下单时必填，可与申请人相同 |
| `projectId/projectName` | long/string | 否 | 项目关联可空；名称为响应快照 |
| `fundingSource` | string | 否 | 最多 120 字符，仅说明来源，不做财务核算 |
| `supplierName` | string | 条件 | 下单时必填，2-120 字符 |
| `platformType` | enum | 条件 | 下单时必填，见 D4 |
| `platformOrderNo` | string | 否 | 最多 120 字符 |
| `currency` | enum | 自动 | CNY |
| `itemSubtotal` | decimal(19,2) | 自动 | 明细小计之和 |
| `shippingAmount` | decimal(19,2) | 是 | 默认 0.00 |
| `discountAmount` | decimal(19,2) | 是 | 默认 0.00 |
| `payableAmount` | decimal(19,2) | 自动 | 服务端计算 |
| `actualPaidAmount` | decimal(19,2) | 条件 | 下单时必填 |
| `paymentDifferenceNote` | string | 条件 | 实付低于应付时必填，1-500 字符 |
| `status` | enum | 自动 | 见 D3 |
| `approvalRequired` | boolean | 自动 | 提交时冻结 |
| `approvalPolicyVersion` | int | 条件 | 提交后必有 |
| `approvalReasons` | array<string> | 自动 | AMOUNT_THRESHOLD / VALUABLE_ITEM / FORCED_CATEGORY / EXEMPT |
| `orderedAt/completedAt` | datetime | 条件 | 状态动作时间 |
| `closeReason` | string | 否 | 取消或关闭剩余量原因 |
| `version` | int | 自动 | 从 1 开始 |
| 审计字段 | — | 自动 | createdBy/createTime/updatedBy/updateTime |

### 3.3 采购明细 `ProcurementOrderLine`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `id` | long | 自动 | 主键 |
| `orderId` | long | 是 | 所属采购单 |
| `lineNo` | int | 自动 | 单内从 1 递增，(orderId,lineNo) 唯一 |
| `componentId` | long | 是 | 创建时必须可见；提交时必须 ACTIVE |
| `componentName/manufacturerName/mpn` | string | 自动 | 提交时冻结快照 |
| `componentSpecSnapshotId/specVersion` | long/int | 自动 | 提交时固定当前正式规格 |
| `unit` | enum | 自动 | 固定为 component.baseUnit |
| `orderedQuantity` | decimal(19,3) | 是 | > 0 |
| `unitPrice` | decimal(19,4) | 是 | > 0 |
| `lineSubtotal` | decimal(19,2) | 自动 | 服务端计算 |
| `allocatedNetAmount` | decimal(19,2) | 自动 | 下单时冻结 |
| `effectiveUnitCost` | decimal(19,4) | 自动 | 分摊后单位成本 |
| `receivedQuantity` | decimal(19,3) | 自动 | 初始 0 |
| `canceledQuantity` | decimal(19,3) | 自动 | 初始 0 |
| `remainingQuantity` | decimal(19,3) | 响应 | ordered-received-canceled |
| `version` | int | 自动 | 并发保护 |

同一 DRAFT 中同一 componentId 最多一行；需要不同价格时拆成不同采购单，避免到货成本归属不清。

### 3.4 审批记录 `ProcurementApprovalRecord`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id/orderId` | long | 自动 | 主键与采购单 |
| `decision` | enum | 自动/是 | AUTO_APPROVED / APPROVED / REJECTED |
| `policyVersion` | int | 是 | 提交策略版本 |
| `triggerSnapshot` | object | 是 | 金额、分类、贵重明细及理由快照 |
| `actorType` | enum | 自动 | SYSTEM / USER |
| `decidedBy/decidedAt` | long/datetime | 条件/自动 | AUTO_APPROVED 时 decidedBy=null、actorType=SYSTEM |
| `comment` | string | 条件 | 人工通过可空；拒绝必填 1-500 字符 |
| `requestId` | string | 自动 | 幂等/追踪 |

人工审批人不能是 requester；SYSTEM_ADMIN 不绕过该职责分离规则，需由另一名 TEACHER/SYSTEM_ADMIN 审批。登记下单时 buyer 也不能等于人工审批人，否则返回职责分离冲突。

### 3.5 到货记录 `ProcurementReceipt`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `id` | long | 自动 | 主键 |
| `receiptNo` | string | 自动 | `RCV-YYYYMMDD-XXXXXX`，全局唯一 |
| `orderId` | long | 是 | 采购单 |
| `status` | enum | 自动 | DRAFT / CONFIRMED / CANCELED |
| `receivedAt` | datetime | 是 | 实际到货时间，不得晚于当前时间超过 5 分钟 |
| `receiverId/receiverName` | long/string | 自动 | 确认人 |
| `note` | string | 否 | 最多 1000 字符 |
| `confirmedAt/canceledAt` | datetime | 条件 | 状态审计 |
| `version` | int | 自动 | 乐观锁 |
| 审计字段 | — | 自动 | createdBy/createTime/updatedBy/updateTime |

### 3.6 到货明细 `ProcurementReceiptLine`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `id` | long | 自动 | 主键 |
| `receiptId/orderLineId/componentId` | long | 是 | 固定关联；同一 receipt 内 orderLine 唯一 |
| `quantity` | decimal(19,3) | 是 | >0 且确认时不超 remaining |
| `unit` | enum | 自动 | 与采购明细一致 |
| `supplierLotNo` | string | 否 | 最多 120 字符 |
| `productionDate/expiryDate` | date | 否 | 到期不得早于生产日期 |
| `locationNote` | string | 否 | 最多 200 字符，不建设库位系统 |
| `qualityNote` | string | 否 | 最多 500 字符 |
| `version` | int | 自动 | 草稿编辑并发保护 |

CONFIRMED 到货记录及明细不可编辑或删除。

### 3.7 库存批次 `InventoryBatch`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `id` | long | 自动 | 主键 |
| `batchNo` | string | 自动 | `LOT-YYYYMMDD-XXXXXX`，全局唯一 |
| `componentId` | long | 是 | 元器件 |
| `sourceReceiptLineId` | long | 是 | 唯一，一条到货明细一个批次 |
| `initialQuantity/currentQuantity` | decimal(19,3) | 自动 | W45 两者初始相等 |
| `unit` | enum | 自动 | 采购明细单位 |
| `unitCost` | decimal(19,4) | 自动 | effectiveUnitCost |
| `supplierLotNo` | string | 否 | 到货快照 |
| `productionDate/expiryDate` | date | 否 | 到货快照 |
| `locationNote/qualityNote` | string | 否 | 到货快照 |
| `status` | enum | 自动 | AVAILABLE；其他库存状态留第 10 周 |
| 审计字段 | — | 自动 | createdBy/createTime |

### 3.8 库存增加流水 `InventoryMovement`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `id` | long | 自动 | 主键 |
| `movementNo` | string | 自动 | `MOV-YYYYMMDD-XXXXXX`，全局唯一 |
| `movementType` | enum | 自动 | W45 仅 PROCUREMENT_RECEIPT_IN |
| `componentId/batchId` | long | 是 | 事实关联 |
| `sourceType/sourceId` | enum/long | 是 | RECEIPT_LINE + receiptLineId，组合唯一 |
| `quantityDelta` | decimal(19,3) | 自动 | 必须为正 |
| `balanceBefore/balanceAfter` | decimal(19,3) | 自动 | 元器件总库存前后值 |
| `unit/unitCost` | enum/decimal | 自动 | 批次事实 |
| `operatorId/occurredAt` | long/datetime | 自动 | 确认到货操作者和时间 |
| `requestId` | string | 自动 | 幂等追踪 |

流水追加写、不可修改或删除。W45 不提供通用“调整库存”接口。

### 3.9 库存余额 `ComponentInventoryBalance`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `componentId` | long | 自动 | 主键 |
| `onHandQuantity` | decimal(19,3) | 自动 | 已确认入库减后续合法出库；W45 只增不减 |
| `unit` | enum | 自动 | component.baseUnit |
| `version` | int | 自动 | 条件更新/行锁保护 |
| `updateTime` | datetime | 自动 | 最近库存事实时间 |

库存余额是可由批次和流水重建的投影，不替代不可变流水事实。

### 3.10 发票及采购附件 `ProcurementAttachmentLink`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | long | 自动 | 业务关联 ID |
| `ownerType` | enum | 自动 | PROCUREMENT_ORDER / PROCUREMENT_RECEIPT |
| `ownerId` | long | 自动 | 采购单或到货记录 |
| `mediaId` | long | 自动 | 稳定媒体身份 |
| `purpose` | enum | 是 | 见 D9 |
| `invoiceNo` | string | 否 | purpose=INVOICE 时可填，最多 120 字符 |
| `note` | string | 否 | 最多 500 字符 |
| `version` | int | 自动 | 替换/删除并发保护 |
| 审计字段 | — | 自动 | createdBy/createTime/deletedBy/deletedAt |

业务 API 只返回 mediaId、purpose、受控媒体元数据和 `/api/media/{mediaId}/content`；禁止返回 objectKey、绝对路径、bucket、endpoint、provider、签名 URL或临时 URL。

## 4. 状态机与动作矩阵

### 4.1 采购单动作

| 当前状态 | 动作 | 操作者 | 结果 | 前置条件 |
|---|---|---|---|---|
| DRAFT | edit | requester；SYSTEM_ADMIN | DRAFT | version 匹配 |
| DRAFT | submit | requester；SYSTEM_ADMIN | PENDING_APPROVAL 或 APPROVED | 至少一条明细，component ACTIVE，金额重算 |
| PENDING_APPROVAL | approve | TEACHER/SYSTEM_ADMIN | APPROVED | 非本人申请/购买，version 匹配 |
| PENDING_APPROVAL | reject | TEACHER/SYSTEM_ADMIN | REJECTED | comment 必填 |
| APPROVED | mark-ordered | STOCK_KEEPER/SYSTEM_ADMIN | ORDERED | 供应商、平台、buyer、实付完整 |
| ORDERED/PARTIALLY_RECEIVED | create receipt | STOCK_KEEPER/SYSTEM_ADMIN | 状态不变 | 无其他 DRAFT receipt |
| ORDERED | confirm full receipt | STOCK_KEEPER/SYSTEM_ADMIN | RECEIVED | 全量且不过量 |
| ORDERED | confirm partial receipt | STOCK_KEEPER/SYSTEM_ADMIN | PARTIALLY_RECEIVED | 至少一条仍有 remaining |
| PARTIALLY_RECEIVED | confirm next receipt | STOCK_KEEPER/SYSTEM_ADMIN | PARTIALLY_RECEIVED/RECEIVED | 同上 |
| RECEIVED | complete | STOCK_KEEPER/SYSTEM_ADMIN | COMPLETED | version 匹配；附件非前置 |
| DRAFT/PENDING_APPROVAL/APPROVED | cancel | requester 或 SYSTEM_ADMIN；APPROVED 另允许 STOCK_KEEPER | CANCELED | reason 必填 |
| ORDERED | cancel | STOCK_KEEPER/SYSTEM_ADMIN | CANCELED | confirmed receipt 数为 0 |
| PARTIALLY_RECEIVED | cancel-remaining | STOCK_KEEPER/SYSTEM_ADMIN | COMPLETED | 取消全部 remaining，reason 必填 |

项目角色只影响查看，不出现在审批、下单或到货确认操作者列。

### 4.2 到货状态

```text
DRAFT --confirm--> CONFIRMED
DRAFT --cancel--> CANCELED
```

CONFIRMED/CANCELED 为终态。到货确认失败时必须保持 DRAFT，便于修正后重试。

创建、编辑或取消 DRAFT 到货记录都会锁定采购单并递增 order.version；确认请求必须同时携带最新 receiptVersion 与 orderVersion，避免取消剩余量、另一批到货或采购关闭与当前确认交错。

## 5. 权限矩阵

| 操作 | SYSTEM_ADMIN | TEACHER | STOCK_KEEPER | MEMBER | 项目 OWNER/MAINTAINER | GUEST |
|---|---|---|---|---|---|---|
| 查看全部采购单 | 可 | 可 | 可 | 不可 | 不可 | 不可 |
| 查看本人申请/购买 | 可 | 可 | 可 | 可 | 可 | 不可 |
| 查看关联项目采购 | 可 | 可 | 可 | 仅本人 | 可 | 不可 |
| 创建采购单 | 可 | 可 | 可 | 可 | 可 | 不可 |
| 编辑 DRAFT | 可 | 仅本人 | 可（本人或代办） | 仅本人 | 仅本人 | 不可 |
| 提交/取消本人申请 | 可 | 可 | 可 | 可 | 仅本人 | 不可 |
| 人工批准/拒绝 | 可，且非本人 | 可，且非本人 | 不可 | 不可 | 不可 | 不可 |
| 登记下单 | 可 | 不可 | 可 | 不可 | 不可 | 不可 |
| 创建/编辑到货草稿 | 可 | 不可 | 可 | 不可 | 不可 | 不可 |
| 确认到货入库 | 可 | 不可 | 可 | 不可 | 不可 | 不可 |
| 查看发票 | 可 | 可 | 可 | 本人申请/购买 | 关联项目可 | 不可 |
| 上传订单截图/发票 | 可 | 本人采购 | 可 | 本人 DRAFT/已下单采购 | 仅本人 | 不可 |
| 上传到货/包装照片 | 可 | 不可 | 可 | 不可 | 不可 | 不可 |
| 查看库存批次/流水 | 可 | 可 | 可 | ACTIVE 元器件汇总只读，不看成本 | 同 MEMBER | 不可 |
| 修改采购策略 | 可 | 不可 | 不可 | 不可 | 不可 | 不可 |

- MEMBER 查询库存时不返回 unitCost、供应商批号、采购单号或附件。
- 不可见采购单、到货、附件统一 404；已知资源但动作权限不足返回 403。
- 媒体内容端点必须重新计算采购 owner ACL，不能只验证登录态。

## 6. 确认到货的事务与锁规则

`POST /api/procurement-receipts/{receiptId}/confirm` 必须在一个数据库事务中：

1. 检查并命中幂等记录；精确重放直接返回首次结果。
2. 锁定采购单，再按 orderLineId 升序锁定涉及的采购明细。
3. 锁定到货记录及 receiptLineId 升序的到货明细。
4. 按 componentId 升序锁定或创建库存余额行。
5. 重新计算每条 remainingQuantity；任一超量或状态非法则整体失败。
6. 将到货记录置为 CONFIRMED，不再可修改。
7. 每条到货明细创建唯一库存批次。
8. 更新库存余额并创建唯一增加流水，记录 before/after。
9. 累加采购明细 receivedQuantity/version，重新计算采购单状态/version。
10. 写审计和幂等响应后提交。

任何一步失败必须回滚本次到货状态、全部批次、全部流水、全部余额和采购数量。附件文件不参加该数据库事务，且不是确认到货前置条件。

固定唯一约束至少包括：

- purchaseNo、receiptNo、batchNo、movementNo。
- `(platformType, normalizedPlatformOrderNo)`（非空时）。
- `(orderId, lineNo)`、`(orderId, componentId)`。
- `(receiptId, orderLineId)`。
- `inventoryBatch.sourceReceiptLineId`。
- `(movementType, sourceType, sourceId)`。
- `(actorId, endpoint, Idempotency-Key)` 幂等命令键。

### 6.1 V13 数据保留与回滚注意事项

- V13 只能 additive 新建采购/库存表、索引、外键和初始策略版本；不得修改已共享执行的 V1-V12，不得删除或重写元器件/媒体历史。
- 默认策略通过带稳定业务键的幂等 INSERT 建立，不依赖数据库自增 ID；重复迁移不得产生第二份“版本 1”。
- 金额与数量列必须直接使用冻结的 DECIMAL 精度，不使用 FLOAT/DOUBLE。
- 迁移验证同时覆盖 V1→V13 干净库和含 V12 元器件/媒体事实的 V12→V13 升级；升级前后抽查 component、spec snapshot、mediaId 和 PM-001 事实不变。
- 发布回滚只回滚应用版本，不执行破坏性 down migration；V13 新表保留但旧应用不访问。若迁移本身失败，由 Flyway 保持失败状态并在隔离库修正新的迁移，不手工删真实数据表。
- 真实验收使用独立数据库或已批准备份；任何清理只针对已核验的隔离测试库。PR 必须记录备份/恢复命令、迁移版本和数据保留检查。

## 7. API 草案

### 7.1 策略

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/procurement/policy` | 获取当前策略 |
| GET | `/api/procurement/policy/versions` | 获取历史版本 |
| PUT | `/api/procurement/policy` | SYSTEM_ADMIN 发布新版本，携带 currentPolicyVersion |

### 7.2 采购单

| 方法 | 路径 | 说明 |
|---|---|---|
| POST/GET | `/api/procurement-orders` | 创建 DRAFT / 分页查询 |
| GET/PUT | `/api/procurement-orders/{id}` | 详情 / 编辑 DRAFT |
| POST | `/api/procurement-orders/{id}/submit` | 提交并判断审批 |
| POST | `/api/procurement-orders/{id}/approve` | 人工批准 |
| POST | `/api/procurement-orders/{id}/reject` | 人工拒绝 |
| POST | `/api/procurement-orders/{id}/mark-ordered` | 登记下单事实 |
| POST | `/api/procurement-orders/{id}/cancel` | 取消整单 |
| POST | `/api/procurement-orders/{id}/cancel-remaining` | 取消全部未到剩余量 |
| POST | `/api/procurement-orders/{id}/complete` | RECEIVED 后完成 |

### 7.3 到货与库存

| 方法 | 路径 | 说明 |
|---|---|---|
| POST/GET | `/api/procurement-orders/{id}/receipts` | 创建到货草稿 / 列表 |
| GET/PUT | `/api/procurement-receipts/{receiptId}` | 详情 / 编辑 DRAFT |
| POST | `/api/procurement-receipts/{receiptId}/confirm` | 原子确认并增加库存 |
| POST | `/api/procurement-receipts/{receiptId}/cancel` | 取消 DRAFT |
| GET | `/api/inventory/components/{componentId}` | 库存汇总 |
| GET | `/api/inventory/components/{componentId}/batches` | 批次分页 |
| GET | `/api/inventory/movements` | 流水分页 |

### 7.4 附件

| 方法 | 路径 | 说明 |
|---|---|---|
| POST/GET | `/api/procurement-orders/{id}/attachments` | multipart 上传 / 列表 |
| POST/GET | `/api/procurement-receipts/{receiptId}/attachments` | multipart 上传 / 列表 |
| POST | `/api/procurement-attachments/{id}/replacement` | 替换媒体，创建新 mediaId |
| DELETE | `/api/procurement-attachments/{id}` | 逻辑删除，带 attachmentVersion/mediaVersion/reason |
| GET | `/api/media/{mediaId}` | 复用受控媒体元数据 |
| GET | `/api/media/{mediaId}/content` | 复用 ACL 后安全内容读取 |

所有 POST 命令与 multipart 上传必须携带 `Idempotency-Key`。PUT 和状态动作携带当前业务 version；状态 POST 同时使用幂等键和 version。

采购单附件上传 multipart 字段固定为 `file/purpose/orderVersion/invoiceNo/note`；到货附件为 `file/purpose/receiptVersion/note`。替换字段为 `file/attachmentVersion/mediaVersion/ownerVersion` 并继承 purpose；删除 JSON 为 `attachmentVersion/mediaVersion/ownerVersion/reason`。服务端锁定 owner 后检查数量上限并递增 ownerVersion；精确重放返回首次 mediaId，不重复占用附件名额。

## 8. 请求与响应样例

### 8.1 创建采购单

```json
POST /api/procurement-orders
Idempotency-Key: 2d17d18b-...

{
  "title":"气象站原型元器件采购",
  "projectId":10,
  "fundingSource":"实验室开放基金",
  "shippingAmount":12.00,
  "discountAmount":5.00,
  "lines":[
    {"componentId":301,"orderedQuantity":20.000,"unitPrice":8.5000},
    {"componentId":302,"orderedQuantity":5.000,"unitPrice":60.0000}
  ]
}
```

```json
{
  "code":200,
  "msg":"操作成功",
  "data":{
    "id":901,
    "purchaseNo":"PO-20261105-000001",
    "status":"DRAFT",
    "currency":"CNY",
    "itemSubtotal":470.00,
    "shippingAmount":12.00,
    "discountAmount":5.00,
    "payableAmount":477.00,
    "version":1,
    "lines":[
      {"id":911,"lineNo":1,"componentId":301,"unit":"PCS","orderedQuantity":20.000,"receivedQuantity":0.000,"canceledQuantity":0.000,"remainingQuantity":20.000,"unitPrice":8.5000,"lineSubtotal":170.00},
      {"id":912,"lineNo":2,"componentId":302,"unit":"PCS","orderedQuantity":5.000,"receivedQuantity":0.000,"canceledQuantity":0.000,"remainingQuantity":5.000,"unitPrice":60.0000,"lineSubtotal":300.00}
    ]
  }
}
```

### 8.2 提交并自动免审

```json
POST /api/procurement-orders/901/submit
Idempotency-Key: 6a5c...

{"version":1}
```

若金额、贵重和分类均未触发：

```json
{
  "code":200,
  "msg":"采购单已提交并自动通过",
  "data":{"id":901,"status":"APPROVED","approvalRequired":false,"approvalReasons":["EXEMPT"],"approvalRecord":{"decision":"AUTO_APPROVED","policyVersion":1},"version":2}
}
```

达到阈值时同一接口返回 `PENDING_APPROVAL`，不得由前端自行判断或跳过审批。

### 8.3 人工审批与下单

```json
POST /api/procurement-orders/902/approve
Idempotency-Key: 746b...

{"version":2,"comment":"预算与用途已确认"}
```

```json
POST /api/procurement-orders/902/mark-ordered
Idempotency-Key: 5fb4...

{
  "version":3,
  "buyerId":21,
  "supplierName":"某授权经销商",
  "platformType":"1688",
  "platformOrderNo":"A202611050001",
  "actualPaidAmount":1250.00,
  "paymentDifferenceNote":null
}
```

### 8.4 创建并确认第一批到货

```json
POST /api/procurement-orders/902/receipts
Idempotency-Key: a839...

{
  "orderVersion":4,
  "receivedAt":"2026-11-05T14:30:00+08:00",
  "note":"第一箱到货",
  "lines":[
    {"orderLineId":921,"quantity":6.000,"supplierLotNo":"LOT-A","productionDate":"2026-10-01","expiryDate":null,"locationNote":"待上架 A 区","qualityNote":"外观正常"}
  ]
}
```

```json
POST /api/procurement-receipts/1001/confirm
Idempotency-Key: 73c9...

{"receiptVersion":1,"orderVersion":5}
```

```json
{
  "code":200,
  "msg":"到货已确认并入库",
  "data":{
    "receiptId":1001,
    "receiptNo":"RCV-20261105-000001",
    "receiptStatus":"CONFIRMED",
    "orderId":902,
    "orderStatus":"PARTIALLY_RECEIVED",
    "orderVersion":6,
    "batches":[{"batchId":1101,"batchNo":"LOT-20261105-000001","componentId":301,"quantity":6.000,"unit":"PCS","unitCost":8.6250}],
    "movements":[{"movementId":1201,"movementNo":"MOV-20261105-000001","quantityDelta":6.000,"balanceBefore":10.000,"balanceAfter":16.000}]
  }
}
```

相同幂等键和完全相同请求重放返回同一 receiptId/batchId/movementId/balanceAfter，不再次增加库存。

### 8.5 空列表与部分到货

```json
{"code":200,"msg":"操作成功","data":{"total":0,"page":1,"pageSize":20,"list":[]}}
```

详情进度固定返回：

```json
{"orderedQuantity":20.000,"receivedQuantity":6.000,"canceledQuantity":0.000,"remainingQuantity":14.000,"progressPercent":30.00,"lineState":"PARTIALLY_RECEIVED"}
```

## 9. 列表、搜索和排序

采购单列表：

```text
GET /api/procurement-orders?page=1&pageSize=20
  &status=PENDING_APPROVAL&projectId=10&requesterId=21
  &supplierKeyword=经销商&keyword=PO-2026
  &sortBy=updateTime&sortOrder=desc
```

- page 从 1 开始，pageSize 默认 20、最大 100。
- keyword 搜 purchaseNo/title/platformOrderNo；长度 1-80。
- 排序白名单：createTime/updateTime/purchaseNo/payableAmount/orderedAt。
- 默认 `updateTime DESC, id DESC`。
- 数据范围先按权限收敛，再做筛选和 total；不得返回不可见总数。

库存批次和流水分页默认按 createTime DESC/id DESC；SYSTEM_ADMIN/TEACHER/STOCK_KEEPER 可按 componentId、purchaseNo、receiptNo、batchNo、时间范围筛选。MEMBER 只使用元器件汇总接口。

## 10. 幂等、乐观锁和审计

- `Idempotency-Key` 为 8-128 字符；幂等作用域是 actorId + HTTP 方法 + 规范化端点 + key。
- 同 key + 同请求摘要精确重放首次 HTTP 状态和业务结果，即使资源后续 version 已变化也不误报冲突。
- 同 key 换内容返回 409 `IDEMPOTENCY_KEY_REUSED`。
- 状态动作条件更新包含 id + version + allowed current status；零行更新返回 VERSION_CONFLICT 或更具体的状态冲突。
- 到货确认另有数据库唯一约束兜底；不能依赖应用内锁或前端按钮防重。
- 审计固定记录 actorId、action、targetType/targetId、from/to status、requestId、reason、policyVersion、createTime。
- 禁止审计日志写密码、Token、媒体内部对象键、绝对路径或供应商凭据。

## 11. 错误语义

格式沿用 `{code,msg,reason,data}`；错误 `data.traceId` 必须存在。

### 11.1 400

| reason | 场景 |
|---|---|
| `INVALID_PARAMETER` | 字段、分页、排序、version 或幂等键格式非法 |
| `PROCUREMENT_AMOUNT_INVALID` | 金额负数、精度超限、应付小于 0或实付差异无说明 |
| `PROCUREMENT_QUANTITY_INVALID` | 数量非正、精度超限或单位不一致 |
| `PROCUREMENT_LINES_REQUIRED` | 没有采购明细 |
| `PROCUREMENT_COMPONENT_INVALID` | component 不存在、不可见、无正式规格或提交时非 ACTIVE |
| `RECEIPT_LINES_REQUIRED` | 到货无明细 |
| `RECEIPT_DATE_INVALID` | 到货/生产/到期日期非法 |
| `ATTACHMENT_PURPOSE_INVALID` | owner 与 purpose 不匹配 |

### 11.2 401/403

| HTTP | reason | 场景 |
|---:|---|---|
| 401 | `TOKEN_MISSING/TOKEN_INVALID/TOKEN_EXPIRED/ACCOUNT_DISABLED` | 全局鉴权 |
| 403 | `PROCUREMENT_OPERATION_DENIED` | 无采购写权限 |
| 403 | `PROCUREMENT_APPROVAL_DENIED` | 无审批权限或尝试审批本人采购 |
| 403 | `PROCUREMENT_SELF_APPROVAL` | requester/buyer 尝试审批本人采购 |
| 403 | `PROCUREMENT_RECEIPT_DENIED` | 无到货/入库权限 |
| 403 | `PROCUREMENT_ATTACHMENT_DENIED` | 无附件写权限 |
| 403 | `PROCUREMENT_POLICY_DENIED` | 非 SYSTEM_ADMIN 修改策略 |

### 11.3 404

| reason | 场景 |
|---|---|
| `PROCUREMENT_ORDER_NOT_FOUND` | 采购单不存在或不可见 |
| `PROCUREMENT_LINE_NOT_FOUND` | 明细不存在或不属于采购单 |
| `PROCUREMENT_RECEIPT_NOT_FOUND` | 到货不存在或不可见 |
| `INVENTORY_BATCH_NOT_FOUND` | 批次不存在或不可见 |
| `INVENTORY_MOVEMENT_NOT_FOUND` | 流水不存在或不可见 |
| `MEDIA_NOT_FOUND` | 附件不存在、已删除或 owner 不可见 |
| `PROJECT_NOT_FOUND` | 关联项目不存在或不可见 |

### 11.4 409

| reason | 场景 |
|---|---|
| `VERSION_CONFLICT` | 采购单、明细、到货、附件或策略版本过期 |
| `IDEMPOTENCY_KEY_REUSED` | 同 key 换请求内容 |
| `PROCUREMENT_INVALID_TRANSITION` | 当前状态不允许动作 |
| `PROCUREMENT_APPROVAL_REQUIRED` | 尝试绕过审批直接下单 |
| `PROCUREMENT_DUTY_CONFLICT` | buyer 与人工审批人为同一用户，违反职责分离 |
| `PROCUREMENT_PLATFORM_ORDER_DUPLICATE` | 平台+订单号重复 |
| `PROCUREMENT_POLICY_CHANGED` | 提交时策略版本并发变化，需重新计算 |
| `RECEIPT_DRAFT_EXISTS` | 同一采购单已有 DRAFT 到货 |
| `RECEIPT_OVER_QUANTITY` | 任一明细超 remaining，data 返回 lineId/remaining/requested |
| `RECEIPT_ALREADY_CONFIRMED` | 非幂等请求重复确认终态到货 |
| `RECEIPT_INVENTORY_DUPLICATE` | receiptLine 已存在批次/流水，数据库唯一约束兜底 |
| `PROCUREMENT_REMAINING_EXISTS` | 未全部收到/取消却尝试完成 |
| `PROCUREMENT_CONFIRMED_RECEIPT_EXISTS` | 已下单整单取消但已有确认到货 |
| `ATTACHMENT_LIMIT_EXCEEDED` | 用途或 owner 附件上限 |

### 11.5 500

| reason | 场景与要求 |
|---|---|
| `PROCUREMENT_TRANSACTION_FAILED` | 到货确认内部失败；事务必须全部回滚，不返回半完成 ID |
| `MEDIA_STORAGE_FAILED/MEDIA_PROCESSING_FAILED` | 复用媒体失败语义；不得改变采购或库存事实 |

事务失败响应不得暴露 SQL、表名、锁信息、文件路径或对象键。

## 12. 失败与越权样例

### 12.1 超量到货

```json
{
  "code":409,
  "msg":"到货数量超过未到数量",
  "reason":"RECEIPT_OVER_QUANTITY",
  "data":{"orderLineId":921,"remainingQuantity":4.000,"requestedQuantity":5.000,"traceId":"..."}
}
```

同一请求的其他明细、批次、流水和库存余额必须全部未变化。

### 12.2 版本冲突

```json
{"code":409,"msg":"采购单已被更新，请刷新后重试","reason":"VERSION_CONFLICT","data":{"currentVersion":7,"traceId":"..."}}
```

### 12.3 MEMBER 越权确认到货

```json
{"code":403,"msg":"无确认到货权限","reason":"PROCUREMENT_RECEIPT_DENIED","data":{"traceId":"..."}}
```

### 12.4 附件不可见

```json
{"code":404,"msg":"媒体不存在或不可见","reason":"MEDIA_NOT_FOUND","data":{"traceId":"..."}}
```

### 12.5 模拟事务失败

验收环境通过受控测试故障点在“第一条库存流水写入后”抛出异常；响应为 500 `PROCUREMENT_TRANSACTION_FAILED`。随后查询必须证明：receipt 仍为 DRAFT、无新 batch/movement、采购 receivedQuantity 和库存余额均未变化。该故障点只允许 test profile，生产 API 不接受故障参数。

```json
{"code":500,"msg":"到货确认失败，未产生库存变化","reason":"PROCUREMENT_TRANSACTION_FAILED","data":{"traceId":"..."}}
```

### 12.6 重复确认精确重放

首次确认与相同 key/相同请求的响应必须包含相同事实 ID：

```json
{
  "code":200,
  "msg":"到货已确认并入库",
  "data":{"receiptId":1001,"batches":[{"batchId":1101}],"movements":[{"movementId":1201,"balanceAfter":16.000}]}
}
```

重放后 batch、movement 行数和库存余额不变。使用新 key 再确认同一终态 receipt 返回 409 `RECEIPT_ALREADY_CONFIRMED`，而不是再次返回新的批次。

## 13. 页面信息层级与 UI 状态

### 13.1 路由范围

- `/procurements`：采购列表、筛选、待审批入口。
- `/procurements/new`：创建采购单。
- `/procurements/{id}`：详情、审批时间线、金额、到货进度、附件和审计。
- `/procurements/{id}/edit`：仅 DRAFT 编辑。
- `/procurements/{id}/approval`：人工批准/拒绝。
- `/procurements/{id}/receipts/new`：新建分批到货草稿。
- `/procurement-receipts/{id}`：到货详情、确认和照片。
- `/procurement/settings`：SYSTEM_ADMIN 采购策略版本。

### 13.2 采购详情显示顺序

1. 标题区：purchaseNo、标题、状态、申请人、项目、版本和主要动作。
2. 审批区：是否需审、触发原因、策略版本、审批时间线。
3. 订单区：供应商、平台、平台订单号、购买人、下单时间。
4. 金额区：明细小计、运费、优惠、应付、实付、差异说明和币种。
5. 明细区：元器件、MPN、规格版本、单位、采购/已到/取消/剩余、进度。
6. 到货区：每批 receiptNo、时间、状态、批次和流水入口。
7. 附件区：订单截图、发票、到货/包装照片；按 ACL 显示或隐藏。
8. 审计区：状态、操作者、reason、requestId 和时间；普通成员只看业务时间线。

### 13.3 状态文案

| 枚举 | 中文 |
|---|---|
| DRAFT | 草稿 |
| PENDING_APPROVAL | 待审批 |
| APPROVED | 已批准 |
| ORDERED | 已下单 |
| PARTIALLY_RECEIVED | 部分到货 |
| RECEIVED | 已全部到货 |
| COMPLETED | 已完成 |
| REJECTED | 已拒绝 |
| CANCELED | 已取消 |

### 13.4 完整 UI 状态

| 状态 | 要求 |
|---|---|
| loading | 列表/详情骨架；提交、审批、确认到货和上传使用局部 loading，防重复点击 |
| empty | 无采购、筛选无结果、无到货、无附件分别显示明确空状态；附件空不阻塞动作 |
| error | 显示 reason、traceId、重试；表单草稿不因请求失败丢失 |
| permission | 不可见资源显示 404；只读用户不渲染写按钮；附件无权不显示数量/文件名 |
| success | 展示新状态/version、receiptNo、batchNo、movementNo 和库存变化 |
| conflict | VERSION_CONFLICT 显示服务端 currentVersion，要求刷新比较，不自动覆盖 |
| partial-received | 明细显示采购/已到/取消/剩余和进度条，不把采购状态当库存数量 |
| transaction-failed | 明确“未入库，可修正后重试”，不得局部显示成功批次 |

固定视口：1440×900、1280×800、390×844。移动端金额摘要优先，明细和到货按卡片/折叠展示；禁止通过横向超宽表强塞全部字段。金额始终带 `¥` 与两位小数，数量最多三位小数并显示单位。

## 14. 与 W44 及后续周兼容

- W44：componentId、specSnapshotId、baseUnit、mediaId、媒体内容端点和供应商中立存储边界保持不变；ownerType/purpose 仅 additive 扩展。
- W45 对 component baseUnit 增加历史引用保护：存在已提交采购或库存批次后禁止直接改变单位，避免同一库存余额混合单位；其他 W44 目录语义不变。
- W45 不物理删除媒体原件，不支持 PDF，不改变 COMPONENT 图片上限、占位和 ACL。
- 第 10 周在 InventoryBatch/Movement/Balance 上增加领用、退回、损耗、报废、预占和扣减，不得改写 W45 入库流水。
- 第 12 周执行采购到领用并发、恢复和资源闭环验收，并重新评审退货/退款范围。
- 第 13 周 BOM 只能引用元器件和库存批次，不得把采购单状态作为可用库存。
- 第 18 周切换正式对象存储不得改变本周 mediaId、owner/purpose、业务 API 和 ACL。

## 15. REVIEW 确认清单

- [x] D1 默认 500.00 CNY 阈值与版本化策略
- [x] D2 单价 1000.0000 CNY 或指定分类强制审批
- [x] D3 采购状态机与 AUTO_APPROVED 事实
- [x] D4 内部编号、平台订单号唯一性和供应商快照
- [x] D5 CNY、精度、HALF_UP、分摊、实付上限和 baseUnit 历史保护
- [x] D6 项目可选关联和 OWNER/MAINTAINER 可见范围
- [x] D7 部分到货、零容差和取消剩余量
- [x] D8 一到货明细一批次、成本与保质字段
- [x] D9 四类媒体用途、数量、格式和 ACL
- [x] D10 取消范围与退货/退款延期
- [ ] 后端确认 V13、锁序、事务、幂等、唯一约束和故障回滚可实现
- [ ] 前端确认页面、金额/数量展示、附件 ACL 和三视口可实现

确认记录：负责人于 2026-11-06 明确回复“确认”，同意 D1-D10 推荐方案，包括职责分离、实付不得超过审批应付和 baseUnit 历史保护。后端与前端可实现性尚无证据，本契约继续保持 REVIEW；两方评审完成前不得标记 FROZEN、合入 dev、创建实现分支、迁移或业务页面。

## 16. 变更记录

| 日期 | 变更 | 状态 |
|---|---|---|
| 2026-11-05 | 初始 v1.0-draft：采购、审批、分批到货、库存增加事务和采购附件 | REVIEW |
| 2026-11-06 | 负责人确认 D1-D10；等待后端/前端可实现性评审 | REVIEW |
