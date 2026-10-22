import request from '@/utils/request'
import { getMockEnabled } from '@/utils/mock'
import { userStore, currentRoles } from '@/store/user'
import { ROLE } from '@/utils/permission'
import { PROJECT_ROLE, findMockProject, getMockProjectMembers } from '@/api/projects'
import { idempotencyHeaders, requestDigest } from '@/utils/idempotency'
import {
  currentMockScenario,
  isMockScenario,
  mockDelay,
  mockReject,
  mockTraceId
} from '@/utils/mockScenario'

/**
 * 采集计划 / 批次 / CSV 导入 / 人工观测 / 快照 API（第 7 周 M02-W03）
 *
 * 契约来源：docs/contracts/EXPERIMENT_RECORD_ACQUISITION_API.md v1.0（FROZEN）
 *
 * 真实端点（§8.2 / §8.3）：
 *   POST/GET /api/projects/{projectId}/collection-plans      创建/分页查询计划
 *   GET/PUT  /api/collection-plans/{id}                      查询/编辑 DRAFT 计划
 *   POST     /api/collection-plans/{id}/ready                置 READY
 *   POST     /api/collection-plans/{id}/archive              归档计划
 *   POST     /api/collection-plans/{id}/cancel               取消未归档计划
 *   POST/GET /api/collection-plans/{id}/batches              创建/分页查询批次
 *   GET      /api/collection-batches/{id}                    批次详情
 *   GET      /api/collection-batches/{id}/records            cursor 分页查询记录
 *   POST     /api/collection-batches/{id}/complete           完成并冻结批次
 *   POST     /api/collection-batches/{id}/cancel             取消未完成批次
 *   POST     /api/collection-batches/{id}/archive            归档完成批次
 *   POST     /api/collection-batches/{id}/snapshots          创建/幂等返回快照
 *   POST     /api/collection-batches/{id}/file-imports       multipart 上传原件
 *   GET      /api/data-imports/{id}/preview                  表头 + 最多 50 行 + 警告
 *   PUT      /api/data-imports/{id}/mapping                  保存映射并增加 importVersion
 *   POST     /api/data-imports/{id}/confirm                  确认导入
 *   GET      /api/data-imports/{id}                          导入状态与摘要
 *   POST     /api/collection-batches/{id}/manual-records     新增人工观测
 *   PUT      /api/manual-records/{id}                        追加更正修订
 *   GET      /api/manual-records/{id}/revisions              查看修改审计
 *
 * W43 边界：仅 FILE/MANUAL；批次完成后原件、映射、人工观测、计数、时间范围和 checksum 不可修改。
 * 幂等：创建批次、确认导入、完成批次、生成快照必须携带 Idempotency-Key。
 *
 * mock 开关：VITE_USE_MOCK=true 走本地 mock（字段与契约一致），默认 false 走真实接口。
 * 前端补充字段（展示名/摘要，需后端确认）：
 *   plan.projectName/ownerName/linkedExperimentRecordCode/batchCount/latestBatch
 *   batch.planName/projectName/operatorName
 *   import.batchCode/originalFile.uploadedByName
 */

// ========== 枚举 ==========

export const COLLECTION_SOURCE_TYPE = Object.freeze({
  FILE: 'FILE',
  MANUAL: 'MANUAL'
})

export const COLLECTION_SOURCE_TYPE_META = Object.freeze({
  [COLLECTION_SOURCE_TYPE.FILE]: { label: 'CSV 文件', tagType: 'primary', desc: '上传 CSV 原件并映射列' },
  [COLLECTION_SOURCE_TYPE.MANUAL]: { label: '人工观测', tagType: 'success', desc: '逐条录入观测值' }
})

export const COLLECTION_PLAN_STATUS = Object.freeze({
  DRAFT: 'DRAFT',
  READY: 'READY',
  ARCHIVED: 'ARCHIVED',
  CANCELED: 'CANCELED'
})

export const COLLECTION_PLAN_STATUS_META = Object.freeze({
  [COLLECTION_PLAN_STATUS.DRAFT]: { label: '草稿', tagType: 'info', desc: '可编辑计划字段' },
  [COLLECTION_PLAN_STATUS.READY]: { label: '已就绪', tagType: 'success', desc: '可创建批次，核心指标锁定' },
  [COLLECTION_PLAN_STATUS.ARCHIVED]: { label: '已归档', tagType: 'info', desc: '只读，不删除证据' },
  [COLLECTION_PLAN_STATUS.CANCELED]: { label: '已取消', tagType: 'danger', desc: '不再执行采集' }
})

export const COLLECTION_BATCH_STATUS = Object.freeze({
  CREATED: 'CREATED',
  PROCESSING: 'PROCESSING',
  REVIEW_REQUIRED: 'REVIEW_REQUIRED',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
  CANCELED: 'CANCELED',
  ARCHIVED: 'ARCHIVED'
})

export const COLLECTION_BATCH_STATUS_META = Object.freeze({
  [COLLECTION_BATCH_STATUS.CREATED]: { label: '已创建', tagType: 'info', desc: '等待上传或录入' },
  [COLLECTION_BATCH_STATUS.PROCESSING]: { label: '处理中', tagType: 'warning', desc: '解析或映射进行中' },
  [COLLECTION_BATCH_STATUS.REVIEW_REQUIRED]: { label: '待确认', tagType: 'primary', desc: '需人工确认后完成' },
  [COLLECTION_BATCH_STATUS.COMPLETED]: { label: '已完成', tagType: 'success', desc: '数据冻结，可生成快照' },
  [COLLECTION_BATCH_STATUS.FAILED]: { label: '失败', tagType: 'danger', desc: '解析或校验失败' },
  [COLLECTION_BATCH_STATUS.CANCELED]: { label: '已取消', tagType: 'info', desc: '未完成批次已取消' },
  [COLLECTION_BATCH_STATUS.ARCHIVED]: { label: '已归档', tagType: 'info', desc: '只读，不删除证据' }
})

export const DATA_IMPORT_STATUS = Object.freeze({
  UPLOADED: 'UPLOADED',
  MAPPING_REQUIRED: 'MAPPING_REQUIRED',
  CONFIRMED: 'CONFIRMED',
  FAILED: 'FAILED'
})

export const DATA_IMPORT_STATUS_META = Object.freeze({
  [DATA_IMPORT_STATUS.UPLOADED]: { label: '已上传', tagType: 'info', desc: '原件已保存，待保存映射' },
  [DATA_IMPORT_STATUS.MAPPING_REQUIRED]: { label: '待确认映射', tagType: 'warning', desc: '映射已保存，待确认导入' },
  [DATA_IMPORT_STATUS.CONFIRMED]: { label: '已确认', tagType: 'success', desc: '正式记录已写入' },
  [DATA_IMPORT_STATUS.FAILED]: { label: '解析失败', tagType: 'danger', desc: '保留原件与失败审计' }
})

export const COLLECTION_VALUE_TYPE = Object.freeze({
  DECIMAL: 'DECIMAL',
  INTEGER: 'INTEGER',
  BOOLEAN: 'BOOLEAN',
  STRING: 'STRING'
})

export const COLLECTION_VALUE_TYPE_META = Object.freeze({
  [COLLECTION_VALUE_TYPE.DECIMAL]: { label: '小数' },
  [COLLECTION_VALUE_TYPE.INTEGER]: { label: '整数' },
  [COLLECTION_VALUE_TYPE.BOOLEAN]: { label: '布尔' },
  [COLLECTION_VALUE_TYPE.STRING]: { label: '文本' }
})

export const QUALITY_STATUS = Object.freeze({
  GOOD: 'GOOD',
  REJECTED: 'REJECTED'
})

export const SNAPSHOT_TYPE = Object.freeze({
  FULL_BATCH: 'FULL_BATCH'
})

export const PLAN_TRANSITIONS = Object.freeze({
  [COLLECTION_PLAN_STATUS.DRAFT]: [COLLECTION_PLAN_STATUS.READY, COLLECTION_PLAN_STATUS.CANCELED],
  [COLLECTION_PLAN_STATUS.READY]: [COLLECTION_PLAN_STATUS.ARCHIVED, COLLECTION_PLAN_STATUS.CANCELED],
  [COLLECTION_PLAN_STATUS.ARCHIVED]: [],
  [COLLECTION_PLAN_STATUS.CANCELED]: []
})

export const BATCH_TRANSITIONS = Object.freeze({
  [COLLECTION_BATCH_STATUS.CREATED]: [
    COLLECTION_BATCH_STATUS.PROCESSING,
    COLLECTION_BATCH_STATUS.REVIEW_REQUIRED,
    COLLECTION_BATCH_STATUS.FAILED,
    COLLECTION_BATCH_STATUS.CANCELED
  ],
  [COLLECTION_BATCH_STATUS.PROCESSING]: [
    COLLECTION_BATCH_STATUS.REVIEW_REQUIRED,
    COLLECTION_BATCH_STATUS.FAILED,
    COLLECTION_BATCH_STATUS.CANCELED
  ],
  [COLLECTION_BATCH_STATUS.REVIEW_REQUIRED]: [
    COLLECTION_BATCH_STATUS.COMPLETED,
    COLLECTION_BATCH_STATUS.FAILED,
    COLLECTION_BATCH_STATUS.CANCELED
  ],
  [COLLECTION_BATCH_STATUS.COMPLETED]: [COLLECTION_BATCH_STATUS.ARCHIVED],
  [COLLECTION_BATCH_STATUS.FAILED]: [COLLECTION_BATCH_STATUS.CANCELED],
  [COLLECTION_BATCH_STATUS.CANCELED]: [],
  [COLLECTION_BATCH_STATUS.ARCHIVED]: []
})

export function planStatusMeta(status) {
  return COLLECTION_PLAN_STATUS_META[status] || { label: status || '--', tagType: 'info', desc: '' }
}

export function batchStatusMeta(status) {
  return COLLECTION_BATCH_STATUS_META[status] || { label: status || '--', tagType: 'info', desc: '' }
}

export function importStatusMeta(status) {
  return DATA_IMPORT_STATUS_META[status] || { label: status || '--', tagType: 'info', desc: '' }
}

export function sourceTypeMeta(type) {
  return COLLECTION_SOURCE_TYPE_META[type] || { label: type || '--', tagType: 'info', desc: '' }
}

export function valueTypeMeta(type) {
  return COLLECTION_VALUE_TYPE_META[type] || { label: type || '--' }
}

export function canTransitionPlan(from, to) {
  return (PLAN_TRANSITIONS[from] || []).includes(to)
}

