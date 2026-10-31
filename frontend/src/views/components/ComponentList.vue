<template>
  <div class="component-list-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">元器件目录</h1>
        <p class="page-subtitle muted">
          正式目录仅展示启用型号；规格以不可变快照版本管理，无图型号使用确定性占位
        </p>
      </div>
      <div class="page-header__actions">
        <el-tag class="domain-tag" effect="plain" size="large">采购 · 库存基础</el-tag>
        <el-button v-if="canManage" :icon="Plus" type="primary" @click="goCreate">新建型号</el-button>
      </div>
    </header>

    <MockStateBanner />

    <RegionState
      v-if="!canAccess"
      state="permission"
      variant="section"
      description="当前账号无权访问元器件目录"
      sub-title="元器件目录仅对已登录的实验室成员开放"
    />

    <template v-else>
      <div class="catalog-links" v-if="canReadInactive || canManage">
        <el-button :icon="Share" plain @click="router.push('/component-categories')">分类树管理</el-button>
        <el-button :icon="OfficeBuilding" plain @click="router.push('/component-manufacturers')">制造商管理</el-button>
        <span class="text-help muted">分类与制造商只停用/恢复，不物理删除</span>
      </div>

      <div class="filter-bar toolbar">
        <el-input
          v-model="query.keyword"
          class="filter-keyword"
          placeholder="搜索 MPN、名称或制造商"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-tree-select
          v-model="query.categoryId"
          class="filter-select"
          :data="categoryTree"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="全部分类"
          clearable
          check-strictly
          @change="handleSearch"
        />

        <el-select
          v-if="canReadInactive"
          v-model="query.status"
          class="filter-select"
          placeholder="全部状态"
          clearable
          @change="handleSearch"
        >
          <el-option label="草稿" :value="COMPONENT_STATUS.DRAFT" />
          <el-option label="启用" :value="COMPONENT_STATUS.ACTIVE" />
          <el-option label="停用" :value="COMPONENT_STATUS.INACTIVE" />
        </el-select>

        <el-select
          v-model="query.manufacturerId"
          class="filter-select"
          placeholder="全部制造商"
          clearable
          filterable
          @change="handleSearch"
        >
          <el-option
            v-for="item in manufacturers"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>

        <el-select v-model="query.sortBy" class="filter-select" @change="handleSearch">
          <el-option label="最近更新" value="updateTime" />
          <el-option label="创建时间" value="createTime" />
          <el-option label="MPN 升序" value="mpn" />
          <el-option label="名称" value="name" />
          <el-option label="制造商" value="manufacturerName" />
        </el-select>
      </div>

      <div class="list-summary">
        <span class="text-help muted">
          共 {{ total }} 个型号
          <template v-if="!canReadInactive">（普通成员仅可见启用型号）</template>
        </span>
        <el-tag v-if="currentScenario === 'inactive'" type="info" size="small">已停用视图</el-tag>
      </div>

      <RegionState
        v-if="loading"
        state="loading"
        variant="card"
        description="正在加载元器件目录"
      />

      <RegionState
        v-else-if="loadError"
        state="error"
        variant="section"
        description="元器件目录加载失败"
        :error-message="`${loadError}${loadTraceId ? ` · traceId ${loadTraceId}` : ''}`"
        @retry="loadList"
      />

      <RegionState
        v-else-if="!list.length"
        state="empty"
        variant="card"
        :description="emptyDescription"
        sub-title="检查筛选条件，或由管理员新建型号草稿"
      >
        <div class="empty-actions">
          <el-button v-if="hasFilter" @click="resetFilter">清除筛选条件</el-button>
          <el-button v-if="canManage" type="primary" @click="goCreate">新建型号</el-button>
        </div>
      </RegionState>

      <div v-else class="component-grid">
        <article
          v-for="item in list"
          :key="item.id"
          class="component-card"
          :class="{ 'component-card--inactive': item.status === COMPONENT_STATUS.INACTIVE }"
          @click="goDetail(item.id)"
        >
          <div class="component-card__media">
            <ComponentImage
              :media-id="item.primaryMediaId"
              :placeholder-key="item.placeholderKey"
              :alt="`${item.mpn} 图片`"
              compact
            />
          </div>

          <div class="component-card__body">
            <div class="component-card__head">
              <h3 class="component-card__title">{{ item.name }}</h3>
              <el-tag :type="statusMeta(item.status).tagType" size="small" effect="light">
                {{ statusMeta(item.status).label }}
              </el-tag>
            </div>

            <div class="component-card__mpn">
              <span class="mpn-text">{{ item.mpn }}</span>
              <span class="text-help muted">{{ item.catalogCode }}</span>
            </div>

            <p class="component-card__path">
              {{ categoryPathText(item.categoryPath) || '未分类' }}
              <span class="text-help muted"> · {{ item.packageType }}</span>
            </p>

            <dl class="component-card__meta">
              <div>
                <dt>制造商</dt>
                <dd>{{ item.manufacturerName || '—' }}</dd>
              </div>
              <div>
                <dt>规格版本</dt>
                <dd>{{ item.specVersion ? `v${item.specVersion}` : '无正式快照' }}</dd>
              </div>
              <div>
                <dt>基础单位</dt>
                <dd>{{ baseUnitMeta(item.baseUnit).label }}</dd>
              </div>
              <div>
                <dt>更新时间</dt>
                <dd>{{ formatDateTime(item.updateTime) }}</dd>
              </div>
            </dl>

            <div class="component-card__footer">
              <span class="text-help muted">
                {{ item.status === COMPONENT_STATUS.DRAFT ? '草稿：需正式快照后才能激活' : `version ${item.version}` }}
              </span>
              <el-button size="small" @click.stop="goDetail(item.id)">查看详情</el-button>
            </div>
          </div>
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
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Plus, Share, OfficeBuilding } from '@element-plus/icons-vue'
import {
  listComponents,
  listCategories,
  listManufacturers,
  canAccessComponents,
  canManageCatalog,
  canReadInactiveCatalog,
  COMPONENT_STATUS,
  componentStatusMeta,
  baseUnitMeta
} from '@/api/components'
import { notifyApiError, normalizeApiError, errorTraceId } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'
import { currentMockScenario } from '@/utils/mockScenario'
import RegionState from '@/components/RegionState.vue'
import MockStateBanner from '@/components/MockStateBanner.vue'
import ComponentImage from '@/components/components/ComponentImage.vue'

