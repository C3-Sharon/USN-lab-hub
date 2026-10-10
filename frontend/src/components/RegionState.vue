<template>
  <div class="region-state" :class="`region-state--${state}`">
    <div v-if="state === 'loading'" class="region-state__skeleton">
      <div v-if="variant === 'card'" class="skeleton-card">
        <el-skeleton :rows="3" animated />
      </div>
      <div v-else-if="variant === 'table'" class="skeleton-table">
        <el-skeleton :rows="5" animated />
      </div>
      <div v-else-if="variant === 'list'" class="skeleton-list">
        <el-skeleton :rows="3" animated />
      </div>
      <div v-else class="skeleton-section">
        <el-skeleton :rows="4" animated />
      </div>
    </div>

    <div v-else-if="state === 'empty'" class="region-state__hint">
      <el-empty :description="description || '暂无数据'" :image-size="imageSize" />
    </div>

    <div v-else-if="state === 'not-available'" class="region-state__hint">
      <el-empty :description="description || '尚未开放'" :image-size="imageSize">
        <template #image>
          <div class="region-state__icon">
            <el-icon :size="iconSize"><Clock /></el-icon>
          </div>
        </template>
      </el-empty>
    </div>

    <div v-else-if="state === 'permission'" class="region-state__hint">
      <el-result icon="warning" :title="description || '无权访问'" :sub-title="subTitle">
        <template #extra>
          <el-button type="primary" @click="$emit('contact')">联系管理员</el-button>
        </template>
      </el-result>
    </div>

    <div v-else-if="state === 'offline'" class="region-state__hint">
      <el-result icon="info" :title="description || '网络不可用'" :sub-title="subTitle || '已切换到本地缓存视图，请检查网络后重试'">
        <template #extra>
          <el-button @click="$emit('retry')">重新加载</el-button>
        </template>
      </el-result>
    </div>

    <div v-else-if="state === 'error'" class="region-state__hint">
      <el-result icon="error" :title="description || '加载失败'" :sub-title="subTitle || errorMessage">
        <template #extra>
          <el-button type="primary" @click="$emit('retry')">重试</el-button>
        </template>
      </el-result>
    </div>

    <div v-else class="region-state__content">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Clock } from '@element-plus/icons-vue'

const props = defineProps({
  state: {
    type: String,
    default: 'success',
    validator: (v) => ['loading', 'empty', 'error', 'permission', 'success', 'offline', 'not-available'].includes(v)
  },
  variant: {
    type: String,
    default: 'card',
    validator: (v) => ['card', 'table', 'list', 'section'].includes(v)
  },
  description: { type: String, default: '' },
  subTitle: { type: String, default: '' },
  errorMessage: { type: String, default: '' }
})

defineEmits(['retry', 'contact'])

const imageSize = computed(() => (props.state === 'not-available' ? 72 : 96))
const iconSize = computed(() => (props.state === 'not-available' ? 56 : 64))
</script>

<style scoped>
.region-state {
  min-height: 120px;
  display: flex;
  align-items: stretch;
  justify-content: center;
  width: 100%;
}

.region-state__skeleton {
  width: 100%;
  padding: var(--usn-space-4);
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  min-height: 132px;
}

.skeleton-card,
.skeleton-table,
.skeleton-list,
.skeleton-section {
  min-height: 96px;
}

.region-state__hint {
  width: 100%;
  padding: var(--usn-space-4);
  background: var(--usn-surface);
  border: 1px dashed var(--usn-line);
  border-radius: var(--usn-radius-md);
  min-height: 132px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.region-state__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--usn-blue-100);
  color: var(--usn-blue-700);
}

.region-state__content {
  width: 100%;
}
</style>
