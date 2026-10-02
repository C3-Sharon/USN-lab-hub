import request from '@/utils/request'
import { getMockEnabled } from '@/utils/mock'
import { findMockProject } from '@/api/projects'

/**
 * 项目里程碑 API
 *
 * W40 真实端点（契约 §8）：
 *   POST /api/projects/{projectId}/milestones                     创建里程碑
 *   GET  /api/projects/{projectId}/milestones                     里程碑列表（含任务统计）
 *   PUT  /api/projects/{projectId}/milestones/{milestoneId}/status 更新里程碑状态
 *
 * 契约来源：docs/contracts/PROJECT_WORKSPACE_API.md §8（FROZEN）
 *
 * mock 开关：VITE_USE_MOCK=true 时走本地 mock（字段与契约一致），默认 false 走真实接口。
 * mock 成功响应直接 resolve data，失败 reject { code, msg, reason }，与 request.js
 * 拦截器的解包/拒绝行为保持一致。
 */

// ========== 枚举 ==========
export const MILESTONE_STATUS = Object.freeze({
  PLANNED: 'PLANNED',
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED'
})

export const MILESTONE_STATUS_META = Object.freeze({
  [MILESTONE_STATUS.PLANNED]: { label: '已规划', tagType: 'info' },
  [MILESTONE_STATUS.IN_PROGRESS]: { label: '进行中', tagType: 'primary' },
  [MILESTONE_STATUS.COMPLETED]: { label: '已完成', tagType: 'success' }
})

// PLANNED → IN_PROGRESS → COMPLETED（单向，COMPLETED 为终态）
export const MILESTONE_TRANSITIONS = Object.freeze({
  [MILESTONE_STATUS.PLANNED]: [MILESTONE_STATUS.IN_PROGRESS],
  [MILESTONE_STATUS.IN_PROGRESS]: [MILESTONE_STATUS.COMPLETED],
  [MILESTONE_STATUS.COMPLETED]: []
})

export function canTransitionMilestone(from, to) {
  return (MILESTONE_TRANSITIONS[from] || []).includes(to)
}

/** 里程碑的下一状态（终态返回 null），供「推进」按钮使用 */
export function nextMilestoneStatus(status) {
  return (MILESTONE_TRANSITIONS[status] || [])[0] || null
}

// ========== Mock 数据 ==========
const MOCK_MILESTONES = {
  10: [
    {
      id: 101,
      projectId: 10,
      name: '原型设计与评审',
      description: '完成硬件原型设计并组织评审',
      status: 'IN_PROGRESS',
      startDate: '2026-09-22',
      endDate: '2026-10-06',
      sortOrder: 1,
      taskCount: 4,
      taskDone: 1,
      createTime: '2026-09-22T10:00:00',
      updateTime: '2026-09-24T09:00:00'
    },
    {
      id: 102,
      projectId: 10,
      name: '数据采集联调',
      description: 'ESP32 采集链路联调与传感器校准',
      status: 'PLANNED',
      startDate: '2026-10-07',
      endDate: '2026-10-20',
      sortOrder: 2,
      taskCount: 1,
      taskDone: 0,
      createTime: '2026-09-22T10:05:00',
      updateTime: '2026-09-22T10:05:00'
    }
  ],
  5: [
    {
      id: 501,
      projectId: 5,
      name: 'PM-001 演示交付',
      description: '完成功率监测演示与告警闭环',
      status: 'COMPLETED',
      startDate: '2026-08-18',
      endDate: '2026-09-10',
      sortOrder: 1,
      taskCount: 2,
      taskDone: 1,
      createTime: '2026-08-18T09:00:00',
      updateTime: '2026-09-10T18:00:00'
    }
  ]
}

function isManager(role) {
  return role === 'OWNER' || role === 'MAINTAINER'
}

function guardProject(projectId) {
  const project = findMockProject(projectId)
  if (!project) {
    return { error: { code: 404, msg: '项目不存在或无权访问', reason: 'PROJECT_NOT_FOUND' } }
  }
  return { project }
}

function guardProjectMutable(project) {
  if (project.status === 'PAUSED') {
    return { code: 409, msg: '项目已暂停，禁止修改', reason: 'PROJECT_PAUSED' }
  }
  if (project.status === 'COMPLETED') {
    return { code: 409, msg: '项目已完成，禁止修改', reason: 'PROJECT_COMPLETED' }
  }
  if (project.status === 'ARCHIVED') {
    return { code: 409, msg: '项目已归档，禁止修改', reason: 'PROJECT_ARCHIVED' }
  }
  return null
}

