<template>
  <div class="procurement-settings-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">采购审批策略</h1>
        <p class="text-help muted">
          策略按版本生效；修改会发布新版本，历史版本永久保留。金额使用 CNY，审批阈值 2 位小数、贵重单价阈值 4 位小数。
        </p>
      </div>
      <el-button text :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canUpdatePolicy()"
      state="permission"
      variant="section"
      description="只有系统管理员可以查看与修改采购审批策略"
      sub-title="老师、库存管理员与普通成员可使用采购功能，但不能修改审批阈值或指定分类"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载当前策略与版本历史"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="采购策略加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="loadAll"
    />

    <template v-else-if="policy">
      <el-alert
        v-if="conflictInfo"
        type="warning"
        :closable="true"
        show-icon
        :title="`检测到策略版本冲突：服务端 currentPolicyVersion=${conflictInfo.currentVersion ?? '未知'}。表单未自动覆盖。`"
        @close="conflictInfo = null"
      >
        <el-button text type="primary" @click="loadAll">刷新并比较</el-button>
      </el-alert>

      <section class="data-panel settings-section">
        <header class="section-header">
          <div>
            <h2 class="section-title">当前生效策略</h2>
            <p class="text-help muted">
              生效于 {{ formatDateTime(policy.effectiveAt) }} · 发布人 {{ policy.createdBy ?? '—' }} ·
              <span class="version-text">policyVersion {{ policy.policyVersion }}</span>
            </p>
          </div>
          <el-tag type="success" effect="plain">CNY</el-tag>
        </header>

        <div class="policy-summary">
          <div>
            <span class="text-help muted">审批阈值</span>
            <strong>{{ formatAmount(policy.approvalThreshold) }}</strong>
            <span class="text-help muted">应付金额达到或超过即需人工审批</span>
          </div>
          <div>
            <span class="text-help muted">贵重单价阈值</span>
            <strong>{{ formatUnitPrice(policy.valuableUnitPriceThreshold) }}</strong>
            <span class="text-help muted">任一明细单价达到或超过即需人工审批</span>
          </div>
          <div>
            <span class="text-help muted">指定强制审批分类</span>
            <strong>{{ (policy.forcedCategoryIds || []).length }} 个</strong>
            <span class="text-help muted">{{ forcedCategoryText(policy.forcedCategoryIds) }}</span>
          </div>
        </div>
      </section>

      <section class="data-panel settings-section">
        <header class="section-header">
          <div>
            <h2 class="section-title">发布新策略版本</h2>
            <p class="text-help muted">提交会携带 currentPolicyVersion={{ policy.policyVersion }}；成功后新版本立即生效。</p>
          </div>
        </header>

        <div class="form-grid">
          <label class="form-field">
            <span>审批阈值（CNY，&gt; 0）*</span>
            <el-input-number
              v-model="form.approvalThreshold"
              :min="0.01"
              :precision="2"
              :step="1"
              controls-position="right"
              style="width: 100%"
            />
            <span class="text-help muted">默认 500.00；金额达到阈值触发人工审批</span>
          </label>
          <label class="form-field">
            <span>贵重单价阈值（CNY，&gt; 0）*</span>
            <el-input-number
              v-model="form.valuableUnitPriceThreshold"
              :min="0.0001"
              :precision="4"
              :step="1"
              controls-position="right"
              style="width: 100%"
            />
            <span class="text-help muted">默认 1000.0000；任一明细单价达到即触发</span>
          </label>
          <label class="form-field form-field--wide">
            <span>指定强制审批分类</span>
            <el-select
              v-model="form.forcedCategoryIds"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              placeholder="选择需要强制审批的启用分类"
              style="width: 100%"
            >
              <el-option
                v-for="option in categoryOptions"
                :key="option.id"
                :label="option.label"
                :value="option.id"
              />
            </el-select>
            <span class="text-help muted">只接受启用分类；空表示不按分类强制审批</span>
          </label>
        </div>

        <div class="change-summary">
          <span class="text-help muted">变更预览：</span>
          审批阈值 {{ formatAmount(policy.approvalThreshold) }} → {{ formatAmount(form.approvalThreshold) }} ·
          贵重单价 {{ formatUnitPrice(policy.valuableUnitPriceThreshold) }} → {{ formatUnitPrice(form.valuableUnitPriceThreshold) }} ·
          指定分类 {{ (policy.forcedCategoryIds || []).length }} → {{ form.forcedCategoryIds.length }} 个
        </div>

        <div class="settings-footer">
          <el-button :disabled="saving" @click="resetForm">重置为当前策略</el-button>
          <el-button type="primary" :loading="saving" @click="publish">发布新策略版本</el-button>
        </div>
      </section>

      <section class="data-panel settings-section">
        <header class="section-header">
          <div>
            <h2 class="section-title">策略版本历史</h2>
            <p class="text-help muted">历史版本只读；已提交采购单继续绑定提交时的 policyVersion。</p>
          </div>
          <el-tag type="info" effect="plain" size="small">{{ versionsTotal }} 个版本</el-tag>
        </header>

        <RegionState
          v-if="versionsLoading"
          state="loading"
          variant="section"
          description="正在加载策略版本历史"
        />
        <RegionState
          v-else-if="versionsError"
          state="error"
          variant="section"
          description="策略版本历史加载失败"
          :error-message="versionsError"
          @retry="loadVersions"
        />
        <RegionState
          v-else-if="!versions.length"
          state="empty"
          variant="section"
          description="暂无策略版本历史"
        />

        <template v-else>
          <el-table class="desktop-only" :data="versions" row-key="policyVersion" stripe>
            <el-table-column label="版本" width="90">
              <template #default="{ row }"><strong>v{{ row.policyVersion }}</strong></template>
            </el-table-column>
            <el-table-column label="审批阈值" width="140" align="right">
              <template #default="{ row }">{{ formatAmount(row.approvalThreshold) }}</template>
            </el-table-column>
            <el-table-column label="贵重单价阈值" width="160" align="right">
              <template #default="{ row }">{{ formatUnitPrice(row.valuableUnitPriceThreshold) }}</template>
            </el-table-column>
            <el-table-column label="指定分类" min-width="200">
              <template #default="{ row }">{{ forcedCategoryText(row.forcedCategoryIds) }}</template>
            </el-table-column>
            <el-table-column label="生效时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.effectiveAt) }}</template>
            </el-table-column>
            <el-table-column label="发布人 / 时间" min-width="200">
              <template #default="{ row }">
                {{ row.createdBy ?? '—' }} · {{ formatDateTime(row.createTime) }}
              </template>
            </el-table-column>
          </el-table>

          <div class="mobile-only version-cards">
            <article v-for="row in versions" :key="row.policyVersion" class="version-card">
              <div class="version-card__top">
                <strong>v{{ row.policyVersion }}</strong>
                <el-tag v-if="row.policyVersion === policy.policyVersion" type="success" size="small">当前生效</el-tag>
              </div>
              <p>审批阈值 {{ formatAmount(row.approvalThreshold) }}</p>
              <p>贵重单价 {{ formatUnitPrice(row.valuableUnitPriceThreshold) }}</p>
              <p class="text-help muted">指定分类：{{ forcedCategoryText(row.forcedCategoryIds) }}</p>
              <p class="text-help muted">生效 {{ formatDateTime(row.effectiveAt) }}</p>
              <p class="text-help muted">发布人 {{ row.createdBy ?? '—' }} · {{ formatDateTime(row.createTime) }}</p>
            </article>
          </div>

          <footer class="table-footer">
            <el-pagination
              v-model:current-page="versionsPage"
              v-model:page-size="versionsPageSize"
              :page-sizes="[10, 20, 50]"
              :total="versionsTotal"
              layout="total, sizes, prev, pager, next"
              background
              @current-change="loadVersions"
              @size-change="onVersionsPageSizeChange"
            />
          </footer>
        </template>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MockStateBanner from '@/components/MockStateBanner.vue'
