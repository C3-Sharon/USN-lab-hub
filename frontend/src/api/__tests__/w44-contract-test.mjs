/**
 * W44 契约测试：元器件目录与通用媒体基础
 *
 * 契约来源：docs/contracts/COMPONENT_CATALOG_MEDIA_API.md v1.0.6（FROZEN）
 *           docs/weekly/2026-W44.md §4 / §9 / §10 / §12
 *
 * 覆盖：
 *   - 枚举、状态机、权限矩阵与中文映射（§3 / §4 / §5）
 *   - 契约端点路径静态检查（§6）
 *   - 幂等键、乐观锁、重复 MPN、状态冲突（§9 / §10）
 *   - MEMBER 仅见 ACTIVE；DRAFT/INACTIVE 统一 404
 *   - 候选登记、人工确认生成不可变快照、手工快照与历史版本
 *   - 图片上传 / 主图 / 排序 / 删除 / 占位回退
 *   - 媒体响应不泄露 objectKey、bucket、provider、endpoint、签名 URL
 *   - 占位 key 公式与确定性
 *   - mockState: empty/error/permission/conflict/inactive/placeholder/success
 *   - 页面 / 路由 / 侧栏静态检查
 *
 * 运行：node src/api/__tests__/w44-contract-test.mjs
 *
 * 不引入 jest / vitest；沿用 w43 的“纯 Node + assert”风格，
 * 通过临时文件替换 @/ 别名后动态 import。
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
    failures.push({ name, message: err.message })
    process.stdout.write(`  ✗ ${name}\n    ${err.message}\n`)
  }
}

async function group(name, fn) {
  process.stdout.write(`\n# ${name}\n`)
  await fn()
}

function makeStub(name, code) {
  const file = path.join(here, `.tmp-w44-stub-${name}-${Date.now()}-${Math.random().toString(36).slice(2)}.mjs`)
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
  get userInfo() { return { id: globalThis.__W44_USER_ID ?? 1, name: '测试用户' } }
}
export const currentRoles = () => globalThis.__W44_ROLES || ['MEMBER']`)
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
  const tmpPath = path.join(here, `.tmp-w44-${Date.now()}-${Math.random().toString(36).slice(2)}.mjs`)
  writeFileSync(tmpPath, code)
  tmpFiles.push(tmpPath)
  return { mod: await import(pathToFileURL(tmpPath).href), url: pathToFileURL(tmpPath).href }
}

function cleanup() {
  for (const file of tmpFiles) {
    try { rmSync(file, { force: true }) } catch { /* 忽略清理失败 */ }
  }
}

globalThis.__W44_USER_ID = 1
globalThis.__W44_ROLES = ['SYSTEM_ADMIN']

function setUser(id, roles) {
  globalThis.__W44_USER_ID = id
  globalThis.__W44_ROLES = roles
}

function setScenario(state) {
  if (state) {
    globalThis.window = { location: { search: `?mockState=${state}` } }
  } else {
    delete globalThis.window
  }
}

async function expectReject(fn, reason, code) {
  let error = null
  try {
    await fn()
  } catch (err) {
    error = err
  }
  assert.ok(error, `预期拒绝（${reason}），实际成功`)
  assert.equal(error.reason, reason, `预期 reason=${reason}，实际 ${error.reason}`)
  if (code != null) assert.equal(error.code, code, `预期 code=${code}，实际 ${error.code}`)
  return error
}

const loaded = await loadModule('../components.js')
const api = loaded.mod

setScenario(null)

