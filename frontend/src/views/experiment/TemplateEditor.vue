<template>
  <div class="template-editor-page">
    <div class="editor-back">
      <el-button link :icon="ArrowLeft" @click="goDetail">返回模板详情</el-button>
    </div>

    <RegionState
      v-if="pageState !== 'success'"
      :state="pageState"
      variant="section"
      :description="pageStateDescription"
      @retry="loadAll"
    />

    <div v-else-if="!editable" class="editor-readonly">
      <RegionState
        state="not-available"
        variant="section"
        description="仅草稿版本可编辑"
        sub-title="已发布与已废弃版本不可修改，请返回详情页查看或编辑当前草稿"
      />
      <el-button type="primary" @click="goDetail">返回模板详情</el-button>
    </div>

    <template v-else>
      <header class="editor-header">
        <div class="editor-header__main">
          <h1 class="page-title">{{ template.title }}</h1>
          <div class="editor-header__tags">
            <span class="version-badge">草稿 {{ version.version }}</span>
            <VersionStatusTag :status="version.status" />
            <el-tag size="small" effect="plain" type="info">
              {{ fields.length }}/{{ SCHEMA_MAX_FIELD_COUNT }} 字段
            </el-tag>
            <el-tag v-if="validation.valid" size="small" type="success" effect="plain">Schema 校验通过</el-tag>
            <el-tag v-else size="small" type="danger" effect="plain">{{ validation.errors.length }} 项 Schema 问题</el-tag>
            <el-tag v-if="dirty" size="small" type="warning" effect="plain">有未保存修改</el-tag>
          </div>
        </div>

        <div class="editor-header__actions">
          <el-input
            v-model="changelog"
            class="changelog-input"
            size="default"
            maxlength="500"
            placeholder="本次变更说明（选填）"
          />
          <el-button :disabled="!dirty" @click="reloadVersion">放弃修改</el-button>
          <el-button :loading="saving" :disabled="!dirty" @click="handleSave">保存草稿</el-button>
          <el-button
            type="primary"
            :loading="publishing"
            :disabled="!validation.valid || !fields.length"
            @click="handlePublish"
          >
            发布版本
          </el-button>
        </div>
      </header>

      <div class="mobile-steps">
        <el-radio-group v-model="mobileStep" size="small">
          <el-radio-button value="fields">字段列表</el-radio-button>
          <el-radio-button value="props">字段属性</el-radio-button>
          <el-radio-button value="preview">预览</el-radio-button>
          <el-radio-button value="schema">预检</el-radio-button>
        </el-radio-group>
      </div>

      <div class="editor-grid" :class="`editor-grid--${mobileStep}`">
        <!-- 左：字段列表 -->
        <section class="field-panel">
          <div class="panel-head">
            <h2 class="panel-title">字段列表</h2>
            <el-button
              size="small"
              type="primary"
              plain
              :icon="Plus"
              :disabled="fields.length >= SCHEMA_MAX_FIELD_COUNT"
              @click="addField"
            >
              添加字段
            </el-button>
          </div>
          <p class="panel-hint text-help muted">拖拽或使用上下箭头调整顺序，顺序即实验填写顺序（displayOrder）</p>

          <RegionState
            v-if="!fields.length"
            state="empty"
            variant="list"
            description="暂无字段，点击「添加字段」开始配置"
          />

          <ul v-else class="field-list">
            <li
              v-for="(field, index) in fields"
              :key="field.uid"
              class="field-item"
              :class="{
                'field-item--active': field.uid === activeUid,
                'field-item--invalid': Boolean(fieldErrorMap[field.key]),
                'field-item--dragging': field.uid === draggingUid
              }"
              draggable="true"
              @dragstart="handleDragStart(field.uid)"
              @dragover.prevent
              @drop="handleDrop(field.uid)"
              @dragend="handleDragEnd"
              @click="selectField(field)"
            >
              <span class="field-item__handle" aria-hidden="true"><el-icon><Rank /></el-icon></span>
              <div class="field-item__body">
                <div class="field-item__title">
                  <span class="field-item__name">{{ field.title || '未命名字段' }}</span>
                  <el-tag v-if="field.required" size="small" type="danger" effect="plain">必填</el-tag>
                </div>
                <div class="field-item__meta text-help muted">
                  <code>{{ field.key || '未设置 key' }}</code> · {{ fieldTypeLabel(field.type) }}
                </div>
              </div>
              <div class="field-item__ops" @click.stop>
                <el-button size="small" text :icon="ArrowUp" :disabled="index === 0" aria-label="上移" @click="moveField(index, -1)" />
                <el-button size="small" text :icon="ArrowDown" :disabled="index === fields.length - 1" aria-label="下移" @click="moveField(index, 1)" />
                <el-button size="small" text type="danger" :icon="Delete" aria-label="删除字段" @click="removeField(index)" />
              </div>
            </li>
          </ul>
        </section>

        <!-- 右：属性面板 / 预览 -->
        <section class="prop-panel">
          <el-tabs v-model="panelTab" class="panel-tabs">
            <el-tab-pane label="字段属性" name="props">
              <RegionState
                v-if="!activeField"
                state="empty"
                variant="section"
                description="请选择左侧字段进行配置"
              />
              <template v-else>
                <el-form label-width="88px" class="prop-form">
                  <el-form-item label="字段 key">
                    <el-input v-model="activeField.key" maxlength="50" placeholder="小写字母/数字/下划线，如 voltage" @input="markDirty" />
                    <p class="form-hint text-help muted">字段 key 用于实验记录引用，保存后建议保持稳定</p>
                  </el-form-item>

                  <el-form-item label="显示名称">
                    <el-input v-model="activeField.title" maxlength="40" show-word-limit placeholder="如：电压" @input="markDirty" />
                  </el-form-item>

                  <el-form-item label="字段类型">
                    <el-select v-model="activeField.type" class="form-control" @change="handleTypeChange">
                      <el-option v-for="t in TYPE_OPTIONS" :key="t" :label="fieldTypeLabel(t)" :value="t" />
                    </el-select>
                  </el-form-item>

                  <el-form-item label="控件">
                    <el-select v-model="activeField.widget" class="form-control" @change="markDirty">
                      <el-option v-for="w in widgetOptions" :key="w" :label="widgetLabel(w)" :value="w" />
                    </el-select>
                  </el-form-item>

                  <el-form-item label="单位">
                    <el-input v-model="activeField.unit" maxlength="20" placeholder="如：V、℃（选填）" @input="markDirty" />
                  </el-form-item>

                  <el-form-item label="字段说明">
                    <el-input
                      v-model="activeField.description"
                      type="textarea"
                      :rows="2"
                      maxlength="200"
                      show-word-limit
                      placeholder="填写提示（选填）"
                      @input="markDirty"
                    />
                  </el-form-item>

                  <el-form-item label="是否必填">
                    <el-switch v-model="activeField.required" @change="markDirty" />
                  </el-form-item>

                  <el-form-item v-if="activeField.type === 'string'" label="可选值">
                    <el-input
                      v-model="activeField.enumText"
                      placeholder="用、或逗号分隔，如：直流、交流（留空为自由输入）"
                      @input="markDirty"
                    />
                  </el-form-item>

                  <el-form-item v-if="activeField.type === 'string'" label="长度限制">
                    <div class="range-row">
                      <el-input-number v-model="activeField.minLength" :min="0" controls-position="right" placeholder="最小" @change="markDirty" />
                      <span class="range-sep">~</span>
                      <el-input-number v-model="activeField.maxLength" :min="0" controls-position="right" placeholder="最大" @change="markDirty" />
                    </div>
                  </el-form-item>

                  <el-form-item v-if="isNumeric(activeField.type)" label="取值范围">
                    <div class="range-row">
                      <el-input-number v-model="activeField.minimum" controls-position="right" placeholder="最小" @change="markDirty" />
                      <span class="range-sep">~</span>
                      <el-input-number v-model="activeField.maximum" controls-position="right" placeholder="最大" @change="markDirty" />
                    </div>
                  </el-form-item>

                  <el-form-item label="默认值">
                    <el-switch v-if="activeField.type === 'boolean'" v-model="activeField.defaultValue" @change="markDirty" />
                    <el-input-number
                      v-else-if="isNumeric(activeField.type)"
                      v-model="activeField.defaultValue"
                      controls-position="right"
                      @change="markDirty"
                    />
                    <el-input v-else v-model="activeField.defaultValue" maxlength="200" placeholder="选填" @input="markDirty" />
                  </el-form-item>
                </el-form>

                <div v-if="activeFieldErrors.length" class="field-errors">
                  <p class="field-errors__title">该字段存在 {{ activeFieldErrors.length }} 项问题：</p>
                  <ul>
                    <li v-for="(err, i) in activeFieldErrors" :key="i">
                      <code>{{ err.path }}</code> {{ err.message }}
                    </li>
                  </ul>
                </div>
                <p v-else class="field-ok text-help">该字段配置符合受控 Schema 子集</p>
              </template>
            </el-tab-pane>

            <el-tab-pane label="预览填写" name="preview">
              <p class="panel-hint text-help muted">
                预览按当前编辑中的 Schema 实时渲染；未保存修改时使用本地镜像校验，保存后走后端校验接口。
              </p>
              <SchemaPreview :schema-json="draftSchema" :validator="previewValidator" />
            </el-tab-pane>

            <el-tab-pane label="Schema 预检" name="schema">
              <div class="schema-check">
                <el-alert
                  v-if="validation.valid"
                  type="success"
                  :closable="false"
                  show-icon
                  title="Schema 符合受控子集，可保存或发布"
                  description="仅支持扁平对象与 string / number / integer / boolean，最多 50 个字段。"
                />
                <div v-else class="schema-check__errors">
                  <p class="schema-check__title">发现 {{ validation.errors.length }} 项 Schema 问题：</p>
                  <ul>
                    <li v-for="(err, i) in validation.errors" :key="i">
                      <code>{{ err.path }}</code> {{ err.message }}
                    </li>
                  </ul>
                </div>

                <p class="raw-hint text-help muted">原始 JSON Schema（只读，随编辑实时更新）</p>
                <pre class="raw-schema">{{ rawSchema }}</pre>
              </div>
            </el-tab-pane>
          </el-tabs>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowUp, ArrowDown, Plus, Delete, Rank } from '@element-plus/icons-vue'
