import request from '@/utils/request'
import { getMockEnabled } from '@/utils/mock'
import { userStore, currentRoles } from '@/store/user'
import { ROLE } from '@/utils/permission'
import { idempotencyHeaders, requestDigest } from '@/utils/idempotency'
import { isMockScenario, mockDelay, mockReject, mockTraceId } from '@/utils/mockScenario'

/**
 * 元器件目录与通用媒体 API（第 8 周 M03-W01）
 *
 * 契约来源：docs/contracts/COMPONENT_CATALOG_MEDIA_API.md v1.0.1（FROZEN）
 *
 * 真实端点（契约 §6）：
 *   POST/GET /api/component-categories
 *   PUT      /api/component-categories/{id}
 *   POST     /api/component-categories/{id}/deactivate|restore
 *   POST/GET /api/component-manufacturers
 *   GET/PUT  /api/component-manufacturers/{id}
 *   POST     /api/component-manufacturers/{id}/deactivate|restore
 *   POST/GET /api/components
 *   GET/PUT  /api/components/{id}
 *   POST     /api/components/{id}/activate|deactivate|restore
 *   POST/GET /api/components/{id}/spec-candidates
 *   GET      /api/component-spec-candidates/{candidateId}
 *   POST     /api/component-spec-candidates/{candidateId}/confirm|reject
 *   POST     /api/components/{id}/spec-snapshots/manual
 *   GET      /api/components/{id}/spec-snapshots
 *   GET      /api/component-spec-snapshots/{snapshotId}
 *   POST/GET /api/components/{id}/images
 *   POST     /api/components/{id}/images/{mediaId}/replacement
 *   PUT      /api/components/{id}/images/{mediaId}/primary
 *   PUT      /api/components/{id}/images/order
 *   DELETE   /api/components/{id}/images/{mediaId}
 *   GET      /api/media/{mediaId}
 *   GET      /api/media/{mediaId}/content
 *
 * 边界：只返回 mediaId 与受控元数据；不返回 objectKey、绝对路径、bucket、
 * provider、endpoint 或签名 URL。mock 开关默认关闭。
 */

// ========== 枚举 ==========

export const COMPONENT_STATUS = Object.freeze({
  DRAFT: 'DRAFT',
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE'
})

export const COMPONENT_STATUS_META = Object.freeze({
  [COMPONENT_STATUS.DRAFT]: { label: '草稿', tagType: 'warning', desc: '需正式规格快照后才能激活' },
  [COMPONENT_STATUS.ACTIVE]: { label: '启用', tagType: 'success', desc: '正式目录可见' },
  [COMPONENT_STATUS.INACTIVE]: { label: '停用', tagType: 'info', desc: '只读，历史事实保留' }
})

export const CATALOG_STATUS = Object.freeze({
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE'
})

export const CATALOG_STATUS_META = Object.freeze({
  [CATALOG_STATUS.ACTIVE]: { label: '启用', tagType: 'success' },
  [CATALOG_STATUS.INACTIVE]: { label: '停用', tagType: 'info' }
})

export const BASE_UNIT = Object.freeze({
  PCS: 'PCS',
  METER: 'METER',
  GRAM: 'GRAM',
  MILLILITER: 'MILLILITER',
  SET: 'SET'
})

export const BASE_UNIT_META = Object.freeze({
  [BASE_UNIT.PCS]: { label: '个 (PCS)' },
  [BASE_UNIT.METER]: { label: '米 (METER)' },
  [BASE_UNIT.GRAM]: { label: '克 (GRAM)' },
  [BASE_UNIT.MILLILITER]: { label: '毫升 (MILLILITER)' },
  [BASE_UNIT.SET]: { label: '套 (SET)' }
})

export const VALUE_TYPE = Object.freeze({
  STRING: 'STRING',
  DECIMAL: 'DECIMAL',
  INTEGER: 'INTEGER',
  BOOLEAN: 'BOOLEAN'
})

export const VALUE_TYPE_META = Object.freeze({
  [VALUE_TYPE.STRING]: { label: '文本' },
  [VALUE_TYPE.DECIMAL]: { label: '小数' },
  [VALUE_TYPE.INTEGER]: { label: '整数' },
  [VALUE_TYPE.BOOLEAN]: { label: '布尔' }
})

export const CANDIDATE_STATUS = Object.freeze({
  PENDING: 'PENDING',
  CONFIRMED: 'CONFIRMED',
  REJECTED: 'REJECTED',
  SUPERSEDED: 'SUPERSEDED'
})

export const CANDIDATE_STATUS_META = Object.freeze({
  [CANDIDATE_STATUS.PENDING]: { label: '待确认', tagType: 'warning' },
  [CANDIDATE_STATUS.CONFIRMED]: { label: '已确认', tagType: 'success' },
  [CANDIDATE_STATUS.REJECTED]: { label: '已拒绝', tagType: 'info' },
  [CANDIDATE_STATUS.SUPERSEDED]: { label: '已被替代', tagType: 'info' }
})

export const SOURCE_TYPE = Object.freeze({
  MANUFACTURER_WEBSITE: 'MANUFACTURER_WEBSITE',
  MANUFACTURER_DATASHEET: 'MANUFACTURER_DATASHEET',
  AUTHORIZED_DISTRIBUTOR: 'AUTHORIZED_DISTRIBUTOR',
  OTHER_PUBLIC_URL: 'OTHER_PUBLIC_URL',
  MANUAL: 'MANUAL'
})

export const SOURCE_TYPE_META = Object.freeze({
  [SOURCE_TYPE.MANUFACTURER_WEBSITE]: { label: '制造商网站' },
  [SOURCE_TYPE.MANUFACTURER_DATASHEET]: { label: '制造商数据手册' },
  [SOURCE_TYPE.AUTHORIZED_DISTRIBUTOR]: { label: '授权分销商' },
  [SOURCE_TYPE.OTHER_PUBLIC_URL]: { label: '其他公开来源' },
  [SOURCE_TYPE.MANUAL]: { label: '人工录入' }
})

export const IMAGE_STATE = Object.freeze({
  READY: 'READY',
  PLACEHOLDER: 'PLACEHOLDER'
})

export const MEDIA_ROLE = Object.freeze({
  PRIMARY: 'PRIMARY',
  GALLERY: 'GALLERY'
})

export const MEDIA_STATUS = Object.freeze({
  READY: 'READY',
  DELETED: 'DELETED',
  FAILED: 'FAILED'
})

export const COMPONENT_TRANSITIONS = Object.freeze({
  [COMPONENT_STATUS.DRAFT]: [COMPONENT_STATUS.ACTIVE, COMPONENT_STATUS.INACTIVE],
  [COMPONENT_STATUS.ACTIVE]: [COMPONENT_STATUS.INACTIVE],
  [COMPONENT_STATUS.INACTIVE]: [COMPONENT_STATUS.ACTIVE]
})

export function canTransitionComponent(from, to) {
  return (COMPONENT_TRANSITIONS[from] || []).includes(to)
}

export function componentStatusMeta(status) {
  return COMPONENT_STATUS_META[status] || { label: status || '--', tagType: 'info', desc: '' }
}

export function catalogStatusMeta(status) {
  return CATALOG_STATUS_META[status] || { label: status || '--', tagType: 'info' }
}

export function sourceTypeMeta(type) {
  return SOURCE_TYPE_META[type] || { label: type || '--' }
}

export function candidateStatusMeta(status) {
  return CANDIDATE_STATUS_META[status] || { label: status || '--', tagType: 'info' }
}

export function baseUnitMeta(unit) {
  return BASE_UNIT_META[unit] || { label: unit || '--' }
}

export function valueTypeMeta(type) {
  return VALUE_TYPE_META[type] || { label: type || '--' }
}

// ========== 权限判定（前端 UI 收敛，真正鉴权以后端为准） ==========

const MANAGE_ROLES = [ROLE.SYSTEM_ADMIN, ROLE.STOCK_KEEPER]
const READ_INACTIVE_ROLES = [...MANAGE_ROLES, ROLE.TEACHER]

export function canAccessComponents() {
  return currentRoles().length > 0
}

export function canManageCatalog() {
  return currentRoles().some((role) => MANAGE_ROLES.includes(role))
}

export function canReadInactiveCatalog() {
  return currentRoles().some((role) => READ_INACTIVE_ROLES.includes(role))
}

export function canManageMedia() {
  return canManageCatalog()
}

// ========== Mock 数据 ==========

const NETWORK_STATE_META = {
  loading: { delay: 1200 }
}

const MOCK_CURRENT_USER_ID = 1
const MOCK_USER_NAMES = { 1: '系统管理员', 2: '张同学', 3: '李同学' }

const PNG_FALLBACK =
  'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='

function nowIso() {
  return new Date().toISOString()
}

function currentUserId() {
  const id = userStore?.userInfo?.id
  return id == null ? MOCK_CURRENT_USER_ID : Number(id)
}

function currentUserName() {
  return userStore?.userInfo?.name || userStore?.userInfo?.username || MOCK_USER_NAMES[currentUserId()] || '当前用户'
}

function userName(userId) {
  return MOCK_USER_NAMES[userId] || `用户 ${userId}`
}

function checksumOf(seed) {
  let hash = 0x811c9dc5
  let out = ''
  for (let round = 0; round < 8; round++) {
    for (let i = 0; i < seed.length; i++) {
      hash ^= seed.charCodeAt(i) + round * 131
      hash = Math.imul(hash, 0x01000193) >>> 0
      hash = (hash ^ (hash >>> 13)) >>> 0
    }
    out += hash.toString(16).padStart(8, '0')
  }
  return out
}

function makeMockImage(seed) {
  if (typeof document === 'undefined') return PNG_FALLBACK
  try {
    const canvas = document.createElement('canvas')
    canvas.width = 640
    canvas.height = 480
    const ctx = canvas.getContext('2d')
    if (!ctx) return PNG_FALLBACK
    const palettes = [
      ['#e8f1fa', '#0b6db6'],
      ['#eef7f1', '#1d7a4d'],
      ['#fbf3e6', '#a66100'],
      ['#f4edf7', '#93559c']
    ]
    const palette = palettes[seed.length % palettes.length]
    const grad = ctx.createLinearGradient(0, 0, 640, 480)
    grad.addColorStop(0, palette[0])
    grad.addColorStop(1, palette[1])
    ctx.fillStyle = grad
    ctx.fillRect(0, 0, 640, 480)
    ctx.fillStyle = 'rgba(255,255,255,0.16)'
    ctx.fillRect(48, 48, 544, 384)
    ctx.fillStyle = '#ffffff'
    ctx.font = 'bold 84px "Microsoft YaHei", sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(seed.slice(0, 4).toUpperCase(), 320, 220)
    ctx.font = '24px "Microsoft YaHei", sans-serif'
    ctx.fillText('COMPONENT IMAGE', 320, 310)
    return canvas.toDataURL('image/png')
  } catch {
    return PNG_FALLBACK
  }
}

