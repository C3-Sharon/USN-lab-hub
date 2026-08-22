# USN Lab Hub 软硬件数据采集设计规格

> 状态：已讨论确认，等待团队书面审阅
> 日期：2026-08-22
> 交付约束：软硬件数据采集属于六个月完整成品，不是周期外增强项

## 1. 目标

数据采集能力让实验室能够回答：为什么采、采什么、由谁和哪台设备采、使用什么配置、何时开始结束、数据质量如何，以及这些数据支撑了哪个项目、实验、硬件版本和结论。

平台同时支持 MQTT 自动遥测、串口/蓝牙边缘采集、CSV/示波器等文件导入和人工测量记录。所有来源统一进入“采集计划 -> 采集批次 -> 采集记录/原始文件 -> 质量报告”，但保留来源差异和原始证据。

## 2. 核心决策

1. 项目创建本身不强制配置数据采集；需要实验或设备数据时再创建采集计划。
2. 持续设备遥测与有明确目的的采集批次分开。普通遥测可以长期保存，采集批次用于项目/实验追溯。
3. 实验记录引用已经完成的采集批次或冻结区间快照，不直接引用会不断变化的 latest。
4. 串口和蓝牙由实验室电脑上的 Python/C++ Edge Collector 采集，不由 Vue 浏览器承担长期连接。
5. Edge Collector 只做协议适配、时间戳、有限缓冲和规范化，不承载业务审批、告警或数据真相。
6. MQTT 与 Edge Collector 使用统一规范化记录模型；CSV、仪器文件和人工记录也映射到同一批次模型。
7. 原始数据与解析结果同时留证。解析规则更新可重放原始数据，不覆盖既有实验引用。
8. 数据重复、迟到、乱序、单位错误和越界都有明确策略，不能靠前端猜测或静默丢弃。
9. 模型和 RAG 不整体向量化原始时序数据；Agent 通过受控查询/聚合工具访问授权数据。
10. 不建设通用工业采集平台、任意协议可视化编排或完整数据湖。

## 3. 概念区分

### 3.1 持续遥测

设备不依赖某次实验持续上报的 telemetry。它用于设备 latest、history、在线状态、告警和健康。持续遥测可以在事后选取时间区间形成冻结快照。

### 3.2 采集计划

描述一次或一类采集的目的、负责人、来源、指标、频率、质量要求、时间窗口、关联项目对象和保留策略。计划可以多次执行，每次形成独立采集批次。

### 3.3 采集批次

计划的一次实际执行。批次固定记录开始/结束、设备、配置快照、采集者、原始文件、记录数量、缺失率和质量结论。完成后内容不可被静默改写。

### 3.4 实验数据快照

实验记录引用一个完成批次，或从持续遥测中选择设备和时间区间创建不可变快照。快照保存查询条件、边界、记录集合/归档引用和生成时的数据版本。

## 4. 领域模型

```text
Project
├─ Milestone / Task / Experiment
├─ HardwareVersion / DeviceInstance
└─ CollectionPlan
   ├─ CollectionSourceConfig
   ├─ MetricDefinitionSnapshot
   └─ CollectionBatch
      ├─ CollectionRecord
      ├─ RawArtifact / RawMessage
      ├─ ImportJob / EdgeAssignment
      ├─ QualityIssue
      └─ QualityReport
```

`CollectionPlan` 必须包含：项目、名称、目的、负责人、可选里程碑/任务/实验、来源类型、设备或采集端、指标快照、采样频率、上报频率、计划时间、质量规则和保留策略。

`CollectionBatch` 必须包含：批次编号、计划、状态、实际开始/结束、执行者、设备/硬件/固件快照、配置快照、记录数、原始证据、质量摘要和完成原因。

`CollectionRecord` 统一字段：

```text
recordId / messageId
projectId / planId / batchId
deviceId / hardwareVersionId / firmwareVersion
sourceType / metricCode / valueType / value / unit
observedAt / receivedAt / sequenceNo
qualityStatus / qualityFlags
rawReference / parserVersion
```

