<template>
  <div class="template-list-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">实验模板</h1>
        <p class="page-subtitle muted">定义实验字段结构，版本发布后不可修改，旧版本永久可读</p>
      </div>
      <el-tag class="domain-tag" effect="plain" size="large">学习 · 实验基础</el-tag>
    </header>

    <RegionState
      v-if="!canAccess"
      state="permission"
      variant="section"
      description="当前账号无权访问实验模板"
      sub-title="实验模板仅对已登录的实验室成员开放"
    />

    <template v-else>
      <div class="template-toolbar">
        <el-tabs v-model="activeScope" class="scope-tabs" @tab-change="handleScopeChange">
          <el-tab-pane :label="scopeMeta(TEMPLATE_SCOPE.SYSTEM).label" :name="TEMPLATE_SCOPE.SYSTEM" />
          <el-tab-pane :label="scopeMeta(TEMPLATE_SCOPE.PROJECT).label" :name="TEMPLATE_SCOPE.PROJECT" />
        </el-tabs>

        <el-button v-if="canCreateCurrentScope" type="primary" :icon="Plus" @click="openCreate">
          创建模板
        </el-button>
      </div>

      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索模板标题或描述"
          clearable
          class="filter-keyword"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-select
          v-if="activeScope === TEMPLATE_SCOPE.PROJECT"
          v-model="query.projectId"
          placeholder="全部项目"
          clearable
          class="filter-select"
          @change="handleSearch"
        >
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>

        <el-select v-model="query.category" placeholder="全部分类" clearable class="filter-select" @change="handleSearch">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>

        <el-select v-if="isManager" v-model="query.status" placeholder="版本状态" clearable class="filter-select" @change="handleSearch">
          <el-option label="草稿" :value="TEMPLATE_VERSION_STATUS.DRAFT" />
          <el-option label="已发布" :value="TEMPLATE_VERSION_STATUS.PUBLISHED" />
          <el-option label="已废弃" :value="TEMPLATE_VERSION_STATUS.DEPRECATED" />
          <el-option label="全部" value="ALL" />
        </el-select>

        <el-select v-model="query.sortBy" class="filter-select" @change="handleSearch">
          <el-option label="最近更新" value="updateTime" />
          <el-option label="创建时间" value="createTime" />
          <el-option label="标题" value="title" />
        </el-select>
      </div>

      <div v-loading="loading" class="template-grid">
        <template v-if="list.length">
          <article v-for="item in list" :key="item.id" class="template-card" @click="goDetail(item.id)">
            <div class="template-card__head">
              <h3 class="template-card__title">{{ item.title }}</h3>
              <el-tag :type="scopeMeta(item.scope).tagType" size="small" effect="light">
                {{ scopeMeta(item.scope).label }}
              </el-tag>
            </div>

            <p class="template-card__summary">{{ item.description || '暂无模板描述' }}</p>

            <div class="template-card__tags">
              <el-tag v-if="item.category" size="small" effect="plain">{{ item.category }}</el-tag>
              <el-tag v-if="item.scope === TEMPLATE_SCOPE.PROJECT && item.projectName" size="small" effect="plain" type="info">
                {{ item.projectName }}
              </el-tag>
            </div>

            <dl class="template-card__meta">
              <div>
                <dt>最新版本</dt>
                <dd>{{ item.latestVersion || '未发布' }}</dd>
              </div>
              <div>
                <dt>已发布版本</dt>
                <dd>{{ item.publishedVersionCount }}</dd>
              </div>
            </dl>

            <div class="template-card__footer">
              <span class="text-help muted">{{ item.createdByName || '未知' }} · {{ formatDateTime(item.updateTime) }}</span>
              <div class="template-card__actions" @click.stop>
                <el-button size="small" @click="goDetail(item.id)">查看</el-button>
                <el-button
                  v-if="item.scope === TEMPLATE_SCOPE.SYSTEM && canCopyToProject"
                  size="small"
                  type="primary"
                  plain
                  @click="openCopy(item)"
                >
                  复制到项目
                </el-button>
              </div>
            </div>
          </article>
        </template>

        <RegionState v-else-if="!loading" state="empty" variant="card" :description="emptyDescription">
          <el-button v-if="hasFilter" @click="resetFilter">清除筛选条件</el-button>
        </RegionState>
      </div>

      <div v-if="total > 0" class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[9, 12, 24]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSearch"
          @current-change="loadList"
        />
      </div>
    </template>

    <TemplateFormDialog
      v-model="createVisible"
      mode="create"
      :projects="projects"
      :allow-system="canManageSystemTemplates()"
      :submitting="submitting"
      @submit="handleCreate"
    />

    <CopyTemplateDialog
      v-model="copyVisible"
      :template="copySource"
      :projects="copyTargetProjects"
      :submitting="submitting"
      @submit="handleCopy"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  listTemplates,
  createTemplate,
  copyTemplate,
  canAccessTemplates,
  canManageSystemTemplates,
  canManageProjectTemplates,
  TEMPLATE_SCOPE,
  TEMPLATE_VERSION_STATUS,
  templateScopeMeta
} from '@/api/experimentTemplates'
import { listProjects } from '@/api/projects'
import { notifyApiError } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'
import RegionState from '@/components/RegionState.vue'
import TemplateFormDialog from '@/components/templates/TemplateFormDialog.vue'
import CopyTemplateDialog from '@/components/templates/CopyTemplateDialog.vue'

