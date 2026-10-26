/**
 * W43 契约测试：实验记录 / 修订 / 导师反馈 / 采集计划 / 批次 / CSV 导入 / 人工观测 / 快照 / 项目转换
 *
 * 契约来源：docs/contracts/EXPERIMENT_RECORD_ACQUISITION_API.md v1.0（FROZEN）
 *           docs/weekly/2026-W43.md §7 / §8 / §13
 *
 * 覆盖：
 *   - 记录/修订/反馈/采集枚举与中文映射（契约 §2 / §3）
 *   - 记录与修订状态机、计划/批次状态机（§4.1 / §3.4 / §3.5）
 *   - 13 个记录端点 + 20 个采集端点路径（§8.1 / §8.2 / §8.3）
 *   - 幂等键（创建/提交/反馈/发布/转项目/建批次/确认导入/完成批次/生成快照）与乐观锁（§8.4 / §8.5）
 *   - 权限矩阵（§7.1）：成员不可自我评审、MEMBER 不可管理计划、GUEST 不可访问
 *   - 完整主流程：保存 → 提交 → 要求修改 → 重新提交 → 批准 → 发布 → 新修订 → 绑定快照 → 转项目
 *   - 采集主流程：计划 → 批次 → CSV 上传/预览/映射/确认 → 完成 → 快照 → 不可变
 *   - 人工观测：新增 → 更正追加修订 → 完成批次后不可改
 *   - UI 状态注入：loading/empty/error/permission/conflict/immutable（§11.3）
 *   - 错误码归一化（§10）与页面/路由/侧栏静态检查
 *
 * 运行：node src/api/__tests__/w43-contract-test.mjs
 *
 * 不引入 jest / vitest；保持与 w42-contract-test.mjs 一致的“纯 Node + assert”风格，
 * 通过临时文件替换 @/ 别名后动态 import。
 */

import assert from 'node:assert/strict'
import { readFileSync, writeFileSync, rmSync } from 'node:fs'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'

const here = path.dirname(fileURLToPath(import.meta.url))
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
    failures.push({ name, message: err.message })
    process.stdout.write(`  ✗ ${name}\n    ${err.message}\n`)
  }
}

async function group(name, fn) {
  process.stdout.write(`\n# ${name}\n`)
  await fn()
}

function makeStub(name, code) {
  const file = path.join(here, `.tmp-w43-stub-${name}-${Date.now()}-${Math.random().toString(36).slice(2)}.mjs`)
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
  get userInfo() { return { id: globalThis.__W43_USER_ID ?? 2 } }
}
export const currentRoles = () => globalThis.__W43_ROLES || ['MEMBER']`)
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

async function loadModule(srcRelative, extraAliases = {}) {
  const srcUrl = new URL(srcRelative, import.meta.url)
  let code = readFileSync(srcUrl, 'utf8')
  const map = { ...aliasTargets, ...extraAliases }
  code = code.replace(/from '@\/([^']+)'/g, (whole, sub) => {
    const target = map[sub]
    assert.ok(target, `缺少别名映射 @/${sub}`)
    return `from '${target}'`
  })
  const tmpPath = path.join(here, `.tmp-w43-${Date.now()}-${Math.random().toString(36).slice(2)}.mjs`)
  writeFileSync(tmpPath, code)
  tmpFiles.push(tmpPath)
  return { mod: await import(pathToFileURL(tmpPath).href), url: pathToFileURL(tmpPath).href }
}

function cleanup() {
  for (const f of tmpFiles) {
    try { rmSync(f, { force: true }) } catch { /* 忽略清理失败 */ }
  }
}

globalThis.__W43_USER_ID = 2
globalThis.__W43_ROLES = ['MEMBER']

function setUser(id, roles = ['MEMBER']) {
  globalThis.__W43_USER_ID = id
  globalThis.__W43_ROLES = roles
}

// ========== 加载被测模块（依赖顺序：projects → templates → collection → records） ==========
const projectsLoaded = await loadModule('../projects.js')
aliasTargets['api/projects'] = projectsLoaded.url

const templatesLoaded = await loadModule('../experimentTemplates.js')
aliasTargets['api/experimentTemplates'] = templatesLoaded.url

const collectionLoaded = await loadModule('../collection.js')
aliasTargets['api/collection'] = collectionLoaded.url

const recordsLoaded = await loadModule('../experimentRecords.js')

const apiError = (await loadModule('../../utils/apiError.js', {
  'element-plus': makeStub('element-plus', `export const ElMessage = { error() {} }
