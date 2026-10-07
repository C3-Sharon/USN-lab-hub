import request from '@/utils/request'
import { getMockEnabled } from '@/utils/mock'
import { userStore } from '@/store/user'
import { findMockProject, getMockProjectMembers } from '@/api/projects'
import { findMockMilestone, recomputeMilestoneStats } from '@/api/milestones'

/**
 * 项目任务 API
 *
 * W40 真实端点（契约 §9）：
 *   POST /api/projects/{projectId}/tasks                     创建任务
 *   GET  /api/projects/{projectId}/tasks                     任务列表（看板，分页/筛选/排序）
 *   GET  /api/projects/{projectId}/tasks/{taskId}            任务详情
 *   PUT  /api/projects/{projectId}/tasks/{taskId}            编辑任务基本信息（乐观锁）
 *   PUT  /api/projects/{projectId}/tasks/{taskId}/status     任务状态变更（乐观锁）
 *
 * 契约来源：docs/contracts/PROJECT_WORKSPACE_API.md §9 / §10 / §13（FROZEN）
 *
 * mock 开关：VITE_USE_MOCK=true 时走本地 mock（字段与契约一致），默认 false 走真实接口。
 * mock 成功响应直接 resolve data，失败 reject { code, msg, reason }，与 request.js
 * 拦截器的解包/拒绝行为保持一致。
 */

// ========== 枚举 ==========

export const TASK_STATUS = Object.freeze({
  TODO: 'TODO',
  IN_PROGRESS: 'IN_PROGRESS',
  BLOCKED: 'BLOCKED',
  DONE: 'DONE',
  CANCELED: 'CANCELED'
})

export const TASK_STATUS_META = Object.freeze({
  [TASK_STATUS.TODO]: { label: '待开始', tagType: 'info' },
  [TASK_STATUS.IN_PROGRESS]: { label: '进行中', tagType: 'primary' },
  [TASK_STATUS.BLOCKED]: { label: '已阻塞', tagType: 'danger' },
  [TASK_STATUS.DONE]: { label: '已完成', tagType: 'success' },
  [TASK_STATUS.CANCELED]: { label: '已取消', tagType: 'info' }
})

/** 看板默认四列（CANCELED 不在默认看板展示，需显式筛选） */
export const BOARD_STATUSES = Object.freeze([
  TASK_STATUS.TODO,
  TASK_STATUS.IN_PROGRESS,
  TASK_STATUS.BLOCKED,
  TASK_STATUS.DONE
])

export const TASK_PRIORITY = Object.freeze({
  LOW: 'LOW',
  MEDIUM: 'MEDIUM',
  HIGH: 'HIGH'
})

export const TASK_PRIORITY_META = Object.freeze({
  [TASK_PRIORITY.LOW]: { label: '低', tagType: 'info', rank: 1 },
  [TASK_PRIORITY.MEDIUM]: { label: '中', tagType: 'warning', rank: 2 },
  [TASK_PRIORITY.HIGH]: { label: '高', tagType: 'danger', rank: 3 }
})

/** 契约 §9.3 合法状态转换（终态 DONE / CANCELED 不可转出） */
export const TASK_TRANSITIONS = Object.freeze({
  [TASK_STATUS.TODO]: [TASK_STATUS.IN_PROGRESS, TASK_STATUS.CANCELED],
  [TASK_STATUS.IN_PROGRESS]: [TASK_STATUS.BLOCKED, TASK_STATUS.DONE, TASK_STATUS.CANCELED],
  [TASK_STATUS.BLOCKED]: [TASK_STATUS.IN_PROGRESS, TASK_STATUS.CANCELED],
  [TASK_STATUS.DONE]: [],
  [TASK_STATUS.CANCELED]: []
})

/**
 * MEMBER（任务负责人）可执行的状态转换（契约 §13）。
 * 负责人不能取消任务，取消仅 OWNER / MAINTAINER。
 */
export const MEMBER_TRANSITIONS = Object.freeze({
  [TASK_STATUS.TODO]: [TASK_STATUS.IN_PROGRESS],
  [TASK_STATUS.IN_PROGRESS]: [TASK_STATUS.BLOCKED, TASK_STATUS.DONE],
  [TASK_STATUS.BLOCKED]: [TASK_STATUS.IN_PROGRESS],
  [TASK_STATUS.DONE]: [],
  [TASK_STATUS.CANCELED]: []
})

