import request from '@/utils/request'
import { getMockEnabled } from '@/utils/mock'
import { userStore, currentRoles } from '@/store/user'
import { ROLE } from '@/utils/permission'
import { PROJECT_ROLE, findMockProject, registerMockProject } from '@/api/projects'
import { resolveMockTemplateVersion, validateDataAgainstSchema } from '@/api/experimentTemplates'
import { findMockSnapshot } from '@/api/collection'
import { idempotencyHeaders, requestDigest } from '@/utils/idempotency'
import {
  currentMockScenario,
  isMockScenario,
  mockDelay,
  mockReject,
  mockTraceId
} from '@/utils/mockScenario'

/**
 * 实验记录 / 内容修订 / 导师反馈 API（第 7 周 M02-W03）
 *
 * 契约来源：docs/contracts/EXPERIMENT_RECORD_ACQUISITION_API.md v1.0（FROZEN）
 *
 * 真实端点（§8.1）：
 *   POST   /api/experiment-records                                    创建记录与 revision 1
 *   GET    /api/experiment-records                                    分页查询可见记录
 *   GET    /api/experiment-records/{id}                               详情（含当前/发布修订）
 *   PUT    /api/experiment-records/{id}/revisions/{revisionId}        保存 EDITABLE 修订
 *   GET    /api/experiment-records/{id}/revisions                     修订历史分页
 *   GET    /api/experiment-records/{id}/revisions/{revisionId}        修订详情
 *   POST   /api/experiment-records/{id}/submit                        提交当前修订
 *   POST   /api/experiment-records/{id}/feedback                      COMMENT / CHANGE_REQUEST / APPROVAL
 *   GET    /api/experiment-records/{id}/feedback                      反馈分页
 *   POST   /api/experiment-records/{id}/publish                       发布已批准修订
 *   POST   /api/experiment-records/{id}/revisions                     从发布版创建新修订
 *   PUT    /api/experiment-records/{id}/collection-snapshot           绑定/解除快照
 *   POST   /api/experiment-records/{id}/project-draft                 转 PREPARING 项目
 *
 * 幂等与乐观锁（§8.4 / §8.5）：
 *   - 更新与状态动作一律携带 version；缺失 400 INVALID_PARAMETER，过期 409 VERSION_CONFLICT。
 *   - 创建、提交、反馈、发布、项目转换携带 Idempotency-Key。
 *   - 精确重放先命中已提交的幂等结果，再校验版本。
 *
 * mock 开关：VITE_USE_MOCK=true 走本地 mock（字段与契约一致），默认 false 走真实接口。
 * mock 成功 resolve data，失败 reject { code, msg, reason, data }，与 request.js 拦截器一致。
 *
 * 前端补充字段（契约未显式定义，仅用于 UI 收敛，需后端确认）：
 *   - 记录详情/列表返回 authorName/projectName/templateName/templateVersion 等展示名，
 *     避免页面为每行再发详情请求；真实模式下缺失时页面回退为编号。
 *   - 页面通过 canEditRecord/canReviewRecord 等工具判定按钮可见性，
 *     项目角色可由调用方传入（详情页来自项目详情接口），未传入时回退到 authorId 与全局角色。
 */

// ========== 枚举 ==========

export const RECORD_STATUS = Object.freeze({
  DRAFT: 'DRAFT',
  IN_REVIEW: 'IN_REVIEW',
  CHANGES_REQUESTED: 'CHANGES_REQUESTED',
  APPROVED: 'APPROVED',
  PUBLISHED: 'PUBLISHED',
  REVISING: 'REVISING'
})

export const RECORD_STATUS_META = Object.freeze({
  [RECORD_STATUS.DRAFT]: { label: '草稿', tagType: 'info', desc: '仅作者与导师可见' },
  [RECORD_STATUS.IN_REVIEW]: { label: '评审中', tagType: 'warning', desc: '等待导师反馈' },
  [RECORD_STATUS.CHANGES_REQUESTED]: { label: '待修改', tagType: 'danger', desc: '导师已要求修改' },
  [RECORD_STATUS.APPROVED]: { label: '已批准', tagType: 'primary', desc: '可发布' },
  [RECORD_STATUS.PUBLISHED]: { label: '已发布', tagType: 'success', desc: '内容不可变，可复现' },
  [RECORD_STATUS.REVISING]: { label: '修订中', tagType: 'primary', desc: '旧发布版仍有效' }
})

export const REVISION_STATUS = Object.freeze({
  EDITABLE: 'EDITABLE',
  SUBMITTED: 'SUBMITTED',
  CHANGES_REQUESTED: 'CHANGES_REQUESTED',
  APPROVED: 'APPROVED',
  PUBLISHED: 'PUBLISHED',
  SUPERSEDED: 'SUPERSEDED'
})

export const REVISION_STATUS_META = Object.freeze({
  [REVISION_STATUS.EDITABLE]: { label: '可编辑', tagType: 'info', immutable: false },
  [REVISION_STATUS.SUBMITTED]: { label: '已提交', tagType: 'warning', immutable: true },
  [REVISION_STATUS.CHANGES_REQUESTED]: { label: '待修改', tagType: 'danger', immutable: true },
  [REVISION_STATUS.APPROVED]: { label: '已批准', tagType: 'primary', immutable: true },
  [REVISION_STATUS.PUBLISHED]: { label: '已发布', tagType: 'success', immutable: true },
  [REVISION_STATUS.SUPERSEDED]: { label: '已被替代', tagType: 'info', immutable: true }
})

export const FEEDBACK_TYPE = Object.freeze({
  COMMENT: 'COMMENT',
  CHANGE_REQUEST: 'CHANGE_REQUEST',
  APPROVAL: 'APPROVAL'
})

export const FEEDBACK_TYPE_META = Object.freeze({
  [FEEDBACK_TYPE.COMMENT]: { label: '评论', tagType: 'info', action: false },
  [FEEDBACK_TYPE.CHANGE_REQUEST]: { label: '要求修改', tagType: 'danger', action: true },
  [FEEDBACK_TYPE.APPROVAL]: { label: '批准', tagType: 'success', action: true }
})

export const FEEDBACK_VISIBILITY = Object.freeze({
  AUTHOR_AND_REVIEWERS: 'AUTHOR_AND_REVIEWERS',
  PROJECT_MEMBERS: 'PROJECT_MEMBERS'
})

export const FEEDBACK_VISIBILITY_META = Object.freeze({
  [FEEDBACK_VISIBILITY.AUTHOR_AND_REVIEWERS]: { label: '作者与评审人' },
  [FEEDBACK_VISIBILITY.PROJECT_MEMBERS]: { label: '项目成员' }
})

/** 契约 §4.1 记录状态机 */
export const RECORD_TRANSITIONS = Object.freeze({
  [RECORD_STATUS.DRAFT]: [RECORD_STATUS.IN_REVIEW],
  [RECORD_STATUS.IN_REVIEW]: [RECORD_STATUS.CHANGES_REQUESTED, RECORD_STATUS.APPROVED],
  [RECORD_STATUS.CHANGES_REQUESTED]: [RECORD_STATUS.IN_REVIEW],
  [RECORD_STATUS.APPROVED]: [RECORD_STATUS.PUBLISHED],
  [RECORD_STATUS.PUBLISHED]: [RECORD_STATUS.REVISING],
  [RECORD_STATUS.REVISING]: [RECORD_STATUS.IN_REVIEW]
})

/** 契约 §2 D1 内容修订状态机 */
export const REVISION_TRANSITIONS = Object.freeze({
  [REVISION_STATUS.EDITABLE]: [REVISION_STATUS.SUBMITTED],
  [REVISION_STATUS.SUBMITTED]: [REVISION_STATUS.CHANGES_REQUESTED, REVISION_STATUS.APPROVED],
  [REVISION_STATUS.CHANGES_REQUESTED]: [],
  [REVISION_STATUS.APPROVED]: [REVISION_STATUS.PUBLISHED],
  [REVISION_STATUS.PUBLISHED]: [REVISION_STATUS.SUPERSEDED],
  [REVISION_STATUS.SUPERSEDED]: []
})

