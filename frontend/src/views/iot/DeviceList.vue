<template>
  <div class="iot-device-list">
    <h2>设备列表</h2>
    <el-table :data="devices" v-loading="loading" stripe>
      <el-table-column prop="deviceCode" label="设备编号" />
      <el-table-column prop="deviceName" label="设备名称" />
      <el-table-column prop="deviceType" label="类型" width="120" />
      <el-table-column prop="projectName" label="所属项目" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastSeenAt" label="最后在线" width="160" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="goToDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listDevices } from '@/api/iot'

const router = useRouter()
const loading = ref(false)
const devices = ref([])

function statusTagType(status) {
  const map = {
    ONLINE: 'success',
    OFFLINE: 'info',
    ALERT: 'danger',
    MAINTENANCE: 'warning'
  }
  return map[status] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await listDevices()
    devices.value = res.data.records || []
  } catch (err) {
    console.error('加载设备列表失败', err)
  } finally {
    loading.value = false
  }
}

function goToDetail(id) {
  router.push(`/iot/devices/${id}`)
}

onMounted(loadData)
</script>

<style scoped>
.iot-device-list {
  padding: 8px;
}
</style>
