<template>
  <div class="procurement-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">采购单</h1>
        <p class="text-help muted">
          从申请、审批、下单到分批到货与只读库存入口；字段、状态、reason 与 version 均服从冻结契约。
        </p>
      </div>
      <div class="page-header__actions">
        <el-button
          v-if="canViewAllOrders()"
          plain
          :icon="Stamp"
          @click="showPending"
        >
          待审批
        </el-button>
        <el-button v-if="canCreateOrder()" type="primary" :icon="Plus" @click="goCreate">
          新建采购单
        </el-button>
      </div>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canAccessProcurement()"
      state="permission"
      variant="section"
      description="当前账号无权访问采购与库存"
      sub-title="请使用实验室成员、库存管理员、老师或系统管理员账号登录"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载采购单列表"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="采购单列表加载失败"
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
              placeholder="采购单号 / 标题 / 供应商 / 平台订单号"
              @keyup.enter="search"
            />
          </label>
          <label class="filter-field">
            <span>状态</span>
            <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 100%">
              <el-option
                v-for="(meta, status) in ORDER_STATUS_META"
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
            <span>供应商</span>
            <el-input v-model="filters.supplierKeyword" clearable placeholder="供应商名称" @keyup.enter="search" />
          </label>
          <label class="filter-field">
            <span>排序</span>
            <el-select v-model="filters.sort" style="width: 100%">
              <el-option label="最近更新优先" value="updateTime,desc" />
              <el-option label="创建时间优先" value="createTime,desc" />
              <el-option label="应付金额从高到低" value="payableAmount,desc" />
            </el-select>
          </label>
          <label class="filter-field filter-field--switch">
            <span>可见范围</span>
            <el-checkbox v-model="filters.mineOnly" :disabled="!currentUserId()">
              只看我的申请/买手单
            </el-checkbox>
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
            <h2 class="section-title">采购单列表</h2>
            <p class="text-help muted">共 {{ total }} 条 · 第 {{ page }} 页 · 每页 {{ pageSize }} 条</p>
          </div>
          <el-tag v-if="filters.status === ORDER_STATUS.PENDING_APPROVAL" type="warning" effect="plain">
            待审批筛选
          </el-tag>
        </header>

        <div v-if="!list.length" class="empty-block">
          <el-empty :description="emptyDescription">
            <el-button v-if="hasFilters" @click="resetFilters">清空筛选</el-button>
            <el-button v-else-if="canCreateOrder()" type="primary" @click="goCreate">新建采购单</el-button>
          </el-empty>
        </div>

        <template v-else>
          <el-table
            class="desktop-only"
            :data="list"
            row-key="id"
            stripe
            @row-click="goDetail"
          >
            <el-table-column label="采购单号" min-width="160">
              <template #default="{ row }">
                <el-button text type="primary" class="link-button" @click.stop="goDetail(row)">
                  {{ row.purchaseNo }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="130">
              <template #default="{ row }">
                <OrderStatusTag :status="row.status" size="small" />
              </template>
            </el-table-column>
            <el-table-column prop="requesterName" label="申请人" width="110">
              <template #default="{ row }">{{ row.requesterName || `用户 ${row.requesterId}` }}</template>
            </el-table-column>
            <el-table-column label="项目" min-width="130">
              <template #default="{ row }">{{ row.projectName || '—' }}</template>
            </el-table-column>
            <el-table-column label="应付金额" width="130" align="right">
              <template #default="{ row }">
                <span class="amount-text">{{ formatAmount(row.payableAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="下单时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.orderedAt) }}</template>
            </el-table-column>
            <el-table-column label="最近更新" width="160">
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
                <OrderStatusTag :status="row.status" size="small" />
              </div>
              <p class="text-help muted">{{ row.purchaseNo }} · v{{ row.version }}</p>
              <div class="mobile-card__meta">
                <span>申请人 {{ row.requesterName || row.requesterId }}</span>
                <span>项目 {{ row.projectName || '—' }}</span>
              </div>
              <div class="mobile-card__foot">
                <span class="amount-text">{{ formatAmount(row.payableAmount) }}</span>
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
import { Plus, Stamp } from '@element-plus/icons-vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import RegionState from '@/components/RegionState.vue'
import OrderStatusTag from '@/components/procurement/OrderStatusTag.vue'
import {
  ORDER_STATUS,
  ORDER_STATUS_META,
  canAccessProcurement,
  canCreateOrder,
  canViewAllOrders,
  currentUserId,
  formatAmount,
  listOrders,
  normalizePage
} from '@/api/procurement'
import { listProjects } from '@/api/projects'
import { formatDateTime } from '@/utils/format'
import { isProcurementDenied, normalizeApiError } from '@/utils/apiError'

const router = useRouter()

const filters = reactive({
  keyword: '',
  status: '',
  projectId: '',
  supplierKeyword: '',
  mineOnly: false,
  sort: 'updateTime,desc'
})

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const projects = ref([])

const hasFilters = computed(() => Boolean(
  filters.keyword
  || filters.status
  || filters.projectId
  || filters.supplierKeyword
  || filters.mineOnly
))

const emptyDescription = computed(() => (hasFilters.value
  ? '没有符合当前筛选条件的采购单'
  : '暂无采购单；新建后可提交审批或安全免审批准'))

function paramsOf() {
  const params = {
    page: page.value,
    pageSize: pageSize.value,
    sort: filters.sort
  }
  if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
  if (filters.status) params.status = filters.status
  if (filters.projectId) params.projectId = filters.projectId
  if (filters.supplierKeyword.trim()) params.supplierKeyword = filters.supplierKeyword.trim()
  if (filters.mineOnly && currentUserId()) params.requesterId = currentUserId()
  return params
}

async function load() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  try {
    const response = await listOrders(paramsOf())
    const normalized = normalizePage(response)
    list.value = normalized.list
    total.value = normalized.total
    if (!list.value.length && normalized.total === 0 && page.value > 1) {
      page.value = 1
      await load()
    }
  } catch (error) {
    const normalized = normalizeApiError(error)
    loadError.value = isProcurementDenied(error)
      ? '当前账号没有采购访问权限'
      : normalized.msg || '采购单列表加载失败'
    loadTraceId.value = normalized.traceId || ''
    list.value = []
    total.value = 0
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
  filters.supplierKeyword = ''
  filters.mineOnly = false
  filters.sort = 'updateTime,desc'
  page.value = 1
  load()
}

function showPending() {
  filters.status = ORDER_STATUS.PENDING_APPROVAL
  page.value = 1
  load()
}

function onPageSizeChange() {
  page.value = 1
  load()
}

function goCreate() {
  router.push('/procurements/new')
}

function goDetail(row) {
  router.push(`/procurements/${row.id}`)
}

async function loadProjects() {
  try {
    const response = await listProjects({ page: 1, pageSize: 100 })
    projects.value = normalizePage(response).list
  } catch {
    projects.value = []
  }
}

onMounted(() => {
  load()
  loadProjects()
})
</script>

<style scoped>
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.page-header__actions { display: flex; gap: 8px; flex-wrap: wrap; }
.filter-panel { display: flex; flex-direction: column; gap: 12px; }
.filter-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; }
.filter-field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--text-secondary, #606266); }
.filter-field--switch { justify-content: flex-end; }
.filter-actions { display: flex; gap: 8px; }
.list-panel { display: flex; flex-direction: column; gap: 12px; }
.list-panel__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.link-button { padding: 0; font-weight: 600; }
.amount-text { font-variant-numeric: tabular-nums; font-weight: 600; }
.version-text { color: var(--text-secondary, #909399); font-variant-numeric: tabular-nums; }
.empty-block { padding: 16px 0; }
.mobile-cards { display: flex; flex-direction: column; gap: 10px; }
.mobile-card { border: 1px solid var(--border-color, #ebeef5); border-radius: 8px; padding: 12px; display: flex; flex-direction: column; gap: 8px; cursor: pointer; }
.mobile-card__top { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.mobile-card__meta { display: flex; gap: 12px; flex-wrap: wrap; font-size: 13px; color: var(--text-secondary, #606266); }
.mobile-card__foot { display: flex; align-items: center; justify-content: space-between; }
.mobile-only { display: none; }
@media (max-width: 767px) {
  .desktop-only { display: none; }
  .mobile-only { display: flex; }
  .filter-grid { grid-template-columns: 1fr; }
}
</style>
