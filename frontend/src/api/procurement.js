import request from '@/utils/request'
import { getMockEnabled } from '@/utils/mock'
import { userStore, currentRoles } from '@/store/user'
import { ROLE } from '@/utils/permission'
import { idempotencyHeaders, requestDigest } from '@/utils/idempotency'
import { isMockScenario, mockDelay, mockReject, mockTraceId } from '@/utils/mockScenario'

/**
 * 采购、分批到货与库存增加 API（第 9 周 M03-W02）
 *
 * 契约来源：docs/contracts/PROCUREMENT_INVENTORY_API.md v1.0（FROZEN）
 *
 * 真实端点（契约 §7）：
 *   GET/PUT  /api/procurement/policy
 *   GET      /api/procurement/policy/versions
 *   POST/GET /api/procurement-orders
 *   GET/PUT  /api/procurement-orders/{id}
 *   POST     /api/procurement-orders/{id}/submit|approve|reject|mark-ordered|cancel|cancel-remaining|complete
 *   POST/GET /api/procurement-orders/{id}/receipts
 *   GET/PUT  /api/procurement-receipts/{receiptId}
 *   POST     /api/procurement-receipts/{receiptId}/confirm|cancel
 *   GET      /api/inventory/components/{componentId}
 *   GET      /api/inventory/components/{componentId}/batches
 *   GET      /api/inventory/movements
 *   POST/GET /api/procurement-orders/{id}/attachments
 *   POST/GET /api/procurement-receipts/{receiptId}/attachments
 *   POST     /api/procurement-attachments/{id}/replacement
 *   DELETE   /api/procurement-attachments/{id}
 *   GET      /api/media/{mediaId}/content
 *
 * 边界：附件只返回 mediaId、purpose 与受控元数据；不返回 objectKey、绝对路径、
 * bucket、provider、endpoint 或签名 URL。owner 不可见统一 404，且不泄露附件数量、
 * 文件名、mediaId、purpose 或存在性。mock 默认关闭（VITE_USE_MOCK）。
 */

// ========== 枚举（契约 §3 / §4 / §13.3） ==========

export const ORDER_STATUS = Object.freeze({
  DRAFT: 'DRAFT',
  PENDING_APPROVAL: 'PENDING_APPROVAL',
  APPROVED: 'APPROVED',
  ORDERED: 'ORDERED',
  PARTIALLY_RECEIVED: 'PARTIALLY_RECEIVED',
  RECEIVED: 'RECEIVED',
  COMPLETED: 'COMPLETED',
  REJECTED: 'REJECTED',
  CANCELED: 'CANCELED'
})

export const ORDER_STATUS_META = Object.freeze({
  [ORDER_STATUS.DRAFT]: { label: '草稿', tagType: 'info', desc: '仅申请人/管理员可编辑，未提交' },
  [ORDER_STATUS.PENDING_APPROVAL]: { label: '待审批', tagType: 'warning', desc: '等待非申请人的老师/管理员批准' },
  [ORDER_STATUS.APPROVED]: { label: '已批准', tagType: 'success', desc: '可登记供应商与下单事实' },
  [ORDER_STATUS.ORDERED]: { label: '已下单', tagType: 'primary', desc: '等待到货，可创建到货草稿' },
  [ORDER_STATUS.PARTIALLY_RECEIVED]: { label: '部分到货', tagType: 'warning', desc: '仍有未到剩余数量' },
  [ORDER_STATUS.RECEIVED]: { label: '已全部到货', tagType: 'success', desc: '可标记采购完成' },
  [ORDER_STATUS.COMPLETED]: { label: '已完成', tagType: 'success', desc: '终态；重办需复制为新草稿' },
  [ORDER_STATUS.REJECTED]: { label: '已拒绝', tagType: 'danger', desc: '终态；需重新提交新采购单' },
  [ORDER_STATUS.CANCELED]: { label: '已取消', tagType: 'info', desc: '终态；已取消整单' }
})

export const RECEIPT_STATUS = Object.freeze({
  DRAFT: 'DRAFT',
  CONFIRMED: 'CONFIRMED',
  CANCELED: 'CANCELED'
})

export const RECEIPT_STATUS_META = Object.freeze({
  [RECEIPT_STATUS.DRAFT]: { label: '草稿', tagType: 'info', desc: '可编辑、确认或取消' },
  [RECEIPT_STATUS.CONFIRMED]: { label: '已确认', tagType: 'success', desc: '已原子入库，不可撤销' },
  [RECEIPT_STATUS.CANCELED]: { label: '已取消', tagType: 'info', desc: '未产生库存事实' }
})

export const PLATFORM_TYPE = Object.freeze({
  TAOBAO: 'TAOBAO',
  JD: 'JD',
  PINDUODUO: 'PINDUODUO',
  ALIBABA_1688: '1688',
  OFFLINE: 'OFFLINE',
  OTHER: 'OTHER'
})

export const PLATFORM_TYPE_META = Object.freeze({
  [PLATFORM_TYPE.TAOBAO]: { label: '淘宝' },
  [PLATFORM_TYPE.JD]: { label: '京东' },
  [PLATFORM_TYPE.PINDUODUO]: { label: '拼多多' },
  [PLATFORM_TYPE.ALIBABA_1688]: { label: '1688' },
  [PLATFORM_TYPE.OFFLINE]: { label: '线下' },
  [PLATFORM_TYPE.OTHER]: { label: '其他' }
})

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
  VALUABLE_ITEM: 'VALUABLE_ITEM',
  FORCED_CATEGORY: 'FORCED_CATEGORY',
  EXEMPT: 'EXEMPT'
})

export const APPROVAL_REASON_META = Object.freeze({
  [APPROVAL_REASON.AMOUNT_THRESHOLD]: { label: '金额达到阈值', tagType: 'warning' },
  [APPROVAL_REASON.VALUABLE_ITEM]: { label: '贵重明细', tagType: 'warning' },
  [APPROVAL_REASON.FORCED_CATEGORY]: { label: '指定分类', tagType: 'warning' },
  [APPROVAL_REASON.EXEMPT]: { label: '免审', tagType: 'success' }
})

export const ATTACHMENT_OWNER_TYPE = Object.freeze({
  PROCUREMENT_ORDER: 'PROCUREMENT_ORDER',
  PROCUREMENT_RECEIPT: 'PROCUREMENT_RECEIPT'
})

export const ATTACHMENT_PURPOSE = Object.freeze({
  ORDER_SCREENSHOT: 'ORDER_SCREENSHOT',
  INVOICE: 'INVOICE',
  RECEIPT_PHOTO: 'RECEIPT_PHOTO',
  PACKAGE_PHOTO: 'PACKAGE_PHOTO'
})

export const ATTACHMENT_PURPOSE_META = Object.freeze({
  [ATTACHMENT_PURPOSE.ORDER_SCREENSHOT]: { label: '订单截图', ownerType: ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER },
  [ATTACHMENT_PURPOSE.INVOICE]: { label: '发票', ownerType: ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER },
  [ATTACHMENT_PURPOSE.RECEIPT_PHOTO]: { label: '到货照片', ownerType: ATTACHMENT_OWNER_TYPE.PROCUREMENT_RECEIPT },
  [ATTACHMENT_PURPOSE.PACKAGE_PHOTO]: { label: '包装照片', ownerType: ATTACHMENT_OWNER_TYPE.PROCUREMENT_RECEIPT }
})

export const ORDER_ACTION = Object.freeze({
  EDIT: 'edit',
  SUBMIT: 'submit',
  APPROVE: 'approve',
  REJECT: 'reject',
  MARK_ORDERED: 'mark-ordered',
  CREATE_RECEIPT: 'create-receipt',
  CANCEL: 'cancel',
  CANCEL_REMAINING: 'cancel-remaining',
  COMPLETE: 'complete'
})

export const AMOUNT_SCALE = 2
export const QUANTITY_SCALE = 3
export const UNIT_PRICE_SCALE = 4

export const POLICY_DEFAULTS = Object.freeze({
  currency: 'CNY',
  approvalThreshold: 500.00,
  valuableUnitPriceThreshold: 1000.0000,
  forcedCategoryIds: [31]
})

export function orderStatusMeta(status) {
  return ORDER_STATUS_META[status] || { label: status || '--', tagType: 'info', desc: '' }
}

export function receiptStatusMeta(status) {
  return RECEIPT_STATUS_META[status] || { label: status || '--', tagType: 'info', desc: '' }
}

export function platformTypeMeta(type) {
  return PLATFORM_TYPE_META[type] || { label: type || '--' }
}

export function approvalDecisionMeta(decision) {
  return APPROVAL_DECISION_META[decision] || { label: decision || '--', tagType: 'info' }
}

export function approvalReasonMeta(reason) {
  return APPROVAL_REASON_META[reason] || { label: reason || '--', tagType: 'warning' }
}

export function attachmentPurposeMeta(purpose) {
  return ATTACHMENT_PURPOSE_META[purpose] || { label: purpose || '--' }
}

export function isTerminalOrderStatus(status) {
  return [ORDER_STATUS.COMPLETED, ORDER_STATUS.REJECTED, ORDER_STATUS.CANCELED].includes(status)
}

// ========== 展示格式（契约 §13.4） ==========

export function formatAmount(value) {
  const n = Number(value)
  return `¥${(Number.isFinite(n) ? n : 0).toFixed(AMOUNT_SCALE)}`
}

export function formatQuantity(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '0'
  const fixed = n.toFixed(QUANTITY_SCALE)
  return fixed.replace(/(\.\d*?[1-9])0+$/, '$1').replace(/\.0+$/, '')
}

export function formatQuantityUnit(value, unit) {
  return `${formatQuantity(value)}${unit ? ` ${unit}` : ''}`
}

export function formatUnitPrice(value) {
  const n = Number(value)
  return `¥${(Number.isFinite(n) ? n : 0).toFixed(UNIT_PRICE_SCALE)}`
}

/** 进度百分比按 received+canceled 计算，不能把采购状态当库存数量 */
export function lineProgressPercent(line) {
  if (!line) return 0
  if (line.progressPercent != null) return Number(line.progressPercent)
  const ordered = Number(line.orderedQuantity || 0)
  if (ordered <= 0) return 0
  const done = Number(line.receivedQuantity || 0) + Number(line.canceledQuantity || 0)
  return Math.min(100, Math.round((done / ordered) * 10000) / 100)
}

export function lineRemainingQuantity(line) {
  if (line?.remainingQuantity != null) return Number(line.remainingQuantity)
  return Number(line?.orderedQuantity || 0) - Number(line?.receivedQuantity || 0) - Number(line?.canceledQuantity || 0)
}

export function lineStateOf(line) {
  if (!line) return ORDER_STATUS.DRAFT
  const ordered = Number(line.orderedQuantity || 0)
  const received = Number(line.receivedQuantity || 0)
  const canceled = Number(line.canceledQuantity || 0)
  if (received + canceled >= ordered && ordered > 0) {
    return received >= ordered ? 'RECEIVED' : 'COMPLETED'
  }
  if (received > 0) return 'PARTIALLY_RECEIVED'
  return 'ORDERED'
}

// ========== 权限（契约 §5；前端只控制 UI，真正鉴权以后端为准） ==========

export const PROCUREMENT_ROLES = Object.freeze([ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER])

function roles() {
  return currentRoles()
}

function has(role) {
  return roles().includes(role)
}

export function currentUserId() {
  const id = userStore?.userInfo?.id
  return id == null ? null : Number(id)
}

export function canAccessProcurement() {
  return PROCUREMENT_ROLES.some((role) => has(role))
}

export function canViewAllOrders() {
  return has(ROLE.SYSTEM_ADMIN) || has(ROLE.TEACHER) || has(ROLE.STOCK_KEEPER)
}

export function canCreateOrder() {
  return canAccessProcurement()
}

export function isOrderRequester(order) {
  return order?.requesterId != null && Number(order.requesterId) === currentUserId()
}

export function isOrderBuyer(order) {
  return order?.buyerId != null && Number(order.buyerId) === currentUserId()
}

export function canViewOrder(order) {
  if (!order) return false
  return canViewAllOrders() || isOrderRequester(order) || isOrderBuyer(order)
}

export function canViewInvoice(order) {
  return canViewOrder(order)
}

export function canEditOrder(order) {
  if (!order || order.status !== ORDER_STATUS.DRAFT) return false
  return has(ROLE.SYSTEM_ADMIN) || has(ROLE.STOCK_KEEPER) || isOrderRequester(order)
}

export function canSubmitOrder(order) {
  if (!order || order.status !== ORDER_STATUS.DRAFT) return false
  return has(ROLE.SYSTEM_ADMIN) || isOrderRequester(order)
}

export function canApproveOrder(order) {
  if (!order || order.status !== ORDER_STATUS.PENDING_APPROVAL) return false
  if (!has(ROLE.SYSTEM_ADMIN) && !has(ROLE.TEACHER)) return false
  return !isOrderRequester(order) && !isOrderBuyer(order)
}

export function canMarkOrdered(order) {
  return Boolean(order) && order.status === ORDER_STATUS.APPROVED && (has(ROLE.SYSTEM_ADMIN) || has(ROLE.STOCK_KEEPER))
}

export function canCreateReceipt(order) {
  if (!order) return false
  if (![ORDER_STATUS.ORDERED, ORDER_STATUS.PARTIALLY_RECEIVED].includes(order.status)) return false
  return has(ROLE.SYSTEM_ADMIN) || has(ROLE.STOCK_KEEPER)
}

export function canConfirmReceipt() {
  return has(ROLE.SYSTEM_ADMIN) || has(ROLE.STOCK_KEEPER)
}

export function canCompleteOrder(order) {
  return Boolean(order) && order.status === ORDER_STATUS.RECEIVED && (has(ROLE.SYSTEM_ADMIN) || has(ROLE.STOCK_KEEPER))
}

export function canCancelOrder(order) {
  if (!order) return false
  const status = order.status
  if ([ORDER_STATUS.DRAFT, ORDER_STATUS.PENDING_APPROVAL].includes(status)) {
    return has(ROLE.SYSTEM_ADMIN) || isOrderRequester(order)
  }
  if (status === ORDER_STATUS.APPROVED) {
    return has(ROLE.SYSTEM_ADMIN) || has(ROLE.STOCK_KEEPER) || isOrderRequester(order)
  }
  if (status === ORDER_STATUS.ORDERED) {
    return has(ROLE.SYSTEM_ADMIN) || has(ROLE.STOCK_KEEPER)
  }
  return false
}

export function canCancelRemaining(order) {
  return Boolean(order)
    && order.status === ORDER_STATUS.PARTIALLY_RECEIVED
    && (has(ROLE.SYSTEM_ADMIN) || has(ROLE.STOCK_KEEPER))
}

