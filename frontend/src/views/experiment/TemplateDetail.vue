<template>
  <div class="template-detail-page">
    <div class="detail-back">
      <el-button link :icon="ArrowLeft" @click="goBack">返回实验模板</el-button>
    </div>

    <RegionState
      v-if="pageState !== 'success'"
      :state="pageState"
      variant="section"
      :description="pageStateDescription"
      @retry="loadAll"
    />

    <template v-else>
      <header class="detail-header">
        <div class="detail-header__main">
          <h1 class="page-title">{{ template.title }}</h1>
          <div class="detail-header__tags">
            <el-tag :type="scopeMeta(template.scope).tagType" size="small" effect="light">
              {{ scopeMeta(template.scope).label }}
            </el-tag>
            <el-tag v-if="template.category" size="small" effect="plain">{{ template.category }}</el-tag>
            <el-tag v-if="template.projectName" size="small" effect="plain" type="info">
              {{ template.projectName }}
            </el-tag>
            <el-tag v-if="hasDraft" size="small" effect="light" type="warning">存在草稿版本</el-tag>
          </div>
          <p class="detail-header__desc">{{ template.description || '暂无模板描述' }}</p>
          <p class="detail-header__meta text-help muted">
            {{ template.createdByName || '未知' }} 创建于 {{ formatDateTime(template.createTime) }} · 最近更新 {{ formatDateTime(template.updateTime) }}
          </p>
        </div>

        <div class="detail-header__actions">
          <el-button v-if="manage" :icon="Edit" @click="openEdit">编辑基本信息</el-button>
          <el-button v-if="canCopy" type="primary" plain :icon="CopyDocument" @click="openCopy">复制到项目</el-button>
        </div>
      </header>

      <div class="detail-grid">
        <!-- 左：版本历史时间线 -->
        <section class="version-panel">
          <div class="panel-head">
            <h2 class="panel-title">版本历史</h2>
            <el-select
              v-if="manage"
              v-model="statusFilter"
              size="small"
              class="version-filter"
              @change="loadVersions"
            >
              <el-option label="全部版本" value="ALL" />
              <el-option label="草稿" :value="TEMPLATE_VERSION_STATUS.DRAFT" />
              <el-option label="已发布" :value="TEMPLATE_VERSION_STATUS.PUBLISHED" />
              <el-option label="已废弃" :value="TEMPLATE_VERSION_STATUS.DEPRECATED" />
            </el-select>
          </div>

          <p v-if="!manage" class="panel-hint text-help muted">
            已发布版本永久可读，模板更新不会改变历史版本结构
          </p>

          <RegionState
            v-if="!versions.length && !versionsLoading"
            state="empty"
            variant="list"
            description="暂无版本记录"
          />

          <VersionTimeline
            v-else
            :versions="versions"
            :selected-id="selectedVersionId"
            :manage="manage"
            @select="selectVersion"
            @edit="goEditor"
            @publish="handlePublish"
            @deprecate="handleDeprecate"
          />
        </section>

        <!-- 右：版本详情（只读） -->
        <section class="version-detail" v-loading="detailLoading">
          <RegionState
            v-if="!selectedVersion"
            state="empty"
            variant="card"
            description="请选择左侧版本查看字段结构"
          />

          <template v-else>
            <header class="version-detail__head">
              <div class="version-detail__title">
                <span class="version-number" :class="{ 'is-deprecated': selectedVersion.status === 'DEPRECATED' }">
                  {{ selectedVersion.version }}
                </span>
                <VersionStatusTag :status="selectedVersion.status" />
                <span class="text-help muted">{{ selectedVersion.fieldCount }} 个字段</span>
              </div>
              <el-button
                v-if="manage && selectedVersion.status === 'DRAFT'"
                size="small"
                type="primary"
                plain
                @click="goEditor(selectedVersion)"
              >
                编辑草稿
              </el-button>
            </header>

            <p class="version-detail__changelog">
              {{ selectedVersion.changelog || '暂无变更说明' }}
            </p>

            <dl class="version-detail__meta">
              <div>
                <dt>状态</dt>
                <dd>{{ versionStatusMeta(selectedVersion.status).label }}</dd>
              </div>
              <div>
                <dt>{{ selectedVersion.status === 'DRAFT' ? '最近更新' : '发布时间' }}</dt>
                <dd>{{ selectedVersion.status === 'DRAFT' ? formatDateTime(selectedVersion.updateTime) : formatDateTime(selectedVersion.publishedAt) }}</dd>
              </div>
              <div v-if="selectedVersion.status !== 'DRAFT'">
                <dt>发布人</dt>
                <dd>{{ selectedVersion.publishedByName || '未知' }}</dd>
              </div>
              <div v-if="selectedVersion.status === 'DEPRECATED'">
                <dt>废弃时间</dt>
                <dd>{{ formatDateTime(selectedVersion.deprecatedAt) }}</dd>
              </div>
            </dl>

            <el-alert
              v-if="selectedVersion.status === 'DEPRECATED'"
              type="warning"
              :closable="false"
              class="deprecated-alert"
              show-icon
              :title="`该版本已废弃：${selectedVersion.deprecatedReason || '未填写原因'}`"
              description="已关联该版本的学习单元仍可继续使用，但新关联只能选择其他已发布版本。"
            />

            <el-tabs v-model="detailTab" class="version-tabs">
              <el-tab-pane label="字段结构" name="fields">
                <RegionState
                  v-if="!fields.length"
                  state="empty"
                  variant="list"
                  description="该版本暂无字段"
                />
                <el-table v-else :data="fields" size="small" class="field-table">
                  <el-table-column type="index" label="#" width="48" />
                  <el-table-column prop="key" label="字段 key" min-width="120">
                    <template #default="{ row }">
                      <code class="field-key">{{ row.key }}</code>
                    </template>
                  </el-table-column>
                  <el-table-column prop="title" label="显示名" min-width="110" />
                  <el-table-column label="类型" width="90">
                    <template #default="{ row }">{{ fieldTypeLabel(row.type) }}</template>
                  </el-table-column>
                  <el-table-column label="控件" width="100">
                    <template #default="{ row }">{{ widgetLabel(row.widget) }}</template>
                  </el-table-column>
                  <el-table-column label="必填" width="72">
                    <template #default="{ row }">
                      <el-tag v-if="isRequired(row.key)" size="small" type="danger" effect="plain">必填</el-tag>
                      <span v-else class="text-help muted">否</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="unit" label="单位" width="72">
                    <template #default="{ row }">{{ row.unit || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="约束" min-width="180">
                    <template #default="{ row }">{{ constraintText(row) }}</template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>

              <el-tab-pane label="预览填写" name="preview">
                <SchemaPreview :schema-json="selectedVersion.schemaJson || {}" :validator="validatePreviewData" />
              </el-tab-pane>

              <el-tab-pane label="原始 Schema" name="raw">
                <p class="raw-hint text-help muted">
                  原始 JSON Schema 仅只读展示。本阶段仅支持扁平对象与 string / number / integer / boolean。
                </p>
                <pre class="raw-schema">{{ rawSchema }}</pre>
              </el-tab-pane>
            </el-tabs>
          </template>
        </section>
      </div>
    </template>

    <TemplateFormDialog
      v-model="editVisible"
      mode="edit"
      :template="template"
      :submitting="submitting"
      @submit="handleUpdate"
    />

    <CopyTemplateDialog
      v-model="copyVisible"
      :template="template"
      :projects="copyTargetProjects"
      :submitting="submitting"
      @submit="handleCopy"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Edit, CopyDocument } from '@element-plus/icons-vue'
