<template>
  <section v-if="visible" class="attachment-panel data-panel">
    <header class="attachment-panel__header">
      <div>
        <h2 class="section-title">{{ title }}</h2>
        <p class="text-help muted">
          {{ countText }} · 仅返回 mediaId 与受控元数据，不展示存储路径或签名 URL
        </p>
      </div>
      <el-button
        v-if="canUpload"
        type="primary"
        plain
        :icon="Upload"
        :disabled="uploading"
        @click="uploadVisible = !uploadVisible"
      >
        上传附件
      </el-button>
    </header>

    <el-alert
      v-if="uploading"
      type="info"
      :closable="false"
      show-icon
      title="附件上传中：审批、下单与到货确认已暂时锁定，避免携带旧 version 并行提交"
      class="attachment-panel__busy"
    />

    <section v-if="uploadVisible && canUpload" class="attachment-upload">
      <div class="attachment-upload__grid">
        <label class="field-block">
          <span class="field-label">用途</span>
          <el-select v-model="uploadForm.purpose" size="default" style="width: 100%">
            <el-option
              v-for="option in purposeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </label>
        <label v-if="uploadForm.purpose === 'INVOICE'" class="field-block">
          <span class="field-label">发票号</span>
          <el-input v-model="uploadForm.invoiceNo" maxlength="64" show-word-limit placeholder="发票号必填" />
        </label>
        <label class="field-block">
          <span class="field-label">说明</span>
          <el-input v-model="uploadForm.note" maxlength="200" show-word-limit placeholder="可选，200 字以内" />
        </label>
        <label class="field-block">
          <span class="field-label">图片（JPEG/PNG，≤10MB）</span>
          <input ref="fileInput" class="native-file" type="file" accept="image/jpeg,image/png" @change="onFilePicked" />
        </label>
      </div>
      <p v-if="uploadForm.file" class="text-help muted">
        已选择 {{ uploadForm.file.name }} · {{ formatFileSize(uploadForm.file.size) }}
      </p>
      <div class="attachment-upload__actions">
        <el-button type="primary" :loading="uploading" @click="submitUpload">
          上传并刷新 version
        </el-button>
        <el-button :disabled="uploading" @click="resetUpload">取消</el-button>
      </div>
    </section>

    <div v-if="loading" class="attachment-loading">
      <el-skeleton :rows="2" animated />
    </div>

    <el-alert
      v-else-if="errorMessage"
      type="error"
      :closable="false"
      show-icon
      :title="`附件加载失败：${errorMessage}${traceId ? ` · traceId ${traceId}` : ''}`"
    >
      <el-button text type="primary" @click="load">重试</el-button>
    </el-alert>

    <p v-else-if="!list.length" class="attachment-empty text-help muted">
      暂无附件。附件缺失不影响审批与入库数量。
    </p>

    <div v-else class="attachment-grid">
      <article v-for="item in list" :key="item.id" class="attachment-card">
        <div class="attachment-card__preview" @click="openPreview(item)">
          <el-icon><Picture /></el-icon>
          <span>{{ item.originalFileName }}</span>
        </div>
        <div class="attachment-card__body">
          <div class="attachment-card__tags">
            <el-tag size="small">{{ purposeLabel(item.purpose) }}</el-tag>
            <el-tag v-if="item.invoiceNo" size="small" type="success" effect="plain">
              发票号 {{ item.invoiceNo }}
            </el-tag>
            <el-tag size="small" type="info" effect="plain">v{{ item.version }}</el-tag>
          </div>
          <p v-if="item.note" class="attachment-card__note">{{ item.note }}</p>
          <p class="text-help muted">
            {{ item.contentType }} · {{ formatFileSize(item.size) }} · {{ item.width }}×{{ item.height }}
          </p>
          <p class="text-help muted">上传人 ID {{ item.createdBy }} · {{ formatDateTime(item.createTime) }}</p>
        </div>
        <div v-if="canUpload" class="attachment-card__actions">
          <el-button text size="small" :disabled="uploading" @click="openReplace(item)">替换</el-button>
          <el-button text size="small" type="danger" :disabled="uploading" @click="remove(item)">删除</el-button>
        </div>
      </article>
    </div>

    <el-dialog v-model="previewVisible" :title="previewTitle" width="min(720px, 92vw)" @closed="closePreview">
      <div v-if="previewLoading" class="attachment-preview__loading">
        <el-skeleton :rows="4" animated />
      </div>
      <el-alert
        v-else-if="previewError"
        type="error"
        :closable="false"
        show-icon
        :title="previewError"
      />
      <img v-else-if="previewUrl" class="attachment-preview__image" :src="previewUrl" :alt="previewTitle" />
    </el-dialog>

    <el-dialog v-model="replaceVisible" title="替换附件" width="min(520px, 92vw)">
      <p class="text-help muted">
        当前 mediaId {{ replaceTarget?.mediaId }}，替换后会生成新 mediaId 并递增附件与归属记录 version。
      </p>
      <input class="native-file" type="file" accept="image/jpeg,image/png" @change="onReplaceFilePicked" />
      <template #footer>
        <el-button @click="replaceVisible = false">取消</el-button>
        <el-button type="primary" :loading="replaceBusy" @click="submitReplace">确认替换</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import {
  ATTACHMENT_LIMITS,
  ATTACHMENT_OWNER_TYPE,
  ATTACHMENT_PURPOSE,
  attachmentPurposeMeta,
  deleteAttachment,
  getAttachmentContent,
  listOrderAttachments,
  listReceiptAttachments,
  normalizeAttachmentList,
  replaceAttachment,
  uploadOrderAttachment,
  uploadReceiptAttachment
} from '@/api/procurement'
import { createIdempotencyKey } from '@/utils/idempotency'
import { isProcurementAttachmentHidden, isProcurementVersionConflict, normalizeApiError } from '@/utils/apiError'

