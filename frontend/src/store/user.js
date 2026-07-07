import { reactive } from 'vue'

const STORAGE_KEY = 'usn_lab_hub_auth'

function parseStorage() {
  const raw = localStorage.getItem(STORAGE_KEY) || sessionStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return {}
  }
  try {
    return JSON.parse(raw)
  } catch {
    return {}
  }
}

const cached = parseStorage()

export const userStore = reactive({
  token: cached.token || localStorage.getItem('token') || sessionStorage.getItem('token') || '',
  userInfo: cached.userInfo || null,
  todayAttendance: cached.todayAttendance || null,

  setLoginData(loginData, remember = true) {
    this.token = loginData?.token || ''
    this.userInfo = loginData?.user || null
    this.todayAttendance = loginData?.attendance || null
    this.persist(remember)
  },

  setAttendance(attendance) {
    this.todayAttendance = attendance || null
    this.persist(true)
  },

  persist(remember = true) {
    const targetStorage = remember ? localStorage : sessionStorage
    const otherStorage = remember ? sessionStorage : localStorage
    const payload = JSON.stringify({
      token: this.token,
      userInfo: this.userInfo,
      todayAttendance: this.todayAttendance
    })
    targetStorage.setItem(STORAGE_KEY, payload)
    targetStorage.setItem('token', this.token)
    otherStorage.removeItem(STORAGE_KEY)
    otherStorage.removeItem('token')
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
  return Boolean(userStore.token)
}

export function isAdmin() {
  return userStore.userInfo?.role === 'admin'
}
