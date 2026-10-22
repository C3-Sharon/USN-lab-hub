<template>
  <el-dialog
    :model-value="modelValue"
    title="绑定采集快照"
    width="560px"
    class="snapshot-dialog"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-alert type="info" :closable="false" show-icon class="snapshot-hint">
      每个修订本周最多绑定 1 个 FULL_BATCH 快照；只有已完成批次可生成快照，提交后快照随修订冻结。
    </el-alert>

    <div v-if="record?.collectionSnapshot" class="current-box">
      <span class="current-box__label">当前绑定</span>
      <span class="current-box__value">{{ record.collectionSnapshot.snapshotCode }}</span>
      <span class="text-help muted">
        批次 {{ record.collectionSnapshot.batchCode }} · {{ record.collectionSnapshot.recordCount }} 条记录
      </span>
    </div>

    <div v-if="loading" class="snapshot-loading">
      <el-skeleton :rows="3" animated />
    </div>

    <RegionState
      v-else-if="errorMessage"
      state="error"
      variant="section"
      description="快照加载失败"
      :sub-title="errorMessage"
      :error-message="errorMessage"
      @retry="loadSnapshots"
    />

    <template v-else>
      <el-empty v-if="!snapshots.length" :image-size="72" description="当前项目暂无可用快照，请先完成采集批次并生成快照" />

      <el-form v-else label-position="top">
        <el-form-item label="选择快照">
          <el-select v-model="selectedId" class="full-width" placeholder="请选择 FULL_BATCH 快照">
            <el-option
              v-for="item in snapshots"
              :key="item.id"
              :label="`${item.snapshotCode} · ${item.batchCode} · ${item.recordCount} 条`"
              :value="item.id"
            />
            <el-option v-if="record?.collectionSnapshot" label="解除当前绑定" :value="0" />
          </el-select>
        </el-form-item>
        <p class="text-help muted">
          仅可绑定当前记录所属项目内的已完成批次快照；跨项目快照将返回 404。
        </p>
      </el-form>
    </template>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="loading || !canSubmit"
        @click="handleSubmit"
      >
        保存绑定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { listCollectionPlans, listCollectionBatches, getCollectionBatch } from '@/api/collection'
import { normalizeApiError, errorTraceId } from '@/utils/apiError'
import RegionState from '@/components/RegionState.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  record: { type: Object, default: null },
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const loading = ref(false)
const errorMessage = ref('')
const snapshots = ref([])
const selectedId = ref(null)

const canSubmit = computed(() => selectedId.value !== null && selectedId.value !== undefined)

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    selectedId.value = props.record?.collectionSnapshot?.id ?? null
    loadSnapshots()
  }
)

async function loadSnapshots() {
  if (!props.record?.projectId) {
    snapshots.value = []
    errorMessage.value = ''
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const planPage = await listCollectionPlans(props.record.projectId, { page: 1, pageSize: 50 })
    const collected = []
    for (const plan of planPage?.list || []) {
      const batchPage = await listCollectionBatches(plan.id, { page: 1, pageSize: 50 })
      for (const batch of batchPage?.list || []) {
        if (!['COMPLETED', 'ARCHIVED'].includes(batch.status)) continue
        const detail = await getCollectionBatch(batch.id)
        if (detail?.snapshot) collected.push(detail.snapshot)
      }
    }
    const seen = new Set()
    snapshots.value = collected.filter((item) => {
      if (seen.has(item.id)) return false
      seen.add(item.id)
      return true
    })
  } catch (err) {
    const { msg } = normalizeApiError(err)
    const traceId = errorTraceId(err)
    errorMessage.value = traceId ? `${msg}（traceId: ${traceId}）` : msg
    snapshots.value = []
  } finally {
    loading.value = false
  }
}

function handleSubmit() {
  emit('submit', {
    snapshotId: selectedId.value === 0 ? null : selectedId.value,
    recordVersion: props.record?.version,
    revisionVersion: props.record?.currentRevision?.version
  })
}
</script>

<style scoped>
.snapshot-hint {
  margin-bottom: var(--usn-space-4);
}

.current-box {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
  padding: var(--usn-space-3);
  margin-bottom: var(--usn-space-4);
  background: var(--usn-canvas);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-sm);
}

.current-box__label {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.current-box__value {
  font-weight: 600;
  color: var(--usn-ink-900);
}

.snapshot-loading {
  padding: var(--usn-space-3) 0;
}

.full-width {
  width: 100%;
}

@media (max-width: 767px) {
  :global(.snapshot-dialog) {
    width: 94vw !important;
  }
}
</style>
