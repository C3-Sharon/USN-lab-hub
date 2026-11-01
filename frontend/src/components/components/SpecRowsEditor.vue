<template>
  <div class="spec-rows">
    <div v-if="mode === 'raw'" class="spec-table spec-table--raw">
      <div class="spec-head">
        <span>原始名称</span>
        <span>原始值</span>
        <span>单位</span>
        <span />
      </div>
      <div v-for="(row, index) in rows" :key="`raw-${index}`" class="spec-row">
        <el-input v-model="row.name" placeholder="如 Supply voltage" maxlength="200" @input="commit" />
        <el-input v-model="row.value" placeholder="如 1.8 V to 5.5 V" maxlength="500" @input="commit" />
        <el-input v-model="row.unit" placeholder="如 V" maxlength="20" @input="commit" />
        <el-button :icon="Delete" circle plain type="danger" aria-label="删除该行" @click="remove(index)" />
      </div>
    </div>

    <div v-else class="spec-table spec-table--normalized">
      <div class="spec-head">
        <span>规格编码</span>
        <span>显示名</span>
        <span>类型</span>
        <span>标准化值</span>
        <span>单位</span>
        <span>排序</span>
        <span />
      </div>
      <div v-for="(row, index) in rows" :key="`spec-${index}`" class="spec-row">
        <el-input v-model="row.specCode" placeholder="supply_voltage_range" maxlength="50" @input="commit" />
        <el-input v-model="row.label" placeholder="供电电压范围" maxlength="80" @input="commit" />
        <el-select v-model="row.valueType" @change="commit">
          <el-option v-for="item in valueTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-model="row.normalizedValue" placeholder="1.8-5.5" @input="commit" />
        <el-input v-model="row.unit" placeholder="V" maxlength="20" @input="commit" />
        <el-input-number v-model="row.sortOrder" :min="0" :max="9999" :step="10" controls-position="right" @change="commit" />
        <el-button :icon="Delete" circle plain type="danger" aria-label="删除该行" @click="remove(index)" />
      </div>
    </div>

    <div class="spec-rows__footer">
      <el-button size="small" :icon="Plus" :disabled="rows.length >= 50" @click="add">新增一行</el-button>
      <span class="text-help muted">
        {{ rows.length }} / 50 项；{{ mode === 'raw' ? '原始值按来源原文保留，不得猜测未知单位' : '规格编码需为 [a-z][a-z0-9_]，同一快照内唯一' }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'
import { VALUE_TYPE, VALUE_TYPE_META } from '@/api/components'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  mode: {
    type: String,
    default: 'raw',
    validator: (v) => ['raw', 'normalized'].includes(v)
  }
})

const emit = defineEmits(['update:modelValue'])

const valueTypeOptions = Object.entries(VALUE_TYPE).map(([, value]) => ({
  value,
  label: VALUE_TYPE_META[value].label
}))

function cloneRows(value) {
  return (Array.isArray(value) ? value : []).map((row) => ({ ...row }))
}

const rows = ref(cloneRows(props.modelValue))

watch(
  () => props.modelValue,
  (value) => { rows.value = cloneRows(value) },
  { deep: true }
)

function commit() {
  emit('update:modelValue', rows.value.map((row) => ({ ...row })))
}

function add() {
  if (props.mode === 'raw') {
    rows.value.push({ name: '', value: '', unit: '' })
  } else {
    rows.value.push({
      specCode: '',
      label: '',
      valueType: VALUE_TYPE.STRING,
      normalizedValue: '',
      unit: '',
      sortOrder: rows.value.length * 10 + 10
    })
  }
  commit()
}

function remove(index) {
  rows.value.splice(index, 1)
  commit()
}
</script>

<style scoped>
.spec-rows {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-3);
}

.spec-table {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
}

.spec-head {
  display: grid;
  gap: var(--usn-space-2);
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-500);
}

.spec-table--raw .spec-head,
.spec-table--raw .spec-row {
  grid-template-columns: 1.2fr 1.4fr 0.6fr 40px;
}

.spec-table--normalized .spec-head,
.spec-table--normalized .spec-row {
  grid-template-columns: 1.1fr 1fr 0.9fr 1fr 0.55fr 0.8fr 40px;
}

.spec-row {
  display: grid;
  gap: var(--usn-space-2);
  align-items: center;
}

.spec-rows__footer {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
  flex-wrap: wrap;
}

@media (max-width: 900px) {
  .spec-head {
    display: none;
  }

  .spec-table--raw .spec-row,
  .spec-table--normalized .spec-row {
    grid-template-columns: 1fr;
    padding: var(--usn-space-3);
    border: 1px dashed var(--usn-line);
    border-radius: var(--usn-radius-md);
  }

  .spec-row .el-button {
    justify-self: start;
  }
}
</style>
