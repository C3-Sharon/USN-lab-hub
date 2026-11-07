<template>
  <div class="procurement-editor-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="back">返回采购列表</el-button>
      <span class="text-help muted">{{ isEdit ? `编辑草稿 ${order?.purchaseNo || ''}` : '新建采购单' }}</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canAccessProcurement()"
      state="permission"
      variant="section"
      description="当前账号无权创建采购单"
      sub-title="采购申请面向所有实验室成员；如需查看全部单据请联系管理员"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载采购单草稿"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="采购单草稿加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="initialize"
    />

    <RegionState
      v-else-if="isEdit && order && order.status !== ORDER_STATUS.DRAFT"
      state="permission"
      variant="section"
      :description="`当前状态为 ${orderStatusMeta(order.status).label}，只有 DRAFT 草稿可编辑`"
      sub-title="已提交或已终态的单据为只读事实；如需重办请复制为新草稿"
    />

    <RegionState
      v-else-if="isEdit && !canEdit"
      state="permission"
      variant="section"
      description="当前账号不能编辑该草稿"
      sub-title="仅申请人、库存管理员或系统管理员可编辑 DRAFT"
    />

    <template v-else>
      <header class="page-header">
        <div>
          <h1 class="page-title">{{ isEdit ? '编辑采购单草稿' : '新建采购单' }}</h1>
          <p class="text-help muted">
            金额由服务端按明细小计 + 运费 - 优惠重算；页面展示仅为预估，不用于绕过审批。
          </p>
        </div>
        <div class="page-header__actions">
          <el-button @click="back">取消</el-button>
          <el-button :loading="saving" :disabled="saving || attachmentBusy" @click="save(false)">
            保存草稿
          </el-button>
          <el-button
            v-if="canSubmit"
            type="primary"
            :loading="submitting"
            :disabled="saving || submitting || attachmentBusy"
            @click="save(true)"
          >
            保存并提交
          </el-button>
        </div>
      </header>

      <el-alert
        v-if="attachmentBusy"
        type="info"
        :closable="false"
        show-icon
        title="附件上传中，保存与提交已暂时锁定，避免携带旧 version 并行写入"
        class="editor-alert"
      />

      <el-alert
        v-if="conflictVersion != null"
        type="warning"
        :closable="false"
        show-icon
        :title="`检测到 version 冲突：服务端 currentVersion=${conflictVersion}。表单内容已保留，请刷新草稿并人工比较后再提交。`"
      >
        <el-button text type="primary" @click="initialize">刷新草稿并比较</el-button>
      </el-alert>

      <section class="data-panel editor-section">
        <h2 class="section-title">基本信息</h2>
        <div class="form-grid">
          <label class="form-field form-field--wide">
            <span>标题 *</span>
            <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="如：气象站原型元器件采购" />
          </label>
          <label class="form-field">
            <span>关联项目</span>
            <el-select v-model="form.projectId" clearable placeholder="可选：仅本组成员可见项目" style="width: 100%">
              <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
            </el-select>
          </label>
          <label class="form-field">
            <span>经费来源</span>
            <el-input v-model="form.fundingSource" maxlength="120" placeholder="可选，用于审计" />
          </label>
          <label class="form-field">
            <span>运费（CNY）</span>
            <el-input-number v-model="form.shippingAmount" :min="0" :precision="2" :step="1" controls-position="right" style="width: 100%" />
          </label>
          <label class="form-field">
            <span>优惠（CNY）</span>
            <el-input-number v-model="form.discountAmount" :min="0" :precision="2" :step="1" controls-position="right" style="width: 100%" />
          </label>
        </div>
      </section>

      <section class="data-panel editor-section">
        <header class="section-header">
          <div>
            <h2 class="section-title">采购明细</h2>
            <p class="text-help muted">同一草稿内元器件不能重复；规格快照与单位由服务端提交时冻结。</p>
          </div>
          <el-button plain :icon="Plus" :disabled="saving" @click="addLine">添加明细</el-button>
        </header>

        <div v-if="!lines.length" class="empty-block">
          <el-empty description="还没有采购明细">
            <el-button type="primary" @click="addLine">添加第一条明细</el-button>
          </el-empty>
        </div>

        <div v-else class="line-stack">
          <article v-for="(line, index) in lines" :key="line.key" class="line-card">
            <header class="line-card__header">
              <div class="line-card__index">明细 {{ index + 1 }}</div>
              <el-button text type="danger" :icon="Delete" :disabled="saving" @click="removeLine(index)">
                移除
              </el-button>
            </header>
            <div class="line-card__grid">
              <label class="form-field line-card__component">
                <span>元器件 *</span>
                <el-select
                  v-model="line.componentId"
                  filterable
                  remote
                  clearable
                  :remote-method="searchComponents"
                  :loading="componentLoading"
                  placeholder="搜索名称、MPN 或制造商"
                  style="width: 100%"
                  @change="(value) => onComponentChange(line, value)"
                >
                  <el-option
                    v-for="component in componentOptions"
                    :key="component.id"
                    :label="`${component.name} · ${component.mpn}`"
                    :value="component.id"
                  />
                </el-select>
                <span v-if="line.componentId" class="text-help muted">
                  {{ line.manufacturerName || '—' }} · {{ line.categoryName || '—' }} · 规格 v{{ line.specVersion || '—' }} ·
                  单位 {{ line.unit || '—' }}
                </span>
              </label>
              <label class="form-field">
                <span>数量 *</span>
                <el-input-number
                  v-model="line.orderedQuantity"
                  :min="0.001"
                  :precision="3"
                  :step="1"
                  :disabled="saving"
                  controls-position="right"
                  style="width: 100%"
                />
              </label>
              <label class="form-field">
                <span>单价（CNY）*</span>
                <el-input-number
                  v-model="line.unitPrice"
                  :min="0"
                  :precision="4"
                  :step="0.1"
                  :disabled="saving"
                  controls-position="right"
                  style="width: 100%"
                />
              </label>
              <div class="form-field">
                <span>小计（预估）</span>
                <strong class="line-subtotal">{{ formatAmount(lineSubtotal(line)) }}</strong>
              </div>
            </div>
          </article>
        </div>

        <div class="amount-summary">
          <div>
            <span class="text-help muted">明细小计</span>
            <strong>{{ formatAmount(itemSubtotal) }}</strong>
          </div>
          <div>
            <span class="text-help muted">+ 运费</span>
            <strong>{{ formatAmount(Number(form.shippingAmount || 0)) }}</strong>
          </div>
          <div>
            <span class="text-help muted">- 优惠</span>
            <strong>{{ formatAmount(Number(form.discountAmount || 0)) }}</strong>
          </div>
          <div class="amount-summary__payable">
            <span>应付金额（预估）</span>
            <strong>{{ formatAmount(estimatedPayable) }}</strong>
          </div>
        </div>
        <p class="text-help muted">
          应付金额达到审批阈值、任一明细单价达到贵重阈值、或命中指定分类时，提交后进入待审批；
          申请人不能审批自己的单据。
        </p>
      </section>

      <footer class="editor-footer">
        <el-button @click="back">取消</el-button>
        <el-button :loading="saving" :disabled="saving || attachmentBusy" @click="save(false)">保存草稿</el-button>
        <el-button
          v-if="canSubmit"
          type="primary"
          :loading="submitting"
          :disabled="saving || submitting || attachmentBusy"
          @click="save(true)"
        >
          保存并提交
        </el-button>
      </footer>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MockStateBanner from '@/components/MockStateBanner.vue'
