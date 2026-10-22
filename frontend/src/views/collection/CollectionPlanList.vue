<template>
  <div class="collection-plan-list">
    <header v-if="!embedded" class="page-header">
      <div>
        <h1 class="page-title">数据采集</h1>
        <p class="page-subtitle muted">按采集计划创建 FILE / MANUAL 批次，批次完成后冻结证据并生成快照</p>
      </div>
      <el-tag class="domain-tag" effect="plain" size="large">项目 · 数据采集</el-tag>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canAccess"
      state="permission"
      variant="section"
      description="当前账号无权访问数据采集"
      sub-title="数据采集仅对已登录的实验室成员开放"
    />

    <RegionState
      v-else-if="permissionDenied"
      state="permission"
      variant="section"
      description="项目不存在或无权查看采集计划"
      :sub-title="permissionMessage"
    />

    <template v-else>
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="全部状态" clearable class="filter-select" @change="handleSearch">
          <el-option v-for="s in statusOptions" :key="s" :label="planStatusMeta(s).label" :value="s" />
        </el-select>

        <el-select v-model="query.sourceType" placeholder="全部来源" clearable class="filter-select" @change="handleSearch">
          <el-option v-for="t in sourceOptions" :key="t" :label="sourceTypeMeta(t).label" :value="t" />
        </el-select>

        <el-input
          v-model="query.keyword"
          placeholder="搜索计划名称、目的或编号"
          clearable
          class="filter-keyword"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">新建采集计划</el-button>
      </div>

      <RegionState v-if="loading && !plans.length" state="loading" variant="list" />

      <RegionState
        v-else-if="errorMessage"
        state="error"
        variant="section"
        description="加载采集计划失败"
        :sub-title="errorMessage"
        :error-message="errorMessage"
        @retry="loadPlans"
      />

      <RegionState
        v-else-if="!plans.length"
        state="empty"
        variant="card"
        :description="hasFilter ? '没有符合筛选条件的采集计划' : '暂无采集计划，先创建计划并置为就绪'"
      >
        <el-button v-if="hasFilter" @click="resetFilter">清除筛选条件</el-button>
        <el-button v-else-if="canManage" type="primary" @click="openCreate">新建采集计划</el-button>
      </RegionState>

      <div v-else class="plan-grid">
        <article v-for="plan in plans" :key="plan.id" class="plan-card">
          <div class="plan-card__head">
            <div class="plan-card__title-wrap">
              <h3 class="plan-card__title">{{ plan.name }}</h3>
              <span class="plan-card__code">{{ plan.code }}</span>
            </div>
            <el-tag :type="planStatusMeta(plan.status).tagType" size="small" effect="light">
              {{ planStatusMeta(plan.status).label }}
            </el-tag>
          </div>

          <p class="plan-card__purpose">{{ plan.purpose }}</p>

          <div class="plan-card__tags">
            <el-tag :type="sourceTypeMeta(plan.sourceType).tagType" size="small" effect="plain">
              {{ sourceTypeMeta(plan.sourceType).label }}
            </el-tag>
            <el-tag size="small" effect="plain">负责人：{{ plan.ownerName || '--' }}</el-tag>
            <el-tag v-if="plan.linkedExperimentRecordCode" size="small" type="warning" effect="plain">
              记录 {{ plan.linkedExperimentRecordCode }}
            </el-tag>
          </div>

          <dl class="plan-card__meta">
            <div>
              <dt>指标</dt>
              <dd>{{ plan.metricDefinitions?.length || 0 }} 个</dd>
            </div>
            <div>
              <dt>批次</dt>
              <dd>{{ plan.batchCount ?? 0 }} 个</dd>
            </div>
            <div>
              <dt>最近批次</dt>
              <dd>
                <template v-if="plan.latestBatch">
                  <el-tag :type="batchStatusMeta(plan.latestBatch.status).tagType" size="small" effect="plain">
                    {{ batchStatusMeta(plan.latestBatch.status).label }}
                  </el-tag>
                </template>
                <span v-else class="muted">暂无</span>
              </dd>
            </div>
            <div>
              <dt>更新</dt>
              <dd>{{ formatDateTime(plan.updateTime) }}</dd>
            </div>
          </dl>

          <div class="plan-card__footer">
            <el-button size="small" @click="goPlan(plan.id)">查看计划</el-button>
            <el-button v-if="canEditPlan(plan)" size="small" @click="openEdit(plan)">编辑</el-button>
            <el-button
              v-if="canCreateBatch(plan)"
              size="small"
              type="primary"
              plain
              :loading="batchLoading === plan.id"
              @click="handleCreateBatch(plan)"
            >
              新建批次
            </el-button>
          </div>
        </article>
      </div>

      <div v-if="total > 0" class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[6, 9, 18]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSearch"
          @current-change="loadPlans"
        />
      </div>
    </template>

    <CollectionPlanDialog
      v-model="dialogVisible"
      :mode="dialogMode"
      :plan="editingPlan"
      :members="members"
      :submitting="submitting"
      @submit="handleSubmit"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import {
  listCollectionPlans,
  createCollectionPlan,
  updateCollectionPlan,
  createCollectionBatch,
  canAccessCollection,
  canManageCollection,
  canOperateCollectionBatch,
  planStatusMeta,
  batchStatusMeta,
  sourceTypeMeta,
  COLLECTION_PLAN_STATUS,
  COLLECTION_SOURCE_TYPE
} from '@/api/collection'
import { getProjectDetail } from '@/api/projects'
import {
  notifyApiError,
  normalizeApiError,
  errorTraceId,
  promptVersionConflict,
  isVersionConflict,
  isCollectionNotFound,
  isCollectionDenied,
  isBatchInvalidTransition,
  isIdempotencyKeyReused
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'
import RegionState from '@/components/RegionState.vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import CollectionPlanDialog from '@/components/collection/CollectionPlanDialog.vue'

const props = defineProps({
  project: { type: Object, default: null },
  embedded: { type: Boolean, default: false }
})

const route = useRoute()
const router = useRouter()

const statusOptions = Object.values(COLLECTION_PLAN_STATUS)
const sourceOptions = [COLLECTION_SOURCE_TYPE.FILE, COLLECTION_SOURCE_TYPE.MANUAL]

const loading = ref(false)
const errorMessage = ref('')
const permissionDenied = ref(false)
const permissionMessage = ref('项目不存在，或当前账号不在项目可见范围内')

const project = ref(props.project)
const plans = ref([])
const total = ref(0)
const query = ref({ status: '', sourceType: '', keyword: '', page: 1, pageSize: 6 })

const dialogVisible = ref(false)
const dialogMode = ref('create')
const editingPlan = ref(null)
const submitting = ref(false)
const batchLoading = ref(null)

const projectId = computed(() => Number(props.project?.id || route.params.id || project.value?.id))
const projectRole = computed(() => project.value?.myRole || null)
const members = computed(() => project.value?.members || [])
const canAccess = computed(() => canAccessCollection())
const canManage = computed(() => canManageCollection(projectId.value, projectRole.value))
const hasFilter = computed(() => Boolean(query.value.status || query.value.sourceType || query.value.keyword))

function canEditPlan(plan) {
  return canManage.value && plan.status === COLLECTION_PLAN_STATUS.DRAFT
}

function canCreateBatch(plan) {
  return plan.status === COLLECTION_PLAN_STATUS.READY && canOperateCollectionBatch(plan, projectRole.value)
}

async function loadProject() {
  if (project.value) return
  try {
    project.value = await getProjectDetail(projectId.value)
  } catch (err) {
    permissionDenied.value = true
    permissionMessage.value = describeError(err)
    project.value = null
  }
}

async function loadPlans() {
  if (!projectId.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await listCollectionPlans(projectId.value, {
      status: query.value.status || undefined,
      sourceType: query.value.sourceType || undefined,
      keyword: query.value.keyword || undefined,
      page: query.value.page,
      pageSize: query.value.pageSize
    })
    plans.value = res?.list || []
    total.value = res?.total ?? 0
  } catch (err) {
    if (isCollectionNotFound(err)) {
      permissionDenied.value = true
      permissionMessage.value = describeError(err)
    } else {
      errorMessage.value = describeError(err)
    }
    plans.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function describeError(err) {
  const { msg } = normalizeApiError(err)
  const traceId = errorTraceId(err)
  return traceId ? `${msg}（traceId: ${traceId}）` : msg
}

function handleSearch() {
  query.value.page = 1
  loadPlans()
}

function resetFilter() {
  query.value = { status: '', sourceType: '', keyword: '', page: 1, pageSize: query.value.pageSize }
  loadPlans()
}

function goPlan(planId) {
  router.push(`/collection/plans/${planId}`)
}

function openCreate() {
  dialogMode.value = 'create'
  editingPlan.value = null
  dialogVisible.value = true
}

function openEdit(plan) {
  dialogMode.value = 'edit'
  editingPlan.value = plan
  dialogVisible.value = true
}

function handleActionError(err) {
  if (isVersionConflict(err)) {
    promptVersionConflict(loadPlans)
    return
  }
  if (isCollectionDenied(err)) {
    ElMessage.warning('当前账号无权执行该操作')
    return
  }
  if (isBatchInvalidTransition(err)) {
    ElMessage.warning('当前状态不允许创建批次，请刷新后重试')
    loadPlans()
    return
  }
  if (isIdempotencyKeyReused(err)) {
    ElMessage.warning('相同幂等键已用于其他请求，请重新发起操作')
    return
  }
  notifyApiError(err)
}

async function handleSubmit(payload) {
  submitting.value = true
  try {
    if (dialogMode.value === 'edit' && editingPlan.value) {
      await updateCollectionPlan(editingPlan.value.id, payload)
      ElMessage.success('采集计划已保存')
    } else {
      await createCollectionPlan(projectId.value, payload)
      ElMessage.success('采集计划已创建，置为就绪后可创建批次')
    }
    dialogVisible.value = false
    await loadPlans()
  } catch (err) {
    handleActionError(err)
  } finally {
    submitting.value = false
  }
}

async function handleCreateBatch(plan) {
  try {
    await ElMessageBox.confirm(
      `将基于计划「${plan.name}」创建 ${sourceTypeMeta(plan.sourceType).label} 批次。批次完成后不可修改。`,
      '创建采集批次',
      { confirmButtonText: '创建批次', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }
  batchLoading.value = plan.id
  try {
    const res = await createCollectionBatch(
      plan.id,
      { planVersion: plan.version, sourceType: plan.sourceType },
      createIdempotencyKey('w43-batch-create')
    )
    ElMessage.success(`已创建批次 ${res.batchCode}`)
    router.push(`/collection/batches/${res.id}`)
  } catch (err) {
    handleActionError(err)
  } finally {
    batchLoading.value = null
  }
}

onMounted(async () => {
  if (!canAccess.value) return
  await loadProject()
  if (permissionDenied.value) return
  await loadPlans()
})
</script>

<style scoped>
.collection-plan-list {
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
  color: var(--usn-blue-600);
  border-color: var(--usn-blue-600);
  background: #eff6ff;
  font-weight: 600;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.filter-select {
  width: 150px;
}

.filter-keyword {
  flex: 1 1 220px;
}

.plan-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--usn-space-4);
}

.plan-card {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
  padding: var(--usn-space-4);
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-top: 3px solid var(--usn-blue-600);
  border-radius: var(--usn-radius-md);
  box-shadow: var(--usn-shadow-panel);
}

.plan-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-2);
}

.plan-card__title-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.plan-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--usn-ink-900);
  line-height: 1.3;
}

.plan-card__code {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.plan-card__purpose {
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

.plan-card__tags {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.plan-card__meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--usn-space-2);
  margin: 0;
  padding: var(--usn-space-2) 0;
  border-top: 1px solid var(--usn-line);
}

.plan-card__meta div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.plan-card__meta dt {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.plan-card__meta dd {
  margin: 0;
  font-size: var(--usn-font-size-body);
  font-weight: 600;
  color: var(--usn-ink-900);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.plan-card__footer {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  margin-top: auto;
  flex-wrap: wrap;
}

.pagination-bar {
  display: flex;
  justify-content: center;
  padding-top: var(--usn-space-2);
}

@media (max-width: 1365px) {
  .plan-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 767px) {
  .plan-grid {
    grid-template-columns: 1fr;
  }

  .filter-select {
    width: 100%;
  }

  .plan-card__footer :deep(.el-button) {
    flex: 1;
  }
}
</style>
