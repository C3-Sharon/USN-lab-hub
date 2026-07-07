import request from '@/utils/request'

export function doAttendanceAction(actionType) {
  return request.post('/usnhub/attendance/action', { actionType })
}

export function fetchAttendancePage(query) {
  return request.post('/admin/attendance/page', query)
}

export function exportAttendance(query) {
  return request.post('/admin/attendance/export', query, {
    responseType: 'blob'
  })
}