值类型至少支持 `DECIMAL`、`INTEGER`、`BOOLEAN`、`STRING`。大型二进制波形不拆成海量普通记录；原文件进入 MinIO，数据库保存通道、采样率、时间范围、摘要和可选派生数据。

## 5. 状态机

采集计划：

```text
DRAFT -> READY -> RUNNING -> PAUSED -> RUNNING
                         -> COMPLETED -> ARCHIVED
                         -> FAILED
        -> CANCELED
```

只有字段、权限、设备/来源、指标和质量规则校验通过才能从 `DRAFT` 进入 `READY`。实时计划开始时创建批次；文件和人工来源可以创建导入批次后直接进入处理。

采集批次：

```text
CREATED -> ACQUIRING -> PROCESSING -> QUALITY_CHECK
        -> COMPLETED -> ARCHIVED
        -> FAILED / CANCELED
```

批次进入 `COMPLETED` 后，原始证据和配置快照不可直接修改。修正解析规则创建新的处理修订或派生结果，并保留原结果和审计。

## 6. MQTT 硬件接口

保留现有兼容 Topic：

```text
iot/{projectCode}/{deviceCode}/telemetry
```

通用 v2 payload：

```json
{
  "schemaVersion": "2.0",
  "messageId": "01K34D9P8G6Q1P7M9S2R",
  "deviceCode": "PM-001",
  "observedAt": "2026-08-22T14:30:00.250+08:00",
  "sequenceNo": 12834,
  "collectionContext": {
    "planCode": "CP-POWER-001",
    "batchCode": "CB-20260822-001"
  },
  "metrics": [
    {"code": "voltage", "valueType": "DECIMAL", "value": 220.1, "unit": "V"},
    {"code": "current", "valueType": "DECIMAL", "value": 0.42, "unit": "A"},
    {"code": "power", "valueType": "DECIMAL", "value": 92.4, "unit": "W"}
  ]
}
```

`collectionContext` 可选。缺少时保存为持续遥测；存在时后端校验设备、计划、批次和时间窗口后关联批次。关联失败不丢原始消息，记录质量/关联问题并进入持续遥测事实。

PM-001 v1 payload 继续由兼容适配器处理，不能为了 v2 接口破坏现有真实硬件。设备编号必须与 Topic 一致；`messageId` 在设备范围内唯一；`sequenceNo` 用于检测缺口和乱序，不作为唯一幂等依据。

## 7. Edge Collector

Edge Collector 是运行在实验室 Windows/Linux 电脑或边缘终端上的受控客户端。第一批内置适配器：

```text
SERIAL
BLUETOOTH
FILE_WATCH（仅显式目录和文件类型）
```

Java 后端定义稳定边界，Python/C++ 负责设备侧实现：

```java
public interface DataAcquisitionAdapter {
    SourceType supports();
    AcquisitionSession open(AcquisitionConfig config);
    List<NormalizedRecord> poll(AcquisitionSession session);
    void close(AcquisitionSession session);
}
```

Collector 使用独立凭据注册，领取被授权的 `EdgeAssignment`，发送心跳和批量记录。它不能创建项目、批准计划、修改质量规则或直接写数据库。

边缘接口：

```text
POST /api/edge/collectors/register
GET  /api/edge/collection-assignments/next
POST /api/edge/collection-assignments/{id}/claim
POST /api/edge/collection-assignments/{id}/heartbeat
POST /api/edge/collection-assignments/{id}/records
POST /api/edge/collection-assignments/{id}/complete
POST /api/edge/collection-assignments/{id}/fail
```

真实生产注册使用管理员预生成的一次性注册码换取 Collector 凭据。记录批量接口要求请求幂等键、批次、序号范围和内容校验和。网络中断时 Collector 使用有界本地缓冲，恢复后按原 messageId/sequenceNo 重传；缓存满时停止采集或按计划策略明确丢弃并报告，不能静默无限占盘。

## 8. 项目和批次 API