export const ElMessageBox = { confirm() { return Promise.resolve() } }`)
})).mod

const idempotency = await import(pathToFileURL(path.join(here, '../../utils/idempotency.js')).href)
const mockScenario = await import(pathToFileURL(path.join(here, '../../utils/mockScenario.js')).href)

const records = recordsLoaded.mod
const collection = collectionLoaded.mod

function assertErrorShape(err, code, reason) {
  assert.equal(err.code, code, `错误 code 应为 ${code}，实际 ${err.code}`)
  assert.equal(err.reason, reason, `错误 reason 应为 ${reason}，实际 ${err.reason}`)
  assert.equal(typeof err.msg, 'string', 'msg 应为 string')
}

async function expectReject(fn, code, reason) {
  await assert.rejects(fn, (err) => {
    assertErrorShape(err, code, reason)
    return true
  })
}

const RECORD_STATUSES = ['DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'PUBLISHED', 'REVISING']
const REVISION_STATUSES = ['EDITABLE', 'SUBMITTED', 'CHANGES_REQUESTED', 'APPROVED', 'PUBLISHED', 'SUPERSEDED']
const FEEDBACK_TYPES = ['COMMENT', 'CHANGE_REQUEST', 'APPROVAL']
const PLAN_STATUSES = ['DRAFT', 'READY', 'ARCHIVED', 'CANCELED']
const BATCH_STATUSES = ['CREATED', 'PROCESSING', 'REVIEW_REQUIRED', 'COMPLETED', 'FAILED', 'CANCELED', 'ARCHIVED']
const IMPORT_STATUSES = ['UPLOADED', 'MAPPING_REQUIRED', 'CONFIRMED', 'FAILED']
const VALUE_TYPES = ['DECIMAL', 'INTEGER', 'BOOLEAN', 'STRING']

// ========== 枚举与映射（§2 / §3） ==========
await group('枚举与中文映射（契约 §2 / §3）', async () => {
  await test('记录状态六态齐全且 META 含 label/tagType/desc', async () => {
    assert.deepEqual(Object.values(records.RECORD_STATUS).sort(), [...RECORD_STATUSES].sort())
    for (const s of RECORD_STATUSES) {
      assert.ok(records.RECORD_STATUS_META[s], `缺少记录状态 META：${s}`)
      assert.equal(typeof records.RECORD_STATUS_META[s].label, 'string')
      assert.equal(typeof records.RECORD_STATUS_META[s].tagType, 'string')
      assert.equal(typeof records.RECORD_STATUS_META[s].desc, 'string')
    }
    assert.equal(records.RECORD_STATUS_META.PUBLISHED.label, '已发布')
    assert.equal(records.RECORD_STATUS_META.CHANGES_REQUESTED.label, '待修改')
  })

  await test('修订状态六态齐全，仅 EDITABLE 可编辑（§2 D1）', async () => {
    assert.deepEqual(Object.values(records.REVISION_STATUS).sort(), [...REVISION_STATUSES].sort())
    for (const s of REVISION_STATUSES) {
      assert.ok(records.REVISION_STATUS_META[s], `缺少修订状态 META：${s}`)
      assert.equal(typeof records.REVISION_STATUS_META[s].immutable, 'boolean')
    }
    assert.equal(records.REVISION_STATUS_META.EDITABLE.immutable, false)
    for (const s of REVISION_STATUSES.filter((x) => x !== 'EDITABLE')) {
      assert.equal(records.REVISION_STATUS_META[s].immutable, true, `${s} 应为只读`)
    }
  })

  await test('反馈类型与可见范围齐全（§3.3）', async () => {
    assert.deepEqual(Object.values(records.FEEDBACK_TYPE).sort(), [...FEEDBACK_TYPES].sort())
    for (const t of FEEDBACK_TYPES) {
      assert.ok(records.FEEDBACK_TYPE_META[t], `缺少反馈类型 META：${t}`)
      assert.equal(typeof records.FEEDBACK_TYPE_META[t].action, 'boolean')
    }
    assert.equal(records.FEEDBACK_TYPE_META.COMMENT.action, false, 'COMMENT 不是状态动作')
    assert.equal(records.FEEDBACK_TYPE_META.CHANGE_REQUEST.action, true)
    assert.equal(records.FEEDBACK_TYPE_META.APPROVAL.action, true)
    assert.deepEqual(
      Object.values(records.FEEDBACK_VISIBILITY).sort(),
      ['AUTHOR_AND_REVIEWERS', 'PROJECT_MEMBERS'].sort()
    )
    for (const v of Object.values(records.FEEDBACK_VISIBILITY)) {
      assert.ok(records.FEEDBACK_VISIBILITY_META[v], `缺少可见范围 META：${v}`)
    }
  })

  await test('采集计划/批次/导入/值类型枚举齐全', async () => {
    assert.deepEqual(Object.values(collection.COLLECTION_PLAN_STATUS).sort(), [...PLAN_STATUSES].sort())
    assert.deepEqual(Object.values(collection.COLLECTION_BATCH_STATUS).sort(), [...BATCH_STATUSES].sort())
    assert.deepEqual(Object.values(collection.DATA_IMPORT_STATUS).sort(), [...IMPORT_STATUSES].sort())
    assert.deepEqual(Object.values(collection.COLLECTION_VALUE_TYPE).sort(), [...VALUE_TYPES].sort())
    assert.deepEqual(Object.values(collection.COLLECTION_SOURCE_TYPE).sort(), ['FILE', 'MANUAL'].sort())
    assert.deepEqual(Object.values(collection.SNAPSHOT_TYPE), ['FULL_BATCH'])
    assert.deepEqual(Object.values(collection.QUALITY_STATUS).sort(), ['GOOD', 'REJECTED'].sort())
    for (const s of PLAN_STATUSES) assert.ok(collection.COLLECTION_PLAN_STATUS_META[s])
    for (const s of BATCH_STATUSES) assert.ok(collection.COLLECTION_BATCH_STATUS_META[s])
    for (const s of IMPORT_STATUSES) assert.ok(collection.DATA_IMPORT_STATUS_META[s])
  })

  await test('未知状态回退安全，不抛异常', async () => {
    assert.equal(records.recordStatusMeta('UNKNOWN').label, 'UNKNOWN')
    assert.equal(records.revisionStatusMeta(null).immutable, true)
    assert.equal(collection.batchStatusMeta('NOPE').label, 'NOPE')
  })
})

// ========== 状态机（§4.1 / §3.4 / §3.5） ==========
await group('状态机与不可变判定（契约 §4.1 / §3.4 / §3.5 / §6）', async () => {
  await test('记录状态机与 §4.1 完全一致', async () => {
    assert.deepEqual(records.RECORD_TRANSITIONS.DRAFT, ['IN_REVIEW'])
    assert.deepEqual(records.RECORD_TRANSITIONS.IN_REVIEW, ['CHANGES_REQUESTED', 'APPROVED'])
    assert.deepEqual(records.RECORD_TRANSITIONS.CHANGES_REQUESTED, ['IN_REVIEW'])
    assert.deepEqual(records.RECORD_TRANSITIONS.APPROVED, ['PUBLISHED'])
    assert.deepEqual(records.RECORD_TRANSITIONS.PUBLISHED, ['REVISING'])
    assert.deepEqual(records.RECORD_TRANSITIONS.REVISING, ['IN_REVIEW'])
    assert.equal(records.canTransitionRecord('DRAFT', 'IN_REVIEW'), true)
    assert.equal(records.canTransitionRecord('DRAFT', 'PUBLISHED'), false)
    assert.equal(records.canTransitionRecord('IN_REVIEW', 'REVISING'), false)
  })

  await test('修订状态机与 D1 完全一致，SUPERSEDED/CHANGES_REQUESTED 为终态', async () => {
    assert.deepEqual(records.REVISION_TRANSITIONS.EDITABLE, ['SUBMITTED'])
    assert.deepEqual(records.REVISION_TRANSITIONS.SUBMITTED, ['CHANGES_REQUESTED', 'APPROVED'])
    assert.deepEqual(records.REVISION_TRANSITIONS.CHANGES_REQUESTED, [])
    assert.deepEqual(records.REVISION_TRANSITIONS.APPROVED, ['PUBLISHED'])
    assert.deepEqual(records.REVISION_TRANSITIONS.PUBLISHED, ['SUPERSEDED'])
    assert.deepEqual(records.REVISION_TRANSITIONS.SUPERSEDED, [])
    assert.equal(records.canTransitionRevision('EDITABLE', 'SUBMITTED'), true)
    assert.equal(records.canTransitionRevision('EDITABLE', 'APPROVED'), false)
  })

  await test('计划/批次状态机与不可变状态', async () => {
    assert.deepEqual(collection.PLAN_TRANSITIONS.DRAFT, ['READY', 'CANCELED'])
    assert.deepEqual(collection.PLAN_TRANSITIONS.READY, ['ARCHIVED', 'CANCELED'])
    assert.deepEqual(collection.PLAN_TRANSITIONS.ARCHIVED, [])
    assert.deepEqual(collection.PLAN_TRANSITIONS.CANCELED, [])
    assert.deepEqual(collection.BATCH_TRANSITIONS.COMPLETED, ['ARCHIVED'])
    assert.deepEqual(collection.BATCH_TRANSITIONS.ARCHIVED, [])
    assert.equal(collection.canTransitionBatch('REVIEW_REQUIRED', 'COMPLETED'), true)
    assert.equal(collection.canTransitionBatch('COMPLETED', 'REVIEW_REQUIRED'), false)
    assert.equal(collection.isBatchImmutableStatus('COMPLETED'), true)
    assert.equal(collection.isBatchImmutableStatus('ARCHIVED'), true)
    assert.equal(collection.isBatchImmutableStatus('REVIEW_REQUIRED'), false)
    assert.equal(collection.canCreateSnapshot({ status: 'COMPLETED' }), true)
    assert.equal(collection.canCreateSnapshot({ status: 'REVIEW_REQUIRED' }), false)
  })
})

// ========== 端点路径（§8.1 / §8.2 / §8.3） ==========
await group('端点路径与契约一致（§8.1 / §8.2 / §8.3）', async () => {
  const recordSrc = readFileSync(new URL('../experimentRecords.js', import.meta.url), 'utf8')
  const collectionSrc = readFileSync(new URL('../collection.js', import.meta.url), 'utf8')

  await test('记录与修订 13 个端点齐全', async () => {
    const paths = [
      '/api/experiment-records`',
      '/api/experiment-records/${recordId}`',
      '/api/experiment-records/${recordId}/revisions/${revisionId}`',
      '/api/experiment-records/${recordId}/revisions`',
      '/api/experiment-records/${recordId}/submit`',
      '/api/experiment-records/${recordId}/feedback`',
      '/api/experiment-records/${recordId}/publish`',
      '/api/experiment-records/${recordId}/collection-snapshot`',
      '/api/experiment-records/${recordId}/project-draft`'
    ]
    for (const p of paths) assert.ok(recordSrc.includes(p), `缺少端点 ${p}`)
  })

  await test('采集计划/批次 12 个端点齐全', async () => {
    const paths = [
      '/api/projects/${projectId}/collection-plans`',
      '/api/collection-plans/${planId}`',
      '/api/collection-plans/${planId}/ready`',
      '/api/collection-plans/${planId}/archive`',
      '/api/collection-plans/${planId}/cancel`',
      '/api/collection-plans/${planId}/batches`',
      '/api/collection-batches/${batchId}`',
      '/api/collection-batches/${batchId}/records`',
      '/api/collection-batches/${batchId}/complete`',
      '/api/collection-batches/${batchId}/cancel`',
      '/api/collection-batches/${batchId}/archive`',
      '/api/collection-batches/${batchId}/snapshots`'
    ]
    for (const p of paths) assert.ok(collectionSrc.includes(p), `缺少端点 ${p}`)
  })

  await test('文件与人工数据 8 个端点齐全', async () => {
    const paths = [
      '/api/collection-batches/${batchId}/file-imports`',
      '/api/data-imports/${importId}/preview`',
      '/api/data-imports/${importId}/mapping`',
      '/api/data-imports/${importId}/confirm`',
      '/api/data-imports/${importId}`',
      '/api/collection-batches/${batchId}/manual-records`',
      '/api/manual-records/${manualId}`',
      '/api/manual-records/${manualId}/revisions`'
    ]
    for (const p of paths) assert.ok(collectionSrc.includes(p), `缺少端点 ${p}`)
  })
})

