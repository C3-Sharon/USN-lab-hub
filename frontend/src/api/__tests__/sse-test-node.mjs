/**
 * DeviceTelemetrySSE Node.js 逻辑测试
 * 使用 Mock EventSource 验证所有状态转换和边界场景。
 *
 * 运行方式：node src/api/__tests__/sse-test-node.mjs
 * 注意：需在 frontend 目录下运行，且 Node.js >= 18
 */

// ========== Mock EventSource ==========
class MockEventSource {
  constructor(url) {
    this.url = url
    this.readyState = 0
    this._listeners = {}
    this._closed = false
    this._connectTimer = null
  }

  addEventListener(type, fn) {
    if (!this._listeners[type]) this._listeners[type] = []
    this._listeners[type].push(fn)
  }

  _emit(type, data) {
    if (this._closed) return
    ;(this._listeners[type] || []).forEach(fn => fn({ data }))
  }

  _open() {
    if (this._closed) return
    this.readyState = 1
    if (this.onopen) this.onopen()
  }

  _error() {
    if (this._closed) return
    if (this.onerror) this.onerror(new Error('Mock SSE error'))
  }

  close() {
    this._closed = true
    this.readyState = 2
    if (this._connectTimer) {
      clearTimeout(this._connectTimer)
      this._connectTimer = null
    }
  }
}

// 注入全局 EventSource
global.EventSource = MockEventSource

// ========== 加载被测模块 ==========
const { DeviceTelemetrySSE, SSE_STATUS } = await import('../sse.js')

// ========== 测试工具 ==========
let testCount = 0
let passCount = 0

function assert(condition, message) {
  testCount++
  if (condition) {
    passCount++
    console.log(`  ✓ ${message}`)
  } else {
    console.error(`  ✗ ${message}`)
  }
}

function sleep(ms) {
  return new Promise(r => setTimeout(r, ms))
}

function createTelemetry(value = 92.5) {
  return JSON.stringify({
    deviceId: 1,
    deviceCode: 'PM-001',
    deviceName: '实验室功耗监测仪 #1',
    projectName: '实验室功耗监测',
    status: 'ONLINE',
    reportTime: '2026-07-16 20:00:00',
    metrics: [
      { metricKey: 'voltage', metricName: '电压', value: 220.3, unit: 'V' },
      { metricKey: 'current', metricName: '电流', value: 0.42, unit: 'A' },
      { metricKey: 'power', metricName: '功率', value, unit: 'W' }
    ]
  })
}

// ========== 测试用例 ==========

console.log('\n=== 测试 1: 首次连接成功 ===')
{
  const statuses = []
  const sse = new DeviceTelemetrySSE({
    url: 'http://mock/sse',
    onStatusChange: s => statuses.push(s),
    onTelemetry: () => {}
  })
  sse.connect()
  // Mock 浏览器立即 open
  sse.es._open()
  assert(sse.status === SSE_STATUS.CONNECTED, '状态应为 connected')
  assert(statuses.includes(SSE_STATUS.RECONNECTING), '应先经过 reconnecting')
  assert(statuses.includes(SSE_STATUS.CONNECTED), '应到达 connected')
  sse.close()
}

console.log('\n=== 测试 2: telemetry 连续推送 ===')
{
  const payloads = []
  const sse = new DeviceTelemetrySSE({
    url: 'http://mock/sse',
    onTelemetry: data => payloads.push(data),
    onStatusChange: () => {}
  })
  sse.connect()
  sse.es._open()
  sse.es._emit('telemetry', createTelemetry(90))
  sse.es._emit('telemetry', createTelemetry(95))
  sse.es._emit('telemetry', createTelemetry(100))
  assert(payloads.length === 3, '应收到 3 次 telemetry')
  assert(payloads[2].metrics.find(m => m.metricKey === 'power').value === 100, '第三次功率应为 100')
  sse.close()
}

console.log('\n=== 测试 3: 重复事件 ===')
{
  const payloads = []
  const sse = new DeviceTelemetrySSE({
    url: 'http://mock/sse',
    onTelemetry: data => payloads.push(data),
    onStatusChange: () => {}
  })
  sse.connect()
  sse.es._open()
  const sameData = createTelemetry(88)
  sse.es._emit('telemetry', sameData)
  sse.es._emit('telemetry', sameData)
  assert(payloads.length === 2, '重复事件也应被接收 2 次')
  sse.close()
}

console.log('\n=== 测试 4: 非法 JSON ===')
{
  const payloads = []
  let errorLogged = false
  const origError = console.error
  console.error = (...args) => {
    if (args[0] && args[0].includes && args[0].includes('telemetry JSON 解析失败')) {
      errorLogged = true
    }
  }
  const sse = new DeviceTelemetrySSE({
    url: 'http://mock/sse',
    onTelemetry: data => payloads.push(data),
    onStatusChange: () => {}
  })
  sse.connect()
  sse.es._open()
  sse.es._emit('telemetry', 'not-json{{')
  assert(payloads.length === 0, '非法 JSON 不应触发 onTelemetry')
  assert(errorLogged, '应输出解析错误日志')
  console.error = origError
  sse.close()
}

