# PR：第 10 周库存领用、预占与受控流水契约 REVIEW

## 建议标题

`docs(product): review W46 inventory issuance and reservation contract`

## 背景

第 10 周在 W45 已合入的库存批次、余额和不可变入库流水之上，建立领用申请、条件审批、库存预占、发放扣减、拒绝/取消/超时释放以及受控退回/损耗/报废契约。本 PR 只提交产品文档，不创建 V14，不编写后端业务代码或前端页面。

## 事实基线

- GitHub `dev@ae871cb` 已包含 PR #59、#61、#60。
- W45 后端真实 MySQL、四角色、首次余额双并发、故障回滚、183 项回归与打包通过。
- 前端关闭 mock 后完成真实 API 人工检查。
- W45 固定视口截图文件仍缺失，因此路线保持 IN_PROGRESS，不虚标 VERIFIED。
- W43、W44 原有证据债务继续独立记录。

## 本 PR 内容

- 新增 `docs/contracts/INVENTORY_ISSUANCE_API.md` v1.0-draft REVIEW。
- 新增 `docs/weekly/2026-W46.md`：范围、D1-D12、三人矩阵、评审提示词和验收方法。
- 校准 W45、CURRENT_STATE、ROADMAP、TRACEABILITY_MATRIX 和 CONTRACT_INDEX 的真实事实。
- 明确 onHand/reserved/available 不变量、七态申请状态机、固定锁序、事务边界、权限和错误 reason。
- 明确 RETURN_IN/LOSS_OUT/SCRAP_OUT 本周推荐范围，以及 STOCKTAKE_ADJUSTMENT 第 12 周恢复计划。

## 待负责人确认 D1-D12

1. 直接领用金额/价值/数量/低库存阈值。
2. 版本化策略与历史快照。
3. 七态申请状态机。
4. 审批与实际发放分离。
5. W46 不做部分发放或部分取消。
6. FEFO+FIFO 和受控批次重分配。
7. 提交预占、72 小时期限和安全过期释放。
8. 20% 库存紧张规则。
9. 全局角色、项目角色和职责分离。
10. W46 实现 RETURN_IN/LOSS_OUT/SCRAP_OUT。
11. 盘点修正冻结语义、第 12 周实现。
12. 项目关联、成本隐藏和不可见资源零泄露。

## 核心一致性边界

- available = onHand - reserved，且 onHand >= reserved >= 0。
- 预占不改变物理库存；确认发放才扣减并写 ISSUE_OUT。
- 拒绝、取消和超时只释放预占，不生成库存数量流水。
- 发放、释放、退回、损耗和报废均为单事务，任一步失败整体回滚。
- RETURN_IN 与其他入口使用同一正向锁序；批次不增加 version，使用行锁和数量条件更新防止超扣。
- 精确幂等重放返回首次事实 ID，不重复预占、扣减、释放或写流水。
- LOSS_OUT/SCRAP_OUT 只能扣未预占可用量。
- 页面和管理员不能直接编辑库存现量。

## 评审流程

1. 后端仅按 W46 阶段 REVIEW 提示词逐项评审 V14、模型、锁序、防超扣、过期释放、真实 MySQL 双并发、故障注入和 W45 兼容性；不建分支、不编码。
2. 前端仅逐项评审页面范围、数量展示、冲突刷新、权限零泄露、全部 UI 状态、mock 和三视口；不建分支、不编码。
3. 两方结论记录评审人、日期、逐项结论、遗留风险和建议改约。
4. 任一阻塞项先修订 REVIEW 契约。
5. 负责人确认 D1-D12 且两方均可实现后，才在同一 PR 改为 v1.0 FROZEN。
6. PR 转 Ready 并经负责人确认后合入 dev；随后才生成正式开工词和实现分支。

### 后端评审进展

- `fa1f26d` 已提交后端 1-11 项书面评审，总体结论为“需调整后可实现，无架构级阻塞”。
- 产品已按五项意见补齐 component override、RETURN_IN 锁序、V14 流水约束、batch 并发策略及有界数据库时间过期任务。
- 后端轻量复核已确认五项修订全部解决、无阻塞；前端 1-9 项书面评审仍待完成，契约继续保持 REVIEW。

## 契约影响

- API：计划新增领用策略、申请、审批、预占、发放、退回、损耗/报废端点。
- 数据库：计划 V14 additive；本 PR 不创建迁移。
- 权限：新增领用、审批、发放、退回、write-off、策略和成本可见范围。
- 库存：扩展 balance/batch reservedQuantity 与 movementType，不改写 PROCUREMENT_RECEIPT_IN。
- UI：计划新增申请、审批、发放、流水和策略页面及九类 UI 状态。
- 媒体、MQTT、知识、RAG、Agent：无影响。

## 明确不做

- 资产借还、BOM/装配、采购退款、ERP。
- Redis 分布式锁、新中间件或缓存事实源。
- 直接修改库存数量。
- V14、后端业务代码、前端页面或默认 mock。
- W46 盘点修正写 API；第 12 周恢复。

## 当前验证

- 文档引用与状态检查。
- Markdown/空白差异检查。
- REVIEW/FROZEN、W45 事实和延期项一致性检查。
- 不运行稳定全量前后端测试，不冒充 API 已实现。

## PR 状态与合并方向

```text
feature/m03-w03-product-inventory-issuance -> dev
```

本 PR 保持 Draft/REVIEW。D1-D12 尚未确认；后端首轮评审和轻量复核均已完成且无阻塞，前端书面评审尚未提交。不得提前合入或发送正式开工词。
