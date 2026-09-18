/**
 * 权限判定工具
 *
 * 来源：W38 任务书 + UI_SPEC
 * - 旧 user.role / user.roleKey 仅做兼容，**不再作为新权限判断依据**。
 * - 全部以 user.roles[] / user.primaryRoleKey / user.primaryRoleName 为准。
 */

export const ROLE = Object.freeze({
  SYSTEM_ADMIN: 'SYSTEM_ADMIN',
  TEACHER: 'TEACHER',
  STOCK_KEEPER: 'STOCK_KEEPER',
  MEMBER: 'MEMBER'
})

export const ROLE_META = Object.freeze({
  [ROLE.SYSTEM_ADMIN]: { key: 'SYSTEM_ADMIN', name: '系统管理员', tagType: 'danger' },
  [ROLE.TEACHER]: { key: 'TEACHER', name: '老师/实验室负责人', tagType: 'primary' },
  [ROLE.STOCK_KEEPER]: { key: 'STOCK_KEEPER', name: '库存管理员', tagType: 'warning' },
  [ROLE.MEMBER]: { key: 'MEMBER', name: '普通成员', tagType: 'info' }
})

export const KNOWN_ROLES = Object.freeze(Object.values(ROLE))

export function getUserRoles(user) {
  if (!user) return []
  if (Array.isArray(user.roles) && user.roles.length) {
    const roles = user.roles
      .map((role) => (typeof role === 'string' ? role : role?.roleKey))
      .filter((roleKey) => KNOWN_ROLES.includes(roleKey))
    if (roles.length) return [...new Set(roles)]
  }
  if (user.primaryRoleKey && KNOWN_ROLES.includes(user.primaryRoleKey)) {
    return [user.primaryRoleKey]
  }
  if (user.role === 'admin') return [ROLE.SYSTEM_ADMIN]
  if (user.roleKey && KNOWN_ROLES.includes(user.roleKey)) return [user.roleKey]
  return [ROLE.MEMBER]
}

export function getPrimaryRole(user) {
  if (!user) return null
  if (user.primaryRoleKey && KNOWN_ROLES.includes(user.primaryRoleKey)) {
    return user.primaryRoleKey
  }
  const roles = getUserRoles(user)
  return roles[0] || null
}

export function hasAnyRole(user, requiredRoles) {
  if (!requiredRoles || requiredRoles.length === 0) return true
  if (!user) return false
  const userRoles = getUserRoles(user)
  return requiredRoles.some((r) => userRoles.includes(r))
}

export function isAdminUser(user) {
  return hasAnyRole(user, [ROLE.SYSTEM_ADMIN])
}
