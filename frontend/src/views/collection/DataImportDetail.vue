<template>
  <div class="import-detail-page">
    <header class="page-header">
      <div class="header-main">
        <el-button text :icon="ArrowLeft" @click="goBack">返回批次</el-button>
        <div>
          <h1 class="page-title">{{ importItem?.originalFile?.fileName || 'CSV 导入' }}</h1>
          <div class="title-meta">
            <el-tag v-if="importItem" :type="importStatusMeta(importItem.status).tagType" size="small" effect="light">
              {{ importStatusMeta(importItem.status).label }}
            </el-tag>
            <el-link v-if="batch" type="primary" @click="goBatch">{{ batch.batchCode }}</el-link>
            <span class="muted text-help">导入版本 v{{ importItem?.importVersion ?? '--' }}</span>
            <span class="muted text-help">记录版本 v{{ importItem?.version ?? '--' }}</span>
          </div>
        </div>
      </div>

      <div v-if="importItem" class="header-actions">
        <el-button v-if="canSaveMapping" :loading="actionLoading === 'save'" @click="handleSaveMapping">
          保存映射
        </el-button>
        <el-button v-if="canConfirm" type="primary" :loading="actionLoading === 'confirm'" @click="handleConfirm">
          确认导入
        </el-button>
        <el-button v-if="batch" @click="goBatch">查看批次</el-button>
      </div>
    </header>

    <MockStateBanner />

    <RegionState v-if="loading" state="loading" variant="section" />

    <RegionState
      v-else-if="permissionDenied"
      state="permission"
      variant="section"
      description="导入任务不存在或无权查看"
      :sub-title="permissionMessage"
    />

    <RegionState
      v-else-if="errorMessage"
      state="error"
      variant="section"
      description="加载导入任务失败"
      :sub-title="errorMessage"
      :error-message="errorMessage"
      @retry="loadPage"
    />

    <template v-else-if="importItem">
      <el-steps :active="stepActive" finish-status="success" simple class="import-steps">
        <el-step title="上传原件" description="仅 UTF-8 / UTF-8 BOM，20 MiB 内" />
        <el-step title="列与单位映射" description="时间列、指标列、单位与忽略列" />
        <el-step title="确认导入" description="写入正式记录并保留失败行审计" />
      </el-steps>

      <el-alert
        v-if="readOnlyReason"
        :type="importItem.status === 'FAILED' ? 'error' : 'info'"
        :closable="false"
        show-icon
        :title="readOnlyReason"
      >
        <template v-if="importItem.failureReason">{{ importItem.failureReason }}</template>
        <template v-else>原件与映射已冻结，只读展示导入结果。</template>
      </el-alert>

      <el-alert
        v-if="importItem.status === 'CONFIRMED'"
        type="success"
        :closable="false"
        show-icon
        title="导入已确认"
      >
        共写入 {{ importItem.recordCount }} 条有效记录，拒绝 {{ importItem.rejectedCount }} 行；拒绝行保留原始行号与原因。
      </el-alert>

      <div class="detail-grid">
        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <el-icon><Document /></el-icon>
              <span>原件信息</span>
            </div>
          </template>
          <dl class="info-rows">
            <div class="info-row">
              <dt>文件名</dt>
              <dd>{{ importItem.originalFile.fileName }}</dd>
            </div>
            <div class="info-row">
              <dt>大小</dt>
              <dd>{{ formatBytes(importItem.originalFile.size) }}</dd>
            </div>
            <div class="info-row">
              <dt>上传人 / 时间</dt>
              <dd>{{ importItem.originalFile.uploadedByName || '--' }} · {{ formatDateTime(importItem.originalFile.uploadTime) }}</dd>
            </div>
            <div class="info-row">
              <dt>SHA-256</dt>
              <dd><code>{{ shortHash(importItem.originalFile.sha256) }}</code></dd>
            </div>
            <div class="info-row">
              <dt>记录 / 拒绝</dt>
              <dd>{{ importItem.recordCount }} / {{ importItem.rejectedCount }}</dd>
            </div>
          </dl>
        </el-card>

        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <el-icon><Files /></el-icon>
              <span>预览信息</span>
              <el-tag v-if="preview" size="small" type="info">前 {{ preview.rows?.length || 0 }} 行</el-tag>
            </div>
          </template>
          <dl v-if="preview" class="info-rows">
            <div class="info-row">
              <dt>列数</dt>
              <dd>{{ preview.headers?.length || 0 }} 列</dd>
            </div>
            <div class="info-row">
              <dt>解析行数</dt>
              <dd>{{ preview.parsedRowCount }}</dd>
            </div>
            <div class="info-row">
              <dt>截断</dt>
              <dd>{{ preview.truncated ? '预览已截断，导入仍处理全量' : '预览完整（最多 50 行）' }}</dd>
            </div>
            <div class="info-row">
              <dt>警告</dt>
              <dd>{{ preview.warnings?.length || 0 }} 条</dd>
            </div>
          </dl>
          <el-empty v-else description="无可用预览" :image-size="64" />
        </el-card>
      </div>

      <el-card class="detail-card">
        <template #header>
          <div class="card-header">
            <el-icon><Grid /></el-icon>
            <span>数据预览</span>
          </div>
        </template>

        <RegionState
          v-if="!preview"
          state="empty"
          variant="card"
          description="该导入没有可用预览，请查看失败原因后重新上传"
        />

        <template v-else>
          <el-alert
            v-for="(warning, index) in preview.warnings || []"
            :key="`${warning.row}-${warning.column}-${index}`"
            type="warning"
            :closable="false"
            show-icon
            class="preview-warning"
            :title="`第 ${warning.row} 行 · ${warning.column}：${warning.message}`"
          />

          <div class="preview-scroll">
            <el-table :data="previewRows" size="small" border>
              <el-table-column type="index" label="#" width="56" />
              <el-table-column
                v-for="(header, index) in preview.headers || []"
                :key="header"
                :label="header"
                min-width="140"
              >
                <template #default="{ row }">{{ row[index] ?? '' }}</template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-card>

      <el-card class="detail-card">
        <template #header>
          <div class="card-header">
            <el-icon><SetUp /></el-icon>
            <span>列 / 单位 / 时间映射</span>
            <el-tag v-if="readOnly" size="small" type="info">只读</el-tag>
          </div>
        </template>

        <el-form label-position="top" class="mapping-form">
          <div class="form-grid">
            <el-form-item label="分隔符">
              <el-select v-model="mapping.delimiter" class="full-width" :disabled="readOnly">
                <el-option label="逗号 (,)" value="," />
                <el-option label="分号 (;)" value=";" />
                <el-option label="制表符 (Tab)" value="\t" />
              </el-select>
            </el-form-item>
            <el-form-item label="编码">
              <el-select v-model="mapping.encoding" class="full-width" :disabled="readOnly">
                <el-option label="UTF-8" value="UTF-8" />
                <el-option label="UTF-8 BOM" value="UTF-8 BOM" />
              </el-select>
            </el-form-item>
            <el-form-item label="表头行">
              <el-input-number v-model="mapping.headerRow" :min="1" :max="10" class="full-width" :disabled="readOnly" />
            </el-form-item>
            <el-form-item label="时区">
              <el-select v-model="mapping.timeMapping.timezone" class="full-width" :disabled="readOnly">
                <el-option label="Asia/Shanghai (UTC+8)" value="Asia/Shanghai" />
                <el-option label="UTC" value="UTC" />
              </el-select>
            </el-form-item>
            <el-form-item label="时间列">
              <el-select v-model="mapping.timeMapping.sourceColumn" class="full-width" :disabled="readOnly" placeholder="选择时间列">
                <el-option v-for="h in headers" :key="h" :label="h" :value="h" />
              </el-select>
            </el-form-item>
            <el-form-item label="时间格式">
              <el-input v-model="mapping.timeMapping.format" :disabled="readOnly" placeholder="yyyy-MM-dd HH:mm:ss" />
            </el-form-item>
          </div>

          <div class="mapping-block">
            <div class="mapping-block__head">
              <span class="mapping-block__title">指标列映射</span>
              <el-button v-if="!readOnly" size="small" :icon="Plus" @click="addColumn">添加列</el-button>
            </div>
            <p class="text-help muted">
              指标必须来自计划快照；单位不一致且不在白名单换算范围内时不能保存。
            </p>

            <div v-for="(column, index) in mapping.columns" :key="index" class="column-row">
              <el-select v-model="column.sourceColumn" class="column-row__source" :disabled="readOnly" placeholder="源列">
                <el-option v-for="h in headers" :key="h" :label="h" :value="h" />
              </el-select>
              <el-select
                v-model="column.metricCode"
                class="column-row__metric"
                :disabled="readOnly"
                placeholder="计划指标"
                @change="applyMetric(index)"
              >
                <el-option
                  v-for="metric in planMetrics"
                  :key="metric.code"
                  :label="`${metric.name}（${metric.code}）`"
                  :value="metric.code"
                />
              </el-select>
              <el-input v-model="column.sourceUnit" class="column-row__unit" :disabled="readOnly" placeholder="源单位" />
              <el-input v-model="column.targetUnit" class="column-row__unit" :disabled="readOnly" placeholder="目标单位" />
              <span class="column-row__type text-help muted">{{ column.valueType || '--' }}</span>
              <el-button v-if="!readOnly" text type="danger" :disabled="mapping.columns.length <= 1" @click="removeColumn(index)">
                删除
              </el-button>
            </div>
          </div>

          <div class="ignored-block">
            <span class="muted text-help">忽略列：</span>
            <el-tag v-for="h in ignoredColumns" :key="h" size="small" type="info" effect="plain">{{ h }}</el-tag>
            <span v-if="!ignoredColumns.length" class="muted text-help">无</span>
          </div>
        </el-form>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Document, Files, Grid, SetUp, Plus } from '@element-plus/icons-vue'
