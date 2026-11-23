<template>
  <div class="issue-editor-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="back">返回领用申请列表</el-button>
      <span class="text-help muted">{{ isEdit ? `编辑草稿 ${issueRequest?.requestNo || ''}` : '新建领用申请' }}</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canCreateIssueRequest()"
      state="permission"
      variant="section"
      description="当前账号无权创建领用申请"
      sub-title="领用申请面向所有实验室成员、库存管理员、老师与系统管理员"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载领用申请草稿"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="领用申请草稿加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="initialize"
    />

    <RegionState
      v-else-if="isEdit && issueRequest && issueRequest.status !== ISSUE_STATUS.DRAFT"
      state="permission"
      variant="section"
      :description="`当前状态为 ${issueStatusMeta(issueRequest.status).label}，只有 DRAFT 草稿可编辑`"
      sub-title="已提交或已终态的单据为只读事实；如需重办请复制为新草稿"
    />

    <RegionState
      v-else-if="isEdit && !canEditIssueRequest(issueRequest)"
      state="permission"
      variant="section"
      description="当前账号不能编辑该草稿"
      sub-title="仅申请人或系统管理员可编辑 DRAFT"
    />

    <template v-else>
      <header class="page-header">
        <div>
          <h1 class="page-title">{{ isEdit ? '编辑领用申请草稿' : '新建领用申请' }}</h1>
          <p class="text-help muted">
            提交即预占可用库存（默认 {{ policy.reservationTtlHours }} 小时），审批不扣库存，发放确认后才写 ISSUE_OUT；
            金额与审批要求均由服务端按冻结契约重算。
          </p>
        </div>
        <div class="page-header__actions">
          <el-button @click="back">取消</el-button>
          <el-button :loading="saving" :disabled="saving || submitting" @click="save(false)">
            保存草稿
          </el-button>
          <el-button
            v-if="canSubmit"
            type="primary"
            :loading="submitting"
            :disabled="saving || submitting"
            @click="save(true)"
          >
            保存并提交
          </el-button>
        </div>
      </header>

      <el-alert
        v-if="conflictVersion != null"
        type="warning"
        :closable="false"
        show-icon
        :title="`检测到 version 冲突：服务端 currentVersion=${conflictVersion}。表单内容已保留，请刷新草稿并人工比较后再提交。`"
      >
        <el-button text type="primary" @click="initialize">刷新草稿并比较</el-button>
      </el-alert>

      <el-alert
        v-if="stockShortage"
        type="error"
        :closable="false"
        show-icon
        :title="`库存不足：元器件 ${stockShortage.componentId} 申请 ${formatQuantity(stockShortage.requestedQuantity)}，可用 ${formatQuantity(stockShortage.availableQuantity)}${stockShortage.traceId ? ` · traceId ${stockShortage.traceId}` : ''}`"
      >
        请减少数量、拆分申请或等待补货；本次提交未生成任何预占。
      </el-alert>

      <section class="data-panel editor-section">
        <h2 class="section-title">基本信息</h2>
        <div class="form-grid">
          <label class="form-field form-field--wide">
            <span>标题 *</span>
            <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="如：气象站原型焊接领料" />
          </label>
          <label class="form-field form-field--wide">
            <span>用途 *</span>
            <el-input
              v-model="form.purpose"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              placeholder="说明实验/项目用途，审批人会看到该说明"
            />
          </label>
          <label class="form-field">
            <span>关联项目</span>
            <el-select v-model="form.projectId" clearable placeholder="可选：仅本组成员可见项目" style="width: 100%">
              <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
            </el-select>
          </label>
        </div>
      </section>

      <section class="data-panel editor-section">
        <h2 class="section-title">领用明细</h2>
        <IssueLinesEditor v-model="form.lines" />
      </section>

      <footer class="editor-footer">
        <span class="text-help muted">
          提交成功后状态为「待审批」或「已批准，待发放」；预占到期会自动释放。
        </span>
        <div class="page-header__actions">
          <el-button @click="back">取消</el-button>
          <el-button :loading="saving" :disabled="saving || submitting" @click="save(false)">保存草稿</el-button>
          <el-button
            v-if="canSubmit"
            type="primary"
            :loading="submitting"
            :disabled="saving || submitting"
            @click="save(true)"
          >
            保存并提交
          </el-button>
        </div>
      </footer>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import RegionState from '@/components/RegionState.vue'
import IssueLinesEditor from '@/components/inventory/IssueLinesEditor.vue'
import {
  ISSUE_STATUS,
  ISSUE_POLICY_DEFAULTS,
  canCreateIssueRequest,
  canEditIssueRequest,
  createIssueRequest,
  formatQuantity,
  getIssuePolicy,
  getIssueRequest,
  issueStatusMeta,
  submitIssueRequest,
  updateIssueRequest
} from '@/api/inventoryIssuance'
import {
  errorTraceId,
  isIssueInsufficientStock,
  isIssueVersionConflict,
  normalizeApiError,
  notifyApiError,
  issueInsufficientStockDetail
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import { listProjects } from '@/api/projects'
import { buildQueryWithoutEmpty } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => Boolean(route.params.id))
const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const saving = ref(false)
const submitting = ref(false)
const conflictVersion = ref(null)
const stockShortage = ref(null)
const issueRequest = ref(null)
const projects = ref([])
const policy = reactive({ ...ISSUE_POLICY_DEFAULTS })