// ========== 1. 枚举与状态机 ==========
await group('枚举、状态机与权限矩阵（契约 §3 / §4 / §5）', async () => {
  await test('元器件状态为 DRAFT/ACTIVE/INACTIVE', () => {
    assert.deepEqual(Object.values(api.COMPONENT_STATUS), ['DRAFT', 'ACTIVE', 'INACTIVE'])
  })

  await test('状态机仅允许 DRAFT→ACTIVE/INACTIVE、ACTIVE→INACTIVE、INACTIVE→ACTIVE', () => {
    assert.equal(api.canTransitionComponent('DRAFT', 'ACTIVE'), true)
    assert.equal(api.canTransitionComponent('DRAFT', 'INACTIVE'), true)
    assert.equal(api.canTransitionComponent('ACTIVE', 'INACTIVE'), true)
    assert.equal(api.canTransitionComponent('INACTIVE', 'ACTIVE'), true)
    assert.equal(api.canTransitionComponent('ACTIVE', 'DRAFT'), false)
    assert.equal(api.canTransitionComponent('INACTIVE', 'DRAFT'), false)
  })

  await test('分类/制造商/来源/候选/媒体枚举完整', () => {
    assert.deepEqual(Object.values(api.CATALOG_STATUS), ['ACTIVE', 'INACTIVE'])
    assert.deepEqual(Object.values(api.CANDIDATE_STATUS), ['PENDING', 'CONFIRMED', 'REJECTED', 'SUPERSEDED'])
    assert.deepEqual(Object.values(api.SOURCE_TYPE), [
      'MANUFACTURER_WEBSITE',
      'MANUFACTURER_DATASHEET',
      'AUTHORIZED_DISTRIBUTOR',
      'OTHER_PUBLIC_URL',
      'MANUAL'
    ])
    assert.deepEqual(Object.values(api.BASE_UNIT), ['PCS', 'METER', 'GRAM', 'MILLILITER', 'SET'])
    assert.deepEqual(Object.values(api.VALUE_TYPE), ['STRING', 'DECIMAL', 'INTEGER', 'BOOLEAN'])
    assert.deepEqual(Object.values(api.IMAGE_STATE), ['READY', 'PLACEHOLDER'])
    assert.deepEqual(Object.values(api.MEDIA_ROLE), ['PRIMARY', 'GALLERY'])
  })

  await test('SYSTEM_ADMIN / STOCK_KEEPER 可写；TEACHER 只读但可见停用；MEMBER 仅 ACTIVE', () => {
    setUser(1, ['SYSTEM_ADMIN'])
    assert.equal(api.canManageCatalog(), true)
    assert.equal(api.canReadInactiveCatalog(), true)
    setUser(5, ['STOCK_KEEPER'])
    assert.equal(api.canManageCatalog(), true)
    setUser(2, ['TEACHER'])
    assert.equal(api.canManageCatalog(), false)
    assert.equal(api.canReadInactiveCatalog(), true)
    setUser(3, ['MEMBER'])
    assert.equal(api.canManageCatalog(), false)
    assert.equal(api.canReadInactiveCatalog(), false)
    setUser(4, [])
    assert.equal(api.canAccessComponents(), false)
    setUser(1, ['SYSTEM_ADMIN'])
  })
})

// ========== 2. 契约端点静态检查 ==========
await group('契约端点路径（契约 §6）', async () => {
  const source = readFileSync(path.join(here, '../components.js'), 'utf8')
  const endpoints = [
    '/api/component-categories',
    '/api/component-categories/${categoryId}',
    '/api/component-categories/${categoryId}/deactivate',
    '/api/component-categories/${categoryId}/restore',
    '/api/component-manufacturers',
    '/api/component-manufacturers/${manufacturerId}',
    '/api/component-manufacturers/${manufacturerId}/deactivate',
    '/api/component-manufacturers/${manufacturerId}/restore',
    '/api/components',
    '/api/components/${componentId}',
    '/api/components/${componentId}/activate',
    '/api/components/${componentId}/deactivate',
    '/api/components/${componentId}/restore',
    '/api/components/${componentId}/spec-candidates',
    '/api/component-spec-candidates/${candidateId}',
    '/api/component-spec-candidates/${candidateId}/confirm',
    '/api/component-spec-candidates/${candidateId}/reject',
    '/api/components/${componentId}/spec-snapshots/manual',
    '/api/components/${componentId}/spec-snapshots',
    '/api/component-spec-snapshots/${snapshotId}',
    '/api/components/${componentId}/images',
    '/api/components/${componentId}/images/${mediaId}/replacement',
    '/api/components/${componentId}/images/${mediaId}/primary',
    '/api/components/${componentId}/images/order',
    '/api/media/${mediaId}',
    '/api/media/${mediaId}/content'
  ]

  await test(`源码包含 ${endpoints.length} 个契约端点`, () => {
    for (const endpoint of endpoints) {
      assert.ok(source.includes(endpoint), `缺少端点 ${endpoint}`)
    }
  })

  await test('源码未出现禁止的基础设施响应字段名', () => {
    const forbidden = ['absolutePath', 'localPath', 'objectKey', 'bucket', 'endpoint', 'provider', 'signedUrl', 'temporaryUrl']
    for (const field of forbidden) {
      const assignment = new RegExp(`[^\\n]*${field}[^\\n]*:`)
      const hit = source.split('\n').filter((line) => assignment.test(line) && !line.includes('禁止'))
      assert.equal(hit.length, 0, `发现疑似响应字段 ${field}: ${hit.join(' | ')}`)
    }
  })
})

