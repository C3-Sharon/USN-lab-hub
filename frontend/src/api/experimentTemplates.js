import request from '@/utils/request'
import { getMockEnabled } from '@/utils/mock'
import { userStore, currentRoles } from '@/store/user'
import { ROLE } from '@/utils/permission'
import { PROJECT_ROLE, findMockProject } from '@/api/projects'

/**
 * 实验模板与版本 API（第 6 周 M02-W02）
 *
 * 契约来源：docs/contracts/EXPERIMENT_TEMPLATE_API.md v1.0（FROZEN）
 *
 * 真实端点（共 12 个）：
 *   POST   /api/experiment/templates                                     创建模板
 *   GET    /api/experiment/templates                                     模板列表（分页/筛选）
 *   GET    /api/experiment/templates/{id}                                模板详情
 *   PUT    /api/experiment/templates/{id}                                编辑模板基本信息
 *   POST   /api/experiment/templates/{id}/copy                           复制系统模板为项目模板
 *   GET    /api/experiment/templates/{id}/versions                       版本列表
 *   GET    /api/experiment/templates/{id}/versions/{vid}                 版本详情（含 Schema）
 *   PUT    /api/experiment/templates/{id}/versions/{vid}                 编辑 DRAFT 版本
 *   PUT    /api/experiment/templates/{id}/versions/{vid}/publish         发布版本
 *   PUT    /api/experiment/templates/{id}/versions/{vid}/deprecate       废弃版本
 *   GET    /api/experiment/templates/{id}/versions/published             已发布版本列表（简要）
 *   POST   /api/experiment/templates/{id}/versions/{vid}/validate-data   数据校验（预览用）
 *
 * mock 开关：VITE_USE_MOCK=true 走本地 mock（字段与契约一致），默认 false 走真实接口。
 * mock 成功 resolve data，失败 reject { code, msg, reason }，与 request.js 拦截器行为一致。
 *
 * 说明：Schema 子集校验（§4）在真实模式下由后端执行（400 SCHEMA_INVALID）；
 *       mock 分支使用本文件内的受控子集校验器模拟后端行为，二者规则一致。
 *       前端不实现完整 JSON Schema 引擎，只镜像契约冻结的受控子集。
 */

// ========== 枚举 ==========

export const TEMPLATE_SCOPE = Object.freeze({
  SYSTEM: 'SYSTEM',
  PROJECT: 'PROJECT'
})

export const TEMPLATE_SCOPE_META = Object.freeze({
  [TEMPLATE_SCOPE.SYSTEM]: { label: '系统模板', tagType: 'primary', desc: '全局通用实验模板' },
  [TEMPLATE_SCOPE.PROJECT]: { label: '项目模板', tagType: 'success', desc: '归属某个项目' }
})

export const TEMPLATE_VERSION_STATUS = Object.freeze({
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  DEPRECATED: 'DEPRECATED'
})

/** 版本状态标签；deprecated 供组件套用灰色 + 删除线样式（契约 §9.9） */
export const TEMPLATE_VERSION_STATUS_META = Object.freeze({
  [TEMPLATE_VERSION_STATUS.DRAFT]: { label: '草稿', tagType: 'info', deprecated: false },
  [TEMPLATE_VERSION_STATUS.PUBLISHED]: { label: '已发布', tagType: 'success', deprecated: false },
  [TEMPLATE_VERSION_STATUS.DEPRECATED]: { label: '已废弃', tagType: 'info', deprecated: true }
})

/** 契约 §3.3 合法版本状态转换（DEPRECATED 为终态） */
export const TEMPLATE_VERSION_TRANSITIONS = Object.freeze({
  [TEMPLATE_VERSION_STATUS.DRAFT]: [TEMPLATE_VERSION_STATUS.PUBLISHED],
  [TEMPLATE_VERSION_STATUS.PUBLISHED]: [TEMPLATE_VERSION_STATUS.DEPRECATED],
  [TEMPLATE_VERSION_STATUS.DEPRECATED]: []
})

export function canTransitionTemplateVersion(from, to) {
  return (TEMPLATE_VERSION_TRANSITIONS[from] || []).includes(to)
}

export function isTerminalTemplateVersionStatus(status) {
  return (TEMPLATE_VERSION_TRANSITIONS[status] || []).length === 0
}

export function templateScopeMeta(scope) {
  return TEMPLATE_SCOPE_META[scope] || { label: scope || '--', tagType: 'info', desc: '' }
}

export function templateVersionStatusMeta(status) {
  return TEMPLATE_VERSION_STATUS_META[status] || { label: status || '--', tagType: 'info', deprecated: false }
}

// ========== JSON Schema 受控子集（契约 §4） ==========

export const SCHEMA_FIELD_TYPE = Object.freeze({
  STRING: 'string',
  NUMBER: 'number',
  INTEGER: 'integer',
  BOOLEAN: 'boolean'
})

export const SCHEMA_FIELD_TYPE_META = Object.freeze({
  [SCHEMA_FIELD_TYPE.STRING]: { label: '字符串' },
  [SCHEMA_FIELD_TYPE.NUMBER]: { label: '数值' },
  [SCHEMA_FIELD_TYPE.INTEGER]: { label: '整数' },
  [SCHEMA_FIELD_TYPE.BOOLEAN]: { label: '布尔值' }
})

export const SCHEMA_WIDGET = Object.freeze({
  INPUT: 'input',
  INPUT_NUMBER: 'input-number',
  SELECT: 'select',
  SWITCH: 'switch',
  TEXTAREA: 'textarea'
})

export const SCHEMA_WIDGET_META = Object.freeze({
  [SCHEMA_WIDGET.INPUT]: { label: '单行文本', types: [SCHEMA_FIELD_TYPE.STRING] },
  [SCHEMA_WIDGET.INPUT_NUMBER]: { label: '数字输入', types: [SCHEMA_FIELD_TYPE.NUMBER, SCHEMA_FIELD_TYPE.INTEGER] },
  [SCHEMA_WIDGET.SELECT]: { label: '下拉选择', types: [SCHEMA_FIELD_TYPE.STRING] },
  [SCHEMA_WIDGET.SWITCH]: { label: '开关', types: [SCHEMA_FIELD_TYPE.BOOLEAN] },
  [SCHEMA_WIDGET.TEXTAREA]: { label: '多行文本', types: [SCHEMA_FIELD_TYPE.STRING] }
})

/** 契约 §4.4 明确不支持的 JSON Schema 关键字 */
export const SCHEMA_UNSUPPORTED_KEYWORDS = Object.freeze([
  '$ref',
  'oneOf',
  'anyOf',
  'allOf',
  'if',
  'then',
  'else',
  'pattern',
  'format',
  'additionalProperties',
  'patternProperties'
])

const SCHEMA_KEYWORD_MESSAGES = Object.freeze({
  $ref: '不支持 $ref 远程引用或内部引用',
  oneOf: '不支持 oneOf 组合 Schema',
  anyOf: '不支持 anyOf 组合 Schema',
  allOf: '不支持 allOf 组合 Schema',
  if: '不支持 if/then/else 条件 Schema',
  then: '不支持 if/then/else 条件 Schema',
  else: '不支持 if/then/else 条件 Schema',
  pattern: '不支持 pattern 正则验证',
  format: '不支持 format 验证器',
  additionalProperties: '不支持 additionalProperties',
  patternProperties: '不支持 patternProperties'
})

export const SCHEMA_MAX_FIELD_COUNT = 50
const FIELD_KEY_PATTERN = /^[a-z][a-z0-9_]*$/