const MOCK_CATEGORIES = [
  {
    id: 11, code: 'SEMICONDUCTOR', name: '半导体', parentId: null, level: 1, path: '/11',
    status: 'ACTIVE', sortOrder: 10, version: 1, createdBy: 1, createTime: '2026-10-20T09:00:00', updatedBy: 1, updateTime: '2026-10-20T09:00:00'
  },
  {
    id: 21, code: 'AMPLIFIER', name: '放大器', parentId: 11, level: 2, path: '/11/21',
    status: 'ACTIVE', sortOrder: 20, version: 1, createdBy: 1, createTime: '2026-10-20T09:10:00', updatedBy: 1, updateTime: '2026-10-20T09:10:00'
  },
  {
    id: 31, code: 'OPAMP', name: '运算放大器', parentId: 21, level: 3, path: '/11/21/31',
    status: 'ACTIVE', sortOrder: 30, version: 1, createdBy: 1, createTime: '2026-10-20T09:20:00', updatedBy: 1, updateTime: '2026-10-20T09:20:00'
  },
  {
    id: 24, code: 'MCU', name: '微控制器', parentId: 11, level: 2, path: '/11/24',
    status: 'ACTIVE', sortOrder: 24, version: 1, createdBy: 1, createTime: '2026-10-20T09:30:00', updatedBy: 1, updateTime: '2026-10-20T09:30:00'
  },
  {
    id: 12, code: 'PASSIVE', name: '被动器件', parentId: null, level: 1, path: '/12',
    status: 'ACTIVE', sortOrder: 12, version: 1, createdBy: 1, createTime: '2026-10-20T09:40:00', updatedBy: 1, updateTime: '2026-10-20T09:40:00'
  },
  {
    id: 23, code: 'CAPACITOR', name: '电容器', parentId: 12, level: 2, path: '/12/23',
    status: 'ACTIVE', sortOrder: 23, version: 1, createdBy: 1, createTime: '2026-10-20T09:50:00', updatedBy: 1, updateTime: '2026-10-20T09:50:00'
  },
  {
    id: 13, code: 'CONNECTOR', name: '连接器', parentId: null, level: 1, path: '/13',
    status: 'ACTIVE', sortOrder: 13, version: 1, createdBy: 1, createTime: '2026-10-20T10:00:00', updatedBy: 1, updateTime: '2026-10-20T10:00:00'
  },
  {
    id: 14, code: 'OBSOLETE', name: '停用分类示例', parentId: null, level: 1, path: '/14',
    status: 'INACTIVE', sortOrder: 14, version: 2, createdBy: 1, createTime: '2026-10-20T10:10:00', updatedBy: 1, updateTime: '2026-10-20T10:20:00'
  }
]

const MOCK_MANUFACTURERS = [
  {
    id: 21, code: 'TI', name: 'Texas Instruments', website: 'https://www.ti.com',
    status: 'ACTIVE', version: 1, createdBy: 1, createTime: '2026-10-20T09:00:00', updatedBy: 1, updateTime: '2026-10-20T09:00:00'
  },
  {
    id: 22, code: 'ST', name: 'STMicroelectronics', website: 'https://www.st.com',
    status: 'ACTIVE', version: 1, createdBy: 1, createTime: '2026-10-20T09:10:00', updatedBy: 1, updateTime: '2026-10-20T09:10:00'
  },
  {
    id: 23, code: 'MURATA', name: 'Murata', website: 'https://www.murata.com',
    status: 'ACTIVE', version: 1, createdBy: 1, createTime: '2026-10-20T09:20:00', updatedBy: 1, updateTime: '2026-10-20T09:20:00'
  },
  {
    id: 24, code: 'OLDCO', name: '已停用厂商示例', website: 'https://example.com',
    status: 'INACTIVE', version: 2, createdBy: 1, createTime: '2026-10-20T09:30:00', updatedBy: 1, updateTime: '2026-10-20T10:30:00'
  }
]

const MOCK_COMPONENTS = [
  {
    id: 301,
    catalogCode: 'CMP-00000301',
    name: '低功耗零漂移运算放大器',
    manufacturerId: 21,
    mpn: 'OPA333AIDBVR',
    normalizedMpn: 'OPA333AIDBVR',
    categoryId: 31,
    packageType: 'SOT-23-5',
    baseUnit: 'PCS',
    description: '零漂移精密运算放大器，用于实验室低功耗信号采集与前端调理。',
    status: 'ACTIVE',
    currentSpecSnapshotId: 602,
    specVersion: 2,
    primaryMediaId: 801,
    imageState: 'READY',
    placeholderKey: 'component:SEMICONDUCTOR:SOT:O',
    version: 4,
    createdBy: 1,
    createTime: '2026-10-20T10:30:00',
    updatedBy: 1,
    updateTime: '2026-10-23T15:20:00'
  },
  {
    id: 302,
    catalogCode: 'CMP-00000302',
    name: 'ARM Cortex-M3 微控制器',
    manufacturerId: 22,
    mpn: 'STM32F103C8T6',
    normalizedMpn: 'STM32F103C8T6',
    categoryId: 24,
    packageType: 'LQFP-48',
    baseUnit: 'PCS',
    description: '实验室通用控制板主控，支持串口采集、PWM 与基础控制。',
    status: 'ACTIVE',
    currentSpecSnapshotId: 603,
    specVersion: 1,
    primaryMediaId: null,
    imageState: 'PLACEHOLDER',
    placeholderKey: 'component:SEMICONDUCTOR:LQFP:S',
    version: 3,
    createdBy: 1,
    createTime: '2026-10-21T09:00:00',
    updatedBy: 1,
    updateTime: '2026-10-22T16:40:00'
  },
  {
    id: 303,
    catalogCode: 'CMP-00000303',
    name: '低噪声低压差稳压器',
    manufacturerId: 21,
    mpn: 'TPS7A0233PDBVR',
    normalizedMpn: 'TPS7A0233PDBVR',
    categoryId: 11,
    packageType: 'SOT-23-5',
    baseUnit: 'PCS',
    description: '待补充正式规格的草稿型号，用于演示候选确认与手工快照。',
    status: 'DRAFT',
    currentSpecSnapshotId: null,
    specVersion: 0,
    primaryMediaId: null,
    imageState: 'PLACEHOLDER',
    placeholderKey: 'component:SEMICONDUCTOR:SOT:T',
    version: 2,
    createdBy: 1,
    createTime: '2026-10-24T10:00:00',
    updatedBy: 1,
    updateTime: '2026-10-24T11:30:00'
  },
  {
    id: 304,
    catalogCode: 'CMP-00000304',
    name: '0603 陶瓷电容（停用示例）',
    manufacturerId: 23,
    mpn: 'GRM188R71H104KA93D',
    normalizedMpn: 'GRM188R71H104KA93D',
    categoryId: 23,
    packageType: '0603',
    baseUnit: 'PCS',
    description: '已停用的历史型号，保留规格与采购引用。',
    status: 'INACTIVE',
    currentSpecSnapshotId: 604,
    specVersion: 1,
    primaryMediaId: null,
    imageState: 'PLACEHOLDER',
    placeholderKey: 'component:PASSIVE:0603:G',
    version: 2,
    createdBy: 1,
    createTime: '2026-10-20T11:00:00',
    updatedBy: 1,
    updateTime: '2026-10-25T09:00:00'
  }
]

const MOCK_CANDIDATES = [
  {
    id: 501,
    componentId: 303,
    status: 'PENDING',
    sourceType: 'MANUFACTURER_DATASHEET',
    sourceUrl: 'https://www.ti.com/lit/ds/symlink/tps7a02.pdf',
    sourceTitle: 'TPS7A02 datasheet',
    capturedAt: '2026-10-28T10:00:00',
    rawSpecs: [
      { name: 'Input voltage', value: '1.6 V to 5.5 V', unit: 'V' },
      { name: 'Dropout voltage', value: '300 mV', unit: 'mV' }
    ],
    normalizedProposal: [
      { specCode: 'input_voltage_range', label: '输入电压范围', valueType: 'STRING', normalizedValue: '1.6-5.5', unit: 'V', sortOrder: 10 },
      { specCode: 'dropout_voltage_max', label: '最大压差', valueType: 'INTEGER', normalizedValue: 300, unit: 'mV', sortOrder: 20 }
    ],
    submittedBy: 1,
    submittedAt: '2026-10-28T10:10:00',
    decidedBy: null,
    decidedAt: null,
    decisionNote: null,
    version: 1
  },
  {
    id: 502,
    componentId: 301,
    status: 'CONFIRMED',
    sourceType: 'MANUFACTURER_DATASHEET',
    sourceUrl: 'https://www.ti.com/lit/ds/symlink/opa333.pdf',
    sourceTitle: 'OPA333 datasheet',
    capturedAt: '2026-10-20T10:40:00',
    rawSpecs: [
      { name: 'Supply voltage', value: '1.8 V to 5.5 V', unit: 'V' },
      { name: 'Offset voltage', value: '10 uV max', unit: 'uV' }
    ],
    normalizedProposal: [
      { specCode: 'supply_voltage_range', label: '供电电压范围', valueType: 'STRING', normalizedValue: '1.8-5.5', unit: 'V', sortOrder: 10 },
      { specCode: 'offset_voltage_max', label: '最大失调电压', valueType: 'DECIMAL', normalizedValue: 10, unit: 'uV', sortOrder: 20 }
    ],
    submittedBy: 1,
    submittedAt: '2026-10-20T10:45:00',
    decidedBy: 1,
    decidedAt: '2026-10-20T11:00:00',
    decisionNote: '首次确认制造商数据手册规格',
    version: 2
  },
  {
    id: 503,
    componentId: 303,
    status: 'REJECTED',
    sourceType: 'OTHER_PUBLIC_URL',
    sourceUrl: 'https://example.com/unofficial-datasheet',
    sourceTitle: '非官方规格页',
    capturedAt: '2026-10-27T09:30:00',
    rawSpecs: [{ name: 'Output current', value: '500 mA', unit: 'mA' }],
    normalizedProposal: [
      { specCode: 'output_current_max', label: '最大输出电流', valueType: 'INTEGER', normalizedValue: 500, unit: 'mA', sortOrder: 30 }
    ],
    submittedBy: 1,
    submittedAt: '2026-10-27T09:40:00',
    decidedBy: 1,
    decidedAt: '2026-10-27T10:00:00',
    decisionNote: '来源不是制造商或授权分销商，拒绝采用。',
    version: 2
  }
]

