<template>
  <div class="record-detail-page">
    <RegionState
      v-if="!canAccess"
      state="permission"
      variant="section"
      description="当前账号无权访问实验记录"
      sub-title="实验记录仅对已登录的实验室成员开放"
    />

    <template v-else>
      <RegionState v-if="loading" state="loading" variant="section" />

      <RegionState
        v-else-if="permissionDenied"
        state="permission"
        variant="section"
        description="实验记录不存在或不可见"
        :sub-title="permissionMessage"
      >
        <template #default />
      </RegionState>

      <RegionState
        v-else-if="errorMessage"
        state="error"
        variant="section"
        description="加载实验记录失败"
        :sub-title="errorMessage"
        :error-message="errorMessage"
        @retry="loadPage"
      />

      <template v-else-if="record">
        <header class="page-header">
          <div class="header-main">
            <el-button text :icon="ArrowLeft" @click="goList">返回列表</el-button>
            <div class="header-title">
              <h1 class="page-title">{{ record.title }}</h1>
              <div class="title-meta">
                <span class="record-code">{{ record.recordCode }}</span>
                <RecordStatusTag :status="record.status" />
                <el-tag size="small" effect="plain">
                  {{ record.templateName || '模板' }} {{ record.templateVersion || '' }}
                </el-tag>
                <el-tag v-if="record.projectName" size="small" type="primary" effect="plain">
                  项目 · {{ record.projectName }}
                </el-tag>
                <el-tag v-else-if="record.learningUnitName" size="small" type="warning" effect="plain">
                  学习单元 · {{ record.learningUnitName }}
                </el-tag>
              </div>
            </div>
          </div>

          <div class="header-actions">
            <el-button
              v-if="canEdit && currentRevisionEditable"
              :icon="Edit"
              @click="goEdit"
            >
              编辑当前修订
            </el-button>
            <el-button
              v-if="canSubmit"
              type="primary"
              :loading="actionLoading === 'submit'"
              @click="handleSubmit"
            >
              提交评审
            </el-button>
            <el-button
              v-if="canCreateRevision"
              :loading="actionLoading === 'revise'"
              @click="handleCreateRevision"
            >
              创建新修订
            </el-button>
            <el-button
              v-if="canBindSnapshot"
              :icon="Link"
              @click="snapshotDialogVisible = true"
            >
              绑定采集快照
            </el-button>
            <el-dropdown v-if="canReviewNow" trigger="click" @command="openFeedback">
              <el-button type="primary" plain>
                评审
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :command="FEEDBACK_TYPE.COMMENT">
                    {{ feedbackTypeMeta(FEEDBACK_TYPE.COMMENT).label }}
                  </el-dropdown-item>
                  <el-dropdown-item :command="FEEDBACK_TYPE.CHANGE_REQUEST">
                    {{ feedbackTypeMeta(FEEDBACK_TYPE.CHANGE_REQUEST).label }}
                  </el-dropdown-item>
                  <el-dropdown-item :command="FEEDBACK_TYPE.APPROVAL">
                    {{ feedbackTypeMeta(FEEDBACK_TYPE.APPROVAL).label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button
              v-if="canPublishNow"
              type="success"
              :loading="actionLoading === 'publish'"
              @click="handlePublish"
            >
              发布修订
            </el-button>
            <el-button
              v-if="canConvertNow"
              type="success"
              plain
              @click="convertDialogVisible = true"
            >
              转为项目草案
            </el-button>
            <el-button
              v-if="record.convertedProjectId"
              :icon="FolderOpened"
              @click="goConvertedProject"
            >
              查看来源项目
            </el-button>
          </div>
        </header>

        <MockStateBanner />

        <el-alert
          v-if="!canEdit && !canReviewNow"
          type="info"
          :closable="false"
          show-icon
          class="readonly-alert"
        >
          当前账号对该记录为只读：无写权限时不展示编辑、提交与评审动作，后端仍会二次鉴权。
        </el-alert>

        <div class="detail-grid">
          <div class="main-column">
            <el-card class="detail-card">
              <template #header>
                <div class="card-header">
                  <el-icon><Document /></el-icon>
                  <span>当前工作修订</span>
                  <RevisionStatusTag v-if="currentRevision" :status="currentRevision.status" />
                  <span class="text-help muted">
                    第 {{ currentRevision?.revisionNo ?? '--' }} 版 · v{{ currentRevision?.version ?? '--' }}
                  </span>
                  <el-tag v-if="currentRevision?.collectionSnapshotId" size="small" type="success" effect="plain">
                    已绑定快照
                  </el-tag>
                </div>
              </template>

              <RegionState
                v-if="!currentRevision"
                state="empty"
                variant="card"
                description="暂无工作修订"
              />

              <template v-else>
                <el-alert
                  v-if="!currentRevisionEditable"
                  type="warning"
                  :closable="false"
                  show-icon
                  class="immutable-alert"
                >
                  该修订已{{ revisionStatusMeta(currentRevision.status).label }}，内容不可修改；发布记录需创建新修订。
                </el-alert>

                <div class="summary-box">
                  <span class="summary-box__label">实验摘要</span>
                  <span class="summary-box__value">{{ record.summary || '暂无实验摘要' }}</span>
                </div>

                <SchemaFieldForm
                  v-if="record.schemaJson"
                  :schema-json="record.schemaJson"
                  :model-value="currentRevision.contentJson || {}"
                  disabled
                />
                <el-empty v-else :image-size="64" description="模板版本缺少字段定义" />

                <div class="context-grid">
                  <div v-for="item in contextItems" :key="item.key" class="context-item">
                    <span class="context-item__label">{{ item.label }}</span>
                    <span class="context-item__value">{{ item.value || '--' }}</span>
                  </div>
                </div>

                <div v-if="currentRevision.changeSummary" class="change-summary">
                  <span class="change-summary__label">本次修订说明</span>
                  <span class="change-summary__value">{{ currentRevision.changeSummary }}</span>
                </div>
              </template>
            </el-card>

            <el-card v-if="publishedRevision" class="detail-card">
              <template #header>
                <div class="card-header">
                  <el-icon><Medal /></el-icon>
                  <span>当前发布版</span>
                  <RevisionStatusTag :status="publishedRevision.status" />
                  <span class="text-help muted">第 {{ publishedRevision.revisionNo }} 版，内容永久只读</span>
                </div>
              </template>

              <SchemaFieldForm
                v-if="record.schemaJson"
                :schema-json="record.schemaJson"
                :model-value="publishedRevision.contentJson || {}"
                disabled
              />
              <div class="publish-meta">
                <span>发布人：{{ publishedRevision.publishedByName || '--' }}</span>
                <span>发布时间：{{ formatDateTime(publishedRevision.publishedAt) }}</span>
              </div>
            </el-card>

            <el-card class="detail-card">
              <template #header>
                <div class="card-header">
                  <el-icon><Clock /></el-icon>
                  <span>修订时间线</span>
                  <el-tag size="small" type="info">{{ revisionTotal }} 个修订</el-tag>
                </div>
              </template>

              <RegionState v-if="revisionLoading" state="loading" variant="list" />

              <RegionState
                v-else-if="revisionError"
                state="error"
                variant="section"
                description="加载修订历史失败"
                :sub-title="revisionError"
                :error-message="revisionError"
                @retry="loadRevisions"
              />

              <RegionState
                v-else-if="!revisions.length"
                state="empty"
                variant="card"
                description="暂无修订历史"
              />

              <el-timeline v-else class="revision-timeline">
                <el-timeline-item
                  v-for="item in revisions"
                  :key="item.id"
                  :timestamp="formatDateTime(item.createTime)"
                  placement="top"
                  :type="timelineType(item.status)"
                >
                  <div class="timeline-head">
                    <strong>第 {{ item.revisionNo }} 版</strong>
                    <RevisionStatusTag :status="item.status" />
                    <el-tag v-if="item.collectionSnapshotId" size="small" type="success" effect="plain">
                      快照 #{{ item.collectionSnapshotId }}
                    </el-tag>
                    <el-tag v-if="item.id === record.publishedRevisionId" size="small" type="success">
                      当前发布版
                    </el-tag>
                    <el-tag v-if="item.id === record.currentRevisionId" size="small" type="primary" effect="plain">
                      当前工作修订
                    </el-tag>
                  </div>
                  <p class="timeline-summary">{{ item.changeSummary || '初始修订' }}</p>
                  <div class="timeline-meta text-help muted">
                    <span>创建：{{ item.createdByName || '--' }}</span>
                    <span v-if="item.submittedAt">提交：{{ formatDateTime(item.submittedAt) }}</span>
                    <span v-if="item.approvedAt">批准：{{ formatDateTime(item.approvedAt) }}</span>
                    <span v-if="item.publishedAt">发布：{{ formatDateTime(item.publishedAt) }}</span>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </el-card>
          </div>

          <aside class="side-column">
            <el-card class="detail-card">
              <template #header>
                <div class="card-header">
                  <el-icon><Connection /></el-icon>
                  <span>采集快照</span>
                  <el-button
                    v-if="canBindSnapshot"
                    size="small"
                    text
                    type="primary"
                    @click="snapshotDialogVisible = true"
                  >
                    {{ record.collectionSnapshot ? '更换' : '绑定' }}
                  </el-button>
                </div>
              </template>

              <div v-if="record.collectionSnapshot" class="snapshot-box">
                <div class="snapshot-code">{{ record.collectionSnapshot.snapshotCode }}</div>
                <div class="snapshot-row">
                  <span class="text-help muted">批次</span>
                  <span>{{ record.collectionSnapshot.batchCode }}</span>
                </div>
                <div class="snapshot-row">
                  <span class="text-help muted">记录数</span>
                  <span>{{ record.collectionSnapshot.recordCount }} 条（拒绝 {{ record.collectionSnapshot.rejectedCount }}）</span>
                </div>
                <div class="snapshot-row">
                  <span class="text-help muted">观测范围</span>
                  <span>{{ formatDateTime(record.collectionSnapshot.observedFrom) }} ~ {{ formatDateTime(record.collectionSnapshot.observedTo) }}</span>
                </div>
                <div class="snapshot-checksum" :title="record.collectionSnapshot.batchChecksum">
                  SHA-256：{{ shortHash(record.collectionSnapshot.batchChecksum) }}
                </div>
              </div>

              <el-empty v-else :image-size="64" description="当前修订未绑定采集快照">
                <el-button v-if="canBindSnapshot" size="small" type="primary" @click="snapshotDialogVisible = true">
                  绑定已完成批次快照
                </el-button>
              </el-empty>
            </el-card>

            <el-card class="detail-card">
              <template #header>
                <div class="card-header">
                  <el-icon><Guide /></el-icon>
                  <span>来源与去向</span>
                </div>
              </template>

              <div class="source-list">
                <div class="source-row">
                  <span class="source-row__label">作者</span>
                  <span>{{ record.authorName || '--' }}</span>
                </div>
                <div class="source-row">
                  <span class="source-row__label">归属</span>
                  <span>{{ ownershipLabel }}</span>
                </div>
                <div class="source-row">
                  <span class="source-row__label">固定模板</span>
                  <span>{{ record.templateName || '--' }} {{ record.templateVersion || '' }}</span>
                </div>
                <div class="source-row">
                  <span class="source-row__label">创建时间</span>
                  <span>{{ formatDateTime(record.createTime) }}</span>
                </div>
                <div class="source-row">
                  <span class="source-row__label">最近更新</span>
                  <span>{{ formatDateTime(record.updateTime) }}</span>
                </div>
                <div class="source-row">
                  <span class="source-row__label">转项目</span>
                  <el-button
                    v-if="record.convertedProjectId"
                    text
                    type="primary"
                    @click="goConvertedProject"
                  >
                    项目 #{{ record.convertedProjectId }}
                  </el-button>
                  <span v-else>尚未转换</span>
                </div>
              </div>
            </el-card>

            <el-card class="detail-card">
              <template #header>
                <div class="card-header">
                  <el-icon><ChatLineSquare /></el-icon>
                  <span>导师反馈</span>
                  <el-tag size="small" type="info">{{ feedbackTotal }} 条</el-tag>
                </div>
              </template>

              <RegionState v-if="feedbackLoading" state="loading" variant="list" />

              <RegionState
                v-else-if="feedbackError"
                state="error"
                variant="section"
                description="加载导师反馈失败"
                :sub-title="feedbackError"
                :error-message="feedbackError"
                @retry="loadFeedback"
              />

              <RegionState
                v-else-if="!feedbackList.length"
                state="empty"
                variant="card"
                description="暂无导师反馈"
              />

              <div v-else class="feedback-list">
                <article v-for="item in feedbackList" :key="item.id" class="feedback-item">
                  <div class="feedback-item__head">
                    <el-tag :type="feedbackTypeMeta(item.type).tagType" size="small" effect="light">
                      {{ feedbackTypeMeta(item.type).label }}
                    </el-tag>
                    <span class="feedback-item__revision">第 {{ item.revisionNo ?? '--' }} 版</span>
                    <span class="text-help muted">{{ formatDateTime(item.createTime) }}</span>
                  </div>
                  <p class="feedback-item__content">{{ item.content || '（无补充说明）' }}</p>
                  <div class="feedback-item__meta text-help muted">
                    <span>{{ item.reviewerName || '评审人' }}</span>
                    <span>{{ feedbackVisibilityMeta(item.visibility).label }}</span>
                    <span>requestId: {{ item.requestId || '--' }}</span>
                  </div>
                </article>
              </div>

              <el-button
                v-if="canReviewNow"
                class="feedback-action"
                type="primary"
                plain
                @click="openFeedback(FEEDBACK_TYPE.COMMENT)"
              >
                以导师身份评审
              </el-button>
            </el-card>
          </aside>
        </div>
      </template>
    </template>

    <FeedbackDialog
      v-if="record"
      v-model="feedbackDialogVisible"
      :record="record"
      :initial-type="feedbackType"
      :submitting="actionLoading === 'feedback'"
      @submit="handleFeedback"
    />

    <SnapshotBindDialog
      v-if="record"
      v-model="snapshotDialogVisible"
      :record="record"
      :submitting="actionLoading === 'snapshot'"
      @submit="handleBindSnapshot"
    />

    <ConvertProjectDialog
      v-if="record"
      v-model="convertDialogVisible"
      :record="record"
      :submitting="actionLoading === 'convert'"
      @submit="handleConvert"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  ArrowDown,
  Document,
  Medal,
  Clock,
  Connection,
  Guide,
  ChatLineSquare,
  Edit,
  Link,
  FolderOpened
} from '@element-plus/icons-vue'
import {
  getExperimentRecord,
  listRevisions,
  listFeedback,
  submitExperimentRecord,
  createFeedback,
  publishExperimentRecord,
  createRevision,
  bindCollectionSnapshot,
  convertToProjectDraft,
  canAccessExperiments,
  canEditRecord,
  canReviewRecord,
  canPublishRecord,
  canConvertRecord,
  isRevisionEditable,
  recordStatusMeta,
  revisionStatusMeta,
  feedbackTypeMeta,
  feedbackVisibilityMeta,
  RECORD_STATUS,
  REVISION_STATUS,
  FEEDBACK_TYPE
} from '@/api/experimentRecords'
import {
  notifyApiError,
  normalizeApiError,
  errorTraceId,
  promptVersionConflict,
  isVersionConflict,
  isExperimentNotFound,
  isExperimentDenied,
  isFeedbackDenied,
  isRevisionImmutable,
  isExperimentInvalidTransition,
  isProjectAlreadyConverted,
  isProjectConversionDenied,
  isIdempotencyKeyReused,
  isExperimentContentInvalid
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'
import RegionState from '@/components/RegionState.vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import RecordStatusTag from '@/components/experiment/RecordStatusTag.vue'
import RevisionStatusTag from '@/components/experiment/RevisionStatusTag.vue'
import SchemaFieldForm from '@/components/experiment/SchemaFieldForm.vue'
import FeedbackDialog from '@/components/experiment/FeedbackDialog.vue'
import SnapshotBindDialog from '@/components/experiment/SnapshotBindDialog.vue'
import ConvertProjectDialog from '@/components/experiment/ConvertProjectDialog.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const errorMessage = ref('')
const permissionDenied = ref(false)
const permissionMessage = ref('该记录不存在，或当前账号不在可见范围内')

const record = ref(null)
const revisions = ref([])
const revisionTotal = ref(0)
const revisionLoading = ref(false)
const revisionError = ref('')

const feedbackList = ref([])
const feedbackTotal = ref(0)
const feedbackLoading = ref(false)
const feedbackError = ref('')

const actionLoading = ref('')
const feedbackDialogVisible = ref(false)
const feedbackType = ref(FEEDBACK_TYPE.COMMENT)
const snapshotDialogVisible = ref(false)
const convertDialogVisible = ref(false)

const canAccess = computed(() => canAccessExperiments())
const currentRevision = computed(() => record.value?.currentRevision || null)
const publishedRevision = computed(() => record.value?.publishedRevision || null)
const currentRevisionEditable = computed(() => isRevisionEditable(currentRevision.value))

const canEdit = computed(() => canEditRecord(record.value))
const canReview = computed(() => canReviewRecord(record.value))
const canPublish = computed(() => canPublishRecord(record.value))
const canConvert = computed(() => canConvertRecord(record.value))

const canSubmit = computed(
  () =>
    canEdit.value &&
    currentRevisionEditable.value &&
    [RECORD_STATUS.DRAFT, RECORD_STATUS.REVISING, RECORD_STATUS.CHANGES_REQUESTED].includes(record.value?.status)
)

const canCreateRevision = computed(() => canEdit.value && record.value?.status === RECORD_STATUS.PUBLISHED)

const canReviewNow = computed(
  () =>
    canReview.value &&
    record.value?.status === RECORD_STATUS.IN_REVIEW &&
    currentRevision.value?.status === REVISION_STATUS.SUBMITTED
)

const canPublishNow = computed(
  () =>
    canPublish.value &&
    record.value?.status === RECORD_STATUS.APPROVED &&
    currentRevision.value?.status === REVISION_STATUS.APPROVED
)

const canConvertNow = computed(() => canConvert.value && !record.value?.convertedProjectId)

const canBindSnapshot = computed(
  () => canEdit.value && currentRevisionEditable.value && Boolean(record.value?.projectId)
)

const ownershipLabel = computed(() => {
  if (!record.value) return '--'
  if (record.value.projectName) return `项目 · ${record.value.projectName}`
  if (record.value.learningUnitName) return `学习单元 · ${record.value.learningUnitName}`
  return '--'
})

const contextItems = computed(() => {
  const rev = currentRevision.value || {}
  return [
    { key: 'purpose', label: '实验目的', value: rev.purpose },
    { key: 'environment', label: '实验环境', value: rev.environment },
    { key: 'steps', label: '实验步骤', value: rev.steps },
    { key: 'result', label: '实验结果', value: rev.result },
    { key: 'anomaly', label: '异常说明', value: rev.anomaly },
    { key: 'conclusion', label: '结论', value: rev.conclusion },
    { key: 'hardwareVersionRef', label: '硬件版本引用', value: rev.hardwareVersionRef },
    { key: 'deviceInstanceRef', label: '设备实例引用', value: rev.deviceInstanceRef },
    { key: 'firmwareVersion', label: '固件版本', value: rev.firmwareVersion },
    { key: 'bomVersionRef', label: 'BOM 版本引用', value: rev.bomVersionRef }
  ]
})

function shortHash(value) {
  if (!value) return '--'
  return `${value.slice(0, 12)}…${value.slice(-8)}`
}

function timelineType(status) {
  if (status === REVISION_STATUS.PUBLISHED) return 'success'
  if (status === REVISION_STATUS.APPROVED) return 'success'
  if (status === REVISION_STATUS.CHANGES_REQUESTED) return 'warning'
  if (status === REVISION_STATUS.SUPERSEDED) return 'info'
  return 'primary'
}

function goList() {
  router.push('/experiment/records')
}

function goEdit() {
  router.push(`/experiment/records/${record.value.id}/edit/${currentRevision.value.id}`)
}

function goConvertedProject() {
  if (record.value?.convertedProjectId) router.push(`/projects/${record.value.convertedProjectId}`)
}

async function loadRevisions() {
  revisionLoading.value = true
  revisionError.value = ''
  try {
    const res = await listRevisions(route.params.id, { page: 1, pageSize: 50 })
    revisions.value = res?.list || []
    revisionTotal.value = res?.total ?? 0
  } catch (err) {
    revisionError.value = describeError(err)
    revisions.value = []
    revisionTotal.value = 0
  } finally {
    revisionLoading.value = false
  }
}

async function loadFeedback() {
  feedbackLoading.value = true
  feedbackError.value = ''
  try {
    const res = await listFeedback(route.params.id, { page: 1, pageSize: 50 })
    feedbackList.value = res?.list || []
    feedbackTotal.value = res?.total ?? 0
  } catch (err) {
    feedbackError.value = describeError(err)
    feedbackList.value = []
    feedbackTotal.value = 0
  } finally {
    feedbackLoading.value = false
  }
}

function describeError(err) {
  const { msg } = normalizeApiError(err)
  const traceId = errorTraceId(err)
  return traceId ? `${msg}（traceId: ${traceId}）` : msg
}

async function loadPage() {
  loading.value = true
  errorMessage.value = ''
  permissionDenied.value = false
  try {
    record.value = await getExperimentRecord(route.params.id)
    await Promise.all([loadRevisions(), loadFeedback()])
  } catch (err) {
    if (isExperimentNotFound(err) || isExperimentDenied(err)) {
      permissionDenied.value = true
      permissionMessage.value = describeError(err)
    } else {
      errorMessage.value = describeError(err)
    }
    record.value = null
  } finally {
    loading.value = false
  }
}

function handleActionError(err) {
  if (isVersionConflict(err)) {
    promptVersionConflict(loadPage)
    return
  }
  if (isRevisionImmutable(err)) {
    ElMessage.warning('当前修订已冻结，不能修改；请刷新后查看最新状态')
    loadPage()
    return
  }
  if (isExperimentInvalidTransition(err)) {
    ElMessage.warning('当前状态不允许该动作，请刷新后重试')
    loadPage()
    return
  }
  if (isFeedbackDenied(err) || isExperimentDenied(err) || isProjectConversionDenied(err)) {
    ElMessage.warning('当前账号无权执行该操作')
    return
  }
  if (isProjectAlreadyConverted(err)) {
    ElMessage.warning('该实验已转为项目，不能重复转换')
    loadPage()
    return
  }
  if (isIdempotencyKeyReused(err)) {
    ElMessage.warning('相同幂等键已用于其他请求，请重新发起操作')
    return
  }
  if (isExperimentContentInvalid(err)) {
    ElMessage.error('实验内容不符合固定模板版本，请返回编辑修正')
    return
  }
  notifyApiError(err)
}

async function handleSubmit() {
  try {
    await ElMessageBox.confirm(
      `提交第 ${currentRevision.value.revisionNo} 版后内容与快照将冻结，等待导师评审。`,
      '提交评审',
      { confirmButtonText: '提交', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  actionLoading.value = 'submit'
  try {
    await submitExperimentRecord(
      record.value.id,
      {
        revisionId: currentRevision.value.id,
        recordVersion: record.value.version,
        revisionVersion: currentRevision.value.version
      },
      createIdempotencyKey('w43-record-submit')
    )
    ElMessage.success('已提交评审，等待导师反馈')
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function handleCreateRevision() {
  try {
    await ElMessageBox.confirm(
      '将基于当前发布版创建新的可编辑修订，旧发布版继续有效。',
      '创建新修订',
      { confirmButtonText: '创建', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }
  actionLoading.value = 'revise'
  try {
    const res = await createRevision(record.value.id, { changeSummary: '基于发布版创建新修订' })
    ElMessage.success(`已创建第 ${res.revisionNo} 版修订`)
    router.push(`/experiment/records/${record.value.id}/edit/${res.revisionId}`)
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

function openFeedback(type) {
  feedbackType.value = type || FEEDBACK_TYPE.COMMENT
  feedbackDialogVisible.value = true
}

async function handleFeedback(payload) {
  actionLoading.value = 'feedback'
  try {
    const res = await createFeedback(
      record.value.id,
      payload,
      createIdempotencyKey('w43-record-feedback')
    )
    feedbackDialogVisible.value = false
    if (payload.type === FEEDBACK_TYPE.CHANGE_REQUEST) {
      ElMessage.success(`已要求修改，自动生成第 ${res.newRevision?.revisionNo ?? '--'} 版可编辑修订`)
    } else if (payload.type === FEEDBACK_TYPE.APPROVAL) {
      ElMessage.success('已批准，等待发布')
    } else {
      ElMessage.success('评论已追加')
    }
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function handlePublish() {
  try {
    await ElMessageBox.confirm(
      '发布后该修订内容、模板版本与采集快照将永久冻结，不可再修改。',
      '发布修订',
      { confirmButtonText: '发布', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  actionLoading.value = 'publish'
  try {
    await publishExperimentRecord(
      record.value.id,
      { revisionId: currentRevision.value.id, recordVersion: record.value.version },
      createIdempotencyKey('w43-record-publish')
    )
    ElMessage.success('修订已发布，可按固定模板版本与快照复现')
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function handleBindSnapshot(payload) {
  actionLoading.value = 'snapshot'
  try {
    await bindCollectionSnapshot(record.value.id, {
      revisionId: currentRevision.value.id,
      recordVersion: record.value.version,
      revisionVersion: currentRevision.value.version,
      snapshotId: payload.snapshotId
    })
    snapshotDialogVisible.value = false
    ElMessage.success(payload.snapshotId == null ? '已解除快照绑定' : '快照已绑定到当前修订')
    await loadPage()
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function handleConvert(payload) {
  actionLoading.value = 'convert'
  try {
    const project = await convertToProjectDraft(
      record.value.id,
      payload,
      createIdempotencyKey('w43-project-convert')
    )
    convertDialogVisible.value = false
    ElMessage.success('已创建 PREPARING 项目草案')
    router.push(`/projects/${project.id}`)
  } catch (err) {
    handleActionError(err)
  } finally {
    actionLoading.value = ''
  }
}

onMounted(loadPage)
</script>

<style scoped>
.record-detail-page {
  max-width: var(--usn-content-max-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-4);
  flex-wrap: wrap;
}

.header-main {
  display: flex;
  align-items: flex-start;
  gap: var(--usn-space-2);
  min-width: 0;
}

.header-title {
  min-width: 0;
}

.page-title {
  margin: 0;
  font-size: var(--usn-font-size-page-title);
  font-weight: 700;
  color: var(--usn-ink-900);
  line-height: 1.3;
}

.title-meta {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  margin-top: 6px;
  flex-wrap: wrap;
}

.record-code {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  font-weight: 600;
  color: var(--usn-ink-700);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.readonly-alert {
  margin-bottom: var(--usn-space-1);
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(300px, 1fr);
  gap: var(--usn-space-4);
  align-items: start;
}

.main-column,
.side-column {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
  min-width: 0;
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
  flex-wrap: wrap;
}

.immutable-alert {
  margin-bottom: var(--usn-space-3);
}

.summary-box {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--usn-space-3);
  margin-bottom: var(--usn-space-4);
  background: var(--usn-canvas);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-sm);
}

.summary-box__label,
.context-item__label,
.change-summary__label {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.summary-box__value,
.change-summary__value {
  color: var(--usn-ink-900);
  line-height: 1.6;
}

.context-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--usn-space-3);
  padding-top: var(--usn-space-3);
  margin-top: var(--usn-space-4);
  border-top: 1px solid var(--usn-line);
}

.context-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.context-item__value {
  color: var(--usn-ink-900);
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}

.change-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--usn-space-3);
  margin-top: var(--usn-space-4);
  background: #f5f3ff;
  border: 1px solid #ddd6fe;
  border-radius: var(--usn-radius-sm);
}

.publish-meta {
  display: flex;
  gap: var(--usn-space-4);
  flex-wrap: wrap;
  margin-top: var(--usn-space-3);
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.revision-timeline {
  padding-left: var(--usn-space-1);
}

.timeline-head {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.timeline-summary {
  margin: 6px 0;
  color: var(--usn-ink-700);
  line-height: 1.5;
}

.timeline-meta {
  display: flex;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.snapshot-box {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
}

.snapshot-code {
  font-family: 'Courier New', monospace;
  font-weight: 700;
  color: var(--usn-ink-900);
}

.snapshot-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-3);
  font-size: var(--usn-font-size-help);
}

.snapshot-checksum {
  padding-top: var(--usn-space-2);
  border-top: 1px dashed var(--usn-line);
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: var(--usn-ink-500);
  word-break: break-all;
}

.source-list {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
}

.source-row {
  display: flex;
  align-items: flex-start;
  gap: var(--usn-space-3);
  font-size: var(--usn-font-size-help);
}

.source-row__label {
  width: 64px;
  flex-shrink: 0;
  color: var(--usn-ink-500);
  text-align: right;
}

.feedback-list {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.feedback-item {
  padding: var(--usn-space-3);
  background: var(--usn-canvas);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-sm);
}

.feedback-item__head {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.feedback-item__revision {
  font-size: var(--usn-font-size-help);
  font-weight: 600;
  color: var(--usn-ink-700);
}

.feedback-item__content {
  margin: 8px 0;
  color: var(--usn-ink-900);
  line-height: 1.6;
  white-space: pre-wrap;
}

.feedback-item__meta {
  display: flex;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.feedback-action {
  width: 100%;
  margin-top: var(--usn-space-3);
}

@media (max-width: 1180px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .header-actions {
    width: 100%;
  }

  .header-actions :deep(.el-button),
  .header-actions :deep(.el-dropdown) {
    flex: 1 1 45%;
    margin-left: 0;
  }

  .context-grid {
    grid-template-columns: 1fr;
  }
}
</style>