import RegionState from '@/components/RegionState.vue'
import {
  canUpdatePolicy,
  formatAmount,
  formatUnitPrice,
  getPolicy,
  listPolicyVersions,
  normalizePage,
  updatePolicy
} from '@/api/procurement'
import { listCategories } from '@/api/components'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'
import {
  isProcurementDenied,
  isProcurementIdempotencyReused,
  isProcurementVersionConflict,
  normalizeApiError
} from '@/utils/apiError'

const policy = ref(null)
const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const saving = ref(false)
const conflictInfo = ref(null)

const versions = ref([])
const versionsTotal = ref(0)
const versionsPage = ref(1)
const versionsPageSize = ref(10)
const versionsLoading = ref(false)
const versionsError = ref('')

const categoryOptions = ref([])

const form = reactive({
  approvalThreshold: 0,
  valuableUnitPriceThreshold: 0,
  forcedCategoryIds: []
})

let publishKey = null
let publishDigest = ''

const categoryLabelMap = computed(() => {
  const map = new Map()
  categoryOptions.value.forEach((option) => map.set(option.id, option.label))
  return map
})

function flattenCategories(nodes, depth = 0, acc = []) {
  ;(nodes || []).forEach((node) => {
    if (node.status && node.status !== 'ACTIVE') return
    const prefix = depth > 0 ? `${'—'.repeat(depth)} ` : ''
    acc.push({ id: node.id, label: `${prefix}${node.name}（${node.code}）` })
    if (node.children?.length) flattenCategories(node.children, depth + 1, acc)
  })
  return acc
}

