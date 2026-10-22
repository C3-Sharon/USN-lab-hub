# 实验记录、采集批次与导师反馈 API 契约 V1

> 状态：FROZEN
> 版本：v1.0（第 7 周：实验记录、FILE/MANUAL 采集、反馈与项目草案转换）
> 最后校准周：第 7 周（2026-W43）
> 冻结确认：2026-10-22，用户确认 D1-D6 全部采用推荐方案
> 影响范围：API、数据库、权限、文件存储、前端字段、审计、项目转换
> 关联周文档：`docs/weekly/2026-W43.md`
> 前置契约：`EXPERIMENT_TEMPLATE_API.md`、`LEARNING_EXPERIMENT_API.md`、`PROJECT_WORKSPACE_API.md`
> 设计约束：`docs/superpowers/specs/2026-08-22-data-acquisition-design.md`

---

## 1. 目标与本周边界

本契约建立以下最小完整闭环：

```text
成员按固定模板版本创建实验记录
→ 保存结构化内容修订
→ 以 FILE 或 MANUAL 来源形成采集批次
→ 从完成批次生成不可变快照并绑定内容修订
→ 提交导师评审
→ 评论、要求修改或批准
→ 发布仍可复现的记录
→ 转为一个正常的 PREPARING 项目草案
```

本周只实现 `FILE` 和 `MANUAL`。不实现 MQTT v2、Serial、Bluetooth、Edge Collector、持续遥测区间快照、实时设备控制、完整质量引擎、大型波形解析、RAG、Agent、知识库、元器件、库存、微服务、Kafka 或复杂工作流引擎。

## 2. 六项高风险冻结结论

以下决定已经用户确认，是前后端实现、测试与验收的共同边界。

### D1 实验记录与内容版本状态机

**冻结：记录聚合和内容修订分层。**

- 记录状态：`DRAFT → IN_REVIEW → CHANGES_REQUESTED → IN_REVIEW → APPROVED → PUBLISHED`。
- 已发布记录发起修订时：`PUBLISHED → REVISING → IN_REVIEW → … → PUBLISHED`。
- 内容修订状态：`EDITABLE → SUBMITTED → APPROVED → PUBLISHED`；被要求修改的修订标记 `CHANGES_REQUESTED`，被新发布版本替代后标记 `SUPERSEDED`。
- 只有 `EDITABLE` 修订可改；提交后内容、模板版本和快照引用均不可覆盖。
- 记录保留 `publishedRevisionId`，因此新修订评审期间旧发布版仍可读取和复现。

理由：用一条记录承载连续成果，用追加修订保存每次提交/发布证据，避免复制整条记录或覆盖历史。

### D2 导师要求修改后的流转

**冻结：要求修改是一条不可变反馈事件，并自动复制出下一份可编辑修订。**

- 导师不能直接修改成员内容。
- `CHANGE_REQUEST` 必须填写意见并指向被评审的 `revisionId`。
- 原修订冻结为 `CHANGES_REQUESTED`；系统复制其内容和快照引用为 `revisionNo + 1` 的 `EDITABLE` 修订。
- 成员修改后重新提交；导师再次评审。所有轮次和反馈永久保留。

理由：成员修改路径简单，同时保留导师看到的原始提交证据。

### D3 批次、快照与实验记录绑定时机

**冻结：只有 `COMPLETED` 批次可生成快照；记录只绑定快照，不直接绑定可变批次。**

- W43 每个内容修订最多绑定 1 个 `FULL_BATCH` 快照。
- 在修订仍为 `EDITABLE` 时可创建、替换或移除快照引用。
- 提交时同时冻结修订与引用；之后不能换批次或重建同名快照。
- 快照保存批次、导入/人工数据修订、映射版本、记录数、时间范围和 SHA-256 汇总；批次后续归档不影响快照读取。

理由：统一“整批引用”路径，避免把不断变化的批次或 latest 当作实验事实；以后可 additive 增加时间区间和多快照。

### D4 文件原件保存及限制

**冻结：原件必须保存；W43 使用可替换的本地不可变文件存储适配器，不提前引入 MinIO。**

- 原文件保存在服务端配置目录、Web 根目录之外；数据库只保存对象键、原文件名、大小、MIME、SHA-256、上传人和时间。
- 文件对象一经写入不可原地覆盖；业务删除只解除引用，本周不提供物理删除 API。
- 仅支持 `.csv`；编码仅 `UTF-8`/`UTF-8 BOM`；单文件不超过 20 MiB、100,000 数据行、100 列。
- 同时校验扩展名、MIME 和内容；禁止压缩包、Excel、宏、公式执行和路径穿越。
- 第 18 周媒体基础切换 MinIO 时保持对象键和 API 不变；W43 单实例本地存储限制写入部署说明。

理由：满足原始证据留存，又不把第 18 周对象存储和媒体处理提前塞入本周。

### D5 重复文件与人工记录修改

**冻结：重复文件复用原件，映射变化产生导入修订；人工记录始终追加修订。**

- 同一批次、同一 SHA-256 重传：返回已有导入任务，不重复保存或重复写记录。
- 同一原件在映射未变化时重复确认：幂等返回原结果。
- 同一原件采用不同映射：确认前创建新的 `importRevisionNo`，旧预览/映射保留；批次完成后禁止新增导入修订。
- 人工观测每次更正都新增修订，记录 `changeReason`、修改人、修改时间和前序修订 ID；不做覆盖更新。

