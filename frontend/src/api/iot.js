import request from '@/utils/request'

// ========== Mock 开关 ==========
// 后端接口未就绪时设为 true，使用 mock 数据
export const USE_MOCK = false

// ========== Mock 数据 ==========
const MOCK_PROJECTS = [
  {
    id: 1,
    projectCode: 'power-monitor',
    projectName: '功耗检测项目',
    description: '功耗检测与功耗表演示项目',
    ownerId: 1,
    ownerName: '项目负责人',
    status: 'ACTIVE',
    deviceCount: 1,
    onlineDeviceCount: 1,
    publicVisible: true,
    createdAt: '2026-07-06 20:00:00'
  }
]

const MOCK_DEVICES = [
  {
    id: 1,
    projectId: 1,
    projectName: '功耗检测项目',
    deviceCode: 'PM-001',
    deviceName: '功耗检测设备 001',
    deviceType: 'POWER_METER',
    protocol: 'MQTT',
    location: '实验室',
    ownerId: 1,
    ownerName: '硬件负责人',
    status: 'ONLINE',
    lastSeenAt: '2026-07-09 13:50:00',
    alertCount: 1,
    tags: ['功耗', '演示']
  }
]

const MOCK_DEVICE_DETAIL = {
  id: 1,
  projectId: 1,
  projectName: '功耗检测项目',
  deviceCode: 'PM-001',
  deviceName: '功耗检测设备 001',
  deviceType: 'POWER_METER',
  protocol: 'MQTT',
  location: '实验室',
  ownerName: '硬件负责人',
  status: 'ONLINE',
  lastSeenAt: '2026-07-09 13:50:00',
  description: '最小纵向切片演示设备',
  metrics: [
    { metricKey: 'voltage', metricName: '电压', unit: 'V' },
    { metricKey: 'current', metricName: '电流', unit: 'A' },
    { metricKey: 'power', metricName: '功率', unit: 'W' }
  ]
}

const MOCK_LATEST_METRICS = {
  deviceId: 1,
  deviceCode: 'PM-001',
  deviceName: '实验室功耗监测仪 #1',
  projectName: '实验室功耗监测',
  status: 'ONLINE',
  reportTime: '2026-07-09 13:50:00',
  metrics: [
    { metricKey: 'voltage', metricName: '电压', value: 220.3, unit: 'V' },
    { metricKey: 'current', metricName: '电流', value: 0.42, unit: 'A' },
    { metricKey: 'power', metricName: '功率', value: 92.5, unit: 'W' }
  ]
}

const MOCK_METRIC_HISTORY = {
  deviceId: 1,
  metricKey: 'power',
  unit: 'W',
  points: [
    { time: '2026-07-09 13:00:00', value: 80.1 },
    { time: '2026-07-09 13:05:00', value: 85.3 },
    { time: '2026-07-09 13:10:00', value: 90.2 },
    { time: '2026-07-09 13:15:00', value: 95.0 },
    { time: '2026-07-09 13:20:00', value: 98.5 },
    { time: '2026-07-09 13:25:00', value: 105.2 },
    { time: '2026-07-09 13:30:00', value: 110.8 },
    { time: '2026-07-09 13:35:00', value: 115.3 },
    { time: '2026-07-09 13:40:00', value: 118.0 },
    { time: '2026-07-09 13:45:00', value: 119.5 },
    { time: '2026-07-09 13:50:00', value: 120.5 }
  ]
}

const MOCK_ALERTS = [
  {
    id: 1,
    deviceId: 1,
    deviceCode: 'PM-001',
    deviceName: '功耗检测设备 001',
    metricKey: 'power',
    level: 'WARNING',
    message: '功率超过阈值',
    triggerValue: 120.5,
    thresholdValue: 100.0,
    status: 'OPEN',
    createdAt: '2026-07-09 13:25:00'
  }
]

const MOCK_RECOMMENDATIONS = [
  {
    id: 1,
    source: 'RULE',
    deviceId: 1,
    alertId: 1,
    title: '建议检查设备功耗',
    content: '当前功率超过阈值，建议降低负载、检查供电，或临时提高采样间隔观察波动。',
    status: 'PENDING',
    createdAt: '2026-07-09 13:25:10'
  }
]