export function canTransitionBatch(from, to) {
  return (BATCH_TRANSITIONS[from] || []).includes(to)
}

/** 完成/归档批次不可再改（§3.5 / §6） */
export function isBatchImmutableStatus(status) {
  return [COLLECTION_BATCH_STATUS.COMPLETED, COLLECTION_BATCH_STATUS.ARCHIVED].includes(status)
}

/** 批次是否可生成快照（仅 COMPLETED） */
export function canCreateSnapshot(batch) {
  return batch?.status === COLLECTION_BATCH_STATUS.COMPLETED
}

// ========== 权限判定（前端 UI 收敛，真正鉴权以后端为准） ==========

const GLOBAL_MANAGE_ROLES = [ROLE.SYSTEM_ADMIN, ROLE.TEACHER]
const PROJECT_MANAGE_ROLES = [PROJECT_ROLE.OWNER, PROJECT_ROLE.MAINTAINER]

export function canAccessCollection() {
  return currentRoles().length > 0
}

export function canManageCollection(projectId, projectRole) {
  if (!canAccessCollection()) return false
  if (currentRoles().some((role) => GLOBAL_MANAGE_ROLES.includes(role))) return true
  const role = projectRole || findMockProject(projectId)?.myRole || null
  return PROJECT_MANAGE_ROLES.includes(role)
}

/** 创建/执行批次：管理者，或计划负责人（本项目 MEMBER） */
export function canOperateCollectionBatch(plan, projectRole) {
  if (!plan) return false
  if (canManageCollection(plan.projectId, projectRole)) return true
  const role = projectRole || findMockProject(plan.projectId)?.myRole || null
  return role === PROJECT_ROLE.MEMBER && Number(plan.ownerId) === currentUserId()
}

// ========== Mock 数据 ==========

const MOCK_CURRENT_USER_ID = 2
const MOCK_USER_NAMES = { 1: '系统管理员', 2: '张同学', 3: '李同学' }
const MOCK_RECORD_CODES = { 301: 'ER-202610-000301', 302: 'ER-202610-000302' }

function currentUserId() {
  const id = userStore?.userInfo?.id
  return id == null ? MOCK_CURRENT_USER_ID : Number(id)
}

function currentUserName() {
  return userStore?.userInfo?.name || userStore?.userInfo?.username || MOCK_USER_NAMES[currentUserId()] || '当前用户'
}

function userName(userId) {
  return MOCK_USER_NAMES[userId] || `用户 ${userId}`
}

function nowIso() {
  return new Date().toISOString()
}

function projectNameOf(projectId) {
  return findMockProject(projectId)?.name || null
}

function mockHash(seed) {
  let hash = 0x811c9dc5
  let out = ''
  for (let round = 0; round < 8; round++) {
    for (let i = 0; i < seed.length; i++) {
      hash ^= seed.charCodeAt(i) + round * 131
      hash = Math.imul(hash, 0x01000193) >>> 0
      hash = (hash ^ (hash >>> 13)) >>> 0
    }
    out += hash.toString(16).padStart(8, '0')
  }
  return out
}

function metric(code, name, valueType, unit, required = true) {
  return { code, name, valueType, unit, required }
}

// ---- 计划 ----

const MOCK_PLANS = [
  {
    id: 401,
    code: 'CP-202610-0401',
    projectId: 10,
    name: '5V 输出 CSV 采集',
    purpose: '记录负载变化下的输出电压与电流，用于稳定性分析。',
    ownerId: 2,
    sourceType: COLLECTION_SOURCE_TYPE.FILE,
    metricDefinitions: [metric('voltage', '电压', 'DECIMAL', 'V'), metric('current', '电流', 'DECIMAL', 'mA', false)],
    linkedExperimentRecordId: 301,
    retentionNote: '随实验长期保留，原件不可覆盖。',
    status: COLLECTION_PLAN_STATUS.READY,
    version: 2,
    createdBy: 2,
    createTime: '2026-10-18T10:00:00',
    updateTime: '2026-10-19T09:00:00'
  },
  {
    id: 402,
    code: 'CP-202610-0402',
    projectId: 10,
    name: '环境温湿度人工记录',
    purpose: '在实验现场逐条录入温湿度，保留方法与仪器。',
    ownerId: 2,
    sourceType: COLLECTION_SOURCE_TYPE.MANUAL,
    metricDefinitions: [metric('temperature', '温度', 'DECIMAL', '℃'), metric('humidity', '湿度', 'DECIMAL', '%')],
    linkedExperimentRecordId: 302,
    retentionNote: null,
    status: COLLECTION_PLAN_STATUS.READY,
    version: 2,
    createdBy: 2,
    createTime: '2026-10-17T11:00:00',
    updateTime: '2026-10-18T15:00:00'
  },
  {
    id: 403,
    code: 'CP-202610-0403',
    projectId: 5,
    name: '功率长时间采集',
    purpose: '计划长时间记录功率，尚未就绪。',
    ownerId: 2,
    sourceType: COLLECTION_SOURCE_TYPE.FILE,
    metricDefinitions: [metric('power', '功率', 'DECIMAL', 'W')],
    linkedExperimentRecordId: null,
    retentionNote: null,
    status: COLLECTION_PLAN_STATUS.DRAFT,
    version: 1,
    createdBy: 2,
    createTime: '2026-10-20T16:00:00',
    updateTime: '2026-10-20T16:00:00'
  },
  {
    id: 404,
    code: 'CP-202610-0404',
    projectId: 10,
    name: '旧版电压采集（归档）',
    purpose: '已归档的历史计划，保留批次与快照证据。',
    ownerId: 1,
    sourceType: COLLECTION_SOURCE_TYPE.FILE,
    metricDefinitions: [metric('voltage', '电压', 'DECIMAL', 'V')],
    linkedExperimentRecordId: null,
    retentionNote: '归档不删除证据。',
    status: COLLECTION_PLAN_STATUS.ARCHIVED,
    version: 3,
    createdBy: 1,
    createTime: '2026-09-30T10:00:00',
    updateTime: '2026-10-10T10:00:00'
  }
]

// ---- 批次 ----

const MOCK_BATCHES = [
  {
    id: 601,
    batchCode: 'CB-20261020-000601',
    planId: 401,
    projectId: 10,
    sourceType: COLLECTION_SOURCE_TYPE.FILE,
    status: COLLECTION_BATCH_STATUS.COMPLETED,
    operatorId: 2,
    startedAt: '2026-10-20T09:00:00',
    completedAt: '2026-10-20T11:20:00',
    recordCount: 128,
    rejectedCount: 2,
    observedFrom: '2026-10-20T09:00:00',
    observedTo: '2026-10-20T11:07:00',
    sourceRevisionId: 701,
    completionReason: 'CSV 导入与映射已人工确认',
    checksum: 'a41c6d0f8b27e95c3d14f60b7a28e95d61c0b4f3ae72d98c15f0b6a7d3e8c920',
    version: 4,
    createTime: '2026-10-20T09:00:00',
    updateTime: '2026-10-20T11:20:00'
  },
  {
    id: 602,
    batchCode: 'CB-20261021-000602',
    planId: 401,
    projectId: 10,
    sourceType: COLLECTION_SOURCE_TYPE.FILE,
    status: COLLECTION_BATCH_STATUS.REVIEW_REQUIRED,
    operatorId: 2,
    startedAt: '2026-10-21T14:00:00',
    completedAt: null,
    recordCount: 0,
    rejectedCount: 0,
    observedFrom: null,
    observedTo: null,
    sourceRevisionId: 702,
    completionReason: null,
    checksum: null,
    version: 3,
    createTime: '2026-10-21T14:00:00',
    updateTime: '2026-10-21T14:12:00'
  },
  {
    id: 603,
    batchCode: 'CB-20261018-000603',
    planId: 402,
    projectId: 10,
    sourceType: COLLECTION_SOURCE_TYPE.MANUAL,
    status: COLLECTION_BATCH_STATUS.COMPLETED,
    operatorId: 2,
    startedAt: '2026-10-18T15:10:00',
    completedAt: '2026-10-18T16:00:00',
    recordCount: 6,
    rejectedCount: 0,
    observedFrom: '2026-10-18T15:10:00',
    observedTo: '2026-10-18T15:55:00',
    sourceRevisionId: 801,
    completionReason: '人工观测已复核',
    checksum: '5d8c1f7a9b3e6d2407c4a8f1b5e9d3628a7c0f4e1d9b6a3c8e2f5d7b1a4c9e60',
    version: 5,
    createTime: '2026-10-18T15:10:00',
    updateTime: '2026-10-18T16:00:00'
  },
  {
    id: 604,
    batchCode: 'CB-20261019-000604',
    planId: 401,
    projectId: 10,
    sourceType: COLLECTION_SOURCE_TYPE.FILE,
    status: COLLECTION_BATCH_STATUS.FAILED,
    operatorId: 2,
    startedAt: '2026-10-19T08:00:00',
    completedAt: null,
    recordCount: 0,
    rejectedCount: 1,
    observedFrom: null,
    observedTo: null,
    sourceRevisionId: 703,
    completionReason: '第 12 行缺少电压值，解析失败；原件与失败审计已保留',
    checksum: null,
    version: 3,
    createTime: '2026-10-19T08:00:00',
    updateTime: '2026-10-19T08:03:00'
  },
  {
    id: 605,
    batchCode: 'CB-20261021-000605',
    planId: 403,
    projectId: 5,
    sourceType: COLLECTION_SOURCE_TYPE.FILE,
    status: COLLECTION_BATCH_STATUS.CREATED,
    operatorId: 2,
    startedAt: null,
    completedAt: null,
    recordCount: 0,
    rejectedCount: 0,
    observedFrom: null,
    observedTo: null,
    sourceRevisionId: null,
    completionReason: null,
    checksum: null,
    version: 1,
    createTime: '2026-10-21T18:00:00',
    updateTime: '2026-10-21T18:00:00'
  }
]

// ---- CSV 预览与导入 ----

function buildFilePreview(rows) {
  const headers = ['observed_at', 'voltage', 'current', 'note']
  return {
    headers,
    rows: rows.map((r) => [r.observedAt, r.voltage, r.current, r.note || '']),
    warnings: [
      { row: 12, column: 'voltage', message: '电压为空，将记为 REJECTED（MISSING_VALUE）' },
      { row: 64, column: 'current', message: '电流单位识别为 mA，与目标单位一致' }
    ],
    truncated: false,
    parsedRowCount: rows.length
  }
}