const props = defineProps({
  ownerType: { type: String, required: true },
  ownerId: { type: [Number, String], required: true },
  ownerVersion: { type: Number, default: 0 },
  canView: { type: Boolean, default: true },
  canUpload: { type: Boolean, default: false },
  title: { type: String, default: '附件' }
})

const emit = defineEmits(['owner-version', 'busy', 'refresh'])

const list = ref([])
const loading = ref(false)
const hidden = ref(false)
const errorMessage = ref('')
const traceId = ref('')
const uploadVisible = ref(false)
const uploading = ref(false)
const fileInput = ref(null)
const localOwnerVersion = ref(props.ownerVersion)
const uploadForm = reactive({ purpose: '', invoiceNo: '', note: '', file: null })
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewError = ref('')
const previewUrl = ref('')
const previewTitle = ref('')
const replaceVisible = ref(false)
const replaceTarget = ref(null)
const replaceFile = ref(null)
const replaceBusy = ref(false)

let uploadKey = null
let replaceKey = null

const visible = computed(() => props.canView && !hidden.value)
const countText = computed(() => `${list.value.length} 个可见附件`)

const purposeOptions = computed(() => {
  const values = props.ownerType === ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER
    ? [ATTACHMENT_PURPOSE.ORDER_SCREENSHOT, ATTACHMENT_PURPOSE.INVOICE]
    : [ATTACHMENT_PURPOSE.RECEIPT_PHOTO, ATTACHMENT_PURPOSE.PACKAGE_PHOTO]
  return values.map((value) => ({ value, label: attachmentPurposeMeta(value).label }))
})

watch(() => props.ownerVersion, (value) => {
  localOwnerVersion.value = value
})

watch(() => [props.ownerId, props.ownerType, props.canView], () => {
  hidden.value = false
  load()
})

function purposeLabel(purpose) {
  return attachmentPurposeMeta(purpose).label
}

