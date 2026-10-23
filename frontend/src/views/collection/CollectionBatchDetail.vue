<template>
  <div class="batch-detail-page">
    <header class="page-header">
      <div class="header-main">
        <el-button text :icon="ArrowLeft" @click="goBack">返回采集计划</el-button>
        <div>
          <h1 class="page-title">{{ batch?.batchCode || '采集批次' }}</h1>
          <div class="title-meta">
            <el-tag v-if="batch" :type="batchStatusMeta(batch.status).tagType" size="small" effect="light">
              {{ batchStatusMeta(batch.status).label }}
            </el-tag>
            <el-tag v-if="batch" :type="sourceTypeMeta(batch.sourceType).tagType" size="small" effect="plain">
              {{ sourceTypeMeta(batch.sourceType).label }}
            </el-tag>
            <el-link v-if="batch?.plan" type="primary" @click="goPlan">{{ batch.plan.name }}</el-link>
            <span class="muted text-help">操作者：{{ batch?.operatorName || '--' }}</span>
            <span class="muted text-help">v{{ batch?.version ?? '--' }}</span>
          </div>
        </div>
      </div>

      <div v-if="batch" class="header-actions">
        <template v-if="batch.sourceType === 'FILE'">
          <input ref="fileInput" type="file" accept=".csv,text/csv" class="hidden-input" @change="handleFileChange" />
          <el-button v-if="canUpload" type="primary" :icon="Upload" :loading="actionLoading === 'upload'" @click="pickFile">
            上传 CSV
          </el-button>
        </template>
        <el-button v-else-if="canAddManual" type="primary" :icon="Plus" @click="openManualCreate">
          新增人工观测
        </el-button>
        <el-button v-if="canComplete" type="success" :loading="actionLoading === 'complete'" @click="handleComplete">
          完成批次
        </el-button>
        <el-button
          v-if="canSnapshot"
          type="primary"
          plain
          :loading="actionLoading === 'snapshot'"
          @click="handleSnapshot"
        >
          生成快照
        </el-button>
        <el-button v-if="canArchive" :loading="actionLoading === 'archive'" @click="handleArchive">归档批次</el-button>
        <el-button v-if="canCancel" type="danger" plain :loading="actionLoading === 'cancel'" @click="handleCancel">
          取消批次
        </el-button>
      </div>
    </header>

    <MockStateBanner />

    <RegionState v-if="loading" state="loading" variant="section" />

    <RegionState
      v-else-if="permissionDenied"
      state="permission"
      variant="section"
      description="采集批次不存在或无权查看"
      :sub-title="permissionMessage"
    />

    <RegionState
      v-else-if="errorMessage"
      state="error"
      variant="section"
      description="加载采集批次失败"
      :sub-title="errorMessage"
      :error-message="errorMessage"
      @retry="loadPage"
    />

    <template v-else-if="batch">
      <el-alert
        v-if="immutable"
        type="info"
        :closable="false"
        show-icon
        title="批次已完成或已归档，数据只读"
      >
        原件、映射、人工观测、计数、时间范围与 checksum 已冻结，不因后续操作而覆盖。
      </el-alert>

      <el-alert
        v-else-if="batch.status === 'FAILED'"
        type="error"
        :closable="false"
        show-icon
        :title="batch.completionReason || '批次处理失败'"
      >
        原件与失败审计已保留；如需重新采集，请创建新批次。
      </el-alert>

      <div class="detail-grid">
        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <el-icon><Document /></el-icon>
              <span>批次信息</span>
            </div>
          </template>
          <dl class="info-rows">
            <div class="info-row">
              <dt>采集计划</dt>
              <dd>{{ batch.plan?.name || '--' }}</dd>
            </div>
            <div class="info-row">
              <dt>项目</dt>
              <dd>{{ batch.projectName || '--' }}</dd>
            </div>
            <div class="info-row">
              <dt>开始 / 完成</dt>
              <dd>{{ formatDateTime(batch.startedAt) }} / {{ formatDateTime(batch.completedAt) }}</dd>
            </div>
            <div class="info-row">
              <dt>观测时间范围</dt>
              <dd>{{ formatDateTime(batch.observedFrom) }} ~ {{ formatDateTime(batch.observedTo) }}</dd>
            </div>
            <div class="info-row">
              <dt>完成说明</dt>
              <dd>{{ batch.completionReason || '--' }}</dd>
            </div>
          </dl>
        </el-card>

        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <el-icon><DataAnalysis /></el-icon>
              <span>数据摘要</span>
            </div>
          </template>
          <div class="stat-grid">
            <div class="stat-item">
              <span class="stat-value">{{ batch.recordCount }}</span>
              <span class="stat-label">有效记录</span>
            </div>
            <div class="stat-item">
              <span class="stat-value stat-value--danger">{{ batch.rejectedCount }}</span>
              <span class="stat-label">拒绝行</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ batch.imports?.length || 0 }}</span>
              <span class="stat-label">导入修订</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ batch.manualRecords?.length || 0 }}</span>
              <span class="stat-label">人工观测</span>
            </div>
          </div>
          <div class="checksum">
            <span class="muted text-help">checksum</span>
            <code>{{ shortHash(batch.checksum) }}</code>
          </div>
        </el-card>
      </div>

      <el-card v-if="batch.snapshot" class="detail-card">
        <template #header>
          <div class="card-header">
            <el-icon><Collection /></el-icon>
            <span>采集快照</span>
            <el-tag size="small" type="success" effect="light">FULL_BATCH</el-tag>
          </div>
        </template>
        <dl class="info-rows">
          <div class="info-row">
            <dt>快照编号</dt>
            <dd>{{ batch.snapshot.snapshotCode }}</dd>
          </div>
          <div class="info-row">
            <dt>记录 / 拒绝</dt>
            <dd>{{ batch.snapshot.recordCount }} / {{ batch.snapshot.rejectedCount }}</dd>
          </div>
          <div class="info-row">
            <dt>观测范围</dt>
            <dd>{{ formatDateTime(batch.snapshot.observedFrom) }} ~ {{ formatDateTime(batch.snapshot.observedTo) }}</dd>
          </div>
          <div class="info-row">
            <dt>批次 checksum</dt>
            <dd><code>{{ shortHash(batch.snapshot.batchChecksum) }}</code></dd>
          </div>
          <div class="info-row">
            <dt>映射摘要</dt>
            <dd><code>{{ shortHash(batch.snapshot.mappingDigest) }}</code></dd>
          </div>
          <div class="info-row">
            <dt>生成时间</dt>
            <dd>{{ formatDateTime(batch.snapshot.createTime) }}</dd>
          </div>
        </dl>
      </el-card>

      <el-card v-if="batch.sourceType === 'FILE'" class="detail-card">
        <template #header>
          <div class="card-header">
            <el-icon><UploadFilled /></el-icon>
            <span>CSV 导入</span>
            <el-tag size="small" type="info">{{ batch.imports?.length || 0 }} 个修订</el-tag>
          </div>
        </template>

        <RegionState
          v-if="!batch.imports?.length"
          state="empty"
          variant="card"
          description="尚未上传 CSV 原件"
        >
          <el-button v-if="canUpload" type="primary" @click="pickFile">上传 CSV</el-button>
        </RegionState>

        <el-table v-else :data="batch.imports" size="small">
          <el-table-column label="原件" min-width="180">
            <template #default="{ row }">
              <div class="file-cell">
                <span>{{ row.originalFile.fileName }}</span>
                <span class="muted text-help">{{ formatBytes(row.originalFile.size) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="importStatusMeta(row.status).tagType" size="small" effect="light">
                {{ importStatusMeta(row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="记录 / 拒绝" width="120">
            <template #default="{ row }">{{ row.recordCount }} / {{ row.rejectedCount }}</template>
          </el-table-column>
          <el-table-column label="导入版本" width="100">
            <template #default="{ row }">v{{ row.importVersion }}</template>
          </el-table-column>
          <el-table-column label="上传时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.originalFile.uploadTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="goImport(row.id)">
                {{ row.status === 'CONFIRMED' ? '查看结果' : row.status === 'FAILED' ? '查看失败' : '继续导入' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card v-if="batch.sourceType === 'MANUAL'" class="detail-card">
        <template #header>
          <div class="card-header">
            <el-icon><EditPen /></el-icon>
            <span>人工观测</span>
            <el-tag size="small" type="info">{{ batch.manualRecords?.length || 0 }} 条</el-tag>
            <span class="muted text-help">更正只追加修订，不覆盖历史</span>
          </div>
        </template>

        <RegionState
          v-if="!batch.manualRecords?.length"
          state="empty"
          variant="card"
          description="暂无人工观测，逐条录入观测值"
        >
          <el-button v-if="canAddManual" type="primary" @click="openManualCreate">新增人工观测</el-button>
        </RegionState>

        <el-table v-else :data="batch.manualRecords" size="small">
          <el-table-column label="指标" prop="metricCode" width="120" />
          <el-table-column label="观测值" width="130">
            <template #default="{ row }">{{ row.value }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="观测时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.observedAt) }}</template>
          </el-table-column>
          <el-table-column label="方法 / 仪器" min-width="180">
            <template #default="{ row }">
              <div class="file-cell">
                <span>{{ row.method }}</span>
                <span class="muted text-help">{{ row.instrument }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="修订" width="80">
            <template #default="{ row }">第 {{ row.revisionNo }} 版</template>
          </el-table-column>
          <el-table-column label="记录人" prop="recordedByName" width="100" />
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openRevisions(row)">修改历史</el-button>
              <el-button v-if="canAddManual" size="small" type="primary" plain @click="openManualEdit(row)">
                更正
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <div class="detail-grid">
        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <el-icon><Grid /></el-icon>
              <span>记录摘要（前 10 条）</span>
            </div>
          </template>
          <el-table :data="batch.recordSamples || []" size="small" empty-text="暂无采集记录">
            <el-table-column label="观测时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.observedAt) }}</template>
            </el-table-column>
            <el-table-column label="指标" prop="metricCode" width="110" />
            <el-table-column label="值" width="110">
              <template #default="{ row }">{{ row.value ?? '--' }} {{ row.unit }}</template>
            </el-table-column>
            <el-table-column label="质量" width="90">
              <template #default="{ row }">
                <el-tag :type="row.qualityStatus === 'REJECTED' ? 'danger' : 'success'" size="small" effect="plain">
                  {{ row.qualityStatus === 'REJECTED' ? '拒绝' : '有效' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="来源行" width="80">
              <template #default="{ row }">{{ row.rawRowNo ?? '--' }}</template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <el-icon><Warning /></el-icon>
              <span>错误样本</span>
            </div>
          </template>
          <el-table :data="batch.errorSamples || []" size="small" empty-text="无拒绝行">
            <el-table-column label="行号" prop="rawRowNo" width="80" />
            <el-table-column label="指标" prop="metricCode" width="110" />
            <el-table-column label="原因" width="120">
              <template #default="{ row }">
                <el-tag type="danger" size="small" effect="plain">{{ row.reason }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="说明" prop="message" min-width="160" />
          </el-table>
        </el-card>
      </div>
    </template>

    <ManualRecordDialog
      v-model="manualDialogVisible"
      :mode="manualMode"
      :plan="batch?.plan"
      :record="editingManual"
      :submitting="actionLoading === 'manual'"
      @submit="handleManualSubmit"
    />

    <el-dialog v-model="revisionDialogVisible" title="人工观测修改历史" width="720px">
      <el-table :data="manualRevisions" size="small" empty-text="暂无修订">
        <el-table-column label="版本" width="80">
          <template #default="{ row }">第 {{ row.revisionNo }} 版</template>
        </el-table-column>
        <el-table-column label="旧值" width="110">
          <template #default="{ row }">{{ row.oldValue ?? '--' }}</template>
        </el-table-column>
        <el-table-column label="新值" width="110">
          <template #default="{ row }">{{ row.newValue }}</template>
        </el-table-column>
        <el-table-column label="更正原因" prop="changeReason" min-width="160" />
        <el-table-column label="操作者" prop="operatorName" width="100" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Upload,
  Plus,
  Document,
  DataAnalysis,
  Collection,
  UploadFilled,
  EditPen,
  Grid,
  Warning
} from '@element-plus/icons-vue'
import {
  getCollectionBatch,
  completeCollectionBatch,
  cancelCollectionBatch,
  archiveCollectionBatch,
  createCollectionSnapshot,
  uploadFileImport,
  createManualRecord,
  updateManualRecord,
  listManualRevisions,
  canOperateCollectionBatch,
  batchStatusMeta,
  importStatusMeta,
  sourceTypeMeta,
  isBatchImmutableStatus,
  COLLECTION_BATCH_STATUS,
  COLLECTION_SOURCE_TYPE,
  SNAPSHOT_TYPE
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
  isBatchImmutable,
  isBatchNotCompleted,
  isSnapshotAlreadyBound,
  isImportAlreadyConfirmed,
  isFileUnsupported,
  isImportMappingInvalid,
  isManualRecordInvalid,
  isIdempotencyKeyReused
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'
import RegionState from '@/components/RegionState.vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import ManualRecordDialog from '@/components/collection/ManualRecordDialog.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const errorMessage = ref('')
const permissionDenied = ref(false)
const permissionMessage = ref('该批次不存在，或当前账号不在项目可见范围内')

const batch = ref(null)
const project = ref(null)
const actionLoading = ref('')

const fileInput = ref(null)
const manualDialogVisible = ref(false)
const manualMode = ref('create')
const editingManual = ref(null)
const revisionDialogVisible = ref(false)
const manualRevisions = ref([])

const projectRole = computed(() => project.value?.myRole || null)
const immutable = computed(() => isBatchImmutableStatus(batch.value?.status))
const canOperate = computed(() => canOperateCollectionBatch(batch.value?.plan, projectRole.value))

const canUpload = computed(
  () => canOperate.value && !immutable.value && batch.value?.sourceType === COLLECTION_SOURCE_TYPE.FILE
)
const canAddManual = computed(
  () => canOperate.value && !immutable.value && batch.value?.sourceType === COLLECTION_SOURCE_TYPE.MANUAL
)
const canComplete = computed(
  () => canOperate.value && batch.value?.status === COLLECTION_BATCH_STATUS.REVIEW_REQUIRED
)
const canSnapshot = computed(
  () => canOperate.value && batch.value?.status === COLLECTION_BATCH_STATUS.COMPLETED && !batch.value?.snapshot
)
const canArchive = computed(
  () => canOperate.value && batch.value?.status === COLLECTION_BATCH_STATUS.COMPLETED
)
const canCancel = computed(
  () =>
    canOperate.value &&
    [
      COLLECTION_BATCH_STATUS.CREATED,
      COLLECTION_BATCH_STATUS.PROCESSING,
      COLLECTION_BATCH_STATUS.REVIEW_REQUIRED,
      COLLECTION_BATCH_STATUS.FAILED
    ].includes(batch.value?.status)
)

function describeError(err) {
  const { msg } = normalizeApiError(err)
  const traceId = errorTraceId(err)
  return traceId ? `${msg}（traceId: ${traceId}）` : msg
}

function shortHash(value) {
  if (!value) return '--'
  return `${value.slice(0, 12)}…${value.slice(-8)}`
}

function formatBytes(bytes) {
  const value = Number(bytes || 0)
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function goBack() {
  if (batch.value?.planId) router.push(`/collection/plans/${batch.value.planId}`)
  else router.push('/projects')
}

function goPlan() {
  if (batch.value?.planId) router.push(`/collection/plans/${batch.value.planId}`)
}

function goImport(importId) {
  router.push(`/collection/imports/${importId}`)
}

async function loadPage() {
  loading.value = true
  errorMessage.value = ''
  permissionDenied.value = false
  try {
    batch.value = await getCollectionBatch(route.params.id)
    project.value = await getProjectDetail(batch.value.projectId).catch(() => null)
  } catch (err) {
    if (isCollectionNotFound(err) || isCollectionDenied(err)) {
      permissionDenied.value = true
      permissionMessage.value = describeError(err)
    } else {
      errorMessage.value = describeError(err)
    }
    batch.value = null
  } finally {
    loading.value = false
  }
}

function handleActionError(err) {
  if (isVersionConflict(err)) {
    promptVersionConflict(loadPage)
    return
  }
  if (isBatchImmutable(err)) {
    ElMessage.warning('批次已完成或已归档，数据不可修改')
    loadPage()
    return
  }
  if (isBatchNotCompleted(err)) {
    ElMessage.warning('批次尚未完成，不能生成快照')
    loadPage()
    return
  }
  if (isBatchInvalidTransition(err)) {
    ElMessage.warning('当前批次状态不允许该动作，请刷新后重试')
    loadPage()
    return
  }
  if (isSnapshotAlreadyBound(err)) {
    ElMessage.warning('该批次已生成过快照，不能重复生成')
    loadPage()
    return
  }
  if (isImportAlreadyConfirmed(err)) {
    ElMessage.warning('该导入修订已确认，不能重复确认')
    return
  }
  if (isFileUnsupported(err) || isImportMappingInvalid(err)) {
    ElMessage.error(describeError(err))
    return
  }
  if (isManualRecordInvalid(err)) {
    ElMessage.error(describeError(err))
    return
  }
  if (isCollectionDenied(err)) {
    ElMessage.warning('当前账号无权执行该操作')
    return
  }
  if (isIdempotencyKeyReused(err)) {
    ElMessage.warning('相同幂等键已用于其他请求，请重新发起操作')
    return
  }
  notifyApiError(err)
}

function pickFile() {
  fileInput.value?.click()
}

async function handleFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  actionLoading.value = 'upload'
  try {
    const res = await uploadFileImport(batch.value.id, file, { batchVersion: batch.value.version })
    ElMessage.success(res.reusedExisting ? '检测到相同文件哈希，已复用既有导入修订' : '原件已上传，继续完成映射')
    router.push(`/collection/imports/${res.id}`)
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function handleComplete() {
  let completionReason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      '完成后原件、计数、时间范围与 checksum 将冻结，不能再修改。',
      '完成采集批次',
      {
        confirmButtonText: '完成批次',
        cancelButtonText: '取消',
        inputPlaceholder: '填写完成说明',
        inputValue:
          batch.value.sourceType === COLLECTION_SOURCE_TYPE.FILE
            ? 'CSV 导入与映射已人工确认'
            : '人工观测已复核'
      }
    )
    completionReason = value || ''
  } catch {
    return
  }
  actionLoading.value = 'complete'
  try {
    const res = await completeCollectionBatch(
      batch.value.id,
      { version: batch.value.version, completionReason },
      createIdempotencyKey('w43-batch-complete')
    )
    ElMessage.success(`批次已完成，共 ${res.recordCount} 条有效记录`)
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function handleSnapshot() {
  try {
    await ElMessageBox.confirm(
      '将按当前批次生成 FULL_BATCH 快照，快照用于记录绑定与复现，生成后不可修改。',
      '生成采集快照',
      { confirmButtonText: '生成快照', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }
  actionLoading.value = 'snapshot'
  try {
    const res = await createCollectionSnapshot(
      batch.value.id,
      { batchVersion: batch.value.version, snapshotType: SNAPSHOT_TYPE.FULL_BATCH },
      createIdempotencyKey('w43-snapshot-create')
    )
    ElMessage.success(`快照 ${res.snapshotCode} 已生成`)
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function handleArchive() {
  try {
    await ElMessageBox.confirm('归档后批次只读，数据与快照继续保留。', '归档采集批次', {
      confirmButtonText: '归档',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  actionLoading.value = 'archive'
  try {
    await archiveCollectionBatch(batch.value.id, { version: batch.value.version })
    ElMessage.success('批次已归档')
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
    const { value } = await ElMessageBox.prompt('取消后该批次不再执行采集，已上传原件与失败审计保留。', '取消采集批次', {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      inputPlaceholder: '填写取消原因',
      inputValue: '采集条件变化，取消本批次'
    })
    reason = value || ''
  } catch {
    return
  }
  actionLoading.value = 'cancel'
  try {
    await cancelCollectionBatch(batch.value.id, { version: batch.value.version, reason })
    ElMessage.success('批次已取消')
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

function openManualCreate() {
  manualMode.value = 'create'
  editingManual.value = null
  manualDialogVisible.value = true
}

function openManualEdit(record) {
  manualMode.value = 'edit'
  editingManual.value = record
  manualDialogVisible.value = true
}

async function handleManualSubmit(payload) {
  actionLoading.value = 'manual'
  try {
    if (manualMode.value === 'edit' && editingManual.value) {
      const res = await updateManualRecord(editingManual.value.id, payload)
      ElMessage.success(`已追加第 ${res.revisionNo} 版修订，历史值保留`)
    } else {
      await createManualRecord(batch.value.id, payload)
      ElMessage.success('人工观测已录入')
    }
    manualDialogVisible.value = false
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function openRevisions(record) {
  revisionDialogVisible.value = true
  manualRevisions.value = []
  try {
    const res = await listManualRevisions(record.id)
    manualRevisions.value = res?.list || []
  } catch (err) {
    notifyApiError(err)
  }
}

onMounted(loadPage)
</script>

<style scoped>
.batch-detail-page {
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
  font-family: var(--usn-font-mono, monospace);
}

.title-meta {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
  margin-top: 4px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.hidden-input {
  display: none;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
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
  grid-template-columns: 110px minmax(0, 1fr);
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

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--usn-space-3);
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--usn-space-3);
  background: var(--usn-surface-soft, #f7f9fb);
  border-radius: var(--usn-radius-sm);
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--usn-ink-900);
}

.stat-value--danger {
  color: #d14343;
}

.stat-label {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.checksum {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  margin-top: var(--usn-space-3);
  padding-top: var(--usn-space-3);
  border-top: 1px solid var(--usn-line);
  flex-wrap: wrap;
}

.checksum code {
  font-family: var(--usn-font-mono, monospace);
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-700);
}

.file-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
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

  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .info-row {
    grid-template-columns: 1fr;
  }
}
</style>
