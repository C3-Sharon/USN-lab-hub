<template>
  <div class="iot-overview">
    <h2>IoT 总览</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="12" v-loading="loading">
      <el-col :xs="12" :sm="8" :md="4" v-for="s in statCards" :key="s.label">
        <el-card class="stat-card" :body-style="{ padding: '16px' }">
          <div class="stat-value" :class="s.class">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- PM-001 摘要 + 快捷入口 -->
    <el-row :gutter="16" style="margin-top: 20px">
      <el-col :xs="24" :md="16">
        <el-card v-loading="pmLoading" class="pm-card">
          <template #header>
            <div class="section-header">
              <div class="section-title">
                <el-icon><Cpu /></el-icon>
                <span>PM-001 实时摘要</span>
              </div>
              <div class="section-tags">
                <el-tag v-if="pmOnline" type="success" size="small" effect="dark">在线</el-tag>
                <el-tag v-else type="info" size="small" effect="dark">离线</el-tag>
                <el-tag v-if="openAlertCount > 0" type="danger" size="small" effect="plain" style="margin-left: 8px">{{ openAlertCount }} 条告警</el-tag>
              </div>
            </div>
          </template>

          <template v-if="pmError">
            <el-alert title="PM-001 数据加载失败" :description="pmError" type="error" :closable="false" show-icon />
            <div style="text-align: center; margin-top: 12px">
              <el-button type="primary" size="small" @click="loadPmData">重试</el-button>
            </div>
          </template>

          <template v-else-if="!pmLoading && !pmData?.device">
            <el-empty description="暂无 PM-001 数据">
              <template #image>
                <el-icon :size="64" class="waiting-icon"><Loading /></el-icon>
              </template>
            </el-empty>
            <div style="text-align: center; margin-top: 12px">
              <el-button type="primary" :loading="pmLoading" @click="loadPmData">刷新</el-button>
            </div>
          </template>

          <template v-else>
            <!-- 指标 -->
            <el-row :gutter="12" class="metrics-row">
              <el-col :xs="8" v-for="m in pmMetrics" :key="m.metricKey">
                <div class="metric-mini">
                  <div class="metric-name">{{ m.metricName }}</div>
                  <div class="metric-value">
                    {{ m.value !== undefined && m.value !== null ? m.value.toFixed(2) : '--' }}
                    <span class="unit">{{ m.unit }}</span>
                  </div>
                </div>
              </el-col>
            </el-row>

            <!-- 健康评分 -->
            <div v-if="healthScore" class="health-row">
              <div class="health-block">
                <div class="health-score" :class="healthClass">{{ healthScore.score ?? '--' }}</div>
                <div class="health-meta">
                  <el-tag :type="healthTagType" size="small">{{ healthLevelText }}</el-tag>
                  <span v-if="healthScore.calculatedAt" class="health-time">计算于 {{ healthScore.calculatedAt }}</span>
                </div>
                <div v-if="healthScore.reasons?.length" class="health-reasons">
                  <el-tag v-for="r in healthScore.reasons" :key="r" type="info" size="small" effect="plain">{{ r }}</el-tag>
                </div>
              </div>
            </div>

            <!-- 迷你趋势 -->
            <div class="trend-section">
              <div class="trend-label">最近功率趋势</div>
              <template v-if="!powerTrend.length">
                <el-empty description="暂无趋势数据" />
              </template>
              <div v-show="powerTrend.length" ref="miniChartRef" class="mini-chart"></div>
            </div>
          </template>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card class="action-card">
          <template #header><span>快捷入口</span></template>
          <div class="quick-actions">
            <el-button type="primary" :icon="Cpu" @click="$router.push('/iot/pm001')">查看 PM-001 实时页</el-button>
            <el-button :icon="Monitor" @click="openPublic">预览公开展示页</el-button>
          </div>
          <div v-if="openAlertCount > 0" class="alert-hint">
            <el-alert :title="`PM-001 有 ${openAlertCount} 条未处理告警`" type="warning" :closable="false" show-icon />
          </div>
          <div v-else class="alert-hint">
            <el-alert title="PM-001 暂无未处理告警" type="success" :closable="false" show-icon />
          </div>
        </el-card>

        <el-card class="project-card-mini" style="margin-top: 16px">
          <template #header><span>项目</span></template>
          <div v-if="projects.length" class="project-list">
            <div v-for="p in projects" :key="p.id" class="project-item" @click="goToProject(p.id)">
              <div class="project-name">{{ p.projectName }}</div>
              <div class="project-meta">
                <el-tag size="small" :type="p.status === 'ACTIVE' ? 'success' : 'info'">{{ p.status === 'ACTIVE' ? '进行中' : p.status }}</el-tag>
                <span class="device-count">{{ p.deviceCount }} 台设备 / {{ p.onlineDeviceCount }} 在线</span>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无项目" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Cpu, Monitor, Loading } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { listProjects, listDevices, listAlerts, getPublicProject } from '@/api/iot'

