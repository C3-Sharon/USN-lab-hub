<template>
  <div class="iot-device-list">
    <h2>设备列表</h2>

    <el-card class="filter-bar">
      <el-form inline>
        <el-form-item label="搜索">
          <el-input v-model="query.keyword" placeholder="设备名称/编号" clearable @clear="search" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="项目">
          <el-select v-model="query.projectId" placeholder="全部项目" clearable @change="search" style="width: 160px">
            <el-option v-for="p in projectOptions" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable @change="search" style="width: 140px">
            <el-option label="在线" value="ONLINE" />
            <el-option label="离线" value="OFFLINE" />
            <el-option label="告警" value="ALERT" />
            <el-option label="维护中" value="MAINTENANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="query.deviceType" placeholder="全部类型" clearable @change="search" style="width: 140px">
            <el-option label="功耗检测仪" value="POWER_METER" />
            <el-option label="温度传感器" value="TEMP_SENSOR" />
            <el-option label="湿度传感器" value="HUMIDITY_SENSOR" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

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
    <el-empty v-if="!loading && devices.length === 0" description="暂无设备数据" />

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
import { useRouter } from 'vue-router'
import { listDevices, listProjects } from '@/api/iot'

const router = useRouter()
const loading = ref(false)
const devices = ref([])
const total = ref(0)
const projectOptions = ref([])
const query = ref({
  keyword: '',
  projectId: '',
  status: '',
  deviceType: '',
  pageNum: 1,
  pageSize: 10
})

function statusTagType(status) {
  const map = { ONLINE: 'success', OFFLINE: 'info', ALERT: 'danger', MAINTENANCE: 'warning' }
  return map[status] || 'info'
}

async function loadProjects() {
  try {
    const res = await listProjects({ pageSize: 100 })
    projectOptions.value = res.data.records || []
  } catch (err) {
    console.error('加载项目选项失败', err)
  }
}

async function loadData() {
  loading.value = true
  try {
    const params = { ...query.value }
    // 清除空值
    Object.keys(params).forEach(k => { if (!params[k]) delete params[k] })
    const res = await listDevices(params)
    devices.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (err) {
    console.error('加载设备列表失败', err)
    devices.value = []
  } finally {
    loading.value = false
  }
}

function search() {
  query.value.pageNum = 1
  loadData()
}

function resetSearch() {
  query.value = { keyword: '', projectId: '', status: '', deviceType: '', pageNum: 1, pageSize: 10 }
  loadData()
}

function goToDetail(id) {
  router.push(`/iot/devices/${id}`)
}

onMounted(() => {
  loadProjects()
  loadData()
})
</script>

<style scoped>
.iot-device-list { padding: 8px; }
.filter-bar { margin-bottom: 16px; }
</style>