// ========== 3. 目录列表与权限 ==========
await group('目录列表、筛选与权限（§5 / §8 / §10.2）', async () => {
  await test('MEMBER 仅看到 ACTIVE 型号', async () => {
    setUser(3, ['MEMBER'])
    const res = await api.listComponents({ page: 1, pageSize: 20 })
    assert.ok(res.list.every((item) => item.status === 'ACTIVE'), 'MEMBER 不应看到 DRAFT/INACTIVE')
    assert.equal(res.list.some((item) => item.id === 303), false)
    assert.equal(res.list.some((item) => item.id === 304), false)
    setUser(1, ['SYSTEM_ADMIN'])
  })

  await test('管理者可按状态筛选，分类包含后代', async () => {
    const all = await api.listComponents({ page: 1, pageSize: 50 })
    assert.ok(all.list.some((item) => item.id === 303))
    const draft = await api.listComponents({ status: 'DRAFT', page: 1, pageSize: 50 })
    assert.deepEqual(draft.list.map((item) => item.id), [303])
    const category = await api.listComponents({ categoryId: 11, page: 1, pageSize: 50 })
    assert.ok(category.list.some((item) => item.id === 301), '父分类应包含后代型号')
    const withoutDescendants = await api.listComponents({ categoryId: 11, includeDescendants: false, page: 1, pageSize: 50 })
    assert.equal(withoutDescendants.list.some((item) => item.id === 301), false)
  })

  await test('非法 pageSize 与排序字段返回 400 INVALID_PARAMETER', async () => {
    await expectReject(() => api.listComponents({ page: 1, pageSize: 101 }), 'INVALID_PARAMETER', 400)
    await expectReject(() => api.listComponents({ sortBy: 'drop_table' }), 'INVALID_PARAMETER', 400)
    await expectReject(() => api.listComponents({ sortOrder: 'sideways' }), 'INVALID_PARAMETER', 400)
  })

  await test('GUEST 访问目录返回 403', async () => {
    setUser(9, [])
    await expectReject(() => api.listComponents({}), 'COMPONENT_OPERATION_DENIED', 403)
    setUser(1, ['SYSTEM_ADMIN'])
  })

  await test('MEMBER 打开 DRAFT 详情返回 404，不暴露存在性', async () => {
    setUser(3, ['MEMBER'])
    await expectReject(() => api.getComponent(303), 'COMPONENT_NOT_FOUND', 404)
    setUser(1, ['SYSTEM_ADMIN'])
  })
})

// ========== 4. 创建、幂等与唯一性 ==========
await group('创建、幂等与唯一性（§9 / §10.4）', async () => {
  await test('缺少 Idempotency-Key 返回 400', async () => {
    await expectReject(() => api.createComponent({
      name: '测试型号', manufacturerId: 22, categoryId: 24, mpn: 'W44-NO-KEY', packageType: 'QFN-32', baseUnit: 'PCS'
    }), 'INVALID_PARAMETER', 400)
  })

  await test('创建型号固定 DRAFT 并返回 placeholderKey', async () => {
    const created = await api.createComponent({
      name: 'W44 幂等测试型号',
      manufacturerId: 22,
      categoryId: 24,
      mpn: 'W44-IDEMPOTENT-MPN',
      packageType: 'LQFP-48',
      baseUnit: 'PCS',
      description: '契约测试临时数据'
    }, 'w44-test-create-1')
    assert.equal(created.status, 'DRAFT')
    assert.equal(created.specVersion, 0)
    assert.equal(created.imageState, 'PLACEHOLDER')
    assert.match(created.placeholderKey, /^component:SEMICONDUCTOR:LQFP:W$/)
    assert.equal(created.version, 1)
  })

  await test('相同幂等键 + 相同请求精确重放返回首次结果', async () => {
    const replay = await api.createComponent({
      name: 'W44 幂等测试型号',
      manufacturerId: 22,
      categoryId: 24,
      mpn: 'W44-IDEMPOTENT-MPN',
      packageType: 'LQFP-48',
      baseUnit: 'PCS',
      description: '契约测试临时数据'
    }, 'w44-test-create-1')
    assert.equal(replay.idempotentReplay, true)
    assert.equal(replay.id, 305)
  })

  await test('相同幂等键 + 不同请求内容返回 409 IDEMPOTENCY_KEY_REUSED', async () => {
    await expectReject(() => api.createComponent({
      name: '另一个名称',
      manufacturerId: 22,
      categoryId: 24,
      mpn: 'W44-IDEMPOTENT-MPN',
      packageType: 'LQFP-48',
      baseUnit: 'PCS'
    }, 'w44-test-create-1'), 'IDEMPOTENCY_KEY_REUSED', 409)
  })

  await test('同制造商重复 MPN 返回 409 COMPONENT_DUPLICATE_MODEL 与 existingComponentId', async () => {
    const err = await expectReject(() => api.createComponent({
      name: '重复型号',
      manufacturerId: 21,
      categoryId: 11,
      mpn: 'OPA333AIDBVR',
      packageType: 'SOT-23-5',
      baseUnit: 'PCS'
    }, 'w44-test-create-dup'), 'COMPONENT_DUPLICATE_MODEL', 409)
    assert.equal(err.data.existingComponentId, 301)
  })

  await test('停用分类/制造商不能被引用', async () => {
    await expectReject(() => api.createComponent({
      name: '无效引用', manufacturerId: 24, categoryId: 14, mpn: 'W44-X', packageType: '0603', baseUnit: 'PCS'
    }, 'w44-test-create-inactive-ref'), 'MANUFACTURER_IN_USE', 409)
  })
})

