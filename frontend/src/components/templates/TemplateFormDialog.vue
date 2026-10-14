<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'create' ? '创建实验模板' : '编辑模板基本信息'"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="如：电压测量实验" />
      </el-form-item>

      <el-form-item label="描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="模板用途与适用场景（选填）"
        />
      </el-form-item>

      <el-form-item label="分类" prop="category">
        <el-select v-model="form.category" allow-create filterable clearable placeholder="选择或输入分类（选填）" class="form-control">
          <el-option v-for="opt in CATEGORY_OPTIONS" :key="opt" :label="opt" :value="opt" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="mode === 'create'" label="范围" prop="scope">
        <el-radio-group v-model="form.scope">
          <el-radio-button :value="TEMPLATE_SCOPE.SYSTEM" :disabled="!allowSystem">系统模板</el-radio-button>
          <el-radio-button :value="TEMPLATE_SCOPE.PROJECT">项目模板</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-else label="范围">
        <span>{{ scopeMeta(form.scope).label }}<span v-if="template?.projectName" class="text-help muted"> · {{ template.projectName }}</span></span>
        <p class="form-hint text-help muted">模板范围与所属项目创建后不可修改</p>
      </el-form-item>

      <el-form-item v-if="mode === 'create' && form.scope === TEMPLATE_SCOPE.PROJECT" label="所属项目" prop="projectId">
        <el-select v-model="form.projectId" placeholder="选择项目" class="form-control">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ mode === 'create' ? '创建' : '保存' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { TEMPLATE_SCOPE, templateScopeMeta } from '@/api/experimentTemplates'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  mode: { type: String, default: 'create' },
  template: { type: Object, default: null },
  projects: { type: Array, default: () => [] },
  allowSystem: { type: Boolean, default: true },
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const CATEGORY_OPTIONS = ['电路测试', '传感器', '电路基础', '校准', '数据采集', '编程入门']

const formRef = ref(null)
const form = reactive({ title: '', description: '', category: '', scope: TEMPLATE_SCOPE.SYSTEM, projectId: null })

const rules = computed(() => ({
  title: [
    { required: true, message: '请输入模板标题', trigger: 'blur' },
    { min: 2, max: 120, message: '标题需为 2-120 字符', trigger: 'blur' }
  ],
  scope: [{ required: true, message: '请选择模板范围', trigger: 'change' }],
  projectId: [
    {
      validator: (_rule, value, callback) => {
        if (form.scope === TEMPLATE_SCOPE.PROJECT && value == null) callback(new Error('项目模板必须选择所属项目'))
        else callback()
      },
      trigger: 'change'
    }
  ]
}))

function scopeMeta(scope) {
  return templateScopeMeta(scope)
}

function reset() {
  const t = props.template
  form.title = t?.title || ''
  form.description = t?.description || ''
  form.category = t?.category || ''
  form.scope = t?.scope || (props.allowSystem ? TEMPLATE_SCOPE.SYSTEM : TEMPLATE_SCOPE.PROJECT)
  form.projectId = t?.projectId ?? null
  formRef.value?.clearValidate?.()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) reset()
  }
)

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (props.mode === 'create') {
    emit('submit', {
      title: form.title.trim(),
      description: form.description || null,
      category: form.category || null,
      scope: form.scope,
      projectId: form.scope === TEMPLATE_SCOPE.PROJECT ? form.projectId : null
    })
  } else {
    emit('submit', {
      title: form.title.trim(),
      description: form.description || null,
      category: form.category || null
    })
  }
}
</script>

<style scoped>
.form-control {
  width: 100%;
}

.form-hint {
  margin: 4px 0 0;
  line-height: 1.4;
}
</style>
