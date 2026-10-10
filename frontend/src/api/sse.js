/**
 * DeviceTelemetrySSE
 * 统一封装 EventSource 连接管理，不在 Vue 页面散写连接代码。
 *
 * 契约来源：docs/planning/week7-product-spec.md
 * - SSE URL：GET /api/iot/public/devices/1/telemetry/stream
 * - 事件名：telemetry（data 复用 latest 结构）、heartbeat（忽略）
 * - 断线约 10 秒后降级到轮询（由外部 Vue 组件控制轮询启停）
 */

const SSE_BASE_URL = (typeof import.meta.env !== 'undefined' ? (import.meta.env.VITE_API_BASE_URL || '') : '') + '/api/iot/public/devices/1/telemetry/stream'
const DEFAULT_DEGRADE_TIMEOUT = 10000 // 10s

export const SSE_STATUS = {
  IDLE: 'idle',
  CONNECTED: 'connected',
  RECONNECTING: 'reconnecting',
  POLLING: 'polling'
}

export class DeviceTelemetrySSE {
  constructor(options = {}) {
    this.url = options.url || SSE_BASE_URL
    this.degradeTimeout = options.degradeTimeout || DEFAULT_DEGRADE_TIMEOUT

    this.onTelemetry = options.onTelemetry || (() => {})
    this.onStatusChange = options.onStatusChange || (() => {})
    this.onError = options.onError || (() => {})

    this.es = null
    this.status = SSE_STATUS.IDLE
    this.errorTimer = null
    this._lastTelemetry = null
  }

  connect() {
    if (this.es) return
    this._updateStatus(SSE_STATUS.RECONNECTING)

    try {
      this.es = new EventSource(this.url)
    } catch (err) {
      console.error('[SSE] EventSource 创建失败:', err)
      this._startDegradeTimer()
      return
    }

    this.es.onopen = () => {
      this._clearDegradeTimer()
      this._updateStatus(SSE_STATUS.CONNECTED)
    }

    this.es.onerror = (err) => {
      this.onError(err)
      if (this.status === SSE_STATUS.CONNECTED) {
        this._updateStatus(SSE_STATUS.RECONNECTING)
      }
      if (!this.errorTimer) {
        this.errorTimer = setTimeout(() => {
          this._updateStatus(SSE_STATUS.POLLING)
        }, this.degradeTimeout)
      }
    }

    this.es.addEventListener('telemetry', (e) => {
      try {
        const data = JSON.parse(e.data)
        this._lastTelemetry = data
        this.onTelemetry(data)
      } catch (err) {
        console.error('[SSE] telemetry JSON 解析失败:', err, e.data)
      }
    })

    this.es.addEventListener('heartbeat', () => {
      // 心跳仅用于保持连接活跃，不代表设备在线，不刷新任何 UI
    })
  }

  /**
   * 获取最后一次有效的 telemetry 数据（用于断线时仍显示最后数据）
   */
  getLastTelemetry() {
    return this._lastTelemetry
  }

  _startDegradeTimer() {
    this._clearDegradeTimer()
    this.errorTimer = setTimeout(() => {
      this._updateStatus(SSE_STATUS.POLLING)
    }, this.degradeTimeout)
  }

  _clearDegradeTimer() {
    if (this.errorTimer) {
      clearTimeout(this.errorTimer)
      this.errorTimer = null
    }
  }

  _updateStatus(status) {
    if (this.status === status) return
    this.status = status
    this.onStatusChange(status)
  }

  close() {
    this._clearDegradeTimer()
    if (this.es) {
      this.es.close()
      this.es = null
    }
    this._updateStatus(SSE_STATUS.IDLE)
  }
}