const MOCK_SNAPSHOTS = [
  {
    id: 601,
    componentId: 301,
    snapshotVersion: 1,
    sourceType: 'MANUFACTURER_DATASHEET',
    sourceUrl: 'https://www.ti.com/lit/ds/symlink/opa333.pdf',
    sourceTitle: 'OPA333 datasheet',
    capturedAt: '2026-10-20T10:40:00',
    candidateId: 502,
    sourceNote: null,
    rawSpecs: [
      { name: 'Supply voltage', value: '1.8 V to 5.5 V', unit: 'V' },
      { name: 'Offset voltage', value: '10 uV max', unit: 'uV' }
    ],
    specifications: [
      { specCode: 'supply_voltage_range', label: '供电电压范围', valueType: 'STRING', rawValue: '1.8 V to 5.5 V', normalizedValue: '1.8-5.5', unit: 'V', sortOrder: 10 },
      { specCode: 'offset_voltage_max', label: '最大失调电压', valueType: 'DECIMAL', rawValue: '10 uV max', normalizedValue: 10, unit: 'uV', sortOrder: 20 }
    ],
    changeSummary: '首次确认制造商数据手册规格',
    checksum: checksumOf('301-1'),
    confirmedBy: 1,
    confirmedAt: '2026-10-20T11:00:00'
  },
  {
    id: 602,
    componentId: 301,
    snapshotVersion: 2,
    sourceType: 'MANUAL',
    sourceUrl: null,
    sourceTitle: null,
    capturedAt: null,
    candidateId: null,
    sourceNote: '供应商页面暂不可用，依据实验室现有料盘标签和老师确认录入',
    rawSpecs: [{ name: '封装标签', value: 'SOT-23-5', unit: null }],
    specifications: [
      { specCode: 'supply_voltage_range', label: '供电电压范围', valueType: 'STRING', rawValue: '1.8 V to 5.5 V', normalizedValue: '1.8-5.5', unit: 'V', sortOrder: 10 },
      { specCode: 'offset_voltage_max', label: '最大失调电压', valueType: 'DECIMAL', rawValue: '10 uV max', normalizedValue: 10, unit: 'uV', sortOrder: 20 },
      { specCode: 'package_marking', label: '封装标签', valueType: 'STRING', rawValue: 'SOT-23-5', normalizedValue: 'SOT-23-5', unit: null, sortOrder: 30 }
    ],
    changeSummary: '补充线下确认的封装标签',
    checksum: checksumOf('301-2'),
    confirmedBy: 1,
    confirmedAt: '2026-10-23T15:20:00'
  },
  {
    id: 603,
    componentId: 302,
    snapshotVersion: 1,
    sourceType: 'MANUFACTURER_WEBSITE',
    sourceUrl: 'https://www.st.com/en/microcontrollers-microprocessors/stm32f103c8.html',
    sourceTitle: 'STM32F103C8 product page',
    capturedAt: '2026-10-21T09:10:00',
    candidateId: null,
    sourceNote: null,
    rawSpecs: [{ name: 'Core', value: 'ARM Cortex-M3', unit: null }],
    specifications: [
      { specCode: 'core', label: '内核', valueType: 'STRING', rawValue: 'ARM Cortex-M3', normalizedValue: 'ARM Cortex-M3', unit: null, sortOrder: 10 },
      { specCode: 'flash_size', label: 'Flash 容量', valueType: 'INTEGER', rawValue: '64 KB', normalizedValue: 64, unit: 'KB', sortOrder: 20 }
    ],
    changeSummary: '首次录入核心规格',
    checksum: checksumOf('302-1'),
    confirmedBy: 1,
    confirmedAt: '2026-10-21T09:30:00'
  },
  {
    id: 604,
    componentId: 304,
    snapshotVersion: 1,
    sourceType: 'MANUAL',
    sourceUrl: null,
    sourceTitle: null,
    capturedAt: null,
    candidateId: null,
    sourceNote: '依据历史料盘标签录入，型号已停用但保留事实。',
    rawSpecs: [{ name: '容值', value: '100 nF', unit: 'nF' }],
    specifications: [
      { specCode: 'capacitance', label: '容值', valueType: 'INTEGER', rawValue: '100 nF', normalizedValue: 100, unit: 'nF', sortOrder: 10 }
    ],
    changeSummary: '历史型号补录',
    checksum: checksumOf('304-1'),
    confirmedBy: 1,
    confirmedAt: '2026-10-20T11:10:00'
  }
]

const MOCK_MEDIA = [
  {
    mediaId: 801,
    ownerType: 'COMPONENT',
    ownerId: 301,
    role: 'PRIMARY',
    status: 'READY',
    originalFileName: 'opa333.png',
    contentType: 'image/png',
    size: 48213,
    width: 1200,
    height: 900,
    sha256: checksumOf('media-801'),
    contentPath: '/api/media/801/content',
    sortOrder: 10,
    version: 1,
    uploadedBy: 1,
    uploadedAt: '2026-10-21T10:00:00',
    deletedBy: null,
    deletedAt: null,
    _dataUrl: makeMockImage('OPA333AIDBVR')
  },
  {
    mediaId: 802,
    ownerType: 'COMPONENT',
    ownerId: 301,
    role: 'GALLERY',
    status: 'READY',
    originalFileName: 'opa333-package.png',
    contentType: 'image/png',
    size: 36520,
    width: 1200,
    height: 900,
    sha256: checksumOf('media-802'),
    contentPath: '/api/media/802/content',
    sortOrder: 20,
    version: 1,
    uploadedBy: 1,
    uploadedAt: '2026-10-22T09:00:00',
    deletedBy: null,
    deletedAt: null,
    _dataUrl: makeMockImage('SOT-23-5')
  }
]

// ========== Mock 辅助 ==========

function normalizeMpn(value) {
  return String(value || '').normalize('NFKC').trim().replace(/\s+/g, ' ').toUpperCase()
}

function firstAlphaNumeric(value) {
  const match = String(value || '').toUpperCase().match(/[A-Z0-9]/)
  return match ? match[0] : '?'
}

function packageBucket(packageType) {
  return String(packageType || 'UNKNOWN').trim().split(/[-_\s]/)[0].toUpperCase().slice(0, 12) || 'UNKNOWN'
}

function buildPlaceholderKey(component) {
  const category = findCategory(component.categoryId)
  let top = category
  while (top && top.parentId) {
    const parent = findCategory(top.parentId)
    if (!parent) break
    top = parent
  }
  const topCode = top?.code || 'COMPONENT'
  return `component:${topCode}:${packageBucket(component.packageType)}:${firstAlphaNumeric(component.normalizedMpn || component.mpn)}`
}

function listAllCategories() {
  return MOCK_CATEGORIES
}

function findCategory(id) {
  return MOCK_CATEGORIES.find((c) => c.id === Number(id)) || null
}

function findManufacturer(id) {
  return MOCK_MANUFACTURERS.find((m) => m.id === Number(id)) || null
}

function findComponent(id) {
  return MOCK_COMPONENTS.find((c) => c.id === Number(id)) || null
}

function findCandidate(id) {
  return MOCK_CANDIDATES.find((c) => c.id === Number(id)) || null
}

function findSnapshot(id) {
  return MOCK_SNAPSHOTS.find((s) => s.id === Number(id)) || null
}

function findMedia(id) {
  return MOCK_MEDIA.find((m) => m.mediaId === Number(id)) || null
}

function categoryPath(categoryId) {
  const path = []
  let current = findCategory(categoryId)
  while (current) {
    path.unshift({ id: current.id, code: current.code, name: current.name })
    current = current.parentId ? findCategory(current.parentId) : null
  }
  return path
}

function descendantIds(categoryId) {
  const ids = new Set([Number(categoryId)])
  let changed = true
  while (changed) {
    changed = false
    for (const category of MOCK_CATEGORIES) {
      if (category.parentId && ids.has(Number(category.parentId)) && !ids.has(category.id)) {
        ids.add(category.id)
        changed = true
      }
    }
  }
  return ids
}

function decorateComponent(component) {
  const manufacturer = findManufacturer(component.manufacturerId)
  return {
    ...component,
    manufacturerName: manufacturer?.name || null,
    categoryPath: categoryPath(component.categoryId),
    normalizedMpn: normalizeMpn(component.mpn),
    placeholderKey: component.placeholderKey || buildPlaceholderKey(component),
    imageState: component.primaryMediaId ? 'READY' : 'PLACEHOLDER'
  }
}

function publicMedia(media) {
  if (!media) return null
  return {
    mediaId: media.mediaId,
    ownerType: media.ownerType,
    ownerId: media.ownerId,
    role: media.role,
    status: media.status,
    originalFileName: media.originalFileName,
    contentType: media.contentType,
    size: media.size,
    width: media.width,
    height: media.height,
    sha256: media.sha256,
    contentPath: media.contentPath,
    sortOrder: media.sortOrder,
    version: media.version,
    uploadedBy: media.uploadedBy,
    uploadedAt: media.uploadedAt,
    deletedBy: media.deletedBy,
    deletedAt: media.deletedAt
  }
}

function snapshotDetail(snapshot) {
  if (!snapshot) return null
  return {
    ...snapshot,
    confirmedByName: userName(snapshot.confirmedBy)
  }
}

function candidateDetail(candidate) {
  if (!candidate) return null
  return {
    ...candidate,
    submittedByName: userName(candidate.submittedBy),
    decidedByName: candidate.decidedBy ? userName(candidate.decidedBy) : null
  }
}

function visibleMediaList(componentId) {
  return MOCK_MEDIA.filter((m) => m.ownerId === Number(componentId) && m.status === 'READY')
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder || a.mediaId - b.mediaId)
}

function imagesPayload(component) {
  const images = visibleMediaList(component.id).map(publicMedia)
  return {
    componentId: component.id,
    componentVersion: component.version,
    imageState: images.length ? 'READY' : 'PLACEHOLDER',
    primaryMediaId: component.primaryMediaId || null,
    images
  }
}

function reject(code, msg, reason, data = null) {
  return mockReject(code, msg, reason, data)
}

function errorWithTrace(msg, reason = 'INTERNAL_ERROR') {
  return mockReject(500, msg, reason, { traceId: mockTraceId('w44') })
}

function requireManager(reason = 'COMPONENT_OPERATION_DENIED') {
  if (!canManageCatalog()) return reject(403, '无元器件目录维护权限', reason)
  return null
}

function requireVersion(resource, version, extra = {}) {
  if (version == null) return reject(400, '缺少 version 参数', 'INVALID_PARAMETER')
  if (Number(version) !== Number(resource.version)) {
    return reject(409, '数据已被更新，请刷新后重试', 'VERSION_CONFLICT', {
      currentVersion: resource.version,
      ...extra
    })
  }
  return null
}

const MOCK_IDEMPOTENCY = new Map()

function withIdempotency(key, payload, handler) {
  if (!key) return reject(400, '缺少 Idempotency-Key', 'INVALID_PARAMETER')
  const digest = requestDigest(payload)
  const hit = MOCK_IDEMPOTENCY.get(key)
  if (hit) {
    if (hit.digest !== digest) return reject(409, '幂等键已用于其他请求', 'IDEMPOTENCY_KEY_REUSED')
    return Promise.resolve({ ...hit.result, idempotentReplay: true })
  }
  return Promise.resolve()
    .then(handler)
    .then((result) => {
      MOCK_IDEMPOTENCY.set(key, { digest, result })
      return result
    })
}

function nextId(list, key) {
  return Math.max(...list.map((item) => item[key]), 0) + 1
}

function scenarioGate(kind) {
  if (isMockScenario('permission')) {
    return reject(403, '无元器件目录访问权限', 'COMPONENT_OPERATION_DENIED')
  }
  if (isMockScenario('error')) {
    return errorWithTrace(`加载${kind}失败`)
  }
  return null
}

// ========== 请求校验 ==========