function forcedCategoryText(ids) {
  const list = Array.isArray(ids) ? ids : []
  if (!list.length) return '无'
  return list
    .map((id) => categoryLabelMap.value.get(Number(id)) || `分类 ${id}`)
    .join('、')
}

function resetForm() {
  if (!policy.value) return
  form.approvalThreshold = Number(policy.value.approvalThreshold)
  form.valuableUnitPriceThreshold = Number(policy.value.valuableUnitPriceThreshold)
  form.forcedCategoryIds = [...(policy.value.forcedCategoryIds || [])].map(Number)
  publishKey = null
  publishDigest = ''
}

async function loadAll() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  conflictInfo.value = null
  try {
    policy.value = await getPolicy()
    resetForm()
    await Promise.all([loadVersions(), loadCategories()])
  } catch (error) {
    if (isProcurementDenied(error)) {
      loadError.value = '只有系统管理员可以查看采购审批策略'
      policy.value = null
    } else {
      const normalized = normalizeApiError(error)
      loadError.value = normalized.msg || '采购策略加载失败'
      loadTraceId.value = normalized.traceId || ''
    }
  } finally {
    loading.value = false
  }
}

async function loadVersions() {
  versionsLoading.value = true
  versionsError.value = ''
  try {
    const response = await listPolicyVersions({ page: versionsPage.value, pageSize: versionsPageSize.value })
    const normalized = normalizePage(response)
    versions.value = normalized.list
    versionsTotal.value = normalized.total
  } catch (error) {
    const normalized = normalizeApiError(error)
    versionsError.value = `${normalized.msg || '策略版本历史加载失败'}${normalized.traceId ? ` · traceId ${normalized.traceId}` : ''}`
    versions.value = []
    versionsTotal.value = 0
  } finally {
    versionsLoading.value = false
  }
}

async function loadCategories() {
  try {
    const response = await listCategories()
    const nodes = Array.isArray(response) ? response : (normalizePage(response).list || [])
    categoryOptions.value = flattenCategories(nodes)
  } catch {
    categoryOptions.value = []
  }
}

function onVersionsPageSizeChange() {
  versionsPage.value = 1
  loadVersions()
}

