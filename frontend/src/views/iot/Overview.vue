<template>
  <div class="iot-overview">
    <h2>IoT 总览</h2>
    <p class="sub-title">功耗检测项目实时摘要</p>

    <template v-if="error && !loaded">
      <el-alert title="数据加载失败" :description="error" type="error" :closable="false" show-icon />
      <div style="text-align: center; margin-top: 16px">
        <el-button type="primary" @click="loadData">重试</el-button>
      </div>
    </template>

    <template v-else>
      <!-- 统计卡片行 -->
      <el-row :gutter="16" class="stat-row">
        <el-col :xs="12" :sm="8" :md="4">
          <el-card class="stat-card" v-loading="loading">
            <div class="stat-label">项目</div>
            <div class="stat-value">{{ statProjectCount }}</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card class="stat-card" v-loading="loading">
            <div class="stat-label">设备</div>
            <div class="stat-value">{{ statDeviceCount }}</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card class="stat-card" v-loading="loading">
            <div class="stat-label">在线设备</div>
            <div class="stat-value" :class="statOnlineCount > 0 ? 'online' : ''">{{ statOnlineCount }}</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card class="stat-card alert-card" v-loading="loading">
            <div class="stat-label">未处理告警</div>
            <div class="stat-value" :class="statOpenAlertCount > 0 ? 'alert' : ''">{{ statOpenAlertCount }}</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card class="stat-card" v-loading="loading">
            <div class="stat-label">最新功率</div>
            <div class="stat-value power">{{ latestPower }} <span class="unit">W</span></div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card class="stat-card health-card" v-loading="loading">
            <div class="stat-label">健康评分</div>
            <div class="stat-value" :class="healthLevelClass">{{ healthScore.score ?? '--' }}</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- PM-001 摘要 -->
      <el-row :gutter="20" class="summary-row">
        <el-col :xs="24" :md="12">
          <el-card class="summary-card" v-loading="loading">
            <template #header>
              <div class="card-header">
                <el-icon size="18"><Cpu /></el-icon>
                <span>PM-001 状态</span>
                <el-tag :type="isOnline ? 'success' : 'info'" size="small">{{ isOnline ? '在线' : '离线' }}</el-tag>
              </div>
            </template>
            <div class="pm-summary">
              <div class="pm-item">
                <span class="pm-label">设备编号</span>
                <span class="pm-value code">{{ device.deviceCode || '--' }}</span>
              </div>
              <div class="pm-item">
                <span class="pm-label">设备名称</span>
                <span class="pm-value">{{ device.deviceName || '--' }}</span>
              </div>
              <div class="pm-item">
                <span class="pm-label">上报时间</span>
                <span class="pm-value">{{ device.reportTime || '--' }}</span>
              </div>
              <div class="pm-item">
                <span class="pm-label">健康等级</span>
                <el-tag :type="healthTagType" size="small">{{ healthLevelText }}</el-tag>
              </div>
              <div v-if="healthScore.reasons?.length" class="pm-item reasons">
                <span class="pm-label">健康原因</span>
                <div class="reason-tags">
                  <el-tag v-for="(r, i) in healthScore.reasons" :key="i" size="small" type="info" effect="plain">{{ r }}</el-tag>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="12">
          <el-card class="summary-card" v-loading="loading">
            <template #header>
              <div class="card-header">
                <el-icon size="18"><TrendCharts /></el-icon>
                <span>功率趋势</span>
              </div>
            </template>
            <template v-if="!trendPoints.length && !loading">
              <el-empty description="暂无趋势数据" />
            </template>
            <div v-show="trendPoints.length" ref="trendRef" class="mini-trend"></div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 快捷入口 -->
      <el-card class="action-card">
        <div class="action-title">快速入口</div>
        <div class="action-buttons">
          <a href="/iot/public" target="_blank" rel="noopener noreferrer">
            <el-button type="primary" size="large">
              <el-icon><Monitor /></el-icon> 公开演示页
            </el-button>
          </a>
          <router-link to="/iot/pm001">
            <el-button type="success" size="large">
              <el-icon><Cpu /></el-icon> PM-001 详情
            </el-button>
          </router-link>
          <el-button size="large" :icon="Refresh" :loading="loading" @click="loadData">刷新数据</el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { Cpu, TrendCharts, Monitor, Refresh } from '@element-plus/icons-vue'
import { getPublicProject, listAlerts } from '@/api/iot'
import * as echarts from 'echarts'

const PROJECT_CODE = 'power-monitor'
const DEVICE_ID = 1
const REFRESH_INTERVAL = 30

// ========== 数据状态 ==========
const loading = ref(false)
const loaded = ref(false)
const error = ref('')

const project = ref({})
const device = ref({})
const latestMetrics = ref([])
const powerTrend = ref([])
const healthScore = ref({})
const openAlertCount = ref(0)

let refreshTimer = null
let trendChartInstance = null
const trendRef = ref(null)

// ========== 计算属性 ==========
const statProjectCount = computed(() => (project.value.projectName ? 1 : 0))
const statDeviceCount = computed(() => project.value.deviceCount || 0)
const statOnlineCount = computed(() => project.value.onlineDeviceCount || 0)
const statOpenAlertCount = computed(() => openAlertCount.value)

const latestPower = computed(() => {
  const p = (latestMetrics.value || []).find(m => m.metricKey === 'power')
  return p && p.value !== undefined ? p.value.toFixed(1) : '--'
})

const isOnline = computed(() => {
  return device.value.status === 'ONLINE'
})

const trendPoints = computed(() => powerTrend.value || [])

