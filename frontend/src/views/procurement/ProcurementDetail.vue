<template>
  <div class="procurement-detail-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="router.push('/procurements')">返回采购列表</el-button>
      <span class="text-help muted">{{ order?.purchaseNo || '采购单详情' }}</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canAccessProcurement()"
      state="permission"
      variant="section"
      description="当前账号无权访问采购单"
      sub-title="请使用实验室成员、库存管理员、老师或系统管理员账号登录"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载采购单详情"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="采购单详情加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="loadDetail"
    />

    <RegionState
      v-else-if="notFound"
      state="permission"
      variant="section"
      description="采购单不存在或当前账号不可见"
      sub-title="不可见资源统一返回 404，不泄露单据、附件或数量是否存在"
    />

    <template v-else-if="order">
      <section class="detail-hero data-panel">
        <div class="detail-hero__body">
          <div class="hero-title-row">
            <div>
              <p class="text-help muted">{{ order.purchaseNo }} · 申请人 {{ order.requesterName || `用户 ${order.requesterId}` }}</p>
              <h1 class="page-title">{{ order.title }}</h1>
              <p class="text-help muted">
                项目 {{ order.projectName || '—' }} · 最近更新 {{ formatDateTime(order.updateTime) }} ·
                <span class="version-text">version {{ order.version }}</span>
              </p>
            </div>
            <div class="hero-tags">
              <OrderStatusTag :status="order.status" effect="dark" />
              <el-tag v-if="order.approvalRequired" type="warning" effect="plain" size="small">需人工审批</el-tag>
              <el-tag v-else-if="order.approvalRequired === false" type="success" effect="plain" size="small">安全免审</el-tag>
            </div>
          </div>

          <dl class="hero-meta">
            <div>
              <dt>明细小计</dt>
              <dd>{{ formatAmount(order.itemSubtotal) }}</dd>
            </div>
            <div>
              <dt>运费 / 优惠</dt>
              <dd>{{ formatAmount(order.shippingAmount) }} / -{{ formatAmount(order.discountAmount) }}</dd>
            </div>
            <div>
              <dt>应付金额</dt>
              <dd class="amount-strong">{{ formatAmount(order.payableAmount) }}</dd>
            </div>
            <div>
              <dt>实际实付</dt>
              <dd>{{ order.actualPaidAmount == null ? '—' : formatAmount(order.actualPaidAmount) }}</dd>
            </div>
            <div>
              <dt>币种</dt>
              <dd>{{ order.currency }}</dd>
            </div>
            <div>
              <dt>审批策略版本</dt>
              <dd>{{ order.approvalPolicyVersion == null ? '—' : `v${order.approvalPolicyVersion}` }}</dd>
            </div>
          </dl>

          <div class="hero-actions">
            <el-button v-if="actions.edit" plain :icon="Edit" :disabled="attachmentBusy" @click="goEdit">编辑草稿</el-button>
            <el-button v-if="actions.submit" type="primary" plain :disabled="attachmentBusy" @click="openSubmit">提交审批</el-button>
            <el-button v-if="actions.approve" type="success" :disabled="attachmentBusy" @click="openDecision('approve')">批准</el-button>
            <el-button v-if="actions.reject" type="danger" plain :disabled="attachmentBusy" @click="openDecision('reject')">拒绝</el-button>
            <el-button v-if="actions.markOrdered" type="primary" :disabled="attachmentBusy" @click="openOrdered">登记下单</el-button>
            <el-button v-if="actions.createReceipt" type="primary" plain :disabled="attachmentBusy" @click="goCreateReceipt">新建到货</el-button>
            <el-button v-if="actions.cancel" type="warning" plain :disabled="attachmentBusy" @click="openCancel">取消整单</el-button>
            <el-button v-if="actions.cancelRemaining" type="warning" plain :disabled="attachmentBusy" @click="openCancelRemaining">取消剩余</el-button>
            <el-button v-if="actions.complete" type="primary" :disabled="attachmentBusy" @click="openComplete">标记完成</el-button>
          </div>
          <p v-if="actionHints.length" class="text-help muted">
            {{ actionHints.join('；') }}
          </p>
        </div>
      </section>

      <el-alert
        v-if="conflictInfo"
        type="warning"
        :closable="true"
        show-icon
        :title="`检测到 version 冲突：服务端 currentVersion=${conflictInfo.currentVersion ?? '未知'}。未自动覆盖，请刷新比较后重试。`"
        @close="conflictInfo = null"
      >
        <el-button text type="primary" @click="loadDetail">刷新并比较</el-button>
      </el-alert>

      <section class="data-panel order-section">
        <header class="section-header">
          <div>
            <h2 class="section-title">审批</h2>
            <p class="text-help muted">人工审批人不能是申请人或买手；买手也不能等于人工审批人。</p>
          </div>
        </header>

        <div class="approval-summary">
          <div>
            <span class="text-help muted">审批结论</span>
            <strong>{{ order.approvalRequired == null ? '未提交' : (order.approvalRequired ? '需人工审批' : '安全免审') }}</strong>
          </div>
          <div>
            <span class="text-help muted">触发原因</span>
            <div class="tag-row">
              <el-tag
                v-for="reason in order.approvalReasons || []"
                :key="reason"
                size="small"
                :type="approvalReasonMeta(reason).tagType"
                effect="plain"
              >
                {{ approvalReasonMeta(reason).label }}
              </el-tag>
              <span v-if="!(order.approvalReasons || []).length" class="text-help muted">—</span>
            </div>
          </div>
          <div>
            <span class="text-help muted">策略版本</span>
            <strong>{{ order.approvalPolicyVersion == null ? '—' : `v${order.approvalPolicyVersion}` }}</strong>
          </div>
          <div>
            <span class="text-help muted">当前状态</span>
            <OrderStatusTag :status="order.status" size="small" />
          </div>
        </div>

        <el-alert
          v-if="order.status === ORDER_STATUS.PENDING_APPROVAL && isOrderRequester(order)"
          type="info"
          :closable="false"
          show-icon
          title="你是申请人，不能审批自己的单据；请由老师或系统管理员审批。"
        />

        <div v-if="(order.approvalRecords || []).length" class="timeline">
          <div v-for="record in order.approvalRecords" :key="record.id" class="timeline-item">
            <el-tag :type="approvalDecisionMeta(record.decision).tagType" size="small">
              {{ approvalDecisionMeta(record.decision).label }}
            </el-tag>
            <div class="timeline-item__body">
              <p>
                {{ formatDateTime(record.decidedAt) }} ·
                {{ record.actorType === 'SYSTEM' ? '系统' : `用户 ${record.decidedBy ?? '—'}` }} ·
                策略 v{{ record.policyVersion }}
              </p>
              <p v-if="record.triggerSnapshot?.payableAmount != null" class="text-help muted">
                触发快照应付 {{ formatAmount(record.triggerSnapshot.payableAmount) }}
              </p>
              <p v-if="record.comment" class="timeline-item__comment">{{ record.comment }}</p>
            </div>
          </div>
        </div>
        <p v-else class="text-help muted">暂无审批记录。</p>
      </section>

      <section class="data-panel order-section">
        <h2 class="section-title">下单事实</h2>
        <div class="facts-grid">
          <div><span class="text-help muted">供应商</span><strong>{{ order.supplierName || '—' }}</strong></div>
          <div><span class="text-help muted">平台</span><strong>{{ order.platformType ? platformTypeMeta(order.platformType).label : '—' }}</strong></div>
          <div><span class="text-help muted">平台订单号</span><strong>{{ order.platformOrderNo || '—' }}</strong></div>
          <div><span class="text-help muted">买手</span><strong>{{ order.buyerName || (order.buyerId ? `用户 ${order.buyerId}` : '—') }}</strong></div>
          <div><span class="text-help muted">下单时间</span><strong>{{ formatDateTime(order.orderedAt) }}</strong></div>
          <div><span class="text-help muted">完成时间</span><strong>{{ formatDateTime(order.completedAt) }}</strong></div>
          <div><span class="text-help muted">经费来源</span><strong>{{ order.fundingSource || '—' }}</strong></div>
          <div><span class="text-help muted">关闭原因</span><strong>{{ order.closeReason || '—' }}</strong></div>
        </div>
        <el-alert
          v-if="order.paymentDifferenceNote"
          type="warning"
          :closable="false"
          show-icon
          :title="`实付与应付差异原因：${order.paymentDifferenceNote}`"
        />
      </section>

      <section class="data-panel order-section">
        <header class="section-header">
          <div>
            <h2 class="section-title">采购明细</h2>
            <p class="text-help muted">进度按 received + canceled 计算；不可用采购状态冒充库存数量。</p>
          </div>
        </header>

        <el-table class="desktop-only" :data="order.lines || []" row-key="id" stripe>
          <el-table-column label="元器件" min-width="220">
            <template #default="{ row }">
              <div class="line-name">
                <strong>{{ row.componentName }}</strong>
                <span class="text-help muted">{{ row.mpn }} · 规格 v{{ row.specVersion }} · {{ row.manufacturerName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="订购 / 已收 / 取消 / 剩余" min-width="250">
            <template #default="{ row }">
              <div class="quantity-cell">
                <span>{{ formatQuantityUnit(row.orderedQuantity, row.unit) }}</span>
                <span class="muted">{{ formatQuantityUnit(row.receivedQuantity, row.unit) }}</span>
                <span class="muted">{{ formatQuantityUnit(row.canceledQuantity, row.unit) }}</span>
                <span :class="{ 'text-danger': row.remainingQuantity > 0 }">{{ formatQuantityUnit(row.remainingQuantity, row.unit) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="到货进度" min-width="220">
            <template #default="{ row }"><ReceiptProgress :line="row" /></template>
          </el-table-column>
          <el-table-column label="单价 / 小计" width="160" align="right">
            <template #default="{ row }">
              <div class="amount-cell">
                <span>{{ formatUnitPrice(row.unitPrice) }}</span>
                <strong>{{ formatAmount(row.lineSubtotal) }}</strong>
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="canViewInventoryCost()" label="分摊净额 / 单位成本" width="180" align="right">
            <template #default="{ row }">
              <div class="amount-cell">
                <span>{{ row.allocatedNetAmount == null ? '下单后生成' : formatAmount(row.allocatedNetAmount) }}</span>
                <strong>{{ row.effectiveUnitCost == null ? '—' : formatUnitPrice(row.effectiveUnitCost) }}</strong>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="mobile-only line-mobile-list">
          <article v-for="row in order.lines || []" :key="row.id" class="line-mobile-card">
            <div class="line-mobile-card__top">
              <strong>{{ row.componentName }}</strong>
              <el-tag size="small" effect="plain">{{ row.unit }}</el-tag>
            </div>
            <p class="text-help muted">{{ row.mpn }} · 规格 v{{ row.specVersion }}</p>
            <ReceiptProgress :line="row" />
            <div class="line-mobile-card__amount">
              <span>单价 {{ formatUnitPrice(row.unitPrice) }}</span>
              <strong>小计 {{ formatAmount(row.lineSubtotal) }}</strong>
            </div>
            <p v-if="canViewInventoryCost() && row.effectiveUnitCost != null" class="text-help muted">
              分摊净额 {{ formatAmount(row.allocatedNetAmount) }} · 单位成本 {{ formatUnitPrice(row.effectiveUnitCost) }}
            </p>
          </article>
        </div>

        <div class="amount-summary">
          <div><span class="text-help muted">明细小计</span><strong>{{ formatAmount(order.itemSubtotal) }}</strong></div>
          <div><span class="text-help muted">运费</span><strong>{{ formatAmount(order.shippingAmount) }}</strong></div>
          <div><span class="text-help muted">优惠</span><strong>-{{ formatAmount(order.discountAmount) }}</strong></div>
          <div class="amount-summary__payable"><span>应付金额</span><strong>{{ formatAmount(order.payableAmount) }}</strong></div>
        </div>
      </section>

      <section class="data-panel order-section">
        <header class="section-header">
          <div>
            <h2 class="section-title">到货记录</h2>
            <p class="text-help muted">同一采购单最多一条 DRAFT；确认后批次、流水与余额原子写入。</p>
          </div>
          <el-button v-if="actions.createReceipt" type="primary" plain :disabled="attachmentBusy" @click="goCreateReceipt">
            新建到货
          </el-button>
        </header>

        <div v-if="receiptsLoading" class="receipts-loading"><el-skeleton :rows="2" animated /></div>
        <el-alert
          v-else-if="receiptsError"
          type="error"
          :closable="false"
          show-icon
          :title="receiptsError"
        >
          <el-button text type="primary" @click="loadReceipts">重试</el-button>
        </el-alert>
        <p v-else-if="!receipts.length" class="text-help muted">暂无到货记录。</p>

        <div v-else class="receipt-grid">
          <article v-for="receipt in receipts" :key="receipt.id" class="receipt-card">
            <div class="receipt-card__top">
              <strong>{{ receipt.receiptNo }}</strong>
              <el-tag :type="receiptStatusMeta(receipt.status).tagType" size="small">
                {{ receiptStatusMeta(receipt.status).label }}
              </el-tag>
            </div>
            <p class="text-help muted">
              到货 {{ formatDateTime(receipt.receivedAt) }} · version {{ receipt.version }}
            </p>
            <p v-if="receipt.note">{{ receipt.note }}</p>
            <p class="text-help muted">
              收货人 {{ receipt.receiverName || '—' }} · 确认 {{ formatDateTime(receipt.confirmedAt) }}
            </p>
            <div class="receipt-card__actions">
              <el-button
                text
                type="primary"
                @click="router.push(`/procurement-receipts/${receipt.id}`)"
              >
                {{ receipt.status === RECEIPT_STATUS.DRAFT ? '继续草稿' : '查看到货详情' }}
              </el-button>
            </div>
          </article>
        </div>
      </section>

      <AttachmentPanel
        v-if="canViewAttachments(order)"
        owner-type="PROCUREMENT_ORDER"
        :owner-id="order.id"
        :owner-version="order.version"
        :can-upload="canUploadOrderFiles"
        can-view
        title="采购单附件"
        @owner-version="onOwnerVersion"
        @busy="attachmentBusy = $event"
        @refresh="loadDetail"
      />

      <section class="data-panel order-section">
        <h2 class="section-title">审计时间线</h2>
        <p class="text-help muted">仅展示契约已有字段：status、version、操作者、reason、requestId 与时间；不伪造状态历史。</p>
        <div class="timeline">
          <div v-for="event in auditEvents" :key="event.key" class="timeline-item">
            <el-tag size="small" effect="plain">{{ event.label }}</el-tag>
            <div class="timeline-item__body">
              <p>{{ event.time }}</p>
              <p v-if="event.detail" class="timeline-item__comment">{{ event.detail }}</p>
            </div>
          </div>
        </div>
      </section>

      <el-dialog v-model="submitVisible" title="提交采购单" width="min(520px, 92vw)">
        <p>提交后将锁定当前草稿并递增 version。</p>
        <p class="text-help muted">
          提交时服务端按冻结策略重算应付金额与审批原因；命中阈值/贵重/指定分类将进入待审批。
        </p>
        <template #footer>
          <el-button @click="submitVisible = false">取消</el-button>
          <el-button type="primary" :loading="actionLoading === 'submit'" @click="doSubmit">确认提交</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="decisionVisible" :title="decisionType === 'approve' ? '批准采购单' : '拒绝采购单'" width="min(560px, 92vw)">
        <label class="form-field">
          <span>{{ decisionType === 'reject' ? '拒绝原因 *（1-500 字）' : '审批意见（可选，≤500 字）' }}</span>
          <el-input v-model="decisionComment" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </label>
        <p class="text-help muted">审批人不能是申请人或买手；操作会递增 version。</p>
        <template #footer>
          <el-button @click="decisionVisible = false">取消</el-button>
          <el-button
            :type="decisionType === 'approve' ? 'success' : 'danger'"
            :loading="actionLoading === decisionType"
            @click="submitDecision"
          >
            {{ decisionType === 'approve' ? '确认批准' : '确认拒绝' }}
          </el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="orderedVisible" title="登记下单事实" width="min(640px, 94vw)">
        <div class="dialog-grid">
          <label class="form-field">
            <span>买手 ID *</span>
            <div class="inline-field">
              <el-input-number v-model="orderedForm.buyerId" :min="1" :precision="0" controls-position="right" style="width: 100%" />
              <el-button v-if="currentUserId()" text type="primary" @click="orderedForm.buyerId = currentUserId()">使用我</el-button>
            </div>
          </label>
          <label class="form-field">
            <span>供应商名称 *（2-120 字）</span>
            <el-input v-model="orderedForm.supplierName" maxlength="120" show-word-limit />
          </label>
          <label class="form-field">
            <span>平台 *</span>
            <el-select v-model="orderedForm.platformType" style="width: 100%">
              <el-option
                v-for="(meta, value) in PLATFORM_TYPE_META"
                :key="value"
                :label="meta.label"
                :value="value"
              />
            </el-select>
          </label>
          <label class="form-field">
            <span>平台订单号</span>
            <el-input v-model="orderedForm.platformOrderNo" maxlength="64" placeholder="平台唯一订单号；重复会被拒绝" />
          </label>
          <label class="form-field">
            <span>实际实付（CNY）*</span>
            <el-input-number v-model="orderedForm.actualPaidAmount" :min="0" :precision="2" :step="1" controls-position="right" style="width: 100%" />
          </label>
          <label class="form-field form-field--wide">
            <span>实付差异原因{{ payableDifferenceRequired ? ' *' : '' }}</span>
            <el-input
              v-model="orderedForm.paymentDifferenceNote"
              maxlength="500"
              show-word-limit
              :placeholder="payableDifferenceRequired ? '实付低于应付时必须填写 1-500 字原因' : '可选'"
            />
          </label>
        </div>
        <p class="text-help muted">
          应付 {{ formatAmount(order.payableAmount) }}；实付不得超过应付。登记后将冻结按行分摊净额与 effectiveUnitCost。
        </p>
        <template #footer>
          <el-button @click="orderedVisible = false">取消</el-button>
          <el-button type="primary" :loading="actionLoading === 'mark-ordered'" @click="submitOrdered">确认下单</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="cancelVisible" :title="cancelType === 'remaining' ? '取消剩余数量' : '取消整单'" width="min(520px, 92vw)">
        <label class="form-field">
          <span>{{ cancelType === 'remaining' ? '取消剩余原因 *（1-500 字）' : '取消原因 *（1-500 字）' }}</span>
          <el-input v-model="cancelReason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </label>
        <p class="text-help muted">
          {{ cancelType === 'remaining'
            ? '已收数量保留；剩余数量写入 canceledQuantity，采购单进入 COMPLETED。'
            : '已确认到货不可整单取消；部分到货请使用“取消剩余”。' }}
        </p>
        <template #footer>
          <el-button @click="cancelVisible = false">取消</el-button>
          <el-button type="warning" :loading="actionLoading === (cancelType === 'remaining' ? 'cancel-remaining' : 'cancel')" @click="submitCancel">
            确认{{ cancelType === 'remaining' ? '取消剩余' : '取消整单' }}
          </el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="completeVisible" title="标记采购完成" width="min(480px, 92vw)">
        <p>仅 RECEIVED 状态可标记完成；操作会写入 completedAt 并递增 version。</p>
        <template #footer>
          <el-button @click="completeVisible = false">取消</el-button>
          <el-button type="primary" :loading="actionLoading === 'complete'" @click="submitComplete">确认完成</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Edit } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MockStateBanner from '@/components/MockStateBanner.vue'
import RegionState from '@/components/RegionState.vue'
import OrderStatusTag from '@/components/procurement/OrderStatusTag.vue'
import ReceiptProgress from '@/components/procurement/ReceiptProgress.vue'
import AttachmentPanel from '@/components/procurement/AttachmentPanel.vue'
import {
  APPROVAL_REASON_META,
  ORDER_STATUS,
  PLATFORM_TYPE,
  PLATFORM_TYPE_META,
  RECEIPT_STATUS,
  approvalDecisionMeta,
  approvalReasonMeta,
  approveOrder,
  canAccessProcurement,
  canUploadOrderAttachment,
  canViewAttachments,
  canViewInventoryCost,
  cancelOrder,
  cancelRemaining,
  completeOrder,
  currentUserId,
  formatAmount,
  formatQuantityUnit,
  formatUnitPrice,
  getOrder,
  isOrderRequester,
  listReceipts,
  markOrdered,
  normalizePage,
  orderActions,
  orderStatusMeta,
  platformTypeMeta,
  receiptStatusMeta,
  rejectOrder,
  submitOrder
} from '@/api/procurement'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'
import {
  isProcurementApprovalRequired,
  isProcurementDenied,
  isProcurementDutyConflict,
  isProcurementInvalidTransition,
  isProcurementOrderNotFound,
  isProcurementVersionConflict,
  isPaymentDifferenceNoteRequired,
  isPlatformOrderDuplicated,
  normalizeApiError
} from '@/utils/apiError'

const route = useRoute()
const router = useRouter()
const orderId = computed(() => route.params.id)

const order = ref(null)
const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const notFound = ref(false)
const conflictInfo = ref(null)
const attachmentBusy = ref(false)

const receipts = ref([])
const receiptsLoading = ref(false)
const receiptsError = ref('')

const submitVisible = ref(false)
const decisionVisible = ref(false)
const decisionType = ref('approve')
const decisionComment = ref('')
const orderedVisible = ref(false)
const orderedForm = reactive({
  buyerId: null,
  supplierName: '',
  platformType: PLATFORM_TYPE.TAOBAO,
  platformOrderNo: '',
  actualPaidAmount: 0,
  paymentDifferenceNote: ''
})
const cancelVisible = ref(false)
const cancelType = ref('order')
const cancelReason = ref('')
const completeVisible = ref(false)
const actionLoading = ref('')

let actionKey = null

const actions = computed(() => orderActions(order.value))
const canUploadOrderFiles = computed(() => Boolean(order.value) && (
  canUploadOrderAttachment(order.value, 'INVOICE')
  || canUploadOrderAttachment(order.value, 'ORDER_SCREENSHOT')
))
const payableDifferenceRequired = computed(() => Boolean(order.value)
  && Number(orderedForm.actualPaidAmount || 0) < Number(order.value.payableAmount || 0))

const actionHints = computed(() => {
  if (!order.value) return []
  const hints = []
  if (order.value.status === ORDER_STATUS.PENDING_APPROVAL && isOrderRequester(order.value)) {
    hints.push('申请人不可以审批自己的单据')
  }
  if (order.value.status === ORDER_STATUS.PARTIALLY_RECEIVED) {
    hints.push('部分到货：可继续分批到货或取消剩余')
  }
  if (order.value.status === ORDER_STATUS.ORDERED && Number(order.value.version) > 0) {
    hints.push('已下单：到货确认前不得取消整单')
  }
  return hints
})

const auditEvents = computed(() => {
  if (!order.value) return []
  const events = []
  events.push({
    key: 'create',
    time: formatDateTime(order.value.createTime),
    label: '创建',
    detail: `申请人 ${order.value.requesterName || order.value.requesterId}`
  })
  ;(order.value.approvalRecords || []).forEach((record) => {
    events.push({
      key: `approval-${record.id}`,
      time: formatDateTime(record.decidedAt),
      label: approvalDecisionMeta(record.decision).label,
      detail: [
        record.comment || '',
        record.requestId ? `requestId ${record.requestId}` : '',
        record.policyVersion != null ? `策略 v${record.policyVersion}` : ''
      ].filter(Boolean).join(' · ')
    })
  })
  if (order.value.orderedAt) {
    events.push({
      key: 'ordered',
      time: formatDateTime(order.value.orderedAt),
      label: '下单',
      detail: `${order.value.supplierName || '—'} · 买手 ${order.value.buyerName || order.value.buyerId || '—'}`
    })
  }
  if (order.value.completedAt) {
    events.push({
      key: 'completed',
      time: formatDateTime(order.value.completedAt),
      label: '完成',
      detail: order.value.closeReason || ''
    })
  }
  if (order.value.status === ORDER_STATUS.CANCELED || order.value.status === ORDER_STATUS.REJECTED) {
    const lastApproval = (order.value.approvalRecords || []).slice(-1)[0]
    events.push({
      key: 'close',
      time: formatDateTime(lastApproval?.decidedAt || order.value.updateTime),
      label: order.value.status === ORDER_STATUS.CANCELED ? '取消' : '拒绝',
      detail: order.value.closeReason || lastApproval?.comment || ''
    })
  }
  events.push({
    key: 'update',
    time: formatDateTime(order.value.updateTime),
    label: '最近更新',
    detail: `status ${order.value.status} · version ${order.value.version} · 操作者 ${order.value.updatedBy ?? '—'}`
  })
  return events
})

async function loadDetail() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  notFound.value = false
  try {
    const detail = await getOrder(orderId.value)
    order.value = detail
    await loadReceipts()
    if (route.name === 'ProcurementApproval' && orderActions(detail).approve) {
      openDecision('approve')
    }
  } catch (error) {
    if (isProcurementOrderNotFound(error)) {
      notFound.value = true
      order.value = null
    } else if (isProcurementDenied(error)) {
      loadError.value = '当前账号无权访问该采购单'
    } else {
      const normalized = normalizeApiError(error)
      loadError.value = normalized.msg || '采购单详情加载失败'
      loadTraceId.value = normalized.traceId || ''
    }
  } finally {
    loading.value = false
  }
}

async function loadReceipts() {
  receiptsLoading.value = true
  receiptsError.value = ''
  try {
    const response = await listReceipts(orderId.value, { page: 1, pageSize: 50 })
    receipts.value = normalizePage(response).list
  } catch (error) {
    const normalized = normalizeApiError(error)
    receiptsError.value = `${normalized.msg || '到货记录加载失败'}${normalized.traceId ? ` · traceId ${normalized.traceId}` : ''}`
    receipts.value = []
  } finally {
    receiptsLoading.value = false
  }
}

function onOwnerVersion(version) {
  if (!order.value) return
  order.value.version = Number(version)
}

function goEdit() {
  router.push(`/procurements/${orderId.value}/edit`)
}

function goCreateReceipt() {
  router.push(`/procurements/${orderId.value}/receipts/new`)
}

function openSubmit() {
  conflictInfo.value = null
  submitVisible.value = true
}

function openDecision(type) {
  conflictInfo.value = null
  decisionType.value = type
  decisionComment.value = ''
  decisionVisible.value = true
}

function openOrdered() {
  conflictInfo.value = null
  orderedForm.buyerId = order.value.buyerId || currentUserId() || null
  orderedForm.supplierName = order.value.supplierName || ''
  orderedForm.platformType = order.value.platformType || PLATFORM_TYPE.TAOBAO
  orderedForm.platformOrderNo = order.value.platformOrderNo || ''
  orderedForm.actualPaidAmount = Number(order.value.actualPaidAmount ?? order.value.payableAmount ?? 0)
  orderedForm.paymentDifferenceNote = order.value.paymentDifferenceNote || ''
  orderedVisible.value = true
}

function openCancel() {
  conflictInfo.value = null
  cancelType.value = 'order'
  cancelReason.value = ''
  cancelVisible.value = true
}

function openCancelRemaining() {
  conflictInfo.value = null
  cancelType.value = 'remaining'
  cancelReason.value = ''
  cancelVisible.value = true
}

function openComplete() {
  conflictInfo.value = null
  completeVisible.value = true
}

function handleActionError(error) {
  if (isProcurementVersionConflict(error)) {
    const normalized = normalizeApiError(error)
    conflictInfo.value = { currentVersion: normalized.data?.currentVersion ?? null }
    ElMessage.warning(`版本冲突：currentVersion=${conflictInfo.value.currentVersion ?? '未知'}；未自动覆盖`)
    return
  }
  if (isProcurementDenied(error)) {
    ElMessage.error('当前账号无权执行该操作')
    return
  }
  if (isProcurementDutyConflict(error)) {
    ElMessage.error('审批人/买手职责冲突，请更换人员')
    return
  }
  if (isPlatformOrderDuplicated(error)) {
    ElMessage.error('该平台订单号已登记在另一采购单')
    return
  }
  if (isPaymentDifferenceNoteRequired(error)) {
    ElMessage.warning('实付低于应付时必须填写差异原因')
    return
  }
  if (isProcurementInvalidTransition(error)) {
    ElMessage.error('当前状态不允许该操作，已拒绝并保持原状态')
    return
  }
  if (isProcurementApprovalRequired(error)) {
    ElMessage.error('尚未获得有效审批，不能登记下单')
    return
  }
  const normalized = normalizeApiError(error)
  ElMessage.error(`${normalized.msg || '操作失败'}${normalized.traceId ? ` · traceId ${normalized.traceId}` : ''}`)
}

async function afterSuccess(message) {
  ElMessage.success(message)
  conflictInfo.value = null
  submitVisible.value = false
  decisionVisible.value = false
  orderedVisible.value = false
  cancelVisible.value = false
  completeVisible.value = false
  await loadDetail()
}

async function doSubmit() {
  if (actionLoading.value) return
  if (!actionKey) actionKey = createIdempotencyKey('w45-submit')
  actionLoading.value = 'submit'
  try {
    const result = await submitOrder(orderId.value, { version: order.value.version }, actionKey)
    actionKey = null
    await afterSuccess(`已提交，状态 ${orderStatusMeta(result.status).label}，version ${result.version}`)
  } catch (error) {
    handleActionError(error)
  } finally {
    actionLoading.value = ''
  }
}

async function submitDecision() {
  if (actionLoading.value) return
  const comment = decisionComment.value.trim()
  if (decisionType.value === 'reject' && !comment) {
    ElMessage.warning('拒绝时必须填写 1-500 字原因')
    return
  }
  if (!actionKey) actionKey = createIdempotencyKey(`w45-${decisionType.value}`)
  actionLoading.value = decisionType.value
  try {
    const result = decisionType.value === 'approve'
      ? await approveOrder(orderId.value, { version: order.value.version, comment }, actionKey)
      : await rejectOrder(orderId.value, { version: order.value.version, comment }, actionKey)
    actionKey = null
    await afterSuccess(`${decisionType.value === 'approve' ? '已批准' : '已拒绝'}，状态 ${orderStatusMeta(result.status).label}，version ${result.version}`)
  } catch (error) {
    handleActionError(error)
  } finally {
    actionLoading.value = ''
  }
}

async function submitOrdered() {
  if (actionLoading.value) return
  if (!orderedForm.buyerId) {
    ElMessage.warning('请填写买手 ID')
    return
  }
  if (orderedForm.supplierName.trim().length < 2) {
    ElMessage.warning('供应商名称至少 2 个字符')
    return
  }
  if (orderedForm.actualPaidAmount > order.value.payableAmount) {
    ElMessage.warning('实际实付不能超过应付金额')
    return
  }
  if (payableDifferenceRequired.value && !orderedForm.paymentDifferenceNote.trim()) {
    ElMessage.warning('实付低于应付时必须填写差异原因')
    return
  }
  if (!actionKey) actionKey = createIdempotencyKey('w45-mark-ordered')
  actionLoading.value = 'mark-ordered'
  try {
    const result = await markOrdered(orderId.value, {
      version: order.value.version,
      buyerId: orderedForm.buyerId,
      supplierName: orderedForm.supplierName.trim(),
      platformType: orderedForm.platformType,
      platformOrderNo: orderedForm.platformOrderNo.trim() || null,
      actualPaidAmount: Number(orderedForm.actualPaidAmount),
      paymentDifferenceNote: orderedForm.paymentDifferenceNote.trim() || null
    }, actionKey)
    actionKey = null
    const firstCost = result.lines?.find((line) => line.effectiveUnitCost != null)
    await afterSuccess(`已登记下单，version ${result.version}${firstCost ? `，首行 effectiveUnitCost ${formatUnitPrice(firstCost.effectiveUnitCost)}` : ''}`)
  } catch (error) {
    handleActionError(error)
  } finally {
    actionLoading.value = ''
  }
}

async function submitCancel() {
  if (actionLoading.value) return
  const reason = cancelReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写取消原因')
    return
  }
  const action = cancelType.value === 'remaining' ? 'cancel-remaining' : 'cancel'
  if (!actionKey) actionKey = createIdempotencyKey(`w45-${action}`)
  actionLoading.value = action
  try {
    const result = cancelType.value === 'remaining'
      ? await cancelRemaining(orderId.value, { version: order.value.version, reason }, actionKey)
      : await cancelOrder(orderId.value, { version: order.value.version, reason }, actionKey)
    actionKey = null
    await afterSuccess(`已${cancelType.value === 'remaining' ? '取消剩余' : '取消整单'}，状态 ${orderStatusMeta(result.status).label}，version ${result.version}`)
  } catch (error) {
    handleActionError(error)
  } finally {
    actionLoading.value = ''
  }
}

async function submitComplete() {
  if (actionLoading.value) return
  if (!actionKey) actionKey = createIdempotencyKey('w45-complete')
  actionLoading.value = 'complete'
  try {
    const result = await completeOrder(orderId.value, { version: order.value.version }, actionKey)
    actionKey = null
    await afterSuccess(`已完成，version ${result.version}，completedAt ${formatDateTime(result.completedAt)}`)
  } catch (error) {
    handleActionError(error)
  } finally {
    actionLoading.value = ''
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.procurement-detail-page { display: flex; flex-direction: column; gap: 16px; }
.detail-hero { display: flex; flex-direction: column; }
.detail-hero__body { display: flex; flex-direction: column; gap: 16px; }
.hero-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.hero-tags { display: flex; gap: 8px; flex-wrap: wrap; }
.hero-meta { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; margin: 0; }
.hero-meta > div { display: flex; flex-direction: column; gap: 4px; }
.hero-meta dt { font-size: 12px; color: var(--text-secondary, #909399); }
.hero-meta dd { margin: 0; font-weight: 600; }
.amount-strong { color: var(--primary-color, #409eff); }
.hero-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.order-section { display: flex; flex-direction: column; gap: 14px; }
.section-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.approval-summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; }
.approval-summary > div { display: flex; flex-direction: column; gap: 6px; }
.tag-row { display: flex; gap: 6px; flex-wrap: wrap; }
.facts-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }
.facts-grid > div { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
.line-name { display: flex; flex-direction: column; gap: 2px; }
.quantity-cell { display: flex; flex-direction: column; gap: 2px; font-variant-numeric: tabular-nums; }
.amount-cell { display: flex; flex-direction: column; gap: 2px; font-variant-numeric: tabular-nums; }
.text-danger { color: var(--danger-color, #f56c6c); }
.amount-summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; border-top: 1px solid var(--border-color, #ebeef5); padding-top: 12px; }
.amount-summary > div { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
.amount-summary__payable { color: var(--primary-color, #409eff); }
.timeline { display: flex; flex-direction: column; gap: 12px; }
.timeline-item { display: flex; align-items: flex-start; gap: 10px; }
.timeline-item__body { display: flex; flex-direction: column; gap: 2px; font-size: 13px; }
.timeline-item__body p { margin: 0; }
.timeline-item__comment { color: var(--text-primary, #303133); }
.receipt-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
.receipt-card { border: 1px solid var(--border-color, #ebeef5); border-radius: 8px; padding: 12px; display: flex; flex-direction: column; gap: 8px; }
.receipt-card__top { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.receipt-card__actions { display: flex; justify-content: flex-end; }
.receipts-loading { padding: 8px 0; }
.dialog-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; }
.form-field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--text-secondary, #606266); }
.form-field--wide { grid-column: span 2; }
.inline-field { display: flex; align-items: center; gap: 8px; }
.mobile-only { display: none; }
.line-mobile-list { display: flex; flex-direction: column; gap: 12px; }
.line-mobile-card { border: 1px solid var(--border-color, #ebeef5); border-radius: 8px; padding: 12px; display: flex; flex-direction: column; gap: 8px; }
.line-mobile-card__top { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.line-mobile-card__amount { display: flex; justify-content: space-between; gap: 8px; }
@media (max-width: 767px) {
  .desktop-only { display: none; }
  .mobile-only { display: flex; }
  .form-field--wide { grid-column: span 1; }
  .hero-actions { width: 100%; }
}
</style>
