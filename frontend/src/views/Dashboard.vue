<template>
  <div class="page-shell">
    <section class="hero-panel">
      <div>
        <h2>欢迎回来，{{ userInfo.username || '同学' }}</h2>
        <p>{{ userInfo.memberId || '-' }} · {{ userInfo.groupName || '未分组' }}</p>
      </div>
      <el-tag size="large" effect="dark" :type="attendanceTag.type">{{ attendanceTag.label }}</el-tag>
    </section>

    <el-row :gutter="18">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="info-card">
          <span>姓名</span>
          <strong>{{ userInfo.username || '-' }}</strong>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="info-card">
          <span>专业</span>
          <strong>{{ userInfo.majorName || '-' }}</strong>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="info-card">
          <span>身份</span>
          <strong>{{ userInfo.identity || '-' }}</strong>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="info-card">
          <span>学院</span>
          <strong>{{ userInfo.facultyName || '-' }}</strong>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="18">
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="metric-card">
          <span>本周学时</span>
          <strong>{{ formatHours(attendance.weekHours) }}</strong>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="metric-card">
          <span>本学期学时</span>
          <strong>{{ formatHours(attendance.semesterHours) }}</strong>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="action-card">
          <el-button
            class="attendance-button"
            :type="actionButton.type"
            size="large"
            :loading="submitting"
            :disabled="submitting || !actionButton.actionType"
            @click="handleAttendanceAction"
          >
            {{ actionButton.text }}
          </el-button>
        </el-card>
      </el-col>
    </el-row>

    <section class="data-panel">
      <div class="page-heading table-heading">
        <div>
          <h3 class="page-title">今日打卡明细</h3>
          <p class="page-subtitle">签到、签退和单次累计时长</p>
        </div>
      </div>
      <el-table :data="attendance.todayRecords || []" empty-text="今日暂无打卡记录">
        <el-table-column prop="inTime" label="签到时间" min-width="140" />
        <el-table-column prop="outTime" label="签退时间" min-width="140" />
        <el-table-column label="时长累计" min-width="140">
          <template #default="{ row }">{{ formatMinutes(row.durationMins) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { doAttendanceAction } from '@/api/attendance'
import { userStore } from '@/store/user'
import { formatHours, formatMinutes } from '@/utils/format'

const submitting = ref(false)

const userInfo = computed(() => userStore.userInfo || {})
const attendance = computed(() => userStore.todayAttendance || { todayStatus: 0, todayRecords: [] })

const attendanceTag = computed(() => {
  if (attendance.value.todayStatus === 1) {
    return { label: '进行中', type: 'warning' }
  }
  if (attendance.value.todayStatus === 2) {
    return { label: '已签退', type: 'success' }
  }
  return { label: '未签到', type: 'info' }
})

const actionButton = computed(() => {
  if (attendance.value.todayStatus === 1) {
    return { text: '执行签退', type: 'warning', actionType: 2 }
  }
  return { text: '执行签到', type: 'primary', actionType: 1 }
})

async function handleAttendanceAction() {
  submitting.value = true
  try {
    const newAttendance = await doAttendanceAction(actionButton.value.actionType)
    userStore.setAttendance(newAttendance)
    ElMessage.success('考勤状态已更新')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.hero-panel {
  min-height: 142px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 28px;
  border-radius: 8px;
  color: #fff;
  background:
    linear-gradient(rgba(23, 32, 51, 0.68), rgba(23, 32, 51, 0.78)),
    url("https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=1400&q=80") center / cover;
}

.hero-panel h2 {
  margin: 0 0 10px;
  font-size: 28px;
  letter-spacing: 0;
}

.hero-panel p {
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
}

.info-card,
.metric-card,
.action-card {
  height: 132px;
  border-radius: 8px;
}

.info-card :deep(.el-card__body),
.metric-card :deep(.el-card__body),
.action-card :deep(.el-card__body) {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.info-card span,
.metric-card span {
  color: #7c8798;
  font-size: 14px;
}

.info-card strong {
  margin-top: 10px;
  font-size: 20px;
  color: #172033;
}

.metric-card strong {
  margin-top: 12px;
  font-size: 34px;
  color: #1d4f91;
}

.attendance-button {
  width: 100%;
  height: 56px;
  font-size: 18px;
  font-weight: 700;
}

.table-heading {
  margin-bottom: 16px;
}

@media (max-width: 768px) {
  .hero-panel {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