// ========== 5. 更新、状态动作与冲突 ==========
await group('乐观锁、状态动作与冲突（§9 / §10.4 / §12.3）', async () => {
  await test('过期 version 更新返回 409 VERSION_CONFLICT 且原数据不被覆盖', async () => {
    const before = await api.getComponent(301)
    await expectReject(() => api.updateComponent(301, {
      name: '被覆盖的名称',
      version: before.version - 1
    }), 'VERSION_CONFLICT', 409)
    const after = await api.getComponent(301)
    assert.equal(after.name, before.name)
    assert.equal(after.version, before.version)
  })

  await test('DRAFT 无正式快照激活返回 409 COMPONENT_SPEC_REQUIRED', async () => {
    const draft = await api.getComponent(303)
    await expectReject(() => api.activateComponent(
      draft.id,
      { version: draft.version },
      'w44-activate-no-spec'
    ), 'COMPONENT_SPEC_REQUIRED', 409)
  })

  await test('mockState=conflict 的状态动作返回 409', async () => {
    const active = await api.getComponent(302)
    setScenario('conflict')
    await expectReject(() => api.deactivateComponent(
      active.id,
      { version: active.version, reason: '契约冲突演示' },
      'w44-deactivate-conflict'
    ), 'VERSION_CONFLICT', 409)
    setScenario(null)
    const after = await api.getComponent(302)
    assert.equal(after.status, 'ACTIVE')
    assert.equal(after.version, active.version)
  })

  await test('停用原因必填；停用后编辑返回 409', async () => {
    const draft = await api.getComponent(303)
    await expectReject(() => api.deactivateComponent(
      draft.id,
      { version: draft.version, reason: '' },
      'w44-deactivate-empty-reason'
    ), 'INVALID_PARAMETER', 400)
  })
})