function validateCategoryPayload(payload, { creating = true, parentId = null } = {}) {
  if (creating) {
    const code = String(payload?.code || '').trim().toUpperCase()
    if (!/^[A-Z0-9_]{2,40}$/.test(code)) {
      return { error: reject(400, '分类编码需为 2-40 位大写字母、数字或下划线', 'INVALID_PARAMETER') }
    }
  }
  const name = String(payload?.name || '').trim()
  if (name.length < 2 || name.length > 80) {
    return { error: reject(400, '分类名称需为 2-80 字符', 'INVALID_PARAMETER') }
  }
  const sortOrder = Number(payload?.sortOrder ?? 0)
  if (!Number.isInteger(sortOrder) || sortOrder < 0 || sortOrder > 9999) {
    return { error: reject(400, '排序值需为 0-9999 的整数', 'INVALID_PARAMETER') }
  }
  const resolvedParentId = parentId ?? (payload?.parentId != null ? Number(payload.parentId) : null)
  if (resolvedParentId != null) {
    const parent = findCategory(resolvedParentId)
    if (!parent) return { error: reject(404, '父分类不存在或不可见', 'COMPONENT_CATEGORY_NOT_FOUND') }
    if (parent.status !== 'ACTIVE') {
      return { error: reject(409, '父分类已停用，禁止新增子分类', 'CATEGORY_IN_USE') }
    }
    if (parent.level >= 3) {
      return { error: reject(409, '分类最多三级', 'CATEGORY_DEPTH_EXCEEDED') }
    }
  }
  return { name, sortOrder, parentId: resolvedParentId, code: payload?.code ? String(payload.code).trim().toUpperCase() : undefined }
}

function validateManufacturerPayload(payload, { creating = true } = {}) {
  if (creating) {
    const code = String(payload?.code || '').trim().toUpperCase()
    if (!/^[A-Z0-9_]{2,40}$/.test(code)) {
      return { error: reject(400, '厂商编码需为 2-40 位大写字母、数字或下划线', 'INVALID_PARAMETER') }
    }
  }
  const name = String(payload?.name || '').trim()
  if (name.length < 2 || name.length > 120) {
    return { error: reject(400, '厂商名称需为 2-120 字符', 'INVALID_PARAMETER') }
  }
  const website = payload?.website ? String(payload.website).trim() : ''
  if (website && (!/^https?:\/\//i.test(website) || website.length > 2048)) {
    return { error: reject(400, '官网仅支持 http/https 且最多 2048 字符', 'INVALID_PARAMETER') }
  }
  return { name, website: website || null, code: payload?.code ? String(payload.code).trim().toUpperCase() : undefined }
}

function validateSpecRow(row, index) {
  const specCode = String(row?.specCode || '').trim()
  if (!/^[a-z][a-z0-9_]{1,49}$/.test(specCode)) {
    return `第 ${index + 1} 项规格编码需为 [a-z][a-z0-9_]{1,49}`
  }
  const label = String(row?.label || '').trim()
  if (label.length < 2 || label.length > 80) {
    return `第 ${index + 1} 项规格显示名需为 2-80 字符`
  }
  if (!Object.values(VALUE_TYPE).includes(row?.valueType)) {
    return `第 ${index + 1} 项规格值类型非法`
  }
  if (row?.unit && String(row.unit).length > 20) {
    return `第 ${index + 1} 项单位最多 20 字符`
  }
  const sortOrder = Number(row?.sortOrder ?? index * 10)
  if (!Number.isInteger(sortOrder) || sortOrder < 0 || sortOrder > 9999) {
    return `第 ${index + 1} 项排序值需为 0-9999 的整数`
  }
  if (row?.normalizedValue === undefined || row?.normalizedValue === null || String(row.normalizedValue).trim() === '') {
    return `第 ${index + 1} 项缺少标准化值`
  }
  return ''
}

function validateSpecs(specs) {
  if (!Array.isArray(specs) || specs.length < 1 || specs.length > 50) {
    return { error: reject(400, '正式规格需为 1-50 项', 'COMPONENT_SPEC_INVALID') }
  }
  const codes = new Set()
  for (let i = 0; i < specs.length; i++) {
    const message = validateSpecRow(specs[i], i)
    if (message) return { error: reject(400, message, 'COMPONENT_SPEC_INVALID') }
    if (codes.has(specs[i].specCode)) {
      return { error: reject(400, `规格编码重复：${specs[i].specCode}`, 'COMPONENT_SPEC_INVALID') }
    }
    codes.add(specs[i].specCode)
  }
  return { specs: specs.map((row, index) => ({
    specCode: String(row.specCode).trim(),
    label: String(row.label).trim(),
    valueType: row.valueType,
    rawValue: row.rawValue != null ? String(row.rawValue) : null,
    normalizedValue: row.normalizedValue,
    unit: row.unit ? String(row.unit).trim() : null,
    sortOrder: Number(row.sortOrder ?? index * 10)
  })) }
}

function validateRawSpecs(rawSpecs, reason = 'SOURCE_METADATA_INVALID') {
  if (!Array.isArray(rawSpecs) || rawSpecs.length < 1 || rawSpecs.length > 50) {
    return { error: reject(400, '原始规格需为 1-50 项', reason) }
  }
  for (let i = 0; i < rawSpecs.length; i++) {
    const row = rawSpecs[i]
    if (!String(row?.name || '').trim() || row?.value == null) {
      return { error: reject(400, `第 ${i + 1} 项原始规格缺少名称或值`, reason) }
    }
    if (String(row.name).length > 200 || String(row.value).length > 500 || (row.unit && String(row.unit).length > 20)) {
      return { error: reject(400, `第 ${i + 1} 项原始规格超长`, reason) }
    }
  }
  return { rawSpecs: rawSpecs.map((row) => ({
    name: String(row.name).trim(),
    value: String(row.value),
    unit: row.unit ? String(row.unit).trim() : null
  })) }
}

function validateComponentPayload(payload, componentId = null) {
  const name = String(payload?.name || '').trim()
  if (name.length < 2 || name.length > 120) {
    return { error: reject(400, '型号名称需为 2-120 字符', 'INVALID_PARAMETER') }
  }
  const manufacturerId = Number(payload?.manufacturerId)
  const manufacturer = findManufacturer(manufacturerId)
  if (!manufacturer || manufacturer.status !== 'ACTIVE') {
    return { error: reject(409, '必须引用启用状态的制造商', 'MANUFACTURER_IN_USE') }
  }
  const categoryId = Number(payload?.categoryId)
  const category = findCategory(categoryId)
  if (!category || category.status !== 'ACTIVE') {
    return { error: reject(409, '必须引用启用状态的分类', 'CATEGORY_IN_USE') }
  }
  const mpn = String(payload?.mpn || '').trim()
  if (mpn.length < 1 || mpn.length > 120) {
    return { error: reject(400, 'MPN 需为 1-120 字符', 'INVALID_PARAMETER') }
  }
  const normalizedMpn = normalizeMpn(mpn)
  const duplicate = MOCK_COMPONENTS.find((c) => c.manufacturerId === manufacturerId
    && c.normalizedMpn === normalizedMpn
    && c.id !== Number(componentId))
  if (duplicate) {
    return { error: reject(409, '同一制造商下的型号已存在', 'COMPONENT_DUPLICATE_MODEL', {
      existingComponentId: duplicate.id,
      currentStatus: duplicate.status
    }) }
  }
  const packageType = String(payload?.packageType || '').trim()
  if (packageType.length < 1 || packageType.length > 80) {
    return { error: reject(400, '封装需为 1-80 字符', 'INVALID_PARAMETER') }
  }
  if (!Object.values(BASE_UNIT).includes(payload?.baseUnit)) {
    return { error: reject(400, '基础单位不在冻结枚举内', 'INVALID_PARAMETER') }
  }
  const description = payload?.description ? String(payload.description) : ''
  if (description.length > 2000) {
    return { error: reject(400, '描述最多 2000 字符', 'INVALID_PARAMETER') }
  }
  return {
    data: {
      name,
      manufacturerId,
      mpn,
      normalizedMpn,
      categoryId,
      packageType,
      baseUnit: payload.baseUnit,
      description: description || null
    }
  }
}

function nextSnapshot(component, base, { sourceNote, rawSpecs, specifications, changeSummary, candidateId, sourceType, sourceUrl, sourceTitle, capturedAt }) {
  const snapshotVersion = Math.max(...MOCK_SNAPSHOTS.filter((s) => s.componentId === component.id).map((s) => s.snapshotVersion), 0) + 1
  const snapshot = {
    id: nextId(MOCK_SNAPSHOTS, 'id'),
    componentId: component.id,
    snapshotVersion,
    sourceType,
    sourceUrl: sourceUrl || null,
    sourceTitle: sourceTitle || null,
    capturedAt: capturedAt || null,
    candidateId: candidateId || null,
    sourceNote: sourceNote || null,
    rawSpecs,
    specifications,
    changeSummary,
    checksum: checksumOf(`${component.id}-${snapshotVersion}-${JSON.stringify(specifications)}`),
    confirmedBy: currentUserId(),
    confirmedAt: nowIso()
  }
  MOCK_SNAPSHOTS.push(snapshot)
  component.currentSpecSnapshotId = snapshot.id
  component.specVersion = snapshotVersion
  component.version += 1
  component.updatedBy = currentUserId()
  component.updateTime = nowIso()
  return snapshot
}

// ========== Mock：分类与制造商 ==========

function listCategoryNodes() {
  const nodes = listAllCategories()
    .filter((c) => canReadInactiveCatalog() || c.status === 'ACTIVE')
    .map((c) => ({ ...c, children: [] }))
    .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
  const map = new Map(nodes.map((node) => [node.id, node]))
  const roots = []
  for (const node of nodes) {
    if (node.parentId && map.has(node.parentId)) map.get(node.parentId).children.push(node)
    else roots.push(node)
  }
  return roots
}

// ========== API：分类 ==========

export function listCategories(params = {}) {
  if (!getMockEnabled()) {
    return request.get('/api/component-categories', { params })
  }
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    const gate = scenarioGate('分类')
    if (gate) return gate
    if (!canAccessComponents()) return reject(403, 'GUEST 不可访问元器件目录', 'COMPONENT_OPERATION_DENIED')
    return listCategoryNodes()
  })()
}