function mockList(projectId) {
  const key = Number(projectId)
  if (!MOCK_MILESTONES[key]) MOCK_MILESTONES[key] = []
  return MOCK_MILESTONES[key]
}

// ========== API ==========

export function createMilestone(projectId, data) {
  if (!getMockEnabled()) {
    return request.post(`/api/projects/${projectId}/milestones`, data)
  }
  const { project, error } = guardProject(projectId)
  if (error) return Promise.reject(error)
  if (!isManager(project.myRole)) {
    return Promise.reject({ code: 403, msg: '当前角色无权创建里程碑', reason: 'PROJECT_OPERATION_DENIED' })
  }
  const blocked = guardProjectMutable(project)
  if (blocked) return Promise.reject(blocked)

  const name = String(data?.name || '').trim()
  if (name.length < 2 || name.length > 80) {
    return Promise.reject({ code: 400, msg: '里程碑名称需为 2-80 字符', reason: 'INVALID_PARAMETER' })
  }

  const list = mockList(projectId)
  const item = {
    id: Math.floor(Date.now() / 1000) * 10 + list.length,
    projectId: Number(projectId),
    name,
    description: data?.description || null,
    status: data?.status || MILESTONE_STATUS.PLANNED,
    startDate: data?.startDate || null,
    endDate: data?.endDate || null,
    sortOrder: Number.isInteger(data?.sortOrder) ? data.sortOrder : 0,
    taskCount: 0,
    taskDone: 0,
    createTime: new Date().toISOString(),
    updateTime: new Date().toISOString()
  }
  list.push(item)
  return Promise.resolve(item)
}

export function listMilestones(projectId, params = {}) {
  if (!getMockEnabled()) {
    return request.get(`/api/projects/${projectId}/milestones`, { params })
  }
  const { error } = guardProject(projectId)
  if (error) return Promise.reject(error)

  let list = [...mockList(projectId)]
  if (params.status) list = list.filter((m) => m.status === params.status)
  list.sort((a, b) => (a.sortOrder - b.sortOrder) || (a.id - b.id))
  return Promise.resolve(list)
}

export function updateMilestoneStatus(projectId, milestoneId, status) {
  if (!getMockEnabled()) {
    return request.put(`/api/projects/${projectId}/milestones/${milestoneId}/status`, { status })
  }
  const { project, error } = guardProject(projectId)
  if (error) return Promise.reject(error)
  if (!isManager(project.myRole)) {
    return Promise.reject({ code: 403, msg: '当前角色无权更新里程碑状态', reason: 'PROJECT_OPERATION_DENIED' })
  }
  const blocked = guardProjectMutable(project)
  if (blocked) return Promise.reject(blocked)

  const item = mockList(projectId).find((m) => m.id === Number(milestoneId))
  if (!item) {
    return Promise.reject({ code: 404, msg: '里程碑不存在', reason: 'MILESTONE_NOT_FOUND' })
  }
  if (!canTransitionMilestone(item.status, status)) {
    return Promise.reject({ code: 409, msg: '里程碑状态转换非法', reason: 'MILESTONE_INVALID_TRANSITION' })
  }
  item.status = status
  item.updateTime = new Date().toISOString()
  return Promise.resolve({ id: item.id, status: item.status, updateTime: item.updateTime })
}

// ========== 供 tasks.js 复用的 mock 内部入口 ==========

/** 校验里程碑存在且属于该项目（mock 模式下 createTask / updateTask 使用） */
export function findMockMilestone(projectId, milestoneId) {
  return mockList(projectId).find((m) => m.id === Number(milestoneId)) || null
}

/** 任务增删改后重算里程碑的任务统计（taskCount / taskDone），CANCELED 不计入 */
export function recomputeMilestoneStats(projectId, taskList) {
  for (const milestone of mockList(projectId)) {
    const related = taskList.filter((t) => t.milestoneId === milestone.id && t.status !== 'CANCELED')
    milestone.taskCount = related.length
    milestone.taskDone = related.filter((t) => t.status === 'DONE').length
  }
}