/** 字段可用的控件（按类型推导，编辑器属性面板用） */
export function widgetsForType(type) {
  return Object.entries(SCHEMA_WIDGET_META)
    .filter(([, meta]) => meta.types.includes(type))
    .map(([key]) => key)
}

export function isWidgetCompatible(type, widget) {
  if (!widget) return true
  const meta = SCHEMA_WIDGET_META[widget]
  return Boolean(meta) && meta.types.includes(type)
}

/** 新建字段的默认属性（编辑器「添加字段」用） */
export function buildDefaultField(type = SCHEMA_FIELD_TYPE.STRING, displayOrder = 1) {
  const base = { type, title: '', displayOrder, widget: widgetsForType(type)[0] }
  if (type === SCHEMA_FIELD_TYPE.STRING) return { ...base, maxLength: 200 }
  if (type === SCHEMA_FIELD_TYPE.NUMBER || type === SCHEMA_FIELD_TYPE.INTEGER) return { ...base, minimum: 0, maximum: 100 }
  return base
}

export function countSchemaFields(schemaJson) {
  const props = schemaJson?.properties
  return props && typeof props === 'object' ? Object.keys(props).length : 0
}

export function emptySchemaJson() {
  return { schemaVersion: '1.0', type: 'object', properties: {}, required: [] }
}

/**
 * 受控子集 Schema 校验（契约 §4），返回 { valid, errors: [{ path, message }], fieldCount }。
 * 真实模式下后端返回同形 400 SCHEMA_INVALID；此处用于 mock 分支与编辑器实时预检。
 */
export function validateSchemaJson(schemaJson) {
  const errors = []
  const push = (path, message) => errors.push({ path, message })

  if (!schemaJson || typeof schemaJson !== 'object' || Array.isArray(schemaJson)) {
    return { valid: false, errors: [{ path: 'schemaJson', message: 'Schema 必须为对象' }], fieldCount: 0 }
  }

  if (schemaJson.type !== undefined && schemaJson.type !== 'object') {
    if (schemaJson.type === 'array') {
      push('type', '不支持的类型：array。本阶段仅支持 string、number、integer、boolean')
    } else {
      push('type', '不支持的类型：object。本阶段仅支持扁平结构')
    }
  }

  for (const keyword of SCHEMA_UNSUPPORTED_KEYWORDS) {
    if (schemaJson[keyword] !== undefined) {
      push(keyword, SCHEMA_KEYWORD_MESSAGES[keyword])
    }
  }

  const props = schemaJson.properties
  if (props === undefined) {
    return { valid: false, errors: [{ path: 'properties', message: '缺少 properties 字段' }], fieldCount: 0 }
  }
  if (!props || typeof props !== 'object' || Array.isArray(props)) {
    return { valid: false, errors: [{ path: 'properties', message: 'properties 必须为对象' }], fieldCount: 0 }
  }

  const keys = Object.keys(props)
  if (keys.length > SCHEMA_MAX_FIELD_COUNT) {
    push('properties', `单个版本最多支持 ${SCHEMA_MAX_FIELD_COUNT} 个字段，当前 ${keys.length} 个`)
  }

  for (const key of keys) {
    const fieldPath = `properties.${key}`
    if (key.length > 50) push(fieldPath, '字段 key 最长 50 字符')
    if (!FIELD_KEY_PATTERN.test(key)) {
      push(fieldPath, '字段 key 只能包含小写字母、数字和下划线，且首字母必须为小写')
    }
    validateField(props[key], fieldPath, push)
  }

  validateRequired(schemaJson.required, keys, push)

  return { valid: errors.length === 0, errors, fieldCount: keys.length }
}

function validateField(field, fieldPath, push) {
  if (!field || typeof field !== 'object' || Array.isArray(field)) {
    push(fieldPath, '字段定义必须为对象')
    return
  }

  for (const keyword of SCHEMA_UNSUPPORTED_KEYWORDS) {
    if (field[keyword] !== undefined) {
      push(`${fieldPath}.${keyword}`, SCHEMA_KEYWORD_MESSAGES[keyword])
    }
  }

  const type = field.type
  const typeMeta = SCHEMA_FIELD_TYPE_META[type]
  if (!type) {
    push(`${fieldPath}.type`, '缺少字段类型')
  } else if (!typeMeta) {
    if (type === 'object') push(`${fieldPath}.type`, '不支持的类型：object。本阶段仅支持扁平结构')
    else if (type === 'array') push(`${fieldPath}.type`, '不支持的类型：array。本阶段仅支持 string、number、integer、boolean')
    else push(`${fieldPath}.type`, `不支持的类型：${type}。本阶段仅支持 string、number、integer、boolean`)
  }

  const title = field.title
  if (title === undefined || title === null || String(title).trim() === '') {
    push(`${fieldPath}.title`, '缺少字段显示名称')
  } else if (String(title).length < 2 || String(title).length > 40) {
    push(`${fieldPath}.title`, '字段显示名称需为 2-40 字符')
  }

  if (field.description !== undefined && field.description !== null && String(field.description).length > 200) {
    push(`${fieldPath}.description`, '字段说明最多 200 字符')
  }

  if (field.unit !== undefined && field.unit !== null && String(field.unit).length > 20) {
    push(`${fieldPath}.unit`, '单位最多 20 字符')
  }

  if (field.displayOrder === undefined || field.displayOrder === null) {
    push(`${fieldPath}.displayOrder`, '缺少显示顺序 displayOrder')
  } else if (!Number.isInteger(Number(field.displayOrder))) {
    push(`${fieldPath}.displayOrder`, 'displayOrder 必须为整数')
  }

  if (field.widget !== undefined && field.widget !== null && field.widget !== '') {
    if (!SCHEMA_WIDGET_META[field.widget]) {
      push(`${fieldPath}.widget`, `不支持的控件：${field.widget}`)
    } else if (typeMeta && !isWidgetCompatible(type, field.widget)) {
      push(`${fieldPath}.widget`, `控件 ${field.widget} 不适用于类型 ${type}`)
    }
  }

  if (field.enum !== undefined && field.enum !== null) {
    if (!Array.isArray(field.enum)) {
      push(`${fieldPath}.enum`, 'enum 必须为数组')
    } else if (field.enum.length === 0) {
      push(`${fieldPath}.enum`, 'enum 不能为空数组')
    } else if (typeMeta && !field.enum.every((v) => typeof v === type)) {
      push(`${fieldPath}.enum`, `enum 取值必须与字段类型 ${type} 一致`)
    }
  }

  if (typeMeta && (type === SCHEMA_FIELD_TYPE.NUMBER || type === SCHEMA_FIELD_TYPE.INTEGER)) {
    if (field.minimum !== undefined && field.minimum !== null && typeof field.minimum !== 'number') {
      push(`${fieldPath}.minimum`, 'minimum 必须为数值')
    }
    if (field.maximum !== undefined && field.maximum !== null && typeof field.maximum !== 'number') {
      push(`${fieldPath}.maximum`, 'maximum 必须为数值')
    }
    if (
      typeof field.minimum === 'number' &&
      typeof field.maximum === 'number' &&
      field.minimum > field.maximum
    ) {
      push(`${fieldPath}.maximum`, 'maximum 不能小于 minimum')
    }
  }

  if (typeMeta && type === SCHEMA_FIELD_TYPE.STRING) {
    if (field.minLength !== undefined && field.minLength !== null && !Number.isInteger(Number(field.minLength))) {
      push(`${fieldPath}.minLength`, 'minLength 必须为整数')
    }
    if (field.maxLength !== undefined && field.maxLength !== null && !Number.isInteger(Number(field.maxLength))) {
      push(`${fieldPath}.maxLength`, 'maxLength 必须为整数')
    }
    if (
      Number.isInteger(Number(field.minLength)) &&
      Number.isInteger(Number(field.maxLength)) &&
      Number(field.minLength) > Number(field.maxLength)
    ) {
      push(`${fieldPath}.maxLength`, 'maxLength 不能小于 minLength')
    }
  }

  if (field.default !== undefined && field.default !== null && typeMeta && typeof field.default !== type) {
    push(`${fieldPath}.default`, `default 必须与字段类型 ${type} 一致`)
  }
}

