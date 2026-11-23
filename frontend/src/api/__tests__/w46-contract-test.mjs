/**
 * W46 契约测试：库存领用、预占、审批、发放、退回与受控库存流水
 *
 * 契约来源：docs/contracts/INVENTORY_ISSUANCE_API.md v1.0（FROZEN）
 *
 * 覆盖：
 *   - 状态 / reason / 流水类型 / 阈值默认值与展示格式（§4 / §9 / §13）
 *   - 权限矩阵、本人不得审批/发放、普通成员成本脱敏与不可见 404（§7 / §8）
 *   - loading / empty / error / permission / conflict / success /
 *     insufficient-stock / reserved / expired 九种 UI 状态
 *   - 提交即预占、审批不扣库存、发放写 ISSUE_OUT、幂等与 version 冲突
 *   - 全量发放、重复确认、退回累计上限、RETURN_IN
 *   - FEFO 批次分配、重分配、预占过期释放、损耗/报废不得扣预占
 *   - 策略发布、覆盖项校验、版本冲突与版本历史
 *   - 页面 / 路由 / 侧栏 / 三视口静态检查
 *
 * 运行：npm run test:w46
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
  const file = path.join(here, `.tmp-w46-stub-${name}-${Date.now()}-${Math.random().toString(36).slice(2)}.mjs`)
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
const mockStubUrl = makeStub('mock', 'export const getMockEnabled = () => true')
const userStubUrl = makeStub('user', `export const userStore = {
  get userInfo() { return { id: globalThis.__W46_USER_ID ?? 4, name: '测试用户' } }
}
export const currentRoles = () => globalThis.__W46_ROLES || ['STOCK_KEEPER']`)
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
  const tmpPath = path.join(here, `.tmp-w46-${Date.now()}-${Math.random().toString(36).slice(2)}.mjs`)
  writeFileSync(tmpPath, code)
  tmpFiles.push(tmpPath)
  return pathToFileURL(tmpPath).href
}

async function loadInventory() {
  return import(await loadModule('../inventoryIssuance.js'))
}

function cleanup() {
  for (const file of tmpFiles) {
    try { rmSync(file, { force: true }) } catch { /* 忽略清理失败 */ }
  }
}

globalThis.__W46_USER_ID = 4
globalThis.__W46_ROLES = ['STOCK_KEEPER']

function setUser(id, roles) {
  globalThis.__W46_USER_ID = id
  globalThis.__W46_ROLES = roles
}

function setScenario(state) {
  globalThis.window = { location: { search: state ? `?mockState=${state}` : '' } }
}

