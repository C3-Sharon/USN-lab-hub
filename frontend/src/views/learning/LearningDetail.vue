<template>
  <div class="learning-detail-page">
    <div class="detail-back">
      <el-button link :icon="ArrowLeft" @click="goBack">返回学习实验台</el-button>
    </div>

    <RegionState
      v-if="pageState !== 'success'"
      :state="pageState"
      variant="section"
      :description="pageStateDescription"
      @retry="loadAll"
    />

    <div v-else class="detail-grid">
      <!-- 左：路线概览 -->
      <aside class="roadmap-aside">
        <div class="roadmap-aside__head">
          <RoadmapCover :title="roadmap.title" :size="64" />
          <h1 class="roadmap-aside__title">{{ roadmap.title }}</h1>
          <div class="roadmap-aside__tags">
            <DifficultyTag :difficulty="roadmap.difficulty" />
            <el-tag :type="statusMeta(roadmap.status).tagType" size="small" effect="light">
              {{ statusMeta(roadmap.status).label }}
            </el-tag>
          </div>
        </div>

        <p class="roadmap-aside__desc">{{ roadmap.description || '暂无路线描述' }}</p>

        <dl class="roadmap-aside__meta">
          <div><dt>预计时长</dt><dd>{{ roadmap.estimatedHours ? `${roadmap.estimatedHours} 小时` : '待定' }}</dd></div>
          <div><dt>阶段数</dt><dd>{{ stages.length }} 个</dd></div>
          <div>
            <dt>学习人数</dt>
            <dd>{{ roadmap.learnerCount == null ? '暂未统计' : `${roadmap.learnerCount} 人` }}</dd>
          </div>
        </dl>

        <div v-if="record" class="roadmap-aside__progress">
          <ProgressBar
            :value="record.progress"
            :label="`已完成 ${record.completedUnitCount}/${record.totalUnitCount} 单元`"
          />
          <p class="roadmap-aside__status text-help muted">
            学习状态：{{ learningStatusMeta(record.status).label }}
          </p>
        </div>

        <div class="roadmap-aside__actions">
          <el-button v-if="action.type === 'enroll'" type="primary" :loading="enrolling" @click="handleEnroll">
            {{ action.label }}
          </el-button>
          <el-button v-else-if="action.type === 'continue'" type="primary" @click="continueLearning">
            {{ action.label }}
          </el-button>
          <span v-else class="text-help muted">{{ action.label }}</span>
        </div>

        <p v-if="!record && roadmap.status === 'PUBLISHED'" class="roadmap-aside__hint text-help muted">
          开始学习后即可勾选单元完成并记录进度
        </p>
      </aside>

      <!-- 右：阶段时间线 -->
      <section class="roadmap-timeline">
        <h2 class="timeline-title">学习阶段</h2>

        <RegionState
          v-if="!stages.length"
          state="empty"
          variant="list"
          description="该路线暂未添加学习阶段"
        />

        <ol v-else class="timeline">
          <li
            v-for="(stage, index) in stages"
            :key="stage.id"
            :ref="(el) => setStageRef(stage.id, el)"
            class="timeline-item"
            :class="{ 'timeline-item--done': isStageDone(stage) }"
          >
            <span class="timeline-item__dot" aria-hidden="true">
              <el-icon v-if="isStageDone(stage)"><Check /></el-icon>
              <template v-else>{{ index + 1 }}</template>
            </span>

            <div class="timeline-item__body">
              <header class="timeline-item__head" @click="toggleStage(stage.id)">
                <div class="timeline-item__heading">
                  <h3 class="timeline-item__name">{{ stage.name }}</h3>
                  <span class="timeline-item__count text-help muted">
                    {{ stageDoneCount(stage) }}/{{ stage.units.length }} 单元
                  </span>
                </div>
                <el-icon class="timeline-item__caret" :class="{ 'is-open': !isCollapsed(stage.id) }">
                  <ArrowRight />
                </el-icon>
              </header>

              <p v-if="stage.description" class="timeline-item__desc">{{ stage.description }}</p>

              <ul v-show="!isCollapsed(stage.id)" class="unit-list">
                <li v-for="unit in stage.units" :key="unit.id" class="unit-item" :class="{ 'unit-item--done': unit.completed }">
                  <el-checkbox
                    :model-value="unit.completed"
                    :disabled="!record || roadmap.status === 'ARCHIVED'"
                    class="unit-item__check"
                    @change="(val) => toggleUnit(unit, val)"
                  >
                    <span class="unit-item__title">{{ unit.title }}</span>
                  </el-checkbox>
                  <p v-if="!isMobile && unit.description" class="unit-item__desc">{{ unit.description }}</p>
                </li>
              </ul>
            </div>
          </li>
        </ol>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Check } from '@element-plus/icons-vue'