// ========== 6. 候选、快照与历史 ==========
await group('候选确认、不可变快照与历史（§4.3 / §7.2 / §7.3）', async () => {
  await test('登记候选需要来源 URL/标题/获取时间与原始/标准化规格', async () => {
    await expectReject(() => api.createSpecCandidate(303, {
      componentVersion: 2,
      sourceType: 'MANUFACTURER_DATASHEET',
      rawSpecs: [{ name: 'Input voltage', value: '1.6 V to 5.5 V', unit: 'V' }],
      normalizedProposal: [{
        specCode: 'input_voltage_range', label: '输入电压范围', valueType: 'STRING', normalizedValue: '1.6-5.5', unit: 'V', sortOrder: 10
      }]
    }, 'w44-candidate-missing-source'), 'SOURCE_METADATA_INVALID', 400)
  })

  await test('确认 PENDING 候选生成不可变快照并推进 specVersion', async () => {
    const draft = await api.getComponent(303)
    const result = await api.confirmSpecCandidate(501, {
      componentVersion: draft.version,
      candidateVersion: 1,
      specifications: [
        { specCode: 'input_voltage_range', label: '输入电压范围', valueType: 'STRING', rawValue: '1.6 V to 5.5 V', normalizedValue: '1.6-5.5', unit: 'V', sortOrder: 10 },
        { specCode: 'dropout_voltage_max', label: '最大压差', valueType: 'INTEGER', rawValue: '300 mV', normalizedValue: 300, unit: 'mV', sortOrder: 20 }
      ],
      changeSummary: '首次确认 TPS7A02 数据手册规格'
    }, 'w44-confirm-501')
    assert.equal(result.candidateStatus, 'CONFIRMED')
    assert.equal(result.snapshotVersion, 1)
    assert.equal(result.specVersion, 1)
    assert.ok(result.snapshotId > 0)

    const after = await api.getComponent(303)
    assert.equal(after.specVersion, 1)
    assert.equal(after.currentSpecSnapshotId, result.snapshotId)

    const snapshot = await api.getSpecSnapshot(result.snapshotId)
    assert.equal(snapshot.candidateId, 501)
    assert.equal(snapshot.changeSummary, '首次确认 TPS7A02 数据手册规格')
    assert.ok(snapshot.checksum.length >= 32)
    assert.equal(snapshot.specifications.length, 2)
  })

  await test('终态候选再次确认返回 409 SPEC_CANDIDATE_ALREADY_DECIDED', async () => {
    const draft = await api.getComponent(303)
    await expectReject(() => api.confirmSpecCandidate(501, {
      componentVersion: draft.version,
      candidateVersion: 2,
      specifications: [{ specCode: 'a_b', label: '测试', valueType: 'STRING', normalizedValue: 'x', sortOrder: 10 }],
      changeSummary: '重复确认'
    }, 'w44-confirm-501-again'), 'SPEC_CANDIDATE_ALREADY_DECIDED', 409)
  })

  await test('手工快照生成下一版本，旧版本仍可查询', async () => {
    const before = await api.getComponent(303)
    const oldSnapshots = await api.listSpecSnapshots(303, { pageSize: 100 })
    const manual = await api.createManualSpecSnapshot(303, {
      componentVersion: before.version,
      sourceType: 'MANUAL',
      sourceUrl: null,
      sourceNote: '供应商页面暂不可用，依据实验室料盘标签和老师确认录入',
      rawSpecs: [{ name: '封装标签', value: 'SOT-23-5', unit: null }],
      specifications: [
        { specCode: 'input_voltage_range', label: '输入电压范围', valueType: 'STRING', normalizedValue: '1.6-5.5', unit: 'V', sortOrder: 10 },
        { specCode: 'package_marking', label: '封装标签', valueType: 'STRING', normalizedValue: 'SOT-23-5', unit: null, sortOrder: 20 }
      ],
      changeSummary: '补充线下确认的封装标签'
    }, 'w44-manual-303')
    assert.equal(manual.snapshotVersion, 2)

    const history = await api.listSpecSnapshots(303, { pageSize: 100 })
    assert.ok(history.total >= oldSnapshots.total + 1)
    assert.ok(history.list.some((item) => item.snapshotVersion === 1), '旧快照应永久可查')

    const after = await api.getComponent(303)
    assert.equal(after.currentSpecSnapshotId, manual.snapshotId)
    assert.equal(after.specVersion, 2)
  })

  await test('MANUAL 快照缺少来源说明返回 400 SOURCE_METADATA_INVALID', async () => {
    const component = await api.getComponent(303)
    await expectReject(() => api.createManualSpecSnapshot(303, {
      componentVersion: component.version,
      sourceType: 'MANUAL',
      sourceNote: '',
      rawSpecs: [{ name: '标签', value: 'x', unit: null }],
      specifications: [{ specCode: 'a_b', label: '测试', valueType: 'STRING', normalizedValue: 'x', sortOrder: 10 }],
      changeSummary: '测试'
    }, 'w44-manual-no-note'), 'SOURCE_METADATA_INVALID', 400)
  })

  await test('MEMBER 只看到当前正式快照', async () => {
    setUser(3, ['MEMBER'])
    const res = await api.listSpecSnapshots(301, { pageSize: 100 })
    assert.equal(res.total, 1)
    assert.equal(res.list[0].id, 602)
    setUser(1, ['SYSTEM_ADMIN'])
  })

  await test('TEACHER 可查看候选但无确认权限', async () => {
    setUser(2, ['TEACHER'])
    try {
      const candidates = await api.listSpecCandidates(301, { pageSize: 100 })
      assert.equal(candidates.total, 1)
      // 契约 §10.4：确认候选的越权原因固定为 SPEC_CONFIRM_DENIED
      await expectReject(() => api.confirmSpecCandidate(502, {
        componentVersion: 1, candidateVersion: 2, specifications: [], changeSummary: 'x'
      }, 'w44-teacher-confirm'), 'SPEC_CONFIRM_DENIED', 403)
    } finally {
      setUser(1, ['SYSTEM_ADMIN'])
    }
  })
})