function formatFileSize(size) {
  const bytes = Number(size || 0)
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${bytes} B`
}

function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function setBusy(value) {
  emit('busy', value)
}

async function load() {
  if (!props.canView) {
    list.value = []
    return
  }
  loading.value = true
  errorMessage.value = ''
  traceId.value = ''
  try {
    const response = props.ownerType === ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER
      ? await listOrderAttachments(props.ownerId)
      : await listReceiptAttachments(props.ownerId)
    list.value = normalizeAttachmentList(response)
  } catch (error) {
    if (isProcurementAttachmentHidden(error)) {
      // 不可见 owner：不渲染任何数量、文件名、mediaId 或存在性提示
      hidden.value = true
      list.value = []
    } else {
      const normalized = normalizeApiError(error)
      errorMessage.value = normalized.msg || '附件加载失败'
      traceId.value = normalized.traceId || ''
    }
  } finally {
    loading.value = false
  }
}

function onFilePicked(event) {
  const file = event.target.files?.[0] || null
  uploadForm.file = file
}

function resetUpload() {
  uploadVisible.value = false
  uploadForm.purpose = purposeOptions.value[0]?.value || ''
  uploadForm.invoiceNo = ''
  uploadForm.note = ''
  uploadForm.file = null
  uploadKey = null
  if (fileInput.value) fileInput.value.value = ''
}

function validateFile(file) {
  if (!file) {
    ElMessage.warning('请选择 JPEG/PNG 图片')
    return false
  }
  if (!ATTACHMENT_LIMITS.CONTENT_TYPES.includes(file.type)) {
    ElMessage.warning('附件仅支持 JPEG/PNG')
    return false
  }
  if (file.size > ATTACHMENT_LIMITS.MAX_BYTES) {
    ElMessage.warning('单个附件不能超过 10MB')
    return false
  }
  return true
}

async function submitUpload() {
  if (uploading.value) return
  if (!uploadForm.purpose) {
    ElMessage.warning('请选择附件用途')
    return
  }
  if (!validateFile(uploadForm.file)) return
  if (uploadForm.purpose === ATTACHMENT_PURPOSE.INVOICE && !uploadForm.invoiceNo.trim()) {
    ElMessage.warning('发票附件必须填写发票号')
    return
  }
  if (!uploadKey) uploadKey = createIdempotencyKey('w45-attachment')
  uploading.value = true
  setBusy(true)
  try {
    const payload = {
      file: uploadForm.file,
      purpose: uploadForm.purpose,
      invoiceNo: uploadForm.invoiceNo.trim(),
      note: uploadForm.note.trim(),
      ownerVersion: localOwnerVersion.value
    }
    const result = props.ownerType === ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER
      ? await uploadOrderAttachment(props.ownerId, payload, uploadKey)
      : await uploadReceiptAttachment(props.ownerId, payload, uploadKey)
    uploadKey = null
    const nextVersion = Number(result?.ownerVersion ?? localOwnerVersion.value + 1)
    localOwnerVersion.value = nextVersion
    emit('owner-version', nextVersion)
    ElMessage.success(`附件已上传，mediaId ${result?.mediaId}，归属 version ${nextVersion}`)
    resetUpload()
    await load()
  } catch (error) {
    const normalized = normalizeApiError(error)
    if (isProcurementVersionConflict(error)) {
      ElMessage.warning('版本已变化，已保留表单；请刷新对比后再提交')
      emit('refresh')
    } else {
      ElMessage.error(`${normalized.msg || '附件上传失败'}${normalized.traceId ? ` · traceId ${normalized.traceId}` : ''}`)
    }
  } finally {
    uploading.value = false
    setBusy(false)
  }
}

function openReplace(item) {
  replaceTarget.value = item
  replaceFile.value = null
  replaceKey = null
  replaceVisible.value = true
}

function onReplaceFilePicked(event) {
  replaceFile.value = event.target.files?.[0] || null
}

async function submitReplace() {
  if (replaceBusy.value || !replaceTarget.value) return
  if (!validateFile(replaceFile.value)) return
  if (!replaceKey) replaceKey = createIdempotencyKey('w45-replace')
  replaceBusy.value = true
  setBusy(true)
  try {
    const result = await replaceAttachment(replaceTarget.value.id, {
      file: replaceFile.value,
      attachmentVersion: replaceTarget.value.version,
      mediaVersion: replaceTarget.value.mediaVersion,
      ownerVersion: localOwnerVersion.value
    }, replaceKey)
    replaceKey = null
    const nextVersion = Number(result?.ownerVersion ?? localOwnerVersion.value + 1)
    localOwnerVersion.value = nextVersion
    emit('owner-version', nextVersion)
    ElMessage.success(`附件已替换，新 mediaId ${result?.mediaId}，归属 version ${nextVersion}`)
    replaceVisible.value = false
    await load()
  } catch (error) {
    const normalized = normalizeApiError(error)
    if (isProcurementVersionConflict(error)) {
      ElMessage.warning(`版本冲突，currentVersion ${normalized.data?.currentVersion ?? '未知'}；请刷新后重试`)
      emit('refresh')
    } else {
      ElMessage.error(`${normalized.msg || '附件替换失败'}${normalized.traceId ? ` · traceId ${normalized.traceId}` : ''}`)
    }
  } finally {
    replaceBusy.value = false
    setBusy(false)
  }
}

async function remove(item) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请填写删除原因（1-200 字）', `删除附件 ${item.originalFileName}`, {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      inputValidator: (value) => (String(value || '').trim() ? true : '删除原因不能为空'),
      type: 'warning'
    })
    reason = String(result.value || '').trim()
  } catch {
    return
  }
  const key = createIdempotencyKey('w45-delete')
  setBusy(true)
  try {
    const result = await deleteAttachment(item.id, {
      attachmentVersion: item.version,
      mediaVersion: item.mediaVersion,
      ownerVersion: localOwnerVersion.value,
      reason
    }, key)
    const nextVersion = Number(result?.ownerVersion ?? localOwnerVersion.value + 1)
    localOwnerVersion.value = nextVersion
    emit('owner-version', nextVersion)
    ElMessage.success(`附件已删除，归属 version ${nextVersion}`)
    await load()
  } catch (error) {
    const normalized = normalizeApiError(error)
    if (isProcurementVersionConflict(error)) {
      ElMessage.warning(`版本冲突，currentVersion ${normalized.data?.currentVersion ?? '未知'}；请刷新后重试`)
      emit('refresh')
    } else {
      ElMessage.error(`${normalized.msg || '附件删除失败'}${normalized.traceId ? ` · traceId ${normalized.traceId}` : ''}`)
    }
  } finally {
    setBusy(false)
  }
}

async function openPreview(item) {
  previewTitle.value = `${purposeLabel(item.purpose)} · ${item.originalFileName}`
  previewVisible.value = true
  previewLoading.value = true
  previewError.value = ''
  previewUrl.value = ''
  try {
    previewUrl.value = await getAttachmentContent(item.mediaId)
  } catch {
    previewError.value = '附件内容不可见或加载失败'
  } finally {
    previewLoading.value = false
  }
}

function closePreview() {
  if (previewUrl.value && previewUrl.value.startsWith('blob:')) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ''
  previewError.value = ''
}

onMounted(() => {
  uploadForm.purpose = purposeOptions.value[0]?.value || ''
  load()
})

onBeforeUnmount(closePreview)
</script>

<style scoped>
.attachment-panel { display: flex; flex-direction: column; gap: 16px; }
.attachment-panel__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.attachment-panel__busy { margin: 0; }
.attachment-upload { border: 1px dashed var(--border-color, #dcdfe6); border-radius: 8px; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.attachment-upload__grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; }
.attachment-upload__actions { display: flex; gap: 8px; flex-wrap: wrap; }
.field-block { display: flex; flex-direction: column; gap: 6px; }
.field-label { font-size: 13px; color: var(--text-secondary, #606266); }
.native-file { font-size: 13px; max-width: 100%; }
.attachment-loading { padding: 8px 0; }
.attachment-empty { padding: 8px 0; }
.attachment-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px; }
.attachment-card { border: 1px solid var(--border-color, #ebeef5); border-radius: 8px; padding: 12px; display: flex; flex-direction: column; gap: 8px; }
.attachment-card__preview { display: flex; align-items: center; gap: 8px; cursor: pointer; color: var(--primary-color, #409eff); font-size: 13px; word-break: break-all; }
.attachment-card__tags { display: flex; gap: 6px; flex-wrap: wrap; }
.attachment-card__note { margin: 0; font-size: 13px; }
.attachment-card__actions { display: flex; gap: 4px; justify-content: flex-end; }
.attachment-preview__image { width: 100%; max-height: 70vh; object-fit: contain; background: #f5f7fa; }
.attachment-preview__loading { padding: 16px; }
@media (max-width: 767px) {
  .attachment-grid { grid-template-columns: 1fr; }
  .attachment-panel__header { flex-direction: column; }
}
</style>