export function canTransitionTask(from, to) {
  return (TASK_TRANSITIONS[from] || []).includes(to)
}

export function nextTaskStatuses(from) {
  return [...(TASK_TRANSITIONS[from] || [])]
}

export function isTerminalTaskStatus(status) {
  return (TASK_TRANSITIONS[status] || []).length === 0
}

// ========== Mock 数据 ==========

/** mock 模式下未登录/无 id 时假定的当前用户（张同学），用于「负责人」权限判定 */
const MOCK_CURRENT_USER_ID = 2

const MOCK_TASKS = {
  10: [
    {
      id: 1001,
      projectId: 10,
      milestoneId: 101,
      title: '设计温湿度传感器电路',
      description: '完成 DHT22 传感器的信号调理和 ADC 采集电路设计，输出原理图与 BOM。',
      status: 'TODO',
      assigneeUserId: 2,
      assigneeName: '张同学',
      priority: 'HIGH',
      dueDate: '2026-10-05',
      blockReason: null,
      version: 1,
      createdBy: 1,
      createTime: '2026-09-25T10:30:00',
      updateTime: '2026-09-25T10:30:00'
    },
    {
      id: 1002,
      projectId: 10,
      milestoneId: 101,
      title: 'PCB 布局布线',
      description: '根据原理图完成两层板布局布线并导出 Gerber。',
      status: 'IN_PROGRESS',
      assigneeUserId: 2,
      assigneeName: '张同学',
      priority: 'MEDIUM',
      dueDate: '2026-10-02',
      blockReason: null,
      version: 2,
      createdBy: 1,
      createTime: '2026-09-25T11:00:00',
      updateTime: '2026-09-26T08:00:00'
    },
    {
      id: 1003,
      projectId: 10,
      milestoneId: 101,
      title: '传感器采购到货确认',
      description: '跟进 DHT22 与气压传感器的采购进度。',
      status: 'BLOCKED',
      assigneeUserId: 2,
      assigneeName: '张同学',
      priority: 'HIGH',
      dueDate: '2026-09-30',
      blockReason: '等待传感器到货，供应商延期约一周',
      version: 3,
      createdBy: 1,
      createTime: '2026-09-24T09:00:00',
      updateTime: '2026-09-27T16:20:00'
    },
    {
      id: 1004,
      projectId: 10,
      milestoneId: 101,
      title: '需求评审会议纪要',
      description: '整理原型设计评审会议的结论与待办。',
      status: 'DONE',
      assigneeUserId: 2,
      assigneeName: '张同学',
      priority: 'MEDIUM',
      dueDate: '2026-09-24',
      blockReason: null,
      version: 4,
      createdBy: 1,
      createTime: '2026-09-22T14:00:00',
      updateTime: '2026-09-24T17:30:00'
    },
    {
      id: 1005,
      projectId: 10,
      milestoneId: 102,
      title: '搭建数据采集联调环境',
      description: '准备 ESP32 开发板与串口调试环境，暂未分配负责人。',
      status: 'TODO',
      assigneeUserId: null,
      assigneeName: null,
      priority: 'LOW',
      dueDate: '2026-10-15',
      blockReason: null,
      version: 1,
      createdBy: 1,
      createTime: '2026-09-28T09:30:00',
      updateTime: '2026-09-28T09:30:00'
    },
    {
      id: 1006,
      projectId: 10,
      milestoneId: 102,
      title: '旧版采集脚本清理',
      description: '已确认不再使用，任务取消。',
      status: 'CANCELED',
      assigneeUserId: null,
      assigneeName: null,
      priority: 'LOW',
      dueDate: null,
      blockReason: null,
      version: 2,
      createdBy: 1,
      createTime: '2026-09-23T09:00:00',
      updateTime: '2026-09-26T10:00:00'
    }
  ],
  5: [
    {
      id: 5001,
      projectId: 5,
      milestoneId: 501,
      title: '整理 PM-001 演示脚本',
      description: '梳理功率监测演示的讲解流程与数据看点。',
      status: 'IN_PROGRESS',
      assigneeUserId: 2,
      assigneeName: '张同学',
      priority: 'MEDIUM',
      dueDate: '2026-09-29',
      blockReason: null,
      version: 2,
      createdBy: 1,
      createTime: '2026-09-18T10:00:00',
      updateTime: '2026-09-20T09:15:00'
    },
    {
      id: 5002,
      projectId: 5,
      milestoneId: 501,
      title: '录制演示视频',
      description: '录制 3 分钟演示视频并归档。',
      status: 'DONE',
      assigneeUserId: 2,
      assigneeName: '张同学',
      priority: 'LOW',
      dueDate: '2026-09-28',
      blockReason: null,
      version: 2,
      createdBy: 1,
      createTime: '2026-09-15T10:00:00',
      updateTime: '2026-09-19T18:00:00'
    }
  ]
}

