<template>
  <div class="iot-projects">
    <div class="page-header">
      <h2>项目列表</h2>
      <el-button type="primary" @click="showCreate = true">新建项目</el-button>
    </div>

    <!-- 搜索筛选 -->
    <el-card class="filter-bar">
      <el-form inline>
        <el-form-item label="搜索">
          <el-input v-model="query.keyword" placeholder="项目名称/编号" clearable @clear="search" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable @change="search" style="width: 140px">
            <el-option label="活跃" value="ACTIVE" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-table :data="projects" v-loading="loading" stripe>
      <el-table-column prop="projectCode" label="项目编号" width="140" />
      <el-table-column prop="projectName" label="项目名称" min-width="160" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="ownerName" label="负责人" width="120" />
      <el-table-column label="设备数" width="100">
        <template #default="{ row }">
          {{ row.deviceCount || 0 }} / {{ row.onlineDeviceCount || 0 }} 在线
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '活跃' : '已归档' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="goToDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && projects.length === 0" description="暂无项目数据" />

    <!-- 分页 -->
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

    <!-- 新建项目对话框 -->
    <el-dialog v-model="showCreate" title="新建项目" width="500px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="项目编号" required>
          <el-input v-model="createForm.projectCode" placeholder="如 power-monitor" />
        </el-form-item>
        <el-form-item label="项目名称" required>
          <el-input v-model="createForm.projectName" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="公开可见">
          <el-switch v-model="createForm.publicVisible" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">确认创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProjects } from '@/api/iot'

const router = useRouter()
const loading = ref(false)
const projects = ref([])
const total = ref(0)
const query = ref({
  keyword: '',
  status: '',
  pageNum: 1,
  pageSize: 10
})

// 创建
const showCreate = ref(false)
const creating = ref(false)
const createForm = ref({
  projectCode: '',
  projectName: '',
  description: '',
  publicVisible: false
})

async function loadData() {
  loading.value = true
  try {
    const res = await listProjects(query.value)
    projects.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (err) {
    console.error('加载项目列表失败', err)
    projects.value = []
  } finally {
    loading.value = false
  }
}

function search() {
  query.value.pageNum = 1
  loadData()
}

function resetSearch() {
  query.value = { keyword: '', status: '', pageNum: 1, pageSize: 10 }
  loadData()
}

function goToDetail(id) {
  router.push(`/iot/projects/${id}`)
}

async function handleCreate() {
  if (!createForm.value.projectCode || !createForm.value.projectName) {
    ElMessage.warning('请填写项目编号和名称')
    return
  }
  creating.value = true
  try {
    await new Promise(r => setTimeout(r, 500))
    ElMessage.success('项目创建成功（mock）')
    showCreate.value = false
    createForm.value = { projectCode: '', projectName: '', description: '', publicVisible: false }
    loadData()
  } catch (err) {
    console.error('创建项目失败', err)
  } finally {
    creating.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.iot-projects { padding: 8px; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.page-header h2 { margin: 0; }
.filter-bar { margin-bottom: 16px; }
</style>