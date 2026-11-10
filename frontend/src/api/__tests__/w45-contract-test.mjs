/**
 * W45 契约测试：采购、分批到货、库存增加与审批策略
 *
 * 契约来源：docs/contracts/PROCUREMENT_INVENTORY_API.md v1.0（FROZEN）
 *
 * 覆盖：
 *   - 枚举 / 状态 / reason / 金额与数量展示（§3 / §4 / §13.4）
 *   - 权限矩阵与不可见 404（§5 / §15）
 *   - 列表、详情、策略的 loading / empty / error / permission / conflict / success
 *   - partial-received 与 transaction-failed 场景
 *   - Idempotency-Key 必填、同键重放、同键不同请求体 409
 *   - 创建→提交→自动免审/人工审批→下单金额分摊→到货→部分/全部→完成
 *   - 重复确认、超量、事务失败不改数据、取消剩余
 *   - 附件 ACL 零泄露、字段白名单、发票号、大小/类型/数量上限常量
 *   - 页面 / 路由 / 侧栏 / 三视口静态检查
 *
 * 运行：npm run test:w45
 */

import assert from 'node:assert/strict'
import { existsSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'

const here = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(here, '../../..')
const tmpFiles = []

let pass = 0
let fail = 0
const failures = []

async function test(name, fn) {
  try {
    await fn()
    pass++
    process.stdout.write(`  ✓ ${name}\n`)
  } catch (err) {
    fail++
    const detail = err instanceof Error
      ? err.message
      : `code=${err?.code} reason=${err?.reason} msg=${err?.msg} data=${JSON.stringify(err?.data)}`
    failures.push({ name, message: detail })
    process.stdout.write(`  ✗ ${name}\n    ${detail}\n`)
  }
}

async function group(name, fn) {
  process.stdout.write(`\n# ${name}\n`)
  await fn()
}

function makeStub(name, code) {
  const file = path.join(here, `.tmp-w45-stub-${name}-${Date.now()}-${Math.random().toString(36).slice(2)}.mjs`)
  writeFileSync(file, code)
  tmpFiles.push(file)
  return pathToFileURL(file).href
}

const requestStubUrl = makeStub('request', `export default {
  get() { return Promise.reject(new Error('contract-test: 禁止真实网络')) },
  post() { return Promise.reject(new Error('contract-test: 禁止真实网络')) },
  put() { return Promise.reject(new Error('contract-test: 禁止真实网络')) },
  delete() { return Promise.reject(new Error('contract-test: 禁止真实网络')) }
}`)
const mockStubUrl = makeStub('mock', `export const getMockEnabled = () => true`)
const userStubUrl = makeStub('user', `export const userStore = {
  get userInfo() { return { id: globalThis.__W45_USER_ID ?? 4, name: '测试用户' } }
}
export const currentRoles = () => globalThis.__W45_ROLES || ['STOCK_KEEPER']`)
const roleStubUrl = makeStub('permission', `export const ROLE = {
  SYSTEM_ADMIN: 'SYSTEM_ADMIN',
  TEACHER: 'TEACHER',
  STOCK_KEEPER: 'STOCK_KEEPER',
  MEMBER: 'MEMBER'
}`)

const aliasTargets = {
  'utils/request': requestStubUrl,
  'utils/mock': mockStubUrl,
  'store/user': userStubUrl,
  'utils/permission': roleStubUrl,
  'utils/idempotency': pathToFileURL(path.join(here, '../../utils/idempotency.js')).href,
  'utils/mockScenario': pathToFileURL(path.join(here, '../../utils/mockScenario.js')).href
}

async function loadModule(srcRelative) {
  const srcUrl = new URL(srcRelative, import.meta.url)
  let code = readFileSync(srcUrl, 'utf8')
  code = code.replace(/from '@\/([^']+)'/g, (whole, sub) => {
    const target = aliasTargets[sub]
    assert.ok(target, `缺少别名映射 @/${sub}`)
    return `from '${target}'`
  })
  const tmpPath = path.join(here, `.tmp-w45-${Date.now()}-${Math.random().toString(36).slice(2)}.mjs`)
  writeFileSync(tmpPath, code)
  tmpFiles.push(tmpPath)
  return pathToFileURL(tmpPath).href
}

function cleanup() {
  for (const file of tmpFiles) {
    try { rmSync(file, { force: true }) } catch { /* 忽略清理失败 */ }
  }
}

globalThis.__W45_USER_ID = 4
globalThis.__W45_ROLES = ['STOCK_KEEPER']

function setUser(id, roles) {
  globalThis.__W45_USER_ID = id
  globalThis.__W45_ROLES = roles
}

function setScenario(state) {
  if (state) {
    globalThis.window = { location: { search: `?mockState=${state}` } }
  } else {
    globalThis.window = { location: { search: '' } }
  }
}

let keySeq = 0
function newKey(prefix = 'w45') {
  keySeq += 1
  return `w45-${prefix}-${Date.now().toString(36)}-${keySeq}`
}