function validateRequired(required, keys, push) {
  if (required === undefined || required === null) return
  if (!Array.isArray(required)) {
    push('required', 'required 必须为数组')
    return
  }
  const keySet = new Set(keys)
  for (const key of required) {
    if (typeof key !== 'string') {
      push('required', 'required 元素必须为字符串')
    } else if (!keySet.has(key)) {
      push('required', `required 引用了不存在的字段：${key}`)
    }
  }
}

/**
 * 数据校验（契约 §4.8）：只校验类型、必填、枚举、范围、长度，不做业务逻辑校验。
 * 返回 { valid, errors: [{ path, message }] }。
 */
export function validateDataAgainstSchema(schemaJson, data) {
  const errors = []
  const push = (path, message) => errors.push({ path, message })
  const schema = schemaJson && typeof schemaJson === 'object' ? schemaJson : {}
  const props = schema.properties && typeof schema.properties === 'object' ? schema.properties : {}
  const required = Array.isArray(schema.required) ? schema.required : []
  const payload = data && typeof data === 'object' ? data : {}

  for (const key of Object.keys(props)) {
    const field = props[key]
    const value = payload[key]
    const present = value !== undefined && value !== null

    if (!present) {
      if (required.includes(key)) push(key, `${key} 是必填字段`)
      continue
    }

    if (field.type && typeof value !== field.type) {
      push(key, `${key} 类型应为 ${field.type}`)
      continue
    }

    if (Array.isArray(field.enum) && field.enum.length > 0 && !field.enum.includes(value)) {
      push(key, `${key} 取值必须是：${field.enum.join('、')}`)
    }

    if (typeof value === 'number') {
      if (typeof field.minimum === 'number' && value < field.minimum) {
        push(key, `${key} 不能小于 ${field.minimum}`)
      }
      if (typeof field.maximum === 'number' && value > field.maximum) {
        push(key, `${key} 不能大于 ${field.maximum}`)
      }
    }

    if (typeof value === 'string') {
      if (Number.isInteger(Number(field.minLength)) && value.length < Number(field.minLength)) {
        push(key, `${key} 长度不能小于 ${field.minLength}`)
      }
      if (Number.isInteger(Number(field.maxLength)) && value.length > Number(field.maxLength)) {
        push(key, `${key} 长度不能大于 ${field.maxLength}`)
      }
    }
  }

  return { valid: errors.length === 0, errors }
}

// ========== 版本号规则（契约 §3.2） ==========

const DRAFT_VERSION_PATTERN = /^0\.(\d+)\.0$/
const SEMVER_PATTERN = /^(\d+)\.(\d+)\.(\d+)$/

export function isDraftVersionNumber(version) {
  return DRAFT_VERSION_PATTERN.test(String(version || ''))
}

export function draftRevision(version) {
  const m = DRAFT_VERSION_PATTERN.exec(String(version || ''))
  return m ? Number(m[1]) : 0
}

export function buildDraftVersion(revision) {
  return `0.${Number(revision) || 1}.0`
}

export function buildPublishedVersion(publishedCount) {
  return `1.${Number(publishedCount) || 0}.0`
}

export function versionCodeOf(version) {
  const m = SEMVER_PATTERN.exec(String(version || ''))
  if (!m) return null
  return Number(m[1]) * 10000 + Number(m[2]) * 100 + Number(m[3])
}

// ========== 权限判定（前端 UI 收敛，真正鉴权以后端为准） ==========

const GLOBAL_MANAGE_ROLES = [ROLE.SYSTEM_ADMIN, ROLE.TEACHER]
const PROJECT_MANAGE_ROLES = [PROJECT_ROLE.OWNER, PROJECT_ROLE.MAINTAINER]

export function canAccessTemplates() {
  return currentRoles().length > 0
}

export function canManageSystemTemplates() {
  return currentRoles().some((role) => GLOBAL_MANAGE_ROLES.includes(role))
}

function projectRoleOf(projectId) {
  if (projectId == null) return null
  return findMockProject(projectId)?.myRole || null
}

export function canManageProjectTemplates(projectId) {
  if (canManageSystemTemplates()) return true
  return PROJECT_MANAGE_ROLES.includes(projectRoleOf(projectId))
}

/** 是否可管理某模板（基本信息 / DRAFT Schema / 发布 / 废弃） */
export function canManageTemplate(template) {
  if (!template) return false
  if (canManageSystemTemplates()) return true
  if (template.scope === TEMPLATE_SCOPE.PROJECT) return canManageProjectTemplates(template.projectId)
  return false
}

// ========== Mock 数据 ==========

/** mock 模式未登录时假定的当前用户（张同学，MEMBER，项目 5 成员 / 项目 10 负责人） */
const MOCK_CURRENT_USER_ID = 2

const MOCK_USER_NAMES = { 1: '管理员', 2: '张同学', 3: '李同学' }

const MOCK_PROJECT_NAMES = { 5: '功率监测演示项目', 10: '智能气象站项目' }

function field(key, def) {
  return [key, def]
}

function schemaOf(fields, required = []) {
  return {
    schemaVersion: '1.0',
    type: 'object',
    properties: Object.fromEntries(fields),
    required
  }
}

const MOCK_TEMPLATES = [
  {
    id: 1,
    title: '电压测量实验',
    description: '基础直流电压测量实验模板，适用于电路测试入门。',
    scope: 'SYSTEM',
    projectId: null,
    category: '电路测试',
    createdBy: 1,
    createTime: '2026-10-08T10:00:00',
    updateTime: '2026-10-08T16:00:00'
  },
  {
    id: 2,
    title: '传感器标定实验',
    description: '温湿度传感器标定流程模板，含标定点和环境条件记录。',
    scope: 'SYSTEM',
    projectId: null,
    category: '传感器',
    createdBy: 1,
    createTime: '2026-09-28T09:00:00',
    updateTime: '2026-10-07T11:30:00'
  },
  {
    id: 3,
    title: '电路基础实验',
    description: '欧姆定律与串并联电路验证模板（草稿，尚未发布）。',
    scope: 'SYSTEM',
    projectId: null,
    category: '电路基础',
    createdBy: 1,
    createTime: '2026-10-10T14:00:00',
    updateTime: '2026-10-10T14:00:00'
  },
  {
    id: 4,
    title: 'PM-001 功率校准实验',
    description: 'PM-001 电表功率读数校准实验，记录标准值与实测值。',
    scope: 'PROJECT',
    projectId: 5,
    category: '校准',
    createdBy: 2,
    createTime: '2026-10-09T09:00:00',
    updateTime: '2026-10-11T10:00:00'
  },
  {
    id: 5,
    title: '气象站数据采集实验',
    description: '智能气象站多要素数据采集实验，记录环境参数。',
    scope: 'PROJECT',
    projectId: 10,
    category: '数据采集',
    createdBy: 2,
    createTime: '2026-10-02T10:00:00',
    updateTime: '2026-10-05T15:00:00'
  }
]

