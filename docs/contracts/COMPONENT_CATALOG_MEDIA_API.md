# 元器件目录与通用媒体 API 契约 V1

> 状态：FROZEN（D1-D9 已于 2026-10-29 全部确认，媒体请求结构于 2026-10-31 补充冻结）
> 版本：v1.0.5（第 8 周：元器件目录、外部规格快照、可选图片与通用媒体身份）
> 最后校准周：第 8 周（2026-W44）
> 影响范围：API、数据库、权限、前端字段、审计、文件存储边界
> 关联周文档：`docs/weekly/2026-W44.md`
> 前置契约：`AUTH_WORKBENCH_V1.md`、`EXPERIMENT_RECORD_ACQUISITION_API.md`
> 总体约束：`docs/superpowers/specs/2026-08-19-usn-lab-hub-platform-design.md` §6、§9、§15

---

## 1. 目标与边界

本契约建立以下最小闭环：

```text
维护分类与制造商
→ 新建元器件型号草稿
→ 手工录入规格，或登记来源明确的外部规格候选
→ 有权限者人工确认并生成不可变正式规格快照
→ 激活进入正式目录
→ 查询、筛选和编辑核心元数据
→ 可选上传图片，未上传时显示确定性默认占位
→ 停用或恢复型号，不删除历史事实
```

本周同时建立可被第 9 周发票、第 13 周硬件版本图片和后续实验附件复用的通用媒体身份与权限边界。W44 只实现本地单实例存储适配，不选择或接入最终对象存储产品。

### 1.1 明确不做

- 采购单、金额、发票流程、到货和分批入库。
- 库存数量、库存批次、领用、审批、预占和并发扣减。
- BOM、替代料自动生效、打板、装配、资产借还和硬件实例。
- 自动抓取网页、绕过登录/验证码/robots，或由系统自动接受外部规格。
- RAG、Knowledge Radar、Agent 自动建议或自动写目录。
- MinIO、AIStor、SeaweedFS、AWS S3 等最终产品选型、集群部署和历史文件迁移。
- 通用文档预览、视频、音频、OCR、缩略图服务和图片智能识别。

## 2. 九项高风险决策（已确认）

### D1 元器件字段、分类、厂商、封装、单位和状态

**推荐：分类、制造商、元器件型号分表；正式关键规格使用不可变快照。**

- 分类：`code/name/parentId/level/path/status/sortOrder/version`。
- 制造商：`code/name/website/status/version`。
- 元器件：`name/manufacturerId/mpn/packageType/baseUnit/description/status/currentSpecSnapshotId/specVersion/primaryMediaId/version`。
- 元器件状态：`DRAFT / ACTIVE / INACTIVE`。
- 基础计量单位：`PCS / METER / GRAM / MILLILITER / SET`；不接受未冻结自由单位。
- 关键规格支持 `STRING / DECIMAL / INTEGER / BOOLEAN`，每型号最多 50 项；每项包含稳定 `specCode`、显示名、标准化值、单位和排序。

理由：分类和厂商可复用，型号核心字段稳定；变化频繁的规格通过快照保留来源和历史。

### D2 唯一性、分类层级、停用/恢复和删除

**推荐：制造商 + 规范化 MPN 全局唯一；分类最多三级；所有业务对象只停用、不物理删除。**

- `normalizedMpn = NFKC(trim(mpn)).toUpperCase(Locale.ROOT)`；只折叠连续空白，不删除 `- / . _` 等可能属于型号的字符。
- 唯一约束覆盖 DRAFT、ACTIVE、INACTIVE；重复型号必须使用已有记录或恢复停用记录。
- 分类最多 3 级；父分类停用时禁止新增子分类或型号。
- 分类存在 ACTIVE 子分类或 ACTIVE/DRAFT 型号时不能停用。
- 元器件 `ACTIVE → INACTIVE → ACTIVE`；DRAFT 可激活或停用，INACTIVE 除恢复外只读。
- 分类、制造商、元器件、规格快照和媒体均不提供物理删除 API。

理由：避免型号重复、历史采购/BOM 引用断裂，并控制分类树复杂度。

### D3 权限矩阵

**推荐：SYSTEM_ADMIN 与 STOCK_KEEPER 维护目录；TEACHER 全局只读；MEMBER 只读 ACTIVE 正式目录。**

- SYSTEM_ADMIN：全部目录、规格确认和媒体操作。
- STOCK_KEEPER：与目录相关的创建、编辑、确认、停用/恢复和图片操作。
- TEACHER：查看 DRAFT/ACTIVE/INACTIVE、候选来源和历史规格快照，不写。
- MEMBER：只看 ACTIVE 型号、当前正式规格和 READY 图片；看不到未确认候选、内部审计和停用型号。
- GUEST：不可访问。

理由：目录维护与库存职责一致，同时保留老师监督和成员查阅能力。

### D4 外部规格候选的保存

**推荐：外部候选与正式规格彻底分层，原始值和标准化建议同时保留。**

- 候选状态：`PENDING / CONFIRMED / REJECTED / SUPERSEDED`。
- 来源类型：`MANUFACTURER_WEBSITE / MANUFACTURER_DATASHEET / AUTHORIZED_DISTRIBUTOR / OTHER_PUBLIC_URL / MANUAL`。
- 非 MANUAL 候选必须保存 `sourceUrl/sourceType/capturedAt/rawSpecs/normalizedProposal/sourceTitle`。
- `rawSpecs` 保留来源原文和值；`normalizedProposal` 保存本平台 `specCode/valueType/value/unit` 建议。
- W44 不根据 URL 发起服务器抓取；候选由人工粘贴或未来受控 Adapter 提交。
- 来源 URL 只作审计元数据，不成为可信指令，也不自动打开、下载或刷新。