export function canUpdatePolicy() {
  return has(ROLE.SYSTEM_ADMIN)
}

export function canViewInventoryCost() {
  return has(ROLE.SYSTEM_ADMIN) || has(ROLE.TEACHER) || has(ROLE.STOCK_KEEPER)
}

export function canViewInventoryBatches() {
  return canViewInventoryCost()
}

export function canUploadOrderAttachment(order, purpose) {
  if (!order || !canViewOrder(order)) return false
  if (![ATTACHMENT_PURPOSE.ORDER_SCREENSHOT, ATTACHMENT_PURPOSE.INVOICE].includes(purpose)) return false
  if (has(ROLE.SYSTEM_ADMIN) || has(ROLE.STOCK_KEEPER)) return true
  return has(ROLE.TEACHER) || has(ROLE.MEMBER) ? isOrderRequester(order) : false
}

export function canUploadReceiptAttachment(order, purpose) {
  if (!order) return false
  if (![ATTACHMENT_PURPOSE.RECEIPT_PHOTO, ATTACHMENT_PURPOSE.PACKAGE_PHOTO].includes(purpose)) return false
  return has(ROLE.SYSTEM_ADMIN) || has(ROLE.STOCK_KEEPER)
}

export function canViewAttachments(order) {
  return canViewOrder(order)
}

/** 详情页可用动作，按契约 §4.1 */
export function orderActions(order) {
  return {
    edit: canEditOrder(order),
    submit: canSubmitOrder(order),
    approve: canApproveOrder(order),
    reject: canApproveOrder(order),
    markOrdered: canMarkOrdered(order),
    createReceipt: canCreateReceipt(order),
    cancel: canCancelOrder(order),
    cancelRemaining: canCancelRemaining(order),
    complete: canCompleteOrder(order)
  }
}

// ========== 请求校验辅助（仅用于 mock，真实后端以服务端为准） ==========

function round(value, scale) {
  const factor = 10 ** scale
  const n = Number(value)
  if (!Number.isFinite(n)) return 0
  return Math.round((n + Number.EPSILON) * factor) / factor
}

function sum(values) {
  return values.reduce((acc, value) => acc + Number(value || 0), 0)
}

// ========== Mock 基础数据 ==========

const MOCK_USERS = {
  1: '系统管理员',
  2: '张同学',
  3: '李同学',
  4: '王老师',
  21: '赵采购'
}

function nowIso() {
  return new Date().toISOString()
}

function userName(userId) {
  if (userId == null) return null
  return MOCK_USERS[userId] || `用户 ${userId}`
}

const MOCK_PROJECT_NAMES = {
  10: '气象站原型',
  11: '实验室工具升级'
}

const MOCK_COMPONENTS = [
  {
    id: 301, name: 'STM32F103C8T6 最小系统板', mpn: 'STM32F103C8T6', manufacturerName: '意法半导体',
    categoryId: 24, categoryName: '微控制器', baseUnit: 'PCS', specVersion: 3, specSnapshotId: 3301, status: 'ACTIVE'
  },
  {
    id: 302, name: 'DHT22 温湿度传感器', mpn: 'DHT22', manufacturerName: '奥松电子',
    categoryId: 25, categoryName: '传感器', baseUnit: 'PCS', specVersion: 2, specSnapshotId: 3302, status: 'ACTIVE'
  },
  {
    id: 303, name: '0.96 寸 OLED 显示屏', mpn: 'SSD1306', manufacturerName: '中景园电子',
    categoryId: 26, categoryName: '显示器件', baseUnit: 'PCS', specVersion: 1, specSnapshotId: 3303, status: 'ACTIVE'
  },
  {
    id: 304, name: '2.54mm 排针 40P', mpn: 'PZ-40P', manufacturerName: '国产通用',
    categoryId: 13, categoryName: '连接器', baseUnit: 'PCS', specVersion: 1, specSnapshotId: 3304, status: 'ACTIVE'
  },
  {
    id: 305, name: '杜邦线 20cm 母对母', mpn: 'DUPONT-20', manufacturerName: '国产通用',
    categoryId: 13, categoryName: '连接器', baseUnit: 'SET', specVersion: 1, specSnapshotId: 3305, status: 'ACTIVE'
  },
  {
    id: 306, name: '10uF 0805 电容', mpn: 'CL10A106KP8NNNC', manufacturerName: '三星电机',
    categoryId: 23, categoryName: '电容器', baseUnit: 'PCS', specVersion: 2, specSnapshotId: 3306, status: 'ACTIVE'
  },
  {
    id: 307, name: '1kΩ 0603 电阻', mpn: 'RC0603FR-071KL', manufacturerName: '国巨',
    categoryId: 12, categoryName: '被动器件', baseUnit: 'PCS', specVersion: 2, specSnapshotId: 3307, status: 'ACTIVE'
  },
  {
    id: 308, name: '恒温焊接台', mpn: 'T12-D', manufacturerName: '白光',
    categoryId: 27, categoryName: '工具', baseUnit: 'SET', specVersion: 1, specSnapshotId: 3308, status: 'ACTIVE'
  },
  {
    id: 309, name: 'LM358 双运放', mpn: 'LM358', manufacturerName: '德州仪器',
    categoryId: 31, categoryName: '运算放大器', baseUnit: 'PCS', specVersion: 2, specSnapshotId: 3309, status: 'ACTIVE'
  }
]

function findMockComponent(componentId) {
  return MOCK_COMPONENTS.find((component) => component.id === Number(componentId)) || null
}

function publicMockComponent(component) {
  if (!component) return null
  return { ...component }
}

function makeMockLine(orderId, lineNo, componentId, orderedQuantity, unitPrice) {
  const component = findMockComponent(componentId)
  if (!component) return null
  const quantity = round(orderedQuantity, QUANTITY_SCALE)
  const price = round(unitPrice, UNIT_PRICE_SCALE)
  return {
    id: orderId * 10 + lineNo,
    orderId,
    lineNo,
    componentId: component.id,
    componentName: component.name,
    manufacturerName: component.manufacturerName,
    mpn: component.mpn,
    categoryId: component.categoryId,
    componentSpecSnapshotId: component.specSnapshotId,
    specVersion: component.specVersion,
    unit: component.baseUnit,
    orderedQuantity: quantity,
    unitPrice: price,
    lineSubtotal: round(quantity * price, AMOUNT_SCALE),
    allocatedNetAmount: null,
    effectiveUnitCost: null,
    receivedQuantity: 0,
    canceledQuantity: 0,
    version: 1
  }
}

function computeOrderAmounts(order) {
  order.itemSubtotal = round(sum(order.lines.map((line) => line.lineSubtotal)), AMOUNT_SCALE)
  order.payableAmount = round(order.itemSubtotal + Number(order.shippingAmount || 0) - Number(order.discountAmount || 0), AMOUNT_SCALE)
  return order
}

/** 下单时按行小计比例分摊 payableAmount；最后一条吸收尾差并冻结 effectiveUnitCost */
function allocateOrderAmounts(order) {
  const lines = order.lines
  if (!lines.length) return order
  const payable = round(order.payableAmount, AMOUNT_SCALE)
  let allocated = 0
  lines.forEach((line, index) => {
    const share = index === lines.length - 1
      ? round(payable - allocated, AMOUNT_SCALE)
      : round(payable * (line.lineSubtotal / order.itemSubtotal), AMOUNT_SCALE)
    allocated = round(allocated + share, AMOUNT_SCALE)
    line.allocatedNetAmount = share
    line.effectiveUnitCost = line.orderedQuantity > 0
      ? round(share / line.orderedQuantity, UNIT_PRICE_SCALE)
      : 0
  })
  return order
}

function seedOrder(config) {
  const order = {
    id: config.id,
    purchaseNo: config.purchaseNo || `PO-2026110${String(config.id - 900).padStart(1, '0')}-0000${String(config.id - 900).padStart(2, '0')}`,
    title: config.title,
    requesterId: config.requesterId,
    requesterName: userName(config.requesterId),
    buyerId: config.buyerId || null,
    buyerName: config.buyerId ? userName(config.buyerId) : null,
    projectId: config.projectId || null,
    projectName: config.projectId ? (MOCK_PROJECT_NAMES[config.projectId] || `项目 ${config.projectId}`) : null,
    fundingSource: config.fundingSource || null,
    supplierName: config.supplierName || null,
    platformType: config.platformType || null,
    platformOrderNo: config.platformOrderNo || null,
    currency: 'CNY',
    shippingAmount: round(config.shippingAmount || 0, AMOUNT_SCALE),
    discountAmount: round(config.discountAmount || 0, AMOUNT_SCALE),
    actualPaidAmount: config.actualPaidAmount != null ? round(config.actualPaidAmount, AMOUNT_SCALE) : null,
    paymentDifferenceNote: config.paymentDifferenceNote || null,
    status: config.status,
    approvalRequired: config.approvalRequired ?? null,
    approvalPolicyVersion: config.approvalPolicyVersion ?? null,
    approvalReasons: config.approvalReasons || [],
    orderedAt: config.orderedAt || null,
    completedAt: config.completedAt || null,
    closeReason: config.closeReason || null,
    version: config.version || 1,
    createdBy: config.requesterId,
    createTime: config.createTime || '2026-11-02T09:00:00',
    updatedBy: config.updatedBy || config.requesterId,
    updateTime: config.updateTime || '2026-11-02T09:00:00',
    approvalRecords: config.approvalRecords || [],
    lines: config.lines
  }
  computeOrderAmounts(order)
  if ([ORDER_STATUS.ORDERED, ORDER_STATUS.PARTIALLY_RECEIVED, ORDER_STATUS.RECEIVED, ORDER_STATUS.COMPLETED].includes(order.status)) {
    allocateOrderAmounts(order)
  }
  return order
}

function approvalRecord(orderId, decision, policyVersion, decidedBy, decidedAt, comment, payableAmount, reasons) {
  return {
    id: orderId * 100 + (decision === APPROVAL_DECISION.REJECTED ? 2 : 1),
    orderId,
    decision,
    policyVersion,
    triggerSnapshot: {
      payableAmount: round(payableAmount, AMOUNT_SCALE),
      reasons,
      lines: []
    },
    actorType: decidedBy == null ? 'SYSTEM' : 'USER',
    decidedBy: decidedBy ?? null,
    decidedAt,
    comment: comment || null,
    requestId: `mock-approval-${orderId}-${decision.toLowerCase()}`
  }
}

const MOCK_POLICIES = [
  {
    id: 1,
    policyVersion: 1,
    currency: 'CNY',
    approvalThreshold: 500.00,
    valuableUnitPriceThreshold: 1000.0000,
    forcedCategoryIds: [31],
    effectiveAt: '2026-11-07T00:00:00',
    createdBy: 1,
    createTime: '2026-11-07T00:00:00'
  }
]

