import request from '@/utils/request'
import { getMockEnabled } from '@/utils/mock'
import { userStore } from '@/store/user'

/**
 * 工作台接口
 *
 * W38 真实端点：
 *   GET  /api/workbench/overview   个人工作台 6 区域
 * 区域 schema（W38 任务书）：
 *   { state: 'READY' | 'NOT_AVAILABLE' | 'ERROR', ...regionFields }
 *   - attendance: 真实考勤；字段直接位于 attendance 区域
 *   - projects / tasks / learning / notifications: 本周统一为 NOT_AVAILABLE
 *   - deviceReminder: { state, onlineCount, alertCount }
 *
 * 注意：本文件仅在前端 mock 模式（VITE_USE_MOCK !== 'false'）下走 mockResponse，
 * 真实后端模式下走 /api/workbench/* 标准响应，request.js 拦截器已解包到 data。
 */

function notAvailable() {
  return { state: 'NOT_AVAILABLE' }
}

function attendanceRegion() {
  const att = userStore.todayAttendance
  if (!att) {
    return { state: 'READY', todayStatus: 0, weekHours: 0, semesterHours: 0, todayRecords: [] }
  }
  return {
    state: 'READY',
    todayStatus: att.todayStatus ?? 0,
    weekHours: att.weekHours ?? 0,
    semesterHours: att.semesterHours ?? 0,
    todayRecords: att.todayRecords || []
  }
}

function mockOverview() {
  return Promise.resolve({
    code: 200,
    msg: 'success',
    data: {
      attendance: attendanceRegion(),
      projects: notAvailable(),
      tasks: notAvailable(),
      learning: notAvailable(),
      notifications: notAvailable(),
      deviceReminder: { state: 'READY', onlineCount: 1, alertCount: 0 },
      generatedAt: new Date().toISOString()
    }
  })
}

export function fetchWorkbenchOverview() {
  if (getMockEnabled()) {
    return mockOverview().then((res) => res.data)
  }
  return request.get('/api/workbench/overview')
}