const MOCK_VERSIONS = [
  // 模板 1：1 个 PUBLISHED + 1 个 DRAFT
  {
    id: 2,
    templateId: 1,
    version: '1.0.0',
    versionCode: 10000,
    status: 'PUBLISHED',
    schemaJson: schemaOf(
      [
        field('voltage', { type: 'number', title: '电压', description: '测量的直流电压值', unit: 'V', minimum: 0, maximum: 24, default: 5, displayOrder: 1, widget: 'input-number' }),
        field('test_type', { type: 'string', title: '测试类型', enum: ['直流', '交流', '脉冲'], default: '直流', displayOrder: 2, widget: 'select' }),
        field('is_pass', { type: 'boolean', title: '是否通过', default: false, displayOrder: 3, widget: 'switch' }),
        field('remarks', { type: 'string', title: '备注', maxLength: 500, displayOrder: 4, widget: 'textarea' })
      ],
      ['voltage', 'test_type']
    ),
    changelog: '初始发布版本',
    publishedBy: 1,
    publishedByName: '管理员',
    publishedAt: '2026-10-08T14:00:00',
    deprecatedReason: null,
    deprecatedBy: null,
    deprecatedAt: null,
    createdBy: 1,
    createTime: '2026-10-08T10:00:00',
    updateTime: '2026-10-08T14:00:00'
  },
  {
    id: 3,
    templateId: 1,
    version: '0.2.0',
    versionCode: null,
    status: 'DRAFT',
    schemaJson: schemaOf(
      [
        field('voltage', { type: 'number', title: '电压', description: '测量的直流电压值', unit: 'V', minimum: 0, maximum: 24, default: 5, displayOrder: 1, widget: 'input-number' }),
        field('current', { type: 'number', title: '电流', unit: 'mA', minimum: 0, maximum: 1000, displayOrder: 2, widget: 'input-number' }),
        field('test_type', { type: 'string', title: '测试类型', enum: ['直流', '交流', '脉冲'], default: '直流', displayOrder: 3, widget: 'select' }),
        field('is_pass', { type: 'boolean', title: '是否通过', default: false, displayOrder: 4, widget: 'switch' }),
        field('remarks', { type: 'string', title: '备注', maxLength: 500, displayOrder: 5, widget: 'textarea' })
      ],
      ['voltage', 'test_type']
    ),
    changelog: '增加电流字段',
    publishedBy: null,
    publishedByName: null,
    publishedAt: null,
    deprecatedReason: null,
    deprecatedBy: null,
    deprecatedAt: null,
    createdBy: 1,
    createTime: '2026-10-08T15:00:00',
    updateTime: '2026-10-08T16:00:00'
  },
  // 模板 2：2 个 PUBLISHED + 1 个 DRAFT
  {
    id: 22,
    templateId: 2,
    version: '1.1.0',
    versionCode: 10100,
    status: 'PUBLISHED',
    schemaJson: schemaOf(
      [
        field('temperature', { type: 'number', title: '温度', unit: '℃', minimum: -20, maximum: 80, displayOrder: 1, widget: 'input-number' }),
        field('humidity', { type: 'number', title: '湿度', unit: '%', minimum: 0, maximum: 100, displayOrder: 2, widget: 'input-number' }),
        field('calibration_point', { type: 'integer', title: '标定点', minimum: 0, maximum: 10, displayOrder: 3, widget: 'input-number' })
      ],
      ['temperature', 'humidity']
    ),
    changelog: '新增标定点字段',
    publishedBy: 1,
    publishedByName: '管理员',
    publishedAt: '2026-10-07T11:30:00',
    deprecatedReason: null,
    deprecatedBy: null,
    deprecatedAt: null,
    createdBy: 1,
    createTime: '2026-09-30T10:00:00',
    updateTime: '2026-10-07T11:30:00'
  },
  {
    id: 21,
    templateId: 2,
    version: '1.0.0',
    versionCode: 10000,
    status: 'PUBLISHED',
    schemaJson: schemaOf(
      [
        field('temperature', { type: 'number', title: '温度', unit: '℃', minimum: -20, maximum: 80, displayOrder: 1, widget: 'input-number' }),
        field('humidity', { type: 'number', title: '湿度', unit: '%', minimum: 0, maximum: 100, displayOrder: 2, widget: 'input-number' })
      ],
      ['temperature', 'humidity']
    ),
    changelog: '初始发布版本',
    publishedBy: 1,
    publishedByName: '管理员',
    publishedAt: '2026-09-29T15:00:00',
    deprecatedReason: null,
    deprecatedBy: null,
    deprecatedAt: null,
    createdBy: 1,
    createTime: '2026-09-28T09:00:00',
    updateTime: '2026-09-29T15:00:00'
  },
  {
    id: 23,
    templateId: 2,
    version: '0.3.0',
    versionCode: null,
    status: 'DRAFT',
    schemaJson: schemaOf(
      [
        field('temperature', { type: 'number', title: '温度', unit: '℃', minimum: -20, maximum: 80, displayOrder: 1, widget: 'input-number' }),
        field('humidity', { type: 'number', title: '湿度', unit: '%', minimum: 0, maximum: 100, displayOrder: 2, widget: 'input-number' }),
        field('calibration_point', { type: 'integer', title: '标定点', minimum: 0, maximum: 10, displayOrder: 3, widget: 'input-number' }),
        field('env_remark', { type: 'string', title: '环境备注', maxLength: 200, displayOrder: 4, widget: 'textarea' })
      ],
      ['temperature', 'humidity']
    ),
    changelog: '补充环境备注字段',
    publishedBy: null,
    publishedByName: null,
    publishedAt: null,
    deprecatedReason: null,
    deprecatedBy: null,
    deprecatedAt: null,
    createdBy: 1,
    createTime: '2026-10-07T11:30:00',
    updateTime: '2026-10-07T11:30:00'
  },
  // 模板 3：仅 DRAFT（非管理者不可见）
  {
    id: 31,
    templateId: 3,
    version: '0.1.0',
    versionCode: null,
    status: 'DRAFT',
    schemaJson: emptySchemaJson(),
    changelog: null,
    publishedBy: null,
    publishedByName: null,
    publishedAt: null,
    deprecatedReason: null,
    deprecatedBy: null,
    deprecatedAt: null,
    createdBy: 1,
    createTime: '2026-10-10T14:00:00',
    updateTime: '2026-10-10T14:00:00'
  },
  // 模板 4：1 个 DEPRECATED + 1 个 PUBLISHED + 1 个 DRAFT
  {
    id: 42,
    templateId: 4,
    version: '1.1.0',
    versionCode: 10100,
    status: 'PUBLISHED',
    schemaJson: schemaOf(
      [
        field('power', { type: 'number', title: '功率', unit: 'W', minimum: 0, maximum: 3000, displayOrder: 1, widget: 'input-number' }),
        field('duration', { type: 'number', title: '持续时间', unit: 's', minimum: 0, maximum: 86400, displayOrder: 2, widget: 'input-number' }),
        field('mode', { type: 'string', title: '采集模式', enum: ['连续', '定时', '触发'], displayOrder: 3, widget: 'select' })
      ],
      ['power']
    ),
    changelog: '新增采集模式字段',
    publishedBy: 2,
    publishedByName: '张同学',
    publishedAt: '2026-10-11T10:00:00',
    deprecatedReason: null,
    deprecatedBy: null,
    deprecatedAt: null,
    createdBy: 2,
    createTime: '2026-10-10T09:00:00',
    updateTime: '2026-10-11T10:00:00'
  },
  {
    id: 41,
    templateId: 4,
    version: '1.0.0',
    versionCode: 10000,
    status: 'DEPRECATED',
    schemaJson: schemaOf(
      [
        field('power', { type: 'number', title: '功率', unit: 'W', minimum: 0, maximum: 3000, displayOrder: 1, widget: 'input-number' }),
        field('duration', { type: 'number', title: '持续时间', unit: 's', minimum: 0, maximum: 86400, displayOrder: 2, widget: 'input-number' })
      ],
      ['power']
    ),
    changelog: '初始发布版本',
    publishedBy: 2,
    publishedByName: '张同学',
    publishedAt: '2026-10-09T16:00:00',
    deprecatedReason: '有新版本替代',
    deprecatedBy: 2,
    deprecatedAt: '2026-10-11T10:00:00',
    createdBy: 2,
    createTime: '2026-10-09T09:00:00',
    updateTime: '2026-10-11T10:00:00'
  },
  {
    id: 43,
    templateId: 4,
    version: '0.3.0',
    versionCode: null,
    status: 'DRAFT',
    schemaJson: schemaOf(
      [
        field('power', { type: 'number', title: '功率', unit: 'W', minimum: 0, maximum: 3000, displayOrder: 1, widget: 'input-number' }),
        field('duration', { type: 'number', title: '持续时间', unit: 's', minimum: 0, maximum: 86400, displayOrder: 2, widget: 'input-number' }),
        field('mode', { type: 'string', title: '采集模式', enum: ['连续', '定时', '触发'], displayOrder: 3, widget: 'select' }),
        field('note', { type: 'string', title: '校准备注', maxLength: 300, displayOrder: 4, widget: 'textarea' })
      ],
      ['power']
    ),
    changelog: '补充校准备注字段',
    publishedBy: null,
    publishedByName: null,
    publishedAt: null,
    deprecatedReason: null,
    deprecatedBy: null,
    deprecatedAt: null,
    createdBy: 2,
    createTime: '2026-10-11T10:00:00',
    updateTime: '2026-10-11T10:00:00'
  },
  // 模板 5：1 个 PUBLISHED
  {
    id: 51,
    templateId: 5,
    version: '1.0.0',
    versionCode: 10000,
    status: 'PUBLISHED',
    schemaJson: schemaOf(
      [
        field('temperature', { type: 'number', title: '温度', unit: '℃', minimum: -40, maximum: 60, displayOrder: 1, widget: 'input-number' }),
        field('humidity', { type: 'number', title: '湿度', unit: '%', minimum: 0, maximum: 100, displayOrder: 2, widget: 'input-number' }),
        field('pressure', { type: 'number', title: '气压', unit: 'hPa', minimum: 300, maximum: 1100, displayOrder: 3, widget: 'input-number' }),
        field('light', { type: 'number', title: '光照', unit: 'lx', minimum: 0, maximum: 200000, displayOrder: 4, widget: 'input-number' })
      ],
      ['temperature', 'humidity', 'pressure']
    ),
    changelog: '初始发布版本',
    publishedBy: 2,
    publishedByName: '张同学',
    publishedAt: '2026-10-05T15:00:00',
    deprecatedReason: null,
    deprecatedBy: null,
    deprecatedAt: null,
    createdBy: 2,
    createTime: '2026-10-02T10:00:00',
    updateTime: '2026-10-05T15:00:00'
  }
]

