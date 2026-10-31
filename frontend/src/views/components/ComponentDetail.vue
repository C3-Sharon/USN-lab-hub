<template>
  <div class="component-detail-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="router.push('/components')">返回元器件目录</el-button>
      <span class="text-help muted">{{ catalogCode || '—' }}</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canAccess"
      state="permission"
      variant="section"
      description="当前账号无权访问元器件目录"
      sub-title="请联系实验室管理员开通目录查看权限"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载型号详情"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="型号详情加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="loadDetail"
    />

    <RegionState
      v-else-if="!component"
      state="permission"
      variant="section"
      :description="notFoundText"
      sub-title="型号不存在、已停用或当前账号不可见，接口统一返回 404 以隐藏存在性"
    />

    <template v-else>
      <section class="detail-hero data-panel" :class="{ 'detail-hero--inactive': isInactive }">
        <div class="detail-hero__media">
          <ComponentImage
            :media-id="component.primaryMediaId"
            :placeholder-key="component.placeholderKey"
            :alt="`${component.mpn} 主图`"
            ratio="4 / 3"
            show-label
          />
          <p class="text-help muted hero-media-note">
            {{ component.primaryMediaId ? `主图 mediaId ${component.primaryMediaId}` : '确定性无图占位，不改变布局尺寸' }}
          </p>
        </div>

        <div class="detail-hero__body">
          <div class="hero-title-row">
            <div>
              <h1 class="page-title">{{ component.name }}</h1>
              <p class="hero-mpn">
                <span class="mpn-text">{{ component.mpn }}</span>
                <span class="text-help muted">normalizedMpn {{ component.normalizedMpn }}</span>
              </p>
            </div>
            <div class="hero-tags">
              <el-tag :type="statusMeta(component.status).tagType" effect="dark">
                {{ statusMeta(component.status).label }}
              </el-tag>
              <el-tag v-if="!component.currentSpecSnapshotId" type="warning" effect="plain" size="small">
                无正式规格快照
              </el-tag>
            </div>
          </div>

          <p v-if="component.description" class="hero-description">{{ component.description }}</p>

          <dl class="hero-meta">
            <div>
              <dt>制造商</dt>
              <dd>{{ component.manufacturerName || '—' }}</dd>
            </div>
            <div>
              <dt>分类</dt>
              <dd>{{ categoryPathText }}</dd>
            </div>
            <div>
              <dt>封装</dt>
              <dd>{{ component.packageType }}</dd>
            </div>
            <div>
              <dt>基础单位</dt>
              <dd>{{ baseUnitMeta(component.baseUnit).label }}</dd>
            </div>
            <div>
              <dt>正式规格版本</dt>
              <dd>{{ component.specVersion ? `v${component.specVersion}` : '无' }}</dd>
            </div>
            <div>
              <dt>当前 version</dt>
              <dd>{{ component.version }}</dd>
            </div>
            <div>
              <dt>最近更新</dt>
              <dd>{{ formatDateTime(component.updateTime) }}</dd>
            </div>
            <div>
              <dt>目录编码</dt>
              <dd>{{ component.catalogCode }}</dd>
            </div>
          </dl>

          <div class="hero-actions">
            <el-button v-if="canManage && !isInactive" type="primary" :icon="Edit" @click="goEdit">编辑核心字段</el-button>
            <el-button v-if="canSpec" plain :icon="Document" @click="goCandidates">规格候选与历史</el-button>
            <el-button v-if="canManage && !isInactive" plain :icon="Collection" @click="manualVisible = true">
              手工录入快照
            </el-button>
            <el-button
              v-if="canManage && component.status === COMPONENT_STATUS.DRAFT"
              type="success"
              plain
              :loading="actionLoading === 'activate'"
              @click="activate"
            >
              激活型号
            </el-button>
            <el-button
              v-if="canManage && !isInactive"
              type="danger"
              plain
              :loading="actionLoading === 'deactivate'"
              @click="deactivate"
            >
              停用型号
            </el-button>
            <el-button
              v-if="canManage && isInactive"
              type="success"
              :loading="actionLoading === 'restore'"
              @click="restore"
            >
              恢复型号
            </el-button>
          </div>

          <el-alert
            v-if="isInactive"
            type="info"
            show-icon
            :closable="false"
            class="hero-alert"
            title="该型号已停用：核心元数据、图片和规格均为只读，仅可恢复"
            description="停用不影响已有采购、BOM、实验或知识引用，它们继续绑定原 ID 与版本。"
          />
          <el-alert
            v-else-if="!canManage"
            type="info"
            show-icon
            :closable="false"
            class="hero-alert"
            :title="isTeacher ? '老师账号：元器件目录全局只读' : '普通成员：仅可查看启用型号与当前正式规格'"
          />
        </div>
      </section>

      <section class="data-panel">
        <header class="section-head">
          <div>
            <h2 class="panel-title">当前正式规格</h2>
            <p class="text-help muted">只展示当前快照；快照一经确认不可修改、不可删除</p>
          </div>
          <el-tag v-if="currentSnapshot" type="success" effect="plain">
            v{{ currentSnapshot.snapshotVersion }}
          </el-tag>
        </header>

        <RegionState v-if="snapshotLoading" state="loading" variant="section" description="正在加载当前规格" />
        <RegionState
          v-else-if="snapshotError"
          state="error"
          variant="section"
          description="当前规格加载失败"
          :error-message="`${snapshotError}${snapshotTraceId ? ` · traceId ${snapshotTraceId}` : ''}`"
          @retry="loadSnapshots"
        />
        <RegionState
          v-else-if="!currentSnapshot"
          state="empty"
          variant="section"
          description="尚无正式规格快照"
          sub-title="DRAFT 型号需先确认外部候选或手工录入快照，才能激活进入正式目录"
        >
          <div class="empty-actions">
            <el-button v-if="canManage" type="primary" @click="manualVisible = true">手工录入快照</el-button>
            <el-button v-if="canSpec" plain @click="goCandidates">登记外部候选</el-button>
          </div>
        </RegionState>
        <template v-else>
          <div class="snapshot-source">
            <div class="source-item">
              <span class="text-help muted">来源类型</span>
              <strong>{{ sourceTypeMeta(currentSnapshot.sourceType).label }}</strong>
            </div>
            <div class="source-item">
              <span class="text-help muted">来源标题</span>
              <strong>{{ currentSnapshot.sourceTitle || '—' }}</strong>
            </div>
            <div class="source-item">
              <span class="text-help muted">来源 URL</span>
              <strong class="break-anywhere">{{ currentSnapshot.sourceUrl || '—' }}</strong>
            </div>
            <div class="source-item">
              <span class="text-help muted">获取时间</span>
              <strong>{{ formatDateTime(currentSnapshot.capturedAt) }}</strong>
            </div>
            <div class="source-item">
              <span class="text-help muted">确认人 / 时间</span>
              <strong>{{ currentSnapshot.confirmedByName || `#${currentSnapshot.confirmedBy}` }} · {{ formatDateTime(currentSnapshot.confirmedAt) }}</strong>
            </div>
            <div class="source-item">
              <span class="text-help muted">来源说明</span>
              <strong class="break-anywhere">{{ currentSnapshot.sourceNote || '—' }}</strong>
            </div>
          </div>

          <p class="change-summary">
            <span class="text-help muted">变更说明：</span>{{ currentSnapshot.changeSummary }}
          </p>

          <div class="spec-table">
            <div class="spec-table__head">
              <span>规格</span>
              <span>标准化值</span>
              <span>原始值</span>
              <span>类型 / 单位</span>
            </div>
            <div v-for="spec in currentSnapshot.specifications" :key="spec.specCode" class="spec-table__row">
              <span class="spec-label">
                <strong>{{ spec.label }}</strong>
                <code>{{ spec.specCode }}</code>
              </span>
              <span class="spec-value">
                <em class="spec-mobile-label">标准化值</em>{{ displaySpecValue(spec) }}
              </span>
              <span class="spec-raw">
                <em class="spec-mobile-label">原始值</em>{{ spec.rawValue || '—' }}
              </span>
              <span class="text-help muted">
                <em class="spec-mobile-label">类型 / 单位</em>{{ valueTypeMeta(spec.valueType).label }} · {{ spec.unit || '无单位' }}
              </span>
            </div>
          </div>

          <p class="text-help muted checksum">
            checksum：<code>{{ currentSnapshot.checksum }}</code>
          </p>
        </template>
      </section>

      <section class="data-panel">
        <header class="section-head">
          <div>
            <h2 class="panel-title">规格版本历史</h2>
            <p class="text-help muted">历史快照只读；修正必须生成新版本，旧版本永久可查</p>
          </div>
          <el-tag type="info" effect="plain" size="small">{{ snapshots.length }} 个版本</el-tag>
        </header>

        <RegionState
          v-if="!snapshotLoading && !snapshotError && !snapshots.length"
          state="empty"
          variant="section"
          description="暂无规格历史"
        />
        <div v-else class="history-list">
          <article
            v-for="snapshot in snapshots"
            :key="snapshot.id"
            class="history-item"
            :class="{ 'history-item--current': snapshot.id === component.currentSpecSnapshotId }"
          >
            <div class="history-item__head">
              <div class="history-version">
                <strong>v{{ snapshot.snapshotVersion }}</strong>
                <el-tag v-if="snapshot.id === component.currentSpecSnapshotId" type="success" size="small">当前</el-tag>
              </div>
              <span class="text-help muted">
                {{ sourceTypeMeta(snapshot.sourceType).label }} · {{ formatDateTime(snapshot.confirmedAt) }}
              </span>
            </div>
            <p class="history-item__summary">{{ snapshot.changeSummary }}</p>
            <div class="history-item__meta">
              <span class="text-help muted">snapshotId {{ snapshot.id }} · 确认人 {{ snapshot.confirmedByName || `#${snapshot.confirmedBy}` }}</span>
              <el-button size="small" text @click="toggleHistory(snapshot.id)">
                {{ expandedHistory.includes(snapshot.id) ? '收起规格' : '展开规格' }}
              </el-button>
            </div>
            <div v-if="expandedHistory.includes(snapshot.id)" class="history-specs">
              <div v-for="spec in snapshot.specifications" :key="spec.specCode" class="history-spec-row">
                <span>{{ spec.label }}</span>
                <strong>{{ displaySpecValue(spec) }}</strong>
              </div>
            </div>
          </article>
        </div>
      </section>

      <ComponentGallery
        :component-id="component.id"
        :component-version="component.version"
        :primary-media-id="component.primaryMediaId"
        :placeholder-key="component.placeholderKey"
        :placeholder-label="component.mpn"
        :images="images"
        :can-manage="canManage"
        :inactive="isInactive"
        @updated="handleMediaUpdated"
        @conflict="refreshAll"
      />

      <section v-if="canManage" class="data-panel">
        <header class="section-head">
          <div>
            <h2 class="panel-title">审计信息</h2>
            <p class="text-help muted">仅 SYSTEM_ADMIN / STOCK_KEEPER 可见；审计记录动作、目标、状态与请求号</p>
          </div>
        </header>
        <dl class="audit-grid">
          <div><dt>创建人 / 时间</dt><dd>#{{ component.createdBy }} · {{ formatDateTime(component.createTime) }}</dd></div>
          <div><dt>最近更新人 / 时间</dt><dd>#{{ component.updatedBy }} · {{ formatDateTime(component.updateTime) }}</dd></div>
          <div><dt>当前版本</dt><dd>version {{ component.version }}</dd></div>
          <div><dt>当前规格版本</dt><dd>{{ component.specVersion ? `v${component.specVersion}` : '无正式快照' }}</dd></div>
          <div><dt>当前快照 ID</dt><dd>{{ component.currentSpecSnapshotId || '—' }}</dd></div>
          <div><dt>图片状态</dt><dd>{{ component.imageState }}</dd></div>
        </dl>
      </section>

      <ManualSnapshotDialog
        v-model="manualVisible"
        :component="component"
        :current-snapshot="currentSnapshot"
        @submitted="handleSnapshotSubmitted"
        @conflict="refreshAll"
      />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Edit, Document, Collection } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getComponent,
  listSpecSnapshots,
  listComponentImages,
  activateComponent,
  deactivateComponent,
  restoreComponent,
  canAccessComponents,
  canManageCatalog,
  COMPONENT_STATUS,
  componentStatusMeta,
  sourceTypeMeta,
  baseUnitMeta,
  valueTypeMeta
} from '@/api/components'
import {
  notifyApiError,
  normalizeApiError,
  errorTraceId,
  isComponentNotFound,
  isComponentDenied,
  isVersionConflict,
  isComponentSpecRequired,
  promptVersionConflict
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'
import { currentRoles } from '@/store/user'
import { ROLE } from '@/utils/permission'
import RegionState from '@/components/RegionState.vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import ComponentImage from '@/components/components/ComponentImage.vue'
import ComponentGallery from '@/components/components/ComponentGallery.vue'
import ManualSnapshotDialog from '@/components/components/ManualSnapshotDialog.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const notFoundText = ref('')

const component = ref(null)
const snapshots = ref([])
const images = ref([])

const snapshotLoading = ref(false)
const snapshotError = ref('')
const snapshotTraceId = ref('')

const manualVisible = ref(false)
const actionLoading = ref('')
const expandedHistory = ref([])

const canAccess = computed(() => canAccessComponents())
const canManage = computed(() => canManageCatalog())
const canSpec = computed(() => currentRoles().includes(ROLE.SYSTEM_ADMIN)
  || currentRoles().includes(ROLE.STOCK_KEEPER)
  || currentRoles().includes(ROLE.TEACHER))
const isTeacher = computed(() => currentRoles().includes(ROLE.TEACHER))
const isInactive = computed(() => component.value?.status === COMPONENT_STATUS.INACTIVE)
const catalogCode = computed(() => component.value?.catalogCode || '')
const categoryPathText = computed(() => {
  const path = component.value?.categoryPath
  return Array.isArray(path) && path.length ? path.map((p) => p.name).join(' / ') : '—'
})
const currentSnapshot = computed(() => {
  const id = component.value?.currentSpecSnapshotId
  if (!id) return null
  return snapshots.value.find((item) => item.id === id) || snapshots.value[0] || null
})

function statusMeta(status) {
  return componentStatusMeta(status)
}

function displaySpecValue(spec) {
  if (spec.normalizedValue === null || spec.normalizedValue === undefined || spec.normalizedValue === '') return '—'
  return `${spec.normalizedValue}${spec.unit ? ` ${spec.unit}` : ''}`
}

function toggleHistory(id) {
  const index = expandedHistory.value.indexOf(id)
  if (index >= 0) expandedHistory.value.splice(index, 1)
  else expandedHistory.value.push(id)
}

async function loadDetail() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  notFoundText.value = ''
  try {
    component.value = await getComponent(route.params.id)
  } catch (err) {
    component.value = null
    if (isComponentNotFound(err) || isComponentDenied(err)) {
      notFoundText.value = normalizeApiError(err).msg || '型号不存在或不可见'
    } else {
      loadError.value = normalizeApiError(err).msg || '加载失败'
      loadTraceId.value = errorTraceId(err)
    }
    notifyApiError(err)
  } finally {
    loading.value = false
  }
}

