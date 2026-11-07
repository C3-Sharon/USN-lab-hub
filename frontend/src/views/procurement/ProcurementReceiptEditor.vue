<template>
  <div class="receipt-editor-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="backToOrder">返回到货记录</el-button>
      <span class="text-help muted">{{ order?.purchaseNo || '新建到货草稿' }}</span>
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
      description="正在加载采购单与可到货明细"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="采购单加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="initialize"
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
        <div class="hero-title-row">
          <div>
            <p class="text-help muted">{{ order.purchaseNo }} · 申请人 {{ order.requesterName || `用户 ${order.requesterId}` }}</p>
            <h1 class="page-title">新建到货草稿 · {{ order.title }}</h1>
            <p class="text-help muted">
              供应商 {{ order.supplierName || '—' }} · 平台 {{ order.platformType ? platformTypeMeta(order.platformType).label : '—' }} ·
              <span class="version-text">orderVersion {{ order.version }}</span>
            </p>
          </div>
          <div class="hero-tags">
            <OrderStatusTag :status="order.status" effect="dark" />
            <el-tag type="info" effect="plain" size="small">{{ formatAmount(order.payableAmount) }}</el-tag>
          </div>
        </div>

        <el-alert
          v-if="!canCreate"
          type="warning"
          :closable="false"
          show-icon
          :title="`当前采购单状态 ${orderStatusMeta(order.status).label} 或当前账号无权创建到货草稿`"
          description="只有 ORDERED / PARTIALLY_RECEIVED 状态，且库存管理员或系统管理员可以创建到货。"
        />

        <el-alert
          v-else-if="draftReceipt"
          type="warning"
          :closable="false"
          show-icon
          :title="`该采购单已存在草稿到货记录 ${draftReceipt.receiptNo}`"
          description="同一采购单同时最多一条 DRAFT；请先完成或取消该草稿。"
        >
          <el-button text type="primary" @click="router.push(`/procurement-receipts/${draftReceipt.id}`)">继续编辑草稿</el-button>
        </el-alert>

        <el-alert
          v-else-if="order.status === ORDER_STATUS.PARTIALLY_RECEIVED"
          type="info"
          :closable="false"
          show-icon
          description="部分到货：本次只需填写实际到货数量，剩余数量继续保留，可在后续批次继续到货。"
        />
      </section>

      <template v-if="canCreate && !draftReceipt">
        <section class="data-panel editor-section">
          <header class="section-header">
            <div>
              <h2 class="section-title">到货事实</h2>
              <p class="text-help muted">到货时间不得晚于当前时间 5 分钟；保存草稿会锁定采购单并递增 orderVersion。</p>
            </div>
          </header>

          <div class="form-grid">
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
              <el-input v-model="form.note" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="如第一箱到货、包装情况" />
            </label>
          </div>
        </section>

        <section class="data-panel editor-section">
          <header class="section-header">
            <div>
              <h2 class="section-title">到货明细</h2>
              <p class="text-help muted">
                明细小计 {{ formatAmount(order.itemSubtotal) }} · 应付 {{ formatAmount(order.payableAmount) }} ·
                数量最多 3 位小数并显示单位
              </p>
            </div>
            <el-tag type="info" effect="plain" size="small">{{ selectedLines.length }} 条本次到货</el-tag>
          </header>

          <ReceiptLinesEditor v-model="form.lines" :order-lines="order.lines || []" />

          <el-alert
            v-if="overQuantityInfo"
            type="error"
            :closable="false"
            show-icon
            :title="overQuantityTitle"
            description="草稿可保存，但确认到货时服务端会按最新 remainingQuantity 拒绝超量；不会被静默截断。"
          />
        </section>

        <div class="editor-footer">
          <el-button :disabled="saving" @click="backToOrder">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveDraft">保存到货草稿</el-button>
        </div>
      </template>
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
import {
  ORDER_STATUS,
  RECEIPT_STATUS,
  canAccessProcurement,
  canCreateReceipt,
  createReceipt,
  formatAmount,
  formatQuantityUnit,
  getOrder,
  lineRemainingQuantity,
  listReceipts,
  normalizePage,
  orderStatusMeta,
  platformTypeMeta
} from '@/api/procurement'
import { createIdempotencyKey } from '@/utils/idempotency'
import {
  isProcurementDenied,
  isProcurementIdempotencyReused,
  isProcurementInvalidTransition,
  isProcurementOrderNotFound,
  isProcurementVersionConflict,
  isReceiptDraftExists,
  isReceiptOverQuantity,
  normalizeApiError
} from '@/utils/apiError'

const route = useRoute()
const router = useRouter()
const orderId = computed(() => route.params.id)

const order = ref(null)
const draftReceipt = ref(null)
const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const notFound = ref(false)
const saving = ref(false)
const overQuantityInfo = ref(null)

const form = reactive({
  receivedAt: localDateTimeValue(),
  note: '',
  lines: []
})

let saveKey = null
let saveDigest = ''

const canCreate = computed(() => Boolean(order.value) && canCreateReceipt(order.value))
const selectedLines = computed(() => form.lines || [])
const overQuantityTitle = computed(() => {
  if (!overQuantityInfo.value) return ''
  const line = (order.value?.lines || []).find((item) => item.id === overQuantityInfo.value.orderLineId)
  const remaining = overQuantityInfo.value.remainingQuantity
  return `明细 ${line?.componentName || overQuantityInfo.value.orderLineId} 到货数量超过剩余可收 ${formatQuantityUnit(remaining ?? 0, line?.unit)}`
})