// ========== Mock 辅助 ==========

function isManager(role) {
  return role === 'OWNER' || role === 'MAINTAINER'
}

function reject(code, msg, reason, data = null) {
  return Promise.reject({ code, msg, reason, data })
}

function guardProjectMutable(project) {
  if (project.status === 'PAUSED') {
    return { code: 409, msg: '项目已暂停，禁止修改', reason: 'PROJECT_PAUSED', data: null }
  }
  if (project.status === 'COMPLETED') {
    return { code: 409, msg: '项目已完成，禁止修改', reason: 'PROJECT_COMPLETED', data: null }
  }
  if (project.status === 'ARCHIVED') {
    return { code: 409, msg: '项目已归档，禁止修改', reason: 'PROJECT_ARCHIVED', data: null }
  }
  return null
}

function currentUserId() {
  const id = userStore?.userInfo?.id
  return id == null ? MOCK_CURRENT_USER_ID : Number(id)
}

function mockList(projectId) {
  const key = Number(projectId)
  if (!MOCK_TASKS[key]) MOCK_TASKS[key] = []
  return MOCK_TASKS[key]
}

function resolveAssigneeName(userId) {
  if (userId == null) return null
  const member = getMockProjectMembers().find((m) => Number(m.userId) === Number(userId))
  return member ? member.name : null
}

function resolveMilestoneName(projectId, milestoneId) {
  if (milestoneId == null) return null
  const milestone = findMockMilestone(projectId, milestoneId)
  return milestone ? milestone.name : null
}

/** 补全返回时冗余字段（milestoneName / assigneeName），不修改原始 mock 对象 */
function decorate(task) {
  return {
    ...task,
    milestoneName: resolveMilestoneName(task.projectId, task.milestoneId),
    assigneeName: task.assigneeName ?? resolveAssigneeName(task.assigneeUserId)
  }
}

function compareTasks(a, b, sortBy, sortOrder) {
  const dir = sortOrder === 'asc' ? 1 : -1
  let result = 0
  if (sortBy === 'priority') {
    result = (TASK_PRIORITY_META[a.priority]?.rank || 0) - (TASK_PRIORITY_META[b.priority]?.rank || 0)
  } else if (sortBy === 'dueDate') {
    const av = a.dueDate || '9999-12-31'
    const bv = b.dueDate || '9999-12-31'
    result = av < bv ? -1 : av > bv ? 1 : 0
  } else if (sortBy === 'updateTime') {
    result = Date.parse(a.updateTime) - Date.parse(b.updateTime)
  } else {
    result = Date.parse(a.createTime) - Date.parse(b.createTime)
  }
  if (result === 0) result = a.id - b.id
  return result * dir
}

// ========== API ==========