export function canTransitionRecord(from, to) {
  return (RECORD_TRANSITIONS[from] || []).includes(to)
}

export function canTransitionRevision(from, to) {
  return (REVISION_TRANSITIONS[from] || []).includes(to)
}

export function recordStatusMeta(status) {
  return RECORD_STATUS_META[status] || { label: status || '--', tagType: 'info', desc: '' }
}

export function revisionStatusMeta(status) {
  return REVISION_STATUS_META[status] || { label: status || '--', tagType: 'info', immutable: true }
}

export function feedbackTypeMeta(type) {
  return FEEDBACK_TYPE_META[type] || { label: type || '--', tagType: 'info', action: false }
}

export function feedbackVisibilityMeta(visibility) {
  return FEEDBACK_VISIBILITY_META[visibility] || { label: visibility || '--' }
}

/** 记录聚合已发布（内容只读，需创建新修订） */
export function isRecordPublished(record) {
  return record?.status === RECORD_STATUS.PUBLISHED
}

/** 修订是否可写（仅 EDITABLE） */
export function isRevisionEditable(revision) {
  return revision?.status === REVISION_STATUS.EDITABLE
}

/** 记录是否已有进行中的编辑修订（避免重复创建） */
export function hasEditableRevision(record) {
  return isRevisionEditable(record?.currentRevision)
}

// ========== 权限判定（前端 UI 收敛，真正鉴权以后端为准） ==========

const GLOBAL_REVIEW_ROLES = [ROLE.SYSTEM_ADMIN, ROLE.TEACHER]
const PROJECT_MANAGE_ROLES = [PROJECT_ROLE.OWNER, PROJECT_ROLE.MAINTAINER]

export function canAccessExperiments() {
  return currentRoles().length > 0
}

export function canReviewGlobally() {
  return currentRoles().some((role) => GLOBAL_REVIEW_ROLES.includes(role))
}

/** 当前用户是否项目 OWNER/MAINTAINER；projectRole 可由页面传入真实项目角色 */
export function isProjectManager(projectId, projectRole) {
  const role = projectRole || findMockProject(projectId)?.myRole || null
  return PROJECT_MANAGE_ROLES.includes(role)
}

export function canCreateExperimentRecord(projectId, projectRole) {
  if (!canAccessExperiments()) return false
  if (canReviewGlobally()) return true
  if (projectId == null) return true
  const role = projectRole || findMockProject(projectId)?.myRole || null
  return role !== PROJECT_ROLE.OBSERVER
}

export function canEditRecord(record, projectRole) {
  if (!record || !canAccessExperiments()) return false
  if (canReviewGlobally()) return true
  if (Number(record.authorId) === currentUserId()) return true
  return isProjectManager(record.projectId, projectRole)
}

export function canReviewRecord(record, projectRole) {
  if (!record) return false
  // 评审人不能与作者相同：即使具备 TEACHER/ADMIN/OWNER 身份也不得自我批准（§3.3）
  if (Number(record.authorId) === currentUserId()) return false
  return canReviewGlobally() || isProjectManager(record.projectId, projectRole)
}

export function canPublishRecord(record, projectRole) {
  return canReviewRecord(record, projectRole)
}

export function canConvertRecord(record, projectRole) {
  if (!record || record.status !== RECORD_STATUS.PUBLISHED) return false
  if (record.convertedProjectId) return false
  if (canReviewGlobally()) return true
  if (Number(record.authorId) === currentUserId()) return true
  return isProjectManager(record.projectId, projectRole)
}

// ========== Mock 数据 ==========

const MOCK_CURRENT_USER_ID = 2
const MOCK_USER_NAMES = { 1: '系统管理员', 2: '张同学', 3: '李同学' }
const MOCK_LEARNING_UNIT_NAMES = { 104: '电路基础（路线 1）', 105: '万用表使用（路线 1）' }

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

function templateInfo(versionId) {
  const found = resolveMockTemplateVersion(versionId)
  if (!found) return { templateId: null, templateName: null, templateVersion: null, schemaJson: null, scope: null, projectId: null }
  return {
    templateId: found.template.id,
    templateName: found.template.title,
    templateVersion: found.version.version,
    schemaJson: found.version.schemaJson,
    scope: found.template.scope,
    projectId: found.template.projectId
  }
}

function projectNameOf(projectId) {
  return findMockProject(projectId)?.name || null
}

function revisionNoOf(revision) {
  return revision?.revisionNo ?? null
}

// ---- 记录与修订种子数据 ----

const MOCK_RECORDS = [
  {
    id: 301,
    recordCode: 'ER-202610-000301',
    title: '直流电压稳定性实验',
    summary: '验证 5V 输出在负载变化下的稳定性，记录空载与带载读数。',
    authorId: 2,
    projectId: 10,
    learningUnitId: null,
    templateId: 1,
    templateVersionId: 2,
    status: RECORD_STATUS.DRAFT,
    currentRevisionId: 901,
    publishedRevisionId: null,
    convertedProjectId: null,
    version: 1,
    createTime: '2026-10-18T09:20:00',
    updateTime: '2026-10-18T09:20:00'
  },
  {
    id: 302,
    recordCode: 'ER-202610-000302',
    title: '温湿度传感器标定记录',
    summary: '在 2 个标定点下记录温湿度读数，等待导师评审。',
    authorId: 2,
    projectId: 10,
    learningUnitId: null,
    templateId: 2,
    templateVersionId: 22,
    status: RECORD_STATUS.IN_REVIEW,
    currentRevisionId: 902,
    publishedRevisionId: null,
    convertedProjectId: null,
    version: 3,
    createTime: '2026-10-17T14:00:00',
    updateTime: '2026-10-19T10:30:00'
  },
  {
    id: 303,
    recordCode: 'ER-202610-000303',
    title: '电路串并联验证实验',
    summary: '验证串并联电路的电压分配，导师要求补充第二次测量。',
    authorId: 3,
    projectId: 10,
    learningUnitId: null,
    templateId: 1,
    templateVersionId: 2,
    status: RECORD_STATUS.CHANGES_REQUESTED,
    currentRevisionId: 904,
    publishedRevisionId: null,
    convertedProjectId: null,
    version: 5,
    createTime: '2026-10-16T10:10:00',
    updateTime: '2026-10-19T16:40:00'
  },
  {
    id: 304,
    recordCode: 'ER-202610-000304',
    title: '气象站环境参数采集',
    summary: '记录温度、湿度、气压与光照，已通过导师批准，待发布。',
    authorId: 2,
    projectId: 10,
    learningUnitId: null,
    templateId: 5,
    templateVersionId: 51,
    status: RECORD_STATUS.APPROVED,
    currentRevisionId: 905,
    publishedRevisionId: null,
    convertedProjectId: null,
    version: 6,
    createTime: '2026-10-15T09:00:00',
    updateTime: '2026-10-20T15:10:00'
  },
  {
    id: 305,
    recordCode: 'ER-202610-000305',
    title: 'PM-001 功率读数校准',
    summary: '以标准功率源校准 PM-001 读数，发布版可复现。',
    authorId: 2,
    projectId: 5,
    learningUnitId: null,
    templateId: 4,
    templateVersionId: 42,
    status: RECORD_STATUS.PUBLISHED,
    currentRevisionId: 906,
    publishedRevisionId: 906,
    convertedProjectId: null,
    version: 7,
    createTime: '2026-10-12T10:30:00',
    updateTime: '2026-10-19T11:00:00'
  },
  {
    id: 306,
    recordCode: 'ER-202610-000306',
    title: '5V 稳压模块改进结论',
    summary: '已发布并转为 PREPARING 项目草案，来源关系可追溯。',
    authorId: 2,
    projectId: 10,
    learningUnitId: null,
    templateId: 2,
    templateVersionId: 22,
    status: RECORD_STATUS.PUBLISHED,
    currentRevisionId: 907,
    publishedRevisionId: 907,
    convertedProjectId: 11,
    version: 9,
    createTime: '2026-10-11T13:20:00',
    updateTime: '2026-10-20T17:30:00'
  },
  {
    id: 307,
    recordCode: 'ER-202610-000307',
    title: '电压基准复测（修订中）',
    summary: '旧发布版仍有效，新修订补充第三次测量。',
    authorId: 2,
    projectId: 10,
    learningUnitId: null,
    templateId: 1,
    templateVersionId: 2,
    status: RECORD_STATUS.REVISING,
    currentRevisionId: 909,
    publishedRevisionId: 908,
    convertedProjectId: null,
    version: 10,
    createTime: '2026-10-10T09:00:00',
    updateTime: '2026-10-21T09:15:00'
  },
  {
    id: 308,
    recordCode: 'ER-202610-000308',
    title: '学习实验：电路基础验证',
    summary: '纯学习记录，关联学习路线单元，无项目归属。',
    authorId: 2,
    projectId: null,
    learningUnitId: 104,
    templateId: 1,
    templateVersionId: 2,
    status: RECORD_STATUS.DRAFT,
    currentRevisionId: 910,
    publishedRevisionId: null,
    convertedProjectId: null,
    version: 1,
    createTime: '2026-10-21T19:00:00',
    updateTime: '2026-10-21T19:00:00'
  }
]