import {
  getDataImport,
  getImportPreview,
  saveImportMapping,
  confirmImport,
  getCollectionBatch,
  canOperateCollectionBatch,
  importStatusMeta,
  isBatchImmutableStatus,
  DATA_IMPORT_STATUS
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
  isImportAlreadyConfirmed,
  isImportMappingInvalid,
  isBatchImmutable,
  isIdempotencyKeyReused
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'
import RegionState from '@/components/RegionState.vue'
import MockStateBanner from '@/components/MockStateBanner.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const errorMessage = ref('')
const permissionDenied = ref(false)
const permissionMessage = ref('该导入任务不存在，或当前账号不在项目可见范围内')

const importItem = ref(null)
const batch = ref(null)
const project = ref(null)
const actionLoading = ref('')

const mapping = ref(emptyMapping())

const headers = computed(() => importItem.value?.preview?.headers || [])
const preview = computed(() => importItem.value?.preview || null)
const previewRows = computed(() => preview.value?.rows || [])
const planMetrics = computed(() => batch.value?.plan?.metricDefinitions || [])
const projectRole = computed(() => project.value?.myRole || null)
const canOperate = computed(() => canOperateCollectionBatch(batch.value?.plan, projectRole.value))

const batchImmutable = computed(() => isBatchImmutableStatus(batch.value?.status))
const readOnly = computed(
  () =>
    batchImmutable.value ||
    [DATA_IMPORT_STATUS.CONFIRMED, DATA_IMPORT_STATUS.FAILED].includes(importItem.value?.status)
)
const canSaveMapping = computed(
  () =>
    canOperate.value &&
    !readOnly.value &&
    [DATA_IMPORT_STATUS.UPLOADED, DATA_IMPORT_STATUS.MAPPING_REQUIRED].includes(importItem.value?.status)
)
const canConfirm = computed(
  () => canOperate.value && !readOnly.value && importItem.value?.status === DATA_IMPORT_STATUS.MAPPING_REQUIRED
)
const readOnlyReason = computed(() => {
  if (!importItem.value) return ''
  if (importItem.value.status === DATA_IMPORT_STATUS.FAILED) return '导入解析失败，原件与失败审计已保留'
  if (importItem.value.status === DATA_IMPORT_STATUS.CONFIRMED) return '导入已确认，映射与结果只读'
  if (batchImmutable.value) return '批次已完成或已归档，导入数据只读'
  if (!canOperate.value) return '当前账号对该批次只有只读权限'
  return ''
})

const ignoredColumns = computed(() => {
  const used = new Set()
  if (mapping.value.timeMapping?.sourceColumn) used.add(mapping.value.timeMapping.sourceColumn)
  mapping.value.columns.forEach((column) => {
    if (column.sourceColumn) used.add(column.sourceColumn)
  })
  return headers.value.filter((h) => !used.has(h))
})

const stepActive = computed(() => {
  const status = importItem.value?.status
  if (status === DATA_IMPORT_STATUS.CONFIRMED) return 3
  if (status === DATA_IMPORT_STATUS.MAPPING_REQUIRED) return 2
  if (status === DATA_IMPORT_STATUS.UPLOADED) return 1
  return 1
})

function emptyMapping() {
  return {
    delimiter: ',',
    encoding: 'UTF-8',
    headerRow: 1,
    timeMapping: { sourceColumn: '', format: 'yyyy-MM-dd HH:mm:ss', timezone: 'Asia/Shanghai' },
    columns: [],
    ignoredColumns: []
  }
}

function buildDefaultMapping(item, plan) {
  const next = emptyMapping()
  const cols = item?.preview?.headers || []
  const timeColumn = cols[0] || ''
  next.timeMapping.sourceColumn = timeColumn
  const metrics = plan?.metricDefinitions || []
  const valueColumns = cols.filter((h) => h !== timeColumn)
  next.columns = metrics
    .slice(0, Math.max(valueColumns.length, 1))
    .map((metric, index) => ({
      sourceColumn: valueColumns[index] || '',
      metricCode: metric.code,
      valueType: metric.valueType,
      sourceUnit: metric.unit,
      targetUnit: metric.unit
    }))
    .filter((column) => column.sourceColumn)
  return next
}

function normalizeMapping(item, plan) {
  const existing = item?.mapping?.mapping
  if (!existing) return buildDefaultMapping(item, plan)
  return {
    delimiter: existing.delimiter || ',',
    encoding: existing.encoding || 'UTF-8',
    headerRow: existing.headerRow || 1,
    timeMapping: {
      sourceColumn: existing.timeMapping?.sourceColumn || '',
      format: existing.timeMapping?.format || 'yyyy-MM-dd HH:mm:ss',
      timezone: existing.timeMapping?.timezone || 'Asia/Shanghai'
    },
    columns: (existing.columns || []).map((column) => ({
      sourceColumn: column.sourceColumn,
      metricCode: column.metricCode,
      valueType: column.valueType || planMetricType(plan, column.metricCode),
      sourceUnit: column.sourceUnit,
      targetUnit: column.targetUnit
    })),
    ignoredColumns: [...(existing.ignoredColumns || [])]
  }
}

function planMetricType(plan, metricCode) {
  return plan?.metricDefinitions?.find((m) => m.code === metricCode)?.valueType || 'DECIMAL'
}

function applyMetric(index) {
  const column = mapping.value.columns[index]
  const metric = planMetrics.value.find((m) => m.code === column.metricCode)
  if (!metric) return
  column.valueType = metric.valueType
  column.sourceUnit = column.sourceUnit || metric.unit
  column.targetUnit = metric.unit
}

function addColumn() {
  mapping.value.columns.push({
    sourceColumn: '',
    metricCode: planMetrics.value[0]?.code || '',
    valueType: planMetrics.value[0]?.valueType || 'DECIMAL',
    sourceUnit: planMetrics.value[0]?.unit || '',
    targetUnit: planMetrics.value[0]?.unit || ''
  })
}

function removeColumn(index) {
  mapping.value.columns.splice(index, 1)
}

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
  if (importItem.value?.batchId) router.push(`/collection/batches/${importItem.value.batchId}`)
  else router.push('/projects')
}

