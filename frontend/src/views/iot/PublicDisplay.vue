<template>
  <div class="iot-public">
    <header class="public-header">
      <div class="public-brand">
        <span class="brand-icon">USN</span>
        <strong>Lab Hub IoT · 公开项目展示</strong>
      </div>
    </header>

    <main class="public-main">
      <h2 class="page-title">当前展示项目</h2>

      <el-row :gutter="20" v-loading="loading">
        <el-col :span="8" v-for="project in projects" :key="project.id" style="margin-bottom: 20px">
          <el-card shadow="hover" class="project-card">
            <div class="card-header">
              <span class="project-code">{{ project.projectCode }}</span>
              <el-tag :type="project.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                {{ project.status === 'ACTIVE' ? '进行中' : '已归档' }}
              </el-tag>
            </div>
            <h3 class="project-name">{{ project.projectName }}</h3>
            <p class="project-desc">{{ project.description || '暂无描述' }}</p>
            <div class="card-footer">
              <span>设备 {{ project.deviceCount || 0 }} 台</span>
              <span>在线 {{ project.onlineDeviceCount || 0 }} 台</span>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!loading && projects.length === 0" description="暂无公开项目展示" />
    </main>

    <footer class="public-footer">
      USN Lab Hub IoT · 实验室硬件项目管理与智能运维平台
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listProjects } from '@/api/iot'

const loading = ref(false)
const projects = ref([])

async function loadData() {
  loading.value = true
  try {
    const res = await listProjects({ publicVisible: true })
    projects.value = res.data.records || []
  } catch (err) {
    console.error('加载公开项目失败', err)
    projects.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.iot-public {
  min-height: 100vh;
  background: #f4f7fb;
  display: flex;
  flex-direction: column;
}
.public-header {
  background: #172033;
  color: #fff;
  padding: 20px 40px;
}
.public-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 18px;
}
.brand-icon {
  display: inline-flex;
  width: 38px; height: 38px;
  align-items: center; justify-content: center;
  border-radius: 8px;
  background: #f5c542;
  color: #172033;
  font-weight: 800;
}
.public-main {
  flex: 1;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 32px 24px;
  box-sizing: border-box;
}
.page-title {
  margin: 0 0 24px;
  font-size: 22px;
  font-weight: 700;
}
.project-card {
  cursor: default;
}
.project-card:hover {
  transform: translateY(-2px);
  transition: transform 0.2s;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.project-code {
  font-size: 12px;
  color: #7c8798;
  font-family: monospace;
}
.project-name {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
}
.project-desc {
  margin: 0 0 12px;
  color: #606266;
  font-size: 14px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-footer {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #7c8798;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}
.public-footer {
  text-align: center;
  padding: 20px;
  color: #7c8798;
  font-size: 13px;
  border-top: 1px solid #e7ecf3;
  background: #fff;
}
</style>