<template>
  <el-dialog
    :model-value="modelValue"
    title="转为项目草案"
    width="560px"
    class="convert-dialog"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-alert type="info" :closable="false" show-icon class="convert-hint">
      仅已发布记录可一次性创建 PREPARING 项目；项目将保存来源记录与发布修订关系。
    </el-alert>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="项目编号" prop="code">
        <el-input v-model="form.code" maxlength="50" show-word-limit placeholder="例如 PROJ-021" />
      </el-form-item>
      <el-form-item label="项目名称" prop="name">
        <el-input v-model="form.name" maxlength="120" show-word-limit />
      </el-form-item>
      <el-form-item label="项目简介" prop="summary">
        <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="分类" prop="category">
        <el-select v-model="form.category" class="full-width" allow-create filterable>
          <el-option label="硬件项目" value="hardware_project" />
          <el-option label="软件项目" value="software_project" />
          <el-option label="综合项目" value="mixed_project" />
        </el-select>
      </el-form-item>
    </el-form>

    <div class="source-box">
      <span class="source-box__label">来源记录</span>
      <span class="source-box__value">{{ record?.recordCode }} · {{ record?.title }}</span>
      <span class="text-help muted">发布修订：第 {{ record?.publishedRevision?.revisionNo ?? '--' }} 版</span>
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">创建项目草案</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  record: { type: Object, default: null },
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref(null)
const form = ref({ code: '', name: '', summary: '', category: 'hardware_project' })

const rules = {
  code: [
    { required: true, message: '请填写项目编号', trigger: 'blur' },
    { min: 1, max: 50, message: '项目编号需为 1-50 字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请填写项目名称', trigger: 'blur' },
    { min: 2, max: 120, message: '项目名称需为 2-120 字符', trigger: 'blur' }
  ],
  summary: [{ max: 500, message: '项目简介最多 500 字符', trigger: 'blur' }]
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    form.value = {
      code: props.record ? `PROJ-${String(props.record.id).slice(-3)}` : '',
      name: props.record?.title || '',
      summary: props.record?.publishedRevision?.conclusion || props.record?.summary || '',
      category: 'hardware_project'
    }
    formRef.value?.clearValidate()
  }
)

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', {
    code: form.value.code.trim(),
    name: form.value.name.trim(),
    summary: form.value.summary.trim(),
    category: form.value.category,
    recordVersion: props.record?.version
  })
}
</script>

<style scoped>
.convert-hint {
  margin-bottom: var(--usn-space-4);
}

.full-width {
  width: 100%;
}

.source-box {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--usn-space-3);
  background: var(--usn-canvas);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-sm);
}

.source-box__label {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.source-box__value {
  font-weight: 600;
  color: var(--usn-ink-900);
}

@media (max-width: 767px) {
  :global(.convert-dialog) {
    width: 94vw !important;
  }
}
</style>