function makeRevision(record, overrides) {
  return {
    id: overrides.id,
    recordId: record.id,
    revisionNo: overrides.revisionNo,
    status: overrides.status,
    contentJson: overrides.contentJson || {},
    purpose: overrides.purpose ?? null,
    environment: overrides.environment ?? null,
    steps: overrides.steps ?? null,
    result: overrides.result ?? null,
    anomaly: overrides.anomaly ?? null,
    conclusion: overrides.conclusion ?? null,
    hardwareVersionRef: overrides.hardwareVersionRef ?? null,
    deviceInstanceRef: overrides.deviceInstanceRef ?? null,
    firmwareVersion: overrides.firmwareVersion ?? null,
    bomVersionRef: overrides.bomVersionRef ?? null,
    collectionSnapshotId: overrides.collectionSnapshotId ?? null,
    changeSummary: overrides.changeSummary ?? null,
    attachmentRefs: [],
    createdBy: overrides.createdBy ?? record.authorId,
    createTime: overrides.createTime || record.createTime,
    submittedBy: overrides.submittedBy ?? null,
    submittedAt: overrides.submittedAt ?? null,
    approvedBy: overrides.approvedBy ?? null,
    approvedAt: overrides.approvedAt ?? null,
    publishedBy: overrides.publishedBy ?? null,
    publishedAt: overrides.publishedAt ?? null,
    version: overrides.version ?? 1
  }
}

const record301 = MOCK_RECORDS[0]
const record302 = MOCK_RECORDS[1]
const record303 = MOCK_RECORDS[2]
const record304 = MOCK_RECORDS[3]
const record305 = MOCK_RECORDS[4]
const record306 = MOCK_RECORDS[5]
const record307 = MOCK_RECORDS[6]
const record308 = MOCK_RECORDS[7]

const MOCK_REVISIONS = [
  makeRevision(record301, {
    id: 901,
    revisionNo: 1,
    status: REVISION_STATUS.EDITABLE,
    contentJson: { voltage: 5.12, test_type: '直流', is_pass: true, remarks: '空载与带载各测 3 次' },
    purpose: '验证供电稳定性',
    environment: '室温 24℃，负载 10Ω',
    steps: '连接负载并记录读数',
    result: '波动小于 1%',
    anomaly: null,
    conclusion: '通过',
    hardwareVersionRef: 'HW-PSU-02',
    deviceInstanceRef: 'LAB-A-017',
    firmwareVersion: '1.2.0',
    bomVersionRef: 'BOM-2026-10',
    version: 2
  }),
  makeRevision(record302, {
    id: 902,
    revisionNo: 2,
    status: REVISION_STATUS.SUBMITTED,
    contentJson: { temperature: 24.6, humidity: 55.2, calibration_point: 2 },
    purpose: '标定温湿度传感器',
    environment: '恒温箱 25℃，湿度 55%',
    steps: '分别在 0 点与 2 点读取温湿度',
    result: '偏差在允许范围内',
    anomaly: null,
    conclusion: '标定有效',
    collectionSnapshotId: 501,
    changeSummary: '补充第 2 个标定点',
    submittedBy: 2,
    submittedAt: '2026-10-19T10:30:00',
    version: 2
  }),
  makeRevision(record303, {
    id: 903,
    revisionNo: 1,
    status: REVISION_STATUS.CHANGES_REQUESTED,
    contentJson: { voltage: 5.08, test_type: '直流', is_pass: true, remarks: '仅完成第一次测量' },
    purpose: '验证串并联电压分配',
    environment: '实验台 3，室温 23℃',
    steps: '搭建串联与并联电路并读数',
    result: '串联分压符合理论值',
    anomaly: null,
    conclusion: '初步通过',
    submittedBy: 3,
    submittedAt: '2026-10-17T15:00:00',
    version: 3
  }),
  makeRevision(record303, {
    id: 904,
    revisionNo: 2,
    status: REVISION_STATUS.EDITABLE,
    contentJson: { voltage: 5.1, test_type: '直流', is_pass: true, remarks: '已补充第二次测量' },
    purpose: '验证串并联电压分配',
    environment: '实验台 3，室温 23℃',
    steps: '搭建串联与并联电路，重复测量两次',
    result: '两次测量偏差 0.4%',
    anomaly: '第二次测量时电源轻微波动',
    conclusion: '通过',
    changeSummary: '按导师意见补充第二次测量',
    version: 1
  }),
  makeRevision(record304, {
    id: 905,
    revisionNo: 1,
    status: REVISION_STATUS.APPROVED,
    contentJson: { temperature: 23.8, humidity: 61.4, pressure: 1012.5, light: 320 },
    purpose: '采集校园环境参数',
    environment: '气象站观测点，室外',
    steps: '设备上电后连续采集 10 分钟',
    result: '数据稳定，无缺测',
    anomaly: null,
    conclusion: '可用于环境趋势分析',
    submittedBy: 2,
    submittedAt: '2026-10-20T09:00:00',
    approvedBy: 1,
    approvedAt: '2026-10-20T15:10:00',
    version: 4
  }),
  makeRevision(record305, {
    id: 906,
    revisionNo: 1,
    status: REVISION_STATUS.PUBLISHED,
    contentJson: { power: 48.6, duration: 600, mode: '连续' },
    purpose: '校准 PM-001 功率读数',
    environment: '标准功率源，室温 24℃',
    steps: '以 50W 标准值对比 PM-001 读数',
    result: '偏差 1.4%',
    anomaly: null,
    conclusion: '校准系数有效',
    submittedBy: 2,
    submittedAt: '2026-10-18T10:00:00',
    approvedBy: 1,
    approvedAt: '2026-10-19T09:00:00',
    publishedBy: 1,
    publishedAt: '2026-10-19T11:00:00',
    version: 3
  }),
  makeRevision(record306, {
    id: 907,
    revisionNo: 3,
    status: REVISION_STATUS.PUBLISHED,
    contentJson: { temperature: 25.1, humidity: 58.7, calibration_point: 3 },
    purpose: '确认稳压模块温度漂移',
    environment: '恒温箱 25℃，负载 1A',
    steps: '连续运行 2 小时并记录温度',
    result: '温升 4.2℃，输出稳定',
    anomaly: null,
    conclusion: '可作为改进项目输入',
    collectionSnapshotId: 502,
    changeSummary: '补充 2 小时连续运行数据',
    submittedBy: 2,
    submittedAt: '2026-10-19T16:00:00',
    approvedBy: 1,
    approvedAt: '2026-10-20T10:00:00',
    publishedBy: 1,
    publishedAt: '2026-10-20T17:30:00',
    version: 5
  }),
  makeRevision(record307, {
    id: 908,
    revisionNo: 1,
    status: REVISION_STATUS.PUBLISHED,
    contentJson: { voltage: 5.02, test_type: '直流', is_pass: true, remarks: '第一次与第二次测量' },
    purpose: '建立电压基准',
    environment: '实验台 1，室温 24℃',
    steps: '空载、半载、满载各测一次',
    result: '满载偏差 0.8%',
    anomaly: null,
    conclusion: '基准有效',
    collectionSnapshotId: 502,
    submittedBy: 2,
    submittedAt: '2026-10-11T10:00:00',
    approvedBy: 1,
    approvedAt: '2026-10-12T09:00:00',
    publishedBy: 1,
    publishedAt: '2026-10-12T10:00:00',
    version: 4
  }),
  makeRevision(record307, {
    id: 909,
    revisionNo: 2,
    status: REVISION_STATUS.EDITABLE,
    contentJson: { voltage: 5.05, test_type: '直流', is_pass: true, remarks: '补充第三次测量' },
    purpose: '建立电压基准',
    environment: '实验台 1，室温 25℃',
    steps: '空载、半载、满载各测一次，追加第三次满载',
    result: '满载偏差 0.6%',
    anomaly: null,
    conclusion: '基准有效，稳定性提升',
    changeSummary: '补充第三次满载测量',
    version: 1
  }),
  makeRevision(record308, {
    id: 910,
    revisionNo: 1,
    status: REVISION_STATUS.EDITABLE,
    contentJson: { voltage: 4.98, test_type: '直流', is_pass: true, remarks: '学习单元练习' },
    purpose: '完成电路基础单元实验',
    environment: '学习实验台，室温 23℃',
    steps: '按单元指引测量分压',
    result: '读数与理论值一致',
    anomaly: null,
    conclusion: '掌握分压测量',
    version: 1
  })
]