// ========== Mock 辅助 ==========

function reject(code, msg, reason, data = null) {
  return Promise.reject({ code, msg, reason, data })
}

function nowIso() {
  return new Date().toISOString()
}

function currentUserId() {
  const id = userStore?.userInfo?.id
  return id == null ? MOCK_CURRENT_USER_ID : Number(id)
}

function userName(userId) {
  return MOCK_USER_NAMES[userId] || null
}

function projectNameOf(projectId) {
  return MOCK_PROJECT_NAMES[projectId] || findMockProject(projectId)?.name || null
}

function findMockTemplate(templateId) {
  return MOCK_TEMPLATES.find((t) => t.id === Number(templateId)) || null
}

function findMockTemplateVersion(versionId) {
  const version = MOCK_VERSIONS.find((v) => v.id === Number(versionId)) || null
  if (!version) return null
  const template = findMockTemplate(version.templateId)
  return template ? { template, version } : null
}

function versionsOf(templateId) {
  return MOCK_VERSIONS.filter((v) => v.templateId === Number(templateId))
}

function draftOf(templateId) {
  return versionsOf(templateId).find((v) => v.status === TEMPLATE_VERSION_STATUS.DRAFT) || null
}

/** 模板聚合字段：latestVersion / latestVersionId / publishedVersionCount / hasDraft（契约 §2.1 / §2.4） */
function templateAggregates(template) {
  const versions = versionsOf(template.id)
  const published = versions
    .filter((v) => v.status === TEMPLATE_VERSION_STATUS.PUBLISHED)
    .sort((a, b) => (b.versionCode || 0) - (a.versionCode || 0))
  const latest = published[0] || null
  return {
    hasDraft: versions.some((v) => v.status === TEMPLATE_VERSION_STATUS.DRAFT),
    hasPublished: published.length > 0,
    hasDeprecated: versions.some((v) => v.status === TEMPLATE_VERSION_STATUS.DEPRECATED),
    publishedVersionCount: published.length,
    latestVersion: latest ? latest.version : null,
    latestVersionId: latest ? latest.id : null
  }
}

function toTemplateSummary(template) {
  const agg = templateAggregates(template)
  return {
    id: template.id,
    title: template.title,
    description: template.description || null,
    scope: template.scope,
    projectId: template.projectId ?? null,
    projectName: template.scope === TEMPLATE_SCOPE.PROJECT ? projectNameOf(template.projectId) : null,
    category: template.category || null,
    latestVersion: agg.latestVersion,
    latestVersionId: agg.latestVersionId,
    publishedVersionCount: agg.publishedVersionCount,
    createdBy: template.createdBy,
    createdByName: userName(template.createdBy),
    createTime: template.createTime,
    updateTime: template.updateTime
  }
}

function toTemplateDetail(template) {
  const summary = toTemplateSummary(template)
  const agg = templateAggregates(template)
  const detail = { ...summary }
  // hasDraft 仅对有管理权限的用户返回（契约 §2.4）
  if (canManageTemplate(template)) detail.hasDraft = agg.hasDraft
  return detail
}

function toVersionSummary(version) {
  return {
    id: version.id,
    templateId: version.templateId,
    version: version.version,
    versionCode: version.versionCode ?? null,
    status: version.status,
    fieldCount: countSchemaFields(version.schemaJson),
    changelog: version.changelog ?? null,
    publishedBy: version.publishedBy ?? null,
    publishedByName: version.publishedByName ?? null,
    publishedAt: version.publishedAt ?? null,
    deprecatedReason: version.deprecatedReason ?? null,
    deprecatedBy: version.deprecatedBy ?? null,
    deprecatedAt: version.deprecatedAt ?? null,
    createdBy: version.createdBy,
    createTime: version.createTime,
    updateTime: version.updateTime
  }
}

function toVersionDetail(version) {
  return { ...toVersionSummary(version), schemaJson: version.schemaJson }
}

function toPublishedVersionBrief(version) {
  return {
    id: version.id,
    version: version.version,
    status: version.status,
    fieldCount: countSchemaFields(version.schemaJson),
    changelog: version.changelog ?? null,
    publishedAt: version.publishedAt ?? null
  }
}