export function createCategory(data, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post('/api/component-categories', data, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(idempotencyKey, data, async () => {
    const managerError = requireManager()
    if (managerError) return managerError
    const check = validateCategoryPayload(data, { creating: true })
    if (check.error) return check.error
    if (MOCK_CATEGORIES.some((c) => c.code === check.code)) {
      return reject(409, '分类编码已存在', 'CATEGORY_DUPLICATE_CODE')
    }
    if (MOCK_CATEGORIES.some((c) => c.parentId === check.parentId && String(c.name).toLowerCase() === check.name.toLowerCase())) {
      return reject(409, '同一父分类下分类名称已存在', 'CATEGORY_DUPLICATE_CODE')
    }
    const parent = check.parentId ? findCategory(check.parentId) : null
    const id = nextId(MOCK_CATEGORIES, 'id')
    const now = nowIso()
    const category = {
      id,
      code: check.code,
      name: check.name,
      parentId: check.parentId,
      level: parent ? parent.level + 1 : 1,
      path: parent ? `${parent.path}/${id}` : `/${id}`,
      status: 'ACTIVE',
      sortOrder: check.sortOrder,
      version: 1,
      createdBy: currentUserId(),
      createTime: now,
      updatedBy: currentUserId(),
      updateTime: now
    }
    MOCK_CATEGORIES.push(category)
    return { ...category }
  })
}

export function updateCategory(categoryId, data) {
  if (!getMockEnabled()) {
    return request.put(`/api/component-categories/${categoryId}`, data)
  }
  const category = findCategory(categoryId)
  if (!category) return reject(404, '分类不存在或不可见', 'COMPONENT_CATEGORY_NOT_FOUND')
  const managerError = requireManager()
  if (managerError) return managerError
  if (category.status !== 'ACTIVE') return reject(409, '停用分类不可编辑', 'CATEGORY_IN_USE')
  const versionError = requireVersion(category, data?.version)
  if (versionError) return versionError
  const check = validateCategoryPayload({ ...category, name: data?.name ?? category.name, sortOrder: data?.sortOrder ?? category.sortOrder }, { creating: false, parentId: category.parentId })
  if (check.error) return check.error
  if (MOCK_CATEGORIES.some((c) => c.id !== category.id && c.parentId === category.parentId && String(c.name).toLowerCase() === check.name.toLowerCase())) {
    return reject(409, '同一父分类下分类名称已存在', 'CATEGORY_DUPLICATE_CODE')
  }
  category.name = check.name
  category.sortOrder = check.sortOrder
  category.version += 1
  category.updatedBy = currentUserId()
  category.updateTime = nowIso()
  return { ...category }
}

export function deactivateCategory(categoryId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/component-categories/${categoryId}/deactivate`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(idempotencyKey, { categoryId, ...payload }, async () => {
    const category = findCategory(categoryId)
    if (!category) return reject(404, '分类不存在或不可见', 'COMPONENT_CATEGORY_NOT_FOUND')
    const managerError = requireManager()
    if (managerError) return managerError
    const versionError = requireVersion(category, payload?.version, { categoryId })
    if (versionError) return versionError
    const ids = descendantIds(category.id)
    if (MOCK_CATEGORIES.some((c) => ids.has(c.id) && c.id !== category.id && c.status === 'ACTIVE')) {
      return reject(409, '存在启用的子分类，不能停用', 'CATEGORY_IN_USE')
    }
    if (MOCK_COMPONENTS.some((c) => ids.has(c.categoryId) && ['ACTIVE', 'DRAFT'].includes(c.status))) {
      return reject(409, '分类下存在启用或草稿型号，不能停用', 'CATEGORY_IN_USE')
    }
    category.status = 'INACTIVE'
    category.version += 1
    category.updatedBy = currentUserId()
    category.updateTime = nowIso()
    return { id: category.id, status: category.status, version: category.version }
  })
}

export function restoreCategory(categoryId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/component-categories/${categoryId}/restore`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(idempotencyKey, { categoryId, ...payload }, async () => {
    const category = findCategory(categoryId)
    if (!category) return reject(404, '分类不存在或不可见', 'COMPONENT_CATEGORY_NOT_FOUND')
    const managerError = requireManager()
    if (managerError) return managerError
    const versionError = requireVersion(category, payload?.version, { categoryId })
    if (versionError) return versionError
    if (category.parentId) {
      const parent = findCategory(category.parentId)
      if (!parent || parent.status !== 'ACTIVE') {
        return reject(409, '父分类已停用，不能恢复', 'CATEGORY_IN_USE')
      }
    }
    category.status = 'ACTIVE'
    category.version += 1
    category.updatedBy = currentUserId()
    category.updateTime = nowIso()
    return { id: category.id, status: category.status, version: category.version }
  })
}

// ========== API：制造商 ==========

export function listManufacturers(params = {}) {
  if (!getMockEnabled()) {
    return request.get('/api/component-manufacturers', { params })
  }
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    if (isMockScenario('empty')) return { total: 0, page: 1, pageSize: 20, list: [] }
    const gate = scenarioGate('制造商')
    if (gate) return gate
    if (!canAccessComponents()) return reject(403, 'GUEST 不可访问元器件目录', 'COMPONENT_OPERATION_DENIED')
    let list = MOCK_MANUFACTURERS.filter((m) => canReadInactiveCatalog() || m.status === 'ACTIVE').slice()
    if (params.status) list = list.filter((m) => m.status === params.status)
    if (params.keyword) {
      const keyword = String(params.keyword).toLowerCase()
      list = list.filter((m) => m.code.toLowerCase().includes(keyword) || m.name.toLowerCase().includes(keyword))
    }
    const sortBy = ['updateTime', 'createTime', 'mpn', 'name', 'manufacturerName'].includes(params.sortBy) ? params.sortBy : 'updateTime'
    const direction = params.sortOrder === 'asc' ? 1 : -1
    list.sort((a, b) => {
      if (sortBy === 'name') return a.name.localeCompare(b.name) * direction || (a.id - b.id) * direction
      if (sortBy === 'createTime') return (Date.parse(a.createTime) - Date.parse(b.createTime)) * direction || (a.id - b.id) * direction
      return (Date.parse(a.updateTime) - Date.parse(b.updateTime)) * direction || (b.id - a.id)
    })
    const page = Number(params.page) > 0 ? Number(params.page) : 1
    const pageSize = Number(params.pageSize) > 0 ? Math.min(Number(params.pageSize), 100) : 20
    const start = (page - 1) * pageSize
    return {
      total: list.length,
      page,
      pageSize,
      list: list.slice(start, start + pageSize).map((m) => ({ ...m }))
    }
  })()
}

export function createManufacturer(data, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post('/api/component-manufacturers', data, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(idempotencyKey, data, async () => {
    const managerError = requireManager()
    if (managerError) return managerError
    const check = validateManufacturerPayload(data, { creating: true })
    if (check.error) return check.error
    if (MOCK_MANUFACTURERS.some((m) => m.code === check.code)) {
      return reject(409, '厂商编码已存在', 'MANUFACTURER_DUPLICATE_CODE')
    }
    if (MOCK_MANUFACTURERS.some((m) => m.name.toLowerCase() === check.name.toLowerCase())) {
      return reject(409, '厂商名称已存在', 'MANUFACTURER_DUPLICATE_CODE')
    }
    const id = nextId(MOCK_MANUFACTURERS, 'id')
    const now = nowIso()
    const manufacturer = {
      id,
      code: check.code,
      name: check.name,
      website: check.website,
      status: 'ACTIVE',
      version: 1,
      createdBy: currentUserId(),
      createTime: now,
      updatedBy: currentUserId(),
      updateTime: now
    }
    MOCK_MANUFACTURERS.push(manufacturer)
    return { ...manufacturer }
  })
}

export function getManufacturer(manufacturerId) {
  if (!getMockEnabled()) {
    return request.get(`/api/component-manufacturers/${manufacturerId}`)
  }
  const manufacturer = findManufacturer(manufacturerId)
  if (!manufacturer) return reject(404, '制造商不存在或不可见', 'COMPONENT_MANUFACTURER_NOT_FOUND')
  if (!canReadInactiveCatalog() && manufacturer.status !== 'ACTIVE') {
    return reject(404, '制造商不存在或不可见', 'COMPONENT_MANUFACTURER_NOT_FOUND')
  }
  return Promise.resolve({ ...manufacturer })
}

export function updateManufacturer(manufacturerId, data) {
  if (!getMockEnabled()) {
    return request.put(`/api/component-manufacturers/${manufacturerId}`, data)
  }
  const manufacturer = findManufacturer(manufacturerId)
  if (!manufacturer) return reject(404, '制造商不存在或不可见', 'COMPONENT_MANUFACTURER_NOT_FOUND')
  const managerError = requireManager()
  if (managerError) return managerError
  if (manufacturer.status !== 'ACTIVE') return reject(409, '停用制造商不可编辑', 'MANUFACTURER_IN_USE')
  const versionError = requireVersion(manufacturer, data?.version, { manufacturerId })
  if (versionError) return versionError
  const check = validateManufacturerPayload({ ...manufacturer, name: data?.name ?? manufacturer.name, website: data?.website ?? manufacturer.website }, { creating: false })
  if (check.error) return check.error
  if (MOCK_MANUFACTURERS.some((m) => m.id !== manufacturer.id && m.name.toLowerCase() === check.name.toLowerCase())) {
    return reject(409, '厂商名称已存在', 'MANUFACTURER_DUPLICATE_CODE')
  }
  manufacturer.name = check.name
  manufacturer.website = check.website
  manufacturer.version += 1
  manufacturer.updatedBy = currentUserId()
  manufacturer.updateTime = nowIso()
  return { ...manufacturer }
}

export function deactivateManufacturer(manufacturerId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/component-manufacturers/${manufacturerId}/deactivate`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(idempotencyKey, { manufacturerId, ...payload }, async () => {
    const manufacturer = findManufacturer(manufacturerId)
    if (!manufacturer) return reject(404, '制造商不存在或不可见', 'COMPONENT_MANUFACTURER_NOT_FOUND')
    const managerError = requireManager()
    if (managerError) return managerError
    const versionError = requireVersion(manufacturer, payload?.version, { manufacturerId })
    if (versionError) return versionError
    if (MOCK_COMPONENTS.some((c) => c.manufacturerId === manufacturer.id && ['ACTIVE', 'DRAFT'].includes(c.status))) {
      return reject(409, '制造商下存在启用或草稿型号，不能停用', 'MANUFACTURER_IN_USE')
    }
    manufacturer.status = 'INACTIVE'
    manufacturer.version += 1
    manufacturer.updatedBy = currentUserId()
    manufacturer.updateTime = nowIso()
    return { id: manufacturer.id, status: manufacturer.status, version: manufacturer.version }
  })
}

export function restoreManufacturer(manufacturerId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/component-manufacturers/${manufacturerId}/restore`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(idempotencyKey, { manufacturerId, ...payload }, async () => {
    const manufacturer = findManufacturer(manufacturerId)
    if (!manufacturer) return reject(404, '制造商不存在或不可见', 'COMPONENT_MANUFACTURER_NOT_FOUND')
    const managerError = requireManager()
    if (managerError) return managerError
    const versionError = requireVersion(manufacturer, payload?.version, { manufacturerId })
    if (versionError) return versionError
    manufacturer.status = 'ACTIVE'
    manufacturer.version += 1
    manufacturer.updatedBy = currentUserId()
    manufacturer.updateTime = nowIso()
    return { id: manufacturer.id, status: manufacturer.status, version: manufacturer.version }
  })
}

// ========== API：元器件 ==========

export function listComponents(params = {}) {
  if (!getMockEnabled()) {
    return request.get('/api/components', { params })
  }
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    if (!canAccessComponents()) return reject(403, 'GUEST 不可访问元器件目录', 'COMPONENT_OPERATION_DENIED')
    if (isMockScenario('error')) return errorWithTrace('加载元器件目录失败')
    if (isMockScenario('empty')) return { total: 0, page: 1, pageSize: 20, list: [] }
    const page = Number(params.page) > 0 ? Number(params.page) : 1
    const pageSize = Number(params.pageSize) > 0 ? Math.min(Number(params.pageSize), 100) : 20
    if (Number(params.pageSize) > 100) return reject(400, 'pageSize 最大 100', 'INVALID_PARAMETER')

    let list = MOCK_COMPONENTS.slice()
    if (!canReadInactiveCatalog()) {
      list = list.filter((c) => c.status === 'ACTIVE')
    } else if (params.status) {
      list = list.filter((c) => c.status === params.status)
    }
    if (params.categoryId) {
      const ids = params.includeDescendants === false
        ? new Set([Number(params.categoryId)])
        : descendantIds(params.categoryId)
      list = list.filter((c) => ids.has(c.categoryId))
    }
    if (params.manufacturerId) list = list.filter((c) => c.manufacturerId === Number(params.manufacturerId))
    if (params.packageType) list = list.filter((c) => c.packageType === params.packageType)
    if (params.baseUnit) list = list.filter((c) => c.baseUnit === params.baseUnit)
    if (params.keyword) {
      const keyword = String(params.keyword).toLowerCase()
      list = list.filter((c) => {
        const manufacturer = findManufacturer(c.manufacturerId)
        return c.mpn.toLowerCase().includes(keyword)
          || c.name.toLowerCase().includes(keyword)
          || (manufacturer?.name || '').toLowerCase().includes(keyword)
      })
    }
    const sortBy = ['updateTime', 'createTime', 'mpn', 'name', 'manufacturerName'].includes(params.sortBy) ? params.sortBy : 'updateTime'
    if (params.sortBy && !['updateTime', 'createTime', 'mpn', 'name', 'manufacturerName'].includes(params.sortBy)) {
      return reject(400, '排序字段不在白名单内', 'INVALID_PARAMETER')
    }
    if (params.sortOrder && !['asc', 'desc'].includes(params.sortOrder)) {
      return reject(400, '排序方向只支持 asc 或 desc', 'INVALID_PARAMETER')
    }
    const direction = params.sortOrder === 'asc' ? 1 : -1
    list.sort((a, b) => {
      if (sortBy === 'mpn') return a.mpn.localeCompare(b.mpn) * direction || (a.id - b.id) * direction
      if (sortBy === 'name') return a.name.localeCompare(b.name) * direction || (a.id - b.id) * direction
      if (sortBy === 'manufacturerName') return (findManufacturer(a.manufacturerId)?.name || '').localeCompare(findManufacturer(b.manufacturerId)?.name || '') * direction || (a.id - b.id) * direction
      if (sortBy === 'createTime') return (Date.parse(a.createTime) - Date.parse(b.createTime)) * direction || (a.id - b.id) * direction
      return (Date.parse(a.updateTime) - Date.parse(b.updateTime)) * direction || (b.id - a.id) * direction
    })
    const start = (page - 1) * pageSize
    return {
      total: list.length,
      page,
      pageSize,
      list: list.slice(start, start + pageSize).map(decorateComponent)
    }
  })()
}