const MOCK_ORDERS = [
  seedOrder({
    id: 901,
    purchaseNo: 'PO-20261102-000001',
    title: '气象站原型元器件采购',
    requesterId: 2,
    projectId: 10,
    fundingSource: '实验室开放基金',
    status: ORDER_STATUS.DRAFT,
    shippingAmount: 12.00,
    discountAmount: 5.00,
    version: 3,
    updateTime: '2026-11-02T16:20:00',
    lines: [
      makeMockLine(901, 1, 301, 20, 8.50),
      makeMockLine(901, 2, 302, 5, 60.00)
    ]
  }),
  seedOrder({
    id: 902,
    purchaseNo: 'PO-20261103-000002',
    title: '电源模块补充采购',
    requesterId: 2,
    status: ORDER_STATUS.PENDING_APPROVAL,
    approvalRequired: true,
    approvalPolicyVersion: 1,
    approvalReasons: [APPROVAL_REASON.VALUABLE_ITEM],
    version: 2,
    updateTime: '2026-11-03T10:15:00',
    lines: [
      makeMockLine(902, 1, 309, 10, 120.00),
      makeMockLine(902, 2, 303, 2, 80.00)
    ]
  }),
  seedOrder({
    id: 903,
    purchaseNo: 'PO-20261103-000003',
    title: '传感器批量采购',
    requesterId: 3,
    projectId: 10,
    status: ORDER_STATUS.APPROVED,
    approvalRequired: true,
    approvalPolicyVersion: 1,
    approvalReasons: [APPROVAL_REASON.AMOUNT_THRESHOLD],
    version: 3,
    updateTime: '2026-11-03T14:40:00',
    approvalRecords: [
      approvalRecord(903, APPROVAL_DECISION.APPROVED, 1, 4, '2026-11-03T14:40:00', '预算与用途已确认', 1250.00, [APPROVAL_REASON.AMOUNT_THRESHOLD])
    ],
    lines: [makeMockLine(903, 1, 306, 1000, 1.25)]
  }),
  seedOrder({
    id: 904,
    purchaseNo: 'PO-20261104-000004',
    title: '连接器与线材采购',
    requesterId: 3,
    projectId: 11,
    buyerId: 21,
    supplierName: '某授权经销商',
    platformType: PLATFORM_TYPE.ALIBABA_1688,
    platformOrderNo: 'A202611050001',
    status: ORDER_STATUS.ORDERED,
    shippingAmount: 50.00,
    actualPaidAmount: 1250.00,
    approvalRequired: false,
    approvalPolicyVersion: 1,
    approvalReasons: [APPROVAL_REASON.EXEMPT],
    orderedAt: '2026-11-04T11:30:00',
    version: 5,
    updateTime: '2026-11-04T11:30:00',
    approvalRecords: [
      approvalRecord(904, APPROVAL_DECISION.AUTO_APPROVED, 1, null, '2026-11-04T09:10:00', null, 1200.00, [APPROVAL_REASON.EXEMPT])
    ],
    lines: [
      makeMockLine(904, 1, 304, 100, 6.00),
      makeMockLine(904, 2, 305, 100, 6.00)
    ]
  }),
  seedOrder({
    id: 905,
    purchaseNo: 'PO-20261103-000005',
    title: '电容与电阻补货',
    requesterId: 2,
    projectId: 10,
    buyerId: 3,
    supplierName: '深圳华强电子',
    platformType: PLATFORM_TYPE.TAOBAO,
    platformOrderNo: 'TB20261103008',
    status: ORDER_STATUS.PARTIALLY_RECEIVED,
    actualPaidAmount: 110.00,
    approvalRequired: false,
    approvalPolicyVersion: 1,
    approvalReasons: [APPROVAL_REASON.EXEMPT],
    orderedAt: '2026-11-03T16:00:00',
    version: 7,
    updateTime: '2026-11-05T15:10:00',
    approvalRecords: [
      approvalRecord(905, APPROVAL_DECISION.AUTO_APPROVED, 1, null, '2026-11-03T15:30:00', null, 110.00, [APPROVAL_REASON.EXEMPT])
    ],
    lines: [
      { ...makeMockLine(905, 1, 306, 200, 0.50), receivedQuantity: 80, version: 2 },
      makeMockLine(905, 2, 307, 500, 0.02)
    ]
  }),
  seedOrder({
    id: 906,
    purchaseNo: 'PO-20261105-000006',
    title: '实验台工具采购',
    requesterId: 3,
    projectId: 11,
    buyerId: 21,
    supplierName: '白光工具旗舰店',
    platformType: PLATFORM_TYPE.JD,
    platformOrderNo: 'JD20261105002',
    status: ORDER_STATUS.RECEIVED,
    actualPaidAmount: 1000.00,
    approvalRequired: true,
    approvalPolicyVersion: 1,
    approvalReasons: [APPROVAL_REASON.AMOUNT_THRESHOLD],
    orderedAt: '2026-11-05T10:00:00',
    version: 6,
    updateTime: '2026-11-06T09:30:00',
    approvalRecords: [
      approvalRecord(906, APPROVAL_DECISION.APPROVED, 1, 4, '2026-11-05T09:20:00', '工具预算已确认', 1000.00, [APPROVAL_REASON.AMOUNT_THRESHOLD])
    ],
    lines: [
      { ...makeMockLine(906, 1, 308, 5, 200.00), receivedQuantity: 5, version: 2 }
    ]
  }),
  seedOrder({
    id: 907,
    purchaseNo: 'PO-20261101-000007',
    title: '开学耗材采购',
    requesterId: 2,
    buyerId: 2,
    supplierName: '淘宝电子元件',
    platformType: PLATFORM_TYPE.TAOBAO,
    platformOrderNo: 'TB20261101001',
    status: ORDER_STATUS.COMPLETED,
    actualPaidAmount: 800.00,
    approvalRequired: false,
    approvalPolicyVersion: 1,
    approvalReasons: [APPROVAL_REASON.EXEMPT],
    orderedAt: '2026-11-01T13:00:00',
    completedAt: '2026-11-04T17:20:00',
    version: 8,
    updateTime: '2026-11-04T17:20:00',
    approvalRecords: [
      approvalRecord(907, APPROVAL_DECISION.AUTO_APPROVED, 1, null, '2026-11-01T12:40:00', null, 800.00, [APPROVAL_REASON.EXEMPT])
    ],
    lines: [
      { ...makeMockLine(907, 1, 303, 10, 80.00), receivedQuantity: 10, version: 2 }
    ]
  }),
  seedOrder({
    id: 908,
    purchaseNo: 'PO-20261103-000008',
    title: '传感器备件采购',
    requesterId: 2,
    status: ORDER_STATUS.REJECTED,
    approvalRequired: true,
    approvalPolicyVersion: 1,
    approvalReasons: [APPROVAL_REASON.AMOUNT_THRESHOLD],
    version: 3,
    updateTime: '2026-11-03T18:00:00',
    approvalRecords: [
      approvalRecord(908, APPROVAL_DECISION.REJECTED, 1, 4, '2026-11-03T18:00:00', '本季度预算不足，请下季度重新提交', 1200.00, [APPROVAL_REASON.AMOUNT_THRESHOLD])
    ],
    lines: [makeMockLine(908, 1, 302, 20, 60.00)]
  }),
  seedOrder({
    id: 909,
    purchaseNo: 'PO-20261104-000009',
    title: '排针临时采购',
    requesterId: 3,
    status: ORDER_STATUS.CANCELED,
    closeReason: '需求取消，改用现有库存',
    version: 2,
    updateTime: '2026-11-04T10:00:00',
    lines: [makeMockLine(909, 1, 304, 50, 6.00)]
  }),
  seedOrder({
    id: 910,
    purchaseNo: 'PO-20261106-000010',
    title: '杜邦线补货',
    requesterId: 3,
    buyerId: 1,
    supplierName: '淘宝电子元件',
    platformType: PLATFORM_TYPE.TAOBAO,
    platformOrderNo: 'TB20261106012',
    status: ORDER_STATUS.ORDERED,
    actualPaidAmount: 300.00,
    approvalRequired: false,
    approvalPolicyVersion: 1,
    approvalReasons: [APPROVAL_REASON.EXEMPT],
    orderedAt: '2026-11-06T11:00:00',
    version: 6,
    updateTime: '2026-11-06T15:00:00',
    approvalRecords: [
      approvalRecord(910, APPROVAL_DECISION.AUTO_APPROVED, 1, null, '2026-11-06T10:40:00', null, 300.00, [APPROVAL_REASON.EXEMPT])
    ],
    lines: [makeMockLine(910, 1, 305, 300, 1.00)]
  })
]

const MOCK_RECEIPTS = [
  {
    id: 1001,
    receiptNo: 'RCV-20261105-000001',
    orderId: 905,
    status: RECEIPT_STATUS.CONFIRMED,
    receivedAt: '2026-11-05T14:30:00',
    receiverId: 3,
    receiverName: userName(3),
    note: '第一箱到货',
    confirmedAt: '2026-11-05T15:10:00',
    canceledAt: null,
    version: 2,
    createdBy: 3,
    createTime: '2026-11-05T14:30:00',
    updatedBy: 3,
    updateTime: '2026-11-05T15:10:00',
    lines: [
      {
        id: 10011,
        receiptId: 1001,
        orderLineId: 9051,
        componentId: 306,
        quantity: 80.000,
        unit: 'PCS',
        supplierLotNo: 'LOT-CAP-01',
        productionDate: '2026-10-01',
        expiryDate: '2028-10-01',
        locationNote: 'A 区 2 号柜',
        qualityNote: '外观正常',
        version: 2
      }
    ]
  },
  {
    id: 1002,
    receiptNo: 'RCV-20261106-000002',
    orderId: 906,
    status: RECEIPT_STATUS.CONFIRMED,
    receivedAt: '2026-11-06T09:00:00',
    receiverId: 3,
    receiverName: userName(3),
    note: '工具到货，验收正常',
    confirmedAt: '2026-11-06T09:30:00',
    canceledAt: null,
    version: 2,
    createdBy: 3,
    createTime: '2026-11-06T09:00:00',
    updatedBy: 3,
    updateTime: '2026-11-06T09:30:00',
    lines: [
      {
        id: 10021,
        receiptId: 1002,
        orderLineId: 9061,
        componentId: 308,
        quantity: 5.000,
        unit: 'SET',
        supplierLotNo: 'LOT-T12-01',
        productionDate: '2026-09-20',
        expiryDate: null,
        locationNote: '工具柜 B',
        qualityNote: '通电正常',
        version: 2
      }
    ]
  },
  {
    id: 1003,
    receiptNo: 'RCV-20261106-000003',
    orderId: 910,
    status: RECEIPT_STATUS.DRAFT,
    receivedAt: '2026-11-06T15:00:00',
    receiverId: null,
    receiverName: null,
    note: '首批到货草稿',
    confirmedAt: null,
    canceledAt: null,
    version: 1,
    createdBy: 3,
    createTime: '2026-11-06T15:00:00',
    updatedBy: 3,
    updateTime: '2026-11-06T15:00:00',
    lines: [
      {
        id: 10031,
        receiptId: 1003,
        orderLineId: 9101,
        componentId: 305,
        quantity: 150.000,
        unit: 'SET',
        supplierLotNo: null,
        productionDate: null,
        expiryDate: null,
        locationNote: null,
        qualityNote: null,
        version: 1
      }
    ]
  },
  {
    id: 1004,
    receiptNo: 'RCV-20261102-000004',
    orderId: 907,
    status: RECEIPT_STATUS.CONFIRMED,
    receivedAt: '2026-11-02T10:00:00',
    receiverId: 3,
    receiverName: userName(3),
    note: '全部到货',
    confirmedAt: '2026-11-02T10:20:00',
    canceledAt: null,
    version: 2,
    createdBy: 3,
    createTime: '2026-11-02T10:00:00',
    updatedBy: 3,
    updateTime: '2026-11-02T10:20:00',
    lines: [
      {
        id: 10041,
        receiptId: 1004,
        orderLineId: 9071,
        componentId: 303,
        quantity: 10.000,
        unit: 'PCS',
        supplierLotNo: 'LOT-OLED-01',
        productionDate: '2026-09-01',
        expiryDate: null,
        locationNote: 'A 区 1 号柜',
        qualityNote: '显示正常',
        version: 2
      }
    ]
  }
]

const MOCK_BATCHES = [
  {
    id: 1101,
    batchNo: 'LOT-20261105-000001',
    componentId: 306,
    sourceReceiptLineId: 10011,
    initialQuantity: 80.000,
    currentQuantity: 80.000,
    unit: 'PCS',
    unitCost: 0.5000,
    supplierLotNo: 'LOT-CAP-01',
    productionDate: '2026-10-01',
    expiryDate: '2028-10-01',
    locationNote: 'A 区 2 号柜',
    qualityNote: '外观正常',
    status: 'AVAILABLE',
    createdBy: 3,
    createTime: '2026-11-05T15:10:00'
  },
  {
    id: 1102,
    batchNo: 'LOT-20261106-000002',
    componentId: 308,
    sourceReceiptLineId: 10021,
    initialQuantity: 5.000,
    currentQuantity: 5.000,
    unit: 'SET',
    unitCost: 200.0000,
    supplierLotNo: 'LOT-T12-01',
    productionDate: '2026-09-20',
    expiryDate: null,
    locationNote: '工具柜 B',
    qualityNote: '通电正常',
    status: 'AVAILABLE',
    createdBy: 3,
    createTime: '2026-11-06T09:30:00'
  },
  {
    id: 1104,
    batchNo: 'LOT-20261102-000003',
    componentId: 303,
    sourceReceiptLineId: 10041,
    initialQuantity: 10.000,
    currentQuantity: 10.000,
    unit: 'PCS',
    unitCost: 80.0000,
    supplierLotNo: 'LOT-OLED-01',
    productionDate: '2026-09-01',
    expiryDate: null,
    locationNote: 'A 区 1 号柜',
    qualityNote: '显示正常',
    status: 'AVAILABLE',
    createdBy: 3,
    createTime: '2026-11-02T10:20:00'
  }
]

const MOCK_MOVEMENTS = [
  {
    id: 1201,
    movementNo: 'MOV-20261105-000001',
    movementType: 'PROCUREMENT_RECEIPT_IN',
    componentId: 306,
    batchId: 1101,
    sourceType: 'RECEIPT_LINE',
    sourceId: 10011,
    quantityDelta: 80.000,
    balanceBefore: 0.000,
    balanceAfter: 80.000,
    unit: 'PCS',
    unitCost: 0.5000,
    operatorId: 3,
    occurredAt: '2026-11-05T15:10:00',
    requestId: 'mock-confirm-1001'
  },
  {
    id: 1202,
    movementNo: 'MOV-20261106-000002',
    movementType: 'PROCUREMENT_RECEIPT_IN',
    componentId: 308,
    batchId: 1102,
    sourceType: 'RECEIPT_LINE',
    sourceId: 10021,
    quantityDelta: 5.000,
    balanceBefore: 0.000,
    balanceAfter: 5.000,
    unit: 'SET',
    unitCost: 200.0000,
    operatorId: 3,
    occurredAt: '2026-11-06T09:30:00',
    requestId: 'mock-confirm-1002'
  },
  {
    id: 1204,
    movementNo: 'MOV-20261102-000003',
    movementType: 'PROCUREMENT_RECEIPT_IN',
    componentId: 303,
    batchId: 1104,
    sourceType: 'RECEIPT_LINE',
    sourceId: 10041,
    quantityDelta: 10.000,
    balanceBefore: 0.000,
    balanceAfter: 10.000,
    unit: 'PCS',
    unitCost: 80.0000,
    operatorId: 3,
    occurredAt: '2026-11-02T10:20:00',
    requestId: 'mock-confirm-1004'
  }
]

const MOCK_BALANCES = [
  { componentId: 303, onHandQuantity: 10.000, unit: 'PCS', version: 1, updateTime: '2026-11-02T10:20:00' },
  { componentId: 306, onHandQuantity: 80.000, unit: 'PCS', version: 1, updateTime: '2026-11-05T15:10:00' },
  { componentId: 308, onHandQuantity: 5.000, unit: 'SET', version: 1, updateTime: '2026-11-06T09:30:00' }
]

