import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { userStore } from '@/store/user'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 15000
})

function readStoredToken() {
  return localStorage.getItem('token') || sessionStorage.getItem('token') || ''
}

request.interceptors.request.use(
  (config) => {
    const token = userStore.token || readStoredToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
      config.headers.token = token
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (response.config.responseType === 'blob') {
      return response
    }
    if (!payload || typeof payload.code === 'undefined') {
      return payload
    }
    if (payload.code !== 200) {
      ElMessage.error(payload.msg || '请求处理失败')
      return Promise.reject(payload)
    }
    return payload.data
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      userStore.logout()
      ElMessage.error('登录状态已失效，请重新登录')
      router.replace('/login')
    } else {
      ElMessage.error(error.response?.data?.msg || error.message || '网络请求失败')
    }
    return Promise.reject(error)
  }
)

export default request