export function createComponent(data, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post('/api/components', data, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(idempotencyKey, data, async () => {
    const managerError = requireManager()
    if (managerError) return managerError
    const check = validateComponentPayload(data)
    if (check.error) return check.error
    const id = nextId(MOCK_COMPONENTS, 'id')
    const now = nowIso()
    const component = {
      id,
      catalogCode: `CMP-${String(id).padStart(8, '0')}`,
      ...check.data,
      status: 'DRAFT',
      currentSpecSnapshotId: null,
      specVersion: 0,
      primaryMediaId: null,
      imageState: 'PLACEHOLDER',
      placeholderKey: '',
      version: 1,
      createdBy: currentUserId(),
      createTime: now,
      updatedBy: currentUserId(),
      updateTime: now
    }
    component.placeholderKey = buildPlaceholderKey(component)
    MOCK_COMPONENTS.push(component)
    return decorateComponent(component)
  })
}

export function getComponent(componentId) {
  if (!getMockEnabled()) {
    return request.get(`/api/components/${componentId}`)
  }
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    const component = findComponent(componentId)
    if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
    if (!canAccessComponents()) return reject(403, 'GUEST 不可访问元器件目录', 'COMPONENT_OPERATION_DENIED')
    if (!canReadInactiveCatalog() && component.status !== 'ACTIVE') {
      return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
    }
    if (isMockScenario('error')) return errorWithTrace('加载元器件详情失败')
    return decorateComponent(component)
  })()
}

export function updateComponent(componentId, data) {
  if (!getMockEnabled()) {
    return request.put(`/api/components/${componentId}`, data)
  }
  const component = findComponent(componentId)
  if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
  const managerError = requireManager()
  if (managerError) return managerError
  if (component.status === 'INACTIVE') return reject(409, '停用型号只能查看或恢复', 'COMPONENT_INVALID_TRANSITION')
  const versionError = requireVersion(component, data?.version, { componentId })
  if (versionError) return versionError
  const check = validateComponentPayload({
    name: data?.name ?? component.name,
    manufacturerId: data?.manufacturerId ?? component.manufacturerId,
    categoryId: data?.categoryId ?? component.categoryId,
    mpn: data?.mpn ?? component.mpn,
    packageType: data?.packageType ?? component.packageType,
    baseUnit: data?.baseUnit ?? component.baseUnit,
    description: data?.description ?? component.description
  }, component.id)
  if (check.error) return check.error
  Object.assign(component, check.data)
  component.placeholderKey = buildPlaceholderKey(component)
  component.version += 1
  component.updatedBy = currentUserId()
  component.updateTime = nowIso()
  return decorateComponent(component)
}

