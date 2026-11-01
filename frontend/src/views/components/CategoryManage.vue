<template>
  <div class="category-manage-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="router.push('/components')">返回元器件目录</el-button>
      <span class="text-help muted">分类最多三级 · 只停用/恢复，不物理删除</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canAccess"
      state="permission"
      variant="section"
      description="当前账号无权查看分类管理"
      sub-title="分类树对已登录的实验室成员开放；写操作仅 SYSTEM_ADMIN / STOCK_KEEPER"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载分类树"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="分类树加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="loadTree"
    />

    <template v-else>
      <header class="page-header">
        <div>
          <h1 class="page-title">分类树管理</h1>
          <p class="page-subtitle muted">
            分类编码创建后不可修改；停用前必须先停用子分类，并确保分类下没有启用或草稿型号
          </p>
        </div>
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate(null)">新增顶级分类</el-button>
      </header>

      <el-alert
        v-if="!canManage"
        type="info"
        show-icon
        :closable="false"
        title="老师账号为全局只读：可查看分类树、编码、层级和启用状态，但不能新增、编辑、停用或恢复"
      />

      <RegionState
        v-if="!tree.length"
        state="empty"
        variant="section"
        description="暂无分类"
        sub-title="先创建顶级分类，再逐级添加子分类（最多三级）"
      >
        <el-button v-if="canManage" type="primary" @click="openCreate(null)">创建第一个分类</el-button>
      </RegionState>

      <section v-else class="data-panel tree-panel">
        <el-tree
          :data="tree"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          class="category-tree"
        >
          <template #default="{ node, data }">
            <div class="tree-node" :class="{ 'tree-node--inactive': data.status === CATALOG_STATUS.INACTIVE }">
              <div class="tree-node__main">
                <strong class="tree-node__name">{{ data.name }}</strong>
                <code class="tree-node__code">{{ data.code }}</code>
                <el-tag
                  :type="catalogStatusMeta(data.status).tagType"
                  size="small"
                  effect="light"
                >
                  {{ catalogStatusMeta(data.status).label }}
                </el-tag>
                <span class="text-help muted">L{{ data.level }} · 排序 {{ data.sortOrder }} · v{{ data.version }}</span>
              </div>

              <div v-if="canManage" class="tree-node__actions">
                <el-button
                  v-if="data.status === CATALOG_STATUS.ACTIVE && data.level < 3"
                  size="small"
                  text
                  @click.stop="openCreate(data)"
                >
                  新增子分类
                </el-button>
                <el-button
                  v-if="data.status === CATALOG_STATUS.ACTIVE"
                  size="small"
                  text
                  @click.stop="openEdit(data)"
                >
                  编辑
                </el-button>
                <el-button
                  v-if="data.status === CATALOG_STATUS.ACTIVE"
                  size="small"
                  text
                  type="danger"
                  @click.stop="deactivate(data)"
                >
                  停用
                </el-button>
                <el-button
                  v-if="data.status === CATALOG_STATUS.INACTIVE"
                  size="small"
                  text
                  type="success"
                  @click.stop="restore(data)"
                >
                  恢复
                </el-button>
              </div>
              <span v-else class="text-help muted">只读 · {{ node.level }} 层</span>
            </div>
          </template>
        </el-tree>
      </section>

      <el-dialog
        v-model="dialogVisible"
        :title="dialogMode === 'create' ? '新增分类' : '编辑分类'"
        width="min(520px, 92vw)"
        :close-on-click-modal="false"
      >
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item v-if="dialogMode === 'create'" label="分类编码 code" prop="code">
            <el-input
              v-model="form.code"
              maxlength="40"
              placeholder="大写字母、数字、下划线，如 SEMICONDUCTOR"
              @input="form.code = form.code.toUpperCase()"
            />
          </el-form-item>
          <el-form-item v-else label="分类编码 code（创建后不可修改）">
            <el-input :model-value="form.code" disabled />
          </el-form-item>

          <el-form-item label="分类名称 name" prop="name">
            <el-input v-model="form.name" maxlength="80" show-word-limit placeholder="如 半导体" />
          </el-form-item>

          <el-form-item label="父分类 parentId">
            <el-input :model-value="parentLabel" disabled />
          </el-form-item>

          <el-form-item label="排序 sortOrder" prop="sortOrder">
            <el-input-number v-model="form.sortOrder" :min="0" :max="9999" :step="1" controls-position="right" class="w-full" />
          </el-form-item>
        </el-form>

        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submit">
            {{ dialogMode === 'create' ? '创建分类' : '保存修改' }}
          </el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listCategories,
  createCategory,
  updateCategory,
  deactivateCategory,
  restoreCategory,
  canAccessComponents,
  canManageCatalog,
  CATALOG_STATUS,
  catalogStatusMeta
} from '@/api/components'
import {
  notifyApiError,
  normalizeApiError,
  errorTraceId,
  isVersionConflict,
  isCategoryDuplicateCode,
  isCategoryDepthExceeded,
  isCatalogInUse,
  promptVersionConflict
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import RegionState from '@/components/RegionState.vue'
import MockStateBanner from '@/components/MockStateBanner.vue'

const router = useRouter()

const canAccess = computed(() => canAccessComponents())
const canManage = computed(() => canManageCatalog())

const tree = ref([])
const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')

const dialogVisible = ref(false)
const dialogMode = ref('create')
const submitting = ref(false)
const formRef = ref(null)
const parentNode = ref(null)

const form = reactive({
  id: null,
  code: '',
  name: '',
  parentId: null,
  sortOrder: 10,
  version: null
})

const parentLabel = computed(() => {
  if (!form.parentId) return '顶级分类'
  return parentPath.value || `#${form.parentId}`
})

const parentPath = ref('')

const rules = {
  code: [
    { required: true, message: '请输入分类编码', trigger: 'blur' },
    { pattern: /^[A-Z0-9_]{2,40}$/, message: '需为 2-40 位大写字母、数字或下划线', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 2, max: 80, message: '分类名称需为 2-80 字符', trigger: 'blur' }
  ],
  sortOrder: [{ required: true, message: '请输入排序值', trigger: 'change' }]
}

async function loadTree() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  try {
    tree.value = await listCategories()
  } catch (err) {
    loadError.value = normalizeApiError(err).msg || '加载失败'
    loadTraceId.value = errorTraceId(err)
    tree.value = []
    notifyApiError(err)
  } finally {
    loading.value = false
  }
}