/** 模板是否可见（契约 §2.3 / §2.4 / §7） */
function isTemplateVisible(template) {
  if (canManageTemplate(template)) return true
  if (!canAccessTemplates()) return false
  if (template.scope === TEMPLATE_SCOPE.PROJECT && !projectRoleOf(template.projectId)) return false
  return templateAggregates(template).hasPublished
}

/** status 过滤：非管理者仅可见有 PUBLISHED 版本的模板（契约 §2.3） */
function templateMatchesStatus(template, statusFilter) {
  const agg = templateAggregates(template)
  if (canManageTemplate(template)) {
    if (statusFilter === 'ALL') return true
    if (statusFilter === TEMPLATE_VERSION_STATUS.DRAFT) return agg.hasDraft
    if (statusFilter === TEMPLATE_VERSION_STATUS.PUBLISHED) return agg.hasPublished
    if (statusFilter === TEMPLATE_VERSION_STATUS.DEPRECATED) return agg.hasDeprecated
    return true
  }
  if (!agg.hasPublished) return false
  if (statusFilter === 'ALL' || statusFilter === TEMPLATE_VERSION_STATUS.PUBLISHED) return true
  if (statusFilter === TEMPLATE_VERSION_STATUS.DEPRECATED) return agg.hasDeprecated
  return false
}

function requireAccess() {
  if (!canAccessTemplates()) return reject(403, '当前账号无权访问实验模板', 'ACCESS_DENIED')
  return null
}

function requireManage(template) {
  if (!canManageTemplate(template)) return reject(403, '无模板管理权限', 'TEMPLATE_OPERATION_DENIED')
  return null
}

/** 供 learning.js 复用：解析版本关联（含模板主记录） */
export function resolveMockTemplateVersion(versionId) {
  return findMockTemplateVersion(versionId)
}

export function isPublishedTemplateVersion(versionId) {
  const found = findMockTemplateVersion(versionId)
  return Boolean(found) && found.version.status === TEMPLATE_VERSION_STATUS.PUBLISHED
}

// ========== 模板 API ==========

export function createTemplate(data) {
  if (!getMockEnabled()) {
    return request.post('/api/experiment/templates', data)
  }
  const denied = requireAccess()
  if (denied) return denied

  const scope = data?.scope
  if (scope !== TEMPLATE_SCOPE.SYSTEM && scope !== TEMPLATE_SCOPE.PROJECT) {
    return reject(400, '模板范围非法', 'INVALID_PARAMETER')
  }

  if (scope === TEMPLATE_SCOPE.SYSTEM) {
    if (!canManageSystemTemplates()) {
      return reject(403, '无模板创建权限', 'TEMPLATE_OPERATION_DENIED')
    }
    if (data?.projectId != null) {
      return reject(400, '系统模板的 projectId 必须为 null', 'INVALID_PARAMETER')
    }
  } else {
    const projectId = data?.projectId
    if (projectId == null) {
      return reject(400, '项目模板必须指定 projectId', 'INVALID_PARAMETER')
    }
    if (!findMockProject(projectId)) {
      return reject(400, '目标项目不存在', 'INVALID_PARAMETER')
    }
    if (!canManageProjectTemplates(projectId)) {
      return reject(403, '无模板创建权限', 'TEMPLATE_OPERATION_DENIED')
    }
  }

  const title = String(data?.title || '').trim()
  if (title.length < 2 || title.length > 120) {
    return reject(400, '模板标题需为 2-120 字符', 'INVALID_PARAMETER')
  }
  if (data?.description && String(data.description).length > 500) {
    return reject(400, '模板描述最多 500 字符', 'INVALID_PARAMETER')
  }
  if (data?.category && String(data.category).length > 50) {
    return reject(400, '分类标签最多 50 字符', 'INVALID_PARAMETER')
  }

  const now = nowIso()
  const template = {
    id: MOCK_TEMPLATES.reduce((max, t) => Math.max(max, t.id), 0) + 1,
    title,
    description: data?.description || null,
    scope,
    projectId: scope === TEMPLATE_SCOPE.PROJECT ? Number(data.projectId) : null,
    category: data?.category || null,
    createdBy: currentUserId(),
    createTime: now,
    updateTime: now
  }
  MOCK_TEMPLATES.push(template)

  // 创建模板后自动创建一个 DRAFT 版本（version=0.1.0，schemaJson 为空对象）
  MOCK_VERSIONS.push({
    id: MOCK_VERSIONS.reduce((max, v) => Math.max(max, v.id), 0) + 1,
    templateId: template.id,
    version: buildDraftVersion(1),
    versionCode: null,
    status: TEMPLATE_VERSION_STATUS.DRAFT,
    schemaJson: emptySchemaJson(),
    changelog: null,
    publishedBy: null,
    publishedByName: null,
    publishedAt: null,
    deprecatedReason: null,
    deprecatedBy: null,
    deprecatedAt: null,
    createdBy: template.createdBy,
    createTime: now,
    updateTime: now
  })

  return Promise.resolve(toTemplateDetail(template))
}

export function listTemplates(params = {}) {
  if (!getMockEnabled()) {
    return request.get('/api/experiment/templates', { params })
  }
  const denied = requireAccess()
  if (denied) return denied

  const statusFilter = params.status || TEMPLATE_VERSION_STATUS.PUBLISHED
  let list = MOCK_TEMPLATES.filter(
    (t) => isTemplateVisible(t) && templateMatchesStatus(t, statusFilter)
  )

  if (params.scope) list = list.filter((t) => t.scope === params.scope)
  if (params.projectId != null) list = list.filter((t) => t.projectId === Number(params.projectId))
  if (params.category) list = list.filter((t) => t.category === params.category)
  if (params.keyword) {
    const k = String(params.keyword).toLowerCase()
    list = list.filter(
      (t) =>
        t.title.toLowerCase().includes(k) ||
        String(t.description || '').toLowerCase().includes(k)
    )
  }

  list = list.map(toTemplateSummary)

  const sortBy = params.sortBy || 'updateTime'
  const dir = params.sortOrder === 'asc' ? 1 : -1
  list.sort((a, b) => {
    let result
    if (sortBy === 'title') result = a.title.localeCompare(b.title, 'zh-CN')
    else if (sortBy === 'createTime') result = Date.parse(a.createTime) - Date.parse(b.createTime)
    else result = Date.parse(a.updateTime) - Date.parse(b.updateTime)
    if (result === 0) result = a.id - b.id
    return result * dir
  })

  const page = Number(params.page) > 0 ? Number(params.page) : 1
  const pageSize = Number(params.pageSize) > 0 ? Math.min(Number(params.pageSize), 100) : 20
  const start = (page - 1) * pageSize
  return Promise.resolve({
    total: list.length,
    page,
    pageSize,
    list: list.slice(start, start + pageSize)
  })
}

export function getTemplateDetail(templateId) {
  if (!getMockEnabled()) {
    return request.get(`/api/experiment/templates/${templateId}`)
  }
  const denied = requireAccess()
  if (denied) return denied
  const template = findMockTemplate(templateId)
  if (!template || !isTemplateVisible(template)) {
    return reject(404, '模板不存在或不可见', 'TEMPLATE_NOT_FOUND')
  }
  return Promise.resolve(toTemplateDetail(template))
}

