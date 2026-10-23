<template>
  <div class="record-list-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">实验记录</h1>
        <p class="page-subtitle muted">固定模板版本记录实验过程，发布后内容不可变，修订与导师反馈全程留痕</p>
      </div>
      <el-tag class="domain-tag" effect="plain" size="large">学习 · 实验记录</el-tag>
    </header>

    <RegionState
      v-if="!canAccess"
      state="permission"
      variant="section"
      description="当前账号无权访问实验记录"
      sub-title="实验记录仅对已登录的实验室成员开放"
    />

    <template v-else>
      <div class="toolbar">
        <el-radio-group v-model="query.scope" @change="handleSearch">
          <el-radio-button value="ALL">全部可见</el-radio-button>
          <el-radio-button value="MINE">我的记录</el-radio-button>
        </el-radio-group>

        <el-select v-model="query.status" placeholder="全部状态" clearable class="filter-select" @change="handleSearch">
          <el-option v-for="s in statusOptions" :key="s" :label="recordStatusMeta(s).label" :value="s" />
        </el-select>

        <el-select v-model="query.projectId" placeholder="全部项目" clearable class="filter-select" @change="handleSearch">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>

        <el-input
          v-model="query.keyword"
          placeholder="搜索标题、摘要或编号"
          clearable
          class="filter-keyword"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-button v-if="canCreate" type="primary" :icon="Plus" @click="goCreate">新建实验记录</el-button>
      </div>

      <RegionState
        v-if="loading && !list.length"
        state="loading"
        variant="list"
      />

      <RegionState
        v-else-if="errorMessage"
        state="error"
        variant="section"
        description="加载实验记录失败"
        :sub-title="errorMessage"
        :error-message="errorMessage"
        @retry="loadList"
      />

      <RegionState
        v-else-if="!list.length"
        state="empty"
        variant="card"
        :description="hasFilter ? '没有符合筛选条件的实验记录' : '暂无实验记录，从固定模板版本创建第一条记录'"
      >
        <el-button v-if="hasFilter" @click="resetFilter">清除筛选条件</el-button>
        <el-button v-else-if="canCreate" type="primary" @click="goCreate">新建实验记录</el-button>
      </RegionState>

      <div v-else class="record-grid">
        <article v-for="item in list" :key="item.id" class="record-card" @click="goDetail(item.id)">
          <div class="record-card__head">
            <h3 class="record-card__title">{{ item.title }}</h3>
            <RecordStatusTag :status="item.status" />
          </div>

          <p class="record-card__summary">{{ item.summary || '暂无实验摘要' }}</p>

          <div class="record-card__tags">
            <el-tag size="small" effect="plain">{{ item.recordCode }}</el-tag>
            <el-tag v-if="item.projectName" size="small" type="primary" effect="plain">{{ item.projectName }}</el-tag>
            <el-tag v-else-if="item.learningUnitName" size="small" type="warning" effect="plain">
              {{ item.learningUnitName }}
            </el-tag>
          </div>

          <dl class="record-card__meta">
            <div>
              <dt>模板版本</dt>
              <dd>{{ item.templateName || '--' }} {{ item.templateVersion || '' }}</dd>
            </div>
            <div>
              <dt>当前修订</dt>
              <dd>
                第 {{ item.currentRevisionNo ?? '--' }} 版
                <RevisionStatusTag v-if="item.currentRevisionStatus" :status="item.currentRevisionStatus" />
              </dd>
            </div>
          </dl>

          <div class="record-card__footer">
            <span class="text-help muted">{{ item.authorName || '未知作者' }} · {{ formatDateTime(item.updateTime) }}</span>
            <div class="record-card__actions" @click.stop>
              <el-button size="small" @click="goDetail(item.id)">查看详情</el-button>
              <el-button
                v-if="item.currentRevisionStatus === 'EDITABLE'"
                size="small"
                type="primary"
                plain
                @click="goEdit(item)"
              >
                继续编辑
              </el-button>
            </div>
          </div>
        </article>
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
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Plus } from '@element-plus/icons-vue'
import {
  listExperimentRecords,
  canAccessExperiments,
  canCreateExperimentRecord,
  recordStatusMeta,
  RECORD_STATUS
} from '@/api/experimentRecords'
import { listProjects } from '@/api/projects'
import { notifyApiError, normalizeApiError, errorTraceId } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'
import RegionState from '@/components/RegionState.vue'
import RecordStatusTag from '@/components/experiment/RecordStatusTag.vue'
import RevisionStatusTag from '@/components/experiment/RevisionStatusTag.vue'

const router = useRouter()

const statusOptions = Object.values(RECORD_STATUS)
const loading = ref(false)
const errorMessage = ref('')
const list = ref([])
const total = ref(0)
const projects = ref([])

const query = ref({ scope: 'ALL', status: '', projectId: null, keyword: '', page: 1, pageSize: 9 })

const canAccess = computed(() => canAccessExperiments())
const canCreate = computed(() => canCreateExperimentRecord(null))
const hasFilter = computed(() => Boolean(query.value.status || query.value.projectId || query.value.keyword || query.value.scope === 'MINE'))

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
  errorMessage.value = ''
  try {
    const res = await listExperimentRecords({
      scope: query.value.scope,
      status: query.value.status || undefined,
      projectId: query.value.projectId || undefined,
      keyword: query.value.keyword || undefined,
      page: query.value.page,
      pageSize: query.value.pageSize
    })
    list.value = res?.list || []
    total.value = res?.total ?? 0
  } catch (err) {
    notifyApiError(err)
    const { msg } = normalizeApiError(err)
    const traceId = errorTraceId(err)
    errorMessage.value = traceId ? `${msg}（traceId: ${traceId}）` : msg
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

function resetFilter() {
  query.value = { scope: 'ALL', status: '', projectId: null, keyword: '', page: 1, pageSize: query.value.pageSize }
  loadList()
}

function goDetail(id) {
  router.push(`/experiment/records/${id}`)
}

function goCreate() {
  router.push('/experiment/records/new')
}

function goEdit(item) {
  router.push(`/experiment/records/${item.id}/edit/${item.currentRevisionId}`)
}

onMounted(async () => {
  if (!canAccess.value) return
  await loadProjects()
  await loadList()
})
</script>

<style scoped>
.record-list-page {
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

.domain-tag {
  color: var(--usn-purple-600);
  border-color: var(--usn-purple-600);
  background: #faf5ff;
  font-weight: 600;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.filter-keyword {
  flex: 1 1 220px;
}

.filter-select {
  width: 150px;
}

.record-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--usn-space-4);
}

.record-card {
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

.record-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(23, 33, 43, 0.1);
}

.record-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-2);
}

.record-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--usn-ink-900);
  line-height: 1.3;
}

.record-card__summary {
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

.record-card__tags {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.record-card__meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--usn-space-2);
  margin: 0;
  padding: var(--usn-space-2) 0;
  border-top: 1px solid var(--usn-line);
}

.record-card__meta div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.record-card__meta dt {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.record-card__meta dd {
  margin: 0;
  font-size: var(--usn-font-size-body);
  font-weight: 600;
  color: var(--usn-ink-900);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.record-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-2);
  margin-top: auto;
  flex-wrap: wrap;
}

.record-card__actions {
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
  .record-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 767px) {
  .record-grid {
    grid-template-columns: 1fr;
  }

  .filter-select {
    width: 100%;
  }

  .record-card__footer {
    flex-direction: column;
    align-items: stretch;
  }

  .record-card__actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