function goBatch() {
  if (importItem.value?.batchId) router.push(`/collection/batches/${importItem.value.batchId}`)
}

function validateMapping() {
  if (!mapping.value.timeMapping.sourceColumn) {
    ElMessage.warning('必须映射时间列')
    return false
  }
  if (!mapping.value.columns.length) {
    ElMessage.warning('至少映射一个指标列')
    return false
  }
  for (const column of mapping.value.columns) {
    if (!column.sourceColumn) {
      ElMessage.warning('存在未选择源列的指标映射')
      return false
    }
    if (!column.metricCode) {
      ElMessage.warning('存在未选择计划指标的映射')
      return false
    }
    if (!column.sourceUnit || !column.targetUnit) {
      ElMessage.warning('请填写源单位与目标单位')
      return false
    }
    if (column.sourceUnit !== column.targetUnit) {
      ElMessage.warning(`单位不一致且无白名单换算：${column.sourceUnit} → ${column.targetUnit}`)
      return false
    }
  }
  return true
}

function buildPayload() {
  return {
    delimiter: mapping.value.delimiter,
    encoding: mapping.value.encoding,
    headerRow: mapping.value.headerRow,
    timeMapping: { ...mapping.value.timeMapping },
    columns: mapping.value.columns.map((column) => ({
      sourceColumn: column.sourceColumn,
      metricCode: column.metricCode,
      valueType: column.valueType,
      sourceUnit: column.sourceUnit,
      targetUnit: column.targetUnit
    })),
    ignoredColumns: ignoredColumns.value
  }
}

