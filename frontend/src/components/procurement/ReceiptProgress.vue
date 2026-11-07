<template>
  <div class="receipt-progress">
    <el-progress
      :percentage="percent"
      :stroke-width="8"
      :status="percent >= 100 ? 'success' : 'warning'"
      :show-text="false"
    />
    <span class="receipt-progress__text">
      已收 {{ formatQuantityUnit(received, unit) }}
      <span v-if="canceled > 0" class="muted">/ 取消 {{ formatQuantityUnit(canceled, unit) }}</span>
      <span class="muted"> / 订购 {{ formatQuantityUnit(ordered, unit) }}</span>
    </span>
    <span v-if="remaining > 0" class="receipt-progress__remaining">
      剩余 {{ formatQuantityUnit(remaining, unit) }}
    </span>
    <span v-else class="receipt-progress__remaining receipt-progress__remaining--done">已结清</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatQuantityUnit, lineProgressPercent, lineRemainingQuantity } from '@/api/procurement'

const props = defineProps({
  line: { type: Object, default: () => ({}) }
})

const percent = computed(() => lineProgressPercent(props.line))
const ordered = computed(() => Number(props.line?.orderedQuantity || 0))
const received = computed(() => Number(props.line?.receivedQuantity || 0))
const canceled = computed(() => Number(props.line?.canceledQuantity || 0))
const remaining = computed(() => lineRemainingQuantity(props.line))
const unit = computed(() => props.line?.unit || '')
</script>

<style scoped>
.receipt-progress {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 170px;
}

.receipt-progress__text,
.receipt-progress__remaining {
  font-size: 12px;
  color: var(--text-secondary, #606266);
}

.receipt-progress__remaining--done {
  color: var(--success-color, #67c23a);
}
</style>