理由：既避免重复计数，也允许纠正映射和人工录入错误，并保留完整审计。

### D6 转项目草案的最小范围

**冻结：复用现有项目模型，不新增第二套“项目草案”实体或状态。**

- 只有 `PUBLISHED` 实验记录可转换。
- 调用专用转换接口，用户确认 `code/name/summary/category` 后创建现有 `lab_project`，初始状态仍为 `PREPARING`；操作者自动成为 OWNER。
- 新增不可变来源关系 `sourceExperimentRecordId/sourcePublishedRevisionId`，项目详情展示“来源实验”。
- 同一实验记录最多转换一次；相同幂等键返回原项目，重复业务转换返回 409。
- 本周不自动复制成员、任务、附件、采集数据或硬件，也不建立项目模板体系。

理由：`PREPARING` 已表达可继续完善的项目草案，可在本周满足 v0.3 阶段门且不破坏现有项目状态机。

## 3. 核心领域模型

### 3.1 实验记录 `ExperimentRecord`

| 字段 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|
| `id` | long | 自动 | 主键 |
| `recordCode` | string | 自动 | `ER-YYYYMM-XXXXXX`，全局唯一 |
| `title` | string | 是 | 2-120 字符 |
| `summary` | string | 否 | 最多 500 字符 |
| `authorId` | long | 自动 | 创建人，创建后不可改 |
| `projectId` | long | 否 | 项目实验时必填；学习实验可为空 |
| `learningUnitId` | long | 否 | 学习实验可填；必须关联同一 `templateVersionId` |
| `templateId` | long | 自动 | 由模板版本反查 |
| `templateVersionId` | long | 是 | 只能引用创建时可见的 PUBLISHED 版本，之后不可改 |
| `status` | enum | 自动 | 见 §4.1 |
| `currentRevisionId` | long | 自动 | 当前工作修订 |
| `publishedRevisionId` | long | 否 | 当前对外有效的发布修订 |
| `convertedProjectId` | long | 否 | 成功转换后的项目 ID |
| `version` | int | 自动 | 记录聚合乐观锁，从 1 开始 |
| `createTime/updateTime` | datetime | 自动 | Asia/Shanghai 业务展示，API 使用 ISO 8601 |

规则：`projectId` 与 `learningUnitId` 至少一个非空；两者同时存在时，学习单元引用的模板版本、项目模板归属和用户权限必须一致。项目模板不能用于其他项目或纯学习记录。

### 3.2 内容修订 `ExperimentRevision`

| 字段 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|
| `id` | long | 自动 | 修订 ID |
| `recordId` | long | 是 | 所属记录 |
| `revisionNo` | int | 自动 | 从 1 递增，`(recordId, revisionNo)` 唯一 |
| `status` | enum | 自动 | EDITABLE / SUBMITTED / CHANGES_REQUESTED / APPROVED / PUBLISHED / SUPERSEDED |
| `contentJson` | object | 是 | 必须通过固定模板版本的数据校验 |
| `purpose` | string | 否 | 目的，最多 1000 字符 |
| `environment` | string | 否 | 环境，最多 1000 字符 |
| `steps` | string | 否 | 步骤，最多 10000 字符 |
| `result` | string | 否 | 结果，最多 5000 字符 |
| `anomaly` | string | 否 | 异常，最多 5000 字符 |
| `conclusion` | string | 否 | 结论，最多 5000 字符 |
| `hardwareVersionRef` | string | 否 | W43 先保存受控文本/编号，正式硬件外键第 13 周补充 |
| `deviceInstanceRef` | string | 否 | W43 先保存受控文本/编号，正式设备外键第 16 周补充 |
| `firmwareVersion` | string | 否 | 最多 100 字符 |
| `bomVersionRef` | string | 否 | 最多 100 字符 |
| `collectionSnapshotId` | long | 否 | 最多 1 个完成批次快照 |
| `changeSummary` | string | 条件 | revisionNo > 1 时必填，最多 500 字符 |
| `createdBy/createTime` | long/datetime | 自动 | 创建审计 |
| `submittedBy/submittedAt` | long/datetime | 否 | 提交审计 |
| `approvedBy/approvedAt` | long/datetime | 否 | 批准审计 |
| `publishedBy/publishedAt` | long/datetime | 否 | 发布审计 |
| `version` | int | 自动 | 仅 EDITABLE 修订更新使用的乐观锁 |

附件字段本周仅保留 `attachmentRefs: []` 响应占位，不新增通用附件上传；采集 CSV 原件由采集域保存。

### 3.3 导师反馈 `MentorFeedback`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | long | 自动 | 反馈 ID |
| `recordId/revisionId` | long | 是 | 必须固定指向被评审修订 |
| `type` | enum | 是 | COMMENT / CHANGE_REQUEST / APPROVAL |
| `content` | string | 条件 | COMMENT/CHANGE_REQUEST 必填，1-2000 字符；APPROVAL 可选 |
| `visibility` | enum | 是 | AUTHOR_AND_REVIEWERS / PROJECT_MEMBERS |
| `reviewerId/reviewerName` | long/string | 自动 | 操作人快照 |
| `createTime` | datetime | 自动 | 创建时间 |
| `requestId` | string | 自动 | 请求追踪/审计 ID |

反馈不可编辑、不可删除；纠正意见必须新增一条 COMMENT。`PROJECT_MEMBERS` 仅允许项目实验，且只有已加入项目的成员可见；默认 `AUTHOR_AND_REVIEWERS`。

