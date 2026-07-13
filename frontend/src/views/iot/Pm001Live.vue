<template>
  <div class="pm001-live">
    <h2>PM-001 实时数据监控</h2>
    <p class="sub-title">第三周 — 数据看板、历史曲线、告警与指令控制台</p>

    <!-- ========== 最新数据卡片 ========== -->
    <el-card v-loading="loading && !hasLoadedOnce" class="device-card">
      <template v-if="error">
        <el-alert title="数据加载失败" :description="error" type="error" :closable="false" show-icon />
        <div style="text-align: center; margin-top: 16px">
          <el-button type="primary" @click="loadData">重试</el-button>
        </div>
      </template>

      <template v-else-if="!loading && !hasData">
        <el-empty description="等待设备上报数据">
          <template #image>
            <el-icon :size="64" class="waiting-icon"><Loading /></el-icon>
          </template>
        </el-empty>
        <div class="waiting-hint">
          <el-icon><Timer /></el-icon>
          <span>页面每 {{ POLL_INTERVAL }} 秒自动刷新，等待模拟器发送数据...</span>
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
            <el-tag v-if="loading" type="warning" size="small" effect="plain" class="polling-tag">刷新中...</el-tag>
          </div>
          <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadData">刷新数据</el-button>
        </div>
      </template>
    </el-card>

    <!-- ========== 历史曲线 ========== -->
    <el-card class="section-card">
      <template #header>
        <div class="section-header">
          <el-icon size="18"><TrendCharts /></el-icon>
          <span>功率历史曲线</span>
          <el-tag size="small" type="info">近1小时</el-tag>
        </div>
      </template>
      <div ref="chartRef" class="chart-container"></div>
      <el-empty v-if="!chartData.length" description="暂无历史数据" />
    </el-card>

    <!-- ========== 告警中心 ========== -->
    <el-card class="section-card">
      <template #header>
        <div class="section-header">
          <el-icon size="18" color="#f56c6c"><Warning /></el-icon>
          <span>告警中心</span>
          <el-tag v-if="alerts.length" :type="alertTagType" size="small">{{ alerts.length }} 条</el-tag>
        </div>
      </template>
      <el-table :data="alerts" stripe style="width: 100%" v-loading="alertsLoading">
        <el-table-column prop="level" label="等级" width="90">
          <template #default="{ row }">
            <el-tag :type="row.level === 'WARNING' ? 'warning' : 'danger'" size="small">
              {{ row.level === 'WARNING' ? '警告' : '严重' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="告警内容" min-width="180" />
        <el-table-column prop="triggerValue" label="触发值" width="100">
          <template #default="{ row }">
            {{ row.triggerValue }} {{ row.metricKey === 'power' ? 'W' : '' }}
          </template>
        </el-table-column>
        <el-table-column prop="thresholdValue" label="阈值" width="100">
          <template #default="{ row }">
            {{ row.thresholdValue }} {{ row.metricKey === 'power' ? 'W' : '' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'OPEN' ? 'danger' : 'success'" size="small">
              {{ row.status === 'OPEN' ? '未处理' : '已处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="触发时间" width="160" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'OPEN'"
              type="primary"
              size="small"
              @click="doHandleAlert(row.id)"
            >处理</el-button>
            <span v-else class="handled-text">已处理</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!alerts.length && !alertsLoading" description="暂无告警" />
    </el-card>

    <!-- ========== 智能建议 ========== -->
    <el-card class="section-card">
      <template #header>
        <div class="section-header">
          <el-icon size="18" color="#e6a23c"><InfoFilled /></el-icon>
          <span>智能建议</span>
        </div>
      </template>
      <div v-if="recommendations.length" class="recommendation-list">
        <div v-for="rec in recommendations" :key="rec.id" class="recommendation-item">
          <div class="rec-header">
            <span class="rec-title">{{ rec.title }}</span>
            <el-tag v-if="rec.status === 'PENDING'" type="warning" size="small">待确认</el-tag>
            <el-tag v-else type="success" size="small">已确认</el-tag>
          </div>
          <p class="rec-content">{{ rec.content }}</p>
          <div class="rec-footer">
            <span class="rec-time">{{ rec.createdAt }}</span>
            <el-button
              v-if="rec.status === 'PENDING'"
              type="primary"
              size="small"
              @click="doConfirmRecommendation(rec.id)"
            >确认建议</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无建议" />
    </el-card>

    <!-- ========== 指令控制台 ========== -->
    <el-card class="section-card">
      <template #header>
        <div class="section-header">
          <el-icon size="18" color="#409eff"><SwitchButton /></el-icon>
          <span>指令控制台</span>
        </div>
      </template>

      <el-form :model="commandForm" label-width="120px" class="command-form">
        <el-form-item label="指令类型">
          <el-select v-model="commandForm.command" placeholder="选择指令">
            <el-option label="设置采样间隔" value="SET_SAMPLE_INTERVAL" />
            <el-option label="重启设备" value="REBOOT" />
            <el-option label="查询状态" value="QUERY_STATUS" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="commandForm.command === 'SET_SAMPLE_INTERVAL'" label="采样间隔(秒)">
          <el-input-number v-model="commandForm.params.intervalSeconds" :min="1" :max="60" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="commandLoading" @click="sendCommand">下发指令</el-button>
        </el-form-item>
      </el-form>

      <el-divider v-if="commands.length" content-position="left">指令记录</el-divider>

      <el-timeline v-if="commands.length">
        <el-timeline-item
          v-for="cmd in commands"
          :key="cmd.commandId"
          :type="cmd.status === 'ACKED' ? 'success' : cmd.status === 'FAILED' ? 'danger' : 'primary'"
          :timestamp="cmd.createdAt"
        >
          <div class="cmd-item">
            <span class="cmd-name">{{ commandNameMap[cmd.command] || cmd.command }}</span>
            <el-tag :type="commandStatusType[cmd.status] || 'info'" size="small">
              {{ commandStatusText[cmd.status] || cmd.status }}
            </el-tag>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- ========== 操作日志 ========== -->
    <el-card class="section-card">
      <template #header>
        <div class="section-header">
          <el-icon size="18"><Document /></el-icon>
          <span>操作日志</span>
        </div>
      </template>
      <el-timeline v-if="operationLogs.length">
        <el-timeline-item
          v-for="log in operationLogs"
          :key="log.id"
          :timestamp="log.createdAt"
        >
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
      <p>接口：GET /api/iot/devices/1/latest | GET /api/iot/devices/1/metrics/history</p>
      <p>数据链路：模拟器 → MQTT → 后端入库 → 接口返回 → 前端展示</p>
      <p>自动刷新：每 {{ POLL_INTERVAL }} 秒 | 在线判断：上报时间距当前 15 秒内为在线</p>
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
  getLatestMetrics,
  getMetricHistory,
  listAlerts,
  handleAlert,
  listRecommendations,
  confirmRecommendation,
  sendCommand as apiSendCommand,
  listCommands,
  listOperationLogs
} from '@/api/iot'
import * as echarts from 'echarts'

// ========== 配置 ==========
const POLL_INTERVAL = 5
const ONLINE_THRESHOLD = 15
const DEVICE_ID = 1

// ========== 最新数据 ==========
const loading = ref(false)
const error = ref('')
const hasLoadedOnce = ref(false)
const latestData = ref({ deviceCode: '', deviceName: '', projectName: '', status: '', reportTime: '', metrics: [] })
const lastUpdateTime = ref('')
let pollTimer = null

const hasData = computed(() => !!(latestData.value.deviceCode || latestData.value.metrics?.length))
const iconMap = { voltage: OfficeBuilding, current: Magnet, power: Lightning }
const reportTime = computed(() => latestData.value.reportTime || latestData.value.reportedAt || '')

const isOnline = computed(() => {
  const timeStr = reportTime.value
  if (!timeStr) return false
  const reportTs = new Date(timeStr.replace(' ', 'T')).getTime()
  if (isNaN(reportTs)) return latestData.value.status === 'ONLINE'
  return Date.now() - reportTs <= ONLINE_THRESHOLD * 1000
})

const metricsList = computed(() => {
  return (latestData.value.metrics || []).map(m => ({ ...m, icon: iconMap[m.metricKey] || Cpu }))
})

function formatValue(val) {
  return val !== undefined && val !== null ? val.toFixed(2) : '--'
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const res = await getLatestMetrics(DEVICE_ID)
    const payload = res.data || res || {}
    latestData.value = {
      deviceCode: payload.deviceCode || '',
      deviceName: payload.deviceName || '',
      projectName: payload.projectName || '',
      status: payload.status || '',
      reportTime: payload.reportTime || payload.reportedAt || '',
      metrics: payload.metrics || []
    }
    lastUpdateTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
    hasLoadedOnce.value = true
  } catch (err) {
    console.error('加载最新数据失败', err)
    error.value = err.message || '接口请求失败'
  } finally {
    loading.value = false
  }
}

// ========== 历史曲线 ==========
const chartRef = ref(null)
let chartInstance = null
const chartData = ref([])

async function loadHistory() {
  try {
    const res = await getMetricHistory(DEVICE_ID, { metricKey: 'power' })
    const payload = res.data || res || {}
    chartData.value = payload.points || []
    nextTick(() => renderChart())
  } catch (err) {
    console.error('加载历史数据失败', err)
  }
}

function renderChart() {
  if (!chartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }
  const points = chartData.value
  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: points.map(p => p.time?.slice(11, 16) || ''),
      axisLabel: { color: '#7c8798' }
    },
    yAxis: {
      type: 'value',
      name: '功率 (W)',
      axisLabel: { color: '#7c8798' },
      splitLine: { lineStyle: { color: '#f0f0f0' } }
    },
    series: [{
      name: '功率',
      type: 'line',
      smooth: true,
      data: points.map(p => p.value),
      lineStyle: { color: '#409eff', width: 3 },
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
  })
}

// ========== 告警 ==========
const alerts = ref([])
const alertsLoading = ref(false)
const alertTagType = computed(() => alerts.value.some(a => a.level === 'CRITICAL') ? 'danger' : 'warning')

async function loadAlerts() {
  alertsLoading.value = true
  try {
    const res = await listAlerts({ deviceId: DEVICE_ID })
    const payload = res.data || res || {}
    alerts.value = payload.records || payload || []
  } catch (err) {
    console.error('加载告警失败', err)
  } finally {
    alertsLoading.value = false
  }
}

async function doHandleAlert(id) {
  try {
    await handleAlert(id, { status: 'HANDLED', note: '已在页面确认处理' })
    await loadAlerts()
  } catch (err) {
    console.error('处理告警失败', err)
  }
}

// ========== 建议 ==========
const recommendations = ref([])

async function loadRecommendations() {
  try {
    const res = await listRecommendations({ deviceId: DEVICE_ID })
    const payload = res.data || res || {}
    recommendations.value = payload.records || payload || []
  } catch (err) {
    console.error('加载建议失败', err)
  }
}

async function doConfirmRecommendation(id) {
  try {
    await confirmRecommendation(id)
    await loadRecommendations()
  } catch (err) {
    console.error('确认建议失败', err)
  }
}

// ========== 指令 ==========
const commands = ref([])
const commandLoading = ref(false)
const commandForm = ref({
  command: 'SET_SAMPLE_INTERVAL',
  params: { intervalSeconds: 5 }
})

const commandNameMap = {
  SET_SAMPLE_INTERVAL: '设置采样间隔',
  REBOOT: '重启设备',
  QUERY_STATUS: '查询状态'
}

const commandStatusType = {
  PENDING: 'info',
  SENT: 'primary',
  ACKED: 'success',
  FAILED: 'danger',
  TIMEOUT: 'warning'
}

const commandStatusText = {
  PENDING: '等待中',
  SENT: '已发送',
  ACKED: '已确认',
  FAILED: '失败',
  TIMEOUT: '超时'
}

async function loadCommands() {
  try {
    const res = await listCommands({ deviceId: DEVICE_ID })
    const payload = res.data || res || {}
    commands.value = payload.records || payload || []
  } catch (err) {
    console.error('加载指令记录失败', err)
  }
}

async function sendCommand() {
  commandLoading.value = true
  try {
    await apiSendCommand(DEVICE_ID, commandForm.value)
    await loadCommands()
  } catch (err) {
    console.error('下发指令失败', err)
  } finally {
    commandLoading.value = false
  }
}

// ========== 操作日志 ==========
const operationLogs = ref([])

async function loadOperationLogs() {
  try {
    const res = await listOperationLogs({ targetType: 'DEVICE', targetId: DEVICE_ID })
    const payload = res.data || res || {}
    operationLogs.value = payload.records || payload || []
  } catch (err) {
    console.error('加载操作日志失败', err)
  }
}

// ========== 轮询 ==========
function startPoll() {
  stopPoll()
  pollTimer = setInterval(() => {
    loadData()
    loadHistory()
    loadAlerts()
    loadCommands()
  }, POLL_INTERVAL * 1000)
}

function stopPoll() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

// ========== 生命周期 ==========
onMounted(() => {
  loadData()
  loadHistory()
  loadAlerts()
  loadRecommendations()
  loadCommands()
  loadOperationLogs()
  startPoll()
  window.addEventListener('resize', () => chartInstance?.resize())
})

onUnmounted(() => {
  stopPoll()
  if (chartInstance) { chartInstance.dispose(); chartInstance = null }
})
</script>

<style scoped>
.pm001-live {
  padding: 8px;
  max-width: 960px;
  margin: 0 auto;
}

.sub-title {
  color: #7c8798;
  margin: -8px 0 16px;
}

/* 通用区域卡片 */
.section-card {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #172033;
}

/* 最新数据区域 */
.device-card {
  margin-bottom: 20px;
}

.waiting-icon {
  color: #c0c4cc;
  animation: rotate 2s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.waiting-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #909399;
  font-size: 14px;
  margin-top: 8px;
}

.device-info {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 32px;
}

.info-label {
  width: 80px;
  color: #7c8798;
  font-size: 14px;
  text-align: right;
  flex-shrink: 0;
}

.info-value {
  font-size: 15px;
  color: #172033;
  font-weight: 500;
}

.info-value.code {
  font-family: 'Courier New', monospace;
  background: #f4f7fb;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 14px;
}

.offline-reason {
  color: #909399;
  font-size: 13px;
  margin-left: 8px;
}

.metrics-row {
  margin: 8px 0;
}

.metric-box {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 20px;
  border-radius: 12px;
  background: #f6f8fc;
  transition: transform 0.2s;
}

.metric-box:hover { transform: translateY(-2px); }

.metric-box.voltage { background: linear-gradient(135deg, #e0f2fe 0%, #f0f9ff 100%); }
.metric-box.current { background: linear-gradient(135deg, #fef3c7 0%, #fffbeb 100%); }
.metric-box.power { background: linear-gradient(135deg, #fee2e2 0%, #fef2f2 100%); }

.metric-icon {
  width: 56px; height: 56px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 12px; background: #fff; color: #409eff;
}

.metric-info { flex: 1; }
.metric-name { color: #7c8798; font-size: 14px; margin-bottom: 4px; }
.metric-value { font-size: 36px; font-weight: 700; color: #172033; line-height: 1.2; }
.metric-unit { font-size: 16px; font-weight: 400; color: #7c8798; margin-left: 4px; }

.footer-bar {
  display: flex; align-items: center; justify-content: space-between;
  flex-wrap: wrap; gap: 12px;
}

.footer-left { display: flex; align-items: center; gap: 8px; }
.last-update { color: #7c8798; font-size: 13px; }
.polling-tag { animation: pulse 1.5s ease-in-out infinite; }

@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }

/* 历史曲线 */
.chart-container { width: 100%; height: 300px; }

/* 建议 */
.recommendation-list { display: flex; flex-direction: column; gap: 12px; }
.recommendation-item {
  padding: 16px;
  border-radius: 8px;
  background: #fff9e6;
  border-left: 4px solid #e6a23c;
}

.rec-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.rec-title { font-weight: 600; color: #172033; }
.rec-content { color: #606266; font-size: 14px; margin: 0 0 12px; line-height: 1.6; }
.rec-footer { display: flex; align-items: center; justify-content: space-between; }
.rec-time { color: #909399; font-size: 13px; }

/* 指令 */
.command-form { max-width: 500px; }
.cmd-item { display: flex; align-items: center; gap: 12px; }
.cmd-name { font-weight: 500; color: #172033; }

/* 日志 */
.log-item { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.log-operator { font-weight: 500; color: #172033; }
.log-summary { color: #606266; font-size: 14px; }

/* 已处理 */
.handled-text { color: #909399; font-size: 13px; }

/* 联调说明 */
.debug-hint { margin-top: 16px; }
.debug-hint p { margin: 4px 0; font-size: 13px; }

@media (max-width: 768px) {
  .metric-value { font-size: 28px; }
  .metric-box { padding: 16px 12px; margin-bottom: 12px; }
  .info-label { width: 70px; }
  .chart-container { height: 240px; }
}
</style>
