<template>
  <div class="receipt-detail-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="backToOrder">返回采购单</el-button>
      <span class="text-help muted">{{ receipt?.receiptNo || '到货记录' }}</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canAccessProcurement()"
      state="permission"
      variant="section"
      description="当前账号无权访问到货记录"
      sub-title="请使用实验室成员、库存管理员、老师或系统管理员账号登录"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载到货记录"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="到货记录加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="loadAll"
    />

    <RegionState
      v-else-if="notFound"
      state="permission"
      variant="section"
      description="到货记录不存在或当前账号不可见"
      sub-title="不可见资源统一返回 404，不泄露单据、附件、数量或批号是否存在"
    />

    <template v-else-if="receipt && order">
      <section class="detail-hero data-panel">
        <div class="hero-title-row">
          <div>
            <p class="text-help muted">
              <el-button text type="primary" class="link-button" @click="backToOrder">{{ order.purchaseNo }}</el-button>
              · {{ order.title }}
            </p>
            <h1 class="page-title">{{ receipt.receiptNo }}</h1>
            <p class="text-help muted">
              到货 {{ formatDateTime(receipt.receivedAt) }} ·
              <span class="version-text">receiptVersion {{ receipt.version }}</span> ·
              <span class="version-text">orderVersion {{ order.version }}</span>
            </p>
          </div>
          <div class="hero-tags">
            <el-tag :type="receiptStatusMeta(receipt.status).tagType" effect="dark">
              {{ receiptStatusMeta(receipt.status).label }}
            </el-tag>
            <OrderStatusTag :status="order.status" effect="plain" size="small" />
          </div>
        </div>

        <dl class="hero-meta">
          <div>
            <dt>确认人</dt>
            <dd>{{ receipt.receiverName || '—' }}</dd>
          </div>
          <div>
            <dt>确认时间</dt>
            <dd>{{ formatDateTime(receipt.confirmedAt) }}</dd>
          </div>
          <div>
            <dt>取消时间</dt>
            <dd>{{ formatDateTime(receipt.canceledAt) }}</dd>
          </div>
          <div>
            <dt>采购单应付</dt>
            <dd class="amount-strong">{{ formatAmount(order.payableAmount) }}</dd>
          </div>
          <div>
            <dt>创建人 / 时间</dt>
            <dd>{{ receipt.createdBy ?? '—' }} · {{ formatDateTime(receipt.createTime) }}</dd>
          </div>
        </dl>

        <div class="hero-actions">
          <el-button v-if="canEditDraft" type="primary" :loading="saving" :disabled="attachmentBusy" @click="saveDraft">
            保存草稿修改
          </el-button>
          <el-button v-if="canEditDraft" type="success" :disabled="saving || attachmentBusy" @click="openConfirm">
            确认到货入库
          </el-button>
          <el-button v-if="canEditDraft" type="warning" plain :disabled="saving || attachmentBusy" @click="openCancel">
            取消草稿
          </el-button>
          <el-button plain @click="backToOrder">查看采购单</el-button>
        </div>

        <el-alert
          v-if="receipt.status === RECEIPT_STATUS.CONFIRMED"
          type="success"
          :closable="false"
          show-icon
          title="该到货已确认：批次、流水、余额与采购明细到货数量已原子写入，记录不可编辑或删除"
        />
        <el-alert
          v-else-if="receipt.status === RECEIPT_STATUS.CANCELED"
          type="info"
          :closable="false"
          show-icon
          title="该到货草稿已取消，无库存变化；如需到货请重新创建草稿"
        />
        <el-alert
          v-else-if="!canEditDraft"
          type="info"
          :closable="false"
          show-icon
          title="当前账号可查看到货记录，但只有库存管理员或系统管理员可以编辑/确认/取消草稿"
        />
      </section>

      <el-alert
        v-if="conflictInfo"
        type="warning"
        :closable="true"
        show-icon
        :title="`检测到 version 冲突：服务端 currentVersion=${conflictInfo.currentVersion ?? '未知'}。未自动覆盖，请刷新比较后重试。`"
        @close="conflictInfo = null"
      >
        <el-button text type="primary" @click="loadAll">刷新并比较</el-button>
      </el-alert>

      <el-alert
        v-if="transactionFailed"
        type="error"
        :closable="false"
        show-icon
        title="入库事务失败，未产生批次、流水或数量变化"
        description="到货记录保持 DRAFT，未局部入库；请修正后重新确认，本次未显示任何成功批次。"
      >
        <el-button text type="primary" @click="openConfirm">重新确认</el-button>
      </el-alert>

      <el-alert
        v-if="overQuantityInfo"
        type="error"
        :closable="true"
        show-icon
        :title="overQuantityTitle"
        description="超量请求已被服务端拒绝，未改写任何库存事实；请按最新 remainingQuantity 修正草稿后重试。"
        @close="overQuantityInfo = null"
      >
        <el-button text type="primary" @click="refreshOrderFacts">刷新剩余数量</el-button>
      </el-alert>

      <section v-if="confirmResult" class="data-panel order-section">
        <header class="section-header">
          <div>
            <h2 class="section-title">确认结果</h2>
            <p class="text-help muted">本次确认生成的批次与流水；库存变化来自 balanceBefore → balanceAfter。</p>
          </div>
          <el-tag type="success" effect="plain">订单 {{ orderStatusMeta(confirmResult.orderStatus).label }}</el-tag>
        </header>
        <div class="confirm-grid">
          <article v-for="batch in confirmResult.batches" :key="batch.batchId" class="confirm-card">
            <strong>{{ batch.batchNo }}</strong>
            <p class="text-help muted">batchId {{ batch.batchId }} · component {{ batch.componentId }}</p>
            <p>入库 {{ formatQuantityUnit(batch.quantity, batch.unit) }} · 单位成本 {{ formatUnitPrice(batch.unitCost) }}</p>
          </article>
        </div>
        <div class="movement-list">
          <div v-for="movement in confirmResult.movements" :key="movement.movementId" class="movement-item">
            <strong>{{ movement.movementNo }}</strong>
            <span>
              {{ formatQuantityUnit(movement.quantityDelta, '') }} · 库存
              {{ formatQuantityUnit(movement.balanceBefore, '') }} → {{ formatQuantityUnit(movement.balanceAfter, '') }}
            </span>
          </div>
        </div>
      </section>

      <section class="data-panel order-section">
        <header class="section-header">
          <div>
            <h2 class="section-title">到货明细</h2>
            <p class="text-help muted">
              {{ canEditDraft ? '草稿可直接修正数量/批号/日期/位置；同一采购明细只能出现一次。' : '已确认到货明细只读。' }}
            </p>
          </div>
          <el-tag type="info" effect="plain" size="small">{{ receipt.lines?.length || 0 }} 条</el-tag>
        </header>

        <ReceiptLinesEditor
          v-if="canEditDraft"
          v-model="form.lines"
          :order-lines="order.lines || []"
        />
        <template v-else>
          <el-table class="desktop-only" :data="receipt.lines || []" row-key="id" stripe>
            <el-table-column label="元器件" min-width="220">
              <template #default="{ row }">
                <strong>{{ lineOf(row).componentName || `component ${row.componentId}` }}</strong>
                <p class="text-help muted">{{ lineOf(row).mpn || '—' }} · 规格 v{{ lineOf(row).specVersion ?? '—' }}</p>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="140">
              <template #default="{ row }">{{ formatQuantityUnit(row.quantity, row.unit) }}</template>
            </el-table-column>
            <el-table-column label="供应商批号" min-width="120">
              <template #default="{ row }">{{ row.supplierLotNo || '—' }}</template>
            </el-table-column>
            <el-table-column label="生产 / 有效期" min-width="170">
              <template #default="{ row }">{{ row.productionDate || '—' }} / {{ row.expiryDate || '—' }}</template>
            </el-table-column>
            <el-table-column label="位置 / 质量备注" min-width="180">
              <template #default="{ row }">{{ row.locationNote || '—' }} · {{ row.qualityNote || '—' }}</template>
            </el-table-column>
          </el-table>

          <div class="mobile-only line-mobile-list">
            <article v-for="row in receipt.lines || []" :key="row.id" class="line-mobile-card">
              <div class="line-mobile-card__top">
                <strong>{{ lineOf(row).componentName || `component ${row.componentId}` }}</strong>
                <el-tag size="small" effect="plain">{{ row.unit }}</el-tag>
              </div>
              <p class="text-help muted">{{ lineOf(row).mpn || '—' }}</p>
              <p>数量 {{ formatQuantityUnit(row.quantity, row.unit) }} · 批号 {{ row.supplierLotNo || '—' }}</p>
              <p class="text-help muted">生产 {{ row.productionDate || '—' }} / 到期 {{ row.expiryDate || '—' }}</p>
              <p class="text-help muted">位置 {{ row.locationNote || '—' }} · {{ row.qualityNote || '—' }}</p>
            </article>
          </div>
        </template>

        <div v-if="canEditDraft" class="form-grid">
          <label class="form-field">
            <span>到货时间 *</span>
            <el-date-picker
              v-model="form.receivedAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="选择实际到货时间"
              style="width: 100%"
            />
          </label>
          <label class="form-field form-field--wide">
            <span>备注（≤1000 字）</span>
            <el-input v-model="form.note" type="textarea" :rows="2" maxlength="1000" show-word-limit />
          </label>
        </div>
        <p v-else-if="receipt.note" class="text-help muted">备注：{{ receipt.note }}</p>
      </section>

      <section v-if="receipt.status === RECEIPT_STATUS.CONFIRMED" class="data-panel order-section">
        <h2 class="section-title">库存事实入口</h2>
        <p class="text-help muted">
          本条到货已按“一条到货明细一个批次”写入。批次、流水与余额为不可变事实，可从元器件库存查看。
        </p>
        <div class="component-links">
          <el-button
            v-for="row in distinctComponents"
            :key="row.componentId"
            text
            type="primary"
            @click="router.push(`/components/${row.componentId}`)"
          >
            {{ row.componentName || `组件 ${row.componentId}` }}
          </el-button>
        </div>
      </section>

      <AttachmentPanel
        v-if="canViewAttachments(order)"
        owner-type="PROCUREMENT_RECEIPT"
        :owner-id="receipt.id"
        :owner-version="receipt.version"
        :can-upload="canUploadReceiptFiles"
        can-view
        title="到货 / 包装照片"
        @owner-version="onOwnerVersion"
        @busy="attachmentBusy = $event"
        @refresh="loadAll"
      />

      <el-dialog v-model="confirmVisible" title="确认到货入库" width="min(560px, 92vw)">
        <p>确认后将在同一事务内写入批次、流水、库存余额与采购明细到货数量，并递增订单 version。</p>
        <p class="text-help muted">
          当前 receiptVersion {{ receipt?.version }} · orderVersion {{ order?.version }}；提交时必须同时匹配，冲突会被拒绝。
        </p>
        <template #footer>
          <el-button @click="confirmVisible = false">取消</el-button>
          <el-button type="success" :loading="confirming" @click="doConfirm">确认并入库</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="cancelVisible" title="取消到货草稿" width="min(480px, 92vw)">
        <p>取消只会改变草稿状态，不产生批次、流水或库存变化；操作会递增 receiptVersion 与 orderVersion。</p>
        <template #footer>
          <el-button @click="cancelVisible = false">保留草稿</el-button>
          <el-button type="warning" :loading="canceling" @click="doCancel">确认取消草稿</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MockStateBanner from '@/components/MockStateBanner.vue'
