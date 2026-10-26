<template>
  <div class="record-editor-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">{{ isCreate ? '新建实验记录' : '编辑实验记录' }}</h1>
        <p class="page-subtitle muted">
          {{ isCreate ? '选择归属与已发布模板版本，创建记录与第 1 份修订' : '仅可编辑当前 EDITABLE 修订，保存时携带版本号防止覆盖他人修改' }}
        </p>
      </div>
      <el-button @click="goBack">返回</el-button>
    </header>

    <RegionState v-if="loading" state="loading" variant="section" />

    <RegionState
      v-else-if="errorMessage"
      state="error"
      variant="section"
      description="加载实验记录失败"
      :sub-title="errorMessage"
      :error-message="errorMessage"
      @retry="loadPage"
    />

    <RegionState
      v-else-if="!canEdit"
      state="not-available"
      variant="section"
      description="当前修订不可编辑"
      sub-title="只有 EDITABLE 修订可以保存；已提交、已批准或已发布的内容需创建新修订"
    />

    <template v-else>
      <el-alert v-if="immutableReason" type="warning" :closable="false" show-icon class="immutable-alert">
        {{ immutableReason }}
      </el-alert>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="editor-form">
        <el-card class="editor-card">
          <template #header>
            <div class="card-header">
              <el-icon><Document /></el-icon>
              <span>基本信息</span>
              <el-tag v-if="!isCreate" size="small" effect="plain">{{ record?.recordCode }}</el-tag>
            </div>
          </template>

          <div class="form-grid">
            <el-form-item label="实验标题" prop="title">
              <el-input v-model="form.title" maxlength="120" show-word-limit :disabled="!isCreate" />
            </el-form-item>
            <el-form-item v-if="isCreate" label="记录归属" prop="ownership">
              <el-radio-group v-model="form.ownership" @change="onOwnershipChange">
                <el-radio-button value="PROJECT">项目实验</el-radio-button>
                <el-radio-button value="LEARNING">学习实验</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="isCreate && form.ownership === 'PROJECT'" label="所属项目" prop="projectId">
              <el-select v-model="form.projectId" class="full-width" placeholder="选择项目" @change="onProjectChange">
                <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="p.id" />
              </el-select>
            </el-form-item>

            <template v-if="isCreate && form.ownership === 'LEARNING'">
              <el-form-item label="学习路线" prop="roadmapId">
                <el-select v-model="form.roadmapId" class="full-width" placeholder="选择已加入的学习路线" @change="onRoadmapChange">
                  <el-option v-for="r in roadmaps" :key="r.roadmapId" :label="r.roadmapTitle" :value="r.roadmapId" />
                </el-select>
              </el-form-item>
              <el-form-item label="学习单元" prop="learningUnitId">
                <el-select v-model="form.learningUnitId" class="full-width" placeholder="选择学习单元" @change="onUnitChange">
                  <el-option
                    v-for="u in unitOptions"
                    :key="u.id"
                    :label="`${u.title}${u.templateName ? ` · ${u.templateName}` : ''}`"
                    :value="u.id"
                  />
                </el-select>
              </el-form-item>
            </template>

            <el-form-item v-if="!isCreate" label="归属">
              <el-input :model-value="ownershipLabel" disabled />
            </el-form-item>

            <el-form-item label="模板" prop="templateId">
              <el-select
                v-model="form.templateId"
                class="full-width"
                placeholder="选择已发布模板"
                :disabled="!isCreate"
                @change="onTemplateChange"
              >
                <el-option v-for="t in templateOptions" :key="t.id" :label="t.title" :value="t.id" />
              </el-select>
            </el-form-item>

            <el-form-item label="固定模板版本" prop="templateVersionId">
              <el-select
                v-model="form.templateVersionId"
                class="full-width"
                placeholder="选择已发布版本"
                :disabled="!isCreate"
                @change="onVersionChange"
              >
                <el-option v-for="v in versionOptions" :key="v.id" :label="`${v.version}（${v.fieldCount} 字段）`" :value="v.id" />
              </el-select>
            </el-form-item>
          </div>

          <el-alert v-if="!templateOptions.length && isCreate" type="info" :closable="false" show-icon class="template-empty">
            当前范围内暂无已发布模板版本，请先在实验模板中发布版本。
          </el-alert>

          <el-form-item label="实验摘要">
            <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="500" show-word-limit :disabled="!isCreate" />
          </el-form-item>

          <div v-if="fixedVersionHint" class="fixed-version-hint">
            <el-icon><Lock /></el-icon>
            <span>{{ fixedVersionHint }}</span>
          </div>
        </el-card>

        <el-card class="editor-card">
          <template #header>
            <div class="card-header">
              <el-icon><Grid /></el-icon>
              <span>结构化内容</span>
              <span class="text-help muted">按固定模板版本填写，提交时由后端再次校验</span>
            </div>
          </template>

          <RegionState v-if="!schemaJson" state="empty" variant="card" description="请选择模板版本后填写结构化内容" />
          <SchemaFieldForm
            v-else
            v-model="form.contentJson"
            :schema-json="schemaJson"
            :errors="contentErrors"
          />
        </el-card>

        <el-card class="editor-card">
          <template #header>
            <div class="card-header">
              <el-icon><Tickets /></el-icon>
              <span>实验上下文</span>
            </div>
          </template>

          <div class="form-grid">
            <el-form-item label="实验目的">
              <el-input v-model="form.purpose" maxlength="500" show-word-limit />
            </el-form-item>
            <el-form-item label="实验环境">
              <el-input v-model="form.environment" maxlength="500" show-word-limit />
            </el-form-item>
            <el-form-item label="实验步骤">
              <el-input v-model="form.steps" type="textarea" :rows="3" maxlength="2000" show-word-limit />
            </el-form-item>
            <el-form-item label="实验结果">
              <el-input v-model="form.result" type="textarea" :rows="3" maxlength="2000" show-word-limit />
            </el-form-item>
            <el-form-item label="异常说明">
              <el-input v-model="form.anomaly" type="textarea" :rows="2" maxlength="1000" show-word-limit />
            </el-form-item>
            <el-form-item label="结论">
              <el-input v-model="form.conclusion" type="textarea" :rows="2" maxlength="1000" show-word-limit />
            </el-form-item>
            <el-form-item label="硬件版本引用">
              <el-input v-model="form.hardwareVersionRef" maxlength="120" placeholder="受控文本，如 HW-PSU-02" />
            </el-form-item>
            <el-form-item label="设备实例引用">
              <el-input v-model="form.deviceInstanceRef" maxlength="120" placeholder="受控文本，如 LAB-A-017" />
            </el-form-item>
            <el-form-item label="固件版本">
              <el-input v-model="form.firmwareVersion" maxlength="120" />
            </el-form-item>
            <el-form-item label="BOM 版本引用">
              <el-input v-model="form.bomVersionRef" maxlength="120" />
            </el-form-item>
          </div>

          <el-form-item v-if="!isCreate" label="本次修订说明">
            <el-input v-model="form.changeSummary" maxlength="500" show-word-limit placeholder="说明本次修改内容，便于导师评审" />
          </el-form-item>
        </el-card>
      </el-form>

      <div class="editor-actions">
        <el-button @click="goBack">取消</el-button>
        <el-button :loading="saving" :disabled="submitting" @click="handleSave(false)">保存草稿</el-button>
        <el-button type="primary" :loading="submitting" :disabled="saving" @click="handleSave(true)">
          保存并提交评审
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Document, Grid, Tickets, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  createExperimentRecord,
  getExperimentRecord,
  saveRevision,
  submitExperimentRecord,
  canCreateExperimentRecord,
  canEditRecord,
  isRevisionEditable,
  RECORD_STATUS,
  REVISION_STATUS
} from '@/api/experimentRecords'
import {
  listTemplates,
  listPublishedVersions,
  getVersionDetail,
  TEMPLATE_SCOPE,
  TEMPLATE_VERSION_STATUS
} from '@/api/experimentTemplates'
import { listProjects } from '@/api/projects'
import { listMyRoadmaps, listStages } from '@/api/learning'
import {
  notifyApiError,
  normalizeApiError,
  errorTraceId,
  promptVersionConflict,
  experimentContentErrors,
  isVersionConflict,
  isExperimentContentInvalid,
  isRevisionImmutable,
  isExperimentDenied
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import RegionState from '@/components/RegionState.vue'
import SchemaFieldForm from '@/components/experiment/SchemaFieldForm.vue'

const route = useRoute()
const router = useRouter()

const isCreate = computed(() => route.path.endsWith('/new'))
const recordId = computed(() => route.params.id)
const revisionId = computed(() => route.params.revisionId)

const loading = ref(false)
const errorMessage = ref('')
const saving = ref(false)
const submitting = ref(false)
const record = ref(null)
const schemaJson = ref(null)
const contentErrors = ref([])
const projects = ref([])
const roadmaps = ref([])
const stages = ref([])
const templates = ref([])
const versionOptions = ref([])
const formRef = ref(null)

const form = ref(emptyForm())

const canEdit = computed(() => {
  if (isCreate.value) return canCreateExperimentRecord(form.value.projectId)
  if (!record.value) return false
  if (!canEditRecord(record.value)) return false
  return record.value.currentRevision?.id === Number(revisionId.value) && isRevisionEditable(record.value.currentRevision)
})

const immutableReason = computed(() => {
  if (isCreate.value || !record.value) return ''
  const revision = record.value.currentRevision
  if (revision && revision.id !== Number(revisionId.value)) {
    return '该修订不是当前工作修订，只能查看；请返回详情页编辑当前修订。'
  }
  if (revision && !isRevisionEditable(revision)) {
    return '当前修订已提交或已发布，内容不可修改；发布记录需创建新修订。'
  }
  return ''
})

const ownershipLabel = computed(() => {
  if (!record.value) return '--'
  if (record.value.projectName) return `项目 · ${record.value.projectName}`
  if (record.value.learningUnitName) return `学习单元 · ${record.value.learningUnitName}`
  return '--'
})

const templateOptions = computed(() => {
  if (form.value.ownership === 'LEARNING') {
    return templates.value.filter((t) => t.scope === TEMPLATE_SCOPE.SYSTEM)
  }
  return templates.value.filter(
    (t) => t.scope === TEMPLATE_SCOPE.SYSTEM || Number(t.projectId) === Number(form.value.projectId)
  )
})

const unitOptions = computed(() => stages.value.flatMap((stage) => stage.units || []))

const fixedVersionHint = computed(() => {
  if (!form.value.templateVersionId) return ''
  const template = templates.value.find((t) => t.id === Number(form.value.templateId))
  const version = versionOptions.value.find((v) => v.id === Number(form.value.templateVersionId))
  if (!template) return ''
  return `固定模板版本：${template.title} ${version?.version || ''}。创建后不可更换，发布版将按该版本复现。`
})

const rules = computed(() => ({
  title: [
    { required: true, message: '请填写实验标题', trigger: 'blur' },
    { min: 2, max: 120, message: '实验标题需为 2-120 字符', trigger: 'blur' }
  ],
  projectId:
    isCreate.value && form.value.ownership === 'PROJECT'
      ? [{ required: true, message: '请选择所属项目', trigger: 'change' }]
      : [],
  roadmapId:
    isCreate.value && form.value.ownership === 'LEARNING'
      ? [{ required: true, message: '请选择学习路线', trigger: 'change' }]
      : [],
  learningUnitId:
    isCreate.value && form.value.ownership === 'LEARNING'
      ? [{ required: true, message: '请选择学习单元', trigger: 'change' }]
      : [],
  templateId: [{ required: true, message: '请选择模板', trigger: 'change' }],
  templateVersionId: [{ required: true, message: '请选择已发布模板版本', trigger: 'change' }]
}))

function emptyForm() {
  return {
    title: '',
    summary: '',
    ownership: 'PROJECT',
    projectId: null,
    roadmapId: null,
    learningUnitId: null,
    templateId: null,
    templateVersionId: null,
    contentJson: {},
    purpose: '',
    environment: '',
    steps: '',
    result: '',
    anomaly: '',
    conclusion: '',
    hardwareVersionRef: '',
    deviceInstanceRef: '',
    firmwareVersion: '',
    bomVersionRef: '',
    changeSummary: ''
  }
}

function goBack() {
  if (isCreate.value) router.push('/experiment/records')
  else router.push(`/experiment/records/${recordId.value}`)
}

async function loadCreateOptions() {
  const [projectRes, roadmapRes, templateRes] = await Promise.all([
    listProjects({ page: 1, pageSize: 50 }),
    listMyRoadmaps({ page: 1, pageSize: 50 }),
    listTemplates({ page: 1, pageSize: 50, status: TEMPLATE_VERSION_STATUS.PUBLISHED })
  ])
  projects.value = projectRes?.list || []
  roadmaps.value = roadmapRes?.list || []
  templates.value = templateRes?.list || []
}

async function loadEditRecord() {
  const detail = await getExperimentRecord(recordId.value)
  record.value = detail
  form.value = {
    ...emptyForm(),
    title: detail.title,
    summary: detail.summary || '',
    projectId: detail.projectId,
    learningUnitId: detail.learningUnitId,
    templateId: detail.templateId,
    templateVersionId: detail.templateVersionId,
    contentJson: { ...(detail.currentRevision?.contentJson || {}) },
    purpose: detail.currentRevision?.purpose || '',
    environment: detail.currentRevision?.environment || '',
    steps: detail.currentRevision?.steps || '',
    result: detail.currentRevision?.result || '',
    anomaly: detail.currentRevision?.anomaly || '',
    conclusion: detail.currentRevision?.conclusion || '',
    hardwareVersionRef: detail.currentRevision?.hardwareVersionRef || '',
    deviceInstanceRef: detail.currentRevision?.deviceInstanceRef || '',
    firmwareVersion: detail.currentRevision?.firmwareVersion || '',
    bomVersionRef: detail.currentRevision?.bomVersionRef || '',
    changeSummary: ''
  }
  templates.value = [
    {
      id: detail.templateId,
      title: detail.templateName || `模板 ${detail.templateId}`,
      scope: detail.projectId ? TEMPLATE_SCOPE.PROJECT : TEMPLATE_SCOPE.SYSTEM,
      projectId: detail.projectId
    }
  ]
  versionOptions.value = [
    {
      id: detail.templateVersionId,
      version: detail.templateVersion || '--',
      fieldCount: Object.keys(detail.schemaJson?.properties || {}).length
    }
  ]
  schemaJson.value = detail.schemaJson || {}
}

async function loadPage() {
  loading.value = true
  errorMessage.value = ''
  contentErrors.value = []
  try {
    if (isCreate.value) {
      await loadCreateOptions()
    } else {
      await loadEditRecord()
    }
  } catch (err) {
    notifyApiError(err)
    const { msg } = normalizeApiError(err)
    const traceId = errorTraceId(err)
    errorMessage.value = traceId ? `${msg}（traceId: ${traceId}）` : msg
  } finally {
    loading.value = false
  }
}

function onOwnershipChange() {
  form.value.projectId = null
  form.value.roadmapId = null
  form.value.learningUnitId = null
  stages.value = []
  form.value.templateId = null
  form.value.templateVersionId = null
  versionOptions.value = []
  schemaJson.value = null
  form.value.contentJson = {}
}

function onProjectChange() {
  form.value.templateId = null
  form.value.templateVersionId = null
  versionOptions.value = []
  schemaJson.value = null
  form.value.contentJson = {}
}

async function onRoadmapChange(roadmapId) {
  form.value.learningUnitId = null
  form.value.templateId = null
  form.value.templateVersionId = null
  versionOptions.value = []
  schemaJson.value = null
  stages.value = []
  if (!roadmapId) return
  try {
    stages.value = await listStages(roadmapId)
  } catch (err) {
    notifyApiError(err)
  }
}

async function onUnitChange(unitId) {
  const unit = unitOptions.value.find((u) => u.id === Number(unitId))
  if (!unit?.templateVersionId) return
  form.value.templateId = unit.templateId
  try {
    versionOptions.value = await listPublishedVersions(unit.templateId)
    form.value.templateVersionId = unit.templateVersionId
    await onVersionChange(unit.templateVersionId)
  } catch (err) {
    notifyApiError(err)
  }
}

async function onTemplateChange(templateId) {
  form.value.templateVersionId = null
  versionOptions.value = []
  schemaJson.value = null
  form.value.contentJson = {}
  if (!templateId) return
  try {
    versionOptions.value = await listPublishedVersions(templateId)
  } catch (err) {
    notifyApiError(err)
  }
}

async function onVersionChange(versionId) {
  schemaJson.value = null
  contentErrors.value = []
  if (!versionId) return
  try {
    const detail = await getVersionDetail(form.value.templateId, versionId)
    schemaJson.value = detail?.schemaJson || {}
    if (isCreate.value) form.value.contentJson = defaultContent(schemaJson.value)
  } catch (err) {
    notifyApiError(err)
  }
}

function defaultContent(schema) {
  const next = {}
  for (const [key, def] of Object.entries(schema?.properties || {})) {
    if (def?.default !== undefined && def?.default !== null) next[key] = def.default
    else if (def?.type === 'boolean') next[key] = false
    else next[key] = undefined
  }
  return next
}

function buildPayload() {
  return {
    title: form.value.title.trim(),
    summary: form.value.summary.trim() || null,
    projectId: form.value.ownership === 'PROJECT' ? form.value.projectId : null,
    learningUnitId: form.value.ownership === 'LEARNING' ? form.value.learningUnitId : null,
    templateVersionId: form.value.templateVersionId,
    contentJson: { ...form.value.contentJson },
    purpose: form.value.purpose.trim() || null,
    environment: form.value.environment.trim() || null,
    steps: form.value.steps.trim() || null,
    result: form.value.result.trim() || null,
    anomaly: form.value.anomaly.trim() || null,
    conclusion: form.value.conclusion.trim() || null,
    hardwareVersionRef: form.value.hardwareVersionRef.trim() || null,
    deviceInstanceRef: form.value.deviceInstanceRef.trim() || null,
    firmwareVersion: form.value.firmwareVersion.trim() || null,
    bomVersionRef: form.value.bomVersionRef.trim() || null
  }
}

function handleActionError(err, reload) {
  if (isVersionConflict(err)) {
    promptVersionConflict(reload)
    return
  }
  if (isExperimentContentInvalid(err)) {
    contentErrors.value = experimentContentErrors(err)
    ElMessage.error('实验内容不符合固定模板版本，请按字段提示修正')
    return
  }
  if (isRevisionImmutable(err)) {
    ElMessage.warning('当前修订不可编辑，请刷新后查看最新状态')
    return
  }
  if (isExperimentDenied(err)) {
    ElMessage.warning('当前账号无权执行该操作')
    return
  }
  notifyApiError(err)
}

async function handleSave(withSubmit) {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  contentErrors.value = []

  if (isCreate.value) {
    saving.value = true
    try {
      const created = await createExperimentRecord(buildPayload(), createIdempotencyKey('w43-record-create'))
      if (withSubmit) {
        await submitExperimentRecord(
          created.id,
          {
            revisionId: created.currentRevision.id,
            recordVersion: created.version,
            revisionVersion: created.currentRevision.version
          },
          createIdempotencyKey('w43-record-submit')
        )
        ElMessage.success('记录已创建并提交评审')
      } else {
        ElMessage.success('记录草稿已保存')
      }
      router.push(`/experiment/records/${created.id}`)
    } catch (err) {
      handleActionError(err, loadPage)
    } finally {
      saving.value = false
    }
    return
  }

  if (withSubmit) submitting.value = true
  else saving.value = true
  try {
    const saved = await saveRevision(recordId.value, revisionId.value, {
      ...buildPayload(),
      recordVersion: record.value.version,
      revisionVersion: record.value.currentRevision.version,
      changeSummary: form.value.changeSummary.trim() || null
    })
    if (withSubmit) {
      await submitExperimentRecord(
        recordId.value,
        {
          revisionId: saved.revisionId,
          recordVersion: saved.recordVersion,
          revisionVersion: saved.revisionVersion
        },
        createIdempotencyKey('w43-record-submit')
      )
      ElMessage.success('修订已保存并提交评审')
    } else {
      ElMessage.success('修订已保存')
    }
    router.push(`/experiment/records/${recordId.value}`)
  } catch (err) {
    handleActionError(err, loadPage)
  } finally {
    saving.value = false
    submitting.value = false
  }
}

onMounted(loadPage)
</script>

<style scoped>
.record-editor-page {
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

.immutable-alert {
  margin-bottom: var(--usn-space-2);
}

.editor-form {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.editor-card {
  border: 1px solid var(--usn-line);
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  font-weight: 600;
  color: var(--usn-ink-900);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--usn-space-4);
}

.full-width {
  width: 100%;
}

.template-empty {
  margin-bottom: var(--usn-space-3);
}

.fixed-version-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: var(--usn-space-2) var(--usn-space-3);
  margin-top: var(--usn-space-2);
  background: #f5f3ff;
  border: 1px solid #ddd6fe;
  border-radius: var(--usn-radius-sm);
  font-size: var(--usn-font-size-help);
  color: var(--usn-purple-600);
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

@media (max-width: 767px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .editor-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .editor-actions :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>