function mockImageDataUrl(seed, label) {
  const hue = seed.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0) % 360
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="480"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl(${hue},70%,94%)"/><stop offset="1" stop-color="hsl(${hue},55%,55%)"/></linearGradient></defs><rect width="640" height="480" fill="url(#g)"/><rect x="48" y="48" width="544" height="384" fill="rgba(255,255,255,0.16)"/><text x="320" y="220" font-family="Microsoft YaHei,sans-serif" font-size="72" font-weight="bold" fill="#fff" text-anchor="middle">${seed.slice(0, 6).toUpperCase()}</text><text x="320" y="300" font-family="Microsoft YaHei,sans-serif" font-size="22" fill="#fff" text-anchor="middle">${label}</text></svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

const MOCK_MEDIA = [
  {
    mediaId: 2001, originalFileName: '发票-20261103.png', contentType: 'image/png', size: 184320,
    width: 640, height: 480, contentPath: '/api/media/2001/content', version: 1,
    _dataUrl: mockImageDataUrl('2001invoice', '采购发票')
  },
  {
    mediaId: 2002, originalFileName: '订单截图-1688.png', contentType: 'image/png', size: 125660,
    width: 640, height: 480, contentPath: '/api/media/2002/content', version: 1,
    _dataUrl: mockImageDataUrl('2002order', '订单截图')
  },
  {
    mediaId: 2003, originalFileName: '到货照片-纸箱.jpg', contentType: 'image/jpeg', size: 208940,
    width: 640, height: 480, contentPath: '/api/media/2003/content', version: 1,
    _dataUrl: mockImageDataUrl('2003receipt', '到货照片')
  },
  {
    mediaId: 2004, originalFileName: '包装照片.jpg', contentType: 'image/jpeg', size: 198320,
    width: 640, height: 480, contentPath: '/api/media/2004/content', version: 1,
    _dataUrl: mockImageDataUrl('2004package', '包装照片')
  },
  {
    mediaId: 2005, originalFileName: '发票-A202611050001.pdf.png', contentType: 'image/png', size: 164220,
    width: 640, height: 480, contentPath: '/api/media/2005/content', version: 1,
    _dataUrl: mockImageDataUrl('2005invoice', '授权经销商发票')
  },
  {
    mediaId: 2006, originalFileName: '杜邦线订单.png', contentType: 'image/png', size: 142880,
    width: 640, height: 480, contentPath: '/api/media/2006/content', version: 1,
    _dataUrl: mockImageDataUrl('2006order', '杜邦线订单')
  }
]

const MOCK_ATTACHMENTS = [
  {
    id: 2101, ownerType: ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER, ownerId: 905, mediaId: 2001,
    purpose: ATTACHMENT_PURPOSE.INVOICE, invoiceNo: 'FP-20261103-001', note: '补货发票',
    version: 1, createdBy: 3, createTime: '2026-11-05T15:20:00', deletedBy: null, deletedAt: null
  },
  {
    id: 2102, ownerType: ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER, ownerId: 905, mediaId: 2002,
    purpose: ATTACHMENT_PURPOSE.ORDER_SCREENSHOT, invoiceNo: null, note: '淘宝订单页截图',
    version: 1, createdBy: 2, createTime: '2026-11-03T16:10:00', deletedBy: null, deletedAt: null
  },
  {
    id: 2103, ownerType: ATTACHMENT_OWNER_TYPE.PROCUREMENT_RECEIPT, ownerId: 1001, mediaId: 2003,
    purpose: ATTACHMENT_PURPOSE.RECEIPT_PHOTO, invoiceNo: null, note: '第一箱外观',
    version: 1, createdBy: 3, createTime: '2026-11-05T14:35:00', deletedBy: null, deletedAt: null
  },
  {
    id: 2104, ownerType: ATTACHMENT_OWNER_TYPE.PROCUREMENT_RECEIPT, ownerId: 1001, mediaId: 2004,
    purpose: ATTACHMENT_PURPOSE.PACKAGE_PHOTO, invoiceNo: null, note: '包装标签',
    version: 1, createdBy: 3, createTime: '2026-11-05T14:36:00', deletedBy: null, deletedAt: null
  },
  {
    id: 2105, ownerType: ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER, ownerId: 904, mediaId: 2005,
    purpose: ATTACHMENT_PURPOSE.INVOICE, invoiceNo: 'A-20261105-88', note: '授权经销商发票',
    version: 1, createdBy: 21, createTime: '2026-11-04T11:40:00', deletedBy: null, deletedAt: null
  },
  {
    id: 2106, ownerType: ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER, ownerId: 910, mediaId: 2006,
    purpose: ATTACHMENT_PURPOSE.ORDER_SCREENSHOT, invoiceNo: null, note: '下单页截图',
    version: 1, createdBy: 3, createTime: '2026-11-06T11:10:00', deletedBy: null, deletedAt: null
  }
]

let mockSequence = 3000

function nextMockId(prefix = 0) {
  mockSequence += 1
  return prefix ? prefix * 1000 + (mockSequence % 1000) : mockSequence
}

function clone(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value))
}

function findMockOrder(orderId) {
  return MOCK_ORDERS.find((order) => order.id === Number(orderId)) || null
}

function findMockReceipt(receiptId) {
  return MOCK_RECEIPTS.find((receipt) => receipt.id === Number(receiptId)) || null
}

function findMockMedia(mediaId) {
  return MOCK_MEDIA.find((media) => media.mediaId === Number(mediaId)) || null
}

function findMockAttachment(attachmentId) {
  return MOCK_ATTACHMENTS.find((attachment) => attachment.id === Number(attachmentId) && !attachment.deletedAt) || null
}

function currentMockScenarioState() {
  if (!isMockScenario('empty')
    && !isMockScenario('loading')
    && !isMockScenario('error')
    && !isMockScenario('permission')
    && !isMockScenario('conflict')
    && !isMockScenario('success')
    && !isMockScenario('partial-received')
    && !isMockScenario('transaction-failed')) {
    return ''
  }
  return ['loading', 'empty', 'error', 'permission', 'conflict', 'success', 'partial-received', 'transaction-failed']
    .find((state) => isMockScenario(state)) || ''
}

function errorWithTrace(code, msg, reason, data = null) {
  return mockReject(code, msg, reason, { ...(data || {}), traceId: mockTraceId('w45') })
}

function scenarioConflict(currentVersion) {
  if (isMockScenario('conflict')) {
    return errorWithTrace(409, '数据已被他人更新，请刷新后重新确认', 'VERSION_CONFLICT', { currentVersion })
  }
  return null
}

function orderVisibilityError() {
  return errorWithTrace(404, '采购单不存在或不可见', 'PROCUREMENT_ORDER_NOT_FOUND', null)
}

function orderVisible(order) {
  return Boolean(order) && canViewOrder(order)
}

function requireVisibleOrder(orderId) {
  const order = findMockOrder(orderId)
  if (!order || !orderVisible(order) || isMockScenario('permission')) return null
  return order
}

function receiptOwnerOrder(receipt) {
  return receipt ? findMockOrder(receipt.orderId) : null
}

function attachmentVisible(ownerType, ownerId) {
  if (isMockScenario('permission')) return false
  if (ownerType === ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER) {
    const order = findMockOrder(ownerId)
    return orderVisible(order)
  }
  const receipt = findMockReceipt(ownerId)
  return orderVisible(receiptOwnerOrder(receipt))
}

function ownerVersionOf(ownerType, ownerId) {
  if (ownerType === ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER) {
    return findMockOrder(ownerId)?.version ?? null
  }
  return findMockReceipt(ownerId)?.version ?? null
}

function publicAttachment(attachment) {
  if (!attachment) return null
  const media = findMockMedia(attachment.mediaId)
  if (!media) return null
  return {
    id: attachment.id,
    ownerType: attachment.ownerType,
    ownerId: attachment.ownerId,
    mediaId: attachment.mediaId,
    purpose: attachment.purpose,
    invoiceNo: attachment.invoiceNo || null,
    note: attachment.note || null,
    version: attachment.version,
    createdBy: attachment.createdBy,
    createTime: attachment.createTime,
    deletedBy: attachment.deletedBy || null,
    deletedAt: attachment.deletedAt || null,
    originalFileName: media.originalFileName,
    contentType: media.contentType,
    size: media.size,
    width: media.width,
    height: media.height,
    contentPath: media.contentPath,
    mediaVersion: media.version
  }
}

function publicMedia(media) {
  if (!media) return null
  return {
    mediaId: media.mediaId,
    originalFileName: media.originalFileName,
    contentType: media.contentType,
    size: media.size,
    width: media.width,
    height: media.height,
    contentPath: media.contentPath,
    version: media.version
  }
}

function normalizeOrderLine(line) {
  const copy = clone(line)
  copy.remainingQuantity = round(Number(copy.orderedQuantity || 0) - Number(copy.receivedQuantity || 0) - Number(copy.canceledQuantity || 0), QUANTITY_SCALE)
  copy.progressPercent = lineProgressPercent(copy)
  copy.lineState = lineStateOf(copy)
  return copy
}

function normalizeOrderDetail(order) {
  const copy = clone(order)
  copy.lines = (order.lines || []).map(normalizeOrderLine)
  return copy
}

function normalizeOrderSummary(order) {
  const copy = clone(order)
  delete copy.lines
  delete copy.approvalRecords
  return copy
}

