<template>
  <div class="milestone-panel">
    <div class="milestone-panel__toolbar">
      <span class="muted text-help">共 {{ milestones.length }} 个里程碑</span>
      <el-button v-if="canCreate" type="primary" :icon="Plus" @click="dialogVisible = true">
        创建里程碑
      </el-button>
    </div>

    <RegionState :state="state" :variant="'list'" :error-message="error" @retry="loadMilestones">
      <div v-if="milestones.length" class="milestone-list">
        <el-card v-for="item in milestones" :key="item.id" class="milestone-card" shadow="never">
          <div class="milestone-card__head">
            <div class="milestone-card__title-wrap">
              <h3 class="milestone-card__title">{{ item.name }}</h3>
              <el-tag :type="statusMeta(item.status).tagType" size="small" effect="light">
                {{ statusMeta(item.status).label }}
              </el-tag>
            </div>
            <el-button
              v-if="nextStatus(item)"
              type="primary"
              plain
              size="small"
              :loading="advancingId === item.id"
              @click="advance(item)"
            >
              推进为「{{ statusMeta(nextStatus(item)).label }}」
            </el-button>
          </div>

          <p class="milestone-card__desc">{{ item.description || '暂无描述' }}</p>

          <div class="milestone-card__dates">
            <el-icon><Calendar /></el-icon>
            <span>{{ item.startDate || '--' }} ~ {{ item.endDate || '--' }}</span>
          </div>

          <div class="milestone-card__progress">
            <el-progress
              :percentage="percent(item)"
              :stroke-width="10"
              :status="item.status === 'COMPLETED' ? 'success' : undefined"
            />
            <span class="milestone-card__progress-text muted text-help">
              {{ item.taskDone }}/{{ item.taskCount }} 任务完成
            </span>
          </div>
        </el-card>
      </div>

      <el-empty v-else-if="!loading" description="暂无里程碑">
        <el-button v-if="canCreate" type="primary" @click="dialogVisible = true">创建第一个里程碑</el-button>
      </el-empty>
    </RegionState>

    <CreateMilestoneDialog v-model="dialogVisible" :project="project" @created="onCreated" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Calendar } from '@element-plus/icons-vue'
import {
  listMilestones,
  updateMilestoneStatus,
  nextMilestoneStatus,
  MILESTONE_STATUS_META
} from '@/api/milestones'
import { canCreateMilestone, canUpdateMilestoneStatus } from '@/utils/projectPermission'
import { notifyApiError } from '@/utils/apiError'
import RegionState from '@/components/RegionState.vue'
import CreateMilestoneDialog from './CreateMilestoneDialog.vue'

const props = defineProps({
  project: { type: Object, required: true }
})

const loading = ref(false)
const error = ref('')
const milestones = ref([])
const dialogVisible = ref(false)
const advancingId = ref(null)

const canCreate = computed(() => canCreateMilestone(props.project?.myRole))

const state = computed(() => {
  if (error.value) return 'error'
  if (loading.value && !milestones.value.length) return 'loading'
  return 'success'
})

function statusMeta(status) {
  return MILESTONE_STATUS_META[status] || { label: status, tagType: 'info' }
}

function percent(item) {
  if (!item.taskCount) return 0
  return Math.round((item.taskDone / item.taskCount) * 100)
}

function nextStatus(item) {
  if (!canUpdateMilestoneStatus(props.project?.myRole)) return null
  return nextMilestoneStatus(item.status)
}

async function loadMilestones() {
  loading.value = true
  error.value = ''
  try {
    milestones.value = await listMilestones(props.project.id)
  } catch (err) {
    error.value = err?.msg || err?.message || '里程碑加载失败'
  } finally {
    loading.value = false
  }
}

async function advance(item) {
  const target = nextStatus(item)
  if (!target) return
  advancingId.value = item.id
  try {
    await updateMilestoneStatus(props.project.id, item.id, target)
    ElMessage.success('里程碑状态已更新')
    await loadMilestones()
  } catch (err) {
    notifyApiError(err)
    console.error('更新里程碑状态失败', err)
  } finally {
    advancingId.value = null
  }
}

function onCreated() {
  dialogVisible.value = false
  loadMilestones()
}

onMounted(() => loadMilestones())
</script>

<style scoped>
.milestone-panel {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.milestone-panel__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.milestone-list {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.milestone-card {
  border: 1px solid var(--usn-line);
}

.milestone-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.milestone-card__title-wrap {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.milestone-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--usn-ink-900);
}

.milestone-card__desc {
  margin: var(--usn-space-2) 0;
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
  line-height: 1.5;
}

.milestone-card__dates {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
  margin-bottom: var(--usn-space-3);
}

.milestone-card__progress {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
}

.milestone-card__progress :deep(.el-progress) {
  flex: 1;
}

.milestone-card__progress-text {
  flex-shrink: 0;
}
</style>