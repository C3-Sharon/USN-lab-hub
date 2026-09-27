import request from '@/utils/request'
import { getMockEnabled } from '@/utils/mock'

/**
 * 项目工作台 API
 *
 * W39 真实端点：
 *   POST   /api/projects           创建项目
 *   GET    /api/projects           分页查询可见项目
 *   GET    /api/projects/{id}      项目详情（含成员列表）
 *   POST   /api/projects/{id}/members  添加成员
 *
 * 契约来源：docs/contracts/PROJECT_WORKSPACE_API.md（FROZEN）
 * 后端实际字段：ProjectSummaryVO / ProjectDetailVO / ProjectPageVO / ProjectMemberVO
 *
 * mock 开关：VITE_USE_MOCK=true 时走本地 mock（字段与契约一致），默认 false 走真实接口。
 */

// ========== 常量 ==========
export const PROJECT_STATUS = Object.freeze({
  PREPARING: 'PREPARING',
  ACTIVE: 'ACTIVE',
  PAUSED: 'PAUSED',
  COMPLETED: 'COMPLETED',
  ARCHIVED: 'ARCHIVED'
})

export const PROJECT_STATUS_META = Object.freeze({
  [PROJECT_STATUS.PREPARING]: { label: '筹备中', tagType: 'info' },
  [PROJECT_STATUS.ACTIVE]: { label: '进行中', tagType: 'success' },
  [PROJECT_STATUS.PAUSED]: { label: '已暂停', tagType: 'warning' },
  [PROJECT_STATUS.COMPLETED]: { label: '已完成', tagType: 'primary' },
  [PROJECT_STATUS.ARCHIVED]: { label: '已归档', tagType: 'info' }
})

export const PROJECT_ROLE = Object.freeze({
  OWNER: 'OWNER',
  MAINTAINER: 'MAINTAINER',
  MEMBER: 'MEMBER',
  OBSERVER: 'OBSERVER'
})

export const PROJECT_ROLE_META = Object.freeze({
  [PROJECT_ROLE.OWNER]: { label: '负责人', tagType: 'danger' },
  [PROJECT_ROLE.MAINTAINER]: { label: '维护者', tagType: 'warning' },
  [PROJECT_ROLE.MEMBER]: { label: '成员', tagType: 'primary' },
  [PROJECT_ROLE.OBSERVER]: { label: '观察者', tagType: 'info' }
})

// ========== Mock 数据 ==========
const MOCK_PROJECTS = [
  {
    id: 10,
    code: 'PROJ-001',
    name: '智能气象站项目',
    summary: '基于 ESP32 的校园气象监测站，采集温湿度、气压和光照数据。',
    category: 'hardware_project',
    status: 'PREPARING',
    coverUrl: null,
    myRole: 'OWNER',
    memberCount: 2,
    createTime: '2026-09-21T10:30:00',
    updateTime: '2026-09-21T11:00:00'
  },
  {
    id: 5,
    code: 'PM-001',
    name: '功率监测演示项目',
    summary: '首轮 IoT 演示项目，含 PM-001 设备实时监控与告警闭环。',
    category: 'iot_demo',
    status: 'ACTIVE',
    coverUrl: null,
    myRole: 'MEMBER',
    memberCount: 3,
    createTime: '2026-08-15T14:00:00',
    updateTime: '2026-09-20T09:15:00'
  }
]

const MOCK_MEMBERS = [
  { userId: 1, memberId: 'admin', name: '系统管理员', projectRole: 'OWNER', joinedAt: '2026-09-21T10:30:00' },
  { userId: 2, memberId: '20260001', name: '张同学', projectRole: 'MEMBER', joinedAt: '2026-09-21T11:00:00' }
]

/**
 * mock 成功响应：与真实模式对齐 —— request.js 拦截器会把
 * { code: 200, data } 解包为 data，因此 mock 也直接 resolve data，
 * 页面层拿到的数据结构在两种模式下完全一致。
 * mock 失败响应：reject { code, msg, reason }，与拦截器 reject(payload) 一致。
 */
function mockResponse(data) {
  return Promise.resolve(data)
}

function mockPageResponse(list, total = list.length, page = 1, pageSize = 10) {
  return mockResponse({ total, page, pageSize, list })
}

function filterMockList({ status, keyword }) {
  let list = [...MOCK_PROJECTS]
  if (status) list = list.filter((p) => p.status === status)
  if (keyword) {
    const k = String(keyword).toLowerCase()
    list = list.filter((p) => p.code.toLowerCase().includes(k) || p.name.toLowerCase().includes(k))
  }
  return list
}

// ========== API ==========

export function createProject(data) {
  if (getMockEnabled()) {
    const exists = MOCK_PROJECTS.some((p) => p.code === data.code)
    if (exists) {
      return Promise.reject({ code: 409, msg: '项目编号已存在', reason: 'PROJECT_CODE_DUPLICATE' })
    }
    const newProject = {
      id: Date.now(),
      code: data.code,
      name: data.name,
      summary: data.summary || '',
      category: data.category || '',
      status: 'PREPARING',
      coverUrl: null,
      myRole: 'OWNER',
      memberCount: 1,
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString()
    }
    MOCK_PROJECTS.unshift(newProject)
    return mockResponse(newProject)
  }
  return request.post('/api/projects', data)
}

export function listProjects(params = {}) {
  if (getMockEnabled()) {
    const { page = 1, pageSize = 10, status, keyword } = params
    const filtered = filterMockList({ status, keyword })
    return mockPageResponse(filtered, filtered.length, page, pageSize)
  }
  return request.get('/api/projects', { params })
}

export function getProjectDetail(id) {
  if (getMockEnabled()) {
    const project = MOCK_PROJECTS.find((p) => p.id === Number(id))
    if (!project) {
      return Promise.reject({ code: 404, msg: '项目不存在', reason: 'PROJECT_NOT_FOUND' })
    }
    return mockResponse({ ...project, members: [...MOCK_MEMBERS] })
  }
  return request.get(`/api/projects/${id}`)
}

export function addProjectMember(projectId, data) {
  if (getMockEnabled()) {
    const project = MOCK_PROJECTS.find((p) => p.id === Number(projectId))
    if (!project) {
      return Promise.reject({ code: 404, msg: '项目不存在', reason: 'PROJECT_NOT_FOUND' })
    }
    const existing = MOCK_MEMBERS.find((m) => m.memberId === data.memberId)
    if (existing) {
      if (existing.projectRole === data.projectRole) {
        return mockResponse(existing)
      }
      return Promise.reject({
        code: 409,
        msg: '该成员已在项目中，角色不同，请使用角色变更功能',
        reason: 'ALREADY_MEMBER_DIFFERENT_ROLE'
      })
    }
    const newMember = {
      userId: Date.now(),
      memberId: data.memberId,
      name: `成员-${data.memberId}`,
      projectRole: data.projectRole,
      joinedAt: new Date().toISOString()
    }
    MOCK_MEMBERS.push(newMember)
    project.memberCount = MOCK_MEMBERS.length
    return mockResponse(newMember)
  }
  return request.post(`/api/projects/${projectId}/members`, data)
}