function transitionComponent(componentId, payload, idempotencyKey, targetStatus) {
  return withIdempotency(idempotencyKey, { componentId, ...payload }, async () => {
    const component = findComponent(componentId)
    if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
    const managerError = requireManager()
    if (managerError) return managerError
    const versionError = requireVersion(component, payload?.version, { componentId })
    if (versionError) return versionError
    if (!canTransitionComponent(component.status, targetStatus)) {
      return reject(409, '非法元器件状态转换', 'COMPONENT_INVALID_TRANSITION', {
        currentStatus: component.status
      })
    }
    if (targetStatus === 'ACTIVE') {
      if (!component.currentSpecSnapshotId) {
        return reject(409, '草稿型号至少需要一份正式规格快照后才能激活', 'COMPONENT_SPEC_REQUIRED')
      }
      const manufacturer = findManufacturer(component.manufacturerId)
      const category = findCategory(component.categoryId)
      if (!manufacturer || manufacturer.status !== 'ACTIVE' || !category || category.status !== 'ACTIVE') {
        return reject(409, '分类或制造商未启用，不能激活型号', 'COMPONENT_INVALID_TRANSITION')
      }
    }
    if (targetStatus === 'INACTIVE') {
      const reason = String(payload?.reason || '').trim()
      if (reason.length < 1 || reason.length > 500) {
        return reject(400, '停用原因需为 1-500 字符', 'INVALID_PARAMETER')
      }
    }
    if (component.status === 'INACTIVE' && targetStatus === 'ACTIVE') {
      const reason = String(payload?.reason || '').trim()
      if (reason.length < 1 || reason.length > 500) {
        return reject(400, '恢复原因需为 1-500 字符', 'INVALID_PARAMETER')
      }
    }
    if (isMockScenario('conflict')) {
      return reject(409, '数据已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: component.version + 1 })
    }
    component.status = targetStatus
    component.version += 1
    component.updatedBy = currentUserId()
    component.updateTime = nowIso()
    return {
      id: component.id,
      status: component.status,
      version: component.version,
      updateTime: component.updateTime
    }
  })
}

export function activateComponent(componentId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/components/${componentId}/activate`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return transitionComponent(componentId, payload, idempotencyKey, 'ACTIVE')
}

export function deactivateComponent(componentId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/components/${componentId}/deactivate`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return transitionComponent(componentId, payload, idempotencyKey, 'INACTIVE')
}

export function restoreComponent(componentId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/components/${componentId}/restore`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return transitionComponent(componentId, payload, idempotencyKey, 'ACTIVE')
}

// ========== API：规格候选与快照 ==========

export function listSpecCandidates(componentId, params = {}) {
  if (!getMockEnabled()) {
    return request.get(`/api/components/${componentId}/spec-candidates`, { params })
  }
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    if (isMockScenario('empty')) return { total: 0, page: 1, pageSize: 20, list: [] }
    if (isMockScenario('error')) return errorWithTrace('加载规格候选失败')
    if (!canManageCatalog() && !currentRoles().includes(ROLE.TEACHER)) {
      return reject(403, '无规格候选查看权限', 'SPEC_CONFIRM_DENIED')
    }
    const component = findComponent(componentId)
    if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
    let list = MOCK_CANDIDATES.filter((c) => c.componentId === component.id).map(candidateDetail)
    if (params.status) list = list.filter((c) => c.status === params.status)
    list.sort((a, b) => Date.parse(b.submittedAt) - Date.parse(a.submittedAt) || b.id - a.id)
    const page = Number(params.page) > 0 ? Number(params.page) : 1
    const pageSize = Number(params.pageSize) > 0 ? Math.min(Number(params.pageSize), 100) : 20
    const start = (page - 1) * pageSize
    return { total: list.length, page, pageSize, list: list.slice(start, start + pageSize) }
  })()
}

export function getSpecCandidate(candidateId) {
  if (!getMockEnabled()) {
    return request.get(`/api/component-spec-candidates/${candidateId}`)
  }
  if (!canManageCatalog() && !currentRoles().includes(ROLE.TEACHER)) {
    return reject(403, '无规格候选查看权限', 'SPEC_CONFIRM_DENIED')
  }
  const candidate = findCandidate(candidateId)
  if (!candidate) return reject(404, '规格候选不存在或不可见', 'COMPONENT_SPEC_CANDIDATE_NOT_FOUND')
  return Promise.resolve(candidateDetail(candidate))
}

export function createSpecCandidate(componentId, data, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/components/${componentId}/spec-candidates`, data, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(idempotencyKey, { componentId, ...data }, async () => {
    const managerError = requireManager()
    if (managerError) return managerError
    const component = findComponent(componentId)
    if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
    if (component.status === 'INACTIVE') return reject(409, '停用型号不能登记候选', 'COMPONENT_INVALID_TRANSITION')
    const versionError = requireVersion(component, data?.componentVersion, { componentId })
    if (versionError) return versionError
    if (!Object.values(SOURCE_TYPE).includes(data?.sourceType)) {
      return reject(400, '来源类型非法', 'SOURCE_METADATA_INVALID')
    }
    const sourceUrl = data?.sourceUrl ? String(data.sourceUrl).trim() : null
    const sourceTitle = data?.sourceTitle ? String(data.sourceTitle).trim() : null
    const capturedAt = data?.capturedAt ? String(data.capturedAt) : null
    if (data.sourceType !== 'MANUAL') {
      if (!sourceUrl || !/^https?:\/\//i.test(sourceUrl) || sourceUrl.length > 2048) {
        return reject(400, '非 MANUAL 候选必须提供 http/https 来源 URL', 'SOURCE_METADATA_INVALID')
      }
      if (!sourceTitle || sourceTitle.length > 200) {
        return reject(400, '非 MANUAL 候选必须提供 1-200 字符来源标题', 'SOURCE_METADATA_INVALID')
      }
      if (!capturedAt || Number.isNaN(Date.parse(capturedAt)) || Date.parse(capturedAt) > Date.now()) {
        return reject(400, '获取时间必填且不得晚于当前时间', 'SOURCE_METADATA_INVALID')
      }
    }
    const raw = validateRawSpecs(data?.rawSpecs)
    if (raw.error) return raw.error
    const normalized = validateSpecs(data?.normalizedProposal)
    if (normalized.error) return normalized.error
    const now = nowIso()
    const candidate = {
      id: nextId(MOCK_CANDIDATES, 'id'),
      componentId: component.id,
      status: 'PENDING',
      sourceType: data.sourceType,
      sourceUrl,
      sourceTitle,
      capturedAt: capturedAt || null,
      rawSpecs: raw.rawSpecs,
      normalizedProposal: normalized.specs,
      submittedBy: currentUserId(),
      submittedAt: now,
      decidedBy: null,
      decidedAt: null,
      decisionNote: null,
      version: 1
    }
    MOCK_CANDIDATES.push(candidate)
    return candidateDetail(candidate)
  })
}

export function confirmSpecCandidate(candidateId, data, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/component-spec-candidates/${candidateId}/confirm`, data, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(idempotencyKey, { candidateId, ...data }, async () => {
    const managerError = requireManager('SPEC_CONFIRM_DENIED')
    if (managerError) return managerError
    const candidate = findCandidate(candidateId)
    if (!candidate) return reject(404, '规格候选不存在或不可见', 'COMPONENT_SPEC_CANDIDATE_NOT_FOUND')
    if (candidate.status !== 'PENDING') {
      return reject(409, '候选已处理，不能再次确认', 'SPEC_CANDIDATE_ALREADY_DECIDED')
    }
    const candidateVersionError = requireVersion(candidate, data?.candidateVersion, { candidateId })
    if (candidateVersionError) return candidateVersionError
    const component = findComponent(candidate.componentId)
    if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
    const componentVersionError = requireVersion(component, data?.componentVersion, { componentId: component.id })
    if (componentVersionError) return componentVersionError
    if (isMockScenario('conflict')) {
      return reject(409, '数据已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: component.version + 1 })
    }
    const specs = validateSpecs(data?.specifications)
    if (specs.error) return specs.error
    const changeSummary = String(data?.changeSummary || '').trim()
    if (changeSummary.length < 1 || changeSummary.length > 500) {
      return reject(400, '变更说明需为 1-500 字符', 'INVALID_PARAMETER')
    }
    const snapshot = nextSnapshot(component, candidate, {
      sourceNote: candidate.sourceType === 'MANUAL' ? '人工录入候选' : null,
      rawSpecs: candidate.rawSpecs,
      specifications: specs.specs,
      changeSummary,
      candidateId: candidate.id,
      sourceType: candidate.sourceType,
      sourceUrl: candidate.sourceUrl,
      sourceTitle: candidate.sourceTitle,
      capturedAt: candidate.capturedAt
    })
    candidate.status = 'CONFIRMED'
    candidate.decidedBy = currentUserId()
    candidate.decidedAt = nowIso()
    candidate.decisionNote = changeSummary
    candidate.version += 1
    return {
      candidateId: candidate.id,
      candidateStatus: candidate.status,
      snapshotId: snapshot.id,
      snapshotVersion: snapshot.snapshotVersion,
      componentId: component.id,
      componentVersion: component.version,
      specVersion: component.specVersion
    }
  })
}

export function rejectSpecCandidate(candidateId, data, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/component-spec-candidates/${candidateId}/reject`, data, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(idempotencyKey, { candidateId, ...data }, async () => {
    const managerError = requireManager('SPEC_CONFIRM_DENIED')
    if (managerError) return managerError
    const candidate = findCandidate(candidateId)
    if (!candidate) return reject(404, '规格候选不存在或不可见', 'COMPONENT_SPEC_CANDIDATE_NOT_FOUND')
    if (candidate.status !== 'PENDING') {
      return reject(409, '候选已处理，不能再次拒绝', 'SPEC_CANDIDATE_ALREADY_DECIDED')
    }
    const candidateVersionError = requireVersion(candidate, data?.candidateVersion, { candidateId })
    if (candidateVersionError) return candidateVersionError
    const note = String(data?.decisionNote || '').trim()
    if (note.length > 1000) return reject(400, '处理说明最多 1000 字符', 'INVALID_PARAMETER')
    candidate.status = 'REJECTED'
    candidate.decidedBy = currentUserId()
    candidate.decidedAt = nowIso()
    candidate.decisionNote = note || null
    candidate.version += 1
    return { candidateId: candidate.id, candidateStatus: candidate.status, candidateVersion: candidate.version }
  })
}

export function createManualSpecSnapshot(componentId, data, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/components/${componentId}/spec-snapshots/manual`, data, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(idempotencyKey, { componentId, ...data }, async () => {
    const managerError = requireManager('SPEC_CONFIRM_DENIED')
    if (managerError) return managerError
    const component = findComponent(componentId)
    if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
    if (component.status === 'INACTIVE') return reject(409, '停用型号不能生成快照', 'COMPONENT_INVALID_TRANSITION')
    const versionError = requireVersion(component, data?.componentVersion, { componentId })
    if (versionError) return versionError
    if (data?.sourceType !== 'MANUAL') return reject(400, '手工快照来源类型必须为 MANUAL', 'SOURCE_METADATA_INVALID')
    if (data?.sourceUrl) return reject(400, 'MANUAL 快照不允许 sourceUrl', 'SOURCE_METADATA_INVALID')
    const sourceNote = String(data?.sourceNote || '').trim()
    if (sourceNote.length < 1 || sourceNote.length > 1000) {
      return reject(400, '手工快照必须提供 1-1000 字符来源说明', 'SOURCE_METADATA_INVALID')
    }
    const raw = validateRawSpecs(data?.rawSpecs)
    if (raw.error) return raw.error
    const specs = validateSpecs(data?.specifications)
    if (specs.error) return specs.error
    const changeSummary = String(data?.changeSummary || '').trim()
    if (changeSummary.length < 1 || changeSummary.length > 500) {
      return reject(400, '变更说明需为 1-500 字符', 'INVALID_PARAMETER')
    }
    const snapshot = nextSnapshot(component, null, {
      sourceNote,
      rawSpecs: raw.rawSpecs,
      specifications: specs.specs,
      changeSummary,
      candidateId: null,
      sourceType: 'MANUAL',
      sourceUrl: null,
      sourceTitle: null,
      capturedAt: null
    })
    return {
      snapshotId: snapshot.id,
      snapshotVersion: snapshot.snapshotVersion,
      componentId: component.id,
      componentVersion: component.version,
      specVersion: component.specVersion
    }
  })
}

export function listSpecSnapshots(componentId, params = {}) {
  if (!getMockEnabled()) {
    return request.get(`/api/components/${componentId}/spec-snapshots`, { params })
  }
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    if (isMockScenario('empty')) return { total: 0, page: 1, pageSize: 20, list: [] }
    if (isMockScenario('error')) return errorWithTrace('加载规格历史失败')
    const component = findComponent(componentId)
    if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
    if (!canAccessComponents()) return reject(403, 'GUEST 不可访问元器件目录', 'COMPONENT_OPERATION_DENIED')
    if (!canReadInactiveCatalog()) {
      if (component.status !== 'ACTIVE') return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
      const current = component.currentSpecSnapshotId ? findSnapshot(component.currentSpecSnapshotId) : null
      return { total: current ? 1 : 0, page: 1, pageSize: Number(params.pageSize) || 20, list: current ? [snapshotDetail(current)] : [] }
    }
    let list = MOCK_SNAPSHOTS.filter((s) => s.componentId === component.id).slice()
    list.sort((a, b) => b.snapshotVersion - a.snapshotVersion || b.id - a.id)
    const page = Number(params.page) > 0 ? Number(params.page) : 1
    const pageSize = Number(params.pageSize) > 0 ? Math.min(Number(params.pageSize), 100) : 20
    const start = (page - 1) * pageSize
    return {
      total: list.length,
      page,
      pageSize,
      list: list.slice(start, start + pageSize).map(snapshotDetail)
    }
  })()
}

export function getSpecSnapshot(snapshotId) {
  if (!getMockEnabled()) {
    return request.get(`/api/component-spec-snapshots/${snapshotId}`)
  }
  const snapshot = findSnapshot(snapshotId)
  if (!snapshot) return reject(404, '规格快照不存在或不可见', 'COMPONENT_SPEC_SNAPSHOT_NOT_FOUND')
  const component = findComponent(snapshot.componentId)
  if (!canReadInactiveCatalog() && (!component || component.status !== 'ACTIVE' || component.currentSpecSnapshotId !== snapshot.id)) {
    return reject(404, '规格快照不存在或不可见', 'COMPONENT_SPEC_SNAPSHOT_NOT_FOUND')
  }
  return Promise.resolve(snapshotDetail(snapshot))
}

// ========== API：图片与媒体 ==========

export function listComponentImages(componentId) {
  if (!getMockEnabled()) {
    return request.get(`/api/components/${componentId}/images`)
  }
  return (async () => {
    if (isMockScenario('loading')) await mockDelay()
    if (isMockScenario('empty')) {
      const component = findComponent(componentId)
      return {
        componentId: Number(componentId),
        componentVersion: component?.version ?? 0,
        imageState: 'PLACEHOLDER',
        primaryMediaId: null,
        images: []
      }
    }
    if (isMockScenario('error')) return errorWithTrace('加载图片失败')
    const component = findComponent(componentId)
    if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
    if (!canReadInactiveCatalog() && component.status !== 'ACTIVE') {
      return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
    }
    return imagesPayload(component)
  })()
}

function validateImageFile(file) {
  if (!file) return reject(400, '请选择图片文件', 'INVALID_PARAMETER')
  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    return reject(400, '仅支持 JPEG/PNG 图片', 'MEDIA_TYPE_UNSUPPORTED')
  }
  if (Number(file.size) > 5 * 1024 * 1024) {
    return reject(400, '图片不能超过 5 MiB', 'MEDIA_TOO_LARGE')
  }
  return null
}

function addMockMedia(component, file, sortOrder, role) {
  const mediaId = nextId(MOCK_MEDIA, 'mediaId')
  const dataUrl = (() => {
    if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
      try { return URL.createObjectURL(file) } catch { /* ignore */ }
    }
    return makeMockImage(String(file?.name || mediaId))
  })()
  const media = {
    mediaId,
    ownerType: 'COMPONENT',
    ownerId: component.id,
    role,
    status: 'READY',
    originalFileName: String(file?.name || `image-${mediaId}.png`),
    contentType: file?.type || 'image/png',
    size: Number(file?.size || 1),
    width: 1200,
    height: 900,
    sha256: checksumOf(`${file?.name || mediaId}-${file?.size || 1}-${mediaId}`),
    contentPath: `/api/media/${mediaId}/content`,
    sortOrder,
    version: 1,
    uploadedBy: currentUserId(),
    uploadedAt: nowIso(),
    deletedBy: null,
    deletedAt: null,
    _dataUrl: dataUrl
  }
  MOCK_MEDIA.push(media)
  return media
}

export function uploadComponentImage(componentId, { file, componentVersion, sortOrder = 10 }, idempotencyKey) {
  if (!getMockEnabled()) {
    const form = new FormData()
    form.append('file', file)
    form.append('componentVersion', String(componentVersion))
    form.append('sortOrder', String(sortOrder))
    return request.post(`/api/components/${componentId}/images`, form, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(idempotencyKey, { componentId, componentVersion, sortOrder, name: file?.name, size: file?.size, type: file?.type }, async () => {
    const managerError = requireManager('MEDIA_OPERATION_DENIED')
    if (managerError) return managerError
    const component = findComponent(componentId)
    if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
    if (component.status === 'INACTIVE') return reject(409, '停用型号不能上传图片', 'COMPONENT_INVALID_TRANSITION')
    const versionError = requireVersion(component, componentVersion, { componentId })
    if (versionError) return versionError
    const fileError = validateImageFile(file)
    if (fileError) return fileError
    if (visibleMediaList(component.id).length >= 5) {
      return reject(409, '每个型号最多 5 张 READY 图片', 'MEDIA_LIMIT_EXCEEDED')
    }
    const role = component.primaryMediaId ? 'GALLERY' : 'PRIMARY'
    const media = addMockMedia(component, file, Number(sortOrder) || 10, role)
    if (!component.primaryMediaId) component.primaryMediaId = media.mediaId
    component.imageState = 'READY'
    component.version += 1
    component.updatedBy = currentUserId()
    component.updateTime = nowIso()
    return {
      componentId: component.id,
      componentVersion: component.version,
      imageState: 'READY',
      primaryMediaId: component.primaryMediaId,
      media: publicMedia(media)
    }
  })
}

export function replaceComponentImage(componentId, mediaId, { file, componentVersion, sortOrder = 10 }, idempotencyKey) {
  if (!getMockEnabled()) {
    const form = new FormData()
    form.append('file', file)
    form.append('componentVersion', String(componentVersion))
    form.append('sortOrder', String(sortOrder))
    return request.post(`/api/components/${componentId}/images/${mediaId}/replacement`, form, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(idempotencyKey, { componentId, mediaId, componentVersion, name: file?.name, size: file?.size, type: file?.type }, async () => {
    const managerError = requireManager('MEDIA_OPERATION_DENIED')
    if (managerError) return managerError
    const component = findComponent(componentId)
    if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
    const old = findMedia(mediaId)
    if (!old || old.ownerId !== component.id || old.status !== 'READY') {
      return reject(404, '媒体不存在或不可见', 'MEDIA_NOT_FOUND')
    }
    if (component.status === 'INACTIVE') return reject(409, '停用型号不能替换图片', 'COMPONENT_INVALID_TRANSITION')
    const versionError = requireVersion(component, componentVersion, { componentId })
    if (versionError) return versionError
    const fileError = validateImageFile(file)
    if (fileError) return fileError
    const wasPrimary = component.primaryMediaId === old.mediaId
    old.status = 'DELETED'
    old.deletedBy = currentUserId()
    old.deletedAt = nowIso()
    old.version += 1
    const media = addMockMedia(component, file, Number(sortOrder) || old.sortOrder, wasPrimary ? 'PRIMARY' : 'GALLERY')
    if (wasPrimary) component.primaryMediaId = media.mediaId
    component.version += 1
    component.updatedBy = currentUserId()
    component.updateTime = nowIso()
    return {
      componentId: component.id,
      componentVersion: component.version,
      imageState: component.primaryMediaId ? 'READY' : 'PLACEHOLDER',
      primaryMediaId: component.primaryMediaId || null,
      media: publicMedia(media),
      images: imagesPayload(component).images
    }
  })
}

export function setPrimaryComponentImage(componentId, mediaId, payload) {
  if (!getMockEnabled()) {
    return request.put(`/api/components/${componentId}/images/${mediaId}/primary`, payload)
  }
  const managerError = requireManager('MEDIA_OPERATION_DENIED')
  if (managerError) return managerError
  const component = findComponent(componentId)
  if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
  const media = findMedia(mediaId)
  if (!media || media.ownerId !== component.id || media.status !== 'READY') {
    return reject(404, '媒体不存在或不可见', 'MEDIA_NOT_FOUND')
  }
  const componentVersionError = requireVersion(component, payload?.componentVersion, { componentId })
  if (componentVersionError) return componentVersionError
  const mediaVersionError = requireVersion(media, payload?.mediaVersion, { mediaId })
  if (mediaVersionError) return mediaVersionError
  for (const item of visibleMediaList(component.id)) item.role = item.mediaId === media.mediaId ? 'PRIMARY' : 'GALLERY'
  component.primaryMediaId = media.mediaId
  component.version += 1
  component.updatedBy = currentUserId()
  component.updateTime = nowIso()
  return imagesPayload(component)
}

export function reorderComponentImages(componentId, payload) {
  if (!getMockEnabled()) {
    return request.put(`/api/components/${componentId}/images/order`, payload)
  }
  const managerError = requireManager('MEDIA_OPERATION_DENIED')
  if (managerError) return managerError
  const component = findComponent(componentId)
  if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
  const versionError = requireVersion(component, payload?.componentVersion, { componentId })
  if (versionError) return versionError
  const items = Array.isArray(payload?.items) ? payload.items : []
  if (!items.length || items.length > 5) return reject(400, '排序项需为 1-5 项', 'INVALID_PARAMETER')
  for (const item of items) {
    const media = findMedia(item.mediaId)
    if (!media || media.ownerId !== component.id || media.status !== 'READY') {
      return reject(404, '媒体不存在或不可见', 'MEDIA_NOT_FOUND')
    }
    if (Number(item.version) !== Number(media.version)) {
      return reject(409, '数据已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: media.version, mediaId: media.mediaId })
    }
  }
  for (const item of items) {
    const media = findMedia(item.mediaId)
    media.sortOrder = Number(item.sortOrder)
    media.version += 1
  }
  component.version += 1
  component.updatedBy = currentUserId()
  component.updateTime = nowIso()
  return imagesPayload(component)
}

export function deleteComponentImage(componentId, mediaId, payload) {
  if (!getMockEnabled()) {
    return request.delete(`/api/components/${componentId}/images/${mediaId}`, { data: payload })
  }
  const managerError = requireManager('MEDIA_OPERATION_DENIED')
  if (managerError) return managerError
  const component = findComponent(componentId)
  if (!component) return reject(404, '元器件型号不存在或不可见', 'COMPONENT_NOT_FOUND')
  const media = findMedia(mediaId)
  if (!media || media.ownerId !== component.id || media.status !== 'READY') {
    return reject(404, '媒体不存在或不可见', 'MEDIA_NOT_FOUND')
  }
  const componentVersionError = requireVersion(component, payload?.componentVersion, { componentId })
  if (componentVersionError) return componentVersionError
  const mediaVersionError = requireVersion(media, payload?.mediaVersion, { mediaId })
  if (mediaVersionError) return mediaVersionError
  const reason = String(payload?.reason || '').trim()
  if (reason.length < 1 || reason.length > 500) {
    return reject(400, '删除原因需为 1-500 字符', 'INVALID_PARAMETER')
  }
  media.status = 'DELETED'
  media.deletedBy = currentUserId()
  media.deletedAt = nowIso()
  media.version += 1
  if (component.primaryMediaId === media.mediaId) {
    const remaining = visibleMediaList(component.id)
    const next = remaining.slice().sort((a, b) => a.sortOrder - b.sortOrder)[0]
    component.primaryMediaId = next ? next.mediaId : null
    if (next) next.role = 'PRIMARY'
  }
  if (!component.primaryMediaId) component.imageState = 'PLACEHOLDER'
  component.version += 1
  component.updatedBy = currentUserId()
  component.updateTime = nowIso()
  return imagesPayload(component)
}

export function getMedia(mediaId) {
  if (!getMockEnabled()) {
    return request.get(`/api/media/${mediaId}`)
  }
  const media = findMedia(mediaId)
  if (!media || media.status !== 'READY') return reject(404, '媒体不存在或不可见', 'MEDIA_NOT_FOUND')
  const component = findComponent(media.ownerId)
  if (!component) return reject(404, '媒体不存在或不可见', 'MEDIA_NOT_FOUND')
  if (!canReadInactiveCatalog() && component.status !== 'ACTIVE') {
    return reject(404, '媒体不存在或不可见', 'MEDIA_NOT_FOUND')
  }
  return Promise.resolve(publicMedia(media))
}

export function getMediaContent(mediaId) {
  if (!getMockEnabled()) {
    // 图片失败由组件回退同一 placeholderKey，避免多张坏图重复弹全局错误 toast
    return request.get(`/api/media/${mediaId}/content`, { responseType: 'blob', silentError: true })
      .then((res) => {
        const blob = res?.data
        if (!blob) throw { code: 404, msg: '媒体不存在或不可见', reason: 'MEDIA_NOT_FOUND', data: null }
        return typeof URL !== 'undefined' && URL.createObjectURL ? URL.createObjectURL(blob) : ''
      })
  }
  const media = findMedia(mediaId)
  if (!media || media.status !== 'READY') return reject(404, '媒体不存在或不可见', 'MEDIA_NOT_FOUND')
  const component = findComponent(media.ownerId)
  if (!component) return reject(404, '媒体不存在或不可见', 'MEDIA_NOT_FOUND')
  if (!canReadInactiveCatalog() && component.status !== 'ACTIVE') {
    return reject(404, '媒体不存在或不可见', 'MEDIA_NOT_FOUND')
  }
  return Promise.resolve(media._dataUrl || media._objectUrl || '')
}

// ========== 占位与展示辅助 ==========

export const PLACEHOLDER_PALETTE = Object.freeze([
  { bg: '#e8f1fa', fg: '#07598f', accent: '#0b6db6' },
  { bg: '#eef7f1', fg: '#1d7a4d', accent: '#2f8f5f' },
  { bg: '#fbf3e6', fg: '#a66100', accent: '#c77a0c' },
  { bg: '#f4edf7', fg: '#93559c', accent: '#a96bb1' }
])

export function placeholderDisplay(placeholderKey) {
  const parts = String(placeholderKey || 'component:COMPONENT:UNKNOWN:?').split(':')
  const category = parts[1] || 'COMPONENT'
  const packageType = parts[2] || 'UNKNOWN'
  const glyph = parts[3] || '?'
  let hash = 0
  for (let i = 0; i < String(placeholderKey).length; i++) {
    hash = (hash * 31 + String(placeholderKey).charCodeAt(i)) >>> 0
  }
  const palette = PLACEHOLDER_PALETTE[hash % PLACEHOLDER_PALETTE.length]
  return { category, packageType, glyph, palette, label: `${category} · ${packageType}` }
}

export function componentImageState(component) {
  return component?.primaryMediaId ? IMAGE_STATE.READY : IMAGE_STATE.PLACEHOLDER
}