function buildCsvRows(count, startIso = '2026-10-20T09:00:00') {
  const start = Date.parse(startIso)
  return Array.from({ length: count }, (_, i) => {
    const observedAt = new Date(start + i * 60 * 1000)
    const pad = (n) => String(n).padStart(2, '0')
    const text = `${observedAt.getFullYear()}-${pad(observedAt.getMonth() + 1)}-${pad(observedAt.getDate())} ${pad(observedAt.getHours())}:${pad(observedAt.getMinutes())}:${pad(observedAt.getSeconds())}`
    return {
      observedAt: text,
      voltage: i === 11 ? '' : (5 + (i % 7) * 0.01).toFixed(2),
      current: 100 + (i % 5) * 10,
      note: i % 16 === 0 ? '负载切换' : ''
    }
  })
}

const MOCK_CSV_ROWS = buildCsvRows(128)

const MOCK_IMPORTS = [
  {
    id: 701,
    batchId: 601,
    status: DATA_IMPORT_STATUS.CONFIRMED,
    version: 3,
    importVersion: 2,
    originalFile: {
      fileName: 'voltage_20261020.csv',
      size: 18432,
      mimeType: 'text/csv',
      sha256: '9f2c41d7a8b3e60c5d1f7a2b9e4c8d3061f5a7c2e9d4b8a1c6f3d0e7b2a5c9d48',
      uploadedBy: 2,
      uploadedByName: '张同学',
      uploadTime: '2026-10-20T09:02:00'
    },
    mapping: {
      version: 2,
      importVersion: 2,
      mapping: {
        delimiter: ',',
        encoding: 'UTF-8',
        headerRow: 1,
        timeMapping: { sourceColumn: 'observed_at', format: 'yyyy-MM-dd HH:mm:ss', timezone: 'Asia/Shanghai' },
        columns: [
          { sourceColumn: 'voltage', metricCode: 'voltage', valueType: 'DECIMAL', sourceUnit: 'V', targetUnit: 'V' },
          { sourceColumn: 'current', metricCode: 'current', valueType: 'DECIMAL', sourceUnit: 'mA', targetUnit: 'mA' }
        ],
        ignoredColumns: ['note']
      }
    },
    preview: buildFilePreview(MOCK_CSV_ROWS.slice(0, 50)),
    recordCount: 128,
    rejectedCount: 2,
    failureReason: null,
    createTime: '2026-10-20T09:02:00',
    updateTime: '2026-10-20T09:18:00'
  },
  {
    id: 702,
    batchId: 602,
    status: DATA_IMPORT_STATUS.MAPPING_REQUIRED,
    version: 2,
    importVersion: 2,
    originalFile: {
      fileName: 'sensor_20261021.csv',
      size: 9216,
      mimeType: 'text/csv',
      sha256: 'c7d1a0e5b9f3c62d8a4e1f7b0c9d3e6a2f5b8c1d4e7a0f3b6c9d2e5a8f1b4c70',
      uploadedBy: 2,
      uploadedByName: '张同学',
      uploadTime: '2026-10-21T14:01:00'
    },
    mapping: {
      version: 2,
      importVersion: 2,
      mapping: {
        delimiter: ',',
        encoding: 'UTF-8',
        headerRow: 1,
        timeMapping: { sourceColumn: 'observed_at', format: 'yyyy-MM-dd HH:mm:ss', timezone: 'Asia/Shanghai' },
        columns: [
          { sourceColumn: 'temperature', metricCode: 'voltage', valueType: 'DECIMAL', sourceUnit: 'V', targetUnit: 'V' }
        ],
        ignoredColumns: ['humidity', 'note']
      }
    },
    preview: {
      headers: ['observed_at', 'temperature', 'humidity', 'note'],
      rows: Array.from({ length: 50 }, (_, i) => [
        `2026-10-21 14:${String(i % 60).padStart(2, '0')}:00`,
        (24 + (i % 5) * 0.1).toFixed(1),
        (55 + (i % 7) * 0.2).toFixed(1),
        i % 10 === 0 ? '空调出风口附近' : ''
      ]),
      warnings: [{ row: 18, column: 'temperature', message: '温度列尚未映射到计划指标' }],
      truncated: false,
      parsedRowCount: 50
    },
    recordCount: 0,
    rejectedCount: 0,
    failureReason: null,
    createTime: '2026-10-21T14:01:00',
    updateTime: '2026-10-21T14:12:00'
  },
  {
    id: 703,
    batchId: 604,
    status: DATA_IMPORT_STATUS.FAILED,
    version: 2,
    importVersion: 1,
    originalFile: {
      fileName: 'broken_20261019.csv',
      size: 6144,
      mimeType: 'text/csv',
      sha256: 'b1e4d7a0c3f6b9d2e5a8c1f4b7d0e3a6c9f2b5d8e1a4c7f0b3d6e9a2c5f8b1d4',
      uploadedBy: 2,
      uploadedByName: '张同学',
      uploadTime: '2026-10-19T08:01:00'
    },
    mapping: null,
    preview: null,
    recordCount: 0,
    rejectedCount: 1,
    failureReason: '第 12 行 voltage 列缺失，无法解析为 DECIMAL（PARSE_ERROR）',
    createTime: '2026-10-19T08:01:00',
    updateTime: '2026-10-19T08:03:00'
  }
]

// ---- 人工观测与审计 ----

const MOCK_MANUAL_REVISIONS = [
  { id: 8101, manualRecordId: 801, revisionNo: 1, oldValue: null, newValue: 24.6, changeReason: '初始录入', operatorId: 2, operatorName: '张同学', createTime: '2026-10-18T15:10:00' },
  { id: 8102, manualRecordId: 801, revisionNo: 2, oldValue: 24.6, newValue: 24.8, changeReason: '原读数受空调出风口影响', operatorId: 2, operatorName: '张同学', createTime: '2026-10-18T15:30:00' },
  { id: 8103, manualRecordId: 802, revisionNo: 1, oldValue: null, newValue: 55.2, changeReason: '初始录入', operatorId: 2, operatorName: '张同学', createTime: '2026-10-18T15:12:00' },
  { id: 8104, manualRecordId: 803, revisionNo: 1, oldValue: null, newValue: 25.0, changeReason: '初始录入', operatorId: 2, operatorName: '张同学', createTime: '2026-10-18T15:25:00' },
  { id: 8105, manualRecordId: 804, revisionNo: 1, oldValue: null, newValue: 57.8, changeReason: '初始录入', operatorId: 2, operatorName: '张同学', createTime: '2026-10-18T15:28:00' },
  { id: 8106, manualRecordId: 805, revisionNo: 1, oldValue: null, newValue: 24.9, changeReason: '初始录入', operatorId: 2, operatorName: '张同学', createTime: '2026-10-18T15:40:00' },
  { id: 8107, manualRecordId: 806, revisionNo: 1, oldValue: null, newValue: 56.4, changeReason: '初始录入', operatorId: 2, operatorName: '张同学', createTime: '2026-10-18T15:55:00' }
]

const MOCK_MANUAL_RECORDS = [
  {
    id: 801,
    batchId: 603,
    metricCode: 'temperature',
    valueType: 'DECIMAL',
    value: 24.8,
    unit: '℃',
    observedAt: '2026-10-18T15:10:00+08:00',
    recordedBy: 2,
    method: '温湿度计直读',
    instrument: 'TH-20 / LAB-B-003',
    note: '修正后读数',
    version: 2,
    revisionNo: 2,
    previousRevisionId: 8101,
    createTime: '2026-10-18T15:10:00',
    updateTime: '2026-10-18T15:30:00'
  },
  {
    id: 802,
    batchId: 603,
    metricCode: 'humidity',
    valueType: 'DECIMAL',
    value: 55.2,
    unit: '%',
    observedAt: '2026-10-18T15:12:00+08:00',
    recordedBy: 2,
    method: '温湿度计直读',
    instrument: 'TH-20 / LAB-B-003',
    note: null,
    version: 1,
    revisionNo: 1,
    previousRevisionId: null,
    createTime: '2026-10-18T15:12:00',
    updateTime: '2026-10-18T15:12:00'
  },
  {
    id: 803,
    batchId: 603,
    metricCode: 'temperature',
    valueType: 'DECIMAL',
    value: 25.0,
    unit: '℃',
    observedAt: '2026-10-18T15:25:00+08:00',
    recordedBy: 2,
    method: '温湿度计直读',
    instrument: 'TH-20 / LAB-B-003',
    note: null,
    version: 1,
    revisionNo: 1,
    previousRevisionId: null,
    createTime: '2026-10-18T15:25:00',
    updateTime: '2026-10-18T15:25:00'
  },
  {
    id: 804,
    batchId: 603,
    metricCode: 'humidity',
    valueType: 'DECIMAL',
    value: 57.8,
    unit: '%',
    observedAt: '2026-10-18T15:28:00+08:00',
    recordedBy: 2,
    method: '温湿度计直读',
    instrument: 'TH-20 / LAB-B-003',
    note: null,
    version: 1,
    revisionNo: 1,
    previousRevisionId: null,
    createTime: '2026-10-18T15:28:00',
    updateTime: '2026-10-18T15:28:00'
  },
  {
    id: 805,
    batchId: 603,
    metricCode: 'temperature',
    valueType: 'DECIMAL',
    value: 24.9,
    unit: '℃',
    observedAt: '2026-10-18T15:40:00+08:00',
    recordedBy: 2,
    method: '温湿度计直读',
    instrument: 'TH-20 / LAB-B-003',
    note: null,
    version: 1,
    revisionNo: 1,
    previousRevisionId: null,
    createTime: '2026-10-18T15:40:00',
    updateTime: '2026-10-18T15:40:00'
  },
  {
    id: 806,
    batchId: 603,
    metricCode: 'humidity',
    valueType: 'DECIMAL',
    value: 56.4,
    unit: '%',
    observedAt: '2026-10-18T15:55:00+08:00',
    recordedBy: 2,
    method: '温湿度计直读',
    instrument: 'TH-20 / LAB-B-003',
    note: null,
    version: 1,
    revisionNo: 1,
    previousRevisionId: null,
    createTime: '2026-10-18T15:55:00',
    updateTime: '2026-10-18T15:55:00'
  }
]

// ---- 采集记录与快照 ----

function buildFileCollectionRecords() {
  return MOCK_CSV_ROWS.map((row, i) => {
    const rejected = i === 11 || i === 63
    return {
      id: 9001 + i,
      batchId: 601,
      sourceType: COLLECTION_SOURCE_TYPE.FILE,
      metricCode: 'voltage',
      valueType: 'DECIMAL',
      value: row.voltage === '' ? null : Number(row.voltage),
      unit: 'V',
      observedAt: row.observedAt.replace(' ', 'T') + '+08:00',
      recordedBy: 2,
      method: 'FILE_IMPORT',
      instrument: null,
      rawRowNo: i + 2,
      qualityStatus: rejected ? QUALITY_STATUS.REJECTED : QUALITY_STATUS.GOOD,
      parserVersion: 'w43-csv-1',
      createTime: '2026-10-20T09:18:00'
    }
  })
}

