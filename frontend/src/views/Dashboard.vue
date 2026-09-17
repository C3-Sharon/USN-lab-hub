<template>
  <div class="workbench-page">
    <header class="workbench-page__header">
      <div>
        <h1 class="page-title">个人工作台</h1>
        <p class="page-subtitle muted">今日考勤、进行中项目、本周任务、学习、设备提醒</p>
      </div>
      <div class="header-meta">
        <el-tag :type="roleMeta.tagType" size="large" effect="dark" class="role-tag">
          {{ roleMeta.name }}
        </el-tag>
        <span class="muted text-help">欢迎回来，{{ user?.username || '同学' }}</span>
      </div>
    </header>

    <div v-loading="overviewLoading" class="workbench-grid">
      <section class="region region--attendance">
        <header class="region__head">
          <h2 class="region__title">今日考勤</h2>
          <span class="region__sub muted text-help">签到/签退/累计时长</span>
        </header>
        <RegionState
          :state="attendanceState"
          :variant="'card'"
          :error-message="overviewError"
          @retry="loadOverview"
        >
          <div v-if="attendance" class="attendance-content">
            <div class="attendance-content__hero">
              <el-tag :type="attendance.tagType" effect="dark" size="large">
                {{ attendance.statusLabel }}
              </el-tag>
              <div class="attendance-content__hours">
                <span class="muted text-help">本周学时</span>
                <strong>{{ formatHours(attendance.weekHours) }}</strong>
              </div>
              <div class="attendance-content__hours">
                <span class="muted text-help">本学期学时</span>
                <strong>{{ formatHours(attendance.semesterHours) }}</strong>
              </div>
            </div>
            <el-table :data="attendance.todayRecords || []" size="small" empty-text="今日暂无打卡记录">
              <el-table-column prop="inTime" label="签到时间" min-width="120" />
              <el-table-column prop="outTime" label="签退时间" min-width="120" />
              <el-table-column label="时长累计" min-width="100">
                <template #default="{ row }">{{ formatMinutes(row.durationMins) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </RegionState>
      </section>

      <section class="region region--projects">
        <header class="region__head">
          <h2 class="region__title">我参与的项目</h2>
          <span class="region__sub muted text-help">{{ projectsHint }}</span>
        </header>
        <RegionState
          :state="projectsState"
          :variant="'list'"
          :description="projectsHint"
          :error-message="overview?.projects?.message || overviewError"
          @retry="loadOverview"
        />
      </section>

      <section class="region region--tasks">
        <header class="region__head">
          <h2 class="region__title">本周任务</h2>
          <span class="region__sub muted text-help">{{ tasksHint }}</span>
        </header>
        <RegionState
          :state="tasksState"
          :variant="'list'"
          :description="tasksHint"
          :error-message="overview?.tasks?.message || overviewError"
          @retry="loadOverview"
        />
      </section>

      <section class="region region--learning">
        <header class="region__head">
          <h2 class="region__title">学习实验</h2>
          <span class="region__sub muted text-help">{{ learningHint }}</span>
        </header>
        <RegionState
          :state="learningState"
          :variant="'list'"
          :description="learningHint"
          :error-message="overview?.learning?.message || overviewError"
          @retry="loadOverview"
        />
      </section>

      <section class="region region--notifications">
        <header class="region__head">
          <h2 class="region__title">待处理事项</h2>
          <span class="region__sub muted text-help">{{ notificationsHint }}</span>
        </header>
        <RegionState
          :state="notificationsState"
          :variant="'list'"
          :description="notificationsHint"
          :error-message="overview?.notifications?.message || overviewError"
          @retry="loadOverview"
        />
      </section>

      <section class="region region--device">
        <header class="region__head">
          <h2 class="region__title">设备提醒</h2>
          <span class="region__sub muted text-help">设备在线与告警汇总</span>
        </header>
        <RegionState
          :state="deviceState"
          :variant="'card'"
          :error-message="deviceReminder?.message || overviewError"
          @retry="loadOverview"
        >
          <div v-if="deviceReminder" class="device-content">
            <div class="device-content__metric">
              <span class="muted text-help">在线设备</span>
              <strong>{{ deviceReminder.onlineCount }}</strong>
            </div>
            <div class="device-content__metric">
              <span class="muted text-help">告警数</span>
              <strong>{{ deviceReminder.alertCount }}</strong>
            </div>
          </div>
        </RegionState>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userStore } from '@/store/user'
import { currentPrimaryRole, currentRoles } from '@/store/user'
import { ROLE_META, ROLE } from '@/utils/permission'
import { fetchWorkbenchOverview } from '@/api/workbench'
import { formatHours, formatMinutes } from '@/utils/format'
import RegionState from '@/components/RegionState.vue'

