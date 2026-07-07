<template>
  <div class="page-shell">
    <div class="page-heading">
      <div>
        <h2 class="page-title">考勤检查与导出</h2>
        <p class="page-subtitle">按人员、工作组和日期范围检索考勤记录</p>
      </div>
      <el-button type="success" :icon="Download" :loading="exporting" @click="handleExport">导出当前数据</el-button>
    </div>

    <section class="toolbar">
      <el-form :model="queryForm" label-width="86px">
        <el-row :gutter="14">
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="工作组">
              <el-select v-model="queryForm.groupId" clearable placeholder="选择工作组">
                <el-option v-for="item in groupOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="姓名">
              <el-input v-model.trim="queryForm.username" clearable placeholder="姓名/学号" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="考勤状态">
              <el-select v-model="queryForm.attendanceStatus" clearable placeholder="选择状态">
                <el-option
                  v-for="item in attendanceStatusOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :lg="6">
            <el-form-item label="日期范围">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                clearable
              />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="toolbar-actions">
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="primary" :icon="Search" @click="loadAttendance">查询</el-button>
        </div>
      </el-form>
    </section>

    <section class="data-panel">
      <el-table v-loading="loading" :data="tableData" empty-text="暂无考勤记录">
        <el-table-column prop="username" label="姓名" min-width="110" />
        <el-table-column prop="memberId" label="学号/工号" min-width="140" />
        <el-table-column prop="groupName" label="所属组别" min-width="130" />
        <el-table-column label="签到时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.checkInTime) }}</template>
        </el-table-column>
        <el-table-column label="签退时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.checkOutTime) }}</template>
        </el-table-column>
        <el-table-column label="本次时长" min-width="130">
          <template #default="{ row }">{{ formatMinutes(row.durationMinutes) }}</template>
        </el-table-column>
        <el-table-column prop="semester" label="所属学期" min-width="120">
          <template #default="{ row }">{{ row.semester || '-' }}</template>
        </el-table-column>
        <el-table-column prop="source" label="操作来源" min-width="120" />
      </el-table>
      <div class="table-footer">
        <el-pagination
          v-model:current-page="queryForm.pageNo"
          v-model:page-size="queryForm.pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          @size-change="loadAttendance"
          @current-change="loadAttendance"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh, Search } from '@element-plus/icons-vue'
import { exportAttendance, fetchAttendancePage } from '@/api/attendance'
import { attendanceStatusOptions, groupOptions } from '@/constants/dictionaries'
import { buildQueryWithoutEmpty, formatDateTime, formatMinutes } from '@/utils/format'

const loading = ref(false)
const exporting = ref(false)
const tableData = ref([])
const total = ref(0)
const dateRange = ref([])

const queryForm = reactive({
  pageNo: 1,
  pageSize: 10,
  groupId: '',
  username: '',
  attendanceStatus: '',
  startDate: '',
  endDate: ''
})

watch(dateRange, (range) => {
  queryForm.startDate = range?.[0] || ''
  queryForm.endDate = range?.[1] || ''
})

async function loadAttendance() {
  loading.value = true
  try {
    const result = await fetchAttendancePage(buildQueryWithoutEmpty(queryForm))
    tableData.value = result.records || []
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  dateRange.value = []
  Object.assign(queryForm, {
    pageNo: 1,
    pageSize: 10,
    groupId: '',
    username: '',
    attendanceStatus: '',
    startDate: '',
    endDate: ''
  })
  loadAttendance()
}

function resolveFileName(response) {
  const disposition = response.headers?.['content-disposition'] || ''
  const match = disposition.match(/filename\*=UTF-8''([^;]+)/)
  if (match?.[1]) {
    return decodeURIComponent(match[1])
  }
  return `考勤总列表_${new Date().toISOString().slice(0, 10)}.xlsx`
}

async function handleExport() {
  exporting.value = true
  try {
    const exportQuery = { ...queryForm, pageNo: undefined, pageSize: undefined }
    const response = await exportAttendance(buildQueryWithoutEmpty(exportQuery))
    const blob = new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = resolveFileName(response)
    link.click()
    URL.revokeObjectURL(link.href)
    ElMessage.success('导出任务已开始')
  } finally {
    exporting.value = false
  }
}

onMounted(loadAttendance)
</script>

<style scoped>
.toolbar-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