import RegionState from '@/components/RegionState.vue'
import {
  ORDER_STATUS,
  canAccessProcurement,
  canCreateOrder,
  canEditOrder,
  canSubmitOrder,
  createOrder,
  formatAmount,
  getOrder,
  orderStatusMeta,
  searchPurchaseComponents,
  submitOrder,
  updateOrder
} from '@/api/procurement'
import { listProjects } from '@/api/projects'
import { createIdempotencyKey } from '@/utils/idempotency'
import {
  isProcurementDenied,
  isProcurementOrderNotFound,
  isProcurementVersionConflict,
  normalizeApiError
} from '@/utils/apiError'
import { normalizePage } from '@/api/procurement'

const route = useRoute()
const router = useRouter()

const orderId = computed(() => route.params.id || null)
const isEdit = computed(() => Boolean(orderId.value))

const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const order = ref(null)
const persistedOrder = ref(null)
const projects = ref([])
const componentOptions = ref([])
const componentLoading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const conflictVersion = ref(null)
const attachmentBusy = ref(false)

let lineKey = 0
let saveKey = null
let submitKey = null

const form = reactive({
  title: '',
  projectId: '',
  fundingSource: '',
  shippingAmount: 0,
  discountAmount: 0
})

const lines = ref([])

const currentOrder = computed(() => persistedOrder.value || order.value)
const canEdit = computed(() => (currentOrder.value ? canEditOrder(currentOrder.value) : canCreateOrder()))
const canSubmit = computed(() => {
  if (!canAccessProcurement()) return false
  if (!currentOrder.value) return canCreateOrder()
  return canSubmitOrder(currentOrder.value)
})