// ========== 幂等与乐观锁（§8.4 / §8.5） ==========
await group('幂等键与乐观锁工具（契约 §8.4 / §8.5）', async () => {
  await test('创建/提交/反馈/发布/转项目均发送 Idempotency-Key', async () => {
    const src = readFileSync(new URL('../experimentRecords.js', import.meta.url), 'utf8')
    const count = (src.match(/headers: idempotencyHeaders\(idempotencyKey\)/g) || []).length
    assert.ok(count >= 5, `记录域应至少 5 处发送幂等键，实际 ${count}`)
    for (const fn of [
      'createExperimentRecord',
      'submitExperimentRecord',
      'createFeedback',
      'publishExperimentRecord',
      'convertToProjectDraft'
    ]) {
      assert.ok(src.includes(`export function ${fn}(`), `缺少接口 ${fn}`)
    }
  })

  await test('建批次/确认导入/完成批次/生成快照均发送 Idempotency-Key', async () => {
    const src = readFileSync(new URL('../collection.js', import.meta.url), 'utf8')
    const count = (src.match(/headers: idempotencyHeaders\(idempotencyKey\)/g) || []).length
    assert.ok(count >= 4, `采集域应至少 4 处发送幂等键，实际 ${count}`)
  })

  await test('幂等键格式唯一，请求摘要按 key 排序', async () => {
    const a = idempotency.createIdempotencyKey('w43-test')
    const b = idempotency.createIdempotencyKey('w43-test')
    assert.notEqual(a, b, '连续两次幂等键应不同')
    assert.match(a, /^w43-test-[a-z0-9]+-[a-z0-9]+-[a-z0-9]+$/)
    assert.deepEqual(idempotency.idempotencyHeaders(a), { 'Idempotency-Key': a })
    assert.deepEqual(idempotency.idempotencyHeaders(''), {})
    assert.equal(idempotency.requestDigest({ b: 1, a: 2 }), idempotency.requestDigest({ a: 2, b: 1 }))
    assert.notEqual(idempotency.requestDigest({ a: 1 }), idempotency.requestDigest({ a: 2 }))
  })

  await test('保存/状态动作携带 version 字段（页面静态检查）', async () => {
    const detail = readFileSync(new URL('../../views/experiment/RecordDetail.vue', import.meta.url), 'utf8')
    const editor = readFileSync(new URL('../../views/experiment/RecordEditor.vue', import.meta.url), 'utf8')
    const planDetail = readFileSync(new URL('../../views/collection/CollectionPlanDetail.vue', import.meta.url), 'utf8')
    const batchDetail = readFileSync(new URL('../../views/collection/CollectionBatchDetail.vue', import.meta.url), 'utf8')
    const importDetail = readFileSync(new URL('../../views/collection/DataImportDetail.vue', import.meta.url), 'utf8')
    assert.match(detail, /recordVersion/)
    assert.match(detail, /revisionVersion/)
    assert.match(editor, /recordVersion/)
    assert.match(editor, /revisionVersion/)
    assert.match(planDetail, /planVersion/)
    assert.match(batchDetail, /batchVersion/)
    assert.match(importDetail, /importVersion/)
  })
})

// ========== 权限矩阵（§7.1） ==========
await group('权限矩阵与隐藏语义（契约 §7.1 / §7.2）', async () => {
  await test('成员不可评审自己的记录，导师/管理员可评审', async () => {
    setUser(2, ['MEMBER'])
    const own = { authorId: 2, projectId: 10, status: 'IN_REVIEW' }
    assert.equal(records.canReviewRecord(own), false, '作者不得自我批准')
    assert.equal(records.canEditRecord(own), true)
    setUser(1, ['TEACHER'])
    assert.equal(records.canReviewGlobally(), true)
    assert.equal(records.canReviewRecord(own), true)
    setUser(3, ['MEMBER'])
    assert.equal(records.canReviewRecord({ authorId: 3, projectId: 10 }), false)
    setUser(2, ['MEMBER'])
  })

  await test('采集管理仅 OWNER/MAINTAINER/全局角色，成员只能执行自己负责的批次', async () => {
    setUser(2, ['MEMBER'])
    assert.equal(collection.canManageCollection(10), true, '项目 10 的 OWNER 可管理')
    assert.equal(collection.canManageCollection(5), false, '项目 5 的 MEMBER 不可管理')
    assert.equal(collection.canOperateCollectionBatch({ projectId: 5, ownerId: 2 }), true)
    assert.equal(collection.canOperateCollectionBatch({ projectId: 5, ownerId: 1 }), false)
    setUser(1, ['TEACHER'])
    assert.equal(collection.canManageCollection(5), true)
    setUser(2, ['MEMBER'])
  })

  await test('GUEST 无法访问实验记录与采集（403 ACCESS_DENIED）', async () => {
    setUser(99, [])
    assert.equal(records.canAccessExperiments(), false)
    assert.equal(collection.canAccessCollection(), false)
    await expectReject(() => records.listExperimentRecords({}), 403, 'ACCESS_DENIED')
    await expectReject(() => collection.listCollectionPlans(10, {}), 403, 'ACCESS_DENIED')
    setUser(2, ['MEMBER'])
  })
})

