<template>
  <div class="iot-alert-center">
    <h2>告警中心</h2>
    <el-table :data="alerts" v-loading="loading" stripe>
      <el-table-column prop="deviceCode" label="设备编号" width="120" />
      <el-table-column prop="deviceName" label="设备名称" />
      <el-table-column prop="metricKey" label="指标" width="100" />
      <el-table-column prop="level" label="级别" width="100">
        <template #default="{ row }">
          <el-tag :type="row.level === 'WARNING' ? 'warning' : 'danger'">{{ row.level }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="message" label="告警内容" />
      <el-table-column prop="triggerValue" label="触发值" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'OPEN' ? 'danger' : 'success'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="160" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'OPEN'"
            type="primary"
            size="small"
            @click="handleAlert(row.id)"
          >
            处理
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listAlerts, handleAlert } from '@/api/iot'

const loading = ref(false)
const alerts = ref([])

async function loadData() {
  loading.value = true
  try {
    const res = await listAlerts()
    alerts.value = res.data.records || []
  } catch (err) {
    console.error('加载告警失败', err)
  } finally {
    loading.value = false
  }
}

async function handleAlertAction(id) {
  try {
    await handleAlert(id, { status: 'HANDLED', note: '已处理' })
    await loadData()
  } catch (err) {
    console.error('处理告警失败', err)
  }
}

onMounted(loadData)
</script>

<style scoped>
.iot-alert-center {
  padding: 8px;
}
</style>