理由：可以解释“规格从哪里来、原文是什么、平台如何标准化”，又不把外部结果直接当事实。

### D5 人工确认与手工录入

**推荐：外部候选必须经确认才能生成正式快照；来源不可用时走 MANUAL 快照。**

- 确认请求明确选择采用的规格项，并允许有权限者修正标准化值和单位。
- PENDING 候选不能出现在 ACTIVE 目录的正式规格中。
- 来源不可用时创建 `sourceType=MANUAL` 的规格快照，`sourceUrl=null`，`sourceNote` 必填并说明依据。
- 任何确认都必须由 SYSTEM_ADMIN/STOCK_KEEPER 执行；不提供自动确认策略。
- DRAFT 型号至少有一份正式快照后才允许激活。

理由：满足外部结果人工确认和离线手工录入两条路径，避免来源不可用阻塞工作。

### D6 规格快照、审计与并发

**推荐：正式规格快照追加写、不可变；组件记录只移动当前快照指针。**

- `snapshotVersion` 按 componentId 从 1 递增，`(componentId, snapshotVersion)` 唯一。
- 快照保存来源、原始值、标准化值、确认人、确认时间、变更摘要和 SHA-256 摘要。
- 新确认生成下一版本并原子更新 `currentSpecSnapshotId/specVersion/component.version`；旧快照永久可读。
- 确认请求携带 component `version`、candidate `version` 和 `Idempotency-Key`。
- 并发确认仅一个成功；过期请求返回 409 `VERSION_CONFLICT`，不得静默覆盖或重排版本。

理由：采购、BOM 和知识引用以后可以绑定具体规格版本，而不是不断变化的 latest。

### D7 图片与默认占位

**推荐：每型号最多 5 张图片、1 张主图；JPEG/PNG、5 MiB、4096×4096；替换产生新 mediaId，删除为逻辑删除。**

- 图片可选；没有 READY 图片时返回确定性 `placeholderKey`，不创建媒体记录、不调用 AI 或外部图片服务。
- 允许 `image/jpeg`、`image/png`；同时校验扩展名、MIME、魔数和可解码尺寸。
- 每文件不超过 5 MiB，宽高均为 1-4096；拒绝 SVG、GIF、脚本、伪装文件和路径穿越。
- 原始字节与去除 EXIF/定位信息后的安全展示版本均通过内部逻辑对象键保存；业务 API 只提供 mediaId 和受控元数据。
- 上传首图自动成为 PRIMARY；替换创建新 mediaId 并将旧媒体标为 DELETED；删除主图后按 sortOrder 最小的 READY 图片补为主图，无剩余图片则回到占位。
- 同一型号仅在存在相同 SHA-256 的 READY 媒体时返回已有 mediaId；仅存在 DELETED 媒体时，重新上传必须创建新的 mediaId，旧媒体记录和已提交原件继续保留。
- 不同业务对象即使字节相同也使用独立 mediaId，避免权限串联。
- W44 不物理清理 DELETED 文件；第 18 周统一冻结保留和清理策略。

理由：图片不阻塞建档，替换和删除可审计，存储实现可替换且不泄露基础设施。

### D8 写操作、幂等、乐观锁和错误

**推荐：所有 POST 命令动作使用 Idempotency-Key；更新和状态动作同时使用 version 条件更新。**

- 创建分类、制造商、型号、候选、手工快照、确认/拒绝候选、激活/停用/恢复、上传/替换图片必须带 `Idempotency-Key`。
- PUT、激活、停用、恢复、设主图、删除媒体必须带当前 `version`。
- 分类、制造商和型号的停用/恢复请求统一使用 `{"version":N,"reason":"..."}`，reason 必填且为 1-500 字符；型号激活只使用 `{"version":N}`。
- 同 key + 同操作者 + 同端点 + 同请求摘要精确重放；key 复用不同内容返回 409 `IDEMPOTENCY_KEY_REUSED`。
- 401 沿用 TOKEN_MISSING/INVALID/EXPIRED/ACCOUNT_DISABLED；403 表示已知资源但无操作权限；不可见资源统一 404；状态、重复和版本冲突使用 409。

理由：防止重复建型号、重复快照和重复媒体，同时明确并发编辑结果。

### D9 分页、筛选、搜索与排序

**推荐：页码分页，服务端白名单排序；分类筛选默认包含后代。**

- `page=1&pageSize=20`，pageSize 最大 100。
- 筛选：`categoryId/includeDescendants/status/manufacturerId/packageType/baseUnit`。
- `keyword` 在 MPN、型号名称和制造商名称中做受控模糊搜索，长度 1-80。
- 排序白名单：`updateTime/createTime/mpn/name/manufacturerName`；`sortOrder=asc|desc`。
- MEMBER 强制 `status=ACTIVE`；不能通过参数探测 DRAFT/INACTIVE 数量。

理由：满足高频目录查询，同时阻止任意 SQL 排序和越权状态探测。

## 3. 领域字段

### 3.1 元器件分类 `ComponentCategory`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `id` | long | 自动 | 主键 |
| `code` | string | 是 | 2-40 位大写字母、数字、下划线；全局唯一 |
| `name` | string | 是 | 2-80 字符；同父分类下唯一 |
| `parentId` | long | 否 | 顶级为空 |
| `level` | int | 自动 | 1-3 |
| `path` | string | 自动 | 稳定 ID 路径，仅用于查询，不接受客户端写入 |
| `status` | enum | 自动 | ACTIVE / INACTIVE |
| `sortOrder` | int | 是 | 0-9999 |
| `version` | int | 自动 | 从 1 开始 |
| 审计字段 | — | 自动 | createdBy/createTime/updatedBy/updateTime |

