<template>
  <div class="task-kanban">
    <!-- 工具栏：筛选 + 操作 -->
    <div class="kanban-toolbar">
      <div v-show="!isMobile || filterOpen" class="kanban-toolbar__filters">
        <el-input
          v-model="filters.keyword"
          class="kanban-filter kanban-filter--keyword"
          placeholder="搜索任务标题"
          clearable
          @keyup.enter="reload"
          @clear="reload"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select
          v-model="filters.milestoneId"
          class="kanban-filter"
          placeholder="全部里程碑"
          clearable
          @change="reload"
        >
          <el-option v-for="m in milestones" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
        <el-select
          v-model="filters.assigneeUserId"
          class="kanban-filter"
          placeholder="全部负责人"
          clearable
          @change="reload"
        >
          <el-option v-for="m in memberOptions" :key="m.userId" :label="m.name" :value="m.userId" />
        </el-select>
        <el-checkbox v-model="filters.showCanceled" class="kanban-filter--canceled">显示已取消</el-checkbox>
      </div>

      <div class="kanban-toolbar__actions">
        <el-button v-if="isMobile" :icon="Filter" @click="filterOpen = !filterOpen">筛选</el-button>
        <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">创建任务</el-button>
      </div>
    </div>

    <RegionState :state="state" :variant="'list'" :error-message="error" @retry="reload">
      <div class="kanban-board" :class="{ 'kanban-board--stacked': isMobile }">
        <section
          v-for="status in columns"
          :key="status"
          class="kanban-column"
          :class="`kanban-column--${status.toLowerCase()}`"
        >
          <header class="kanban-column__head" @click="toggleCollapse(status)">
            <span class="kanban-column__title">
              <span class="kanban-column__dot" :class="`dot--${status.toLowerCase()}`" />
              {{ statusLabel(status) }}
            </span>
            <span class="kanban-column__count">{{ grouped[status]?.length || 0 }}</span>
            <el-icon v-if="isMobile" class="kanban-column__caret" :class="{ 'is-collapsed': collapsed[status] }">
              <ArrowDown />
            </el-icon>
          </header>

          <div v-show="!isMobile || !collapsed[status]" class="kanban-column__body">
            <TaskCard
              v-for="task in grouped[status]"
              :key="task.id"
              :task="task"
              :transitions="transitionsFor(task)"
              :compact="isMobile"
              @open="openTask"
              @change-status="handleStatusChange"
            />
            <el-empty
              v-if="!grouped[status]?.length"
              :image-size="56"
              :description="emptyText(status)"
              class="kanban-column__empty"
            />
          </div>
        </section>
      </div>
    </RegionState>

    <!-- 任务详情抽屉 -->
    <TaskDetailDrawer
      v-model="drawerVisible"
      :task="activeTask"
      :transitions="activeTask ? transitionsFor(activeTask) : []"
      :can-edit="canEdit"
      @change-status="handleStatusChange"
      @edit="openEdit"
    />

    <!-- 创建 / 编辑任务表单 -->
    <TaskFormDialog
      v-model="formVisible"
      :project="project"
      :task="editingTask"
      :milestones="milestones"
      :members="memberOptions"
      @saved="onSaved"
      @conflict="onConflict"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Filter, ArrowDown } from '@element-plus/icons-vue'
import {
  BOARD_STATUSES,
  TASK_STATUS,
  TASK_STATUS_META,
  listTasks,
  updateTaskStatus
} from '@/api/tasks'
import { listMilestones } from '@/api/milestones'
import { allowedTaskTransitions, canCreateTask, canEditTask } from '@/utils/projectPermission'
import { isVersionConflict, promptVersionConflict } from '@/utils/apiError'
import RegionState from '@/components/RegionState.vue'
import TaskCard from './TaskCard.vue'
import TaskDetailDrawer from './TaskDetailDrawer.vue'
import TaskFormDialog from './TaskFormDialog.vue'

const props = defineProps({
  project: { type: Object, required: true }
})

const loading = ref(false)
const error = ref('')
const tasks = ref([])
const milestones = ref([])

const filters = reactive({
  milestoneId: null,
  assigneeUserId: null,
  keyword: '',
  showCanceled: false
})

const isMobile = ref(false)
const filterOpen = ref(false)
const collapsed = reactive({})

const drawerVisible = ref(false)
const activeTask = ref(null)
const formVisible = ref(false)
const editingTask = ref(null)

let mediaQuery = null

const canCreate = computed(() => canCreateTask(props.project?.myRole))
const canEdit = computed(() => canEditTask(props.project?.myRole))

const columns = computed(() =>
  filters.showCanceled ? [...BOARD_STATUSES, TASK_STATUS.CANCELED] : [...BOARD_STATUSES]
)

const memberOptions = computed(() => props.project?.members || [])

const grouped = computed(() => {
  const map = {}
  for (const status of columns.value) map[status] = []
  for (const task of tasks.value) {
    if (map[task.status]) map[task.status].push(task)
  }
  return map
})

const state = computed(() => {
  if (error.value) return 'error'
  if (loading.value && !tasks.value.length) return 'loading'
  return 'success'
})

function statusLabel(status) {
  return TASK_STATUS_META[status]?.label || status
}