const route = useRoute()

const user = computed(() => userStore.userInfo || {})
const primaryRole = computed(() => currentPrimaryRole() || ROLE.MEMBER)
const roleMeta = computed(() => ROLE_META[primaryRole.value] || ROLE_META[ROLE.MEMBER])

const overviewLoading = ref(false)
const overviewError = ref('')
const overview = ref(null)

const attendance = computed(() => {
  const source = overview.value?.attendance
  if (!source || source.state !== 'READY') return null
  const statusMeta = {
    0: { statusLabel: '未签到', tagType: 'info' },
    1: { statusLabel: '已签到', tagType: 'success' },
    2: { statusLabel: '已签退', tagType: 'warning' }
  }
  return { ...source, ...(statusMeta[source.todayStatus] || statusMeta[0]) }
})
const attendanceState = computed(() => regionState(overview.value?.attendance))

const projectsHint = computed(() => {
  const r = overview.value?.projects
  if (r?.state === 'NOT_AVAILABLE') return '尚未开放'
  if (!r) return '加载中'
  return ''
})
const projectsState = computed(() => {
  if (overviewError.value) return 'error'
  if (overviewLoading.value && !overview.value) return 'loading'
  if (!overview.value) return 'loading'
  return regionState(overview.value.projects)
})

const tasksHint = computed(() => regionHint(overview.value?.tasks))
const tasksState = computed(() => regionState(overview.value?.tasks))

const learningHint = computed(() => regionHint(overview.value?.learning))
const learningState = computed(() => regionState(overview.value?.learning))

const notificationsHint = computed(() => regionHint(overview.value?.notifications))
const notificationsState = computed(() => regionState(overview.value?.notifications))

const deviceReminder = computed(() => overview.value?.deviceReminder || null)
const deviceState = computed(() => regionState(deviceReminder.value))

function regionHint(region) {
  if (!region) return '加载中'
  if (region.state === 'NOT_AVAILABLE') return '尚未开放'
  return ''
}

function regionState(region) {
  if (overviewError.value) return 'error'
  if (overviewLoading.value && !overview.value) return 'loading'
  if (!region) return 'loading'
  if (region.state === 'NOT_AVAILABLE') return 'not-available'
  if (region.state === 'READY') return 'success'
  if (region.state === 'ERROR') return 'error'
  return 'empty'
}

async function loadOverview() {
  overviewLoading.value = true
  overviewError.value = ''
  try {
    overview.value = await fetchWorkbenchOverview()
  } catch (err) {
    overviewError.value = err?.msg || err?.message || '工作台数据加载失败'
    if (err?.code === 403 || err?.code === 'ACCESS_DENIED') {
      ElMessage.warning('当前账号无权访问工作台')
    }
  } finally {
    overviewLoading.value = false
  }
}

onMounted(() => {
  loadOverview()
  if (route.query.reason === 'ACCOUNT_DISABLED') {
    ElMessage.error('账号已被禁用，请联系管理员')
  } else if (route.query.reason) {
    ElMessage.warning('登录状态已失效，请重新登录')
  }
})

defineExpose({ currentRoles })
</script>

<style scoped>
.workbench-page {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
  max-width: var(--usn-content-max-width);
  margin: 0 auto;
  width: 100%;
}

.workbench-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-4);
  flex-wrap: wrap;
}

.header-meta {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
}

.role-tag {
  font-weight: 600;
}

.workbench-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--usn-space-4);
  width: 100%;
}

.region {
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  padding: var(--usn-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
  box-shadow: var(--usn-shadow-panel);
  min-height: 240px;
}

.region--attendance {
  grid-column: span 2;
}

.region__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.region__title {
  margin: 0;
  font-size: var(--usn-font-size-panel-title);
  font-weight: 700;
  color: var(--usn-ink-900);
}

.region__sub {
  font-size: var(--usn-font-size-help);
}

.attendance-content {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.attendance-content__hero {
  display: flex;
  align-items: center;
  gap: var(--usn-space-6);
  flex-wrap: wrap;
}

.attendance-content__hours {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.attendance-content__hours strong {
  font-size: 20px;
  color: var(--usn-blue-700);
  font-weight: 700;
}

.device-content {
  display: flex;
  gap: var(--usn-space-6);
  flex-wrap: wrap;
}

.device-content__metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.device-content__metric strong {
  font-size: 24px;
  color: var(--usn-blue-700);
  font-weight: 700;
}

@media (max-width: 1280px) {
  .workbench-grid {
    grid-template-columns: 1fr;
  }

  .region--attendance {
    grid-column: span 1;
  }
}

@media (max-width: 768px) {
  .workbench-page__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .attendance-content__hero,
  .device-content {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--usn-space-3);
  }
}
</style>