### 3.2 制造商 `ComponentManufacturer`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `id` | long | 自动 | 主键 |
| `code` | string | 是 | 2-40 位大写字母、数字、下划线；全局唯一 |
| `name` | string | 是 | 2-120 字符；大小写不敏感唯一 |
| `website` | string | 否 | 仅 http/https，最多 2048 字符；W44 不自动访问 |
| `status` | enum | 自动 | ACTIVE / INACTIVE |
| `version` | int | 自动 | 乐观锁 |
| 审计字段 | — | 自动 | createdBy/createTime/updatedBy/updateTime |

### 3.3 元器件型号 `Component`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `id` | long | 自动 | 主键 |
| `catalogCode` | string | 自动 | `CMP-XXXXXXXX`，全局唯一 |
| `name` | string | 是 | 2-120 字符 |
| `manufacturerId` | long | 是 | 必须引用 ACTIVE 制造商 |
| `manufacturerName` | string | 响应 | 查询投影，不冗余写入请求 |
| `mpn` | string | 是 | 原始型号，1-120 字符 |
| `normalizedMpn` | string | 响应 | 服务端计算，不接受客户端写入 |
| `categoryId` | long | 是 | 必须引用 ACTIVE 分类 |
| `categoryPath` | array | 响应 | 从顶级到当前分类的摘要 |
| `packageType` | string | 是 | 1-80 字符，如 SOT-23、0603、DIP-8 |
| `baseUnit` | enum | 是 | PCS / METER / GRAM / MILLILITER / SET |
| `description` | string | 否 | 最多 2000 字符 |
| `status` | enum | 自动 | DRAFT / ACTIVE / INACTIVE |
| `currentSpecSnapshotId` | long | 响应 | 无正式快照时为 null |
| `specVersion` | int | 响应 | 无快照为 0 |
| `primaryMediaId` | long | 响应 | 无图片为 null |
| `imageState` | enum | 响应 | READY / PLACEHOLDER |
| `placeholderKey` | string | 响应 | imageState=PLACEHOLDER 时返回 |
| `version` | int | 自动 | 乐观锁 |
| 审计字段 | — | 自动 | createdBy/createTime/updatedBy/updateTime |

### 3.4 规格项 `ComponentSpecification`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `specCode` | string | 是 | `[a-z][a-z0-9_]{1,49}`，同快照唯一 |
| `label` | string | 是 | 2-80 字符 |
| `valueType` | enum | 是 | STRING / DECIMAL / INTEGER / BOOLEAN |
| `rawValue` | string | 条件 | 来源原文，最多 500 字符 |
| `normalizedValue` | string/number/boolean | 是 | 与 valueType 匹配 |
| `unit` | string | 否 | 最多 20 字符；未知单位不得猜测 |
| `sortOrder` | int | 是 | 0-9999 |

### 3.5 外部候选 `ComponentSpecCandidate`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | long | 自动 | 候选 ID |
| `componentId` | long | 是 | 目标型号 |
| `status` | enum | 自动 | PENDING / CONFIRMED / REJECTED / SUPERSEDED |
| `sourceType` | enum | 是 | 见 D4 |
| `sourceUrl` | string | 条件 | 非 MANUAL 必填；http/https，最多 2048 |
| `sourceTitle` | string | 否 | 最多 200 字符 |
| `capturedAt` | datetime | 条件 | 非 MANUAL 必填，不得晚于当前时间 |
| `rawSpecs` | array | 是 | 1-50 项原始名称/值/单位 |
| `normalizedProposal` | array | 是 | 1-50 项标准化建议 |
| `submittedBy/submittedAt` | long/datetime | 自动 | 登记审计 |
| `decidedBy/decidedAt` | long/datetime | 否 | 确认/拒绝审计 |
| `decisionNote` | string | 否 | 最多 1000 字符 |
| `version` | int | 自动 | 乐观锁 |

### 3.6 正式规格快照 `ComponentSpecSnapshot`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | long | 自动 | 快照 ID |
| `componentId` | long | 是 | 目标型号 |
| `snapshotVersion` | int | 自动 | 从 1 递增 |
| `sourceType/sourceUrl/sourceTitle/capturedAt` | — | 条件 | 来源事实 |
| `candidateId` | long | 否 | 来自候选时固定引用 |
| `sourceNote` | string | 条件 | MANUAL 时必填，1-1000 字符 |
| `rawSpecs` | array | 是 | 确认时的原始值快照 |
| `specifications` | array | 是 | 正式标准化规格 |
| `changeSummary` | string | 是 | 1-500 字符 |
| `checksum` | string | 自动 | 规范化快照的 SHA-256 |
| `confirmedBy/confirmedAt` | long/datetime | 自动 | 确认审计 |

快照一经创建不可更新、不可删除；后续修正必须生成下一版本。

### 3.7 通用媒体 `MediaObject`

媒体响应不包含逻辑对象键或存储供应商信息：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `mediaId` | long | 自动 | 稳定媒体身份，切换存储产品时不变 |
| `ownerType` | enum | 自动 | W44 仅 COMPONENT；后续 additive 扩展 |
| `ownerId` | long | 自动 | 所属业务对象 |
| `role` | enum | 自动 | PRIMARY / GALLERY |
| `status` | enum | 自动 | READY / DELETED / FAILED |
| `originalFileName` | string | 响应 | 清洗后的文件名，仅显示用途 |
| `contentType` | string | 响应 | image/jpeg / image/png |
| `size` | long | 响应 | 原文件字节数 |
| `width/height` | int | 响应 | 解码后尺寸 |
| `sha256` | string | 响应 | 原文件校验值 |
| `contentPath` | string | 响应 | 稳定相对路径 `/api/media/{mediaId}/content` |
| `sortOrder` | int | 响应 | 0-9999 |
| `version` | int | 响应 | 乐观锁 |
| 审计字段 | — | 响应 | uploadedBy/uploadedAt/deletedBy/deletedAt |