function buildManualCollectionRecords() {
  return MOCK_MANUAL_RECORDS.map((record, i) => ({
    id: 9501 + i,
    batchId: 603,
    sourceType: COLLECTION_SOURCE_TYPE.MANUAL,
    metricCode: record.metricCode,
    valueType: record.valueType,
    value: record.value,
    unit: record.unit,
    observedAt: record.observedAt,
    recordedBy: record.recordedBy,
    method: record.method,
    instrument: record.instrument,
    rawRowNo: null,
    qualityStatus: QUALITY_STATUS.GOOD,
    parserVersion: 'w43-manual-1',
    createTime: record.createTime
  }))
}

const MOCK_COLLECTION_RECORDS = [...buildFileCollectionRecords(), ...buildManualCollectionRecords()]

const MOCK_SNAPSHOTS = [
  {
    id: 501,
    snapshotCode: 'SN-20261020-000501',
    batchId: 601,
    batchCode: 'CB-20261020-000601',
    projectId: 10,
    snapshotType: SNAPSHOT_TYPE.FULL_BATCH,
    sourceRevisionId: 701,
    recordCount: 128,
    rejectedCount: 2,
    observedFrom: '2026-10-20T09:00:00+08:00',
    observedTo: '2026-10-20T11:07:00+08:00',
    batchChecksum: 'a41c6d0f8b27e95c3d14f60b7a28e95d61c0b4f3ae72d98c15f0b6a7d3e8c920',
    mappingDigest: mockHash(JSON.stringify(MOCK_IMPORTS[0].mapping)),
    createdBy: 2,
    createTime: '2026-10-20T11:25:00'
  },
  {
    id: 502,
    snapshotCode: 'SN-20261018-000502',
    batchId: 603,
    batchCode: 'CB-20261018-000603',
    projectId: 10,
    snapshotType: SNAPSHOT_TYPE.FULL_BATCH,
    sourceRevisionId: 801,
    recordCount: 6,
    rejectedCount: 0,
    observedFrom: '2026-10-18T15:10:00+08:00',
    observedTo: '2026-10-18T15:55:00+08:00',
    batchChecksum: '5d8c1f7a9b3e6d2407c4a8f1b5e9d3628a7c0f4e1d9b6a3c8e2f5d7b1a4c9e60',
    mappingDigest: mockHash('manual-w43'),
    createdBy: 2,
    createTime: '2026-10-18T16:05:00'
  }
]

// ---- mock 辅助 ----

export function findMockPlan(id) {
  return MOCK_PLANS.find((p) => p.id === Number(id)) || null
}

export function findMockBatch(id) {
  return MOCK_BATCHES.find((b) => b.id === Number(id)) || null
}

export function findMockSnapshot(id) {
  return MOCK_SNAPSHOTS.find((s) => s.id === Number(id)) || null
}

export function findMockImport(id) {
  return MOCK_IMPORTS.find((i) => i.id === Number(id)) || null
}

export function findMockManualRecord(id) {
  return MOCK_MANUAL_RECORDS.find((m) => m.id === Number(id)) || null
}

function latestBatchOfPlan(planId) {
  const list = MOCK_BATCHES.filter((b) => b.planId === Number(planId))
    .slice()
    .sort((a, b) => Date.parse(b.createTime) - Date.parse(a.createTime))
  return list[0] || null
}

function planSummary(plan) {
  const latest = latestBatchOfPlan(plan.id)
  return {
    id: plan.id,
    code: plan.code,
    projectId: plan.projectId,
    projectName: projectNameOf(plan.projectId),
    name: plan.name,
    purpose: plan.purpose,
    ownerId: plan.ownerId,
    ownerName: userName(plan.ownerId),
    sourceType: plan.sourceType,
    metricDefinitions: plan.metricDefinitions.map((m) => ({ ...m })),
    linkedExperimentRecordId: plan.linkedExperimentRecordId,
    linkedExperimentRecordCode: plan.linkedExperimentRecordId ? MOCK_RECORD_CODES[plan.linkedExperimentRecordId] || null : null,
    retentionNote: plan.retentionNote,
    status: plan.status,
    version: plan.version,
    createdBy: plan.createdBy,
    createdByName: userName(plan.createdBy),
    createTime: plan.createTime,
    updateTime: plan.updateTime,
    batchCount: MOCK_BATCHES.filter((b) => b.planId === plan.id).length,
    latestBatch: latest ? batchSummary(latest) : null
  }
}

function batchSummary(batch) {
  const plan = findMockPlan(batch.planId)
  return {
    id: batch.id,
    batchCode: batch.batchCode,
    planId: batch.planId,
    planName: plan?.name || null,
    projectId: batch.projectId,
    projectName: projectNameOf(batch.projectId),
    sourceType: batch.sourceType,
    status: batch.status,
    operatorId: batch.operatorId,
    operatorName: userName(batch.operatorId),
    startedAt: batch.startedAt,
    completedAt: batch.completedAt,
    recordCount: batch.recordCount,
    rejectedCount: batch.rejectedCount,
    observedFrom: batch.observedFrom,
    observedTo: batch.observedTo,
    sourceRevisionId: batch.sourceRevisionId,
    completionReason: batch.completionReason,
    checksum: batch.checksum,
    version: batch.version,
    createTime: batch.createTime,
    updateTime: batch.updateTime
  }
}

function snapshotDetail(snapshot) {
  return {
    ...snapshot,
    createdByName: userName(snapshot.createdBy)
  }
}

function importSummary(importItem) {
  return {
    id: importItem.id,
    batchId: importItem.batchId,
    batchCode: findMockBatch(importItem.batchId)?.batchCode || null,
    status: importItem.status,
    version: importItem.version,
    importVersion: importItem.importVersion,
    originalFile: { ...importItem.originalFile },
    recordCount: importItem.recordCount,
    rejectedCount: importItem.rejectedCount,
    failureReason: importItem.failureReason,
    createTime: importItem.createTime,
    updateTime: importItem.updateTime
  }
}

function importDetail(importItem) {
  return {
    ...importSummary(importItem),
    mapping: importItem.mapping ? JSON.parse(JSON.stringify(importItem.mapping)) : null,
    preview: importItem.preview ? JSON.parse(JSON.stringify(importItem.preview)) : null
  }
}

function manualSummary(record) {
  return {
    id: record.id,
    batchId: record.batchId,
    metricCode: record.metricCode,
    valueType: record.valueType,
    value: record.value,
    unit: record.unit,
    observedAt: record.observedAt,
    recordedBy: record.recordedBy,
    recordedByName: userName(record.recordedBy),
    method: record.method,
    instrument: record.instrument,
    note: record.note,
    version: record.version,
    revisionNo: record.revisionNo,
    previousRevisionId: record.previousRevisionId,
    createTime: record.createTime,
    updateTime: record.updateTime
  }
}

function batchDetail(batch) {
  const plan = findMockPlan(batch.planId)
  const imports = MOCK_IMPORTS.filter((i) => i.batchId === batch.id).map(importSummary)
  const manualRecords = MOCK_MANUAL_RECORDS.filter((m) => m.batchId === batch.id).map(manualSummary)
  const snapshot = MOCK_SNAPSHOTS.find((s) => s.batchId === batch.id) || null
  const records = MOCK_COLLECTION_RECORDS.filter((r) => r.batchId === batch.id)
  const rejected = records.filter((r) => r.qualityStatus === QUALITY_STATUS.REJECTED).slice(0, 5)
  return {
    ...batchSummary(batch),
    plan: plan ? planSummary(plan) : null,
    imports,
    manualRecords,
    snapshot: snapshot ? snapshotDetail(snapshot) : null,
    recordSamples: records.slice(0, 10),
    errorSamples: rejected.map((r) => ({
      rawRowNo: r.rawRowNo,
      metricCode: r.metricCode,
      reason: r.value == null ? 'MISSING_VALUE' : 'PARSE_ERROR',
      message: r.value == null ? '该行电压为空，已拒绝' : '该行无法解析为 DECIMAL'
    }))
  }
}

function planVisible(plan) {
  return Boolean(plan) && Boolean(findMockProject(plan.projectId))
}

function batchVisible(batch) {
  return Boolean(batch) && Boolean(findMockProject(batch.projectId))
}

function requireVersion(resource, version) {
  if (version == null) return mockReject(400, '缺少 version 参数', 'INVALID_PARAMETER')
  if (Number(version) !== Number(resource.version)) {
    return mockReject(409, '数据已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: resource.version })
  }
  return null
}

const MOCK_IDEMPOTENCY = new Map()

function withIdempotency(key, payload, handler) {
  if (!key) return mockReject(400, '缺少 Idempotency-Key', 'INVALID_PARAMETER')
  const digest = requestDigest(payload)
  const hit = MOCK_IDEMPOTENCY.get(key)
  if (hit) {
    if (hit.digest !== digest) return mockReject(409, '幂等键已用于其他请求', 'IDEMPOTENCY_KEY_REUSED')
    return Promise.resolve({ ...hit.result, idempotentReplay: true })
  }
  return Promise.resolve()
    .then(handler)
    .then((result) => {
      MOCK_IDEMPOTENCY.set(key, { result, digest })
      return result
    })
}

function nextPlanId() {
  return Math.max(...MOCK_PLANS.map((p) => p.id)) + 1
}

function nextBatchId() {
  return Math.max(...MOCK_BATCHES.map((b) => b.id)) + 1
}

function nextImportId() {
  return Math.max(...MOCK_IMPORTS.map((i) => i.id)) + 1
}

function nextManualId() {
  return Math.max(...MOCK_MANUAL_RECORDS.map((m) => m.id)) + 1
}

function nextManualRevisionId() {
  return Math.max(...MOCK_MANUAL_REVISIONS.map((r) => r.id)) + 1
}

function nextSnapshotId() {
  return Math.max(...MOCK_SNAPSHOTS.map((s) => s.id)) + 1
}

function todayCode(date = new Date()) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`
}

function monthCode(date = new Date()) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}`
}