export function createTask(projectId, data) {
  if (!getMockEnabled()) {
    return request.post(`/api/projects/${projectId}/tasks`, data)
  }
  const project = findMockProject(projectId)
  if (!project) return reject(404, '项目不存在或无权访问', 'PROJECT_NOT_FOUND')
  if (!isManager(project.myRole)) {
    return reject(403, '当前角色无权创建任务', 'PROJECT_OPERATION_DENIED')
  }
  const blocked = guardProjectMutable(project)
  if (blocked) return Promise.reject(blocked)

  const title = String(data?.title || '').trim()
  if (title.length < 2 || title.length > 120) {
    return reject(400, '任务标题需为 2-120 字符', 'INVALID_PARAMETER')
  }
  if (data?.description && String(data.description).length > 2000) {
    return reject(400, '任务说明最多 2000 字符', 'INVALID_PARAMETER')
  }
  if (data?.assigneeUserId != null && !resolveAssigneeName(data.assigneeUserId)) {
    return reject(400, '负责人不是项目成员', 'ASSIGNEE_NOT_MEMBER')
  }
  if (data?.milestoneId != null && !findMockMilestone(projectId, data.milestoneId)) {
    return reject(400, '里程碑不存在或不属于该项目', 'MILESTONE_NOT_FOUND')
  }

  const list = mockList(projectId)
  const now = new Date().toISOString()
  const task = {
    id: list.reduce((max, t) => Math.max(max, t.id), 9000) + 1,
    projectId: Number(projectId),
    milestoneId: data?.milestoneId != null ? Number(data.milestoneId) : null,
    title,
    description: data?.description || null,
    status: TASK_STATUS.TODO,
    assigneeUserId: data?.assigneeUserId != null ? Number(data.assigneeUserId) : null,
    assigneeName: resolveAssigneeName(data?.assigneeUserId),
    priority: TASK_PRIORITY_META[data?.priority] ? data.priority : TASK_PRIORITY.MEDIUM,
    dueDate: data?.dueDate || null,
    blockReason: null,
    version: 1,
    createdBy: currentUserId(),
    createTime: now,
    updateTime: now
  }
  list.push(task)
  recomputeMilestoneStats(projectId, list)
  return Promise.resolve(decorate(task))
}

