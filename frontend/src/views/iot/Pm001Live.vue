<template>
  <div class="pm001-live">
    <h2>PM-001 实时数据监控</h2>
    <p class="sub-title">第七周 — SSE 实时遥测 + 轮询降级</p>

    <!-- SSE 连接状态指示器 -->
    <div v-if="sseStatus !== 'idle'" class="sse-status-bar">
      <span class="sse-dot" :class="sseStatus"></span>
      <span class="sse-label">{{
        sseStatus === 'connected' ? '实时推送中' :
        sseStatus === 'reconnecting' ? 'SSE 重连中…' :
        sseStatus === 'polling' ? '已降级为轮询' : ''
      }}</span>
    </div>

    <!-- ========== 最新数据卡片 ========== -->
    <el-card v-loading="latestLoading && !latestLoaded" class="device-card">
      <template v-if="latestError">
        <el-alert title="最新数据加载失败" :description="latestError" type="error" :closable="false" show-icon />
        <div style="text-align: center; margin-top: 16px">
          <el-button type="primary" @click="loadLatest">重试</el-button>
        </div>
      </template>

      <template v-else-if="!latestLoading && !hasLatestData">
        <el-empty description="等待设备上报数据">
          <template #image>
            <el-icon :size="64" class="waiting-icon"><Loading /></el-icon>
          </template>
        </el-empty>
        <div class="waiting-hint">
          <el-icon><Timer /></el-icon>
          <span>页面每 {{ LATEST_INTERVAL }} 秒自动刷新，等待模拟器发送数据...</span>
        </div>
        <div style="text-align: center; margin-top: 12px">
          <el-button type="primary" :loading="latestLoading" @click="loadLatest">手动刷新</el-button>
        </div>
      </template>

      <template v-else>
        <div class="device-info">
          <div class="info-row">
            <span class="info-label">项目名称</span>
            <span class="info-value">{{ latestData.projectName || '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">设备名称</span>
            <span class="info-value">{{ latestData.deviceName || '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">设备编号</span>
            <span class="info-value code">{{ latestData.deviceCode || '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">在线状态</span>
            <el-tag :type="isOnline ? 'success' : 'info'" size="large" effect="dark">
              {{ isOnline ? '在线' : '离线' }}
            </el-tag>
            <span v-if="!isOnline && reportTime" class="offline-reason">（超过 15 秒未上报）</span>
          </div>
          <div class="info-row">
            <span class="info-label">上报时间</span>
            <span class="info-value">{{ reportTime || '--' }}</span>
          </div>
        </div>

        <el-divider />

        <el-row :gutter="20" class="metrics-row">
          <el-col :span="8" v-for="m in metricsList" :key="m.metricKey">
            <div class="metric-box" :class="m.metricKey">
              <div class="metric-icon">
                <el-icon size="32"><component :is="m.icon" /></el-icon>
              </div>
              <div class="metric-info">
                <div class="metric-name">{{ m.metricName }}</div>
                <div class="metric-value">
                  {{ formatValue(m.value) }}
                  <span class="metric-unit">{{ m.unit }}</span>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>

        <el-divider />

        <div class="footer-bar">
          <div class="footer-left">
            <span v-if="lastUpdateTime" class="last-update">页面更新于 {{ lastUpdateTime }}</span>
            <el-tag v-if="latestLoading" type="warning" size="small" effect="plain" class="polling-tag">刷新中...</el-tag>
          </div>
          <el-button type="primary" :icon="Refresh" :loading="latestLoading" @click="loadLatest">刷新数据</el-button>
        </div>
      </template>
    </el-card>

    <!-- ========== 功率历史曲线 ========== -->
    <el-card v-loading="historyLoading" class="section-card">
      <template #header>
        <div class="section-header">
          <el-icon size="18"><TrendCharts /></el-icon>
          <span>功率历史曲线</span>
          <el-tag size="small" type="info">近1小时</el-tag>
        </div>
      </template>
      <template v-if="historyError && !chartData.length">
        <el-alert title="历史数据加载失败" :description="historyError" type="error" :closable="false" show-icon style="margin-bottom: 16px" />
        <div style="text-align: center">
          <el-button type="primary" @click="loadHistory">重试</el-button>
        </div>
      </template>
      <template v-else-if="!historyLoading && !chartData.length">
        <el-empty description="暂无历史数据">
          <template #image>
            <el-icon :size="64" class="waiting-icon"><Loading /></el-icon>
          </template>
        </el-empty>
        <div class="waiting-hint">
          <el-icon><Timer /></el-icon>
          <span>历史数据每 {{ HISTORY_INTERVAL }} 秒自动刷新...</span>
        </div>
      </template>
      <div v-show="chartData.length" ref="chartRef" class="chart-container"></div>
    </el-card>

    <!-- ========== 告警区域 ========== -->
    <el-card v-loading="pollLoading" class="section-card">
      <template #header>
        <div class="section-header">
          <el-icon size="18" color="#f56c6c"><Warning /></el-icon>
          <span>告警区域</span>
          <el-tag v-if="openAlertCount > 0" type="danger" size="small">{{ openAlertCount }} 条未处理</el-tag>
        </div>
      </template>
      <template v-if="pollError && !alerts.length">
        <el-alert title="告警数据加载失败" :description="pollError" type="error" :closable="false" show-icon style="margin-bottom: 16px" />
        <div style="text-align: center">
          <el-button type="primary" @click="pollData">重试</el-button>
        </div>
      </template>
      <el-table v-else-if="alerts.length" :data="alerts" stripe style="width: 100%">
        <el-table-column prop="level" label="等级" width="90">
          <template #default="{ row }">
            <el-tag :type="row.level === 'WARNING' ? 'warning' : 'danger'" size="small">{{ row.level === 'WARNING' ? '警告' : '严重' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="告警内容" min-width="160" />
        <el-table-column prop="triggerValue" label="触发值" width="100">
          <template #default="{ row }">{{ row.triggerValue }} {{ row.metricKey === 'power' ? 'W' : '' }}</template>
        </el-table-column>
        <el-table-column prop="thresholdValue" label="阈值" width="100">
          <template #default="{ row }">{{ row.thresholdValue }} {{ row.metricKey === 'power' ? 'W' : '' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="alertStatusType[row.status] || 'info'" size="small">{{ alertStatusText[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="触发时间" width="160" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'OPEN'" type="primary" size="small" :loading="alertActionLoading[row.id] === 'handle'" @click="doHandleAlert(row.id)">处理</el-button>
            <el-button v-if="row.status === 'OPEN'" type="info" size="small" :loading="alertActionLoading[row.id] === 'ignore'" @click="doIgnoreAlert(row.id)">忽略</el-button>
            <span v-else class="handled-text">{{ alertStatusText[row.status] }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无告警" />
    </el-card>

    <!-- ========== 建议区域 ========== -->
    <el-card v-loading="pollLoading" class="section-card">
      <template #header>
        <div class="section-header">
          <el-icon size="18" color="#e6a23c"><InfoFilled /></el-icon>
          <span>规则建议</span>
        </div>
      </template>
      <template v-if="pollError && !recommendations.length">
        <el-alert title="建议数据加载失败" :description="pollError" type="error" :closable="false" show-icon style="margin-bottom: 16px" />
        <div style="text-align: center">
          <el-button type="primary" @click="pollData">重试</el-button>
        </div>
      </template>
      <div v-else-if="recommendations.length" class="recommendation-list">
        <div v-for="rec in recommendations" :key="rec.id" class="recommendation-item">
          <div class="rec-header">
            <span class="rec-title">{{ rec.title }}</span>
            <el-tag v-if="rec.status === 'PENDING'" type="warning" size="small">待确认</el-tag>
            <el-tag v-else :type="rec.status === 'CONFIRMED' ? 'success' : 'info'" size="small">
              {{ rec.status === 'CONFIRMED' ? '已确认' : '已忽略' }}
            </el-tag>
          </div>
          <p class="rec-content">{{ rec.content }}</p>
          <div class="rec-footer">
            <span class="rec-time">{{ rec.createdAt }}</span>
            <div v-if="rec.status === 'PENDING'" class="rec-actions">
              <el-button type="primary" size="small" :loading="recActionLoading[rec.id] === 'confirm'" @click="doConfirmRec(rec.id)">确认并下发指令</el-button>
              <el-button type="info" size="small" :loading="recActionLoading[rec.id] === 'ignore'" @click="doIgnoreRec(rec.id)">忽略建议</el-button>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无建议" />
    </el-card>

    <!-- ========== 指令区域 ========== -->
    <el-card class="section-card">
      <template #header>
        <div class="section-header">
          <el-icon size="18" color="#409eff"><SwitchButton /></el-icon>
          <span>指令控制台</span>
        </div>
      </template>
      <div class="command-row">
        <div class="command-desc">
          <span class="command-name">设置采样间隔</span>
          <el-tag size="small" type="info">固定 5 秒</el-tag>
        </div>
        <el-button type="primary" :loading="commandSending" :disabled="commandSending" @click="sendCommand">下发指令</el-button>
      </div>

      <el-divider v-if="commands.length" content-position="left">指令记录</el-divider>
      <el-timeline v-if="commands.length">
        <el-timeline-item
          v-for="cmd in commands"
          :key="cmd.commandId"
          :type="cmd.status === 'ACKED' ? 'success' : cmd.status === 'FAILED' ? 'danger' : cmd.status === 'TIMEOUT' ? 'warning' : 'primary'"
          :timestamp="cmd.createdAt"
        >
          <div class="cmd-item">
            <span class="cmd-name">{{ commandNameMap[cmd.command] || cmd.command }}</span>
            <el-tag :type="commandStatusType[cmd.status] || 'info'" size="small">{{ commandStatusText[cmd.status] || cmd.status }}</el-tag>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- ========== 操作日志 ========== -->
    <el-card v-loading="pollLoading" class="section-card">
      <template #header>
        <div class="section-header">
          <el-icon size="18"><Document /></el-icon>
          <span>操作日志</span>
        </div>
      </template>
      <template v-if="pollError && !operationLogs.length">
        <el-alert title="日志数据加载失败" :description="pollError" type="error" :closable="false" show-icon style="margin-bottom: 16px" />
        <div style="text-align: center">
          <el-button type="primary" @click="pollData">重试</el-button>
        </div>
      </template>
      <el-timeline v-else-if="operationLogs.length">
        <el-timeline-item v-for="log in operationLogs" :key="log.id" :timestamp="log.createdAt">
          <div class="log-item">
            <span class="log-operator">{{ log.operatorName }}</span>
            <el-tag size="small" type="info">{{ log.action }}</el-tag>
            <span class="log-summary">{{ log.summary }}</span>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无操作日志" />
    </el-card>

    <!-- ========== 联调说明 ========== -->
    <el-alert title="联调说明" type="info" :closable="false" class="debug-hint">
      <p>SSE: EventSource /api/iot/public/devices/1/telemetry/stream (telemetry 事件实时更新)</p>
      <p>latest: GET /api/iot/devices/1/latest | history: GET /api/iot/devices/1/metrics/history?metricKey=power</p>
      <p>alerts: GET /api/iot/devices/1/alerts | recommendations: GET /api/iot/devices/1/recommendations</p>
      <p>commands: POST/GET /api/iot/devices/1/commands | logs: GET /api/iot/devices/1/operation-logs</p>
      <p>latest 每 {{ LATEST_INTERVAL }} 秒（SSE 降级时）| history 每 {{ HISTORY_INTERVAL }} 秒 | 告警/建议/指令/日志 每 {{ POLL_INTERVAL }} 秒</p>
    </el-alert>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import {
  Cpu, Refresh, Lightning, Magnet, OfficeBuilding,
  Timer, Loading, TrendCharts, Warning, InfoFilled,
  SwitchButton, Document
} from '@element-plus/icons-vue'
import {
  getLatestMetrics, getMetricHistory,
  listAlerts, handleAlert as apiHandleAlert,
  listRecommendations, confirmRecommendation as apiConfirmRec, ignoreRecommendation as apiIgnoreRec,
  sendCommand as apiSendCommand, listCommands,
  listOperationLogs
} from '@/api/iot'
import { DeviceTelemetrySSE, SSE_STATUS } from '@/api/sse'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

// ========== 配置 ==========
const LATEST_INTERVAL = 5
const HISTORY_INTERVAL = 30
const POLL_INTERVAL = 10
const ONLINE_THRESHOLD = 15
const DEVICE_ID = 1

// ========== 最新数据 ==========
const latestLoading = ref(false)
const latestError = ref('')
const latestLoaded = ref(false)
const latestData = ref({ deviceCode: '', deviceName: '', projectName: '', status: '', reportTime: '', metrics: [] })
const lastUpdateTime = ref('')
let latestPollTimer = null
const sseStatus = ref(SSE_STATUS.IDLE)
let sse = null

const hasLatestData = computed(() => !!(latestData.value.deviceCode || latestData.value.metrics?.length))
const iconMap = { voltage: OfficeBuilding, current: Magnet, power: Lightning }
const reportTime = computed(() => latestData.value.reportTime || latestData.value.reportedAt || '')

const isOnline = computed(() => {
  // 优先信任后端计算的 status，不在前端自行根据时间计算在线状态
  const status = latestData.value.status
  if (status === 'ONLINE' || status === 'OFFLINE') {
    return status === 'ONLINE'
  }
  // 兼容 fallback：旧数据或异常场景下保留时间判断
  const timeStr = reportTime.value
  if (!timeStr) return false
  const reportTs = new Date(timeStr.replace(' ', 'T')).getTime()
  if (isNaN(reportTs)) return false
  return Date.now() - reportTs <= ONLINE_THRESHOLD * 1000
})

const metricsList = computed(() => {
  return (latestData.value.metrics || []).map(m => ({ ...m, icon: iconMap[m.metricKey] || Cpu }))
})

function formatValue(val) {
  return val !== undefined && val !== null ? val.toFixed(2) : '--'
}

function applyLatestPayload(payload) {
  latestError.value = ''
  latestData.value = {
    deviceCode: payload.deviceCode || '',
    deviceName: payload.deviceName || '',
    projectName: payload.projectName || '',
    status: payload.status || '',
    reportTime: payload.reportTime || payload.reportedAt || '',
    metrics: payload.metrics || []
  }
  lastUpdateTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
  latestLoaded.value = true
}

async function loadLatest() {
  latestLoading.value = true
  latestError.value = ''
  try {
    const res = await getLatestMetrics(DEVICE_ID)
    const payload = res.data || res || {}
    applyLatestPayload(payload)
  } catch (err) {
    console.error('加载最新数据失败', err)
    latestError.value = err.message || '接口请求失败，请检查后端服务是否启动'
  } finally {
    latestLoading.value = false
  }
}

// ========== SSE 实时推送 ==========
function initSSE() {
  sse = new DeviceTelemetrySSE({
    onTelemetry: (data) => {
      applyLatestPayload(data)
    },
    onStatusChange: (status) => {
      sseStatus.value = status
      if (status === SSE_STATUS.POLLING) {
        startLatestPoll()
      } else if (status === SSE_STATUS.CONNECTED) {
        stopLatestPoll()
      }
    }
  })
  sse.connect()
}

function closeSSE() {
  if (sse) {
    sse.close()
    sse = null
  }
}

// ========== 历史曲线 ==========
const chartRef = ref(null)
let chartInstance = null
const chartData = ref([])
const historyLoading = ref(false)
const historyError = ref('')
let historyPollTimer = null

async function loadHistory() {
  historyLoading.value = true
  historyError.value = ''
  try {
    const res = await getMetricHistory(DEVICE_ID, { metricKey: 'power' })
    const payload = res.data || res || {}
    chartData.value = payload.points || []
    if (chartData.value.length) nextTick(() => renderChart())
  } catch (err) {
    console.error('加载历史数据失败', err)
    historyError.value = err.message || '历史数据接口请求失败'
  } finally {
    historyLoading.value = false
  }
}

function renderChart() {
  if (!chartRef.value) return
  if (!chartInstance) chartInstance = echarts.init(chartRef.value)
  const points = chartData.value
  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: points.map(p => p.time?.slice(11, 16) || ''), axisLabel: { color: '#7c8798' } },
    yAxis: { type: 'value', name: '功率 (W)', axisLabel: { color: '#7c8798' }, splitLine: { lineStyle: { color: '#f0f0f0' } } },
    series: [{
      name: '功率', type: 'line', smooth: true,
      data: points.map(p => p.value),
      lineStyle: { color: '#409eff', width: 3 }, symbolSize: 8,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.3)' },
          { offset: 1, color: 'rgba(64,158,255,0.05)' }
        ])
      },
      markLine: {
        silent: true,
        data: [{ yAxis: 100, label: { formatter: '阈值 100W' }, lineStyle: { color: '#f56c6c', type: 'dashed' } }]
      }
    }]
  }, true)
}

function onChartResize() {
  chartInstance?.resize()
}

// ========== 告警/建议/指令/日志 共用轮询 ==========
const pollLoading = ref(false)
const pollError = ref('')
const alerts = ref([])
const recommendations = ref([])
const commands = ref([])
const operationLogs = ref([])
let pollTimer = null

const openAlertCount = computed(() => alerts.value.filter(a => a.status === 'OPEN').length)

const alertStatusType = { OPEN: 'danger', HANDLED: 'success', IGNORED: 'info' }
const alertStatusText = { OPEN: '未处理', HANDLED: '已处理', IGNORED: '已忽略' }

const alertActionLoading = ref({})
const recActionLoading = ref({})
const commandSending = ref(false)

const commandNameMap = { SET_SAMPLE_INTERVAL: '设置采样间隔' }
const commandStatusType = { PENDING: 'info', SENT: 'primary', ACKED: 'success', FAILED: 'danger', TIMEOUT: 'warning' }
const commandStatusText = { PENDING: '等待中', SENT: '已发送', ACKED: '已确认', FAILED: '失败', TIMEOUT: '超时' }

async function pollData() {
  pollLoading.value = true
  pollError.value = ''
  try {
    const [aRes, rRes, cRes, lRes] = await Promise.all([
      listAlerts(DEVICE_ID).catch(() => null),
      listRecommendations(DEVICE_ID).catch(() => null),
      listCommands(DEVICE_ID).catch(() => null),
      listOperationLogs(DEVICE_ID).catch(() => null)
    ])
    if (aRes) {
      const payload = aRes.data || aRes || {}
      alerts.value = payload.list || []
    }
    if (rRes) {
      const payload = rRes.data || rRes || {}
      recommendations.value = payload.list || []
    }
    if (cRes) {
      const payload = cRes.data || cRes || {}
      commands.value = payload.list || []
    }
    if (lRes) {
      const payload = lRes.data || lRes || {}
      operationLogs.value = payload.list || []
    }
  } catch (err) {
    console.error('轮询数据失败', err)
    pollError.value = err.message || '数据加载失败'
  } finally {
    pollLoading.value = false
  }
}

async function doHandleAlert(id) {
  if (alertActionLoading.value[id]) return
  alertActionLoading.value[id] = 'handle'
  try {
    await apiHandleAlert(id, { status: 'HANDLED', note: '已在页面确认处理' })
    await pollData()
  } catch (err) {
    console.error('处理告警失败', err)
    alertError('处理告警失败: ' + (err.message || '请重试'))
  } finally {
    alertActionLoading.value[id] = null
  }
}

async function doIgnoreAlert(id) {
  if (alertActionLoading.value[id]) return
  alertActionLoading.value[id] = 'ignore'
  try {
    await apiHandleAlert(id, { status: 'IGNORED', note: '已在页面确认忽略' })
    await pollData()
  } catch (err) {
    console.error('忽略告警失败', err)
    alertError('忽略告警失败: ' + (err.message || '请重试'))
  } finally {
    alertActionLoading.value[id] = null
  }
}

async function doConfirmRec(id) {
  if (recActionLoading.value[id]) return
  recActionLoading.value[id] = 'confirm'
  try {
    await apiConfirmRec(id)
    await apiSendCommand(DEVICE_ID, { command: 'SET_SAMPLE_INTERVAL', params: { intervalSeconds: 5 } })
    await pollData()
    ElMessage.success('建议已确认，指令已下发')
  } catch (err) {
    console.error('确认建议或下发指令失败', err)
    alertError('确认建议或下发指令失败: ' + (err.message || '请重试'))
  } finally {
    recActionLoading.value[id] = null
  }
}

async function doIgnoreRec(id) {
  if (recActionLoading.value[id]) return
  recActionLoading.value[id] = 'ignore'
  try {
    await apiIgnoreRec(id)
    await pollData()
    ElMessage.success('建议已忽略')
  } catch (err) {
    console.error('忽略建议失败', err)
    alertError('忽略建议失败: ' + (err.message || '请重试'))
  } finally {
    recActionLoading.value[id] = null
  }
}

async function sendCommand() {
  if (commandSending.value) return
  commandSending.value = true
  try {
    await apiSendCommand(DEVICE_ID, { command: 'SET_SAMPLE_INTERVAL', params: { intervalSeconds: 5 } })
    await pollData()
  } catch (err) {
    console.error('下发指令失败', err)
    alertError('下发指令失败: ' + (err.message || '请重试'))
  } finally {
    commandSending.value = false
  }
}

function alertError(msg) {
  ElMessage.error(msg)
}

// ========== 轮询 ==========
function startLatestPoll() {
  stopLatestPoll()
  latestPollTimer = setInterval(() => loadLatest(), LATEST_INTERVAL * 1000)
}

function stopLatestPoll() {
  if (latestPollTimer) { clearInterval(latestPollTimer); latestPollTimer = null }
}

function startHistoryPoll() {
  stopHistoryPoll()
  historyPollTimer = setInterval(() => loadHistory(), HISTORY_INTERVAL * 1000)
}

function stopHistoryPoll() {
  if (historyPollTimer) { clearInterval(historyPollTimer); historyPollTimer = null }
}

function startPoll() {
  stopPoll()
  pollTimer = setInterval(() => pollData(), POLL_INTERVAL * 1000)
}

function stopPoll() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

// ========== 生命周期 ==========
onMounted(() => {
  loadLatest()
  initSSE()
  loadHistory()
  pollData()
  startHistoryPoll()
  startPoll()
  window.addEventListener('resize', onChartResize)
})

onUnmounted(() => {
  closeSSE()
  stopLatestPoll()
  stopHistoryPoll()
  stopPoll()
  window.removeEventListener('resize', onChartResize)
  if (chartInstance) { chartInstance.dispose(); chartInstance = null }
})
</script>

<style scoped>
.pm001-live { padding: 8px; max-width: 960px; margin: 0 auto; }
.sub-title { color: #7c8798; margin: -8px 0 16px; }
.device-card { margin-bottom: 20px; }
.section-card { margin-bottom: 20px; }
.section-header { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; color: #172033; }
.waiting-icon { color: #c0c4cc; animation: rotate 2s linear infinite; }
@keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.waiting-hint { display: flex; align-items: center; justify-content: center; gap: 6px; color: #909399; font-size: 14px; margin-top: 8px; }

.device-info { display: flex; flex-direction: column; gap: 14px; }
.info-row { display: flex; align-items: center; gap: 16px; min-height: 32px; }
.info-label { width: 80px; color: #7c8798; font-size: 14px; text-align: right; flex-shrink: 0; }
.info-value { font-size: 15px; color: #172033; font-weight: 500; }
.info-value.code { font-family: 'Courier New', monospace; background: #f4f7fb; padding: 2px 10px; border-radius: 4px; font-size: 14px; }
.offline-reason { color: #909399; font-size: 13px; margin-left: 8px; }

.metrics-row { margin: 8px 0; }
.metric-box { display: flex; align-items: center; gap: 16px; padding: 24px 20px; border-radius: 12px; background: #f6f8fc; transition: transform 0.2s; }
.metric-box:hover { transform: translateY(-2px); }
.metric-box.voltage { background: linear-gradient(135deg, #e0f2fe 0%, #f0f9ff 100%); }
.metric-box.current { background: linear-gradient(135deg, #fef3c7 0%, #fffbeb 100%); }
.metric-box.power { background: linear-gradient(135deg, #fee2e2 0%, #fef2f2 100%); }
.metric-icon { width: 56px; height: 56px; display: flex; align-items: center; justify-content: center; border-radius: 12px; background: #fff; color: #409eff; }
.metric-info { flex: 1; }
.metric-name { color: #7c8798; font-size: 14px; margin-bottom: 4px; }
.metric-value { font-size: 36px; font-weight: 700; color: #172033; line-height: 1.2; }
.metric-unit { font-size: 16px; font-weight: 400; color: #7c8798; margin-left: 4px; }

.footer-bar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
.footer-left { display: flex; align-items: center; gap: 8px; }
.last-update { color: #7c8798; font-size: 13px; }
.polling-tag { animation: pulse 1.5s ease-in-out infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }

.chart-container { width: 100%; height: 300px; }

.handled-text { color: #909399; font-size: 13px; }

.recommendation-list { display: flex; flex-direction: column; gap: 12px; }
.recommendation-item { padding: 16px; border-radius: 8px; background: #fff9e6; border-left: 4px solid #e6a23c; }
.rec-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.rec-title { font-weight: 600; color: #172033; }
.rec-content { color: #606266; font-size: 14px; margin: 0 0 12px; line-height: 1.6; }
.rec-footer { display: flex; align-items: center; justify-content: space-between; }
.rec-time { color: #909399; font-size: 13px; }
.rec-actions { display: flex; align-items: center; gap: 8px; }

.command-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.command-desc { display: flex; align-items: center; gap: 8px; }
.command-name { font-weight: 500; color: #172033; }
.cmd-item { display: flex; align-items: center; gap: 12px; }

.log-item { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.log-operator { font-weight: 500; color: #172033; }
.log-summary { color: #606266; font-size: 14px; }

.debug-hint { margin-top: 16px; }
.debug-hint p { margin: 4px 0; font-size: 13px; }

.sse-status-bar { display: flex; align-items: center; gap: 8px; margin: -4px 0 16px; padding: 8px 12px; background: #f6f8fc; border-radius: 6px; width: fit-content; }
.sse-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; flex-shrink: 0; }
.sse-dot.connected { background: #67c23a; }
.sse-dot.reconnecting { background: #e6a23c; animation: sse-pulse 1s ease-in-out infinite; }
.sse-dot.polling { background: #909399; }
.sse-label { font-size: 13px; color: #606266; }
@keyframes sse-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }

@media (max-width: 768px) {
  .metric-value { font-size: 28px; }
  .metric-box { padding: 16px 12px; margin-bottom: 12px; }
  .info-label { width: 70px; }
  .chart-container { height: 240px; }
}
</style>