function normalizeMetricDefinitions(definitions) {
  if (!Array.isArray(definitions) || definitions.length < 1 || definitions.length > 100) {
    return { error: '指标定义需为 1-100 个' }
  }
  const seen = new Set()
  const normalized = []
  for (const item of definitions) {
    const code = String(item?.code || '').trim()
    const name = String(item?.name || '').trim()
    if (!/^[a-z][a-z0-9_]{1,49}$/.test(code)) return { error: `指标编码非法：${code || '(空)'}` }
    if (seen.has(code)) return { error: `指标编码重复：${code}` }
    seen.add(code)
    if (name.length < 1 || name.length > 50) return { error: `指标名称需为 1-50 字符：${code}` }
    if (!Object.values(COLLECTION_VALUE_TYPE).includes(item?.valueType)) return { error: `指标值类型非法：${code}` }
    const unit = String(item?.unit || '').trim()
    if (!unit || unit.length > 20) return { error: `指标单位需为 1-20 字符：${code}` }
    normalized.push({ code, name, valueType: item.valueType, unit, required: Boolean(item.required) })
  }
  return { definitions: normalized }
}

// ========== API：采集计划 ==========

export function createCollectionPlan(projectId, data) {
  if (!getMockEnabled()) {
    return request.post(`/api/projects/${projectId}/collection-plans`, data)
  }
  if (!canAccessCollection()) return mockReject(403, '当前账号无权访问数据采集', 'ACCESS_DENIED')
  const project = findMockProject(projectId)
  if (!project) return mockReject(404, '项目不存在或不可见', 'PROJECT_NOT_FOUND')
  if (!canManageCollection(projectId)) {
    return mockReject(403, '无采集计划管理权限', 'COLLECTION_OPERATION_DENIED')
  }
  const name = String(data?.name || '').trim()
  if (name.length < 2 || name.length > 120) return mockReject(400, '计划名称需为 2-120 字符', 'INVALID_PARAMETER')
  const purpose = String(data?.purpose || '').trim()
  if (purpose.length < 1 || purpose.length > 1000) return mockReject(400, '计划目的需为 1-1000 字符', 'INVALID_PARAMETER')
  if (!Object.values(COLLECTION_SOURCE_TYPE).includes(data?.sourceType)) {
    return mockReject(400, '采集来源非法', 'INVALID_PARAMETER')
  }
  const ownerId = Number(data?.ownerId)
  const members = getMockProjectMembers()
  if (!members.some((m) => Number(m.userId) === ownerId)) {
    return mockReject(400, '负责人必须是项目成员', 'INVALID_PARAMETER')
  }
  const normalized = normalizeMetricDefinitions(data?.metricDefinitions)
  if (normalized.error) return mockReject(400, normalized.error, 'INVALID_PARAMETER')

  const id = nextPlanId()
  const plan = {
    id,
    code: `CP-${monthCode()}-${String(id).padStart(4, '0')}`,
    projectId: Number(projectId),
    name,
    purpose,
    ownerId,
    sourceType: data.sourceType,
    metricDefinitions: normalized.definitions,
    linkedExperimentRecordId: data?.linkedExperimentRecordId == null ? null : Number(data.linkedExperimentRecordId),
    retentionNote: data?.retentionNote || null,
    status: COLLECTION_PLAN_STATUS.DRAFT,
    version: 1,
    createdBy: currentUserId(),
    createTime: nowIso(),
    updateTime: nowIso()
  }
  MOCK_PLANS.unshift(plan)
  return Promise.resolve({ id: plan.id, code: plan.code, status: plan.status, version: plan.version })
}

export function listCollectionPlans(projectId, params = {}) {
  if (!getMockEnabled()) {
    return request.get(`/api/projects/${projectId}/collection-plans`, { params })
  }
  if (!canAccessCollection()) return mockReject(403, '当前账号无权访问数据采集', 'ACCESS_DENIED')
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    if (isMockScenario('empty')) return { total: 0, page: 1, pageSize: 20, list: [] }
    if (isMockScenario('permission')) return mockReject(403, '无权查看采集计划', 'COLLECTION_OPERATION_DENIED')
    if (isMockScenario('error')) return mockReject(500, '加载采集计划失败', 'INTERNAL_ERROR', { traceId: mockTraceId('plans') })
    const project = findMockProject(projectId)
    if (!project) return mockReject(404, '项目不存在或不可见', 'PROJECT_NOT_FOUND')

    let list = MOCK_PLANS.filter((p) => p.projectId === Number(projectId))
    if (params.status) list = list.filter((p) => p.status === params.status)
    if (params.sourceType) list = list.filter((p) => p.sourceType === params.sourceType)
    if (params.keyword) {
      const k = String(params.keyword).toLowerCase()
      list = list.filter((p) => `${p.name}${p.purpose}${p.code}`.toLowerCase().includes(k))
    }
    list = list
      .slice()
      .sort((a, b) => Date.parse(b.updateTime) - Date.parse(a.updateTime) || b.id - a.id)
      .map(planSummary)
    const page = Number(params.page) > 0 ? Number(params.page) : 1
    const pageSize = Number(params.pageSize) > 0 ? Math.min(Number(params.pageSize), 100) : 20
    const start = (page - 1) * pageSize
    return { total: list.length, page, pageSize, list: list.slice(start, start + pageSize) }
  })()
}

export function getCollectionPlan(planId) {
  if (!getMockEnabled()) {
    return request.get(`/api/collection-plans/${planId}`)
  }
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    if (isMockScenario('error')) return mockReject(500, '加载采集计划失败', 'INTERNAL_ERROR', { traceId: mockTraceId('plan') })
    if (isMockScenario('permission')) return mockReject(404, '采集计划不存在或不可见', 'COLLECTION_PLAN_NOT_FOUND')
    const plan = findMockPlan(planId)
    if (!planVisible(plan)) return mockReject(404, '采集计划不存在或不可见', 'COLLECTION_PLAN_NOT_FOUND')
    return planSummary(plan)
  })()
}

export function updateCollectionPlan(planId, data) {
  if (!getMockEnabled()) {
    return request.put(`/api/collection-plans/${planId}`, data)
  }
  const plan = findMockPlan(planId)
  if (!planVisible(plan)) return mockReject(404, '采集计划不存在或不可见', 'COLLECTION_PLAN_NOT_FOUND')
  if (!canManageCollection(plan.projectId)) {
    return mockReject(403, '无采集计划管理权限', 'COLLECTION_OPERATION_DENIED')
  }
  if (plan.status !== COLLECTION_PLAN_STATUS.DRAFT) {
    return mockReject(400, '只有 DRAFT 计划可编辑', 'INVALID_PARAMETER')
  }
  const versionError = requireVersion(plan, data?.version)
  if (versionError) return versionError
  if (data?.name != null) {
    const name = String(data.name).trim()
    if (name.length < 2 || name.length > 120) return mockReject(400, '计划名称需为 2-120 字符', 'INVALID_PARAMETER')
    plan.name = name
  }
  if (data?.purpose != null) {
    const purpose = String(data.purpose).trim()
    if (purpose.length < 1 || purpose.length > 1000) return mockReject(400, '计划目的需为 1-1000 字符', 'INVALID_PARAMETER')
    plan.purpose = purpose
  }
  if (data?.ownerId != null) {
    const ownerId = Number(data.ownerId)
    if (!getMockProjectMembers().some((m) => Number(m.userId) === ownerId)) {
      return mockReject(400, '负责人必须是项目成员', 'INVALID_PARAMETER')
    }
    plan.ownerId = ownerId
  }
  if (data?.metricDefinitions != null) {
    const normalized = normalizeMetricDefinitions(data.metricDefinitions)
    if (normalized.error) return mockReject(400, normalized.error, 'INVALID_PARAMETER')
    plan.metricDefinitions = normalized.definitions
  }
  if (data?.linkedExperimentRecordId !== undefined) {
    plan.linkedExperimentRecordId = data.linkedExperimentRecordId == null ? null : Number(data.linkedExperimentRecordId)
  }
  if (data?.retentionNote !== undefined) plan.retentionNote = data.retentionNote || null
  plan.version += 1
  plan.updateTime = nowIso()
  return Promise.resolve({ id: plan.id, status: plan.status, version: plan.version })
}

