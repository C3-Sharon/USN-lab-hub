import request from '@/utils/request'
import { getMockEnabled } from '@/utils/mock'
import { userStore, currentRoles } from '@/store/user'
import { ROLE } from '@/utils/permission'

/**
 * 学习实验域 API（第 5 周 M02-W01）
 *
 * 契约来源：docs/contracts/LEARNING_EXPERIMENT_API.md v1.0（FROZEN）§11
 *
 * 真实端点：
 *   POST   /api/learning/roadmaps                      创建学习路线
 *   GET    /api/learning/roadmaps                      路线列表（分页/筛选/排序）
 *   GET    /api/learning/roadmaps/{id}                 路线详情（含阶段统计）
 *   PUT    /api/learning/roadmaps/{id}                 编辑路线基本信息
 *   PUT    /api/learning/roadmaps/{id}/status          更新路线状态
 *   POST   /api/learning/roadmaps/{id}/stages          创建学习阶段
 *   GET    /api/learning/roadmaps/{id}/stages          阶段列表（含单元）
 *   PUT    /api/learning/stages/{id}                   编辑阶段基本信息
 *   POST   /api/learning/stages/{id}/units             创建学习单元
 *   PUT    /api/learning/units/{id}                    编辑单元基本信息
 *   POST   /api/learning/roadmaps/{id}/enroll          开始学习（幂等）
 *   POST   /api/learning/units/{id}/complete           标记单元完成（幂等）
 *   DELETE /api/learning/units/{id}/complete           取消单元完成（幂等）
 *   GET    /api/learning/my-roadmaps                   我的学习路线
 *
 * mock 开关：VITE_USE_MOCK=true 走本地 mock（字段与契约一致），默认 false 走真实接口。
 * mock 成功 resolve data，失败 reject { code, msg, reason }，与 request.js 拦截器行为一致。
 *
 * 前端补充字段（契约未显式定义，渲染必需，需后端确认）：
 *   - listStages 返回的每个 unit 附带 `completed`（boolean，当前用户是否已完成该单元）。
 *     契约 §9.3 要求详情页单元项展示「完成状态勾选框」，但 §5 未定义按单元查询完成态的接口，
 *     故在阶段列表接口为单元补充 completed 字段（未开始时恒为 false）。
 */

// ========== 枚举 ==========

export const ROADMAP_STATUS = Object.freeze({
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  ARCHIVED: 'ARCHIVED'
})

export const ROADMAP_STATUS_META = Object.freeze({
  [ROADMAP_STATUS.DRAFT]: { label: '草稿', tagType: 'info' },
  [ROADMAP_STATUS.PUBLISHED]: { label: '已发布', tagType: 'success' },
  [ROADMAP_STATUS.ARCHIVED]: { label: '已归档', tagType: 'info' }
})

export const LEARNING_DIFFICULTY = Object.freeze({
  BEGINNER: 'BEGINNER',
  INTERMEDIATE: 'INTERMEDIATE',
  ADVANCED: 'ADVANCED'
})

/** 难度标签：tone 供组件套用紫色系（学习语义色，小面积识别） */
export const LEARNING_DIFFICULTY_META = Object.freeze({
  [LEARNING_DIFFICULTY.BEGINNER]: { label: '入门', tone: 'beginner', rank: 1 },
  [LEARNING_DIFFICULTY.INTERMEDIATE]: { label: '进阶', tone: 'intermediate', rank: 2 },
  [LEARNING_DIFFICULTY.ADVANCED]: { label: '高阶', tone: 'advanced', rank: 3 }
})

export const LEARNING_STATUS = Object.freeze({
  NOT_STARTED: 'NOT_STARTED',
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED'
})

export const LEARNING_STATUS_META = Object.freeze({
  [LEARNING_STATUS.NOT_STARTED]: { label: '未开始', tagType: 'info' },
  [LEARNING_STATUS.IN_PROGRESS]: { label: '进行中', tagType: 'primary' },
  [LEARNING_STATUS.COMPLETED]: { label: '已完成', tagType: 'success' }
})

/** 契约 §2.2 合法状态转换（ARCHIVED 为终态） */
export const ROADMAP_TRANSITIONS = Object.freeze({
  [ROADMAP_STATUS.DRAFT]: [ROADMAP_STATUS.PUBLISHED, ROADMAP_STATUS.ARCHIVED],
  [ROADMAP_STATUS.PUBLISHED]: [ROADMAP_STATUS.ARCHIVED],
  [ROADMAP_STATUS.ARCHIVED]: []
})

export function canTransitionRoadmap(from, to) {
  return (ROADMAP_TRANSITIONS[from] || []).includes(to)
}

export function nextRoadmapStatuses(from) {
  return [...(ROADMAP_TRANSITIONS[from] || [])]
}

export function isTerminalRoadmapStatus(status) {
  return (ROADMAP_TRANSITIONS[status] || []).length === 0
}

export function difficultyMeta(difficulty) {
  return LEARNING_DIFFICULTY_META[difficulty] || LEARNING_DIFFICULTY_META[LEARNING_DIFFICULTY.BEGINNER]
}

export function roadmapStatusMeta(status) {
  return ROADMAP_STATUS_META[status] || { label: status || '--', tagType: 'info' }
}

