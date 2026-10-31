<template>
  <div class="component-editor-page">
    <header class="detail-nav">
      <el-button text :icon="ArrowLeft" @click="goBack">返回</el-button>
      <span class="text-help muted">{{ isEdit ? `编辑型号 #${route.params.id}` : '新建型号草稿' }}</span>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canManage"
      state="permission"
      variant="section"
      description="当前账号无权创建或编辑元器件型号"
      sub-title="只有 SYSTEM_ADMIN / STOCK_KEEPER 可写；TEACHER 与 MEMBER 为只读"
    />

    <RegionState
      v-else-if="loading"
      state="loading"
      variant="section"
      description="正在加载型号与目录基础数据"
    />

    <RegionState
      v-else-if="loadError"
      state="error"
      variant="section"
      description="加载失败"
      :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
      @retry="initialize"
    />

    <template v-else>
      <header class="page-header">
        <div>
          <h1 class="page-title">{{ isEdit ? '编辑元器件型号' : '新建元器件型号' }}</h1>
          <p class="page-subtitle muted">
            创建后固定为 DRAFT；需先登记外部候选或手工录入正式规格快照，才能激活进入正式目录
          </p>
        </div>
        <el-tag effect="plain" size="large">MPN 唯一 · version 乐观锁</el-tag>
      </header>

      <el-alert
        v-if="readOnlyInactive"
        type="info"
        show-icon
        :closable="false"
        title="该型号已停用，核心字段不可编辑"
        description="停用型号只能查看或恢复；请先在详情页恢复型号后再编辑。"
        class="editor-alert"
      />

      <div class="editor-grid">
        <section class="data-panel editor-form-panel">
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            :disabled="readOnlyInactive || submitting"
          >
            <div class="form-grid">
              <el-form-item label="型号名称 name" prop="name">
                <el-input v-model="form.name" maxlength="120" show-word-limit placeholder="例如：低功耗零漂移运算放大器" />
              </el-form-item>

              <el-form-item label="制造商 manufacturerId" prop="manufacturerId">
                <el-select v-model="form.manufacturerId" filterable placeholder="选择启用状态的制造商" class="w-full">
                  <el-option
                    v-for="item in manufacturers"
                    :key="item.id"
                    :label="`${item.name} (${item.code})`"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="分类 categoryId" prop="categoryId">
                <el-tree-select
                  v-model="form.categoryId"
                  :data="categoryTree"
                  :props="{ label: 'name', value: 'id', children: 'children' }"
                  placeholder="选择启用状态的分类"
                  check-strictly
                  class="w-full"
                />
              </el-form-item>

              <el-form-item label="MPN 原始型号" prop="mpn">
                <el-input v-model="form.mpn" maxlength="120" placeholder="例如：OPA333AIDBVR" />
              </el-form-item>

              <el-form-item label="封装 packageType" prop="packageType">
                <el-input v-model="form.packageType" maxlength="80" placeholder="例如：SOT-23-5、0603、DIP-8" />
              </el-form-item>

              <el-form-item label="基础单位 baseUnit" prop="baseUnit">
                <el-select v-model="form.baseUnit" class="w-full">
                  <el-option
                    v-for="item in baseUnitOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </div>

            <el-form-item label="描述 description">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="3"
                maxlength="2000"
                show-word-limit
                placeholder="用途、关键特性或实验室使用场景，最多 2000 字符"
              />
            </el-form-item>
          </el-form>

          <div class="editor-actions">
            <el-button @click="goBack">取消</el-button>
            <el-button
              type="primary"
              :loading="submitting"
              :disabled="readOnlyInactive"
              @click="submit"
            >
              {{ isEdit ? '保存核心字段' : '创建 DRAFT 型号' }}
            </el-button>
          </div>
        </section>

        <aside class="editor-aside">
          <section class="data-panel aside-panel">
            <h2 class="panel-title">契约提示</h2>
            <ul class="aside-list">
              <li><strong>唯一性：</strong>同一制造商下 <code>(manufacturerId, normalizedMpn)</code> 全状态唯一，重复返回 409。</li>
              <li><strong>引用约束：</strong>只能选择启用状态的分类与制造商；停用后不可新引用。</li>
              <li><strong>分类层级：</strong>最多三级，路径与 level 由服务端计算。</li>
              <li><strong>状态：</strong>新建固定 DRAFT；DRAFT 无正式快照不能激活。</li>
              <li><strong>并发：</strong>编辑携带当前 version，过期返回 409 VERSION_CONFLICT，不自动覆盖。</li>
              <li><strong>规格：</strong>核心字段可编辑，但关键规格变更必须新增不可变快照。</li>
            </ul>
          </section>

          <section v-if="isEdit && component" class="data-panel aside-panel">
            <h2 class="panel-title">当前状态</h2>
            <dl class="aside-meta">
              <div><dt>状态</dt><dd>{{ statusMeta(component.status).label }}</dd></div>
              <div><dt>目录编码</dt><dd>{{ component.catalogCode }}</dd></div>
              <div><dt>正式规格</dt><dd>{{ component.specVersion ? `v${component.specVersion}` : '无' }}</dd></div>
              <div><dt>version</dt><dd>{{ component.version }}</dd></div>
              <div><dt>最近更新</dt><dd>{{ formatDateTime(component.updateTime) }}</dd></div>
            </dl>
            <el-button
              v-if="component.currentSpecSnapshotId"
              plain
              class="w-full"
              @click="router.push(`/components/${component.id}/spec-candidates`)"
            >
              查看规格候选与历史
            </el-button>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createComponent,
  updateComponent,
  getComponent,
  listCategories,
  listManufacturers,
  canManageCatalog,
  COMPONENT_STATUS,
  componentStatusMeta,
  BASE_UNIT,
  BASE_UNIT_META
} from '@/api/components'
import {
  notifyApiError,
  normalizeApiError,
  errorTraceId,
  isVersionConflict,
  isComponentDuplicateModel,
  duplicateComponentId,
  isComponentNotFound,
  promptVersionConflict
} from '@/utils/apiError'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'
import RegionState from '@/components/RegionState.vue'
import MockStateBanner from '@/components/MockStateBanner.vue'

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => route.name === 'ComponentEdit')
const canManage = computed(() => canManageCatalog())
const readOnlyInactive = computed(() => component.value?.status === COMPONENT_STATUS.INACTIVE)