const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const loadTraceId = ref('')
const list = ref([])
const total = ref(0)
const categoryTree = ref([])
const manufacturers = ref([])

const canAccess = computed(() => canAccessComponents())
const canManage = computed(() => canManageCatalog())
const canReadInactive = computed(() => canReadInactiveCatalog())
const currentScenario = currentMockScenario()

const initialStatus = (() => {
  if (!canReadInactiveCatalog()) return ''
  return currentMockScenario() === 'inactive' ? COMPONENT_STATUS.INACTIVE : ''
})()

const query = ref({
  page: 1,
  pageSize: 12,
  keyword: '',
  categoryId: null,
  status: initialStatus,
  manufacturerId: null,
  sortBy: 'updateTime',
  sortOrder: 'desc'
})

const hasFilter = computed(() =>
  Boolean(query.value.keyword || query.value.categoryId || query.value.status || query.value.manufacturerId)
)

const emptyDescription = computed(() =>
  hasFilter.value ? '没有符合筛选条件的元器件型号' : '目录中暂无元器件型号'
)

function statusMeta(status) {
  return componentStatusMeta(status)
}

function categoryPathText(path) {
  return Array.isArray(path) ? path.map((p) => p.name).join(' / ') : ''
}

function goCreate() {
  router.push('/components/new')
}

function goDetail(id) {
  router.push(`/components/${id}`)
}

function handleSearch() {
  query.value.page = 1
  loadList()
}

function resetFilter() {
  query.value.keyword = ''
  query.value.categoryId = null
  query.value.status = ''
  query.value.manufacturerId = null
  handleSearch()
}

async function loadReference() {
  try {
    categoryTree.value = await listCategories()
  } catch (err) {
    categoryTree.value = []
  }
  try {
    const res = await listManufacturers({ page: 1, pageSize: 100 })
    manufacturers.value = res?.list || []
  } catch (err) {
    manufacturers.value = []
  }
}

async function loadList() {
  loading.value = true
  loadError.value = ''
  loadTraceId.value = ''
  try {
    const res = await listComponents({
      page: query.value.page,
      pageSize: query.value.pageSize,
      keyword: query.value.keyword || undefined,
      categoryId: query.value.categoryId || undefined,
      includeDescendants: true,
      status: query.value.status || undefined,
      manufacturerId: query.value.manufacturerId || undefined,
      sortBy: query.value.sortBy,
      sortOrder: query.value.sortBy === 'mpn' ? 'asc' : 'desc'
    })
    list.value = res?.list || []
    total.value = res?.total ?? 0
  } catch (err) {
    const normalized = normalizeApiError(err)
    loadError.value = normalized.msg || '加载失败'
    loadTraceId.value = errorTraceId(err)
    list.value = []
    total.value = 0
    notifyApiError(err)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!canAccess.value) return
  await loadReference()
  await loadList()
})
</script>

<style scoped>
.component-list-page {
  max-width: var(--usn-content-max-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-4);
  flex-wrap: wrap;
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
}

.domain-tag {
  color: var(--usn-warning);
  border-color: var(--usn-warning);
  background: var(--usn-canvas);
  font-weight: 600;
}

.catalog-links {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.filter-bar {
  display: flex;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

.filter-keyword {
  flex: 1 1 260px;
}

.filter-select {
  width: 168px;
}

.list-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-3);
}

.component-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--usn-space-4);
  min-height: 200px;
}

.component-card {
  display: flex;
  flex-direction: column;
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  box-shadow: var(--usn-shadow-panel);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.component-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(23, 33, 43, 0.1);
}

.component-card--inactive {
  filter: grayscale(0.6);
  border-color: var(--usn-line);
  background: var(--usn-canvas);
}

.component-card__media {
  border-bottom: 1px solid var(--usn-line);
}

.component-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  padding: var(--usn-space-4);
  flex: 1;
}

.component-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usn-space-2);
}

.component-card__title {
  margin: 0;
  font-size: 16px;
  line-height: 1.35;
  font-weight: 600;
  color: var(--usn-ink-900);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.component-card__mpn {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.mpn-text {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: var(--usn-font-size-body);
  font-weight: 600;
  color: var(--usn-blue-700);
}

.component-card__path {
  margin: 0;
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.component-card__meta {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--usn-space-2);
  padding: var(--usn-space-2) 0;
  margin: 0;
  border-top: 1px solid var(--usn-line);
}

.component-card__meta div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.component-card__meta dt {
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.component-card__meta dd {
  margin: 0;
  font-size: var(--usn-font-size-help);
  font-weight: 600;
  color: var(--usn-ink-900);
  overflow-wrap: anywhere;
}

.component-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-2);
  margin-top: auto;
  padding-top: var(--usn-space-2);
  flex-wrap: wrap;
}

.empty-actions {
  display: flex;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
  justify-content: center;
}

@media (max-width: 1365px) {
  .component-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 767px) {
  .component-grid {
    grid-template-columns: 1fr;
  }

  .filter-select,
  .filter-keyword {
    width: 100%;
    flex: 1 1 100%;
  }

  .component-card__footer {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