const itemSubtotal = computed(() => round(sum(lines.value.map(lineSubtotal)), 2))
const estimatedPayable = computed(() => {
  const value = itemSubtotal.value + Number(form.shippingAmount || 0) - Number(form.discountAmount || 0)
  return Math.max(0, round(value, 2))
})

function round(value, scale) {
  const factor = 10 ** scale
  const n = Number(value)
  if (!Number.isFinite(n)) return 0
  return Math.round((n + Number.EPSILON) * factor) / factor
}

function sum(values) {
  return values.reduce((acc, value) => acc + Number(value || 0), 0)
}

function lineSubtotal(line) {
  return round(Number(line.orderedQuantity || 0) * Number(line.unitPrice || 0), 2)
}

function newLine(patch = {}) {
  lineKey += 1
  return {
    key: `line-${lineKey}`,
    componentId: null,
    componentName: '',
    mpn: '',
    manufacturerName: '',
    categoryName: '',
    unit: '',
    specVersion: null,
    orderedQuantity: 1,
    unitPrice: 0,
    ...patch
  }
}

function addLine() {
  lines.value.push(newLine())
}

function removeLine(index) {
  lines.value.splice(index, 1)
}

function mergeComponentOptions(components) {
  const merged = [...componentOptions.value]
  components.forEach((component) => {
    if (component && !merged.some((item) => item.id === component.id)) merged.push(component)
  })
  componentOptions.value = merged
}

async function loadComponentOptions(keyword = '') {
  componentLoading.value = true
  try {
    const response = await searchPurchaseComponents({ keyword })
    mergeComponentOptions(normalizePage(response).list)
  } catch (error) {
    const normalized = normalizeApiError(error)
    ElMessage.error(`${normalized.msg || '元器件搜索失败'}${normalized.traceId ? ` · traceId ${normalized.traceId}` : ''}`)
  } finally {
    componentLoading.value = false
  }
}

function searchComponents(keyword) {
  loadComponentOptions(keyword)
}

function onComponentChange(line, componentId) {
  const component = componentOptions.value.find((item) => item.id === componentId)
  if (!component) {
    line.componentName = ''
    return
  }
  line.componentName = component.name
  line.mpn = component.mpn
  line.manufacturerName = component.manufacturerName
  line.categoryName = component.categoryName
  line.unit = component.baseUnit
  line.specVersion = component.specVersion
}

function fillFormFromOrder(value) {
  form.title = value.title || ''
  form.projectId = value.projectId ?? ''
  form.fundingSource = value.fundingSource || ''
  form.shippingAmount = Number(value.shippingAmount || 0)
  form.discountAmount = Number(value.discountAmount || 0)
  lines.value = (value.lines || []).map((line) => newLine({
    componentId: line.componentId,
    componentName: line.componentName,
    mpn: line.mpn,
    manufacturerName: line.manufacturerName,
    categoryName: line.categoryName,
    unit: line.unit,
    specVersion: line.specVersion,
    orderedQuantity: Number(line.orderedQuantity || 0),
    unitPrice: Number(line.unitPrice || 0)
  }))
  mergeComponentOptions((value.lines || []).map((line) => ({
    id: line.componentId,
    name: line.componentName,
    mpn: line.mpn,
    manufacturerName: line.manufacturerName,
    categoryName: line.categoryName,
    baseUnit: line.unit,
    specVersion: line.specVersion
  })))
}