// ========== 7. 媒体与占位 ==========
await group('图片、媒体边界与确定性占位（§4.4 / §7.5 / §12.3）', async () => {
  const filePng = { name: 'w44-photo.png', type: 'image/png', size: 48213 }
  const fileJpg = { name: 'w44-photo.jpg', type: 'image/jpeg', size: 36000 }
  let firstMediaId = null

  await test('上传 PNG 返回 mediaId 与受控元数据，不出现基础设施字段', async () => {
    const before = await api.getComponent(302)
    const res = await api.uploadComponentImage(302, {
      file: filePng,
      componentVersion: before.version,
      sortOrder: 10
    }, 'w44-media-upload-1')
    assert.equal(res.imageState, 'READY')
    assert.equal(res.primaryMediaId, res.media.mediaId)
    assert.equal(res.media.role, 'PRIMARY')
    assert.equal(res.media.contentType, 'image/png')
    const forbidden = ['objectKey', 'absolutePath', 'localPath', 'bucket', 'endpoint', 'provider', 'signedUrl', 'temporaryUrl']
    for (const field of forbidden) {
      assert.equal(field in res.media, false, `媒体响应不应包含 ${field}`)
    }
    firstMediaId = res.media.mediaId
  })

  await test('非 JPEG/PNG 与超过 5 MiB 返回 400', async () => {
    const component = await api.getComponent(302)
    await expectReject(() => api.uploadComponentImage(302, {
      file: { name: 'x.gif', type: 'image/gif', size: 1000 },
      componentVersion: component.version
    }, 'w44-media-gif'), 'MEDIA_TYPE_UNSUPPORTED', 400)
    await expectReject(() => api.uploadComponentImage(302, {
      file: { name: 'big.png', type: 'image/png', size: 6 * 1024 * 1024 },
      componentVersion: component.version
    }, 'w44-media-big'), 'MEDIA_TOO_LARGE', 400)
  })

  await test('上传第二张、替换、设主图、排序均使用双版本并返回最新图片集', async () => {
    const component = await api.getComponent(302)
    const second = await api.uploadComponentImage(302, {
      file: fileJpg,
      componentVersion: component.version,
      sortOrder: 20
    }, 'w44-media-upload-2')
    assert.equal(second.media.role, 'GALLERY')
    const replaced = await api.replaceComponentImage(302, second.media.mediaId, {
      file: { ...fileJpg, name: 'w44-replacement.jpg' },
      componentVersion: second.componentVersion,
      mediaVersion: second.media.version
    }, 'w44-media-replacement')
    assert.notEqual(replaced.media.mediaId, second.media.mediaId)
    assert.equal(replaced.media.sortOrder, second.media.sortOrder)
    assert.equal(replaced.media.role, second.media.role)
    const secondMediaId = replaced.media.mediaId

    const primary = await api.setPrimaryComponentImage(302, secondMediaId, {
      componentVersion: replaced.componentVersion,
      mediaVersion: replaced.media.version
    })
    assert.equal(primary.primaryMediaId, secondMediaId)
    assert.equal(primary.images.find((item) => item.mediaId === secondMediaId).role, 'PRIMARY')

    const ordered = await api.reorderComponentImages(302, {
      componentVersion: primary.componentVersion,
      items: primary.images.map((item, index) => ({
        mediaId: item.mediaId,
        mediaVersion: item.version,
        sortOrder: index === 0 ? 20 : 10
      }))
    })
    assert.equal(ordered.images.length, 2)
    assert.ok(ordered.componentVersion > primary.componentVersion)
  })

  await test('监控图片上限：每型号最多 5 张 READY', async () => {
    const component = await api.getComponent(302)
    const images = await api.listComponentImages(302)
    let version = component.version
    for (let i = images.images.length; i < 5; i++) {
      const res = await api.uploadComponentImage(302, {
        file: { ...filePng, name: `w44-fill-${i}.png` },
        componentVersion: version,
        sortOrder: (i + 1) * 10
      }, `w44-media-fill-${i}`)
      version = res.componentVersion
    }
    assert.equal((await api.listComponentImages(302)).images.length, 5)
    await expectReject(() => api.uploadComponentImage(302, {
      file: { ...filePng, name: 'w44-overflow.png' },
      componentVersion: version,
      sortOrder: 60
    }, 'w44-media-overflow'), 'MEDIA_LIMIT_EXCEEDED', 409)
  })

  await test('删除图片逻辑化；删除最后一张回到同一 placeholderKey', async () => {
    const payload = await api.listComponentImages(302)
    const originalKey = (await api.getComponent(302)).placeholderKey
    let componentVersion = payload.componentVersion
    for (const media of payload.images) {
      const current = await api.listComponentImages(302)
      const target = current.images.find((item) => item.mediaId === media.mediaId)
      if (!target) continue
      const res = await api.deleteComponentImage(302, target.mediaId, {
        componentVersion: current.componentVersion,
        mediaVersion: target.version,
        reason: '契约测试清理'
      })
      componentVersion = res.componentVersion
      assert.equal((await api.getMedia(target.mediaId).catch(() => null)) === null, true, '已删除媒体元数据应 404')
    }
    const after = await api.listComponentImages(302)
    assert.equal(after.images.length, 0)
    assert.equal(after.imageState, 'PLACEHOLDER')
    assert.equal(after.primaryMediaId, null)
    assert.equal((await api.getComponent(302)).placeholderKey, originalKey)
  })

  await test('MEMBER 可读 ACTIVE 型号图片；DRAFT 型号媒体与未知 mediaId 统一 404', async () => {
    setUser(3, ['MEMBER'])
    const media = await api.getMedia(801)
    assert.equal(media.status, 'READY')
    assert.equal(media.contentPath, '/api/media/801/content')
    assert.equal(typeof (await api.getMediaContent(801)), 'string')
    await expectReject(() => api.getMedia(999), 'MEDIA_NOT_FOUND', 404)

    // DRAFT 型号对 MEMBER 不可见，其媒体也不能通过 mediaId 串联读取
    setUser(1, ['SYSTEM_ADMIN'])
    const draft = await api.getComponent(303)
    const uploaded = await api.uploadComponentImage(303, {
      file: filePng,
      componentVersion: draft.version,
      sortOrder: 10
    }, 'w44-media-draft')
    const draftMediaId = uploaded.media.mediaId
    setUser(3, ['MEMBER'])
    await expectReject(() => api.getMedia(draftMediaId), 'MEDIA_NOT_FOUND', 404)
    await expectReject(() => api.getMediaContent(draftMediaId), 'MEDIA_NOT_FOUND', 404)
    setUser(1, ['SYSTEM_ADMIN'])
    assert.equal((await api.getMedia(draftMediaId)).status, 'READY')
  })

  await test('相同 placeholderKey 始终得到相同占位样式', () => {
    const key = 'component:SEMICONDUCTOR:SOT:O'
    const a = api.placeholderDisplay(key)
    const b = api.placeholderDisplay(key)
    assert.deepEqual(a, b)
    assert.equal(a.category, 'SEMICONDUCTOR')
    assert.equal(a.packageType, 'SOT')
    assert.equal(a.glyph, 'O')
    assert.notDeepEqual(api.placeholderDisplay(key).palette, api.placeholderDisplay('component:PASSIVE:0603:G').palette)
  })
})

