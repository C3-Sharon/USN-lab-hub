<template>
  <el-dialog
    :model-value="modelValue"
    title="手工录入正式规格快照"
    width="min(880px, 92vw)"
    :close-on-click-modal="false"
    class="manual-snapshot-dialog"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-alert
      type="warning"
      show-icon
      :closable="false"
      title="来源不可用时才允许手工录入"
      description="必须填写信息来源说明；确认后生成不可变快照，后续修正需再生成新版本，历史版本永久只读。"
      class="manual-alert"
    />

    <el-form label-position="top" class="manual-form">
      <el-form-item label="来源说明 sourceNote" required>
        <el-input
          v-model="form.sourceNote"
          type="textarea"
          :rows="2"
          maxlength="1000"
          show-word-limit
          placeholder="例如：供应商页面暂不可用，依据实验室现有料盘标签和老师确认录入"
        />
      </el-form-item>

      <el-form-item label="变更说明 changeSummary" required>
        <el-input
          v-model="form.changeSummary"
          maxlength="500"
          show-word-limit
          placeholder="例如：补充线下确认的封装标签"
        />
      </el-form-item>

      <el-form-item label="原始规格 rawSpecs" required>
        <SpecRowsEditor v-model="form.rawSpecs" mode="raw" />
      </el-form-item>

      <el-form-item label="标准化正式规格 specifications" required>
        <SpecRowsEditor v-model="form.specifications" mode="normalized" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认并生成快照</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createManualSpecSnapshot } from '@/api/components'
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
  component: { type: Object, default: null },
  currentSnapshot: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'submitted', 'conflict'])

const submitting = ref(false)

const form = reactive({
  sourceNote: '',
  changeSummary: '',
  rawSpecs: [{ name: '', value: '', unit: '' }],
  specifications: []
})

function reset() {
  form.sourceNote = ''
  form.changeSummary = ''
  const raw = props.currentSnapshot?.rawSpecs || []
  form.rawSpecs = raw.length
    ? raw.map((row) => ({ name: row.name || '', value: String(row.value ?? ''), unit: row.unit || '' }))
    : [{ name: '', value: '', unit: '' }]
  const specs = props.currentSnapshot?.specifications || []
  form.specifications = specs.length
    ? specs.map((row) => ({
        specCode: row.specCode,
        label: row.label,
        valueType: row.valueType,
        normalizedValue: row.normalizedValue,
        unit: row.unit || '',
        sortOrder: row.sortOrder
      }))
    : [{
        specCode: '',
        label: '',
        valueType: 'STRING',
        normalizedValue: '',
        unit: '',
        sortOrder: 10
      }]
}

watch(
  () => props.modelValue,
  (visible) => { if (visible) reset() }
)

async function submit() {
  if (!props.component?.id) return
  const sourceNote = form.sourceNote.trim()
  const changeSummary = form.changeSummary.trim()
  if (!sourceNote) {
    ElMessage.warning('请填写来源说明')
    return
  }
  if (!changeSummary) {
    ElMessage.warning('请填写变更说明')
    return
  }

  submitting.value = true
  try {
    const result = await createManualSpecSnapshot(
      props.component.id,
      {
        componentVersion: props.component.version,
        sourceType: 'MANUAL',
        sourceUrl: null,
        sourceNote,
        rawSpecs: form.rawSpecs.map((row) => ({
          name: row.name,
          value: row.value,
          unit: row.unit || null
        })),
        specifications: form.specifications.map((row, index) => ({
          specCode: row.specCode,
          label: row.label,
          valueType: row.valueType,
          rawValue: null,
          normalizedValue: row.normalizedValue,
          unit: row.unit || null,
          sortOrder: Number(row.sortOrder ?? (index + 1) * 10)
        })),
        changeSummary
      },
      createIdempotencyKey('w44-manual-snapshot')
    )
    ElMessage.success(`已生成规格快照 v${result.snapshotVersion}（snapshotId ${result.snapshotId}）`)
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
.manual-alert {
  margin-bottom: var(--usn-space-4);
}

.manual-form {
  max-height: 58vh;
  overflow-y: auto;
  padding-right: var(--usn-space-2);
}

@media (max-width: 767px) {
  :deep(.manual-snapshot-dialog) {
    width: 94vw;
  }
}
</style>
