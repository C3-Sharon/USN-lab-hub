<template>
  <div class="iot-device-detail">
    <!-- 设备基本信息 -->
    <el-card class="device-header" v-loading="deviceLoading">
      <template v-if="deviceError">
        <el-alert title="加载设备信息失败" type="error" :description="deviceError" :closable="false" show-icon />
      </template>
      <template v-else-if="!deviceLoading && !device.deviceCode">
        <el-empty description="设备信息为空" />
      </template>
      <template v-else>
        <div class="device-title">
          <h2>
            {{ device.deviceName }}
            <el-tag :type="statusTagType(device.status)" size="small">{{ device.status }}</el-tag>
          </h2>
          <p class="device-meta">
            编号：{{ device.deviceCode }} | 类型：{{ device.deviceType }} | 协议：{{ device.protocol }} |
            位置：{{ device.location }} | 负责人：{{ device.ownerName }}
          </p>
        </div>
      </template>
    </el-card>

    <!-- 刷新操作栏 -->
    <div class="action-bar">
      <span class="last-update" v-if="lastUpdateTime">上次更新：{{ lastUpdateTime }}</span>
      <el-button text @click="handleManualRefresh" :loading="refreshing" :icon="Refresh">
        {{ refreshing ? '刷新中...' : '刷新数据' }}
      </el-button>
    </div>

    <!-- 最新指标卡片 -->
    <el-row :gutter="16" class="metric-cards" v-loading="metricsLoading">
      <template v-if="metricsError">
        <el-col :span="24">
          <el-alert title="加载指标数据失败" type="error" :description="metricsError" :closable="false" show-icon />
        </el-col>
      </template>
      <template v-else-if="!latestMetrics.metrics.length">
        <el-col :span="24">
          <el-empty description="暂无指标数据" />
        </el-col>
      </template>
      <el-col :span="8" v-for="m in latestMetrics.metrics" :key="m.metricKey" v-else>
        <el-card>
          <div class="metric-name">{{ m.metricName }}</div>
          <div class="metric-value">
            {{ formatValue(m.value) }}
            <span class="metric-unit">{{ m.unit }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- power 历史曲线 -->
    <el-card class="chart-card" v-loading="historyLoading">
      <template #header>
        <span>功率历史趋势</span>
      </template>
      <template v-if="historyError">
        <el-alert title="加载历史数据失败" type="error" :description="historyError" :closable="false" show-icon />
      </template>
      <template v-else-if="!historyData.points.length">
        <el-empty description="暂无历史数据" />
      </template>
      <div v-else ref="chartRef" class="chart-container"></div>
    </el-card>

    <!-- 告警与建议 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card v-loading="alertsLoading">
          <template #header>
            <span>告警记录</span>
          </template>
          <template v-if="alertsError">
            <el-alert title="加载告警失败" type="error" :description="alertsError" :closable="false" show-icon />
          </template>
          <template v-else-if="!alerts.length">
            <el-empty description="暂无告警" />
          </template>
          <div v-for="alert in alerts" :key="alert.id" class="alert-item">
            <el-alert
              :title="alert.message"
              :type="alert.level === 'WARNING' ? 'warning' : 'error'"
              :description="`触发值: ${alert.triggerValue}${alert.thresholdValue ? ' | 阈值: ' + alert.thresholdValue : ''} | ${alert.createdAt}`"
              :closable="false"
              show-icon
            />
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card v-loading="recommendationsLoading">
          <template #header>
            <span>智能建议</span>
          </template>
          <template v-if="recommendationsError">
            <el-alert title="加载建议失败" type="error" :description="recommendationsError" :closable="false" show-icon />
          </template>
          <template v-else-if="!recommendations.length">
            <el-empty description="暂无建议" />
          </template>
          <div v-for="rec in recommendations" :key="rec.id" class="recommendation-item">
            <el-alert
              :title="rec.title"
              type="info"
              :description="rec.content"
              :closable="false"
              show-icon
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 指令控制台 -->
    <el-card class="command-card">
      <template #header>
        <span>指令控制台</span>
      </template>
      <div class="command-form">
        <el-form inline>
          <el-form-item label="指令">
            <el-select v-model="commandForm.command" style="width: 200px">
              <el-option label="设置采样间隔" value="SET_SAMPLE_INTERVAL" />
            </el-select>
          </el-form-item>
          <el-form-item label="间隔(秒)">
            <el-input-number v-model="commandForm.params.intervalSeconds" :min="1" :max="60" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="sendingCommand" @click="handleSendCommand">
              下发指令
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-divider />

      <h4>指令记录</h4>
      <template v-if="commandsError">
        <el-alert title="加载指令记录失败" type="error" :description="commandsError" :closable="false" show-icon />
      </template>
      <template v-else-if="!commands.length">
        <el-empty description="暂无指令记录" />
      </template>
      <el-table v-else :data="commands" size="small" stripe>
        <el-table-column prop="commandId" label="指令ID" width="160" />
        <el-table-column prop="command" label="指令" width="160" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="commandStatusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" />
      </el-table>
    </el-card>

    <!-- 操作日志预览 -->
    <el-card class="log-card">
      <template #header>
        <span>操作日志</span>
      </template>
      <template v-if="logsError">
        <el-alert title="加载操作日志失败" type="error" :description="logsError" :closable="false" show-icon />
      </template>
      <template v-else-if="!operationLogs.length">
        <el-empty description="暂无操作日志" />
      </template>
      <el-table v-else :data="operationLogs" size="small" stripe>
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="action" label="动作" width="120" />
        <el-table-column prop="summary" label="摘要" />
        <el-table-column prop="createdAt" label="时间" width="160" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  getDeviceDetail,
  getLatestMetrics,
  getMetricHistory,
  listAlerts,
  listRecommendations,
  sendCommand,
  listCommands,
  listOperationLogs
} from '@/api/iot'