const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const activeScope = ref(TEMPLATE_SCOPE.SYSTEM)
const list = ref([])
const total = ref(0)
const projects = ref([])

const createVisible = ref(false)
const copyVisible = ref(false)
const copySource = ref(null)

const canAccess = computed(() => canAccessTemplates())
const isManager = computed(() => canManageSystemTemplates())

/**
 * 管理者默认查看全部状态，避免仅含草稿的模板在列表中被默认过滤而不可见；
 * 非管理者不下发 status，由接口按契约默认 PUBLISHED 仅返回已发布版本。
 */
function defaultStatusFilter() {
  return isManager.value ? 'ALL' : ''
}

const query = ref({
  page: 1,
  pageSize: 9,
  keyword: '',
  category: '',
  status: defaultStatusFilter(),
  projectId: null,
  sortBy: 'updateTime',
  sortOrder: 'desc'
})
const canManageAnyProject = computed(() => projects.value.some((p) => canManageProjectTemplates(p.id)))
const canCreateCurrentScope = computed(() =>
  activeScope.value === TEMPLATE_SCOPE.SYSTEM ? canManageSystemTemplates() : canManageSystemTemplates() || canManageAnyProject.value
)
const canCopyToProject = computed(() => canManageAnyProject.value)
const copyTargetProjects = computed(() => projects.value.filter((p) => canManageProjectTemplates(p.id)))

const categories = computed(() => {
  const set = new Set(list.value.map((t) => t.category).filter(Boolean))
  return [...set]
})

const hasFilter = computed(() =>
  Boolean(
    query.value.keyword ||
      query.value.category ||
      query.value.projectId ||
      (isManager.value && query.value.status && query.value.status !== 'ALL')
  )
)

const emptyDescription = computed(() => {
  if (hasFilter.value) return '没有符合筛选条件的实验模板'
  if (activeScope.value === TEMPLATE_SCOPE.SYSTEM) return '暂无系统模板'
  return '暂无项目模板'
})

function scopeMeta(scope) {
  return templateScopeMeta(scope)
}