export function updateTemplate(templateId, data) {
  if (!getMockEnabled()) {
    return request.put(`/api/experiment/templates/${templateId}`, data)
  }
  const template = findMockTemplate(templateId)
  if (!template) return reject(404, '模板不存在或不可见', 'TEMPLATE_NOT_FOUND')
  const denied = requireManage(template)
  if (denied) return denied

  if (data?.scope !== undefined && data.scope !== template.scope) {
    return reject(409, '模板范围不可修改', 'TEMPLATE_SCOPE_IMMUTABLE')
  }
  if (data?.projectId !== undefined && Number(data.projectId) !== Number(template.projectId)) {
    return reject(409, '模板范围不可修改', 'TEMPLATE_SCOPE_IMMUTABLE')
  }

  if (data?.title != null) {
    const title = String(data.title).trim()
    if (title.length < 2 || title.length > 120) {
      return reject(400, '模板标题需为 2-120 字符', 'INVALID_PARAMETER')
    }
    template.title = title
  }
  if (data?.description !== undefined) template.description = data.description || null
  if (data?.category !== undefined) {
    if (data.category && String(data.category).length > 50) {
      return reject(400, '分类标签最多 50 字符', 'INVALID_PARAMETER')
    }
    template.category = data.category || null
  }
  template.updateTime = nowIso()
  return Promise.resolve(toTemplateDetail(template))
}

export function copyTemplate(templateId, data) {
  if (!getMockEnabled()) {
    return request.post(`/api/experiment/templates/${templateId}/copy`, data)
  }
  const source = findMockTemplate(templateId)
  if (!source) return reject(404, '模板不存在或不可见', 'TEMPLATE_NOT_FOUND')
  if (source.scope !== TEMPLATE_SCOPE.SYSTEM) {
    return reject(400, '只能从系统模板复制', 'TEMPLATE_NOT_SYSTEM')
  }
  const targetProjectId = data?.targetProjectId
  if (targetProjectId == null) {
    return reject(400, '必须指定目标项目', 'INVALID_PARAMETER')
  }
  if (!findMockProject(targetProjectId)) {
    return reject(400, '目标项目不存在', 'INVALID_PARAMETER')
  }
  if (!canManageProjectTemplates(targetProjectId)) {
    return reject(403, '无目标项目的模板管理权限', 'TEMPLATE_OPERATION_DENIED')
  }

  const defaultTitle = `${source.title} - ${projectNameOf(targetProjectId) || '项目'}`
  const title = String(data?.title || defaultTitle).trim()
  if (title.length < 2 || title.length > 120) {
    return reject(400, '模板标题需为 2-120 字符', 'INVALID_PARAMETER')
  }

  const now = nowIso()
  const template = {
    id: MOCK_TEMPLATES.reduce((max, t) => Math.max(max, t.id), 0) + 1,
    title,
    description: data?.description || source.description || null,
    scope: TEMPLATE_SCOPE.PROJECT,
    projectId: Number(targetProjectId),
    category: source.category || null,
    createdBy: currentUserId(),
    createTime: now,
    updateTime: now
  }
  MOCK_TEMPLATES.push(template)

  // DRAFT schemaJson 从源模板最新 PUBLISHED 版本复制（无则空对象）
  const sourceAgg = templateAggregates(source)
  const sourceLatest = sourceAgg.latestVersionId
    ? MOCK_VERSIONS.find((v) => v.id === sourceAgg.latestVersionId)
    : null
  MOCK_VERSIONS.push({
    id: MOCK_VERSIONS.reduce((max, v) => Math.max(max, v.id), 0) + 1,
    templateId: template.id,
    version: buildDraftVersion(1),
    versionCode: null,
    status: TEMPLATE_VERSION_STATUS.DRAFT,
    schemaJson: sourceLatest ? JSON.parse(JSON.stringify(sourceLatest.schemaJson)) : emptySchemaJson(),
    changelog: null,
    publishedBy: null,
    publishedByName: null,
    publishedAt: null,
    deprecatedReason: null,
    deprecatedBy: null,
    deprecatedAt: null,
    createdBy: template.createdBy,
    createTime: now,
    updateTime: now
  })

  return Promise.resolve(toTemplateDetail(template))
}

// ========== 版本 API ==========

export function listVersions(templateId, params = {}) {
  if (!getMockEnabled()) {
    return request.get(`/api/experiment/templates/${templateId}/versions`, { params })
  }
  const denied = requireAccess()
  if (denied) return denied
  const template = findMockTemplate(templateId)
  if (!template || !isTemplateVisible(template)) {
    return reject(404, '模板不存在或不可见', 'TEMPLATE_NOT_FOUND')
  }

  const manage = canManageTemplate(template)
  const statusFilter = params.status || 'ALL'
  let list = versionsOf(template.id).filter((v) => {
    if (!manage && v.status === TEMPLATE_VERSION_STATUS.DRAFT) return false
    if (statusFilter === 'ALL') return true
    return v.status === statusFilter
  })

  // 管理者视图 DRAFT 固定置顶，其余按 versionCode 降序（契约 §3.5）
  list = list.sort((a, b) => {
    if (manage) {
      const aDraft = a.status === TEMPLATE_VERSION_STATUS.DRAFT
      const bDraft = b.status === TEMPLATE_VERSION_STATUS.DRAFT
      if (aDraft !== bDraft) return aDraft ? -1 : 1
    }
    return (b.versionCode || 0) - (a.versionCode || 0) || b.id - a.id
  })

  const summaries = list.map(toVersionSummary)
  const page = Number(params.page) > 0 ? Number(params.page) : 1
  const pageSize = Number(params.pageSize) > 0 ? Math.min(Number(params.pageSize), 100) : 20
  const start = (page - 1) * pageSize
  return Promise.resolve({
    total: summaries.length,
    page,
    pageSize,
    list: summaries.slice(start, start + pageSize)
  })
}

export function getVersionDetail(templateId, versionId) {
  if (!getMockEnabled()) {
    return request.get(`/api/experiment/templates/${templateId}/versions/${versionId}`)
  }
  const denied = requireAccess()
  if (denied) return denied
  const template = findMockTemplate(templateId)
  if (!template || !isTemplateVisible(template)) {
    return reject(404, '模板不存在或不可见', 'TEMPLATE_NOT_FOUND')
  }
  const version = MOCK_VERSIONS.find((v) => v.id === Number(versionId) && v.templateId === template.id)
  if (!version) return reject(404, '模板版本不存在或不可见', 'TEMPLATE_VERSION_NOT_FOUND')
  if (version.status === TEMPLATE_VERSION_STATUS.DRAFT && !canManageTemplate(template)) {
    return reject(404, '模板版本不存在或不可见', 'TEMPLATE_VERSION_NOT_FOUND')
  }
  return Promise.resolve(toVersionDetail(version))
}

export function updateDraftVersion(templateId, versionId, data) {
  if (!getMockEnabled()) {
    return request.put(`/api/experiment/templates/${templateId}/versions/${versionId}`, data)
  }
  const template = findMockTemplate(templateId)
  if (!template) return reject(404, '模板不存在或不可见', 'TEMPLATE_NOT_FOUND')
  const denied = requireManage(template)
  if (denied) return denied
  const version = MOCK_VERSIONS.find((v) => v.id === Number(versionId) && v.templateId === template.id)
  if (!version) return reject(404, '模板版本不存在或不可见', 'TEMPLATE_VERSION_NOT_FOUND')
  if (version.status !== TEMPLATE_VERSION_STATUS.DRAFT) {
    return reject(409, '仅草稿版本可编辑', 'TEMPLATE_VERSION_NOT_EDITABLE')
  }

  if (data?.schemaJson !== undefined) {
    const result = validateSchemaJson(data.schemaJson)
    if (!result.valid) {
      return reject(400, 'Schema 校验失败', 'SCHEMA_INVALID', { errors: result.errors })
    }
    version.schemaJson = data.schemaJson
  }
  if (data?.changelog !== undefined) {
    if (data.changelog && String(data.changelog).length > 500) {
      return reject(400, '变更说明最多 500 字符', 'INVALID_PARAMETER')
    }
    version.changelog = data.changelog || null
  }
  version.updateTime = nowIso()
  template.updateTime = version.updateTime
  return Promise.resolve({
    id: version.id,
    templateId: version.templateId,
    version: version.version,
    versionCode: version.versionCode ?? null,
    status: version.status,
    fieldCount: countSchemaFields(version.schemaJson),
    changelog: version.changelog ?? null,
    updateTime: version.updateTime
  })
}