import {
  getTemplateDetail,
  getVersionDetail,
  updateDraftVersion,
  publishVersion,
  validateVersionData,
  validateSchemaJson,
  validateDataAgainstSchema,
  canManageTemplate,
  widgetsForType,
  SCHEMA_MAX_FIELD_COUNT,
  SCHEMA_FIELD_TYPE,
  SCHEMA_FIELD_TYPE_META,
  SCHEMA_WIDGET_META
} from '@/api/experimentTemplates'
import { notifyApiError, isTemplateNotFound, isTemplateDenied, schemaErrors } from '@/utils/apiError'
import RegionState from '@/components/RegionState.vue'
import VersionStatusTag from '@/components/templates/VersionStatusTag.vue'
import SchemaPreview from '@/components/templates/SchemaPreview.vue'

const route = useRoute()
const router = useRouter()

const templateId = computed(() => Number(route.params.id))
const versionId = computed(() => Number(route.params.versionId))

const TYPE_OPTIONS = Object.values(SCHEMA_FIELD_TYPE)

const pageState = ref('loading')
const pageStateDescription = ref('')
const template = ref(null)
const version = ref(null)
const fields = ref([])
const activeUid = ref(null)
const draggingUid = ref(null)
const panelTab = ref('props')
const mobileStep = ref('fields')
const changelog = ref('')
const dirty = ref(false)
const saving = ref(false)
const publishing = ref(false)