import {
  getRoadmapDetail,
  listStages,
  listMyRoadmaps,
  enrollRoadmap,
  completeUnit,
  cancelUnitComplete,
  roadmapStatusMeta,
  learningStatusMeta
} from '@/api/learning'
import { notifyApiError, isLearningNotFound, isLearningDenied } from '@/utils/apiError'
import RoadmapCover from '@/components/RoadmapCover.vue'
import DifficultyTag from '@/components/DifficultyTag.vue'
import ProgressBar from '@/components/ProgressBar.vue'
import RegionState from '@/components/RegionState.vue'

const route = useRoute()
const router = useRouter()

const roadmapId = computed(() => Number(route.params.id))

const loading = ref(true)
const pageState = ref('loading')
const pageStateDescription = ref('')
const roadmap = ref(null)
const stages = ref([])
const record = ref(null)
const enrolling = ref(false)
const collapsedStages = ref(new Set())
const isMobile = ref(false)

let mediaQuery = null

const stageElsMap = new Map()

function setStageRef(id, el) {
  if (el) stageElsMap.set(id, el)
  else stageElsMap.delete(id)
}

const action = computed(() => {
  if (!roadmap.value) return { type: 'none', label: '' }
  if (record.value) {
    return { type: 'continue', label: record.value.status === 'COMPLETED' ? '回顾路线' : '继续学习' }
  }
  if (roadmap.value.status === 'PUBLISHED') return { type: 'enroll', label: '开始学习' }
  if (roadmap.value.status === 'ARCHIVED') return { type: 'none', label: '已归档，不可开始学习' }
  return { type: 'none', label: '未发布' }
})

function statusMeta(status) {
  return roadmapStatusMeta(status)
}

function stageDoneCount(stage) {
  return stage.units.filter((u) => u.completed).length
}

function isStageDone(stage) {
  return stage.units.length > 0 && stageDoneCount(stage) === stage.units.length
}

function isCollapsed(stageId) {
  return collapsedStages.value.has(stageId)
}

function toggleStage(stageId) {
  const next = new Set(collapsedStages.value)
  if (next.has(stageId)) next.delete(stageId)
  else next.add(stageId)
  collapsedStages.value = next
}

function applyMobileDefault() {
  if (isMobile.value) {
    collapsedStages.value = new Set(stages.value.map((s) => s.id))
  } else {
    collapsedStages.value = new Set()
  }
}

function handleResize() {
  const mobile = window.innerWidth <= 767
  if (mobile !== isMobile.value) {
    isMobile.value = mobile
    applyMobileDefault()
  }
}

async function loadAll() {
  loading.value = true
  pageState.value = 'loading'
  try {
    const [detail, stageList, myRes] = await Promise.all([
      getRoadmapDetail(roadmapId.value),
      listStages(roadmapId.value),
      listMyRoadmaps({ page: 1, pageSize: 100 })
    ])
    roadmap.value = detail
    stages.value = stageList || []
    record.value = (myRes?.list || []).find((r) => r.roadmapId === roadmapId.value) || null
    applyMobileDefault()
    pageState.value = 'success'
  } catch (err) {
    if (isLearningNotFound(err)) {
      pageState.value = 'empty'
      pageStateDescription.value = '学习路线不存在或不可见'
    } else if (isLearningDenied(err)) {
      pageState.value = 'permission'
      pageStateDescription.value = '当前账号无权访问学习实验台'
    } else {
      pageState.value = 'error'
      pageStateDescription.value = '学习路线加载失败'
      notifyApiError(err)
    }
  } finally {
    loading.value = false
  }
}

function findFirstIncompleteStage() {
  return stages.value.find((s) => s.units.length > 0 && stageDoneCount(s) < s.units.length) || null
}

