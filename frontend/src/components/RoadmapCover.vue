<template>
  <span class="roadmap-cover" :style="{ background: meta.bg, color: meta.fg }" :aria-label="`路线封面 ${title}`">
    {{ meta.initial }}
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { roadmapCoverMeta } from '@/utils/roadmapCover'

const props = defineProps({
  title: { type: String, default: '' },
  size: { type: Number, default: 40 }
})

const meta = computed(() => roadmapCoverMeta(props.title))
const sizePx = computed(() => `${props.size}px`)
</script>

<style scoped>
.roadmap-cover {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: v-bind(sizePx);
  height: v-bind(sizePx);
  border-radius: var(--usn-radius-md);
  font-weight: 700;
  font-size: calc(v-bind(sizePx) * 0.45);
  flex-shrink: 0;
  user-select: none;
}
</style>