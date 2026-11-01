<template>
  <section class="gallery-panel data-panel">
    <header class="gallery-header">
      <div>
        <h2 class="panel-title">图片与占位</h2>
        <p class="text-help muted">JPEG/PNG、≤5 MiB、4096×4096，每型号最多 5 张；替换生成新 mediaId，删除仅逻辑留痕</p>
      </div>
      <div class="gallery-actions" v-if="canManage">
        <el-button
          type="primary"
          :icon="Upload"
          :loading="uploading"
          :disabled="images.length >= 5 || inactive"
          @click="openUpload"
        >
          上传图片
        </el-button>
        <span v-if="images.length >= 5" class="text-help muted">已达 5 张上限</span>
      </div>
    </header>

    <el-alert
      v-if="inactive"
      type="info"
      show-icon
      :closable="false"
      class="gallery-alert"
      title="型号已停用：图片区只读，恢复型号后才能上传、替换、删除或排序"
    />

    <div v-if="images.length" class="gallery-grid">
      <article v-for="(media, index) in images" :key="media.mediaId" class="gallery-item">
        <div class="gallery-item__media">
          <ComponentImage
            :media-id="media.mediaId"
            :placeholder-key="placeholderKey"
            :alt="`${media.originalFileName} 预览`"
            ratio="4 / 3"
          />
          <el-tag
            v-if="media.role === MEDIA_ROLE.PRIMARY"
            class="gallery-item__badge"
            type="success"
            size="small"
            effect="dark"
          >
            主图
          </el-tag>
        </div>

        <div class="gallery-item__info">
          <strong class="gallery-item__name">{{ media.originalFileName }}</strong>
          <span class="text-help muted">
            mediaId {{ media.mediaId }} · {{ media.width }}×{{ media.height }} · {{ formatSize(media.size) }}
          </span>
          <span class="text-help muted">上传 {{ formatDateTime(media.uploadedAt) }} · v{{ media.version }}</span>
        </div>

        <div v-if="canManage && !inactive" class="gallery-item__actions">
          <el-button size="small" :disabled="media.role === MEDIA_ROLE.PRIMARY" @click="setPrimary(media)">设为主图</el-button>
          <el-button size="small" @click="openReplace(media)">替换</el-button>
          <el-button size="small" :disabled="index === 0" @click="move(media, -1)">上移</el-button>
          <el-button size="small" :disabled="index === images.length - 1" @click="move(media, 1)">下移</el-button>
          <el-button size="small" type="danger" plain @click="remove(media)">删除</el-button>
        </div>
      </article>
    </div>

    <div v-else class="gallery-empty">
      <ComponentImage
        :media-id="null"
        :placeholder-key="placeholderKey"
        :alt="`${placeholderLabel} 无图占位`"
        ratio="16 / 9"
        show-label
      />
      <div class="gallery-empty__hint">
        <strong>当前使用确定性占位</strong>
        <span class="text-help muted">
          placeholderKey：<code>{{ placeholderKey || '—' }}</code>
        </span>
        <span class="text-help muted">上传第一张图片后自动成为主图；删除最后一张会回到同一占位，不改变布局尺寸。</span>
        <el-button v-if="canManage && !inactive" type="primary" plain :icon="Upload" @click="openUpload">
          上传第一张图片
        </el-button>
      </div>
    </div>

    <input
      ref="fileInput"
      type="file"
      accept="image/jpeg,image/png"
      class="hidden-input"
      @change="handleFilePicked"
    />
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  uploadComponentImage,
  replaceComponentImage,
  setPrimaryComponentImage,
  reorderComponentImages,
  deleteComponentImage,
  MEDIA_ROLE
} from '@/api/components'
import {
  notifyApiError,
  errorTraceId,
  isVersionConflict,
  isMediaUnsupported,
  isMediaLimitExceeded,
  promptVersionConflict
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'
import ComponentImage from './ComponentImage.vue'

const props = defineProps({
  componentId: { type: Number, required: true },
  componentVersion: { type: Number, default: 0 },
  primaryMediaId: { type: Number, default: null },
  placeholderKey: { type: String, default: '' },
  placeholderLabel: { type: String, default: '元器件' },
  images: { type: Array, default: () => [] },
  canManage: { type: Boolean, default: false },
  inactive: { type: Boolean, default: false }
})

const emit = defineEmits(['updated', 'conflict'])

const fileInput = ref(null)
const uploading = ref(false)
const replaceTarget = ref(null)
const localImages = ref([])
const localVersion = ref(0)

watch(
  () => props.images,
  (value) => { localImages.value = Array.isArray(value) ? value.slice() : [] },
  { immediate: true }
)
watch(
  () => props.componentVersion,
  (value) => { localVersion.value = Number(value) || 0 },
  { immediate: true }
)

const images = computed(() => localImages.value)

function formatSize(size) {
  const value = Number(size || 0)
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function openUpload() {
  if (uploading.value || props.inactive) return
  replaceTarget.value = null
  fileInput.value?.click()
}

function openReplace(media) {
  if (uploading.value || props.inactive) return
  replaceTarget.value = media
  fileInput.value?.click()
}

function validateFile(file) {
  if (!file) return '请选择图片文件'
  if (!['image/jpeg', 'image/png'].includes(file.type)) return '仅支持 JPEG/PNG 图片'
  if (Number(file.size) > 5 * 1024 * 1024) return '图片不能超过 5 MiB'
  return ''
}

async function handleFilePicked(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  const message = validateFile(file)
  if (message) {
    ElMessage.warning(message)
    replaceTarget.value = null
    return
  }

  uploading.value = true
  const target = replaceTarget.value
  replaceTarget.value = null
  try {
    const payload = target
      ? await replaceComponentImage(
          props.componentId,
          target.mediaId,
          { file, componentVersion: localVersion.value, mediaVersion: target.version },
          createIdempotencyKey('w44-media')
        )
      : await uploadComponentImage(
          props.componentId,
          { file, componentVersion: localVersion.value, sortOrder: (images.value.length + 1) * 10 },
          createIdempotencyKey('w44-media')
        )
    const uploadedAt = payload.media?.uploadedAt ? ` · ${formatDateTime(payload.media.uploadedAt)}` : ''
    ElMessage.success(
      `${target ? '图片已替换，旧媒体已逻辑删除' : '图片已上传'}，mediaId ${payload.media.mediaId}${uploadedAt}`
    )
    emit('updated', payload)
  } catch (err) {
    if (isVersionConflict(err)) {
      promptVersionConflict(() => emit('conflict'))
      return
    }
    if (isMediaUnsupported(err)) {
      ElMessage.error('图片不符合类型、大小或解码限制')
      return
    }
    if (isMediaLimitExceeded(err)) {
      ElMessage.error('每个型号最多 5 张 READY 图片')
      return
    }
    const traceId = errorTraceId(err)
    notifyApiError(err)
    if (traceId) ElMessage.info(`traceId：${traceId}`)
  } finally {
    uploading.value = false
  }
}

async function setPrimary(media) {
  try {
    const payload = await setPrimaryComponentImage(props.componentId, media.mediaId, {
      componentVersion: localVersion.value,
      mediaVersion: media.version
    })
    ElMessage.success(`已将 mediaId ${media.mediaId} 设为主图`)
    emit('updated', payload)
  } catch (err) {
    if (isVersionConflict(err)) {
      promptVersionConflict(() => emit('conflict'))
      return
    }
    notifyApiError(err)
  }
}

async function move(media, direction) {
  const index = images.value.findIndex((item) => item.mediaId === media.mediaId)
  const targetIndex = index + direction
  if (index < 0 || targetIndex < 0 || targetIndex >= images.value.length) return
  const ordered = images.value.slice()
  const [removed] = ordered.splice(index, 1)
  ordered.splice(targetIndex, 0, removed)
  try {
    const payload = await reorderComponentImages(props.componentId, {
      componentVersion: localVersion.value,
      items: ordered.map((item, i) => ({ mediaId: item.mediaId, mediaVersion: item.version, sortOrder: (i + 1) * 10 }))
    })
    ElMessage.success('图片顺序已更新')
    emit('updated', payload)
  } catch (err) {
    if (isVersionConflict(err)) {
      promptVersionConflict(() => emit('conflict'))
      return
    }
    notifyApiError(err)
  }
}

async function remove(media) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      '删除为逻辑删除并保留审计，请填写删除原因（1-500 字符）。',
      `删除图片 ${media.originalFileName}`,
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：图片模糊，替换为清晰版本',
        inputValidator: (value) => {
          const text = String(value || '').trim()
          if (text.length < 1 || text.length > 500) return '删除原因需为 1-500 字符'
          return true
        },
        type: 'warning'
      }
    )
    reason = String(result.value || '').trim()
  } catch {
    return
  }
  try {
    const payload = await deleteComponentImage(props.componentId, media.mediaId, {
      componentVersion: localVersion.value,
      mediaVersion: media.version,
      reason
    })
    ElMessage.success(images.value.length <= 1 ? '图片已删除，已回到确定性占位' : '图片已删除')
    emit('updated', payload)
  } catch (err) {
    if (isVersionConflict(err)) {
      promptVersionConflict(() => emit('conflict'))
      return
    }
    notifyApiError(err)
  }
}
</script>