const form = reactive({
  title: '',
  purpose: '',
  projectId: null,
  lines: []
})

const canSubmit = computed(() => !isEdit.value || canEditIssueRequest(issueRequest.value))

function back() {
  router.push('/inventory/issues')
}

async function loadPolicy() {
  try {
    const data = await getIssuePolicy()
    Object.assign(policy, data || {})
  } catch (error) {
    // 策略仅用于展示默认值与时长提示，读取失败不阻塞表单
    notifyApiError(error)
  }
}

async function loadProjects() {
  try {
    const response = await listProjects(buildQueryWithoutEmpty({ page: 1, pageSize: 100 }))
    projects.value = response?.list || response?.items || []
  } catch (error) {
    projects.value = []
  }
}

async function initialize() {
  loadError.value = ''
  loadTraceId.value = ''
  conflictVersion.value = null
  stockShortage.value = null
  loading.value = true
  try {
    await Promise.all([loadPolicy(), loadProjects()])
    if (!isEdit.value) {
      return
    }
    const detail = await getIssueRequest(route.params.id)
    issueRequest.value = detail
    form.title = detail.title || ''
    form.purpose = detail.purpose || ''
    form.projectId = detail.projectId ?? null
    form.lines = (detail.lines || []).map((line) => ({
      id: line.id,
      componentId: line.componentId,
      requestedQuantity: Number(line.requestedQuantity || 0)
    }))
  } catch (error) {
    const { msg } = normalizeApiError(error)
    loadError.value = msg
    loadTraceId.value = errorTraceId(error)
  } finally {
    loading.value = false
  }
}

function payload() {
  return {
    title: String(form.title || '').trim(),
    purpose: String(form.purpose || '').trim(),
    projectId: form.projectId || null,
    lines: (form.lines || []).map((line) => ({
      componentId: line.componentId,
      requestedQuantity: Number(line.requestedQuantity || 0)
    }))
  }
}

function validate() {
  const body = payload()
  if (body.title.length < 2 || body.title.length > 120) {
    ElMessage.warning('标题需为 2-120 个字符')
    return null
  }
  if (body.purpose.length < 2 || body.purpose.length > 500) {
    ElMessage.warning('用途需为 2-500 个字符')
    return null
  }
  if (!body.lines.length) {
    ElMessage.warning('至少需要一条领用明细')
    return null
  }
  const missing = body.lines.some((line) => !line.componentId)
  if (missing) {
    ElMessage.warning('每条明细都需要选择元器件')
    return null
  }
  const invalidQuantity = body.lines.some((line) => !(line.requestedQuantity > 0))
  if (invalidQuantity) {
    ElMessage.warning('领用数量必须大于 0')
    return null
  }
  const ids = body.lines.map((line) => Number(line.componentId))
  if (new Set(ids).size !== ids.length) {
    ElMessage.warning('同一元器件只能出现一行')
    return null
  }
  return body
}

async function save(andSubmit) {
  const body = validate()
  if (!body) return
  conflictVersion.value = null
  stockShortage.value = null
  if (andSubmit) submitting.value = true
  else saving.value = true
  try {
    let saved
    if (isEdit.value) {
      saved = await updateIssueRequest(route.params.id, { ...body, version: issueRequest.value.version })
      issueRequest.value = saved
    } else {
      saved = await createIssueRequest(body, createIdempotencyKey('w46-issue-create'))
      issueRequest.value = saved
    }
    if (andSubmit) {
      saved = await submitIssueRequest(saved.id, { requestVersion: saved.version }, createIdempotencyKey('w46-issue-submit'))
      ElMessage.success(`已提交 ${saved.requestNo}，当前状态：${issueStatusMeta(saved.status).label}`)
      router.push(`/inventory/issues/${saved.id}`)
      return
    }
    ElMessage.success(isEdit.value ? '草稿已更新' : '草稿已创建')
    if (!isEdit.value) router.replace(`/inventory/issues/${saved.id}/edit`)
  } catch (error) {
    if (isIssueVersionConflict(error)) {
      conflictVersion.value = normalizeApiError(error).data?.currentVersion ?? 0
      return
    }
    if (isIssueInsufficientStock(error)) {
      stockShortage.value = issueInsufficientStockDetail(error)
      return
    }
    notifyApiError(error)
    const { msg } = normalizeApiError(error)
    ElMessage.error(msg)
  } finally {
    saving.value = false
    submitting.value = false
  }
}

onMounted(initialize)
</script>

<style scoped>
.issue-editor-page {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.editor-section {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--usn-space-3);
  padding: var(--usn-space-3) 0;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .editor-footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