async function initialize() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  conflictVersion.value = null
  try {
    if (isEdit.value) {
      const detail = await getOrder(orderId.value)
      order.value = detail
      persistedOrder.value = detail
      fillFormFromOrder(detail)
    }
  } catch (error) {
    if (isProcurementOrderNotFound(error)) {
      loadError.value = '采购单不存在或当前账号不可见'
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

function validate() {
  const title = form.title.trim()
  if (title.length < 2 || title.length > 120) {
    ElMessage.warning('标题需为 2-120 个字符')
    return false
  }
  if (!lines.value.length) {
    ElMessage.warning('至少需要一条采购明细')
    return false
  }
  const seen = new Set()
  for (let index = 0; index < lines.value.length; index += 1) {
    const line = lines.value[index]
    if (!line.componentId) {
      ElMessage.warning(`明细 ${index + 1} 请选择元器件`)
      return false
    }
    if (seen.has(line.componentId)) {
      ElMessage.warning('同一采购单内元器件不能重复')
      return false
    }
    seen.add(line.componentId)
    if (Number(line.orderedQuantity) <= 0) {
      ElMessage.warning(`明细 ${index + 1} 数量必须大于 0`)
      return false
    }
    if (Number(line.unitPrice) < 0) {
      ElMessage.warning(`明细 ${index + 1} 单价不能为负`)
      return false
    }
  }
  if (Number(form.shippingAmount || 0) < 0 || Number(form.discountAmount || 0) < 0) {
    ElMessage.warning('运费与优惠不能为负数')
    return false
  }
  if (estimatedPayable.value < 0) {
    ElMessage.warning('应付金额不能为负数')
    return false
  }
  return true
}

function payloadOf() {
  return {
    title: form.title.trim(),
    projectId: form.projectId || null,
    fundingSource: form.fundingSource.trim() || null,
    shippingAmount: Number(form.shippingAmount || 0),
    discountAmount: Number(form.discountAmount || 0),
    lines: lines.value.map((line) => ({
      componentId: line.componentId,
      orderedQuantity: Number(line.orderedQuantity),
      unitPrice: Number(line.unitPrice)
    }))
  }
}

async function save(andSubmit) {
  if (saving.value || submitting.value) return
  if (!validate()) return
  conflictVersion.value = null
  if (!saveKey) saveKey = createIdempotencyKey('w45-save-order')
  saving.value = true
  try {
    const payload = payloadOf()
    const activeOrder = currentOrder.value
    const saved = activeOrder
      ? await updateOrder(activeOrder.id, { ...payload, version: activeOrder.version })
      : await createOrder(payload, saveKey)
    saveKey = null
    persistedOrder.value = saved
    order.value = saved
    const savedId = saved?.id || activeOrder?.id || route.params.id
    if (andSubmit) {
      submitting.value = true
      if (!submitKey) submitKey = createIdempotencyKey('w45-submit-order')
      const submitted = await submitOrder(savedId, { version: saved.version }, submitKey)
      submitKey = null
      saving.value = false
      submitting.value = false
      ElMessage.success(`已提交，状态 ${orderStatusMeta(submitted.status).label}，version ${submitted.version}`)
      router.push(`/procurements/${savedId}`)
      return
    }
    ElMessage.success(`草稿已保存，version ${saved.version}`)
    router.push(`/procurements/${savedId}`)
  } catch (error) {
    if (isProcurementVersionConflict(error)) {
      const normalized = normalizeApiError(error)
      conflictVersion.value = normalized.data?.currentVersion ?? null
      ElMessage.warning(`版本冲突：currentVersion=${conflictVersion.value ?? '未知'}；表单内容已保留`)
    } else if (isProcurementDenied(error)) {
      ElMessage.error('当前账号无权执行此操作')
    } else {
      const normalized = normalizeApiError(error)
      ElMessage.error(`${normalized.msg || '保存失败'}${normalized.traceId ? ` · traceId ${normalized.traceId}` : ''}`)
    }
  } finally {
    saving.value = false
    submitting.value = false
  }
}

function back() {
  router.push('/procurements')
}

async function loadProjects() {
  try {
    const response = await listProjects({ page: 1, pageSize: 100 })
    projects.value = normalizePage(response).list
  } catch {
    projects.value = []
  }
}

onMounted(() => {
  loadProjects()
  loadComponentOptions('')
  initialize()
})
</script>

<style scoped>
.procurement-editor-page { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.page-header__actions { display: flex; gap: 8px; flex-wrap: wrap; }
.editor-alert { margin: 0; }
.editor-section { display: flex; flex-direction: column; gap: 14px; }
.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
.form-field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--text-secondary, #606266); }
.form-field--wide { grid-column: span 2; }
.section-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.line-stack { display: flex; flex-direction: column; gap: 12px; }
.line-card { border: 1px solid var(--border-color, #ebeef5); border-radius: 8px; padding: 14px; display: flex; flex-direction: column; gap: 12px; }
.line-card__header { display: flex; align-items: center; justify-content: space-between; }
.line-card__index { font-weight: 600; }
.line-card__grid { display: grid; grid-template-columns: minmax(260px, 2fr) repeat(3, minmax(150px, 1fr)); gap: 12px; }
.line-card__component { grid-column: span 1; }
.line-subtotal { font-variant-numeric: tabular-nums; color: var(--text-primary, #303133); }
.amount-summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; border-top: 1px solid var(--border-color, #ebeef5); padding-top: 12px; }
.amount-summary > div { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
.amount-summary__payable { color: var(--primary-color, #409eff); }
.empty-block { padding: 12px 0; }
.editor-footer { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; padding-top: 4px; }
@media (max-width: 1024px) {
  .line-card__grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 767px) {
  .form-field--wide { grid-column: span 1; }
  .line-card__grid { grid-template-columns: 1fr; }
  .page-header__actions, .editor-footer { width: 100%; justify-content: flex-start; }
}
</style>
