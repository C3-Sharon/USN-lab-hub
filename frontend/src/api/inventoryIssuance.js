import request from '@/utils/request'
import { getMockEnabled } from '@/utils/mock'
import { userStore, currentRoles } from '@/store/user'
import { ROLE } from '@/utils/permission'
import { idempotencyHeaders, requestDigest } from '@/utils/idempotency'
import { isMockScenario, mockDelay, mockReject, mockTraceId } from '@/utils/mockScenario'

/**
 * 库存领用、预占与受控库存流水 API（第 10 周 M03-W03）
 *
 * 契约来源：docs/contracts/INVENTORY_ISSUANCE_API.md v1.0（FROZEN）
 *
 * 真实端点（契约 §9）：
 *   GET/PUT  /api/inventory/issue-policy
 *   GET      /api/inventory/issue-policy/versions
 *   POST/GET /api/inventory-issue-requests
 *   GET/PUT  /api/inventory-issue-requests/{id}
 *   POST     /api/inventory-issue-requests/{id}/submit|approve|reject|cancel|reallocate|confirm-issue
 *   POST/GET /api/inventory-fulfillments/{id}/returns
 *   POST     /api/inventory-returns/{id}/confirm|reject
 *   POST     /api/inventory/batches/{batchId}/losses|scraps
 *   GET      /api/inventory/components/{componentId}
 *   GET      /api/inventory/components/{componentId}/batches
 *   GET      /api/inventory/movements
 *
 * 边界：提交即预占（默认 72h）；审批不扣库存；发放才写 ISSUE_OUT 并扣减；
 * 全量发放、整单取消；批次 FEFO + FIFO；普通成员不返回 unitCost、estimatedAmount
 * 或采购来源；不可见申请统一 404；mock 默认关闭（VITE_USE_MOCK）。
 */

// ========== 枚举（契约 §4 / §13.3） ==========

export const ISSUE_STATUS = Object.freeze({
  DRAFT: 'DRAFT',
  PENDING_APPROVAL: 'PENDING_APPROVAL',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  ISSUED: 'ISSUED',
  CANCELED: 'CANCELED',
  EXPIRED: 'EXPIRED'
})

export const ISSUE_STATUS_META = Object.freeze({
  [ISSUE_STATUS.DRAFT]: { label: '草稿', tagType: 'info', desc: '未预占；可编辑、提交或取消' },
  [ISSUE_STATUS.PENDING_APPROVAL]: { label: '待审批', tagType: 'warning', desc: '已预占；等待非本人的老师/管理员批准' },
  [ISSUE_STATUS.APPROVED]: { label: '已批准，待发放', tagType: 'success', desc: '预占保持；库存管理员确认发放后才扣减' },
  [ISSUE_STATUS.REJECTED]: { label: '已拒绝', tagType: 'danger', desc: '终态；预占已释放，需重新申请' },
  [ISSUE_STATUS.ISSUED]: { label: '已发放', tagType: 'success', desc: '终态；已扣减库存并写 ISSUE_OUT' },
  [ISSUE_STATUS.CANCELED]: { label: '已取消', tagType: 'info', desc: '终态；预占已释放（草稿无预占）' },
  [ISSUE_STATUS.EXPIRED]: { label: '预占已过期', tagType: 'danger', desc: '预占已释放；需重新申请' }
})

export const ISSUE_TERMINAL_STATUSES = Object.freeze([
  ISSUE_STATUS.REJECTED,
  ISSUE_STATUS.ISSUED,
  ISSUE_STATUS.CANCELED,
  ISSUE_STATUS.EXPIRED
])

export const APPROVAL_DECISION = Object.freeze({
  AUTO_APPROVED: 'AUTO_APPROVED',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED'
})

export const APPROVAL_DECISION_META = Object.freeze({
  [APPROVAL_DECISION.AUTO_APPROVED]: { label: '自动免审', tagType: 'success' },
  [APPROVAL_DECISION.APPROVED]: { label: '人工批准', tagType: 'success' },
  [APPROVAL_DECISION.REJECTED]: { label: '人工拒绝', tagType: 'danger' }
})

export const APPROVAL_REASON = Object.freeze({
  AMOUNT_THRESHOLD: 'AMOUNT_THRESHOLD',
  VALUABLE_BATCH: 'VALUABLE_BATCH',
  FORCED_CATEGORY: 'FORCED_CATEGORY',
  QUANTITY_THRESHOLD: 'QUANTITY_THRESHOLD',
  LOW_STOCK: 'LOW_STOCK',
  POLICY_REQUIRED: 'POLICY_REQUIRED',
  EXEMPT: 'EXEMPT'
})

export const APPROVAL_REASON_META = Object.freeze({
  [APPROVAL_REASON.AMOUNT_THRESHOLD]: { label: '金额超阈值', tagType: 'warning' },
  [APPROVAL_REASON.VALUABLE_BATCH]: { label: '贵重批次', tagType: 'warning' },
  [APPROVAL_REASON.FORCED_CATEGORY]: { label: '指定分类', tagType: 'warning' },
  [APPROVAL_REASON.QUANTITY_THRESHOLD]: { label: '数量超直接领用上限', tagType: 'warning' },
  [APPROVAL_REASON.LOW_STOCK]: { label: '库存紧张', tagType: 'danger' },
  [APPROVAL_REASON.POLICY_REQUIRED]: { label: '策略要求人工审批', tagType: 'warning' },
  [APPROVAL_REASON.EXEMPT]: { label: '安全免审', tagType: 'success' }
})

export const RESERVATION_STATUS = Object.freeze({
  ACTIVE: 'ACTIVE',
  CONSUMED: 'CONSUMED',
  RELEASED: 'RELEASED',
  EXPIRED: 'EXPIRED'
})

export const RESERVATION_STATUS_META = Object.freeze({
  [RESERVATION_STATUS.ACTIVE]: { label: '预占中', tagType: 'warning' },
  [RESERVATION_STATUS.CONSUMED]: { label: '已消费', tagType: 'success' },
  [RESERVATION_STATUS.RELEASED]: { label: '已释放', tagType: 'info' },
  [RESERVATION_STATUS.EXPIRED]: { label: '已过期', tagType: 'danger' }
})

export const RELEASE_REASON = Object.freeze({
  REJECTED: 'REJECTED',
  CANCELED: 'CANCELED',
  EXPIRED: 'EXPIRED',
  REALLOCATED: 'REALLOCATED'
})

export const RETURN_STATUS = Object.freeze({
  PENDING_CONFIRMATION: 'PENDING_CONFIRMATION',
  CONFIRMED: 'CONFIRMED',
  REJECTED: 'REJECTED',
  CANCELED: 'CANCELED'
})

export const RETURN_STATUS_META = Object.freeze({
  [RETURN_STATUS.PENDING_CONFIRMATION]: { label: '待确认', tagType: 'warning' },
  [RETURN_STATUS.CONFIRMED]: { label: '已确认退回', tagType: 'success' },
  [RETURN_STATUS.REJECTED]: { label: '已拒绝', tagType: 'danger' },
  [RETURN_STATUS.CANCELED]: { label: '已取消', tagType: 'info' }
})

export const MOVEMENT_TYPE = Object.freeze({
  PROCUREMENT_RECEIPT_IN: 'PROCUREMENT_RECEIPT_IN',
  ISSUE_OUT: 'ISSUE_OUT',
  RETURN_IN: 'RETURN_IN',
  LOSS_OUT: 'LOSS_OUT',
  SCRAP_OUT: 'SCRAP_OUT',
  STOCKTAKE_ADJUSTMENT: 'STOCKTAKE_ADJUSTMENT'
})

export const MOVEMENT_TYPE_META = Object.freeze({
  [MOVEMENT_TYPE.PROCUREMENT_RECEIPT_IN]: { label: '采购入库', tagType: 'success', direction: 1 },
  [MOVEMENT_TYPE.ISSUE_OUT]: { label: '领用发放', tagType: 'primary', direction: -1 },
  [MOVEMENT_TYPE.RETURN_IN]: { label: '领用退回', tagType: 'success', direction: 1 },
  [MOVEMENT_TYPE.LOSS_OUT]: { label: '受控损耗', tagType: 'danger', direction: -1 },
  [MOVEMENT_TYPE.SCRAP_OUT]: { label: '受控报废', tagType: 'warning', direction: -1 },
  [MOVEMENT_TYPE.STOCKTAKE_ADJUSTMENT]: { label: '盘点修正（冻结）', tagType: 'info', direction: 0 }
})

export const WRITE_OFF_TYPE = Object.freeze({ LOSS: 'LOSS', SCRAP: 'SCRAP' })

export const AMOUNT_SCALE = 2
export const QUANTITY_SCALE = 3
export const UNIT_COST_SCALE = 4

export const ISSUE_POLICY_DEFAULTS = Object.freeze({
  currency: 'CNY',
  directAmountThreshold: 100.0,
  valuableUnitCostThreshold: 500.0,
  defaultDirectQuantityLimit: 10.0,
  lowStockRatio: 20.0,
  reservationTtlHours: 72
})

export function issueStatusMeta(status) {
  return ISSUE_STATUS_META[status] || { label: status || '未知', tagType: 'info', desc: '' }
}

export function approvalDecisionMeta(decision) {
  return APPROVAL_DECISION_META[decision] || { label: decision || '—', tagType: 'info' }
}

export function approvalReasonMeta(reason) {
  return APPROVAL_REASON_META[reason] || { label: reason || '—', tagType: 'info' }
}

export function reservationStatusMeta(status) {
  return RESERVATION_STATUS_META[status] || { label: status || '—', tagType: 'info' }
}

export function returnStatusMeta(status) {
  return RETURN_STATUS_META[status] || { label: status || '—', tagType: 'info' }
}

export function movementTypeMeta(type) {
  return MOVEMENT_TYPE_META[type] || { label: type || '—', tagType: 'info', direction: 0 }
}

export function isTerminalIssueStatus(status) {
  return ISSUE_TERMINAL_STATUSES.includes(status)
}

// ========== 展示格式 ==========

function round(value, scale) {
  const factor = 10 ** scale
  return Math.round((Number(value || 0) + Number.EPSILON) * factor) / factor
}

export function formatAmount(value) {
  if (value == null) return '—'
  return `¥${Number(value).toFixed(AMOUNT_SCALE)}`
}

export function formatQuantity(value) {
  if (value == null) return '—'
  const fixed = Number(value).toFixed(QUANTITY_SCALE)
  return fixed.replace(/\.?0+$/, '') || '0'
}

export function formatQuantityUnit(value, unit) {
  const text = formatQuantity(value)
  return unit ? `${text} ${unit}` : text
}

export function formatUnitCost(value) {
  if (value == null) return '—'
  return `¥${Number(value).toFixed(UNIT_COST_SCALE)}`
}

export function lineReturnableQuantity(line) {
  if (!line) return 0
  return round(Number(line.issuedQuantity || 0) - Number(line.returnedQuantity || 0), QUANTITY_SCALE)
}

export function lineAvailablePercent(line) {
  const onHand = Number(line?.onHandQuantity || 0)
  if (onHand <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((Number(line.availableQuantity || 0) / onHand) * 100)))
}

export function formatRemainingDuration(expiresAt, now = Date.now()) {
  if (!expiresAt) return ''
  const target = new Date(expiresAt).getTime()
  if (Number.isNaN(target)) return ''
  const diff = target - now
  if (diff <= 0) return '已到期'
  const totalMinutes = Math.floor(diff / 60000)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours >= 24) {
    const days = Math.floor(hours / 24)
    return `${days} 天 ${hours % 24} 小时`
  }
  return minutes ? `${hours} 小时 ${minutes} 分钟` : `${hours} 小时`
}

// ========== 权限（契约 §7；前端只控制 UI，真正鉴权以后端为准） ==========

export const ISSUE_ROLES = Object.freeze([ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER])

function roles() {
  return currentRoles() || []
}

function has(role) {
  return roles().includes(role)
}

export function currentUserId() {
  return userStore.userInfo?.id ?? null
}

export function canAccessIssuance() {
  return ISSUE_ROLES.some((role) => has(role))
}

export function canViewAllRequests() {
  return has(ROLE.SYSTEM_ADMIN) || has(ROLE.TEACHER) || has(ROLE.STOCK_KEEPER)
}

export function canCreateIssueRequest() {
  return canAccessIssuance()
}

export function isIssueRequester(issueRequest) {
  return Boolean(issueRequest) && Number(issueRequest.requesterId) === Number(currentUserId())
}

export function canViewIssueRequest(issueRequest) {
  if (!issueRequest) return false
  return canViewAllRequests() || isIssueRequester(issueRequest)
}

export function canEditIssueRequest(issueRequest) {
  if (!issueRequest || issueRequest.status !== ISSUE_STATUS.DRAFT) return false
  return isIssueRequester(issueRequest) || has(ROLE.SYSTEM_ADMIN)
}

export function canSubmitIssueRequest(issueRequest) {
  return canEditIssueRequest(issueRequest)
}

export function canApproveIssueRequest(issueRequest) {
  if (!issueRequest || issueRequest.status !== ISSUE_STATUS.PENDING_APPROVAL) return false
  const roleAllowed = has(ROLE.TEACHER) || has(ROLE.SYSTEM_ADMIN)
  return roleAllowed && !isIssueRequester(issueRequest)
}

export function canRejectIssueRequest(issueRequest) {
  return canApproveIssueRequest(issueRequest)
}