// ========== 记录主流程（§4 / §8.1） ==========
await group('实验记录、修订、反馈与发布主流程（契约 §4 / §8.1）', async () => {
  let recordVersion = 1
  let revisionVersion = 2
  let newRevisionId = null
  let newRevisionVersion = null
  const submitKey = idempotency.createIdempotencyKey('w43-submit')
  const submitPayload = { recordVersion: 2, revisionId: 901, revisionVersion: 3 }

  await test('保存修订：过期版本 409 VERSION_CONFLICT，缺失版本 400', async () => {
    setUser(2, ['MEMBER'])
    await expectReject(
      () => records.saveRevision(301, 901, { recordVersion: 1, revisionVersion: 1, contentJson: { voltage: 5, test_type: '直流' } }),
      409,
      'VERSION_CONFLICT'
    )
    await expectReject(
      () => records.saveRevision(301, 901, { contentJson: { voltage: 5, test_type: '直流' } }),
      400,
      'INVALID_PARAMETER'
    )
  })

  await test('非 EDITABLE 修订不可保存：409 EXPERIMENT_REVISION_IMMUTABLE', async () => {
    await expectReject(
      () => records.saveRevision(302, 902, { recordVersion: 3, revisionVersion: 2, contentJson: {} }),
      409,
      'EXPERIMENT_REVISION_IMMUTABLE'
    )
  })

  await test('内容不符合固定模板版本：400 EXPERIMENT_CONTENT_INVALID 且带字段错误', async () => {
    await assert.rejects(
      () => records.saveRevision(308, 910, {
        recordVersion: 1,
        revisionVersion: 1,
        contentJson: { voltage: 999, test_type: '直流' }
      }),
      (err) => {
        assertErrorShape(err, 400, 'EXPERIMENT_CONTENT_INVALID')
        assert.ok(Array.isArray(err.data?.errors) && err.data.errors.some((e) => e.path === 'voltage'))
        return true
      }
    )
  })

  await test('保存草稿成功：返回新 recordVersion/revisionVersion/status', async () => {
    const res = await records.saveRevision(301, 901, {
      recordVersion: 1,
      revisionVersion: 2,
      contentJson: { voltage: 5.12, test_type: '直流', is_pass: true, remarks: '补充第三次测量' },
      result: '波动小于 1%'
    })
    assert.equal(res.recordVersion, 2)
    assert.equal(res.revisionId, 901)
    assert.equal(res.revisionVersion, 3)
    assert.equal(res.status, 'DRAFT')
    recordVersion = res.recordVersion
    revisionVersion = res.revisionVersion
  })

  await test('提交缺少幂等键：400 INVALID_PARAMETER', async () => {
    await expectReject(
      () => records.submitExperimentRecord(301, submitPayload),
      400,
      'INVALID_PARAMETER'
    )
  })

  await test('提交成功进入 IN_REVIEW，同键同摘要返回 idempotentReplay', async () => {
    const res = await records.submitExperimentRecord(301, submitPayload, submitKey)
    assert.equal(res.status, 'IN_REVIEW')
    assert.equal(res.revisionStatus, 'SUBMITTED')
    assert.equal(res.recordVersion, 3)
    recordVersion = res.recordVersion

    const replay = await records.submitExperimentRecord(301, submitPayload, submitKey)
    assert.equal(replay.idempotentReplay, true)
    assert.equal(replay.status, 'IN_REVIEW')
  })

  await test('同键不同摘要：409 IDEMPOTENCY_KEY_REUSED', async () => {
    await expectReject(
      () => records.submitExperimentRecord(301, { ...submitPayload, recordVersion: 999 }, submitKey),
      409,
      'IDEMPOTENCY_KEY_REUSED'
    )
  })

  await test('作者不能自我评审：403 FEEDBACK_OPERATION_DENIED', async () => {
    await expectReject(
      () => records.createFeedback(301, {
        recordVersion: 3,
        revisionId: 901,
        type: 'APPROVAL',
        content: '自我批准'
      }, idempotency.createIdempotencyKey('w43-feedback-self')),
      403,
      'FEEDBACK_OPERATION_DENIED'
    )
  })

  await test('导师 COMMENT 不改变状态但增加记录版本', async () => {
    setUser(1, ['TEACHER'])
    const res = await records.createFeedback(301, {
      recordVersion: 3,
      revisionId: 901,
      type: 'COMMENT',
      content: '请补充第三次测量数据'
    }, idempotency.createIdempotencyKey('w43-feedback-comment'))
    assert.equal(res.status, 'IN_REVIEW')
    assert.equal(res.revisionStatus, 'SUBMITTED')
    assert.equal(res.recordVersion, 4)
    recordVersion = res.recordVersion
  })

  await test('CHANGE_REQUEST 冻结原修订并自动复制下一 EDITABLE 修订', async () => {
    const res = await records.createFeedback(301, {
      recordVersion: 4,
      revisionId: 901,
      type: 'CHANGE_REQUEST',
      content: '请补充第三次测量并说明负载条件',
      visibility: 'AUTHOR_AND_REVIEWERS'
    }, idempotency.createIdempotencyKey('w43-feedback-change'))
    assert.equal(res.status, 'CHANGES_REQUESTED')
    assert.equal(res.revisionStatus, 'CHANGES_REQUESTED')
    assert.ok(res.feedbackId)
    assert.equal(res.newRevision.status, 'EDITABLE')
    assert.equal(res.newRevision.revisionNo, 2)
    newRevisionId = res.newRevision.id
    newRevisionVersion = res.newRevision.version
    recordVersion = res.recordVersion

    const timeline = await records.listFeedback(301, { pageSize: 100 })
    assert.ok(timeline.total >= 2)
    assert.ok(timeline.list.some((f) => f.type === 'CHANGE_REQUEST'))
  })

  await test('成员修改新修订并重新提交', async () => {
    setUser(2, ['MEMBER'])
    const saved = await records.saveRevision(301, newRevisionId, {
      recordVersion,
      revisionVersion: newRevisionVersion,
      contentJson: { voltage: 5.14, test_type: '直流', is_pass: true, remarks: '补充第三次测量与负载条件' },
      changeSummary: '按导师意见补充第三次测量'
    })
    recordVersion = saved.recordVersion
    newRevisionVersion = saved.revisionVersion

    const res = await records.submitExperimentRecord(301, {
      recordVersion,
      revisionId: newRevisionId,
      revisionVersion: newRevisionVersion
    }, idempotency.createIdempotencyKey('w43-submit-2'))
    assert.equal(res.status, 'IN_REVIEW')
    recordVersion = res.recordVersion
  })

  await test('导师批准后状态为 APPROVED', async () => {
    setUser(1, ['TEACHER'])
    const res = await records.createFeedback(301, {
      recordVersion,
      revisionId: newRevisionId,
      type: 'APPROVAL',
      content: '复审通过',
      visibility: 'AUTHOR_AND_REVIEWERS'
    }, idempotency.createIdempotencyKey('w43-feedback-approve'))
    assert.equal(res.status, 'APPROVED')
    assert.equal(res.revisionStatus, 'APPROVED')
    recordVersion = res.recordVersion
  })

  await test('发布已批准修订：PUBLISHED，重放不重复发布', async () => {
    const publishKey = idempotency.createIdempotencyKey('w43-publish')
    const payload = { recordVersion, revisionId: newRevisionId }
    const res = await records.publishExperimentRecord(301, payload, publishKey)
    assert.equal(res.status, 'PUBLISHED')
    assert.equal(res.revisionStatus, 'PUBLISHED')
    recordVersion = res.recordVersion

    const replay = await records.publishExperimentRecord(301, payload, publishKey)
    assert.equal(replay.idempotentReplay, true)
    assert.equal(replay.status, 'PUBLISHED')

    const detail = await records.getExperimentRecord(301)
    assert.equal(detail.publishedRevision.id, newRevisionId)
    assert.equal(detail.currentRevision.status, 'PUBLISHED')
  })

  await test('从发布版创建新修订进入 REVISING，旧发布版仍可读', async () => {
    setUser(2, ['MEMBER'])
    const res = await records.createRevision(301, { changeSummary: '补充第四次测量' })
    assert.equal(res.status, 'REVISING')
    assert.equal(res.revisionStatus, 'EDITABLE')
    assert.equal(res.revisionNo, 3)
    recordVersion = res.recordVersion
    newRevisionId = res.revisionId
    newRevisionVersion = res.revisionNo && 1

    const detail = await records.getExperimentRecord(301)
    assert.equal(detail.status, 'REVISING')
    assert.equal(detail.publishedRevision.status, 'PUBLISHED')
    assert.equal(detail.currentRevision.status, 'EDITABLE')
  })

  await test('绑定快照成功；重复绑定不同快照 409 SNAPSHOT_ALREADY_BOUND；可显式解除', async () => {
    const bound = await records.bindCollectionSnapshot(301, {
      recordVersion,
      revisionId: newRevisionId,
      revisionVersion: newRevisionVersion,
      snapshotId: 501
    })
    assert.equal(bound.collectionSnapshotId, 501)
    assert.equal(bound.revisionVersion, newRevisionVersion + 1)
    recordVersion = bound.recordVersion
    newRevisionVersion = bound.revisionVersion

    await expectReject(
      () => records.bindCollectionSnapshot(301, {
        recordVersion,
        revisionId: newRevisionId,
        revisionVersion: newRevisionVersion,
        snapshotId: 502
      }),
      409,
      'SNAPSHOT_ALREADY_BOUND'
    )

    const unbound = await records.bindCollectionSnapshot(301, {
      recordVersion,
      revisionId: newRevisionId,
      revisionVersion: newRevisionVersion,
      snapshotId: null
    })
    assert.equal(unbound.collectionSnapshotId, null)
    recordVersion = unbound.recordVersion
    newRevisionVersion = unbound.revisionVersion
  })
})