const router = useRouter()
const REFRESH_INTERVAL = 30
const DEVICE_ID = 1

// ========== 全局统计 ==========
const loading = ref(false)
const projects = ref([])
const devices = ref([])
const openAlertCount = ref(0)

// ========== PM-001 ==========
const pmLoading = ref(false)
const pmError = ref('')
const pmData = ref(null)

const pmMetrics = computed(() => pmData.value?.device?.metrics || [])
const healthScore = computed(() => pmData.value?.device?.health || null)
const powerTrend = computed(() => pmData.value?.powerTrend || [])

const pmOnline = computed(() => {
  const device = pmData.value?.device
  if (!device) return false
  if (device.status === 'OFFLINE') return false
  const timeStr = device.reportTime
  if (!timeStr) return false
  const ts = new Date(timeStr.replace(' ', 'T')).getTime()
  return !isNaN(ts) && Date.now() - ts <= 15 * 1000
})

const healthClass = computed(() => {
  const level = healthScore.value?.level
  return level === 'HEALTHY' ? 'healthy' : level === 'ATTENTION' ? 'attention' : level === 'RISK' ? 'risk' : ''
})

const healthTagType = computed(() => {
  const level = healthScore.value?.level
  return level === 'HEALTHY' ? 'success' : level === 'ATTENTION' ? 'warning' : level === 'RISK' ? 'danger' : 'info'
})

const healthLevelText = computed(() => {
  const map = { HEALTHY: '健康', ATTENTION: '需关注', RISK: '风险' }
  return map[healthScore.value?.level] || healthScore.value?.level || '--'
})

const statCards = computed(() => {
  const powerMetric = pmMetrics.value.find(m => m.metricKey === 'power')
  return [
    { value: projects.value.length, label: '激活项目', class: '' },
    { value: devices.value.length, label: '设备总数', class: '' },
    { value: devices.value.filter(d => d.status === 'ONLINE').length, label: '在线设备', class: 'text-success' },
    { value: openAlertCount.value, label: '开放告警', class: openAlertCount.value > 0 ? 'text-danger' : '' },
    {
      value: powerMetric?.value !== undefined ? powerMetric.value.toFixed(1) + ' W' : '--',
      label: 'PM-001 功率',
      class: ''
    },
    {
      value: healthScore.value?.score ?? '--',
      label: '健康评分',
      class: healthClass.value
    }
  ]
})

// ========== 图表 ==========
const miniChartRef = ref(null)
let miniChartInstance = null

function onChartResize() {
  miniChartInstance?.resize()
}

function renderMiniChart() {
  if (!miniChartRef.value || !powerTrend.value.length) return
  if (!miniChartInstance) miniChartInstance = echarts.init(miniChartRef.value)
  miniChartInstance.setOption({
    tooltip: { trigger: 'axis', formatter: '{b}: {c} W' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '5%', containLabel: true },
    xAxis: {
      type: 'category',
      data: powerTrend.value.map(p => p.time?.slice(11, 16) || ''),
      axisLabel: { color: '#7c8798', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#7c8798', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f0f0f0' } }
    },
    series: [{
      type: 'line',
      smooth: true,
      data: powerTrend.value.map(p => p.value),
      lineStyle: { color: '#409eff', width: 2 },
      symbol: 'none',
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.2)' },
          { offset: 1, color: 'rgba(64,158,255,0.02)' }
        ])
      }
    }]
  }, true)
}