console.log('\n=== 测试 5: SSE 断线触发 reconnecting ===')
{
  const statuses = []
  const sse = new DeviceTelemetrySSE({
    url: 'http://mock/sse',
    degradeTimeout: 50,
    onStatusChange: s => statuses.push(s),
    onTelemetry: () => {}
  })
  sse.connect()
  sse.es._open()
  assert(sse.status === SSE_STATUS.CONNECTED, '初始应为 connected')
  sse.es._error()
  assert(sse.status === SSE_STATUS.RECONNECTING, 'error 后应为 reconnecting')
  sse.close()
}

console.log('\n=== 测试 6: 自动重连后恢复 connected ===')
{
  const statuses = []
  const sse = new DeviceTelemetrySSE({
    url: 'http://mock/sse',
    degradeTimeout: 50,
    onStatusChange: s => statuses.push(s),
    onTelemetry: () => {}
  })
  sse.connect()
  sse.es._open()
  sse.es._error()
  assert(sse.status === SSE_STATUS.RECONNECTING, 'error 后 reconnecting')
  // 模拟浏览器自动重连成功（再次 open）
  sse.es._open()
  assert(sse.status === SSE_STATUS.CONNECTED, '重连成功后应恢复 connected')
  sse.close()
}

console.log('\n=== 测试 7: 断线超 10s 后降级为 polling ===')
{
  const statuses = []
  const sse = new DeviceTelemetrySSE({
    url: 'http://mock/sse',
    degradeTimeout: 50, // 测试用 50ms 代替 10s
    onStatusChange: s => statuses.push(s),
    onTelemetry: () => {}
  })
  sse.connect()
  sse.es._open()
  sse.es._error()
  await sleep(80)
  assert(sse.status === SSE_STATUS.POLLING, '超时后应降级为 polling')
  sse.close()
}

console.log('\n=== 测试 8: SSE 恢复后停止轮询 ===')
{
  const statuses = []
  const sse = new DeviceTelemetrySSE({
    url: 'http://mock/sse',
    degradeTimeout: 50,
    onStatusChange: s => statuses.push(s),
    onTelemetry: () => {}
  })
  sse.connect()
  sse.es._open()
  sse.es._error()
  await sleep(80)
  assert(sse.status === SSE_STATUS.POLLING, '降级为 polling')
  // 模拟重连成功
  sse.es._open()
  assert(sse.status === SSE_STATUS.CONNECTED, '恢复后应为 connected')
  sse.close()
}

console.log('\n=== 测试 9: 页面卸载后资源清理 ===')
{
  const sse = new DeviceTelemetrySSE({
    url: 'http://mock/sse',
    onStatusChange: () => {},
    onTelemetry: () => {}
  })
  sse.connect()
  sse.es._open()
  assert(sse.es !== null, '连接应存在')
  sse.close()
  assert(sse.status === SSE_STATUS.IDLE, '关闭后状态应为 idle')
  assert(sse.es === null, 'es 应被置空')
  assert(sse.errorTimer === null, 'errorTimer 应被清理')
}

console.log('\n=== 测试 10: 后端关闭时仍保留最后有效数据 ===')
{
  const payloads = []
  const sse = new DeviceTelemetrySSE({
    url: 'http://mock/sse',
    onTelemetry: data => payloads.push(data),
    onStatusChange: () => {}
  })
  sse.connect()
  sse.es._open()
  sse.es._emit('telemetry', createTelemetry(77))
  const last = sse.getLastTelemetry()
  assert(last !== null, '应保留最后数据')
  assert(last.metrics.find(m => m.metricKey === 'power').value === 77, '最后功率应为 77')
  // 模拟后端关闭
  sse.es._error()
  assert(sse.getLastTelemetry().metrics.find(m => m.metricKey === 'power').value === 77, 'error 后仍应保留最后数据')
  sse.close()
}

console.log('\n=== 测试 11: 心跳事件不影响数据 ===')
{
  const payloads = []
  const sse = new DeviceTelemetrySSE({
    url: 'http://mock/sse',
    onTelemetry: data => payloads.push(data),
    onStatusChange: () => {}
  })
  sse.connect()
  sse.es._open()
  sse.es._emit('heartbeat', JSON.stringify({ ts: '2026-07-16 20:00:05' }))
  assert(payloads.length === 0, '心跳不应触发 onTelemetry')
  sse.close()
}

// ========== 汇总 ==========
console.log(`\n==============================`)
console.log(`  测试完成: ${passCount}/${testCount} 通过`)
console.log(`==============================`)
if (passCount < testCount) {
  process.exit(1)
}