// ========== 项目转换（D6 / §8.1） ==========
await group('发布记录转 PREPARING 项目草案（契约 D6 / §8.1）', async () => {
  await test('非 PUBLISHED 记录不可转项目：409 EXPERIMENT_INVALID_TRANSITION', async () => {
    setUser(2, ['MEMBER'])
    await expectReject(
      () => records.convertToProjectDraft(301, {
        recordVersion: 15,
        code: 'PROJ-W43-X',
        name: '不应创建的项目'
      }, idempotency.createIdempotencyKey('w43-convert-invalid')),
      409,
      'EXPERIMENT_INVALID_TRANSITION'
    )
  })

  await test('非作者且非管理者：403 PROJECT_CONVERSION_DENIED', async () => {
    setUser(3, ['MEMBER'])
    await expectReject(
      () => records.convertToProjectDraft(305, {
        recordVersion: 7,
        code: 'PROJ-W43-X',
        name: '不应创建的项目'
      }, idempotency.createIdempotencyKey('w43-convert-denied')),
      403,
      'PROJECT_CONVERSION_DENIED'
    )
    setUser(2, ['MEMBER'])
  })

  await test('发布记录转换成功，返回 PREPARING 与来源关系，重放幂等', async () => {
    const key = idempotency.createIdempotencyKey('w43-convert')
    const payload = {
      recordVersion: 7,
      code: 'PROJ-W43-01',
      name: 'PM-001 功率读数校准复现',
      summary: '来源实验记录 305 的发布结论',
      category: 'hardware_project'
    }
    const project = await records.convertToProjectDraft(305, payload, key)
    assert.equal(project.status, 'PREPARING')
    assert.equal(project.sourceExperimentRecordId, 305)
    assert.equal(project.sourcePublishedRevisionId, 906)

    const replay = await records.convertToProjectDraft(305, payload, key)
    assert.equal(replay.idempotentReplay, true)
    assert.equal(replay.id, project.id)
  })

  await test('同一记录重复转换（新键）：409 PROJECT_ALREADY_CONVERTED', async () => {
    await expectReject(
      () => records.convertToProjectDraft(305, {
        recordVersion: 8,
        code: 'PROJ-W43-02',
        name: '重复转换'
      }, idempotency.createIdempotencyKey('w43-convert-again')),
      409,
      'PROJECT_ALREADY_CONVERTED'
    )
    await expectReject(
      () => records.convertToProjectDraft(306, {
        recordVersion: 9,
        code: 'PROJ-W43-03',
        name: '已转换记录'
      }, idempotency.createIdempotencyKey('w43-convert-existing')),
      409,
      'PROJECT_ALREADY_CONVERTED'
    )
  })
})

