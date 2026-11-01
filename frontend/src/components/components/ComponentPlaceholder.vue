<template>
  <div
    class="component-placeholder"
    :class="{ 'component-placeholder--compact': compact }"
    :style="containerStyle"
    role="img"
    :aria-label="`${label}，无图片占位`"
  >
    <svg class="component-placeholder__art" viewBox="0 0 160 120" aria-hidden="true">
      <defs>
        <linearGradient :id="gradientId" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" :stop-color="display.palette.bg" />
          <stop offset="100%" :stop-color="display.palette.accent" stop-opacity="0.18" />
        </linearGradient>
      </defs>
      <rect x="0" y="0" width="160" height="120" :fill="`url(#${gradientId})`" />
      <rect
        x="14"
        y="16"
        width="132"
        height="88"
        rx="10"
        fill="var(--usn-surface)"
        fill-opacity="0.72"
        :stroke="display.palette.accent"
        stroke-opacity="0.32"
      />
      <path d="M30 74 L60 44 L90 74 L118 50" fill="none" :stroke="display.palette.accent" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" opacity="0.5" />
      <text x="80" y="58" text-anchor="middle" :fill="display.palette.fg" font-size="26" font-weight="700" font-family="'Microsoft YaHei', sans-serif">
        {{ display.glyph }}
      </text>
      <text x="80" y="82" text-anchor="middle" :fill="display.palette.fg" font-size="11" font-family="'Microsoft YaHei', sans-serif" opacity="0.85">
        {{ display.packageType }}
      </text>
      <text x="80" y="96" text-anchor="middle" :fill="display.palette.fg" font-size="9" font-family="'Microsoft YaHei', sans-serif" opacity="0.7">
        {{ display.category }}
      </text>
    </svg>
    <span v-if="showLabel" class="component-placeholder__label">无图 · 确定性占位</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { placeholderDisplay } from '@/api/components'

const props = defineProps({
  placeholderKey: { type: String, default: '' },
  ratio: { type: String, default: '4 / 3' },
  compact: { type: Boolean, default: false },
  showLabel: { type: Boolean, default: false },
  label: { type: String, default: '元器件' }
})

const display = computed(() => placeholderDisplay(props.placeholderKey))
const containerStyle = computed(() => ({
  aspectRatio: props.ratio,
  background: display.value.palette.bg
}))
const gradientId = computed(() => {
  let hash = 0
  const key = String(props.placeholderKey || '')
  for (let i = 0; i < key.length; i++) hash = (hash * 31 + key.charCodeAt(i)) >>> 0
  return `placeholder-gradient-${hash}`
})
</script>

<style scoped>
.component-placeholder {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: var(--usn-radius-md);
  border: 1px solid var(--usn-line);
  display: flex;
  align-items: center;
  justify-content: center;
  /* 固定比例保证图片缺失时布局不跳动 */
  min-height: 96px;
}

.component-placeholder__art {
  width: 100%;
  height: 100%;
  display: block;
}

.component-placeholder__label {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--usn-ink-500);
  font-size: var(--usn-font-size-help);
  line-height: 1.6;
}

.component-placeholder--compact {
  min-height: 48px;
}

.component-placeholder--compact .component-placeholder__label {
  display: none;
}
</style>
