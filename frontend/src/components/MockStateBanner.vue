<template>
  <el-alert
    v-if="scenario"
    class="mock-state-banner"
    type="info"
    :closable="false"
    show-icon
  >
    <template #title>
      Mock 状态：{{ meta.label }}（{{ scenario }}）
    </template>
    {{ meta.desc }}。仅用于 mock 构建的人工验收与三视口证据，真实接口忽略该参数。
  </el-alert>
</template>

<script setup>
import { computed } from 'vue'
import { getMockEnabled } from '@/utils/mock'
import { currentMockScenario, MOCK_STATE_META } from '@/utils/mockScenario'

const scenario = computed(() => (getMockEnabled() ? currentMockScenario() : ''))
const meta = computed(() => MOCK_STATE_META[scenario.value] || { label: scenario.value, desc: '' })
</script>

<style scoped>
.mock-state-banner {
  margin-bottom: var(--usn-space-3);
}
</style>