// ========== 采集主流程（§5 / §8.2 / §8.3） ==========
await group('采集计划、批次、CSV 导入与快照主流程（契约 §5 / §8.2 / §8.3）', async () => {
  let planId = null
  let planVersion = 1
  let batchId = null
  let batchVersion = 1
  let importId = null
  let importVersion = 1

  await test('MEMBER 无权管理计划：403 COLLECTION_OPERATION_DENIED', async () => {
    setUser(2, ['MEMBER'])
    await expectReject(
      () => collection.createCollectionPlan(5, {
        name: '不应创建的计划',
        purpose: '无权限',
        ownerId: 2,
        sourceType: 'FILE',
        metricDefinitions: [{ code: 'voltage', name: '电压', valueType: 'DECIMAL', unit: 'V', required: true }]
      }),
      403,
      'COLLECTION_OPERATION_DENIED'
    )
  })

  await test('指标定义非法：400 INVALID_PARAMETER', async () => {
    await expectReject(
      () => collection.createCollectionPlan(10, {
        name: '非法指标计划',
        purpose: '校验指标编码',
        ownerId: 2,
        sourceType: 'FILE',
        metricDefinitions: [{ code: 'Voltage-1', name: '电压', valueType: 'DECIMAL', unit: 'V' }]
      }),
      400,
      'INVALID_PARAMETER'
    )
  })

  await test('创建计划 → 置 READY，状态与版本递增', async () => {
    const plan = await collection.createCollectionPlan(10, {
      name: 'W43 电压 CSV 采集',
      purpose: '记录负载变化下的输出电压',
      ownerId: 2,
      sourceType: 'FILE',
      metricDefinitions: [{ code: 'voltage', name: '电压', valueType: 'DECIMAL', unit: 'V', required: true }],
      linkedExperimentRecordId: 301,
      retentionNote: '随实验长期保留'
    })
    planId = plan.id
    planVersion = plan.version
    assert.equal(plan.status, 'DRAFT')
    assert.equal(planVersion, 1)

    const ready = await collection.readyCollectionPlan(planId, { version: planVersion })
    assert.equal(ready.status, 'READY')
    planVersion = ready.version

    await expectReject(
      () => collection.readyCollectionPlan(planId, { version: planVersion }),
      400,
      'INVALID_PARAMETER'
    )
  })

  await test('创建批次：缺少幂等键 400，成功返回 CREATED', async () => {
    await expectReject(
      () => collection.createCollectionBatch(planId, { planVersion, sourceType: 'FILE' }),
      400,
      'INVALID_PARAMETER'
    )
    const batch = await collection.createCollectionBatch(
      planId,
      { planVersion, sourceType: 'FILE' },
      idempotency.createIdempotencyKey('w43-batch-create')
    )
    batchId = batch.id
    batchVersion = batch.version
    assert.equal(batch.status, 'CREATED')
    assert.equal(batch.sourceType, 'FILE')
    assert.ok(batch.batchCode)
  })

  await test('CSV 类型/编码/大小限制：400 FILE_* 错误', async () => {
    await expectReject(
      () => collection.uploadFileImport(batchId, { name: 'data.txt', size: 1024 }),
      400,
      'FILE_TYPE_UNSUPPORTED'
    )
    await expectReject(
      () => collection.uploadFileImport(batchId, { name: 'gbk-data.csv', size: 1024 }),
      400,
      'FILE_ENCODING_UNSUPPORTED'
    )
    await expectReject(
      () => collection.uploadFileImport(batchId, { name: 'big.csv', size: 21 * 1024 * 1024 }),
      400,
      'FILE_TOO_LARGE'
    )
  })

  await test('上传原件返回 SHA-256 与 importVersion；同文件重传复用原任务', async () => {
    const file = { name: 'w43-voltage.csv', size: 2048, lastModified: 1792663970000 }
    const uploaded = await collection.uploadFileImport(batchId, file)
    importId = uploaded.id
    importVersion = uploaded.importVersion
    batchVersion = uploaded.batchVersion
    assert.match(uploaded.originalFile.sha256, /^[0-9a-f]{64}$/)
    assert.equal(uploaded.status, 'UPLOADED')

    const again = await collection.uploadFileImport(batchId, file)
    assert.equal(again.id, importId)
    assert.equal(again.reusedExisting, true)
  })

  await test('前 50 行预览可用；未保存映射前确认 400 IMPORT_MAPPING_INVALID', async () => {
    const preview = await collection.getImportPreview(importId)
    assert.ok(Array.isArray(preview.headers) && preview.headers.length > 0)
    assert.ok(preview.rows.length <= 50)
    assert.equal(preview.importVersion, importVersion)

    await expectReject(
      () => collection.confirmImport(importId, { version: 1, importVersion, batchVersion }, idempotency.createIdempotencyKey('w43-confirm-too-early')),
      400,
      'IMPORT_MAPPING_INVALID'
    )
  })

  await test('映射非法（未知指标）：400 IMPORT_MAPPING_INVALID', async () => {
    await expectReject(
      () => collection.saveImportMapping(importId, {
        version: 1,
        importVersion,
        mapping: {
          delimiter: ',',
          encoding: 'UTF-8',
          headerRow: 1,
          timeMapping: { sourceColumn: 'observed_at', format: 'yyyy-MM-dd HH:mm:ss', timezone: 'Asia/Shanghai' },
          columns: [{ sourceColumn: 'voltage', metricCode: 'unknown_metric', valueType: 'DECIMAL', sourceUnit: 'V', targetUnit: 'V' }]
        }
      }),
      400,
      'IMPORT_MAPPING_INVALID'
    )
  })

  await test('保存映射增加 importVersion；确认导入幂等且写入记录摘要', async () => {
    const saved = await collection.saveImportMapping(importId, {
      version: 1,
      importVersion,
      mapping: {
        delimiter: ',',
        encoding: 'UTF-8',
        headerRow: 1,
        timeMapping: { sourceColumn: 'observed_at', format: 'yyyy-MM-dd HH:mm:ss', timezone: 'Asia/Shanghai' },
        columns: [{ sourceColumn: 'voltage', metricCode: 'voltage', valueType: 'DECIMAL', sourceUnit: 'V', targetUnit: 'V' }],
        ignoredColumns: ['current', 'note']
      }
    })
    assert.equal(saved.status, 'MAPPING_REQUIRED')
    assert.equal(saved.importVersion, importVersion + 1)
    importVersion = saved.importVersion

    const confirmKey = idempotency.createIdempotencyKey('w43-confirm')
    const payload = { version: saved.version, importVersion, batchVersion: saved.batchVersion }
    const confirmed = await collection.confirmImport(importId, payload, confirmKey)
    assert.equal(confirmed.status, 'CONFIRMED')
    assert.ok(confirmed.recordCount > 0)
    assert.equal(confirmed.rejectedCount, 2)
    batchVersion = confirmed.batchVersion

    const replay = await collection.confirmImport(importId, payload, confirmKey)
    assert.equal(replay.idempotentReplay, true)
    assert.equal(replay.recordCount, confirmed.recordCount)
  })

  await test('完成批次：版本冲突 409，成功 COMPLETED 并冻结 checksum', async () => {
    await expectReject(
      () => collection.completeCollectionBatch(
        batchId,
        { version: batchVersion - 1, completionReason: '过期版本' },
        idempotency.createIdempotencyKey('w43-complete-stale')
      ),
      409,
      'VERSION_CONFLICT'
    )

    const completeKey = idempotency.createIdempotencyKey('w43-complete')
    const payload = { version: batchVersion, completionReason: 'CSV 导入与映射已人工确认' }
    const completed = await collection.completeCollectionBatch(batchId, payload, completeKey)
    assert.equal(completed.status, 'COMPLETED')
    assert.match(completed.checksum, /^[0-9a-f]{64}$/)
    batchVersion = completed.version

    const replay = await collection.completeCollectionBatch(batchId, payload, completeKey)
    assert.equal(replay.idempotentReplay, true)
    assert.equal(replay.status, 'COMPLETED')
  })

  await test('完成批次不可变：再次上传/保存映射均 409 BATCH_IMMUTABLE', async () => {
    await expectReject(
      () => collection.uploadFileImport(batchId, { name: 'again.csv', size: 1024 }),
      409,
      'BATCH_IMMUTABLE'
    )
    await expectReject(
      () => collection.saveImportMapping(importId, { version: 3, importVersion: 2, mapping: {} }),
      409,
      'BATCH_IMMUTABLE'
    )
    await expectReject(
      () => collection.saveImportMapping(701, { version: 3, importVersion: 2, mapping: {} }),
      409,
      'BATCH_IMMUTABLE'
    )
  })

  await test('未完成批次不能生成快照：409 BATCH_NOT_COMPLETED', async () => {
    await expectReject(
      () => collection.createCollectionSnapshot(
        602,
        { batchVersion: 3, snapshotType: 'FULL_BATCH' },
        idempotency.createIdempotencyKey('w43-snapshot-early')
      ),
      409,
      'BATCH_NOT_COMPLETED'
    )
  })

  await test('完成批次生成 FULL_BATCH 快照，返回完整快照字段', async () => {
    const snapshot = await collection.createCollectionSnapshot(
      batchId,
      { batchVersion, snapshotType: 'FULL_BATCH' },
      idempotency.createIdempotencyKey('w43-snapshot')
    )
    for (const key of [
      'id', 'snapshotCode', 'batchId', 'snapshotType', 'sourceRevisionId',
      'recordCount', 'rejectedCount', 'observedFrom', 'observedTo',
      'batchChecksum', 'mappingDigest', 'createdBy', 'createTime'
    ]) {
      assert.ok(snapshot[key] !== undefined, `快照缺少字段 ${key}`)
    }
    assert.equal(snapshot.snapshotType, 'FULL_BATCH')
    assert.match(snapshot.batchChecksum, /^[0-9a-f]{64}$/)
  })
})