禁止响应字段：`absolutePath`、`localPath`、`objectKey`、`bucket`、`endpoint`、`provider`、`signedUrl`、`temporaryUrl`。

## 4. 生命周期与业务规则

### 4.1 元器件状态

```text
DRAFT ──activate──> ACTIVE ──deactivate──> INACTIVE ──restore──> ACTIVE
DRAFT ──deactivate──> INACTIVE
```

- DRAFT 激活前必须有正式规格快照，分类和制造商必须 ACTIVE。
- ACTIVE 可编辑核心元数据，但变更关键规格必须新增快照。
- INACTIVE 不可编辑、不可上传图片、不可生成候选或快照，只能查看和恢复。
- 停用不影响已有采购、BOM、实验或知识引用；这些引用继续绑定原 ID/版本。

### 4.2 分类与制造商

- 分类/制造商只有 ACTIVE/INACTIVE；恢复前必须满足父级和唯一性约束。
- 停用制造商时，已有型号保留；存在 ACTIVE/DRAFT 型号时返回 409，需先迁移或停用型号。
- 分类和制造商代码创建后不可修改；名称可在 version 匹配时修改。

### 4.3 候选和快照

```text
PENDING ──confirm──> CONFIRMED
PENDING ──reject──> REJECTED
PENDING ──newer confirmed candidate──> SUPERSEDED（显式操作，不自动猜测）
```

- CONFIRMED/REJECTED/SUPERSEDED 为终态。
- 确认候选和创建正式快照处于同一事务；任何一步失败不得移动当前快照指针。
- 只有当前 PENDING 候选可确认；同一幂等请求重放返回第一次结果。

### 4.4 占位规则

无 READY 图片时返回：

```json
{
  "imageState": "PLACEHOLDER",
  "primaryMediaId": null,
  "primaryMedia": null,
  "placeholderKey": "component:SEMICONDUCTOR:SOT:U"
}
```

`placeholderKey = component:{topCategoryCode}:{packageBucket}:{firstAlphaNumeric(normalizedMpn)}`。前端按该 key 使用仓库内 CSS/SVG 图形和固定色板；相同输入始终得到相同结果。图片加载失败也回退同一占位，不改变布局尺寸。

`packageBucket` 对 packageType 执行 NFKC、trim、转大写后，取开头连续的 ASCII 字母或数字，遇到其他字符停止；没有有效开头时使用 `OTHER`。例如 `SOT-23-5 → SOT`、`DIP-8 → DIP`、`0603 → 0603`。`firstAlphaNumeric` 取 normalizedMpn 中第一个 `A-Z/0-9`，不存在时使用 `X`。

## 5. 权限矩阵

| 操作 | SYSTEM_ADMIN | STOCK_KEEPER | TEACHER | MEMBER | GUEST |
|---|---|---|---|---|---|
| 查看 ACTIVE 目录 | 可 | 可 | 可 | 可 | 不可 |
| 查看 DRAFT/INACTIVE | 可 | 可 | 可 | 不可 | 不可 |
| 查看候选与历史快照 | 可 | 可 | 可 | MEMBER 仅当前正式快照 | 不可 |
| 创建/编辑分类和制造商 | 可 | 可 | 不可 | 不可 | 不可 |
| 创建/编辑型号 | 可 | 可 | 不可 | 不可 | 不可 |
| 激活/停用/恢复型号 | 可 | 可 | 不可 | 不可 | 不可 |
| 登记外部候选 | 可 | 可 | 不可 | 不可 | 不可 |
| 确认/拒绝候选 | 可 | 可 | 不可 | 不可 | 不可 |
| 创建 MANUAL 正式快照 | 可 | 可 | 不可 | 不可 | 不可 |
| 上传/替换/删除/排序图片 | 可 | 可 | 不可 | 不可 | 不可 |
| 读取媒体内容 | 可 | 可 | 可 | ACTIVE 型号图片 | 不可 |

- MEMBER 请求 DRAFT/INACTIVE 型号、未确认候选或 DELETED 媒体统一返回 404，不暴露存在性。
- TEACHER 的全局只读不允许通过前端隐藏代替后端鉴权。
- 媒体权限继承 owner；知道 mediaId 不代表有权读取内容。

## 6. API

### 6.1 分类与制造商

| 方法 | 路径 | 说明 |
|---|---|---|
| POST/GET | `/api/component-categories` | 创建/获取分类树 |
| PUT | `/api/component-categories/{id}` | 编辑分类名称/排序 |
| POST | `/api/component-categories/{id}/deactivate` | 停用分类 |
| POST | `/api/component-categories/{id}/restore` | 恢复分类 |
| POST/GET | `/api/component-manufacturers` | 创建/分页查询制造商 |
| GET/PUT | `/api/component-manufacturers/{id}` | 详情/编辑 |
| POST | `/api/component-manufacturers/{id}/deactivate` | 停用制造商 |
| POST | `/api/component-manufacturers/{id}/restore` | 恢复制造商 |

### 6.2 元器件目录