评审人不能与记录作者相同；即使作者同时具有 TEACHER、OWNER 或 MAINTAINER 身份，也不能自我要求修改或自我批准。SYSTEM_ADMIN 的全局覆盖同样不绕过该约束。

### 3.4 采集计划 `CollectionPlan`

W43 只开放 FILE/MANUAL 所需字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id/code` | long/string | 自动 | code 格式 `CP-YYYYMM-XXXX` |
| `projectId` | long | 是 | 项目归属 |
| `name` | string | 是 | 2-120 字符 |
| `purpose` | string | 是 | 1-1000 字符 |
| `ownerId` | long | 是 | 负责人，必须是项目成员 |
| `sourceType` | enum | 是 | FILE / MANUAL |
| `metricDefinitions` | array | 是 | 1-100 个；code、name、valueType、unit、required |
| `linkedExperimentRecordId` | long | 否 | 预关联实验记录，只作上下文，不等于冻结引用 |
| `retentionNote` | string | 否 | 最多 500 字符；W43 不实现自动清理 |
| `status` | enum | 自动 | DRAFT / READY / ARCHIVED / CANCELED；实时态留到 W16 |
| `version` | int | 自动 | 乐观锁 |
| 审计字段 | — | 自动 | createdBy/createTime/updateTime |

W43 状态转换：`DRAFT → READY → ARCHIVED`，以及 `DRAFT/READY → CANCELED`。READY 后核心指标定义不可直接修改；如需变化，创建新计划。`RUNNING/PAUSED/COMPLETED/FAILED` 仍属于第 16-17 周实时采集契约，本周不伪造。

### 3.5 采集批次 `CollectionBatch`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id/batchCode` | long/string | 自动 | `CB-YYYYMMDD-XXXXXX` |
| `planId/projectId` | long | 是 | 固定归属 |
| `sourceType` | enum | 是 | 必须等于计划 FILE/MANUAL |
| `status` | enum | 自动 | CREATED / PROCESSING / REVIEW_REQUIRED / COMPLETED / FAILED / CANCELED / ARCHIVED |
| `operatorId` | long | 自动 | 创建/执行者 |
| `startedAt/completedAt` | datetime | 自动 | 实际处理边界 |
| `recordCount/rejectedCount` | long | 自动 | 当前有效记录数/拒绝数 |
| `observedFrom/observedTo` | datetime | 否 | 数据观测时间范围 |
| `sourceRevisionId` | long | 否 | FILE 完成时固定的已确认导入修订 ID；MANUAL 固定为 null |
| `completionReason` | string | 条件 | 完成/失败/取消说明，最多 500 字符 |
| `checksum` | string | 否 | 完成时生成的 SHA-256 汇总 |
| `version` | int | 自动 | 乐观锁 |

批次转换：

```text
FILE:   CREATED → PROCESSING → REVIEW_REQUIRED → PROCESSING → COMPLETED
MANUAL: CREATED → REVIEW_REQUIRED → COMPLETED
任一未完成态 → FAILED / CANCELED
COMPLETED → ARCHIVED
```

`COMPLETED` 后原件、映射、人工观测、计数、时间范围和 checksum 均不可修改；归档只改变可见状态，不删除证据。只有 `READY` 计划可以创建批次；批次 `sourceType` 由计划派生，请求若保留该字段则必须与计划完全一致，否则返回 400 `INVALID_PARAMETER`。

### 3.5.1 文件导入 `DataImport` 与映射修订 `DataImportRevision`

`DataImport` 表示同一批次中的一份不可变原件，至少包含 `id/batchId/originalFile/status/currentRevisionId/version/createTime/updateTime`。聚合状态为：

- `UPLOADED`：原件已保存，尚未形成有效映射。
- `MAPPING_REQUIRED`：当前映射修订待确认，或最近一次处理失败后需要重新映射。
- `CONFIRMED`：当前映射修订已确认并成为批次有效数据来源。
- `FAILED`：原件解析或处理失败，未形成可确认结果。

每次保存一份与当前映射摘要不同的映射都追加 `DataImportRevision`，至少包含 `id/dataImportId/importRevisionNo/mappingJson/mappingDigest/status/recordCount/rejectedCount/previousRevisionId/createdBy/createTime/confirmedBy/confirmedAt`。修订状态为 `MAPPING_REQUIRED / CONFIRMED / SUPERSEDED / FAILED`。

- 保存新映射后，`DataImport.currentRevisionId` 指向新修订，聚合状态变为 `MAPPING_REQUIRED`；旧 `CONFIRMED` 修订暂时仍是有效回退证据。
- 新修订确认成功时，事务内将它置为 `CONFIRMED`，将上一份 `CONFIRMED` 修订置为 `SUPERSEDED`，替换该导入在批次最终集合中的记录；旧记录与修订继续可审计，但不计入最终批次记录数。
- 批次 `COMPLETED` 后禁止新增、确认或切换导入修订。
- `GET /api/data-imports/{id}` 至少返回聚合字段、`currentRevision` 和按 `importRevisionNo DESC` 排列的 `revisions` 摘要；修订摘要包含 `id/importRevisionNo/status/mappingDigest/recordCount/rejectedCount/createTime/confirmedAt`。

### 3.6 统一采集记录与快照

`CollectionRecord` 字段：`id/batchId/sourceType/metricCode/valueType/value/unit/observedAt/recordedBy/method/instrument/rawRowNo/qualityStatus/parserVersion/createTime`。