```text
POST   /api/projects/{projectId}/collection-plans
GET    /api/projects/{projectId}/collection-plans
GET    /api/collection-plans/{id}
PUT    /api/collection-plans/{id}
POST   /api/collection-plans/{id}/ready
POST   /api/collection-plans/{id}/start
POST   /api/collection-plans/{id}/pause
POST   /api/collection-plans/{id}/resume
POST   /api/collection-plans/{id}/complete
POST   /api/collection-plans/{id}/cancel

GET    /api/collection-plans/{id}/batches
GET    /api/collection-batches/{id}
GET    /api/collection-batches/{id}/records
GET    /api/collection-batches/{id}/quality-report
POST   /api/collection-batches/{id}/snapshots
POST   /api/experiments/{experimentId}/collection-snapshots/{snapshotId}
```

状态改变使用 `version` 或旧状态条件更新，重复开始不得创建两个活动批次。同一计划默认只允许一个活动批次；确需并行时由计划显式配置分片/设备范围，不在前端临时绕过。

## 9. 文件导入和人工记录

文件导入：

```text
POST /api/collection-batches/{id}/file-imports
GET  /api/data-imports/{importId}/preview
PUT  /api/data-imports/{importId}/mapping
POST /api/data-imports/{importId}/confirm
GET  /api/data-imports/{importId}
```

流程为上传原文件、解析预览、人工确认列/单位/时间映射、异步导入、质量检查。确认前不写正式 CollectionRecord。相同文件哈希和目标批次重复提交返回已有任务；若映射不同，创建新的导入修订并明确显示差异。

人工记录：

```text
POST /api/collection-batches/{id}/manual-records
PUT  /api/manual-records/{recordId}
```

人工记录必须保存记录者、记录时间、实际观测时间、仪器/方法、单位和修改审计。已被发布实验引用的人工记录修改形成修订，不直接覆盖引用值。

## 10. 质量规则

质量状态：`GOOD`、`SUSPECT`、`REJECTED`。质量标记至少包括：

```text
DUPLICATE_MESSAGE
OUT_OF_ORDER
SEQUENCE_GAP
CLOCK_SKEW
MISSING_METRIC
UNIT_MISMATCH
OUT_OF_RANGE
PARSE_ERROR
UNLINKED_COLLECTION_CONTEXT
EDGE_BUFFER_OVERFLOW
```

解析失败和非法值保存原始证据并标记拒绝，不进入正常聚合；迟到/乱序但合法的数据进入历史事实并标记，不能倒退 latest。单位转换只按审核后的指标定义执行，未知单位不自动猜测。

质量报告包含期望/实际记录数、完整率、重复率、拒绝率、最大时间缺口、时钟偏差、各标记数量和人工结论。实验发布时可以要求质量门槛，不满足时明确提示但由权限规则决定是否允许带风险发布。

## 11. 数据一致性和存储

- MQTT/Edge 请求先以 `deviceId + messageId` 或批量幂等键去重。
- 原始消息、结构化记录和批次计数采用可解释事务边界；异步聚合通过 Outbox 更新。
- MySQL 保存计划、批次、记录索引、质量和审计；MinIO 保存大型原文件、波形和归档块。
- Redis 只缓存 latest、短期幂等结果和会话，不作为采集事实唯一来源。
- 遥测和采集记录使用稳定时间/ID游标分页；按压测结果决定归档和分区，不提前分库分表。
- 批次统计可以重建；批次配置、原始证据和实验引用不可只存在派生表或缓存。

## 12. 权限和安全

- 项目负责人/维护者创建计划；负责人或被授权成员执行计划；观察者只读。
- Collector 凭据绑定实验室、Collector 和允许的设备/来源，不能访问普通用户 API。
- MQTT Broker 使用设备级 Topic ACL，设备不能冒充其他 deviceCode 或 batchCode。
- 文件限制类型、大小、压缩展开和恶意内容；公式/脚本不在导入时执行。
- 人工/文件数据必须显示来源，不能伪装为设备实时测量。
- 下载、导出和 Agent 查询都在项目 ACL 后执行，审计涉及谁、何时、什么范围和用途。

