<template>
  <div class="fulfillment-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="go('/inventory/issues')">返回领用申请</el-button>
      <span class="text-help muted">发放队列 · APPROVED 待发放</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canViewAllRequests()"
      state="permission"
      variant="section"
      description="当前账号无权查看发放队列"
      sub-title="发放面向库存管理员与系统管理员；申请人不得发放本人申请"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载待发放申请"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="发放队列加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="load"
    />

    <template v-else>
      <header class="page-header">
        <div>
          <h1 class="page-title">待发放领用申请</h1>
          <p class="text-help muted">
            共 {{ total }} 条待发放；确认发放才扣减库存并写 ISSUE_OUT，预占到期后需重新申请。
          </p>
        </div>
      </header>

      <section class="list-panel data-panel">
        <div v-if="!list.length" class="empty-block">
          <el-empty description="当前没有待发放申请" />
        </div>

        <el-table v-else :data="list" row-key="id" stripe @row-click="goDetail">
          <el-table-column label="申请单号" min-width="170">
            <template #default="{ row }">
              <el-button text type="primary" class="link-button" @click.stop="goDetail(row)">{{ row.requestNo }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column label="申请人" width="110">
            <template #default="{ row }">{{ row.requesterName || row.requesterId }}</template>
          </el-table-column>
          <el-table-column label="关联项目" min-width="130">
            <template #default="{ row }">{{ row.projectName || '—' }}</template>
          </el-table-column>
          <el-table-column label="预占到期" width="170">
            <template #default="{ row }">{{ formatDateTime(row.expiresAt) }}</template>
          </el-table-column>
          <el-table-column label="预占剩余" width="150">
            <template #default="{ row }">{{ formatRemainingDuration(row.expiresAt) || '—' }}</template>
          </el-table-column>
          <el-table-column label="version" width="90" align="center">
            <template #default="{ row }"><span class="version-text">v{{ row.version }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button v-if="!isIssueRequester(row)" text type="primary" @click.stop="goDetail(row)">去发放</el-button>
              <span v-else class="text-help muted">本人申请</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="canWriteOffStock()" class="data-panel write-off-panel">
        <header class="list-panel__header">
          <div>
            <h2 class="section-title">受控损耗 / 报废</h2>
            <p class="text-help muted">
              损耗与报废只扣减未预占库存，写 LOSS_OUT / SCRAP_OUT；不得占用已预占数量。STOCKTAKE_ADJUSTMENT 留待第 12 周。
            </p>
          </div>
        </header>
        <div class="write-off-grid">
          <label class="filter-field">
            <span>类型</span>
            <el-radio-group v-model="writeOff.type">
              <el-radio-button value="LOSS">损耗</el-radio-button>
              <el-radio-button value="SCRAP">报废</el-radio-button>
            </el-radio-group>
          </label>
          <label class="filter-field">
            <span>元器件</span>
            <el-select
              v-model="writeOff.componentId"
              filterable
              remote
              clearable
              :remote-method="searchComponents"
              :loading="searching"
              placeholder="搜索名称 / MPN"
              style="width: 100%"
              @change="onComponentChange"
            >
              <el-option
                v-for="component in componentOptions"
                :key="component.id"
                :label="`${component.name} · ${component.mpn}`"
                :value="component.id"
              />
            </el-select>
          </label>
          <label class="filter-field">
            <span>批次</span>
            <el-select v-model="writeOff.batchId" placeholder="先选择元器件" style="width: 100%" :disabled="!batches.length">
              <el-option
                v-for="batch in batches"
                :key="batch.id"
                :label="`${batch.batchNo} · 可用 ${formatQuantity(batch.availableQuantity)}${batch.expiryDate ? ` · 效期 ${batch.expiryDate}` : ''}`"
                :value="batch.id"
              />
            </el-select>
          </label>
          <label class="filter-field">
            <span>数量</span>
            <el-input-number v-model="writeOff.quantity" :min="0.001" :precision="3" controls-position="right" style="width: 100%" />
          </label>
          <label class="filter-field filter-field--wide">
            <span>原因 *</span>
            <el-input v-model="writeOff.reason" maxlength="500" show-word-limit placeholder="如：实验台散落遗失 / 插针变形报废" />
          </label>
        </div>
        <div class="filter-actions">
          <el-button type="danger" plain :loading="writeOffBusy" :disabled="writeOffBusy" @click="submitWriteOff">
            登记{{ writeOff.type === 'LOSS' ? '损耗' : '报废' }}
          </el-button>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import RegionState from '@/components/RegionState.vue'
import {
  ISSUE_STATUS,
  canViewAllRequests,
  canWriteOffStock,
  formatQuantity,
  formatRemainingDuration,
  isIssueRequester,
  listInventoryComponentBatches,
  listIssueRequests,
  normalizePage,
  registerBatchLoss,
  registerBatchScrap,
  searchIssueComponents
} from '@/api/inventoryIssuance'
import {
  errorTraceId,
  isIssueReservedStockProtected,
  normalizeApiError,
  notifyApiError
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const list = ref([])
const total = ref(0)

const searching = ref(false)
const componentOptions = ref([])
const batches = ref([])
const writeOffBusy = ref(false)
const writeOff = reactive({ type: 'LOSS', componentId: null, batchId: null, quantity: 1, reason: '' })

function go(path) {
  router.push(path)
}

function goDetail(row) {
  router.push(`/inventory/issues/${row.id}`)
}

async function load() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  try {
    const response = await listIssueRequests({ status: ISSUE_STATUS.APPROVED, page: 1, pageSize: 100, sort: 'expiresAt,asc' })
    const normalized = normalizePage(response)
    list.value = normalized.list
    total.value = normalized.total
  } catch (error) {
    list.value = []
    total.value = 0
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

async function onComponentChange(componentId) {
  writeOff.batchId = null
  batches.value = []
  if (!componentId) return
  try {
    const response = await listInventoryComponentBatches(componentId, { page: 1, pageSize: 50 })
    batches.value = normalizePage(response).list.filter((batch) => Number(batch.availableQuantity) > 0)
  } catch (error) {
    notifyApiError(error)
  }
}

async function submitWriteOff() {
  if (!writeOff.batchId) {
    ElMessage.warning('请选择批次')
    return
  }
  if (!(Number(writeOff.quantity) > 0)) {
    ElMessage.warning('数量必须大于 0')
    return
  }
  if (!String(writeOff.reason || '').trim()) {
    ElMessage.warning('必须填写原因')
    return
  }
  writeOffBusy.value = true
  try {
    const payload = { quantity: Number(writeOff.quantity), reason: writeOff.reason.trim() }
    const key = createIdempotencyKey(writeOff.type === 'LOSS' ? 'w46-loss' : 'w46-scrap')
    const result = writeOff.type === 'LOSS'
      ? await registerBatchLoss(writeOff.batchId, payload, key)
      : await registerBatchScrap(writeOff.batchId, payload, key)
    ElMessage.success(`${result.commandNo} 已登记，流水 #${result.movementId}`)
    writeOff.quantity = 1
    writeOff.reason = ''
    await onComponentChange(writeOff.componentId)
  } catch (error) {
    if (isIssueReservedStockProtected(error)) {
      const { data } = normalizeApiError(error)
      ElMessage.error(`不能扣减已预占库存：该批次可用 ${formatQuantity(data?.availableQuantity)}`)
      return
    }
    notifyApiError(error)
    ElMessage.error(normalizeApiError(error).msg)
  } finally {
    writeOffBusy.value = false
  }
}

onMounted(() => {
  load()
  searchComponents('')
})
</script>

<style scoped>
.fulfillment-page {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.write-off-panel {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.write-off-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--usn-space-3);
}

.filter-field--wide {
  grid-column: 1 / -1;
}

@media (max-width: 768px) {
  .write-off-grid {
    grid-template-columns: 1fr;
  }
}
</style>