export function learningStatusMeta(status) {
  return LEARNING_STATUS_META[status] || { label: status || '--', tagType: 'info' }
}

// ========== 权限判定（前端 UI 收敛，真正鉴权以后端为准） ==========

const MANAGE_ROLES = [ROLE.SYSTEM_ADMIN, ROLE.TEACHER]

/** 是否可管理学习路线（创建/编辑路线、阶段、单元，更新状态） */
export function canManageLearning() {
  return currentRoles().some((role) => MANAGE_ROLES.includes(role))
}

/** 是否可开始学习 / 标记完成（GUEST 不可；本系统已知角色均可） */
export function canLearn() {
  return currentRoles().length > 0
}

// ========== Mock 数据 ==========

/** mock 模式未登录时假定的当前用户（张同学，MEMBER） */
const MOCK_CURRENT_USER_ID = 2

const MOCK_USER_NAMES = { 1: '管理员', 2: '张同学', 3: '李同学' }

/**
 * 预置路线（契约 §2.3：实验室安全 → 电路与焊接 → STM32/ESP32 → 传感器 → MQTT → 原理图/PCB → 联网硬件）
 * stages[].units[] 为内嵌结构；接口返回时按契约裁剪/补全字段。
 */
const MOCK_ROADMAPS = [
  {
    id: 1,
    title: '嵌入式硬件入门',
    description: '从零开始学习嵌入式硬件开发：实验室安全、电路与焊接、STM32/ESP32、传感器、MQTT、原理图/PCB 到联网硬件。',
    status: 'PUBLISHED',
    difficulty: 'BEGINNER',
    estimatedHours: 40,
    coverMediaId: null,
    sortOrder: 1,
    createdBy: 1,
    createTime: '2026-10-06T10:00:00',
    updateTime: '2026-10-07T09:00:00',
    stages: [
      {
        id: 11,
        name: '实验室安全',
        description: '实验室安全规范和基本操作。',
        sortOrder: 1,
        units: [
          { id: 101, title: '安全规范阅读', description: '阅读实验室安全规范文档并完成自测。', sortOrder: 1 },
          { id: 102, title: '用电与消防', description: '掌握用电安全和消防器材使用。', sortOrder: 2 },
          { id: 103, title: '危化品管理', description: '了解危化品存放与废弃处理流程。', sortOrder: 3 }
        ]
      },
      {
        id: 12,
        name: '电路与焊接',
        description: '基础电路知识和焊接技能。',
        sortOrder: 2,
        units: [
          { id: 104, title: '电路基础', description: '欧姆定律、串并联与常用元器件识别。', sortOrder: 1 },
          { id: 105, title: '万用表使用', description: '测量电压、电流、电阻与通断。', sortOrder: 2 },
          { id: 106, title: '手工焊接', description: '直插与贴片元件的手工焊接练习。', sortOrder: 3 },
          { id: 107, title: '焊接质量检查', description: '识别虚焊、冷焊与连锡。', sortOrder: 4 }
        ]
      },
      {
        id: 13,
        name: 'STM32/ESP32',
        description: '主流单片机开发环境与基础外设。',
        sortOrder: 3,
        units: [
          { id: 108, title: '开发环境搭建', description: '安装工具链并点亮第一个 LED。', sortOrder: 1 },
          { id: 109, title: 'GPIO 点灯', description: 'GPIO 输入输出与按键消抖。', sortOrder: 2 },
          { id: 110, title: '串口通信', description: 'UART 收发与调试信息输出。', sortOrder: 3 }
        ]
      },
      {
        id: 14,
        name: '传感器',
        description: '常用传感器的原理与数据读取。',
        sortOrder: 4,
        units: [
          { id: 111, title: '温湿度传感器', description: 'DHT22 数据采集与校验。', sortOrder: 1 },
          { id: 112, title: '光照传感器', description: '光敏电阻与 ADC 采集。', sortOrder: 2 },
          { id: 113, title: '传感器数据读取', description: '多传感器数据整合与上报。', sortOrder: 3 }
        ]
      },
      {
        id: 15,
        name: 'MQTT',
        description: '物联网消息协议与客户端接入。',
        sortOrder: 5,
        units: [
          { id: 114, title: 'MQTT 协议基础', description: '发布订阅模型与 QoS 等级。', sortOrder: 1 },
          { id: 115, title: '客户端连接', description: '连接 Broker 与断线重连。', sortOrder: 2 },
          { id: 116, title: '主题发布订阅', description: '主题设计与消息收发实践。', sortOrder: 3 }
        ]
      },
      {
        id: 16,
        name: '原理图/PCB',
        description: '从原理图到 PCB 打样。',
        sortOrder: 6,
        units: [
          { id: 117, title: '原理图绘制', description: '使用 EDA 工具绘制原理图。', sortOrder: 1 },
          { id: 118, title: 'PCB 布局布线', description: '两层板布局布线与规则检查。', sortOrder: 2 },
          { id: 119, title: 'Gerber 导出', description: '导出打样文件并送厂。', sortOrder: 3 }
        ]
      },
      {
        id: 17,
        name: '联网硬件',
        description: '端到端联网硬件方案落地。',
        sortOrder: 7,
        units: [
          { id: 120, title: '联网方案设计', description: '选择通信方式与供电方案。', sortOrder: 1 },
          { id: 121, title: '端到端联调', description: '硬件—网关—平台全链路联调。', sortOrder: 2 }
        ]
      }
    ]
  },
  {
    id: 2,
    title: '实验室安全与规范',
    description: '面向全体新成员的安全基础必修路线。',
    status: 'PUBLISHED',
    difficulty: 'BEGINNER',
    estimatedHours: 6,
    coverMediaId: null,
    sortOrder: 2,
    createdBy: 1,
    createTime: '2026-09-15T10:00:00',
    updateTime: '2026-09-25T16:00:00',
    stages: [
      {
        id: 21,
        name: '安全基础',
        description: '安全守则与应急处理。',
        sortOrder: 1,
        units: [
          { id: 201, title: '安全守则', description: '学习实验室通用安全守则。', sortOrder: 1 },
          { id: 202, title: '应急预案', description: '掌握突发情况应急处置流程。', sortOrder: 2 }
        ]
      },
      {
        id: 22,
        name: '设备操作',
        description: '常用设备的规范操作。',
        sortOrder: 2,
        units: [
          { id: 203, title: '常用设备使用', description: '电源、示波器等设备规范使用。', sortOrder: 1 },
          { id: 204, title: '设备维护', description: '日常点检与维护记录。', sortOrder: 2 }
        ]
      }
    ]
  },
  {
    id: 3,
    title: 'Python 数据处理入门',
    description: '使用 Python 处理实验数据并绘图（草稿，待发布）。',
    status: 'DRAFT',
    difficulty: 'INTERMEDIATE',
    estimatedHours: 20,
    coverMediaId: null,
    sortOrder: 3,
    createdBy: 1,
    createTime: '2026-10-08T14:00:00',
    updateTime: '2026-10-08T14:00:00',
    stages: [
      {
        id: 31,
        name: 'Python 基础',
        description: '语法、数据类型与文件读写。',
        sortOrder: 1,
        units: [
          { id: 301, title: '语法与数据类型', description: '变量、列表、字典与函数。', sortOrder: 1 },
          { id: 302, title: '文件读写', description: 'CSV 与 JSON 数据读取。', sortOrder: 2 }
        ]
      }
    ]
  },
  {
    id: 4,
    title: '往届传感器实训',
    description: '2025 学年传感器实训路线（已归档，仅供回顾）。',
    status: 'ARCHIVED',
    difficulty: 'ADVANCED',
    estimatedHours: 30,
    coverMediaId: null,
    sortOrder: 4,
    createdBy: 1,
    createTime: '2025-09-01T10:00:00',
    updateTime: '2026-06-30T10:00:00',
    stages: [
      {
        id: 41,
        name: '传感器选型',
        description: '按场景选择传感器。',
        sortOrder: 1,
        units: [
          { id: 401, title: '选型方法', description: '量程、精度与接口选择。', sortOrder: 1 },
          { id: 402, title: '成本评估', description: '方案成本对比。', sortOrder: 2 }
        ]
      },
      {
        id: 42,
        name: '系统集成',
        description: '传感器系统集成与标定。',
        sortOrder: 2,
        units: [{ id: 403, title: '标定实验', description: '完成传感器标定。', sortOrder: 1 }]
      }
    ]
  }
]