// ========== 人工观测（§6） ==========
await group('人工观测新增、更正与审计（契约 §6）', async () => {
  let manualBatchId = null
  let manualBatchVersion = 1
  let manualId = null

  await test('创建 MANUAL 计划与批次', async () => {
    setUser(2, ['MEMBER'])
    const plan = await collection.createCollectionPlan(10, {
      name: 'W43 人工温湿度记录',
      purpose: '现场逐条录入温湿度并保留方法与仪器',
      ownerId: 2,
      sourceType: 'MANUAL',
      metricDefinitions: [{ code: 'temperature', name: '温度', valueType: 'DECIMAL', unit: '℃', required: true }]
    })
    const ready = await collection.readyCollectionPlan(plan.id, { version: plan.version })
    const batch = await collection.createCollectionBatch(
      plan.id,
      { planVersion: ready.version, sourceType: 'MANUAL' },
      idempotency.createIdempotencyKey('w43-manual-batch')
    )
    manualBatchId = batch.id
    manualBatchVersion = batch.version
    assert.equal(batch.sourceType, 'MANUAL')
  })

  await test('人工观测必填方法/仪器/单位/观测时间：缺失 400 MANUAL_RECORD_INVALID', async () => {
    await expectReject(
      () => collection.createManualRecord(manualBatchId, {
        metricCode: 'temperature',
        valueType: 'DECIMAL',
        value: 24.6,
        unit: '℃'
      }),
      400,
      'MANUAL_RECORD_INVALID'
    )
  })

  await test('新增人工观测返回 revisionNo 1，批次进入 REVIEW_REQUIRED', async () => {
    const res = await collection.createManualRecord(manualBatchId, {
      metricCode: 'temperature',
      valueType: 'DECIMAL',
      value: 24.6,
      unit: '℃',
      observedAt: '2026-10-22T14:30:00+08:00',
      method: '万用表温度探头测量',
      instrument: 'UNI-T UT61E+ / LAB-A-017',
      note: '室温 24℃'
    })
    manualId = res.id
    manualBatchVersion = res.batchVersion
    assert.equal(res.revisionNo, 1)
    assert.equal(res.version, 1)

    const batch = await collection.getCollectionBatch(manualBatchId)
    assert.equal(batch.status, 'REVIEW_REQUIRED')
  })

  await test('更正必须填写 changeReason；更正追加修订并保留旧值', async () => {
    await expectReject(
      () => collection.updateManualRecord(manualId, { version: 1, value: 24.8 }),
      400,
      'MANUAL_RECORD_INVALID'
    )

    const corrected = await collection.updateManualRecord(manualId, {
      version: 1,
      value: 24.8,
      changeReason: '原读数受空调出风口影响'
    })
    assert.equal(corrected.version, 2)
    assert.equal(corrected.revisionNo, 2)
    assert.ok(corrected.previousRevisionId)
    manualBatchVersion = corrected.batchVersion

    const audit = await collection.listManualRevisions(manualId)
    assert.equal(audit.total, 2)
    assert.equal(audit.list[0].newValue, 24.6)
    assert.equal(audit.list[1].oldValue, 24.6)
    assert.equal(audit.list[1].newValue, 24.8)
    assert.equal(audit.list[1].changeReason, '原读数受空调出风口影响')
    assert.ok(audit.list[1].operatorName)
    assert.ok(audit.list[1].createTime)
  })

  await test('完成人工批次后新增/更正均 409 BATCH_IMMUTABLE', async () => {
    const completed = await collection.completeCollectionBatch(
      manualBatchId,
      { version: manualBatchVersion, completionReason: '人工观测已复核' },
      idempotency.createIdempotencyKey('w43-manual-complete')
    )
    assert.equal(completed.status, 'COMPLETED')

    await expectReject(
      () => collection.createManualRecord(manualBatchId, {
        metricCode: 'temperature',
        valueType: 'DECIMAL',
        value: 25,
        unit: '℃',
        observedAt: '2026-10-22T15:00:00+08:00',
        method: '补录',
        instrument: 'LAB-A-017'
      }),
      409,
      'BATCH_IMMUTABLE'
    )
    await expectReject(
      () => collection.updateManualRecord(manualId, { version: 2, value: 25, changeReason: '补录' }),
      409,
      'BATCH_IMMUTABLE'
    )
  })
})

// ========== UI 状态注入（§11.3） ==========
await group('UI 状态：empty / error / permission / conflict / immutable（契约 §11.3）', async () => {
  await test('mockScenario 仅接受约定状态', async () => {
    assert.deepEqual(
      Object.keys(mockScenario.MOCK_STATE_META).sort(),
      ['conflict', 'empty', 'error', 'immutable', 'loading', 'permission'].sort()
    )
    for (const s of Object.keys(mockScenario.MOCK_STATE_META)) {
      assert.equal(typeof mockScenario.MOCK_STATE_META[s].label, 'string')
      assert.equal(typeof mockScenario.MOCK_STATE_META[s].desc, 'string')
    }
  })

  await test('empty：记录/反馈/计划列表返回空分页', async () => {
    globalThis.window = { location: { search: '?mockState=empty' } }
    assert.equal(mockScenario.currentMockScenario(), 'empty')
    const list = await records.listExperimentRecords({})
    assert.deepEqual(list, { total: 0, page: 1, pageSize: 20, list: [] })
    const feedback = await records.listFeedback(301, {})
    assert.equal(feedback.total, 0)
    const plans = await collection.listCollectionPlans(10, {})
    assert.equal(plans.total, 0)
    delete globalThis.window
  })

  await test('error：列表/详情返回 500 且带 traceId', async () => {
    globalThis.window = { location: { search: '?mockState=error' } }
    await assert.rejects(
      () => records.listExperimentRecords({}),
      (err) => {
        assertErrorShape(err, 500, 'INTERNAL_ERROR')
        assert.match(err.data.traceId, /^mock-/)
        return true
      }
    )
    await assert.rejects(
      () => collection.getCollectionBatch(601),
      (err) => {
        assertErrorShape(err, 500, 'INTERNAL_ERROR')
        assert.match(err.data.traceId, /^mock-/)
        return true
      }
    )
    delete globalThis.window
  })

  await test('permission：详情 404 隐藏资源，列表 403 说明原因', async () => {
    globalThis.window = { location: { search: '?mockState=permission' } }
    await expectReject(() => records.getExperimentRecord(301), 404, 'EXPERIMENT_RECORD_NOT_FOUND')
    await expectReject(() => collection.getDataImport(701), 404, 'DATA_IMPORT_NOT_FOUND')
    await expectReject(() => collection.listCollectionPlans(10, {}), 403, 'COLLECTION_OPERATION_DENIED')
    delete globalThis.window
  })

  await test('conflict：保存与完成批次返回 409 VERSION_CONFLICT', async () => {
    globalThis.window = { location: { search: '?mockState=conflict' } }
    await expectReject(
      () => records.saveRevision(308, 910, { recordVersion: 1, revisionVersion: 1, contentJson: {} }),
      409,
      'VERSION_CONFLICT'
    )
    await expectReject(
      () => collection.completeCollectionBatch(602, { version: 3 }, idempotency.createIdempotencyKey('w43-conflict-batch')),
      409,
      'VERSION_CONFLICT'
    )
    delete globalThis.window
  })

  await test('immutable：非 EDITABLE 修订与完成批次返回只读错误', async () => {
    globalThis.window = { location: { search: '?mockState=immutable' } }
    await expectReject(
      () => records.saveRevision(308, 910, { recordVersion: 1, revisionVersion: 1, contentJson: {} }),
      409,
      'EXPERIMENT_REVISION_IMMUTABLE'
    )
    await expectReject(
      () => collection.completeCollectionBatch(602, { version: 3 }, idempotency.createIdempotencyKey('w43-immutable-batch')),
      409,
      'BATCH_IMMUTABLE'
    )
    delete globalThis.window
  })
})

