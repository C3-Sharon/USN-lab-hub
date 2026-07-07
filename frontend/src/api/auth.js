import request from '@/utils/request'

export function login(data) {
  return request.post('/usnhub/user/login', data)
}