import RegionState from '@/components/RegionState.vue'
import OrderStatusTag from '@/components/procurement/OrderStatusTag.vue'
import ReceiptLinesEditor from '@/components/procurement/ReceiptLinesEditor.vue'
import AttachmentPanel from '@/components/procurement/AttachmentPanel.vue'
import {
  ORDER_STATUS,
  RECEIPT_STATUS,
  canAccessProcurement,
  canConfirmReceipt,
  canUploadReceiptAttachment,
  canViewAttachments,
  cancelReceipt,
  confirmReceipt,
  formatAmount,
  formatQuantityUnit,
  formatUnitPrice,
  getOrder,
  getReceipt,
  orderStatusMeta,
  receiptStatusMeta,
  updateReceipt
} from '@/api/procurement'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'
import {
  isProcurementDenied,
  isProcurementIdempotencyReused,
  isProcurementOrderNotFound,
  isProcurementTransactionFailed,
  isProcurementVersionConflict,
  isReceiptAlreadyConfirmed,
  isReceiptOverQuantity,
  normalizeApiError
} from '@/utils/apiError'

const route = useRoute()
const router = useRouter()
const receiptId = computed(() => route.params.id)

const receipt = ref(null)
const order = ref(null)
const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const notFound = ref(false)
const saving = ref(false)
const confirming = ref(false)
const canceling = ref(false)
const attachmentBusy = ref(false)
const conflictInfo = ref(null)
const overQuantityInfo = ref(null)
const transactionFailed = ref(false)
const confirmResult = ref(null)
const confirmVisible = ref(false)
const cancelVisible = ref(false)