function continueLearning() {
  const stage = findFirstIncompleteStage() || stages.value[0]
  if (!stage) return
  if (isCollapsed(stage.id)) {
    const next = new Set(collapsedStages.value)
    next.delete(stage.id)
    collapsedStages.value = next
  }
  const el = stageElsMap.get(stage.id)
  if (el && typeof el.scrollIntoView === 'function') {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

async function handleEnroll() {
  enrolling.value = true
  try {
    const res = await enrollRoadmap(roadmapId.value)
    record.value = res
    ElMessage.success('已加入学习，可以开始勾选单元了')
  } catch (err) {
    notifyApiError(err)
  } finally {
    enrolling.value = false
  }
}

async function toggleUnit(unit, val) {
  if (!record.value) {
    ElMessage.warning('请先开始学习该路线')
    return
  }
  const previous = unit.completed
  unit.completed = val
  try {
    const res = val ? await completeUnit(unit.id) : await cancelUnitComplete(unit.id)
    const rp = res?.roadmapProgress
    if (rp && record.value) {
      record.value = {
        ...record.value,
        status: rp.status,
        progress: rp.progress,
        completedUnitCount: rp.completedUnitCount,
        totalUnitCount: rp.totalUnitCount
      }
    }
  } catch (err) {
    unit.completed = previous
    notifyApiError(err)
  }
}

function goBack() {
  router.push('/learning')
}

onMounted(() => {
  isMobile.value = window.innerWidth <= 767
  mediaQuery = window.matchMedia('(max-width: 767px)')
  mediaQuery.addEventListener('change', handleResize)
  loadAll()
})

onBeforeUnmount(() => {
  if (mediaQuery) mediaQuery.removeEventListener('change', handleResize)
})
</script>

<style scoped>
.learning-detail-page {
  max-width: var(--usn-content-max-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.detail-back {
  display: flex;
}

.detail-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: var(--usn-space-6);
  align-items: start;
}

/* 左：路线概览 */
.roadmap-aside {
  position: sticky;
  top: var(--usn-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
  padding: var(--usn-space-5);
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-top: 3px solid var(--usn-purple-600);
  border-radius: var(--usn-radius-md);
  box-shadow: var(--usn-shadow-panel);
}

.roadmap-aside__head {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
}

.roadmap-aside__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--usn-ink-900);
  line-height: 1.3;
}

.roadmap-aside__tags {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.roadmap-aside__desc {
  margin: 0;
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
  line-height: 1.6;
}

.roadmap-aside__meta {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--usn-space-2);
  margin: 0;
  padding: var(--usn-space-3) 0;
  border-top: 1px solid var(--usn-line);
  border-bottom: 1px solid var(--usn-line);
}

.roadmap-aside__meta div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.roadmap-aside__meta dt {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.roadmap-aside__meta dd {
  margin: 0;
  font-size: var(--usn-font-size-body);
  font-weight: 600;
  color: var(--usn-ink-900);
}

.roadmap-aside__progress {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
}

.roadmap-aside__status {
  margin: 0;
}

.roadmap-aside__actions {
  display: flex;
}

.roadmap-aside__actions :deep(.el-button) {
  width: 100%;
}

.roadmap-aside__hint {
  margin: 0;
}

/* 右：时间线 */
.roadmap-timeline {
  min-width: 0;
}

.timeline-title {
  margin: 0 0 var(--usn-space-4);
  font-size: var(--usn-font-size-panel-title);
  font-weight: 700;
  color: var(--usn-ink-900);
}

.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  position: relative;
}

.timeline-item {
  position: relative;
  padding-left: 44px;
  padding-bottom: var(--usn-space-5);
}

.timeline-item::before {
  content: '';
  position: absolute;
  left: 15px;
  top: 28px;
  bottom: 0;
  width: 2px;
  background: var(--usn-line);
}

.timeline-item:last-child::before {
  display: none;
}

.timeline-item__dot {
  position: absolute;
  left: 0;
  top: 0;
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--usn-surface);
  border: 2px solid var(--usn-line);
  color: var(--usn-ink-500);
  font-weight: 700;
  font-size: var(--usn-font-size-help);
}

.timeline-item--done .timeline-item__dot {
  background: var(--usn-purple-600);
  border-color: var(--usn-purple-600);
  color: #ffffff;
}

.timeline-item__body {
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  padding: var(--usn-space-4);
}

.timeline-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-3);
  cursor: pointer;
}

.timeline-item__heading {
  display: flex;
  align-items: baseline;
  gap: var(--usn-space-3);
  min-width: 0;
}

.timeline-item__name {
  margin: 0;
  font-size: var(--usn-font-size-body);
  font-weight: 700;
  color: var(--usn-ink-900);
}

.timeline-item__count {
  white-space: nowrap;
}

.timeline-item__caret {
  transition: transform 0.2s;
  color: var(--usn-ink-500);
}

.timeline-item__caret.is-open {
  transform: rotate(90deg);
}

.timeline-item__desc {
  margin: var(--usn-space-2) 0 0;
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
  line-height: 1.5;
}

.unit-list {
  list-style: none;
  margin: var(--usn-space-3) 0 0;
  padding: var(--usn-space-3) 0 0;
  border-top: 1px dashed var(--usn-line);
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
}

.unit-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.unit-item__check {
  height: auto;
}

.unit-item__check :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.4;
}

.unit-item__title {
  font-size: var(--usn-font-size-body);
  color: var(--usn-ink-900);
}

.unit-item--done .unit-item__title {
  color: var(--usn-ink-500);
  text-decoration: line-through;
}

.unit-item__desc {
  margin: 0 0 0 24px;
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
  line-height: 1.5;
}

@media (max-width: 1024px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .roadmap-aside {
    position: static;
  }
}

@media (max-width: 767px) {
  .detail-grid {
    gap: var(--usn-space-4);
  }

  .timeline-item {
    padding-left: 36px;
  }

  .timeline-item__dot {
    width: 26px;
    height: 26px;
  }

  .timeline-item::before {
    left: 12px;
  }
}
</style>
