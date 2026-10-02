<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑任务' : '创建任务'"
    width="min(560px, 94vw)"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="syncForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="任务标题" prop="title">
        <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="2-120 字符" />
      </el-form-item>
      <el-form-item label="任务说明" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="2000"
          show-word-limit
          placeholder="可选，最多 2000 字符"
        />
      </el-form-item>
      <div class="task-form__row">
        <el-form-item label="所属里程碑" prop="milestoneId">
          <el-select v-model="form.milestoneId" placeholder="未归类" clearable style="width: 100%">
            <el-option v-for="m in milestones" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人" prop="assigneeUserId">
          <el-select v-model="form.assigneeUserId" placeholder="未分配" clearable style="width: 100%">
            <el-option v-for="m in members" :key="m.userId" :label="m.name" :value="m.userId" />
          </el-select>
        </el-form-item>
      </div>
      <div class="task-form__row">
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="form.priority" style="width: 100%">
            <el-option v-for="(meta, key) in TASK_PRIORITY_META" :key="key" :label="meta.label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期" prop="dueDate">
          <el-date-picker
            v-model="form.dueDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        {{ isEdit ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createTask, updateTask, TASK_PRIORITY_META, TASK_PRIORITY } from '@/api/tasks'
import { isVersionConflict, notifyApiError } from '@/utils/apiError'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  project: { type: Object, required: true },
  task: { type: Object, default: null },
  milestones: { type: Array, default: () => [] },
  members: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'saved', 'conflict'])

const formRef = ref(null)
const submitting = ref(false)

const isEdit = computed(() => Boolean(props.task))

const form = reactive({
  title: '',
  description: '',
  milestoneId: null,
  assigneeUserId: null,
  priority: TASK_PRIORITY.MEDIUM,
  dueDate: null
})

const rules = {
  title: [
    { required: true, message: '请输入任务标题', trigger: 'blur' },
    { min: 2, max: 120, message: '2-120 字符', trigger: 'blur' }
  ],
  description: [{ max: 2000, message: '最多 2000 字符', trigger: 'blur' }]
}

function syncForm() {
  const source = props.task
  form.title = source?.title || ''
  form.description = source?.description || ''
  form.milestoneId = source?.milestoneId ?? null
  form.assigneeUserId = source?.assigneeUserId ?? null
  form.priority = source?.priority || TASK_PRIORITY.MEDIUM
  form.dueDate = source?.dueDate || null
}

function close() {
  emit('update:modelValue', false)
}

function buildPayload() {
  return {
    title: form.title.trim(),
    description: form.description || null,
    milestoneId: form.milestoneId ?? null,
    assigneeUserId: form.assigneeUserId ?? null,
    priority: form.priority,
    dueDate: form.dueDate || null
  }
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateTask(props.project.id, props.task.id, {
        ...buildPayload(),
        version: props.task.version
      })
      ElMessage.success('任务已保存')
    } else {
      await createTask(props.project.id, buildPayload())
      ElMessage.success('任务创建成功')
    }
    emit('saved')
  } catch (err) {
    if (isVersionConflict(err)) {
      emit('conflict')
    } else {
      notifyApiError(err)
      console.error('保存任务失败', err)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.task-form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--usn-space-4);
}

@media (max-width: 520px) {
  .task-form__row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>