export function canCancelIssueRequest(issueRequest) {
  if (!issueRequest) return false
  if (![ISSUE_STATUS.DRAFT, ISSUE_STATUS.PENDING_APPROVAL, ISSUE_STATUS.APPROVED].includes(issueRequest.status)) return false
  return isIssueRequester(issueRequest) || has(ROLE.SYSTEM_ADMIN)
}

export function canReallocateIssueRequest(issueRequest) {
  if (!issueRequest || issueRequest.status !== ISSUE_STATUS.APPROVED) return false
  return has(ROLE.STOCK_KEEPER) || has(ROLE.SYSTEM_ADMIN)
}

export function canConfirmIssue(issueRequest) {
  if (!issueRequest || issueRequest.status !== ISSUE_STATUS.APPROVED) return false
  const roleAllowed = has(ROLE.STOCK_KEEPER) || has(ROLE.SYSTEM_ADMIN)
  return roleAllowed && !isIssueRequester(issueRequest)
}

export function canInitiateReturn(issueRequest) {
  if (!issueRequest || issueRequest.status !== ISSUE_STATUS.ISSUED) return false
  return isIssueRequester(issueRequest) || has(ROLE.SYSTEM_ADMIN)
}

export function canConfirmReturn() {
  return has(ROLE.STOCK_KEEPER) || has(ROLE.SYSTEM_ADMIN)
}

export function canWriteOffStock() {
  return has(ROLE.STOCK_KEEPER) || has(ROLE.SYSTEM_ADMIN)
}

export function canViewIssueCost() {
  return has(ROLE.SYSTEM_ADMIN) || has(ROLE.TEACHER) || has(ROLE.STOCK_KEEPER)
}

export function canViewIssueBatches() {
  return canViewIssueCost()
}

export function canViewInventoryMovements() {
  return canViewIssueCost()
}

export function canPublishIssuePolicy() {
  return has(ROLE.SYSTEM_ADMIN)
}

export function issueRequestActions(issueRequest) {
  return {
    edit: canEditIssueRequest(issueRequest),
    submit: canSubmitIssueRequest(issueRequest),
    approve: canApproveIssueRequest(issueRequest),
    reject: canRejectIssueRequest(issueRequest),
    cancel: canCancelIssueRequest(issueRequest),
    reallocate: canReallocateIssueRequest(issueRequest),
    confirmIssue: canConfirmIssue(issueRequest),
    initiateReturn: canInitiateReturn(issueRequest)
  }
}

// ========== Mock 基础数据 ==========

const MOCK_USERS = {
  1: '系统管理员',
  2: '林小满',
  3: '周老师',
  4: '陈仓管',
  99: '无关成员'
}

const MOCK_PROJECT_NAMES = { 10: '气象站原型', 11: '机械臂控制板' }

const MOCK_COMPONENTS = [
  { id: 301, name: 'STM32F103C8T6 最小系统板', mpn: 'STM32F103C8T6', manufacturerName: 'ST', baseUnit: 'PCS', categoryId: 31, categoryName: '单片机/核心板', unitCost: 18.5, status: 'ACTIVE' },
  { id: 302, name: '10uF 贴片电容', mpn: 'CL10A106KP8NNNC', manufacturerName: 'Samsung', baseUnit: 'PCS', categoryId: 32, categoryName: '被动元件', unitCost: 0.12, status: 'ACTIVE' },
  { id: 303, name: 'BME280 环境传感器模块', mpn: 'BME280', manufacturerName: 'Bosch', baseUnit: 'PCS', categoryId: 33, categoryName: '传感器', unitCost: 22, status: 'ACTIVE' },
  { id: 304, name: 'OPA333 高精度运放', mpn: 'OPA333AIDBVR', manufacturerName: 'TI', baseUnit: 'PCS', categoryId: 31, categoryName: '单片机/核心板', unitCost: 620, status: 'ACTIVE' },
  { id: 305, name: '10k 贴片电阻', mpn: 'RC0603FR-0710KL', manufacturerName: 'Yageo', baseUnit: 'PCS', categoryId: 32, categoryName: '被动元件', unitCost: 0.01, status: 'ACTIVE' },
  { id: 306, name: 'XH2.54 4Pin 连接器', mpn: 'XH2.54-4P', manufacturerName: 'JST', baseUnit: 'PCS', categoryId: 34, categoryName: '连接器', unitCost: 0.8, status: 'ACTIVE' }
]

const MOCK_BALANCES = [
  { componentId: 301, onHandQuantity: 20, reservedQuantity: 5, unit: 'PCS', version: 7, updateTime: '2026-11-22T10:00:00+08:00' },
  { componentId: 302, onHandQuantity: 405, reservedQuantity: 20, unit: 'PCS', version: 12, updateTime: '2026-11-22T10:00:00+08:00' },
  { componentId: 303, onHandQuantity: 8, reservedQuantity: 0, unit: 'PCS', version: 3, updateTime: '2026-11-21T09:00:00+08:00' },
  { componentId: 304, onHandQuantity: 3, reservedQuantity: 1, unit: 'PCS', version: 2, updateTime: '2026-11-20T09:00:00+08:00' },
  { componentId: 305, onHandQuantity: 1980, reservedQuantity: 0, unit: 'PCS', version: 5, updateTime: '2026-11-19T09:00:00+08:00' },
  { componentId: 306, onHandQuantity: 4, reservedQuantity: 0, unit: 'PCS', version: 2, updateTime: '2026-11-18T09:00:00+08:00' }
]

const MOCK_BATCHES = [
  { id: 1101, batchNo: 'B-301-A', componentId: 301, currentQuantity: 12, reservedQuantity: 0, expiryDate: '2027-03-01', receivedAt: '2026-10-01', unitCost: 18.5, sourceReceiptNo: 'RCP-20261001-000001', locationNote: 'A 区 1 柜' },
  { id: 1102, batchNo: 'B-301-B', componentId: 301, currentQuantity: 8, reservedQuantity: 5, expiryDate: '2026-12-01', receivedAt: '2026-09-01', unitCost: 18.0, sourceReceiptNo: 'RCP-20260901-000002', locationNote: 'A 区 2 柜' },
  { id: 1201, batchNo: 'B-302-A', componentId: 302, currentQuantity: 205, reservedQuantity: 20, expiryDate: '2027-06-01', receivedAt: '2026-08-01', unitCost: 0.12, sourceReceiptNo: 'RCP-20260801-000001', locationNote: 'B 区 1 柜' },
  { id: 1202, batchNo: 'B-302-B', componentId: 302, currentQuantity: 200, reservedQuantity: 0, expiryDate: null, receivedAt: '2026-09-15', unitCost: 0.11, sourceReceiptNo: 'RCP-20260915-000003', locationNote: 'B 区 2 柜' },
  { id: 1301, batchNo: 'B-303-A', componentId: 303, currentQuantity: 8, reservedQuantity: 0, expiryDate: '2027-01-01', receivedAt: '2026-07-20', unitCost: 22, sourceReceiptNo: 'RCP-20260720-000001', locationNote: 'C 区 1 柜' },
  { id: 1401, batchNo: 'B-304-A', componentId: 304, currentQuantity: 3, reservedQuantity: 1, expiryDate: '2028-01-01', receivedAt: '2026-06-01', unitCost: 620, sourceReceiptNo: 'RCP-20260601-000001', locationNote: '贵重柜' },
  { id: 1501, batchNo: 'B-305-A', componentId: 305, currentQuantity: 1980, reservedQuantity: 0, expiryDate: null, receivedAt: '2026-05-01', unitCost: 0.01, sourceReceiptNo: 'RCP-20260501-000001', locationNote: 'D 区' },
  { id: 1601, batchNo: 'B-306-A', componentId: 306, currentQuantity: 4, reservedQuantity: 0, expiryDate: null, receivedAt: '2026-05-10', unitCost: 0.8, sourceReceiptNo: 'RCP-20260510-000001', locationNote: 'D 区' }
]

const MOCK_POLICIES = [
  {
    id: 1,
    policyVersion: 1,
    currency: 'CNY',
    directAmountThreshold: 100.0,
    valuableUnitCostThreshold: 500.0,
    defaultDirectQuantityLimit: 10.0,
    lowStockRatio: 20.0,
    reservationTtlHours: 72,
    manualApprovalRequired: false,
    forcedCategoryIds: [34],
    componentOverrides: [{ id: 11, componentId: 303, directQuantityLimit: 5, componentName: 'BME280 环境传感器模块', unitSnapshot: 'PCS' }],
    effectiveAt: '2026-11-19T09:00:00+08:00',
    createdBy: 1,
    createTime: '2026-11-19T09:00:00+08:00'
  }
]

function mockLine(id, lineNo, componentId, requestedQuantity, extra = {}) {
  const component = MOCK_COMPONENTS.find((item) => item.id === componentId)
  return {
    id,
    lineNo,
    componentId,
    componentName: component?.name || `元器件 ${componentId}`,
    manufacturerName: component?.manufacturerName || '—',
    mpn: component?.mpn || '—',
    categoryId: component?.categoryId ?? null,
    unit: component?.baseUnit || 'PCS',
    requestedQuantity,
    reservedQuantity: 0,
    issuedQuantity: 0,
    returnedQuantity: 0,
    version: 1,
    ...extra
  }
}

function mockReservation(id, requestId, requestLineId, componentId, quantity, status, expiresAt, allocations, extra = {}) {
  return {
    reservationId: id,
    reservationNo: `RSV-20261122-${String(id).padStart(6, '0')}`,
    requestId,
    requestLineId,
    componentId,
    quantity,
    status,
    expiresAt,
    releaseReason: null,
    version: 1,
    allocations,
    ...extra
  }
}

function mockAllocation(id, reservationId, batchId, quantity, unitCostSnapshot) {
  return { id, reservationId, batchId, quantity, unitCostSnapshot, version: 1 }
}

