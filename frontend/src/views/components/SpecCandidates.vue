<template>
  <div class="spec-candidates-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="goBack">返回型号详情</el-button>
      <span class="text-help muted">{{ component?.catalogCode || '—' }}</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canSpec"
      state="permission"
      variant="section"
      description="当前账号无权查看规格候选"
      sub-title="规格候选仅对 SYSTEM_ADMIN、STOCK_KEEPER 和 TEACHER 开放；MEMBER 只能查看当前正式快照"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载规格候选与型号信息"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="规格候选加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="initialize"
    />

    <template v-else>
      <header class="page-header">
        <div>
          <h1 class="page-title">规格候选与确认</h1>
          <p class="page-subtitle muted">
            外部来源先保存为 PENDING 候选；人工逐项对比并修正后确认，生成不可变正式快照
          </p>
        </div>
        <el-button
          v-if="canManage && component && component.status !== COMPONENT_STATUS.INACTIVE"
          type="primary"
          :icon="Plus"
          @click="candidateVisible = true"
        >
          登记外部候选
        </el-button>
      </header>

      <section v-if="component" class="component-summary data-panel">
        <div class="summary-main">
          <strong>{{ component.name }}</strong>
          <span class="mpn-text">{{ component.mpn }}</span>
          <el-tag :type="statusMeta(component.status).tagType" size="small" effect="light">
            {{ statusMeta(component.status).label }}
          </el-tag>
        </div>
        <dl class="summary-meta">
          <div><dt>制造商</dt><dd>{{ component.manufacturerName || '—' }}</dd></div>
          <div><dt>封装</dt><dd>{{ component.packageType }}</dd></div>
          <div><dt>正式规格</dt><dd>{{ component.specVersion ? `v${component.specVersion}` : '无' }}</dd></div>
          <div><dt>version</dt><dd>{{ component.version }}</dd></div>
        </dl>
        <p class="text-help muted summary-note">
          候选确认与生成快照在同一事务；确认失败不会移动当前快照指针。候选终态为 CONFIRMED / REJECTED / SUPERSEDED。
        </p>
      </section>

      <el-alert
        v-if="!canManage"
        type="info"
        show-icon
        :closable="false"
        title="老师账号为全局只读：可查看候选来源、原始值与标准化建议，但不能登记、确认或拒绝"
      class="candidate-alert"
      />

      <div class="filter-bar toolbar">
        <el-radio-group v-model="statusFilter" @change="loadCandidates">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button v-for="item in statusOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
        <span class="text-help muted">共 {{ total }} 条候选</span>
      </div>

      <RegionState
        v-if="candidateLoading"
        state="loading"
        variant="card"
        description="正在加载候选"
      />

      <RegionState
        v-else-if="candidateError"
        state="error"
        variant="section"
        description="候选加载失败"
        :error-message="`${candidateError}${candidateTraceId ? ` · traceId ${candidateTraceId}` : ''}`"
        @retry="loadCandidates"
      />

      <RegionState
        v-else-if="!candidates.length"
        state="empty"
        variant="section"
        :description="statusFilter ? '该状态下暂无候选' : '暂无规格候选'"
        sub-title="登记外部数据手册或授权分销商来源，等待人工对比与确认"
      >
        <div class="empty-actions">
          <el-button v-if="statusFilter" @click="resetFilter">查看全部候选</el-button>
          <el-button
            v-if="canManage && component && component.status !== COMPONENT_STATUS.INACTIVE"
            type="primary"
            @click="candidateVisible = true"
          >
            登记第一个候选
          </el-button>
        </div>
      </RegionState>

      <div v-else class="candidate-list">
        <article v-for="candidate in candidates" :key="candidate.id" class="candidate-card data-panel">
          <header class="candidate-head">
            <div class="candidate-head__main">
              <el-tag :type="candidateStatusMeta(candidate.status).tagType" effect="dark">
                {{ candidateStatusMeta(candidate.status).label }}
              </el-tag>
              <strong>candidateId {{ candidate.id }}</strong>
              <span class="text-help muted">{{ sourceTypeMeta(candidate.sourceType).label }}</span>
            </div>
            <div v-if="canManage && candidate.status === CANDIDATE_STATUS.PENDING" class="candidate-head__actions">
              <el-button type="success" size="small" @click="openConfirm(candidate)">人工确认</el-button>
              <el-button type="danger" size="small" plain @click="reject(candidate)">拒绝</el-button>
            </div>
          </header>

          <div class="source-grid">
            <div class="source-cell">
              <span class="text-help muted">来源标题</span>
              <strong>{{ candidate.sourceTitle || '—' }}</strong>
            </div>
            <div class="source-cell">
              <span class="text-help muted">来源 URL</span>
              <el-link
                v-if="candidate.sourceUrl"
                :href="candidate.sourceUrl"
                target="_blank"
                rel="noopener noreferrer"
                type="primary"
                class="break-anywhere"
              >
                {{ candidate.sourceUrl }}
              </el-link>
              <strong v-else>—</strong>
            </div>
            <div class="source-cell">
              <span class="text-help muted">获取时间</span>
              <strong>{{ formatDateTime(candidate.capturedAt) }}</strong>
            </div>
            <div class="source-cell">
              <span class="text-help muted">登记人 / 时间</span>
              <strong>{{ candidate.submittedByName || `#${candidate.submittedBy}` }} · {{ formatDateTime(candidate.submittedAt) }}</strong>
            </div>
            <div v-if="candidate.decidedAt" class="source-cell">
              <span class="text-help muted">处理人 / 时间</span>
              <strong>{{ candidate.decidedByName || `#${candidate.decidedBy}` }} · {{ formatDateTime(candidate.decidedAt) }}</strong>
            </div>
            <div v-if="candidate.decisionNote" class="source-cell source-cell--wide">
              <span class="text-help muted">处理说明</span>
              <strong>{{ candidate.decisionNote }}</strong>
            </div>
          </div>

          <div class="compare-head">
            <h3 class="compare-title">原始值与标准化建议对比</h3>
            <span class="text-help muted">人工对照，不做自动映射；未知单位不得猜测</span>
          </div>

          <div class="compare-grid">
            <section class="compare-column">
              <header class="compare-column__head">
                <strong>原始规格 rawSpecs</strong>
                <el-tag size="small" effect="plain" type="info">{{ candidate.rawSpecs.length }} 项</el-tag>
              </header>
              <el-collapse v-model="rawActive[candidate.id]" class="compare-collapse">
                <el-collapse-item
                  v-for="(row, index) in candidate.rawSpecs"
                  :key="`raw-${candidate.id}-${index}`"
                  :name="`raw-${index}`"
                  :title="row.name || `原始项 ${index + 1}`"
                >
                  <p class="compare-value">{{ row.value }}</p>
                  <p class="text-help muted">单位：{{ row.unit || '未提供（不猜测）' }}</p>
                </el-collapse-item>
              </el-collapse>
            </section>

            <section class="compare-column">
              <header class="compare-column__head">
                <strong>标准化建议 normalizedProposal</strong>
                <el-tag size="small" effect="plain" type="success">{{ candidate.normalizedProposal.length }} 项</el-tag>
              </header>
              <el-collapse v-model="normalizedActive[candidate.id]" class="compare-collapse">
                <el-collapse-item
                  v-for="(row, index) in candidate.normalizedProposal"
                  :key="`spec-${candidate.id}-${index}`"
                  :name="`spec-${index}`"
                  :title="row.label || row.specCode || `标准化项 ${index + 1}`"
                >
                  <dl class="compare-detail">
                    <div><dt>specCode</dt><dd><code>{{ row.specCode }}</code></dd></div>
                    <div><dt>标准化值</dt><dd>{{ row.normalizedValue }}{{ row.unit ? ` ${row.unit}` : '' }}</dd></div>
                    <div><dt>值类型</dt><dd>{{ valueTypeMeta(row.valueType).label }}</dd></div>
                    <div><dt>排序</dt><dd>{{ row.sortOrder }}</dd></div>
                  </dl>
                </el-collapse-item>
              </el-collapse>
            </section>
          </div>
        </article>
      </div>

      <CandidateDialog
        v-model="candidateVisible"
        :component="component"
        @submitted="handleCandidateSubmitted"
        @conflict="initialize"
      />

      <el-dialog
        v-model="confirmVisible"
        title="人工确认候选并生成正式快照"
        width="min(900px, 92vw)"
        :close-on-click-modal="false"
        class="confirm-dialog"
      >
        <el-alert
          type="warning"
          show-icon
          :closable="false"
          title="确认后生成不可变快照，旧版本永久只读"
          description="请逐项核对并修正标准化值；确认请求同时校验候选 version 与型号 version，冲突时不覆盖。"
          class="confirm-alert"
        />

        <el-form label-position="top">
          <el-form-item label="变更说明 changeSummary" required>
            <el-input
              v-model="confirmSummary"
              maxlength="500"
              show-word-limit
              placeholder="例如：首次确认制造商数据手册规格"
            />
          </el-form-item>
          <el-form-item label="正式规格 specifications" required>
            <SpecRowsEditor v-model="confirmSpecs" mode="normalized" />
          </el-form-item>
        </el-form>

        <template #footer>
          <el-button @click="confirmVisible = false">取消</el-button>
          <el-button type="primary" :loading="confirming" @click="confirmCandidate">确认并生成快照</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getComponent,
  listSpecCandidates,
  confirmSpecCandidate,
  rejectSpecCandidate,
  canManageCatalog,
  COMPONENT_STATUS,
  CANDIDATE_STATUS,
  componentStatusMeta,
  candidateStatusMeta,
  sourceTypeMeta,
  valueTypeMeta
} from '@/api/components'
import {
  notifyApiError,
  normalizeApiError,
  errorTraceId,
  isVersionConflict,
  isCandidateAlreadyDecided,
  promptVersionConflict
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import { currentRoles } from '@/store/user'
import { ROLE } from '@/utils/permission'
import { formatDateTime } from '@/utils/format'
import RegionState from '@/components/RegionState.vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import CandidateDialog from '@/components/components/CandidateDialog.vue'
import SpecRowsEditor from '@/components/components/SpecRowsEditor.vue'

const route = useRoute()
const router = useRouter()

const canManage = canManageCatalog()
const canSpec = currentRoles().includes(ROLE.SYSTEM_ADMIN)
  || currentRoles().includes(ROLE.STOCK_KEEPER)
  || currentRoles().includes(ROLE.TEACHER)

const component = ref(null)
const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')

const candidates = ref([])
const total = ref(0)
const candidateLoading = ref(false)
const candidateError = ref('')
const candidateTraceId = ref('')
const statusFilter = ref('')

const candidateVisible = ref(false)
const confirmVisible = ref(false)
const confirming = ref(false)
const confirmTarget = ref(null)
const confirmSummary = ref('')
const confirmSpecs = ref([])

const rawActive = reactive({})
const normalizedActive = reactive({})

const statusOptions = Object.entries(CANDIDATE_STATUS).map(([, value]) => ({
  value,
  label: candidateStatusMeta(value).label
}))

function statusMeta(status) {
  return componentStatusMeta(status)
}

function goBack() {
  if (component.value) router.push(`/components/${component.value.id}`)
  else router.push('/components')
}

function resetFilter() {
  statusFilter.value = ''
  loadCandidates()
}

async function loadCandidates() {
  if (!component.value) return
  candidateLoading.value = true
  candidateError.value = ''
  candidateTraceId.value = ''
  try {
    const res = await listSpecCandidates(component.value.id, {
      page: 1,
      pageSize: 100,
      status: statusFilter.value || undefined
    })
    candidates.value = res?.list || []
    total.value = res?.total ?? 0
    for (const candidate of candidates.value) {
      if (!rawActive[candidate.id]) {
        rawActive[candidate.id] = candidate.rawSpecs.map((_, index) => `raw-${index}`)
      }
      if (!normalizedActive[candidate.id]) {
        normalizedActive[candidate.id] = candidate.normalizedProposal.map((_, index) => `spec-${index}`)
      }
    }
  } catch (err) {
    candidateError.value = normalizeApiError(err).msg || '加载失败'
    candidateTraceId.value = errorTraceId(err)
    candidates.value = []
    total.value = 0
    notifyApiError(err)
  } finally {
    candidateLoading.value = false
  }
}

async function initialize() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  try {
    component.value = await getComponent(route.params.id)
    await loadCandidates()
  } catch (err) {
    const normalized = normalizeApiError(err)
    loadError.value = normalized.msg || '加载失败'
    loadTraceId.value = errorTraceId(err)
    notifyApiError(err)
  } finally {
    loading.value = false
  }
}

