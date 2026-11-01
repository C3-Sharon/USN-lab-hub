<template>
  <div class="component-image" :style="{ aspectRatio: ratio }">
    <div v-if="loading" class="component-image__loading" aria-label="图片加载中">
      <el-icon class="is-loading" :size="20"><Loading /></el-icon>
    </div>
    <img
      v-else-if="objectUrl"
      class="component-image__img"
      :src="objectUrl"
      :alt="alt"
      loading="lazy"
    />
    <ComponentPlaceholder
      v-else
      :placeholder-key="placeholderKey"
      :ratio="ratio"
      :compact="compact"
      :show-label="showLabel"
      :label="alt"
    />
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { getMediaContent } from '@/api/components'
import ComponentPlaceholder from './ComponentPlaceholder.vue'

const props = defineProps({
  mediaId: { type: [Number, String], default: null },
  placeholderKey: { type: String, default: '' },
  alt: { type: String, default: '元器件图片' },
  ratio: { type: String, default: '4 / 3' },
  compact: { type: Boolean, default: false },
  showLabel: { type: Boolean, default: false }
})

const loading = ref(false)
const objectUrl = ref('')
let revokeUrl = ''

function release() {
  if (revokeUrl && typeof URL !== 'undefined' && URL.revokeObjectURL) {
    URL.revokeObjectURL(revokeUrl)
  }
  revokeUrl = ''
  objectUrl.value = ''
}

async function load(mediaId) {
  release()
  if (!mediaId) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const url = await getMediaContent(mediaId)
    if (typeof url === 'string') {
      objectUrl.value = url
      // 仅回收本次接口创建的 blob URL，mock 的 dataUrl 不需要回收
      if (url.startsWith('blob:') && typeof URL !== 'undefined' && URL.createObjectURL) {
        revokeUrl = url
      }
    }
  } catch {
    // 图片失败回退同一 placeholderKey，保持固定比例
    objectUrl.value = ''
  } finally {
    loading.value = false
  }
}

watch(
  () => props.mediaId,
  (value) => load(value),
  { immediate: true }
)

onBeforeUnmount(release)
</script>

<style scoped>
.component-image {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: var(--usn-radius-md);
  background: var(--usn-canvas);
  display: flex;
  align-items: center;
  justify-content: center;
}

.component-image__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: var(--usn-surface);
  display: block;
}

.component-image__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 96px;
  color: var(--usn-ink-500);
}
</style>
