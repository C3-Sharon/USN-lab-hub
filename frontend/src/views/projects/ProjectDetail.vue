<template>
  <div v-loading="loading" class="project-detail-page">
    <template v-if="project">
      <header class="page-header">
        <div class="header-left">
          <ProjectCover :code="project.code" :size="56" />
          <div>
            <h1 class="page-title">{{ project.name }}</h1>
            <div class="title-meta">
              <span class="code">{{ project.code }}</span>
              <el-tag :type="statusMeta(project.status).tagType" size="small" effect="light">
                {{ statusMeta(project.status).label }}
              </el-tag>
              <el-tag :type="roleMeta(project.myRole).tagType" size="small" effect="plain">
                我的角色：{{ roleMeta(project.myRole).label }}
              </el-tag>
            </div>
          </div>
        </div>
        <el-button v-if="canAddMember" type="primary" :icon="Plus" @click="memberDialogVisible = true">添加成员</el-button>
      </header>

      <div class="detail-grid">
        <!-- 基本信息 -->
        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <el-icon><Document /></el-icon>
              <span>基本信息</span>
            </div>
          </template>
          <div class="info-rows">
            <div class="info-row">
              <span class="info-label">项目简介</span>
              <span class="info-value">{{ project.summary || '暂无简介' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">分类</span>
              <span class="info-value">{{ project.category || '--' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">成员数</span>
              <span class="info-value">{{ project.memberCount }} 人</span>
            </div>
            <div class="info-row">
              <span class="info-label">创建时间</span>
              <span class="info-value">{{ formatTime(project.createTime) }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">最近更新</span>
              <span class="info-value">{{ formatTime(project.updateTime) }}</span>
            </div>
          </div>
        </el-card>

        <!-- 成员列表 -->
        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <el-icon><UserFilled /></el-icon>
              <span>项目成员</span>
              <el-tag size="small" type="info">{{ project.members?.length || 0 }} 人</el-tag>
            </div>
          </template>
          <el-table :data="project.members || []" size="small" empty-text="暂无成员">
            <el-table-column label="成员" min-width="160">
              <template #default="{ row }">
                <div class="member-name">
                  <el-avatar :size="28" class="member-avatar">{{ row.name?.charAt(0) }}</el-avatar>
                  <span>{{ row.name }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="学工号" prop="memberId" width="120" />
            <el-table-column label="角色" width="120">
              <template #default="{ row }">
                <el-tag :type="roleMeta(row.projectRole).tagType" size="small" effect="plain">
                  {{ roleMeta(row.projectRole).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="加入时间" width="160">
              <template #default="{ row }">{{ formatTime(row.joinedAt) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </template>

    <el-empty v-else-if="!loading && notFound" description="项目不存在或无权访问">
      <el-button type="primary" @click="$router.push('/projects')">返回项目列表</el-button>
    </el-empty>

    <el-empty v-else-if="!loading && !notFound" description="加载失败">
      <el-button type="primary" @click="loadDetail">重试</el-button>
    </el-empty>

    <!-- 添加成员弹窗 -->
    <AddMemberDialog
      v-if="project"
      v-model="memberDialogVisible"
      :project-id="project.id"
      @added="onMemberAdded"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Document, UserFilled } from '@element-plus/icons-vue'
import { getProjectDetail, PROJECT_STATUS_META, PROJECT_ROLE_META } from '@/api/projects'
import { hasAnyRole } from '@/store/user'
import { ROLE } from '@/utils/permission'
import ProjectCover from '@/components/ProjectCover.vue'
import AddMemberDialog from './AddMemberDialog.vue'

const route = useRoute()
const router = useRouter()

const project = ref(null)
const loading = ref(false)
const notFound = ref(false)
const memberDialogVisible = ref(false)

const canAddMember = computed(() => {
  const role = project.value?.myRole
  return role === 'OWNER' || role === 'MAINTAINER'
})

function statusMeta(status) {
  return PROJECT_STATUS_META[status] || { label: status, tagType: 'info' }
}

function roleMeta(role) {
  return PROJECT_ROLE_META[role] || { label: role, tagType: 'info' }
}

function formatTime(iso) {
  if (!iso) return '--'
  const d = new Date(iso)
  return isNaN(d) ? iso : d.toLocaleString('zh-CN', { hour12: false })
}

async function loadDetail() {
  const id = route.params.id
  if (!id) {
    notFound.value = true
    return
  }
  loading.value = true
  notFound.value = false
  try {
    project.value = await getProjectDetail(id)
  } catch (err) {
    console.error('加载项目详情失败', err)
    // 兼容两种错误形态：mock/业务错误直接是 {code, reason}；HTTP 404 走 axios error，载荷在 err.response.data
    const code = err?.code ?? err?.response?.data?.code ?? err?.response?.status
    const reason = err?.reason ?? err?.response?.data?.reason
    notFound.value = code === 404 || reason === 'PROJECT_NOT_FOUND' || reason === 'PROJECT_ACCESS_DENIED'
  } finally {
    loading.value = false
  }
}

function onMemberAdded(member) {
  memberDialogVisible.value = false
  if (project.value && project.value.members) {
    const exists = project.value.members.find((m) => m.userId === member.userId)
    if (!exists) {
      project.value.members.push(member)
      project.value.memberCount = (project.value.memberCount || 0) + 1
    }
  }
}

onMounted(() => loadDetail())
</script>

<style scoped>
.project-detail-page {
  max-width: var(--usn-content-max-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-4);
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--usn-space-4);
}

.page-title {
  margin: 0;
  font-size: var(--usn-font-size-page-title);
  font-weight: 700;
  color: var(--usn-ink-900);
}

.title-meta {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
  margin-top: 4px;
  flex-wrap: wrap;
}

.code {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: var(--usn-ink-700);
  font-weight: 600;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--usn-space-4);
}

.detail-card {
  border: 1px solid var(--usn-line);
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  font-weight: 600;
  color: var(--usn-ink-900);
}

.info-rows {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.info-row {
  display: flex;
  gap: var(--usn-space-3);
}

.info-label {
  width: 80px;
  flex-shrink: 0;
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
  text-align: right;
}

.info-value {
  flex: 1;
  font-size: var(--usn-font-size-body);
  color: var(--usn-ink-900);
  word-break: break-word;
}

.member-name {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
}

.member-avatar {
  background: var(--usn-blue-100);
  color: var(--usn-blue-700);
  font-weight: 700;
  font-size: 12px;
}

@media (max-width: 1024px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .header-left {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