export function readyCollectionPlan(planId, payload) {
  if (!getMockEnabled()) {
    return request.post(`/api/collection-plans/${planId}/ready`, payload)
  }
  const plan = findMockPlan(planId)
  if (!planVisible(plan)) return mockReject(404, '采集计划不存在或不可见', 'COLLECTION_PLAN_NOT_FOUND')
  if (!canManageCollection(plan.projectId)) {
    return mockReject(403, '无采集计划管理权限', 'COLLECTION_OPERATION_DENIED')
  }
  if (isMockScenario('conflict')) {
    return mockReject(409, '数据已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: plan.version + 1 })
  }
  const versionError = requireVersion(plan, payload?.version)
  if (versionError) return versionError
  if (plan.status !== COLLECTION_PLAN_STATUS.DRAFT) {
    return mockReject(400, '只有 DRAFT 计划可置为 READY', 'INVALID_PARAMETER')
  }
  if (!plan.metricDefinitions.length) {
    return mockReject(400, '计划至少需要一个指标定义', 'INVALID_PARAMETER')
  }
  plan.status = COLLECTION_PLAN_STATUS.READY
  plan.version += 1
  plan.updateTime = nowIso()
  return Promise.resolve({ id: plan.id, status: plan.status, version: plan.version })
}

export function archiveCollectionPlan(planId, payload) {
  if (!getMockEnabled()) {
    return request.post(`/api/collection-plans/${planId}/archive`, payload)
  }
  const plan = findMockPlan(planId)
  if (!planVisible(plan)) return mockReject(404, '采集计划不存在或不可见', 'COLLECTION_PLAN_NOT_FOUND')
  if (!canManageCollection(plan.projectId)) {
    return mockReject(403, '无采集计划管理权限', 'COLLECTION_OPERATION_DENIED')
  }
  const versionError = requireVersion(plan, payload?.version)
  if (versionError) return versionError
  if (plan.status !== COLLECTION_PLAN_STATUS.READY) {
    return mockReject(400, '只有 READY 计划可归档', 'INVALID_PARAMETER')
  }
  plan.status = COLLECTION_PLAN_STATUS.ARCHIVED
  plan.version += 1
  plan.updateTime = nowIso()
  return Promise.resolve({ id: plan.id, status: plan.status, version: plan.version })
}

export function cancelCollectionPlan(planId, payload) {
  if (!getMockEnabled()) {
    return request.post(`/api/collection-plans/${planId}/cancel`, payload)
  }
  const plan = findMockPlan(planId)
  if (!planVisible(plan)) return mockReject(404, '采集计划不存在或不可见', 'COLLECTION_PLAN_NOT_FOUND')
  if (!canManageCollection(plan.projectId)) {
    return mockReject(403, '无采集计划管理权限', 'COLLECTION_OPERATION_DENIED')
  }
  const versionError = requireVersion(plan, payload?.version)
  if (versionError) return versionError
  if (![COLLECTION_PLAN_STATUS.DRAFT, COLLECTION_PLAN_STATUS.READY].includes(plan.status)) {
    return mockReject(400, '当前计划状态不可取消', 'INVALID_PARAMETER')
  }
  plan.status = COLLECTION_PLAN_STATUS.CANCELED
  plan.version += 1
  plan.updateTime = nowIso()
  return Promise.resolve({ id: plan.id, status: plan.status, version: plan.version })
}

// ========== API：采集批次 ==========

export function createCollectionBatch(planId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/collection-plans/${planId}/batches`, payload, {
      headers: idempotencyHeaders(idempotencyKey)
    })
  }
  return withIdempotency(idempotencyKey, { planId, ...payload }, async () => {
    const plan = findMockPlan(planId)
    if (!planVisible(plan)) return mockReject(404, '采集计划不存在或不可见', 'COLLECTION_PLAN_NOT_FOUND')
    if (!canOperateCollectionBatch(plan)) {
      return mockReject(403, '无批次创建权限', 'COLLECTION_OPERATION_DENIED')
    }
    const planVersionError = requireVersion(plan, payload?.planVersion)
    if (planVersionError) return planVersionError
    if (plan.status !== COLLECTION_PLAN_STATUS.READY) {
      return mockReject(409, '只有 READY 计划可创建批次', 'BATCH_INVALID_TRANSITION')
    }
    if (payload?.sourceType !== plan.sourceType) {
      return mockReject(400, '批次来源必须与计划一致', 'INVALID_PARAMETER')
    }
    const id = nextBatchId()
    const batch = {
      id,
      batchCode: `CB-${todayCode()}-${String(id).padStart(6, '0')}`,
      planId: plan.id,
      projectId: plan.projectId,
      sourceType: plan.sourceType,
      status: COLLECTION_BATCH_STATUS.CREATED,
      operatorId: currentUserId(),
      startedAt: nowIso(),
      completedAt: null,
      recordCount: 0,
      rejectedCount: 0,
      observedFrom: null,
      observedTo: null,
      sourceRevisionId: null,
      completionReason: null,
      checksum: null,
      version: 1,
      createTime: nowIso(),
      updateTime: nowIso()
    }
    MOCK_BATCHES.unshift(batch)
    return {
      id: batch.id,
      batchCode: batch.batchCode,
      status: batch.status,
      sourceType: batch.sourceType,
      version: batch.version
    }
  })
}

export function listCollectionBatches(planId, params = {}) {
  if (!getMockEnabled()) {
    return request.get(`/api/collection-plans/${planId}/batches`, { params })
  }
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    if (isMockScenario('empty')) return { total: 0, page: 1, pageSize: 20, list: [] }
    if (isMockScenario('permission')) return mockReject(403, '无权查看采集批次', 'COLLECTION_OPERATION_DENIED')
    if (isMockScenario('error')) return mockReject(500, '加载采集批次失败', 'INTERNAL_ERROR', { traceId: mockTraceId('batches') })
    const plan = findMockPlan(planId)
    if (!planVisible(plan)) return mockReject(404, '采集计划不存在或不可见', 'COLLECTION_PLAN_NOT_FOUND')
    let list = MOCK_BATCHES.filter((b) => b.planId === plan.id)
    if (params.status) list = list.filter((b) => b.status === params.status)
    if (params.sourceType) list = list.filter((b) => b.sourceType === params.sourceType)
    list = list.slice().sort((a, b) => Date.parse(b.createTime) - Date.parse(a.createTime) || b.id - a.id).map(batchSummary)
    const page = Number(params.page) > 0 ? Number(params.page) : 1
    const pageSize = Number(params.pageSize) > 0 ? Math.min(Number(params.pageSize), 100) : 20
    const start = (page - 1) * pageSize
    return { total: list.length, page, pageSize, list: list.slice(start, start + pageSize) }
  })()
}

export function getCollectionBatch(batchId) {
  if (!getMockEnabled()) {
    return request.get(`/api/collection-batches/${batchId}`)
  }
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    if (isMockScenario('error')) return mockReject(500, '加载采集批次失败', 'INTERNAL_ERROR', { traceId: mockTraceId('batch') })
    if (isMockScenario('permission')) return mockReject(404, '采集批次不存在或不可见', 'COLLECTION_BATCH_NOT_FOUND')
    const batch = findMockBatch(batchId)
    if (!batchVisible(batch)) return mockReject(404, '采集批次不存在或不可见', 'COLLECTION_BATCH_NOT_FOUND')
    return batchDetail(batch)
  })()
}

export function listCollectionRecords(batchId, params = {}) {
  if (!getMockEnabled()) {
    return request.get(`/api/collection-batches/${batchId}/records`, { params })
  }
  const batch = findMockBatch(batchId)
  if (!batchVisible(batch)) return mockReject(404, '采集批次不存在或不可见', 'COLLECTION_BATCH_NOT_FOUND')
  const list = MOCK_COLLECTION_RECORDS.filter((r) => r.batchId === batch.id)
    .slice()
    .sort((a, b) => Date.parse(a.observedAt) - Date.parse(b.observedAt) || a.id - b.id)
  const limit = Math.min(Number(params.limit) > 0 ? Number(params.limit) : 100, 500)
  const start = Number(params.cursor) > 0 ? Number(params.cursor) : 0
  const slice = list.slice(start, start + limit)
  const next = start + slice.length
  return Promise.resolve({
    list: slice,
    nextCursor: next < list.length ? String(next) : null,
    hasMore: next < list.length
  })
}

export function completeCollectionBatch(batchId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/collection-batches/${batchId}/complete`, payload, {
      headers: idempotencyHeaders(idempotencyKey)
    })
  }
  return withIdempotency(idempotencyKey, { batchId, ...payload }, async () => {
    const batch = findMockBatch(batchId)
    if (!batchVisible(batch)) return mockReject(404, '采集批次不存在或不可见', 'COLLECTION_BATCH_NOT_FOUND')
    if (!canOperateCollectionBatch(findMockPlan(batch.planId))) {
      return mockReject(403, '无批次完成权限', 'COLLECTION_OPERATION_DENIED')
    }
    if (isMockScenario('conflict')) {
      return mockReject(409, '数据已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: batch.version + 1 })
    }
    if (isMockScenario('immutable')) return mockReject(409, '批次已完成，不能重复修改', 'BATCH_IMMUTABLE')
    const versionError = requireVersion(batch, payload?.version)
    if (versionError) return versionError
    if (batch.status !== COLLECTION_BATCH_STATUS.REVIEW_REQUIRED) {
      return mockReject(409, '当前批次状态不可完成', 'BATCH_INVALID_TRANSITION')
    }
    if (batch.sourceType === COLLECTION_SOURCE_TYPE.FILE) {
      const imports = MOCK_IMPORTS.filter((i) => i.batchId === batch.id)
      if (!imports.some((i) => i.status === DATA_IMPORT_STATUS.CONFIRMED)) {
        return mockReject(409, 'CSV 导入尚未确认，不能完成批次', 'BATCH_INVALID_TRANSITION')
      }
    }
    if (batch.sourceType === COLLECTION_SOURCE_TYPE.MANUAL) {
      const count = MOCK_MANUAL_RECORDS.filter((m) => m.batchId === batch.id).length
      if (!count) return mockReject(409, '人工观测为空，不能完成批次', 'BATCH_INVALID_TRANSITION')
    }
    const records = MOCK_COLLECTION_RECORDS.filter((r) => r.batchId === batch.id)
    const times = records.map((r) => Date.parse(r.observedAt)).filter((t) => !isNaN(t))
    batch.status = COLLECTION_BATCH_STATUS.COMPLETED
    batch.completedAt = nowIso()
    batch.completionReason = payload?.completionReason || '采集数据已人工确认'
    batch.checksum = mockHash(`${batch.id}-${batch.recordCount}-${batch.sourceRevisionId}`)
    batch.observedFrom = times.length ? new Date(Math.min(...times)).toISOString() : null
    batch.observedTo = times.length ? new Date(Math.max(...times)).toISOString() : null
    batch.version += 1
    batch.updateTime = nowIso()
    return {
      id: batch.id,
      status: batch.status,
      version: batch.version,
      recordCount: batch.recordCount,
      rejectedCount: batch.rejectedCount,
      checksum: batch.checksum
    }
  })
}

export function cancelCollectionBatch(batchId, payload) {
  if (!getMockEnabled()) {
    return request.post(`/api/collection-batches/${batchId}/cancel`, payload)
  }
  const batch = findMockBatch(batchId)
  if (!batchVisible(batch)) return mockReject(404, '采集批次不存在或不可见', 'COLLECTION_BATCH_NOT_FOUND')
  if (!canOperateCollectionBatch(findMockPlan(batch.planId))) {
    return mockReject(403, '无批次取消权限', 'COLLECTION_OPERATION_DENIED')
  }
  const versionError = requireVersion(batch, payload?.version)
  if (versionError) return versionError
  if (![COLLECTION_BATCH_STATUS.CREATED, COLLECTION_BATCH_STATUS.PROCESSING, COLLECTION_BATCH_STATUS.REVIEW_REQUIRED, COLLECTION_BATCH_STATUS.FAILED].includes(batch.status)) {
    return mockReject(409, '当前批次状态不可取消', 'BATCH_INVALID_TRANSITION')
  }
  batch.status = COLLECTION_BATCH_STATUS.CANCELED
  batch.completionReason = payload?.reason || '批次已取消'
  batch.version += 1
  batch.updateTime = nowIso()
  return Promise.resolve({ id: batch.id, status: batch.status, version: batch.version })
}

export function archiveCollectionBatch(batchId, payload) {
  if (!getMockEnabled()) {
    return request.post(`/api/collection-batches/${batchId}/archive`, payload)
  }
  const batch = findMockBatch(batchId)
  if (!batchVisible(batch)) return mockReject(404, '采集批次不存在或不可见', 'COLLECTION_BATCH_NOT_FOUND')
  if (!canOperateCollectionBatch(findMockPlan(batch.planId))) {
    return mockReject(403, '无批次归档权限', 'COLLECTION_OPERATION_DENIED')
  }
  const versionError = requireVersion(batch, payload?.version)
  if (versionError) return versionError
  if (batch.status !== COLLECTION_BATCH_STATUS.COMPLETED) {
    return mockReject(409, '只有已完成批次可归档', 'BATCH_INVALID_TRANSITION')
  }
  batch.status = COLLECTION_BATCH_STATUS.ARCHIVED
  batch.version += 1
  batch.updateTime = nowIso()
  return Promise.resolve({ id: batch.id, status: batch.status, version: batch.version })
}

