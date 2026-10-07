<template>
  <el-dialog
    :model-value="modelValue"
    title="创建里程碑"
    width="min(520px, 94vw)"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="里程碑名称" prop="name">
        <el-input v-model="form.name" maxlength="80" show-word-limit placeholder="2-80 字符" />
      </el-form-item>
      <el-form-item label="里程碑描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="可选，最多 500 字符"
        />
      </el-form-item>
      <div class="milestone-form__row">
        <el-form-item label="计划开始" prop="startDate">
          <el-date-picker
            v-model="form.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="计划完成" prop="endDate">
          <el-date-picker
            v-model="form.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>
      </div>
      <el-form-item label="排序序号" prop="sortOrder">
        <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
        <span class="milestone-form__hint muted text-help">值越小越靠前</span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createMilestone } from '@/api/milestones'
import { notifyApiError } from '@/utils/apiError'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  project: { type: Object, required: true }
})

const emit = defineEmits(['update:modelValue', 'created'])

const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  name: '',
  description: '',
  startDate: null,
  endDate: null,
  sortOrder: 0
})

const rules = {
  name: [
    { required: true, message: '请输入里程碑名称', trigger: 'blur' },
    { min: 2, max: 80, message: '2-80 字符', trigger: 'blur' }
  ],
  description: [{ max: 500, message: '最多 500 字符', trigger: 'blur' }]
}

function resetForm() {
  form.name = ''
  form.description = ''
  form.startDate = null
  form.endDate = null
  form.sortOrder = 0
}

function close() {
  emit('update:modelValue', false)
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const created = await createMilestone(props.project.id, {
      name: form.name.trim(),
      description: form.description || null,
      status: 'PLANNED',
      startDate: form.startDate || null,
      endDate: form.endDate || null,
      sortOrder: Number(form.sortOrder) || 0
    })
    ElMessage.success('里程碑创建成功')
    emit('created', created)
  } catch (err) {
    notifyApiError(err)
    console.error('创建里程碑失败', err)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.milestone-form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--usn-space-4);
}

.milestone-form__hint {
  margin-left: var(--usn-space-3);
}

@media (max-width: 520px) {
  .milestone-form__row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>