const MOCK_REQUESTS = [
  {
    id: 2001,
    requestNo: 'ISS-20261122-000001',
    title: '气象站原型焊接领料',
    purpose: '焊接第一版传感器节点',
    requesterId: 2,
    requesterName: MOCK_USERS[2],
    projectId: 10,
    projectName: MOCK_PROJECT_NAMES[10],
    status: ISSUE_STATUS.APPROVED,
    approvalRequired: false,
    approvalReasons: [APPROVAL_REASON.EXEMPT],
    policyVersion: 1,
    estimatedAmount: 90.0,
    submittedAt: '2026-11-22T10:00:00+08:00',
    approvedAt: '2026-11-22T10:00:00+08:00',
    issuedAt: null,
    expiresAt: '2026-11-25T10:00:00+08:00',
    version: 2,
    createTime: '2026-11-22T09:50:00+08:00',
    updateTime: '2026-11-22T10:00:00+08:00',
    updatedBy: 2,
    lines: [mockLine(2011, 1, 301, 5, { reservedQuantity: 5 })],
    approvalRecords: [{
      id: 3011,
      requestId: 2001,
      decision: APPROVAL_DECISION.AUTO_APPROVED,
      actorType: 'SYSTEM',
      decidedBy: null,
      decidedAt: '2026-11-22T10:00:00+08:00',
      policyVersion: 1,
      triggerSnapshot: { directAmountThreshold: 100, estimatedAmount: 90, reasons: [APPROVAL_REASON.EXEMPT] },
      comment: null,
      requestIdempotencyId: 'mock-auto-2001'
    }],
    reservations: [mockReservation(4001, 2001, 2011, 301, 5, RESERVATION_STATUS.ACTIVE, '2026-11-25T10:00:00+08:00', [mockAllocation(4101, 4001, 1102, 5, 18.0)])],
    fulfillment: null,
    returns: []
  },
  {
    id: 2002,
    requestNo: 'ISS-20261122-000002',
    title: '运放替换件领取',
    purpose: '修复高精度采集板',
    requesterId: 2,
    requesterName: MOCK_USERS[2],
    projectId: 10,
    projectName: MOCK_PROJECT_NAMES[10],
    status: ISSUE_STATUS.PENDING_APPROVAL,
    approvalRequired: true,
    approvalReasons: [APPROVAL_REASON.AMOUNT_THRESHOLD, APPROVAL_REASON.VALUABLE_BATCH],
    policyVersion: 1,
    estimatedAmount: 620.0,
    submittedAt: '2026-11-22T11:00:00+08:00',
    approvedAt: null,
    issuedAt: null,
    expiresAt: '2026-11-25T11:00:00+08:00',
    version: 2,
    createTime: '2026-11-22T10:50:00+08:00',
    updateTime: '2026-11-22T11:00:00+08:00',
    updatedBy: 2,
    lines: [mockLine(2021, 1, 304, 1, { reservedQuantity: 1 })],
    approvalRecords: [],
    reservations: [mockReservation(4002, 2002, 2021, 304, 1, RESERVATION_STATUS.ACTIVE, '2026-11-25T11:00:00+08:00', [mockAllocation(4102, 4002, 1401, 1, 620)])],
    fulfillment: null,
    returns: []
  },
  {
    id: 2003,
    requestNo: 'ISS-20261123-000003',
    title: '传感器模块试制',
    purpose: '验证 BME280 采集链路',
    requesterId: 2,
    requesterName: MOCK_USERS[2],
    projectId: null,
    projectName: null,
    status: ISSUE_STATUS.DRAFT,
    approvalRequired: null,
    approvalReasons: [],
    policyVersion: null,
    estimatedAmount: null,
    submittedAt: null,
    approvedAt: null,
    issuedAt: null,
    expiresAt: null,
    version: 1,
    createTime: '2026-11-23T08:00:00+08:00',
    updateTime: '2026-11-23T08:00:00+08:00',
    updatedBy: 2,
    lines: [mockLine(2031, 1, 303, 2)],
    approvalRecords: [],
    reservations: [],
    fulfillment: null,
    returns: []
  },
  {
    id: 2004,
    requestNo: 'ISS-20261120-000004',
    title: '电容批量领用',
    purpose: '电源滤波批量焊接',
    requesterId: 2,
    requesterName: MOCK_USERS[2],
    projectId: 11,
    projectName: MOCK_PROJECT_NAMES[11],
    status: ISSUE_STATUS.ISSUED,
    approvalRequired: false,
    approvalReasons: [APPROVAL_REASON.EXEMPT],
    policyVersion: 1,
    estimatedAmount: 12.0,
    submittedAt: '2026-11-20T09:00:00+08:00',
    approvedAt: '2026-11-20T09:00:00+08:00',
    issuedAt: '2026-11-20T14:00:00+08:00',
    expiresAt: '2026-11-23T09:00:00+08:00',
    version: 3,
    createTime: '2026-11-20T08:50:00+08:00',
    updateTime: '2026-11-21T10:00:00+08:00',
    updatedBy: 4,
    lines: [mockLine(2041, 1, 302, 100, { issuedQuantity: 100, returnedQuantity: 5 })],
    approvalRecords: [{
      id: 3014,
      requestId: 2004,
      decision: APPROVAL_DECISION.AUTO_APPROVED,
      actorType: 'SYSTEM',
      decidedBy: null,
      decidedAt: '2026-11-20T09:00:00+08:00',
      policyVersion: 1,
      triggerSnapshot: { estimatedAmount: 12, reasons: [APPROVAL_REASON.EXEMPT] },
      comment: null,
      requestIdempotencyId: 'mock-auto-2004'
    }],
    reservations: [mockReservation(4004, 2004, 2041, 302, 100, RESERVATION_STATUS.CONSUMED, '2026-11-23T09:00:00+08:00', [mockAllocation(4104, 4004, 1201, 100, 0.12)])],
    fulfillment: {
      id: 2101,
      fulfillmentNo: 'FUL-20261120-000001',
      requestId: 2004,
      operatorId: 4,
      operatorName: MOCK_USERS[4],
      recipientId: 2,
      recipientName: MOCK_USERS[2],
      issuedAt: '2026-11-20T14:00:00+08:00',
      note: '已交付申请人',
      lines: [
        { id: 2111, fulfillmentId: 2101, requestLineId: 2041, reservationAllocationId: 4104, batchId: 1201, quantity: 100, unitCostSnapshot: 0.12, movementId: 5003 }
      ]
    },
    returns: [{
      id: 6001,
      returnNo: 'RTN-20261121-000001',
      fulfillmentId: 2101,
      requestId: 2004,
      status: RETURN_STATUS.CONFIRMED,
      reason: '焊错批次，退回未使用部分',
      requestedBy: 2,
      requestedAt: '2026-11-21T09:00:00+08:00',
      confirmedBy: 4,
      confirmedAt: '2026-11-21T10:00:00+08:00',
      version: 2,
      lines: [{ id: 6011, returnId: 6001, fulfillmentLineId: 2111, batchId: 1201, quantity: 5 }]
    }]
  },
  {
    id: 2005,
    requestNo: 'ISS-20261119-000005',
    title: '电阻补料',
    purpose: '课程实验补料',
    requesterId: 99,
    requesterName: MOCK_USERS[99],
    projectId: null,
    projectName: null,
    status: ISSUE_STATUS.REJECTED,
    approvalRequired: true,
    approvalReasons: [APPROVAL_REASON.QUANTITY_THRESHOLD],
    policyVersion: 1,
    estimatedAmount: 0.5,
    submittedAt: '2026-11-19T09:00:00+08:00',
    approvedAt: null,
    issuedAt: null,
    expiresAt: '2026-11-22T09:00:00+08:00',
    version: 3,
    createTime: '2026-11-19T08:50:00+08:00',
    updateTime: '2026-11-19T15:00:00+08:00',
    updatedBy: 3,
    lines: [mockLine(2051, 1, 305, 50)],
    approvalRecords: [{
      id: 3015,
      requestId: 2005,
      decision: APPROVAL_DECISION.REJECTED,
      actorType: 'USER',
      decidedBy: 3,
      decidedAt: '2026-11-19T15:00:00+08:00',
      policyVersion: 1,
      triggerSnapshot: { reasons: [APPROVAL_REASON.QUANTITY_THRESHOLD] },
      comment: '请说明课程实验用途后重新申请',
      requestIdempotencyId: 'mock-reject-2005'
    }],
    reservations: [mockReservation(4005, 2005, 2051, 305, 50, RESERVATION_STATUS.RELEASED, '2026-11-22T09:00:00+08:00', [mockAllocation(4105, 4005, 1501, 50, 0.01)], { releaseReason: RELEASE_REASON.REJECTED })],
    fulfillment: null,
    returns: []
  },
  {
    id: 2006,
    requestNo: 'ISS-20261121-000006',
    title: '连接器试用',
    purpose: '原型接线',
    requesterId: 2,
    requesterName: MOCK_USERS[2],
    projectId: null,
    projectName: null,
    status: ISSUE_STATUS.CANCELED,
    approvalRequired: null,
    approvalReasons: [],
    policyVersion: null,
    estimatedAmount: null,
    submittedAt: null,
    approvedAt: null,
    issuedAt: null,
    expiresAt: null,
    version: 2,
    createTime: '2026-11-21T08:00:00+08:00',
    updateTime: '2026-11-21T08:30:00+08:00',
    updatedBy: 2,
    lines: [mockLine(2061, 1, 306, 2)],
    approvalRecords: [],
    reservations: [],
    fulfillment: null,
    returns: []
  },
  {
    id: 2007,
    requestNo: 'ISS-20261118-000007',
    title: '电容补充领用',
    purpose: '实验课备料',
    requesterId: 99,
    requesterName: MOCK_USERS[99],
    projectId: null,
    projectName: null,
    status: ISSUE_STATUS.EXPIRED,
    approvalRequired: true,
    approvalReasons: [APPROVAL_REASON.LOW_STOCK],
    policyVersion: 1,
    estimatedAmount: 36.0,
    submittedAt: '2026-11-18T09:00:00+08:00',
    approvedAt: null,
    issuedAt: null,
    expiresAt: '2026-11-21T09:00:00+08:00',
    version: 2,
    createTime: '2026-11-18T08:50:00+08:00',
    updateTime: '2026-11-21T09:00:00+08:00',
    updatedBy: null,
    lines: [mockLine(2071, 1, 302, 300, { reservedQuantity: 0 })],
    approvalRecords: [],
    reservations: [mockReservation(4007, 2007, 2071, 302, 300, RESERVATION_STATUS.EXPIRED, '2026-11-21T09:00:00+08:00', [mockAllocation(4107, 4007, 1201, 300, 0.12)], { releaseReason: RELEASE_REASON.EXPIRED })],
    fulfillment: null,
    returns: []
  },
  {
    id: 2008,
    requestNo: 'ISS-20261122-000008',
    title: '课堂实验电容发放',
    purpose: '嵌入式课程第 8 次实验',
    requesterId: 2,
    requesterName: MOCK_USERS[2],
    projectId: null,
    projectName: null,
    status: ISSUE_STATUS.PENDING_APPROVAL,
    approvalRequired: true,
    approvalReasons: [APPROVAL_REASON.QUANTITY_THRESHOLD, APPROVAL_REASON.LOW_STOCK],
    policyVersion: 1,
    estimatedAmount: 2.4,
    submittedAt: '2026-11-22T15:00:00+08:00',
    approvedAt: null,
    issuedAt: null,
    expiresAt: '2026-11-25T15:00:00+08:00',
    version: 2,
    createTime: '2026-11-22T14:50:00+08:00',
    updateTime: '2026-11-22T15:00:00+08:00',
    updatedBy: 2,
    lines: [mockLine(2081, 1, 302, 20, { reservedQuantity: 20 })],
    approvalRecords: [],
    reservations: [mockReservation(4008, 2008, 2081, 302, 20, RESERVATION_STATUS.ACTIVE, '2026-11-25T15:00:00+08:00', [mockAllocation(4108, 4008, 1201, 20, 0.12)])],
    fulfillment: null,
    returns: []
  }
]

const MOCK_MOVEMENTS = [
  { movementId: 5001, movementType: MOVEMENT_TYPE.PROCUREMENT_RECEIPT_IN, componentId: 301, batchId: 1101, batchNo: 'B-301-A', quantityDelta: 20, balanceBefore: 0, balanceAfter: 20, batchBefore: 0, batchAfter: 20, operatorId: 4, operatorName: MOCK_USERS[4], sourceType: 'RECEIPT_LINE', sourceId: 9001, reason: null, requestId: null, occurredAt: '2026-10-01T10:00:00+08:00' },
  { movementId: 5002, movementType: MOVEMENT_TYPE.PROCUREMENT_RECEIPT_IN, componentId: 302, batchId: 1201, batchNo: 'B-302-A', quantityDelta: 500, balanceBefore: 0, balanceAfter: 500, batchBefore: 0, batchAfter: 500, operatorId: 4, operatorName: MOCK_USERS[4], sourceType: 'RECEIPT_LINE', sourceId: 9002, reason: null, requestId: null, occurredAt: '2026-08-01T10:00:00+08:00' },
  { movementId: 5003, movementType: MOVEMENT_TYPE.ISSUE_OUT, componentId: 302, batchId: 1201, batchNo: 'B-302-A', quantityDelta: -100, balanceBefore: 500, balanceAfter: 400, batchBefore: 300, batchAfter: 200, operatorId: 4, operatorName: MOCK_USERS[4], sourceType: 'FULFILLMENT_LINE', sourceId: 2111, reason: null, requestId: 2004, occurredAt: '2026-11-20T14:00:00+08:00' },
  { movementId: 5004, movementType: MOVEMENT_TYPE.RETURN_IN, componentId: 302, batchId: 1201, batchNo: 'B-302-A', quantityDelta: 5, balanceBefore: 400, balanceAfter: 405, batchBefore: 200, batchAfter: 205, operatorId: 4, operatorName: MOCK_USERS[4], sourceType: 'RETURN_LINE', sourceId: 6011, reason: '焊错批次，退回未使用部分', requestId: 2004, occurredAt: '2026-11-21T10:00:00+08:00' },
  { movementId: 5005, movementType: MOVEMENT_TYPE.LOSS_OUT, componentId: 305, batchId: 1501, batchNo: 'B-305-A', quantityDelta: -20, balanceBefore: 2000, balanceAfter: 1980, batchBefore: 2000, batchAfter: 1980, operatorId: 4, operatorName: MOCK_USERS[4], sourceType: 'WRITE_OFF_COMMAND', sourceId: 7001, reason: '实验台散落遗失', requestId: null, occurredAt: '2026-11-19T16:00:00+08:00' },
  { movementId: 5006, movementType: MOVEMENT_TYPE.SCRAP_OUT, componentId: 306, batchId: 1601, batchNo: 'B-306-A', quantityDelta: -1, balanceBefore: 5, balanceAfter: 4, batchBefore: 5, batchAfter: 4, operatorId: 4, operatorName: MOCK_USERS[4], sourceType: 'WRITE_OFF_COMMAND', sourceId: 7002, reason: '插针变形报废', requestId: null, occurredAt: '2026-11-18T16:00:00+08:00' }
]

let mockSequence = 9000
function nextMockId() {
  mockSequence += 1
  return mockSequence
}

function clone(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value))
}

function nowIso() {
  return new Date().toISOString()
}

function addHoursIso(baseIso, hours) {
  const time = baseIso ? new Date(baseIso).getTime() : Date.now()
  return new Date(time + Number(hours) * 3600 * 1000).toISOString()
}

function userName(userId) {
  return MOCK_USERS[Number(userId)] || `用户 ${userId}`
}

function findMockComponent(componentId) {
  return MOCK_COMPONENTS.find((component) => component.id === Number(componentId)) || null
}

function findMockBalance(componentId) {
  return MOCK_BALANCES.find((balance) => balance.componentId === Number(componentId)) || null
}

function findMockBatch(batchId) {
  return MOCK_BATCHES.find((batch) => batch.id === Number(batchId)) || null
}

function findMockRequest(requestId) {
  return MOCK_REQUESTS.find((item) => item.id === Number(requestId)) || null
}

function findMockReturn(returnId) {
  for (const issueRequest of MOCK_REQUESTS) {
    const found = (issueRequest.returns || []).find((item) => item.id === Number(returnId))
    if (found) return { issueRequest, returnRecord: found }
  }
  return null
}

function currentMockScenarioState() {
  return ['loading', 'empty', 'error', 'permission', 'conflict', 'success', 'insufficient-stock', 'reserved', 'expired']
    .find((state) => isMockScenario(state)) || ''
}

function errorWithTrace(code, msg, reason, data = null) {
  return mockReject(code, msg, reason, { ...(data || {}), traceId: mockTraceId('w46') })
}

