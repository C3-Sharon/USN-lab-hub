<template>
  <div class="learning-list-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">学习实验台</h1>
        <p class="page-subtitle muted">按学习路线循序渐进，掌握实验基础技能</p>
      </div>
      <el-tag class="domain-tag" effect="plain" size="large">学习 · 知识</el-tag>
    </header>

    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索路线标题"
        clearable
        class="filter-keyword"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="query.difficulty" placeholder="难度" clearable class="filter-select" @change="handleSearch">
        <el-option v-for="(meta, key) in LEARNING_DIFFICULTY_META" :key="key" :label="meta.label" :value="key" />
      </el-select>
      <el-select v-if="canManage" v-model="query.status" placeholder="状态" clearable class="filter-select" @change="handleSearch">
        <el-option v-for="(meta, key) in ROADMAP_STATUS_META" :key="key" :label="meta.label" :value="key" />
      </el-select>
      <el-select v-model="query.sortBy" class="filter-select" @change="handleSearch">
        <el-option label="排序序号" value="sortOrder" />
        <el-option label="最近更新" value="updateTime" />
        <el-option label="创建时间" value="createTime" />
      </el-select>
    </div>

    <div v-loading="loading" class="roadmap-grid">
      <template v-if="filteredList.length">
        <article
          v-for="item in filteredList"
          :key="item.id"
          class="roadmap-card"
          :class="{ 'roadmap-card--archived': item.status === 'ARCHIVED' }"
          @click="goDetail(item.id)"
        >
          <div class="roadmap-card__head">
            <RoadmapCover :title="item.title" :size="48" />
            <div class="roadmap-card__heading">
              <h3 class="roadmap-card__title">{{ item.title }}</h3>
              <div class="roadmap-card__tags">
                <DifficultyTag :difficulty="item.difficulty" />
                <el-tag v-if="item.status !== 'PUBLISHED'" :type="statusMeta(item.status).tagType" size="small" effect="light">
                  {{ statusMeta(item.status).label }}
                </el-tag>
              </div>
            </div>
          </div>

          <p class="roadmap-card__summary">{{ item.description || '暂无路线描述' }}</p>

          <div class="roadmap-card__meta">
            <span class="text-help muted">{{ hoursLabel(item.estimatedHours) }}</span>
            <span class="text-help muted">{{ item.stageCount }} 个阶段</span>
            <span class="text-help muted">{{ item.learnerCount }} 人学习</span>
          </div>

          <div v-if="myRecord(item.id)" class="roadmap-card__progress">
            <ProgressBar
              :value="myRecord(item.id).progress"
              :label="`已完成 ${myRecord(item.id).completedUnitCount}/${myRecord(item.id).totalUnitCount} 单元`"
            />
          </div>

          <div class="roadmap-card__footer">
            <el-button
              v-if="actionFor(item).type === 'continue'"
              type="primary"
              size="small"
              @click.stop="goDetail(item.id)"
            >
              {{ actionFor(item).label }}
            </el-button>
            <el-button
              v-else-if="actionFor(item).type === 'enroll'"
              type="primary"
              size="small"
              plain
              :loading="enrollingId === item.id"
              @click.stop="handleEnroll(item)"
            >
              {{ actionFor(item).label }}
            </el-button>
            <span v-else class="text-help muted">{{ actionFor(item).label }}</span>
          </div>
        </article>
      </template>

      <RegionState
        v-else-if="!loading"
        state="empty"
        variant="card"
        :description="emptyDescription"
      >
        <el-button v-if="hasFilter" @click="resetFilter">清除筛选条件</el-button>
      </RegionState>
    </div>

    <div v-if="total > 0" class="pagination-bar">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[9, 12, 24]"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSearch"
        @current-change="loadList"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  listRoadmaps,
  listMyRoadmaps,
  enrollRoadmap,
  canManageLearning,
  ROADMAP_STATUS_META,
  LEARNING_DIFFICULTY_META,
  roadmapStatusMeta
} from '@/api/learning'
import { notifyApiError } from '@/utils/apiError'
import RoadmapCover from '@/components/RoadmapCover.vue'
import DifficultyTag from '@/components/DifficultyTag.vue'
import ProgressBar from '@/components/ProgressBar.vue'
import RegionState from '@/components/RegionState.vue'

const router = useRouter()

const loading = ref(false)
const enrollingId = ref(null)
const list = ref([])
const total = ref(0)
const myRecords = ref([])

const query = ref({
  page: 1,
  pageSize: 9,
  keyword: '',
  difficulty: '',
  status: '',
  sortBy: 'sortOrder',
  sortOrder: 'asc'
})

