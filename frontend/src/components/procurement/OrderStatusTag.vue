<template>
  <el-tag :type="meta.tagType" :size="size" :effect="effect">{{ meta.label }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { orderStatusMeta } from '@/api/procurement'

const props = defineProps({
  status: { type: String, default: '' },
  size: { type: String, default: 'default' },
  effect: { type: String, default: 'light' }
})

const meta = computed(() => orderStatusMeta(props.status))
</script>
