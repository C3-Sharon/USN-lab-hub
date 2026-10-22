# M02-W03 Product：冻结实验记录、FILE/MANUAL 采集批次与导师反馈契约

## PR 标题

```text
docs(product): freeze W43 experiment records, acquisition batches and mentor feedback
```

## 本周目标

- 收口 W42 模板版本周，将 #47/#48/#49 和 `dev@96e153f` 写回治理事实。
- 冻结“固定模板版本记录 → FILE/MANUAL 批次 → 不可变快照 → 导师修改/复审 → 发布 → PREPARING 项目草案”的 v0.3 闭环。

## 完成内容

- W42 改为 VERIFIED；路线图第 6 周 VERIFIED、第 7 周 IN_PROGRESS。
- 新增 `EXPERIMENT_RECORD_ACQUISITION_API.md` v1.0，并经用户确认冻结 D1-D6。
- 冻结记录与内容修订双层状态机，禁止覆盖提交和发布证据。
- 冻结导师 COMMENT / CHANGE_REQUEST / APPROVAL、可见范围、自我评审禁止和审计字段。
- 冻结 FILE CSV 原件、预览、列/单位/时间映射、确认、重复文件与导入修订语义。
- 冻结 MANUAL 方法、仪器、单位、观测时间和追加修改审计。
- 冻结完成批次、FULL_BATCH 快照、单修订单快照和完成后不可变规则。
- 冻结权限、分页、Idempotency-Key、乐观锁和 400/401/403/404/409/500 错误语义。
- 冻结轻量项目转换：PUBLISHED 实验一次性创建现有 PREPARING 项目并保留来源关系。
- 提供后端/前端同源开工提示词和真实 PowerShell 联调验收脚本。

## 明确未做

- MQTT v2、Serial/Bluetooth、Edge Collector、持续遥测区间快照。
- 完整质量引擎、大型波形、厂商私有格式和数据重放。
- MinIO 通用媒体、RAG、Agent、知识库、元器件、采购、库存和资产。
- 微服务、Kafka 或复杂工作流引擎。

## 契约影响

- API：新增实验记录/修订/反馈、采集计划/批次、FILE/MANUAL、快照和项目转换契约。
- MQTT：无变化。
- 数据库：后端后续以 V11 additive 迁移实现；本 PR 不包含迁移。
- 前端字段：新增记录/修订/反馈/批次/导入/快照字段和完整 UI 状态。
- 权限：新增全局角色、项目角色、作者、计划负责人和评审人的组合边界；禁止自我评审。
- 文件：W43 使用本地不可变存储适配器，限制 UTF-8 CSV 20 MiB/100,000 行/100 列；第 18 周切换 MinIO。

## 如何评审

1. 检查契约 §2 的 D1-D6 与 W43 §5 完全一致。
2. 从创建记录沿状态机走到发布，确认每次提交后只能新增修订。
3. 从 FILE/MANUAL 批次走到 COMPLETED 和快照，确认完成后无覆盖入口。
4. 对照权限矩阵检查普通成员、自我评审、跨项目和 GUEST。
5. 对照错误表和样例检查正常、空、失败、重复、越权和冲突。
6. 检查项目转换只复用 PREPARING，不新增项目状态或第二套项目实体。
7. 用 PowerShell Parser 检查验收脚本语法；待后端实现后执行真实联调。

## 已执行检查

```powershell
git diff --check

$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
  (Resolve-Path 'scripts/acceptance/w43-real-integration.ps1'),
  [ref]$tokens,
  [ref]$errors
) | Out-Null
```

- Markdown 差异检查：通过。
- PowerShell 7 语法解析：通过。
- 契约状态检查：W43 契约和索引均为 FROZEN。
- 未运行真实 W43 API：后端尚未实现，这是后端 PR 和真实联调阶段的验收项。

## 合并与协作

```text
本产品契约 PR
→ feature/m02-w03-backend-experiment-records
→ 后端 PR 合入 dev
→ feature/m02-w03-frontend-experiment-records 同步 dev 并关闭 mock
→ 运行 scripts/acceptance/w43-real-integration.ps1 和前端真实联调
→ 前端 PR
→ 更新周末结果和 v0.3 阶段验收
```

## 风险与回滚

- 最大风险是状态机、快照不可变、文件落盘与数据库事务不一致；后端在 V11 和事务实现前仍需按高风险门禁确认设计。
- 本 PR 只修改文档和验收脚本，不执行数据库迁移，不写真实业务数据。
- 如后端证明冻结语义不可安全实现，必须先修订本契约并重新确认，不得在代码中静默偏离。