import {
  getTemplateDetail,
  updateTemplate,
  copyTemplate,
  listVersions,
  getVersionDetail,
  publishVersion,
  deprecateVersion,
  validateVersionData,
  canManageTemplate,
  canManageProjectTemplates,
  canManageSystemTemplates,
  TEMPLATE_SCOPE,
  TEMPLATE_VERSION_STATUS,
  templateScopeMeta,
  templateVersionStatusMeta,
  SCHEMA_FIELD_TYPE_META,
  SCHEMA_WIDGET_META
} from '@/api/experimentTemplates'
import { listProjects } from '@/api/projects'
import { notifyApiError, isTemplateNotFound, isTemplateDenied, schemaErrors } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'
import RegionState from '@/components/RegionState.vue'
import VersionTimeline from '@/components/templates/VersionTimeline.vue'
import VersionStatusTag from '@/components/templates/VersionStatusTag.vue'
import SchemaPreview from '@/components/templates/SchemaPreview.vue'
import TemplateFormDialog from '@/components/templates/TemplateFormDialog.vue'
import CopyTemplateDialog from '@/components/templates/CopyTemplateDialog.vue'

const route = useRoute()
const router = useRouter()

const templateId = computed(() => Number(route.params.id))

const pageState = ref('loading')
const pageStateDescription = ref('')
const template = ref(null)
const versions = ref([])
const versionsLoading = ref(false)
const selectedVersionId = ref(null)
const selectedVersion = ref(null)
const detailLoading = ref(false)
const detailTab = ref('fields')
const statusFilter = ref('ALL')
const submitting = ref(false)
const editVisible = ref(false)
const copyVisible = ref(false)
const projects = ref([])