// ========== 加载 ==========
async function loadStats() {
  loading.value = true
  try {
    const [projRes, devRes, alertRes] = await Promise.all([
      listProjects(),
      listDevices({ pageSize: 100 }),
      listAlerts(DEVICE_ID)
    ])
    projects.value = projRes.list || []
    devices.value = devRes.list || []
    const alertList = alertRes.list || []
    openAlertCount.value = alertList.filter(a => a.status === 'OPEN').length
  } catch (err) {
    console.error('加载统计数据失败', err)
  } finally {
    loading.value = false
  }
}

async function loadPmData() {
  pmLoading.value = true
  pmError.value = ''
  try {
    const res = await getPublicProject('power-monitor')
    pmData.value = res || {}
    if (powerTrend.value.length) {
      nextTick(() => renderMiniChart())
    }
  } catch (err) {
    console.error('加载 PM-001 数据失败', err)
    pmError.value = err.message || '接口请求失败'
  } finally {
    pmLoading.value = false
  }
}

function openPublic() {
  window.open('/iot/public', '_blank')
}

function goToProject(id) {
  router.push(`/iot/projects/${id}`)
}

// ========== 自动刷新 ==========
let refreshTimer = null
function startRefresh() {
  stopRefresh()
  refreshTimer = setInterval(() => {
    loadPmData()
    loadStats()
  }, REFRESH_INTERVAL * 1000)
}
function stopRefresh() {
  if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null }
}

onMounted(() => {
  loadStats()
  loadPmData()
  startRefresh()
  window.addEventListener('resize', onChartResize)
})

onUnmounted(() => {
  stopRefresh()
  window.removeEventListener('resize', onChartResize)
  if (miniChartInstance) { miniChartInstance.dispose(); miniChartInstance = null }
})
</script>

<style scoped>
.iot-overview { padding: 8px; }
.stat-card { text-align: center; margin-bottom: 12px; }
.stat-value { font-size: 28px; font-weight: 700; color: #172033; line-height: 1.2; }
.stat-label { margin-top: 6px; color: #7c8798; font-size: 13px; }
.text-success { color: #67c23a; }
.text-danger { color: #f56c6c; }
.healthy { color: #67c23a; }
.attention { color: #e6a23c; }
.risk { color: #f56c6c; }

.pm-card { margin-bottom: 16px; }
.section-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.section-title { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; }
.section-tags { display: flex; align-items: center; }

.metrics-row { margin-bottom: 16px; }
.metric-mini { text-align: center; padding: 12px 0; background: #f5f7fa; border-radius: 8px; }
.metric-name { font-size: 13px; color: #7c8798; margin-bottom: 6px; }
.metric-value { font-size: 22px; font-weight: 700; color: #172033; }
.metric-value .unit { font-size: 12px; font-weight: 400; color: #909399; margin-left: 4px; }

.health-row { margin-bottom: 16px; }
.health-block { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; padding: 12px; background: #f5f7fa; border-radius: 8px; }
.health-score { font-size: 36px; font-weight: 800; line-height: 1; }
.health-meta { display: flex; flex-direction: column; gap: 6px; }
.health-time { font-size: 12px; color: #909399; }
.health-reasons { display: flex; gap: 8px; flex-wrap: wrap; }

.trend-section { margin-top: 8px; }
.trend-label { font-size: 13px; color: #7c8798; margin-bottom: 8px; }
.mini-chart { width: 100%; height: 160px; }

.quick-actions { display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }
.quick-actions .el-button { width: 100%; justify-content: center; }
.alert-hint { margin-top: 8px; }

.project-list { display: flex; flex-direction: column; gap: 12px; }
.project-item { padding: 12px; background: #f5f7fa; border-radius: 8px; cursor: pointer; transition: background 0.2s; }
.project-item:hover { background: #e6f2ff; }
.project-name { font-size: 15px; font-weight: 600; color: #172033; margin-bottom: 6px; }
.project-meta { display: flex; align-items: center; gap: 10px; font-size: 13px; color: #606266; }

.waiting-icon { color: #c0c4cc; animation: rotate 2s linear infinite; }
@keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

@media (max-width: 768px) {
  .stat-value { font-size: 22px; }
  .health-block { justify-content: center; }
  .mini-chart { height: 140px; }
}
</style>
