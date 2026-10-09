<template>
  <div class="usn-progress" role="progressbar" :aria-valuenow="clamped" aria-valuemin="0" aria-valuemax="100" :aria-label="label">
    <div class="usn-progress__head" v-if="label || showValue">
      <span v-if="label" class="usn-progress__label">{{ label }}</span>
      <span v-if="showValue" class="usn-progress__value">{{ clamped }}%</span>
    </div>
    <div class="usn-progress__track">
      <div class="usn-progress__fill" :style="{ width: `${clamped}%` }" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: Number, default: 0 },
  label: { type: String, default: '' },
  showValue: { type: Boolean, default: true }
})

const clamped = computed(() => Math.max(0, Math.min(100, Math.round(Number(props.value) || 0))))
</script>

<style scoped>
.usn-progress {
  width: 100%;
}

.usn-progress__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--usn-space-2);
  margin-bottom: var(--usn-space-1);
}

.usn-progress__label {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.usn-progress__value {
  font-size: var(--usn-font-size-help);
  font-weight: 700;
  color: var(--usn-purple-600);
  font-variant-numeric: tabular-nums;
}

.usn-progress__track {
  width: 100%;
  height: 8px;
  background: var(--usn-line);
  border-radius: 999px;
  overflow: hidden;
}

.usn-progress__fill {
  height: 100%;
  background: var(--usn-purple-600);
  border-radius: 999px;
  transition: width 0.3s ease;
}
</style>