<template>
  <el-dialog
    :model-value="modelValue"
    title="复制系统模板为项目模板"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <p class="copy-hint text-help muted">
      源模板：<strong>{{ template?.title }}</strong>（{{ template?.latestVersion || '无已发布版本' }}）。
      复制后生成独立项目模板与草稿版本，后续互不影响。
    </p>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
      <el-form-item label="目标项目" prop="targetProjectId">
        <el-select v-model="form.targetProjectId" placeholder="选择目标项目" class="form-control" @change="handleProjectChange">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="新标题" prop="title">
        <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="复制后的模板标题" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">复制</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  template: { type: Object, default: null },
  projects: { type: Array, default: () => [] },
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref(null)
const form = reactive({ targetProjectId: null, title: '', description: '' })

const rules = {
  targetProjectId: [{ required: true, message: '请选择目标项目', trigger: 'change' }],
  title: [
    { required: true, message: '请输入新标题', trigger: 'blur' },
    { min: 2, max: 120, message: '标题需为 2-120 字符', trigger: 'blur' }
  ]
}

function projectName(id) {
  return props.projects.find((p) => p.id === Number(id))?.name || '项目'
}

function handleProjectChange(id) {
  form.title = `${props.template?.title || '模板'} - ${projectName(id)}`
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.targetProjectId = null
    form.description = props.template?.description || ''
    form.title = ''
    formRef.value?.clearValidate?.()
  }
)

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  emit('submit', {
    targetProjectId: form.targetProjectId,
    title: form.title.trim(),
    description: form.description || null
  })
}
</script>

<style scoped>
.copy-hint {
  margin: 0 0 var(--usn-space-4);
  line-height: 1.6;
}

.form-control {
  width: 100%;
}
</style>