function normalizeReceiptDetail(receipt) {
  const copy = clone(receipt)
  copy.lines = (receipt.lines || []).map((line) => clone(line))
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

// ========== Mock 命令执行 ==========

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

function writeGuard() {
  if (isMockScenario('permission')) {
    return errorWithTrace(403, '当前验收场景无采购操作权限', 'PROCUREMENT_OPERATION_DENIED', null)
  }
  if (!canAccessProcurement()) {
    return errorWithTrace(403, '当前账号没有采购操作权限', 'PROCUREMENT_OPERATION_DENIED', null)
  }
  return null
}

function validateOrderPayload(payload) {
  const title = String(payload?.title || '').trim()
  if (title.length < 2 || title.length > 120) {
    return errorWithTrace(400, '标题需为 2-120 个字符', 'VALIDATION_ERROR', { field: 'title' })
  }
  if (Number(payload?.shippingAmount || 0) < 0 || Number(payload?.discountAmount || 0) < 0) {
    return errorWithTrace(400, '运费与优惠不能为负数', 'VALIDATION_ERROR', { field: 'amount' })
  }
  const rawLines = Array.isArray(payload?.lines) ? payload.lines : []
  if (!rawLines.length) {
    return errorWithTrace(400, '至少需要一条采购明细', 'VALIDATION_ERROR', { field: 'lines' })
  }
  const seen = new Set()
  for (let index = 0; index < rawLines.length; index += 1) {
    const raw = rawLines[index]
    const componentId = Number(raw?.componentId)
    if (!componentId || seen.has(componentId)) {
      return errorWithTrace(400, '同一采购单内元器件不能重复', 'VALIDATION_ERROR', { field: `lines[${index}].componentId` })
    }
    seen.add(componentId)
    const quantity = Number(raw?.orderedQuantity)
    const price = Number(raw?.unitPrice)
    if (!Number.isFinite(quantity) || quantity <= 0) {
      return errorWithTrace(400, '采购数量必须大于 0', 'VALIDATION_ERROR', { field: `lines[${index}].orderedQuantity` })
    }
    if (!Number.isFinite(price) || price < 0) {
      return errorWithTrace(400, '单价不能为负数', 'VALIDATION_ERROR', { field: `lines[${index}].unitPrice` })
    }
    if (!findMockComponent(componentId)) {
      return errorWithTrace(400, '元器件不可用或已停用', 'COMPONENT_NOT_ACTIVE', { componentId })
    }
  }
  const shipping = round(Number(payload?.shippingAmount || 0), AMOUNT_SCALE)
  const discount = round(Number(payload?.discountAmount || 0), AMOUNT_SCALE)
  const itemSubtotal = round(sum(rawLines.map((line) => Number(line.orderedQuantity) * Number(line.unitPrice))), AMOUNT_SCALE)
  if (round(itemSubtotal + shipping - discount, AMOUNT_SCALE) < 0) {
    return errorWithTrace(400, '应付金额不能为负数', 'VALIDATION_ERROR', { field: 'payableAmount' })
  }
  return null
}

function linePayloadOf(order, payload) {
  const lines = payload.lines.map((raw, index) => makeMockLine(order.id, index + 1, raw.componentId, raw.orderedQuantity, raw.unitPrice))
  return lines
}

function nextOrderId() {
  return Math.max(...MOCK_ORDERS.map((order) => order.id), 900) + 1
}

function purchaseNoFor(orderId) {
  return `PO-20261107-${String(orderId).padStart(6, '0')}`
}

function newOrderRecord(payload) {
  const id = nextOrderId()
  const order = {
    id,
    purchaseNo: purchaseNoFor(id),
    title: String(payload.title).trim(),
    requesterId: currentUserId() ?? 1,
    requesterName: userName(currentUserId() ?? 1),
    buyerId: null,
    buyerName: null,
    projectId: payload.projectId ?? null,
    projectName: payload.projectId ? (MOCK_PROJECT_NAMES[Number(payload.projectId)] || `项目 ${payload.projectId}`) : null,
    fundingSource: payload.fundingSource || null,
    supplierName: null,
    platformType: null,
    platformOrderNo: null,
    currency: 'CNY',
    shippingAmount: round(Number(payload.shippingAmount || 0), AMOUNT_SCALE),
    discountAmount: round(Number(payload.discountAmount || 0), AMOUNT_SCALE),
    actualPaidAmount: null,
    paymentDifferenceNote: null,
    status: ORDER_STATUS.DRAFT,
    approvalRequired: null,
    approvalPolicyVersion: null,
    approvalReasons: [],
    orderedAt: null,
    completedAt: null,
    closeReason: null,
    version: 1,
    createdBy: currentUserId() ?? 1,
    createTime: nowIso(),
    updatedBy: currentUserId() ?? 1,
    updateTime: nowIso(),
    approvalRecords: [],
    lines: []
  }
  order.lines = linePayloadOf(order, payload)
  computeOrderAmounts(order)
  MOCK_ORDERS.unshift(order)
  return order
}

// ========== 策略 ==========

export function getPolicy() {
  if (!getMockEnabled()) return request.get('/api/procurement/policy')
  if (!canAccessProcurement()) return errorWithTrace(403, '当前账号没有采购访问权限', 'PROCUREMENT_OPERATION_DENIED', null)
  const scenario = currentMockScenarioState()
  if (scenario === 'permission') return errorWithTrace(403, '当前验收场景无采购访问权限', 'PROCUREMENT_OPERATION_DENIED', null)
  if (scenario === 'error') return errorWithTrace(500, '策略加载失败', 'INTERNAL_ERROR', null)
  const run = () => clone(MOCK_POLICIES[MOCK_POLICIES.length - 1])
  return scenario === 'loading' ? mockDelay(1000).then(run) : Promise.resolve(run())
}

export function listPolicyVersions(params = {}) {
  if (!getMockEnabled()) return request.get('/api/procurement/policy/versions', { params })
  if (!canAccessProcurement()) return errorWithTrace(403, '当前账号没有采购访问权限', 'PROCUREMENT_OPERATION_DENIED', null)
  if (isMockScenario('error')) return errorWithTrace(500, '策略版本加载失败', 'INTERNAL_ERROR', null)
  const list = clone([...MOCK_POLICIES].reverse())
  const run = () => pageOf(list, params)
  return isMockScenario('loading') ? mockDelay(1000).then(run) : Promise.resolve(run())
}

export function updatePolicy(payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.put('/api/procurement/policy', payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  if (!canUpdatePolicy()) return errorWithTrace(403, '只有系统管理员可以修改采购审批策略', 'PROCUREMENT_POLICY_DENIED', null)
  if (isMockScenario('permission')) return errorWithTrace(403, '当前验收场景无策略修改权限', 'PROCUREMENT_POLICY_DENIED', null)
  return withIdempotency('PUT /api/procurement/policy', idempotencyKey, payload, () => {
    const conflict = scenarioConflict(MOCK_POLICIES[MOCK_POLICIES.length - 1].policyVersion)
    if (conflict) return conflict
    const current = MOCK_POLICIES[MOCK_POLICIES.length - 1]
    if (Number(payload?.currentPolicyVersion) !== current.policyVersion) {
      return errorWithTrace(409, '策略已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: current.policyVersion })
    }
    const approvalThreshold = Number(payload?.approvalThreshold)
    const valuable = Number(payload?.valuableUnitPriceThreshold)
    if (!Number.isFinite(approvalThreshold) || approvalThreshold <= 0) {
      return errorWithTrace(400, '审批阈值必须大于 0', 'VALIDATION_ERROR', { field: 'approvalThreshold' })
    }
    if (!Number.isFinite(valuable) || valuable <= 0) {
      return errorWithTrace(400, '贵重单价阈值必须大于 0', 'VALIDATION_ERROR', { field: 'valuableUnitPriceThreshold' })
    }
    const forced = Array.isArray(payload?.forcedCategoryIds) ? payload.forcedCategoryIds.map(Number) : []
    const knownCategories = new Set(MOCK_COMPONENTS.map((component) => component.categoryId))
    const invalidCategory = forced.find((categoryId) => !knownCategories.has(categoryId))
    if (invalidCategory != null) {
      return errorWithTrace(400, '指定分类必须为启用分类', 'VALIDATION_ERROR', { field: 'forcedCategoryIds' })
    }
    const next = {
      id: current.id + 1,
      policyVersion: current.policyVersion + 1,
      currency: 'CNY',
      approvalThreshold: round(approvalThreshold, AMOUNT_SCALE),
      valuableUnitPriceThreshold: round(valuable, UNIT_PRICE_SCALE),
      forcedCategoryIds: forced,
      effectiveAt: nowIso(),
      createdBy: currentUserId() ?? 1,
      createTime: nowIso()
    }
    MOCK_POLICIES.push(next)
    return clone(next)
  })
}

// ========== 元器件选择 ==========

export function searchPurchaseComponents(params = {}) {
  if (!getMockEnabled()) {
    return request.get('/api/components', {
      params: { status: 'ACTIVE', page: 1, pageSize: 20, keyword: params.keyword || '' }
    })
  }
  const keyword = String(params.keyword || '').trim().toLowerCase()
  const list = MOCK_COMPONENTS
    .filter((component) => !keyword
      || component.name.toLowerCase().includes(keyword)
      || component.mpn.toLowerCase().includes(keyword)
      || component.manufacturerName.toLowerCase().includes(keyword))
    .map(publicMockComponent)
  return Promise.resolve({ list, total: list.length, page: 1, pageSize: 20 })
}

// ========== 采购单查询 ==========

export function listOrders(params = {}) {
  if (!getMockEnabled()) return request.get('/api/procurement-orders', { params })
  if (!canAccessProcurement()) return errorWithTrace(403, '当前账号没有采购访问权限', 'PROCUREMENT_OPERATION_DENIED', null)
  const scenario = currentMockScenarioState()
  if (scenario === 'permission') return errorWithTrace(403, '当前验收场景无采购访问权限', 'PROCUREMENT_OPERATION_DENIED', null)
  if (scenario === 'error') return errorWithTrace(500, '采购单列表加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    if (scenario === 'empty') return pageOf([], params)
    let list = MOCK_ORDERS.filter(orderVisible)
    if (scenario === 'partial-received') list = list.filter((order) => order.status === ORDER_STATUS.PARTIALLY_RECEIVED)
    if (params.status) list = list.filter((order) => order.status === params.status)
    if (params.projectId) list = list.filter((order) => Number(order.projectId) === Number(params.projectId))
    if (params.requesterId) list = list.filter((order) => Number(order.requesterId) === Number(params.requesterId))
    const keyword = String(params.keyword || '').trim().toLowerCase()
    if (keyword) {
      list = list.filter((order) => [
        order.purchaseNo,
        order.title,
        order.supplierName,
        order.platformOrderNo
      ].filter(Boolean).some((value) => String(value).toLowerCase().includes(keyword)))
    }
    const supplierKeyword = String(params.supplierKeyword || '').trim().toLowerCase()
    if (supplierKeyword) {
      list = list.filter((order) => String(order.supplierName || '').toLowerCase().includes(supplierKeyword))
    }
    const sort = String(params.sort || 'updateTime,desc').split(',')
    list = [...list].sort((a, b) => {
      const left = a[sort[0]] ?? ''
      const right = b[sort[0]] ?? ''
      const result = left > right ? 1 : left < right ? -1 : 0
      return sort[1] === 'asc' ? result : -result
    })
    return pageOf(list.map(normalizeOrderSummary), params)
  }
  return scenario === 'loading' ? mockDelay(1400).then(run) : Promise.resolve(run())
}

export function getOrder(orderId) {
  if (!getMockEnabled()) return request.get(`/api/procurement-orders/${orderId}`)
  if (!canAccessProcurement()) return errorWithTrace(403, '当前账号没有采购访问权限', 'PROCUREMENT_OPERATION_DENIED', null)
  const scenario = currentMockScenarioState()
  if (scenario === 'error') return errorWithTrace(500, '采购单详情加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    const order = requireVisibleOrder(orderId)
    if (!order) return orderVisibilityError()
    return normalizeOrderDetail(order)
  }
  return scenario === 'loading' ? mockDelay(1400).then(run) : Promise.resolve(run())
}

export function createOrder(payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post('/api/procurement-orders', payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency('POST /api/procurement-orders', idempotencyKey, payload, () => {
    const invalid = validateOrderPayload(payload)
    if (invalid) return invalid
    const order = newOrderRecord(payload)
    return normalizeOrderDetail(order)
  })
}

export function updateOrder(orderId, payload) {
  if (!getMockEnabled()) return request.put(`/api/procurement-orders/${orderId}`, payload)
  const guard = writeGuard()
  if (guard) return guard
  const order = requireVisibleOrder(orderId)
  if (!order) return orderVisibilityError()
  if (!canEditOrder(order)) {
    return errorWithTrace(403, '仅草稿的申请人、库存管理员或系统管理员可编辑', 'PROCUREMENT_OPERATION_DENIED', null)
  }
  const conflict = scenarioConflict(order.version)
  if (conflict) return conflict
  if (Number(payload?.version) !== order.version) {
    return errorWithTrace(409, '数据已被他人更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: order.version })
  }
  const invalid = validateOrderPayload(payload)
  if (invalid) return invalid
  order.title = String(payload.title).trim()
  order.projectId = payload.projectId ?? null
  order.projectName = payload.projectId ? (MOCK_PROJECT_NAMES[Number(payload.projectId)] || `项目 ${payload.projectId}`) : null
  order.fundingSource = payload.fundingSource || null
  order.shippingAmount = round(Number(payload.shippingAmount || 0), AMOUNT_SCALE)
  order.discountAmount = round(Number(payload.discountAmount || 0), AMOUNT_SCALE)
  order.lines = linePayloadOf(order, payload)
  computeOrderAmounts(order)
  order.version += 1
  order.updatedBy = currentUserId() ?? order.updatedBy
  order.updateTime = nowIso()
  return normalizeOrderDetail(order)
}

function versionConflict(order, payload) {
  if (Number(payload?.version) !== order.version) {
    return errorWithTrace(409, '数据已被他人更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: order.version })
  }
  return null
}

function invalidTransition(from, action, reason = 'INVALID_TRANSITION') {
  return errorWithTrace(409, `当前状态 ${from} 不允许执行 ${action}`, reason, { status: from, action })
}

function touchOrder(order) {
  order.version += 1
  order.updatedBy = currentUserId() ?? order.updatedBy
  order.updateTime = nowIso()
  return order
}

function evaluateApproval(order) {
  const policy = MOCK_POLICIES[MOCK_POLICIES.length - 1]
  const reasons = []
  if (order.payableAmount >= Number(policy.approvalThreshold)) {
    reasons.push(APPROVAL_REASON.AMOUNT_THRESHOLD)
  }
  if (order.lines.some((line) => Number(line.unitPrice) >= Number(policy.valuableUnitPriceThreshold))) {
    reasons.push(APPROVAL_REASON.VALUABLE_ITEM)
  }
  const forced = new Set(policy.forcedCategoryIds.map(Number))
  if (order.lines.some((line) => forced.has(Number(line.categoryId)))) {
    reasons.push(APPROVAL_REASON.FORCED_CATEGORY)
  }
  return {
    policy,
    approvalRequired: reasons.length > 0,
    reasons: reasons.length ? reasons : [APPROVAL_REASON.EXEMPT]
  }
}

function approvalSnapshotOf(order) {
  return {
    payableAmount: round(order.payableAmount, AMOUNT_SCALE),
    reasons: clone(order.approvalReasons),
    lines: order.lines.map((line) => ({
      componentId: line.componentId,
      lineSubtotal: line.lineSubtotal,
      unitPrice: line.unitPrice,
      categoryId: line.categoryId
    }))
  }
}

export function submitOrder(orderId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/procurement-orders/${orderId}/submit`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/procurement-orders/${orderId}/submit`, idempotencyKey, payload, () => {
    const order = requireVisibleOrder(orderId)
    if (!order) return orderVisibilityError()
    if (!canSubmitOrder(order)) {
      return errorWithTrace(403, '仅申请人或系统管理员可提交草稿', 'PROCUREMENT_OPERATION_DENIED', null)
    }
    const conflict = scenarioConflict(order.version)
    if (conflict) return conflict
    const versionError = versionConflict(order, payload)
    if (versionError) return versionError
    if (order.status !== ORDER_STATUS.DRAFT) return invalidTransition(order.status, 'submit')
    const invalid = validateOrderPayload({
      title: order.title,
      shippingAmount: order.shippingAmount,
      discountAmount: order.discountAmount,
      lines: order.lines.map((line) => ({
        componentId: line.componentId,
        orderedQuantity: line.orderedQuantity,
        unitPrice: line.unitPrice
      }))
    })
    if (invalid) return invalid
    computeOrderAmounts(order)
    const evaluation = evaluateApproval(order)
    order.approvalRequired = evaluation.approvalRequired
    order.approvalPolicyVersion = evaluation.policy.policyVersion
    order.approvalReasons = evaluation.reasons
    if (evaluation.approvalRequired) {
      order.status = ORDER_STATUS.PENDING_APPROVAL
      order.approvalRecords = []
    } else {
      order.status = ORDER_STATUS.APPROVED
      order.approvalRecords = [
        approvalRecord(
          order.id,
          APPROVAL_DECISION.AUTO_APPROVED,
          evaluation.policy.policyVersion,
          null,
          nowIso(),
          null,
          order.payableAmount,
          evaluation.reasons
        )
      ]
      order.approvalRecords[0].triggerSnapshot = approvalSnapshotOf(order)
    }
    touchOrder(order)
    return normalizeOrderDetail(order)
  })
}

export function approveOrder(orderId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/procurement-orders/${orderId}/approve`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/procurement-orders/${orderId}/approve`, idempotencyKey, payload, () => {
    const order = requireVisibleOrder(orderId)
    if (!order) return orderVisibilityError()
    if (!canApproveOrder(order)) {
      return errorWithTrace(403, '审批人不能是申请人或买手，且仅老师/管理员可审批', 'PROCUREMENT_APPROVAL_DENIED', null)
    }
    const conflict = scenarioConflict(order.version)
    if (conflict) return conflict
    const versionError = versionConflict(order, payload)
    if (versionError) return versionError
    if (order.status !== ORDER_STATUS.PENDING_APPROVAL) return invalidTransition(order.status, 'approve')
    if (payload?.comment && String(payload.comment).length > 500) {
      return errorWithTrace(400, '审批意见不能超过 500 字', 'VALIDATION_ERROR', { field: 'comment' })
    }
    order.status = ORDER_STATUS.APPROVED
    order.approvalRecords = [
      {
        ...approvalRecord(
          order.id,
          APPROVAL_DECISION.APPROVED,
          order.approvalPolicyVersion,
          currentUserId(),
          nowIso(),
          payload?.comment || null,
          order.payableAmount,
          order.approvalReasons
        ),
        triggerSnapshot: approvalSnapshotOf(order)
      }
    ]
    touchOrder(order)
    return normalizeOrderDetail(order)
  })
}

export function rejectOrder(orderId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/procurement-orders/${orderId}/reject`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/procurement-orders/${orderId}/reject`, idempotencyKey, payload, () => {
    const order = requireVisibleOrder(orderId)
    if (!order) return orderVisibilityError()
    if (!canApproveOrder(order)) {
      return errorWithTrace(403, '审批人不能是申请人或买手，且仅老师/管理员可审批', 'PROCUREMENT_APPROVAL_DENIED', null)
    }
    const conflict = scenarioConflict(order.version)
    if (conflict) return conflict
    const versionError = versionConflict(order, payload)
    if (versionError) return versionError
    if (order.status !== ORDER_STATUS.PENDING_APPROVAL) return invalidTransition(order.status, 'reject')
    const comment = String(payload?.comment || '').trim()
    if (!comment || comment.length > 500) {
      return errorWithTrace(400, '拒绝时必须填写 1-500 字原因', 'VALIDATION_ERROR', { field: 'comment' })
    }
    order.status = ORDER_STATUS.REJECTED
    order.closeReason = comment
    order.approvalRecords = [
      {
        ...approvalRecord(
          order.id,
          APPROVAL_DECISION.REJECTED,
          order.approvalPolicyVersion,
          currentUserId(),
          nowIso(),
          comment,
          order.payableAmount,
          order.approvalReasons
        ),
        triggerSnapshot: approvalSnapshotOf(order)
      }
    ]
    touchOrder(order)
    return normalizeOrderDetail(order)
  })
}

export function markOrdered(orderId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/procurement-orders/${orderId}/mark-ordered`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/procurement-orders/${orderId}/mark-ordered`, idempotencyKey, payload, () => {
    const order = requireVisibleOrder(orderId)
    if (!order) return orderVisibilityError()
    if (!canMarkOrdered(order)) {
      return errorWithTrace(403, '仅库存管理员或系统管理员可登记下单', 'PROCUREMENT_OPERATION_DENIED', null)
    }
    const conflict = scenarioConflict(order.version)
    if (conflict) return conflict
    const versionError = versionConflict(order, payload)
    if (versionError) return versionError
    if (order.status !== ORDER_STATUS.APPROVED) return invalidTransition(order.status, 'mark-ordered')
    const approved = order.approvalRecords.find((record) => [
      APPROVAL_DECISION.APPROVED,
      APPROVAL_DECISION.AUTO_APPROVED
    ].includes(record.decision))
    if (order.approvalRequired && !approved) {
      return errorWithTrace(409, '必须先获得有效审批才能登记下单', 'PROCUREMENT_APPROVAL_REQUIRED', null)
    }
    const buyerId = Number(payload?.buyerId)
    if (!buyerId || buyerId <= 0) {
      return errorWithTrace(400, '买手必须为有效成员', 'VALIDATION_ERROR', { field: 'buyerId' })
    }
    const supplierName = String(payload?.supplierName || '').trim()
    if (supplierName.length < 2 || supplierName.length > 120) {
      return errorWithTrace(400, '供应商名称需为 2-120 个字符', 'VALIDATION_ERROR', { field: 'supplierName' })
    }
    if (!Object.values(PLATFORM_TYPE).includes(payload?.platformType)) {
      return errorWithTrace(400, '下单平台不合法', 'VALIDATION_ERROR', { field: 'platformType' })
    }
    const actualPaid = Number(payload?.actualPaidAmount)
    if (!Number.isFinite(actualPaid) || actualPaid < 0) {
      return errorWithTrace(400, '实际实付金额不能为负', 'VALIDATION_ERROR', { field: 'actualPaidAmount' })
    }
    if (round(actualPaid, AMOUNT_SCALE) > order.payableAmount) {
      return errorWithTrace(400, '实际实付不能超过应付金额', 'VALIDATION_ERROR', { field: 'actualPaidAmount' })
    }
    const differenceNote = String(payload?.paymentDifferenceNote || '').trim()
    if (round(actualPaid, AMOUNT_SCALE) < order.payableAmount && !differenceNote) {
      return errorWithTrace(400, '实付与应付不一致时必须填写差异原因', 'PAYMENT_DIFFERENCE_NOTE_REQUIRED', { field: 'paymentDifferenceNote' })
    }
    const humanApprover = approved?.actorType === 'USER' ? Number(approved.decidedBy) : null
    if (humanApprover != null && humanApprover === buyerId) {
      return errorWithTrace(409, '买手不能同时是人工审批人', 'PROCUREMENT_DUTY_CONFLICT', null)
    }
    const platformOrderNo = String(payload?.platformOrderNo || '').trim().replace(/\s+/g, '').toLowerCase()
    if (platformOrderNo) {
      const duplicate = MOCK_ORDERS.find((other) => other.id !== order.id
        && other.platformType === payload.platformType
        && String(other.platformOrderNo || '').trim().replace(/\s+/g, '').toLowerCase() === platformOrderNo)
      if (duplicate) {
        return errorWithTrace(409, '该平台订单号已登记在另一采购单', 'PLATFORM_ORDER_NO_DUPLICATED', { orderId: duplicate.id })
      }
    }
    order.buyerId = buyerId
    order.buyerName = userName(buyerId)
    order.supplierName = supplierName
    order.platformType = payload.platformType
    order.platformOrderNo = payload?.platformOrderNo || null
    order.actualPaidAmount = round(actualPaid, AMOUNT_SCALE)
    order.paymentDifferenceNote = differenceNote || null
    order.status = ORDER_STATUS.ORDERED
    order.orderedAt = nowIso()
    allocateOrderAmounts(order)
    touchOrder(order)
    return normalizeOrderDetail(order)
  })
}

function orderHasConfirmedReceipt(order) {
  return MOCK_RECEIPTS.some((receipt) => receipt.orderId === order.id && receipt.status === RECEIPT_STATUS.CONFIRMED)
}

function orderHasDraftReceipt(order) {
  return MOCK_RECEIPTS.some((receipt) => receipt.orderId === order.id && receipt.status === RECEIPT_STATUS.DRAFT)
}

export function cancelOrder(orderId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/procurement-orders/${orderId}/cancel`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/procurement-orders/${orderId}/cancel`, idempotencyKey, payload, () => {
    const order = requireVisibleOrder(orderId)
    if (!order) return orderVisibilityError()
    if (!canCancelOrder(order)) {
      return errorWithTrace(403, '当前状态或角色不允许取消整单', 'PROCUREMENT_OPERATION_DENIED', null)
    }
    const conflict = scenarioConflict(order.version)
    if (conflict) return conflict
    const versionError = versionConflict(order, payload)
    if (versionError) return versionError
    if (order.status === ORDER_STATUS.PARTIALLY_RECEIVED) {
      return errorWithTrace(409, '部分到货订单只能取消剩余数量', 'PROCUREMENT_REMAINDER_CANCEL_REQUIRED', null)
    }
    if (order.status === ORDER_STATUS.ORDERED && orderHasConfirmedReceipt(order)) {
      return errorWithTrace(409, '已有确认到货，不能取消整单', 'ORDER_HAS_CONFIRMED_RECEIPTS', null)
    }
    if (order.status === ORDER_STATUS.ORDERED && orderHasDraftReceipt(order)) {
      return errorWithTrace(409, '存在到货草稿，请先取消草稿', 'RECEIPT_DRAFT_EXISTS', null)
    }
    if (![ORDER_STATUS.DRAFT, ORDER_STATUS.PENDING_APPROVAL, ORDER_STATUS.APPROVED, ORDER_STATUS.ORDERED].includes(order.status)) {
      return invalidTransition(order.status, 'cancel')
    }
    const reason = String(payload?.reason || '').trim()
    if (!reason || reason.length > 500) {
      return errorWithTrace(400, '取消原因需为 1-500 字', 'VALIDATION_ERROR', { field: 'reason' })
    }
    order.status = ORDER_STATUS.CANCELED
    order.closeReason = reason
    touchOrder(order)
    return normalizeOrderDetail(order)
  })
}

export function cancelRemaining(orderId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/procurement-orders/${orderId}/cancel-remaining`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/procurement-orders/${orderId}/cancel-remaining`, idempotencyKey, payload, () => {
    const order = requireVisibleOrder(orderId)
    if (!order) return orderVisibilityError()
    if (!canCancelRemaining(order)) {
      return errorWithTrace(403, '仅库存管理员/管理员可取消部分到货剩余数量', 'PROCUREMENT_OPERATION_DENIED', null)
    }
    const conflict = scenarioConflict(order.version)
    if (conflict) return conflict
    const versionError = versionConflict(order, payload)
    if (versionError) return versionError
    if (order.status !== ORDER_STATUS.PARTIALLY_RECEIVED) {
      return errorWithTrace(409, '仅部分到货订单可取消剩余数量', 'PROCUREMENT_REMAINDER_NOT_ALLOWED', null)
    }
    const reason = String(payload?.reason || '').trim()
    if (!reason || reason.length > 500) {
      return errorWithTrace(400, '取消剩余原因需为 1-500 字', 'VALIDATION_ERROR', { field: 'reason' })
    }
    order.lines.forEach((line) => {
      const remaining = round(Number(line.orderedQuantity) - Number(line.receivedQuantity) - Number(line.canceledQuantity), QUANTITY_SCALE)
      if (remaining > 0) {
        line.canceledQuantity = round(Number(line.canceledQuantity) + remaining, QUANTITY_SCALE)
        line.version = Number(line.version || 1) + 1
      }
    })
    order.status = ORDER_STATUS.COMPLETED
    order.closeReason = reason
    order.completedAt = nowIso()
    touchOrder(order)
    return normalizeOrderDetail(order)
  })
}

