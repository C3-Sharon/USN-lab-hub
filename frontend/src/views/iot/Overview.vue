<template>
  <div class="iot-overview">
    <h2>IoT 总览</h2>
    <el-row :gutter="16">
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listProjects } from '@/api/iot'

const router = useRouter()
const loading = ref(false)
const projects = ref([])
const stats = ref({ projectCount: 0, deviceCount: 0, onlineCount: 0, alertCount: 0 })

async function loadData() {
  loading.value = true
  try {
    const res = await listProjects()
    projects.value = res.data.records || []
    stats.value.projectCount = projects.value.length
    stats.value.deviceCount = projects.value.reduce((sum, p) => sum + (p.deviceCount || 0), 0)
    stats.value.onlineCount = projects.value.reduce((sum, p) => sum + (p.onlineDeviceCount || 0), 0)
    stats.value.alertCount = projects.value.reduce((sum, p) => sum + (p.alertCount || 0), 0)
  } catch (err) {
    console.error('加载项目列表失败', err)
  } finally {
    loading.value = false
  }
}

function goToProject(id) {
  router.push(`/iot/projects/${id}`)
}

onMounted(loadData)
</script>

<style scoped>
.iot-overview {
  padding: 8px;
}
.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #172033;
  line-height: 1.2;
}
.stat-label {
  margin-top: 8px;
  color: #7c8798;
  font-size: 14px;
}
.text-success { color: #67c23a; }
.text-danger { color: #f56c6c; }
</style>
