<template>
  <div class="iot-operation-logs">
    <h2>操作日志</h2>

    <el-card class="filter-bar">
      <el-form inline>
        <el-form-item label="动作类型">
          <el-select v-model="query.action" placeholder="全部" clearable @change="search" style="width: 160px">
            <el-option label="下发指令" value="SEND_COMMAND" />
            <el-option label="处理告警" value="HANDLE_ALERT" />
            <el-option label="创建设备" value="CREATE_DEVICE" />
            <el-option label="更新设备" value="UPDATE_DEVICE" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类型">
          <el-select v-model="query.targetType" placeholder="全部" clearable @change="search" style="width: 140px">
            <el-option label="设备" value="DEVICE" />
            <el-option label="项目" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="logs" v-loading="loading" stripe>
      <el-table-column prop="operatorName" label="操作人" width="100" />
      <el-table-column prop="action" label="动作" width="120">
        <template #default="{ row }">
          <el-tag :type="actionTagType(row.action)" size="small">{{ actionLabel(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="targetType" label="目标类型" width="100">
        <template #default="{ row }">
          {{ row.targetType === 'DEVICE' ? '设备' : '项目' }}
        </template>
      </el-table-column>
      <el-table-column prop="summary" label="摘要" min-width="300" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="时间" width="160" />
    </el-table>
    <el-empty v-if="!loading && logs.length === 0" description="暂无操作日志" />

    <el-pagination
      v-if="total > 0"
      v-model:current-page="query.pageNum"
      :page-size="query.pageSize"
      :total="total"
      layout="total, prev, pager, next"
      background
      style="margin-top: 16px; justify-content: center"
      @current-change="loadData"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listOperationLogs } from '@/api/iot'

const loading = ref(false)
const logs = ref([])
const total = ref(0)
const query = ref({
  action: '',
  targetType: '',
  pageNum: 1,
  pageSize: 10
})

function actionLabel(action) {
  const map = {
    SEND_COMMAND: '下发指令',
    HANDLE_ALERT: '处理告警',
    CREATE_DEVICE: '创建设备',
    UPDATE_DEVICE: '更新设备'
  }
  return map[action] || action
}

function actionTagType(action) {
  const map = {
    SEND_COMMAND: 'primary',
    HANDLE_ALERT: 'warning',
    CREATE_DEVICE: 'success',
    UPDATE_DEVICE: 'info'
  }
  return map[action] || ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await listOperationLogs(query.value)
    logs.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (err) {
    console.error('加载操作日志失败', err)
    logs.value = []
  } finally {
    loading.value = false
  }
}

function search() {
  query.value.pageNum = 1
  loadData()
}

function resetSearch() {
  query.value = { action: '', targetType: '', pageNum: 1, pageSize: 10 }
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.iot-operation-logs { padding: 8px; }
.filter-bar { margin-bottom: 16px; }
</style>