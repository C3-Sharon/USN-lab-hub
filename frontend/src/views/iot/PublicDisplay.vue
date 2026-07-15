<template>
  <div class="public-display">
    <!-- ========== 顶部品牌栏 ========== -->
    <header class="public-header">
      <div class="header-inner">
        <div class="brand">
          <el-icon size="28" color="#409eff"><Monitor /></el-icon>
          <span class="brand-name">USN Lab Hub IoT · 公开项目展示</span>
        </div>
      </div>
    </header>

    <!-- ========== 主体内容 ========== -->
    <main class="public-main">
      <div class="main-inner">
        <!-- 项目信息卡片 -->
        <el-card v-loading="loading && !loaded" class="project-card">
          <template v-if="error && !project.projectName">
            <el-alert :title="errorTitle" :description="error" type="error" :closable="false" show-icon />
            <div style="text-align: center; margin-top: 16px">
              <el-button type="primary" @click="loadData">重试</el-button>
            </div>
          </template>

          <template v-else-if="!loading && !project.projectName">
            <el-empty description="暂无项目数据">
              <template #image>
                <el-icon :size="64" class="waiting-icon"><Loading /></el-icon>
              </template>
            </el-empty>
            <div style="text-align: center; margin-top: 12px">
              <el-button type="primary" :loading="loading" @click="loadData">刷新</el-button>
            </div>
          </template>

          <template v-else>
            <div class="project-header">
              <div class="project-title">
                <h1>{{ project.projectName }}</h1>
                <el-tag size="small" :type="project.status === 'ACTIVE' ? 'success' : 'info'">
                  {{ project.status === 'ACTIVE' ? '进行中' : project.status }}
                </el-tag>
              </div>
              <p class="project-desc">{{ project.description || '暂无描述' }}</p>
            </div>
          </template>
        </el-card>

        <!-- 设备与指标网格 -->
        <template v-if="project.projectName">
          <el-row :gutter="20" class="info-grid">
            <!-- 设备状态 -->
            <el-col :xs="24" :sm="12" :md="8">
              <el-card class="info-card device-status-card">
                <div class="info-card-header">
                  <el-icon size="20"><Cpu /></el-icon>
                  <span>设备状态</span>
                </div>
                <div class="device-status-body">
                  <div class="device-code">{{ device.deviceCode }}</div>
                  <div class="device-name">{{ device.deviceName }}</div>
                  <div class="device-online-row">
                    <el-tag :type="isOnline ? 'success' : 'info'" size="large" effect="dark">
                      <el-icon v-if="isOnline"><CircleCheck /></el-icon>
                      <el-icon v-else><Warning /></el-icon>
                      {{ isOnline ? '在线' : '离线' }}
                    </el-tag>
                    <span v-if="!isOnline && device.reportTime" class="offline-hint">已离线</span>
                  </div>
                  <div class="last-seen">上报时间：{{ device.reportTime || '--' }}</div>
                </div>
              </el-card>
            </el-col>

            <!-- 指标卡片 -->
            <el-col :xs="24" :sm="12" :md="8" v-for="m in metricsList" :key="m.metricKey">
              <el-card class="info-card metric-card" :class="m.metricKey">
                <div class="info-card-header">
                  <el-icon size="20"><component :is="m.icon" /></el-icon>
                  <span>{{ m.metricName }}</span>
                </div>
                <div class="metric-body">
                  <div class="metric-big-value">
                    {{ formatValue(m.value) }}
                    <span class="metric-unit">{{ m.unit }}</span>
                  </div>
                </div>
              </el-card>
            </el-col>

            <!-- 健康评分 -->
            <el-col :xs="24" :sm="12" :md="8">
              <el-card class="info-card health-card">
                <div class="info-card-header">
                  <el-icon size="20"><FirstAidKit /></el-icon>
                  <span>健康评分</span>
                </div>
                <div class="health-body">
                  <div class="health-score" :class="healthLevelClass">
                    {{ healthScore.score ?? '--' }}
                  </div>
                  <el-tag :type="healthLevelTagType" size="large" effect="dark" class="health-level">
                    {{ healthLevelText }}
                  </el-tag>
                  <div v-if="healthScore.reasons?.length" class="health-reasons">
                    <div v-for="(reason, idx) in healthScore.reasons" :key="idx" class="reason-item">
                      <el-icon size="12"><Check /></el-icon>
                      <span>{{ reason }}</span>
                    </div>
                  </div>
                  <div v-if="healthScore.calculatedAt" class="health-time">
                    计算于 {{ healthScore.calculatedAt }}
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <!-- 功率趋势 -->
          <el-card class="trend-card">
            <template #header>
              <div class="trend-header">
                <div class="trend-title">
                  <el-icon size="18"><TrendCharts /></el-icon>
                  <span>功率趋势</span>
                </div>
                <div class="trend-actions">
                  <el-tag size="small" type="info">单位：W</el-tag>
                  <el-button type="primary" :icon="Refresh" :loading="loading" size="small" @click="loadData">刷新</el-button>
                </div>
              </div>
            </template>
            <template v-if="trendError && !trendPoints.length">
              <el-alert title="趋势数据加载失败" :description="trendError" type="error" :closable="false" show-icon style="margin-bottom: 16px" />
              <div style="text-align: center">
                <el-button type="primary" @click="loadData">重试</el-button>
              </div>
            </template>
            <template v-else-if="!loading && !trendPoints.length">
              <el-empty description="暂无趋势数据" />
            </template>
            <div v-show="trendPoints.length" ref="trendChartRef" class="trend-chart"></div>
          </el-card>
        </template>
      </div>
    </main>

    <!-- ========== 页脚 ========== -->
    <footer class="public-footer">
      <p>USN Lab Hub IoT · 实验室硬件项目管理与智能运维平台</p>
      <p v-if="updatedAt" class="updated-at">数据更新于 {{ updatedAt }}</p>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import {
  Monitor, Cpu, Loading, Refresh,
  CircleCheck, Warning, TrendCharts, FirstAidKit, Check,
  OfficeBuilding, Magnet, Lightning
} from '@element-plus/icons-vue'
import { getPublicProject } from '@/api/iot'
import * as echarts from 'echarts'

