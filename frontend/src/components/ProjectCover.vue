<template>
  <span class="project-cover" :style="{ background: meta.bg, color: meta.fg }" :aria-label="`项目封面 ${code}`">
    {{ meta.initial }}
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { projectCoverMeta } from '@/utils/projectCover'

const props = defineProps({
  code: { type: String, default: '' },
  size: { type: Number, default: 40 }
})

const meta = computed(() => projectCoverMeta(props.code))
const sizePx = computed(() => `${props.size}px`)
</script>

<style scoped>
.project-cover {
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
