<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'edit' ? '编辑采集计划' : '新建采集计划'"
    width="720px"
    class="plan-dialog"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item label="计划名称" prop="name">
          <el-input v-model="form.name" maxlength="120" show-word-limit placeholder="例如 5V 输出 CSV 采集" />
        </el-form-item>
        <el-form-item label="采集来源" prop="sourceType">
          <el-select v-model="form.sourceType" class="full-width" :disabled="mode === 'edit'">
            <el-option
              v-for="type in sourceTypes"
              :key="type"
              :label="sourceTypeMeta(type).label"
              :value="type"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人" prop="ownerId">
          <el-select v-model="form.ownerId" class="full-width" placeholder="项目成员">
            <el-option v-for="m in members" :key="m.userId" :label="`${m.name}（${m.projectRole}）`" :value="m.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联实验记录 ID（可选）" prop="linkedExperimentRecordId">
          <el-input-number
            v-model="form.linkedExperimentRecordId"
            class="full-width"
            :min="1"
            :controls="false"
            placeholder="填写记录 ID 可建立来源关系"
          />
        </el-form-item>
      </div>

      <el-form-item label="采集目的" prop="purpose">
        <el-input v-model="form.purpose" type="textarea" :rows="3" maxlength="1000" show-word-limit />
      </el-form-item>

      <el-form-item label="保留说明">
        <el-input v-model="form.retentionNote" maxlength="500" show-word-limit placeholder="原件与快照的保留约定（可选）" />
      </el-form-item>

      <div class="metrics-block">
        <div class="metrics-block__head">
          <span class="metrics-block__title">指标定义</span>
          <el-button size="small" :icon="Plus" @click="addMetric">添加指标</el-button>
        </div>
        <p class="text-help muted">
          指标编码创建后进入计划快照；CSV 映射与人工观测只能使用此处定义的指标。
        </p>

        <div v-for="(item, index) in form.metricDefinitions" :key="index" class="metric-row">
          <el-input v-model="item.code" placeholder="编码，如 voltage" class="metric-row__code" maxlength="50" />
          <el-input v-model="item.name" placeholder="名称，如 电压" class="metric-row__name" maxlength="50" />
          <el-select v-model="item.valueType" class="metric-row__type">
            <el-option v-for="t in valueTypes" :key="t" :label="valueTypeMeta(t).label" :value="t" />
          </el-select>
          <el-input v-model="item.unit" placeholder="单位" class="metric-row__unit" maxlength="20" />
          <el-checkbox v-model="item.required" class="metric-row__required">必填</el-checkbox>
          <el-button
            text
            type="danger"
            :disabled="form.metricDefinitions.length <= 1"
            @click="removeMetric(index)"
          >
            删除
          </el-button>
        </div>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ mode === 'edit' ? '保存计划' : '创建计划' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import {
  COLLECTION_SOURCE_TYPE,
  COLLECTION_VALUE_TYPE,
  sourceTypeMeta,
  valueTypeMeta
} from '@/api/collection'
import { userStore } from '@/store/user'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  mode: { type: String, default: 'create' },
  plan: { type: Object, default: null },
  members: { type: Array, default: () => [] },
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const sourceTypes = [COLLECTION_SOURCE_TYPE.FILE, COLLECTION_SOURCE_TYPE.MANUAL]
const valueTypes = [
  COLLECTION_VALUE_TYPE.DECIMAL,
  COLLECTION_VALUE_TYPE.INTEGER,
  COLLECTION_VALUE_TYPE.BOOLEAN,
  COLLECTION_VALUE_TYPE.STRING
]

const formRef = ref(null)
const form = ref(emptyForm())

const rules = {
  name: [
    { required: true, message: '请填写计划名称', trigger: 'blur' },
    { min: 2, max: 120, message: '计划名称需为 2-120 字符', trigger: 'blur' }
  ],
  sourceType: [{ required: true, message: '请选择采集来源', trigger: 'change' }],
  ownerId: [{ required: true, message: '请选择负责人', trigger: 'change' }],
  purpose: [
    { required: true, message: '请填写采集目的', trigger: 'blur' },
    { min: 1, max: 1000, message: '采集目的需为 1-1000 字符', trigger: 'blur' }
  ]
}

function emptyMetric() {
  return { code: '', name: '', valueType: COLLECTION_VALUE_TYPE.DECIMAL, unit: '', required: true }
}

function emptyForm() {
  return {
    name: '',
    purpose: '',
    sourceType: COLLECTION_SOURCE_TYPE.FILE,
    ownerId: null,
    retentionNote: '',
    linkedExperimentRecordId: null,
    metricDefinitions: [emptyMetric()]
  }
}

function defaultOwner() {
  const currentId = Number(userStore?.userInfo?.id)
  if (props.members.some((m) => Number(m.userId) === currentId)) return currentId
  return props.members[0]?.userId ?? null
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    if (props.mode === 'edit' && props.plan) {
      form.value = {
        name: props.plan.name,
        purpose: props.plan.purpose,
        sourceType: props.plan.sourceType,
        ownerId: props.plan.ownerId,
        retentionNote: props.plan.retentionNote || '',
        linkedExperimentRecordId: props.plan.linkedExperimentRecordId ?? null,
        metricDefinitions: props.plan.metricDefinitions.map((m) => ({ ...m }))
      }
    } else {
      form.value = { ...emptyForm(), ownerId: defaultOwner() }
    }
    formRef.value?.clearValidate()
  }
)

function addMetric() {
  form.value.metricDefinitions.push(emptyMetric())
}

function removeMetric(index) {
  form.value.metricDefinitions.splice(index, 1)
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const metrics = form.value.metricDefinitions
  if (!metrics.length) return
  for (const item of metrics) {
    if (!/^[a-z][a-z0-9_]{1,49}$/.test(String(item.code || '').trim())) {
      formRef.value?.validate().catch(() => {})
      return
    }
  }
  emit('submit', {
    name: form.value.name.trim(),
    purpose: form.value.purpose.trim(),
    sourceType: form.value.sourceType,
    ownerId: form.value.ownerId,
    retentionNote: form.value.retentionNote.trim() || null,
    linkedExperimentRecordId: form.value.linkedExperimentRecordId,
    metricDefinitions: metrics.map((m) => ({
      code: m.code.trim(),
      name: m.name.trim(),
      valueType: m.valueType,
      unit: m.unit.trim(),
      required: Boolean(m.required)
    })),
    version: props.plan?.version
  })
}
</script>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--usn-space-4);
}

.full-width {
  width: 100%;
}

.metrics-block {
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  padding-top: var(--usn-space-2);
  border-top: 1px solid var(--usn-line);
}

.metrics-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-3);
}

.metrics-block__title {
  font-weight: 600;
  color: var(--usn-ink-900);
}

.metric-row {
  display: grid;
  grid-template-columns: 1.2fr 1.2fr 100px 80px auto auto;
  align-items: center;
  gap: var(--usn-space-2);
}

.metric-row__code,
.metric-row__name,
.metric-row__type,
.metric-row__unit {
  width: 100%;
}

.metric-row__required {
  margin-right: 0;
}

@media (max-width: 767px) {
  :global(.plan-dialog) {
    width: 94vw !important;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .metric-row {
    grid-template-columns: 1fr 1fr;
    padding-bottom: var(--usn-space-2);
    border-bottom: 1px dashed var(--usn-line);
  }
}
</style>