/** 成员学习记录：(roadmapId, userId) 唯一 */
const MOCK_ENROLLMENTS = [
  {
    id: 1,
    roadmapId: 1,
    userId: 2,
    startedAt: '2026-10-01T09:00:00',
    completedAt: null,
    createTime: '2026-10-01T09:00:00',
    updateTime: '2026-10-07T09:00:00'
  },
  {
    id: 2,
    roadmapId: 2,
    userId: 2,
    startedAt: '2026-09-20T09:00:00',
    completedAt: '2026-09-25T16:00:00',
    createTime: '2026-09-20T09:00:00',
    updateTime: '2026-09-25T16:00:00'
  }
]

/** 单元完成记录：(unitId, userId) 唯一 */
const MOCK_UNIT_COMPLETIONS = [
  { unitId: 101, roadmapId: 1, userId: 2, completedAt: '2026-10-01T10:00:00' },
  { unitId: 102, roadmapId: 1, userId: 2, completedAt: '2026-10-02T10:00:00' },
  { unitId: 103, roadmapId: 1, userId: 2, completedAt: '2026-10-03T10:00:00' },
  { unitId: 104, roadmapId: 1, userId: 2, completedAt: '2026-10-05T10:00:00' },
  { unitId: 105, roadmapId: 1, userId: 2, completedAt: '2026-10-06T10:00:00' },
  { unitId: 201, roadmapId: 2, userId: 2, completedAt: '2026-09-21T10:00:00' },
  { unitId: 202, roadmapId: 2, userId: 2, completedAt: '2026-09-22T10:00:00' },
  { unitId: 203, roadmapId: 2, userId: 2, completedAt: '2026-09-24T10:00:00' },
  { unitId: 204, roadmapId: 2, userId: 2, completedAt: '2026-09-25T10:00:00' }
]

// ========== Mock 辅助 ==========