const route = useRoute()
const deviceId = route.params.id

// 设备信息
const device = ref({})
const deviceLoading = ref(false)
const deviceError = ref('')

// 最新指标
const latestMetrics = ref({ metrics: [] })
const metricsLoading = ref(false)
const metricsError = ref('')

// 历史数据
const historyData = ref({ points: [] })
const historyLoading = ref(false)
const historyError = ref('')
let chartInstance = null
const chartRef = ref(null)

// 告警
const alerts = ref([])
const alertsLoading = ref(false)
const alertsError = ref('')

// 建议
const recommendations = ref([])
const recommendationsLoading = ref(false)
const recommendationsError = ref('')

// 指令
const commands = ref([])
const commandsError = ref('')
const sendingCommand = ref(false)
const commandForm = ref({
  command: 'SET_SAMPLE_INTERVAL',
  params: { intervalSeconds: 5 }
})

// 操作日志
const operationLogs = ref([])
const logsError = ref('')

// 刷新状态
const refreshing = ref(false)
const lastUpdateTime = ref('')
let pollTimer = null

function statusTagType(status) {
  const map = { ONLINE: 'success', OFFLINE: 'info', ALERT: 'danger', MAINTENANCE: 'warning' }
  return map[status] || 'info'
}

function commandStatusType(status) {
  const map = { PENDING: 'info', SENT: 'primary', ACKED: 'success', FAILED: 'danger', TIMEOUT: 'warning' }
  return map[status] || 'info'
}

function formatValue(val) {
  return val !== undefined && val !== null ? val.toFixed(2) : '--'
}

function updateTime() {
  lastUpdateTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

async function loadDevice() {
  deviceLoading.value = true
  deviceError.value = ''
  try {
    const res = await getDeviceDetail(deviceId)
    device.value = res.data || {}
  } catch (err) {
    console.error('加载设备详情失败', err)
    deviceError.value = err.message || '接口请求失败，请检查网络或后端服务'
  } finally {
    deviceLoading.value = false
  }
}

async function loadMetrics() {
  metricsLoading.value = true
  metricsError.value = ''
  try {
    const res = await getLatestMetrics(deviceId)
    latestMetrics.value = res.data || { metrics: [] }
  } catch (err) {
    console.error('加载最新指标失败', err)
    metricsError.value = err.message || '接口请求失败'
  } finally {
    metricsLoading.value = false
  }
}

async function loadHistory() {
  historyLoading.value = true
  historyError.value = ''
  try {
    const res = await getMetricHistory(deviceId, { metricKey: 'power' })
    historyData.value = res.data || { points: [] }
    await nextTick()
    renderChart()
  } catch (err) {
    console.error('加载历史数据失败', err)
    historyError.value = err.message || '接口请求失败'
  } finally {
    historyLoading.value = false
  }
}

function renderChart() {
  if (!chartRef.value || !historyData.value.points.length) return

  if (chartInstance) {
    chartInstance.dispose()
  }

  chartInstance = echarts.init(chartRef.value)
  const points = historyData.value.points

  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: points.map(p => p.time.slice(11, 16)),
      axisLabel: { rotate: 30 }
    },
    yAxis: {
      type: 'value',
      name: '功率 (W)',
      min: 0
    },
    series: [{
      data: points.map(p => p.value),
      type: 'line',
      smooth: true,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.3)' },
          { offset: 1, color: 'rgba(64,158,255,0.05)' }
        ])
      },
      lineStyle: { color: '#409EFF', width: 2 },
      itemStyle: { color: '#409EFF' },
      markLine: {
        data: [{ yAxis: 100, name: '阈值', label: { formatter: '阈值 100W' } }],
        lineStyle: { color: '#f56c6c', type: 'dashed' }
      }
    }],
    grid: { left: 60, right: 30, top: 30, bottom: 50 }
  })
}

