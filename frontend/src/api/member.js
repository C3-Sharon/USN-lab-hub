import request from '@/utils/request'

export function fetchMemberPage(query) {
  return request.get('/admin/member/page', { params: query })
}

export function saveMember(data) {
  return request.post('/admin/member/save', data)
}

export function updateMember(data) {
  return request.post('/admin/member/update', data)
}

export function updateMemberStatus(id, status) {
  return request.put(`/admin/member/status/${id}/${status}`)
}