function reject(code, msg, reason, data = null) {
  return Promise.reject({ code, msg, reason, data })
}

function nowIso() {
  return new Date().toISOString()
}

function currentUserId() {
  const id = userStore?.userInfo?.id
  return id == null ? MOCK_CURRENT_USER_ID : Number(id)
}

function isManageRole() {
  return canManageLearning()
}

function findMockRoadmap(roadmapId) {
  return MOCK_ROADMAPS.find((r) => r.id === Number(roadmapId)) || null
}

function findMockStage(stageId) {
  for (const roadmap of MOCK_ROADMAPS) {
    const stage = roadmap.stages.find((s) => s.id === Number(stageId))
    if (stage) return { roadmap, stage }
  }
  return null
}

function findMockUnit(unitId) {
  for (const roadmap of MOCK_ROADMAPS) {
    for (const stage of roadmap.stages) {
      const unit = stage.units.find((u) => u.id === Number(unitId))
      if (unit) return { roadmap, stage, unit }
    }
  }
  return null
}

function countUnits(roadmap) {
  return roadmap.stages.reduce((sum, stage) => sum + stage.units.length, 0)
}

function countLearners(roadmapId) {
  return MOCK_ENROLLMENTS.filter((e) => e.roadmapId === Number(roadmapId)).length + (Number(roadmapId) === 1 ? 14 : 0)
}

function isUnitCompleted(unitId, userId) {
  return MOCK_UNIT_COMPLETIONS.some((c) => c.unitId === Number(unitId) && c.userId === Number(userId))
}

function completedUnitCount(roadmap, userId) {
  const unitIds = new Set(roadmap.stages.flatMap((s) => s.units.map((u) => u.id)))
  return MOCK_UNIT_COMPLETIONS.filter((c) => c.roadmapId === roadmap.id && c.userId === Number(userId) && unitIds.has(c.unitId)).length
}

function deriveLearningStatus(completed, total) {
  if (total > 0 && completed >= total) return LEARNING_STATUS.COMPLETED
  if (completed > 0) return LEARNING_STATUS.IN_PROGRESS
  return LEARNING_STATUS.NOT_STARTED
}

/** 动态计算学习进度（契约 §5.3：progress = floor(completed / total * 100)） */
function computeProgress(roadmap, userId) {
  const total = countUnits(roadmap)
  const completed = completedUnitCount(roadmap, userId)
  const progress = total > 0 ? Math.floor((completed / total) * 100) : 0
  return { totalUnitCount: total, completedUnitCount: completed, progress }
}

function findEnrollment(roadmapId, userId) {
  return MOCK_ENROLLMENTS.find((e) => e.roadmapId === Number(roadmapId) && e.userId === Number(userId)) || null
}

function toRoadmapSummary(roadmap) {
  return {
    id: roadmap.id,
    title: roadmap.title,
    description: roadmap.description,
    status: roadmap.status,
    difficulty: roadmap.difficulty,
    estimatedHours: roadmap.estimatedHours,
    coverMediaId: roadmap.coverMediaId,
    sortOrder: roadmap.sortOrder,
    stageCount: roadmap.stages.length,
    learnerCount: countLearners(roadmap.id),
    createdBy: roadmap.createdBy,
    createdByName: MOCK_USER_NAMES[roadmap.createdBy] || null,
    createTime: roadmap.createTime,
    updateTime: roadmap.updateTime
  }
}

function toStageSummary(stage, roadmapId) {
  return {
    id: stage.id,
    roadmapId: Number(roadmapId),
    name: stage.name,
    description: stage.description,
    sortOrder: stage.sortOrder,
    unitCount: stage.units.length,
    createTime: stage.createTime || '2026-10-06T10:00:00',
    updateTime: stage.updateTime || '2026-10-06T10:00:00'
  }
}

function toUnit(unit, stageId, userId) {
  return {
    id: unit.id,
    stageId: Number(stageId),
    title: unit.title,
    description: unit.description,
    sortOrder: unit.sortOrder,
    templateId: unit.templateId ?? null,
    templateName: unit.templateName ?? null,
    completed: isUnitCompleted(unit.id, userId),
    createTime: unit.createTime || '2026-10-06T10:00:00',
    updateTime: unit.updateTime || '2026-10-06T10:00:00'
  }
}

function toEnrollmentRecord(enrollment, roadmap, userId) {
  const { totalUnitCount, completedUnitCount: completed, progress } = computeProgress(roadmap, userId)
  return {
    id: enrollment.id,
    roadmapId: roadmap.id,
    userId: Number(userId),
    status: deriveLearningStatus(completed, totalUnitCount),
    startedAt: enrollment.startedAt,
    completedAt: enrollment.completedAt,
    progress,
    completedUnitCount: completed,
    totalUnitCount,
    createTime: enrollment.createTime,
    updateTime: enrollment.updateTime
  }
}

function toMyRoadmapItem(enrollment) {
  const roadmap = findMockRoadmap(enrollment.roadmapId)
  if (!roadmap) return null
  const { totalUnitCount, completedUnitCount: completed, progress } = computeProgress(roadmap, enrollment.userId)
  return {
    roadmapId: roadmap.id,
    roadmapTitle: roadmap.title,
    roadmapDifficulty: roadmap.difficulty,
    status: deriveLearningStatus(completed, totalUnitCount),
    progress,
    completedUnitCount: completed,
    totalUnitCount,
    startedAt: enrollment.startedAt,
    completedAt: enrollment.completedAt,
    updateTime: enrollment.updateTime
  }
}

