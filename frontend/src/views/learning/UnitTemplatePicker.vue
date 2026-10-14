<template>
  <el-dialog
    :model-value="modelValue"
    title="关联实验模板版本"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <p class="picker-hint text-help muted">
      单元：<strong>{{ unit?.title }}</strong>。学习单元只能关联<strong>已发布</strong>的模板版本，模板发布新版本后单元不会自动升级。
    </p>

    <div v-if="unit?.templateVersionId" class="picker-current">
      <span class="picker-current__label text-help muted">当前关联</span>
      <span class="picker-current__value">{{ unit.templateName }} · {{ unit.templateVersion }}</span>
      <VersionStatusTag v-if="unit.templateVersionStatus" :status="unit.templateVersionStatus" />
    </div>
    <div v-else class="picker-current picker-current--none">
      <span class="text-help muted">当前未关联任何模板版本</span>
    </div>

    <el-tabs v-model="scope" class="picker-tabs">
      <el-tab-pane :label="scopeMeta(TEMPLATE_SCOPE.SYSTEM).label" :name="TEMPLATE_SCOPE.SYSTEM" />
      <el-tab-pane :label="scopeMeta(TEMPLATE_SCOPE.PROJECT).label" :name="TEMPLATE_SCOPE.PROJECT" />
    </el-tabs>

    <el-form label-width="88px">
      <el-form-item label="模板">
        <el-select
          v-model="templateId"
          filterable
          clearable
          placeholder="选择已发布模板"
          class="form-control"
          :loading="loadingTemplates"
          @change="handleTemplateChange"
        >
          <el-option v-for="t in templates" :key="t.id" :label="optionLabel(t)" :value="t.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="版本">
        <el-select
          v-model="versionId"
          clearable
          placeholder="选择已发布版本"
          class="form-control"
          :loading="loadingVersions"
          :disabled="!templateId"
        >
          <el-option v-for="v in versions" :key="v.id" :label="versionLabel(v)" :value="v.id" />
        </el-select>
      </el-form-item>
    </el-form>

    <p v-if="templateId && !loadingVersions && !versions.length" class="picker-empty text-help muted">
      该模板暂无已发布版本，无法关联。
    </p>

    <template #footer>
      <el-button v-if="unit?.templateVersionId" text type="danger" @click="handleClear">清除关联</el-button>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!versionId && !unit?.templateVersionId" @click="handleSubmit">
        确认关联
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import {
  listTemplates,
  listPublishedVersions,
  TEMPLATE_SCOPE,
  templateScopeMeta
} from '@/api/experimentTemplates'
import { notifyApiError } from '@/utils/apiError'
import VersionStatusTag from '@/components/templates/VersionStatusTag.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  unit: { type: Object, default: null },
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const scope = ref(TEMPLATE_SCOPE.SYSTEM)
const templates = ref([])
const versions = ref([])
const templateId = ref(null)
const versionId = ref(null)
const loadingTemplates = ref(false)
const loadingVersions = ref(false)

function scopeMeta(s) {
  return templateScopeMeta(s)
}

function optionLabel(t) {
  const suffix = t.latestVersion ? `（最新 ${t.latestVersion}）` : ''
  return `${t.title}${suffix}`
}

function versionLabel(v) {
  return `${v.version} · ${v.fieldCount} 字段${v.changelog ? ` · ${v.changelog}` : ''}`
}

async function loadTemplates() {
  loadingTemplates.value = true
  try {
    const res = await listTemplates({ scope: scope.value, status: 'PUBLISHED', pageSize: 100 })
    templates.value = res?.list || []
  } catch (err) {
    notifyApiError(err)
    templates.value = []
  } finally {
    loadingTemplates.value = false
  }
}

async function loadVersions(id) {
  if (!id) {
    versions.value = []
    return
  }
  loadingVersions.value = true
  try {
    versions.value = (await listPublishedVersions(id)) || []
  } catch (err) {
    notifyApiError(err)
    versions.value = []
  } finally {
    loadingVersions.value = false
  }
}

function handleTemplateChange(id) {
  versionId.value = null
  loadVersions(id)
}

function handleSubmit() {
  emit('submit', { templateVersionId: versionId.value })
}

function handleClear() {
  emit('submit', { templateVersionId: null })
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    scope.value = TEMPLATE_SCOPE.SYSTEM
    templateId.value = props.unit?.templateId ?? null
    versionId.value = props.unit?.templateVersionId ?? null
    versions.value = []
    loadTemplates()
    if (templateId.value) loadVersions(templateId.value)
  }
)

watch(scope, () => {
  templateId.value = null
  versionId.value = null
  versions.value = []
  loadTemplates()
})
</script>

<style scoped>
.picker-hint {
  margin: 0 0 var(--usn-space-3);
  line-height: 1.6;
}

.picker-current {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
  padding: var(--usn-space-3);
  margin-bottom: var(--usn-space-3);
  background: #f7f9fb;
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-sm);
}

.picker-current__label {
  flex: 0 0 auto;
}

.picker-current__value {
  font-weight: 600;
  color: var(--usn-ink-900);
}

.picker-tabs {
  margin-bottom: var(--usn-space-1);
}

.form-control {
  width: 100%;
}

.picker-empty {
  margin: 0;
}
</style>
