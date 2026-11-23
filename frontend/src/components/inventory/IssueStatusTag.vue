<template>
  <el-tag :type="meta.tagType" :size="size" effect="plain">{{ meta.label }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { issueStatusMeta } from '@/api/inventoryIssuance'

const props = defineProps({
  status: { type: String, default: '' },
  size: { type: String, default: 'default' }
})

const meta = computed(() => issueStatusMeta(props.status))
</script>
