<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'edit' ? '更正人工观测' : '新增人工观测'"
    width="560px"
    class="manual-dialog"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-alert v-if="mode === 'edit'" type="warning" :closable="false" show-icon class="manual-hint">
      更正不会覆盖历史：系统将追加一条修订，保留旧值、操作者、时间与前序修订。
    </el-alert>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item label="指标" prop="metricCode">
          <el-select v-model="form.metricCode" class="full-width" :disabled="mode === 'edit'" @change="onMetricChange">
            <el-option
              v-for="m in metricOptions"
              :key="m.code"
              :label="`${m.name}（${m.code}）`"
              :value="m.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="form.unit" maxlength="20" />
        </el-form-item>
        <el-form-item label="观测值" prop="value">
          <el-input-number
            v-if="currentMetric?.valueType !== 'STRING'"
            v-model="form.value"
            class="full-width"
            :precision="currentMetric?.valueType === 'INTEGER' ? 0 : undefined"
            :step="currentMetric?.valueType === 'INTEGER' ? 1 : 0.1"
            :controls="false"
          />
          <el-input v-else v-model="form.value" maxlength="200" />
        </el-form-item>
        <el-form-item label="观测时间" prop="observedAt">
          <el-date-picker
            v-model="form.observedAt"
            type="datetime"
            class="full-width"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择观测时间"
          />
        </el-form-item>
        <el-form-item label="采集方法" prop="method">
          <el-input v-model="form.method" maxlength="120" placeholder="例如 温湿度计直读" />
        </el-form-item>
        <el-form-item label="仪器" prop="instrument">
          <el-input v-model="form.instrument" maxlength="120" placeholder="例如 TH-20 / LAB-B-003" />
        </el-form-item>
      </div>

      <el-form-item label="备注">
        <el-input v-model="form.note" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>

      <el-form-item v-if="mode === 'edit'" label="更正原因" prop="changeReason">
        <el-input v-model="form.changeReason" maxlength="500" show-word-limit placeholder="必填，用于审计追溯" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ mode === 'edit' ? '提交更正' : '保存观测' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  mode: { type: String, default: 'create' },
  plan: { type: Object, default: null },
  record: { type: Object, default: null },
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref(null)
const form = ref(emptyForm())

const metricOptions = computed(() => props.plan?.metricDefinitions || [])
const currentMetric = computed(() => metricOptions.value.find((m) => m.code === form.value.metricCode) || null)

const rules = computed(() => ({
  metricCode: [{ required: true, message: '请选择指标', trigger: 'change' }],
  unit: [{ required: true, message: '请填写单位', trigger: 'blur' }],
  value: [{ required: true, message: '请填写观测值', trigger: 'blur' }],
  observedAt: [{ required: true, message: '请选择观测时间', trigger: 'change' }],
  method: [{ required: true, message: '请填写采集方法', trigger: 'blur' }],
  instrument: [{ required: true, message: '请填写仪器', trigger: 'blur' }],
  changeReason:
    props.mode === 'edit'
      ? [{ required: true, message: '请填写更正原因', trigger: 'blur' }]
      : []
}))

function emptyForm() {
  return {
    metricCode: '',
    unit: '',
    value: undefined,
    observedAt: '',
    method: '',
    instrument: '',
    note: '',
    changeReason: ''
  }
}

function onMetricChange(code) {
  const metric = metricOptions.value.find((m) => m.code === code)
  if (metric) form.value.unit = metric.unit
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    if (props.mode === 'edit' && props.record) {
      form.value = {
        metricCode: props.record.metricCode,
        unit: props.record.unit,
        value: props.record.value,
        observedAt: String(props.record.observedAt || '').slice(0, 19),
        method: props.record.method,
        instrument: props.record.instrument,
        note: props.record.note || '',
        changeReason: ''
      }
    } else {
      const first = metricOptions.value[0]
      form.value = {
        ...emptyForm(),
        metricCode: first?.code || '',
        unit: first?.unit || '',
        observedAt: new Date().toISOString().slice(0, 19)
      }
    }
    formRef.value?.clearValidate()
  }
)

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const metric = currentMetric.value
  emit('submit', {
    metricCode: form.value.metricCode,
    valueType: metric?.valueType,
    value: form.value.value,
    unit: form.value.unit.trim(),
    observedAt: form.value.observedAt,
    method: form.value.method.trim(),
    instrument: form.value.instrument.trim(),
    note: form.value.note.trim() || null,
    changeReason: form.value.changeReason.trim() || undefined,
    version: props.record?.version
  })
}
</script>

<style scoped>
.manual-hint {
  margin-bottom: var(--usn-space-4);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--usn-space-4);
}

.full-width {
  width: 100%;
}

@media (max-width: 767px) {
  :global(.manual-dialog) {
    width: 94vw !important;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
