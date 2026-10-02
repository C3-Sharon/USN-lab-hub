import { userStore, currentRoles } from '@/store/user'
import { ROLE } from '@/utils/permission'
import { PROJECT_ROLE } from '@/api/projects'
import { TASK_STATUS, TASK_TRANSITIONS, MEMBER_TRANSITIONS } from '@/api/tasks'

/**
 * 项目内权限判定（里程碑与任务）
 *
 * 契约来源：docs/contracts/PROJECT_WORKSPACE_API.md §4.1 / §13（FROZEN）
 *
 * 说明：
 *   - 前端权限判定只用于「按钮/菜单可见性」的 UI 收敛，真正的鉴权以后端为准。
 *   - 全局角色覆盖：SYSTEM_ADMIN 覆盖项目 OWNER；TEACHER 覆盖项目只读（等价 OBSERVER）。
 *   - 任务状态变更还需满足「负责人」约束：MEMBER 只能操作分配给自己的任务。
 */

const MANAGER_ROLES = [PROJECT_ROLE.OWNER, PROJECT_ROLE.MAINTAINER]

export function getCurrentUserId() {
  const id = userStore?.userInfo?.id
  return id == null ? null : Number(id)
}

function resolveGlobalRoles(globalRoles) {
  return Array.isArray(globalRoles) ? globalRoles : currentRoles()
}

/** 计算生效的项目角色（叠加全局角色覆盖） */
export function effectiveProjectRole(projectRole, globalRoles) {
  const roles = resolveGlobalRoles(globalRoles)
  if (roles.includes(ROLE.SYSTEM_ADMIN)) return PROJECT_ROLE.OWNER
  if (roles.includes(ROLE.TEACHER)) return PROJECT_ROLE.OBSERVER
  return projectRole || null
}

export function isProjectManager(projectRole, globalRoles) {
  return MANAGER_ROLES.includes(effectiveProjectRole(projectRole, globalRoles))
}

export function isProjectMember(projectRole, globalRoles) {
  return Boolean(effectiveProjectRole(projectRole, globalRoles))
}

export function canCreateMilestone(projectRole, globalRoles) {
  return isProjectManager(projectRole, globalRoles)
}

export function canUpdateMilestoneStatus(projectRole, globalRoles) {
  return isProjectManager(projectRole, globalRoles)
}

export function canCreateTask(projectRole, globalRoles) {
  return isProjectManager(projectRole, globalRoles)
}

export function canEditTask(projectRole, globalRoles) {
  return isProjectManager(projectRole, globalRoles)
}

export function canCancelTask(projectRole, globalRoles) {
  return isProjectManager(projectRole, globalRoles)
}

function isOwnTask(task, userId) {
  return task?.assigneeUserId != null && userId != null && Number(task.assigneeUserId) === Number(userId)
}

/**
 * 当前用户对该任务可执行的目标状态列表（卡片菜单用）。
 * 综合：状态机合法性 + 项目角色 + 是否负责人。
 */
export function allowedTaskTransitions({ projectRole, globalRoles, task, currentUserId }) {
  if (!task) return []
  const legal = TASK_TRANSITIONS[task.status] || []
  if (!legal.length) return []
  if (isProjectManager(projectRole, globalRoles)) return [...legal]

  const role = effectiveProjectRole(projectRole, globalRoles)
  if (role !== PROJECT_ROLE.MEMBER) return []

  const uid = currentUserId ?? getCurrentUserId()
  if (!isOwnTask(task, uid)) return []

  const memberAllowed = MEMBER_TRANSITIONS[task.status] || []
  return legal.filter((status) => memberAllowed.includes(status))
}

export function canChangeTaskStatus({ projectRole, globalRoles, task, targetStatus, currentUserId }) {
  return allowedTaskTransitions({ projectRole, globalRoles, task, currentUserId }).includes(targetStatus)
}

/** 该任务当前是否处于终态（DONE / CANCELED，不可再变更） */
export function isTaskLocked(task) {
  return isTerminalStatus(task?.status)
}

function isTerminalStatus(status) {
  return (TASK_TRANSITIONS[status] || []).length === 0
}

export { TASK_STATUS }