let uidSeq = 0

const editable = computed(() => version.value?.status === 'DRAFT' && canManageTemplate(template.value))

const activeField = computed(() => fields.value.find((f) => f.uid === activeUid.value) || null)

const widgetOptions = computed(() => (activeField.value ? widgetsForType(activeField.value.type) : []))

const draftSchema = computed(() => toSchemaJson())

const validation = computed(() => validateSchemaJson(draftSchema.value))

const fieldErrorMap = computed(() => {
  const map = {}
  for (const err of validation.value.errors) {
    const m = /^properties\.([^.]+)/.exec(err.path)
    if (m && !map[m[1]]) map[m[1]] = err.message
  }
  return map
})

const activeFieldErrors = computed(() =>
  activeField.value
    ? validation.value.errors.filter((err) => err.path === `properties.${activeField.value.key}` || err.path.startsWith(`properties.${activeField.value.key}.`))
    : []
)

const rawSchema = computed(() => JSON.stringify(draftSchema.value, null, 2))

function isNumeric(type) {
  return type === SCHEMA_FIELD_TYPE.NUMBER || type === SCHEMA_FIELD_TYPE.INTEGER
}

function fieldTypeLabel(type) {
  return SCHEMA_FIELD_TYPE_META[type]?.label || type || '-'
}