<style scoped>
.gallery-panel {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.gallery-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-4);
  flex-wrap: wrap;
}

.panel-title {
  margin: 0 0 4px;
  font-size: var(--usn-font-size-panel-title);
  font-weight: 700;
  color: var(--usn-ink-900);
}

.gallery-actions {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
}

.gallery-alert {
  margin: 0;
}

.gallery-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--usn-space-4);
}

.gallery-item {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  padding: var(--usn-space-3);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  background: var(--usn-canvas);
}

.gallery-item__media {
  position: relative;
}

.gallery-item__badge {
  position: absolute;
  left: 8px;
  top: 8px;
}

.gallery-item__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.gallery-item__name {
  font-size: var(--usn-font-size-body);
  color: var(--usn-ink-900);
  overflow-wrap: anywhere;
}

.gallery-item__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--usn-space-2);
  margin-top: auto;
}

.gallery-empty {
  display: grid;
  grid-template-columns: minmax(220px, 320px) 1fr;
  gap: var(--usn-space-4);
  align-items: center;
  padding: var(--usn-space-4);
  border: 1px dashed var(--usn-line);
  border-radius: var(--usn-radius-md);
}

.gallery-empty__hint {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  align-items: flex-start;
}

.gallery-empty__hint code {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  color: var(--usn-blue-700);
  overflow-wrap: anywhere;
}

.hidden-input {
  display: none;
}

@media (max-width: 900px) {
  .gallery-grid {
    grid-template-columns: 1fr;
  }

  .gallery-empty {
    grid-template-columns: 1fr;
  }
}
</style>