export function completeOrder(orderId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/procurement-orders/${orderId}/complete`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/procurement-orders/${orderId}/complete`, idempotencyKey, payload, () => {
    const order = requireVisibleOrder(orderId)
    if (!order) return orderVisibilityError()
    if (!canCompleteOrder(order)) {
      return errorWithTrace(403, '仅库存管理员/管理员可标记完成', 'PROCUREMENT_OPERATION_DENIED', null)
    }
    const conflict = scenarioConflict(order.version)
    if (conflict) return conflict
    const versionError = versionConflict(order, payload)
    if (versionError) return versionError
    if (order.status !== ORDER_STATUS.RECEIVED) return invalidTransition(order.status, 'complete')
    order.status = ORDER_STATUS.COMPLETED
    order.completedAt = nowIso()
    touchOrder(order)
    return normalizeOrderDetail(order)
  })
}

// ========== 分批到货 ==========

function validateReceiptPayload(order, payload, { forConfirm = false } = {}) {
  const receivedAt = String(payload?.receivedAt || '')
  const receivedTime = Date.parse(receivedAt)
  if (!receivedTime || receivedTime > Date.now() + 5 * 60 * 1000) {
    return errorWithTrace(400, '到货时间不能为空或未来超过 5 分钟', 'VALIDATION_ERROR', { field: 'receivedAt' })
  }
  const rawLines = Array.isArray(payload?.lines) ? payload.lines : []
  if (!rawLines.length) {
    return errorWithTrace(400, '至少需要一条到货明细', 'VALIDATION_ERROR', { field: 'lines' })
  }
  const seen = new Set()
  for (let index = 0; index < rawLines.length; index += 1) {
    const raw = rawLines[index]
    const orderLineId = Number(raw?.orderLineId)
    const orderLine = order.lines.find((line) => line.id === orderLineId)
    if (!orderLine) {
      return errorWithTrace(400, '到货明细不属于该采购单', 'VALIDATION_ERROR', { field: `lines[${index}].orderLineId` })
    }
    if (seen.has(orderLineId)) {
      return errorWithTrace(400, '同一采购明细只能出现一次', 'VALIDATION_ERROR', { field: `lines[${index}].orderLineId` })
    }
    seen.add(orderLineId)
    const quantity = Number(raw?.quantity)
    if (!Number.isFinite(quantity) || quantity <= 0) {
      return errorWithTrace(400, '到货数量必须大于 0', 'VALIDATION_ERROR', { field: `lines[${index}].quantity` })
    }
    if (raw?.expiryDate && raw?.productionDate && String(raw.expiryDate) <= String(raw.productionDate)) {
      return errorWithTrace(400, '有效期必须晚于生产日期', 'VALIDATION_ERROR', { field: `lines[${index}].expiryDate` })
    }
    const remaining = round(Number(orderLine.orderedQuantity) - Number(orderLine.receivedQuantity) - Number(orderLine.canceledQuantity), QUANTITY_SCALE)
    if (remaining <= 0) {
      return errorWithTrace(409, '该采购明细已无剩余可到货数量', 'RECEIPT_LINE_NO_REMAINING', {
        orderLineId,
        remainingQuantity: remaining
      })
    }
    if (forConfirm && quantity > remaining) {
      return errorWithTrace(409, '到货数量超过剩余可收数量', 'RECEIPT_OVER_QUANTITY', {
        orderLineId,
        remainingQuantity: remaining,
        requestedQuantity: round(quantity, QUANTITY_SCALE)
      })
    }
  }
  return null
}

function receiptLinesOf(receipt, payload) {
  return payload.lines.map((raw) => ({
    id: nextMockId(),
    receiptId: receipt.id,
    orderLineId: Number(raw.orderLineId),
    componentId: findMockOrder(receipt.orderId).lines.find((line) => line.id === Number(raw.orderLineId)).componentId,
    quantity: round(Number(raw.quantity), QUANTITY_SCALE),
    unit: findMockOrder(receipt.orderId).lines.find((line) => line.id === Number(raw.orderLineId)).unit,
    supplierLotNo: raw.supplierLotNo || null,
    productionDate: raw.productionDate || null,
    expiryDate: raw.expiryDate || null,
    locationNote: raw.locationNote || null,
    qualityNote: raw.qualityNote || null,
    version: 1
  }))
}

function nextReceiptId() {
  return Math.max(...MOCK_RECEIPTS.map((receipt) => receipt.id), 1000) + 1
}

function receiptNoFor(receiptId) {
  return `RCV-20261107-${String(receiptId).padStart(6, '0')}`
}

export function listReceipts(orderId, params = {}) {
  if (!getMockEnabled()) return request.get(`/api/procurement-orders/${orderId}/receipts`, { params })
  const order = requireVisibleOrder(orderId)
  if (!order) return orderVisibilityError()
  if (isMockScenario('error')) return errorWithTrace(500, '到货记录加载失败', 'INTERNAL_ERROR', null)
  if (isMockScenario('permission')) return orderVisibilityError()
  const run = () => {
    if (isMockScenario('empty')) return pageOf([], params)
    let list = MOCK_RECEIPTS.filter((receipt) => receipt.orderId === Number(orderId))
    if (params.status) list = list.filter((receipt) => receipt.status === params.status)
    list = [...list].sort((a, b) => (a.receivedAt > b.receivedAt ? -1 : 1))
    return pageOf(list.map((receipt) => {
      const copy = normalizeReceiptDetail(receipt)
      delete copy.lines
      return copy
    }), params)
  }
  return isMockScenario('loading') ? mockDelay(1200).then(run) : Promise.resolve(run())
}

export function getReceipt(receiptId) {
  if (!getMockEnabled()) return request.get(`/api/procurement-receipts/${receiptId}`)
  const scenario = currentMockScenarioState()
  if (scenario === 'error') return errorWithTrace(500, '到货记录详情加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    const receipt = findMockReceipt(receiptId)
    if (!receipt || !orderVisible(receiptOwnerOrder(receipt)) || isMockScenario('permission')) {
      return orderVisibilityError()
    }
    return normalizeReceiptDetail(receipt)
  }
  return scenario === 'loading' ? mockDelay(1400).then(run) : Promise.resolve(run())
}

export function createReceipt(orderId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/procurement-orders/${orderId}/receipts`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/procurement-orders/${orderId}/receipts`, idempotencyKey, payload, () => {
    const order = requireVisibleOrder(orderId)
    if (!order) return orderVisibilityError()
    if (!canCreateReceipt(order)) {
      return errorWithTrace(403, '仅库存管理员/管理员可创建到货记录', 'PROCUREMENT_OPERATION_DENIED', null)
    }
    const conflict = scenarioConflict(order.version)
    if (conflict) return conflict
    if (Number(payload?.orderVersion) !== order.version) {
      return errorWithTrace(409, '采购单已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: order.version })
    }
    if (![ORDER_STATUS.ORDERED, ORDER_STATUS.PARTIALLY_RECEIVED].includes(order.status)) {
      return invalidTransition(order.status, 'create-receipt')
    }
    if (orderHasDraftReceipt(order)) {
      return errorWithTrace(409, '同一采购单只能有一条草稿到货记录', 'RECEIPT_DRAFT_EXISTS', {
        receiptId: MOCK_RECEIPTS.find((receipt) => receipt.orderId === order.id && receipt.status === RECEIPT_STATUS.DRAFT).id
      })
    }
    const invalid = validateReceiptPayload(order, payload)
    if (invalid) return invalid
    const id = nextReceiptId()
    const receipt = {
      id,
      receiptNo: receiptNoFor(id),
      orderId: order.id,
      status: RECEIPT_STATUS.DRAFT,
      receivedAt: payload.receivedAt,
      receiverId: null,
      receiverName: null,
      note: payload.note || null,
      confirmedAt: null,
      canceledAt: null,
      version: 1,
      createdBy: currentUserId() ?? 1,
      createTime: nowIso(),
      updatedBy: currentUserId() ?? 1,
      updateTime: nowIso(),
      lines: []
    }
    receipt.lines = receiptLinesOf(receipt, payload)
    MOCK_RECEIPTS.push(receipt)
    touchOrder(order)
    return {
      receiptId: receipt.id,
      receiptNo: receipt.receiptNo,
      receiptStatus: receipt.status,
      version: receipt.version,
      orderVersion: order.version
    }
  })
}