const MOCK_FEEDBACK = [
  {
    id: 1101,
    recordId: 302,
    revisionId: 902,
    type: FEEDBACK_TYPE.COMMENT,
    content: '请在结论中说明标定有效期。',
    visibility: FEEDBACK_VISIBILITY.AUTHOR_AND_REVIEWERS,
    reviewerId: 1,
    reviewerName: '系统管理员',
    createTime: '2026-10-19T09:40:00',
    requestId: 'REQ-W43-1101'
  },
  {
    id: 1102,
    recordId: 303,
    revisionId: 903,
    type: FEEDBACK_TYPE.CHANGE_REQUEST,
    content: '请补充第二次测量，并说明电源波动对结果的影响。',
    visibility: FEEDBACK_VISIBILITY.AUTHOR_AND_REVIEWERS,
    reviewerId: 1,
    reviewerName: '系统管理员',
    createTime: '2026-10-19T16:40:00',
    requestId: 'REQ-W43-1102'
  },
  {
    id: 1103,
    recordId: 304,
    revisionId: 905,
    type: FEEDBACK_TYPE.APPROVAL,
    content: '复审通过，可按固定模板版本发布。',
    visibility: FEEDBACK_VISIBILITY.AUTHOR_AND_REVIEWERS,
    reviewerId: 1,
    reviewerName: '系统管理员',
    createTime: '2026-10-20T15:10:00',
    requestId: 'REQ-W43-1103'
  },
  {
    id: 1104,
    recordId: 305,
    revisionId: 906,
    type: FEEDBACK_TYPE.APPROVAL,
    content: '校准数据可复现，批准发布。',
    visibility: FEEDBACK_VISIBILITY.PROJECT_MEMBERS,
    reviewerId: 1,
    reviewerName: '系统管理员',
    createTime: '2026-10-19T09:00:00',
    requestId: 'REQ-W43-1104'
  },
  {
    id: 1105,
    recordId: 306,
    revisionId: 907,
    type: FEEDBACK_TYPE.APPROVAL,
    content: '同意发布并转项目草案。',
    visibility: FEEDBACK_VISIBILITY.AUTHOR_AND_REVIEWERS,
    reviewerId: 1,
    reviewerName: '系统管理员',
    createTime: '2026-10-20T10:00:00',
    requestId: 'REQ-W43-1105'
  },
  {
    id: 1106,
    recordId: 307,
    revisionId: 908,
    type: FEEDBACK_TYPE.COMMENT,
    content: '第三次测量如完成，可发起新修订。',
    visibility: FEEDBACK_VISIBILITY.AUTHOR_AND_REVIEWERS,
    reviewerId: 1,
    reviewerName: '系统管理员',
    createTime: '2026-10-20T18:00:00',
    requestId: 'REQ-W43-1106'
  }
]

// ---- mock 辅助 ----

function findMockRecord(id) {
  return MOCK_RECORDS.find((r) => r.id === Number(id)) || null
}

function revisionsOf(recordId) {
  return MOCK_REVISIONS.filter((r) => r.recordId === Number(recordId))
}

function findMockRevision(recordId, revisionId) {
  return MOCK_REVISIONS.find((r) => r.id === Number(revisionId) && r.recordId === Number(recordId)) || null
}

function feedbackOf(recordId) {
  return MOCK_FEEDBACK.filter((f) => f.recordId === Number(recordId))
}

function recordVisible(record) {
  if (!record) return false
  if (canReviewGlobally()) return true
  if (Number(record.authorId) === currentUserId()) return true
  if (record.projectId != null) {
    const project = findMockProject(record.projectId)
    return Boolean(project) && project.myRole !== null
  }
  return false
}

function recordSummary(record) {
  const current = MOCK_REVISIONS.find((r) => r.id === record.currentRevisionId) || null
  const info = templateInfo(record.templateVersionId)
  return {
    id: record.id,
    recordCode: record.recordCode,
    title: record.title,
    summary: record.summary,
    authorId: record.authorId,
    authorName: userName(record.authorId),
    projectId: record.projectId,
    projectName: record.projectId ? projectNameOf(record.projectId) : null,
    learningUnitId: record.learningUnitId,
    learningUnitName: record.learningUnitId ? MOCK_LEARNING_UNIT_NAMES[record.learningUnitId] || `单元 ${record.learningUnitId}` : null,
    templateId: record.templateId,
    templateName: info.templateName,
    templateVersionId: record.templateVersionId,
    templateVersion: info.templateVersion,
    status: record.status,
    currentRevisionId: record.currentRevisionId,
    currentRevisionNo: current?.revisionNo ?? null,
    currentRevisionStatus: current?.status ?? null,
    publishedRevisionId: record.publishedRevisionId,
    convertedProjectId: record.convertedProjectId,
    version: record.version,
    createTime: record.createTime,
    updateTime: record.updateTime
  }
}

function revisionDetail(revision) {
  return {
    ...revision,
    recordId: revision.recordId,
    revisionNo: revision.revisionNo,
    createdByName: userName(revision.createdBy),
    submittedByName: revision.submittedBy ? userName(revision.submittedBy) : null,
    approvedByName: revision.approvedBy ? userName(revision.approvedBy) : null,
    publishedByName: revision.publishedBy ? userName(revision.publishedBy) : null,
    attachmentRefs: [...(revision.attachmentRefs || [])]
  }
}

function snapshotSummary(snapshotId) {
  if (!snapshotId) return null
  const snapshot = findMockSnapshot(snapshotId)
  if (!snapshot) return null
  return {
    id: snapshot.id,
    snapshotCode: snapshot.snapshotCode,
    batchId: snapshot.batchId,
    batchCode: snapshot.batchCode,
    snapshotType: snapshot.snapshotType,
    recordCount: snapshot.recordCount,
    rejectedCount: snapshot.rejectedCount,
    observedFrom: snapshot.observedFrom,
    observedTo: snapshot.observedTo,
    batchChecksum: snapshot.batchChecksum,
    mappingDigest: snapshot.mappingDigest,
    createTime: snapshot.createTime
  }
}