// ========== 错误码归一化（§10） ==========
await group('错误码归一化（契约 §10）', async () => {
  const err = (reason, data = null) => ({ code: 409, msg: 'x', reason, data })

  await test('404/403 资源与权限判定', async () => {
    assert.equal(apiError.isExperimentNotFound({ code: 404, reason: 'EXPERIMENT_RECORD_NOT_FOUND' }), true)
    assert.equal(apiError.isCollectionNotFound(err('COLLECTION_BATCH_NOT_FOUND')), true)
    assert.equal(apiError.isExperimentDenied({ code: 403, reason: 'EXPERIMENT_OPERATION_DENIED' }), true)
    assert.equal(apiError.isFeedbackDenied(err('FEEDBACK_OPERATION_DENIED')), true)
    assert.equal(apiError.isCollectionDenied(err('COLLECTION_OPERATION_DENIED')), true)
    assert.equal(apiError.isProjectConversionDenied(err('PROJECT_CONVERSION_DENIED')), true)
  })

  await test('409 冲突类 reason 全覆盖', async () => {
    for (const [fn, reason] of [
      ['isExperimentInvalidTransition', 'EXPERIMENT_INVALID_TRANSITION'],
      ['isRevisionImmutable', 'EXPERIMENT_REVISION_IMMUTABLE'],
      ['isProjectAlreadyConverted', 'PROJECT_ALREADY_CONVERTED'],
      ['isBatchInvalidTransition', 'BATCH_INVALID_TRANSITION'],
      ['isBatchImmutable', 'BATCH_IMMUTABLE'],
      ['isBatchNotCompleted', 'BATCH_NOT_COMPLETED'],
      ['isSnapshotAlreadyBound', 'SNAPSHOT_ALREADY_BOUND'],
      ['isImportAlreadyConfirmed', 'IMPORT_ALREADY_CONFIRMED'],
      ['isIdempotencyKeyReused', 'IDEMPOTENCY_KEY_REUSED'],
      ['isTemplateVersionMismatch', 'TEMPLATE_VERSION_MISMATCH']
    ]) {
      assert.equal(typeof apiError[fn], 'function', `缺少 ${fn}`)
      assert.equal(apiError[fn](err(reason)), true, `${fn} 应识别 ${reason}`)
    }
  })

  await test('400 内容/文件/映射/人工记录 reason', async () => {
    assert.equal(apiError.isExperimentContentInvalid(err('EXPERIMENT_CONTENT_INVALID')), true)
    for (const reason of ['FILE_TYPE_UNSUPPORTED', 'FILE_TOO_LARGE', 'FILE_ENCODING_UNSUPPORTED']) {
      assert.equal(apiError.isFileUnsupported({ code: 400, reason }), true)
    }
    assert.equal(apiError.isImportMappingInvalid({ code: 400, reason: 'IMPORT_MAPPING_INVALID' }), true)
    assert.equal(apiError.isManualRecordInvalid({ code: 400, reason: 'MANUAL_RECORD_INVALID' }), true)
  })

  await test('traceId 与字段级内容错误提取', async () => {
    const conflict = { code: 409, reason: 'VERSION_CONFLICT', data: { currentVersion: 5, traceId: 'trace-1' } }
    assert.equal(apiError.errorTraceId(conflict), 'trace-1')
    assert.equal(apiError.errorTraceId({ code: 500 }), '')
    const contentErr = err('EXPERIMENT_CONTENT_INVALID', { errors: [{ path: 'voltage', message: '超出范围' }] })
    assert.equal(apiError.experimentContentErrors(contentErr).length, 1)
    assert.deepEqual(apiError.experimentContentErrors(err('OTHER')), [])
  })
})

// ========== 页面、路由与侧栏静态检查（§11.1 / §11.2） ==========
await group('页面、路由与侧栏接入（契约 §11.1）', async () => {
  const readView = (p) => readFileSync(new URL(p, import.meta.url), 'utf8')
  const routerSrc = readView('../../router/index.js')
  const layoutSrc = readView('../../layout/Index.vue')
  const projectDetailSrc = readView('../../views/projects/ProjectDetail.vue')

  await test('8 个 W43 路由均已注册', async () => {
    for (const p of [
      'experiment/records',
      'experiment/records/new',
      'experiment/records/:id/edit/:revisionId',
      'experiment/records/:id',
      'projects/:id/collection',
      'collection/plans/:id',
      'collection/batches/:id',
      'collection/imports/:id'
    ]) {
      assert.ok(routerSrc.includes(`'${p}'`), `缺少路由 ${p}`)
    }
  })

  await test('侧栏新增实验记录入口，项目详情新增数据采集 tab', async () => {
    assert.match(layoutSrc, /\/experiment\/records/)
    assert.match(layoutSrc, /实验记录/)
    assert.match(projectDetailSrc, /CollectionPlanList/)
    assert.match(projectDetailSrc, /name="collection"/)
    assert.match(projectDetailSrc, /数据采集/)
  })

  await test('记录列表/详情/编辑覆盖状态、版本与幂等动作', async () => {
    const list = readView('../../views/experiment/RecordList.vue')
    const detail = readView('../../views/experiment/RecordDetail.vue')
    const editor = readView('../../views/experiment/RecordEditor.vue')
    for (const [name, src] of [['RecordList', list], ['RecordDetail', detail], ['RecordEditor', editor]]) {
      assert.match(src, /RegionState/, `${name} 应使用 RegionState`)
    }
    assert.match(detail, /MockStateBanner/)
    assert.match(detail, /createRevision/)
    assert.match(detail, /bindCollectionSnapshot/)
    assert.match(detail, /convertToProjectDraft/)
    assert.match(detail, /FEEDBACK_TYPE\.CHANGE_REQUEST/)
    assert.match(detail, /FEEDBACK_TYPE\.APPROVAL/)
    assert.match(detail, /isRevisionImmutable/)
    assert.match(detail, /创建新修订/)
    assert.match(detail, /只读/)
    assert.match(editor, /createExperimentRecord/)
    assert.match(editor, /createIdempotencyKey/)
    assert.match(editor, /saveRevision/)
    assert.match(editor, /SchemaFieldForm/)
  })

  await test('采集页面覆盖计划/批次/导入/人工/快照动作与状态', async () => {
    const planList = readView('../../views/collection/CollectionPlanList.vue')
    const planDetail = readView('../../views/collection/CollectionPlanDetail.vue')
    const batchDetail = readView('../../views/collection/CollectionBatchDetail.vue')
    const importDetail = readView('../../views/collection/DataImportDetail.vue')

    for (const [name, src] of [
      ['CollectionPlanList', planList],
      ['CollectionPlanDetail', planDetail],
      ['CollectionBatchDetail', batchDetail],
      ['DataImportDetail', importDetail]
    ]) {
      assert.match(src, /RegionState/, `${name} 应使用 RegionState`)
      assert.match(src, /MockStateBanner/, `${name} 应展示 mock 状态横幅`)
    }

    assert.match(planDetail, /readyCollectionPlan/)
    assert.match(planDetail, /archiveCollectionPlan/)
    assert.match(planDetail, /cancelCollectionPlan/)
    assert.match(planDetail, /createCollectionBatch/)
    assert.match(planDetail, /planVersion/)

    assert.match(batchDetail, /uploadFileImport/)
    assert.match(batchDetail, /completeCollectionBatch/)
    assert.match(batchDetail, /createCollectionSnapshot/)
    assert.match(batchDetail, /cancelCollectionBatch/)
    assert.match(batchDetail, /archiveCollectionBatch/)
    assert.match(batchDetail, /batchVersion/)
    assert.match(batchDetail, /ManualRecordDialog/)

    assert.match(importDetail, /getImportPreview/)
    assert.match(importDetail, /saveImportMapping/)
    assert.match(importDetail, /confirmImport/)
    assert.match(importDetail, /importVersion/)
    assert.match(importDetail, /el-steps/)
    assert.match(importDetail, /最多 50 行/)
  })

  await test('视图不直接拼装 EventSource/axios，复用 API 层与统一错误处理', async () => {
    for (const p of [
      '../../views/experiment/RecordList.vue',
      '../../views/experiment/RecordDetail.vue',
      '../../views/experiment/RecordEditor.vue',
      '../../views/collection/CollectionPlanList.vue',
      '../../views/collection/CollectionPlanDetail.vue',
      '../../views/collection/CollectionBatchDetail.vue',
      '../../views/collection/DataImportDetail.vue'
    ]) {
      const src = readView(p)
      assert.doesNotMatch(src, /from '@\/utils\/request'/, `${p} 不应直接使用 request`)
      assert.doesNotMatch(src, /EventSource/, `${p} 不应直接创建 EventSource`)
    }
  })
})

// ========== summary ==========
cleanup()
process.stdout.write(`\n==========\n`)
process.stdout.write(`passed: ${pass}\n`)
process.stdout.write(`failed: ${fail}\n`)
if (fail > 0) {
  process.stdout.write(`\nfailures:\n`)
  for (const f of failures) process.stdout.write(`- ${f.name}: ${f.message}\n`)
  process.exit(1)
}
process.stdout.write('all tests passed\n')