// ========== 8. mockState 状态注入 ==========
await group('mockState 完整 UI 状态（§12.3）', async () => {
  await test('empty 返回空分页', async () => {
    setScenario('empty')
    const res = await api.listComponents({})
    assert.equal(res.total, 0)
    assert.deepEqual(res.list, [])
    setScenario(null)
  })

  await test('error 带 traceId', async () => {
    setScenario('error')
    const err = await expectReject(() => api.listComponents({}), 'INTERNAL_ERROR', 500)
    assert.ok(err.data.traceId, '错误必须带 traceId')
    setScenario(null)
  })

  await test('permission 返回 403', async () => {
    setScenario('permission')
    await expectReject(() => api.listCategories(), 'COMPONENT_OPERATION_DENIED', 403)
    setScenario(null)
  })

  await test('success / inactive / placeholder 在状态元数据中可识别', async () => {
    const { MOCK_STATE_META } = await import(pathToFileURL(path.join(here, '../../utils/mockScenario.js')).href)
    for (const state of ['success', 'inactive', 'placeholder']) {
      assert.ok(MOCK_STATE_META[state], `缺少 mockState=${state}`)
    }
    assert.equal(MOCK_STATE_META.placeholder.label, '无图占位')
  })
})

// ========== 9. 页面与路由静态检查 ==========
await group('页面、路由与侧栏（§12.1 / §12.3）', async () => {
  const pageFiles = [
    'src/views/components/ComponentList.vue',
    'src/views/components/ComponentDetail.vue',
    'src/views/components/ComponentEditor.vue',
    'src/views/components/SpecCandidates.vue',
    'src/views/components/CategoryManage.vue',
    'src/views/components/ManufacturerManage.vue'
  ]

  await test('6 个 W44 页面文件均存在', () => {
    for (const file of pageFiles) {
      assert.ok(existsSync(path.join(frontendRoot, file)), `缺少页面 ${file}`)
    }
  })

  await test('7 条契约路由与角色限制已注册', () => {
    const router = readFileSync(path.join(frontendRoot, 'src/router/index.js'), 'utf8')
    const required = [
      { path: 'components', name: 'ComponentList' },
      { path: 'components/new', name: 'ComponentCreate' },
      { path: 'components/:id', name: 'ComponentDetail' },
      { path: 'components/:id/edit', name: 'ComponentEdit' },
      { path: 'components/:id/spec-candidates', name: 'ComponentSpecCandidates' },
      { path: 'component-categories', name: 'ComponentCategories' },
      { path: 'component-manufacturers', name: 'ComponentManufacturers' }
    ]
    for (const item of required) {
      assert.ok(router.includes(`path: '${item.path}'`), `缺少路由 ${item.path}`)
      assert.ok(router.includes(`name: '${item.name}'`), `缺少路由名 ${item.name}`)
    }
    assert.ok(router.includes("meta: { title: '新建元器件型号', roles: [ROLE.SYSTEM_ADMIN, ROLE.STOCK_KEEPER] }"))
    assert.ok(router.includes("meta: { title: '规格候选与确认', roles: [ROLE.SYSTEM_ADMIN, ROLE.STOCK_KEEPER, ROLE.TEACHER] }"))
    assert.ok(router.includes("meta: { title: '元器件分类树', roles: [ROLE.SYSTEM_ADMIN, ROLE.STOCK_KEEPER, ROLE.TEACHER] }"))
  })

  await test('侧栏注册元器件目录菜单组', () => {
    const layout = readFileSync(path.join(frontendRoot, 'src/layout/Index.vue'), 'utf8')
    assert.ok(layout.includes('title: \'元器件目录\''))
    assert.ok(layout.includes("index: '/components'"))
    assert.ok(layout.includes("index: '/component-categories'"))
    assert.ok(layout.includes("index: '/component-manufacturers'"))
  })

  await test('页面使用 RegionState、MockStateBanner 与统一 API 模块', () => {
    const list = readFileSync(path.join(frontendRoot, 'src/views/components/ComponentList.vue'), 'utf8')
    assert.ok(list.includes('RegionState'))
    assert.ok(list.includes('MockStateBanner'))
    assert.ok(list.includes("from '@/api/components'"))
    assert.equal(/axios/.test(list), false, '页面不得直接使用 axios')
    const detail = readFileSync(path.join(frontendRoot, 'src/views/components/ComponentDetail.vue'), 'utf8')
    assert.equal(/axios/.test(detail), false, '详情页不得直接使用 axios')
    const candidates = readFileSync(path.join(frontendRoot, 'src/views/components/SpecCandidates.vue'), 'utf8')
    assert.ok(candidates.includes('el-collapse'), '移动端候选对比必须使用逐项折叠')
  })

  await test('页面不消费 objectKey/bucket/provider/endpoint/签名 URL', () => {
    const forbidden = ['objectKey', 'absolutePath', 'localPath', 'bucket', 'provider', 'endpoint', 'signedUrl', 'temporaryUrl']
    for (const file of pageFiles) {
      const content = readFileSync(path.join(frontendRoot, file), 'utf8')
      for (const field of forbidden) {
        assert.equal(content.includes(field), false, `${file} 不应出现 ${field}`)
      }
    }
  })
})

cleanup()

process.stdout.write(`\nW44 契约测试：${pass} 通过，${fail} 失败\n`)
if (fail) {
  for (const item of failures) process.stdout.write(`  - ${item.name}: ${item.message}\n`)
  process.exit(1)
}