function widgetLabel(widget) {
  return SCHEMA_WIDGET_META[widget]?.label || widget || '-'
}

function makeField(partial = {}) {
  const type = partial.type || SCHEMA_FIELD_TYPE.STRING
  return {
    uid: ++uidSeq,
    key: partial.key || '',
    title: partial.title || '',
    type,
    widget: partial.widget || widgetsForType(type)[0],
    description: partial.description || '',
    unit: partial.unit || '',
    required: Boolean(partial.required),
    enumText: Array.isArray(partial.enum) ? partial.enum.join('、') : '',
    minimum: partial.minimum ?? null,
    maximum: partial.maximum ?? null,
    minLength: partial.minLength ?? null,
    maxLength: partial.maxLength ?? null,
    defaultValue: partial.default ?? null
  }
}

function parseEnum(field) {
  const raw = String(field.enumText || '').trim()
  if (!raw) return []
  return raw
    .split(/[、,，\s]+/)
    .filter(Boolean)
    .map((token) => {
      if (isNumeric(field.type)) return Number(token)
      if (field.type === SCHEMA_FIELD_TYPE.BOOLEAN) return token === 'true'
      return token
    })
}

function coerceDefault(field) {
  const val = field.defaultValue
  if (val === null || val === undefined || val === '') return undefined
  if (field.type === SCHEMA_FIELD_TYPE.BOOLEAN) return Boolean(val)
  if (isNumeric(field.type)) {
    const num = Number(val)
    return Number.isNaN(num) ? undefined : num
  }
  return String(val)
}

function toSchemaJson() {
  const properties = {}
  const required = []
  fields.value.forEach((field, index) => {
    const def = { type: field.type, title: field.title, displayOrder: index + 1 }
    if (field.widget) def.widget = field.widget
    if (field.description) def.description = field.description
    if (field.unit) def.unit = field.unit
    const enumArr = parseEnum(field)
    if (enumArr.length) def.enum = enumArr
    if (isNumeric(field.type)) {
      if (field.minimum !== null && field.minimum !== '') def.minimum = Number(field.minimum)
      if (field.maximum !== null && field.maximum !== '') def.maximum = Number(field.maximum)
    }
    if (field.type === SCHEMA_FIELD_TYPE.STRING) {
      if (field.minLength !== null && field.minLength !== '') def.minLength = Number(field.minLength)
      if (field.maxLength !== null && field.maxLength !== '') def.maxLength = Number(field.maxLength)
    }
    const def0 = coerceDefault(field)
    if (def0 !== undefined) def.default = def0
    if (field.key) properties[field.key] = def
    else properties[''] = def
    if (field.required && field.key) required.push(field.key)
  })
  return { schemaVersion: '1.0', type: 'object', properties, required }
}

function fromSchemaJson(schemaJson) {
  const props = schemaJson?.properties
  const requiredSet = new Set(Array.isArray(schemaJson?.required) ? schemaJson.required : [])
  if (!props || typeof props !== 'object') return []
  return Object.entries(props)
    .map(([key, def]) => ({ key, ...(def || {}) }))
    .sort((a, b) => (Number(a.displayOrder) || 0) - (Number(b.displayOrder) || 0) || a.key.localeCompare(b.key))
    .map((def) => makeField({ ...def, required: requiredSet.has(def.key) }))
}

function markDirty() {
  dirty.value = true
}

function uniqueKey() {
  let n = fields.value.length + 1
  while (fields.value.some((f) => f.key === `field_${n}`)) n += 1
  return `field_${n}`
}

function addField() {
  const field = makeField({ key: uniqueKey(), title: '', type: SCHEMA_FIELD_TYPE.STRING, maxLength: 200 })
  fields.value.push(field)
  activeUid.value = field.uid
  panelTab.value = 'props'
  mobileStep.value = 'props'
  markDirty()
}

function removeField(index) {
  const field = fields.value[index]
  fields.value.splice(index, 1)
  if (activeUid.value === field.uid) activeUid.value = fields.value[0]?.uid ?? null
  markDirty()
}

