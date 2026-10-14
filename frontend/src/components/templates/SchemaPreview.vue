<template>
  <div class="schema-preview">
    <div v-if="!fields.length" class="schema-preview__empty">
      <el-empty :image-size="64" description="该版本暂无字段，无法预览" />
    </div>

    <template v-else>
      <div class="schema-preview__form">
        <div v-for="field in fields" :key="field.key" class="schema-field">
          <label class="schema-field__label">
            <span class="schema-field__title">{{ field.title || field.key }}</span>
            <span v-if="isRequired(field.key)" class="schema-field__required" aria-label="必填">*</span>
            <span v-if="field.unit" class="schema-field__unit text-help muted">{{ field.unit }}</span>
          </label>

          <el-select
            v-if="controlOf(field) === 'select'"
            v-model="formData[field.key]"
            placeholder="请选择"
            class="schema-field__control"
          >
            <el-option v-for="opt in field.enum" :key="String(opt)" :label="String(opt)" :value="opt" />
          </el-select>

          <el-input-number
            v-else-if="controlOf(field) === 'number'"
            v-model="formData[field.key]"
            class="schema-field__control"
            :min="typeof field.minimum === 'number' ? field.minimum : undefined"
            :max="typeof field.maximum === 'number' ? field.maximum : undefined"
            :precision="field.type === 'integer' ? 0 : undefined"
            :step="field.type === 'integer' ? 1 : 0.1"
            controls-position="right"
          />

          <el-switch
            v-else-if="controlOf(field) === 'switch'"
            v-model="formData[field.key]"
            class="schema-field__control"
          />

          <el-input
            v-else
            v-model="formData[field.key]"
            class="schema-field__control"
            :type="controlOf(field) === 'textarea' ? 'textarea' : 'text'"
            :rows="controlOf(field) === 'textarea' ? 3 : undefined"
            :maxlength="typeof field.maxLength === 'number' ? field.maxLength : undefined"
            show-word-limit
            :placeholder="`请输入${field.title || field.key}`"
          />

          <p v-if="field.description" class="schema-field__desc text-help muted">{{ field.description }}</p>
          <p v-if="errorOf(field.key)" class="schema-field__error">{{ errorOf(field.key) }}</p>
        </div>
      </div>

      <div class="schema-preview__actions">
        <el-button size="small" type="primary" plain :loading="validating" @click="handleValidate">校验测试数据</el-button>
        <el-button size="small" text @click="resetForm">重置为默认值</el-button>
        <span v-if="result" class="schema-preview__result" :class="result.valid ? 'is-ok' : 'is-bad'">
          {{ result.valid ? '校验通过' : `校验失败（${result.errors.length} 项）` }}
        </span>
      </div>

      <ul v-if="result && !result.valid" class="schema-preview__errors">
        <li v-for="(err, index) in result.errors" :key="`${err.path}-${index}`">
          <code>{{ err.path }}</code> {{ err.message }}
        </li>
      </ul>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { validateDataAgainstSchema } from '@/api/experimentTemplates'

const props = defineProps({
  schemaJson: { type: Object, default: () => ({}) },
  /** 自定义校验函数 (data) => Promise<{ valid, errors }>；缺省走本地受控子集校验 */
  validator: { type: Function, default: null }
})

const formData = ref({})
const result = ref(null)
const validating = ref(false)

const fields = computed(() => {
  const props2 = props.schemaJson?.properties
  if (!props2 || typeof props2 !== 'object') return []
  return Object.entries(props2)
    .map(([key, def]) => ({ key, ...(def || {}) }))
    .sort((a, b) => (Number(a.displayOrder) || 0) - (Number(b.displayOrder) || 0) || a.key.localeCompare(b.key))
})

const requiredSet = computed(() => new Set(Array.isArray(props.schemaJson?.required) ? props.schemaJson.required : []))

function isRequired(key) {
  return requiredSet.value.has(key)
}

function defaultOf(field) {
  if (field.default !== undefined && field.default !== null) return field.default
  if (field.type === 'boolean') return false
  if (field.type === 'number' || field.type === 'integer') return undefined
  return ''
}

function initForm() {
  const next = {}
  for (const field of fields.value) next[field.key] = defaultOf(field)
  formData.value = next
  result.value = null
}

function resetForm() {
  initForm()
}

function controlOf(field) {
  const widget = field.widget
  if (widget === 'textarea') return 'textarea'
  if (widget === 'select' || (Array.isArray(field.enum) && field.enum.length > 0)) return 'select'
  if (widget === 'switch' || field.type === 'boolean') return 'switch'
  if (widget === 'input-number' || field.type === 'number' || field.type === 'integer') return 'number'
  return 'input'
}

function errorOf(key) {
  const errors = result.value?.errors || []
  return errors.find((e) => e.path === key)?.message || ''
}

async function handleValidate() {
  validating.value = true
  try {
    const payload = { ...formData.value }
    result.value = props.validator
      ? await props.validator(payload)
      : validateDataAgainstSchema(props.schemaJson, payload)
  } finally {
    validating.value = false
  }
}

watch(() => props.schemaJson, initForm, { immediate: true, deep: true })
</script>

<style scoped>
.schema-preview {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.schema-preview__empty {
  padding: var(--usn-space-4) 0;
}

.schema-preview__form {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.schema-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.schema-field__label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--usn-font-size-body);
  font-weight: 600;
  color: var(--usn-ink-700);
}

.schema-field__required {
  color: var(--usn-danger);
  font-weight: 700;
}

.schema-field__unit {
  font-weight: 400;
}

.schema-field__control {
  width: 100%;
}

.schema-field__desc {
  margin: 0;
}

.schema-field__error {
  margin: 0;
  font-size: var(--usn-font-size-help);
  color: var(--usn-danger);
}

.schema-preview__actions {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.schema-preview__result {
  font-size: var(--usn-font-size-help);
  font-weight: 600;
}

.schema-preview__result.is-ok {
  color: var(--usn-success);
}

.schema-preview__result.is-bad {
  color: var(--usn-danger);
}

.schema-preview__errors {
  margin: 0;
  padding: var(--usn-space-3);
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-1);
  background: #fdf2f2;
  border: 1px solid #f5c2c0;
  border-radius: var(--usn-radius-sm);
  font-size: var(--usn-font-size-help);
  color: var(--usn-danger);
}

.schema-preview__errors code {
  font-family: Consolas, Monaco, monospace;
  margin-right: 4px;
}
</style>