async function loadSnapshots() {
  if (!component.value) return
  snapshotLoading.value = true
  snapshotError.value = ''
  snapshotTraceId.value = ''
  try {
    const res = await listSpecSnapshots(component.value.id, { page: 1, pageSize: 100 })
    snapshots.value = res?.list || []
  } catch (err) {
    snapshotError.value = normalizeApiError(err).msg || '加载失败'
    snapshotTraceId.value = errorTraceId(err)
    snapshots.value = []
  } finally {
    snapshotLoading.value = false
  }
}

async function loadImages() {
  if (!component.value) return
  try {
    const res = await listComponentImages(component.value.id)
    images.value = res?.images || []
  } catch (err) {
    images.value = []
  }
}

async function refreshAll() {
  await loadDetail()
  if (!component.value) return
  await Promise.all([loadSnapshots(), loadImages()])
}

function goEdit() {
  router.push(`/components/${component.value.id}/edit`)
}

function goCandidates() {
  router.push(`/components/${component.value.id}/spec-candidates`)
}

async function activate() {
  try {
    await ElMessageBox.confirm(
      '激活后该型号进入正式目录，普通成员可查询。激活前必须已有正式规格快照，且分类和制造商均为启用状态。',
      '激活型号',
      { confirmButtonText: '激活', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  actionLoading.value = 'activate'
  try {
    const result = await activateComponent(
      component.value.id,
      { version: component.value.version },
      createIdempotencyKey('w44-component')
    )
    ElMessage.success(`型号已激活，version ${result.version} · ${formatDateTime(result.updateTime)}`)
    await refreshAll()
  } catch (err) {
    if (isVersionConflict(err)) {
      promptVersionConflict(refreshAll)
    } else if (isComponentSpecRequired(err)) {
      ElMessage.error('DRAFT 型号至少需要一份正式规格快照后才能激活')
    } else {
      notifyApiError(err)
    }
  } finally {
    actionLoading.value = ''
  }
}

async function deactivate() {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      '停用后型号只读，已有的采购、BOM、实验或知识引用继续绑定原 ID/版本。请填写停用原因（1-500 字符）。',
      '停用型号',
      {
        confirmButtonText: '停用',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：型号已停产，由替代型号接替',
        inputValidator: (value) => {
          const text = String(value || '').trim()
          return text.length >= 1 && text.length <= 500 ? true : '停用原因需为 1-500 字符'
        },
        type: 'warning'
      }
    )
    reason = String(result.value || '').trim()
  } catch {
    return
  }
  actionLoading.value = 'deactivate'
  try {
    const result = await deactivateComponent(
      component.value.id,
      { version: component.value.version, reason },
      createIdempotencyKey('w44-component')
    )
    ElMessage.success(`型号已停用，version ${result.version} · ${formatDateTime(result.updateTime)}`)
    await refreshAll()
  } catch (err) {
    if (isVersionConflict(err)) promptVersionConflict(refreshAll)
    else notifyApiError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function restore() {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      '恢复后型号重新进入正式目录。请填写恢复原因（1-500 字符）。',
      '恢复型号',
      {
        confirmButtonText: '恢复',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：供应商恢复供货，型号重新启用',
        inputValidator: (value) => {
          const text = String(value || '').trim()
          return text.length >= 1 && text.length <= 500 ? true : '恢复原因需为 1-500 字符'
        },
        type: 'warning'
      }
    )
    reason = String(result.value || '').trim()
  } catch {
    return
  }
  actionLoading.value = 'restore'
  try {
    const result = await restoreComponent(
      component.value.id,
      { version: component.value.version, reason },
      createIdempotencyKey('w44-component')
    )
    ElMessage.success(`型号已恢复，version ${result.version} · ${formatDateTime(result.updateTime)}`)
    await refreshAll()
  } catch (err) {
    if (isVersionConflict(err)) promptVersionConflict(refreshAll)
    else notifyApiError(err)
  } finally {
    actionLoading.value = ''
  }
}

async function handleMediaUpdated() {
  await refreshAll()
}

async function handleSnapshotSubmitted(result) {
  ElMessage.success(`快照 v${result.snapshotVersion} 已生效；型号 version ${result.componentVersion}`)
  await refreshAll()
}

watch(
  () => route.params.id,
  () => {
    if (route.name === 'ComponentDetail') refreshAll()
  }
)

onMounted(async () => {
  if (!canAccess.value) return
  await loadDetail()
  if (component.value) {
    await Promise.all([loadSnapshots(), loadImages()])
  }
})
</script>

<style scoped>
.component-detail-page {
  max-width: var(--usn-content-max-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.detail-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-3);
}

.detail-hero {
  display: grid;
  grid-template-columns: minmax(240px, 360px) 1fr;
  gap: var(--usn-space-6);
  align-items: start;
}

.detail-hero--inactive {
  border-color: var(--usn-line);
  background: var(--usn-canvas);
}

.detail-hero__media {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
}

.hero-media-note {
  margin: 0;
}

.detail-hero__body {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
  min-width: 0;
}

.hero-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.hero-mpn {
  margin: 6px 0 0;
  display: flex;
  align-items: baseline;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.mpn-text {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 16px;
  font-weight: 700;
  color: var(--usn-blue-700);
}

.hero-tags {
  display: flex;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.hero-description {
  margin: 0;
  color: var(--usn-ink-700);
  line-height: 1.6;
}

.hero-meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--usn-space-3);
  margin: 0;
  padding: var(--usn-space-3) 0;
  border-top: 1px solid var(--usn-line);
  border-bottom: 1px solid var(--usn-line);
}

.hero-meta div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.hero-meta dt {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.hero-meta dd {
  margin: 0;
  font-size: var(--usn-font-size-body);
  font-weight: 600;
  color: var(--usn-ink-900);
  overflow-wrap: anywhere;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--usn-space-2);
}

.hero-alert {
  margin: 0;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
  margin-bottom: var(--usn-space-3);
}

.panel-title {
  margin: 0 0 4px;
  font-size: var(--usn-font-size-panel-title);
  font-weight: 700;
  color: var(--usn-ink-900);
}

.snapshot-source {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--usn-space-3);
  padding: var(--usn-space-3);
  background: var(--usn-canvas);
  border-radius: var(--usn-radius-md);
  margin-bottom: var(--usn-space-3);
}