const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const submitting = ref(false)

const component = ref(null)
const categoryTree = ref([])
const manufacturers = ref([])
const formRef = ref(null)

const baseUnitOptions = Object.entries(BASE_UNIT).map(([, value]) => ({
  value,
  label: BASE_UNIT_META[value].label
}))

const form = reactive({
  name: '',
  manufacturerId: null,
  categoryId: null,
  mpn: '',
  packageType: '',
  baseUnit: BASE_UNIT.PCS,
  description: ''
})

const rules = {
  name: [
    { required: true, message: '请输入型号名称', trigger: 'blur' },
    { min: 2, max: 120, message: '型号名称需为 2-120 字符', trigger: 'blur' }
  ],
  manufacturerId: [{ required: true, message: '请选择制造商', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  mpn: [
    { required: true, message: '请输入 MPN', trigger: 'blur' },
    { max: 120, message: 'MPN 最多 120 字符', trigger: 'blur' }
  ],
  packageType: [
    { required: true, message: '请输入封装', trigger: 'blur' },
    { max: 80, message: '封装最多 80 字符', trigger: 'blur' }
  ],
  baseUnit: [{ required: true, message: '请选择基础单位', trigger: 'change' }]
}

function statusMeta(status) {
  return componentStatusMeta(status)
}

function goBack() {
  if (isEdit.value) router.push(`/components/${route.params.id}`)
  else router.push('/components')
}

async function initialize() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  try {
    const [categories, manufacturerPage] = await Promise.all([
      listCategories(),
      listManufacturers({ page: 1, pageSize: 100 })
    ])
    categoryTree.value = categories || []
    manufacturers.value = manufacturerPage?.list || []

    if (isEdit.value) {
      const detail = await getComponent(route.params.id)
      component.value = detail
      form.name = detail.name
      form.manufacturerId = detail.manufacturerId
      form.categoryId = detail.categoryId
      form.mpn = detail.mpn
      form.packageType = detail.packageType
      form.baseUnit = detail.baseUnit
      form.description = detail.description || ''
    }
  } catch (err) {
    const normalized = normalizeApiError(err)
    loadError.value = normalized.msg || '加载失败'
    loadTraceId.value = errorTraceId(err)
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
    const payload = {
      name: form.name.trim(),
      manufacturerId: form.manufacturerId,
      categoryId: form.categoryId,
      mpn: form.mpn.trim(),
      packageType: form.packageType.trim(),
      baseUnit: form.baseUnit,
      description: form.description.trim() || null
    }

    if (isEdit.value) {
      const updated = await updateComponent(component.value.id, {
        ...payload,
        version: component.value.version
      })
      ElMessage.success(`核心字段已保存，version ${updated.version}`)
      router.push(`/components/${updated.id}`)
    } else {
      const created = await createComponent(payload, createIdempotencyKey('w44-component'))
      ElMessage.success(`已创建 DRAFT 型号 ${created.catalogCode}，version ${created.version}`)
      router.push(`/components/${created.id}`)
    }
  } catch (err) {
    if (isVersionConflict(err)) {
      promptVersionConflict(() => initialize())
      return
    }
    if (isComponentDuplicateModel(err)) {
      const existingId = duplicateComponentId(err)
      try {
        await ElMessageBox.confirm(
          '同一制造商下已存在相同 MPN（全状态唯一）。请打开已有型号核对，或更换制造商/MPN。',
          '重复型号（409）',
          {
            confirmButtonText: existingId ? '查看已有型号' : '知道了',
            cancelButtonText: '关闭',
            type: 'warning'
          }
        )
        if (existingId) router.push(`/components/${existingId}`)
      } catch {
        // user closed
      }
      return
    }
    if (isComponentNotFound(err)) {
      ElMessage.error(normalizeApiError(err).msg || '型号不存在或不可见')
      return
    }
    const traceId = errorTraceId(err)
    notifyApiError(err)
    if (traceId) ElMessage.info(`traceId：${traceId}`)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (!canManage.value) return
  initialize()
})
</script>

<style scoped>
.component-editor-page {
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

.editor-alert {
  margin: 0;
}

.editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--usn-space-4);
  align-items: start;
}

.editor-form-panel {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--usn-space-4);
}

.w-full {
  width: 100%;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--usn-space-2);
  padding-top: var(--usn-space-3);
  border-top: 1px solid var(--usn-line);
}

.editor-aside {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.aside-panel {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
}

.panel-title {
  margin: 0;
  font-size: var(--usn-font-size-panel-title);
  font-weight: 700;
  color: var(--usn-ink-900);
}

.aside-list {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  color: var(--usn-ink-700);
  font-size: var(--usn-font-size-help);
  line-height: 1.6;
}

.aside-list code {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  color: var(--usn-blue-700);
}

.aside-meta {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  margin: 0;
}

.aside-meta div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--usn-space-2);
}

.aside-meta dt {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.aside-meta dd {
  margin: 0;
  font-size: var(--usn-font-size-help);
  font-weight: 600;
  color: var(--usn-ink-900);
  overflow-wrap: anywhere;
}

@media (max-width: 1024px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