function moveField(index, delta) {
  const target = index + delta
  if (target < 0 || target >= fields.value.length) return
  const list = fields.value
  const [item] = list.splice(index, 1)
  list.splice(target, 0, item)
  markDirty()
}

function handleDragStart(uid) {
  draggingUid.value = uid
}

function handleDrop(targetUid) {
  if (draggingUid.value == null || draggingUid.value === targetUid) return
  const from = fields.value.findIndex((f) => f.uid === draggingUid.value)
  const to = fields.value.findIndex((f) => f.uid === targetUid)
  if (from < 0 || to < 0) return
  const [item] = fields.value.splice(from, 1)
  fields.value.splice(to, 0, item)
  markDirty()
}

function handleDragEnd() {
  draggingUid.value = null
}

function selectField(field) {
  activeUid.value = field.uid
  panelTab.value = 'props'
  if (mobileStep.value === 'fields') mobileStep.value = 'props'
}

function handleTypeChange(type) {
  if (!activeField.value) return
  const field = activeField.value
  if (!widgetsForType(type).includes(field.widget)) field.widget = widgetsForType(type)[0]
  if (type === SCHEMA_FIELD_TYPE.BOOLEAN) {
    field.enumText = ''
    field.minimum = null
    field.maximum = null
    field.minLength = null
    field.maxLength = null
    field.defaultValue = field.defaultValue === true
  } else if (isNumeric(type)) {
    field.minLength = null
    field.maxLength = null
    field.enumText = ''
  } else {
    field.minimum = null
    field.maximum = null
  }
  markDirty()
}

async function loadAll() {
  pageState.value = 'loading'
  try {
    template.value = await getTemplateDetail(templateId.value)
    const detail = await getVersionDetail(templateId.value, versionId.value)
    version.value = detail
    fields.value = fromSchemaJson(detail.schemaJson)
    activeUid.value = fields.value[0]?.uid ?? null
    changelog.value = detail.changelog || ''
    dirty.value = false
    pageState.value = 'success'
  } catch (err) {
    if (isTemplateNotFound(err)) {
      pageState.value = 'empty'
      pageStateDescription.value = '模板版本不存在或不可见'
    } else if (isTemplateDenied(err)) {
      pageState.value = 'permission'
      pageStateDescription.value = '当前账号无权编辑该模板版本'
    } else {
      pageState.value = 'error'
      pageStateDescription.value = '模板版本加载失败'
      notifyApiError(err)
    }
  }
}

async function reloadVersion() {
  await loadAll()
  ElMessage.info('已放弃未保存的修改')
}

function previewValidator(data) {
  if (dirty.value) return validateDataAgainstSchema(draftSchema.value, data)
  return validateVersionData(templateId.value, versionId.value, data)
}

async function saveDraft() {
  await updateDraftVersion(templateId.value, versionId.value, {
    schemaJson: draftSchema.value,
    changelog: changelog.value || null
  })
  dirty.value = false
}

async function handleSave() {
  if (!validation.value.valid) {
    ElMessage.error('Schema 校验未通过，请先修正后再保存')
    panelTab.value = 'schema'
    return
  }
  saving.value = true
  try {
    await saveDraft()
    version.value = await getVersionDetail(templateId.value, versionId.value)
    ElMessage.success('草稿已保存')
  } catch (err) {
    const errors = schemaErrors(err)
    if (errors.length) ElMessage.error(`Schema 校验失败：${errors.map((e) => e.message).join('；')}`)
    else notifyApiError(err)
  } finally {
    saving.value = false
  }
}