const canManage = computed(() => canManageLearning())

const myMap = computed(() => {
  const map = {}
  myRecords.value.forEach((r) => {
    map[r.roadmapId] = r
  })
  return map
})

const filteredList = computed(() => {
  const k = query.value.keyword.trim().toLowerCase()
  if (!k) return list.value
  return list.value.filter((item) => item.title.toLowerCase().includes(k))
})

const hasFilter = computed(() => Boolean(query.value.keyword || query.value.difficulty || query.value.status))

const emptyDescription = computed(() => {
  if (hasFilter.value) return '没有符合筛选条件的学习路线'
  return canManage.value ? '暂无学习路线，可在后端创建后查看' : '暂无已发布的学习路线'
})

function statusMeta(status) {
  return roadmapStatusMeta(status)
}

function myRecord(roadmapId) {
  return myMap.value[roadmapId] || null
}

function hoursLabel(hours) {
  return hours ? `预计 ${hours} 小时` : '时长待定'
}

function actionFor(item) {
  const record = myRecord(item.id)
  if (record) {
    return { type: 'continue', label: record.status === 'COMPLETED' ? '回顾路线' : '继续学习' }
  }
  if (item.status === 'PUBLISHED') {
    return { type: 'enroll', label: '开始学习' }
  }
  if (item.status === 'ARCHIVED') {
    return { type: 'none', label: '已归档，不可开始' }
  }
  return { type: 'none', label: '未发布' }
}

async function loadList() {
  loading.value = true
  try {
    const [roadmapRes, myRes] = await Promise.all([
      listRoadmaps({ ...query.value, keyword: undefined }),
      listMyRoadmaps({ page: 1, pageSize: 100 })
    ])
    list.value = roadmapRes?.list || []
    total.value = roadmapRes?.total ?? 0
    myRecords.value = myRes?.list || []
  } catch (err) {
    notifyApiError(err)
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.value.page = 1
  loadList()
}

function resetFilter() {
  query.value.keyword = ''
  query.value.difficulty = ''
  query.value.status = ''
  handleSearch()
}

async function handleEnroll(item) {
  enrollingId.value = item.id
  try {
    await enrollRoadmap(item.id)
    ElMessage.success('已加入学习')
    goDetail(item.id)
  } catch (err) {
    notifyApiError(err)
  } finally {
    enrollingId.value = null
  }
}

function goDetail(id) {
  router.push(`/learning/${id}`)
}

onMounted(() => loadList())
</script>

<style scoped>
.learning-list-page {
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

.page-title {
  margin: 0;
  font-size: var(--usn-font-size-page-title);
  font-weight: 700;
  color: var(--usn-ink-900);
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: var(--usn-font-size-help);
}

/* 学习域识别标签：紫色，小面积 */
.domain-tag {
  color: var(--usn-purple-600);
  border-color: var(--usn-purple-600);
  background: #faf5ff;
  font-weight: 600;
}

.filter-bar {
  display: flex;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.filter-keyword {
  flex: 1 1 240px;
}

.filter-select {
  width: 140px;
}

.roadmap-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--usn-space-4);
  min-height: 200px;
}

.roadmap-card {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
  padding: var(--usn-space-4);
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-top: 3px solid var(--usn-purple-600);
  border-radius: var(--usn-radius-md);
  box-shadow: var(--usn-shadow-panel);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.roadmap-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(23, 33, 43, 0.1);
}

.roadmap-card--archived {
  border-top-color: var(--usn-ink-500);
  opacity: 0.85;
}

.roadmap-card__head {
  display: flex;
  align-items: flex-start;
  gap: var(--usn-space-3);
}

.roadmap-card__heading {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.roadmap-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--usn-ink-900);
  line-height: 1.3;
}

.roadmap-card__tags {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.roadmap-card__summary {
  margin: 0;
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.8em;
}

.roadmap-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--usn-space-3);
  font-size: var(--usn-font-size-help);
}

.roadmap-card__progress {
  padding-top: var(--usn-space-1);
}

.roadmap-card__footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-top: auto;
  padding-top: var(--usn-space-1);
}

.pagination-bar {
  display: flex;
  justify-content: center;
  padding-top: var(--usn-space-2);
}

@media (max-width: 1365px) {
  .roadmap-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 767px) {
  .roadmap-grid {
    grid-template-columns: 1fr;
  }

  .filter-select {
    width: 100%;
  }

  .roadmap-card__footer {
    justify-content: stretch;
  }

  .roadmap-card__footer :deep(.el-button) {
    width: 100%;
  }
}
</style>