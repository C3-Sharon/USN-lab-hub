<template>
  <div class="plan-detail-page">
    <header class="page-header">
      <div class="header-main">
        <el-button text :icon="ArrowLeft" @click="goBack">返回数据采集</el-button>
        <div>
          <h1 class="page-title">{{ plan?.name || '采集计划' }}</h1>
          <div class="title-meta">
            <span class="code">{{ plan?.code || '--' }}</span>
            <el-tag v-if="plan" :type="planStatusMeta(plan.status).tagType" size="small" effect="light">
              {{ planStatusMeta(plan.status).label }}
            </el-tag>
            <el-tag v-if="plan" :type="sourceTypeMeta(plan.sourceType).tagType" size="small" effect="plain">
              {{ sourceTypeMeta(plan.sourceType).label }}
            </el-tag>
            <span class="muted text-help">负责人：{{ plan?.ownerName || '--' }}</span>
            <span class="muted text-help">v{{ plan?.version ?? '--' }}</span>
          </div>
        </div>
      </div>

      <div v-if="plan" class="header-actions">
        <el-button v-if="canEdit" :loading="actionLoading === 'edit'" @click="openEdit">编辑计划</el-button>
        <el-button
          v-if="canReady"
          type="primary"
          :loading="actionLoading === 'ready'"
          @click="handleReady"
        >
          置为就绪
        </el-button>
        <el-button
          v-if="canCreateBatch"
          type="primary"
          plain
          :loading="actionLoading === 'batch'"
          @click="handleCreateBatch"
        >
          新建批次
        </el-button>
        <el-button v-if="canArchive" :loading="actionLoading === 'archive'" @click="handleArchive">归档</el-button>
        <el-button v-if="canCancel" type="danger" plain :loading="actionLoading === 'cancel'" @click="handleCancel">
          取消计划
        </el-button>
      </div>
    </header>

    <MockStateBanner />

    <RegionState v-if="loading" state="loading" variant="section" />

    <RegionState
      v-else-if="permissionDenied"
      state="permission"
      variant="section"
      description="采集计划不存在或无权查看"
      :sub-title="permissionMessage"
    />

    <RegionState
      v-else-if="errorMessage"
      state="error"
      variant="section"
      description="加载采集计划失败"
      :sub-title="errorMessage"
      :error-message="errorMessage"
      @retry="loadPage"
    />

    <template v-else-if="plan">
      <el-alert
        v-if="immutable"
        type="info"
        :closable="false"
        show-icon
        title="该计划已归档或取消，只读"
      >
        已归档计划仍保留批次、快照与失败审计，不删除证据，也不能再创建批次。
      </el-alert>

      <div class="detail-grid">
        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <el-icon><Document /></el-icon>
              <span>计划信息</span>
            </div>
          </template>
          <dl class="info-rows">
            <div class="info-row">
              <dt>采集目的</dt>
              <dd>{{ plan.purpose }}</dd>
            </div>
            <div class="info-row">
              <dt>采集来源</dt>
              <dd>{{ sourceTypeMeta(plan.sourceType).label }} · {{ sourceTypeMeta(plan.sourceType).desc }}</dd>
            </div>
            <div class="info-row">
              <dt>关联实验记录</dt>
              <dd>
                <el-link v-if="plan.linkedExperimentRecordId" type="primary" @click="goRecord">
                  {{ plan.linkedExperimentRecordCode || `记录 ${plan.linkedExperimentRecordId}` }}
                </el-link>
                <span v-else class="muted">未关联</span>
              </dd>
            </div>
            <div class="info-row">
              <dt>保留说明</dt>
              <dd>{{ plan.retentionNote || '--' }}</dd>
            </div>
            <div class="info-row">
              <dt>创建 / 更新</dt>
              <dd>{{ formatDateTime(plan.createTime) }} / {{ formatDateTime(plan.updateTime) }}</dd>
            </div>
          </dl>
        </el-card>

        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <el-icon><Odometer /></el-icon>
              <span>指标定义</span>
              <el-tag size="small" type="info">{{ plan.metricDefinitions?.length || 0 }} 个</el-tag>
            </div>
          </template>
          <el-table :data="plan.metricDefinitions || []" size="small" empty-text="暂无指标定义">
            <el-table-column label="编码" prop="code" min-width="120" />
            <el-table-column label="名称" prop="name" min-width="120" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ valueTypeMeta(row.valueType).label }}</template>
            </el-table-column>
            <el-table-column label="单位" prop="unit" width="80" />
            <el-table-column label="必填" width="70">
              <template #default="{ row }">
                <el-tag :type="row.required ? 'danger' : 'info'" size="small" effect="plain">
                  {{ row.required ? '必填' : '可选' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>

      <el-card class="detail-card">
        <template #header>
          <div class="card-header">
            <el-icon><Files /></el-icon>
            <span>采集批次</span>
            <el-tag size="small" type="info">{{ batchTotal }} 个</el-tag>
            <span class="muted text-help">批次完成后原件、计数、时间范围和 checksum 不可修改</span>
          </div>
        </template>

        <RegionState v-if="batchLoading" state="loading" variant="table" />

        <RegionState
          v-else-if="batchError"
          state="error"
          variant="section"
          description="加载采集批次失败"
          :sub-title="batchError"
          :error-message="batchError"
          @retry="loadBatches"
        />

        <RegionState
          v-else-if="!batches.length"
          state="empty"
          variant="card"
          description="暂无采集批次，计划就绪后创建第一个批次"
        >
          <el-button v-if="canCreateBatch" type="primary" @click="handleCreateBatch">新建批次</el-button>
        </RegionState>

        <template v-else>
          <el-table :data="batches" size="small" class="batch-table" @row-click="goBatch">
            <el-table-column label="批次" min-width="180">
              <template #default="{ row }">
                <div class="batch-code">
                  <span>{{ row.batchCode }}</span>
                  <span class="muted text-help">v{{ row.version }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="batchStatusMeta(row.status).tagType" size="small" effect="light">
                  {{ batchStatusMeta(row.status).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="来源" width="100">
              <template #default="{ row }">{{ sourceTypeMeta(row.sourceType).label }}</template>
            </el-table-column>
            <el-table-column label="记录 / 拒绝" width="120">
              <template #default="{ row }">{{ row.recordCount }} / {{ row.rejectedCount }}</template>
            </el-table-column>
            <el-table-column label="操作者" prop="operatorName" width="100" />
            <el-table-column label="开始时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
            </el-table-column>
            <el-table-column label="完成时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
            </el-table-column>
          </el-table>

          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="batchQuery.page"
              v-model:page-size="batchQuery.pageSize"
              :total="batchTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadBatches"
              @current-change="loadBatches"
            />
          </div>
        </template>
      </el-card>
    </template>

    <CollectionPlanDialog
      v-model="dialogVisible"
      mode="edit"
      :plan="plan"
      :members="members"
      :submitting="actionLoading === 'edit'"
      @submit="handleEditSubmit"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Document, Odometer, Files } from '@element-plus/icons-vue'
import {
  getCollectionPlan,
  updateCollectionPlan,
  readyCollectionPlan,
  archiveCollectionPlan,
  cancelCollectionPlan,
  listCollectionBatches,
  createCollectionBatch,
  canManageCollection,
  canOperateCollectionBatch,
  planStatusMeta,
  batchStatusMeta,
  sourceTypeMeta,
  valueTypeMeta,
  COLLECTION_PLAN_STATUS
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

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const errorMessage = ref('')
const permissionDenied = ref(false)
const permissionMessage = ref('该计划不存在，或当前账号不在项目可见范围内')

const plan = ref(null)
const project = ref(null)

const batches = ref([])
const batchTotal = ref(0)
const batchLoading = ref(false)
const batchError = ref('')
const batchQuery = ref({ page: 1, pageSize: 10 })

const actionLoading = ref('')
const dialogVisible = ref(false)

const members = computed(() => project.value?.members || [])
const projectRole = computed(() => project.value?.myRole || null)
const canManage = computed(() => canManageCollection(plan.value?.projectId, projectRole.value))
const immutable = computed(
  () =>
    plan.value &&
    [COLLECTION_PLAN_STATUS.ARCHIVED, COLLECTION_PLAN_STATUS.CANCELED].includes(plan.value.status)
)

const canEdit = computed(() => canManage.value && plan.value?.status === COLLECTION_PLAN_STATUS.DRAFT)
const canReady = computed(() => canEdit.value)
const canArchive = computed(() => canManage.value && plan.value?.status === COLLECTION_PLAN_STATUS.READY)
const canCancel = computed(
  () =>
    canManage.value &&
    [COLLECTION_PLAN_STATUS.DRAFT, COLLECTION_PLAN_STATUS.READY].includes(plan.value?.status)
)
const canCreateBatch = computed(
  () =>
    plan.value?.status === COLLECTION_PLAN_STATUS.READY &&
    canOperateCollectionBatch(plan.value, projectRole.value)
)

function describeError(err) {
  const { msg } = normalizeApiError(err)
  const traceId = errorTraceId(err)
  return traceId ? `${msg}（traceId: ${traceId}）` : msg
}

function goBack() {
  if (plan.value?.projectId) router.push(`/projects/${plan.value.projectId}?tab=collection`)
  else router.push('/projects')
}

function goRecord() {
  if (plan.value?.linkedExperimentRecordId) router.push(`/experiment/records/${plan.value.linkedExperimentRecordId}`)
}

function goBatch(row) {
  router.push(`/collection/batches/${row.id}`)
}

async function loadBatches() {
  if (!plan.value) return
  batchLoading.value = true
  batchError.value = ''
  try {
    const res = await listCollectionBatches(plan.value.id, {
      page: batchQuery.value.page,
      pageSize: batchQuery.value.pageSize
    })
    batches.value = res?.list || []
    batchTotal.value = res?.total ?? 0
  } catch (err) {
    batchError.value = describeError(err)
    batches.value = []
    batchTotal.value = 0
  } finally {
    batchLoading.value = false
  }
}

async function loadPage() {
  loading.value = true
  errorMessage.value = ''
  permissionDenied.value = false
  try {
    plan.value = await getCollectionPlan(route.params.id)
    project.value = await getProjectDetail(plan.value.projectId).catch(() => null)
    await loadBatches()
  } catch (err) {
    if (isCollectionNotFound(err) || isCollectionDenied(err)) {
      permissionDenied.value = true
      permissionMessage.value = describeError(err)
    } else {
      errorMessage.value = describeError(err)
    }
    plan.value = null
  } finally {
    loading.value = false
  }
}

function handleActionError(err) {
  if (isVersionConflict(err)) {
    promptVersionConflict(loadPage)
    return
  }
  if (isCollectionDenied(err)) {
    ElMessage.warning('当前账号无权执行该操作')
    return
  }
  if (isBatchInvalidTransition(err)) {
    ElMessage.warning('当前计划或批次状态不允许该动作，请刷新后重试')
    loadPage()
    return
  }
  if (isIdempotencyKeyReused(err)) {
    ElMessage.warning('相同幂等键已用于其他请求，请重新发起操作')
    return
  }
  notifyApiError(err)
}

function openEdit() {
  dialogVisible.value = true
}

async function handleEditSubmit(payload) {
  actionLoading.value = 'edit'
  try {
    await updateCollectionPlan(plan.value.id, payload)
    dialogVisible.value = false
    ElMessage.success('采集计划已保存')
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function handleReady() {
  try {
    await ElMessageBox.confirm(
      '置为就绪后核心指标定义将锁定，后续如需变化请创建新计划。',
      '置为就绪',
      { confirmButtonText: '置为就绪', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  actionLoading.value = 'ready'
  try {
    await readyCollectionPlan(plan.value.id, { version: plan.value.version })
    ElMessage.success('计划已就绪，可创建采集批次')
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function handleArchive() {
  try {
    await ElMessageBox.confirm(
      '归档后计划只读，批次与快照证据继续保留，不能创建新批次。',
      '归档采集计划',
      { confirmButtonText: '归档', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  actionLoading.value = 'archive'
  try {
    await archiveCollectionPlan(plan.value.id, { version: plan.value.version })
    ElMessage.success('计划已归档')
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function handleCancel() {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('取消后不再执行采集，已产生批次与快照继续保留。', '取消采集计划', {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      inputPlaceholder: '填写取消原因（可选）',
      inputValue: '计划调整，取消本次采集'
    })
    reason = value || ''
  } catch {
    return
  }
  actionLoading.value = 'cancel'
  try {
    await cancelCollectionPlan(plan.value.id, { version: plan.value.version, reason })
    ElMessage.success('计划已取消')
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function handleCreateBatch() {
  try {
    await ElMessageBox.confirm(
      `将创建 ${sourceTypeMeta(plan.value.sourceType).label} 批次。批次完成后原件、计数与 checksum 不可修改。`,
      '创建采集批次',
      { confirmButtonText: '创建批次', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }
  actionLoading.value = 'batch'
  try {
    const res = await createCollectionBatch(
      plan.value.id,
      { planVersion: plan.value.version, sourceType: plan.value.sourceType },
      createIdempotencyKey('w43-batch-create')
    )
    ElMessage.success(`已创建批次 ${res.batchCode}`)
    router.push(`/collection/batches/${res.id}`)
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

onMounted(loadPage)
</script>

<style scoped>
.plan-detail-page {
  max-width: var(--usn-content-max-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-4);
  flex-wrap: wrap;
}

.header-main {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  align-items: flex-start;
}

.page-title {
  margin: 0;
  font-size: var(--usn-font-size-page-title);
  font-weight: 700;
  color: var(--usn-ink-900);
}

.title-meta {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
  margin-top: 4px;
}

.code {
  font-family: var(--usn-font-mono, monospace);
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: var(--usn-space-4);
}

.detail-card {
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  box-shadow: var(--usn-shadow-panel);
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  font-weight: 600;
  color: var(--usn-ink-900);
  flex-wrap: wrap;
}

.info-rows {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
  margin: 0;
}

.info-row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: var(--usn-space-2);
}

.info-row dt {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.info-row dd {
  margin: 0;
  color: var(--usn-ink-900);
  line-height: 1.6;
  word-break: break-word;
}

.batch-table {
  cursor: pointer;
}

.batch-code {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pagination-bar {
  display: flex;
  justify-content: center;
  padding-top: var(--usn-space-3);
}

@media (max-width: 1100px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .header-actions {
    width: 100%;
  }

  .header-actions :deep(.el-button) {
    flex: 1;
  }

  .info-row {
    grid-template-columns: 1fr;
  }
}
</style>