.source-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.source-item strong {
  font-size: var(--usn-font-size-body);
  color: var(--usn-ink-900);
  overflow-wrap: anywhere;
}

.change-summary {
  margin: 0 0 var(--usn-space-3);
  color: var(--usn-ink-700);
}

.break-anywhere {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.spec-table {
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  overflow: hidden;
}

.spec-table__head,
.spec-table__row {
  display: grid;
  grid-template-columns: 1.2fr 1.2fr 1.2fr 0.9fr;
  gap: var(--usn-space-3);
  padding: var(--usn-space-3);
  align-items: center;
}

.spec-table__head {
  background: var(--usn-canvas);
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.spec-table__row {
  border-top: 1px solid var(--usn-line);
}

.spec-mobile-label {
  display: none;
  margin-right: var(--usn-space-2);
  font-style: normal;
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.spec-label {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.spec-label code {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.spec-value {
  font-weight: 600;
  color: var(--usn-ink-900);
}

.spec-raw {
  color: var(--usn-ink-700);
  overflow-wrap: anywhere;
}

.checksum {
  margin: var(--usn-space-2) 0 0;
  overflow-wrap: anywhere;
}

.checksum code {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.history-item {
  padding: var(--usn-space-3);
  border: 1px solid var(--usn-line);
  border-left: 3px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
}

.history-item--current {
  border-left-color: var(--usn-success);
  background: var(--usn-canvas);
}

.history-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.history-version {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
}

.history-item__summary {
  margin: 0;
  color: var(--usn-ink-700);
}

.history-item__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.history-specs {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  padding-top: var(--usn-space-2);
  border-top: 1px dashed var(--usn-line);
}

.history-spec-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--usn-space-3);
}

.audit-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--usn-space-3);
  margin: 0;
}

.audit-grid div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.audit-grid dt {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.audit-grid dd {
  margin: 0;
  font-weight: 600;
  color: var(--usn-ink-900);
  overflow-wrap: anywhere;
}

.empty-actions {
  display: flex;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
  justify-content: center;
}

@media (max-width: 1024px) {
  .detail-hero {
    grid-template-columns: 1fr;
  }

  .hero-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .snapshot-source {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .audit-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .spec-table__head {
    display: none;
  }

  .spec-table__row {
    grid-template-columns: 1fr;
    gap: var(--usn-space-1);
  }

  .spec-mobile-label {
    display: inline;
  }

  .snapshot-source,
  .hero-meta,
  .audit-grid {
    grid-template-columns: 1fr;
  }

  .hero-title-row {
    flex-direction: column;
  }
}
</style>