function recordDetail(record) {
  const summary = recordSummary(record)
  const current = MOCK_REVISIONS.find((r) => r.id === record.currentRevisionId) || null
  const published = record.publishedRevisionId
    ? MOCK_REVISIONS.find((r) => r.id === record.publishedRevisionId) || null
    : null
  const info = templateInfo(record.templateVersionId)
  return {
    ...summary,
    schemaJson: info.schemaJson,
    currentRevision: current ? revisionDetail(current) : null,
    publishedRevision: published ? revisionDetail(published) : null,
    collectionSnapshot: current ? snapshotSummary(current.collectionSnapshotId) : null,
    feedbackCount: feedbackOf(record.id).length,
    revisionCount: revisionsOf(record.id).length
  }
}

function findMockRecordOrReject(id) {
  const record = findMockRecord(id)
  if (!record || !recordVisible(record)) {
    return { error: mockReject(404, '实验记录不存在或不可见', 'EXPERIMENT_RECORD_NOT_FOUND') }
  }
  return { record }
}

function requireVersion(resource, version) {
  if (version == null) return mockReject(400, '缺少 version 参数', 'INVALID_PARAMETER')
  if (Number(version) !== Number(resource.version)) {
    return mockReject(409, '数据已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: resource.version })
  }
  return null
}

/** mock 幂等：同 key + 同摘要返回原结果；同 key 不同摘要 409 IDEMPOTENCY_KEY_REUSED */
const MOCK_IDEMPOTENCY = new Map()

function withIdempotency(key, payload, handler) {
  if (!key) return mockReject(400, '缺少 Idempotency-Key', 'INVALID_PARAMETER')
  const digest = requestDigest(payload)
  const hit = MOCK_IDEMPOTENCY.get(key)
  if (hit) {
    if (hit.digest !== digest) {
      return mockReject(409, '幂等键已用于其他请求', 'IDEMPOTENCY_KEY_REUSED')
    }
    return Promise.resolve({ ...hit.result, idempotentReplay: true })
  }
  return Promise.resolve()
    .then(handler)
    .then((result) => {
      MOCK_IDEMPOTENCY.set(key, { digest, result })
      return result
    })
}

function validateContent(record, contentJson) {
  const info = templateInfo(record.templateVersionId)
  const res = validateDataAgainstSchema(info.schemaJson || {}, contentJson || {})
  if (!res.valid) {
    return mockReject(400, '实验内容不符合模板版本', 'EXPERIMENT_CONTENT_INVALID', {
      errors: res.errors,
      traceId: mockTraceId('experiment')
    })
  }
  return null
}

function nextRecordId() {
  return Math.max(...MOCK_RECORDS.map((r) => r.id)) + 1
}

function nextRevisionId() {
  return Math.max(...MOCK_REVISIONS.map((r) => r.id)) + 1
}

function nextFeedbackId() {
  return Math.max(...MOCK_FEEDBACK.map((f) => f.id)) + 1
}

function recordCodeFor(id) {
  return `ER-202610-${String(id).padStart(6, '0')}`
}

// ========== API：实验记录 ==========

export function createExperimentRecord(data, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post('/api/experiment-records', data, { headers: idempotencyHeaders(idempotencyKey) })
  }
  if (!canAccessExperiments()) {
    return mockReject(403, '当前账号无权创建实验记录', 'ACCESS_DENIED')
  }
  return withIdempotency(idempotencyKey, data, async () => {
    if (isMockScenario('loading')) await mockDelay()
    if (isMockScenario('permission')) {
      return mockReject(403, '无实验记录创建权限', 'EXPERIMENT_OPERATION_DENIED')
    }
    const title = String(data?.title || '').trim()
    if (title.length < 2 || title.length > 120) {
      return mockReject(400, '实验标题需为 2-120 字符', 'INVALID_PARAMETER')
    }
    if (data?.summary && String(data.summary).length > 500) {
      return mockReject(400, '实验摘要最多 500 字符', 'INVALID_PARAMETER')
    }
    const projectId = data?.projectId == null ? null : Number(data.projectId)
    const learningUnitId = data?.learningUnitId == null ? null : Number(data.learningUnitId)
    if (projectId == null && learningUnitId == null) {
      return mockReject(400, '项目实验与学习实验至少指定一个归属', 'INVALID_PARAMETER')
    }
    if (projectId != null && !findMockProject(projectId)) {
      return mockReject(404, '项目不存在或不可见', 'PROJECT_NOT_FOUND')
    }
    const info = templateInfo(data?.templateVersionId)
    if (!info.templateId) {
      return mockReject(404, '模板版本不存在或不可见', 'TEMPLATE_VERSION_NOT_FOUND')
    }
    const version = resolveMockTemplateVersion(data.templateVersionId)?.version
    if (version?.status !== 'PUBLISHED') {
      return mockReject(409, '只能引用已发布的模板版本', 'TEMPLATE_VERSION_MISMATCH')
    }
    if (info.scope === 'PROJECT' && info.projectId !== projectId) {
      return mockReject(409, '项目模板不能用于其他项目或纯学习记录', 'TEMPLATE_VERSION_MISMATCH')
    }
    if (learningUnitId != null && info.scope === 'PROJECT' && info.projectId !== projectId) {
      return mockReject(409, '学习单元与模板版本不一致', 'TEMPLATE_VERSION_MISMATCH')
    }
    if (!canCreateExperimentRecord(projectId)) {
      return mockReject(403, '无实验记录创建权限', 'EXPERIMENT_OPERATION_DENIED')
    }

    const contentError = validateContent({ templateVersionId: data.templateVersionId }, data?.contentJson)
    if (contentError) return contentError

    const id = nextRecordId()
    const revisionId = nextRevisionId()
    const record = {
      id,
      recordCode: recordCodeFor(id),
      title,
      summary: data?.summary || null,
      authorId: currentUserId(),
      projectId,
      learningUnitId,
      templateId: info.templateId,
      templateVersionId: Number(data.templateVersionId),
      status: RECORD_STATUS.DRAFT,
      currentRevisionId: revisionId,
      publishedRevisionId: null,
      convertedProjectId: null,
      version: 1,
      createTime: nowIso(),
      updateTime: nowIso()
    }
    MOCK_RECORDS.unshift(record)
    MOCK_REVISIONS.push(
      makeRevision(record, {
        id: revisionId,
        revisionNo: 1,
        status: REVISION_STATUS.EDITABLE,
        contentJson: data?.contentJson || {},
        purpose: data?.purpose,
        environment: data?.environment,
        steps: data?.steps,
        result: data?.result,
        anomaly: data?.anomaly,
        conclusion: data?.conclusion,
        hardwareVersionRef: data?.hardwareVersionRef,
        deviceInstanceRef: data?.deviceInstanceRef,
        firmwareVersion: data?.firmwareVersion,
        bomVersionRef: data?.bomVersionRef,
        version: 1
      })
    )
    return {
      id: record.id,
      recordCode: record.recordCode,
      status: record.status,
      templateVersionId: record.templateVersionId,
      currentRevision: {
        id: revisionId,
        revisionNo: 1,
        status: REVISION_STATUS.EDITABLE,
        version: 1
      },
      publishedRevisionId: null,
      version: record.version
    }
  })
}