async function handleCandidateSubmitted() {
  await initialize()
}

function openConfirm(candidate) {
  confirmTarget.value = candidate
  confirmSummary.value = ''
  const rawRows = candidate.rawSpecs || []
  confirmSpecs.value = (candidate.normalizedProposal || []).map((row, index) => ({
    specCode: row.specCode,
    label: row.label,
    valueType: row.valueType,
    rawValue: rawRows[index]?.value ?? null,
    normalizedValue: row.normalizedValue,
    unit: row.unit || '',
    sortOrder: row.sortOrder ?? (index + 1) * 10
  }))
  confirmVisible.value = true
}

async function confirmCandidate() {
  if (!confirmTarget.value || !component.value) return
  if (!confirmSummary.value.trim()) {
    ElMessage.warning('请填写变更说明')
    return
  }
  confirming.value = true
  try {
    const result = await confirmSpecCandidate(
      confirmTarget.value.id,
      {
        componentVersion: component.value.version,
        candidateVersion: confirmTarget.value.version,
        specifications: confirmSpecs.value.map((row, index) => ({
          specCode: row.specCode,
          label: row.label,
          valueType: row.valueType,
          rawValue: row.rawValue ?? null,
          normalizedValue: row.normalizedValue,
          unit: row.unit || null,
          sortOrder: Number(row.sortOrder ?? (index + 1) * 10)
        })),
        changeSummary: confirmSummary.value.trim()
      },
      createIdempotencyKey('w44-confirm-candidate')
    )
    ElMessage.success(
      `候选已确认，生成快照 v${result.snapshotVersion}（snapshotId ${result.snapshotId}），正式规格 v${result.specVersion}`
    )
    confirmVisible.value = false
    await initialize()
  } catch (err) {
    if (isVersionConflict(err)) {
      promptVersionConflict(() => initialize())
      return
    }
    if (isCandidateAlreadyDecided(err)) {
      ElMessage.error('该候选已处理，不能再次确认')
      await loadCandidates()
      return
    }
    const traceId = errorTraceId(err)
    notifyApiError(err)
    if (traceId) ElMessage.info(`traceId：${traceId}`)
  } finally {
    confirming.value = false
  }
}