| 方法 | 路径 | 说明 |
|---|---|---|
| POST/GET | `/api/components` | 创建草稿/分页查询 |
| GET/PUT | `/api/components/{id}` | 详情/编辑核心字段 |
| POST | `/api/components/{id}/activate` | 激活草稿 |
| POST | `/api/components/{id}/deactivate` | 停用 |
| POST | `/api/components/{id}/restore` | 恢复 |

### 6.3 规格候选与快照

| 方法 | 路径 | 说明 |
|---|---|---|
| POST/GET | `/api/components/{id}/spec-candidates` | 登记/分页查询候选 |
| GET | `/api/component-spec-candidates/{candidateId}` | 候选详情 |
| POST | `/api/component-spec-candidates/{candidateId}/confirm` | 人工确认并生成快照 |
| POST | `/api/component-spec-candidates/{candidateId}/reject` | 拒绝候选 |
| POST | `/api/components/{id}/spec-snapshots/manual` | 手工创建正式快照 |
| GET | `/api/components/{id}/spec-snapshots` | 历史快照分页 |
| GET | `/api/component-spec-snapshots/{snapshotId}` | 快照详情 |

### 6.4 通用媒体与元器件图片

| 方法 | 路径 | 说明 |
|---|---|---|
| POST/GET | `/api/components/{id}/images` | multipart 上传/图片列表 |
| POST | `/api/components/{id}/images/{mediaId}/replacement` | multipart 替换并逻辑删除旧媒体 |
| PUT | `/api/components/{id}/images/{mediaId}/primary` | 设为主图 |
| PUT | `/api/components/{id}/images/order` | 批量更新排序 |
| DELETE | `/api/components/{id}/images/{mediaId}` | 逻辑删除并重算主图/占位 |
| GET | `/api/media/{mediaId}` | 受控媒体元数据 |
| GET | `/api/media/{mediaId}/content` | 鉴权后返回安全展示图片字节 |

## 7. 请求与响应

### 7.1 创建分类、制造商和型号

```json
POST /api/component-categories
Idempotency-Key: 01K...

{"code":"SEMICONDUCTOR","name":"半导体","parentId":null,"sortOrder":10}
```

```json
POST /api/component-manufacturers
Idempotency-Key: 01K...

{"code":"TI","name":"Texas Instruments","website":"https://www.ti.com"}
```

```json
POST /api/components
Idempotency-Key: 01K...

{
  "name":"低功耗运算放大器",
  "manufacturerId":21,
  "mpn":"OPA333AIDBVR",
  "categoryId":11,
  "packageType":"SOT-23-5",
  "baseUnit":"PCS",
  "description":"零漂移精密运算放大器"
}
```

成功创建型号固定返回 DRAFT：

```json
{
  "code":200,
  "msg":"操作成功",
  "data":{
    "id":301,
    "catalogCode":"CMP-00000301",
    "name":"低功耗运算放大器",
    "manufacturerId":21,
    "manufacturerName":"Texas Instruments",
    "mpn":"OPA333AIDBVR",
    "normalizedMpn":"OPA333AIDBVR",
    "categoryId":11,
    "packageType":"SOT-23-5",
    "baseUnit":"PCS",
    "status":"DRAFT",
    "currentSpecSnapshotId":null,
    "specVersion":0,
    "primaryMediaId":null,
    "imageState":"PLACEHOLDER",
    "placeholderKey":"component:SEMICONDUCTOR:SOT:O",
    "version":1
  }
}
```

### 7.2 登记并确认外部候选

```json
POST /api/components/301/spec-candidates
Idempotency-Key: 01K...

{
  "componentVersion":1,
  "sourceType":"MANUFACTURER_DATASHEET",
  "sourceUrl":"https://www.ti.com/lit/ds/symlink/opa333.pdf",
  "sourceTitle":"OPA333 datasheet",
  "capturedAt":"2026-10-29T10:00:00+08:00",
  "rawSpecs":[
    {"name":"Supply voltage","value":"1.8 V to 5.5 V","unit":"V"},
    {"name":"Offset voltage","value":"10 uV max","unit":"uV"}
  ],
  "normalizedProposal":[
    {"specCode":"supply_voltage_range","label":"供电电压范围","valueType":"STRING","normalizedValue":"1.8-5.5","unit":"V","sortOrder":10},
    {"specCode":"offset_voltage_max","label":"最大失调电压","valueType":"DECIMAL","normalizedValue":10,"unit":"uV","sortOrder":20}
  ]
}
```

```json
POST /api/component-spec-candidates/501/confirm
Idempotency-Key: 01K...

{
  "componentVersion":2,
  "candidateVersion":1,
  "specifications":[
    {"specCode":"supply_voltage_range","label":"供电电压范围","valueType":"STRING","rawValue":"1.8 V to 5.5 V","normalizedValue":"1.8-5.5","unit":"V","sortOrder":10},
    {"specCode":"offset_voltage_max","label":"最大失调电压","valueType":"DECIMAL","rawValue":"10 uV max","normalizedValue":10,"unit":"uV","sortOrder":20}
  ],
  "changeSummary":"首次确认制造商数据手册规格"
}
```

成功响应至少返回：

```json
{
  "candidateId":501,
  "candidateStatus":"CONFIRMED",
  "snapshotId":601,
  "snapshotVersion":1,
  "componentId":301,
  "componentVersion":3,
  "specVersion":1
}
```

### 7.3 手工规格快照

```json
POST /api/components/301/spec-snapshots/manual
Idempotency-Key: 01K...

{
  "componentVersion":3,
  "sourceType":"MANUAL",
  "sourceUrl":null,
  "sourceNote":"供应商页面暂不可用，依据实验室现有料盘标签和老师确认录入",
  "rawSpecs":[{"name":"封装标签","value":"SOT-23-5","unit":null}],
  "specifications":[
    {"specCode":"package_marking","label":"封装标签","valueType":"STRING","rawValue":"SOT-23-5","normalizedValue":"SOT-23-5","unit":null,"sortOrder":30}
  ],
  "changeSummary":"补充线下确认的封装标签"
}
```