function findPath(nodes, id, path = []) {
  for (const node of nodes) {
    const next = [...path, node.name]
    if (node.id === id) return next.join(' / ')
    if (node.children?.length) {
      const found = findPath(node.children, id, next)
      if (found) return found
    }
  }
  return ''
}

function openCreate(parent) {
  dialogMode.value = 'create'
  parentNode.value = parent
  form.id = null
  form.code = ''
  form.name = ''
  form.parentId = parent?.id ?? null
  form.sortOrder = (parent?.children?.length || tree.value.length || 0) * 10 + 10
  form.version = null
  parentPath.value = parent ? findPath(tree.value, parent.id) || parent.name : ''
  dialogVisible.value = true
}

function openEdit(category) {
  dialogMode.value = 'edit'
  parentNode.value = null
  form.id = category.id
  form.code = category.code
  form.name = category.name
  form.parentId = category.parentId
  form.sortOrder = category.sortOrder
  form.version = category.version
  parentPath.value = findPath(tree.value, category.parentId)
  dialogVisible.value = true
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
      const created = await createCategory(
        {
          code: form.code.trim().toUpperCase(),
          name: form.name.trim(),
          parentId: form.parentId,
          sortOrder: form.sortOrder
        },
        createIdempotencyKey('w44-category')
      )
      ElMessage.success(`分类 ${created.code} 已创建（v${created.version}）`)
    } else {
      const updated = await updateCategory(form.id, {
        name: form.name.trim(),
        sortOrder: form.sortOrder,
        version: form.version
      })
      ElMessage.success(`分类已保存，v${updated.version}`)
    }
    dialogVisible.value = false
    await loadTree()
  } catch (err) {
    if (isVersionConflict(err)) {
      promptVersionConflict(() => loadTree())
      return
    }
    if (isCategoryDuplicateCode(err)) {
      ElMessage.error('分类编码或同一父分类下的名称已存在')
      return
    }
    if (isCategoryDepthExceeded(err)) {
      ElMessage.error('分类最多三级')
      return
    }
    if (isCatalogInUse(err)) {
      ElMessage.error(normalizeApiError(err).msg || '父分类已停用或分类仍被引用')
      return
    }
    notifyApiError(err)
  } finally {
    submitting.value = false
  }
}

