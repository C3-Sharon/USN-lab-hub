<template>
  <el-dialog
    :model-value="modelValue"
    title="创建项目"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" label-position="top">
      <el-form-item label="项目编号" prop="code">
        <el-input v-model="form.code" placeholder="如 PROJ-001，3-32 位大写字母/数字/连字符" maxlength="32" />
      </el-form-item>
      <el-form-item label="项目名称" prop="name">
        <el-input v-model="form.name" placeholder="2-80 个字符" maxlength="80" />
      </el-form-item>
      <el-form-item label="项目简介" prop="summary">
        <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" placeholder="可选，最多 500 字符" />
      </el-form-item>
      <el-form-item label="项目分类" prop="category">
        <el-input v-model="form.category" placeholder="如 hardware_project，小写字母/数字/下划线" maxlength="32" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { createProject } from '@/api/projects'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'created'])

const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  code: '',
  name: '',
  summary: '',
  category: ''
})

const rules = {
  code: [
    { required: true, message: '请输入项目编号', trigger: 'blur' },
    { pattern: /^[A-Z0-9-]{3,32}$/, message: '3-32 位大写字母、数字或连字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { min: 2, max: 80, message: '2-80 个字符', trigger: 'blur' }
  ],
  summary: [
    { max: 500, message: '最多 500 字符', trigger: 'blur' }
  ],
  category: [
    { pattern: /^$|^[a-z][a-z0-9_]{1,31}$/, message: '小写字母/数字/下划线，以字母开头', trigger: 'blur' }
  ]
}

function close() {
  emit('update:modelValue', false)
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const res = await createProject({
      code: form.code,
      name: form.name,
      summary: form.summary || undefined,
      category: form.category || undefined
    })
    ElMessage.success('项目创建成功')
    emit('created', res)
    // reset
    form.code = ''
    form.name = ''
    form.summary = ''
    form.category = ''
  } catch (err) {
    console.error('创建项目失败', err)
    // 错误提示已由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>