### 7.4 状态动作

```json
POST /api/components/301/activate
Idempotency-Key: 01K...

{"version":4}
```

停用/恢复请求同样为 `{"version":N,"reason":"..."}`，reason 1-500 字符并写审计；激活、停用和恢复均必须携带 `Idempotency-Key`。

分类和制造商的停用/恢复使用相同请求体与幂等规则。编辑请求固定为：分类 `name/sortOrder/version`，制造商 `name/website/version`，型号为创建字段加 `version`；分类/制造商 code 与型号 catalogCode 均不可修改。

### 7.5 图片响应

上传使用 multipart：字段 `file`、`componentVersion`、`sortOrder`，幂等键放 Header。成功返回：

```json
{
  "code":200,
  "msg":"上传成功",
  "data":{
    "componentId":301,
    "componentVersion":6,
    "imageState":"READY",
    "primaryMediaId":801,
    "media":{
      "mediaId":801,
      "ownerType":"COMPONENT",
      "ownerId":301,
      "role":"PRIMARY",
      "status":"READY",
      "originalFileName":"opa333.png",
      "contentType":"image/png",
      "size":48213,
      "width":1200,
      "height":900,
      "sha256":"...",
      "contentPath":"/api/media/801/content",
      "sortOrder":10,
      "version":1
    }
  }
}
```

替换和删除成功必须返回新的 `componentVersion/imageState/primaryMediaId/images`。响应中出现禁止的基础设施字段属于契约失败。

替换使用 multipart，字段固定为 `file`、`componentVersion`、`mediaVersion`，并携带 `Idempotency-Key`。新媒体继承旧媒体的 `role` 和 `sortOrder`；替换成功创建新 mediaId，将旧媒体逻辑删除，不允许通过替换隐式改主图或排序。

设为主图使用 JSON 请求体：

```json
{"componentVersion":6,"mediaVersion":1}
```

目标必须是该型号的 READY 图片。成功后旧主图降为 GALLERY、目标升为 PRIMARY，并返回新的 `componentVersion/imageState/primaryMediaId/images`；组件版本负责保护整组主图切换，`mediaVersion` 负责保护目标媒体。

批量排序使用完整快照式 JSON 请求体：

```json
{
  "componentVersion":6,
  "items":[
    {"mediaId":801,"mediaVersion":1,"sortOrder":10},
    {"mediaId":802,"mediaVersion":2,"sortOrder":20}
  ]
}
```

`items` 必须恰好包含该型号当前全部 READY 图片，每个 mediaId 只出现一次，`sortOrder` 为 0-9999；缺失、额外、重复或引用非 READY/非本型号媒体均返回 400 `INVALID_PARAMETER`。排序成功返回新的 `componentVersion/imageState/primaryMediaId/images`，所有实际更新的媒体 version 自增；同值按 mediaId 升序稳定展示。

删除图片使用 JSON 请求体 `{"componentVersion":N,"mediaVersion":N,"reason":"..."}`；`reason` 为 1-500 字符。删除只做逻辑删除和审计留痕，不在 W44 请求链路中物理删除原件。

## 8. 列表查询

```text
GET /api/components?page=1&pageSize=20
  &categoryId=11&includeDescendants=true
  &status=ACTIVE&manufacturerId=21
  &packageType=SOT-23-5&baseUnit=PCS
  &keyword=OPA333&sortBy=mpn&sortOrder=asc
```

响应：

```json
{
  "code":200,
  "msg":"操作成功",
  "data":{"total":0,"page":1,"pageSize":20,"list":[]}
}
```

- 默认排序：`updateTime DESC, id DESC`。
- 分类树列表稳定按 `sortOrder ASC, id ASC`。
- 制造商列表支持 status、keyword 和同一排序白名单原则。
- 非法 sortBy/sortOrder/pageSize 返回 400 `INVALID_PARAMETER`，不得拼接任意 SQL 标识符。

## 9. 幂等、乐观锁与审计

- 创建类请求在单个数据库事务中写业务事实和幂等结果；文件写入使用 staging → 校验/规范化 → 提交逻辑对象 → 数据库提交，失败时补偿本次新对象。
- 精确幂等重放先返回首次提交结果，再检查当前 version；资源后续变化不能让精确重放误报冲突。
- 条件更新包含 `id + version + allowed status`；影响行数为 0 返回 `VERSION_CONFLICT` 或更具体的状态冲突。
- 目录写操作审计：`actorId/action/targetType/targetId/fromStatus/toStatus/requestId/reason/createTime`。
- 媒体审计额外记录原文件名、MIME、大小、尺寸、SHA-256；日志和 API 均不得记录内部路径和对象存储凭据。
- 规格确认审计固定记录候选、快照版本、原始/标准化摘要、确认人和变更说明。

## 10. 错误语义

错误格式沿用 `{code,msg,reason,data}`，`traceId` 位于 `data.traceId`。

### 10.1 400

| reason | 场景 |
|---|---|
| `INVALID_PARAMETER` | 字段、分页、排序、version 或幂等键格式非法 |
| `COMPONENT_SPEC_INVALID` | 规格 code、类型、值、单位或数量非法 |
| `SOURCE_METADATA_INVALID` | 来源 URL、类型、时间或原始值不完整 |
| `MEDIA_TYPE_UNSUPPORTED` | 不是 JPEG/PNG 或内容伪装 |
| `MEDIA_TOO_LARGE` | 超过 5 MiB 或尺寸限制 |
| `MEDIA_DECODE_FAILED` | 图片无法安全解码 |