const manage = computed(() => canManageTemplate(template.value))
const hasDraft = computed(() => Boolean(template.value?.hasDraft))
const canCopy = computed(() =>
  template.value?.scope === TEMPLATE_SCOPE.SYSTEM &&
  projects.value.some((p) => canManageProjectTemplates(p.id))
)
const copyTargetProjects = computed(() => projects.value.filter((p) => canManageProjectTemplates(p.id)))

const fields = computed(() => {
  const props = selectedVersion.value?.schemaJson?.properties
  if (!props || typeof props !== 'object') return []
  return Object.entries(props)
    .map(([key, def]) => ({ key, ...(def || {}) }))
    .sort((a, b) => (Number(a.displayOrder) || 0) - (Number(b.displayOrder) || 0) || a.key.localeCompare(b.key))
})

const requiredSet = computed(() => new Set(Array.isArray(selectedVersion.value?.schemaJson?.required) ? selectedVersion.value.schemaJson.required : []))

const rawSchema = computed(() =>
  selectedVersion.value?.schemaJson ? JSON.stringify(selectedVersion.value.schemaJson, null, 2) : '{}'
)

function scopeMeta(scope) {
  return templateScopeMeta(scope)
}

function versionStatusMeta(status) {
  return templateVersionStatusMeta(status)
}

function isRequired(key) {
  return requiredSet.value.has(key)
}

function fieldTypeLabel(type) {
  return SCHEMA_FIELD_TYPE_META[type]?.label || type || '-'
}

function widgetLabel(widget) {
  return SCHEMA_WIDGET_META[widget]?.label || widget || '-'
}

function constraintText(field) {
  const parts = []
  if (Array.isArray(field.enum) && field.enum.length) parts.push(`取值：${field.enum.join('、')}`)
  if (typeof field.minimum === 'number' || typeof field.maximum === 'number') {
    const min = typeof field.minimum === 'number' ? field.minimum : '-∞'
    const max = typeof field.maximum === 'number' ? field.maximum : '+∞'
    parts.push(`范围：${min} ~ ${max}`)
  }
  if (field.minLength != null || field.maxLength != null) {
    const min = field.minLength != null ? field.minLength : 0
    const max = field.maxLength != null ? field.maxLength : '不限'
    parts.push(`长度：${min} ~ ${max}`)
  }
  if (field.default !== undefined && field.default !== null && field.default !== '') parts.push(`默认：${field.default}`)
  return parts.length ? parts.join(' · ') : '-'
}

async function loadProjects() {
  try {
    const res = await listProjects({ page: 1, pageSize: 50 })
    projects.value = res?.list || []
  } catch (err) {
    notifyApiError(err)
    projects.value = []
  }
}

