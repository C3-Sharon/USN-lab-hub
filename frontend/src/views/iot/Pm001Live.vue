<template>
  <div class="pm001-live">
    <h2>PM-001 实时数据监控</h2>
    <p class="sub-title">第三周 — latest 实时数据 + power 历史曲线</p>

    <!-- ========== 最新数据卡片 ========== -->
    <el-card v-loading="latestLoading && !latestLoaded" class="device-card">
      <!-- 错误状态 -->
      <template v-if="latestError">
        <el-alert
          title="最新数据加载失败"
          :description="latestError"
          type="error"
          :closable="false"
          show-icon
        />
        <div style="text-align: center; margin-top: 16px">
          <el-button type="primary" @click="loadLatest">重试</el-button>
        </div>
      </template>

      <!-- 等待数据上报 -->
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

      <!-- 正常数据展示 -->
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
            <el-tag v-if="latestLoading" type="warning" size="small" effect="plain" class="polling-tag">
              刷新中...
            </el-tag>
          </div>
          <el-button type="primary" :icon="Refresh" :loading="latestLoading" @click="loadLatest">
            刷新数据
          </el-button>
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

      <!-- 历史数据错误 -->
      <template v-if="historyError && !chartData.length">
        <el-alert
          title="历史数据加载失败"
          :description="historyError"
          type="error"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        />
        <div style="text-align: center">
          <el-button type="primary" @click="loadHistory">重试</el-button>
        </div>
      </template>

      <!-- 历史数据空状态 -->
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

      <!-- 历史数据图表 -->
      <div v-show="chartData.length" ref="chartRef" class="chart-container"></div>
    </el-card>

    <!-- ========== 联调说明 ========== -->
    <el-alert title="联调说明" type="info" :closable="false" class="debug-hint">
      <p>latest 接口：GET /api/iot/devices/1/latest | history 接口：GET /api/iot/devices/1/metrics/history?metricKey=power</p>
      <p>latest 自动刷新：每 {{ LATEST_INTERVAL }} 秒 | history 自动刷新：每 {{ HISTORY_INTERVAL }} 秒</p>
      <p>在线判断：上报时间距当前 15 秒内为在线</p>
    </el-alert>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { Cpu, Refresh, Lightning, Magnet, OfficeBuilding, Timer, Loading, TrendCharts } from '@element-plus/icons-vue'
import { getLatestMetrics, getMetricHistory } from '@/api/iot'
import * as echarts from 'echarts'

// ========== 配置 ==========
const LATEST_INTERVAL = 5
const HISTORY_INTERVAL = 30
const ONLINE_THRESHOLD = 15
const DEVICE_ID = 1

// ========== 最新数据 ==========
const latestLoading = ref(false)
const latestError = ref('')
const latestLoaded = ref(false)
const latestData = ref({
  deviceCode: '',
  deviceName: '',
  projectName: '',
  status: '',
  reportTime: '',
  metrics: []
})
const lastUpdateTime = ref('')
let latestPollTimer = null

const hasLatestData = computed(() => {
  return !!(latestData.value.deviceCode || latestData.value.metrics?.length)
})

const iconMap = {
  voltage: OfficeBuilding,
  current: Magnet,
  power: Lightning
}

const reportTime = computed(() => {
  return latestData.value.reportTime || latestData.value.reportedAt || ''
})

const isOnline = computed(() => {
  const timeStr = reportTime.value
  if (!timeStr) return false
  const reportTs = new Date(timeStr.replace(' ', 'T')).getTime()
  if (isNaN(reportTs)) return latestData.value.status === 'ONLINE'
  return Date.now() - reportTs <= ONLINE_THRESHOLD * 1000
})

const metricsList = computed(() => {
  const list = latestData.value.metrics || []
  return list.map(m => ({
    ...m,
    icon: iconMap[m.metricKey] || Cpu
  }))
})

function formatValue(val) {
  return val !== undefined && val !== null ? val.toFixed(2) : '--'
}

async function loadLatest() {
  latestLoading.value = true
  latestError.value = ''
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
    latestLoaded.value = true
  } catch (err) {
    console.error('加载最新数据失败', err)
    latestError.value = err.message || '接口请求失败，请检查后端服务是否启动'
  } finally {
    latestLoading.value = false
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
    // axios 拦截器已解包，res 直接是内层数据
    const payload = res || {}
    chartData.value = payload.points || []
    if (chartData.value.length) {
      nextTick(() => renderChart())
    }
  } catch (err) {
    console.error('加载历史数据失败', err)
    historyError.value = err.message || '历史数据接口请求失败'
  } finally {
    historyLoading.value = false
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
      symbolSize: 8,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.3)' },
          { offset: 1, color: 'rgba(64,158,255,0.05)' }
        ])
      },
      markLine: {
        silent: true,
        data: [{
          yAxis: 100,
          label: { formatter: '阈值 100W' },
          lineStyle: { color: '#f56c6c', type: 'dashed' }
        }]
      }
    }]
  }, true)
}

// ========== resize 处理 ==========
function onChartResize() {
  chartInstance?.resize()
}

// ========== 轮询 ==========
function startLatestPoll() {
  stopLatestPoll()
  latestPollTimer = setInterval(() => {
    loadLatest()
  }, LATEST_INTERVAL * 1000)
}

function stopLatestPoll() {
  if (latestPollTimer) {
    clearInterval(latestPollTimer)
    latestPollTimer = null
  }
}

function startHistoryPoll() {
  stopHistoryPoll()
  historyPollTimer = setInterval(() => {
    loadHistory()
  }, HISTORY_INTERVAL * 1000)
}

function stopHistoryPoll() {
  if (historyPollTimer) {
    clearInterval(historyPollTimer)
    historyPollTimer = null
  }
}

// ========== 生命周期 ==========
onMounted(() => {
  loadLatest()
  loadHistory()
  startLatestPoll()
  startHistoryPoll()
  window.addEventListener('resize', onChartResize)
})

onUnmounted(() => {
  stopLatestPoll()
  stopHistoryPoll()
  window.removeEventListener('resize', onChartResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
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

.device-card {
  margin-bottom: 20px;
}

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

.metric-box:hover {
  transform: translateY(-2px);
}

.metric-box.voltage {
  background: linear-gradient(135deg, #e0f2fe 0%, #f0f9ff 100%);
}

.metric-box.current {
  background: linear-gradient(135deg, #fef3c7 0%, #fffbeb 100%);
}

.metric-box.power {
  background: linear-gradient(135deg, #fee2e2 0%, #fef2f2 100%);
}

.metric-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: #fff;
  color: #409eff;
}

.metric-info {
  flex: 1;
}

.metric-name {
  color: #7c8798;
  font-size: 14px;
  margin-bottom: 4px;
}

.metric-value {
  font-size: 36px;
  font-weight: 700;
  color: #172033;
  line-height: 1.2;
}

.metric-unit {
  font-size: 16px;
  font-weight: 400;
  color: #7c8798;
  margin-left: 4px;
}

.footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.last-update {
  color: #7c8798;
  font-size: 13px;
}

.polling-tag {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.chart-container {
  width: 100%;
  height: 300px;
}

.debug-hint {
  margin-top: 16px;
}

.debug-hint p {
  margin: 4px 0;
  font-size: 13px;
}

@media (max-width: 768px) {
  .metric-value {
    font-size: 28px;
  }

  .metric-box {
    padding: 16px 12px;
    margin-bottom: 12px;
  }

  .info-label {
    width: 70px;
  }

  .chart-container {
    height: 240px;
  }
}
</style>