function emptyText(status) {
  const map = {
    [TASK_STATUS.TODO]: '暂无待开始任务',
    [TASK_STATUS.IN_PROGRESS]: '暂无进行中任务',
    [TASK_STATUS.BLOCKED]: '暂无阻塞任务',
    [TASK_STATUS.DONE]: '暂无已完成任务',
    [TASK_STATUS.CANCELED]: '暂无已取消任务'
  }
  return map[status] || '暂无任务'
}

function transitionsFor(task) {
  return allowedTaskTransitions({ projectRole: props.project?.myRole, task })
}

function toggleCollapse(status) {
  if (!isMobile.value) return
  collapsed[status] = !collapsed[status]
}

function buildQuery() {
  const query = { pageSize: 200, sortBy: 'priority', sortOrder: 'desc' }
  if (filters.milestoneId) query.milestoneId = filters.milestoneId
  if (filters.assigneeUserId) query.assigneeUserId = filters.assigneeUserId
  if (filters.keyword) query.keyword = filters.keyword
  return query
}

async function loadTasks() {
  loading.value = true
  error.value = ''
  try {
    const res = await listTasks(props.project.id, buildQuery())
    tasks.value = res?.list || []
    syncActiveTask()
  } catch (err) {
    error.value = err?.msg || err?.message || '任务加载失败'
  } finally {
    loading.value = false
  }
}

async function loadMilestones() {
  try {
    milestones.value = await listMilestones(props.project.id)
  } catch (err) {
    console.error('加载里程碑失败', err)
  }
}

function syncActiveTask() {
  if (!activeTask.value) return
  const latest = tasks.value.find((t) => t.id === activeTask.value.id)
  if (latest) activeTask.value = latest
}

function reload() {
  loadTasks()
}

function openTask(task) {
  activeTask.value = task
  drawerVisible.value = true
}

function openCreate() {
  editingTask.value = null
  formVisible.value = true
}

function openEdit(task) {
  editingTask.value = task
  formVisible.value = true
}

function onSaved() {
  formVisible.value = false
  editingTask.value = null
  loadTasks()
  loadMilestones()
}

function onConflict() {
  formVisible.value = false
  editingTask.value = null
  promptVersionConflict(reload)
}

async function applyStatus(task, status, blockReason) {
  try {
    await updateTaskStatus(props.project.id, task.id, {
      status,
      version: task.version,
      blockReason: blockReason || null
    })
    ElMessage.success('任务状态已更新')
    await loadTasks()
    await loadMilestones()
  } catch (err) {
    if (isVersionConflict(err)) {
      promptVersionConflict(reload)
      return
    }
    console.error('任务状态变更失败', err)
  }
}

async function handleStatusChange(task, status) {
  if (!task || !status) return
  let blockReason = null
  if (status === TASK_STATUS.BLOCKED) {
    try {
      const { value } = await ElMessageBox.prompt('请输入阻塞原因（2-500 字符）', '标记为已阻塞', {
        confirmButtonText: '确认阻塞',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '例如：等待外部依赖到货',
        inputValidator: (val) => {
          const text = String(val || '').trim()
          return (text.length >= 2 && text.length <= 500) || '阻塞原因需为 2-500 字符'
        }
      })
      blockReason = String(value).trim()
    } catch {
      return
    }
  }
  await applyStatus(task, status, blockReason)
}

function updateMobile() {
  isMobile.value = mediaQuery ? mediaQuery.matches : window.innerWidth <= 768
}

onMounted(() => {
  mediaQuery = window.matchMedia('(max-width: 768px)')
  updateMobile()
  mediaQuery.addEventListener('change', updateMobile)
  loadMilestones()
  loadTasks()
})

onBeforeUnmount(() => {
  if (mediaQuery) mediaQuery.removeEventListener('change', updateMobile)
})
</script>

<style scoped>
.task-kanban {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.kanban-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.kanban-toolbar__filters {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
  flex: 1 1 auto;
}

.kanban-filter {
  width: 180px;
}

.kanban-filter--keyword {
  width: 240px;
}

.kanban-toolbar__actions {
  display: flex;
  gap: var(--usn-space-2);
  flex-shrink: 0;
}

.kanban-board {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--usn-space-3);
  align-items: start;
}

.kanban-column {
  background: var(--usn-canvas);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.kanban-column__head {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  padding: var(--usn-space-3);
  border-bottom: 1px solid var(--usn-line);
}

.kanban-column__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--usn-font-size-body);
  font-weight: 600;
  color: var(--usn-ink-900);
  flex: 1;
}

.kanban-column__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--usn-ink-500);
}

.dot--todo { background: var(--usn-ink-500); }
.dot--in_progress { background: var(--usn-blue-600); }
.dot--blocked { background: var(--usn-danger); }
.dot--done { background: var(--usn-success); }
.dot--canceled { background: #98a2b3; }

.kanban-column__count {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-radius: 999px;
  padding: 1px 8px;
  font-weight: 600;
}

.kanban-column__caret {
  transition: transform 0.2s;
}

.kanban-column__caret.is-collapsed {
  transform: rotate(-90deg);
}

.kanban-column__body {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  padding: var(--usn-space-3);
  min-height: 120px;
}

.kanban-column__empty {
  padding: 0;
}

.kanban-board--stacked {
  grid-template-columns: 1fr;
}

@media (max-width: 1024px) {
  .kanban-board {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .kanban-board--stacked {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .kanban-filter,
  .kanban-filter--keyword {
    width: 100%;
  }

  .kanban-toolbar__filters {
    flex-direction: column;
    align-items: stretch;
    width: 100%;
  }
}
</style>