## 13. 前端体验

项目详情增加“数据采集”入口：

1. 计划列表：目的、来源、负责人、关联实验/任务、状态和最近批次。
2. 计划编辑：指标、设备、频率、时间、质量规则和保留策略。
3. 运行监控：连接/心跳、速率、记录数、缺口、质量和停止原因。
4. 批次详情：配置快照、数据预览、原始文件、质量报告和实验引用。
5. 文件导入：上传、列映射预览、单位/时间确认、进度和错误行下载。
6. 人工记录：结构化表单、来源方法和修改审计。

页面必须区分“设备在线”和“本采集批次正在运行”；Collector 在线不代表设备数据有效。连接异常保留最后有效统计，显示数据陈旧时间和缓冲状态。大表使用服务端游标分页，图表按时间范围和聚合粒度请求，不一次加载全量记录。

## 14. RAG 与 Agent

原始时序数据不整体 Embedding。Agent 使用受控工具：

```text
list_collection_plans
get_collection_batch_summary
query_collection_metrics
get_quality_report
compare_collection_batches
create_collection_plan_draft
```

Agent 可以创建计划草稿和解释数据，但启动、暂停、结束采集属于有副作用操作，必须经过权限与人工确认。Agent 回答引用批次、时间范围、指标和质量状态；质量不足时不能只给确定性结论。

## 15. 三人分工和路线

| 周 | 产品 | 前端兼边缘工具 | Java 后端 | 验收 |
|---|---|---|---|---|
| 7 | 采集计划、批次、实验引用、文件/人工规则 | 计划/批次/导入页面基础 | 计划、批次、文件预览、人工记录 API | CSV/人工数据形成批次并被实验引用 |
| 16 | 通用实时采集协议、设备与计划关系 | Python/C++ Serial/BLE Collector、运行监控 | MQTT v2、Edge Assignment、通用记录摄取 | MQTT/串口/蓝牙进入同一批次模型 |
| 17 | 质量规则、失败和告警联动 | 质量报告、断线/缓冲/失败状态 | 质量检测、重放、告警和审计 | 重复、乱序、断线和坏数据可解释 |
| 25 | 性能和恢复验收脚本 | 压力生成器、长稳和恢复联调 | 吞吐、批量导入、归档、双实例和恢复 | 报告可复现且不丢已确认数据 |

产品 PR 冻结数据字典、样例、权限、状态和质量阈值；后端实现真实契约；前端/Edge Collector 最后同步最新 `dev` 做真实设备和文件联调。

## 16. 完整验收

1. 项目负责人创建关联实验、设备和指标的数据采集计划。
2. PM-001 MQTT v1 继续运行；v2 数据可选关联活动批次。
3. Serial 和 Bluetooth Collector 各完成一次真实或硬件组确认的采集。
4. Collector 断网后有界缓冲并重传，不产生重复记录。
5. CSV/示波器文件经过预览映射、确认和异步导入形成批次。
6. 人工测量保存来源、单位和修改审计。
7. 重复、乱序、缺口、时钟偏差、单位错误和越界均进入质量报告。
8. 完成批次被实验记录冻结引用，后续遥测不改变已发布实验数据。
9. 普通成员、观察者和 Collector 凭据不能越权读取或控制采集。
10. Agent 能比较两个批次并引用时间/质量，但未经确认不能启动采集。
11. 压测记录硬件、数据量、吞吐、错误率、p95/p99、资源和恢复结果。

## 17. 明确不做

- 浏览器长期直连 Web Serial/Web Bluetooth 作为正式采集主链路。
- 任意工业协议的低代码拖拽编排和通用驱动市场。
- 自研示波器厂商二进制格式解析器全集；优先支持 CSV/公开格式和必要适配器。
- 在 Edge Collector 中实现业务审批、RAG、告警真相或数据库直写。
- 自动修正未知单位、伪造缺失记录或删除不符合预期的数据。
- 将全部原始遥测向量化，或让模型直接访问数据库/Broker。
