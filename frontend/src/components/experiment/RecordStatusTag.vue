<template>
  <el-tag :type="meta.tagType" size="small" effect="light" class="record-status">{{ meta.label }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { recordStatusMeta } from '@/api/experimentRecords'

const props = defineProps({
  status: { type: String, default: 'DRAFT' }
})

const meta = computed(() => recordStatusMeta(props.status))
</script>