export function createCollectionSnapshot(batchId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/collection-batches/${batchId}/snapshots`, payload, {
      headers: idempotencyHeaders(idempotencyKey)
    })
  }
  return withIdempotency(idempotencyKey, { batchId, ...payload }, async () => {
    const batch = findMockBatch(batchId)
    if (!batchVisible(batch)) return mockReject(404, '采集批次不存在或不可见', 'COLLECTION_BATCH_NOT_FOUND')
    if (!canOperateCollectionBatch(findMockPlan(batch.planId))) {
      return mockReject(403, '无快照生成权限', 'COLLECTION_OPERATION_DENIED')
    }
    if (isMockScenario('immutable')) return mockReject(409, '批次已完成，快照不可修改', 'BATCH_IMMUTABLE')
    if (payload?.snapshotType !== SNAPSHOT_TYPE.FULL_BATCH) {
      return mockReject(400, '本周仅支持 FULL_BATCH 快照', 'INVALID_PARAMETER')
    }
    if (batch.status !== COLLECTION_BATCH_STATUS.COMPLETED) {
      return mockReject(409, '采集批次尚未完成，不能生成快照', 'BATCH_NOT_COMPLETED')
    }
    const batchVersionError = requireVersion(batch, payload?.batchVersion)
    if (batchVersionError) return batchVersionError
    const existing = MOCK_SNAPSHOTS.find((s) => s.batchId === batch.id)
    if (existing) return snapshotDetail(existing)
    const id = nextSnapshotId()
    const snapshot = {
      id,
      snapshotCode: `SN-${todayCode()}-${String(id).padStart(6, '0')}`,
      batchId: batch.id,
      batchCode: batch.batchCode,
      projectId: batch.projectId,
      snapshotType: SNAPSHOT_TYPE.FULL_BATCH,
      sourceRevisionId: batch.sourceRevisionId,
      recordCount: batch.recordCount,
      rejectedCount: batch.rejectedCount,
      observedFrom: batch.observedFrom,
      observedTo: batch.observedTo,
      batchChecksum: batch.checksum,
      mappingDigest: mockHash(`snapshot-${batch.id}`),
      createdBy: currentUserId(),
      createTime: nowIso()
    }
    MOCK_SNAPSHOTS.push(snapshot)
    return snapshotDetail(snapshot)
  })
}

// ========== API：CSV 导入 ==========

function validateCsvFile(file) {
  if (!file || typeof file.name !== 'string') {
    return mockReject(400, '未选择 CSV 文件', 'FILE_TYPE_UNSUPPORTED')
  }
  if (!file.name.toLowerCase().endsWith('.csv')) {
    return mockReject(400, '仅支持 .csv 文件', 'FILE_TYPE_UNSUPPORTED')
  }
  if (file.name.toLowerCase().includes('gbk')) {
    return mockReject(400, '仅支持 UTF-8 / UTF-8 BOM 编码', 'FILE_ENCODING_UNSUPPORTED')
  }
  if (Number(file.size) > 20 * 1024 * 1024) {
    return mockReject(400, '文件不能超过 20 MiB', 'FILE_TOO_LARGE')
  }
  return null
}

export function uploadFileImport(batchId, file, meta = {}) {
  if (!getMockEnabled()) {
    const form = new FormData()
    form.append('file', file)
    if (meta?.batchVersion != null) form.append('batchVersion', String(meta.batchVersion))
    return request.post(`/api/collection-batches/${batchId}/file-imports`, form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
  const batch = findMockBatch(batchId)
  if (!batchVisible(batch)) return mockReject(404, '采集批次不存在或不可见', 'COLLECTION_BATCH_NOT_FOUND')
  if (!canOperateCollectionBatch(findMockPlan(batch.planId))) {
    return mockReject(403, '无批次执行权限', 'COLLECTION_OPERATION_DENIED')
  }
  if (isBatchImmutableStatus(batch.status)) {
    return mockReject(409, '批次已完成或归档，不能再次上传', 'BATCH_IMMUTABLE')
  }
  if (batch.sourceType !== COLLECTION_SOURCE_TYPE.FILE) {
    return mockReject(400, '人工观测批次不支持文件上传', 'INVALID_PARAMETER')
  }
  const fileError = validateCsvFile(file)
  if (fileError) return fileError
  if (isMockScenario('error')) {
    return mockReject(500, '原件保存失败', 'FILE_STORAGE_FAILED', { traceId: mockTraceId('upload') })
  }

  const sha256 = mockHash(`${file.name}-${file.size}-${file.lastModified || 0}`)
  const existing = MOCK_IMPORTS.find((i) => i.batchId === batch.id && i.originalFile.sha256 === sha256)
  if (existing) {
    return Promise.resolve({
      ...importSummary(existing),
      batchVersion: batch.version,
      reusedExisting: true
    })
  }

  const id = nextImportId()
  const importItem = {
    id,
    batchId: batch.id,
    status: DATA_IMPORT_STATUS.UPLOADED,
    version: 1,
    importVersion: 1,
    originalFile: {
      fileName: file.name,
      size: Number(file.size) || 0,
      mimeType: file.type || 'text/csv',
      sha256,
      uploadedBy: currentUserId(),
      uploadedByName: currentUserName(),
      uploadTime: nowIso()
    },
    mapping: null,
    preview: buildFilePreview(buildCsvRows(50)),
    recordCount: 0,
    rejectedCount: 0,
    failureReason: null,
    createTime: nowIso(),
    updateTime: nowIso()
  }
  MOCK_IMPORTS.push(importItem)
  batch.status = COLLECTION_BATCH_STATUS.PROCESSING
  batch.sourceRevisionId = importItem.id
  batch.version += 1
  batch.updateTime = nowIso()
  return Promise.resolve({
    id: importItem.id,
    status: importItem.status,
    version: importItem.version,
    importVersion: importItem.importVersion,
    batchVersion: batch.version,
    originalFile: { ...importItem.originalFile }
  })
}

export function getDataImport(importId) {
  if (!getMockEnabled()) {
    return request.get(`/api/data-imports/${importId}`)
  }
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    if (isMockScenario('error')) return mockReject(500, '加载导入任务失败', 'INTERNAL_ERROR', { traceId: mockTraceId('import') })
    if (isMockScenario('permission')) return mockReject(404, '导入任务不存在或不可见', 'DATA_IMPORT_NOT_FOUND')
    const importItem = findMockImport(importId)
    if (!importItem) return mockReject(404, '导入任务不存在或不可见', 'DATA_IMPORT_NOT_FOUND')
    const batch = findMockBatch(importItem.batchId)
    if (!batchVisible(batch)) return mockReject(404, '导入任务不存在或不可见', 'DATA_IMPORT_NOT_FOUND')
    return importDetail(importItem)
  })()
}

export function getImportPreview(importId) {
  if (!getMockEnabled()) {
    return request.get(`/api/data-imports/${importId}/preview`)
  }
  const importItem = findMockImport(importId)
  if (!importItem) return mockReject(404, '导入任务不存在或不可见', 'DATA_IMPORT_NOT_FOUND')
  const batch = findMockBatch(importItem.batchId)
  if (!batchVisible(batch)) return mockReject(404, '导入任务不存在或不可见', 'DATA_IMPORT_NOT_FOUND')
  if (!importItem.preview) {
    return mockReject(409, '导入已失败，无可用预览', 'IMPORT_PROCESSING_FAILED')
  }
  return Promise.resolve({
    ...importItem.preview,
    importId: importItem.id,
    importVersion: importItem.importVersion
  })
}

export function saveImportMapping(importId, payload) {
  if (!getMockEnabled()) {
    return request.put(`/api/data-imports/${importId}/mapping`, payload)
  }
  const importItem = findMockImport(importId)
  if (!importItem) return mockReject(404, '导入任务不存在或不可见', 'DATA_IMPORT_NOT_FOUND')
  const batch = findMockBatch(importItem.batchId)
  if (!batchVisible(batch)) return mockReject(404, '导入任务不存在或不可见', 'DATA_IMPORT_NOT_FOUND')
  if (isBatchImmutableStatus(batch.status)) return mockReject(409, '批次已完成，映射不可修改', 'BATCH_IMMUTABLE')
  if (importItem.status === DATA_IMPORT_STATUS.CONFIRMED) {
    return mockReject(409, '导入修订已确认', 'IMPORT_ALREADY_CONFIRMED')
  }
  if (importItem.status === DATA_IMPORT_STATUS.FAILED) {
    return mockReject(400, '导入已失败，无法保存映射', 'IMPORT_MAPPING_INVALID')
  }
  const versionError = requireVersion(importItem, payload?.version)
  if (versionError) return versionError

  const mapping = payload?.mapping
  const plan = findMockPlan(batch.planId)
  if (!mapping || !mapping.timeMapping?.sourceColumn) {
    return mockReject(400, '必须映射时间列', 'IMPORT_MAPPING_INVALID')
  }
  if (!Array.isArray(mapping.columns) || mapping.columns.length === 0) {
    return mockReject(400, '至少映射一个指标列', 'IMPORT_MAPPING_INVALID')
  }
  const metricCodes = new Set(plan.metricDefinitions.map((m) => m.code))
  for (const column of mapping.columns) {
    if (!metricCodes.has(column.metricCode)) {
      return mockReject(400, `指标 ${column.metricCode} 不在计划快照中`, 'IMPORT_MAPPING_INVALID')
    }
    if (column.sourceUnit !== column.targetUnit) {
      return mockReject(400, `单位不一致且无白名单换算：${column.sourceUnit} → ${column.targetUnit}`, 'IMPORT_MAPPING_INVALID')
    }
  }
  importItem.importVersion += 1
  importItem.mapping = {
    version: importItem.version + 1,
    importVersion: importItem.importVersion,
    mapping: JSON.parse(JSON.stringify(mapping))
  }
  importItem.status = DATA_IMPORT_STATUS.MAPPING_REQUIRED
  importItem.version += 1
  importItem.updateTime = nowIso()
  return Promise.resolve({
    version: importItem.version,
    importVersion: importItem.importVersion,
    status: importItem.status,
    batchVersion: batch.version
  })
}

export function confirmImport(importId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/data-imports/${importId}/confirm`, payload, {
      headers: idempotencyHeaders(idempotencyKey)
    })
  }
  return withIdempotency(idempotencyKey, { importId, ...payload }, async () => {
    const importItem = findMockImport(importId)
    if (!importItem) return mockReject(404, '导入任务不存在或不可见', 'DATA_IMPORT_NOT_FOUND')
    const batch = findMockBatch(importItem.batchId)
    if (!batchVisible(batch)) return mockReject(404, '导入任务不存在或不可见', 'DATA_IMPORT_NOT_FOUND')
    if (!canOperateCollectionBatch(findMockPlan(batch.planId))) {
      return mockReject(403, '无导入确认权限', 'COLLECTION_OPERATION_DENIED')
    }
    if (isBatchImmutableStatus(batch.status)) return mockReject(409, '批次已完成，不能再次确认', 'BATCH_IMMUTABLE')
    if (isMockScenario('immutable')) return mockReject(409, '导入已确认，不能重复修改', 'IMPORT_ALREADY_CONFIRMED')
    const versionError = requireVersion(importItem, payload?.version)
    if (versionError) return versionError
    if (Number(payload?.importVersion) !== Number(importItem.importVersion)) {
      return mockReject(409, '数据已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: importItem.version })
    }
    const batchVersionError = requireVersion(batch, payload?.batchVersion)
    if (batchVersionError) return batchVersionError
    if (importItem.status === DATA_IMPORT_STATUS.CONFIRMED) {
      return mockReject(409, '导入修订已确认', 'IMPORT_ALREADY_CONFIRMED')
    }
    if (importItem.status !== DATA_IMPORT_STATUS.MAPPING_REQUIRED || !importItem.mapping) {
      return mockReject(400, '请先保存完整映射', 'IMPORT_MAPPING_INVALID')
    }

    const plan = findMockPlan(batch.planId)
    const rows = MOCK_CSV_ROWS.length
    const rejected = 2
    const baseId = Math.max(...MOCK_COLLECTION_RECORDS.map((r) => r.id)) + 1
    MOCK_CSV_ROWS.forEach((row, i) => {
      const rejectedRow = i === 11 || i === 63
      const metric = plan.metricDefinitions[0]
      MOCK_COLLECTION_RECORDS.push({
        id: baseId + i,
        batchId: batch.id,
        sourceType: COLLECTION_SOURCE_TYPE.FILE,
        metricCode: metric.code,
        valueType: metric.valueType,
        value: row.voltage === '' ? null : Number(row.voltage),
        unit: metric.unit,
        observedAt: row.observedAt.replace(' ', 'T') + '+08:00',
        recordedBy: currentUserId(),
        method: 'FILE_IMPORT',
        instrument: null,
        rawRowNo: i + 2,
        qualityStatus: rejectedRow ? QUALITY_STATUS.REJECTED : QUALITY_STATUS.GOOD,
        parserVersion: 'w43-csv-1',
        createTime: nowIso()
      })
    })

    importItem.status = DATA_IMPORT_STATUS.CONFIRMED
    importItem.recordCount = rows - rejected
    importItem.rejectedCount = rejected
    importItem.version += 1
    importItem.updateTime = nowIso()
    batch.status = COLLECTION_BATCH_STATUS.REVIEW_REQUIRED
    batch.recordCount = rows - rejected
    batch.rejectedCount = rejected
    batch.sourceRevisionId = importItem.id
    batch.version += 1
    batch.updateTime = nowIso()
    return {
      id: importItem.id,
      status: importItem.status,
      version: importItem.version,
      importVersion: importItem.importVersion,
      recordCount: importItem.recordCount,
      rejectedCount: importItem.rejectedCount,
      batchVersion: batch.version
    }
  })
}