export function listExperimentRecords(params = {}) {
  if (!getMockEnabled()) {
    return request.get(`/api/experiment-records`, { params })
  }
  if (!canAccessExperiments()) {
    return mockReject(403, '当前账号无权访问实验记录', 'ACCESS_DENIED')
  }
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    if (isMockScenario('empty')) return { total: 0, page: 1, pageSize: 20, list: [] }
    if (isMockScenario('permission')) {
      return mockReject(403, '当前账号无权查看实验记录', 'EXPERIMENT_OPERATION_DENIED')
    }
    if (isMockScenario('error')) {
      return mockReject(500, '加载实验记录失败', 'INTERNAL_ERROR', { traceId: mockTraceId('records') })
    }

    let list = MOCK_RECORDS.filter(recordVisible)
    if (params.scope === 'MINE') list = list.filter((r) => Number(r.authorId) === currentUserId())
    if (params.status) list = list.filter((r) => r.status === params.status)
    if (params.projectId) list = list.filter((r) => r.projectId === Number(params.projectId))
    if (params.templateId) list = list.filter((r) => r.templateId === Number(params.templateId))
    if (params.authorId) list = list.filter((r) => r.authorId === Number(params.authorId))
    if (params.keyword) {
      const k = String(params.keyword).toLowerCase()
      list = list.filter((r) => `${r.title}${r.summary || ''}${r.recordCode}`.toLowerCase().includes(k))
    }
    list = list
      .slice()
      .sort((a, b) => Date.parse(b.updateTime) - Date.parse(a.updateTime) || b.id - a.id)
      .map(recordSummary)

    const page = Number(params.page) > 0 ? Number(params.page) : 1
    const pageSize = Number(params.pageSize) > 0 ? Math.min(Number(params.pageSize), 100) : 20
    const start = (page - 1) * pageSize
    return { total: list.length, page, pageSize, list: list.slice(start, start + pageSize) }
  })()
}

export function getExperimentRecord(recordId) {
  if (!getMockEnabled()) {
    return request.get(`/api/experiment-records/${recordId}`)
  }
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    if (isMockScenario('error')) {
      return mockReject(500, '加载实验记录失败', 'INTERNAL_ERROR', { traceId: mockTraceId('record') })
    }
    if (isMockScenario('permission')) {
      return mockReject(404, '实验记录不存在或不可见', 'EXPERIMENT_RECORD_NOT_FOUND')
    }
    const found = findMockRecordOrReject(recordId)
    if (found.error) return found.error
    return recordDetail(found.record)
  })()
}

export function saveRevision(recordId, revisionId, data) {
  if (!getMockEnabled()) {
    return request.put(`/api/experiment-records/${recordId}/revisions/${revisionId}`, data)
  }
  const found = findMockRecordOrReject(recordId)
  if (found.error) return found.error
  const record = found.record
  if (isMockScenario('conflict')) {
    return mockReject(409, '数据已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: record.version + 1 })
  }
  if (isMockScenario('immutable')) {
    return mockReject(409, '当前修订不可编辑', 'EXPERIMENT_REVISION_IMMUTABLE')
  }
  if (!canEditRecord(record)) {
    return mockReject(403, '无实验记录编辑权限', 'EXPERIMENT_OPERATION_DENIED')
  }
  const revision = findMockRevision(recordId, revisionId)
  if (!revision) return mockReject(404, '实验修订不存在或不可见', 'EXPERIMENT_REVISION_NOT_FOUND')
  if (!isRevisionEditable(revision)) {
    return mockReject(409, '当前修订不可编辑', 'EXPERIMENT_REVISION_IMMUTABLE')
  }
  const recordVersionError = requireVersion(record, data?.recordVersion)
  if (recordVersionError) return recordVersionError
  const revisionVersionError = requireVersion(revision, data?.revisionVersion)
  if (revisionVersionError) return revisionVersionError

  const contentError = validateContent(record, data?.contentJson)
  if (contentError) return contentError

  Object.assign(revision, {
    contentJson: data.contentJson,
    purpose: data.purpose ?? revision.purpose,
    environment: data.environment ?? revision.environment,
    steps: data.steps ?? revision.steps,
    result: data.result ?? revision.result,
    anomaly: data.anomaly ?? revision.anomaly,
    conclusion: data.conclusion ?? revision.conclusion,
    hardwareVersionRef: data.hardwareVersionRef ?? revision.hardwareVersionRef,
    deviceInstanceRef: data.deviceInstanceRef ?? revision.deviceInstanceRef,
    firmwareVersion: data.firmwareVersion ?? revision.firmwareVersion,
    bomVersionRef: data.bomVersionRef ?? revision.bomVersionRef,
    changeSummary: data.changeSummary ?? revision.changeSummary
  })
  revision.version += 1
  record.version += 1
  record.updateTime = nowIso()
  return {
    recordVersion: record.version,
    revisionId: revision.id,
    revisionVersion: revision.version,
    status: record.status
  }
}

export function listRevisions(recordId, params = {}) {
  if (!getMockEnabled()) {
    return request.get(`/api/experiment-records/${recordId}/revisions`, { params })
  }
  const found = findMockRecordOrReject(recordId)
  if (found.error) return found.error
  const list = revisionsOf(recordId)
    .slice()
    .sort((a, b) => b.revisionNo - a.revisionNo)
    .map((r) => ({
      id: r.id,
      recordId: r.recordId,
      revisionNo: r.revisionNo,
      status: r.status,
      changeSummary: r.changeSummary,
      collectionSnapshotId: r.collectionSnapshotId,
      createdByName: userName(r.createdBy),
      createTime: r.createTime,
      submittedAt: r.submittedAt,
      approvedAt: r.approvedAt,
      publishedAt: r.publishedAt,
      version: r.version
    }))
  const page = Number(params.page) > 0 ? Number(params.page) : 1
  const pageSize = Number(params.pageSize) > 0 ? Math.min(Number(params.pageSize), 100) : 20
  const start = (page - 1) * pageSize
  return Promise.resolve({ total: list.length, page, pageSize, list: list.slice(start, start + pageSize) })
}

export function getRevision(recordId, revisionId) {
  if (!getMockEnabled()) {
    return request.get(`/api/experiment-records/${recordId}/revisions/${revisionId}`)
  }
  const found = findMockRecordOrReject(recordId)
  if (found.error) return found.error
  const revision = findMockRevision(recordId, revisionId)
  if (!revision) return mockReject(404, '实验修订不存在或不可见', 'EXPERIMENT_REVISION_NOT_FOUND')
  return Promise.resolve(revisionDetail(revision))
}

export function submitExperimentRecord(recordId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/experiment-records/${recordId}/submit`, payload, {
      headers: idempotencyHeaders(idempotencyKey)
    })
  }
  return withIdempotency(idempotencyKey, { recordId, ...payload }, async () => {
    const found = findMockRecordOrReject(recordId)
    if (found.error) return found.error
    const record = found.record
    if (isMockScenario('conflict')) {
      return mockReject(409, '数据已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: record.version + 1 })
    }
    if (!canEditRecord(record)) {
      return mockReject(403, '无实验记录提交权限', 'EXPERIMENT_OPERATION_DENIED')
    }
    const recordVersionError = requireVersion(record, payload?.recordVersion)
    if (recordVersionError) return recordVersionError
    const revision = findMockRevision(recordId, payload?.revisionId)
    if (!revision) return mockReject(404, '实验修订不存在或不可见', 'EXPERIMENT_REVISION_NOT_FOUND')
    if (!isRevisionEditable(revision)) {
      return mockReject(409, '当前修订不可重复提交', 'EXPERIMENT_INVALID_TRANSITION')
    }
    const revisionVersionError = requireVersion(revision, payload?.revisionVersion)
    if (revisionVersionError) return revisionVersionError
    if (![RECORD_STATUS.DRAFT, RECORD_STATUS.REVISING, RECORD_STATUS.CHANGES_REQUESTED].includes(record.status)) {
      return mockReject(409, '当前记录状态不可提交', 'EXPERIMENT_INVALID_TRANSITION')
    }
    if (revision.collectionSnapshotId && !findMockSnapshot(revision.collectionSnapshotId)) {
      return mockReject(409, '绑定的采集快照不存在', 'COLLECTION_SNAPSHOT_NOT_FOUND')
    }
    const contentError = validateContent(record, revision.contentJson)
    if (contentError) return contentError

    revision.status = REVISION_STATUS.SUBMITTED
    revision.submittedBy = currentUserId()
    revision.submittedAt = nowIso()
    revision.version += 1
    record.status = RECORD_STATUS.IN_REVIEW
    record.version += 1
    record.updateTime = nowIso()
    return {
      recordId: record.id,
      recordVersion: record.version,
      revisionId: revision.id,
      revisionStatus: revision.status,
      status: record.status
    }
  })
}

export function createFeedback(recordId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/experiment-records/${recordId}/feedback`, payload, {
      headers: idempotencyHeaders(idempotencyKey)
    })
  }
  return withIdempotency(idempotencyKey, { recordId, ...payload }, async () => {
    const found = findMockRecordOrReject(recordId)
    if (found.error) return found.error
    const record = found.record
    if (isMockScenario('permission')) {
      return mockReject(403, '无导师评审权限', 'FEEDBACK_OPERATION_DENIED')
    }
    if (isMockScenario('immutable')) {
      return mockReject(409, '当前修订不可再评审', 'EXPERIMENT_REVISION_IMMUTABLE')
    }
    if (!canReviewRecord(record)) {
      return mockReject(403, '无导师评审权限', 'FEEDBACK_OPERATION_DENIED')
    }
    const recordVersionError = requireVersion(record, payload?.recordVersion)
    if (recordVersionError) return recordVersionError
    if (Number(payload?.revisionId) !== Number(record.currentRevisionId)) {
      return mockReject(409, '只能在当前送审修订上评审', 'EXPERIMENT_INVALID_TRANSITION')
    }
    const revision = findMockRevision(recordId, payload.revisionId)
    if (!revision) return mockReject(404, '实验修订不存在或不可见', 'EXPERIMENT_REVISION_NOT_FOUND')
    const type = payload?.type
    if (!Object.values(FEEDBACK_TYPE).includes(type)) {
      return mockReject(400, '反馈类型非法', 'INVALID_PARAMETER')
    }
    const content = String(payload?.content || '').trim()
    if ((type === FEEDBACK_TYPE.COMMENT || type === FEEDBACK_TYPE.CHANGE_REQUEST) && (content.length < 1 || content.length > 2000)) {
      return mockReject(400, '反馈内容需为 1-2000 字符', 'INVALID_PARAMETER')
    }
    if (type === FEEDBACK_TYPE.APPROVAL && content.length > 2000) {
      return mockReject(400, '反馈内容最多 2000 字符', 'INVALID_PARAMETER')
    }
    const visibility = payload?.visibility || FEEDBACK_VISIBILITY.AUTHOR_AND_REVIEWERS
    if (!Object.values(FEEDBACK_VISIBILITY).includes(visibility)) {
      return mockReject(400, '可见范围非法', 'INVALID_PARAMETER')
    }
    if (visibility === FEEDBACK_VISIBILITY.PROJECT_MEMBERS && record.projectId == null) {
      return mockReject(400, '学习实验不支持项目成员可见', 'INVALID_PARAMETER')
    }

    if (type === FEEDBACK_TYPE.COMMENT) {
      const feedback = {
        id: nextFeedbackId(),
        recordId: record.id,
        revisionId: revision.id,
        type,
        content,
        visibility,
        reviewerId: currentUserId(),
        reviewerName: currentUserName(),
        createTime: nowIso(),
        requestId: `REQ-W43-${Date.now()}`
      }
      MOCK_FEEDBACK.push(feedback)
      record.version += 1
      record.updateTime = nowIso()
      return {
        recordId: record.id,
        recordVersion: record.version,
        revisionId: revision.id,
        revisionStatus: revision.status,
        status: record.status,
        feedbackId: feedback.id
      }
    }

    if (record.status !== RECORD_STATUS.IN_REVIEW || revision.status !== REVISION_STATUS.SUBMITTED) {
      return mockReject(409, '当前状态不允许该评审动作', 'EXPERIMENT_INVALID_TRANSITION')
    }

    if (type === FEEDBACK_TYPE.CHANGE_REQUEST) {
      const feedback = {
        id: nextFeedbackId(),
        recordId: record.id,
        revisionId: revision.id,
        type,
        content,
        visibility,
        reviewerId: currentUserId(),
        reviewerName: currentUserName(),
        createTime: nowIso(),
        requestId: `REQ-W43-${Date.now()}`
      }
      MOCK_FEEDBACK.push(feedback)
      revision.status = REVISION_STATUS.CHANGES_REQUESTED
      revision.version += 1
      const newRevisionId = nextRevisionId()
      const newRevision = makeRevision(record, {
        id: newRevisionId,
        revisionNo: revision.revisionNo + 1,
        status: REVISION_STATUS.EDITABLE,
        contentJson: JSON.parse(JSON.stringify(revision.contentJson || {})),
        purpose: revision.purpose,
        environment: revision.environment,
        steps: revision.steps,
        result: revision.result,
        anomaly: revision.anomaly,
        conclusion: revision.conclusion,
        hardwareVersionRef: revision.hardwareVersionRef,
        deviceInstanceRef: revision.deviceInstanceRef,
        firmwareVersion: revision.firmwareVersion,
        bomVersionRef: revision.bomVersionRef,
        collectionSnapshotId: revision.collectionSnapshotId,
        changeSummary: `按导师意见修改（第 ${revision.revisionNo} 轮）`,
        version: 1
      })
      MOCK_REVISIONS.push(newRevision)
      record.currentRevisionId = newRevisionId
      record.status = RECORD_STATUS.CHANGES_REQUESTED
      record.version += 1
      record.updateTime = nowIso()
      return {
        recordId: record.id,
        recordVersion: record.version,
        revisionId: newRevision.id,
        revisionStatus: revision.status,
        status: record.status,
        feedbackId: feedback.id,
        newRevision: {
          id: newRevision.id,
          revisionNo: newRevision.revisionNo,
          status: newRevision.status,
          version: newRevision.version
        }
      }
    }

    // APPROVAL
    const feedback = {
      id: nextFeedbackId(),
      recordId: record.id,
      revisionId: revision.id,
      type,
      content,
      visibility,
      reviewerId: currentUserId(),
      reviewerName: currentUserName(),
      createTime: nowIso(),
      requestId: `REQ-W43-${Date.now()}`
    }
    MOCK_FEEDBACK.push(feedback)
    revision.status = REVISION_STATUS.APPROVED
    revision.approvedBy = currentUserId()
    revision.approvedAt = nowIso()
    revision.version += 1
    record.status = RECORD_STATUS.APPROVED
    record.version += 1
    record.updateTime = nowIso()
    return {
      recordId: record.id,
      recordVersion: record.version,
      revisionId: revision.id,
      revisionStatus: revision.status,
      status: record.status,
      feedbackId: feedback.id
    }
  })
}

