<template>
  <div class="issue-lines-editor">
    <div v-for="(line, index) in lines" :key="line.key" class="issue-line-row">
      <div class="issue-line-row__component">
        <span class="issue-line-label">元器件 {{ index + 1 }}</span>
        <el-select
          v-model="line.componentId"
          filterable
          remote
          clearable
          :remote-method="search"
          :loading="searching"
          placeholder="搜索名称 / MPN / 制造商"
          style="width: 100%"
          @change="onComponentChange(line)"
        >
          <el-option
            v-for="component in options"
            :key="component.id"
            :label="`${component.name} · ${component.mpn}`"
            :value="component.id"
          >
            <div class="component-option">
              <span>{{ component.name }}</span>
              <small class="muted">
                {{ component.mpn }} · 可用
                {{ formatQuantityUnit(component.availableQuantity, component.baseUnit) }}
              </small>
            </div>
          </el-option>
        </el-select>
        <p v-if="line.componentId" class="text-help muted component-hint">
          {{ line.manufacturerName || '—' }} · MPN {{ line.mpn || '—' }} · 当前可用
          {{ formatQuantityUnit(line.availableQuantity, line.unit) }}
        </p>
      </div>
      <label class="issue-line-row__quantity">
        <span class="issue-line-label">领用数量 *</span>
        <el-input-number
          v-model="line.requestedQuantity"
          :min="0.001"
          :precision="3"
          :step="1"
          controls-position="right"
          style="width: 100%"
        />
        <span class="issue-line-label">{{ line.unit || 'PCS' }}</span>
      </label>
      <div class="issue-line-row__actions">
        <el-button text type="danger" :disabled="lines.length <= 1" @click="removeLine(index)">移除</el-button>
      </div>
    </div>

    <el-button plain :icon="Plus" @click="addLine">添加明细</el-button>
    <p class="text-help muted">
      同一申请同一元器件只能出现一行；数量必须大于 0，精度 0.001。提交后由服务端按 FEFO 批次分配并预占。
    </p>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { formatQuantityUnit, searchIssueComponents } from '@/api/inventoryIssuance'

const props = defineProps({
  modelValue: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue'])

const lines = ref([])
const options = ref([])
const searching = ref(false)
let seed = 0

function normalizeLine(line) {
  seed += 1
  return {
    key: line.id ? `line-${line.id}` : `new-${seed}`,
    id: line.id || null,
    componentId: line.componentId || null,
    componentName: line.componentName || '',
    manufacturerName: line.manufacturerName || '',
    mpn: line.mpn || '',
    unit: line.unit || 'PCS',
    requestedQuantity: Number(line.requestedQuantity || 1),
    availableQuantity: line.availableQuantity ?? null
  }
}

function syncOut() {
  emit('update:modelValue', lines.value.map((line) => ({
    id: line.id,
    componentId: line.componentId,
    requestedQuantity: Number(line.requestedQuantity || 0)
  })))
}

function addLine() {
  seed += 1
  lines.value.push({
    key: `new-${seed}`,
    id: null,
    componentId: null,
    componentName: '',
    manufacturerName: '',
    mpn: '',
    unit: 'PCS',
    requestedQuantity: 1,
    availableQuantity: null
  })
  syncOut()
}

function removeLine(index) {
  lines.value.splice(index, 1)
  syncOut()
}

async function search(keyword) {
  searching.value = true
  try {
    const response = await searchIssueComponents({ keyword })
    options.value = response?.list || response?.items || []
  } finally {
    searching.value = false
  }
}

function onComponentChange(line) {
  const component = options.value.find((item) => Number(item.id) === Number(line.componentId))
  if (component) {
    line.componentName = component.name
    line.manufacturerName = component.manufacturerName
    line.mpn = component.mpn
    line.unit = component.baseUnit
    line.availableQuantity = component.availableQuantity
  }
  const duplicated = lines.value.filter((item) => Number(item.componentId) === Number(line.componentId))
  if (duplicated.length > 1) {
    line.componentId = null
    line.componentName = ''
    line.availableQuantity = null
  }
  syncOut()
}

function loadInitial(value) {
  lines.value = (value || []).length
    ? value.map(normalizeLine)
    : [normalizeLine({ requestedQuantity: 1 })]
  syncOut()
}

onMounted(() => {
  loadInitial(props.modelValue)
  search('')
})

defineExpose({ addLine })
</script>

<style scoped>
.issue-lines-editor {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.issue-line-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px 72px;
  gap: var(--usn-space-3);
  align-items: start;
  padding: var(--usn-space-3);
  border: 1px solid var(--usn-border-color, #e4e7ed);
  border-radius: 8px;
}

.issue-line-row__component,
.issue-line-row__quantity {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.issue-line-row__actions {
  display: flex;
  justify-content: flex-end;
  align-items: flex-end;
  height: 100%;
}

.issue-line-label {
  font-size: 12px;
  color: var(--usn-text-secondary, #909399);
}

.component-hint {
  margin: 0;
}

.component-option {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

@media (max-width: 768px) {
  .issue-line-row {
    grid-template-columns: 1fr;
  }

  .issue-line-row__actions {
    justify-content: flex-start;
  }
}
</style>
