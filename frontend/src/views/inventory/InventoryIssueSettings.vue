<template>
  <div class="issue-settings-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="go('/inventory/issues')">返回领用申请</el-button>
      <span class="text-help muted">领用审批策略 · 仅系统管理员可发布</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canPublishIssuePolicy()"
      state="permission"
      variant="section"
      description="当前账号无权管理领用策略"
      sub-title="仅系统管理员可查看与发布策略版本"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载领用策略"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="领用策略加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="load"
    />

    <template v-else>
      <header class="page-header">
        <div>
          <h1 class="page-title">领用审批策略</h1>
          <p class="text-help muted">
            当前版本 v{{ policy.policyVersion || '—' }}；发布后仅影响之后提交的申请，历史申请保留原策略版本快照。
          </p>
        </div>
        <div class="page-header__actions">
          <el-button @click="load">刷新</el-button>
          <el-button type="primary" :loading="publishing" :disabled="publishing" @click="publish">发布新版本</el-button>
        </div>
      </header>

      <el-alert
        v-if="publishSuccess"
        type="success"
        :closable="true"
        show-icon
        :title="publishSuccess"
        @close="publishSuccess = ''"
      />

      <el-alert
        v-if="conflictVersion != null"
        type="warning"
        :closable="false"
        show-icon
        :title="`策略版本冲突：服务端当前为 v${conflictVersion}，请刷新并重新比较后再发布。`"
      >
        <el-button text type="primary" @click="load">刷新策略</el-button>
      </el-alert>

      <section class="data-panel settings-section">
        <h2 class="section-title">免审阈值与预占</h2>
        <div class="form-grid">
          <label class="form-field">
            <span>直接领用金额阈值（CNY）</span>
            <el-input-number v-model="form.directAmountThreshold" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
          </label>
          <label class="form-field">
            <span>贵重批次单位成本阈值（CNY）</span>
            <el-input-number v-model="form.valuableUnitCostThreshold" :min="0.0001" :precision="4" controls-position="right" style="width: 100%" />
          </label>
          <label class="form-field">
            <span>默认单行直接领用上限</span>
            <el-input-number v-model="form.defaultDirectQuantityLimit" :min="0.001" :precision="3" controls-position="right" style="width: 100%" />
          </label>
          <label class="form-field">
            <span>库存紧张比例（%）</span>
            <el-input-number v-model="form.lowStockRatio" :min="0" :max="100" :precision="2" controls-position="right" style="width: 100%" />
          </label>
          <label class="form-field">
            <span>预占时长（小时，1-168）</span>
            <el-input-number v-model="form.reservationTtlHours" :min="1" :max="168" :step="1" :precision="0" controls-position="right" style="width: 100%" />
          </label>
          <label class="form-field">
            <span>强制人工审批</span>
            <el-switch v-model="form.manualApprovalRequired" active-text="所有申请须人工审批" />
          </label>
          <label class="form-field form-field--wide">
            <span>指定分类 ID（逗号分隔，命中即人工审批）</span>
            <el-input v-model="forcedCategoriesText" placeholder="如：34, 35" />
          </label>
        </div>
      </section>

      <section class="data-panel settings-section">
        <header class="list-panel__header">
          <div>
            <h2 class="section-title">元器件级数量覆盖</h2>
            <p class="text-help muted">
              覆盖项优先于默认单行上限；同一元器件不可重复；元器件须为 ACTIVE。
            </p>
          </div>
          <el-button plain :icon="Plus" @click="addOverride">添加覆盖项</el-button>
        </header>

        <div v-if="!overrides.length" class="empty-block">
          <el-empty description="暂无覆盖项" :image-size="80" />
        </div>

        <div v-for="(override, index) in overrides" :key="override.key" class="override-row">
          <el-select
            v-model="override.componentId"
            filterable
            remote
            clearable
            :remote-method="searchComponents"
            :loading="searching"
            placeholder="搜索元器件名称 / MPN"
            style="width: 100%"
            @change="onOverrideComponentChange(override)"
          >
            <el-option
              v-for="component in componentOptions"
              :key="component.id"
              :label="`${component.name} · ${component.mpn}`"
              :value="component.id"
            />
          </el-select>
          <el-input-number v-model="override.directQuantityLimit" :min="0.001" :precision="3" controls-position="right" style="width: 100%" />
          <span class="text-help muted">{{ override.unitSnapshot || 'PCS' }}</span>
          <el-button text type="danger" @click="removeOverride(index)">移除</el-button>
        </div>
      </section>

      <section class="data-panel settings-section">
        <h2 class="section-title">版本历史</h2>
        <div v-if="!versions.length" class="empty-block">
          <el-empty description="暂无历史版本" :image-size="80" />
        </div>
        <el-table v-else :data="versions" row-key="policyVersion" stripe>
          <el-table-column label="版本" width="90">
            <template #default="{ row }">v{{ row.policyVersion }}</template>
          </el-table-column>
          <el-table-column label="生效时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.effectiveAt || row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="金额阈值" width="120" align="right">
            <template #default="{ row }">{{ formatAmount(row.directAmountThreshold) }}</template>
          </el-table-column>
          <el-table-column label="贵重阈值" width="130" align="right">
            <template #default="{ row }">{{ formatUnitCost(row.valuableUnitCostThreshold) }}</template>
          </el-table-column>
          <el-table-column label="预占时长" width="100" align="right">
            <template #default="{ row }">{{ row.reservationTtlHours }} h</template>
          </el-table-column>
          <el-table-column label="覆盖项" width="90" align="center">
            <template #default="{ row }">{{ (row.componentOverrides || []).length }}</template>
          </el-table-column>
          <el-table-column label="操作人" width="100">
            <template #default="{ row }">#{{ row.createdBy }}</template>
          </el-table-column>
        </el-table>
      </section>
    </template>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import RegionState from '@/components/RegionState.vue'
