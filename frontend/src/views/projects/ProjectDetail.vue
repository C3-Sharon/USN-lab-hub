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
              <span class="muted text-help">{{ project.memberCount }} 名成员</span>
            </div>
          </div>
        </div>
      </header>

      <el-tabs v-model="activeTab" class="project-tabs">
        <!-- 概览 -->
        <el-tab-pane label="概览" name="overview">
          <div v-if="activeTab === 'overview'" class="detail-grid">
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

            <el-card class="detail-card">
              <template #header>
                <div class="card-header">
                  <el-icon><Flag /></el-icon>
                  <span>里程碑进度</span>
                  <el-tag size="small" type="info">{{ milestoneSummary.length }} 个</el-tag>
                </div>
              </template>
              <div v-if="milestoneSummary.length" class="summary-list">
                <div v-for="item in milestoneSummary" :key="item.id" class="summary-item">
                  <div class="summary-item__head">
                    <span class="summary-item__name">{{ item.name }}</span>
                    <el-tag :type="milestoneMeta(item.status).tagType" size="small" effect="light">
                      {{ milestoneMeta(item.status).label }}
                    </el-tag>
                  </div>
                  <el-progress
                    :percentage="milestonePercent(item)"
                    :stroke-width="8"
                    :status="item.status === 'COMPLETED' ? 'success' : undefined"
                  />
                  <span class="muted text-help">{{ item.taskDone }}/{{ item.taskCount }} 任务完成</span>
                </div>
              </div>
              <el-empty v-else description="暂无里程碑" :image-size="72" />
            </el-card>
          </div>
        </el-tab-pane>

        <!-- 里程碑 -->
        <el-tab-pane label="里程碑" name="milestones">
          <MilestonePanel v-if="activeTab === 'milestones'" :project="project" />
        </el-tab-pane>

        <!-- 任务看板 -->
        <el-tab-pane label="任务看板" name="kanban">
          <TaskKanban v-if="activeTab === 'kanban'" :project="project" />
        </el-tab-pane>

        <!-- 成员 -->
        <el-tab-pane label="成员" name="members">
          <div v-if="activeTab === 'members'" class="members-pane">
            <div class="members-pane__toolbar">
              <span class="muted text-help">共 {{ project.members?.length || 0 }} 名成员</span>
              <el-button v-if="canAddMember" type="primary" :icon="Plus" @click="memberDialogVisible = true">
                添加成员
              </el-button>
            </div>
            <el-card class="detail-card">
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
                <el-table-column label="加入时间" width="180">
                  <template #default="{ row }">{{ formatTime(row.joinedAt) }}</template>
                </el-table-column>
              </el-table>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>

    <el-empty v-else-if="!loading && notFound" description="项目不存在或无权访问">
      <el-button type="primary" @click="$router.push('/projects')">返回项目列表</el-button>
    </el-empty>

    <el-empty v-else-if="!loading && !notFound" description="加载失败">
      <el-button type="primary" @click="loadDetail">重试</el-button>
    </el-empty>

    <AddMemberDialog
      v-if="project"
      v-model="memberDialogVisible"
      :project-id="project.id"
      @added="onMemberAdded"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Document, Flag } from '@element-plus/icons-vue'
import { getProjectDetail, PROJECT_STATUS_META, PROJECT_ROLE_META } from '@/api/projects'
import { listMilestones, MILESTONE_STATUS_META } from '@/api/milestones'
import { isProjectNotFound } from '@/utils/apiError'
import ProjectCover from '@/components/ProjectCover.vue'
import AddMemberDialog from './AddMemberDialog.vue'
import MilestonePanel from './MilestonePanel.vue'
import TaskKanban from './TaskKanban.vue'

const route = useRoute()
const router = useRouter()

const TAB_NAMES = ['overview', 'milestones', 'kanban', 'members']

const project = ref(null)
const loading = ref(false)
const notFound = ref(false)
const memberDialogVisible = ref(false)
const milestoneSummary = ref([])

const activeTab = ref(TAB_NAMES.includes(route.query.tab) ? route.query.tab : 'overview')

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

function milestoneMeta(status) {
  return MILESTONE_STATUS_META[status] || { label: status, tagType: 'info' }
}

function milestonePercent(item) {
  if (!item.taskCount) return 0
  return Math.round((item.taskDone / item.taskCount) * 100)
}

function formatTime(iso) {
  if (!iso) return '--'
  const d = new Date(iso)
  return isNaN(d) ? iso : d.toLocaleString('zh-CN', { hour12: false })
}

async function loadMilestoneSummary() {
  if (!project.value) return
  try {
    milestoneSummary.value = await listMilestones(project.value.id)
  } catch (err) {
    console.error('加载里程碑摘要失败', err)
  }
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
    if (activeTab.value === 'overview') loadMilestoneSummary()
  } catch (err) {
    console.error('加载项目详情失败', err)
    notFound.value = isProjectNotFound(err)
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

watch(activeTab, (tab) => {
  if (route.query.tab !== tab) {
    router.replace({ query: { ...route.query, tab } })
  }
  if (tab === 'overview') loadMilestoneSummary()
})

watch(
  () => route.params.id,
  () => loadDetail()
)

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

.project-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--usn-space-4);
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--usn-space-4);
  align-items: start;
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

.summary-list {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-2);
}

.summary-item__name {
  font-weight: 600;
  color: var(--usn-ink-900);
}

.members-pane {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.members-pane__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
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