- valueType：DECIMAL / INTEGER / BOOLEAN / STRING。
- W43 qualityStatus 只支持 `GOOD` / `REJECTED`；只报告 PARSE_ERROR、MISSING_VALUE、UNIT_MISMATCH、INVALID_TIME，不实现完整质量评分。
- FILE 的 method 固定 `FILE_IMPORT`；MANUAL 必须提供 method、instrument、unit、observedAt。

`CollectionSnapshot` 字段：`id/snapshotCode/batchId/snapshotType(FULL_BATCH)/sourceRevisionId/recordCount/rejectedCount/observedFrom/observedTo/batchChecksum/mappingDigest/createdBy/createTime`。快照不可更新、不可删除。

- FILE 快照的 `sourceRevisionId` 是完成批次时生效的 `DataImportRevision.id`，`mappingDigest` 是该修订映射摘要。
- MANUAL 快照的 `sourceRevisionId` 和 `mappingDigest` 均为 null。完成 MANUAL 批次时，服务端在内部冻结每条人工观测的当前有效修订 ID 集合，并据此计算 `batchChecksum`；通过 `batchId + batchChecksum` 复现，不虚构“人工集合修订”实体。

## 4. 状态与行为语义

### 4.1 实验记录状态机

```text
DRAFT ──submit──> IN_REVIEW
IN_REVIEW ──comment──> IN_REVIEW
IN_REVIEW ──request changes──> CHANGES_REQUESTED
CHANGES_REQUESTED ──resubmit new revision──> IN_REVIEW
IN_REVIEW ──approve──> APPROVED
APPROVED ──publish──> PUBLISHED
PUBLISHED ──create revision──> REVISING
REVISING ──submit──> IN_REVIEW
```

- 非法转换返回 409 `EXPERIMENT_INVALID_TRANSITION`。
- 提交必须通过模板校验；关联快照若存在必须有效；`version` 必须匹配。
- `COMMENT` 不改变状态；`CHANGE_REQUEST`、`APPROVAL` 是状态动作而非通用更新。
- 发布只能发布最新 APPROVED 修订；旧 `publishedRevisionId` 在新修订发布前保持有效。
- 新修订发布后，旧发布修订改为 SUPERSEDED，但内容和审计字段不变且仍可查询。

### 4.2 保存、提交和发布

- 保存草稿：只更新 EDITABLE 修订，使用 `revisionVersion` 乐观锁；成功后加 1。
- 提交：请求携带 `recordVersion`、`revisionVersion` 和 `Idempotency-Key`；单事务校验并冻结修订。
- 要求修改：单事务写反馈、冻结原修订、克隆新修订并更新记录指针。
- 批准：单事务写 APPROVAL 反馈和批准审计。
- 发布：单事务更新发布指针和发布审计；不重写模板版本、快照或内容 JSON。

## 5. FILE 导入语义

### 5.1 流程

```text
上传原件
→ 计算 SHA-256 并保存不可变原件
→ 解析前 50 行预览
→ 用户映射列、单位和值类型、观测时间
→ 确认导入
→ 写 CollectionRecord 与轻量错误摘要
→ 用户确认完成批次
→ 生成 FULL_BATCH 快照
```

确认前不写正式 `CollectionRecord`。预览或确认失败保留原件和失败审计，不把部分解析结果伪装为完成批次。

### 5.2 映射字段

```json
{
  "version": 1,
  "importVersion": 1,
  "mapping": {
    "delimiter": ",",
    "encoding": "UTF-8",
    "headerRow": 1,
    "timeMapping": {
      "sourceColumn": "observed_at",
      "format": "yyyy-MM-dd HH:mm:ss.SSS",
      "timezone": "Asia/Shanghai"
    },
    "columns": [
      {
        "sourceColumn": "voltage",
        "metricCode": "voltage",
        "valueType": "DECIMAL",
        "sourceUnit": "V",
        "targetUnit": "V"
      }
    ],
    "ignoredColumns": ["note"]
  }
}
```

- 目标 metricCode 必须存在于计划快照。
- 未知单位不自动推断或换算；sourceUnit 与 targetUnit 不一致且无本周白名单转换时返回 400。
- 时间列必须映射；本周不使用上传时间代替缺失观测时间。
- CSV 公式样式文本按普通字符串处理，不执行公式。

### 5.3 重复语义

- `Idempotency-Key` 相同且请求体相同：返回第一次结果并标记 `idempotentReplay=true`。
- 同批次相同文件 SHA-256：返回已有 import；不新增记录。
- 同一文件的新映射只能在批次完成前创建新导入修订；确认新修订时，旧修订记录保持可审计但不进入最终批次集合。
- 批次完成后再次上传或确认返回 409 `BATCH_IMMUTABLE`。

## 6. MANUAL 人工记录语义

创建人工观测必须包含：

```json
{
  "metricCode": "voltage",
  "valueType": "DECIMAL",
  "value": 5.12,
  "unit": "V",
  "observedAt": "2026-10-22T14:30:00+08:00",
  "method": "万用表直流档测量",
  "instrument": "UNI-T UT61E+ / LAB-A-017",
  "note": "室温 24℃"
}
```

- recordedBy 和 recordedAt 由服务端写入。
- 更正使用 PUT，但内部新增修订；必须传 `version` 和 `changeReason`。
- 修订历史包含旧值、新值、原因、操作者和时间；批次详情默认返回最新有效修订。
- 批次进入 COMPLETED 后任何人工记录新增/修改均返回 409 `BATCH_IMMUTABLE`。