const PROJECT_CODE = 'power-monitor'
const REFRESH_INTERVAL = 30

// ========== 数据状态 ==========
const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const errorTitle = ref('数据加载失败')

const project = ref({})
const device = ref({})
const latestMetrics = ref([])
const powerTrend = ref([])
const healthScore = ref({})
const updatedAt = ref('')

let refreshTimer = null
let trendChartInstance = null
const trendChartRef = ref(null)
const trendError = ref('')
const trendPoints = computed(() => powerTrend.value || [])

// ========== 计算属性 ==========
const isOnline = computed(() => {
  return device.value.status === 'ONLINE'
})

const iconMap = { voltage: OfficeBuilding, current: Magnet, power: Lightning }

const metricsList = computed(() => {
  return (latestMetrics.value || []).map(m => ({ ...m, icon: iconMap[m.metricKey] || Cpu }))
})

const healthLevelClass = computed(() => {
  const level = healthScore.value.level
  return level === 'HEALTHY' ? 'healthy' : level === 'ATTENTION' ? 'attention' : level === 'RISK' ? 'risk' : ''
})

const healthLevelTagType = computed(() => {
  const level = healthScore.value.level
  return level === 'HEALTHY' ? 'success' : level === 'ATTENTION' ? 'warning' : level === 'RISK' ? 'danger' : 'info'
})

const healthLevelText = computed(() => {
  const map = { HEALTHY: '健康', ATTENTION: '需关注', RISK: '风险' }
  return map[healthScore.value.level] || healthScore.value.level || '--'
})

// ========== 方法 ==========
function formatValue(val) {
  return val !== undefined && val !== null ? val.toFixed(2) : '--'
}