async function reject(candidate) {
  let note = ''
  try {
    const result = await ElMessageBox.prompt(
      '拒绝为终态操作，候选不会被采用。可填写处理说明（最多 1000 字符）。',
      `拒绝候选 ${candidate.id}`,
      {
        confirmButtonText: '拒绝候选',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：来源不是制造商或授权分销商',
        inputValidator: (value) => String(value || '').trim().length <= 1000 || '处理说明最多 1000 字符',
        type: 'warning'
      }
    )
    note = String(result.value || '').trim()
  } catch {
    return
  }
  try {
    await rejectSpecCandidate(
      candidate.id,
      { candidateVersion: candidate.version, decisionNote: note || null },
      createIdempotencyKey('w44-reject-candidate')
    )
    ElMessage.success('候选已拒绝')
    await loadCandidates()
  } catch (err) {
    if (isVersionConflict(err)) {
      promptVersionConflict(() => initialize())
      return
    }
    if (isCandidateAlreadyDecided(err)) {
      ElMessage.error('该候选已处理，不能再次拒绝')
      await loadCandidates()
      return
    }
    notifyApiError(err)
  }
}

onMounted(() => {
  if (!canSpec) return
  initialize()
})
</script>

<style scoped>
.spec-candidates-page {
  max-width: var(--usn-content-max-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.detail-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-3);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-4);
  flex-wrap: wrap;
}