async function loadAlerts() {
  alertsLoading.value = true
  alertsError.value = ''
  try {
    const res = await listAlerts({ deviceId })
    alerts.value = res.data.records || []
  } catch (err) {
    console.error('加载告警失败', err)
    alertsError.value = err.message || '接口请求失败'
  } finally {
    alertsLoading.value = false
  }
}

async function loadRecommendations() {
  recommendationsLoading.value = true
  recommendationsError.value = ''
  try {
    const res = await listRecommendations({ deviceId })
    recommendations.value = res.data.records || []
  } catch (err) {
    console.error('加载建议失败', err)
    recommendationsError.value = err.message || '接口请求失败'
  } finally {
    recommendationsLoading.value = false
  }
}

async function loadCommands() {
  commandsError.value = ''
  try {
    const res = await listCommands({ deviceId })
    commands.value = res.data.records || []
  } catch (err) {
    console.error('加载指令记录失败', err)
    commandsError.value = err.message || '接口请求失败'
  }
}

async function handleSendCommand() {
  try {
    await ElMessageBox.confirm(
      `确认向 ${device.value.deviceCode} 下发 ${commandForm.value.command} 指令，间隔 ${commandForm.value.params.intervalSeconds} 秒？`,
      '指令确认',
      { confirmButtonText: '确认下发', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    // 用户取消
    return
  }

  sendingCommand.value = true
  try {
    await sendCommand(deviceId, commandForm.value)
    ElMessage.success('指令下发成功')
    await loadCommands()
    await loadOperationLogs()
    updateTime()
  } catch (err) {
    console.error('下发指令失败', err)
    ElMessage.error('指令下发失败：' + (err.message || '接口请求失败'))
  } finally {
    sendingCommand.value = false
  }
}

async function loadOperationLogs() {
  logsError.value = ''
  try {
    const res = await listOperationLogs({ targetId: deviceId, targetType: 'DEVICE' })
    operationLogs.value = res.data.records || []
  } catch (err) {
    console.error('加载操作日志失败', err)
    logsError.value = err.message || '接口请求失败'
  }
}

async function loadAll() {
  await Promise.all([
    loadDevice(),
    loadMetrics(),
    loadHistory(),
    loadAlerts(),
    loadRecommendations(),
    loadCommands(),
    loadOperationLogs()
  ])
  updateTime()
}

async function handleManualRefresh() {
  refreshing.value = true
  await loadAll()
  refreshing.value = false
  ElMessage.success('数据已刷新')
}

// 定时轮询指标（每10秒）
function startPolling() {
  pollTimer = setInterval(async () => {
    await loadMetrics()
    updateTime()
  }, 10000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(() => {
  loadAll()
  startPolling()
  window.addEventListener('resize', () => chartInstance?.resize())
})

onUnmounted(() => {
  stopPolling()
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
  window.removeEventListener('resize', () => chartInstance?.resize())
})
</script>

<style scoped>
.iot-device-detail {
  padding: 8px;
}
.device-header {
  margin-bottom: 16px;
}
.device-title h2 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}
.device-meta {
  margin: 8px 0 0;
  color: #7c8798;
  font-size: 14px;
}
.action-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.last-update {
  font-size: 13px;
  color: #7c8798;
}
.metric-cards {
  margin-bottom: 16px;
}
.metric-cards .el-col {
  margin-bottom: 16px;
}
.metric-name {
  color: #7c8798;
  font-size: 14px;
}
.metric-value {
  font-size: 32px;
  font-weight: 700;
  color: #172033;
  margin-top: 8px;
}
.metric-unit {
  font-size: 16px;
  color: #7c8798;
  font-weight: 400;
  margin-left: 4px;
}
.chart-card {
  margin-bottom: 16px;
}
.chart-container {
  height: 320px;
}
.alert-item,
.recommendation-item {
  margin-bottom: 12px;
}
.command-card,
.log-card {
  margin-top: 16px;
}
.command-form {
  padding: 8px 0;
}
</style>