/** 判断当前用户可见的路线（契约 §2.5：普通成员仅 PUBLISHED） */
function isRoadmapVisible(roadmap, statusFilter) {
  if (isManageRole()) return true
  if (statusFilter) return roadmap.status === statusFilter && statusFilter === ROADMAP_STATUS.PUBLISHED
  return roadmap.status === ROADMAP_STATUS.PUBLISHED
}

// ========== 路线 API ==========

export function createRoadmap(data) {
  if (!getMockEnabled()) {
    return request.post('/api/learning/roadmaps', data)
  }
  if (!isManageRole()) {
    return reject(403, '无学习路线管理权限', 'LEARNING_OPERATION_DENIED')
  }
  const title = String(data?.title || '').trim()
  if (title.length < 2 || title.length > 80) {
    return reject(400, '路线标题需为 2-80 字符', 'INVALID_PARAMETER')
  }
  if (data?.description && String(data.description).length > 500) {
    return reject(400, '路线描述最多 500 字符', 'INVALID_PARAMETER')
  }
  if (data?.difficulty && !LEARNING_DIFFICULTY_META[data.difficulty]) {
    return reject(400, '难度取值非法', 'INVALID_PARAMETER')
  }

  const now = nowIso()
  const roadmap = {
    id: MOCK_ROADMAPS.reduce((max, r) => Math.max(max, r.id), 0) + 1,
    title,
    description: data?.description || null,
    status: ROADMAP_STATUS.DRAFT,
    difficulty: LEARNING_DIFFICULTY_META[data?.difficulty] ? data.difficulty : LEARNING_DIFFICULTY.BEGINNER,
    estimatedHours: data?.estimatedHours != null ? Number(data.estimatedHours) : null,
    coverMediaId: data?.coverMediaId ?? null,
    sortOrder: data?.sortOrder != null ? Number(data.sortOrder) : 0,
    createdBy: currentUserId(),
    createTime: now,
    updateTime: now,
    stages: []
  }
  MOCK_ROADMAPS.push(roadmap)
  return Promise.resolve(toRoadmapSummary(roadmap))
}

export function listRoadmaps(params = {}) {
  if (!getMockEnabled()) {
    return request.get('/api/learning/roadmaps', { params })
  }
  if (!canLearn()) {
    return reject(403, '无权访问学习实验台', 'ACCESS_DENIED')
  }

  const statusFilter = params.status || ''
  let list = MOCK_ROADMAPS.filter((r) => isRoadmapVisible(r, statusFilter)).map(toRoadmapSummary)
  if (statusFilter) list = list.filter((r) => r.status === statusFilter)
  if (params.difficulty) list = list.filter((r) => r.difficulty === params.difficulty)

  const sortBy = params.sortBy || 'sortOrder'
  const dir = params.sortOrder === 'desc' ? -1 : 1
  list.sort((a, b) => {
    let result
    if (sortBy === 'createTime') result = Date.parse(a.createTime) - Date.parse(b.createTime)
    else if (sortBy === 'updateTime') result = Date.parse(a.updateTime) - Date.parse(b.updateTime)
    else result = a.sortOrder - b.sortOrder
    if (result === 0) result = a.id - b.id
    return result * dir
  })

  const page = Number(params.page) > 0 ? Number(params.page) : 1
  const pageSize = Number(params.pageSize) > 0 ? Math.min(Number(params.pageSize), 100) : 20
  const start = (page - 1) * pageSize
  return Promise.resolve({
    total: list.length,
    page,
    pageSize,
    list: list.slice(start, start + pageSize)
  })
}

export function getRoadmapDetail(roadmapId) {
  if (!getMockEnabled()) {
    return request.get(`/api/learning/roadmaps/${roadmapId}`)
  }
  if (!canLearn()) {
    return reject(403, '无权访问学习实验台', 'ACCESS_DENIED')
  }
  const roadmap = findMockRoadmap(roadmapId)
  if (!roadmap) return reject(404, '学习路线不存在', 'LEARNING_ROADMAP_NOT_FOUND')
  if (roadmap.status !== ROADMAP_STATUS.PUBLISHED && !isManageRole()) {
    return reject(404, '学习路线不存在', 'LEARNING_ROADMAP_NOT_FOUND')
  }
  return Promise.resolve({
    ...toRoadmapSummary(roadmap),
    stages: roadmap.stages.map((s) => toStageSummary(s, roadmap.id))
  })
}