function validate() {
  if (!Number.isFinite(Number(form.approvalThreshold)) || Number(form.approvalThreshold) <= 0) {
    ElMessage.warning('审批阈值必须大于 0')
    return false
  }
  if (!Number.isFinite(Number(form.valuableUnitPriceThreshold)) || Number(form.valuableUnitPriceThreshold) <= 0) {
    ElMessage.warning('贵重单价阈值必须大于 0')
    return false
  }
  const known = new Set(categoryOptions.value.map((option) => option.id))
  const unknown = form.forcedCategoryIds.find((id) => !known.has(Number(id)))
  if (unknown != null) {
    ElMessage.warning(`指定分类 ${unknown} 不是当前启用分类`)
    return false
  }
  return true
}

function payloadOf() {
  return {
    currentPolicyVersion: Number(policy.value.policyVersion),
    approvalThreshold: Number(form.approvalThreshold),
    valuableUnitPriceThreshold: Number(form.valuableUnitPriceThreshold),
    forcedCategoryIds: form.forcedCategoryIds.map(Number)
  }
}

async function publish() {
  if (saving.value) return
  if (!validate()) return
  const payload = payloadOf()
  const digest = JSON.stringify(payload)
  if (publishKey && publishDigest !== digest) publishKey = null
  if (!publishKey) {
    publishKey = createIdempotencyKey('w45-policy')
    publishDigest = digest
  }
  saving.value = true
  try {
    const next = await updatePolicy(payload, publishKey)
    publishKey = null
    ElMessage.success(`策略已发布：policyVersion ${next.policyVersion} 立即生效`)
    policy.value = next
    resetForm()
    versionsPage.value = 1
    await loadVersions()
  } catch (error) {
    handleWriteError(error)
  } finally {
    saving.value = false
  }
}

function handleWriteError(error) {
  if (isProcurementVersionConflict(error)) {
    const normalized = normalizeApiError(error)
    conflictInfo.value = { currentVersion: normalized.data?.currentVersion ?? null }
    ElMessage.warning(`策略已被他人更新：currentPolicyVersion=${conflictInfo.value.currentVersion ?? '未知'}；表单内容已保留`)
    return
  }
  if (isProcurementDenied(error)) {
    ElMessage.error('只有系统管理员可以修改采购审批策略')
    return
  }
  if (isProcurementIdempotencyReused(error)) {
    publishKey = null
    ElMessage.error('相同幂等键被用于不同请求体，请重新提交')
    return
  }
  const normalized = normalizeApiError(error)
  ElMessage.error(`${normalized.msg || '策略发布失败'}${normalized.traceId ? ` · traceId ${normalized.traceId}` : ''}`)
}

onMounted(loadAll)
</script>

<style scoped>
.procurement-settings-page { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.settings-section { display: flex; flex-direction: column; gap: 14px; }
.section-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.policy-summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; }
.policy-summary > div { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
.policy-summary strong { font-size: 15px; font-variant-numeric: tabular-nums; }
.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
.form-field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--text-secondary, #606266); }
.form-field--wide { grid-column: span 2; }
.change-summary { border-top: 1px solid var(--border-color, #ebeef5); padding-top: 12px; font-size: 13px; }
.settings-footer { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
.version-text { font-variant-numeric: tabular-nums; }
.table-footer { display: flex; justify-content: flex-end; }
.mobile-only { display: none; }
.version-cards { display: flex; flex-direction: column; gap: 10px; }
.version-card { border: 1px solid var(--border-color, #ebeef5); border-radius: 8px; padding: 12px; display: flex; flex-direction: column; gap: 6px; font-size: 13px; }
.version-card p { margin: 0; }
.version-card__top { display: flex; align-items: center; justify-content: space-between; }
@media (max-width: 767px) {
  .desktop-only { display: none; }
  .mobile-only { display: flex; }
  .form-field--wide { grid-column: span 1; }
  .settings-footer { justify-content: flex-start; }
}
</style>