.component-summary {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
}

.summary-main {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.mpn-text {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-weight: 700;
  color: var(--usn-blue-700);
}

.summary-meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--usn-space-2);
  margin: 0;
}

.summary-meta div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-meta dt {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.summary-meta dd {
  margin: 0;
  font-weight: 600;
}

.summary-note {
  margin: 0;
}

.candidate-alert {
  margin: 0;
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.candidate-list {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.candidate-card {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
  border-left: 3px solid var(--usn-blue-600);
}

.candidate-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.candidate-head__main {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.candidate-head__actions {
  display: flex;
  gap: var(--usn-space-2);
}

.source-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--usn-space-2);
  padding: var(--usn-space-3);
  background: var(--usn-canvas);
  border-radius: var(--usn-radius-md);
}

.source-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.source-cell strong,
.source-cell :deep(.el-link) {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.source-cell--wide {
  grid-column: 1 / -1;
}

.break-anywhere {
  overflow-wrap: anywhere;
}

.compare-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.compare-title {
  margin: 0;
  font-size: var(--usn-font-size-panel-title);
  font-weight: 700;
}

.compare-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--usn-space-4);
  align-items: start;
}

.compare-column {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  padding: var(--usn-space-3);
}

.compare-column__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-2);
}

.compare-collapse {
  border: none;
}

.compare-value {
  margin: 0;
  font-weight: 600;
}

.compare-detail {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-1);
  margin: 0;
}

.compare-detail div {
  display: flex;
  justify-content: space-between;
  gap: var(--usn-space-2);
}

.compare-detail dt {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.compare-detail dd {
  margin: 0;
  font-weight: 600;
}

.compare-detail code {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}

.empty-actions {
  display: flex;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
  justify-content: center;
}

.confirm-alert {
  margin-bottom: var(--usn-space-3);
}

@media (max-width: 1024px) {
  .source-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .compare-grid,
  .source-grid,
  .summary-meta {
    grid-template-columns: 1fr;
  }

  .candidate-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .candidate-head__actions {
    width: 100%;
  }

  .candidate-head__actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