export function listTasks(projectId, params = {}) {
  if (!getMockEnabled()) {
    return request.get(`/api/projects/${projectId}/tasks`, { params })
  }
  const project = findMockProject(projectId)
  if (!project) return reject(404, '项目不存在或无权访问', 'PROJECT_NOT_FOUND')

  let list = mockList(projectId).map(decorate)
  const { milestoneId, status, assigneeUserId, keyword } = params
  if (milestoneId) list = list.filter((t) => t.milestoneId === Number(milestoneId))
  if (status) list = list.filter((t) => t.status === status)
  if (assigneeUserId) list = list.filter((t) => Number(t.assigneeUserId) === Number(assigneeUserId))
  if (keyword) {
    const k = String(keyword).toLowerCase()
    list = list.filter((t) => t.title.toLowerCase().includes(k))
  }
  list.sort((a, b) => compareTasks(a, b, params.sortBy, params.sortOrder))

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

export function getTaskDetail(projectId, taskId) {
  if (!getMockEnabled()) {
    return request.get(`/api/projects/${projectId}/tasks/${taskId}`)
  }
  const project = findMockProject(projectId)
  if (!project) return reject(404, '项目不存在或无权访问', 'PROJECT_NOT_FOUND')
  const task = mockList(projectId).find((t) => t.id === Number(taskId))
  if (!task) return reject(404, '任务不存在或不属于该项目', 'TASK_NOT_FOUND')
  return Promise.resolve(decorate(task))
}

export function updateTaskStatus(projectId, taskId, payload) {
  if (!getMockEnabled()) {
    return request.put(`/api/projects/${projectId}/tasks/${taskId}/status`, payload)
  }
  const project = findMockProject(projectId)
  if (!project) return reject(404, '项目不存在或无权访问', 'PROJECT_NOT_FOUND')
  const blocked = guardProjectMutable(project)
  if (blocked) return Promise.reject(blocked)

  const task = mockList(projectId).find((t) => t.id === Number(taskId))
  if (!task) return reject(404, '任务不存在或不属于该项目', 'TASK_NOT_FOUND')

  const target = payload?.status
  if (!canTransitionTask(task.status, target)) {
    return reject(409, '非法任务状态转换', 'TASK_INVALID_TRANSITION')
  }
  if (Number(payload?.version) !== task.version) {
    return Promise.reject({
      code: 409,
      msg: '任务已被他人更新，请刷新后重试',
      reason: 'VERSION_CONFLICT',
      data: { currentVersion: task.version, taskId: task.id }
    })
  }

  const role = project.myRole
  if (!isManager(role)) {
    const own = task.assigneeUserId != null && Number(task.assigneeUserId) === currentUserId()
    const allowed = (MEMBER_TRANSITIONS[task.status] || []).includes(target)
    if (role !== 'MEMBER' || !own || !allowed) {
      return reject(403, '当前角色无权执行该状态变更', 'PROJECT_OPERATION_DENIED')
    }
  }

  if (target === TASK_STATUS.BLOCKED) {
    const reason = String(payload?.blockReason || '').trim()
    if (reason.length < 2 || reason.length > 500) {
      return reject(400, '阻塞原因需为 2-500 字符', 'BLOCK_REASON_REQUIRED')
    }
    task.blockReason = reason
  }

  task.status = target
  task.version += 1
  task.updateTime = new Date().toISOString()
  recomputeMilestoneStats(projectId, mockList(projectId))
  return Promise.resolve({
    id: task.id,
    status: task.status,
    version: task.version,
    updateTime: task.updateTime
  })
}

export function updateTask(projectId, taskId, data) {
  if (!getMockEnabled()) {
    return request.put(`/api/projects/${projectId}/tasks/${taskId}`, data)
  }
  const project = findMockProject(projectId)
  if (!project) return reject(404, '项目不存在或无权访问', 'PROJECT_NOT_FOUND')
  if (!isManager(project.myRole)) {
    return reject(403, '当前角色无权编辑任务', 'PROJECT_OPERATION_DENIED')
  }
  const blocked = guardProjectMutable(project)
  if (blocked) return Promise.reject(blocked)

  const task = mockList(projectId).find((t) => t.id === Number(taskId))
  if (!task) return reject(404, '任务不存在或不属于该项目', 'TASK_NOT_FOUND')

  if (Number(data?.version) !== task.version) {
    return Promise.reject({
      code: 409,
      msg: '任务已被他人更新，请刷新后重试',
      reason: 'VERSION_CONFLICT',
      data: { currentVersion: task.version, taskId: task.id }
    })
  }
  if (data?.title != null) {
    const title = String(data.title).trim()
    if (title.length < 2 || title.length > 120) {
      return reject(400, '任务标题需为 2-120 字符', 'INVALID_PARAMETER')
    }
    task.title = title
  }
  if (data?.description !== undefined) task.description = data.description || null
  if (data?.assigneeUserId !== undefined) {
    if (data.assigneeUserId != null && !resolveAssigneeName(data.assigneeUserId)) {
      return reject(400, '负责人不是项目成员', 'ASSIGNEE_NOT_MEMBER')
    }
    task.assigneeUserId = data.assigneeUserId != null ? Number(data.assigneeUserId) : null
    task.assigneeName = resolveAssigneeName(data.assigneeUserId)
  }
  if (data?.milestoneId !== undefined) {
    if (data.milestoneId != null && !findMockMilestone(projectId, data.milestoneId)) {
      return reject(400, '里程碑不存在或不属于该项目', 'MILESTONE_NOT_FOUND')
    }
    task.milestoneId = data.milestoneId != null ? Number(data.milestoneId) : null
  }
  if (data?.priority != null && TASK_PRIORITY_META[data.priority]) task.priority = data.priority
  if (data?.dueDate !== undefined) task.dueDate = data.dueDate || null

  task.version += 1
  task.updateTime = new Date().toISOString()
  recomputeMilestoneStats(projectId, mockList(projectId))
  return Promise.resolve(decorate(task))
}

// ========== 供首页 tasks 区域复用的 mock 摘要 ==========

/**
 * 当前用户作为负责人的任务统计与最近 5 条（契约 §11）。
 * 仅 mock 模式使用；真实模式由 /api/workbench/overview 返回。
 */
export function buildMockWorkbenchTasks() {
  const all = Object.values(MOCK_TASKS).flat()
  const mine = all.filter((t) => Number(t.assigneeUserId) === currentUserId())
  const doneThisWeek = mine.filter((t) => t.status === TASK_STATUS.DONE).length
  const list = [...mine]
    .sort((a, b) => Date.parse(b.updateTime) - Date.parse(a.updateTime))
    .slice(0, 5)
    .map((t) => {
      const project = findMockProject(t.projectId)
      return {
        id: t.id,
        projectId: t.projectId,
        projectCode: project?.code || '',
        projectName: project?.name || '',
        milestoneName: resolveMilestoneName(t.projectId, t.milestoneId),
        title: t.title,
        status: t.status,
        priority: t.priority,
        dueDate: t.dueDate,
        updateTime: t.updateTime
      }
    })
  return {
    state: 'READY',
    todo: mine.filter((t) => t.status === TASK_STATUS.TODO).length,
    inProgress: mine.filter((t) => t.status === TASK_STATUS.IN_PROGRESS).length,
    blocked: mine.filter((t) => t.status === TASK_STATUS.BLOCKED).length,
    doneThisWeek,
    list
  }
}