### 10.2 401/403

| HTTP | reason | 场景 |
|---:|---|---|
| 401 | `TOKEN_MISSING/TOKEN_INVALID/TOKEN_EXPIRED/ACCOUNT_DISABLED` | 沿用全局鉴权契约 |
| 403 | `COMPONENT_OPERATION_DENIED` | 无目录写权限 |
| 403 | `SPEC_CONFIRM_DENIED` | 无规格确认权限 |
| 403 | `MEDIA_OPERATION_DENIED` | 无媒体写权限 |

### 10.3 404

| reason | 场景 |
|---|---|
| `COMPONENT_CATEGORY_NOT_FOUND` | 分类不存在或不可见 |
| `COMPONENT_MANUFACTURER_NOT_FOUND` | 制造商不存在或不可见 |
| `COMPONENT_NOT_FOUND` | 型号不存在或不可见 |
| `COMPONENT_SPEC_CANDIDATE_NOT_FOUND` | 候选不存在或不可见 |
| `COMPONENT_SPEC_SNAPSHOT_NOT_FOUND` | 快照不存在或不可见 |
| `MEDIA_NOT_FOUND` | 媒体不存在、已删除或随 owner 不可见 |

### 10.4 409

| reason | 场景 |
|---|---|
| `VERSION_CONFLICT` | 乐观锁过期，data.currentVersion 返回当前版本 |
| `IDEMPOTENCY_KEY_REUSED` | 同 key 用于不同请求摘要 |
| `COMPONENT_DUPLICATE_MODEL` | manufacturerId + normalizedMpn 重复 |
| `CATEGORY_DUPLICATE_CODE` | 分类 code 重复 |
| `CATEGORY_DUPLICATE_NAME` | 同父分类下规范化名称重复 |
| `MANUFACTURER_DUPLICATE_CODE` | 制造商 code/name 重复 |
| `CATEGORY_DEPTH_EXCEEDED` | 分类超过 3 级 |
| `CATEGORY_PARENT_INACTIVE` | 父分类已停用，禁止创建或恢复子分类 |
| `CATEGORY_IN_USE` | 存在 ACTIVE 子分类或 ACTIVE/DRAFT 型号 |
| `MANUFACTURER_IN_USE` | 存在 ACTIVE/DRAFT 型号 |
| `COMPONENT_REFERENCE_INACTIVE` | 型号引用的分类或制造商未启用 |
| `COMPONENT_INVALID_TRANSITION` | 非法激活/停用/恢复 |
| `COMPONENT_SPEC_REQUIRED` | DRAFT 没有正式快照却尝试激活 |
| `SPEC_CANDIDATE_ALREADY_DECIDED` | 再次确认/拒绝终态候选 |
| `MEDIA_LIMIT_EXCEEDED` | READY 图片已达 5 张 |

### 10.5 500

- `MEDIA_STORAGE_FAILED`：staging、规范化或提交逻辑对象失败；不得留下 READY 媒体事实。
- `MEDIA_PROCESSING_FAILED`：图片安全处理失败；不得返回内部路径或库异常。

## 11. 正常、空、失败与越权样例

### 11.1 空目录

```json
{"code":200,"msg":"操作成功","data":{"total":0,"page":1,"pageSize":20,"list":[]}}
```

### 11.2 重复型号

```json
{
  "code":409,
  "msg":"同一制造商下的型号已存在",
  "reason":"COMPONENT_DUPLICATE_MODEL",
  "data":{"existingComponentId":301,"currentStatus":"INACTIVE","traceId":"..."}
}
```

`existingComponentId` 只对 SYSTEM_ADMIN/STOCK_KEEPER/TEACHER 返回；普通成员不会触发创建接口。

### 11.3 并发冲突

```json
{
  "code":409,
  "msg":"数据已被更新，请刷新后重试",
  "reason":"VERSION_CONFLICT",
  "data":{"currentVersion":7,"traceId":"..."}
}
```

### 11.4 越权写入

```json
{
  "code":403,
  "msg":"无元器件目录维护权限",
  "reason":"COMPONENT_OPERATION_DENIED",
  "data":{"traceId":"..."}
}
```

### 11.5 MEMBER 查看停用型号

```json
{
  "code":404,
  "msg":"元器件型号不存在或不可见",
  "reason":"COMPONENT_NOT_FOUND",
  "data":{"traceId":"..."}
}
```

## 12. 页面信息层级与状态

### 12.1 路由

- `/components`：ACTIVE 目录；管理者可切换 DRAFT/INACTIVE。
- `/components/new`：创建型号草稿。
- `/components/{id}`：核心信息、当前规格、来源、历史快照和图片。
- `/components/{id}/edit`：核心字段编辑。
- `/components/{id}/spec-candidates`：候选登记、对比、确认/拒绝。
- `/component-categories`：分类树管理。
- `/component-manufacturers`：制造商管理。

### 12.2 详情层级

1. 标题区：型号、制造商、状态、分类、封装和主图/占位。
2. 当前规格：只展示当前正式快照和版本。
3. 来源区：来源类型、标题、URL、获取时间、确认人和变更说明。
4. 历史区：规格版本时间线，旧版本只读。
5. 图片区：主图、图库、上传/替换/删除/排序；无图显示确定性占位。
6. 审计区：创建、更新、停用/恢复记录，仅管理角色可见。

### 12.3 完整 UI 状态