export function publishVersion(templateId, versionId, payload = {}) {
  if (!getMockEnabled()) {
    return request.put(`/api/experiment/templates/${templateId}/versions/${versionId}/publish`, payload)
  }
  const template = findMockTemplate(templateId)
  if (!template) return reject(404, '模板不存在或不可见', 'TEMPLATE_NOT_FOUND')
  const denied = requireManage(template)
  if (denied) return denied
  const version = MOCK_VERSIONS.find((v) => v.id === Number(versionId) && v.templateId === template.id)
  if (!version) return reject(404, '模板版本不存在或不可见', 'TEMPLATE_VERSION_NOT_FOUND')
  if (version.status !== TEMPLATE_VERSION_STATUS.DRAFT) {
    return reject(409, '仅草稿版本可发布', 'TEMPLATE_VERSION_NOT_PUBLISHABLE')
  }

  const result = validateSchemaJson(version.schemaJson)
  if (!result.valid) {
    return reject(400, 'Schema 校验失败', 'SCHEMA_INVALID', { errors: result.errors })
  }
  if (result.fieldCount < 1) {
    return reject(400, 'Schema 为空，无法发布', 'SCHEMA_EMPTY')
  }

  const agg = templateAggregates(template)
  const publishedVersionNumber = buildPublishedVersion(agg.publishedVersionCount)
  // 发布前的草稿修订序号：发布后自动创建的下一个 DRAFT = 该序号 + 1（契约 §3.2）
  const publishedDraftRevision = draftRevision(version.version) || agg.publishedVersionCount + 1
  const now = nowIso()

  version.version = publishedVersionNumber
  version.versionCode = versionCodeOf(publishedVersionNumber)
  version.status = TEMPLATE_VERSION_STATUS.PUBLISHED
  if (payload?.changelog !== undefined) {
    if (payload.changelog && String(payload.changelog).length > 500) {
      return reject(400, '变更说明最多 500 字符', 'INVALID_PARAMETER')
    }
    version.changelog = payload.changelog || version.changelog
  }
  version.publishedBy = currentUserId()
  version.publishedByName = userName(version.publishedBy)
  version.publishedAt = now
  version.updateTime = now

  // 发布后自动创建下一个 DRAFT（草稿修订序号 +1），schemaJson 从刚发布版本复制
  const newDraft = {
    id: MOCK_VERSIONS.reduce((max, v) => Math.max(max, v.id), 0) + 1,
    templateId: template.id,
    version: buildDraftVersion(publishedDraftRevision + 1),
    versionCode: null,
    status: TEMPLATE_VERSION_STATUS.DRAFT,
    schemaJson: JSON.parse(JSON.stringify(version.schemaJson)),
    changelog: null,
    publishedBy: null,
    publishedByName: null,
    publishedAt: null,
    deprecatedReason: null,
    deprecatedBy: null,
    deprecatedAt: null,
    createdBy: currentUserId(),
    createTime: now,
    updateTime: now
  }
  MOCK_VERSIONS.push(newDraft)

  template.updateTime = now

  return Promise.resolve({
    publishedVersion: {
      id: version.id,
      version: version.version,
      status: version.status,
      publishedBy: version.publishedBy,
      publishedByName: version.publishedByName,
      publishedAt: version.publishedAt
    },
    newDraftVersion: {
      id: newDraft.id,
      version: newDraft.version,
      status: newDraft.status
    }
  })
}

export function deprecateVersion(templateId, versionId, payload = {}) {
  if (!getMockEnabled()) {
    return request.put(`/api/experiment/templates/${templateId}/versions/${versionId}/deprecate`, payload)
  }
  const template = findMockTemplate(templateId)
  if (!template) return reject(404, '模板不存在或不可见', 'TEMPLATE_NOT_FOUND')
  const denied = requireManage(template)
  if (denied) return denied
  const version = MOCK_VERSIONS.find((v) => v.id === Number(versionId) && v.templateId === template.id)
  if (!version) return reject(404, '模板版本不存在或不可见', 'TEMPLATE_VERSION_NOT_FOUND')
  if (version.status !== TEMPLATE_VERSION_STATUS.PUBLISHED) {
    return reject(409, '仅已发布版本可废弃', 'TEMPLATE_VERSION_NOT_DEPRECATABLE')
  }
  if (payload?.reason && String(payload.reason).length > 500) {
    return reject(400, '废弃原因最多 500 字符', 'INVALID_PARAMETER')
  }

  const now = nowIso()
  version.status = TEMPLATE_VERSION_STATUS.DEPRECATED
  version.deprecatedReason = payload?.reason || null
  version.deprecatedBy = currentUserId()
  version.deprecatedAt = now
  version.updateTime = now
  template.updateTime = now

  return Promise.resolve({
    id: version.id,
    version: version.version,
    status: version.status,
    deprecatedReason: version.deprecatedReason,
    deprecatedBy: version.deprecatedBy,
    deprecatedAt: version.deprecatedAt,
    updateTime: version.updateTime
  })
}

export function listPublishedVersions(templateId) {
  if (!getMockEnabled()) {
    return request.get(`/api/experiment/templates/${templateId}/versions/published`)
  }
  const denied = requireAccess()
  if (denied) return denied
  const template = findMockTemplate(templateId)
  if (!template || !isTemplateVisible(template)) {
    return reject(404, '模板不存在或不可见', 'TEMPLATE_NOT_FOUND')
  }
  const list = versionsOf(template.id)
    .filter((v) => v.status === TEMPLATE_VERSION_STATUS.PUBLISHED)
    .sort((a, b) => (b.versionCode || 0) - (a.versionCode || 0))
    .map(toPublishedVersionBrief)
  return Promise.resolve(list)
}

export function validateVersionData(templateId, versionId, data) {
  if (!getMockEnabled()) {
    return request.post(`/api/experiment/templates/${templateId}/versions/${versionId}/validate-data`, data)
  }
  const denied = requireAccess()
  if (denied) return denied
  const template = findMockTemplate(templateId)
  if (!template || !isTemplateVisible(template)) {
    return reject(404, '模板不存在或不可见', 'TEMPLATE_NOT_FOUND')
  }
  const version = MOCK_VERSIONS.find((v) => v.id === Number(versionId) && v.templateId === template.id)
  if (!version) return reject(404, '模板版本不存在或不可见', 'TEMPLATE_VERSION_NOT_FOUND')
  if (version.status === TEMPLATE_VERSION_STATUS.DRAFT && !canManageTemplate(template)) {
    return reject(404, '模板版本不存在或不可见', 'TEMPLATE_VERSION_NOT_FOUND')
  }
  return Promise.resolve(validateDataAgainstSchema(version.schemaJson, data))
}

export { MOCK_CURRENT_USER_ID, MOCK_USER_NAMES, MOCK_PROJECT_NAMES }
