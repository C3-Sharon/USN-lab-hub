<template>
  <div class="iot-command-console">
    <h2>指令控制台</h2>
    <el-table :data="commands" v-loading="loading" stripe>
      <el-table-column prop="commandId" label="指令ID" width="180" />
      <el-table-column prop="deviceCode" label="设备编号" width="120" />
      <el-table-column prop="command" label="指令" width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="下发时间" width="160" />
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listCommands } from '@/api/iot'

const loading = ref(false)
const commands = ref([])

function statusType(status) {
  const map = { PENDING: 'info', SENT: 'primary', ACKED: 'success', FAILED: 'danger', TIMEOUT: 'warning' }
  return map[status] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await listCommands()
    commands.value = res.data.records || []
  } catch (err) {
    console.error('加载指令记录失败', err)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.iot-command-console {
  padding: 8px;
}
</style>
