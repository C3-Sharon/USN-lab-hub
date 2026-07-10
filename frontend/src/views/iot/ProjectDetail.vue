<template>
  <div class="iot-project-detail">
    <el-button text @click="router.back()" :icon="ArrowLeft" style="margin-bottom: 12px">返回项目列表</el-button>

    <el-card v-loading="loading" class="project-info">
      <div class="project-header">
        <div>
          <h2>{{ project.projectName }}</h2>
          <p class="project-meta">
            编号：{{ project.projectCode }} | 负责人：{{ project.ownerName }} |
            <el-tag :type="project.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ project.status === 'ACTIVE' ? '活跃' : '已归档' }}
            </el-tag>
          </p>
          <p class="project-desc" v-if="project.description">{{ project.description }}</p>
        </div>
      </div>
    </el-card>

    <h3 style="margin: 20px 0 12px">下属设备</h3>
    <el-table :data="devices" v-loading="devicesLoading" stripe>
      <el-table-column prop="deviceCode" label="设备编号" width="140" />
      <el-table-column prop="deviceName" label="设备名称" min-width="160" />
      <el-table-column prop="deviceType" label="类型" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastSeenAt" label="最后在线" width="160" />
      <el-table-column prop="ownerName" label="负责人" width="100" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="goToDevice(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!devicesLoading && devices.length === 0" description="该项目暂无设备" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getProjectDetail, listDevices } from '@/api/iot'

const route = useRoute()
const router = useRouter()
const projectId = route.params.id

const loading = ref(false)
const project = ref({})
const devices = ref([])
const devicesLoading = ref(false)

function statusTagType(status) {
  const map = { ONLINE: 'success', OFFLINE: 'info', ALERT: 'danger', MAINTENANCE: 'warning' }
  return map[status] || 'info'
}

async function loadProject() {
  loading.value = true
  try {
    const res = await getProjectDetail(projectId)
    project.value = res.data || {}
    devices.value = res.data.devices || []
  } catch (err) {
    console.error('加载项目详情失败', err)
  } finally {
    loading.value = false
  }
}

async function loadDevices() {
  devicesLoading.value = true
  try {
    const res = await listDevices({ projectId })
    devices.value = res.data.records || []
  } catch (err) {
    console.error('加载设备列表失败', err)
  } finally {
    devicesLoading.value = false
  }
}

function goToDevice(id) {
  router.push(`/iot/devices/${id}`)
}

onMounted(() => {
  loadProject()
  loadDevices()
})
</script>

<style scoped>
.iot-project-detail { padding: 8px; }
.project-info { margin-bottom: 16px; }
.project-header h2 { margin: 0; }
.project-meta { margin: 8px 0 0; color: #7c8798; font-size: 14px; }
.project-desc { margin: 8px 0 0; color: #606266; font-size: 14px; padding: 12px; background: #f5f7fa; border-radius: 6px; }
</style>