const MOCK_COMMANDS = [
  {
    commandId: 'CMD-20260709-0001',
    deviceId: 1,
    deviceCode: 'PM-001',
    command: 'SET_SAMPLE_INTERVAL',
    status: 'ACKED',
    createdAt: '2026-07-09 13:30:00'
  }
]

const MOCK_OPERATION_LOGS = [
  {
    id: 1,
    operatorId: 1,
    operatorName: '管理员',
    action: 'SEND_COMMAND',
    targetType: 'DEVICE',
    targetId: 1,
    summary: '向 PM-001 下发 SET_SAMPLE_INTERVAL 指令',
    createdAt: '2026-07-09 13:30:00'
  }
]

function mockResponse(data) {
  return Promise.resolve({
    code: 200,
    message: 'success',
    data
  })
}

function mockPageResponse(records, total = records.length) {
  return Promise.resolve({
    code: 200,
    message: 'success',
    data: {
      records,
      total,
      size: 10,
      current: 1,
      pages: 1
    }
  })
}

// ========== Project APIs ==========

export function listProjects(params) {
  if (USE_MOCK) return mockPageResponse(MOCK_PROJECTS)
  return request.get('/api/iot/projects', { params })
}

export function getProjectDetail(id) {
  if (USE_MOCK) return mockResponse(MOCK_PROJECTS[0])
  return request.get(`/api/iot/projects/${id}`)
}

// ========== Device APIs ==========

export function listDevices(params) {
  if (USE_MOCK) return mockPageResponse(MOCK_DEVICES)
  return request.get('/api/iot/devices', { params })
}

export function getDeviceDetail(id) {
  if (USE_MOCK) return mockResponse(MOCK_DEVICE_DETAIL)
  return request.get(`/api/iot/devices/${id}`)
}

// ========== Telemetry APIs ==========

export function getLatestMetrics(deviceId) {
  if (USE_MOCK) {
    const data = { ...MOCK_LATEST_METRICS }
    data.reportTime = new Date().toISOString().replace('T', ' ').slice(0, 19)
    return mockResponse(data)
  }
  return request.get(`/api/iot/devices/${deviceId}/latest`)
}

export function getMetricHistory(deviceId, params) {
  if (USE_MOCK) return mockResponse(MOCK_METRIC_HISTORY)
  return request.get(`/api/iot/devices/${deviceId}/metrics/history`, { params })
}

// ========== Alert APIs ==========

export function listAlerts(params) {
  if (USE_MOCK) return mockPageResponse(MOCK_ALERTS)
  return request.get('/api/iot/alerts', { params })
}

export function handleAlert(id, data) {
  if (USE_MOCK) return mockResponse({ success: true })
  return request.post(`/api/iot/alerts/${id}/handle`, data)
}

// ========== Recommendation APIs ==========

export function listRecommendations(params) {
  if (USE_MOCK) return mockPageResponse(MOCK_RECOMMENDATIONS)
  return request.get('/api/iot/recommendations', { params })
}

export function confirmRecommendation(id) {
  if (USE_MOCK) return mockResponse({ success: true })
  return request.post(`/api/iot/recommendations/${id}/confirm`)
}

// ========== Command APIs ==========

export function sendCommand(deviceId, data) {
  if (USE_MOCK) {
    const newCmd = {
      commandId: `CMD-${Date.now()}`,
      deviceId,
      deviceCode: 'PM-001',
      command: data.command,
      status: 'SENT',
      createdAt: new Date().toISOString().replace('T', ' ').slice(0, 19)
    }
    return mockResponse(newCmd)
  }
  return request.post(`/api/iot/devices/${deviceId}/commands`, data)
}

export function listCommands(params) {
  if (USE_MOCK) return mockPageResponse(MOCK_COMMANDS)
  return request.get('/api/iot/commands', { params })
}

// ========== Operation Log APIs ==========

export function listOperationLogs(params) {
  if (USE_MOCK) return mockPageResponse(MOCK_OPERATION_LOGS)
  return request.get('/api/iot/operation-logs', { params })
}