async function handlePublish() {
  try {
    await ElMessageBox.confirm(
      `发布后生成不可修改的正式版本，并自动创建下一个草稿。确认发布草稿 ${version.value.version}？`,
      '发布版本确认',
      { confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  publishing.value = true
  try {
    if (dirty.value) await saveDraft()
    const res = await publishVersion(templateId.value, versionId.value, { changelog: changelog.value || undefined })
    ElMessage.success(`已发布 ${res?.publishedVersion?.version || ''}，并自动创建 ${res?.newDraftVersion?.version || ''} 草稿`)
    router.push(`/experiment/templates/${templateId.value}`)
  } catch (err) {
    const errors = schemaErrors(err)
    if (errors.length) ElMessage.error(`Schema 校验失败：${errors.map((e) => e.message).join('；')}`)
    else notifyApiError(err)
  } finally {
    publishing.value = false
  }
}

function goDetail() {
  router.push(`/experiment/templates/${templateId.value}`)
}

watch(mobileStep, (step) => {
  if (step === 'props') panelTab.value = 'props'
  if (step === 'preview') panelTab.value = 'preview'
  if (step === 'schema') panelTab.value = 'schema'
})

watch(panelTab, (tab) => {
  if (tab === 'props') mobileStep.value = 'props'
  if (tab === 'preview') mobileStep.value = 'preview'
  if (tab === 'schema') mobileStep.value = 'schema'
})

onMounted(loadAll)
</script>

<style scoped>
.template-editor-page {
  max-width: var(--usn-content-max-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.editor-back {
  display: flex;
}

.editor-readonly {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--usn-space-3);
}

.editor-header {
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

.editor-header__main {
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

.editor-header__tags {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.version-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  font-size: var(--usn-font-size-help);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--usn-purple-600);
  background: #faf5ff;
  border: 1px solid var(--usn-purple-600);
  border-radius: var(--usn-radius-sm);
}

.editor-header__actions {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.changelog-input {
  width: 220px;
}

.mobile-steps {
  display: none;
}

.editor-grid {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: var(--usn-space-5);
  align-items: start;
}

.field-panel {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
  padding: var(--usn-space-4);
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  box-shadow: var(--usn-shadow-panel);
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

.panel-hint {
  margin: 0;
  line-height: 1.5;
}

.field-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
}

.field-item {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  padding: var(--usn-space-3);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-sm);
  background: var(--usn-surface);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.field-item:hover {
  border-color: var(--usn-blue-600);
}

.field-item--active {
  border-color: var(--usn-purple-600);
  box-shadow: 0 0 0 2px #faf5ff;
}

.field-item--invalid {
  border-color: var(--usn-danger);
}

.field-item--dragging {
  opacity: 0.5;
}

.field-item__handle {
  color: var(--usn-ink-500);
  cursor: grab;
  display: inline-flex;
}

.field-item__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.field-item__title {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  min-width: 0;
}

.field-item__name {
  font-size: var(--usn-font-size-body);
  font-weight: 600;
  color: var(--usn-ink-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-item__meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-item__meta code {
  font-family: Consolas, Monaco, monospace;
  color: var(--usn-blue-700);
}

.field-item__ops {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 0 0 auto;
}

.prop-panel {
  padding: var(--usn-space-4);
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  box-shadow: var(--usn-shadow-panel);
  min-width: 0;
}

.prop-form {
  max-width: 560px;
}

.form-control {
  width: 100%;
}

.form-hint {
  margin: 4px 0 0;
  line-height: 1.4;
}

.range-row {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
}

.range-row :deep(.el-input-number) {
  flex: 1 1 0;
  min-width: 0;
}

.range-sep {
  color: var(--usn-ink-500);
}

.field-errors,
.schema-check__errors {
  margin-top: var(--usn-space-3);
  padding: var(--usn-space-3);
  background: #fdf2f2;
  border: 1px solid #f5c2c0;
  border-radius: var(--usn-radius-sm);
  font-size: var(--usn-font-size-help);
  color: var(--usn-danger);
}

.field-errors__title,
.schema-check__title {
  margin: 0 0 var(--usn-space-1);
  font-weight: 600;
}

.field-errors ul,
.schema-check__errors ul {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.field-errors code,
.schema-check__errors code {
  font-family: Consolas, Monaco, monospace;
}

.field-ok {
  margin: var(--usn-space-3) 0 0;
  color: var(--usn-success);
}

.schema-check {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.raw-hint {
  margin: 0;
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
  max-height: 420px;
}

@media (max-width: 1024px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }

  .mobile-steps {
    display: flex;
  }

  .editor-grid--fields .prop-panel {
    display: none;
  }

  .editor-grid--props .field-panel,
  .editor-grid--preview .field-panel,
  .editor-grid--schema .field-panel {
    display: none;
  }

  .changelog-input {
    width: 100%;
  }
}

@media (max-width: 767px) {
  .editor-header {
    padding: var(--usn-space-3);
  }

  .editor-header__actions {
    width: 100%;
  }

  .editor-header__actions :deep(.el-button) {
    flex: 1;
  }

  .field-panel,
  .prop-panel {
    padding: var(--usn-space-3);
  }
}
</style>