## 7. 权限与可见范围

### 7.1 权限矩阵

| 操作 | SYSTEM_ADMIN | TEACHER | OWNER | MAINTAINER | MEMBER | OBSERVER/非成员 |
|---|---|---|---|---|---|---|
| 创建学习实验记录 | 可 | 可 | 按全局身份 | 按全局身份 | 自己 | 不可 |
| 创建项目实验记录 | 可 | 仅作为项目成员 | 可 | 可 | 可（本项目） | 不可 |
| 编辑实验草稿 | 仅自己创建 | 仅自己创建 | 仅自己创建 | 仅自己创建 | 仅自己创建 | 不可 |
| 查看未发布实验 | 可 | 可 | 本项目 | 本项目 | 自己；项目共享反馈范围内 | 不可 |
| 查看已发布实验 | 可 | 可 | 本项目 | 本项目 | 本项目或自己的学习记录 | OBSERVER 仅本项目只读 |
| 提交/重新提交 | 仅自己创建 | 仅自己创建 | 仅自己创建 | 仅自己创建 | 仅自己创建 | 不可 |
| 评论/要求修改/批准 | 可 | 可 | 本项目 | 本项目 | 不可 | 不可 |
| 发布实验 | 可 | 可 | 本项目 | 本项目 | 不可（除非兼具前述角色） | 不可 |
| 管理采集计划 | 可 | 仅作为项目 OWNER/MAINTAINER | 本项目 | 本项目 | 不可 | 不可 |
| 创建/执行批次 | 可 | 仅作为项目成员且符合项目角色 | 本项目 | 本项目 | 本项目且被指定/计划负责人 | 不可 |
| 查看批次/快照 | 可 | 可 | 本项目 | 本项目 | 本项目 | OBSERVER 本项目只读 |
| 转项目草案 | 可 | 可 | 记录作者或监管 | 记录作者或监管 | 仅记录作者 | 不可 |

实验内容的编辑、提交和重新提交始终属于记录作者；评审者只能通过不可变反馈推动修改，不能代改作者内容。STOCK_KEEPER 没有采集特权；若同时是项目成员，按项目角色或普通成员规则执行。SYSTEM_ADMIN 是审计性全局覆盖，但也不能编辑或提交他人实验内容。TEACHER 可查看并评审全部实验；未加入项目时沿用项目契约的全局只读能力，不能管理该项目的采集计划或批次。所有全局覆盖评审操作必须记录原因和审计。

### 7.2 隐藏语义

- 无权知道资源存在性时返回 404 `*_NOT_FOUND`。
- 已知项目但操作权限不足时返回 403 `*_OPERATION_DENIED`。
- GUEST 和无 Token 请求沿用全局 401/403 契约。

## 8. API

### 8.1 实验记录与修订

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/experiment-records` | 创建记录和 revision 1 |
| GET | `/api/experiment-records` | 分页查询可见记录 |
| GET | `/api/experiment-records/{id}` | 详情，默认含当前和发布修订摘要 |
| PUT | `/api/experiment-records/{id}/revisions/{revisionId}` | 保存 EDITABLE 修订 |
| GET | `/api/experiment-records/{id}/revisions` | 修订历史分页 |
| GET | `/api/experiment-records/{id}/revisions/{revisionId}` | 修订详情 |
| POST | `/api/experiment-records/{id}/submit` | 提交当前修订 |
| POST | `/api/experiment-records/{id}/feedback` | COMMENT / CHANGE_REQUEST / APPROVAL |
| GET | `/api/experiment-records/{id}/feedback` | 反馈分页 |
| POST | `/api/experiment-records/{id}/publish` | 发布已批准修订 |
| POST | `/api/experiment-records/{id}/revisions` | 从发布版创建新修订 |
| PUT | `/api/experiment-records/{id}/collection-snapshot` | EDITABLE 时绑定/解除快照 |
| POST | `/api/experiment-records/{id}/project-draft` | 转现有 PREPARING 项目 |

### 8.2 采集计划、批次与快照

| 方法 | 路径 | 说明 |
|---|---|---|
| POST/GET | `/api/projects/{projectId}/collection-plans` | 创建/分页查询计划 |
| GET/PUT | `/api/collection-plans/{id}` | 查询/编辑 DRAFT 计划 |
| POST | `/api/collection-plans/{id}/ready` | 校验后置 READY |
| POST | `/api/collection-plans/{id}/archive` | 归档计划 |
| POST | `/api/collection-plans/{id}/cancel` | 取消未归档计划 |
| POST/GET | `/api/collection-plans/{id}/batches` | 创建/分页查询批次 |
| GET | `/api/collection-batches/{id}` | 批次详情 |
| GET | `/api/collection-batches/{id}/records` | cursor 分页查询记录 |
| POST | `/api/collection-batches/{id}/complete` | 完成并冻结批次 |
| POST | `/api/collection-batches/{id}/cancel` | 取消未完成批次 |
| POST | `/api/collection-batches/{id}/archive` | 归档完成批次 |
| POST | `/api/collection-batches/{id}/snapshots` | 为完成批次创建/幂等返回快照 |

### 8.3 文件与人工数据

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/collection-batches/{id}/file-imports` | multipart 上传原件 |
| GET | `/api/data-imports/{id}/preview` | 返回表头、最多 50 行和解析警告 |
| PUT | `/api/data-imports/{id}/mapping` | 保存映射并增加 importVersion |
| POST | `/api/data-imports/{id}/confirm` | 确认导入 |
| GET | `/api/data-imports/{id}` | 查询导入状态与摘要 |
| POST | `/api/collection-batches/{id}/manual-records` | 新增人工观测 |
| PUT | `/api/manual-records/{id}` | 追加更正修订 |
| GET | `/api/manual-records/{id}/revisions` | 查看修改审计 |