async function loadData() {
  loading.value = true
  error.value = ''
  trendError.value = ''
  try {
    const res = await getPublicProject(PROJECT_CODE)
    const payload = res.data || res || {}

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
    updatedAt.value = payload.updatedAt || ''

    loaded.value = true

    if (trendPoints.value.length) {
      nextTick(() => renderTrendChart())
    }
  } catch (err) {
    console.error('加载公开项目数据失败', err)
    error.value = err.message || '接口请求失败，请检查后端服务'
    errorTitle.value = '数据加载失败'
    trendError.value = error.value
  } finally {
    loading.value = false
  }
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChartInstance) trendChartInstance = echarts.init(trendChartRef.value)
  const points = trendPoints.value
  trendChartInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
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
      symbolSize: 6,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.25)' },
          { offset: 1, color: 'rgba(64,158,255,0.03)' }
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
.public-display { min-height: 100vh; display: flex; flex-direction: column; background: #f5f7fa; }

/* 顶部品牌栏 */
.public-header { background: #fff; border-bottom: 1px solid #e4e7ed; position: sticky; top: 0; z-index: 100; }
.header-inner { max-width: 1200px; margin: 0 auto; padding: 0 24px; height: 60px; display: flex; align-items: center; justify-content: space-between; }
.brand { display: flex; align-items: center; gap: 10px; }
.brand-name { font-size: 20px; font-weight: 700; color: #172033; }

/* 主体 */
.public-main { flex: 1; padding: 24px 16px; }
.main-inner { max-width: 1200px; margin: 0 auto; }

/* 项目卡片 */
.project-card { margin-bottom: 20px; }
.project-header { padding: 8px 0; }
.project-title { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.project-title h1 { margin: 0; font-size: 24px; color: #172033; }
.project-desc { color: #606266; font-size: 14px; margin: 0; }

/* 信息网格 */
.info-grid { margin-bottom: 20px; }
.info-grid .el-col { margin-bottom: 20px; }
.info-card { height: 100%; }
.info-card :deep(.el-card__body) { padding: 20px; }
.info-card-header { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; color: #172033; margin-bottom: 16px; }

/* 设备状态 */
.device-status-body { display: flex; flex-direction: column; gap: 10px; }
.device-code { font-family: 'Courier New', monospace; font-size: 18px; font-weight: 700; color: #172033; }
.device-name { font-size: 14px; color: #606266; }
.device-online-row { display: flex; align-items: center; gap: 10px; margin-top: 4px; }
.offline-hint { color: #f56c6c; font-size: 13px; }
.last-seen { color: #909399; font-size: 13px; margin-top: 4px; }

/* 指标卡片 */
.metric-body { display: flex; align-items: center; justify-content: center; padding: 12px 0; }
.metric-big-value { font-size: 42px; font-weight: 700; color: #172033; line-height: 1; }
.metric-unit { font-size: 16px; font-weight: 400; color: #7c8798; margin-left: 6px; }
.metric-card.voltage .metric-big-value { color: #409eff; }
.metric-card.current .metric-big-value { color: #e6a23c; }
.metric-card.power .metric-big-value { color: #f56c6c; }

/* 健康评分 */
.health-body { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 8px 0; }
.health-score { font-size: 56px; font-weight: 800; line-height: 1; }
.health-score.healthy { color: #67c23a; }
.health-score.attention { color: #e6a23c; }
.health-score.risk { color: #f56c6c; }
.health-level { font-size: 16px; padding: 6px 16px; }
.health-reasons { display: flex; flex-direction: column; gap: 6px; width: 100%; margin-top: 4px; }
.reason-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #606266; }
.health-time { color: #909399; font-size: 12px; margin-top: 4px; }

/* 趋势图表 */
.trend-card { margin-bottom: 20px; }
.trend-header { display: flex; align-items: center; justify-content: space-between; }
.trend-title { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; color: #172033; }
.trend-actions { display: flex; align-items: center; gap: 12px; }
.trend-chart { width: 100%; height: 320px; }

/* 页脚 */
.public-footer { background: #fff; border-top: 1px solid #e4e7ed; padding: 20px 24px; text-align: center; }
.public-footer p { margin: 0; color: #909399; font-size: 13px; }
.public-footer .updated-at { margin-top: 6px; }

/* 通用 */
.waiting-icon { color: #c0c4cc; animation: rotate 2s linear infinite; }
@keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

/* 响应式 */
@media (max-width: 768px) {
  .header-inner { padding: 0 12px; }
  .brand-name { font-size: 18px; }
  .project-title h1 { font-size: 20px; }
  .metric-big-value { font-size: 32px; }
  .health-score { font-size: 42px; }
  .trend-chart { height: 240px; }
}
</style>
