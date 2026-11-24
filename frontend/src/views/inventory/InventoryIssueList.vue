<template>
  <div class="issue-list-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">库存领用</h1>
        <p class="text-help muted">
          申请、预占、审批与发放全程可追溯；提交即预占，审批不扣库存，发放确认后才写 ISSUE_OUT。
        </p>
      </div>
      <div class="page-header__actions page-header__actions--wrap">
        <el-button v-if="canViewAllRequests()" plain :icon="Stamp" @click="go('/inventory/issue-approvals')">
          待审批
        </el-button>
        <el-button v-if="canViewAllRequests()" plain :icon="Box" @click="go('/inventory/fulfillments')">
          发放队列
        </el-button>
        <el-button plain :icon="Tickets" @click="go('/inventory/movements')">库存流水</el-button>
        <el-button v-if="canPublishIssuePolicy()" plain :icon="Setting" @click="go('/inventory/issue-settings')">
          领用策略
        </el-button>
        <el-button v-if="canCreateIssueRequest()" type="primary" :icon="Plus" @click="goCreate">
          新建领用申请
        </el-button>
      </div>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canAccessIssuance()"
      state="permission"
      variant="section"
      description="当前账号无权访问库存领用"
      sub-title="请使用实验室成员、库存管理员、老师或系统管理员账号登录"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载领用申请列表"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="领用申请列表加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="load"
    />

    <template v-else>
      <section class="filter-panel data-panel">
        <div class="filter-grid">
          <label class="filter-field">
            <span>关键词</span>
            <el-input
              v-model="filters.keyword"
              clearable
              placeholder="申请单号 / 标题 / 用途"
              @keyup.enter="search"
            />
          </label>
          <label class="filter-field">
            <span>状态</span>
            <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 100%">
              <el-option
                v-for="(meta, status) in ISSUE_STATUS_META"
                :key="status"
                :label="meta.label"
                :value="status"
              />
            </el-select>
          </label>
          <label class="filter-field">
            <span>项目</span>
            <el-select v-model="filters.projectId" clearable placeholder="全部可见项目" style="width: 100%">
              <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
            </el-select>
          </label>
          <label class="filter-field">
            <span>排序</span>
            <el-select v-model="filters.sort" style="width: 100%">
              <el-option label="最近更新优先" value="updateTime,desc" />
              <el-option label="提交时间优先" value="submittedAt,desc" />
              <el-option label="预占到期优先" value="expiresAt,asc" />
            </el-select>
          </label>
          <label class="filter-field filter-field--switch">
            <span>可见范围</span>
            <el-checkbox v-model="filters.mineOnly" :disabled="!currentUserId()">只看我的申请</el-checkbox>
          </label>
        </div>
        <div class="filter-actions">
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </section>

      <section class="list-panel data-panel">
        <header class="list-panel__header">
          <div>
            <h2 class="section-title">领用申请</h2>
            <p class="text-help muted">共 {{ total }} 条 · 第 {{ page }} 页 · 每页 {{ pageSize }} 条</p>
          </div>
          <el-tag v-if="filters.status" type="warning" effect="plain">
            {{ issueStatusMeta(filters.status).label }}筛选
          </el-tag>
        </header>

        <div v-if="!list.length" class="empty-block">
          <el-empty description="暂无领用申请">
            <el-button v-if="hasFilters" @click="resetFilters">清空筛选</el-button>
            <el-button v-else-if="canCreateIssueRequest()" type="primary" @click="goCreate">新建领用申请</el-button>
          </el-empty>
        </div>

        <template v-else>
          <el-table class="desktop-only" :data="list" row-key="id" stripe @row-click="goDetail">
            <el-table-column label="申请单号" min-width="170">
              <template #default="{ row }">
                <el-button text type="primary" class="link-button" @click.stop="goDetail(row)">
                  {{ row.requestNo }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="140">
              <template #default="{ row }">
                <IssueStatusTag :status="row.status" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="申请人" width="110">
              <template #default="{ row }">{{ row.requesterName || `用户 ${row.requesterId}` }}</template>
            </el-table-column>
            <el-table-column label="项目" min-width="130">
              <template #default="{ row }">{{ row.projectName || '—' }}</template>
            </el-table-column>
            <el-table-column label="明细" width="80" align="center">
              <template #default="{ row }">{{ row.lineCount }}</template>
            </el-table-column>
            <el-table-column v-if="canViewIssueCost()" label="预估金额" width="120" align="right">
              <template #default="{ row }">
                <span class="amount-text">{{ row.estimatedAmount == null ? '—' : formatAmount(row.estimatedAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="预占到期" width="170">
              <template #default="{ row }">
                {{ row.expiresAt ? formatDateTime(row.expiresAt) : '—' }}
              </template>
            </el-table-column>
            <el-table-column label="最近更新" width="170">
              <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
            </el-table-column>
            <el-table-column label="version" width="90" align="center">
              <template #default="{ row }"><span class="version-text">v{{ row.version }}</span></template>
            </el-table-column>
          </el-table>

          <div class="mobile-only mobile-cards">
            <article v-for="row in list" :key="row.id" class="mobile-card" @click="goDetail(row)">
              <div class="mobile-card__top">
                <strong>{{ row.title }}</strong>
                <IssueStatusTag :status="row.status" size="small" />
              </div>
              <p class="text-help muted">{{ row.requestNo }} · v{{ row.version }}</p>
              <div class="mobile-card__meta">
                <span>申请人 {{ row.requesterName || row.requesterId }}</span>
                <span>项目 {{ row.projectName || '—' }}</span>
              </div>
              <div class="mobile-card__foot">
                <span class="text-help muted">
                  {{ row.lineCount }} 行 · {{ row.expiresAt ? `预占至 ${formatDateTime(row.expiresAt)}` : '无预占' }}
                </span>
                <span class="text-help muted">{{ formatDateTime(row.updateTime) }}</span>
              </div>
            </article>
          </div>

          <footer class="table-footer">
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50]"
              :total="total"
              layout="total, sizes, prev, pager, next"
              background
              @current-change="load"
              @size-change="onPageSizeChange"
            />
          </footer>
        </template>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Box, Plus, Setting, Stamp, Tickets } from '@element-plus/icons-vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import RegionState from '@/components/RegionState.vue'
import IssueStatusTag from '@/components/inventory/IssueStatusTag.vue'
import {
  ISSUE_STATUS_META,
  canAccessIssuance,
  canCreateIssueRequest,
  canPublishIssuePolicy,
  canViewAllRequests,
  canViewIssueCost,
  currentUserId,
  formatAmount,
  issueStatusMeta,
  listIssueRequests,
  normalizePage
} from '@/api/inventoryIssuance'
import { notifyApiError, normalizeApiError, errorTraceId } from '@/utils/apiError'
import { listProjects } from '@/api/projects'
import { buildQueryWithoutEmpty, formatDateTime } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const projects = ref([])

const filters = reactive({
  keyword: '',
  status: '',
  projectId: '',
  sort: 'updateTime,desc',
  mineOnly: false
})

const hasFilters = computed(() => Boolean(filters.keyword || filters.status || filters.projectId || filters.mineOnly))

function go(path) {
  router.push(path)
}

function goCreate() {
  router.push('/inventory/issues/new')
}

function goDetail(row) {
  router.push(`/inventory/issues/${row.id}`)
}

async function loadProjects() {
  try {
    const response = await listProjects({ page: 1, pageSize: 100 })
    projects.value = response?.list || response?.items || []
  } catch (error) {
    projects.value = []
  }
}

async function load() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  try {
    const response = await listIssueRequests(buildQueryWithoutEmpty({
      keyword: filters.keyword,
      status: filters.status,
      projectId: filters.projectId,
      requesterId: filters.mineOnly ? currentUserId() : '',
      sort: filters.sort,
      page: page.value,
      pageSize: pageSize.value
    }))
    const normalized = normalizePage(response)
    list.value = normalized.list
    total.value = normalized.total
  } catch (error) {
    loadError.value = normalizeApiError(error).msg
    loadTraceId.value = errorTraceId(error)
    list.value = []
    total.value = 0
    notifyApiError(error)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.projectId = ''
  filters.sort = 'updateTime,desc'
  filters.mineOnly = false
  page.value = 1
  load()
}

function onPageSizeChange(size) {
  pageSize.value = size
  page.value = 1
  load()
}

onMounted(async () => {
  await loadProjects()
  load()
})
</script>

<style scoped>
.issue-list-page {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.page-header__actions--wrap {
  flex-wrap: wrap;
}
</style>