function writeGuard() {
  if (isMockScenario('permission')) {
    return errorWithTrace(403, '当前验收场景无库存领用操作权限', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
  }
  if (!canAccessIssuance()) {
    return errorWithTrace(403, '当前账号没有库存领用权限', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
  }
  return null
}

function requestVisibilityError() {
  return errorWithTrace(404, '领用申请不存在或不可见', 'INVENTORY_ISSUE_REQUEST_NOT_FOUND', null)
}

function requestVisible(issueRequest) {
  return Boolean(issueRequest) && canViewIssueRequest(issueRequest)
}

function requireVisibleRequest(requestId) {
  const issueRequest = findMockRequest(requestId)
  if (!issueRequest || !requestVisible(issueRequest) || isMockScenario('permission')) return null
  return issueRequest
}

function balanceOf(componentId) {
  let balance = findMockBalance(componentId)
  if (!balance) {
    balance = { componentId: Number(componentId), onHandQuantity: 0, reservedQuantity: 0, unit: findMockComponent(componentId)?.baseUnit || 'PCS', version: 0, updateTime: null }
    MOCK_BALANCES.push(balance)
  }
  return balance
}

function availableOf(balance) {
  return round(Number(balance.onHandQuantity || 0) - Number(balance.reservedQuantity || 0), QUANTITY_SCALE)
}

function batchAvailable(batch) {
  return round(Number(batch.currentQuantity || 0) - Number(batch.reservedQuantity || 0), QUANTITY_SCALE)
}

function isBatchExpired(batch) {
  if (!batch.expiryDate) return false
  const expiry = new Date(`${batch.expiryDate}T23:59:59`).getTime()
  return !Number.isNaN(expiry) && expiry < Date.now()
}

function latestPolicy() {
  return MOCK_POLICIES[MOCK_POLICIES.length - 1]
}

function policyForVersion(version) {
  return MOCK_POLICIES.find((policy) => policy.policyVersion === Number(version)) || null
}

// ========== 归一化 / 成本脱敏 ==========

function sanitizeLine(line) {
  const copy = clone(line)
  copy.returnableQuantity = lineReturnableQuantity(copy)
  if (!canViewIssueCost()) {
    delete copy.estimatedAmount
    delete copy.unitCost
    if (Array.isArray(copy.allocations)) {
      copy.allocations = copy.allocations.map((allocation) => {
        const item = clone(allocation)
        delete item.unitCostSnapshot
        return item
      })
    }
  }
  return copy
}

function sanitizeApprovalRecord(record) {
  const copy = clone(record)
  if (!canViewIssueCost() && copy.triggerSnapshot) {
    const { estimatedAmount, unitCost, ...rest } = copy.triggerSnapshot
    copy.triggerSnapshot = rest
  }
  return copy
}

function normalizeMovement(movement) {
  const copy = clone(movement)
  if (!canViewIssueCost()) {
    delete copy.balanceBefore
    delete copy.balanceAfter
    delete copy.operatorId
    delete copy.operatorName
  }
  return copy
}

function publicIssueSummary(issueRequest) {
  const copy = clone(issueRequest)
  delete copy.lines
  delete copy.approvalRecords
  delete copy.reservations
  delete copy.fulfillment
  delete copy.returns
  copy.lineCount = (issueRequest.lines || []).length
  if (!canViewIssueCost()) {
    delete copy.estimatedAmount
  }
  return copy
}

function publicIssueDetail(issueRequest) {
  const copy = clone(issueRequest)
  copy.lines = (issueRequest.lines || []).map((line) => {
    const item = sanitizeLine(line)
    const balance = findMockBalance(line.componentId)
    item.onHandQuantity = round(balance?.onHandQuantity || 0, QUANTITY_SCALE)
    item.componentReservedQuantity = round(balance?.reservedQuantity || 0, QUANTITY_SCALE)
    item.availableQuantity = balance ? availableOf(balance) : 0
    return item
  })
  copy.approvalRecords = (issueRequest.approvalRecords || []).map(sanitizeApprovalRecord)
  copy.reservations = (issueRequest.reservations || []).map((reservation) => {
    const item = clone(reservation)
    item.allocations = (reservation.allocations || []).map((allocation) => {
      const row = clone(allocation)
      const batch = findMockBatch(row.batchId)
      row.batchNo = batch?.batchNo || `批次 ${row.batchId}`
      row.expiryDate = batch?.expiryDate || null
      if (!canViewIssueCost()) delete row.unitCostSnapshot
      return row
    })
    return item
  })
  if (issueRequest.fulfillment) {
    copy.fulfillment = clone(issueRequest.fulfillment)
    if (!canViewIssueCost()) {
      copy.fulfillment.lines = (copy.fulfillment.lines || []).map((line) => {
        const row = clone(line)
        delete row.unitCostSnapshot
        return row
      })
    }
  }
  copy.returns = clone(issueRequest.returns || [])
  if (!canViewIssueCost()) delete copy.estimatedAmount
  copy.returnableQuantity = copy.lines.reduce((sum, line) => sum + Number(line.returnableQuantity || 0), 0)
  return copy
}

function pageOf(list, params = {}) {
  const page = Math.max(1, Number(params.page || 1))
  const pageSize = Math.min(100, Math.max(1, Number(params.pageSize || 20)))
  const start = (page - 1) * pageSize
  return {
    list: list.slice(start, start + pageSize),
    total: list.length,
    page,
    pageSize,
    hasNext: start + pageSize < list.length
  }
}

// ========== 预占 / 释放 / 过期 ==========

const MOCK_IDEMPOTENCY = new Map()

function withIdempotency(scope, key, payload, handler) {
  if (!key) {
    return errorWithTrace(400, '缺少 Idempotency-Key', 'IDEMPOTENCY_KEY_REQUIRED', { header: 'Idempotency-Key' })
  }
  const cacheKey = `${scope}::${key}`
  const digest = requestDigest(payload)
  const existing = MOCK_IDEMPOTENCY.get(cacheKey)
  if (existing) {
    if (existing.digest !== digest) {
      return errorWithTrace(409, '同一 Idempotency-Key 被用于不同请求体', 'IDEMPOTENCY_KEY_REUSED', null)
    }
    return Promise.resolve(clone(existing.result))
  }
  return Promise.resolve()
    .then(handler)
    .then((result) => {
      MOCK_IDEMPOTENCY.set(cacheKey, { digest, result: clone(result) })
      return result
    })
}

function releaseReservations(issueRequest, reason) {
  ;(issueRequest.reservations || []).forEach((reservation) => {
    if (reservation.status !== RESERVATION_STATUS.ACTIVE) return
    const balance = findMockBalance(reservation.componentId)
    if (balance) {
      balance.reservedQuantity = round(Math.max(0, Number(balance.reservedQuantity || 0) - Number(reservation.quantity || 0)), QUANTITY_SCALE)
      balance.version += 1
    }
    ;(reservation.allocations || []).forEach((allocation) => {
      const batch = findMockBatch(allocation.batchId)
      if (batch) {
        batch.reservedQuantity = round(Math.max(0, Number(batch.reservedQuantity || 0) - Number(allocation.quantity || 0)), QUANTITY_SCALE)
      }
    })
    reservation.status = reason === RELEASE_REASON.EXPIRED ? RESERVATION_STATUS.EXPIRED : RESERVATION_STATUS.RELEASED
    reservation.releaseReason = reason
    reservation.version += 1
  })
  ;(issueRequest.lines || []).forEach((line) => {
    line.reservedQuantity = 0
  })
}

function expireIssuedReservation(issueRequest) {
  if (![ISSUE_STATUS.PENDING_APPROVAL, ISSUE_STATUS.APPROVED].includes(issueRequest.status)) return false
  if (!issueRequest.expiresAt) return false
  if (new Date(issueRequest.expiresAt).getTime() > Date.now()) return false
  releaseReservations(issueRequest, RELEASE_REASON.EXPIRED)
  issueRequest.status = ISSUE_STATUS.EXPIRED
  issueRequest.version += 1
  issueRequest.updateTime = nowIso()
  return true
}

function refreshExpiredRequests() {
  MOCK_REQUESTS.forEach((issueRequest) => expireIssuedReservation(issueRequest))
}

// ========== 批次分配与审批判定 ==========

function allocateLine(line) {
  const componentId = Number(line.componentId)
  const needed = Number(line.requestedQuantity || 0)
  const balance = findMockBalance(componentId)
  const available = balance ? availableOf(balance) : 0
  if (available < needed) {
    return { error: errorWithTrace(409, '可用库存不足', 'INVENTORY_INSUFFICIENT_STOCK', {
      componentId,
      requestedQuantity: round(needed, QUANTITY_SCALE),
      availableQuantity: round(available, QUANTITY_SCALE)
    }) }
  }
  const candidates = MOCK_BATCHES
    .filter((batch) => batch.componentId === componentId)
    .filter((batch) => !isBatchExpired(batch))
    .filter((batch) => batchAvailable(batch) > 0)
    .sort((a, b) => {
      const left = a.expiryDate ? new Date(a.expiryDate).getTime() : Number.MAX_SAFE_INTEGER
      const right = b.expiryDate ? new Date(b.expiryDate).getTime() : Number.MAX_SAFE_INTEGER
      if (left !== right) return left - right
      if (a.receivedAt !== b.receivedAt) return a.receivedAt > b.receivedAt ? 1 : -1
      return a.id - b.id
    })
  let remaining = needed
  const allocations = []
  for (const batch of candidates) {
    if (remaining <= 0) break
    const take = Math.min(remaining, batchAvailable(batch))
    if (take <= 0) continue
    allocations.push({ batchId: batch.id, quantity: round(take, QUANTITY_SCALE), unitCostSnapshot: round(batch.unitCost, UNIT_COST_SCALE) })
    remaining = round(remaining - take, QUANTITY_SCALE)
  }
  if (remaining > 0) {
    return { error: errorWithTrace(409, '可用库存不足', 'INVENTORY_INSUFFICIENT_STOCK', {
      componentId,
      requestedQuantity: round(needed, QUANTITY_SCALE),
      availableQuantity: round(available - remaining, QUANTITY_SCALE)
    }) }
  }
  return { allocations }
}

function applyReservations(issueRequest) {
  const allocationsByLine = []
  for (const line of issueRequest.lines || []) {
    const result = allocateLine(line)
    if (result.error) return result
    allocationsByLine.push({ line, allocations: result.allocations })
  }
  const nextReservationId = nextMockId()
  const reservations = allocationsByLine.map(({ line, allocations }, index) => {
    const reservationId = nextReservationId + index
    const allocationRows = allocations.map((allocation, allocationIndex) => (
      mockAllocation(nextMockId() + allocationIndex, reservationId, allocation.batchId, allocation.quantity, allocation.unitCostSnapshot)
    ))
    return mockReservation(
      reservationId,
      issueRequest.id,
      line.id,
      line.componentId,
      line.requestedQuantity,
      RESERVATION_STATUS.ACTIVE,
      issueRequest.expiresAt,
      allocationRows
    )
  })
  reservations.forEach((reservation) => {
    const balance = balanceOf(reservation.componentId)
    balance.reservedQuantity = round(Number(balance.reservedQuantity || 0) + Number(reservation.quantity), QUANTITY_SCALE)
    balance.version += 1
    balance.updateTime = nowIso()
    reservation.allocations.forEach((allocation) => {
      const batch = findMockBatch(allocation.batchId)
      batch.reservedQuantity = round(Number(batch.reservedQuantity || 0) + Number(allocation.quantity), QUANTITY_SCALE)
    })
    const line = issueRequest.lines.find((item) => item.id === reservation.requestLineId)
    line.reservedQuantity = reservation.quantity
    line.allocations = clone(reservation.allocations)
  })
  issueRequest.reservations = reservations
  return { reservations }
}

function estimatedAmountOf(issueRequest) {
  let amount = 0
  ;(issueRequest.reservations || []).forEach((reservation) => {
    ;(reservation.allocations || []).forEach((allocation) => {
      amount += Number(allocation.quantity || 0) * Number(allocation.unitCostSnapshot || 0)
    })
  })
  return round(amount, AMOUNT_SCALE)
}

function evaluateApproval(issueRequest, policy) {
  const reasons = []
  const estimatedAmount = estimatedAmountOf(issueRequest)
  if (estimatedAmount >= Number(policy.directAmountThreshold)) reasons.push(APPROVAL_REASON.AMOUNT_THRESHOLD)

  let valuable = false
  let forcedCategory = false
  let quantityExceeded = false
  const forced = new Set((policy.forcedCategoryIds || []).map(Number))

  issueRequest.lines.forEach((line) => {
    const component = findMockComponent(line.componentId)
    const override = (policy.componentOverrides || []).find((item) => Number(item.componentId) === Number(line.componentId))
    const limit = override ? Number(override.directQuantityLimit) : Number(policy.defaultDirectQuantityLimit)
    if (Number(line.requestedQuantity) > limit) quantityExceeded = true
    if (component && forced.has(Number(component.categoryId))) forcedCategory = true
    const reservation = (issueRequest.reservations || []).find((item) => item.requestLineId === line.id)
    ;(reservation?.allocations || []).forEach((allocation) => {
      if (Number(allocation.unitCostSnapshot) >= Number(policy.valuableUnitCostThreshold)) valuable = true
    })
  })

  if (valuable) reasons.push(APPROVAL_REASON.VALUABLE_BATCH)
  if (forcedCategory) reasons.push(APPROVAL_REASON.FORCED_CATEGORY)
  if (quantityExceeded) reasons.push(APPROVAL_REASON.QUANTITY_THRESHOLD)

  const ingredients = new Set(issueRequest.lines.map((line) => Number(line.componentId)))
  let lowStock = false
  ingredients.forEach((componentId) => {
    const balance = findMockBalance(componentId)
    if (!balance || Number(balance.onHandQuantity || 0) <= 0) return
    const ratio = (availableOf(balance) / Number(balance.onHandQuantity)) * 100
    if (ratio < Number(policy.lowStockRatio)) lowStock = true
  })
  if (lowStock) reasons.push(APPROVAL_REASON.LOW_STOCK)
  if (policy.manualApprovalRequired) reasons.push(APPROVAL_REASON.POLICY_REQUIRED)

  return {
    approvalRequired: reasons.length > 0,
    approvalReasons: reasons.length ? reasons : [APPROVAL_REASON.EXEMPT],
    triggerSnapshot: {
      policyVersion: policy.policyVersion,
      directAmountThreshold: policy.directAmountThreshold,
      valuableUnitCostThreshold: policy.valuableUnitCostThreshold,
      defaultDirectQuantityLimit: policy.defaultDirectQuantityLimit,
      lowStockRatio: policy.lowStockRatio,
      estimatedAmount,
      reasons: reasons.length ? reasons : [APPROVAL_REASON.EXEMPT]
    }
  }
}

// ========== 申请命令辅助 ==========

function versionConflict(currentVersion) {
  return errorWithTrace(409, '数据已被他人更新，请刷新后重新确认', 'VERSION_CONFLICT', { currentVersion })
}

function invalidTransition(status, action) {
  return errorWithTrace(409, `当前状态 ${status} 不允许执行 ${action}`, 'INVENTORY_ISSUE_INVALID_TRANSITION', { status, action })
}

function assertVersion(issueRequest, payload, field = 'version') {
  if (payload?.[field] != null && Number(payload[field]) !== issueRequest.version) {
    return versionConflict(issueRequest.version)
  }
  return null
}

function scenarioConflict() {
  if (isMockScenario('conflict')) {
    return errorWithTrace(409, '数据已被他人更新，请刷新后重新确认', 'VERSION_CONFLICT', { currentVersion: null })
  }
  return null
}

function validateRequestPayload(payload) {
  const title = String(payload?.title || '').trim()
  if (title.length < 2 || title.length > 120) {
    return errorWithTrace(400, '标题需为 2-120 个字符', 'INVALID_PARAMETER', { field: 'title' })
  }
  const purpose = String(payload?.purpose || '').trim()
  if (purpose.length < 2 || purpose.length > 500) {
    return errorWithTrace(400, '用途需为 2-500 个字符', 'INVALID_PARAMETER', { field: 'purpose' })
  }
  const lines = Array.isArray(payload?.lines) ? payload.lines : []
  if (!lines.length) {
    return errorWithTrace(400, '至少需要一条领用明细', 'ISSUE_LINES_REQUIRED', { field: 'lines' })
  }
  const seen = new Set()
  for (const line of lines) {
    const componentId = Number(line?.componentId)
    const quantity = Number(line?.requestedQuantity)
    if (!componentId || !findMockComponent(componentId) || findMockComponent(componentId).status !== 'ACTIVE') {
      return errorWithTrace(400, '元器件不存在或已停用', 'ISSUE_COMPONENT_INVALID', { field: 'componentId', componentId: line?.componentId })
    }
    if (!Number.isFinite(quantity) || quantity <= 0) {
      return errorWithTrace(400, '领用数量必须大于 0', 'ISSUE_QUANTITY_INVALID', { field: 'requestedQuantity', componentId })
    }
    if (seen.has(componentId)) {
      return errorWithTrace(400, '同一申请同一元器件只能有一行', 'INVALID_PARAMETER', { field: 'componentId', componentId })
    }
    seen.add(componentId)
  }
  return null
}

function applyRequestPayload(issueRequest, payload) {
  issueRequest.title = String(payload.title).trim()
  issueRequest.purpose = String(payload.purpose).trim()
  issueRequest.projectId = payload.projectId ? Number(payload.projectId) : null
  issueRequest.projectName = issueRequest.projectId ? (MOCK_PROJECT_NAMES[issueRequest.projectId] || `项目 ${issueRequest.projectId}`) : null
  issueRequest.lines = (payload.lines || []).map((line, index) => {
    const component = findMockComponent(line.componentId)
    return mockLine(
      nextMockId(),
      index + 1,
      Number(line.componentId),
      round(Number(line.requestedQuantity), QUANTITY_SCALE),
      { unit: component?.baseUnit || 'PCS' }
    )
  })
  return issueRequest
}

function requestNoFor(requestId) {
  const date = new Date()
  const stamp = `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, '0')}${String(date.getDate()).padStart(2, '0')}`
  return `ISS-${stamp}-${String(requestId).slice(-6).padStart(6, '0')}`
}

function requestSummaryList() {
  return [...MOCK_REQUESTS].sort((a, b) => {
    const left = a.updateTime || ''
    const right = b.updateTime || ''
    if (left === right) return b.id - a.id
    return left > right ? -1 : 1
  })
}

// ========== 策略 API ==========

export function getIssuePolicy() {
  if (!getMockEnabled()) return request.get('/api/inventory/issue-policy')
  if (!canAccessIssuance()) return errorWithTrace(403, '当前账号没有库存领用权限', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
  const scenario = currentMockScenarioState()
  if (scenario === 'permission') return errorWithTrace(403, '当前验收场景无策略访问权限', 'INVENTORY_POLICY_DENIED', null)
  if (scenario === 'error') return errorWithTrace(500, '领用策略加载失败', 'INTERNAL_ERROR', null)
  const run = () => clone(latestPolicy())
  return scenario === 'loading' ? mockDelay(1200).then(run) : Promise.resolve(run())
}

export function listIssuePolicyVersions(params = {}) {
  if (!getMockEnabled()) return request.get('/api/inventory/issue-policy/versions', { params })
  if (!canPublishIssuePolicy()) return errorWithTrace(403, '只有系统管理员可以查看策略版本', 'INVENTORY_POLICY_DENIED', null)
  if (isMockScenario('error')) return errorWithTrace(500, '策略版本加载失败', 'INTERNAL_ERROR', null)
  const run = () => pageOf(clone([...MOCK_POLICIES].reverse()), params)
  return isMockScenario('loading') ? mockDelay(1200).then(run) : Promise.resolve(run())
}

export function publishIssuePolicy(payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.put('/api/inventory/issue-policy', payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  if (!canPublishIssuePolicy()) return errorWithTrace(403, '只有系统管理员可以发布领用策略', 'INVENTORY_POLICY_DENIED', null)
  if (isMockScenario('permission')) return errorWithTrace(403, '当前验收场景无策略发布权限', 'INVENTORY_POLICY_DENIED', null)
  return withIdempotency('PUT /api/inventory/issue-policy', idempotencyKey, payload, () => {
    const conflict = scenarioConflict()
    if (conflict) return conflict
    const current = latestPolicy()
    if (Number(payload?.basePolicyVersion) !== current.policyVersion) {
      return versionConflict(current.policyVersion)
    }
    const numbers = ['directAmountThreshold', 'valuableUnitCostThreshold', 'defaultDirectQuantityLimit', 'lowStockRatio', 'reservationTtlHours']
    for (const field of numbers) {
      if (!Number.isFinite(Number(payload?.[field]))) {
        return errorWithTrace(400, '策略阈值必须为数字', 'ISSUE_POLICY_INVALID', { field })
      }
    }
    if (Number(payload.directAmountThreshold) <= 0 || Number(payload.valuableUnitCostThreshold) <= 0 || Number(payload.defaultDirectQuantityLimit) <= 0) {
      return errorWithTrace(400, '金额与数量阈值必须大于 0', 'ISSUE_POLICY_INVALID', { field: 'threshold' })
    }
    const ratio = Number(payload.lowStockRatio)
    if (ratio < 0 || ratio > 100) {
      return errorWithTrace(400, '库存紧张比例必须在 0-100 之间', 'ISSUE_POLICY_INVALID', { field: 'lowStockRatio' })
    }
    const ttl = Number(payload.reservationTtlHours)
    if (!Number.isInteger(ttl) || ttl < 1 || ttl > 168) {
      return errorWithTrace(400, '预占时长必须为 1-168 小时整数', 'ISSUE_POLICY_INVALID', { field: 'reservationTtlHours' })
    }
    const forced = Array.isArray(payload?.forcedCategoryIds) ? payload.forcedCategoryIds.map(Number) : []
    const overrides = Array.isArray(payload?.componentOverrides) ? payload.componentOverrides : []
    const seen = new Set()
    const normalizedOverrides = []
    for (const override of overrides) {
      const componentId = Number(override?.componentId)
      const component = findMockComponent(componentId)
      const limit = Number(override?.directQuantityLimit)
      if (!component || component.status !== 'ACTIVE') {
        return errorWithTrace(400, '覆盖项元器件不存在或已停用', 'ISSUE_POLICY_OVERRIDE_INVALID', { field: 'componentId', componentId: override?.componentId })
      }
      if (seen.has(componentId)) {
        return errorWithTrace(400, '同一元器件不能重复设置覆盖项', 'ISSUE_POLICY_OVERRIDE_INVALID', { field: 'componentId', componentId })
      }
      if (!Number.isFinite(limit) || limit <= 0) {
        return errorWithTrace(400, '覆盖数量上限必须大于 0', 'ISSUE_POLICY_OVERRIDE_INVALID', { field: 'directQuantityLimit', componentId })
      }
      seen.add(componentId)
      normalizedOverrides.push({
        id: nextMockId(),
        componentId,
        directQuantityLimit: round(limit, QUANTITY_SCALE),
        componentName: component.name,
        unitSnapshot: component.baseUnit
      })
    }
    const next = {
      id: current.id + 1,
      policyVersion: current.policyVersion + 1,
      currency: 'CNY',
      directAmountThreshold: round(payload.directAmountThreshold, AMOUNT_SCALE),
      valuableUnitCostThreshold: round(payload.valuableUnitCostThreshold, UNIT_COST_SCALE),
      defaultDirectQuantityLimit: round(payload.defaultDirectQuantityLimit, QUANTITY_SCALE),
      lowStockRatio: round(ratio, 2),
      reservationTtlHours: ttl,
      manualApprovalRequired: Boolean(payload.manualApprovalRequired),
      forcedCategoryIds: forced,
      componentOverrides: normalizedOverrides,
      effectiveAt: nowIso(),
      createdBy: currentUserId() ?? 1,
      createTime: nowIso()
    }
    MOCK_POLICIES.push(next)
    return clone(next)
  })
}

// ========== 元器件选择与库存查询 ==========

export function searchIssueComponents(params = {}) {
  if (!getMockEnabled()) {
    return request.get('/api/components', {
      params: { status: 'ACTIVE', page: 1, pageSize: 20, keyword: params.keyword || '' }
    })
  }
  const keyword = String(params.keyword || '').trim().toLowerCase()
  const list = MOCK_COMPONENTS
    .filter((component) => component.status === 'ACTIVE')
    .filter((component) => !keyword
      || component.name.toLowerCase().includes(keyword)
      || component.mpn.toLowerCase().includes(keyword)
      || component.manufacturerName.toLowerCase().includes(keyword))
    .map((component) => {
      const balance = findMockBalance(component.id)
      return {
        id: component.id,
        name: component.name,
        mpn: component.mpn,
        manufacturerName: component.manufacturerName,
        baseUnit: component.baseUnit,
        categoryId: component.categoryId,
        categoryName: component.categoryName,
        status: component.status,
        onHandQuantity: round(balance?.onHandQuantity || 0, QUANTITY_SCALE),
        reservedQuantity: round(balance?.reservedQuantity || 0, QUANTITY_SCALE),
        availableQuantity: balance ? availableOf(balance) : 0
      }
    })
  return Promise.resolve({ list, total: list.length, page: 1, pageSize: 20 })
}

export function getInventoryComponentSummary(componentId) {
  if (!getMockEnabled()) return request.get(`/api/inventory/components/${componentId}`)
  if (!canAccessIssuance()) return errorWithTrace(403, '当前账号没有库存访问权限', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
  const scenario = currentMockScenarioState()
  if (scenario === 'error') return errorWithTrace(500, '库存汇总加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    const component = findMockComponent(componentId)
    const balance = findMockBalance(componentId)
    return {
      componentId: Number(componentId),
      componentName: component?.name || `元器件 ${componentId}`,
      unit: balance?.unit || component?.baseUnit || 'PCS',
      onHandQuantity: round(balance?.onHandQuantity || 0, QUANTITY_SCALE),
      reservedQuantity: round(balance?.reservedQuantity || 0, QUANTITY_SCALE),
      availableQuantity: balance ? availableOf(balance) : 0,
      version: balance?.version || 0,
      updateTime: balance?.updateTime || null
    }
  }
  return scenario === 'loading' ? mockDelay(1000).then(run) : Promise.resolve(run())
}

export function listInventoryComponentBatches(componentId, params = {}) {
  if (!getMockEnabled()) return request.get(`/api/inventory/components/${componentId}/batches`, { params })
  if (!canViewIssueBatches()) {
    return errorWithTrace(403, '普通成员不能查看批次明细', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
  }
  if (isMockScenario('error')) return errorWithTrace(500, '库存批次加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    if (isMockScenario('empty')) return pageOf([], params)
    const list = MOCK_BATCHES
      .filter((batch) => batch.componentId === Number(componentId))
      .filter((batch) => !params.batchNo || String(batch.batchNo).includes(params.batchNo))
      .map((batch) => {
        const row = {
          id: batch.id,
          batchNo: batch.batchNo,
          componentId: batch.componentId,
          currentQuantity: round(batch.currentQuantity, QUANTITY_SCALE),
          reservedQuantity: round(batch.reservedQuantity, QUANTITY_SCALE),
          availableQuantity: batchAvailable(batch),
          expiryDate: batch.expiryDate,
          receivedAt: batch.receivedAt,
          locationNote: batch.locationNote
        }
        if (canViewIssueCost()) {
          row.unitCost = batch.unitCost
          row.sourceReceiptNo = batch.sourceReceiptNo
        }
        return row
      })
    return pageOf(list, params)
  }
  return isMockScenario('loading') ? mockDelay(1200).then(run) : Promise.resolve(run())
}

export function listInventoryMovements(params = {}) {
  if (!getMockEnabled()) return request.get('/api/inventory/movements', { params })
  if (!canAccessIssuance()) return errorWithTrace(403, '当前账号没有库存流水权限', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
  if (isMockScenario('error')) return errorWithTrace(500, '库存流水加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    if (isMockScenario('empty')) return pageOf([], params)
    let list = [...MOCK_MOVEMENTS]
    if (!canViewInventoryMovements()) {
      const ownRequestIds = new Set(MOCK_REQUESTS.filter((item) => isIssueRequester(item)).map((item) => item.id))
      list = list.filter((movement) => ownRequestIds.has(Number(movement.requestId)))
    }
    if (params.movementType) list = list.filter((movement) => movement.movementType === params.movementType)
    if (params.componentId) list = list.filter((movement) => movement.componentId === Number(params.componentId))
    if (params.operatorId) list = list.filter((movement) => Number(movement.operatorId) === Number(params.operatorId))
    if (params.sourceType) list = list.filter((movement) => movement.sourceType === params.sourceType)
    if (params.batchNo) list = list.filter((movement) => String(movement.batchNo).includes(params.batchNo))
    if (params.from) list = list.filter((movement) => movement.occurredAt >= params.from)
    if (params.to) list = list.filter((movement) => movement.occurredAt <= params.to)
    if (params.requestId) list = list.filter((movement) => Number(movement.requestId) === Number(params.requestId))
    list = list.sort((a, b) => (a.occurredAt > b.occurredAt ? -1 : 1)).map(normalizeMovement)
    return pageOf(list, params)
  }
  return isMockScenario('loading') ? mockDelay(1200).then(run) : Promise.resolve(run())
}

// ========== 领用申请查询 ==========

export function listIssueRequests(params = {}) {
  if (!getMockEnabled()) return request.get('/api/inventory-issue-requests', { params })
  if (!canAccessIssuance()) return errorWithTrace(403, '当前账号没有库存领用权限', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
  const scenario = currentMockScenarioState()
  if (scenario === 'permission') return errorWithTrace(403, '当前验收场景无库存领用权限', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
  if (scenario === 'error') return errorWithTrace(500, '领用申请列表加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    refreshExpiredRequests()
    if (scenario === 'empty') return pageOf([], params)
    let list = requestSummaryList().filter(requestVisible)
    if (params.status) list = list.filter((item) => item.status === params.status)
    if (params.requesterId) list = list.filter((item) => Number(item.requesterId) === Number(params.requesterId))
    if (params.projectId) list = list.filter((item) => Number(item.projectId) === Number(params.projectId))
    if (params.componentId) {
      list = list.filter((item) => (item.lines || []).some((line) => Number(line.componentId) === Number(params.componentId)))
    }
    if (params.from) list = list.filter((item) => item.submittedAt && item.submittedAt >= params.from)
    if (params.to) list = list.filter((item) => item.submittedAt && item.submittedAt <= params.to)
    const keyword = String(params.keyword || '').trim().toLowerCase()
    if (keyword) {
      list = list.filter((item) => [item.requestNo, item.title, item.purpose]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword)))
    }
    const allowedSort = ['createTime', 'updateTime', 'submittedAt', 'expiresAt', 'requestNo', 'status']
    const [sortField, direction] = String(params.sort || 'updateTime,desc').split(',')
    const field = allowedSort.includes(sortField) ? sortField : 'updateTime'
    list = [...list].sort((a, b) => {
      const left = a[field] ?? ''
      const right = b[field] ?? ''
      const result = left > right ? 1 : left < right ? -1 : 0
      return direction === 'asc' ? result : -result
    })
    return pageOf(list.map(publicIssueSummary), params)
  }
  return scenario === 'loading' ? mockDelay(1400).then(run) : Promise.resolve(run())
}

export function getIssueRequest(requestId) {
  if (!getMockEnabled()) return request.get(`/api/inventory-issue-requests/${requestId}`)
  if (!canAccessIssuance()) return errorWithTrace(403, '当前账号没有库存领用权限', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
  const scenario = currentMockScenarioState()
  if (scenario === 'permission') return requestVisibilityError()
  if (scenario === 'error') return errorWithTrace(500, '领用申请详情加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    const issueRequest = requireVisibleRequest(requestId)
    if (!issueRequest) return requestVisibilityError()
    if (scenario === 'expired') {
      releaseReservations(issueRequest, RELEASE_REASON.EXPIRED)
      issueRequest.status = ISSUE_STATUS.EXPIRED
      issueRequest.expiresAt = '2026-11-20T10:00:00+08:00'
    } else {
      expireIssuedReservation(issueRequest)
    }
    return publicIssueDetail(issueRequest)
  }
  return scenario === 'loading' ? mockDelay(1400).then(run) : Promise.resolve(run())
}

export function createIssueRequest(payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post('/api/inventory-issue-requests', payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency('POST /api/inventory-issue-requests', idempotencyKey, payload, () => {
    const invalid = validateRequestPayload(payload)
    if (invalid) return invalid
    const id = nextMockId()
    const issueRequest = {
      id,
      requestNo: requestNoFor(id),
      title: '',
      purpose: '',
      requesterId: currentUserId() ?? 2,
      requesterName: userName(currentUserId() ?? 2),
      projectId: null,
      projectName: null,
      status: ISSUE_STATUS.DRAFT,
      approvalRequired: null,
      approvalReasons: [],
      policyVersion: null,
      estimatedAmount: null,
      submittedAt: null,
      approvedAt: null,
      issuedAt: null,
      expiresAt: null,
      version: 1,
      createTime: nowIso(),
      updateTime: nowIso(),
      updatedBy: currentUserId() ?? 2,
      lines: [],
      approvalRecords: [],
      reservations: [],
      fulfillment: null,
      returns: []
    }
    applyRequestPayload(issueRequest, payload)
    MOCK_REQUESTS.push(issueRequest)
    return publicIssueDetail(issueRequest)
  })
}

export function updateIssueRequest(requestId, payload) {
  if (!getMockEnabled()) return request.put(`/api/inventory-issue-requests/${requestId}`, payload)
  const guard = writeGuard()
  if (guard) return guard
  const issueRequest = requireVisibleRequest(requestId)
  if (!issueRequest) return requestVisibilityError()
  if (!canEditIssueRequest(issueRequest)) {
    return errorWithTrace(403, '仅 DRAFT 申请人或系统管理员可编辑', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
  }
  const conflict = scenarioConflict()
  if (conflict) return conflict
  const stale = assertVersion(issueRequest, payload)
  if (stale) return stale
  const invalid = validateRequestPayload(payload)
  if (invalid) return invalid
  applyRequestPayload(issueRequest, payload)
  issueRequest.version += 1
  issueRequest.updateTime = nowIso()
  issueRequest.updatedBy = currentUserId() ?? issueRequest.updatedBy
  return publicIssueDetail(issueRequest)
}

export function submitIssueRequest(requestId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/inventory-issue-requests/${requestId}/submit`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const issueRequest = requireVisibleRequest(requestId)
  if (!issueRequest) return requestVisibilityError()
  return withIdempotency(`POST /api/inventory-issue-requests/${requestId}/submit`, idempotencyKey, payload, () => {
    const guard = writeGuard()
    if (guard) return guard
    if (!canSubmitIssueRequest(issueRequest)) {
      return errorWithTrace(403, '只有申请人或系统管理员可提交本人草稿', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
    }
    if (issueRequest.status !== ISSUE_STATUS.DRAFT) return invalidTransition(issueRequest.status, 'submit')
    const conflict = scenarioConflict()
    if (conflict) return conflict
    const stale = assertVersion(issueRequest, payload, 'requestVersion')
    if (stale) return stale

    const policy = latestPolicy()
    issueRequest.expiresAt = addHoursIso(nowIso(), policy.reservationTtlHours)
    const applied = applyReservations(issueRequest)
    if (applied.error) {
      issueRequest.expiresAt = null
      return applied.error
    }
    if (isMockScenario('insufficient-stock')) {
      releaseReservations(issueRequest, RELEASE_REASON.CANCELED)
      issueRequest.reservations = []
      issueRequest.expiresAt = null
      return errorWithTrace(409, '可用库存不足', 'INVENTORY_INSUFFICIENT_STOCK', {
        componentId: issueRequest.lines[0].componentId,
        requestedQuantity: issueRequest.lines[0].requestedQuantity,
        availableQuantity: Math.max(0, Number(issueRequest.lines[0].requestedQuantity) - 3)
      })
    }

    const verdict = evaluateApproval(issueRequest, policy)
    issueRequest.policyVersion = policy.policyVersion
    issueRequest.estimatedAmount = verdict.triggerSnapshot.estimatedAmount
    issueRequest.approvalRequired = verdict.approvalRequired
    issueRequest.approvalReasons = verdict.approvalReasons
    issueRequest.submittedAt = nowIso()
    issueRequest.status = verdict.approvalRequired ? ISSUE_STATUS.PENDING_APPROVAL : ISSUE_STATUS.APPROVED
    if (!verdict.approvalRequired) issueRequest.approvedAt = issueRequest.submittedAt
    if (!verdict.approvalRequired) {
      issueRequest.approvalRecords.push({
        id: nextMockId(),
        requestId: issueRequest.id,
        decision: APPROVAL_DECISION.AUTO_APPROVED,
        actorType: 'SYSTEM',
        decidedBy: null,
        decidedAt: issueRequest.submittedAt,
        policyVersion: policy.policyVersion,
        triggerSnapshot: verdict.triggerSnapshot,
        comment: null,
        requestIdempotencyId: idempotencyKey
      })
    }
    issueRequest.version += 1
    issueRequest.updateTime = issueRequest.submittedAt
    issueRequest.updatedBy = currentUserId() ?? issueRequest.updatedBy
    return publicIssueDetail(issueRequest)
  })
}

export function approveIssueRequest(requestId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/inventory-issue-requests/${requestId}/approve`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const issueRequest = requireVisibleRequest(requestId)
  if (!issueRequest) return requestVisibilityError()
  return withIdempotency(`POST /api/inventory-issue-requests/${requestId}/approve`, idempotencyKey, payload, () => {
    if (isMockScenario('permission')) return errorWithTrace(403, '当前验收场景无审批权限', 'INVENTORY_ISSUE_APPROVAL_DENIED', null)
    if (issueRequest.status !== ISSUE_STATUS.PENDING_APPROVAL) return invalidTransition(issueRequest.status, 'approve')
    if (isIssueRequester(issueRequest)) {
      return errorWithTrace(403, '申请人不能审批自己的申请', 'INVENTORY_ISSUE_SELF_APPROVAL', null)
    }
    if (!(has(ROLE.TEACHER) || has(ROLE.SYSTEM_ADMIN))) {
      return errorWithTrace(403, '只有老师或系统管理员可以审批', 'INVENTORY_ISSUE_APPROVAL_DENIED', null)
    }
    const conflict = scenarioConflict()
    if (conflict) return conflict
    const stale = assertVersion(issueRequest, payload, 'requestVersion')
    if (stale) return stale
    if (expireIssuedReservation(issueRequest)) {
      return errorWithTrace(409, '预占已过期，无法审批', 'INVENTORY_RESERVATION_EXPIRED', null)
    }
    issueRequest.status = ISSUE_STATUS.APPROVED
    issueRequest.approvedAt = nowIso()
    const policy = policyForVersion(issueRequest.policyVersion) || latestPolicy()
    issueRequest.approvalRecords.push({
      id: nextMockId(),
      requestId: issueRequest.id,
      decision: APPROVAL_DECISION.APPROVED,
      actorType: 'USER',
      decidedBy: currentUserId(),
      decidedAt: issueRequest.approvedAt,
      policyVersion: policy.policyVersion,
      triggerSnapshot: { estimatedAmount: issueRequest.estimatedAmount, reasons: issueRequest.approvalReasons },
      comment: payload?.comment || null,
      requestIdempotencyId: idempotencyKey
    })
    issueRequest.version += 1
    issueRequest.updateTime = issueRequest.approvedAt
    issueRequest.updatedBy = currentUserId() ?? issueRequest.updatedBy
    return publicIssueDetail(issueRequest)
  })
}

export function rejectIssueRequest(requestId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/inventory-issue-requests/${requestId}/reject`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const issueRequest = requireVisibleRequest(requestId)
  if (!issueRequest) return requestVisibilityError()
  return withIdempotency(`POST /api/inventory-issue-requests/${requestId}/reject`, idempotencyKey, payload, () => {
    if (isMockScenario('permission')) return errorWithTrace(403, '当前验收场景无审批权限', 'INVENTORY_ISSUE_APPROVAL_DENIED', null)
    if (issueRequest.status !== ISSUE_STATUS.PENDING_APPROVAL) return invalidTransition(issueRequest.status, 'reject')
    if (isIssueRequester(issueRequest)) {
      return errorWithTrace(403, '申请人不能审批自己的申请', 'INVENTORY_ISSUE_SELF_APPROVAL', null)
    }
    if (!(has(ROLE.TEACHER) || has(ROLE.SYSTEM_ADMIN))) {
      return errorWithTrace(403, '只有老师或系统管理员可以拒绝', 'INVENTORY_ISSUE_APPROVAL_DENIED', null)
    }
    const comment = String(payload?.comment || '').trim()
    if (!comment) return errorWithTrace(400, '拒绝时必须填写 1-500 字原因', 'INVALID_PARAMETER', { field: 'comment' })
    const conflict = scenarioConflict()
    if (conflict) return conflict
    const stale = assertVersion(issueRequest, payload, 'requestVersion')
    if (stale) return stale
    releaseReservations(issueRequest, RELEASE_REASON.REJECTED)
    issueRequest.status = ISSUE_STATUS.REJECTED
    issueRequest.approvalRecords.push({
      id: nextMockId(),
      requestId: issueRequest.id,
      decision: APPROVAL_DECISION.REJECTED,
      actorType: 'USER',
      decidedBy: currentUserId(),
      decidedAt: nowIso(),
      policyVersion: issueRequest.policyVersion || latestPolicy().policyVersion,
      triggerSnapshot: { estimatedAmount: issueRequest.estimatedAmount, reasons: issueRequest.approvalReasons },
      comment,
      requestIdempotencyId: idempotencyKey
    })
    issueRequest.version += 1
    issueRequest.updateTime = nowIso()
    issueRequest.updatedBy = currentUserId() ?? issueRequest.updatedBy
    return publicIssueDetail(issueRequest)
  })
}

export function cancelIssueRequest(requestId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/inventory-issue-requests/${requestId}/cancel`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const issueRequest = requireVisibleRequest(requestId)
  if (!issueRequest) return requestVisibilityError()
  return withIdempotency(`POST /api/inventory-issue-requests/${requestId}/cancel`, idempotencyKey, payload, () => {
    const guard = writeGuard()
    if (guard) return guard
    if (!canCancelIssueRequest(issueRequest)) {
      return errorWithTrace(403, '当前账号或状态不允许取消该申请', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
    }
    const conflict = scenarioConflict()
    if (conflict) return conflict
    const stale = assertVersion(issueRequest, payload, 'requestVersion')
    if (stale) return stale
    if ([ISSUE_STATUS.PENDING_APPROVAL, ISSUE_STATUS.APPROVED].includes(issueRequest.status)) {
      releaseReservations(issueRequest, RELEASE_REASON.CANCELED)
    }
    issueRequest.status = ISSUE_STATUS.CANCELED
    issueRequest.version += 1
    issueRequest.updateTime = nowIso()
    issueRequest.updatedBy = currentUserId() ?? issueRequest.updatedBy
    return publicIssueDetail(issueRequest)
  })
}

export function reallocateIssueRequest(requestId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/inventory-issue-requests/${requestId}/reallocate`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const issueRequest = requireVisibleRequest(requestId)
  if (!issueRequest) return requestVisibilityError()
  return withIdempotency(`POST /api/inventory-issue-requests/${requestId}/reallocate`, idempotencyKey, payload, () => {
    if (!canReallocateIssueRequest(issueRequest)) {
      return errorWithTrace(403, '只有库存管理员可重分配已批准申请的批次', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
    }
    const reason = String(payload?.reason || '').trim()
    if (!reason) return errorWithTrace(400, '批次重分配必须填写原因', 'INVALID_PARAMETER', { field: 'reason' })
    const conflict = scenarioConflict()
    if (conflict) return conflict
    const stale = assertVersion(issueRequest, payload, 'requestVersion')
    if (stale) return stale
    const allocations = Array.isArray(payload?.allocations) ? payload.allocations : []
    if (!allocations.length) return errorWithTrace(400, '至少需要一条批次分配', 'INVALID_PARAMETER', { field: 'allocations' })

    const snapshot = clone({ balances: MOCK_BALANCES, batches: MOCK_BATCHES, reservations: issueRequest.reservations })
    releaseReservations(issueRequest, RELEASE_REASON.REALLOCATED)
    issueRequest.reservations = []

    const grouped = new Map()
    for (const allocation of allocations) {
      const lineId = Number(allocation?.requestLineId)
      const batchId = Number(allocation?.batchId)
      const quantity = Number(allocation?.quantity)
      const line = issueRequest.lines.find((item) => Number(item.id) === lineId)
      if (!line || !batchId || !Number.isFinite(quantity) || quantity <= 0) {
        MOCK_BALANCES.splice(0, MOCK_BALANCES.length, ...snapshot.balances)
        MOCK_BATCHES.splice(0, MOCK_BATCHES.length, ...snapshot.batches)
        issueRequest.reservations = snapshot.reservations
        return errorWithTrace(400, '批次分配参数不合法', 'INVALID_PARAMETER', { field: 'allocations' })
      }
      if (!grouped.has(lineId)) grouped.set(lineId, [])
      grouped.get(lineId).push({ batchId, quantity: round(quantity, QUANTITY_SCALE) })
    }

    const nextReservationId = nextMockId()
    const reservations = []
    let index = 0
    for (const line of issueRequest.lines) {
      const rows = grouped.get(Number(line.id)) || []
      const total = round(rows.reduce((sum, row) => sum + row.quantity, 0), QUANTITY_SCALE)
      if (total !== Number(line.requestedQuantity)) {
        MOCK_BALANCES.splice(0, MOCK_BALANCES.length, ...snapshot.balances)
        MOCK_BATCHES.splice(0, MOCK_BATCHES.length, ...snapshot.batches)
        issueRequest.reservations = snapshot.reservations
        return errorWithTrace(400, '每条明细的分配数量必须等于申请量', 'INVALID_PARAMETER', { field: 'allocations', requestLineId: line.id })
      }
      const reservationId = nextReservationId + index
      index += 1
      const allocationRows = rows.map((row, rowIndex) => {
        const batch = findMockBatch(row.batchId)
        if (!batch || batch.componentId !== Number(line.componentId) || batchAvailable(batch) < row.quantity || isBatchExpired(batch)) {
          return null
        }
        return mockAllocation(nextMockId() + rowIndex, reservationId, row.batchId, row.quantity, round(batch.unitCost, UNIT_COST_SCALE))
      })
      if (allocationRows.some((row) => !row)) {
        MOCK_BALANCES.splice(0, MOCK_BALANCES.length, ...snapshot.balances)
        MOCK_BATCHES.splice(0, MOCK_BATCHES.length, ...snapshot.batches)
        issueRequest.reservations = snapshot.reservations
        return errorWithTrace(409, '批次库存已变化，请刷新后重新分配', 'INVENTORY_BATCH_ALLOCATION_CONFLICT', { requestLineId: line.id })
      }
      reservations.push(mockReservation(
        reservationId,
        issueRequest.id,
        line.id,
        line.componentId,
        line.requestedQuantity,
        RESERVATION_STATUS.ACTIVE,
        issueRequest.expiresAt,
        allocationRows,
        { version: 2 }
      ))
    }
    reservations.forEach((reservation) => {
      const balance = balanceOf(reservation.componentId)
      balance.reservedQuantity = round(Number(balance.reservedQuantity || 0) + Number(reservation.quantity), QUANTITY_SCALE)
      balance.version += 1
      reservation.allocations.forEach((allocation) => {
        const batch = findMockBatch(allocation.batchId)
        batch.reservedQuantity = round(Number(batch.reservedQuantity || 0) + Number(allocation.quantity), QUANTITY_SCALE)
      })
      const line = issueRequest.lines.find((item) => item.id === reservation.requestLineId)
      line.reservedQuantity = reservation.quantity
      line.allocations = clone(reservation.allocations)
    })
    issueRequest.reservations = reservations
    issueRequest.estimatedAmount = estimatedAmountOf(issueRequest)
    issueRequest.version += 1
    issueRequest.updateTime = nowIso()
    issueRequest.updatedBy = currentUserId() ?? issueRequest.updatedBy
    return publicIssueDetail(issueRequest)
  })
}

export function confirmIssueRequest(requestId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/inventory-issue-requests/${requestId}/confirm-issue`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const issueRequest = requireVisibleRequest(requestId)
  if (!issueRequest) return requestVisibilityError()
  return withIdempotency(`POST /api/inventory-issue-requests/${requestId}/confirm-issue`, idempotencyKey, payload, () => {
    if (issueRequest.status === ISSUE_STATUS.ISSUED) {
      return errorWithTrace(409, '该申请已发放，不能重复确认', 'INVENTORY_ISSUE_ALREADY_CONFIRMED', { requestId: issueRequest.id })
    }
    if (!canConfirmIssue(issueRequest)) {
      return errorWithTrace(403, '只有非申请人的库存管理员可以确认发放', 'INVENTORY_ISSUE_FULFILL_DENIED', null)
    }
    if (issueRequest.status !== ISSUE_STATUS.APPROVED) return invalidTransition(issueRequest.status, 'confirm-issue')
    const conflict = scenarioConflict()
    if (conflict) return conflict
    const staleRequest = assertVersion(issueRequest, payload, 'requestVersion')
    if (staleRequest) return staleRequest
    const activeReservations = (issueRequest.reservations || []).filter((item) => item.status === RESERVATION_STATUS.ACTIVE)
    if (!activeReservations.length) {
      return errorWithTrace(409, '预占已消费或释放', 'INVENTORY_RESERVATION_NOT_ACTIVE', null)
    }
    if (payload?.reservationVersion != null && activeReservations.some((item) => Number(item.version) !== Number(payload.reservationVersion))) {
      return errorWithTrace(409, '预占版本已变化，请刷新后重新确认', 'VERSION_CONFLICT', { currentVersion: activeReservations[0].version })
    }
    if (expireIssuedReservation(issueRequest)) {
      return errorWithTrace(409, '预占已过期，无法发放', 'INVENTORY_RESERVATION_EXPIRED', null)
    }
    if (isMockScenario('transaction-failed')) {
      return errorWithTrace(500, '发放失败，库存未变化', 'INVENTORY_ISSUE_TRANSACTION_FAILED', { retryable: true })
    }

    const fulfillmentId = nextMockId()
    const fulfillmentLines = []
    const movements = []
    const allocatedByComponent = new Map()
    activeReservations.forEach((reservation) => {
      ;(reservation.allocations || []).forEach((allocation) => {
        allocatedByComponent.set(reservation.componentId, round(
          Number(allocatedByComponent.get(reservation.componentId) || 0) + Number(allocation.quantity),
          QUANTITY_SCALE
        ))
      })
    })
    const balanceBeforeMap = new Map()
    allocatedByComponent.forEach((_quantity, componentId) => {
      balanceBeforeMap.set(componentId, Number(findMockBalance(componentId)?.onHandQuantity || 0))
    })

    activeReservations.forEach((reservation) => {
      reservation.allocations.forEach((allocation) => {
        const batch = findMockBatch(allocation.batchId)
        const balance = findMockBalance(reservation.componentId)
        const quantity = Number(allocation.quantity)
        batch.currentQuantity = round(Number(batch.currentQuantity) - quantity, QUANTITY_SCALE)
        batch.reservedQuantity = round(Math.max(0, Number(batch.reservedQuantity) - quantity), QUANTITY_SCALE)
        const before = Number(balance.onHandQuantity)
        balance.onHandQuantity = round(before - quantity, QUANTITY_SCALE)
        balance.reservedQuantity = round(Math.max(0, Number(balance.reservedQuantity) - quantity), QUANTITY_SCALE)
        balance.version += 1
        balance.updateTime = nowIso()
        const movementId = nextMockId()
        const movement = {
          movementId,
          movementType: MOVEMENT_TYPE.ISSUE_OUT,
          componentId: reservation.componentId,
          batchId: allocation.batchId,
          batchNo: batch.batchNo,
          quantityDelta: -quantity,
          balanceBefore: before,
          balanceAfter: balance.onHandQuantity,
          batchBefore: round(Number(batch.currentQuantity) + quantity, QUANTITY_SCALE),
          batchAfter: batch.currentQuantity,
          operatorId: currentUserId(),
          operatorName: userName(currentUserId()),
          sourceType: 'FULFILLMENT_LINE',
          sourceId: null,
          reason: null,
          requestId: issueRequest.id,
          occurredAt: nowIso()
        }
        MOCK_MOVEMENTS.push(movement)
        movements.push(movement)
        const fulfillmentLineId = nextMockId()
        movement.sourceId = fulfillmentLineId
        fulfillmentLines.push({
          id: fulfillmentLineId,
          fulfillmentId,
          requestLineId: reservation.requestLineId,
          reservationAllocationId: allocation.id,
          batchId: allocation.batchId,
          quantity,
          unitCostSnapshot: allocation.unitCostSnapshot,
          movementId
        })
      })
      reservation.status = RESERVATION_STATUS.CONSUMED
      reservation.version += 1
      const line = issueRequest.lines.find((item) => item.id === reservation.requestLineId)
      line.issuedQuantity = reservation.quantity
      line.reservedQuantity = 0
    })

    const issuedAt = nowIso()
    issueRequest.fulfillment = {
      id: fulfillmentId,
      fulfillmentNo: `FUL-20261123-${String(fulfillmentId).slice(-6).padStart(6, '0')}`,
      requestId: issueRequest.id,
      operatorId: currentUserId(),
      operatorName: userName(currentUserId()),
      recipientId: issueRequest.requesterId,
      recipientName: issueRequest.requesterName,
      issuedAt,
      note: payload?.note || null,
      lines: fulfillmentLines
    }
    issueRequest.status = ISSUE_STATUS.ISSUED
    issueRequest.issuedAt = issuedAt
    issueRequest.version += 1
    issueRequest.updateTime = issuedAt
    issueRequest.updatedBy = currentUserId() ?? issueRequest.updatedBy

    const detail = publicIssueDetail(issueRequest)
    detail.fulfillmentId = fulfillmentId
    detail.movements = movements.map(normalizeMovement)
    return detail
  })
}

// ========== 退回 ==========

export function listFulfillmentReturns(fulfillmentId, params = {}) {
  if (!getMockEnabled()) return request.get(`/api/inventory-fulfillments/${fulfillmentId}/returns`, { params })
  if (!canAccessIssuance()) return errorWithTrace(403, '当前账号没有退回访问权限', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
  if (isMockScenario('error')) return errorWithTrace(500, '退回记录加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    if (isMockScenario('empty')) return pageOf([], params)
    const owner = MOCK_REQUESTS.find((item) => item.fulfillment?.id === Number(fulfillmentId))
    if (!owner || !requestVisible(owner)) {
      return errorWithTrace(404, '发放记录不存在或不可见', 'INVENTORY_FULFILLMENT_NOT_FOUND', null)
    }
    return pageOf(clone(owner.returns || []), params)
  }
  return isMockScenario('loading') ? mockDelay(1200).then(run) : Promise.resolve(run())
}

export function createFulfillmentReturn(fulfillmentId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/inventory-fulfillments/${fulfillmentId}/returns`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/inventory-fulfillments/${fulfillmentId}/returns`, idempotencyKey, payload, () => {
    const owner = MOCK_REQUESTS.find((item) => item.fulfillment?.id === Number(fulfillmentId))
    if (!owner || !requestVisible(owner)) {
      return errorWithTrace(404, '发放记录不存在或不可见', 'INVENTORY_FULFILLMENT_NOT_FOUND', null)
    }
    if (!canInitiateReturn(owner)) {
      return errorWithTrace(403, '只有申请人或系统管理员可以发起退回', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
    }
    const reason = String(payload?.reason || '').trim()
    if (reason.length < 2 || reason.length > 500) {
      return errorWithTrace(400, '退回原因需为 2-500 个字符', 'INVALID_PARAMETER', { field: 'reason' })
    }
    const rows = Array.isArray(payload?.lines) ? payload.lines : []
    if (!rows.length) return errorWithTrace(400, '至少需要一条退回明细', 'INVALID_PARAMETER', { field: 'lines' })
    const returnId = nextMockId()
    const returnLines = []
    for (const row of rows) {
      const fulfillmentLine = owner.fulfillment.lines.find((line) => Number(line.id) === Number(row?.fulfillmentLineId))
      const quantity = Number(row?.quantity)
      if (!fulfillmentLine || !Number.isFinite(quantity) || quantity <= 0) {
        return errorWithTrace(400, '退回明细或数量不合法', 'RETURN_QUANTITY_INVALID', { field: 'lines' })
      }
      const line = owner.lines.find((item) => item.id === fulfillmentLine.requestLineId)
      const already = Number(line?.returnedQuantity || 0)
      const returnable = round(Number(fulfillmentLine.quantity) - already, QUANTITY_SCALE)
      if (quantity > returnable) {
        return errorWithTrace(409, '累计退回不得超过原发放未退数量', 'INVENTORY_RETURN_EXCEEDS_ISSUED', {
          fulfillmentLineId: fulfillmentLine.id,
          returnableQuantity: returnable,
          requestedQuantity: quantity
        })
      }
      returnLines.push({ id: nextMockId(), returnId, fulfillmentLineId: fulfillmentLine.id, batchId: fulfillmentLine.batchId, quantity: round(quantity, QUANTITY_SCALE) })
    }
    const record = {
      id: returnId,
      returnNo: `RTN-20261123-${String(returnId).slice(-6).padStart(6, '0')}`,
      fulfillmentId: Number(fulfillmentId),
      requestId: owner.id,
      status: RETURN_STATUS.PENDING_CONFIRMATION,
      reason,
      requestedBy: currentUserId(),
      requestedAt: nowIso(),
      confirmedBy: null,
      confirmedAt: null,
      version: 1,
      lines: returnLines
    }
    owner.returns = owner.returns || []
    owner.returns.push(record)
    return clone(record)
  })
}

export function confirmReturn(returnId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/inventory-returns/${returnId}/confirm`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(`POST /api/inventory-returns/${returnId}/confirm`, idempotencyKey, payload, () => {
    const found = findMockReturn(returnId)
    if (!found || !requestVisible(found.issueRequest)) {
      return errorWithTrace(404, '退回记录不存在或不可见', 'INVENTORY_RETURN_NOT_FOUND', null)
    }
    if (!canConfirmReturn()) {
      return errorWithTrace(403, '只有库存管理员可以确认退回', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
    }
    const { issueRequest, returnRecord } = found
    if (returnRecord.status !== RETURN_STATUS.PENDING_CONFIRMATION) {
      return invalidTransition(returnRecord.status, 'confirm-return')
    }
    if (payload?.returnVersion != null && Number(payload.returnVersion) !== returnRecord.version) {
      return versionConflict(returnRecord.version)
    }
    returnRecord.lines.forEach((row) => {
      const batch = findMockBatch(row.batchId)
      const componentId = batch.componentId
      const componentBalance = findMockBalance(componentId)
      batch.currentQuantity = round(Number(batch.currentQuantity) + Number(row.quantity), QUANTITY_SCALE)
      if (componentBalance) {
        componentBalance.onHandQuantity = round(Number(componentBalance.onHandQuantity) + Number(row.quantity), QUANTITY_SCALE)
        componentBalance.version += 1
        componentBalance.updateTime = nowIso()
      }
      const movementId = nextMockId()
      MOCK_MOVEMENTS.push({
        movementId,
        movementType: MOVEMENT_TYPE.RETURN_IN,
        componentId,
        batchId: row.batchId,
        batchNo: batch.batchNo,
        quantityDelta: Number(row.quantity),
        balanceBefore: round(Number(componentBalance?.onHandQuantity || 0) - Number(row.quantity), QUANTITY_SCALE),
        balanceAfter: componentBalance?.onHandQuantity ?? null,
        batchBefore: round(Number(batch.currentQuantity) - Number(row.quantity), QUANTITY_SCALE),
        batchAfter: batch.currentQuantity,
        operatorId: currentUserId(),
        operatorName: userName(currentUserId()),
        sourceType: 'RETURN_LINE',
        sourceId: row.id,
        reason: returnRecord.reason,
        requestId: issueRequest.id,
        occurredAt: nowIso()
      })
      const fulfillmentLine = issueRequest.fulfillment.lines.find((line) => line.id === row.fulfillmentLineId)
      const requestLine = issueRequest.lines.find((line) => line.id === fulfillmentLine?.requestLineId)
      if (requestLine) {
        requestLine.returnedQuantity = round(Number(requestLine.returnedQuantity || 0) + Number(row.quantity), QUANTITY_SCALE)
      }
    })
    returnRecord.status = RETURN_STATUS.CONFIRMED
    returnRecord.confirmedBy = currentUserId()
    returnRecord.confirmedAt = nowIso()
    returnRecord.version += 1
    issueRequest.version += 1
    issueRequest.updateTime = returnRecord.confirmedAt
    return clone(returnRecord)
  })
}

export function rejectReturn(returnId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/inventory-returns/${returnId}/reject`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  return withIdempotency(`POST /api/inventory-returns/${returnId}/reject`, idempotencyKey, payload, () => {
    const found = findMockReturn(returnId)
    if (!found || !requestVisible(found.issueRequest)) {
      return errorWithTrace(404, '退回记录不存在或不可见', 'INVENTORY_RETURN_NOT_FOUND', null)
    }
    if (!canConfirmReturn()) {
      return errorWithTrace(403, '只有库存管理员可以处理退回', 'INVENTORY_ISSUE_OPERATION_DENIED', null)
    }
    const comment = String(payload?.comment || '').trim()
    if (!comment) return errorWithTrace(400, '拒绝退回必须填写原因', 'INVALID_PARAMETER', { field: 'comment' })
    const { issueRequest, returnRecord } = found
    if (returnRecord.status !== RETURN_STATUS.PENDING_CONFIRMATION) {
      return invalidTransition(returnRecord.status, 'reject-return')
    }
    returnRecord.status = RETURN_STATUS.REJECTED
    returnRecord.reason = `${returnRecord.reason} · 拒绝原因：${comment}`
    returnRecord.version += 1
    returnRecord.confirmedBy = currentUserId()
    returnRecord.confirmedAt = nowIso()
    issueRequest.version += 1
    issueRequest.updateTime = returnRecord.confirmedAt
    return clone(returnRecord)
  })
}

// ========== 损耗 / 报废 ==========

function registerWriteOff(batchId, payload, idempotencyKey, type) {
  if (!getMockEnabled()) {
    const suffix = type === WRITE_OFF_TYPE.LOSS ? 'losses' : 'scraps'
    return request.post(`/api/inventory/batches/${batchId}/${suffix}`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  const action = type === WRITE_OFF_TYPE.LOSS ? 'losses' : 'scraps'
  return withIdempotency(`POST /api/inventory/batches/${batchId}/${action}`, idempotencyKey, payload, () => {
    if (!canWriteOffStock()) {
      return errorWithTrace(403, '只有库存管理员可以登记损耗/报废', 'INVENTORY_WRITE_OFF_DENIED', null)
    }
    const batch = findMockBatch(batchId)
    if (!batch) return errorWithTrace(404, '批次不存在或不可见', 'INVENTORY_BATCH_NOT_FOUND', null)
    const reason = String(payload?.reason || '').trim()
    if (reason.length < 1 || reason.length > 500) {
      return errorWithTrace(400, '损耗/报废必须填写 1-500 字原因', 'WRITE_OFF_REASON_REQUIRED', { field: 'reason' })
    }
    const quantity = Number(payload?.quantity)
    if (!Number.isFinite(quantity) || quantity <= 0) {
      return errorWithTrace(400, '数量必须大于 0', 'ISSUE_QUANTITY_INVALID', { field: 'quantity' })
    }
    const available = batchAvailable(batch)
    if (quantity > available) {
      return errorWithTrace(409, '不能占用或扣减已预占库存', 'INVENTORY_RESERVED_STOCK_PROTECTED', {
        batchId: batch.id,
        availableQuantity: available,
        requestedQuantity: quantity
      })
    }
    const balance = findMockBalance(batch.componentId)
    const before = Number(balance.onHandQuantity || 0)
    batch.currentQuantity = round(Number(batch.currentQuantity) - quantity, QUANTITY_SCALE)
    if (balance) {
      balance.onHandQuantity = round(before - quantity, QUANTITY_SCALE)
      balance.version += 1
      balance.updateTime = nowIso()
    }
    const commandId = nextMockId()
    const movementId = nextMockId()
    MOCK_MOVEMENTS.push({
      movementId,
      movementType: type === WRITE_OFF_TYPE.LOSS ? MOVEMENT_TYPE.LOSS_OUT : MOVEMENT_TYPE.SCRAP_OUT,
      componentId: batch.componentId,
      batchId: batch.id,
      batchNo: batch.batchNo,
      quantityDelta: -quantity,
      balanceBefore: before,
      balanceAfter: balance?.onHandQuantity ?? null,
      batchBefore: round(Number(batch.currentQuantity) + quantity, QUANTITY_SCALE),
      batchAfter: batch.currentQuantity,
      operatorId: currentUserId(),
      operatorName: userName(currentUserId()),
      sourceType: 'WRITE_OFF_COMMAND',
      sourceId: commandId,
      reason,
      requestId: null,
      occurredAt: nowIso()
    })
    return {
      commandNo: `${type}-20261123-${String(commandId).slice(-6).padStart(6, '0')}`,
      type,
      componentId: batch.componentId,
      batchId: batch.id,
      quantity: round(quantity, QUANTITY_SCALE),
      reason,
      movementId,
      balanceAfter: balance?.onHandQuantity ?? null,
      occurredAt: nowIso()
    }
  })
}

export function registerBatchLoss(batchId, payload, idempotencyKey) {
  return registerWriteOff(batchId, payload, idempotencyKey, WRITE_OFF_TYPE.LOSS)
}

export function registerBatchScrap(batchId, payload, idempotencyKey) {
  return registerWriteOff(batchId, payload, idempotencyKey, WRITE_OFF_TYPE.SCRAP)
}

// ========== 兼容辅助 ==========

export function normalizePage(response) {
  if (Array.isArray(response)) {
    return { list: response, total: response.length, page: 1, pageSize: response.length, hasNext: false }
  }
  return {
    list: response?.list || response?.items || response?.records || [],
    total: Number(response?.total ?? 0),
    page: Number(response?.page || 1),
    pageSize: Number(response?.pageSize || 20),
    hasNext: Boolean(response?.hasNext)
  }
}