import {
  canPublishIssuePolicy,
  formatAmount,
  formatUnitCost,
  getIssuePolicy,
  listIssuePolicyVersions,
  normalizePage,
  publishIssuePolicy,
  searchIssueComponents
} from '@/api/inventoryIssuance'
import { errorTraceId, isIssueVersionConflict, normalizeApiError, notifyApiError } from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const publishing = ref(false)
const publishSuccess = ref('')
const conflictVersion = ref(null)
const searching = ref(false)
const componentOptions = ref([])
const versions = ref([])
const overrides = ref([])
const forcedCategoriesText = ref('')
let overrideSeed = 0

const policy = reactive({
  policyVersion: null,
  directAmountThreshold: 100,
  valuableUnitCostThreshold: 500,
  defaultDirectQuantityLimit: 10,
  lowStockRatio: 20,
  reservationTtlHours: 72,
  manualApprovalRequired: false
})

const form = reactive({
  directAmountThreshold: 100,
  valuableUnitCostThreshold: 500,
  defaultDirectQuantityLimit: 10,
  lowStockRatio: 20,
  reservationTtlHours: 72,
  manualApprovalRequired: false
})

function go(path) {
  router.push(path)
}

function applyPolicy(data) {
  if (!data) return
  Object.assign(policy, data)
  Object.assign(form, {
    directAmountThreshold: Number(data.directAmountThreshold),
    valuableUnitCostThreshold: Number(data.valuableUnitCostThreshold),
    defaultDirectQuantityLimit: Number(data.defaultDirectQuantityLimit),
    lowStockRatio: Number(data.lowStockRatio),
    reservationTtlHours: Number(data.reservationTtlHours),
    manualApprovalRequired: Boolean(data.manualApprovalRequired)
  })
  forcedCategoriesText.value = (data.forcedCategoryIds || []).join(', ')
  overrides.value = (data.componentOverrides || []).map((row) => {
    overrideSeed += 1
    return {
      key: `existing-${row.id || overrideSeed}`,
      componentId: row.componentId,
      componentName: row.componentName,
      unitSnapshot: row.unitSnapshot,
      directQuantityLimit: Number(row.directQuantityLimit)
    }
  })
}

async function loadVersions() {
  try {
    const response = await listIssuePolicyVersions({ page: 1, pageSize: 20 })
    versions.value = normalizePage(response).list
  } catch (error) {
    versions.value = []
  }
}