function pad(value) {
  return String(value).padStart(2, '0')
}

function localDateTimeValue(date = new Date()) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/** 发送带时区偏移的 ISO 时间，避免服务端把本地时间当 UTC */
function isoWithOffset(value) {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const offsetMinutes = -date.getTimezoneOffset()
  const sign = offsetMinutes >= 0 ? '+' : '-'
  const abs = Math.abs(offsetMinutes)
  return `${localDateTimeValue(date)}${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
}

async function initialize() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  notFound.value = false
  draftReceipt.value = null
  try {
    order.value = await getOrder(orderId.value)
    if (canCreateReceipt(order.value)) {
      await loadDraftReceipt()
    }
  } catch (error) {
    if (isProcurementOrderNotFound(error)) {
      notFound.value = true
      order.value = null
    } else if (isProcurementDenied(error)) {
      loadError.value = '当前账号无权访问该采购单'
    } else {
      const normalized = normalizeApiError(error)
      loadError.value = normalized.msg || '采购单加载失败'
      loadTraceId.value = normalized.traceId || ''
    }
  } finally {
    loading.value = false
  }
}

async function loadDraftReceipt() {
  try {
    const response = await listReceipts(orderId.value, { page: 1, pageSize: 50, status: RECEIPT_STATUS.DRAFT })
    draftReceipt.value = normalizePage(response).list[0] || null
  } catch {
    draftReceipt.value = null
  }
}

function payloadOf() {
  return {
    orderVersion: Number(order.value?.version),
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

function validate() {
  if (!form.receivedAt) {
    ElMessage.warning('请填写到货时间')
    return false
  }
  const receivedTime = Date.parse(form.receivedAt)
  if (receivedTime > Date.now() + 5 * 60 * 1000) {
    ElMessage.warning('到货时间不能晚于当前时间超过 5 分钟')
    return false
  }
  if (!form.lines.length) {
    ElMessage.warning('至少选择一条到货明细')
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
    const source = (order.value?.lines || []).find((item) => item.id === Number(line.orderLineId))
    if (source && Number(line.quantity) > lineRemainingQuantity(source)) {
      overQuantityInfo.value = {
        orderLineId: source.id,
        remainingQuantity: lineRemainingQuantity(source),
        requestedQuantity: Number(line.quantity)
      }
      ElMessage.warning('本次到货数量超过剩余可收数量；草稿可保存，确认时会被服务端拒绝')
    }
  }
  return true
}

async function saveDraft() {
  if (saving.value) return
  if (!validate()) return
  const payload = payloadOf()
  const digest = JSON.stringify(payload)
  if (saveKey && saveDigest !== digest) saveKey = null
  if (!saveKey) {
    saveKey = createIdempotencyKey('w45-create-receipt')
    saveDigest = digest
  }
  saving.value = true
  overQuantityInfo.value = null
  try {
    const result = await createReceipt(orderId.value, payload, saveKey)
    saveKey = null
    ElMessage.success(`到货草稿 ${result.receiptNo} 已保存，receiptVersion ${result.version}，orderVersion ${result.orderVersion}`)
    router.push(`/procurement-receipts/${result.receiptId}`)
  } catch (error) {
    handleError(error)
  } finally {
    saving.value = false
  }
}

function handleError(error) {
  if (isProcurementVersionConflict(error)) {
    const normalized = normalizeApiError(error)
    ElMessage.warning(`版本冲突：currentVersion=${normalized.data?.currentVersion ?? '未知'}；表单内容已保留`)
    initialize()
    return
  }
  if (isReceiptDraftExists(error)) {
    ElMessage.warning('该采购单已存在草稿到货记录，已跳转草稿')
    initialize()
    return
  }
  if (isReceiptOverQuantity(error)) {
    const normalized = normalizeApiError(error)
    overQuantityInfo.value = normalized.data || null
    ElMessage.error('到货数量超过剩余可收数量，请修正后重试')
    return
  }
  if (isProcurementInvalidTransition(error)) {
    ElMessage.error('当前采购单状态不允许创建到货')
    initialize()
    return
  }
  if (isProcurementDenied(error)) {
    ElMessage.error('仅库存管理员/管理员可创建到货记录')
    return
  }
  if (isProcurementIdempotencyReused(error)) {
    saveKey = null
    ElMessage.error('相同幂等键被用于不同请求体，请重新提交')
    return
  }
  const normalized = normalizeApiError(error)
  ElMessage.error(`${normalized.msg || '保存失败'}${normalized.traceId ? ` · traceId ${normalized.traceId}` : ''}`)
}

function backToOrder() {
  router.push(`/procurements/${orderId.value}`)
}

onMounted(initialize)
</script>

<style scoped>
.receipt-editor-page { display: flex; flex-direction: column; gap: 16px; }
.detail-hero { display: flex; flex-direction: column; gap: 14px; }
.hero-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.hero-tags { display: flex; gap: 8px; flex-wrap: wrap; }
.version-text { font-variant-numeric: tabular-nums; }
.editor-section { display: flex; flex-direction: column; gap: 14px; }
.section-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
.form-field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--text-secondary, #606266); }
.form-field--wide { grid-column: span 2; }
.editor-footer { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
@media (max-width: 767px) {
  .form-field--wide { grid-column: span 1; }
  .editor-footer { justify-content: flex-start; }
}
</style>