### 8.4 状态动作请求与响应约定

状态动作统一使用 JSON 请求体，成功响应均为 `{code:200,msg,data}`。关键请求如下：

```json
// 保存修订 PUT /api/experiment-records/{id}/revisions/{revisionId}
{
  "recordVersion": 1,
  "revisionVersion": 1,
  "contentJson": {"voltage": 5.12, "isPass": true},
  "purpose": "验证供电稳定性",
  "environment": "室温 24℃",
  "steps": "连接负载并记录读数",
  "result": "波动小于 1%",
  "anomaly": null,
  "conclusion": "通过",
  "changeSummary": null
}
```

保存成功的 `data` 至少返回 `recordVersion`、`revisionId`、`revisionVersion`、`status`。

```json
// 提交 POST /api/experiment-records/{id}/submit
{"recordVersion":2,"revisionId":901,"revisionVersion":2}

// 反馈 POST /api/experiment-records/{id}/feedback
{"recordVersion":3,"revisionId":901,"type":"CHANGE_REQUEST","content":"补充第二次测量","visibility":"AUTHOR_AND_REVIEWERS"}

// 批准使用同一反馈接口
{"recordVersion":5,"revisionId":902,"type":"APPROVAL","content":"复审通过","visibility":"AUTHOR_AND_REVIEWERS"}

// 发布 POST /api/experiment-records/{id}/publish
{"recordVersion":6,"revisionId":902}

// 绑定快照 PUT /api/experiment-records/{id}/collection-snapshot
{"recordVersion":2,"revisionId":901,"revisionVersion":2,"snapshotId":501}
```

- 提交、批准、发布成功的 `data` 至少返回 `recordId/recordVersion/revisionId/revisionStatus/status`。
- CHANGE_REQUEST 额外返回 `feedbackId` 和 `newRevision{id,revisionNo,status,version}`。
- 绑定成功额外返回 `collectionSnapshotId` 和新的 `revisionVersion`。

采集关键请求：

```json
// 创建计划 POST /api/projects/{projectId}/collection-plans
{
  "name":"5V 输出 CSV 采集",
  "purpose":"记录负载变化下的输出",
  "ownerId":2,
  "sourceType":"FILE",
  "metricDefinitions":[{"code":"voltage","name":"电压","valueType":"DECIMAL","unit":"V","required":true}],
  "linkedExperimentRecordId":301,
  "retentionNote":"随实验长期保留"
}

// 计划置 READY POST /api/collection-plans/{id}/ready
{"version":1}

// 创建批次 POST /api/collection-plans/{id}/batches
{"planVersion":2,"sourceType":"FILE"}

// 确认导入 POST /api/data-imports/{id}/confirm
{"version":2,"importVersion":1,"batchVersion":2}

// 完成批次 POST /api/collection-batches/{id}/complete
{"version":3,"completionReason":"CSV 导入与映射已人工确认"}

// 生成快照 POST /api/collection-batches/{id}/snapshots
{"batchVersion":4,"snapshotType":"FULL_BATCH"}
```

- 计划成功响应至少返回 `id/status/version`。
- 批次成功响应至少返回 `id/batchCode/status/sourceType/version`。
- 文件上传成功返回 `id/status/version/importVersion/batchVersion/originalFile{fileName,size,sha256}`；首次上传的 `importVersion` 为 0。
- 映射保存成功返回新的 `version/importVersion/status=MAPPING_REQUIRED/batchVersion/currentRevision{id,importRevisionNo,status,mappingDigest}`；确认成功返回 `id/status=CONFIRMED/version/importVersion/recordCount/rejectedCount/batchVersion/currentRevision`。
- 完成批次返回 `id/status/version/recordCount/rejectedCount/checksum`；快照返回 §3.6 的完整快照字段。
- 人工记录创建返回 `id/version/revisionNo/batchVersion`；更正返回相同 id、新 `version/revisionNo/batchVersion` 和 `previousRevisionId`。

### 8.5 分页与并发通则

- 普通列表使用 `page`（默认 1）和 `pageSize`（默认 20，最大 100），响应统一为 `total/page/pageSize/list`。
- 采集记录使用 `cursor` 和 `limit`（默认 100，最大 500），按 `observedAt ASC, id ASC` 稳定排序，返回 `nextCursor/hasMore/list`。
- 除创建类 POST 外，所有更新和状态动作携带当前 `version`；缺失返回 400，过期返回 409 `VERSION_CONFLICT`。
- 创建、提交、反馈、发布、确认导入、完成批次、生成快照和项目转换必须带 `Idempotency-Key`。
- 同 key + 同操作人 + 同端点 + 同请求摘要返回原结果；同 key 不同摘要返回 409 `IDEMPOTENCY_KEY_REUSED`。
- 精确重放必须先命中已提交的幂等结果，再检查当前 version/状态；因此第一次成功后的相同请求仍返回原成功结果，不因资源随后变化而误报版本冲突。

## 9. 关键请求与响应样例

### 9.1 正常：创建并保存实验