export function updateReceipt(receiptId, payload) {
  if (!getMockEnabled()) return request.put(`/api/procurement-receipts/${receiptId}`, payload)
  const guard = writeGuard()
  if (guard) return guard
  const receipt = findMockReceipt(receiptId)
  const order = receiptOwnerOrder(receipt)
  if (!receipt || !orderVisible(order) || isMockScenario('permission')) return orderVisibilityError()
  if (!canConfirmReceipt()) {
    return errorWithTrace(403, '仅库存管理员/管理员可编辑到货草稿', 'PROCUREMENT_OPERATION_DENIED', null)
  }
  const conflict = scenarioConflict(receipt.version)
  if (conflict) return conflict
  if (receipt.status !== RECEIPT_STATUS.DRAFT) return invalidTransition(receipt.status, 'update-receipt')
  if (Number(payload?.version) !== receipt.version) {
    return errorWithTrace(409, '到货草稿已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: receipt.version })
  }
  if (Number(payload?.orderVersion) !== order.version) {
    return errorWithTrace(409, '采购单已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: order.version })
  }
  const invalid = validateReceiptPayload(order, payload)
  if (invalid) return invalid
  receipt.receivedAt = payload.receivedAt
  receipt.note = payload.note || null
  receipt.lines = receiptLinesOf(receipt, payload)
  receipt.version += 1
  receipt.updatedBy = currentUserId() ?? receipt.updatedBy
  receipt.updateTime = nowIso()
  touchOrder(order)
  return {
    receiptId: receipt.id,
    receiptNo: receipt.receiptNo,
    receiptStatus: receipt.status,
    version: receipt.version,
    orderVersion: order.version
  }
}

export function confirmReceipt(receiptId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/procurement-receipts/${receiptId}/confirm`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/procurement-receipts/${receiptId}/confirm`, idempotencyKey, payload, () => {
    const receipt = findMockReceipt(receiptId)
    const order = receiptOwnerOrder(receipt)
    if (!receipt || !orderVisible(order) || isMockScenario('permission')) return orderVisibilityError()
    if (!canConfirmReceipt()) {
      return errorWithTrace(403, '仅库存管理员/管理员可确认到货', 'PROCUREMENT_OPERATION_DENIED', null)
    }
    const conflict = scenarioConflict(receipt.version)
    if (conflict) return conflict
    if (receipt.status !== RECEIPT_STATUS.DRAFT) {
      return errorWithTrace(409, '到货记录已确认或已取消', 'RECEIPT_ALREADY_CONFIRMED', {
        receiptStatus: receipt.status,
        version: receipt.version
      })
    }
    if (Number(payload?.receiptVersion) !== receipt.version) {
      return errorWithTrace(409, '到货草稿已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: receipt.version })
    }
    if (Number(payload?.orderVersion) !== order.version) {
      return errorWithTrace(409, '采购单已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: order.version })
    }
    if (![ORDER_STATUS.ORDERED, ORDER_STATUS.PARTIALLY_RECEIVED].includes(order.status)) {
      return invalidTransition(order.status, 'confirm-receipt')
    }
    const invalid = validateReceiptPayload(order, {
      receivedAt: receipt.receivedAt,
      lines: receipt.lines
    }, { forConfirm: true })
    if (invalid) return invalid
    if (isMockScenario('transaction-failed')) {
      return errorWithTrace(500, '入库事务失败，未产生批次、流水或数量变化', 'PROCUREMENT_TRANSACTION_FAILED', {
        receiptId: receipt.id,
        orderId: order.id,
        retryable: true
      })
    }
    const confirmedAt = nowIso()
    const requestId = `mock-confirm-${receipt.id}-${idempotencyKey}`
    const batches = []
    const movements = []
    receipt.lines.forEach((receiptLine) => {
      const orderLine = order.lines.find((line) => line.id === receiptLine.orderLineId)
      const quantity = round(Number(receiptLine.quantity), QUANTITY_SCALE)
      const batchId = nextMockId()
      const batchNo = `LOT-20261107-${String(batchId).padStart(6, '0')}`
      const batch = {
        id: batchId,
        batchNo,
        componentId: receiptLine.componentId,
        sourceReceiptLineId: receiptLine.id,
        initialQuantity: quantity,
        currentQuantity: quantity,
        unit: receiptLine.unit,
        unitCost: orderLine.effectiveUnitCost ?? round(orderLine.unitPrice, UNIT_PRICE_SCALE),
        supplierLotNo: receiptLine.supplierLotNo,
        productionDate: receiptLine.productionDate,
        expiryDate: receiptLine.expiryDate,
        locationNote: receiptLine.locationNote,
        qualityNote: receiptLine.qualityNote,
        status: 'AVAILABLE',
        createdBy: currentUserId() ?? 1,
        createTime: confirmedAt
      }
      MOCK_BATCHES.push(batch)
      batches.push(batch)
      const balance = MOCK_BALANCES.find((row) => row.componentId === receiptLine.componentId)
      const before = round(balance?.onHandQuantity || 0, QUANTITY_SCALE)
      const after = round(before + quantity, QUANTITY_SCALE)
      if (balance) {
        balance.onHandQuantity = after
        balance.version += 1
        balance.updateTime = confirmedAt
      } else {
        MOCK_BALANCES.push({
          componentId: receiptLine.componentId,
          onHandQuantity: after,
          unit: receiptLine.unit,
          version: 1,
          updateTime: confirmedAt
        })
      }
      const movementId = nextMockId()
      const movement = {
        id: movementId,
        movementNo: `MOV-20261107-${String(movementId).padStart(6, '0')}`,
        movementType: 'PROCUREMENT_RECEIPT_IN',
        componentId: receiptLine.componentId,
        batchId,
        sourceType: 'RECEIPT_LINE',
        sourceId: receiptLine.id,
        quantityDelta: quantity,
        balanceBefore: before,
        balanceAfter: after,
        unit: receiptLine.unit,
        unitCost: batch.unitCost,
        operatorId: currentUserId() ?? 1,
        occurredAt: confirmedAt,
        requestId
      }
      MOCK_MOVEMENTS.push(movement)
      movements.push(movement)
      orderLine.receivedQuantity = round(Number(orderLine.receivedQuantity) + quantity, QUANTITY_SCALE)
      orderLine.version = Number(orderLine.version || 1) + 1
      receiptLine.version = Number(receiptLine.version || 1) + 1
    })
    receipt.status = RECEIPT_STATUS.CONFIRMED
    receipt.receiverId = currentUserId() ?? 1
    receipt.receiverName = userName(receipt.receiverId)
    receipt.confirmedAt = confirmedAt
    receipt.version += 1
    receipt.updatedBy = currentUserId() ?? receipt.updatedBy
    receipt.updateTime = confirmedAt
    const allReceived = order.lines.every((line) => Number(line.receivedQuantity) >= Number(line.orderedQuantity))
    order.status = allReceived ? ORDER_STATUS.RECEIVED : ORDER_STATUS.PARTIALLY_RECEIVED
    touchOrder(order)
    return {
      receiptId: receipt.id,
      receiptNo: receipt.receiptNo,
      receiptStatus: receipt.status,
      orderId: order.id,
      orderStatus: order.status,
      orderVersion: order.version,
      batches: batches.map((batch) => ({
        batchId: batch.id,
        batchNo: batch.batchNo,
        componentId: batch.componentId,
        quantity: batch.initialQuantity,
        unit: batch.unit,
        unitCost: batch.unitCost
      })),
      movements: movements.map((movement) => ({
        movementId: movement.id,
        movementNo: movement.movementNo,
        quantityDelta: movement.quantityDelta,
        balanceBefore: movement.balanceBefore,
        balanceAfter: movement.balanceAfter
      }))
    }
  })
}

export function cancelReceipt(receiptId, payload, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.post(`/api/procurement-receipts/${receiptId}/cancel`, payload, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/procurement-receipts/${receiptId}/cancel`, idempotencyKey, payload, () => {
    const receipt = findMockReceipt(receiptId)
    const order = receiptOwnerOrder(receipt)
    if (!receipt || !orderVisible(order) || isMockScenario('permission')) return orderVisibilityError()
    if (!canConfirmReceipt()) {
      return errorWithTrace(403, '仅库存管理员/管理员可取消到货草稿', 'PROCUREMENT_OPERATION_DENIED', null)
    }
    const conflict = scenarioConflict(receipt.version)
    if (conflict) return conflict
    if (receipt.status !== RECEIPT_STATUS.DRAFT) return invalidTransition(receipt.status, 'cancel-receipt')
    if (Number(payload?.receiptVersion) !== receipt.version) {
      return errorWithTrace(409, '到货草稿已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: receipt.version })
    }
    if (Number(payload?.orderVersion) !== order.version) {
      return errorWithTrace(409, '采购单已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: order.version })
    }
    receipt.status = RECEIPT_STATUS.CANCELED
    receipt.canceledAt = nowIso()
    receipt.version += 1
    receipt.updatedBy = currentUserId() ?? receipt.updatedBy
    receipt.updateTime = receipt.canceledAt
    touchOrder(order)
    return {
      receiptId: receipt.id,
      receiptNo: receipt.receiptNo,
      receiptStatus: receipt.status,
      version: receipt.version,
      orderVersion: order.version
    }
  })
}

// ========== 只读库存 ==========

export function getInventorySummary(componentId) {
  if (!getMockEnabled()) return request.get(`/api/inventory/components/${componentId}`)
  if (!canAccessProcurement()) return errorWithTrace(403, '当前账号没有库存访问权限', 'PROCUREMENT_OPERATION_DENIED', null)
  const scenario = currentMockScenarioState()
  if (scenario === 'permission') return errorWithTrace(403, '当前验收场景无库存访问权限', 'PROCUREMENT_OPERATION_DENIED', null)
  if (scenario === 'error') return errorWithTrace(500, '库存汇总加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    const balance = MOCK_BALANCES.find((row) => row.componentId === Number(componentId))
    return {
      componentId: Number(componentId),
      onHandQuantity: round(balance?.onHandQuantity || 0, QUANTITY_SCALE),
      unit: balance?.unit || findMockComponent(componentId)?.baseUnit || 'PCS',
      version: balance?.version || 0,
      updateTime: balance?.updateTime || null
    }
  }
  return scenario === 'loading' ? mockDelay(1000).then(run) : Promise.resolve(run())
}

export function listInventoryBatches(componentId, params = {}) {
  if (!getMockEnabled()) return request.get(`/api/inventory/components/${componentId}/batches`, { params })
  if (!canViewInventoryBatches()) {
    return errorWithTrace(403, '普通成员只能查看库存汇总，不能查看批次', 'PROCUREMENT_OPERATION_DENIED', null)
  }
  if (isMockScenario('error')) return errorWithTrace(500, '库存批次加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    if (isMockScenario('empty')) return pageOf([], params)
    const list = MOCK_BATCHES
      .filter((batch) => batch.componentId === Number(componentId))
      .filter((batch) => !params.batchNo || String(batch.batchNo).includes(params.batchNo))
      .map((batch) => clone(batch))
    return pageOf(list, params)
  }
  return isMockScenario('loading') ? mockDelay(1200).then(run) : Promise.resolve(run())
}

export const ATTACHMENT_LIMITS = Object.freeze({
  ORDER_PER_PURPOSE: 5,
  RECEIPT_TOTAL: 10,
  MAX_BYTES: 10 * 1024 * 1024,
  CONTENT_TYPES: ['image/jpeg', 'image/png']
})

export function listInventoryMovements(params = {}) {
  if (!getMockEnabled()) return request.get('/api/inventory/movements', { params })
  if (!canViewInventoryBatches()) {
    return errorWithTrace(403, '普通成员只能查看库存汇总，不能查看流水', 'PROCUREMENT_OPERATION_DENIED', null)
  }
  if (isMockScenario('error')) return errorWithTrace(500, '库存流水加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    if (isMockScenario('empty')) return pageOf([], params)
    let list = [...MOCK_MOVEMENTS]
    if (params.componentId) list = list.filter((movement) => movement.componentId === Number(params.componentId))
    if (params.movementType) list = list.filter((movement) => movement.movementType === params.movementType)
    if (params.batchNo) {
      const batch = MOCK_BATCHES.find((item) => item.batchNo === params.batchNo)
      list = list.filter((movement) => movement.batchId === (batch?.id || -1))
    }
    if (params.from) list = list.filter((movement) => movement.occurredAt >= params.from)
    if (params.to) list = list.filter((movement) => movement.occurredAt <= params.to)
    list = list.sort((a, b) => (a.occurredAt > b.occurredAt ? -1 : 1)).map((movement) => clone(movement))
    return pageOf(list, params)
  }
  return isMockScenario('loading') ? mockDelay(1200).then(run) : Promise.resolve(run())
}

// ========== 附件与受控媒体 ==========

function attachmentOwnerError() {
  // 不可见 owner 统一 404，且不返回数量、文件名、mediaId、purpose 或存在性
  return errorWithTrace(404, '附件不存在或不可见', 'MEDIA_NOT_FOUND', null)
}

function attachmentPurposeAllowed(ownerType, purpose) {
  const meta = ATTACHMENT_PURPOSE_META[purpose]
  return Boolean(meta) && meta.ownerType === ownerType
}

function validateAttachmentInput(file, purpose, invoiceNo) {
  if (!attachmentPurposeAllowed(
    [ATTACHMENT_PURPOSE.ORDER_SCREENSHOT, ATTACHMENT_PURPOSE.INVOICE].includes(purpose)
      ? ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER
      : ATTACHMENT_OWNER_TYPE.PROCUREMENT_RECEIPT,
    purpose
  )) {
    return errorWithTrace(400, '附件用途不合法', 'VALIDATION_ERROR', { field: 'purpose' })
  }
  if (!file) {
    return errorWithTrace(400, '请选择图片文件', 'VALIDATION_ERROR', { field: 'file' })
  }
  if (!ATTACHMENT_LIMITS.CONTENT_TYPES.includes(file.type)) {
    return errorWithTrace(400, '附件仅支持 JPEG/PNG 图片', 'VALIDATION_ERROR', { field: 'file' })
  }
  if (Number(file.size) > ATTACHMENT_LIMITS.MAX_BYTES) {
    return errorWithTrace(400, '单个附件不能超过 10MB', 'VALIDATION_ERROR', { field: 'file' })
  }
  if (purpose === ATTACHMENT_PURPOSE.INVOICE && !String(invoiceNo || '').trim()) {
    return errorWithTrace(400, '发票附件必须填写发票号', 'VALIDATION_ERROR', { field: 'invoiceNo' })
  }
  return null
}

function countOwnerAttachments(ownerType, ownerId) {
  return MOCK_ATTACHMENTS.filter((attachment) => attachment.ownerType === ownerType
    && attachment.ownerId === Number(ownerId)
    && !attachment.deletedAt)
}

function attachmentLimitError(ownerType, ownerId, purpose) {
  const list = countOwnerAttachments(ownerType, ownerId)
  if (ownerType === ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER) {
    if (list.filter((attachment) => attachment.purpose === purpose).length >= ATTACHMENT_LIMITS.ORDER_PER_PURPOSE) {
      return errorWithTrace(409, '同一采购单同一用途最多 5 个附件', 'PROCUREMENT_ATTACHMENT_LIMIT_EXCEEDED', { limit: ATTACHMENT_LIMITS.ORDER_PER_PURPOSE })
    }
    return null
  }
  if (list.length >= ATTACHMENT_LIMITS.RECEIPT_TOTAL) {
    return errorWithTrace(409, '单条到货记录最多 10 个附件', 'PROCUREMENT_ATTACHMENT_LIMIT_EXCEEDED', { limit: ATTACHMENT_LIMITS.RECEIPT_TOTAL })
  }
  return null
}

function addMockMedia(file, seed) {
  const mediaId = nextMockId()
  const media = {
    mediaId,
    originalFileName: file.name || `attachment-${seed}.png`,
    contentType: file.type,
    size: Number(file.size) || 1024,
    width: 640,
    height: 480,
    contentPath: `/api/media/${mediaId}/content`,
    version: 1,
    _dataUrl: mockImageDataUrl(String(mediaId), '采购附件')
  }
  MOCK_MEDIA.push(media)
  return media
}

function addMockAttachment(ownerType, ownerId, media, purpose, invoiceNo, note) {
  const attachment = {
    id: nextMockId(),
    ownerType,
    ownerId: Number(ownerId),
    mediaId: media.mediaId,
    purpose,
    invoiceNo: invoiceNo || null,
    note: note || null,
    version: 1,
    createdBy: currentUserId() ?? 1,
    createTime: nowIso(),
    deletedBy: null,
    deletedAt: null
  }
  MOCK_ATTACHMENTS.push(attachment)
  return attachment
}

function ownerRecord(ownerType, ownerId) {
  if (ownerType === ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER) return findMockOrder(ownerId)
  return findMockReceipt(ownerId)
}

function bumpOwnerVersion(ownerType, ownerId) {
  const owner = ownerRecord(ownerType, ownerId)
  if (!owner) return null
  owner.version += 1
  owner.updatedBy = currentUserId() ?? owner.updatedBy
  owner.updateTime = nowIso()
  return owner.version
}

function attachmentListFor(ownerType, ownerId) {
  return MOCK_ATTACHMENTS
    .filter((attachment) => attachment.ownerType === ownerType && attachment.ownerId === Number(ownerId) && !attachment.deletedAt)
    .sort((a, b) => (a.createTime > b.createTime ? -1 : 1))
    .map(publicAttachment)
    .filter(Boolean)
}

export function listOrderAttachments(orderId) {
  if (!getMockEnabled()) return request.get(`/api/procurement-orders/${orderId}/attachments`)
  const scenario = currentMockScenarioState()
  if (scenario === 'error') return errorWithTrace(500, '附件加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    const order = findMockOrder(orderId)
    if (!attachmentVisible(ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER, orderId)
      || !canViewAttachments(order)
      || isMockScenario('empty')) {
      return attachmentVisible(ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER, orderId) && isMockScenario('empty')
        ? []
        : attachmentOwnerError()
    }
    return attachmentListFor(ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER, orderId)
  }
  return scenario === 'loading' ? mockDelay(1200).then(run) : Promise.resolve(run())
}

export function listReceiptAttachments(receiptId) {
  if (!getMockEnabled()) return request.get(`/api/procurement-receipts/${receiptId}/attachments`)
  const scenario = currentMockScenarioState()
  if (scenario === 'error') return errorWithTrace(500, '附件加载失败', 'INTERNAL_ERROR', null)
  const run = () => {
    if (!attachmentVisible(ATTACHMENT_OWNER_TYPE.PROCUREMENT_RECEIPT, receiptId) || isMockScenario('empty')) {
      return attachmentVisible(ATTACHMENT_OWNER_TYPE.PROCUREMENT_RECEIPT, receiptId) && isMockScenario('empty')
        ? []
        : attachmentOwnerError()
    }
    return attachmentListFor(ATTACHMENT_OWNER_TYPE.PROCUREMENT_RECEIPT, receiptId)
  }
  return scenario === 'loading' ? mockDelay(1200).then(run) : Promise.resolve(run())
}

export function uploadOrderAttachment(orderId, { file, purpose, invoiceNo, note, ownerVersion }, idempotencyKey) {
  if (!getMockEnabled()) {
    const form = new FormData()
    form.append('file', file)
    form.append('purpose', purpose)
    if (invoiceNo) form.append('invoiceNo', invoiceNo)
    if (note) form.append('note', note)
    form.append('orderVersion', ownerVersion)
    return request.post(`/api/procurement-orders/${orderId}/attachments`, form, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/procurement-orders/${orderId}/attachments`, idempotencyKey, { purpose, invoiceNo, note, ownerVersion }, () => {
    const order = requireVisibleOrder(orderId)
    if (!order) return attachmentOwnerError()
    if (!canUploadOrderAttachment(order, purpose)) {
      return errorWithTrace(403, '当前角色不能为该采购单上传该用途附件', 'PROCUREMENT_OPERATION_DENIED', null)
    }
    const conflict = scenarioConflict(order.version)
    if (conflict) return conflict
    if (Number(ownerVersion) !== order.version) {
      return errorWithTrace(409, '采购单已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: order.version })
    }
    const invalid = validateAttachmentInput(file, purpose, invoiceNo)
    if (invalid) return invalid
    const limit = attachmentLimitError(ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER, orderId, purpose)
    if (limit) return limit
    const media = addMockMedia(file, orderId)
    const attachment = addMockAttachment(ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER, orderId, media, purpose, invoiceNo, note)
    const nextVersion = bumpOwnerVersion(ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER, orderId)
    return { ...publicAttachment(attachment), ownerVersion: nextVersion }
  })
}

export function uploadReceiptAttachment(receiptId, { file, purpose, invoiceNo, note, ownerVersion }, idempotencyKey) {
  if (!getMockEnabled()) {
    const form = new FormData()
    form.append('file', file)
    form.append('purpose', purpose)
    if (invoiceNo) form.append('invoiceNo', invoiceNo)
    if (note) form.append('note', note)
    form.append('receiptVersion', ownerVersion)
    return request.post(`/api/procurement-receipts/${receiptId}/attachments`, form, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/procurement-receipts/${receiptId}/attachments`, idempotencyKey, { purpose, invoiceNo, note, ownerVersion }, () => {
    const receipt = findMockReceipt(receiptId)
    const order = receiptOwnerOrder(receipt)
    if (!receipt || !orderVisible(order) || isMockScenario('permission')) return attachmentOwnerError()
    if (!canUploadReceiptAttachment(order, purpose)) {
      return errorWithTrace(403, '仅库存管理员/管理员可上传到货照片', 'PROCUREMENT_OPERATION_DENIED', null)
    }
    const conflict = scenarioConflict(receipt.version)
    if (conflict) return conflict
    if (Number(ownerVersion) !== receipt.version) {
      return errorWithTrace(409, '到货记录已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: receipt.version })
    }
    const invalid = validateAttachmentInput(file, purpose, invoiceNo)
    if (invalid) return invalid
    const limit = attachmentLimitError(ATTACHMENT_OWNER_TYPE.PROCUREMENT_RECEIPT, receiptId, purpose)
    if (limit) return limit
    const media = addMockMedia(file, receiptId)
    const attachment = addMockAttachment(ATTACHMENT_OWNER_TYPE.PROCUREMENT_RECEIPT, receiptId, media, purpose, invoiceNo, note)
    const nextVersion = bumpOwnerVersion(ATTACHMENT_OWNER_TYPE.PROCUREMENT_RECEIPT, receiptId)
    return { ...publicAttachment(attachment), ownerVersion: nextVersion }
  })
}

export function replaceAttachment(attachmentId, { file, attachmentVersion, mediaVersion, ownerVersion }, idempotencyKey) {
  if (!getMockEnabled()) {
    const form = new FormData()
    form.append('file', file)
    form.append('attachmentVersion', attachmentVersion)
    form.append('mediaVersion', mediaVersion)
    form.append('ownerVersion', ownerVersion)
    return request.post(`/api/procurement-attachments/${attachmentId}/replacement`, form, { headers: idempotencyHeaders(idempotencyKey) })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`POST /api/procurement-attachments/${attachmentId}/replacement`, idempotencyKey, { attachmentVersion, mediaVersion, ownerVersion }, () => {
    const attachment = findMockAttachment(attachmentId)
    if (!attachment) return attachmentOwnerError()
    if (!attachmentVisible(attachment.ownerType, attachment.ownerId)) return attachmentOwnerError()
    const owner = ownerRecord(attachment.ownerType, attachment.ownerId)
    const order = attachment.ownerType === ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER
      ? owner
      : receiptOwnerOrder(owner)
    const allowed = attachment.ownerType === ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER
      ? canUploadOrderAttachment(order, attachment.purpose)
      : canUploadReceiptAttachment(order, attachment.purpose)
    if (!allowed) {
      return errorWithTrace(403, '当前角色不能替换该附件', 'PROCUREMENT_OPERATION_DENIED', null)
    }
    const conflict = scenarioConflict(owner?.version)
    if (conflict) return conflict
    if (Number(ownerVersion) !== owner?.version) {
      return errorWithTrace(409, '归属记录已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: owner?.version })
    }
    if (Number(attachmentVersion) !== attachment.version) {
      return errorWithTrace(409, '附件已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: attachment.version })
    }
    if (Number(mediaVersion) !== findMockMedia(attachment.mediaId)?.version) {
      return errorWithTrace(409, '媒体已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: findMockMedia(attachment.mediaId)?.version })
    }
    const invalid = validateAttachmentInput(file, attachment.purpose, attachment.invoiceNo)
    if (invalid) return invalid
    const media = addMockMedia(file, attachment.id)
    attachment.mediaId = media.mediaId
    attachment.version += 1
    const nextVersion = bumpOwnerVersion(attachment.ownerType, attachment.ownerId)
    return { ...publicAttachment(attachment), ownerVersion: nextVersion }
  })
}

export function deleteAttachment(attachmentId, { attachmentVersion, mediaVersion, ownerVersion, reason }, idempotencyKey) {
  if (!getMockEnabled()) {
    return request.delete(`/api/procurement-attachments/${attachmentId}`, {
      data: { attachmentVersion, mediaVersion, ownerVersion, reason },
      headers: idempotencyHeaders(idempotencyKey)
    })
  }
  const guard = writeGuard()
  if (guard) return guard
  return withIdempotency(`DELETE /api/procurement-attachments/${attachmentId}`, idempotencyKey, { attachmentVersion, mediaVersion, ownerVersion, reason }, () => {
    const attachment = findMockAttachment(attachmentId)
    if (!attachment) return attachmentOwnerError()
    if (!attachmentVisible(attachment.ownerType, attachment.ownerId)) return attachmentOwnerError()
    const owner = ownerRecord(attachment.ownerType, attachment.ownerId)
    const order = attachment.ownerType === ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER
      ? owner
      : receiptOwnerOrder(owner)
    const allowed = attachment.ownerType === ATTACHMENT_OWNER_TYPE.PROCUREMENT_ORDER
      ? canUploadOrderAttachment(order, attachment.purpose)
      : canUploadReceiptAttachment(order, attachment.purpose)
    if (!allowed) {
      return errorWithTrace(403, '当前角色不能删除该附件', 'PROCUREMENT_OPERATION_DENIED', null)
    }
    const conflict = scenarioConflict(owner?.version)
    if (conflict) return conflict
    if (Number(ownerVersion) !== owner?.version) {
      return errorWithTrace(409, '归属记录已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: owner?.version })
    }
    if (Number(attachmentVersion) !== attachment.version) {
      return errorWithTrace(409, '附件已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: attachment.version })
    }
    if (Number(mediaVersion) !== findMockMedia(attachment.mediaId)?.version) {
      return errorWithTrace(409, '媒体已被更新，请刷新后重试', 'VERSION_CONFLICT', { currentVersion: findMockMedia(attachment.mediaId)?.version })
    }
    const deleteReason = String(reason || '').trim()
    if (!deleteReason || deleteReason.length > 200) {
      return errorWithTrace(400, '删除原因需为 1-200 字', 'VALIDATION_ERROR', { field: 'reason' })
    }
    attachment.deletedBy = currentUserId() ?? 1
    attachment.deletedAt = nowIso()
    attachment.version += 1
    const nextVersion = bumpOwnerVersion(attachment.ownerType, attachment.ownerId)
    return { id: attachment.id, deletedAt: attachment.deletedAt, ownerVersion: nextVersion }
  })
}

export function getAttachmentContent(mediaId) {
  if (!getMockEnabled()) {
    return request.get(`/api/media/${mediaId}/content`, { responseType: 'blob', silentError: true })
      .then((res) => {
        const blob = res?.data
        if (!blob) throw { code: 404, msg: '附件不存在或不可见', reason: 'MEDIA_NOT_FOUND', data: null }
        return typeof URL !== 'undefined' && URL.createObjectURL ? URL.createObjectURL(blob) : ''
      })
  }
  const attachment = MOCK_ATTACHMENTS.find((item) => item.mediaId === Number(mediaId) && !item.deletedAt)
  const media = findMockMedia(mediaId)
  if (!attachment || !media) return errorWithTrace(404, '附件不存在或不可见', 'MEDIA_NOT_FOUND', null)
  if (!attachmentVisible(attachment.ownerType, attachment.ownerId)) {
    return errorWithTrace(404, '附件不存在或不可见', 'MEDIA_NOT_FOUND', null)
  }
  return Promise.resolve(media._dataUrl || '')
}

/** 把详情/列表响应包归一化为分页对象，兼容后端可能返回 list/pageSize 包装 */
export function normalizePage(response) {
  if (Array.isArray(response)) {
    return { list: response, total: response.length, page: 1, pageSize: response.length, hasNext: false }
  }
  return {
    list: response?.list || response?.records || [],
    total: Number(response?.total ?? 0),
    page: Number(response?.page || 1),
    pageSize: Number(response?.pageSize || 20),
    hasNext: Boolean(response?.hasNext)
  }
}

/** 附件列表兼容数组或 { list } 包装，同时绝不推测不存在的附件 */
export function normalizeAttachmentList(response) {
  if (Array.isArray(response)) return response
  if (Array.isArray(response?.list)) return response.list
  if (Array.isArray(response?.attachments)) return response.attachments
  return []
}
