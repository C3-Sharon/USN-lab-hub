<template>
  <div class="page-shell">
    <div class="page-heading">
      <div>
        <h2 class="page-title">实验室成员管理</h2>
        <p class="page-subtitle">维护成员资料、状态和组织归属</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增成员</el-button>
    </div>

    <section class="toolbar">
      <el-form :model="queryForm" label-width="72px">
        <el-row :gutter="14">
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="姓名">
              <el-input v-model.trim="queryForm.username" clearable placeholder="输入姓名" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="身份">
              <el-select v-model="queryForm.identityId" clearable placeholder="选择身份">
                <el-option v-for="item in identityOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="工作组">
              <el-select v-model="queryForm.groupId" clearable placeholder="选择工作组">
                <el-option v-for="item in groupOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="状态">
              <el-select v-model="queryForm.status" clearable placeholder="选择状态">
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="toolbar-actions">
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="primary" :icon="Search" @click="loadMembers">查询</el-button>
        </div>
      </el-form>
    </section>

    <section class="data-panel">
      <el-table v-loading="loading" :data="tableData" empty-text="暂无成员数据">
        <el-table-column prop="username" label="姓名" min-width="120" />
        <el-table-column prop="memberId" label="学号/工号" min-width="140" />
        <el-table-column label="学院专业" min-width="220">
          <template #default="{ row }">{{ [row.collegeName, row.majorName].filter(Boolean).join(' / ') || '-' }}</template>
        </el-table-column>
        <el-table-column prop="identityName" label="身份" min-width="110" />
        <el-table-column prop="groupName" label="所属工作组" min-width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <el-pagination
          v-model:current-page="queryForm.pageNo"
          v-model:page-size="queryForm.pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          @size-change="loadMembers"
          @current-change="loadMembers"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
      <el-form ref="memberFormRef" :model="memberForm" :rules="memberRules" label-width="96px">
        <el-form-item label="姓名" prop="username">
          <el-input v-model.trim="memberForm.username" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="学号/工号" prop="memberId">
          <el-input v-model.trim="memberForm.memberId" placeholder="请输入学号或工号" />
        </el-form-item>
        <el-form-item label="专业 ID" prop="facultyId">
          <el-input-number v-model="memberForm.facultyId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="身份" prop="identityId">
          <el-select v-model="memberForm.identityId" placeholder="选择身份">
            <el-option v-for="item in identityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作组" prop="groupId">
          <el-select v-model="memberForm.groupId" placeholder="选择工作组">
            <el-option v-for="item in groupOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="memberForm.status">
            <el-radio :label="1">正常</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitMember">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { fetchMemberPage, saveMember, updateMember, updateMemberStatus } from '@/api/member'
import { groupOptions, identityOptions, statusOptions } from '@/constants/dictionaries'
import { buildQueryWithoutEmpty } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const memberFormRef = ref()
const tableData = ref([])
const total = ref(0)

const queryForm = reactive({
  pageNo: 1,
  pageSize: 10,
  username: '',
  identityId: '',
  groupId: '',
  status: ''
})

const memberForm = reactive({
  username: '',
  memberId: '',
  facultyId: undefined,
  identityId: undefined,
  groupId: undefined,
  status: 1
})

const memberRules = {
  username: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  memberId: [{ required: true, message: '请输入学号/工号', trigger: 'blur' }]
}

const dialogTitle = computed(() => (editingId.value ? '编辑成员' : '新增成员'))

function resetMemberForm() {
  Object.assign(memberForm, {
    username: '',
    memberId: '',
    facultyId: undefined,
    identityId: undefined,
    groupId: undefined,
    status: 1
  })
  editingId.value = null
}

async function loadMembers() {
  loading.value = true
  try {
    const result = await fetchMemberPage(buildQueryWithoutEmpty(queryForm))
    tableData.value = result.records || []
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  Object.assign(queryForm, {
    pageNo: 1,
    pageSize: 10,
    username: '',
    identityId: '',
    groupId: '',
    status: ''
  })
  loadMembers()
}

function openCreateDialog() {
  resetMemberForm()
  dialogVisible.value = true
}

function openEditDialog(row) {
  resetMemberForm()
  editingId.value = row.id
  Object.assign(memberForm, {
    username: row.username,
    memberId: row.memberId,
    facultyId: row.facultyId,
    identityId: row.identityId,
    groupId: row.groupId,
    status: row.status
  })
  dialogVisible.value = true
}

async function submitMember() {
  await memberFormRef.value?.validate()
  saving.value = true
  try {
    const payload = buildQueryWithoutEmpty({ id: editingId.value, ...memberForm })
    if (editingId.value) {
      await updateMember(payload)
    } else {
      await saveMember(payload)
    }
    ElMessage.success('成员信息已保存')
    dialogVisible.value = false
    loadMembers()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const nextStatus = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(`确认${nextStatus === 1 ? '启用' : '禁用'}成员「${row.username}」？`, '状态切换', {
    type: 'warning'
  })
  await updateMemberStatus(row.id, nextStatus)
  ElMessage.success('成员状态已更新')
  loadMembers()
}

onMounted(loadMembers)
</script>

<style scoped>
.toolbar-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
