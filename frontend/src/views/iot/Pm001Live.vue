<template>
  <div class="pm001-live">
    <h2>PM-001 实时数据监控</h2>
    <p class="sub-title">第二周 — MQTT 模拟器驱动的真实数据链路</p>

    <el-card v-loading="loading && !hasLoadedOnce" class="device-card">
      <!-- 错误状态 -->
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

      <!-- 等待数据上报 -->
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
        <div style="text-align: center; margin-top: 12px">
          <el-button type="primary" :loading="loading" @click="loadData">手动刷新</el-button>
        </div>
      </template>

      <!-- 正常数据展示 -->
      <template v-else>
        <!-- 设备信息区 -->
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
            <span v-if="!isOnline && reportTime" class="offline-reason">
              （超过 15 秒未上报）
            </span>
          </div>
          <div class="info-row">
            <span class="info-label">上报时间</span>
            <span class="info-value">{{ reportTime || '--' }}</span>
          </div>
        </div>

        <el-divider />

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

        <!-- 底部操作栏 -->
        <div class="footer-bar">
          <div class="footer-left">
            <span v-if="lastUpdateTime" class="last-update">
              页面更新于 {{ lastUpdateTime }}
            </span>
            <el-tag v-if="loading" type="warning" size="small" effect="plain" class="polling-tag">
              刷新中...
            </el-tag>
          </div>
          <el-button
            type="primary"
            :icon="Refresh"
            :loading="loading"
            @click="loadData"
          >
            刷新数据
          </el-button>
        </div>
      </template>
    </el-card>

    <!-- 联调说明 -->
    <el-alert
      title="联调说明"
      type="info"
      :closable="false"
      class="debug-hint"
    >
      <p>接口：GET /api/iot/devices/1/latest</p>
      <p>数据链路：模拟器 → MQTT → 后端入库 → 接口返回 → 前端展示</p>
      <p>自动刷新：每 {{ POLL_INTERVAL }} 秒</p>
      <p>在线判断：上报时间距当前 15 秒内为在线</p>
    </el-alert>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Cpu, Refresh, Lightning, Magnet, OfficeBuilding, Timer, Loading } from '@element-plus/icons-vue'
import { getLatestMetrics } from '@/api/iot'

// 第二周配置
const POLL_INTERVAL = 5 // 轮询间隔（秒）
const ONLINE_THRESHOLD = 15 // 在线判断阈值（秒）

const loading = ref(false)
const error = ref('')
const hasLoadedOnce = ref(false)
const latestData = ref({
  deviceCode: '',
  deviceName: '',
  projectName: '',
  status: '',
  reportTime: '',
  metrics: []
})
const lastUpdateTime = ref('')
let pollTimer = null

// 是否有数据
const hasData = computed(() => {
  return !!(latestData.value.deviceCode || latestData.value.metrics?.length)
})

// 指标图标映射
const iconMap = {
  voltage: OfficeBuilding,
  current: Magnet,
  power: Lightning
}

// 上报时间
const reportTime = computed(() => {
  return latestData.value.reportTime || latestData.value.reportedAt || ''
})

// 在线状态：上报时间距当前 15 秒内为在线
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

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const res = await getLatestMetrics(1)
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
    error.value = err.message || '接口请求失败，请检查后端服务是否启动'
  } finally {
    loading.value = false
  }
}

function startPoll() {
  stopPoll()
  pollTimer = setInterval(() => {
    loadData()
  }, POLL_INTERVAL * 1000)
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

/* 等待状态 */
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

/* 设备信息区 */
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

/* 指标卡片 */
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

/* 底部 */
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
}
</style>
