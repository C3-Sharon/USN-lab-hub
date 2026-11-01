<template>
  <el-dialog
    :model-value="modelValue"
    title="登记外部规格候选"
    width="min(900px, 92vw)"
    :close-on-click-modal="false"
    class="candidate-dialog"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="候选不会自动生效"
      description="保存来源 URL、类型、获取时间、原始规格和标准化建议，等待人工对比与确认；W44 不自动抓取 URL。"
      class="candidate-alert"
    />

    <el-form label-position="top" class="candidate-form">
      <div class="form-grid">
        <el-form-item label="来源类型 sourceType" required>
          <el-select v-model="form.sourceType" class="w-full" @change="syncSourceRequirement">
            <el-option
              v-for="item in sourceTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="获取时间 capturedAt" :required="form.sourceType !== 'MANUAL'">
          <el-date-picker
            v-model="form.capturedAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="来源获取时间，不得晚于当前时间"
            class="w-full"
            :disabled="form.sourceType === 'MANUAL'"
          />
        </el-form-item>
      </div>

      <el-form-item label="来源 URL sourceUrl" :required="form.sourceType !== 'MANUAL'">
        <el-input
          v-model="form.sourceUrl"
          maxlength="2048"
          placeholder="https://..."
          :disabled="form.sourceType === 'MANUAL'"
        />
      </el-form-item>

      <el-form-item label="来源标题 sourceTitle" :required="form.sourceType !== 'MANUAL'">
        <el-input v-model="form.sourceTitle" maxlength="200" placeholder="例如：OPA333 datasheet" />
      </el-form-item>

      <el-form-item label="原始规格 rawSpecs（按来源原文保留）" required>
        <SpecRowsEditor v-model="form.rawSpecs" mode="raw" />
      </el-form-item>

      <el-form-item label="标准化建议 normalizedProposal（人工整理，不自动接受）" required>
        <SpecRowsEditor v-model="form.normalizedProposal" mode="normalized" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存为 PENDING 候选</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createSpecCandidate, SOURCE_TYPE, SOURCE_TYPE_META, VALUE_TYPE } from '@/api/components'
import {
  notifyApiError,
  errorTraceId,
  isVersionConflict,
  isComponentSpecInvalid,
  isSourceMetadataInvalid,
  promptVersionConflict
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import SpecRowsEditor from './SpecRowsEditor.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  component: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'submitted', 'conflict'])

const sourceTypeOptions = Object.entries(SOURCE_TYPE).map(([, value]) => ({
  value,
  label: SOURCE_TYPE_META[value].label
}))

const submitting = ref(false)

const form = reactive({
  sourceType: SOURCE_TYPE.MANUFACTURER_DATASHEET,
  sourceUrl: '',
  sourceTitle: '',
  capturedAt: '',
  rawSpecs: [{ name: '', value: '', unit: '' }],
  normalizedProposal: [{
    specCode: '',
    label: '',
    valueType: VALUE_TYPE.STRING,
    normalizedValue: '',
    unit: '',
    sortOrder: 10
  }]
})

function reset() {
  form.sourceType = SOURCE_TYPE.MANUFACTURER_DATASHEET
  form.sourceUrl = ''
  form.sourceTitle = ''
  form.capturedAt = ''
  form.rawSpecs = [{ name: '', value: '', unit: '' }]
  form.normalizedProposal = [{
    specCode: '',
    label: '',
    valueType: VALUE_TYPE.STRING,
    normalizedValue: '',
    unit: '',
    sortOrder: 10
  }]
}

function syncSourceRequirement(value) {
  if (value === SOURCE_TYPE.MANUAL) {
    form.sourceUrl = ''
    form.sourceTitle = ''
    form.capturedAt = ''
  }
}

watch(
  () => props.modelValue,
  (visible) => { if (visible) reset() }
)

async function submit() {
  if (!props.component?.id) return
  if (form.sourceType !== SOURCE_TYPE.MANUAL) {
    if (!/^https?:\/\//i.test(form.sourceUrl.trim())) {
      ElMessage.warning('请填写 http/https 来源 URL')
      return
    }
    if (!form.sourceTitle.trim()) {
      ElMessage.warning('请填写来源标题')
      return
    }
    if (!form.capturedAt) {
      ElMessage.warning('请选择来源获取时间')
      return
    }
  }

  submitting.value = true
  try {
    const result = await createSpecCandidate(
      props.component.id,
      {
        componentVersion: props.component.version,
        sourceType: form.sourceType,
        sourceUrl: form.sourceType === SOURCE_TYPE.MANUAL ? null : form.sourceUrl.trim(),
        sourceTitle: form.sourceTitle.trim() || null,
        capturedAt: form.sourceType === SOURCE_TYPE.MANUAL ? null : form.capturedAt,
        rawSpecs: form.rawSpecs.map((row) => ({
          name: row.name,
          value: row.value,
          unit: row.unit || null
        })),
        normalizedProposal: form.normalizedProposal.map((row, index) => ({
          specCode: row.specCode,
          label: row.label,
          valueType: row.valueType,
          normalizedValue: row.normalizedValue,
          unit: row.unit || null,
          sortOrder: Number(row.sortOrder ?? (index + 1) * 10)
        }))
      },
      createIdempotencyKey('w44-candidate')
    )
    ElMessage.success(`候选已登记（candidateId ${result.id}），等待人工确认`)
    emit('update:modelValue', false)
    emit('submitted', result)
  } catch (err) {
    if (isVersionConflict(err)) {
      promptVersionConflict(() => emit('conflict'))
      return
    }
    if (isComponentSpecInvalid(err) || isSourceMetadataInvalid(err)) {
      ElMessage.error(err?.msg || '规格或来源元数据不符合契约')
      return
    }
    const traceId = errorTraceId(err)
    notifyApiError(err)
    if (traceId) ElMessage.info(`traceId：${traceId}`)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.candidate-alert {
  margin-bottom: var(--usn-space-4);
}

.candidate-form {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: var(--usn-space-2);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--usn-space-4);
}

.w-full {
  width: 100%;
}

@media (max-width: 767px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