export function updateRoadmap(roadmapId, data) {
  if (!getMockEnabled()) {
    return request.put(`/api/learning/roadmaps/${roadmapId}`, data)
  }
  if (!isManageRole()) {
    return reject(403, '无学习路线管理权限', 'LEARNING_OPERATION_DENIED')
  }
  const roadmap = findMockRoadmap(roadmapId)
  if (!roadmap) return reject(404, '学习路线不存在', 'LEARNING_ROADMAP_NOT_FOUND')
  if (roadmap.status === ROADMAP_STATUS.ARCHIVED) {
    return reject(409, '学习路线已归档，不可编辑', 'LEARNING_ARCHIVED')
  }
  if (data?.title != null) {
    const title = String(data.title).trim()
    if (title.length < 2 || title.length > 80) {
      return reject(400, '路线标题需为 2-80 字符', 'INVALID_PARAMETER')
    }
    roadmap.title = title
  }
  if (data?.description !== undefined) roadmap.description = data.description || null
  if (data?.difficulty != null && LEARNING_DIFFICULTY_META[data.difficulty]) roadmap.difficulty = data.difficulty
  if (data?.estimatedHours !== undefined) {
    roadmap.estimatedHours = data.estimatedHours != null ? Number(data.estimatedHours) : null
  }
  if (data?.coverMediaId !== undefined) roadmap.coverMediaId = data.coverMediaId ?? null
  if (data?.sortOrder !== undefined) roadmap.sortOrder = Number(data.sortOrder) || 0
  roadmap.updateTime = nowIso()
  return Promise.resolve(toRoadmapSummary(roadmap))
}

export function updateRoadmapStatus(roadmapId, payload) {
  if (!getMockEnabled()) {
    return request.put(`/api/learning/roadmaps/${roadmapId}/status`, payload)
  }
  if (!isManageRole()) {
    return reject(403, '无学习路线管理权限', 'LEARNING_OPERATION_DENIED')
  }
  const roadmap = findMockRoadmap(roadmapId)
  if (!roadmap) return reject(404, '学习路线不存在', 'LEARNING_ROADMAP_NOT_FOUND')

  const target = payload?.status
  if (!canTransitionRoadmap(roadmap.status, target)) {
    return reject(409, '非法路线状态转换', 'LEARNING_INVALID_TRANSITION')
  }
  roadmap.status = target
  roadmap.updateTime = nowIso()
  return Promise.resolve({ id: roadmap.id, status: roadmap.status, updateTime: roadmap.updateTime })
}

// ========== 阶段 API ==========

export function createStage(roadmapId, data) {
  if (!getMockEnabled()) {
    return request.post(`/api/learning/roadmaps/${roadmapId}/stages`, data)
  }
  if (!isManageRole()) {
    return reject(403, '无学习路线管理权限', 'LEARNING_OPERATION_DENIED')
  }
  const roadmap = findMockRoadmap(roadmapId)
  if (!roadmap) return reject(404, '学习路线不存在', 'LEARNING_ROADMAP_NOT_FOUND')
  if (roadmap.status === ROADMAP_STATUS.ARCHIVED) {
    return reject(409, '学习路线已归档，不可添加阶段', 'LEARNING_ARCHIVED')
  }
  const name = String(data?.name || '').trim()
  if (name.length < 2 || name.length > 80) {
    return reject(400, '阶段名称需为 2-80 字符', 'INVALID_PARAMETER')
  }
  const now = nowIso()
  const stage = {
    id: MOCK_ROADMAPS.flatMap((r) => r.stages).reduce((max, s) => Math.max(max, s.id), 100) + 1,
    name,
    description: data?.description || null,
    sortOrder: data?.sortOrder != null ? Number(data.sortOrder) : 0,
    createTime: now,
    updateTime: now,
    units: []
  }
  roadmap.stages.push(stage)
  roadmap.updateTime = now
  return Promise.resolve({ ...toStageSummary(stage, roadmap.id), unitCount: 0 })
}

export function listStages(roadmapId) {
  if (!getMockEnabled()) {
    return request.get(`/api/learning/roadmaps/${roadmapId}/stages`)
  }
  if (!canLearn()) {
    return reject(403, '无权访问学习实验台', 'ACCESS_DENIED')
  }
  const roadmap = findMockRoadmap(roadmapId)
  if (!roadmap) return reject(404, '学习路线不存在', 'LEARNING_ROADMAP_NOT_FOUND')
  if (roadmap.status !== ROADMAP_STATUS.PUBLISHED && !isManageRole()) {
    return reject(404, '学习路线不存在', 'LEARNING_ROADMAP_NOT_FOUND')
  }
  const userId = currentUserId()
  const enrolled = Boolean(findEnrollment(roadmap.id, userId))
  const stages = [...roadmap.stages]
    .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
    .map((stage) => ({
      ...toStageSummary(stage, roadmap.id),
      units: [...stage.units]
        .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
        .map((u) => toUnit(u, stage.id, enrolled ? userId : -1))
    }))
  return Promise.resolve(stages)
}

export function updateStage(stageId, data) {
  if (!getMockEnabled()) {
    return request.put(`/api/learning/stages/${stageId}`, data)
  }
  if (!isManageRole()) {
    return reject(403, '无学习路线管理权限', 'LEARNING_OPERATION_DENIED')
  }
  const found = findMockStage(stageId)
  if (!found) return reject(404, '学习阶段不存在', 'LEARNING_STAGE_NOT_FOUND')
  if (found.roadmap.status === ROADMAP_STATUS.ARCHIVED) {
    return reject(409, '学习路线已归档，不可编辑阶段', 'LEARNING_ARCHIVED')
  }
  if (data?.name != null) {
    const name = String(data.name).trim()
    if (name.length < 2 || name.length > 80) {
      return reject(400, '阶段名称需为 2-80 字符', 'INVALID_PARAMETER')
    }
    found.stage.name = name
  }
  if (data?.description !== undefined) found.stage.description = data.description || null
  if (data?.sortOrder !== undefined) found.stage.sortOrder = Number(data.sortOrder) || 0
  found.stage.updateTime = nowIso()
  found.roadmap.updateTime = found.stage.updateTime
  return Promise.resolve(toStageSummary(found.stage, found.roadmap.id))
}