const form = reactive({ receivedAt: '', note: '', lines: [] })

let confirmKey = null
let confirmDigest = ''
let cancelKey = null
let cancelDigest = ''

const canEditDraft = computed(() => Boolean(receipt.value)
  && receipt.value.status === RECEIPT_STATUS.DRAFT
  && canConfirmReceipt()
  && [ORDER_STATUS.ORDERED, ORDER_STATUS.PARTIALLY_RECEIVED].includes(order.value?.status))

const canUploadReceiptFiles = computed(() => Boolean(order.value) && (
  canUploadReceiptAttachment(order.value, 'RECEIPT_PHOTO')
  || canUploadReceiptAttachment(order.value, 'PACKAGE_PHOTO')
))

const distinctComponents = computed(() => {
  const seen = new Set()
  return (receipt.value?.lines || []).reduce((acc, line) => {
    if (seen.has(line.componentId)) return acc
    seen.add(line.componentId)
    const orderLine = lineOf(line)
    acc.push({ componentId: line.componentId, componentName: orderLine.componentName })
    return acc
  }, [])
})

const overQuantityTitle = computed(() => {
  if (!overQuantityInfo.value) return ''
  const line = (order.value?.lines || []).find((item) => item.id === overQuantityInfo.value.orderLineId)
  return `明细 ${line?.componentName || overQuantityInfo.value.orderLineId} 超过剩余可收数量（请求 ${formatQuantityUnit(overQuantityInfo.value.requestedQuantity, line?.unit)} / 剩余 ${formatQuantityUnit(overQuantityInfo.value.remainingQuantity, line?.unit)}）`
})