| 状态 | 要求 |
|---|---|
| loading | 列表/详情骨架；上传、确认、停用按钮局部 loading |
| empty | 无目录、筛选无结果、无候选、无历史、无图片分别提供合适入口 |
| error | reason 文案 + traceId + 重试；不得丢失未提交编辑内容 |
| permission | 只读角色不显示写动作；不可见资源显示 404 状态 |
| success | 展示新 version/specVersion/mediaId 和操作时间 |
| conflict | VERSION_CONFLICT 要求刷新比较，不自动覆盖 |
| inactive | 灰色状态和只读提示，提供恢复而不是编辑 |
| placeholder | 固定比例、不布局跳动，图片失败回退同一 placeholderKey |

三视口：1440×900、1280×800、390×844。移动端规格使用纵向定义列表，候选对比使用逐项折叠，不横向铺开宽表。

## 13. 存储边界

- W44 建立供应商无关的 `MediaStoragePort` 语义：stage、commit、open、rollbackNewObject；内部实现名不进入契约。
- 本地实现只允许开发、测试和单实例演示，根目录位于 Web 根目录之外。
- 数据库保存 mediaId、内部逻辑对象键、媒体版本、SHA-256 和受控元数据；业务 API 不返回内部逻辑对象键。
- `GET /api/media/{mediaId}/content` 在业务 ACL 后读取内容；禁止页面拼接文件路径或存储地址。
- 不保存临时签名 URL；不在数据库保存绝对路径、bucket、endpoint、provider 或凭据。
- 媒体逻辑删除属于媒体业务服务和数据库状态变更，不调用存储端口删除已提交原件；W44 不执行已提交原件的物理清理。
- 第 18 周切换正式对象存储实现时，mediaId、逻辑对象键、业务 API、owner 关系和权限语义不得改变。
- MySQL 事务成功但文件提交失败时不得产生 READY 媒体；文件已提交但事务失败时必须补偿本次新对象或记录可恢复任务，不得静默悬挂。

## 14. 与其他周的兼容

### W43

- W43 CSV 原件继续使用采集域不可变对象键，不强制追溯迁移为 mediaId。
- W44 媒体存储端口与 W43 的 staging、SHA-256、不可变对象和路径隐藏原则一致。
- 本 PR 不修改实验记录、批次、快照或 W43 状态。

### W45（第 9 周）

- 发票、订单截图和到货图片复用 mediaId/owner ACL/content 接口；不得把文件路径写入采购表。
- 本周不预先冻结发票可见范围；第 9 周在媒体 owner 权限之上增加采购业务 ACL。

### 第 13 周

- 硬件产品/版本图片复用 mediaId、PRIMARY/GALLERY、逻辑删除和占位规则。
- 硬件版本图片数量和角色权限可按第 13 周 additive 收紧，不改变媒体身份。

### 第 18 周

- 对候选对象存储产品做许可、离线部署、Java 接入、API、备份恢复和迁移实测后再冻结产品。
- 正式适配不得改变 mediaId、逻辑对象键、业务接口、权限、SHA-256 和审计语义。

## 15. FROZEN 确认清单

- [x] D1 分类、制造商、型号、单位、规格和状态字段
- [x] D2 唯一性、三级分类、停用/恢复和禁止物理删除
- [x] D3 SYSTEM_ADMIN/STOCK_KEEPER 写、TEACHER 全局只读、MEMBER ACTIVE 只读
- [x] D4 外部候选保留 URL、来源类型、获取时间、原始值和标准化建议
- [x] D5 外部候选人工确认，来源不可用时 MANUAL 快照
- [x] D6 正式快照追加写、版本审计和并发确认
- [x] D7 最多 5 图、JPEG/PNG、5 MiB、替换新 mediaId、逻辑删除、READY 去重和占位
- [x] D8 所有 POST 命令的 Idempotency-Key、version 条件更新和 401/403/404/409 语义
- [x] D9 分页、筛选、搜索和排序白名单
- [x] 后端确认媒体事务补偿、图片安全处理和唯一约束可实现
- [ ] 前端确认候选对比、占位、冲突和三视口可实现

确认记录：用户于 2026-10-29 明确回复“D1-D9 全部确认”，于 2026-10-30 确认删除后重传、事务补偿、并发锁序、权限落点、唯一约束、B2 目录冲突语义及确定性占位算法，并于 2026-10-31 确认替换、设主图和完整排序请求结构。后端与前端必须以当前 FROZEN 版本为唯一同源边界；任何字段、状态、权限、错误或媒体语义变化都需新的产品契约变更。前端实现可行性勾选保留到前端 PR，不影响产品决策冻结。

## 16. 变更记录

| 日期 | 变更 | 状态 |
|---|---|---|
| 2026-10-29 | 初始 v1.0-draft：元器件目录、规格候选/快照、通用 mediaId、图片与占位规则 | REVIEW |
| 2026-10-29 | 用户确认 D1-D9；版本升为 v1.0，作为后端与前端开工边界 | FROZEN |
| 2026-10-29 | v1.0.1：统一 POST 命令幂等规则，澄清逻辑删除与存储补偿边界，删除冲突的重复媒体错误语义 | FROZEN |
| 2026-10-30 | v1.0.2：明确仅 READY 媒体参与同型号 SHA-256 去重，DELETED 媒体重传创建新 mediaId | FROZEN |
| 2026-10-30 | v1.0.3：补充分类同级重名、停用父级、停用引用和目录状态请求体语义 | FROZEN |
| 2026-10-30 | v1.0.4：冻结 packageBucket 与 MPN 首字符的确定性占位算法及兜底值 | FROZEN |
| 2026-10-31 | v1.0.5：补充冻结替换 multipart、设主图版本体和完整快照式排序请求，明确版本自增与响应结构 | FROZEN |