async function rejected(promise) {
  try {
    await promise
  } catch (error) {
    return error
  }
  throw new Error('期望请求被拒绝，但实际成功')
}

function assertReason(error, code, reason) {
  assert.equal(error?.code, code, `期望 code=${code}，实际 ${error?.code} (${error?.reason})`)
  assert.equal(error?.reason, reason, `期望 reason=${reason}，实际 ${error?.reason}`)
  assert.ok(error?.data?.traceId, '错误响应必须包含 traceId')
}

function forbidKeys(value, keys, label) {
  const json = JSON.stringify(value)
  for (const key of keys) {
    assert.ok(!json.includes(`"${key}"`), `${label} 泄露字段 ${key}`)
  }
}

async function loadProcurement() {
  return import(await loadModule('../procurement.js'))
}

let createdOrderId = null
function created_order_id() {
  assert.ok(createdOrderId, 'createdOrderId 尚未初始化')
  return createdOrderId
}

const run = async () => {
  const api = await loadProcurement()

  await group('枚举与展示格式', async () => {
    await test('采购单 9 状态与到货 3 状态', () => {
      assert.deepEqual(Object.keys(api.ORDER_STATUS).sort(), [
        'APPROVED', 'CANCELED', 'COMPLETED', 'DRAFT', 'ORDERED', 'PARTIALLY_RECEIVED', 'PENDING_APPROVAL', 'RECEIVED', 'REJECTED'
      ])
      assert.deepEqual(Object.keys(api.RECEIPT_STATUS).sort(), ['CANCELED', 'CONFIRMED', 'DRAFT'])
      for (const status of Object.values(api.ORDER_STATUS)) {
        assert.ok(api.orderStatusMeta(status).label)
      }
    })

    await test('平台枚举含 1688，附件用途 4 类', () => {
      assert.equal(api.PLATFORM_TYPE.ALIBABA_1688, '1688')
      assert.equal(api.platformTypeMeta('1688').label, '1688')
      assert.deepEqual(Object.keys(api.ATTACHMENT_PURPOSE).sort(), ['INVOICE', 'ORDER_SCREENSHOT', 'PACKAGE_PHOTO', 'RECEIPT_PHOTO'])
    })

    await test('金额 2 位、数量 ≤3 位、单价 4 位', () => {
      assert.equal(api.formatAmount(477), '¥477.00')
      assert.equal(api.formatAmount(1200.5), '¥1200.50')
      assert.equal(api.formatQuantity(0.5), '0.5')
      assert.equal(api.formatQuantity(80.000), '80')
      assert.equal(api.formatQuantityUnit(2.25, 'PCS'), '2.25 PCS')
      assert.equal(api.formatUnitPrice(0.5), '¥0.5000')
      assert.equal(api.AMOUNT_SCALE, 2)
      assert.equal(api.QUANTITY_SCALE, 3)
      assert.equal(api.UNIT_PRICE_SCALE, 4)
    })

    await test('行进度按 received+canceled，剩余不超过订购', () => {
      const line = { orderedQuantity: 200, receivedQuantity: 80, canceledQuantity: 20 }
      assert.equal(api.lineProgressPercent(line), 50)
      assert.equal(api.lineRemainingQuantity(line), 100)
      assert.equal(api.lineStateOf(line), 'PARTIALLY_RECEIVED')
      assert.equal(api.lineStateOf({ orderedQuantity: 10, receivedQuantity: 10 }), 'RECEIVED')
    })
  })

  await group('权限矩阵', async () => {
    await test('MEMBER 仅见自己申请/买手的采购单', () => {
      setUser(2, ['MEMBER'])
      assert.equal(api.canAccessProcurement(), true)
      assert.equal(api.canViewAllOrders(), false)
      assert.equal(api.canViewOrder({ requesterId: 2, buyerId: null }), true)
      assert.equal(api.canViewOrder({ requesterId: 3, buyerId: 21 }), false)
      assert.equal(api.canViewOrder({ requesterId: 3, buyerId: 2 }), true)
      assert.equal(api.canCreateReceipt({ status: api.ORDER_STATUS.ORDERED }), false)
    })

    await test('教师不能审批自己申请的单', () => {
      setUser(2, ['TEACHER'])
      assert.equal(api.canApproveOrder({ status: api.ORDER_STATUS.PENDING_APPROVAL, requesterId: 2 }), false)
      assert.equal(api.canApproveOrder({ status: api.ORDER_STATUS.PENDING_APPROVAL, requesterId: 3 }), true)
      assert.equal(api.canUpdatePolicy(), false)
    })

    await test('系统管理员可改策略；库存管理员可确认到货', () => {
      setUser(1, ['SYSTEM_ADMIN'])
      assert.equal(api.canUpdatePolicy(), true)
      setUser(4, ['STOCK_KEEPER'])
      assert.equal(api.canUpdatePolicy(), false)
      assert.equal(api.canConfirmReceipt(), true)
      assert.equal(api.canCreateReceipt({ status: api.ORDER_STATUS.PARTIALLY_RECEIVED }), true)
      assert.equal(api.canCreateReceipt({ status: api.ORDER_STATUS.APPROVED }), false)
    })
  })

  setUser(4, ['STOCK_KEEPER'])

  await group('列表 / 详情 / 8 类 mock 状态', async () => {
    await test('默认列表返回契约状态与展示字段', async () => {
      setScenario('')
      const page = api.normalizePage(await api.listOrders({ page: 1, pageSize: 20 }))
      assert.ok(page.total >= 10)
      const row = page.list.find((item) => item.id === 905)
      assert.equal(row.status, api.ORDER_STATUS.PARTIALLY_RECEIVED)
      assert.equal(row.currency, 'CNY')
      assert.equal(typeof row.payableAmount, 'number')
      assert.ok(row.purchaseNo)
    })

    await test('partial-received 场景只返回部分到货', async () => {
      setScenario('partial-received')
      const page = api.normalizePage(await api.listOrders({ page: 1, pageSize: 20 }))
      assert.ok(page.list.length > 0)
      assert.ok(page.list.every((item) => item.status === api.ORDER_STATUS.PARTIALLY_RECEIVED))
    })

    await test('empty / error / permission 场景', async () => {
      setScenario('empty')
      assert.equal(api.normalizePage(await api.listOrders({})).list.length, 0)

      setScenario('error')
      assertReason(await rejected(api.listOrders({})), 500, 'INTERNAL_ERROR')

      setScenario('permission')
      assertReason(await rejected(api.listOrders({})), 403, 'PROCUREMENT_OPERATION_DENIED')
      setScenario('')
    })

    await test('loading 场景保持 pending，不提前返回空数据', async () => {
      setScenario('loading')
      const pending = api.listOrders({}).then(() => 'settled', () => 'settled')
      const outcome = await Promise.race([
        pending,
        new Promise((resolve) => setTimeout(() => resolve('pending'), 120))
      ])
      assert.equal(outcome, 'pending')
      setScenario('')
    })

    await test('详情对无权限成员返回 404 且不泄露存在性', async () => {
      setUser(99, ['MEMBER'])
      const error = await rejected(api.getOrder(904))
      assert.equal(error.code, 404)
      assert.equal(error.reason, 'PROCUREMENT_ORDER_NOT_FOUND')
      assert.ok(!JSON.stringify(error).includes('连接器'))
      setUser(4, ['STOCK_KEEPER'])
      const detail = await api.getOrder(905)
      assert.equal(detail.id, 905)
      assert.equal(detail.version, 7)
      assert.equal(detail.lines.length, 2)
      assert.equal(detail.lines[0].receivedQuantity, 80)
    })
  })

  await group('审批策略与幂等', async () => {
    await test('非管理员修改策略 403', async () => {
      const error = await rejected(api.updatePolicy({ currentPolicyVersion: 1, approvalThreshold: 500, valuableUnitPriceThreshold: 1000, forcedCategoryIds: [31] }, newKey('policy')))
      assertReason(error, 403, 'PROCUREMENT_POLICY_DENIED')
    })

    await test('管理员发布策略、同键重放、同键不同体 409、旧版本冲突', async () => {
      setUser(1, ['SYSTEM_ADMIN'])
      const payload = { currentPolicyVersion: 1, approvalThreshold: 500, valuableUnitPriceThreshold: 1000, forcedCategoryIds: [31] }
      const key = newKey('policy')
      const next = await api.updatePolicy(payload, key)
      assert.equal(next.policyVersion, 2)
      assert.equal(next.currency, 'CNY')

      const replay = await api.updatePolicy(payload, key)
      assert.deepEqual(replay, next)
      assert.equal((await api.getPolicy()).policyVersion, 2)

      const reused = await rejected(api.updatePolicy({ ...payload, approvalThreshold: 600 }, key))
      assertReason(reused, 409, 'IDEMPOTENCY_KEY_REUSED')

      const stale = await rejected(api.updatePolicy({ ...payload, currentPolicyVersion: 1 }, newKey('policy-stale')))
      assertReason(stale, 409, 'VERSION_CONFLICT')
      assert.equal(stale.data.currentVersion, 2)

      const versions = api.normalizePage(await api.listPolicyVersions({ page: 1, pageSize: 10 }))
      assert.ok(versions.total >= 2)
      assert.equal(versions.list[0].policyVersion, 2)
      setUser(4, ['STOCK_KEEPER'])
    })
  })

  await group('采购单流程与幂等重放', async () => {
    const orderPayload = {
      title: 'W45 低额电阻采购',
      projectId: 10,
      fundingSource: '实验室开放基金',
      shippingAmount: 0,
      discountAmount: 0,
      lines: [{ componentId: 307, orderedQuantity: 10, unitPrice: 0.02 }]
    }

    let created

    await test('缺少 Idempotency-Key 创建被拒绝', async () => {
      assertReason(await rejected(api.createOrder(orderPayload, '')), 400, 'IDEMPOTENCY_KEY_REQUIRED')
    })

    await test('创建草稿并同键重放返回同一单', async () => {
      const key = newKey('create-order')
      created = await api.createOrder(orderPayload, key)
      createdOrderId = created.id
      assert.equal(created.status, api.ORDER_STATUS.DRAFT)
      assert.equal(created.version, 1)
      assert.equal(created.payableAmount, 0.2)
      const replay = await api.createOrder(orderPayload, key)
      assert.equal(replay.id, created.id)
      assert.deepEqual(replay, created)
    })

    await test('提交低额订单自动免审', async () => {
      const submitted = await api.submitOrder(created.id, { version: created.version }, newKey('submit'))
      assert.equal(submitted.status, api.ORDER_STATUS.APPROVED)
      assert.equal(submitted.approvalRequired, false)
      assert.deepEqual(submitted.approvalReasons, ['EXEMPT'])
      assert.equal(submitted.approvalPolicyVersion, 2)
      assert.equal(submitted.version, 2)
      created = submitted
    })

    await test('下单冻结分摊金额与有效单位成本', async () => {
      const ordered = await api.markOrdered(created.id, {
        version: created.version,
        buyerId: 21,
        supplierName: 'W45 测试供应商',
        platformType: api.PLATFORM_TYPE.ALIBABA_1688,
        platformOrderNo: 'W45-TEST-0001',
        actualPaidAmount: 0.2,
        paymentDifferenceNote: null
      }, newKey('mark-ordered'))
      assert.equal(ordered.status, api.ORDER_STATUS.ORDERED)
      assert.equal(ordered.version, 3)
      assert.equal(ordered.buyerId, 21)
      const line = ordered.lines[0]
      assert.equal(line.allocatedNetAmount, ordered.payableAmount)
      assert.equal(line.effectiveUnitCost, 0.0200)
      created = ordered
    })

    await test('重复平台订单号 / 实付差异原因 / 职责冲突', async () => {
      setUser(1, ['SYSTEM_ADMIN'])
      const duplicated = await rejected(api.markOrdered(903, {
        version: 3, buyerId: 21, supplierName: '另一个供应商', platformType: api.PLATFORM_TYPE.ALIBABA_1688,
        platformOrderNo: 'w45-test-0001', actualPaidAmount: 1250
      }, newKey('dup-platform')))
      assertReason(duplicated, 409, 'PLATFORM_ORDER_NO_DUPLICATED')
      assert.equal(duplicated.data.orderId, created.id)

      const gap = await rejected(api.markOrdered(903, {
        version: 3, buyerId: 21, supplierName: '另一个供应商', platformType: api.PLATFORM_TYPE.JD,
        platformOrderNo: 'W45-TEST-0003', actualPaidAmount: 1200
      }, newKey('payment-gap')))
      assertReason(gap, 400, 'PAYMENT_DIFFERENCE_NOTE_REQUIRED')

      const duty = await rejected(api.markOrdered(903, {
        version: 3, buyerId: 4, supplierName: '另一个供应商', platformType: api.PLATFORM_TYPE.JD,
        platformOrderNo: 'W45-TEST-0004', actualPaidAmount: 1250
      }, newKey('duty')))
      assertReason(duty, 409, 'PROCUREMENT_DUTY_CONFLICT')
      setUser(4, ['STOCK_KEEPER'])
    })

    await test('高额+贵重+指定分类单进入人工审批并可批准', async () => {
      const high = await api.createOrder({
        title: 'W45 贵重运放采购',
        projectId: 10,
        shippingAmount: 0,
        discountAmount: 0,
        lines: [{ componentId: 309, orderedQuantity: 10, unitPrice: 120 }]
      }, newKey('create-high'))
      const pending = await api.submitOrder(high.id, { version: high.version }, newKey('submit-high'))
      assert.equal(pending.status, api.ORDER_STATUS.PENDING_APPROVAL)
      assert.equal(pending.approvalRequired, true)
      assert.deepEqual(pending.approvalReasons.sort(), ['AMOUNT_THRESHOLD', 'FORCED_CATEGORY'])

      setUser(1, ['SYSTEM_ADMIN'])
      const approved = await api.approveOrder(high.id, { version: pending.version, comment: 'W45 预算确认' }, newKey('approve-high'))
      assert.equal(approved.status, api.ORDER_STATUS.APPROVED)
      assert.equal(approved.version, pending.version + 1)
      assert.equal(approved.approvalRecords[0].decision, 'APPROVED')
      assert.equal(approved.approvalRecords[0].triggerSnapshot.payableAmount, 1200)
      setUser(4, ['STOCK_KEEPER'])
    })
  })

  await group('到货：草稿 / 确认 / 部分 / 全部 / 完成', async () => {
    let receipt

    await test('创建到货草稿并推进 orderVersion', async () => {
      const order = await api.getOrder(created_order_id())
      receipt = await api.createReceipt(order.id, {
        orderVersion: order.version,
        receivedAt: '2026-11-07T10:00:00+08:00',
        note: 'W45 全量到货',
        lines: [{ orderLineId: order.lines[0].id, quantity: 10 }]
      }, newKey('create-receipt'))
      assert.equal(receipt.receiptStatus, 'DRAFT')
      assert.equal(receipt.version, 1)
      assert.equal(receipt.orderVersion, order.version + 1)
    })

    await test('同一采购单只能有一条草稿', async () => {
      const order = await api.getOrder(created_order_id())
      const error = await rejected(api.createReceipt(order.id, {
        orderVersion: order.version,
        receivedAt: '2026-11-07T10:00:00+08:00',
        lines: [{ orderLineId: order.lines[0].id, quantity: 1 }]
      }, newKey('create-receipt-dup')))
      assertReason(error, 409, 'RECEIPT_DRAFT_EXISTS')
      assert.equal(error.data.receiptId, receipt.receiptId)
    })

    await test('确认前 receiptVersion 必须匹配', async () => {
      const error = await rejected(api.confirmReceipt(receipt.receiptId, {
        receiptVersion: 2, orderVersion: receipt.orderVersion
      }, newKey('confirm-stale')))
      assertReason(error, 409, 'VERSION_CONFLICT')
      assert.equal(error.data.currentVersion, 1)
    })

    await test('确认到货写入批次/流水/余额并完成全部到货', async () => {
      const before = await api.getInventorySummary(307)
      const confirmed = await api.confirmReceipt(receipt.receiptId, {
        receiptVersion: receipt.version, orderVersion: receipt.orderVersion
      }, newKey('confirm'))
      assert.equal(confirmed.receiptStatus, 'CONFIRMED')
      assert.equal(confirmed.orderStatus, api.ORDER_STATUS.RECEIVED)
      assert.equal(confirmed.batches.length, 1)
      assert.equal(confirmed.batches[0].quantity, 10)
      assert.equal(confirmed.batches[0].unitCost, 0.0200)
      assert.equal(confirmed.movements[0].balanceBefore, before.onHandQuantity)
      assert.equal(confirmed.movements[0].balanceAfter, before.onHandQuantity + 10)
      receipt = { ...receipt, ...confirmed }
    })

    await test('重复确认返回 RECEIPT_ALREADY_CONFIRMED 且不改库存', async () => {
      const before = await api.getInventorySummary(307)
      const error = await rejected(api.confirmReceipt(receipt.receiptId, {
        receiptVersion: 2, orderVersion: receipt.orderVersion
      }, newKey('confirm-again')))
      assertReason(error, 409, 'RECEIPT_ALREADY_CONFIRMED')
      assert.equal(error.data.receiptStatus, 'CONFIRMED')
      const after = await api.getInventorySummary(307)
      assert.equal(after.onHandQuantity, before.onHandQuantity)
      assert.equal(after.version, before.version)
    })

    await test('已全部到货后可标记完成', async () => {
      const order = await api.getOrder(created_order_id())
      assert.equal(order.status, api.ORDER_STATUS.RECEIVED)
      const completed = await api.completeOrder(order.id, { version: order.version }, newKey('complete'))
      assert.equal(completed.status, api.ORDER_STATUS.COMPLETED)
    })
  })

  await group('部分到货 / 超量 / 取消剩余', async () => {
    let overReceipt

    await test('草稿允许超量保存，确认时拒绝且不写库存', async () => {
      const order = await api.getOrder(905)
      overReceipt = await api.createReceipt(905, {
        orderVersion: order.version,
        receivedAt: '2026-11-07T10:00:00+08:00',
        lines: [{ orderLineId: order.lines[0].id, quantity: 150 }]
      }, newKey('over-receipt'))
      const batchesBefore = api.normalizePage(await api.listInventoryBatches(306, { page: 1, pageSize: 50 })).total
      const balanceBefore = await api.getInventorySummary(306)
      const error = await rejected(api.confirmReceipt(overReceipt.receiptId, {
        receiptVersion: overReceipt.version, orderVersion: overReceipt.orderVersion
      }, newKey('over-confirm')))
      assertReason(error, 409, 'RECEIPT_OVER_QUANTITY')
      assert.equal(error.data.remainingQuantity, 120)
      assert.equal(error.data.requestedQuantity, 150)
      const batchesAfter = api.normalizePage(await api.listInventoryBatches(306, { page: 1, pageSize: 50 })).total
      const balanceAfter = await api.getInventorySummary(306)
      assert.equal(batchesAfter, batchesBefore)
      assert.equal(balanceAfter.onHandQuantity, balanceBefore.onHandQuantity)
      assert.equal(balanceAfter.version, balanceBefore.version)
    })

    await test('取消超量草稿不改变库存', async () => {
      const order = await api.getOrder(905)
      const canceled = await api.cancelReceipt(overReceipt.receiptId, {
        receiptVersion: overReceipt.version, orderVersion: order.version
      }, newKey('cancel-over'))
      assert.equal(canceled.receiptStatus, 'CANCELED')
      assert.equal(canceled.version, 2)
    })

    await test('首批到货后为 PARTIALLY_RECEIVED 并保留剩余', async () => {
      const order = await api.getOrder(905)
      const receipt = await api.createReceipt(905, {
        orderVersion: order.version,
        receivedAt: '2026-11-07T11:00:00+08:00',
        lines: [{ orderLineId: order.lines[0].id, quantity: 100, supplierLotNo: 'W45-LOT-A' }]
      }, newKey('partial-receipt'))
      const confirmed = await api.confirmReceipt(receipt.receiptId, {
        receiptVersion: receipt.version, orderVersion: receipt.orderVersion
      }, newKey('partial-confirm'))
      assert.equal(confirmed.orderStatus, api.ORDER_STATUS.PARTIALLY_RECEIVED)
      const after = await api.getOrder(905)
      assert.equal(after.lines[0].receivedQuantity, 180)
      assert.equal(api.lineRemainingQuantity(after.lines[0]), 20)
    })

    await test('补收剩余后全部到货', async () => {
      const order = await api.getOrder(905)
      const receipt = await api.createReceipt(905, {
        orderVersion: order.version,
        receivedAt: '2026-11-07T12:00:00+08:00',
        lines: [
          { orderLineId: order.lines[0].id, quantity: 20 },
          { orderLineId: order.lines[1].id, quantity: 500, locationNote: 'W45 A 区' }
        ]
      }, newKey('final-receipt'))
      const confirmed = await api.confirmReceipt(receipt.receiptId, {
        receiptVersion: receipt.version, orderVersion: receipt.orderVersion
      }, newKey('final-confirm'))
      assert.equal(confirmed.orderStatus, api.ORDER_STATUS.RECEIVED)
      const after = await api.getOrder(905)
      assert.equal(after.lines[0].receivedQuantity, 200)
      assert.equal(after.lines[1].receivedQuantity, 500)
    })

    await test('部分到货可取消剩余并进入 COMPLETED', async () => {
      const partial = await api.createOrder({
        title: 'W45 取消剩余验证单',
        shippingAmount: 0,
        discountAmount: 0,
        lines: [{ componentId: 307, orderedQuantity: 10, unitPrice: 0.02 }]
      }, newKey('remainder-create'))
      const submitted = await api.submitOrder(partial.id, { version: partial.version }, newKey('remainder-submit'))
      const ordered = await api.markOrdered(partial.id, {
        version: submitted.version, buyerId: 21, supplierName: 'W45 剩余供应商',
        platformType: api.PLATFORM_TYPE.OFFLINE, platformOrderNo: 'W45-OFFLINE-001', actualPaidAmount: 0.2
      }, newKey('remainder-order'))
      const receipt = await api.createReceipt(partial.id, {
        orderVersion: ordered.version,
        receivedAt: '2026-11-07T13:00:00+08:00',
        lines: [{ orderLineId: ordered.lines[0].id, quantity: 5 }]
      }, newKey('remainder-receipt'))
      const confirmed = await api.confirmReceipt(receipt.receiptId, {
        receiptVersion: receipt.version, orderVersion: receipt.orderVersion
      }, newKey('remainder-confirm'))
      assert.equal(confirmed.orderStatus, api.ORDER_STATUS.PARTIALLY_RECEIVED)

      const current = await api.getOrder(partial.id)
      const completed = await api.cancelRemaining(partial.id, { version: current.version, reason: 'W45 剩余不再采购' }, newKey('cancel-remaining'))
      assert.equal(completed.status, api.ORDER_STATUS.COMPLETED)
      assert.equal(completed.lines[0].canceledQuantity, 5)
      assert.equal(api.lineProgressPercent(completed.lines[0]), 100)
    })
  })

  await group('入库事务失败', async () => {
    let receipt
    let balanceBefore
    let batchesBefore
    let movementsBefore

    await test('事务失败返回 500 且批次/流水/余额/订单均不变', async () => {
      const order = await api.getOrder(904)
      receipt = await api.createReceipt(904, {
        orderVersion: order.version,
        receivedAt: '2026-11-07T14:00:00+08:00',
        lines: [{ orderLineId: order.lines[0].id, quantity: 100 }]
      }, newKey('tx-receipt'))
      balanceBefore = await api.getInventorySummary(304)
      batchesBefore = api.normalizePage(await api.listInventoryBatches(304, { page: 1, pageSize: 50 })).total
      movementsBefore = api.normalizePage(await api.listInventoryMovements({ componentId: 304, page: 1, pageSize: 50 })).total

      setScenario('transaction-failed')
      const error = await rejected(api.confirmReceipt(receipt.receiptId, {
        receiptVersion: receipt.version, orderVersion: receipt.orderVersion
      }, newKey('tx-confirm')))
      assertReason(error, 500, 'PROCUREMENT_TRANSACTION_FAILED')
      assert.equal(error.data.retryable, true)
      setScenario('')

      const balanceAfter = await api.getInventorySummary(304)
      const batchesAfter = api.normalizePage(await api.listInventoryBatches(304, { page: 1, pageSize: 50 })).total
      const movementsAfter = api.normalizePage(await api.listInventoryMovements({ componentId: 304, page: 1, pageSize: 50 })).total
      assert.equal(batchesAfter, batchesBefore)
      assert.equal(movementsAfter, movementsBefore)
      assert.equal(balanceAfter.onHandQuantity, balanceBefore.onHandQuantity)
      assert.equal(balanceAfter.version, balanceBefore.version)

      const draft = await api.getReceipt(receipt.receiptId)
      assert.equal(draft.status, 'DRAFT')
      const orderAfter = await api.getOrder(904)
      assert.equal(orderAfter.status, api.ORDER_STATUS.ORDERED)
      assert.equal(orderAfter.version, receipt.orderVersion)
    })

    await test('事务恢复后同一草稿可成功确认', async () => {
      const confirmed = await api.confirmReceipt(receipt.receiptId, {
        receiptVersion: receipt.version, orderVersion: receipt.orderVersion
      }, newKey('tx-retry'))
      assert.equal(confirmed.receiptStatus, 'CONFIRMED')
      assert.equal(confirmed.orderStatus, api.ORDER_STATUS.PARTIALLY_RECEIVED)
      const balanceAfter = await api.getInventorySummary(304)
      assert.equal(balanceAfter.onHandQuantity, balanceBefore.onHandQuantity + 100)
      assert.equal(balanceAfter.version, balanceBefore.version + 1)
    })
  })

  await group('附件 ACL 与受控媒体', async () => {
    const FORBIDDEN = ['objectKey', 'bucket', 'provider', 'endpoint', 'signedUrl', 'absolutePath', 'storagePath', 'url']

    await test('可见附件只返回受控字段', async () => {
      const list = api.normalizeAttachmentList(await api.listOrderAttachments(905))
      assert.ok(list.length >= 2)
      forbidKeys(list, FORBIDDEN, '采购附件列表')
      const invoice = list.find((item) => item.purpose === 'INVOICE')
      assert.equal(invoice.invoiceNo, 'FP-20261103-001')
      assert.ok(invoice.mediaId)
      assert.ok(invoice.contentPath.startsWith('/api/media/'))
    })

    await test('不可见 owner 统一 404 且零泄露', async () => {
      const visible = api.normalizeAttachmentList(await api.listOrderAttachments(905))
      const mediaId = visible[0].mediaId
      const fileName = visible[0].originalFileName

      setUser(99, ['MEMBER'])
      const hiddenList = await rejected(api.listOrderAttachments(905))
      assertReason(hiddenList, 404, 'MEDIA_NOT_FOUND')
      const hiddenReceipt = await rejected(api.listReceiptAttachments(1001))
      assertReason(hiddenReceipt, 404, 'MEDIA_NOT_FOUND')
      const hiddenContent = await rejected(api.getAttachmentContent(mediaId))
      assert.equal(hiddenContent.code, 404)
      assert.equal(hiddenContent.reason, 'MEDIA_NOT_FOUND')

      for (const error of [hiddenList, hiddenReceipt, hiddenContent]) {
        const json = JSON.stringify(error)
        assert.ok(!json.includes(String(mediaId)), '错误响应泄露 mediaId')
        assert.ok(!json.includes(String(fileName)), '错误响应泄露文件名')
        assert.deepEqual(Object.keys(error.data), ['traceId'])
      }
      setUser(4, ['STOCK_KEEPER'])
    })

    await test('媒体内容通过 contentPath 受控返回', async () => {
      const list = api.normalizeAttachmentList(await api.listOrderAttachments(905))
      const inline = await api.getAttachmentContent(list[0].mediaId)
      assert.ok(String(inline).startsWith('data:image/svg+xml'))
    })

    await test('发票必须填发票号；类型/大小受限', async () => {
      const version = (await api.getOrder(905)).version
      const invoiceError = await rejected(api.uploadOrderAttachment(905, {
        file: { name: 'invoice.png', type: 'image/png', size: 2048 }, purpose: 'INVOICE', invoiceNo: '', ownerVersion: version
      }, newKey('invoice-missing')))
      assert.equal(invoiceError.code, 400)
      assert.equal(invoiceError.data.field, 'invoiceNo')

      const typeError = await rejected(api.uploadOrderAttachment(905, {
        file: { name: 'photo.gif', type: 'image/gif', size: 2048 }, purpose: 'ORDER_SCREENSHOT', ownerVersion: version
      }, newKey('type-invalid')))
      assert.equal(typeError.code, 400)
      assert.equal(typeError.data.field, 'file')

      const sizeError = await rejected(api.uploadOrderAttachment(905, {
        file: { name: 'huge.png', type: 'image/png', size: api.ATTACHMENT_LIMITS.MAX_BYTES + 1 }, purpose: 'ORDER_SCREENSHOT', ownerVersion: version
      }, newKey('size-invalid')))
      assert.equal(sizeError.code, 400)
      assert.equal(sizeError.data.field, 'file')

      assert.equal(api.ATTACHMENT_LIMITS.ORDER_PER_PURPOSE, 5)
      assert.equal(api.ATTACHMENT_LIMITS.RECEIPT_TOTAL, 10)
      assert.deepEqual(api.ATTACHMENT_LIMITS.CONTENT_TYPES, ['image/jpeg', 'image/png'])
    })

    await test('采购单同一用途最多 5 个附件', async () => {
      let lastError = null
      let uploaded = 0
      for (let attempt = 0; attempt < 6; attempt += 1) {
        const version = (await api.getOrder(901)).version
        try {
          await api.uploadOrderAttachment(901, {
            file: { name: `limit-${attempt}.png`, type: 'image/png', size: 1024 },
            purpose: 'ORDER_SCREENSHOT',
            ownerVersion: version
          }, newKey('limit-upload'))
          uploaded += 1
        } catch (error) {
          lastError = error
          break
        }
      }
      assert.equal(uploaded, 5)
      assert.equal(lastError?.reason, 'PROCUREMENT_ATTACHMENT_LIMIT_EXCEEDED')
      assert.equal(lastError?.data?.limit, 5)
    })
  })

  await group('页面 / 路由 / 三视口静态检查', async () => {
    const pages = [
      'src/views/procurement/ProcurementList.vue',
      'src/views/procurement/ProcurementEditor.vue',
      'src/views/procurement/ProcurementDetail.vue',
      'src/views/procurement/ProcurementReceiptEditor.vue',
      'src/views/procurement/ProcurementReceiptDetail.vue',
      'src/views/procurement/ProcurementSettings.vue'
    ]

    await test('采购页面与共享组件存在', () => {
      for (const file of pages) assert.ok(existsSync(path.join(frontendRoot, file)), `缺少页面 ${file}`)
      for (const file of [
        'src/components/procurement/OrderStatusTag.vue',
        'src/components/procurement/ReceiptProgress.vue',
        'src/components/procurement/AttachmentPanel.vue',
        'src/components/procurement/ReceiptLinesEditor.vue'
      ]) assert.ok(existsSync(path.join(frontendRoot, file)), `缺少组件 ${file}`)
    })

    await test('8 条采购路由已注册且静态路由优先', () => {
      const router = readFileSync(path.join(frontendRoot, 'src/router/index.js'), 'utf8')
      for (const routePath of [
        'procurements', 'procurements/new', 'procurements/:id/edit', 'procurements/:id/approval',
        'procurements/:id/receipts/new', 'procurements/:id', 'procurement-receipts/:id', 'procurement/settings'
      ]) {
        assert.ok(router.includes(`path: '${routePath}'`), `缺少路由 ${routePath}`)
      }
      assert.ok(router.indexOf("path: 'procurements/new'") < router.indexOf("path: 'procurements/:id'"), '静态路由必须先于动态路由')
      assert.ok(router.includes("roles: [ROLE.SYSTEM_ADMIN]"), '策略路由必须仅系统管理员')
    })

    await test('侧栏包含采购单与审批策略', () => {
      const layout = readFileSync(path.join(frontendRoot, 'src/layout/Index.vue'), 'utf8')
      assert.ok(layout.includes("title: '采购库存'"))
      assert.ok(layout.includes("index: '/procurements'"))
      assert.ok(layout.includes("index: '/procurement/settings'"))
    })

    await test('采购筛选项目分页符合既有项目接口上限', () => {
      const listPage = readFileSync(path.join(frontendRoot, 'src/views/procurement/ProcurementList.vue'), 'utf8')
      assert.ok(listPage.includes('listProjects({ page: 1, pageSize: 50 })'))
      assert.ok(!listPage.includes('listProjects({ page: 1, pageSize: 100 })'))
    })

    await test('附件上传按钮注册 Upload 图标', () => {
      const attachmentPanel = readFileSync(path.join(frontendRoot, 'src/components/procurement/AttachmentPanel.vue'), 'utf8')
      assert.ok(attachmentPanel.includes("import { Upload } from '@element-plus/icons-vue'"))
    })

    await test('三视口：桌面表格 + 移动卡片 + 平板自适应', () => {
      for (const file of pages) {
        const source = readFileSync(path.join(frontendRoot, file), 'utf8')
        assert.ok(source.includes('@media (max-width: 767px)'), `${file} 缺少移动断点`)
        assert.ok(/min-width: 768px|auto-fit|auto-fill/.test(source), `${file} 缺少平板自适应`)
      }
    })

    await test('test:w45 脚本已注册', () => {
      const pkg = JSON.parse(readFileSync(path.join(frontendRoot, 'package.json'), 'utf8'))
      assert.ok(pkg.scripts['test:w45'])
    })
  })
}

const main = async () => {
  try {
    await run()
  } finally {
    cleanup()
  }
  process.stdout.write(`\n通过 ${pass} / ${fail}\n`)
  if (fail > 0) {
    process.stdout.write('\n失败项：\n')
    for (const item of failures) process.stdout.write(`  - ${item.name}: ${item.message}\n`)
    process.exitCode = 1
  }
}

main()
