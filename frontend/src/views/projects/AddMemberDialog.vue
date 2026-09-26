<template>
  <el-dialog
    :model-value="modelValue"
    title="添加项目成员"
    width="420px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" label-position="top">
      <el-form-item label="学工号" prop="memberId">
        <el-input v-model="form.memberId" placeholder="输入成员学工号" maxlength="64" />
      </el-form-item>
      <el-form-item label="项目角色" prop="projectRole">
        <el-select v-model="form.projectRole" placeholder="选择角色" style="width: 100%">
          <el-option
            v-for="(meta, key) in addableRoles"
            :key="key"
            :label="meta.label"
            :value="key"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">添加</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { addProjectMember, PROJECT_ROLE, PROJECT_ROLE_META } from '@/api/projects'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  projectId: { type: [Number, String], required: true }
})

const emit = defineEmits(['update:modelValue', 'added'])

const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  memberId: '',
  projectRole: 'MEMBER'
})

const rules = {
  memberId: [
    { required: true, message: '请输入学工号', trigger: 'blur' },
    { max: 64, message: '最多 64 个字符', trigger: 'blur' }
  ],
  projectRole: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

// 添加成员不允许直接指定 OWNER（OWNER 转让为待确认项）
const addableRoles = computed(() => ({
  [PROJECT_ROLE.MAINTAINER]: PROJECT_ROLE_META[PROJECT_ROLE.MAINTAINER],
  [PROJECT_ROLE.MEMBER]: PROJECT_ROLE_META[PROJECT_ROLE.MEMBER],
  [PROJECT_ROLE.OBSERVER]: PROJECT_ROLE_META[PROJECT_ROLE.OBSERVER]
}))

function close() {
  emit('update:modelValue', false)
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const res = await addProjectMember(props.projectId, {
      memberId: form.memberId,
      projectRole: form.projectRole
    })
    ElMessage.success('成员添加成功')
    emit('added', res)
    form.memberId = ''
    form.projectRole = 'MEMBER'
  } catch (err) {
    console.error('添加成员失败', err)
    // 错误提示已由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>
