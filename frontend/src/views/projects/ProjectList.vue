<template>
  <div class="project-list-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">项目工作台</h1>
        <p class="page-subtitle muted">查看和参与实验室正式项目</p>
      </div>
      <el-button v-if="canCreate" type="primary" :icon="Plus" @click="createDialogVisible = true">创建项目</el-button>
    </header>

    <!-- 筛选与搜索 -->
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索项目编号或名称"
        clearable
        class="filter-keyword"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="query.status" placeholder="状态" clearable class="filter-status" @change="handleSearch">
        <el-option v-for="(meta, key) in PROJECT_STATUS_META" :key="key" :label="meta.label" :value="key" />
      </el-select>
      <el-select v-model="query.sortBy" class="filter-sort" @change="handleSearch">
        <el-option label="最近更新" value="updateTime" />
        <el-option label="创建时间" value="createTime" />
        <el-option label="名称" value="name" />
        <el-option label="编号" value="code" />
      </el-select>
    </div>

    <!-- 列表 -->
    <div v-loading="loading" class="project-grid">
      <template v-if="list.length">
        <el-card v-for="item in list" :key="item.id" class="project-card" shadow="hover" @click="goDetail(item.id)">
          <div class="project-card__head">
            <ProjectCover :code="item.code" :size="48" />
            <div class="project-card__meta">
              <span class="project-card__code">{{ item.code }}</span>
              <el-tag :type="statusMeta(item.status).tagType" size="small" effect="light">
                {{ statusMeta(item.status).label }}
              </el-tag>
            </div>
          </div>
          <h3 class="project-card__name">{{ item.name }}</h3>
          <p class="project-card__summary">{{ item.summary || '暂无简介' }}</p>
          <div class="project-card__footer">
            <span class="muted text-help">我的角色：{{ roleLabel(item.myRole) }}</span>
            <span class="muted text-help">{{ item.memberCount }} 名成员</span>
            <span class="muted text-help">{{ formatTime(item.updateTime) }}</span>
          </div>
        </el-card>
      </template>

      <el-empty v-else-if="!loading" description="暂无参与的项目">
        <el-button v-if="canCreate" type="primary" @click="createDialogVisible = true">创建第一个项目</el-button>
      </el-empty>
    </div>

    <!-- 分页 -->
    <div v-if="total > 0" class="pagination-bar">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSearch"
        @current-change="loadList"
      />
    </div>

    <!-- 创建项目弹窗 -->
    <CreateProjectDialog v-model="createDialogVisible" @created="onCreated" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Search } from '@element-plus/icons-vue'
import { listProjects, PROJECT_STATUS_META, PROJECT_ROLE_META } from '@/api/projects'
import { hasAnyRole, userStore } from '@/store/user'
import { ROLE } from '@/utils/permission'
import ProjectCover from '@/components/ProjectCover.vue'
import CreateProjectDialog from './CreateProjectDialog.vue'

const router = useRouter()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const createDialogVisible = ref(false)

const query = ref({
  page: 1,
  pageSize: 10,
  status: '',
  keyword: '',
  sortBy: 'updateTime',
  sortOrder: 'desc'
})

const canCreate = computed(() => hasAnyRole([ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.MEMBER]))

function statusMeta(status) {
  return PROJECT_STATUS_META[status] || { label: status, tagType: 'info' }
}

function roleLabel(role) {
  return PROJECT_ROLE_META[role]?.label || role || '--'
}

function formatTime(iso) {
  if (!iso) return '--'
  const d = new Date(iso)
  return isNaN(d) ? iso : d.toLocaleDateString('zh-CN')
}

async function loadList() {
  loading.value = true
  try {
    const res = await listProjects(query.value)
    list.value = res?.list || []
    total.value = res?.total ?? 0
  } catch (err) {
    console.error('加载项目列表失败', err)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.value.page = 1
  loadList()
}

function goDetail(id) {
  router.push(`/projects/${id}`)
}

function onCreated(project) {
  createDialogVisible.value = false
  router.push(`/projects/${project.id}`)
}

onMounted(() => loadList())
</script>

<style scoped>
.project-list-page {
  max-width: var(--usn-content-max-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-4);
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: var(--usn-font-size-page-title);
  font-weight: 700;
  color: var(--usn-ink-900);
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: var(--usn-font-size-help);
}

.filter-bar {
  display: flex;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.filter-keyword { flex: 1 1 240px; }
.filter-status { width: 140px; }
.filter-sort { width: 140px; }

.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--usn-space-4);
  min-height: 200px;
}

.project-card {
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 1px solid var(--usn-line);
}

.project-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(23, 33, 43, 0.1);
}

.project-card__head {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
  margin-bottom: var(--usn-space-3);
}

.project-card__meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.project-card__code {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: var(--usn-ink-700);
  font-weight: 600;
}

.project-card__name {
  margin: 0 0 var(--usn-space-2);
  font-size: 16px;
  font-weight: 600;
  color: var(--usn-ink-900);
  line-height: 1.3;
}

.project-card__summary {
  margin: 0 0 var(--usn-space-3);
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.8em;
}

.project-card__footer {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--usn-space-2);
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.pagination-bar {
  display: flex;
  justify-content: center;
  padding-top: var(--usn-space-2);
}

@media (max-width: 768px) {
  .project-grid {
    grid-template-columns: 1fr;
  }
}
</style>