async function loadVersions() {
  versionsLoading.value = true
  try {
    const res = await listVersions(templateId.value, { status: statusFilter.value, page: 1, pageSize: 100 })
    versions.value = res?.list || []
    if (versions.value.length && !versions.value.some((v) => v.id === selectedVersionId.value)) {
      await selectVersion(versions.value[0].id)
    } else if (!versions.value.length) {
      selectedVersion.value = null
      selectedVersionId.value = null
    }
  } catch (err) {
    notifyApiError(err)
    versions.value = []
  } finally {
    versionsLoading.value = false
  }
}

async function selectVersion(versionId) {
  selectedVersionId.value = versionId
  detailLoading.value = true
  try {
    selectedVersion.value = await getVersionDetail(templateId.value, versionId)
  } catch (err) {
    notifyApiError(err)
    selectedVersion.value = null
  } finally {
    detailLoading.value = false
  }
}

async function loadAll() {
  pageState.value = 'loading'
  try {
    template.value = await getTemplateDetail(templateId.value)
    await loadProjects()
    await loadVersions()
    pageState.value = 'success'
  } catch (err) {
    if (isTemplateNotFound(err)) {
      pageState.value = 'empty'
      pageStateDescription.value = '实验模板不存在或不可见'
    } else if (isTemplateDenied(err)) {
      pageState.value = 'permission'
      pageStateDescription.value = '当前账号无权访问该实验模板'
    } else {
      pageState.value = 'error'
      pageStateDescription.value = '实验模板加载失败'
      notifyApiError(err)
    }
  }
}

async function refreshAfterMutation(selectVersionId = null) {
  template.value = await getTemplateDetail(templateId.value)
  await loadVersions()
  if (selectVersionId != null) await selectVersion(selectVersionId)
}

function validatePreviewData(data) {
  return validateVersionData(templateId.value, selectedVersion.value.id, data)
}

function goBack() {
  router.push('/experiment/templates')
}

function goEditor(version) {
  router.push(`/experiment/templates/${templateId.value}/editor/${version.id}`)
}

function openEdit() {
  editVisible.value = true
}

function openCopy() {
  copyVisible.value = true
}

async function handleUpdate(payload) {
  submitting.value = true
  try {
    template.value = await updateTemplate(templateId.value, payload)
    ElMessage.success('模板基本信息已更新')
    editVisible.value = false
  } catch (err) {
    notifyApiError(err)
  } finally {
    submitting.value = false
  }
}

async function handleCopy(payload) {
  submitting.value = true
  try {
    const created = await copyTemplate(templateId.value, payload)
    ElMessage.success('已复制为项目模板')
    copyVisible.value = false
    if (created?.id) router.push(`/experiment/templates/${created.id}`)
  } catch (err) {
    notifyApiError(err)
  } finally {
    submitting.value = false
  }
}

async function handlePublish(version) {
  try {
    const { value } = await ElMessageBox.prompt(
      `发布后将生成不可修改的正式版本，同时自动创建下一个草稿。请确认版本 ${version.version} 的字段结构。`,
      '发布版本确认',
      {
        confirmButtonText: '确认发布',
        cancelButtonText: '取消',
        inputPlaceholder: '变更说明（选填，最多 500 字）',
        inputValidator: (val) => !val || String(val).length <= 500 || '变更说明最多 500 字符'
      }
    )
    submitting.value = true
    const res = await publishVersion(templateId.value, version.id, { changelog: value || undefined })
    ElMessage.success(`已发布 ${res?.publishedVersion?.version || ''}，并自动创建 ${res?.newDraftVersion?.version || ''} 草稿`)
    await refreshAfterMutation(res?.publishedVersion?.id ?? null)
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      const errors = schemaErrors(err)
      if (errors.length) {
        ElMessage.error(`Schema 校验失败：${errors.map((e) => e.message).join('；')}`)
      } else {
        notifyApiError(err)
      }
    }
  } finally {
    submitting.value = false
  }
}