function pad(value) {
  return String(value).padStart(2, '0')
}

function localDateTimeValue(date = new Date()) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/** 服务端可能返回带时区偏移的时间，编辑控件只接受本地 datetime 字符串 */
function toLocalInput(value) {
  if (!value) return localDateTimeValue()
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).slice(0, 19)
  return localDateTimeValue(date)
}

function isoWithOffset(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const offsetMinutes = -date.getTimezoneOffset()
  const sign = offsetMinutes >= 0 ? '+' : '-'
  const abs = Math.abs(offsetMinutes)
  return `${localDateTimeValue(date)}${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
}

function lineOf(row) {
  return (order.value?.lines || []).find((line) => line.id === Number(row.orderLineId)) || {}
}

function fillForm() {
  form.receivedAt = toLocalInput(receipt.value?.receivedAt)
  form.note = receipt.value?.note || ''
  form.lines = (receipt.value?.lines || []).map((line) => ({
    orderLineId: line.orderLineId,
    quantity: Number(line.quantity),
    supplierLotNo: line.supplierLotNo || '',
    productionDate: line.productionDate || null,
    expiryDate: line.expiryDate || null,
    locationNote: line.locationNote || '',
    qualityNote: line.qualityNote || ''
  }))
}

async function loadAll() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  notFound.value = false
  conflictInfo.value = null
  try {
    receipt.value = await getReceipt(receiptId.value)
    order.value = await getOrder(receipt.value.orderId)
    fillForm()
  } catch (error) {
    if (isProcurementOrderNotFound(error)) {
      notFound.value = true
      receipt.value = null
      order.value = null
    } else if (isProcurementDenied(error)) {
      loadError.value = '当前账号无权访问该到货记录'
    } else {
      const normalized = normalizeApiError(error)
      loadError.value = normalized.msg || '到货记录加载失败'
      loadTraceId.value = normalized.traceId || ''
    }
  } finally {
    loading.value = false
  }
}

async function refreshOrderFacts() {
  try {
    order.value = await getOrder(receipt.value.orderId)
    overQuantityInfo.value = null
    ElMessage.success(`已刷新最新剩余数量，orderVersion ${order.value.version}`)
  } catch (error) {
    handleWriteError(error)
  }
}

function payloadOf() {
  return {
    version: Number(receipt.value.version),
    orderVersion: Number(order.value.version),
    receivedAt: isoWithOffset(form.receivedAt),
    note: form.note.trim() || null,
    lines: form.lines.map((line) => ({
      orderLineId: Number(line.orderLineId),
      quantity: Number(line.quantity),
      supplierLotNo: line.supplierLotNo?.trim() || null,
      productionDate: line.productionDate || null,
      expiryDate: line.expiryDate || null,
      locationNote: line.locationNote?.trim() || null,
      qualityNote: line.qualityNote?.trim() || null
    }))
  }
}

function validateDraft() {
  if (!form.receivedAt) {
    ElMessage.warning('请填写到货时间')
    return false
  }
  if (!form.lines.length) {
    ElMessage.warning('至少保留一条到货明细')
    return false
  }
  for (const line of form.lines) {
    if (Number(line.quantity) <= 0) {
      ElMessage.warning('到货数量必须大于 0')
      return false
    }
    if (line.productionDate && line.expiryDate && line.expiryDate <= line.productionDate) {
      ElMessage.warning('有效期必须晚于生产日期')
      return false
    }
  }
  return true
}

async function saveDraft() {
  if (saving.value) return
  if (!validateDraft()) return
  saving.value = true
  transactionFailed.value = false
  try {
    const result = await updateReceipt(receipt.value.id, payloadOf())
    receipt.value.version = Number(result.version)
    if (order.value) order.value.version = Number(result.orderVersion)
    ElMessage.success(`到货草稿已保存，receiptVersion ${result.version}，orderVersion ${result.orderVersion}`)
    await loadAll()
  } catch (error) {
    handleWriteError(error)
  } finally {
    saving.value = false
  }
}

function openConfirm() {
  conflictInfo.value = null
  confirmVisible.value = true
}

function openCancel() {
  conflictInfo.value = null
  cancelVisible.value = true
}

async function doConfirm() {
  if (confirming.value) return
  const payload = {
    receiptVersion: Number(receipt.value.version),
    orderVersion: Number(order.value.version)
  }
  const digest = JSON.stringify(payload)
  if (confirmKey && confirmDigest !== digest) confirmKey = null
  if (!confirmKey) {
    confirmKey = createIdempotencyKey('w45-confirm-receipt')
    confirmDigest = digest
  }
  confirming.value = true
  transactionFailed.value = false
  overQuantityInfo.value = null
  confirmResult.value = null
  try {
    const result = await confirmReceipt(receipt.value.id, payload, confirmKey)
    confirmKey = null
    confirmVisible.value = false
    confirmResult.value = result
    ElMessage.success(`到货已确认并入库，订单 ${orderStatusMeta(result.orderStatus).label}，orderVersion ${result.orderVersion}`)
    await loadAll()
    confirmResult.value = result
  } catch (error) {
    handleWriteError(error)
  } finally {
    confirming.value = false
  }
}

async function doCancel() {
  if (canceling.value) return
  const payload = {
    receiptVersion: Number(receipt.value.version),
    orderVersion: Number(order.value.version)
  }
  const digest = JSON.stringify(payload)
  if (cancelKey && cancelDigest !== digest) cancelKey = null
  if (!cancelKey) {
    cancelKey = createIdempotencyKey('w45-cancel-receipt')
    cancelDigest = digest
  }
  canceling.value = true
  try {
    const result = await cancelReceipt(receipt.value.id, payload, cancelKey)
    cancelKey = null
    cancelVisible.value = false
    ElMessage.success(`草稿已取消，receiptVersion ${result.version}，orderVersion ${result.orderVersion}`)
    await loadAll()
  } catch (error) {
    handleWriteError(error)
  } finally {
    canceling.value = false
  }
}

function handleWriteError(error) {
  if (isProcurementVersionConflict(error)) {
    const normalized = normalizeApiError(error)
    conflictInfo.value = { currentVersion: normalized.data?.currentVersion ?? null }
    ElMessage.warning(`版本冲突：currentVersion=${conflictInfo.value.currentVersion ?? '未知'}；未自动覆盖`)
    return
  }
  if (isReceiptOverQuantity(error)) {
    const normalized = normalizeApiError(error)
    overQuantityInfo.value = normalized.data || null
    ElMessage.error('到货数量超过剩余可收数量，未改写库存事实')
    return
  }
  if (isProcurementTransactionFailed(error)) {
    transactionFailed.value = true
    confirmResult.value = null
    ElMessage.error('入库事务失败：未产生批次、流水或数量变化，可修正后重试')
    return
  }
  if (isReceiptAlreadyConfirmed(error)) {
    const normalized = normalizeApiError(error)
    ElMessage.warning(`到货记录已确认或已取消（${normalized.data?.receiptStatus || '未知'}），已刷新服务端状态`)
    loadAll()
    return
  }
  if (isProcurementDenied(error)) {
    ElMessage.error('仅库存管理员/管理员可编辑或确认到货')
    return
  }
  if (isProcurementIdempotencyReused(error)) {
    confirmKey = null
    cancelKey = null
    ElMessage.error('相同幂等键被用于不同请求体，请重新提交')
    return
  }
  const normalized = normalizeApiError(error)
  ElMessage.error(`${normalized.msg || '操作失败'}${normalized.traceId ? ` · traceId ${normalized.traceId}` : ''}`)
}

function onOwnerVersion(version) {
  if (!receipt.value) return
  receipt.value.version = Number(version)
}

function backToOrder() {
  router.push(receipt.value?.orderId ? `/procurements/${receipt.value.orderId}` : '/procurements')
}

onMounted(loadAll)
</script>

<style scoped>
.receipt-detail-page { display: flex; flex-direction: column; gap: 16px; }
.detail-hero { display: flex; flex-direction: column; gap: 16px; }
.hero-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.hero-tags { display: flex; gap: 8px; flex-wrap: wrap; }
.hero-meta { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; margin: 0; }
.hero-meta > div { display: flex; flex-direction: column; gap: 4px; }
.hero-meta dt { font-size: 12px; color: var(--text-secondary, #909399); }
.hero-meta dd { margin: 0; font-weight: 600; }
.amount-strong { color: var(--primary-color, #409eff); }
.hero-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.link-button { padding: 0; font-weight: 600; }
.version-text { font-variant-numeric: tabular-nums; }
.order-section { display: flex; flex-direction: column; gap: 14px; }
.section-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
.form-field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--text-secondary, #606266); }
.form-field--wide { grid-column: span 2; }
.confirm-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
.confirm-card { border: 1px solid var(--border-color, #ebeef5); border-radius: 8px; padding: 12px; display: flex; flex-direction: column; gap: 6px; font-size: 13px; }
.confirm-card p { margin: 0; }
.movement-list { display: flex; flex-direction: column; gap: 8px; font-size: 13px; }
.movement-item { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.component-links { display: flex; gap: 8px; flex-wrap: wrap; }
.mobile-only { display: none; }
.line-mobile-list { display: flex; flex-direction: column; gap: 12px; }
.line-mobile-card { border: 1px solid var(--border-color, #ebeef5); border-radius: 8px; padding: 12px; display: flex; flex-direction: column; gap: 8px; }
.line-mobile-card__top { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
@media (max-width: 767px) {
  .desktop-only { display: none; }
  .mobile-only { display: flex; }
  .form-field--wide { grid-column: span 1; }
  .hero-actions { width: 100%; }
}
</style>
