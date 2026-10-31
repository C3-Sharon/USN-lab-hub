<template>
  <div class="manufacturer-manage-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="router.push('/components')">返回元器件目录</el-button>
      <span class="text-help muted">停用前需先迁移或停用其下型号</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canAccess"
      state="permission"
      variant="section"
      description="当前账号无权查看制造商管理"
      sub-title="制造商目录对已登录的实验室成员开放；写操作仅 SYSTEM_ADMIN / STOCK_KEEPER"
    />

    <template v-else>
      <header class="page-header">
        <div>
          <h1 class="page-title">制造商管理</h1>
          <p class="page-subtitle muted">
            制造商编码创建后不可修改；名称为大小写不敏感唯一；W44 不自动访问官网
          </p>
        </div>
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">新增制造商</el-button>
      </header>

      <el-alert
        v-if="!canManage"
        type="info"
        show-icon
        :closable="false"
        title="老师账号为全局只读：可查看制造商编码、名称、官网和状态，但不能新增、编辑、停用或恢复"
      />

      <div class="filter-bar toolbar">
        <el-input
          v-model="query.keyword"
          class="filter-keyword"
          placeholder="搜索制造商编码或名称"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-select
          v-if="canReadInactive"
          v-model="query.status"
          class="filter-select"
          placeholder="全部状态"
          clearable
          @change="handleSearch"
        >
          <el-option label="启用" :value="CATALOG_STATUS.ACTIVE" />
          <el-option label="停用" :value="CATALOG_STATUS.INACTIVE" />
        </el-select>

        <span class="text-help muted">共 {{ total }} 个制造商</span>
      </div>

      <RegionState
        v-if="loading"
        state="loading"
        variant="card"
        description="正在加载制造商"
      />

      <RegionState
        v-else-if="loadError"
        state="error"
        variant="section"
        description="制造商加载失败"
        :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
        @retry="loadList"
      />

      <RegionState
        v-else-if="!list.length"
        state="empty"
        variant="section"
        :description="query.keyword || query.status ? '没有符合条件的制造商' : '暂无制造商'"
        sub-title="先维护制造商，再创建元器件型号草稿"
      >
        <div class="empty-actions">
          <el-button v-if="query.keyword || query.status" @click="resetFilter">清除筛选条件</el-button>
          <el-button v-if="canManage" type="primary" @click="openCreate">新增制造商</el-button>
        </div>
      </RegionState>

      <div v-else class="manufacturer-grid">
        <article
          v-for="item in list"
          :key="item.id"
          class="manufacturer-card"
          :class="{ 'manufacturer-card--inactive': item.status === CATALOG_STATUS.INACTIVE }"
        >
          <header class="manufacturer-card__head">
            <div>
              <h3 class="manufacturer-card__name">{{ item.name }}</h3>
              <code class="manufacturer-card__code">{{ item.code }}</code>
            </div>
            <el-tag :type="catalogStatusMeta(item.status).tagType" size="small" effect="light">
              {{ catalogStatusMeta(item.status).label }}
            </el-tag>
          </header>

          <dl class="manufacturer-card__meta">
            <div>
              <dt>官网</dt>
              <dd>
                <el-link
                  v-if="item.website"
                  :href="item.website"
                  target="_blank"
                  rel="noopener noreferrer"
                  type="primary"
                  class="break-anywhere"
                >
                  {{ item.website }}
                </el-link>
                <span v-else>—</span>
              </dd>
            </div>
            <div>
              <dt>version</dt>
              <dd>{{ item.version }}</dd>
            </div>
            <div>
              <dt>最近更新</dt>
              <dd>{{ formatDateTime(item.updateTime) }}</dd>
            </div>
          </dl>

          <div v-if="canManage" class="manufacturer-card__actions">
            <el-button
              v-if="item.status === CATALOG_STATUS.ACTIVE"
              size="small"
              @click="openEdit(item)"
            >
              编辑
            </el-button>
            <el-button
              v-if="item.status === CATALOG_STATUS.ACTIVE"
              size="small"
              type="danger"
              plain
              @click="deactivate(item)"
            >
              停用
            </el-button>
            <el-button
              v-if="item.status === CATALOG_STATUS.INACTIVE"
              size="small"
              type="success"
              @click="restore(item)"
            >
              恢复
            </el-button>
          </div>
          <span v-else class="text-help muted">只读</span>
        </article>
      </div>

      <div v-if="total > 0" class="table-footer">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[12, 24, 48]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSearch"
          @current-change="loadList"
        />
      </div>

      <el-dialog
        v-model="dialogVisible"
        :title="dialogMode === 'create' ? '新增制造商' : '编辑制造商'"
        width="min(540px, 92vw)"
        :close-on-click-modal="false"
      >
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item v-if="dialogMode === 'create'" label="制造商编码 code" prop="code">
            <el-input
              v-model="form.code"
              maxlength="40"
              placeholder="大写字母、数字、下划线，如 TI"
              @input="form.code = form.code.toUpperCase()"
            />
          </el-form-item>
          <el-form-item v-else label="制造商编码 code（创建后不可修改）">
            <el-input :model-value="form.code" disabled />
          </el-form-item>

          <el-form-item label="制造商名称 name" prop="name">
            <el-input v-model="form.name" maxlength="120" show-word-limit placeholder="如 Texas Instruments" />
          </el-form-item>

          <el-form-item label="官网 website" prop="website">
            <el-input v-model="form.website" maxlength="2048" placeholder="https://www.ti.com（可留空）" />
          </el-form-item>
        </el-form>

        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submit">
            {{ dialogMode === 'create' ? '创建制造商' : '保存修改' }}
          </el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listManufacturers,
  createManufacturer,
  updateManufacturer,
  deactivateManufacturer,
  restoreManufacturer,
  canAccessComponents,
  canManageCatalog,
  canReadInactiveCatalog,
  CATALOG_STATUS,
  catalogStatusMeta
} from '@/api/components'
import {
  notifyApiError,
  normalizeApiError,
  errorTraceId,
  isVersionConflict,
  isManufacturerDuplicateCode,
  isCatalogInUse,
  promptVersionConflict
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'
import RegionState from '@/components/RegionState.vue'
import MockStateBanner from '@/components/MockStateBanner.vue'

const router = useRouter()

const canAccess = computed(() => canAccessComponents())
const canManage = computed(() => canManageCatalog())
const canReadInactive = computed(() => canReadInactiveCatalog())

const list = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')

const dialogVisible = ref(false)
const dialogMode = ref('create')
const submitting = ref(false)
const formRef = ref(null)

const query = reactive({
  page: 1,
  pageSize: 12,
  keyword: '',
  status: ''
})

const form = reactive({
  id: null,
  code: '',
  name: '',
  website: '',
  version: null
})

const rules = {
  code: [
    { required: true, message: '请输入制造商编码', trigger: 'blur' },
    { pattern: /^[A-Z0-9_]{2,40}$/, message: '需为 2-40 位大写字母、数字或下划线', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入制造商名称', trigger: 'blur' },
    { min: 2, max: 120, message: '制造商名称需为 2-120 字符', trigger: 'blur' }
  ],
  website: [
    {
      validator: (_rule, value, callback) => {
        if (!value || /^https?:\/\//i.test(value)) callback()
        else callback(new Error('官网仅支持 http/https 链接'))
      },
      trigger: 'blur'
    }
  ]
}

function handleSearch() {
  query.page = 1
  loadList()
}

function resetFilter() {
  query.keyword = ''
  query.status = ''
  handleSearch()
}

function openCreate() {
  dialogMode.value = 'create'
  form.id = null
  form.code = ''
  form.name = ''
  form.website = ''
  form.version = null
  dialogVisible.value = true
}

function openEdit(item) {
  dialogMode.value = 'edit'
  form.id = item.id
  form.code = item.code
  form.name = item.name
  form.website = item.website || ''
  form.version = item.version
  dialogVisible.value = true
}

async function loadList() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  try {
    const res = await listManufacturers({
      page: query.page,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: canReadInactive.value ? (query.status || undefined) : undefined
    })
    list.value = res?.list || []
    total.value = res?.total ?? 0
  } catch (err) {
    loadError.value = normalizeApiError(err).msg || '加载失败'
    loadTraceId.value = errorTraceId(err)
    list.value = []
    total.value = 0
    notifyApiError(err)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      const created = await createManufacturer(
        {
          code: form.code.trim().toUpperCase(),
          name: form.name.trim(),
          website: form.website.trim() || null
        },
        createIdempotencyKey('w44-manufacturer')
      )
      ElMessage.success(`制造商 ${created.code} 已创建（v${created.version}）`)
    } else {
      const updated = await updateManufacturer(form.id, {
        name: form.name.trim(),
        website: form.website.trim() || null,
        version: form.version
      })
      ElMessage.success(`制造商已保存，v${updated.version}`)
    }
    dialogVisible.value = false
    await loadList()
  } catch (err) {
    if (isVersionConflict(err)) {
      promptVersionConflict(() => loadList())
      return
    }
    if (isManufacturerDuplicateCode(err)) {
      ElMessage.error('制造商编码或名称已存在')
      return
    }
    notifyApiError(err)
  } finally {
    submitting.value = false
  }
}

async function deactivate(item) {
  try {
    await ElMessageBox.confirm(
      `停用制造商「${item.name}」前，需确保其下没有启用或草稿型号；已有型号会保留并继续绑定原 ID。`,
      '停用制造商',
      { confirmButtonText: '停用', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deactivateManufacturer(
      item.id,
      { version: item.version },
      createIdempotencyKey('w44-manufacturer')
    )
    ElMessage.success(`制造商 ${item.code} 已停用`)
    await loadList()
  } catch (err) {
    if (isVersionConflict(err)) promptVersionConflict(() => loadList())
    else if (isCatalogInUse(err)) ElMessage.error(normalizeApiError(err).msg || '制造商下存在启用或草稿型号，不能停用')
    else notifyApiError(err)
  }
}

async function restore(item) {
  try {
    await ElMessageBox.confirm(
      `恢复制造商「${item.name}」后，新型号可重新引用。名称仍需满足大小写不敏感唯一。`,
      '恢复制造商',
      { confirmButtonText: '恢复', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await restoreManufacturer(
      item.id,
      { version: item.version },
      createIdempotencyKey('w44-manufacturer')
    )
    ElMessage.success(`制造商 ${item.code} 已恢复`)
    await loadList()
  } catch (err) {
    if (isVersionConflict(err)) promptVersionConflict(() => loadList())
    else notifyApiError(err)
  }
}

onMounted(() => {
  if (!canAccess.value) return
  loadList()
})
</script>

<style scoped>
.manufacturer-manage-page {
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

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-4);
  flex-wrap: wrap;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.filter-keyword {
  flex: 1 1 260px;
}

.filter-select {
  width: 150px;
}

.manufacturer-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--usn-space-4);
}

.manufacturer-card {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
  padding: var(--usn-space-4);
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  box-shadow: var(--usn-shadow-panel);
}

.manufacturer-card--inactive {
  background: var(--usn-canvas);
  opacity: 0.78;
}

.manufacturer-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-2);
}

.manufacturer-card__name {
  margin: 0 0 2px;
  font-size: var(--usn-font-size-panel-title);
  font-weight: 700;
  color: var(--usn-ink-900);
}

.manufacturer-card__code {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: var(--usn-font-size-help);
  color: var(--usn-blue-700);
}

.manufacturer-card__meta {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  margin: 0;
  padding: var(--usn-space-2) 0;
  border-top: 1px solid var(--usn-line);
}

.manufacturer-card__meta div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.manufacturer-card__meta dt {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.manufacturer-card__meta dd {
  margin: 0;
  font-size: var(--usn-font-size-help);
  font-weight: 600;
  color: var(--usn-ink-900);
}

.manufacturer-card__actions {
  display: flex;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
  margin-top: auto;
}

.break-anywhere {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.empty-actions {
  display: flex;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
  justify-content: center;
}

@media (max-width: 1365px) {
  .manufacturer-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .manufacturer-grid {
    grid-template-columns: 1fr;
  }

  .filter-keyword,
  .filter-select {
    width: 100%;
    flex: 1 1 100%;
  }
}
</style>