// ========== API：人工观测 ==========

export function createManualRecord(batchId, payload) {
  if (!getMockEnabled()) {
    return request.post(`/api/collection-batches/${batchId}/manual-records`, payload)
  }
  const batch = findMockBatch(batchId)
  if (!batchVisible(batch)) return mockReject(404, '采集批次不存在或不可见', 'COLLECTION_BATCH_NOT_FOUND')
  if (!canOperateCollectionBatch(findMockPlan(batch.planId))) {
    return mockReject(403, '无人工观测录入权限', 'COLLECTION_OPERATION_DENIED')
  }
  if (isBatchImmutableStatus(batch.status)) {
    return mockReject(409, '批次已完成或归档，不能新增人工观测', 'BATCH_IMMUTABLE')
  }
  if (batch.sourceType !== COLLECTION_SOURCE_TYPE.MANUAL) {
    return mockReject(400, 'CSV 批次不支持人工观测', 'MANUAL_RECORD_INVALID')
  }
  const plan = findMockPlan(batch.planId)
  const metricDefinition = plan.metricDefinitions.find((m) => m.code === payload?.metricCode)
  if (!metricDefinition) return mockReject(400, '指标不在计划中', 'MANUAL_RECORD_INVALID')
  if (!Object.values(COLLECTION_VALUE_TYPE).includes(payload?.valueType)) {
    return mockReject(400, '值类型非法', 'MANUAL_RECORD_INVALID')
  }
  if (payload?.value === undefined || payload?.value === null || payload?.value === '') {
    return mockReject(400, '观测值不能为空', 'MANUAL_RECORD_INVALID')
  }
  if (payload.valueType === 'DECIMAL' && Number.isNaN(Number(payload.value))) {
    return mockReject(400, '观测值必须为小数', 'MANUAL_RECORD_INVALID')
  }
  if (payload.valueType === 'INTEGER' && !Number.isInteger(Number(payload.value))) {
    return mockReject(400, '观测值必须为整数', 'MANUAL_RECORD_INVALID')
  }
  if (!payload?.unit) return mockReject(400, '必须填写单位', 'MANUAL_RECORD_INVALID')
  if (!payload?.observedAt) return mockReject(400, '必须填写观测时间', 'MANUAL_RECORD_INVALID')
  if (!payload?.method) return mockReject(400, '必须填写采集方法', 'MANUAL_RECORD_INVALID')
  if (!payload?.instrument) return mockReject(400, '必须填写仪器', 'MANUAL_RECORD_INVALID')

  const id = nextManualId()
  const record = {
    id,
    batchId: batch.id,
    metricCode: payload.metricCode,
    valueType: payload.valueType,
    value: payload.valueType === 'STRING' ? String(payload.value) : Number(payload.value),
    unit: payload.unit,
    observedAt: payload.observedAt,
    recordedBy: currentUserId(),
    method: payload.method,
    instrument: payload.instrument,
    note: payload.note || null,
    version: 1,
    revisionNo: 1,
    previousRevisionId: null,
    createTime: nowIso(),
    updateTime: nowIso()
  }
  MOCK_MANUAL_RECORDS.push(record)
  const revisionId = nextManualRevisionId()
  MOCK_MANUAL_REVISIONS.push({
    id: revisionId,
    manualRecordId: record.id,
    revisionNo: 1,
    oldValue: null,
    newValue: record.value,
    changeReason: '初始录入',
    operatorId: currentUserId(),
    operatorName: currentUserName(),
    createTime: nowIso()
  })
  batch.status = COLLECTION_BATCH_STATUS.REVIEW_REQUIRED
  batch.recordCount += 1
  batch.version += 1
  batch.updateTime = nowIso()
  return Promise.resolve({
    id: record.id,
    version: record.version,
    revisionNo: record.revisionNo,
    batchVersion: batch.version
  })
}

export function updateManualRecord(manualId, payload) {
  if (!getMockEnabled()) {
    return request.put(`/api/manual-records/${manualId}`, payload)
  }
  const record = findMockManualRecord(manualId)
  if (!record) return mockReject(404, '人工观测不存在或不可见', 'MANUAL_RECORD_NOT_FOUND')
  const batch = findMockBatch(record.batchId)
  if (!batchVisible(batch)) return mockReject(404, '人工观测不存在或不可见', 'MANUAL_RECORD_NOT_FOUND')
  if (!canOperateCollectionBatch(findMockPlan(batch.planId))) {
    return mockReject(403, '无人工观测修改权限', 'COLLECTION_OPERATION_DENIED')
  }
  if (isBatchImmutableStatus(batch.status)) {
    return mockReject(409, '批次已完成或归档，不能修改人工观测', 'BATCH_IMMUTABLE')
  }
  const versionError = requireVersion(record, payload?.version)
  if (versionError) return versionError
  if (!payload?.changeReason || String(payload.changeReason).trim().length < 1) {
    return mockReject(400, '更正必须填写 changeReason', 'MANUAL_RECORD_INVALID')
  }
  if (payload?.value === undefined || payload?.value === null || payload?.value === '') {
    return mockReject(400, '观测值不能为空', 'MANUAL_RECORD_INVALID')
  }
  const previousRevisionId = MOCK_MANUAL_REVISIONS
    .filter((r) => r.manualRecordId === record.id)
    .sort((a, b) => b.revisionNo - a.revisionNo)[0]?.id || null
  const oldValue = record.value
  record.value = record.valueType === 'STRING' ? String(payload.value) : Number(payload.value)
  if (payload?.unit) record.unit = payload.unit
  if (payload?.observedAt) record.observedAt = payload.observedAt
  if (payload?.method) record.method = payload.method
  if (payload?.instrument) record.instrument = payload.instrument
  if (payload?.note !== undefined) record.note = payload.note || null
  record.revisionNo += 1
  record.previousRevisionId = previousRevisionId
  record.version += 1
  record.updateTime = nowIso()
  MOCK_MANUAL_REVISIONS.push({
    id: nextManualRevisionId(),
    manualRecordId: record.id,
    revisionNo: record.revisionNo,
    oldValue,
    newValue: record.value,
    changeReason: String(payload.changeReason).trim(),
    operatorId: currentUserId(),
    operatorName: currentUserName(),
    createTime: nowIso()
  })
  batch.version += 1
  batch.updateTime = nowIso()
  return Promise.resolve({
    id: record.id,
    version: record.version,
    revisionNo: record.revisionNo,
    batchVersion: batch.version,
    previousRevisionId
  })
}

export function listManualRevisions(manualId) {
  if (!getMockEnabled()) {
    return request.get(`/api/manual-records/${manualId}/revisions`)
  }
  const record = findMockManualRecord(manualId)
  if (!record) return mockReject(404, '人工观测不存在或不可见', 'MANUAL_RECORD_NOT_FOUND')
  const list = MOCK_MANUAL_REVISIONS
    .filter((r) => r.manualRecordId === record.id)
    .slice()
    .sort((a, b) => a.revisionNo - b.revisionNo)
  return Promise.resolve({ total: list.length, list })
}