async function handleDeprecate(version) {
  try {
    const { value } = await ElMessageBox.prompt(
      `废弃后该版本不可恢复，已关联的学习单元仍可继续使用但会显示「已废弃」提示。确认废弃 ${version.version}？`,
      '废弃版本确认',
      {
        confirmButtonText: '确认废弃',
        cancelButtonText: '取消',
        inputPlaceholder: '废弃原因（选填，最多 500 字）',
        inputValidator: (val) => !val || String(val).length <= 500 || '废弃原因最多 500 字符'
      }
    )
    submitting.value = true
    await deprecateVersion(templateId.value, version.id, { reason: value || undefined })
    ElMessage.success(`版本 ${version.version} 已废弃`)
    await refreshAfterMutation(version.id)
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') notifyApiError(err)
  } finally {
    submitting.value = false
  }
}

watch(
  () => route.params.id,
  (next, prev) => {
    if (next && next !== prev) loadAll()
  }
)

onMounted(loadAll)
</script>

<style scoped>
.template-detail-page {
  max-width: var(--usn-content-max-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.detail-back {
  display: flex;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-4);
  flex-wrap: wrap;
  padding: var(--usn-space-4);
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-top: 3px solid var(--usn-purple-600);
  border-radius: var(--usn-radius-md);
  box-shadow: var(--usn-shadow-panel);
}

.detail-header__main {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  min-width: 0;
  flex: 1 1 320px;
}

.page-title {
  margin: 0;
  font-size: var(--usn-font-size-page-title);
  font-weight: 700;
  color: var(--usn-ink-900);
}

.detail-header__tags {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.detail-header__desc {
  margin: 0;
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
  line-height: 1.6;
}

.detail-header__meta {
  margin: 0;
}

.detail-header__actions {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.detail-grid {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: var(--usn-space-5);
  align-items: start;
}

.version-panel {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-2);
}

.panel-title {
  margin: 0;
  font-size: var(--usn-font-size-panel-title);
  font-weight: 700;
  color: var(--usn-ink-900);
}

.version-filter {
  width: 128px;
}

.panel-hint {
  margin: 0;
  line-height: 1.5;
}

.version-detail {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
  padding: var(--usn-space-4);
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  box-shadow: var(--usn-shadow-panel);
  min-width: 0;
  min-height: 320px;
}

.version-detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.version-detail__title {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.version-number {
  font-size: 18px;
  font-weight: 700;
  color: var(--usn-ink-900);
  font-variant-numeric: tabular-nums;
}

.version-number.is-deprecated {
  color: var(--usn-ink-500);
  text-decoration: line-through;
}

.version-detail__changelog {
  margin: 0;
  font-size: var(--usn-font-size-body);
  color: var(--usn-ink-700);
  line-height: 1.6;
}

.version-detail__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--usn-space-3);
  margin: 0;
  padding: var(--usn-space-3) 0;
  border-top: 1px solid var(--usn-line);
  border-bottom: 1px solid var(--usn-line);
}

.version-detail__meta div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.version-detail__meta dt {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.version-detail__meta dd {
  margin: 0;
  font-size: var(--usn-font-size-body);
  font-weight: 600;
  color: var(--usn-ink-900);
}

.deprecated-alert {
  margin: 0;
}

.field-table :deep(.field-key) {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: var(--usn-blue-700);
}

.raw-hint {
  margin: 0 0 var(--usn-space-3);
  line-height: 1.5;
}

.raw-schema {
  margin: 0;
  padding: var(--usn-space-3);
  background: #f7f9fb;
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-sm);
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
  color: var(--usn-ink-700);
  overflow-x: auto;
  white-space: pre;
}

@media (max-width: 1024px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .detail-header {
    padding: var(--usn-space-3);
  }

  .detail-header__actions {
    width: 100%;
  }

  .detail-header__actions :deep(.el-button) {
    flex: 1;
  }

  .version-detail {
    padding: var(--usn-space-3);
  }
}
</style>
