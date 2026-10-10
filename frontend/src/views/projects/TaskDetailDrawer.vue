<template>
  <el-drawer
    :model-value="modelValue"
    :size="'min(480px, 92vw)'"
    title="任务详情"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="task" class="task-detail">
      <div class="task-detail__head">
        <h3 class="task-detail__title">{{ task.title }}</h3>
        <div class="task-detail__tags">
          <el-tag :type="statusMeta.tagType" effect="light">{{ statusMeta.label }}</el-tag>
          <el-tag :type="priorityMeta.tagType" effect="plain">{{ priorityMeta.label }}优先级</el-tag>
        </div>
      </div>

      <div v-if="task.status === 'BLOCKED' && task.blockReason" class="task-detail__block">
        <el-icon><WarningFilled /></el-icon>
        <div>
          <strong>阻塞原因</strong>
          <p>{{ task.blockReason }}</p>
        </div>
      </div>

      <el-descriptions :column="1" border class="task-detail__desc">
        <el-descriptions-item label="所属里程碑">{{ task.milestoneName || '未归类' }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ task.assigneeName || '未分配' }}</el-descriptions-item>
        <el-descriptions-item label="截止日期">{{ task.dueDate || '--' }}</el-descriptions-item>
        <el-descriptions-item label="任务说明">
          <span class="task-detail__text">{{ task.description || '暂无说明' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(task.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="最近更新">{{ formatTime(task.updateTime) }}</el-descriptions-item>
        <el-descriptions-item label="版本号">v{{ task.version }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <template #footer>
      <div class="task-detail__actions">
        <el-dropdown v-if="transitions.length" trigger="click" @command="onCommand">
          <el-button type="primary">
            状态变更<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="status in transitions" :key="status" :command="status">
                转为「{{ statusLabel(status) }}」
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button v-if="canEdit" :icon="EditPen" @click="$emit('edit', task)">编辑</el-button>
        <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowDown, EditPen, WarningFilled } from '@element-plus/icons-vue'
import { TASK_STATUS_META, TASK_PRIORITY_META } from '@/api/tasks'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  task: { type: Object, default: null },
  transitions: { type: Array, default: () => [] },
  canEdit: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'change-status', 'edit'])

const statusMeta = computed(
  () => TASK_STATUS_META[props.task?.status] || { label: props.task?.status || '--', tagType: 'info' }
)
const priorityMeta = computed(
  () => TASK_PRIORITY_META[props.task?.priority] || { label: props.task?.priority || '--', tagType: 'info' }
)

function statusLabel(status) {
  return TASK_STATUS_META[status]?.label || status
}

function formatTime(iso) {
  if (!iso) return '--'
  const d = new Date(iso)
  return isNaN(d) ? iso : d.toLocaleString('zh-CN', { hour12: false })
}

function onCommand(status) {
  emit('change-status', props.task, status)
}
</script>

<style scoped>
.task-detail {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.task-detail__head {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
}

.task-detail__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--usn-ink-900);
  line-height: 1.4;
}

.task-detail__tags {
  display: flex;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.task-detail__block {
  display: flex;
  gap: var(--usn-space-2);
  padding: var(--usn-space-3);
  border-radius: var(--usn-radius-md);
  background: rgba(180, 35, 24, 0.08);
  border: 1px solid rgba(180, 35, 24, 0.25);
  color: var(--usn-danger);
}

.task-detail__block strong {
  display: block;
  margin-bottom: 4px;
}

.task-detail__block p {
  margin: 0;
  color: var(--usn-ink-900);
  line-height: 1.5;
}

.task-detail__text {
  white-space: pre-wrap;
  line-height: 1.6;
}

.task-detail__actions {
  display: flex;
  gap: var(--usn-space-2);
  justify-content: flex-end;
  flex-wrap: wrap;
}
</style>