async function deactivate(category) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      `停用分类「${category.name}」后将不能再被新型号引用。请填写停用原因（1-500 字符）。`,
      '停用分类',
      {
        confirmButtonText: '停用', cancelButtonText: '取消', type: 'warning',
        inputPlaceholder: '例如：分类已合并至新的目录节点',
        inputValidator: (value) => {
          const text = String(value || '').trim()
          return text.length >= 1 && text.length <= 500 ? true : '停用原因需为 1-500 字符'
        }
      }
    )
    reason = String(result.value || '').trim()
  } catch {
    return
  }
  try {
    await deactivateCategory(
      category.id,
      { version: category.version, reason },
      createIdempotencyKey('w44-category')
    )
    ElMessage.success(`分类 ${category.code} 已停用`)
    await loadTree()
  } catch (err) {
    if (isVersionConflict(err)) promptVersionConflict(() => loadTree())
    else if (isCatalogInUse(err)) ElMessage.error(normalizeApiError(err).msg || '分类仍被引用，不能停用')
    else notifyApiError(err)
  }
}

async function restore(category) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      `恢复分类「${category.name}」前，其父分类必须为启用状态。请填写恢复原因（1-500 字符）。`,
      '恢复分类',
      {
        confirmButtonText: '恢复', cancelButtonText: '取消', type: 'warning',
        inputPlaceholder: '例如：分类重新启用并恢复维护',
        inputValidator: (value) => {
          const text = String(value || '').trim()
          return text.length >= 1 && text.length <= 500 ? true : '恢复原因需为 1-500 字符'
        }
      }
    )
    reason = String(result.value || '').trim()
  } catch {
    return
  }
  try {
    await restoreCategory(
      category.id,
      { version: category.version, reason },
      createIdempotencyKey('w44-category')
    )
    ElMessage.success(`分类 ${category.code} 已恢复`)
    await loadTree()
  } catch (err) {
    if (isVersionConflict(err)) promptVersionConflict(() => loadTree())
    else if (isCatalogInUse(err)) ElMessage.error(normalizeApiError(err).msg || '父分类未启用，不能恢复')
    else notifyApiError(err)
  }
}

onMounted(() => {
  if (!canAccess.value) return
  loadTree()
})
</script>

<style scoped>
.category-manage-page {
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

.tree-panel {
  overflow-x: auto;
}

.category-tree {
  background: transparent;
  min-width: 520px;
}

.category-tree :deep(.el-tree-node__content) {
  height: auto;
  min-height: 44px;
  padding: 4px 0;
}

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-3);
  width: 100%;
  padding: 4px var(--usn-space-2);
  border-radius: var(--usn-radius-sm);
  flex-wrap: wrap;
}

.tree-node--inactive {
  opacity: 0.7;
}

.tree-node__main {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
  min-width: 0;
}

.tree-node__name {
  font-size: var(--usn-font-size-body);
  color: var(--usn-ink-900);
}

.tree-node__code {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: var(--usn-font-size-help);
  color: var(--usn-blue-700);
}

.tree-node__actions {
  display: flex;
  align-items: center;
  gap: var(--usn-space-1);
  flex-wrap: wrap;
}

.w-full {
  width: 100%;
}

@media (max-width: 767px) {
  .tree-node {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
