<template>
  <div class="receipt-lines">
    <div v-if="!rows.length" class="receipt-lines__empty text-help muted">
      当前采购单没有可到货的剩余明细。
    </div>

    <article
      v-for="row in rows"
      :key="row.orderLine.id"
      class="receipt-line-card"
      :class="{ 'receipt-line-card--readonly': readonly }"
    >
      <header class="receipt-line-card__header">
        <div>
          <strong>{{ row.orderLine.componentName }}</strong>
          <p class="text-help muted">
            MPN {{ row.orderLine.mpn }} · 规格版本 v{{ row.orderLine.specVersion }} · 单位 {{ row.orderLine.unit }}
          </p>
        </div>
        <div class="receipt-line-card__meta">
          <el-tag size="small" type="warning" effect="plain">
            剩余 {{ formatQuantityUnit(row.remaining, row.orderLine.unit) }}
          </el-tag>
          <el-switch
            v-if="!readonly"
            :model-value="row.included"
            inline-prompt
            active-text="本次到货"
            inactive-text="不到货"
            @update:model-value="(value) => toggleRow(row, value)"
          />
        </div>
      </header>

      <template v-if="row.included">
        <div class="receipt-line-grid">
          <label class="receipt-field">
            <span>到货数量 *</span>
            <el-input-number
              v-if="!readonly"
              :model-value="Number(row.entry.quantity || 0)"
              :min="0.001"
              :max="row.remaining"
              :precision="3"
              :step="0.1"
              :disabled="readonly"
              controls-position="right"
              style="width: 100%"
              @update:model-value="(value) => patchRow(row.orderLine.id, 'quantity', value)"
            />
            <span v-else>{{ formatQuantityUnit(row.entry.quantity, row.orderLine.unit) }}</span>
          </label>
          <label class="receipt-field">
            <span>供应商批号</span>
            <el-input
              v-if="!readonly"
              :model-value="row.entry.supplierLotNo"
              maxlength="64"
              placeholder="可选"
              @update:model-value="(value) => patchRow(row.orderLine.id, 'supplierLotNo', value)"
            />
            <span v-else>{{ row.entry.supplierLotNo || '—' }}</span>
          </label>
          <label class="receipt-field">
            <span>生产日期</span>
            <el-date-picker
              v-if="!readonly"
              :model-value="row.entry.productionDate || null"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="可选"
              style="width: 100%"
              @update:model-value="(value) => patchRow(row.orderLine.id, 'productionDate', value)"
            />
            <span v-else>{{ row.entry.productionDate || '—' }}</span>
          </label>
          <label class="receipt-field">
            <span>有效期</span>
            <el-date-picker
              v-if="!readonly"
              :model-value="row.entry.expiryDate || null"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="可选"
              style="width: 100%"
              @update:model-value="(value) => patchRow(row.orderLine.id, 'expiryDate', value)"
            />
            <span v-else>{{ row.entry.expiryDate || '—' }}</span>
          </label>
          <label class="receipt-field">
            <span>存放位置</span>
            <el-input
              v-if="!readonly"
              :model-value="row.entry.locationNote"
              maxlength="120"
              placeholder="如 A 区 2 号柜"
              @update:model-value="(value) => patchRow(row.orderLine.id, 'locationNote', value)"
            />
            <span v-else>{{ row.entry.locationNote || '—' }}</span>
          </label>
          <label class="receipt-field receipt-field--wide">
            <span>质量备注</span>
            <el-input
              v-if="!readonly"
              :model-value="row.entry.qualityNote"
              maxlength="200"
              placeholder="可选"
              @update:model-value="(value) => patchRow(row.orderLine.id, 'qualityNote', value)"
            />
            <span v-else>{{ row.entry.qualityNote || '—' }}</span>
          </label>
        </div>
      </template>
      <p v-else class="text-help muted receipt-line-card__skip">本次不接收该明细，remaining 不变。</p>
    </article>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatQuantityUnit, lineRemainingQuantity } from '@/api/procurement'

const props = defineProps({
  orderLines: { type: Array, default: () => [] },
  modelValue: { type: Array, default: () => [] },
  readonly: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const rows = computed(() => {
  return props.orderLines
    .map((orderLine) => {
      const remaining = lineRemainingQuantity(orderLine)
      const entry = props.modelValue.find((line) => Number(line.orderLineId) === Number(orderLine.id))
      return {
        orderLine,
        remaining,
        included: Boolean(entry),
        entry: entry || { orderLineId: orderLine.id, quantity: 0 }
      }
    })
    .filter((row) => row.remaining > 0 || row.included)
})

function defaultEntry(orderLine, remaining) {
  return {
    orderLineId: orderLine.id,
    quantity: remaining,
    supplierLotNo: '',
    productionDate: null,
    expiryDate: null,
    locationNote: '',
    qualityNote: ''
  }
}

function toggleRow(row, value) {
  const rest = props.modelValue.filter((line) => Number(line.orderLineId) !== Number(row.orderLine.id))
  if (value) {
    emit('update:modelValue', [...rest, defaultEntry(row.orderLine, row.remaining)])
  } else {
    emit('update:modelValue', rest)
  }
}

function patchRow(orderLineId, field, value) {
  const next = props.modelValue.map((line) => (
    Number(line.orderLineId) === Number(orderLineId) ? { ...line, [field]: value } : line
  ))
  emit('update:modelValue', next)
}
</script>

<style scoped>
.receipt-lines { display: flex; flex-direction: column; gap: 12px; }
.receipt-lines__empty { padding: 12px 0; }
.receipt-line-card { border: 1px solid var(--border-color, #ebeef5); border-radius: 8px; padding: 14px; display: flex; flex-direction: column; gap: 12px; }
.receipt-line-card__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.receipt-line-card__meta { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.receipt-line-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }
.receipt-field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--text-secondary, #606266); }
.receipt-field > span:last-child { color: var(--text-primary, #303133); }
.receipt-field--wide { grid-column: span 2; }
.receipt-line-card__skip { margin: 0; }
@media (max-width: 767px) {
  .receipt-field--wide { grid-column: span 1; }
}
</style>