const healthLevelClass = computed(() => {
  const level = healthScore.value.level
  return level === 'HEALTHY' ? 'healthy' : level === 'ATTENTION' ? 'attention' : level === 'RISK' ? 'risk' : ''
})

const healthTagType = computed(() => {
  const level = healthScore.value.level
  return level === 'HEALTHY' ? 'success' : level === 'ATTENTION' ? 'warning' : level === 'RISK' ? 'danger' : 'info'
})

const healthLevelText = computed(() => {
  const map = { HEALTHY: '健康', ATTENTION: '需关注', RISK: '风险' }
  return map[healthScore.value.level] || healthScore.value.level || '--'
})

// ========== 方法 ==========
async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [pubRes, alertRes] = await Promise.all([
      getPublicProject(PROJECT_CODE),
      listAlerts(DEVICE_ID).catch(() => null)
    ])

    if (pubRes) {
      const payload = pubRes.data || pubRes || {}
      project.value = {
        projectCode: payload.projectCode,
        projectName: payload.projectName,
        description: payload.description,
        status: payload.status,
        deviceCount: payload.deviceCount,
        onlineDeviceCount: payload.onlineDeviceCount
      }
      device.value = payload.device || {}
      latestMetrics.value = payload.device?.metrics || []
      powerTrend.value = payload.powerTrend || []
      healthScore.value = payload.device?.health || {}
    }

    if (alertRes) {
      const payload = alertRes.data || alertRes || {}
      const list = payload.list || []
      openAlertCount.value = list.filter(a => a.status === 'OPEN').length
    }

    loaded.value = true

    if (trendPoints.value.length) {
      nextTick(() => renderMiniTrend())
    }
  } catch (err) {
    console.error('加载总览数据失败', err)
    error.value = err.message || '接口请求失败，请检查后端服务'
  } finally {
    loading.value = false
  }
}

function renderMiniTrend() {
  if (!trendRef.value) return
  if (!trendChartInstance) trendChartInstance = echarts.init(trendRef.value)
  const points = trendPoints.value
  trendChartInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      data: points.map(p => p.time?.slice(11, 16) || ''),
      axisLabel: { color: '#7c8798', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      name: 'W',
      axisLabel: { color: '#7c8798', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f0f0f0' } }
    },
    series: [{
      name: '功率',
      type: 'line',
      smooth: true,
      data: points.map(p => p.value),
      lineStyle: { color: '#409eff', width: 2 },
      symbolSize: 4,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.2)' },
          { offset: 1, color: 'rgba(64,158,255,0.02)' }
        ])
      }
    }]
  }, true)
}

function onChartResize() {
  trendChartInstance?.resize()
}

function startRefresh() {
  stopRefresh()
  refreshTimer = setInterval(() => loadData(), REFRESH_INTERVAL * 1000)
}

function stopRefresh() {
  if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null }
}

// ========== 生命周期 ==========
onMounted(() => {
  loadData()
  startRefresh()
  window.addEventListener('resize', onChartResize)
})

onUnmounted(() => {
  stopRefresh()
  window.removeEventListener('resize', onChartResize)
  if (trendChartInstance) { trendChartInstance.dispose(); trendChartInstance = null }
})
</script>

<style scoped>
.iot-overview { padding: 8px; max-width: 1200px; margin: 0 auto; }
.sub-title { color: #7c8798; margin: -8px 0 16px; }

/* 统计卡片 */
.stat-row { margin-bottom: 20px; }
.stat-row .el-col { margin-bottom: 16px; }
.stat-card { text-align: center; }
.stat-card :deep(.el-card__body) { padding: 16px 12px; }
.stat-label { color: #7c8798; font-size: 13px; margin-bottom: 6px; }
.stat-value { font-size: 28px; font-weight: 700; color: #172033; line-height: 1.2; }
.stat-value .unit { font-size: 13px; font-weight: 400; color: #7c8798; }
.stat-value.online { color: #67c23a; }
.stat-value.alert { color: #f56c6c; }
.stat-value.power { color: #f56c6c; }
.stat-value.healthy { color: #67c23a; }
.stat-value.attention { color: #e6a23c; }
.stat-value.risk { color: #f56c6c; }
.alert-card { border-left: 4px solid #f56c6c; }
.health-card { border-left: 4px solid #67c23a; }

/* 摘要卡片 */
.summary-row { margin-bottom: 20px; }
.summary-card { height: 100%; }
.card-header { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; color: #172033; }
.pm-summary { display: flex; flex-direction: column; gap: 12px; }
.pm-item { display: flex; align-items: center; gap: 12px; min-height: 28px; }
.pm-item.reasons { align-items: flex-start; }
.pm-label { width: 70px; color: #7c8798; font-size: 14px; flex-shrink: 0; }
.pm-value { font-size: 15px; color: #172033; font-weight: 500; }
.pm-value.code { font-family: 'Courier New', monospace; background: #f4f7fb; padding: 2px 10px; border-radius: 4px; font-size: 14px; }
.reason-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.mini-trend { width: 100%; height: 220px; }

/* 快捷入口 */
.action-card { margin-bottom: 20px; }
.action-title { font-size: 16px; font-weight: 600; color: #172033; margin-bottom: 16px; }
.action-buttons { display: flex; flex-wrap: wrap; gap: 12px; }
.action-buttons a { text-decoration: none; }

/* 响应式 */
@media (max-width: 768px) {
  .stat-value { font-size: 22px; }
  .mini-trend { height: 180px; }
  .action-buttons { justify-content: center; }
}
</style>
