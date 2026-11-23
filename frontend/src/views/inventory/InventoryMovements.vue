<template>
  <div class="movements-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="go('/inventory/issues')">返回领用申请</el-button>
      <span class="text-help muted">库存流水 · 受控追加，不提供编辑/删除</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canAccessIssuance()"
      state="permission"
      variant="section"
      description="当前账号无权访问库存流水"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载库存流水"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="库存流水加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="load"
    />

    <template v-else>
      <header class="page-header">
        <div>
          <h1 class="page-title">库存流水</h1>
          <p class="text-help muted">
            入库、发放、退回、损耗与报废均为不可变流水；盘点修正冻结至第 12 周。
            <template v-if="!canViewInventoryMovements()">普通成员仅可查看本人领用业务时间线。</template>
          </p>
        </div>
      </header>

      <section class="filter-panel data-panel">
        <div class="filter-grid">
          <label class="filter-field">
            <span>流水类型</span>
            <el-select v-model="filters.movementType" clearable placeholder="全部类型" style="width: 100%">
              <el-option
                v-for="(meta, type) in MOVEMENT_TYPE_META"
                :key="type"
                :label="meta.label"
                :value="type"
              />
            </el-select>
          </label>
          <label class="filter-field">
            <span>批次号</span>
            <el-input v-model="filters.batchNo" clearable placeholder="如 B-302-A" @keyup.enter="search" />
          </label>
          <label class="filter-field">
            <span>元器件 ID</span>
            <el-input v-model="filters.componentId" clearable placeholder="可选，精确匹配" @keyup.enter="search" />
          </label>
          <label class="filter-field">
            <span>发生时间</span>
            <el-date-picker
              v-model="filters.range"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              start-placeholder="开始"
              end-placeholder="结束"
              style="width: 100%"
            />
          </label>
        </div>
        <div class="filter-actions">
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </section>

      <section class="list-panel data-panel">
        <header class="list-panel__header">
          <div>
            <h2 class="section-title">流水列表</h2>
            <p class="text-help muted">共 {{ total }} 条 · 第 {{ page }} 页 · 每页 {{ pageSize }} 条</p>
          </div>
        </header>

        <div v-if="!list.length" class="empty-block">
          <el-empty description="暂无库存流水">
            <el-button v-if="hasFilters" @click="resetFilters">清空筛选</el-button>
          </el-empty>
        </div>

        <template v-else>
          <el-table :data="list" row-key="movementId" stripe>
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
            <el-table-column label="元器件" width="100">
              <template #default="{ row }">#{{ row.componentId }}</template>
            </el-table-column>
            <el-table-column label="批次" min-width="120">
              <template #default="{ row }">{{ row.batchNo || '—' }}</template>
            </el-table-column>
            <el-table-column label="数量变化" width="110" align="right">
              <template #default="{ row }">
                <span :class="Number(row.quantityDelta) < 0 ? 'danger-text' : 'success-text'">
                  {{ Number(row.quantityDelta) > 0 ? '+' : '' }}{{ formatQuantity(row.quantityDelta) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column v-if="canViewInventoryMovements()" label="余额变化" width="150" align="right">
              <template #default="{ row }">
                {{ formatQuantity(row.balanceBefore) }} → {{ formatQuantity(row.balanceAfter) }}
              </template>
            </el-table-column>
            <el-table-column label="来源" min-width="150">
              <template #default="{ row }">
                <span>{{ row.sourceType }}</span>
                <small v-if="row.sourceId" class="muted block">#{{ row.sourceId }}</small>
              </template>
            </el-table-column>
            <el-table-column v-if="canViewInventoryMovements()" label="操作人" width="120">
              <template #default="{ row }">{{ row.operatorName || '—' }}</template>
            </el-table-column>
            <el-table-column label="原因/申请" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.reason">{{ row.reason }}</span>
                <el-button v-else-if="row.requestId" text type="primary" @click="openRequest(row.requestId)">
                  申请 #{{ row.requestId }}
                </el-button>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="发生时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
            </el-table-column>
          </el-table>

          <footer class="table-footer">
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50]"
              :total="total"
              layout="total, sizes, prev, pager, next"
              background
              @current-change="load"
              @size-change="onPageSizeChange"
            />
          </footer>
        </template>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import RegionState from '@/components/RegionState.vue'
import {
  MOVEMENT_TYPE_META,
  canAccessIssuance,
  canViewInventoryMovements,
  formatQuantity,
  listInventoryMovements,
  movementTypeMeta,
  normalizePage
} from '@/api/inventoryIssuance'
import { errorTraceId, normalizeApiError, notifyApiError } from '@/utils/apiError'
import { buildQueryWithoutEmpty, formatDateTime } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const filters = reactive({
  movementType: '',
  batchNo: '',
  componentId: '',
  range: null
})

const hasFilters = computed(() => Boolean(filters.movementType || filters.batchNo || filters.componentId || filters.range))

function go(path) {
  router.push(path)
}

function openRequest(requestId) {
  router.push(`/inventory/issues/${requestId}`)
}

async function load() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  try {
    const response = await listInventoryMovements(buildQueryWithoutEmpty({
      movementType: filters.movementType,
      batchNo: filters.batchNo,
      componentId: filters.componentId,
      from: filters.range?.[0] || '',
      to: filters.range?.[1] || '',
      page: page.value,
      pageSize: pageSize.value
    }))
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

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  filters.movementType = ''
  filters.batchNo = ''
  filters.componentId = ''
  filters.range = null
  page.value = 1
  load()
}

function onPageSizeChange(size) {
  pageSize.value = size
  page.value = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.movements-page {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.danger-text {
  color: var(--el-color-danger);
}

.success-text {
  color: var(--el-color-success);
}

.block {
  display: block;
}
</style>