export function listFeedback(recordId, params = {}) {
  if (!getMockEnabled()) {
    return request.get(`/api/experiment-records/${recordId}/feedback`, { params })
  }
  const found = findMockRecordOrReject(recordId)
  if (found.error) return found.error
  if (isMockScenario('empty')) {
    return Promise.resolve({ total: 0, page: 1, pageSize: 20, list: [] })
  }
  const list = feedbackOf(recordId)
    .slice()
    .sort((a, b) => Date.parse(a.createTime) - Date.parse(b.createTime) || a.id - b.id)
    .map((f) => ({ ...f, revisionNo: findMockRevision(recordId, f.revisionId)?.revisionNo ?? null }))
  const page = Number(params.page) > 0 ? Number(params.page) : 1
  const pageSize = Number(params.pageSize) > 0 ? Math.min(Number(params.pageSize), 100) : 20
  const start = (page - 1) * pageSize
  return Promise.resolve({ total: list.length, page, pageSize, list: list.slice(start, start + pageSize) })
}

export function publishExperimentRecord(recordId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/experiment-records/${recordId}/publish`, payload, {
      headers: idempotencyHeaders(idempotencyKey)
    })
  }
  return withIdempotency(idempotencyKey, { recordId, ...payload }, async () => {
    const found = findMockRecordOrReject(recordId)
    if (found.error) return found.error
    const record = found.record
    if (isMockScenario('conflict')) {
      return mockReject(409, '数据已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: record.version + 1 })
    }
    if (!canPublishRecord(record)) {
      return mockReject(403, '无实验发布权限', 'EXPERIMENT_OPERATION_DENIED')
    }
    const recordVersionError = requireVersion(record, payload?.recordVersion)
    if (recordVersionError) return recordVersionError
    if (record.status !== RECORD_STATUS.APPROVED) {
      return mockReject(409, '只有已批准记录可发布', 'EXPERIMENT_INVALID_TRANSITION')
    }
    const revision = findMockRevision(recordId, payload?.revisionId)
    if (!revision) return mockReject(404, '实验修订不存在或不可见', 'EXPERIMENT_REVISION_NOT_FOUND')
    if (revision.status !== REVISION_STATUS.APPROVED || revision.id !== record.currentRevisionId) {
      return mockReject(409, '只能发布最新已批准修订', 'EXPERIMENT_INVALID_TRANSITION')
    }
    if (record.publishedRevisionId) {
      const previous = findMockRevision(recordId, record.publishedRevisionId)
      if (previous) previous.status = REVISION_STATUS.SUPERSEDED
    }
    revision.status = REVISION_STATUS.PUBLISHED
    revision.publishedBy = currentUserId()
    revision.publishedAt = nowIso()
    revision.version += 1
    record.publishedRevisionId = revision.id
    record.status = RECORD_STATUS.PUBLISHED
    record.version += 1
    record.updateTime = nowIso()
    return {
      recordId: record.id,
      recordVersion: record.version,
      revisionId: revision.id,
      revisionStatus: revision.status,
      status: record.status
    }
  })
}

export function createRevision(recordId, payload = {}) {
  if (!getMockEnabled()) {
    return request.post(`/api/experiment-records/${recordId}/revisions`, payload)
  }
  const found = findMockRecordOrReject(recordId)
  if (found.error) return found.error
  const record = found.record
  if (!canEditRecord(record)) {
    return mockReject(403, '无实验记录编辑权限', 'EXPERIMENT_OPERATION_DENIED')
  }
  if (record.status !== RECORD_STATUS.PUBLISHED) {
    return mockReject(409, '只有已发布记录可创建新修订', 'EXPERIMENT_INVALID_TRANSITION')
  }
  const source = findMockRevision(recordId, record.publishedRevisionId)
  if (!source) return mockReject(404, '发布修订不存在或不可见', 'EXPERIMENT_REVISION_NOT_FOUND')
  const revisionId = nextRevisionId()
  const maxNo = Math.max(...revisionsOf(recordId).map((r) => r.revisionNo))
  const revision = makeRevision(record, {
    id: revisionId,
    revisionNo: maxNo + 1,
    status: REVISION_STATUS.EDITABLE,
    contentJson: JSON.parse(JSON.stringify(source.contentJson || {})),
    purpose: source.purpose,
    environment: source.environment,
    steps: source.steps,
    result: source.result,
    anomaly: source.anomaly,
    conclusion: source.conclusion,
    hardwareVersionRef: source.hardwareVersionRef,
    deviceInstanceRef: source.deviceInstanceRef,
    firmwareVersion: source.firmwareVersion,
    bomVersionRef: source.bomVersionRef,
    collectionSnapshotId: source.collectionSnapshotId,
    changeSummary: payload.changeSummary || `基于发布修订 ${source.revisionNo} 创建`,
    version: 1
  })
  MOCK_REVISIONS.push(revision)
  record.currentRevisionId = revisionId
  record.status = RECORD_STATUS.REVISING
  record.version += 1
  record.updateTime = nowIso()
  return Promise.resolve({
    recordId: record.id,
    recordVersion: record.version,
    revisionId: revision.id,
    revisionNo: revision.revisionNo,
    revisionStatus: revision.status,
    status: record.status
  })
}

export function bindCollectionSnapshot(recordId, payload) {
  if (!getMockEnabled()) {
    return request.put(`/api/experiment-records/${recordId}/collection-snapshot`, payload)
  }
  const found = findMockRecordOrReject(recordId)
  if (found.error) return found.error
  const record = found.record
  if (isMockScenario('conflict')) {
    return mockReject(409, '数据已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: record.version + 1 })
  }
  if (!canEditRecord(record)) {
    return mockReject(403, '无实验记录编辑权限', 'EXPERIMENT_OPERATION_DENIED')
  }
  const revision = findMockRevision(recordId, payload?.revisionId)
  if (!revision) return mockReject(404, '实验修订不存在或不可见', 'EXPERIMENT_REVISION_NOT_FOUND')
  if (!isRevisionEditable(revision)) {
    return mockReject(409, '只有可编辑修订可绑定快照', 'EXPERIMENT_REVISION_IMMUTABLE')
  }
  const recordVersionError = requireVersion(record, payload?.recordVersion)
  if (recordVersionError) return recordVersionError
  const revisionVersionError = requireVersion(revision, payload?.revisionVersion)
  if (revisionVersionError) return revisionVersionError

  const snapshotId = payload?.snapshotId == null ? null : Number(payload.snapshotId)
  if (snapshotId != null) {
    if (revision.collectionSnapshotId && Number(revision.collectionSnapshotId) !== snapshotId) {
      return mockReject(409, '当前修订已绑定采集快照，如需替换请先解除', 'SNAPSHOT_ALREADY_BOUND')
    }
    const snapshot = findMockSnapshot(snapshotId)
    if (!snapshot) return mockReject(404, '采集快照不存在或不可见', 'COLLECTION_SNAPSHOT_NOT_FOUND')
    if (record.projectId != null && snapshot.projectId != null && Number(snapshot.projectId) !== Number(record.projectId)) {
      return mockReject(404, '采集快照不存在或不可见', 'COLLECTION_SNAPSHOT_NOT_FOUND')
    }
  }

  revision.collectionSnapshotId = snapshotId
  revision.version += 1
  record.version += 1
  record.updateTime = nowIso()
  return Promise.resolve({
    recordId: record.id,
    recordVersion: record.version,
    revisionId: revision.id,
    revisionVersion: revision.version,
    collectionSnapshotId: revision.collectionSnapshotId
  })
}

export function convertToProjectDraft(recordId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/experiment-records/${recordId}/project-draft`, payload, {
      headers: idempotencyHeaders(idempotencyKey)
    })
  }
  return withIdempotency(idempotencyKey, { recordId, ...payload }, async () => {
    const found = findMockRecordOrReject(recordId)
    if (found.error) return found.error
    const record = found.record
    if (record.convertedProjectId) {
      return mockReject(409, '该实验已转为项目', 'PROJECT_ALREADY_CONVERTED')
    }
    const roleAllowed =
      canReviewGlobally() ||
      Number(record.authorId) === currentUserId() ||
      isProjectManager(record.projectId)
    if (!roleAllowed) {
      return mockReject(403, '无转项目权限', 'PROJECT_CONVERSION_DENIED')
    }
    if (record.status !== RECORD_STATUS.PUBLISHED) {
      return mockReject(409, '只有已发布实验可转项目', 'EXPERIMENT_INVALID_TRANSITION')
    }
    const recordVersionError = requireVersion(record, payload?.recordVersion)
    if (recordVersionError) return recordVersionError
    const code = String(payload?.code || '').trim()
    const name = String(payload?.name || '').trim()
    if (!code || code.length > 50) return mockReject(400, '项目编号需为 1-50 字符', 'INVALID_PARAMETER')
    if (name.length < 2 || name.length > 120) return mockReject(400, '项目名称需为 2-120 字符', 'INVALID_PARAMETER')
    if (payload?.summary && String(payload.summary).length > 500) {
      return mockReject(400, '项目简介最多 500 字符', 'INVALID_PARAMETER')
    }
    const project = registerMockProject({
      code,
      name,
      summary: payload?.summary || `来源于 ${record.recordCode} 的已发布结论`,
      category: payload?.category || 'hardware_project',
      sourceExperimentRecordId: record.id,
      sourcePublishedRevisionId: record.publishedRevisionId
    })
    record.convertedProjectId = project.id
    record.version += 1
    record.updateTime = nowIso()
    return {
      ...project,
      sourceExperimentRecordId: record.id,
      sourcePublishedRevisionId: record.publishedRevisionId
    }
  })
}
