import { reactive } from 'vue'
import { getUserRoles, getPrimaryRole } from '@/utils/permission'

const STORAGE_KEY = 'usn_lab_hub_auth'

function parseStorage() {
  const raw = localStorage.getItem(STORAGE_KEY) || sessionStorage.getItem(STORAGE_KEY)
  if (!raw) return {}
  try {
    return JSON.parse(raw) || {}
  } catch {
    return {}
  }
}

function persistAll(payload, remember) {
  const targetStorage = remember ? localStorage : sessionStorage
  const otherStorage = remember ? sessionStorage : localStorage
  targetStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
  targetStorage.setItem('token', payload.token || '')
  otherStorage.removeItem(STORAGE_KEY)
  otherStorage.removeItem('token')
}

const cached = parseStorage()

function normalizeUser(raw) {
  if (!raw) return null
  const primaryRoleKey = raw.primaryRoleKey || raw.roleKey || (raw.role === 'admin' ? 'SYSTEM_ADMIN' : 'MEMBER')
  const primaryRoleName = raw.primaryRoleName || raw.roleName || (primaryRoleKey === 'SYSTEM_ADMIN' ? '系统管理员' : '普通成员')
  const roles = getUserRoles({ ...raw, primaryRoleKey })
  return {
    ...raw,
    primaryRoleKey,
    primaryRoleName,
    roles
  }
}

export const userStore = reactive({
  token: cached.token || localStorage.getItem('token') || sessionStorage.getItem('token') || '',
  userInfo: normalizeUser(cached.userInfo),
  todayAttendance: cached.todayAttendance || null,

  setLoginData(loginData, remember = true) {
    this.token = loginData?.token || ''
    this.userInfo = normalizeUser(loginData?.user || null)
    this.todayAttendance = loginData?.attendance || null
    persistAll({
      token: this.token,
      userInfo: this.userInfo,
      todayAttendance: this.todayAttendance
    }, remember)
  },

  setAttendance(attendance) {
    this.todayAttendance = attendance || null
    persistAll({
      token: this.token,
      userInfo: this.userInfo,
      todayAttendance: this.todayAttendance
    }, true)
  },

  logout() {
    this.token = ''
    this.userInfo = null
    this.todayAttendance = null
    localStorage.removeItem(STORAGE_KEY)
    localStorage.removeItem('token')
    sessionStorage.removeItem(STORAGE_KEY)
    sessionStorage.removeItem('token')
  }
})

export function hasLogin() {
  return Boolean(userStore.token && userStore.userInfo)
}

export function currentRoles() {
  return getUserRoles(userStore.userInfo)
}

export function currentPrimaryRole() {
  return getPrimaryRole(userStore.userInfo)
}

export function hasAnyRole(requiredRoles) {
  return getUserRoles(userStore.userInfo).some((r) => requiredRoles.includes(r))
}

export function isAdmin() {
  return hasAnyRole(['SYSTEM_ADMIN'])
}
