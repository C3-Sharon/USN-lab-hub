<template>
  <el-tag :type="meta.tagType" size="small" effect="light" class="version-status" :class="{ 'version-status--deprecated': meta.deprecated }">
    {{ meta.label }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { templateVersionStatusMeta } from '@/api/experimentTemplates'

const props = defineProps({
  status: { type: String, default: 'DRAFT' }
})

const meta = computed(() => templateVersionStatusMeta(props.status))
</script>

<style scoped>
/* 已废弃版本：灰色标签（契约 §9.9） */
.version-status--deprecated {
  background: #eef1f4;
  color: var(--usn-ink-500);
  border-color: var(--usn-line);
}
</style>