async function loadPage() {
  loading.value = true
  errorMessage.value = ''
  permissionDenied.value = false
  try {
    importItem.value = await getDataImport(route.params.id)
    const previewData = await getImportPreview(importItem.value.id).catch(() => null)
    if (previewData) importItem.value = { ...importItem.value, preview: previewData }
    batch.value = await getCollectionBatch(importItem.value.batchId).catch(() => null)
    project.value = batch.value ? await getProjectDetail(batch.value.projectId).catch(() => null) : null
    mapping.value = normalizeMapping(importItem.value, batch.value?.plan)
  } catch (err) {
    if (isCollectionNotFound(err) || isCollectionDenied(err)) {
      permissionDenied.value = true
      permissionMessage.value = describeError(err)
    } else {
      errorMessage.value = describeError(err)
    }
    importItem.value = null
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
  if (isImportAlreadyConfirmed(err)) {
    ElMessage.warning('该导入修订已确认，不能重复操作')
    loadPage()
    return
  }
  if (isBatchImmutable(err)) {
    ElMessage.warning('批次已完成或已归档，导入数据不可修改')
    loadPage()
    return
  }
  if (isImportMappingInvalid(err)) {
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

async function handleSaveMapping() {
  if (!validateMapping()) return
  actionLoading.value = 'save'
  try {
    await saveImportMapping(importItem.value.id, {
      version: importItem.value.version,
      importVersion: importItem.value.importVersion,
      mapping: buildPayload()
    })
    ElMessage.success('映射已保存，请核对预览后确认导入')
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function handleConfirm() {
  if (!validateMapping()) return
  try {
    await ElMessageBox.confirm(
      `确认后将按当前映射写入正式记录，拒绝行保留原始行号与原因，原件不可覆盖。`,
      '确认导入',
      { confirmButtonText: '确认导入', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  actionLoading.value = 'confirm'
  try {
    const res = await confirmImport(
      importItem.value.id,
      {
        version: importItem.value.version,
        importVersion: importItem.value.importVersion,
        batchVersion: batch.value?.version
      },
      createIdempotencyKey('w43-import-confirm')
    )
    ElMessage.success(`导入已确认：${res.recordCount} 条有效，${res.rejectedCount} 行拒绝`)
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

onMounted(loadPage)
</script>

<style scoped>
.import-detail-page {
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
  word-break: break-all;
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

.import-steps {
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  padding: var(--usn-space-3);
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
  grid-template-columns: 100px minmax(0, 1fr);
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

.info-row code {
  font-family: var(--usn-font-mono, monospace);
  font-size: var(--usn-font-size-help);
}

.preview-warning {
  margin-bottom: var(--usn-space-2);
}

.preview-scroll {
  max-height: 420px;
  overflow: auto;
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-sm);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 var(--usn-space-4);
}

.full-width {
  width: 100%;
}

.mapping-block {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  padding-top: var(--usn-space-3);
  border-top: 1px solid var(--usn-line);
}

.mapping-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-3);
}

.mapping-block__title {
  font-weight: 600;
  color: var(--usn-ink-900);
}

.column-row {
  display: grid;
  grid-template-columns: 1.2fr 1.4fr 90px 90px 70px auto;
  align-items: center;
  gap: var(--usn-space-2);
}

.column-row__source,
.column-row__metric,
.column-row__unit {
  width: 100%;
}

.ignored-block {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
  margin-top: var(--usn-space-3);
  padding-top: var(--usn-space-3);
  border-top: 1px dashed var(--usn-line);
}

@media (max-width: 1100px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .column-row {
    grid-template-columns: 1fr 1fr 80px 80px;
    padding-bottom: var(--usn-space-2);
    border-bottom: 1px dashed var(--usn-line);
  }
}

@media (max-width: 767px) {
  .header-actions {
    width: 100%;
  }

  .header-actions :deep(.el-button) {
    flex: 1;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .column-row {
    grid-template-columns: 1fr 1fr;
  }

  .info-row {
    grid-template-columns: 1fr;
  }
}
</style>