```json
POST /api/experiment-records
Idempotency-Key: 01K...

{
  "title": "直流电压稳定性实验",
  "summary": "验证 5V 输出在负载变化下的稳定性",
  "projectId": 10,
  "learningUnitId": 21,
  "templateVersionId": 102,
  "contentJson": {"voltage": 5.12, "isPass": true},
  "purpose": "验证供电稳定性",
  "steps": "连接负载并记录读数",
  "result": "波动小于 1%",
  "conclusion": "通过"
}
```

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 301,
    "recordCode": "ER-202610-000301",
    "status": "DRAFT",
    "templateVersionId": 102,
    "currentRevision": {"id": 901, "revisionNo": 1, "status": "EDITABLE", "version": 1},
    "publishedRevisionId": null,
    "version": 1
  }
}
```

### 9.2 空：没有可见记录

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {"total": 0, "page": 1, "pageSize": 20, "list": []}
}
```

### 9.3 失败：提交内容不符合模板

```json
{
  "code": 400,
  "msg": "实验内容不符合模板版本",
  "reason": "EXPERIMENT_CONTENT_INVALID",
  "data": {"errors": [{"path": "voltage", "message": "电压必须在 0 到 24 之间"}]}
}
```

### 9.4 失败：未完成批次生成快照

```json
{
  "code": 409,
  "msg": "采集批次尚未完成，不能生成快照",
  "reason": "BATCH_NOT_COMPLETED",
  "data": null
}
```

### 9.5 越权：普通成员批准自己的记录

```json
{
  "code": 403,
  "msg": "无导师评审权限",
  "reason": "FEEDBACK_OPERATION_DENIED",
  "data": null
}
```

### 9.6 冲突：过期版本保存

```json
{
  "code": 409,
  "msg": "数据已被更新，请刷新后重试",
  "reason": "VERSION_CONFLICT",
  "data": {"currentVersion": 4}
}
```

### 9.7 正常：轻量转项目草案

```json
POST /api/experiment-records/301/project-draft
Idempotency-Key: 01K...

{
  "recordVersion": 8,
  "code": "EXP-POWER-01",
  "name": "5V 稳压模块改进",
  "summary": "来源于 ER-202610-000301 的已发布结论",
  "category": "hardware_project"
}
```

成功返回现有项目契约字段，`status=PREPARING`、`myRole=OWNER`，并额外返回：

```json
{
  "sourceExperimentRecordId": 301,
  "sourcePublishedRevisionId": 904
}
```

## 10. 错误语义

### 10.1 400

| reason | 说明 |
|---|---|
| `INVALID_PARAMETER` | 缺字段、格式、长度、分页或版本参数非法 |
| `EXPERIMENT_CONTENT_INVALID` | contentJson 不符合固定模板版本 |
| `FILE_TYPE_UNSUPPORTED` | 不是允许的 CSV |
| `FILE_TOO_LARGE` | 超过 20 MiB/行列限制 |
| `FILE_ENCODING_UNSUPPORTED` | 非 UTF-8/UTF-8 BOM |
| `IMPORT_MAPPING_INVALID` | 列、单位、值类型或时间映射非法 |
| `MANUAL_RECORD_INVALID` | 方法、仪器、单位、观测时间或值非法 |

### 10.2 403

| reason | 说明 |
|---|---|
| `EXPERIMENT_OPERATION_DENIED` | 无记录写权限 |
| `FEEDBACK_OPERATION_DENIED` | 无评审权限 |
| `COLLECTION_OPERATION_DENIED` | 无计划/批次执行权限 |
| `PROJECT_CONVERSION_DENIED` | 无转项目权限 |

### 10.3 404

| reason | 说明 |
|---|---|
| `EXPERIMENT_RECORD_NOT_FOUND` | 记录不存在或不可见 |
| `EXPERIMENT_REVISION_NOT_FOUND` | 修订不存在或不可见 |
| `COLLECTION_PLAN_NOT_FOUND` | 计划不存在或不可见 |
| `COLLECTION_BATCH_NOT_FOUND` | 批次不存在或不可见 |
| `COLLECTION_SNAPSHOT_NOT_FOUND` | 快照不存在或不可见 |
| `DATA_IMPORT_NOT_FOUND` | 导入不存在或不可见 |
| `MANUAL_RECORD_NOT_FOUND` | 人工观测不存在或不可见 |

### 10.4 409

| reason | 说明 |
|---|---|
| `VERSION_CONFLICT` | 乐观锁版本过期 |
| `IDEMPOTENCY_KEY_REUSED` | 相同幂等键用于不同请求摘要 |
| `EXPERIMENT_INVALID_TRANSITION` | 非法记录/修订状态转换 |
| `EXPERIMENT_REVISION_IMMUTABLE` | 修改非 EDITABLE 修订 |
| `BATCH_INVALID_TRANSITION` | 非法批次状态转换 |
| `COLLECTION_PLAN_NOT_READY` | 非 READY 计划不能创建批次 |
| `BATCH_IMMUTABLE` | 修改完成/归档批次 |
| `BATCH_NOT_COMPLETED` | 未完成批次不能生成快照 |
| `SNAPSHOT_ALREADY_BOUND` | 当前修订已有快照且未显式替换 |
| `IMPORT_ALREADY_CONFIRMED` | 当前导入修订已确认；新映射应先追加修订 |
| `PROJECT_ALREADY_CONVERTED` | 记录已经转为项目 |
| `TEMPLATE_VERSION_MISMATCH` | 学习单元/项目与模板版本不一致 |

### 10.5 422 与 5xx

