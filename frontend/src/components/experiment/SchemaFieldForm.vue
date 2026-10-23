<template>
  <div class="schema-field-form">
    <div v-if="!fields.length" class="schema-field-form__empty">
      <el-empty :image-size="64" description="该模板版本暂无字段定义" />
    </div>

    <div v-else class="schema-field-form__grid">
      <div v-for="field in fields" :key="field.key" class="schema-field">
        <label class="schema-field__label">
          <span>{{ field.title || field.key }}</span>
          <span v-if="isRequired(field.key)" class="schema-field__required" aria-label="必填">*</span>
          <span v-if="field.unit" class="schema-field__unit text-help muted">{{ field.unit }}</span>
        </label>

        <el-select
          v-if="controlOf(field) === 'select'"
          :model-value="valueOf(field.key)"
          placeholder="请选择"
          class="schema-field__control"
          @update:model-value="updateField(field.key, $event)"
        >
          <el-option v-for="opt in field.enum" :key="String(opt)" :label="String(opt)" :value="opt" />
        </el-select>

        <el-input-number
          v-else-if="controlOf(field) === 'number'"
          :model-value="valueOf(field.key)"
          class="schema-field__control"
          :min="typeof field.minimum === 'number' ? field.minimum : undefined"
          :max="typeof field.maximum === 'number' ? field.maximum : undefined"
          :precision="field.type === 'integer' ? 0 : undefined"
          :step="field.type === 'integer' ? 1 : 0.1"
          controls-position="right"
          @update:model-value="updateField(field.key, $event)"
        />

        <el-switch
          v-else-if="controlOf(field) === 'switch'"
          :model-value="valueOf(field.key)"
          class="schema-field__control"
          @update:model-value="updateField(field.key, $event)"
        />

        <el-input
          v-else
          :model-value="valueOf(field.key)"
          class="schema-field__control"
          :type="controlOf(field) === 'textarea' ? 'textarea' : 'text'"
          :rows="controlOf(field) === 'textarea' ? 3 : undefined"
          :maxlength="typeof field.maxLength === 'number' ? field.maxLength : undefined"
          show-word-limit
          :disabled="disabled"
          :placeholder="`请输入${field.title || field.key}`"
          @update:model-value="updateField(field.key, $event)"
        />

        <p v-if="field.description" class="schema-field__desc text-help muted">{{ field.description }}</p>
        <p v-if="errorOf(field.key)" class="schema-field__error">{{ errorOf(field.key) }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  schemaJson: { type: Object, default: () => ({}) },
  modelValue: { type: Object, default: () => ({}) },
  errors: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const fields = computed(() => {
  const properties = props.schemaJson?.properties
  if (!properties || typeof properties !== 'object') return []
  return Object.entries(properties)
    .map(([key, def]) => ({ key, ...(def || {}) }))
    .sort((a, b) => (Number(a.displayOrder) || 0) - (Number(b.displayOrder) || 0) || a.key.localeCompare(b.key))
})

const requiredSet = computed(() => new Set(Array.isArray(props.schemaJson?.required) ? props.schemaJson.required : []))

function isRequired(key) {
  return requiredSet.value.has(key)
}

function valueOf(key) {
  const value = props.modelValue?.[key]
  if (value === null) return undefined
  return value
}

function updateField(key, value) {
  emit('update:modelValue', { ...(props.modelValue || {}), [key]: value })
}

function controlOf(field) {
  if (field.widget === 'textarea') return 'textarea'
  if (field.widget === 'select' || (Array.isArray(field.enum) && field.enum.length > 0)) return 'select'
  if (field.widget === 'switch' || field.type === 'boolean') return 'switch'
  if (field.widget === 'input-number' || field.type === 'number' || field.type === 'integer') return 'number'
  return 'input'
}

function errorOf(key) {
  return props.errors.find((item) => item.path === key || item.field === key)?.message || ''
}
</script>

<style scoped>
.schema-field-form {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.schema-field-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--usn-space-4);
}

.schema-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
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

@media (max-width: 767px) {
  .schema-field-form__grid {
    grid-template-columns: 1fr;
  }
}
</style>