// ========== 单元 API ==========

export function createUnit(stageId, data) {
  if (!getMockEnabled()) {
    return request.post(`/api/learning/stages/${stageId}/units`, data)
  }
  if (!isManageRole()) {
    return reject(403, '无学习路线管理权限', 'LEARNING_OPERATION_DENIED')
  }
  const found = findMockStage(stageId)
  if (!found) return reject(404, '学习阶段不存在', 'LEARNING_STAGE_NOT_FOUND')
  if (found.roadmap.status === ROADMAP_STATUS.ARCHIVED) {
    return reject(409, '学习路线已归档，不可添加单元', 'LEARNING_ARCHIVED')
  }
  const title = String(data?.title || '').trim()
  if (title.length < 2 || title.length > 120) {
    return reject(400, '单元标题需为 2-120 字符', 'INVALID_PARAMETER')
  }
  if (data?.description && String(data.description).length > 2000) {
    return reject(400, '单元描述最多 2000 字符', 'INVALID_PARAMETER')
  }
  const now = nowIso()
  const unit = {
    id: MOCK_ROADMAPS.flatMap((r) => r.stages).flatMap((s) => s.units).reduce((max, u) => Math.max(max, u.id), 300) + 1,
    title,
    description: data?.description || null,
    sortOrder: data?.sortOrder != null ? Number(data.sortOrder) : 0,
    templateId: null,
    templateName: null,
    createTime: now,
    updateTime: now
  }
  found.stage.units.push(unit)
  found.stage.updateTime = now
  found.roadmap.updateTime = now
  return Promise.resolve(toUnit(unit, found.stage.id, -1))
}

export function updateUnit(unitId, data) {
  if (!getMockEnabled()) {
    return request.put(`/api/learning/units/${unitId}`, data)
  }
  if (!isManageRole()) {
    return reject(403, '无学习路线管理权限', 'LEARNING_OPERATION_DENIED')
  }
  const found = findMockUnit(unitId)
  if (!found) return reject(404, '学习单元不存在', 'LEARNING_UNIT_NOT_FOUND')
  if (found.roadmap.status === ROADMAP_STATUS.ARCHIVED) {
    return reject(409, '学习路线已归档，不可编辑单元', 'LEARNING_ARCHIVED')
  }
  if (data?.title != null) {
    const title = String(data.title).trim()
    if (title.length < 2 || title.length > 120) {
      return reject(400, '单元标题需为 2-120 字符', 'INVALID_PARAMETER')
    }
    found.unit.title = title
  }
  if (data?.description !== undefined) found.unit.description = data.description || null
  if (data?.sortOrder !== undefined) found.unit.sortOrder = Number(data.sortOrder) || 0
  found.unit.updateTime = nowIso()
  found.roadmap.updateTime = found.unit.updateTime
  return Promise.resolve(toUnit(found.unit, found.stage.id, currentUserId()))
}

// ========== 成员学习 API ==========

export function enrollRoadmap(roadmapId) {
  if (!getMockEnabled()) {
    return request.post(`/api/learning/roadmaps/${roadmapId}/enroll`)
  }
  if (!canLearn()) {
    return reject(403, '无权访问学习实验台', 'ACCESS_DENIED')
  }
  const roadmap = findMockRoadmap(roadmapId)
  if (!roadmap) return reject(404, '学习路线不存在', 'LEARNING_ROADMAP_NOT_FOUND')
  if (roadmap.status === ROADMAP_STATUS.DRAFT) {
    return reject(404, '学习路线不存在', 'LEARNING_ROADMAP_NOT_FOUND')
  }
  if (roadmap.status === ROADMAP_STATUS.ARCHIVED) {
    return reject(409, '学习路线已归档', 'LEARNING_ARCHIVED')
  }

  const userId = currentUserId()
  let enrollment = findEnrollment(roadmap.id, userId)
  if (!enrollment) {
    const now = nowIso()
    enrollment = {
      id: MOCK_ENROLLMENTS.reduce((max, e) => Math.max(max, e.id), 0) + 1,
      roadmapId: roadmap.id,
      userId,
      startedAt: now,
      completedAt: null,
      createTime: now,
      updateTime: now
    }
    MOCK_ENROLLMENTS.push(enrollment)
  }
  return Promise.resolve(toEnrollmentRecord(enrollment, roadmap, userId))
}

function recomputeEnrollment(roadmap, userId) {
  const enrollment = findEnrollment(roadmap.id, userId)
  if (!enrollment) return null
  const { totalUnitCount, completedUnitCount: completed } = computeProgress(roadmap, userId)
  const status = deriveLearningStatus(completed, totalUnitCount)
  enrollment.completedAt = status === LEARNING_STATUS.COMPLETED ? (enrollment.completedAt || nowIso()) : null
  enrollment.updateTime = nowIso()
  return toEnrollmentRecord(enrollment, roadmap, userId)
}