async function loadProjects() {
  try {
    const res = await listProjects({ page: 1, pageSize: 50 })
    projects.value = res?.list || []
  } catch (err) {
    notifyApiError(err)
    projects.value = []
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await listTemplates({
      scope: activeScope.value,
      projectId: activeScope.value === TEMPLATE_SCOPE.PROJECT ? query.value.projectId : undefined,
      category: query.value.category || undefined,
      status: query.value.status || undefined,
      keyword: query.value.keyword || undefined,
      sortBy: query.value.sortBy,
      sortOrder: query.value.sortOrder,
      page: query.value.page,
      pageSize: query.value.pageSize
    })
    list.value = res?.list || []
    total.value = res?.total ?? 0
  } catch (err) {
    notifyApiError(err)
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.value.page = 1
  loadList()
}

function handleScopeChange() {
  query.value.page = 1
  query.value.projectId = null
  query.value.category = ''
  query.value.status = defaultStatusFilter()
  query.value.keyword = ''
  loadList()
}

function resetFilter() {
  query.value.keyword = ''
  query.value.category = ''
  query.value.status = defaultStatusFilter()
  query.value.projectId = null
  handleSearch()
}

function goDetail(id) {
  router.push(`/experiment/templates/${id}`)
}

function openCreate() {
  createVisible.value = true
}

async function handleCreate(payload) {
  submitting.value = true
  try {
    const created = await createTemplate(payload)
    ElMessage.success('模板已创建，已自动生成草稿版本')
    createVisible.value = false
    if (created?.id) {
      router.push(`/experiment/templates/${created.id}`)
    } else {
      loadList()
    }
  } catch (err) {
    notifyApiError(err)
  } finally {
    submitting.value = false
  }
}

function openCopy(template) {
  copySource.value = template
  copyVisible.value = true
}

async function handleCopy(payload) {
  submitting.value = true
  try {
    const created = await copyTemplate(copySource.value.id, payload)
    ElMessage.success('已复制为项目模板')
    copyVisible.value = false
    if (created?.id) router.push(`/experiment/templates/${created.id}`)
    else loadList()
  } catch (err) {
    notifyApiError(err)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  if (!canAccess.value) return
  await loadProjects()
  await loadList()
})
</script>

<style scoped>
.template-list-page {
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

/* 学习实验台识别色：紫色，小面积 */
.domain-tag {
  color: var(--usn-purple-600);
  border-color: var(--usn-purple-600);
  background: #faf5ff;
  font-weight: 600;
}

.template-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-4);
  flex-wrap: wrap;
}

.scope-tabs {
  flex: 1 1 auto;
}

.filter-bar {
  display: flex;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.filter-keyword {
  flex: 1 1 240px;
}

.filter-select {
  width: 150px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--usn-space-4);
  min-height: 200px;
}

.template-card {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
  padding: var(--usn-space-4);
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-top: 3px solid var(--usn-purple-600);
  border-radius: var(--usn-radius-md);
  box-shadow: var(--usn-shadow-panel);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.template-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(23, 33, 43, 0.1);
}

.template-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-2);
}

.template-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--usn-ink-900);
  line-height: 1.3;
}

.template-card__summary {
  margin: 0;
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.8em;
}

.template-card__tags {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.template-card__meta {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--usn-space-2);
  margin: 0;
  padding: var(--usn-space-2) 0;
  border-top: 1px solid var(--usn-line);
}

.template-card__meta div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.template-card__meta dt {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.template-card__meta dd {
  margin: 0;
  font-size: var(--usn-font-size-body);
  font-weight: 600;
  color: var(--usn-ink-900);
}

.template-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-2);
  margin-top: auto;
  flex-wrap: wrap;
}

.template-card__actions {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
}

.pagination-bar {
  display: flex;
  justify-content: center;
  padding-top: var(--usn-space-2);
}

@media (max-width: 1365px) {
  .template-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 767px) {
  .template-grid {
    grid-template-columns: 1fr;
  }

  .filter-select {
    width: 100%;
  }

  .template-card__footer {
    flex-direction: column;
    align-items: stretch;
  }

  .template-card__actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