- W43 不使用 422；业务校验失败统一 400 或状态冲突 409。
- 原件落盘、解析或数据库事务失败返回 500 `FILE_STORAGE_FAILED` / `IMPORT_PROCESSING_FAILED`，批次不得进入 COMPLETED。
- 本契约 API 的错误响应固定沿用 `{code,msg,reason,data}`，`traceId` 放在 `data.traceId`；冲突附加字段（如 `currentVersion`）也放在 `data`。不得返回服务器绝对路径、SQL、堆栈或原始凭据。

## 11. 页面信息层级与完整状态

### 11.1 页面与路由

- `/experiments`：我的/项目实验记录列表，筛选状态、项目、模板、作者。
- `/experiments/new`：选择学习单元/项目和已发布模板版本，创建记录。
- `/experiments/{id}`：发布版优先的详情、修订时间线、快照摘要、反馈时间线、转项目入口。
- `/experiments/{id}/edit/{revisionId}`：按固定 Schema 渲染字段并保存修订。
- `/projects/{projectId}/collection`：计划和最近批次。
- `/collection/plans/{id}`：计划字段、批次列表和创建入口。
- `/collection/batches/{id}`：来源、状态、原件/人工观测、预览、记录摘要、完成与快照。
- `/data-imports/{id}`：上传结果、映射预览、确认、进度和失败明细。

### 11.2 信息层级

1. 首屏：标题、状态、固定模板版本、作者/项目/学习单元、主要动作。
2. 主内容：当前修订结构化字段、目的/步骤/结果/异常/结论。
3. 证据：批次快照、原件摘要、记录数、时间范围、checksum。
4. 协作：导师反馈与修订时间线。
5. 次要元数据：审计、历史版本、来源项目转换关系。

### 11.3 UI 状态

| 状态 | 必须表现 |
|---|---|
| loading | 骨架屏；提交/确认时按钮局部 loading，防重复点击 |
| empty | 无记录、无计划、无批次、无反馈、CSV 无可映射列分别给下一步入口 |
| error | reason 对应文案、traceId、重试；不得丢失未提交表单 |
| permission | 隐藏写操作并显示只读原因；404 隐藏资源不展示名称 |
| success | 保存显示版本号；提交/批准/发布/完成显示新状态和审计时间 |
| conflict | VERSION_CONFLICT 提示刷新对比，不自动覆盖；幂等重放不重复 toast |
| immutable | 发布修订、完成批次和快照明显只读，提供“创建新修订”而非编辑 |

三视口仍为 1440×900、1280×800、390×844。移动端 CSV 映射改为逐列步骤，不横向铺开大表；记录预览只加载当前页。

## 12. 数据与审计约束

- 新增迁移只允许创建 W43 表/索引和必要 additive 关系，不修改 V1-V10。
- 所有状态动作写 `actorId/action/fromStatus/toStatus/targetType/targetId/requestId/createTime`。
- 原文件对象键、SHA-256、映射 JSON、人工修订、快照和发布修订不得物理删除。
- 数据库条件更新必须包含 id + version + 允许的旧状态；零行更新返回稳定冲突错误。
- 创建批次、确认导入、完成批次、生成快照、提交/评审/发布和项目转换都必须在清晰事务边界内实现。
- 本周不用 Redis 作为事实源，不引入异步消息中间件；CSV 解析可用有界应用任务，但 20 MiB 限制和失败回滚必须可解释。

## 13. 兼容与后续扩展

- 模板引用沿用 `templateVersionId`；不复制 schemaJson，不改变 W42 模板 API。
- 转项目复用现有 `POST /api/projects` 语义和 `PREPARING` 状态，通过专用转换服务保证来源关系和幂等。
- 第 13/16 周以 additive 外键替换硬件/设备文本引用，不重写历史修订。
- 第 16-17 周添加 MQTT_V2/SERIAL/BLUETOOTH、持续遥测区间快照、完整质量标记和 Edge Assignment；不改变 W43 FILE/MANUAL 批次 ID 与快照不可变语义。
- 第 18 周将文件存储适配器切换到 MinIO；对象键、SHA-256、权限和下载 API 保持不变。
- W43 单修订最多 1 个 FULL_BATCH 快照；未来若需多个快照，只能 additive 新增关联表和响应数组，旧字段保持兼容窗口。

## 14. 冻结确认清单

- [x] D1 记录/内容修订状态机
- [x] D2 CHANGE_REQUEST 自动复制下一修订
- [x] D3 仅完成批次生成并绑定单个 FULL_BATCH 快照
- [x] D4 CSV 原件本地不可变保存、20 MiB/100,000 行/100 列/UTF-8 限制
- [x] D5 重复文件复用 + 映射/人工记录追加修订
- [x] D6 PUBLISHED 实验一次性转换为现有 PREPARING 项目
- [ ] 后端确认迁移、事务、幂等和错误码可实现
- [ ] 前端确认页面层级、移动端映射和全部 UI 状态可实现

## 15. 变更记录

| 日期 | 变更 | 状态 |
|---|---|---|
| 2026-10-22 | 初始 v1.0-draft：记录修订、反馈、FILE/MANUAL 批次、快照、幂等/乐观锁和项目转换推荐方案 | REVIEW |
| 2026-10-22 | 用户确认 D1-D6；冻结状态机、不可变证据、文件限制、重复/修订语义和轻量项目转换 | FROZEN |
| 2026-10-22 | 冻结校准：作者写入边界、DataImport 修订状态、MANUAL 快照来源、READY 批次前置条件和 `data.traceId` | FROZEN |
