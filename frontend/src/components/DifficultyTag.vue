<template>
  <span class="difficulty-tag" :class="`difficulty-tag--${meta.tone}`">{{ meta.label }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { difficultyMeta } from '@/api/learning'

const props = defineProps({
  difficulty: { type: String, default: 'BEGINNER' }
})

const meta = computed(() => difficultyMeta(props.difficulty))
</script>

<style scoped>
.difficulty-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: var(--usn-radius-sm);
  font-size: var(--usn-font-size-help);
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

/* 难度标签使用紫色系（学习语义色，小面积识别），与项目域蓝色标签区分 */
.difficulty-tag--beginner {
  background: #f3e8ff;
  color: #6b21a8;
}

.difficulty-tag--intermediate {
  background: #ede9fe;
  color: #5b21b6;
}

.difficulty-tag--advanced {
  background: #e0e7ff;
  color: #4338ca;
}
</style>