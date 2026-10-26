<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="560px"
    class="feedback-dialog"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="target-box">
      <div class="target-box__row">
        <span class="target-box__label">目标修订</span>
        <span class="target-box__value">
          第 {{ revision?.revisionNo ?? '--' }} 版
          <RevisionStatusTag :status="revision?.status || 'SUBMITTED'" />
        </span>
      </div>
      <p class="target-box__hint text-help muted">
        评审动作作用于当前送审修订；导师不会直接修改成员内容，要求修改将自动生成下一份可编辑修订。
      </p>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="反馈类型" prop="type">
        <el-radio-group v-model="form.type">
          <el-radio-button :value="FEEDBACK_TYPE.COMMENT">{{ feedbackTypeMeta(FEEDBACK_TYPE.COMMENT).label }}</el-radio-button>
          <el-radio-button :value="FEEDBACK_TYPE.CHANGE_REQUEST">
            {{ feedbackTypeMeta(FEEDBACK_TYPE.CHANGE_REQUEST).label }}
          </el-radio-button>
          <el-radio-button :value="FEEDBACK_TYPE.APPROVAL">{{ feedbackTypeMeta(FEEDBACK_TYPE.APPROVAL).label }}</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item :label="contentLabel" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="4"
          maxlength="2000"
          show-word-limit
          :placeholder="contentPlaceholder"
        />
      </el-form-item>

      <el-form-item label="可见范围" prop="visibility">
        <el-select v-model="form.visibility" class="full-width">
          <el-option
            :label="feedbackVisibilityMeta(FEEDBACK_VISIBILITY.AUTHOR_AND_REVIEWERS).label"
            :value="FEEDBACK_VISIBILITY.AUTHOR_AND_REVIEWERS"
          />
          <el-option
            v-if="record?.projectId != null"
            :label="feedbackVisibilityMeta(FEEDBACK_VISIBILITY.PROJECT_MEMBERS).label"
            :value="FEEDBACK_VISIBILITY.PROJECT_MEMBERS"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">提交反馈</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import {
  FEEDBACK_TYPE,
  FEEDBACK_VISIBILITY,
  feedbackTypeMeta,
  feedbackVisibilityMeta
} from '@/api/experimentRecords'
import RevisionStatusTag from './RevisionStatusTag.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  record: { type: Object, default: null },
  initialType: { type: String, default: FEEDBACK_TYPE.COMMENT },
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref(null)
const form = ref({ type: FEEDBACK_TYPE.COMMENT, content: '', visibility: FEEDBACK_VISIBILITY.AUTHOR_AND_REVIEWERS })

const revision = computed(() => props.record?.currentRevision || null)
const dialogTitle = computed(() => {
  const label = feedbackTypeMeta(form.value.type).label
  return `${label} · ${props.record?.title || '实验记录'}`
})

const contentLabel = computed(() =>
  form.value.type === FEEDBACK_TYPE.APPROVAL ? '批准说明（可选）' : '反馈内容'
)

const contentPlaceholder = computed(() => {
  if (form.value.type === FEEDBACK_TYPE.CHANGE_REQUEST) return '请说明需要补充或修改的内容，成员将收到下一份可编辑修订'
  if (form.value.type === FEEDBACK_TYPE.APPROVAL) return '可补充批准依据，例如数据可复现、快照匹配'
  return '填写评审意见或疑问'
})

const rules = computed(() => ({
  type: [{ required: true, message: '请选择反馈类型', trigger: 'change' }],
  content:
    form.value.type === FEEDBACK_TYPE.APPROVAL
      ? []
      : [
          { required: true, message: '请填写反馈内容', trigger: 'blur' },
          { min: 1, max: 2000, message: '反馈内容需为 1-2000 字符', trigger: 'blur' }
        ],
  visibility: [{ required: true, message: '请选择可见范围', trigger: 'change' }]
}))

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    form.value = {
      type: props.initialType,
      content: '',
      visibility: FEEDBACK_VISIBILITY.AUTHOR_AND_REVIEWERS
    }
    formRef.value?.clearValidate()
  }
)

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', {
    revisionId: revision.value?.id,
    type: form.value.type,
    content: form.value.content.trim(),
    visibility: form.value.visibility,
    recordVersion: props.record?.version
  })
}
</script>

<style scoped>
.target-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--usn-space-3);
  margin-bottom: var(--usn-space-4);
  background: var(--usn-canvas);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-sm);
}

.target-box__row {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.target-box__label {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.target-box__value {
  display: inline-flex;
  align-items: center;
  gap: var(--usn-space-2);
  font-weight: 600;
  color: var(--usn-ink-900);
}

.target-box__hint {
  margin: 0;
  line-height: 1.5;
}

.full-width {
  width: 100%;
}

@media (max-width: 767px) {
  :global(.feedback-dialog) {
    width: 94vw !important;
  }

  :global(.feedback-dialog .el-radio-group) {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
}
</style>
