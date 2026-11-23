<template>
  <div class="issue-approvals-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="go('/inventory/issues')">返回领用申请</el-button>
      <span class="text-help muted">审批队列 · 仅展示 PENDING_APPROVAL</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canViewAllRequests()"
      state="permission"
      variant="section"
      description="当前账号无权查看审批队列"
      sub-title="审批面向老师与系统管理员；申请人不得审批本人申请"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载待审批申请"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="待审批队列加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="load"
    />

    <template v-else>
      <header class="page-header">
        <div>
          <h1 class="page-title">待审批领用申请</h1>
          <p class="text-help muted">
            共 {{ total }} 条待审批；批准不扣库存，拒绝会立即释放预占。本人申请不会出现在可审批操作中。
          </p>
        </div>
      </header>

      <section class="list-panel data-panel">
        <div v-if="!list.length" class="empty-block">
          <el-empty description="当前没有待审批申请" />
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
          <el-table-column label="审批触发" min-width="240">
            <template #default="{ row }">
              <div class="reason-tags">
                <el-tag
                  v-for="reason in row.approvalReasons || []"
                  :key="reason"
                  :type="approvalReasonMeta(reason).tagType"
                  effect="plain"
                  size="small"
                >
                  {{ approvalReasonMeta(reason).label }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="canViewIssueCost()" label="预估金额" width="120" align="right">
            <template #default="{ row }">
              {{ row.estimatedAmount == null ? '—' : formatAmount(row.estimatedAmount) }}
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
          </el-table-column>
          <el-table-column label="预占剩余" width="150">
            <template #default="{ row }">{{ formatRemainingDuration(row.expiresAt) || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button v-if="!isIssueRequester(row)" text type="primary" @click.stop="goDetail(row)">去审批</el-button>
              <span v-else class="text-help muted">本人申请</span>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import RegionState from '@/components/RegionState.vue'
import {
  ISSUE_STATUS,
  approvalReasonMeta,
  canViewAllRequests,
  canViewIssueCost,
  formatAmount,
  formatRemainingDuration,
  isIssueRequester,
  listIssueRequests,
  normalizePage
} from '@/api/inventoryIssuance'
import { errorTraceId, normalizeApiError, notifyApiError } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const list = ref([])
const total = ref(0)

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
    const response = await listIssueRequests({ status: ISSUE_STATUS.PENDING_APPROVAL, page: 1, pageSize: 100, sort: 'submittedAt,asc' })
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

onMounted(load)
</script>

<style scoped>
.issue-approvals-page {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.reason-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
</style>
