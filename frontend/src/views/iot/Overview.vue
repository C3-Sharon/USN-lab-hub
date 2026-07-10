<template>
  <div class="iot-overview">
    <h2>IoT 总览</h2>

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="6">
        <el-card>
          <div class="stat-value">{{ stats.projectCount }}</div>
          <div class="stat-label">项目总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value">{{ stats.deviceCount }}</div>
          <div class="stat-label">设备总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value text-success">{{ stats.onlineCount }}</div>
          <div class="stat-label">在线设备</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value text-danger">{{ stats.alertCount }}</div>
          <div class="stat-label">活跃告警</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="16" style="margin-top: 20px">
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header><span>设备类型分布</span></template>
          <div ref="typeChartRef" class="chart-box"></div>
          <el-empty v-if="!typeChartData.length" description="暂无数据" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header><span>设备在线状态</span></template>
          <div ref="statusChartRef" class="chart-box"></div>
          <el-empty v-if="!statusChartData.length" description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>

    <h3 style="margin-top: 24px">项目列表</h3>
    <el-table :data="projects" v-loading="loading" stripe>
      <el-table-column prop="projectCode" label="项目编号" />
      <el-table-column prop="projectName" label="项目名称" />
      <el-table-column prop="deviceCount" label="设备数" width="100" />
      <el-table-column prop="onlineDeviceCount" label="在线" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="goToProject(row.id)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { listProjects, listDevices } from '@/api/iot'

const router = useRouter()
const loading = ref(false)
const projects = ref([])
const allDevices = ref([])
const stats = ref({ projectCount: 0, deviceCount: 0, onlineCount: 0, alertCount: 0 })

// 图表
const typeChartRef = ref(null)
const statusChartRef = ref(null)
const typeChartData = ref([])
const statusChartData = ref([])
let typeChartInstance = null
let statusChartInstance = null

async function loadData() {
  loading.value = true
  try {
    const [projRes, devRes] = await Promise.all([
      listProjects(),
      listDevices({ pageSize: 100 })
    ])
    projects.value = projRes.data.records || []
    allDevices.value = devRes.data.records || []

    stats.value.projectCount = projects.value.length
    stats.value.deviceCount = allDevices.value.length
    stats.value.onlineCount = allDevices.value.filter(d => d.status === 'ONLINE').length
    stats.value.alertCount = allDevices.value.filter(d => d.status === 'ALERT').length

    // 统计图表数据
    const typeMap = {}
    const statusMap = { ONLINE: 0, OFFLINE: 0, ALERT: 0, MAINTENANCE: 0 }
    allDevices.value.forEach(d => {
      typeMap[d.deviceType] = (typeMap[d.deviceType] || 0) + 1
      if (statusMap[d.status] !== undefined) statusMap[d.status]++
    })
    typeChartData.value = Object.entries(typeMap).map(([name, value]) => ({ name, value }))
    statusChartData.value = Object.entries(statusMap)
      .filter(([, value]) => value > 0)
      .map(([name, value]) => ({ name, value }))

    await nextTick()
    renderCharts()
  } catch (err) {
    console.error('加载数据失败', err)
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  renderTypeChart()
  renderStatusChart()
}

function renderTypeChart() {
  if (!typeChartRef.value || !typeChartData.value.length) return
  if (typeChartInstance) typeChartInstance.dispose()
  typeChartInstance = echarts.init(typeChartRef.value)
  typeChartInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '50%'],
      data: typeChartData.value,
      label: { formatter: '{b}\n{d}%' },
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 }
    }]
  })
}

function renderStatusChart() {
  if (!statusChartRef.value || !statusChartData.value.length) return
  if (statusChartInstance) statusChartInstance.dispose()
  statusChartInstance = echarts.init(statusChartRef.value)
  const colorMap = { ONLINE: '#67c23a', OFFLINE: '#909399', ALERT: '#f56c6c', MAINTENANCE: '#e6a23c' }
  statusChartInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '50%'],
      data: statusChartData.value.map(d => ({ ...d, itemStyle: { color: colorMap[d.name] || '#909399' } })),
      label: { formatter: '{b}\n{d}%' },
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 }
    }]
  })
}

function goToProject(id) {
  router.push(`/iot/projects/${id}`)
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', () => {
    typeChartInstance?.resize()
    statusChartInstance?.resize()
  })
})

onUnmounted(() => {
  typeChartInstance?.dispose()
  statusChartInstance?.dispose()
})
</script>

<style scoped>
.iot-overview { padding: 8px; }
.stat-value { font-size: 32px; font-weight: 700; color: #172033; line-height: 1.2; }
.stat-label { margin-top: 8px; color: #7c8798; font-size: 14px; }
.text-success { color: #67c23a; }
.text-danger { color: #f56c6c; }
.chart-card { margin-bottom: 16px; }
.chart-box { height: 280px; }
</style>