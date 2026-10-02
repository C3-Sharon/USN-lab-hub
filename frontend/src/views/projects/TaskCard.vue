<template>
  <div class="task-card" :class="{ 'task-card--compact': compact }" @click="$emit('open', task)">
    <div class="task-card__head">
      <span class="task-card__title">{{ task.title }}</span>
      <!-- el-dropdown 仅在 split-button 模式 emit click，@click.stop 不会生效；
           用原生 span 承接点击以阻止冒泡到卡片（否则点菜单会同时打开任务详情） -->
      <span v-if="transitions.length" class="task-card__menu" @click.stop>
        <el-dropdown trigger="click" placement="bottom-end" @command="onCommand">
          <el-button text size="small" :icon="MoreFilled" aria-label="任务操作" />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="status in transitions" :key="status" :command="status">
                转为「{{ statusLabel(status) }}」
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </span>
    </div>

    <div class="task-card__tags">
      <el-tag size="small" :type="priorityMeta.tagType" effect="light">
        {{ priorityMeta.label }}优先级
      </el-tag>
      <el-tag v-if="task.status === 'BLOCKED'" size="small" type="danger" effect="dark">已阻塞</el-tag>
    </div>

    <div v-if="task.status === 'BLOCKED' && task.blockReason" class="task-card__block">
      阻塞：{{ task.blockReason }}
    </div>

    <div class="task-card__meta">
      <span class="task-card__assignee">
        <el-avatar :size="20" class="task-card__avatar">
          {{ task.assigneeName ? task.assigneeName.charAt(0) : '?' }}
        </el-avatar>
        <span class="task-card__assignee-name">{{ task.assigneeName || '未分配' }}</span>
      </span>
      <span v-if="task.dueDate" class="task-card__due" :class="dueClass">{{ dueLabel }}</span>
    </div>

    <div v-if="!compact && task.milestoneName" class="task-card__milestone">
      <el-icon><Flag /></el-icon>
      <span>{{ task.milestoneName }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { MoreFilled, Flag } from '@element-plus/icons-vue'
import { TASK_STATUS_META, TASK_PRIORITY_META } from '@/api/tasks'

const props = defineProps({
  task: { type: Object, required: true },
  transitions: { type: Array, default: () => [] },
  compact: { type: Boolean, default: false }
})

const emit = defineEmits(['open', 'change-status'])

const priorityMeta = computed(
  () => TASK_PRIORITY_META[props.task.priority] || { label: props.task.priority || '中', tagType: 'info' }
)

function statusLabel(status) {
  return TASK_STATUS_META[status]?.label || status
}

function onCommand(status) {
  emit('change-status', props.task, status)
}

/** 截止日期视觉：逾期红色、3 天内橙色、其余中性 */
const dueClass = computed(() => {
  const meta = dueMeta.value
  if (meta.level === 'overdue') return 'task-card__due--overdue'
  if (meta.level === 'soon') return 'task-card__due--soon'
  return ''
})

const dueLabel = computed(() => dueMeta.value.label)

const dueMeta = computed(() => {
  const raw = props.task.dueDate
  if (!raw) return { level: 'none', label: '' }
  const due = new Date(`${raw}T23:59:59`)
  if (Number.isNaN(due.getTime())) return { level: 'none', label: raw }
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const dueDay = new Date(due.getFullYear(), due.getMonth(), due.getDate())
  const diffDays = Math.round((dueDay - today) / 86400000)
  const label = raw.slice(5).replace('-', '/')
  if (props.task.status === 'DONE' || props.task.status === 'CANCELED') {
    return { level: 'none', label }
  }
  if (diffDays < 0) return { level: 'overdue', label: `逾期 ${label}` }
  if (diffDays <= 3) return { level: 'soon', label: `临近 ${label}` }
  return { level: 'none', label }
})
</script>

<style scoped>
.task-card {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  padding: var(--usn-space-3);
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s, border-color 0.15s;
}

.task-card:hover {
  transform: translateY(-1px);
  border-color: var(--usn-blue-600);
  box-shadow: 0 4px 12px rgba(23, 33, 43, 0.1);
}

.task-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-2);
}

.task-card__title {
  flex: 1;
  min-width: 0;
  font-size: var(--usn-font-size-body);
  font-weight: 600;
  color: var(--usn-ink-900);
  line-height: 1.4;
  word-break: break-word;
}

.task-card__menu {
  flex-shrink: 0;
}

.task-card__tags {
  display: flex;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.task-card__block {
  font-size: var(--usn-font-size-help);
  color: var(--usn-danger);
  background: rgba(180, 35, 24, 0.06);
  border-radius: var(--usn-radius-sm);
  padding: 4px 8px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.task-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-2);
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.task-card__assignee {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.task-card__assignee-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-card__avatar {
  background: var(--usn-blue-100);
  color: var(--usn-blue-700);
  font-weight: 700;
  font-size: 11px;
  flex-shrink: 0;
}

.task-card__due {
  flex-shrink: 0;
}

.task-card__due--overdue {
  color: var(--usn-danger);
  font-weight: 600;
}

.task-card__due--soon {
  color: var(--usn-warning);
  font-weight: 600;
}

.task-card__milestone {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-card--compact .task-card__title {
  font-size: 13px;
}
</style>