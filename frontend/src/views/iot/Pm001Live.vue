<template>
  <div class="pm001-live">
    <h2>PM-001 实时数据监控</h2>
    <p class="sub-title">本周最小闭环联调 — 只展示最新遥测数据</p>

    <el-card v-loading="loading" class="device-card">
      <template #header>
        <div class="card-header">
          <span class="device-name">
            <el-icon><Cpu /></el-icon>
            功耗检测设备 PM-001
          </span>
          <el-tag :type="statusType" size="large" effect="dark">
            {{ latestData.status === 'ONLINE' ? '在线' : latestData.status === 'OFFLINE' ? '离线' : latestData.status }}
          </el-tag>
        </div>
      </template>

      <template v-if="error">
        <el-alert
          title="数据加载失败"
          :description="error"
          type="error"
          :closable="false"
          show-icon
        />
        <div style="text-align: center; margin-top: 16px">
          <el-button type="primary" @click="loadData">重试</el-button>
        </div>
      </template>

      <template v-else>
        <!-- 指标卡片 -->
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

        <!-- 底部信息栏 -->
        <div class="footer-bar">
          <div class="report-time">
            <el-icon><Timer /></el-icon>
            上报时间：{{ latestData.reportedAt || '--' }}
          </div>
          <div class="actions">
            <span v-if="lastUpdateTime" class="last-update">更新于 {{ lastUpdateTime }}</span>
            <el-button
              type="primary"
              :icon="Refresh"
              :loading="loading"
              @click="loadData"
            >
              刷新数据
            </el-button>
          </div>
        </div>
      </template>
    </el-card>

    <!-- 联调提示 -->
    <el-alert
      title="联调说明"
      type="info"
      :closable="false"
      class="debug-hint"
    >
      <p>接口：GET /api/iot/devices/1/latest</p>
      <p>当前模式：{{ USE_MOCK ? 'Mock 数据' : '真实接口' }}</p>
      <p>自动刷新：每 10 秒</p>
    </el-alert>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Cpu, Timer, Refresh, Lightning, Magnet, OfficeBuilding } from '@element-plus/icons-vue'
import { getLatestMetrics } from '@/api/iot'

// 与 api/iot.js 的开关保持一致（用于页面展示）
import { USE_MOCK } from '@/api/iot'

const loading = ref(false)
const error = ref('')
const latestData = ref({ status: '', reportedAt: '', metrics: [] })
const lastUpdateTime = ref('')
let pollTimer = null

// 指标图标映射
const iconMap = {
  voltage: OfficeBuilding,
  current: Magnet,
  power: Lightning
}

const metricsList = computed(() => {
  const list = latestData.value.metrics || []
  return list.map(m => ({
    ...m,
    icon: iconMap[m.metricKey] || Cpu
  }))
})

const statusType = computed(() => {
  const map = { ONLINE: 'success', OFFLINE: 'info', ALERT: 'danger', MAINTENANCE: 'warning' }
  return map[latestData.value.status] || 'info'
})

function formatValue(val) {
  return val !== undefined && val !== null ? val.toFixed(2) : '--'
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const res = await getLatestMetrics(1)
    // 后端返回：{ code, msg, data }，request 拦截器已做统一处理
    // mock 模式返回：{ code, message, data }
    const payload = res.data || {}
    latestData.value = {
      status: payload.status || 'UNKNOWN',
      reportedAt: payload.reportedAt || '',
      metrics: payload.metrics || []
    }
    lastUpdateTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
  } catch (err) {
    console.error('加载最新数据失败', err)
    error.value = err.message || '接口请求失败，请检查后端服务是否启动'
  } finally {
    loading.value = false
  }
}

function startPoll() {
  stopPoll()
  pollTimer = setInterval(() => {
    loadData()
  }, 10000)
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(() => {
  loadData()
  startPoll()
})

onUnmounted(() => {
  stopPoll()
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

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.device-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
}

.metrics-row {
  margin-bottom: 8px;
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

.report-time {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #7c8798;
  font-size: 14px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.last-update {
  color: #7c8798;
  font-size: 13px;
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
}
</style>
