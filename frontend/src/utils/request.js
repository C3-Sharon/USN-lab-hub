import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import router from '@/router'
import { userStore } from '@/store/user'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 15000
})

function readStoredToken() {
  return userStore.token || localStorage.getItem('token') || sessionStorage.getItem('token') || ''
}

request.interceptors.request.use(
  (config) => {
    const token = readStoredToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
      config.headers.token = token
    }
    return config
  },
  (error) => Promise.reject(error)
)

const LOGIN_REQUIRED_CODES = new Set(['TOKEN_MISSING', 'TOKEN_INVALID', 'TOKEN_EXPIRED', 'ACCOUNT_DISABLED'])

function clearAuthAndRedirect(reason, message) {
  userStore.logout()
  ElMessage.error(message || '登录状态已失效，请重新登录')
  if (reason) {
    router.replace({ path: '/login', query: { reason } })
  } else {
    router.replace('/login')
  }
}

request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response
    }
    const payload = response.data
    if (!payload || typeof payload.code === 'undefined') {
      return payload
    }
    if (payload.code === 200) {
      return payload.data
    }
    if (LOGIN_REQUIRED_CODES.has(payload.code)) {
      const reason = payload.code
      const message = reason === 'ACCOUNT_DISABLED'
        ? '账号已被禁用，请联系管理员'
        : '登录状态已失效，请重新登录'
      clearAuthAndRedirect(reason, message)
      return Promise.reject(payload)
    }
    if (payload.code === 403 || payload.code === 'ACCESS_DENIED') {
      ElMessage.error(payload.msg || '当前账号无权访问该资源')
      return Promise.reject(payload)
    }
    ElMessage.error(payload.msg || '请求处理失败')
    return Promise.reject(payload)
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      const bodyCode = error.response?.data?.code || 'TOKEN_INVALID'
      clearAuthAndRedirect(bodyCode, bodyCode === 'ACCOUNT_DISABLED' ? '账号已被禁用，请联系管理员' : '登录状态已失效，请重新登录')
    } else if (status === 403) {
      ElMessage.error('当前账号无权访问该资源')
    } else {
      ElMessage.error(error.response?.data?.msg || error.message || '网络请求失败')
    }
    return Promise.reject(error)
  }
)

export default request
