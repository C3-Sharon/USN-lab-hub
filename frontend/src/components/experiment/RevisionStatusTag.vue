<template>
  <el-tag :type="meta.tagType" size="small" effect="light" class="revision-status">{{ meta.label }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { revisionStatusMeta } from '@/api/experimentRecords'

const props = defineProps({
  status: { type: String, default: 'EDITABLE' }
})

const meta = computed(() => revisionStatusMeta(props.status))
</script>
