<template>
  <div class="issue-detail-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="back">返回领用申请列表</el-button>
      <span v-if="detail" class="text-help muted">{{ detail.requestNo }} · v{{ detail.version }}</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canAccessIssuance()"
      state="permission"
      variant="section"
      description="当前账号无权访问库存领用"
      sub-title="请使用实验室成员、库存管理员、老师或系统管理员账号登录"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载领用申请详情"
    />

    <RegionState
      v-else-if="notFound"
      state="permission"
      variant="section"
      description="领用申请不存在或不可见"
      sub-title="无权资源统一按 404 处理，不展示标题、明细或批次信息"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="领用申请详情加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="load"
    />

    <template v-else-if="detail">
      <header class="page-header">
        <div>
          <div class="detail-title-row">
            <h1 class="page-title">{{ detail.title }}</h1>
            <IssueStatusTag :status="detail.status" />
          </div>
          <p class="text-help muted">
            {{ detail.requestNo }} · 申请人 {{ detail.requesterName || detail.requesterId }}
            <template v-if="detail.projectName"> · 项目 {{ detail.projectName }}</template>
          </p>
        </div>
        <div class="page-header__actions page-header__actions--wrap">
          <el-button v-if="actions.edit" plain :icon="Edit" @click="goEdit">编辑草稿</el-button>
          <el-button v-if="actions.submit" type="primary" :loading="acting" @click="submitDraft">提交申请</el-button>
          <el-button v-if="actions.cancel" plain @click="cancelRequest">取消申请</el-button>
          <el-button v-if="actions.approve" type="success" @click="approveDialog = true">批准</el-button>
          <el-button v-if="actions.reject" type="danger" plain @click="rejectDialog = true">拒绝</el-button>
          <el-button v-if="actions.reallocate" plain @click="openReallocate">重分配批次</el-button>
          <el-button v-if="actions.confirmIssue" type="primary" @click="openConfirm">确认发放</el-button>
          <el-button v-if="actions.initiateReturn" plain @click="openReturn">发起退回</el-button>
        </div>
      </header>

      <el-alert
        v-if="actionSuccess"
        type="success"
        :closable="true"
        show-icon
        :title="actionSuccess"
        @close="actionSuccess = ''"
      />

      <el-alert
        v-if="conflictVersion != null"
        type="warning"
        :closable="false"
        show-icon
        :title="`version 冲突：服务端 currentVersion=${conflictVersion}，请刷新后重新操作。`"
      >
        <el-button text type="primary" @click="load">刷新详情</el-button>
      </el-alert>

      <el-alert
        v-if="stockShortage"
        type="error"
        :closable="false"
        show-icon
        :title="`库存不足：元器件 ${stockShortage.componentId} 申请 ${formatQuantity(stockShortage.requestedQuantity)}，可用 ${formatQuantity(stockShortage.availableQuantity)}${stockShortage.traceId ? ` · traceId ${stockShortage.traceId}` : ''}`"
      >
        本次提交没有产生任何预占；请减少数量、拆分申请或等待补货。
      </el-alert>

      <el-alert
        v-if="detail.status === ISSUE_STATUS.EXPIRED"
        type="error"
        :closable="false"
        show-icon
        title="预占已过期，库存已释放"
        description="该申请不可再审批或发放；请重新提交新申请。"
      />

      <el-alert
        v-else-if="reservationActive"
        type="warning"
        :closable="false"
        show-icon
        :title="`预占中：剩余 ${remainingText} 到期（${formatDateTime(detail.expiresAt)}）`"
        description="预占到期会自动释放；审批不扣库存，只有库存管理员确认发放才写 ISSUE_OUT 并扣减。"
      />

      <section class="data-panel detail-section">
        <h2 class="section-title">申请概况</h2>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="状态">
            <IssueStatusTag :status="detail.status" size="small" />
          </el-descriptions-item>
          <el-descriptions-item label="申请单号">{{ detail.requestNo }}</el-descriptions-item>
          <el-descriptions-item label="version">v{{ detail.version }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.requesterName || detail.requesterId }}</el-descriptions-item>
          <el-descriptions-item label="关联项目">{{ detail.projectName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="策略版本">
            {{ detail.policyVersion == null ? '—' : `v${detail.policyVersion}` }}
          </el-descriptions-item>
          <el-descriptions-item v-if="canViewIssueCost()" label="预估金额">
            {{ detail.estimatedAmount == null ? '—' : formatAmount(detail.estimatedAmount) }}
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(detail.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="批准时间">{{ formatDateTime(detail.approvedAt) }}</el-descriptions-item>
          <el-descriptions-item label="发放时间">{{ formatDateTime(detail.issuedAt) }}</el-descriptions-item>
          <el-descriptions-item label="预占到期">{{ formatDateTime(detail.expiresAt) }}</el-descriptions-item>
          <el-descriptions-item label="最近更新">{{ formatDateTime(detail.updateTime) }}</el-descriptions-item>
        </el-descriptions>

        <div class="approval-reasons">
          <span class="text-help muted">审批触发：</span>
          <el-tag
            v-for="reason in detail.approvalReasons || []"
            :key="reason"
            :type="approvalReasonMeta(reason).tagType"
            effect="plain"
            size="small"
          >
            {{ approvalReasonMeta(reason).label }}
          </el-tag>
          <span v-if="!(detail.approvalReasons || []).length" class="text-help muted">尚未提交</span>
        </div>

        <div class="purpose-block">
          <h3 class="section-subtitle">用途</h3>
          <p>{{ detail.purpose }}</p>
        </div>
      </section>

      <section class="data-panel detail-section">
        <h2 class="section-title">领用明细</h2>
        <el-table :data="detail.lines" row-key="id" stripe>
          <el-table-column type="index" label="#" width="60" />
          <el-table-column label="元器件" min-width="200">
            <template #default="{ row }">
              <div class="component-cell">
                <strong>{{ row.componentName }}</strong>
                <small class="muted">{{ row.mpn }} · {{ row.manufacturerName }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="申请数量" width="120" align="right">
            <template #default="{ row }">{{ formatQuantityUnit(row.requestedQuantity, row.unit) }}</template>
          </el-table-column>
          <el-table-column label="预占" width="110" align="right">
            <template #default="{ row }">{{ formatQuantityUnit(row.reservedQuantity, row.unit) }}</template>
          </el-table-column>
          <el-table-column label="已发放" width="110" align="right">
            <template #default="{ row }">{{ formatQuantityUnit(row.issuedQuantity, row.unit) }}</template>
          </el-table-column>
          <el-table-column label="已退回" width="110" align="right">
            <template #default="{ row }">{{ formatQuantityUnit(row.returnedQuantity, row.unit) }}</template>
          </el-table-column>
          <el-table-column label="当前可用" width="140" align="right">
            <template #default="{ row }">
              <span :class="{ 'danger-text': Number(row.availableQuantity) < Number(row.requestedQuantity) && detail.status === 'DRAFT' }">
                {{ formatQuantityUnit(row.availableQuantity, row.unit) }}
              </span>
              <small class="muted block">现有 {{ formatQuantity(row.onHandQuantity) }}</small>
            </template>
          </el-table-column>
          <el-table-column label="可退回" width="110" align="right">
            <template #default="{ row }">{{ formatQuantityUnit(lineReturnableQuantity(row), row.unit) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="detail.reservations?.length" class="data-panel detail-section">
        <h2 class="section-title">预占与批次</h2>
        <el-table :data="detail.reservations" row-key="reservationId" stripe>
          <el-table-column label="预占号" min-width="180">
            <template #default="{ row }">{{ row.reservationNo }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="reservationStatusMeta(row.status).tagType" effect="plain" size="small">
                {{ reservationStatusMeta(row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="100" align="right">
            <template #default="{ row }">{{ formatQuantity(row.quantity) }}</template>
          </el-table-column>
          <el-table-column label="到期" width="170">
            <template #default="{ row }">{{ formatDateTime(row.expiresAt) }}</template>
          </el-table-column>
          <el-table-column label="释放原因" width="110">
            <template #default="{ row }">{{ row.releaseReason || '—' }}</template>
          </el-table-column>
          <el-table-column label="批次分配" min-width="260">
            <template #default="{ row }">
              <div v-for="allocation in row.allocations" :key="allocation.id" class="allocation-line">
                <span>{{ allocation.batchNo }}</span>
                <span class="muted">× {{ formatQuantity(allocation.quantity) }}</span>
                <span v-if="allocation.expiryDate" class="muted">效期 {{ allocation.expiryDate }}</span>
                <span v-if="allocation.unitCostSnapshot != null" class="muted">成本 {{ formatUnitCost(allocation.unitCostSnapshot) }}</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <p class="text-help muted">批次按 FEFO（有效期内早到期优先）+ FIFO 分配；已预占库存不能被损耗或报废扣减。</p>
      </section>

      <section v-if="detail.approvalRecords?.length" class="data-panel detail-section">
        <h2 class="section-title">审批记录</h2>
        <el-timeline>
          <el-timeline-item
            v-for="record in detail.approvalRecords"
            :key="record.id"
            :timestamp="formatDateTime(record.decidedAt)"
            placement="top"
          >
            <div class="approval-record">
              <el-tag :type="approvalDecisionMeta(record.decision).tagType" effect="plain" size="small">
                {{ approvalDecisionMeta(record.decision).label }}
              </el-tag>
              <span class="text-help muted">
                {{ record.actorType === 'SYSTEM' ? '系统自动判定' : `操作人 ${record.decidedBy}` }}
                · 策略 v{{ record.policyVersion }}
              </span>
            </div>
            <p v-if="record.comment" class="approval-comment">{{ record.comment }}</p>
            <div class="approval-reasons">
              <el-tag
                v-for="reason in record.triggerSnapshot?.reasons || []"
                :key="reason"
                :type="approvalReasonMeta(reason).tagType"
                effect="plain"
                size="small"
              >
                {{ approvalReasonMeta(reason).label }}
              </el-tag>
            </div>
          </el-timeline-item>
        </el-timeline>
      </section>

      <section v-if="detail.fulfillment" class="data-panel detail-section">
        <h2 class="section-title">发放记录</h2>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="发放单号">{{ detail.fulfillment.fulfillmentNo }}</el-descriptions-item>
          <el-descriptions-item label="发放人">{{ detail.fulfillment.operatorName }}</el-descriptions-item>
          <el-descriptions-item label="领取人">{{ detail.fulfillment.recipientName }}</el-descriptions-item>
          <el-descriptions-item label="发放时间">{{ formatDateTime(detail.fulfillment.issuedAt) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.fulfillment.note || '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detail.fulfillment.lines" row-key="id" stripe class="nested-table">
          <el-table-column label="批次" min-width="130">
            <template #default="{ row }">{{ batchNoOf(row.batchId) }}</template>
          </el-table-column>
          <el-table-column label="发放数量" width="120" align="right">
            <template #default="{ row }">{{ formatQuantity(row.quantity) }}</template>
          </el-table-column>
          <el-table-column v-if="canViewIssueCost()" label="批次成本" width="130" align="right">
            <template #default="{ row }">{{ row.unitCostSnapshot == null ? '—' : formatUnitCost(row.unitCostSnapshot) }}</template>
          </el-table-column>
          <el-table-column label="流水号" width="110" align="right">
            <template #default="{ row }">#{{ row.movementId }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="detail.returns?.length" class="data-panel detail-section">
        <h2 class="section-title">退回记录</h2>
        <el-table :data="detail.returns" row-key="id" stripe>
          <el-table-column label="退回单号" min-width="170">
            <template #default="{ row }">{{ row.returnNo }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="returnStatusMeta(row.status).tagType" effect="plain" size="small">
                {{ returnStatusMeta(row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="100" align="right">
            <template #default="{ row }">{{ formatQuantity(totalReturnQuantity(row)) }}</template>
          </el-table-column>
          <el-table-column label="原因" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.reason }}</template>
          </el-table-column>
          <el-table-column label="发起时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.requestedAt) }}</template>
          </el-table-column>
          <el-table-column label="确认时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.confirmedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING_CONFIRMATION' && canConfirmReturn()">
                <el-button text type="primary" @click="confirmReturnRecord(row)">确认</el-button>
                <el-button text type="danger" @click="rejectReturnRecord(row)">拒绝</el-button>
              </template>
              <span v-else class="text-help muted">—</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="movements.length" class="data-panel detail-section">
        <h2 class="section-title">该申请库存流水</h2>
        <el-table :data="movements" row-key="movementId" stripe>
          <el-table-column label="流水号" width="100">
            <template #default="{ row }">#{{ row.movementId }}</template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag :type="movementTypeMeta(row.movementType).tagType" effect="plain" size="small">
                {{ movementTypeMeta(row.movementType).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="批次" min-width="120">
            <template #default="{ row }">{{ row.batchNo }}</template>
          </el-table-column>
          <el-table-column label="数量变化" width="110" align="right">
            <template #default="{ row }">
              <span :class="Number(row.quantityDelta) < 0 ? 'danger-text' : 'success-text'">
                {{ Number(row.quantityDelta) > 0 ? '+' : '' }}{{ formatQuantity(row.quantityDelta) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column v-if="canViewIssueCost()" label="操作人" width="120">
            <template #default="{ row }">{{ row.operatorName }}</template>
          </el-table-column>
          <el-table-column label="发生时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </template>

    <el-dialog v-model="approveDialog" title="批准领用申请" width="420px">
      <p class="text-help muted">
        批准只改变状态，不扣减库存；库存管理员确认发放后才写 ISSUE_OUT。
      </p>
      <el-input v-model="approveComment" type="textarea" :rows="3" maxlength="500" placeholder="可选：审批意见" />
      <template #footer>
        <el-button @click="approveDialog = false">取消</el-button>
        <el-button type="success" :loading="acting" @click="approveRequest">确认批准</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectDialog" title="拒绝领用申请" width="460px">
      <p class="text-help muted">拒绝必须填写原因；预占会立即释放。</p>
      <el-input v-model="rejectComment" type="textarea" :rows="3" maxlength="500" placeholder="拒绝原因（必填）" />
      <template #footer>
        <el-button @click="rejectDialog = false">取消</el-button>
        <el-button type="danger" :loading="acting" @click="rejectRequest">确认拒绝</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="confirmDialog" title="确认全量发放" width="560px">
      <p class="text-help muted">
        本次将按预占批次全量发放，扣减库存并写入 ISSUE_OUT；确认后不可撤销。
      </p>
      <el-input v-model="confirmNote" type="textarea" :rows="3" maxlength="500" placeholder="可选：发放备注" />
      <template #footer>
        <el-button @click="confirmDialog = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="confirmIssue">确认发放</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="returnDialog" title="发起退回" width="620px">
      <p class="text-help muted">
        退回数量不得超过原发放未退数量；确认退回后写 RETURN_IN 并恢复批次与可用库存。
      </p>
      <el-table :data="returnRows" row-key="fulfillmentLineId" stripe>
        <el-table-column label="批次" min-width="120">
          <template #default="{ row }">{{ batchNoOf(row.batchId) }}</template>
        </el-table-column>
        <el-table-column label="可退回" width="100" align="right">
          <template #default="{ row }">{{ formatQuantity(row.returnableQuantity) }}</template>
        </el-table-column>
        <el-table-column label="退回数量" width="170">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0" :max="row.returnableQuantity" :precision="3" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
      </el-table>
      <el-input v-model="returnReason" type="textarea" :rows="3" maxlength="500" placeholder="退回原因（必填，2-500 字）" class="dialog-field" />
      <template #footer>
        <el-button @click="returnDialog = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="createReturn">提交退回申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reallocateDialog" title="重分配预占批次" width="720px">
      <p class="text-help muted">
        仅 APPROVED 且预占仍有效时可重分配；提交后旧预占释放、新预占生效，并记录原因。
      </p>
      <div v-for="row in reallocateRows" :key="row.requestLineId" class="reallocate-row">
        <div class="reallocate-row__title">
          <strong>{{ row.componentName }}</strong>
          <span class="text-help muted">申请 {{ formatQuantityUnit(row.requestedQuantity, row.unit) }}</span>
        </div>
        <div class="reallocate-row__fields">
          <el-select v-model="row.batchId" placeholder="选择批次" style="width: 100%">
            <el-option
              v-for="batch in row.batches"
              :key="batch.id"
              :label="`${batch.batchNo} · 可用 ${formatQuantity(batch.availableQuantity)}${batch.expiryDate ? ` · 效期 ${batch.expiryDate}` : ''}`"
              :value="batch.id"
            />
          </el-select>
          <el-input-number v-model="row.quantity" :min="0" :precision="3" controls-position="right" style="width: 100%" />
        </div>
      </div>
      <el-input v-model="reallocateReason" type="textarea" :rows="2" maxlength="500" placeholder="重分配原因（必填）" class="dialog-field" />
      <template #footer>
        <el-button @click="reallocateDialog = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitReallocate">确认重分配</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Edit } from '@element-plus/icons-vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import RegionState from '@/components/RegionState.vue'
import IssueStatusTag from '@/components/inventory/IssueStatusTag.vue'
import {
  ISSUE_STATUS,
  approvalDecisionMeta,
  approvalReasonMeta,
  canAccessIssuance,
  canConfirmReturn,
  canViewIssueCost,
  formatAmount,
  formatQuantity,
  formatQuantityUnit,
  formatRemainingDuration,
  formatUnitCost,
  getInventoryComponentSummary,
  getIssueRequest,
  issueRequestActions,
  lineReturnableQuantity,
  listInventoryComponentBatches,
  listInventoryMovements,
  canViewInventoryMovements,
  canViewIssueBatches,
  movementTypeMeta,
  normalizePage,
  reservationStatusMeta,
  returnStatusMeta,
  confirmIssueRequest,
  cancelIssueRequest,
  createFulfillmentReturn,
  confirmReturn,
  rejectReturn,
  rejectIssueRequest,
  approveIssueRequest,
  submitIssueRequest,
  reallocateIssueRequest
} from '@/api/inventoryIssuance'
import {
  errorTraceId,
  isIssueNotFound,
  isIssueVersionConflict,
  isIssueInsufficientStock,
  isIssueReturnExceedsIssued,
  isIssueReservedStockProtected,
  isIssueTransactionFailed,
  issueInsufficientStockDetail,
  normalizeApiError,
  notifyApiError
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const notFound = ref(false)
const detail = ref(null)
const movements = ref([])
const acting = ref(false)
const actionSuccess = ref('')
const conflictVersion = ref(null)
const stockShortage = ref(null)
const nowTick = ref(Date.now())

const approveDialog = ref(false)
const approveComment = ref('')
const rejectDialog = ref(false)
const rejectComment = ref('')
const confirmDialog = ref(false)
const confirmNote = ref('')
const returnDialog = ref(false)
const returnReason = ref('')
const returnRows = ref([])
const reallocateDialog = ref(false)
const reallocateReason = ref('')
const reallocateRows = ref([])

let tickTimer = null

const actions = computed(() => issueRequestActions(detail.value))
const reservationActive = computed(() => (
  [ISSUE_STATUS.PENDING_APPROVAL, ISSUE_STATUS.APPROVED].includes(detail.value?.status)
  && (detail.value?.reservations || []).some((item) => item.status === 'ACTIVE')
))
const remainingText = computed(() => formatRemainingDuration(detail.value?.expiresAt, nowTick.value))

function batchNoOf(batchId) {
  for (const reservation of detail.value?.reservations || []) {
    const found = (reservation.allocations || []).find((item) => Number(item.batchId) === Number(batchId))
    if (found) return found.batchNo
  }
  return `批次 ${batchId}`
}

function totalReturnQuantity(row) {
  return (row.lines || []).reduce((sum, line) => sum + Number(line.quantity || 0), 0)
}

function back() {
  router.push('/inventory/issues')
}

function goEdit() {
  router.push(`/inventory/issues/${detail.value.id}/edit`)
}

async function loadMovements(requestId) {
  try {
    const response = await listInventoryMovements({ requestId, page: 1, pageSize: 50 })
    movements.value = normalizePage(response).list
  } catch (error) {
    movements.value = []
  }
}

async function enrichLineAvailability(data) {
  if (!canViewIssueBatches() || !data?.lines?.length) return
  const componentIds = [...new Set(data.lines.map((line) => Number(line.componentId)).filter(Boolean))]
  await Promise.all(componentIds.map(async (componentId) => {
    try {
      const balance = await getInventoryComponentSummary(componentId)
      data.lines
        .filter((line) => Number(line.componentId) === componentId)
        .forEach((line) => {
          line.onHandQuantity = balance.onHandQuantity
          line.availableQuantity = balance.availableQuantity
        })
    } catch (error) {
      // 库存明细不可见时保持空值，不影响申请详情主体
    }
  }))
}

async function load() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  notFound.value = false
  conflictVersion.value = null
  stockShortage.value = null
  try {
    const data = await getIssueRequest(route.params.id)
    detail.value = data
    await enrichLineAvailability(data)
    if (canViewInventoryMovements()) {
      await loadMovements(data.id)
    }
  } catch (error) {
    detail.value = null
    if (isIssueNotFound(error)) {
      notFound.value = true
      return
    }
    loadError.value = normalizeApiError(error).msg
    loadTraceId.value = errorTraceId(error)
    notifyApiError(error)
  } finally {
    loading.value = false
  }
}

function handleActionError(error) {
  if (isIssueVersionConflict(error)) {
    conflictVersion.value = normalizeApiError(error).data?.currentVersion ?? 0
    return
  }
  if (isIssueInsufficientStock(error)) {
    stockShortage.value = issueInsufficientStockDetail(error)
    return
  }
  notifyApiError(error)
  ElMessage.error(normalizeApiError(error).msg)
}

async function runAction(fn, successMessage) {
  acting.value = true
  conflictVersion.value = null
  stockShortage.value = null
  try {
    await fn()
    actionSuccess.value = successMessage
    ElMessage.success(successMessage)
    await load()
  } catch (error) {
    handleActionError(error)
    if (isIssueTransactionFailed(error) || isIssueReservedStockProtected(error) || isIssueReturnExceedsIssued(error)) {
      await load()
    }
  } finally {
    acting.value = false
  }
}

function submitDraft() {
  return runAction(
    () => submitIssueRequest(detail.value.id, { requestVersion: detail.value.version }, createIdempotencyKey('w46-issue-submit')),
    '提交成功，已生成预占'
  )
}

async function cancelRequest() {
  try {
    await ElMessageBox.confirm(
      detail.value.status === ISSUE_STATUS.DRAFT ? '确认取消该草稿？' : '取消将释放全部预占库存，确认继续？',
      '取消领用申请',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '返回' }
    )
  } catch (error) {
    return
  }
  await runAction(
    () => cancelIssueRequest(detail.value.id, { requestVersion: detail.value.version }, createIdempotencyKey('w46-issue-cancel')),
    '申请已取消，预占已释放'
  )
}

async function approveRequest() {
  await runAction(
    () => approveIssueRequest(detail.value.id, { requestVersion: detail.value.version, comment: approveComment.value || undefined }, createIdempotencyKey('w46-issue-approve')),
    '已批准，等待库存管理员发放'
  )
  approveDialog.value = false
  approveComment.value = ''
}

async function rejectRequest() {
  if (!String(rejectComment.value || '').trim()) {
    ElMessage.warning('拒绝时必须填写原因')
    return
  }
  await runAction(
    () => rejectIssueRequest(detail.value.id, { requestVersion: detail.value.version, comment: rejectComment.value.trim() }, createIdempotencyKey('w46-issue-reject')),
    '已拒绝，预占已释放'
  )
  rejectDialog.value = false
  rejectComment.value = ''
}

function openConfirm() {
  confirmNote.value = ''
  confirmDialog.value = true
}

async function confirmIssue() {
  const reservationVersion = (detail.value.reservations || []).find((item) => item.status === 'ACTIVE')?.version
  acting.value = true
  conflictVersion.value = null
  try {
    const result = await confirmIssueRequest(
      detail.value.id,
      { requestVersion: detail.value.version, reservationVersion, note: confirmNote.value || undefined },
      createIdempotencyKey('w46-issue-confirm')
    )
    const count = Array.isArray(result?.movements) ? result.movements.length : 0
    actionSuccess.value = `发放成功：已扣减库存并写入 ${count} 条 ISSUE_OUT 流水`
    ElMessage.success(actionSuccess.value)
    confirmDialog.value = false
    await load()
  } catch (error) {
    handleActionError(error)
    if (isIssueTransactionFailed(error)) await load()
  } finally {
    acting.value = false
  }
}

function openReturn() {
  const lines = detail.value?.fulfillment?.lines || []
  returnRows.value = lines.map((line) => {
    const requestLine = (detail.value.lines || []).find((item) => item.id === line.requestLineId)
    return {
      fulfillmentLineId: line.id,
      batchId: line.batchId,
      quantity: 0,
      returnableQuantity: Number(line.quantity || 0) - Number(requestLine?.returnedQuantity || 0)
    }
  }).filter((row) => row.returnableQuantity > 0)
  returnReason.value = ''
  if (!returnRows.value.length) {
    ElMessage.info('当前没有可退回的发放数量')
    return
  }
  returnDialog.value = true
}

async function createReturn() {
  const lines = returnRows.value
    .filter((row) => Number(row.quantity) > 0)
    .map((row) => ({ fulfillmentLineId: row.fulfillmentLineId, quantity: Number(row.quantity) }))
  if (!lines.length) {
    ElMessage.warning('至少填写一条退回数量')
    return
  }
  await runAction(
    () => createFulfillmentReturn(
      detail.value.fulfillment.id,
      { reason: String(returnReason.value || '').trim(), lines },
      createIdempotencyKey('w46-return-create')
    ),
    '退回申请已提交，等待库存管理员确认'
  )
  returnDialog.value = false
}

async function confirmReturnRecord(row) {
  await runAction(
    () => confirmReturn(row.id, { returnVersion: row.version }, createIdempotencyKey('w46-return-confirm')),
    '退回已确认，库存已恢复并写入 RETURN_IN'
  )
}

async function rejectReturnRecord(row) {
  let comment = ''
  try {
    const result = await ElMessageBox.prompt('请填写拒绝退回原因', '拒绝退回', {
      inputPlaceholder: '1-500 字',
      confirmButtonText: '确认拒绝',
      cancelButtonText: '取消'
    })
    comment = result.value
  } catch (error) {
    return
  }
  await runAction(
    () => rejectReturn(row.id, { comment }, createIdempotencyKey('w46-return-reject')),
    '退回已拒绝'
  )
}

async function openReallocate() {
  reallocateReason.value = ''
  const rows = []
  for (const line of detail.value.lines || []) {
    let batches = []
    try {
      const response = await listInventoryComponentBatches(line.componentId, { page: 1, pageSize: 50 })
      batches = normalizePage(response).list.filter((batch) => Number(batch.availableQuantity) > 0)
    } catch (error) {
      batches = []
    }
    const currentAllocation = (detail.value.reservations || [])
      .flatMap((reservation) => reservation.allocations || [])
      .find((allocation) => Number(allocation.id) === Number((line.allocations || [])[0]?.id))
    const reservation = (detail.value.reservations || []).find((item) => Number(item.requestLineId) === Number(line.id))
    rows.push({
      reservationId: reservation?.reservationId,
      requestLineId: line.id,
      componentName: line.componentName,
      unit: line.unit,
      requestedQuantity: line.requestedQuantity,
      batchId: currentAllocation?.batchId || batches[0]?.id || null,
      quantity: Number(line.requestedQuantity),
      batches
    })
  }
  reallocateRows.value = rows
  reallocateDialog.value = true
}

async function submitReallocate() {
  if (!String(reallocateReason.value || '').trim()) {
    ElMessage.warning('重分配必须填写原因')
    return
  }
  const allocations = reallocateRows.value
    .filter((row) => row.batchId && Number(row.quantity) > 0)
    .map((row) => ({ reservationId: row.reservationId, batchId: row.batchId, quantity: Number(row.quantity) }))
  if (!allocations.length) {
    ElMessage.warning('至少需要一条批次分配')
    return
  }
  await runAction(
    () => reallocateIssueRequest(
      detail.value.id,
      {
        requestVersion: detail.value.version,
        reason: reallocateReason.value.trim(),
        allocations
      },
      createIdempotencyKey('w46-issue-reallocate')
    ),
    '批次已重分配，预占已更新'
  )
  reallocateDialog.value = false
}

onMounted(() => {
  load()
  tickTimer = setInterval(() => { nowTick.value = Date.now() }, 30000)
})

onUnmounted(() => {
  if (tickTimer) clearInterval(tickTimer)
})
</script>

<style scoped>
.issue-detail-page {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.detail-title-row {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.approval-reasons {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.approval-record {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.approval-comment {
  margin: 8px 0;
}

.component-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.allocation-line {
  display: flex;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.block {
  display: block;
}

.danger-text {
  color: var(--el-color-danger);
}

.success-text {
  color: var(--el-color-success);
}

.nested-table {
  margin-top: var(--usn-space-2);
}

.dialog-field {
  margin-top: var(--usn-space-3);
}

.reallocate-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: var(--usn-space-3) 0;
  border-bottom: 1px dashed var(--usn-border-color, #e4e7ed);
}

.reallocate-row__title {
  display: flex;
  justify-content: space-between;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.reallocate-row__fields {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(120px, 1fr);
  gap: var(--usn-space-3);
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .detail-section :deep(.el-descriptions__body) {
    overflow-x: auto;
  }

  .reallocate-row__fields {
    grid-template-columns: 1fr;
  }
}
</style>