export function completeUnit(unitId) {
  if (!getMockEnabled()) {
    return request.post(`/api/learning/units/${unitId}/complete`)
  }
  if (!canLearn()) {
    return reject(403, '无权访问学习实验台', 'ACCESS_DENIED')
  }
  const found = findMockUnit(unitId)
  if (!found) return reject(404, '学习单元不存在', 'LEARNING_UNIT_NOT_FOUND')
  const { roadmap } = found
  if (roadmap.status === ROADMAP_STATUS.ARCHIVED) {
    return reject(409, '学习路线已归档', 'LEARNING_ARCHIVED')
  }
  const userId = currentUserId()
  if (!findEnrollment(roadmap.id, userId)) {
    return reject(400, '尚未开始学习该路线', 'LEARNING_NOT_ENROLLED')
  }
  if (countUnits(roadmap) === 0) {
    return reject(409, '空路线无法标记完成', 'LEARNING_EMPTY_ROADMAP')
  }

  if (!isUnitCompleted(found.unit.id, userId)) {
    MOCK_UNIT_COMPLETIONS.push({ unitId: found.unit.id, roadmapId: roadmap.id, userId, completedAt: nowIso() })
  }
  const record = recomputeEnrollment(roadmap, userId)
  return Promise.resolve({
    unitId: found.unit.id,
    completed: true,
    roadmapProgress: {
      roadmapId: roadmap.id,
      status: record.status,
      progress: record.progress,
      completedUnitCount: record.completedUnitCount,
      totalUnitCount: record.totalUnitCount
    }
  })
}

export function cancelUnitComplete(unitId) {
  if (!getMockEnabled()) {
    return request.delete(`/api/learning/units/${unitId}/complete`)
  }
  if (!canLearn()) {
    return reject(403, '无权访问学习实验台', 'ACCESS_DENIED')
  }
  const found = findMockUnit(unitId)
  if (!found) return reject(404, '学习单元不存在', 'LEARNING_UNIT_NOT_FOUND')
  const { roadmap } = found
  const userId = currentUserId()
  if (!findEnrollment(roadmap.id, userId)) {
    return reject(400, '尚未开始学习该路线', 'LEARNING_NOT_ENROLLED')
  }

  const idx = MOCK_UNIT_COMPLETIONS.findIndex((c) => c.unitId === found.unit.id && c.userId === userId)
  if (idx >= 0) MOCK_UNIT_COMPLETIONS.splice(idx, 1)
  const record = recomputeEnrollment(roadmap, userId)
  return Promise.resolve({
    unitId: found.unit.id,
    completed: false,
    roadmapProgress: {
      roadmapId: roadmap.id,
      status: record.status,
      progress: record.progress,
      completedUnitCount: record.completedUnitCount,
      totalUnitCount: record.totalUnitCount
    }
  })
}

export function listMyRoadmaps(params = {}) {
  if (!getMockEnabled()) {
    return request.get('/api/learning/my-roadmaps', { params })
  }
  if (!canLearn()) {
    return reject(403, '无权访问学习实验台', 'ACCESS_DENIED')
  }
  const userId = currentUserId()
  let list = MOCK_ENROLLMENTS.filter((e) => e.userId === userId)
    .map(toMyRoadmapItem)
    .filter(Boolean)
    .sort((a, b) => Date.parse(b.updateTime) - Date.parse(a.updateTime))

  const page = Number(params.page) > 0 ? Number(params.page) : 1
  const pageSize = Number(params.pageSize) > 0 ? Number(params.pageSize) : 20
  const start = (page - 1) * pageSize
  return Promise.resolve({
    total: list.length,
    page,
    pageSize,
    list: list.slice(start, start + pageSize)
  })
}

// ========== 供首页 learning 区域复用的 mock 摘要（契约 §8） ==========

/**
 * 当前用户学习进度摘要与最近 3 条路线。
 * 仅 mock 模式使用；真实模式由 /api/workbench/overview 返回。
 */
export function buildMockWorkbenchLearning() {
  const userId = currentUserId()
  const items = MOCK_ENROLLMENTS.filter((e) => e.userId === userId)
    .map(toMyRoadmapItem)
    .filter(Boolean)
    .sort((a, b) => Date.parse(b.updateTime) - Date.parse(a.updateTime))

  return {
    state: 'READY',
    inProgressCount: items.filter((i) => i.status === LEARNING_STATUS.IN_PROGRESS).length,
    completedCount: items.filter((i) => i.status === LEARNING_STATUS.COMPLETED).length,
    list: items.slice(0, 3).map((i) => ({
      roadmapId: i.roadmapId,
      roadmapTitle: i.roadmapTitle,
      roadmapDifficulty: i.roadmapDifficulty,
      status: i.status,
      progress: i.progress,
      completedUnitCount: i.completedUnitCount,
      totalUnitCount: i.totalUnitCount,
      updateTime: i.updateTime
    }))
  }
}

export { MOCK_CURRENT_USER_ID }