async function load() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  conflictVersion.value = null
  try {
    const data = await getIssuePolicy()
    applyPolicy(data)
    await loadVersions()
  } catch (error) {
    loadError.value = normalizeApiError(error).msg
    loadTraceId.value = errorTraceId(error)
    notifyApiError(error)
  } finally {
    loading.value = false
  }
}

async function searchComponents(keyword) {
  searching.value = true
  try {
    const response = await searchIssueComponents({ keyword })
    componentOptions.value = response?.list || response?.items || []
  } finally {
    searching.value = false
  }
}

function addOverride() {
  overrideSeed += 1
  overrides.value.push({
    key: `new-${overrideSeed}`,
    componentId: null,
    componentName: '',
    unitSnapshot: '',
    directQuantityLimit: 1
  })
}

function removeOverride(index) {
  overrides.value.splice(index, 1)
}

function onOverrideComponentChange(override) {
  const component = componentOptions.value.find((item) => Number(item.id) === Number(override.componentId))
  if (component) {
    override.componentName = component.name
    override.unitSnapshot = component.baseUnit
  }
  const duplicated = overrides.value.filter((item) => Number(item.componentId) === Number(override.componentId))
  if (duplicated.length > 1) {
    override.componentId = null
    override.componentName = ''
    override.unitSnapshot = ''
  }
}

function parseForcedCategories() {
  return forcedCategoriesText.value
    .split(',')
    .map((item) => Number(String(item).trim()))
    .filter((value) => Number.isInteger(value) && value > 0)
}

async function publish() {
  const forced = parseForcedCategories()
  const rows = overrides.value.filter((row) => row.componentId)
  if (rows.some((row) => !(Number(row.directQuantityLimit) > 0))) {
    ElMessage.warning('覆盖项数量上限必须大于 0')
    return
  }
  const ids = rows.map((row) => Number(row.componentId))
  if (new Set(ids).size !== ids.length) {
    ElMessage.warning('同一元器件不能重复设置覆盖项')
    return
  }
  if (!Number.isInteger(Number(form.reservationTtlHours)) || Number(form.reservationTtlHours) < 1 || Number(form.reservationTtlHours) > 168) {
    ElMessage.warning('预占时长必须为 1-168 小时整数')
    return
  }
  publishing.value = true
  conflictVersion.value = null
  try {
    const result = await publishIssuePolicy({
      basePolicyVersion: policy.policyVersion,
      directAmountThreshold: Number(form.directAmountThreshold),
      valuableUnitCostThreshold: Number(form.valuableUnitCostThreshold),
      defaultDirectQuantityLimit: Number(form.defaultDirectQuantityLimit),
      lowStockRatio: Number(form.lowStockRatio),
      reservationTtlHours: Number(form.reservationTtlHours),
      manualApprovalRequired: Boolean(form.manualApprovalRequired),
      forcedCategoryIds: forced,
      componentOverrides: rows.map((row) => ({
        componentId: Number(row.componentId),
        directQuantityLimit: Number(row.directQuantityLimit)
      }))
    }, createIdempotencyKey('w46-policy'))
    publishSuccess.value = `策略已发布：v${result.policyVersion}，生效时间 ${formatDateTime(result.effectiveAt)}`
    applyPolicy(result)
    await loadVersions()
  } catch (error) {
    if (isIssueVersionConflict(error)) {
      conflictVersion.value = normalizeApiError(error).data?.currentVersion ?? 0
      return
    }
    notifyApiError(error)
    ElMessage.error(normalizeApiError(error).msg)
  } finally {
    publishing.value = false
  }
}

onMounted(() => {
  load()
  searchComponents('')
})
</script>

<style scoped>
.issue-settings-page {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.settings-section {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.override-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px 60px 80px;
  gap: var(--usn-space-3);
  align-items: center;
  padding: var(--usn-space-2) 0;
  border-bottom: 1px dashed var(--usn-border-color, #e4e7ed);
}

@media (max-width: 768px) {
  .override-row {
    grid-template-columns: 1fr;
    align-items: stretch;
  }
}
</style>