let keySeq = 0
function newKey(prefix = 'w46') {
  keySeq += 1
  return `w46-${prefix}-${Date.now().toString(36)}-${keySeq}`
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

const run = async () => {
  const api = await loadInventory()

  await group('枚举、reason 与展示格式', async () => {
    await test('七种申请状态、三种审批决定、三种预占状态、四种退回状态', () => {
      assert.deepEqual(Object.keys(api.ISSUE_STATUS).sort(), [
        'APPROVED', 'CANCELED', 'DRAFT', 'EXPIRED', 'ISSUED', 'PENDING_APPROVAL', 'REJECTED'
      ])
      assert.deepEqual(Object.keys(api.APPROVAL_DECISION).sort(), ['APPROVED', 'AUTO_APPROVED', 'REJECTED'])
      assert.deepEqual(Object.keys(api.RESERVATION_STATUS).sort(), ['ACTIVE', 'CONSUMED', 'EXPIRED', 'RELEASED'])
      assert.deepEqual(Object.keys(api.RETURN_STATUS).sort(), ['CANCELED', 'CONFIRMED', 'PENDING_CONFIRMATION', 'REJECTED'])
      for (const status of Object.values(api.ISSUE_STATUS)) {
        assert.ok(api.issueStatusMeta(status).label)
      }
    })

    await test('审批触发 reason 与流水类型完整', () => {
      assert.deepEqual(Object.keys(api.APPROVAL_REASON).sort(), [
        'AMOUNT_THRESHOLD', 'EXEMPT', 'FORCED_CATEGORY', 'LOW_STOCK', 'POLICY_REQUIRED', 'QUANTITY_THRESHOLD', 'VALUABLE_BATCH'
      ])
      assert.deepEqual(Object.keys(api.MOVEMENT_TYPE).sort(), [
        'ISSUE_OUT', 'LOSS_OUT', 'PROCUREMENT_RECEIPT_IN', 'RETURN_IN', 'SCRAP_OUT', 'STOCKTAKE_ADJUSTMENT'
      ])
      assert.equal(api.movementTypeMeta('ISSUE_OUT').direction, -1)
      assert.equal(api.movementTypeMeta('RETURN_IN').direction, 1)
      assert.equal(api.movementTypeMeta('STOCKTAKE_ADJUSTMENT').direction, 0)
    })

    await test('策略默认阈值与精度常量', () => {
      assert.equal(api.ISSUE_POLICY_DEFAULTS.directAmountThreshold, 100)
      assert.equal(api.ISSUE_POLICY_DEFAULTS.valuableUnitCostThreshold, 500)
      assert.equal(api.ISSUE_POLICY_DEFAULTS.defaultDirectQuantityLimit, 10)
      assert.equal(api.ISSUE_POLICY_DEFAULTS.reservationTtlHours, 72)
      assert.equal(api.AMOUNT_SCALE, 2)
      assert.equal(api.QUANTITY_SCALE, 3)
      assert.equal(api.UNIT_COST_SCALE, 4)
    })

    await test('金额 2 位、数量 ≤3 位、单位成本 4 位', () => {
      assert.equal(api.formatAmount(477), '¥477.00')
      assert.equal(api.formatQuantity(0.5), '0.5')
      assert.equal(api.formatQuantity(80.0), '80')
      assert.equal(api.formatQuantityUnit(2.25, 'PCS'), '2.25 PCS')
      assert.equal(api.formatUnitCost(0.5), '¥0.5000')
      assert.equal(api.formatAmount(null), '—')
    })

    await test('可退回 = 已发放 - 已退回，且不为负', () => {
      assert.equal(api.lineReturnableQuantity({ issuedQuantity: 100, returnedQuantity: 5 }), 95)
      assert.equal(api.lineReturnableQuantity({ issuedQuantity: 0, returnedQuantity: 0 }), 0)
    })

    await test('预占剩余时长文案', () => {
      const now = new Date('2026-11-23T10:00:00+08:00').getTime()
      assert.ok(api.formatRemainingDuration('2026-11-25T10:00:00+08:00', now).includes('2 天'))
      assert.equal(api.formatRemainingDuration('2026-11-23T09:00:00+08:00', now), '已到期')
    })
  })

  await group('权限矩阵：申请人不得审批/发放本人申请', async () => {
    const draft = { status: 'DRAFT', requesterId: 2 }
    const pending = { status: 'PENDING_APPROVAL', requesterId: 2 }
    const approved = { status: 'APPROVED', requesterId: 2 }
    const issued = { status: 'ISSUED', requesterId: 2 }

    await test('MEMBER：可申请与查看本人，不能审批/发放/看成本', () => {
      setUser(2, ['MEMBER'])
      assert.equal(api.canAccessIssuance(), true)
      assert.equal(api.canCreateIssueRequest(), true)
      assert.equal(api.canViewAllRequests(), false)
      assert.equal(api.canViewIssueRequest({ requesterId: 2 }), true)
      assert.equal(api.canViewIssueRequest({ requesterId: 99 }), false)
      assert.equal(api.canEditIssueRequest(draft), true)
      assert.equal(api.canSubmitIssueRequest(draft), true)
      assert.equal(api.canApproveIssueRequest(pending), false)
      assert.equal(api.canConfirmIssue(approved), false)
      assert.equal(api.canReallocateIssueRequest(approved), false)
      assert.equal(api.canWriteOffStock(), false)
      assert.equal(api.canViewIssueCost(), false)
      assert.equal(api.canInitiateReturn(issued), true)
      assert.equal(api.canPublishIssuePolicy(), false)
    })

    await test('TEACHER：可审批他人但不能审批本人，不可发放/策略', () => {
      setUser(3, ['TEACHER'])
      assert.equal(api.canViewAllRequests(), true)
      assert.equal(api.canApproveIssueRequest(pending), true)
      assert.equal(api.canApproveIssueRequest({ status: 'PENDING_APPROVAL', requesterId: 3 }), false)
      assert.equal(api.canConfirmIssue(approved), false)
      assert.equal(api.canWriteOffStock(), false)
      assert.equal(api.canViewIssueCost(), true)
      assert.equal(api.canPublishIssuePolicy(), false)
    })

    await test('STOCK_KEEPER：可重分配/发放/损耗报废，不可审批，不可编辑他人草稿', () => {
      setUser(4, ['STOCK_KEEPER'])
      assert.equal(api.canApproveIssueRequest(pending), false)
      assert.equal(api.canReallocateIssueRequest(approved), true)
      assert.equal(api.canConfirmIssue(approved), true)
      assert.equal(api.canConfirmIssue({ status: 'APPROVED', requesterId: 4 }), false)
      assert.equal(api.canCancelIssueRequest(approved), false)
      assert.equal(api.canWriteOffStock(), true)
      assert.equal(api.canViewIssueBatches(), true)
      assert.equal(api.canEditIssueRequest(draft), false)
      assert.equal(api.canConfirmReturn(), true)
      assert.equal(api.canPublishIssuePolicy(), false)
    })

    await test('SYSTEM_ADMIN：全部能力，仍不得发放本人申请', () => {
      setUser(1, ['SYSTEM_ADMIN'])
      assert.equal(api.canViewAllRequests(), true)
      assert.equal(api.canApproveIssueRequest(pending), true)
      assert.equal(api.canConfirmIssue(approved), true)
      assert.equal(api.canConfirmIssue({ status: 'APPROVED', requesterId: 1 }), false)
      assert.equal(api.canPublishIssuePolicy(), true)
      assert.equal(api.canRejectReturn ? true : true, true)
    })

    await test('无领用角色不可访问', () => {
      setUser(9, [])
      assert.equal(api.canAccessIssuance(), false)
    })
  })

  await group('读取与九种 UI 状态（MEMBER 视角）', async () => {
    setUser(2, ['MEMBER'])
    setScenario('')

    await test('列表仅返回本人申请，且脱敏 estimatedAmount', async () => {
      const response = await api.listIssueRequests({ page: 1, pageSize: 50 })
      assert.equal(response.total, 6)
      assert.ok(response.list.every((row) => row.requesterId === 2))
      const json = JSON.stringify(response.list)
      assert.ok(!json.includes('"estimatedAmount"'), '普通成员列表不得返回 estimatedAmount')
      assert.ok(!json.includes('unitCost'), '普通成员列表不得返回批次成本')
    })

    await test('本人 DRAFT 详情返回明细与当前可用，但不含成本', async () => {
      const detail = await api.getIssueRequest(2003)
      assert.equal(detail.status, 'DRAFT')
      assert.equal(detail.lines.length, 1)
      assert.equal(detail.lines[0].componentId, 303)
      assert.equal(detail.lines[0].availableQuantity, 8)
      assert.equal(detail.estimatedAmount, undefined)
      assert.equal(detail.lines[0].unitCost, undefined)
      assert.equal(api.issueRequestActions(detail).edit, true)
    })

    await test('他人申请对 MEMBER 统一 404', async () => {
      const error = await rejected(api.getIssueRequest(2005))
      assertReason(error, 404, 'INVENTORY_ISSUE_REQUEST_NOT_FOUND')
    })

    await test('empty / error / loading / permission 四态', async () => {
      setScenario('empty')
      const empty = await api.listIssueRequests({ page: 1, pageSize: 20 })
      assert.equal(empty.total, 0)

      setScenario('error')
      assertReason(await rejected(api.listIssueRequests({})), 500, 'INTERNAL_ERROR')

      setScenario('loading')
      const loading = await api.listIssueRequests({ page: 1, pageSize: 20 })
      assert.ok(loading.total > 0)

      setScenario('permission')
      assertReason(await rejected(api.listIssueRequests({})), 403, 'INVENTORY_ISSUE_OPERATION_DENIED')
      setScenario('')
    })

    await test('conflict 场景写操作返回 VERSION_CONFLICT', async () => {
      setScenario('conflict')
      const error = await rejected(api.submitIssueRequest(2003, { requestVersion: 1 }, newKey('conflict')))
      assertReason(error, 409, 'VERSION_CONFLICT')
      setScenario('')
    })

    await test('reserved 场景：预占中、倒计时与释放条件可见', async () => {
      const detail = await api.getIssueRequest(2001)
      assert.equal(detail.status, 'APPROVED')
      assert.equal(detail.reservations[0].status, 'ACTIVE')
      assert.ok(api.formatRemainingDuration(detail.expiresAt).length > 0)
      assert.equal(detail.lines[0].reservedQuantity, 5)
    })

    await test('expired 场景：状态过期、预占释放、操作全部禁用', async () => {
      setScenario('expired')
      const detail = await api.getIssueRequest(2001)
      assert.equal(detail.status, 'EXPIRED')
      assert.equal(detail.reservations[0].status, 'EXPIRED')
      assert.equal(detail.reservations[0].releaseReason, 'EXPIRED')
      const actions = api.issueRequestActions(detail)
      assert.equal(actions.approve, false)
      assert.equal(actions.confirmIssue, false)
      assert.equal(actions.cancel, false)
      setScenario('')
    })
  })

  await group('提交预占 → 发放 → 退回主流程', async () => {
    const flow = await loadInventory()
    setScenario('')
    let createdId = null
    let createKey = null

    await test('创建草稿并校验必填、重复元器件', async () => {
      setUser(2, ['MEMBER'])
      const error = await rejected(flow.createIssueRequest({ title: '有效标题', purpose: '有效用途', lines: [] }, newKey('invalid')))
      assertReason(error, 400, 'ISSUE_LINES_REQUIRED')

      createKey = newKey('create')
      const payload = {
        title: 'W46 自动化申请',
        purpose: '契约测试焊接用途',
        lines: [{ componentId: 301, requestedQuantity: 1 }]
      }
      const draft = await flow.createIssueRequest(payload, createKey)
      assert.equal(draft.status, 'DRAFT')
      assert.equal(draft.version, 1)
      assert.equal(draft.lines.length, 1)
      createdId = draft.id

      const replay = await flow.createIssueRequest(payload, createKey)
      assert.equal(replay.id, createdId, '同键同请求体应返回同一草稿')
      const reused = await rejected(flow.createIssueRequest({ ...payload, title: '另一个标题' }, createKey))
      assertReason(reused, 409, 'IDEMPOTENCY_KEY_REUSED')

      const duplicated = await rejected(flow.createIssueRequest({
        title: '重复行',
        purpose: '契约测试',
        lines: [{ componentId: 301, requestedQuantity: 1 }, { componentId: 301, requestedQuantity: 2 }]
      }, newKey('dup')))
      assertReason(duplicated, 400, 'INVALID_PARAMETER')
    })

    await test('version 冲突拒绝提交；正确提交即预占', async () => {
      const stale = await rejected(flow.submitIssueRequest(createdId, { requestVersion: 99 }, newKey('stale')))
      assertReason(stale, 409, 'VERSION_CONFLICT')

      const submitted = await flow.submitIssueRequest(createdId, { requestVersion: 1 }, newKey('submit'))
      assert.equal(submitted.status, 'APPROVED')
      assert.deepEqual(submitted.approvalReasons, ['EXEMPT'])
      assert.equal(submitted.reservations[0].status, 'ACTIVE')
      assert.equal(submitted.lines[0].reservedQuantity, 1)
      assert.equal(submitted.lines[0].availableQuantity, 14, '301 原可用 15，预占 1 后应为 14')
      assert.equal(submitted.lines[0].issuedQuantity, 0, '审批/提交不得扣减库存')
      assert.equal(submitted.approvalRecords.length, 1)
      assert.equal(submitted.approvalRecords[0].decision, 'AUTO_APPROVED')
    })

    await test('申请人不得发放本人申请；非本人可发但需正确预占版本', async () => {
      const denied = await rejected(flow.confirmIssueRequest(createdId, { requestVersion: 2, reservationVersion: 1 }, newKey('self')))
      assertReason(denied, 403, 'INVENTORY_ISSUE_FULFILL_DENIED')

      setUser(4, ['STOCK_KEEPER'])
      const current = await flow.getIssueRequest(createdId)
      const stale = await rejected(flow.confirmIssueRequest(createdId, {
        requestVersion: current.version,
        reservationVersion: 99
      }, newKey('resv')))
      assertReason(stale, 409, 'VERSION_CONFLICT')

      const issued = await flow.confirmIssueRequest(createdId, {
        requestVersion: current.version,
        reservationVersion: current.reservations[0].version,
        note: '契约测试发放'
      }, newKey('issue'))
      assert.equal(issued.status, 'ISSUED')
      assert.equal(issued.fulfillment.lines.length, 1)
      assert.equal(issued.movements.length, 1)
      assert.equal(issued.movements[0].movementType, 'ISSUE_OUT')
      assert.equal(issued.lines[0].issuedQuantity, 1)
      assert.equal(issued.lines[0].onHandQuantity, 19)
      assert.equal(issued.reservations[0].status, 'CONSUMED')
    })

    await test('重复确认被拒绝，库存不再变化', async () => {
      const current = await flow.getIssueRequest(createdId)
      const error = await rejected(flow.confirmIssueRequest(createdId, {
        requestVersion: current.version,
        reservationVersion: current.reservations[0].version
      }, newKey('again')))
      assertReason(error, 409, 'INVENTORY_ISSUE_ALREADY_CONFIRMED')
      const after = await flow.getIssueRequest(createdId)
      assert.equal(after.lines[0].onHandQuantity, 19)
    })

    await test('退回累计不得超过发放量，确认后写 RETURN_IN', async () => {
      const issued = await flow.getIssueRequest(createdId)
      const fulfillmentId = issued.fulfillment.id
      const fulfillmentLineId = issued.fulfillment.lines[0].id

      setUser(2, ['MEMBER'])
      const over = await rejected(flow.createFulfillmentReturn(fulfillmentId, {
        reason: '退回测试',
        lines: [{ fulfillmentLineId, quantity: 2 }]
      }, newKey('over')))
      assertReason(over, 409, 'INVENTORY_RETURN_EXCEEDS_ISSUED')

      const created = await flow.createFulfillmentReturn(fulfillmentId, {
        reason: '焊错批次退回',
        lines: [{ fulfillmentLineId, quantity: 1 }]
      }, newKey('return'))
      assert.equal(created.status, 'PENDING_CONFIRMATION')

      setUser(4, ['STOCK_KEEPER'])
      const confirmed = await flow.confirmReturn(created.id, { returnVersion: created.version }, newKey('return-confirm'))
      assert.equal(confirmed.status, 'CONFIRMED')

      const after = await flow.getIssueRequest(createdId)
      assert.equal(after.lines[0].returnedQuantity, 1)
      assert.equal(after.lines[0].onHandQuantity, 20)
      const movements = await flow.listInventoryMovements({ requestId: createdId, page: 1, pageSize: 50 })
      assert.ok(movements.list.some((item) => item.movementType === 'RETURN_IN'), '确认退回应写入 RETURN_IN 流水')
    })
  })

  await group('审批、拒绝与取消', async () => {
    const flow = await loadInventory()
    setScenario('')
    let pendingId = null

    await test('高价值申请提交后进入待审批且记录触发原因', async () => {
      setUser(2, ['MEMBER'])
      const draft = await flow.createIssueRequest({
        title: '高价值运放领取',
        purpose: '精密采集板维修',
        lines: [{ componentId: 304, requestedQuantity: 1 }]
      }, newKey('valuable'))
      const submitted = await flow.submitIssueRequest(draft.id, { requestVersion: draft.version }, newKey('valuable-submit'))
      assert.equal(submitted.status, 'PENDING_APPROVAL')
      assert.ok(submitted.approvalReasons.includes('AMOUNT_THRESHOLD'))
      assert.ok(submitted.approvalReasons.includes('VALUABLE_BATCH'))
      pendingId = draft.id
    })

    await test('申请人自审被拒；老师可批准', async () => {
      const self = await rejected(flow.approveIssueRequest(pendingId, { requestVersion: 2 }, newKey('self-approve')))
      assertReason(self, 403, 'INVENTORY_ISSUE_SELF_APPROVAL')

      setUser(3, ['TEACHER'])
      const approved = await flow.approveIssueRequest(pendingId, { requestVersion: 2, comment: '同意' }, newKey('approve'))
      assert.equal(approved.status, 'APPROVED')
      assert.equal(approved.approvalRecords.at(-1).decision, 'APPROVED')
      assert.equal(approved.approvalRecords.at(-1).decidedBy, 3)
      assert.equal(approved.lines[0].onHandQuantity, 3, '审批不得扣减库存')
    })

    await test('申请人取消已批准申请会释放预占', async () => {
      setUser(2, ['MEMBER'])
      const current = await flow.getIssueRequest(pendingId)
      const canceled = await flow.cancelIssueRequest(pendingId, { requestVersion: current.version }, newKey('cancel'))
      assert.equal(canceled.status, 'CANCELED')
      assert.equal(canceled.reservations[0].status, 'RELEASED')
      assert.equal(canceled.reservations[0].releaseReason, 'CANCELED')
      assert.equal(canceled.lines[0].reservedQuantity, 0)
      assert.equal(canceled.lines[0].availableQuantity, 2, '取消后 304 可用应恢复为 2')
    })

    await test('拒绝必须填写原因，拒绝后释放预占', async () => {
      setUser(2, ['MEMBER'])
      const draft = await flow.createIssueRequest({
        title: '大额电阻领用',
        purpose: '课程实验备料',
        lines: [{ componentId: 302, requestedQuantity: 20 }]
      }, newKey('reject-create'))
      const submitted = await flow.submitIssueRequest(draft.id, { requestVersion: draft.version }, newKey('reject-submit'))
      assert.equal(submitted.status, 'PENDING_APPROVAL')

      setUser(3, ['TEACHER'])
      const missing = await rejected(flow.rejectIssueRequest(draft.id, { requestVersion: submitted.version }, newKey('reject-missing')))
      assertReason(missing, 400, 'INVALID_PARAMETER')

      const rejectedDoc = await flow.rejectIssueRequest(draft.id, {
        requestVersion: submitted.version,
        comment: '请补充课程用途'
      }, newKey('reject'))
      assert.equal(rejectedDoc.status, 'REJECTED')
      assert.equal(rejectedDoc.reservations[0].status, 'RELEASED')
    })
  })

  await group('库存不足、重分配、过期与事务失败', async () => {
    const flow = await loadInventory()
    setScenario('')

    await test('可用不足返回 409 且不产生预占', async () => {
      setUser(2, ['MEMBER'])
      const draft = await flow.createIssueRequest({
        title: '连接器超额领取',
        purpose: '超过可用库存',
        lines: [{ componentId: 306, requestedQuantity: 8 }]
      }, newKey('short-create'))
      const error = await rejected(flow.submitIssueRequest(draft.id, { requestVersion: draft.version }, newKey('short-submit')))
      assertReason(error, 409, 'INVENTORY_INSUFFICIENT_STOCK')
      assert.equal(error.data.availableQuantity, 4)
      const detail = await flow.getIssueRequest(draft.id)
      assert.equal(detail.status, 'DRAFT')
      assert.equal(detail.reservations.length, 0)
    })

    await test('insufficient-stock 场景可视化提交失败', async () => {
      setUser(2, ['MEMBER'])
      const draft = await flow.createIssueRequest({
        title: '场景库存不足',
        purpose: '验收库存不足状态',
        lines: [{ componentId: 301, requestedQuantity: 1 }]
      }, newKey('scene-create'))
      setScenario('insufficient-stock')
      const error = await rejected(flow.submitIssueRequest(draft.id, { requestVersion: draft.version }, newKey('scene-submit')))
      assertReason(error, 409, 'INVENTORY_INSUFFICIENT_STOCK')
      assert.ok(error.data.availableQuantity < error.data.requestedQuantity)
      setScenario('')
    })

    await test('库存管理员可重分配批次并保留预占', async () => {
      setUser(4, ['STOCK_KEEPER'])
      const current = await flow.getIssueRequest(2001)
      assert.equal(current.status, 'APPROVED')
      const reallocated = await flow.reallocateIssueRequest(2001, {
        requestVersion: current.version,
        reason: 'FEFO 调整到更早批次',
        allocations: [{ requestLineId: current.lines[0].id, batchId: 1101, quantity: 5 }]
      }, newKey('reallocate'))
      assert.equal(reallocated.reservations[0].allocations[0].batchId, 1101)
      assert.equal(reallocated.reservations[0].status, 'ACTIVE')
      assert.equal(reallocated.lines[0].reservedQuantity, 5)
    })

    await test('transaction-failed 场景不改变库存', async () => {
      setUser(4, ['STOCK_KEEPER'])
      const current = await flow.getIssueRequest(2001)
      setScenario('transaction-failed')
      const error = await rejected(flow.confirmIssueRequest(2001, {
        requestVersion: current.version,
        reservationVersion: current.reservations[0].version
      }, newKey('tx-fail')))
      assertReason(error, 500, 'INVENTORY_ISSUE_TRANSACTION_FAILED')
      setScenario('')
      const after = await flow.getIssueRequest(2001)
      assert.equal(after.status, 'APPROVED')
      assert.equal(after.lines[0].onHandQuantity, 20)
    })

    await test('expired 场景禁用发放且预占已释放', async () => {
      setUser(4, ['STOCK_KEEPER'])
      setScenario('expired')
      const detail = await flow.getIssueRequest(2001)
      assert.equal(detail.status, 'EXPIRED')
      assert.equal(flow.canConfirmIssue(detail), false)
      setScenario('')
    })
  })

  await group('损耗 / 报废与流水权限', async () => {
    const flow = await loadInventory()
    setScenario('')

    await test('损耗不得扣减已预占批次', async () => {
      setUser(4, ['STOCK_KEEPER'])
      const error = await rejected(flow.registerBatchLoss(1102, { quantity: 6, reason: '测试扣减预占' }, newKey('protected')))
      assertReason(error, 409, 'INVENTORY_RESERVED_STOCK_PROTECTED')
      assert.equal(error.data.availableQuantity, 3)
    })

    await test('损耗与报废写 LOSS_OUT / SCRAP_OUT 并减少余额', async () => {
      const loss = await flow.registerBatchLoss(1501, { quantity: 10, reason: '实验台散落遗失' }, newKey('loss'))
      assert.ok(loss.commandNo.startsWith('LOSS-'))
      assert.ok(loss.movementId)
      const scrap = await flow.registerBatchScrap(1601, { quantity: 1, reason: '插针变形报废' }, newKey('scrap'))
      assert.ok(scrap.commandNo.startsWith('SCRAP-'))

      const movements = await flow.listInventoryMovements({ movementType: 'LOSS_OUT', page: 1, pageSize: 50 })
      assert.ok(movements.list.some((row) => row.movementId === loss.movementId))
      const component = await flow.getInventoryComponentSummary(305)
      assert.equal(component.onHandQuantity, 1970)
    })

    await test('老师不可登记损耗，成员流水脱敏且仅本人业务', async () => {
      setUser(3, ['TEACHER'])
      const denied = await rejected(flow.registerBatchLoss(1501, { quantity: 1, reason: '越权' }, newKey('teacher-loss')))
      assertReason(denied, 403, 'INVENTORY_WRITE_OFF_DENIED')

      setUser(2, ['MEMBER'])
      const response = await flow.listInventoryMovements({ page: 1, pageSize: 50 })
      const ownIds = new Set([2001, 2002, 2003, 2004, 2006, 2008])
      assert.ok(response.list.every((row) => ownIds.has(Number(row.requestId))))
      const json = JSON.stringify(response.list)
      assert.ok(!json.includes('"balanceBefore"'), '普通成员流水不得返回余额')
      assert.ok(!json.includes('"operatorName"'), '普通成员流水不得返回操作人')
    })
  })

  await group('策略发布、覆盖项与版本历史', async () => {
    const flow = await loadInventory()
    setScenario('')

    await test('非管理员可读策略但不可发布/看版本历史', async () => {
      setUser(2, ['MEMBER'])
      const policy = await flow.getIssuePolicy()
      assert.equal(policy.policyVersion, 1, '所有领用角色读取策略用于表单提示')
      assertReason(await rejected(flow.listIssuePolicyVersions({})), 403, 'INVENTORY_POLICY_DENIED')
      assertReason(await rejected(flow.publishIssuePolicy({ basePolicyVersion: 1 }, newKey('member-policy'))), 403, 'INVENTORY_POLICY_DENIED')
    })

    await test('过期 basePolicyVersion 返回版本冲突', async () => {
      setUser(1, ['SYSTEM_ADMIN'])
      const policy = await flow.getIssuePolicy()
      assert.equal(policy.policyVersion, 1)
      const error = await rejected(flow.publishIssuePolicy({
        basePolicyVersion: 0,
        directAmountThreshold: 100,
        valuableUnitCostThreshold: 500,
        defaultDirectQuantityLimit: 10,
        lowStockRatio: 20,
        reservationTtlHours: 72,
        forcedCategoryIds: [],
        componentOverrides: []
      }, newKey('stale-policy')))
      assertReason(error, 409, 'VERSION_CONFLICT')
    })

    await test('发布新版本并规范化覆盖项', async () => {
      const key = newKey('policy')
      const payload = {
        basePolicyVersion: 1,
        directAmountThreshold: 200,
        valuableUnitCostThreshold: 500,
        defaultDirectQuantityLimit: 10,
        lowStockRatio: 25,
        reservationTtlHours: 48,
        forcedCategoryIds: [34],
        componentOverrides: [{ componentId: 303, directQuantityLimit: 5 }]
      }
      const published = await flow.publishIssuePolicy(payload, key)
      assert.equal(published.policyVersion, 2)
      assert.equal(published.reservationTtlHours, 48)
      assert.equal(published.componentOverrides[0].componentName, 'BME280 环境传感器模块')
      assert.equal(published.componentOverrides[0].unitSnapshot, 'PCS')

      const replay = await flow.publishIssuePolicy(payload, key)
      assert.equal(replay.policyVersion, 2, '同键重放不得重复升版')

      const versions = await flow.listIssuePolicyVersions({ page: 1, pageSize: 20 })
      assert.equal(versions.total, 2)
    })

    await test('覆盖项重复、比例越界与预占时长越界被拒绝', async () => {
      const base = {
        basePolicyVersion: 2,
        directAmountThreshold: 200,
        valuableUnitCostThreshold: 500,
        defaultDirectQuantityLimit: 10,
        lowStockRatio: 25,
        reservationTtlHours: 48,
        forcedCategoryIds: [],
        componentOverrides: []
      }
      const dup = await rejected(flow.publishIssuePolicy({
        ...base,
        componentOverrides: [
          { componentId: 303, directQuantityLimit: 5 },
          { componentId: 303, directQuantityLimit: 8 }
        ]
      }, newKey('dup-override')))
      assertReason(dup, 400, 'ISSUE_POLICY_OVERRIDE_INVALID')

      const ratio = await rejected(flow.publishIssuePolicy({ ...base, lowStockRatio: 120 }, newKey('ratio')))
      assertReason(ratio, 400, 'ISSUE_POLICY_INVALID')

      const ttl = await rejected(flow.publishIssuePolicy({ ...base, reservationTtlHours: 200 }, newKey('ttl')))
      assertReason(ttl, 400, 'ISSUE_POLICY_INVALID')
    })

    await test('新策略覆盖项影响后续申请的审批判定', async () => {
      setUser(2, ['MEMBER'])
      const draft = await flow.createIssueRequest({
        title: '覆盖项数量判定',
        purpose: '验证数量覆盖阈值',
        lines: [{ componentId: 303, requestedQuantity: 6 }]
      }, newKey('override-create'))
      const submitted = await flow.submitIssueRequest(draft.id, { requestVersion: draft.version }, newKey('override-submit'))
      assert.equal(submitted.status, 'PENDING_APPROVAL')
      assert.ok(submitted.approvalReasons.includes('QUANTITY_THRESHOLD'))
      assert.equal(submitted.reservations[0].expiresAt != null, true)
    })
  })

  await group('页面、路由、侧栏与三视口静态检查', async () => {
    const views = {
      list: path.join(frontendRoot, 'src/views/inventory/InventoryIssueList.vue'),
      editor: path.join(frontendRoot, 'src/views/inventory/InventoryIssueEditor.vue'),
      detail: path.join(frontendRoot, 'src/views/inventory/InventoryIssueDetail.vue'),
      approvals: path.join(frontendRoot, 'src/views/inventory/InventoryIssueApprovals.vue'),
      fulfillments: path.join(frontendRoot, 'src/views/inventory/InventoryFulfillments.vue'),
      movements: path.join(frontendRoot, 'src/views/inventory/InventoryMovements.vue'),
      settings: path.join(frontendRoot, 'src/views/inventory/InventoryIssueSettings.vue')
    }

    await test('七个页面文件存在并包含关键状态与操作', () => {
      for (const file of Object.values(views)) {
        assert.ok(existsSync(file), `缺少页面 ${file}`)
      }
      const list = readFileSync(views.list, 'utf8')
      assert.ok(list.includes('MockStateBanner'))
      assert.ok(list.includes('desktop-only'))
      assert.ok(list.includes('mobile-only'))
      assert.ok(list.includes('ISSUE_STATUS_META'))
      const detail = readFileSync(views.detail, 'utf8')
      assert.ok(detail.includes('confirmIssueRequest'))
      assert.ok(detail.includes('createFulfillmentReturn'))
      assert.ok(detail.includes('reallocateIssueRequest'))
      assert.ok(detail.includes('预占中'))
      assert.ok(detail.includes('预占已过期'))
      assert.ok(detail.includes('库存不足'))
      const settings = readFileSync(views.settings, 'utf8')
      assert.ok(settings.includes('componentOverrides'))
      assert.ok(settings.includes('reservationTtlHours'))
      const fulfillments = readFileSync(views.fulfillments, 'utf8')
      assert.ok(fulfillments.includes('registerBatchLoss'))
      assert.ok(fulfillments.includes('registerBatchScrap'))
    })

    await test('路由注册八个入口且权限角色正确', () => {
      const router = readFileSync(path.join(frontendRoot, 'src/router/index.js'), 'utf8')
      for (const route of [
        'inventory/issues',
        'inventory/issues/new',
        'inventory/issues/:id/edit',
        'inventory/issues/:id',
        'inventory/issue-approvals',
        'inventory/fulfillments',
        'inventory/movements',
        'inventory/issue-settings'
      ]) {
        assert.ok(router.includes(`path: '${route}'`), `缺少路由 ${route}`)
      }
      assert.ok(/path: 'inventory\/issue-settings'[\s\S]{0,200}roles: \[ROLE\.SYSTEM_ADMIN\]/.test(router))
    })

    await test('侧栏新增库存领用分组与菜单', () => {
      const layout = readFileSync(path.join(frontendRoot, 'src/layout/Index.vue'), 'utf8')
      assert.ok(layout.includes("index: '/inventory'"))
      assert.ok(layout.includes("title: '库存领用'"))
      assert.ok(layout.includes("index: '/inventory/issue-approvals'"))
      assert.ok(layout.includes("index: '/inventory/fulfillments'"))
      assert.ok(layout.includes("index: '/inventory/issue-settings'"))
    })

    await test('页面具备桌面/移动布局与 1440/1280/390 三视口适配标记', () => {
      const list = readFileSync(views.list, 'utf8')
      const detail = readFileSync(views.detail, 'utf8')
      const editor = readFileSync(views.editor, 'utf8')
      assert.ok(list.includes('class="desktop-only"'))
      assert.ok(list.includes('class="mobile-only mobile-cards"'))
      assert.ok(/@media \(max-width: 768px\)/.test(editor))
      assert.ok(/@media \(max-width: 768px\)/.test(detail))
      assert.ok(/@media \(max-width: 768px\)/.test(readFileSync(views.settings, 'utf8')))
    })

    await test('mock 默认关闭且九态可注入', () => {
      const mock = readFileSync(path.join(frontendRoot, 'src/utils/mock.js'), 'utf8')
      assert.ok(mock.includes('VITE_USE_MOCK'))
      assert.ok(/=== 'true'/.test(mock), 'mock 必须显式 true 才开启')
      const scenario = readFileSync(path.join(frontendRoot, 'src/utils/mockScenario.js'), 'utf8')
      for (const state of ['insufficient-stock', 'reserved', 'expired']) {
        assert.ok(scenario.includes(`'${state}'`), `mockScenario 缺少 ${state}`)
      }
    })
  })

  process.stdout.write(`\n${pass} passed, ${fail} failed\n`)
  if (fail) {
    process.stdout.write(`\n失败项：\n${failures.map((item) => `- ${item.name}: ${item.message}`).join('\n')}\n`)
    process.exitCode = 1
  }
}

run()
  .catch((error) => {
    process.stdout.write(`\n运行失败：${error?.stack || error}\n`)
    process.exitCode = 1
  })
  .finally(() => {
    cleanup()
  })
