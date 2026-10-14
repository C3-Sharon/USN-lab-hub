/**
 * W42 契约测试：实验模板 / 模板版本 / JSON Schema 受控子集 / 单元模板关联
 *
 * 契约来源：docs/contracts/EXPERIMENT_TEMPLATE_API.md v1.0（FROZEN）
 *           docs/weekly/2026-W42.md §5
 *
 * 覆盖：
 *   - TEMPLATE_SCOPE / TEMPLATE_VERSION_STATUS / SCHEMA_FIELD_TYPE / SCHEMA_WIDGET 枚举与映射（§2 / §3 / §4）
 *   - 版本状态机（§3.3）与版本号规则（§3.2）
 *   - experimentTemplates.js 12 个端点路径与契约一致（§5.7）
 *   - 受控 Schema 子集校验（§4）与数据校验（§4.8）
 *   - 权限矩阵（§7）：SYSTEM_ADMIN/TEACHER 全局、项目 OWNER/MAINTAINER 本项目、MEMBER 仅已发布、GUEST 不可访问
 *   - 列表可见性/筛选/分页（§2.3 / §2.5）
 *   - 创建 / 复制 / 编辑 / 发布（自动滚动草稿）/ 废弃 / 已发布版本列表 全流程
 *   - 错误码（§8）
 *   - learning.js 单元模板 additive 关联（§5.4）与已废弃版本提示
 *   - apiError.js 实验模板错误码归一化（§8）
 *   - 路由 / 侧栏 / 列表页 / 详情页 / 编辑器 / 单元选择器静态检查（§5.8）
 *
 * 运行：node src/api/__tests__/w42-contract-test.mjs
 *
 * 不引入 jest / vitest；保持与 w41-contract-test.mjs 一致的
 * "纯 Node + assert" 风格：读取源码替换 @/ 别名为桩或临时模块绝对路径后动态 import。
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

async function loadModule(srcRelativeUrl, stubs) {
  const srcUrl = new URL(srcRelativeUrl, import.meta.url)
  let code = readFileSync(srcUrl, 'utf8')
  for (const [importStmt, stub] of Object.entries(stubs)) {
    assert.ok(code.includes(importStmt), `源码中应包含 ${importStmt}`)
    code = code.replace(importStmt, stub)
  }
  const tmpPath = path.join(here, `.tmp-w42-${Date.now()}-${Math.random().toString(36).slice(2)}.mjs`)
  writeFileSync(tmpPath, code)
  tmpFiles.push(tmpPath)
  const url = pathToFileURL(tmpPath).href
  return { mod: await import(url), url }
}

function cleanup() {
  for (const f of tmpFiles) {
    try { rmSync(f, { force: true }) } catch { /* 忽略清理失败 */ }
  }
}

// ========== 通用桩 ==========
const REQUEST_STUB = `const request = {
  get() { return Promise.reject(new Error('contract-test: 禁止真实网络')) },
  post() { return Promise.reject(new Error('contract-test: 禁止真实网络')) },
  put() { return Promise.reject(new Error('contract-test: 禁止真实网络')) },
  delete() { return Promise.reject(new Error('contract-test: 禁止真实网络')) }
}`
const MOCK_STUB = `const getMockEnabled = () => true`
const USER_STUB = `const userStore = { userInfo: { id: 2 } }
const currentRoles = () => globalThis.__W42_ROLES || ['MEMBER']`
const ROLE_STUB = `const ROLE = { SYSTEM_ADMIN: 'SYSTEM_ADMIN', TEACHER: 'TEACHER', STOCK_KEEPER: 'STOCK_KEEPER', MEMBER: 'MEMBER' }`

function setRoles(roles) {
  globalThis.__W42_ROLES = roles
}

// ========== 加载被测模块 ==========
const projectsLoaded = await loadModule('../projects.js', {
  "import request from '@/utils/request'": REQUEST_STUB,
  "import { getMockEnabled } from '@/utils/mock'": MOCK_STUB
})

const templatesLoaded = await loadModule('../experimentTemplates.js', {
  "import request from '@/utils/request'": REQUEST_STUB,
  "import { getMockEnabled } from '@/utils/mock'": MOCK_STUB,
  "import { userStore, currentRoles } from '@/store/user'": USER_STUB,
  "import { ROLE } from '@/utils/permission'": ROLE_STUB,
  "import { PROJECT_ROLE, findMockProject } from '@/api/projects'": `import { PROJECT_ROLE, findMockProject } from '${projectsLoaded.url}'`
})

const learningLoaded = await loadModule('../learning.js', {
  "import request from '@/utils/request'": REQUEST_STUB,
  "import { getMockEnabled } from '@/utils/mock'": MOCK_STUB,
  "import { userStore, currentRoles } from '@/store/user'": USER_STUB,
  "import { ROLE } from '@/utils/permission'": ROLE_STUB,
  "import { resolveMockTemplateVersion } from '@/api/experimentTemplates'": `import { resolveMockTemplateVersion } from '${templatesLoaded.url}'`
})

const apiError = (await loadModule('../../utils/apiError.js', {
  "import { ElMessage, ElMessageBox } from 'element-plus'": `const ElMessage = { error() {} }; const ElMessageBox = { confirm() { return Promise.resolve() } }`
})).mod

const tpl = templatesLoaded.mod
const learning = learningLoaded.mod

const {
  TEMPLATE_SCOPE,
  TEMPLATE_SCOPE_META,
  TEMPLATE_VERSION_STATUS,
  TEMPLATE_VERSION_STATUS_META,
  TEMPLATE_VERSION_TRANSITIONS,
  canTransitionTemplateVersion,
  isTerminalTemplateVersionStatus,
  SCHEMA_FIELD_TYPE,
  SCHEMA_FIELD_TYPE_META,
  SCHEMA_WIDGET,
  SCHEMA_WIDGET_META,
  SCHEMA_UNSUPPORTED_KEYWORDS,
  SCHEMA_MAX_FIELD_COUNT
} = tpl

// ========== 形状断言 ==========
const SCOPES = ['SYSTEM', 'PROJECT']
const VERSION_STATUSES = ['DRAFT', 'PUBLISHED', 'DEPRECATED']
const FIELD_TYPES = ['string', 'number', 'integer', 'boolean']

function assertErrorShape(err, code, reason) {
  assert.equal(err.code, code, `错误 code=${code}`)
  assert.equal(typeof err.msg, 'string', 'msg 为 string')
  assert.equal(err.reason, reason, `reason=${reason}`)
}

function assertTemplateSummary(t) {
  assert.equal(typeof t.id, 'number', 'id 为 number')
  assert.equal(typeof t.title, 'string', 'title 为 string')
  assert.ok(SCOPES.includes(t.scope), `scope 合法，实际 ${t.scope}`)
  assert.equal(typeof t.publishedVersionCount, 'number', 'publishedVersionCount 为 number')
  assert.ok(t.latestVersion === null || typeof t.latestVersion === 'string', 'latestVersion 为 null 或 string')
}

function assertVersionSummary(v) {
  assert.equal(typeof v.id, 'number', 'version.id 为 number')
  assert.equal(typeof v.templateId, 'number', 'version.templateId 为 number')
  assert.equal(typeof v.version, 'string', 'version.version 为 string')
  assert.ok(VERSION_STATUSES.includes(v.status), `version.status 合法，实际 ${v.status}`)
  assert.equal(typeof v.fieldCount, 'number', 'version.fieldCount 为 number')
}

// ========== 枚举与映射（§2 / §3 / §4） ==========
await group('枚举与中文映射（契约 §2 / §3 / §4）', async () => {
  await test('模板范围两档齐全', async () => {
    assert.deepEqual(Object.values(TEMPLATE_SCOPE).sort(), [...SCOPES].sort())
  })

  await test('范围 META 覆盖全部且含 label/tagType/desc', async () => {
    for (const s of SCOPES) {
      assert.ok(TEMPLATE_SCOPE_META[s], `缺少 ${s} 的 META`)
      assert.equal(typeof TEMPLATE_SCOPE_META[s].label, 'string')
      assert.equal(typeof TEMPLATE_SCOPE_META[s].tagType, 'string')
      assert.equal(typeof TEMPLATE_SCOPE_META[s].desc, 'string')
    }
  })

  await test('版本状态三态齐全', async () => {
    assert.deepEqual(Object.values(TEMPLATE_VERSION_STATUS).sort(), [...VERSION_STATUSES].sort())
  })

  await test('版本状态 META 覆盖全部且 deprecated 标记正确（§9.9）', async () => {
    for (const s of VERSION_STATUSES) {
      assert.ok(TEMPLATE_VERSION_STATUS_META[s], `缺少 ${s} 的 META`)
      assert.equal(typeof TEMPLATE_VERSION_STATUS_META[s].label, 'string')
      assert.equal(typeof TEMPLATE_VERSION_STATUS_META[s].tagType, 'string')
      assert.equal(typeof TEMPLATE_VERSION_STATUS_META[s].deprecated, 'boolean')
    }
    assert.equal(TEMPLATE_VERSION_STATUS_META.DEPRECATED.deprecated, true, 'DEPRECATED 标记为废弃样式')
    assert.equal(TEMPLATE_VERSION_STATUS_META.PUBLISHED.deprecated, false)
  })

  await test('字段类型四类齐全', async () => {
    assert.deepEqual(Object.values(SCHEMA_FIELD_TYPE).sort(), [...FIELD_TYPES].sort())
  })

  await test('字段类型 META 覆盖全部', async () => {
    for (const t of FIELD_TYPES) {
      assert.ok(SCHEMA_FIELD_TYPE_META[t], `缺少 ${t} 的 META`)
      assert.equal(typeof SCHEMA_FIELD_TYPE_META[t].label, 'string')
    }
  })

  await test('控件枚举与类型兼容约束齐全', async () => {
    assert.deepEqual(
      Object.values(SCHEMA_WIDGET).sort(),
      ['input', 'input-number', 'select', 'switch', 'textarea'].sort()
    )
    for (const w of Object.values(SCHEMA_WIDGET)) {
      assert.ok(SCHEMA_WIDGET_META[w], `缺少控件 ${w} 的 META`)
      assert.ok(Array.isArray(SCHEMA_WIDGET_META[w].types) && SCHEMA_WIDGET_META[w].types.length > 0)
    }
  })

  await test('明确不支持的 JSON Schema 关键字（§4.4）', async () => {
    for (const kw of ['$ref', 'oneOf', 'anyOf', 'allOf', 'if', 'then', 'else', 'pattern', 'format']) {
      assert.ok(SCHEMA_UNSUPPORTED_KEYWORDS.includes(kw), `应声明不支持 ${kw}`)
    }
  })

  await test('单版本字段上限为 50（§4）', async () => {
    assert.equal(SCHEMA_MAX_FIELD_COUNT, 50)
  })
})

// ========== 版本状态机与版本号规则（§3.2 / §3.3） ==========
await group('版本状态机与版本号规则（契约 §3.2 / §3.3）', async () => {
  await test('转换矩阵与 §3.3 完全一致，DEPRECATED 为终态', async () => {
    assert.deepEqual(TEMPLATE_VERSION_TRANSITIONS.DRAFT, ['PUBLISHED'])
    assert.deepEqual(TEMPLATE_VERSION_TRANSITIONS.PUBLISHED, ['DEPRECATED'])
    assert.deepEqual(TEMPLATE_VERSION_TRANSITIONS.DEPRECATED, [])
    assert.equal(isTerminalTemplateVersionStatus('DEPRECATED'), true)
    assert.equal(isTerminalTemplateVersionStatus('PUBLISHED'), false)
    assert.equal(isTerminalTemplateVersionStatus('DRAFT'), false)
  })

  await test('canTransitionTemplateVersion 合法/非法判定', async () => {
    assert.equal(canTransitionTemplateVersion('DRAFT', 'PUBLISHED'), true)
    assert.equal(canTransitionTemplateVersion('PUBLISHED', 'DEPRECATED'), true)
    assert.equal(canTransitionTemplateVersion('PUBLISHED', 'DRAFT'), false)
    assert.equal(canTransitionTemplateVersion('DEPRECATED', 'PUBLISHED'), false)
    assert.equal(canTransitionTemplateVersion('DRAFT', 'DEPRECATED'), false)
  })

  await test('草稿版本号 0.X.0，正式版本号 1.Y.0', async () => {
    assert.equal(tpl.isDraftVersionNumber('0.2.0'), true)
    assert.equal(tpl.isDraftVersionNumber('1.0.0'), false)
    assert.equal(tpl.buildDraftVersion(1), '0.1.0')
    assert.equal(tpl.buildDraftVersion(3), '0.3.0')
    assert.equal(tpl.buildPublishedVersion(0), '1.0.0')
    assert.equal(tpl.buildPublishedVersion(2), '1.2.0')
    assert.equal(tpl.draftRevision('0.3.0'), 3)
    assert.equal(tpl.versionCodeOf('1.1.0'), 10100)
    assert.equal(tpl.versionCodeOf('1.0.0'), 10000)
  })
})

// ========== 12 个端点路径静态检查（§5.7） ==========
await group('12 个端点路径与契约一致（§5.7）', async () => {
  const src = readFileSync(new URL('../experimentTemplates.js', import.meta.url), 'utf8')

  await test('POST /api/experiment/templates 创建模板', async () => {
    assert.match(src, /request\.post\('\/api\/experiment\/templates', data\)/)
  })
  await test('GET /api/experiment/templates 模板列表', async () => {
    assert.match(src, /request\.get\('\/api\/experiment\/templates', \{ params \}\)/)
  })
  await test('GET /api/experiment/templates/{id} 模板详情', async () => {
    assert.match(src, /request\.get\(`\/api\/experiment\/templates\/\$\{templateId\}`\)/)
  })
  await test('PUT /api/experiment/templates/{id} 编辑模板', async () => {
    assert.match(src, /request\.put\(`\/api\/experiment\/templates\/\$\{templateId\}`, data\)/)
  })
  await test('POST /api/experiment/templates/{id}/copy 复制模板', async () => {
    assert.match(src, /request\.post\(`\/api\/experiment\/templates\/\$\{templateId\}\/copy`, data\)/)
  })
  await test('GET /api/experiment/templates/{id}/versions 版本列表', async () => {
    assert.match(src, /request\.get\(`\/api\/experiment\/templates\/\$\{templateId\}\/versions`, \{ params \}\)/)
  })
  await test('GET /api/experiment/templates/{id}/versions/{vid} 版本详情', async () => {
    assert.match(src, /request\.get\(`\/api\/experiment\/templates\/\$\{templateId\}\/versions\/\$\{versionId\}`\)/)
  })
  await test('PUT /api/experiment/templates/{id}/versions/{vid} 编辑草稿', async () => {
    assert.match(src, /request\.put\(`\/api\/experiment\/templates\/\$\{templateId\}\/versions\/\$\{versionId\}`, data\)/)
  })
  await test('PUT .../versions/{vid}/publish 发布版本', async () => {
    assert.match(src, /request\.put\(`\/api\/experiment\/templates\/\$\{templateId\}\/versions\/\$\{versionId\}\/publish`, payload\)/)
  })
  await test('PUT .../versions/{vid}/deprecate 废弃版本', async () => {
    assert.match(src, /request\.put\(`\/api\/experiment\/templates\/\$\{templateId\}\/versions\/\$\{versionId\}\/deprecate`, payload\)/)
  })
  await test('GET .../versions/published 已发布版本列表', async () => {
    assert.match(src, /request\.get\(`\/api\/experiment\/templates\/\$\{templateId\}\/versions\/published`\)/)
  })
  await test('POST .../versions/{vid}/validate-data 数据校验', async () => {
    assert.match(src, /request\.post\(`\/api\/experiment\/templates\/\$\{templateId\}\/versions\/\$\{versionId\}\/validate-data`, data\)/)
  })
})

// ========== 受控 Schema 子集校验（§4） ==========
await group('受控 Schema 子集校验（契约 §4）', async () => {
  const validSchema = {
    schemaVersion: '1.0',
    type: 'object',
    properties: {
      voltage: { type: 'number', title: '电压', unit: 'V', minimum: 0, maximum: 24, displayOrder: 1, widget: 'input-number' },
      test_type: { type: 'string', title: '测试类型', enum: ['直流', '交流'], displayOrder: 2, widget: 'select' }
    },
    required: ['voltage']
  }

  await test('合法扁平 Schema 通过且 fieldCount 正确', async () => {
    const res = tpl.validateSchemaJson(validSchema)
    assert.equal(res.valid, true, res.errors.map((e) => e.message).join('; '))
    assert.equal(res.fieldCount, 2)
  })

  await test('空 Schema（properties={}）合法但 fieldCount=0', async () => {
    const res = tpl.validateSchemaJson(tpl.emptySchemaJson())
    assert.equal(res.valid, true)
    assert.equal(res.fieldCount, 0)
  })

  await test('array 类型被拒绝并定位到字段路径（§4.4）', async () => {
    const res = tpl.validateSchemaJson({ type: 'object', properties: { data: { type: 'array', title: '数据', displayOrder: 1 } } })
    assert.equal(res.valid, false)
    const err = res.errors.find((e) => e.path === 'properties.data.type')
    assert.ok(err, '应定位到 properties.data.type')
    assert.match(err.message, /array/)
  })

  await test('$ref 等不支持关键字被拒绝', async () => {
    const res = tpl.validateSchemaJson({ type: 'object', properties: { a: { $ref: '#/definitions/x' } } })
    assert.equal(res.valid, false)
    assert.ok(res.errors.some((e) => e.path.includes('$ref')))
  })

  await test('超过 50 字段被拒绝（§4）', async () => {
    const properties = {}
    for (let i = 0; i < 51; i++) properties[`f_${i}`] = { type: 'string', title: `字段${i}`, displayOrder: i }
    const res = tpl.validateSchemaJson({ type: 'object', properties })
    assert.equal(res.valid, false)
    assert.ok(res.errors.some((e) => e.path === 'properties' && /50/.test(e.message)))
  })

  await test('字段 key 命名规则（小写字母开头 + 下划线/数字）', async () => {
    const res = tpl.validateSchemaJson({ type: 'object', properties: { BadKey: { type: 'string', title: '非法', displayOrder: 1 } } })
    assert.equal(res.valid, false)
    assert.ok(res.errors.some((e) => e.path === 'properties.BadKey'))
  })

  await test('required 引用不存在字段被拒绝', async () => {
    const res = tpl.validateSchemaJson({ type: 'object', properties: { a: { type: 'string', title: '甲', displayOrder: 1 } }, required: ['b'] })
    assert.equal(res.valid, false)
    assert.ok(res.errors.some((e) => e.path === 'required'))
  })

  await test('enum 取值与类型不一致被拒绝', async () => {
    const res = tpl.validateSchemaJson({ type: 'object', properties: { a: { type: 'number', title: '数值', enum: ['x'], displayOrder: 1 } } })
    assert.equal(res.valid, false)
    assert.ok(res.errors.some((e) => e.path === 'properties.a.enum'))
  })

  await test('maxLength < minLength 被拒绝', async () => {
    const res = tpl.validateSchemaJson({ type: 'object', properties: { a: { type: 'string', title: '文本', minLength: 10, maxLength: 2, displayOrder: 1 } } })
    assert.equal(res.valid, false)
    assert.ok(res.errors.some((e) => e.path === 'properties.a.maxLength'))
  })

  await test('控件与类型不兼容被拒绝', async () => {
    const res = tpl.validateSchemaJson({ type: 'object', properties: { a: { type: 'boolean', title: '开关', widget: 'textarea', displayOrder: 1 } } })
    assert.equal(res.valid, false)
    assert.ok(res.errors.some((e) => e.path === 'properties.a.widget'))
  })

  await test('title 缺失/过短被拒绝', async () => {
    const res = tpl.validateSchemaJson({ type: 'object', properties: { a: { type: 'string', title: '', displayOrder: 1 } } })
    assert.equal(res.valid, false)
    assert.ok(res.errors.some((e) => e.path === 'properties.a.title'))
  })

  await test('widgetsForType / isWidgetCompatible 按类型推导控件', async () => {
    assert.deepEqual(tpl.widgetsForType('boolean'), ['switch'])
    assert.ok(tpl.widgetsForType('string').includes('select'))
    assert.ok(tpl.widgetsForType('number').includes('input-number'))
    assert.equal(tpl.isWidgetCompatible('boolean', 'switch'), true)
    assert.equal(tpl.isWidgetCompatible('boolean', 'textarea'), false)
  })
})

// ========== 数据校验（§4.8） ==========
await group('数据校验（契约 §4.8）', async () => {
  const schema = {
    type: 'object',
    properties: {
      voltage: { type: 'number', title: '电压', minimum: 0, maximum: 24, displayOrder: 1 },
      mode: { type: 'string', title: '模式', enum: ['直流', '交流'], displayOrder: 2 },
      note: { type: 'string', title: '备注', minLength: 2, maxLength: 10, displayOrder: 3 }
    },
    required: ['voltage']
  }

  await test('合法数据通过', async () => {
    const res = tpl.validateDataAgainstSchema(schema, { voltage: 5, mode: '直流', note: 'ok' })
    assert.equal(res.valid, true)
  })

  await test('缺少必填字段', async () => {
    const res = tpl.validateDataAgainstSchema(schema, { mode: '直流' })
    assert.equal(res.valid, false)
    assert.ok(res.errors.some((e) => e.path === 'voltage'))
  })

  await test('类型不符 / 枚举非法 / 超范围 / 长度越界', async () => {
    const res = tpl.validateDataAgainstSchema(schema, { voltage: 30, mode: '脉冲', note: 'x' })
    assert.equal(res.valid, false)
    assert.ok(res.errors.some((e) => e.path === 'voltage' && /24/.test(e.message)))
    assert.ok(res.errors.some((e) => e.path === 'mode'))
    assert.ok(res.errors.some((e) => e.path === 'note'))
  })
})

// ========== 权限矩阵与可见性（§2.3 / §2.5 / §7） ==========
await group('权限矩阵与列表可见性（契约 §2.3 / §2.5 / §7）', async () => {
  await test('GUEST（无角色）不可访问：403 ACCESS_DENIED', async () => {
    setRoles([])
    assert.equal(tpl.canAccessTemplates(), false)
    await assert.rejects(
      () => tpl.listTemplates(),
      (err) => { assertErrorShape(err, 403, 'ACCESS_DENIED'); return true }
    )
  })

  await test('MEMBER 列表仅返回有已发布版本的模板', async () => {
    setRoles(['MEMBER'])
    const page = await tpl.listTemplates({ pageSize: 100 })
    assert.equal(typeof page.total, 'number')
    assert.ok(Array.isArray(page.list))
    for (const t of page.list) {
      assertTemplateSummary(t)
      assert.ok(t.publishedVersionCount > 0, '成员仅见有已发布版本的模板')
    }
  })

  await test('MEMBER 详情不含 hasDraft（§2.4）', async () => {
    setRoles(['MEMBER'])
    const detail = await tpl.getTemplateDetail(1)
    assertTemplateSummary(detail)
    assert.equal('hasDraft' in detail, false, '非管理者不返回 hasDraft')
  })

  await test('TEACHER 详情含 hasDraft', async () => {
    setRoles(['TEACHER'])
    const detail = await tpl.getTemplateDetail(1)
    assert.equal(detail.hasDraft, true, '模板 1 存在草稿版本')
  })

  await test('TEACHER 可管理系统模板与项目模板', async () => {
    setRoles(['TEACHER'])
    assert.equal(tpl.canManageSystemTemplates(), true)
    assert.equal(tpl.canManageProjectTemplates(10), true)
  })

  await test('MEMBER 仅可管理本项目（项目 10 OWNER）', async () => {
    setRoles(['MEMBER'])
    assert.equal(tpl.canManageSystemTemplates(), false)
    assert.equal(tpl.canManageProjectTemplates(10), true, '用户 2 为项目 10 OWNER')
    assert.equal(tpl.canManageProjectTemplates(5), false, '用户 2 仅为项目 5 MEMBER')
  })

  await test('scope 筛选区分系统/项目模板', async () => {
    setRoles(['TEACHER'])
    const sys = await tpl.listTemplates({ scope: 'SYSTEM', status: 'ALL', pageSize: 100 })
    const proj = await tpl.listTemplates({ scope: 'PROJECT', status: 'ALL', pageSize: 100 })
    assert.ok(sys.list.every((t) => t.scope === 'SYSTEM'))
    assert.ok(proj.list.every((t) => t.scope === 'PROJECT'))
  })

  await test('keyword 搜索标题/描述', async () => {
    setRoles(['TEACHER'])
    const res = await tpl.listTemplates({ status: 'ALL', keyword: '电压', pageSize: 100 })
    assert.ok(res.list.length > 0)
    assert.ok(res.list.every((t) => (t.title + (t.description || '')).includes('电压')))
  })

  await test('分页结构正确', async () => {
    setRoles(['TEACHER'])
    const res = await tpl.listTemplates({ status: 'ALL', page: 1, pageSize: 2 })
    assert.equal(res.page, 1)
    assert.equal(res.pageSize, 2)
    assert.ok(res.list.length <= 2)
  })
})

// ========== 版本列表与详情可见性（§3.5） ==========
await group('版本列表与详情可见性（契约 §3.5）', async () => {
  await test('MEMBER 版本列表不含 DRAFT', async () => {
    setRoles(['MEMBER'])
    const res = await tpl.listVersions(1, { pageSize: 100 })
    assert.ok(res.list.length > 0)
    assert.ok(res.list.every((v) => v.status !== 'DRAFT'), '成员不可见草稿版本')
  })

  await test('TEACHER 版本列表草稿置顶', async () => {
    setRoles(['TEACHER'])
    const res = await tpl.listVersions(1, { pageSize: 100 })
    assertVersionSummary(res.list[0])
    assert.equal(res.list[0].status, 'DRAFT', '管理者视图草稿置顶')
  })

  await test('MEMBER 访问 DRAFT 版本详情：404', async () => {
    setRoles(['MEMBER'])
    await assert.rejects(
      () => tpl.getVersionDetail(1, 3),
      (err) => { assertErrorShape(err, 404, 'TEMPLATE_VERSION_NOT_FOUND'); return true }
    )
  })

  await test('TEACHER 可读 DRAFT 版本详情含 schemaJson', async () => {
    setRoles(['TEACHER'])
    const detail = await tpl.getVersionDetail(1, 3)
    assertVersionSummary(detail)
    assert.equal(typeof detail.schemaJson, 'object', '版本详情含 schemaJson')
  })

  await test('已发布版本列表按 versionCode 降序且为简要结构', async () => {
    setRoles(['TEACHER'])
    const list = await tpl.listPublishedVersions(2)
    assert.ok(list.length >= 2, '模板 2 至少两个已发布版本')
    for (const v of list) {
      assert.equal(typeof v.id, 'number')
      assert.equal(typeof v.version, 'string')
      assert.equal(v.status, 'PUBLISHED')
      assert.equal(typeof v.fieldCount, 'number')
    }
    assert.equal(list[0].version, '1.1.0', '最新已发布版本在前')
  })
})

// ========== 创建 / 复制 / 编辑 / 发布 / 废弃 全流程（§3 / §5） ==========
await group('创建 / 复制 / 编辑 / 发布 / 废弃 全流程', async () => {
  await test('MEMBER 创建系统模板：403 TEMPLATE_OPERATION_DENIED', async () => {
    setRoles(['MEMBER'])
    await assert.rejects(
      () => tpl.createTemplate({ title: '越权系统模板', scope: 'SYSTEM' }),
      (err) => { assertErrorShape(err, 403, 'TEMPLATE_OPERATION_DENIED'); return true }
    )
  })

  await test('非法 scope：400 INVALID_PARAMETER', async () => {
    setRoles(['TEACHER'])
    await assert.rejects(
      () => tpl.createTemplate({ title: '非法范围模板', scope: 'GLOBAL' }),
      (err) => { assertErrorShape(err, 400, 'INVALID_PARAMETER'); return true }
    )
  })

  await test('项目模板缺少 projectId：400 INVALID_PARAMETER', async () => {
    setRoles(['TEACHER'])
    await assert.rejects(
      () => tpl.createTemplate({ title: '缺项目模板', scope: 'PROJECT' }),
      (err) => { assertErrorShape(err, 400, 'INVALID_PARAMETER'); return true }
    )
  })

  let projectTemplateId = null

  await test('项目 OWNER 创建项目模板：自动生成 0.1.0 草稿', async () => {
    setRoles(['MEMBER'])
    const created = await tpl.createTemplate({ title: '契约测试项目模板', scope: 'PROJECT', projectId: 10, category: '校准' })
    assertTemplateSummary(created)
    assert.equal(created.scope, 'PROJECT')
    assert.equal(created.projectId, 10)
    assert.equal(created.publishedVersionCount, 0)
    projectTemplateId = created.id

    const versions = await tpl.listVersions(created.id, { pageSize: 100 })
    assert.equal(versions.list.length, 1)
    assert.equal(versions.list[0].version, '0.1.0', '创建后自动生成草稿 0.1.0')
    assert.equal(versions.list[0].status, 'DRAFT')
  })

  await test('编辑 DRAFT Schema 成功并返回 fieldCount', async () => {
    setRoles(['MEMBER'])
    const versions = await tpl.listVersions(projectTemplateId, { pageSize: 100 })
    const draftId = versions.list[0].id
    const schemaJson = {
      schemaVersion: '1.0',
      type: 'object',
      properties: {
        power: { type: 'number', title: '功率', unit: 'W', minimum: 0, maximum: 3000, displayOrder: 1, widget: 'input-number' }
      },
      required: ['power']
    }
    const res = await tpl.updateDraftVersion(projectTemplateId, draftId, { schemaJson, changelog: '初始版本' })
    assert.equal(res.status, 'DRAFT')
    assert.equal(res.fieldCount, 1)
  })

  await test('编辑 DRAFT 非法 Schema：400 SCHEMA_INVALID 含字段级 errors', async () => {
    setRoles(['MEMBER'])
    const versions = await tpl.listVersions(projectTemplateId, { pageSize: 100 })
    const draftId = versions.list[0].id
    const err = await tpl
      .updateDraftVersion(projectTemplateId, draftId, { schemaJson: { properties: { data: { type: 'array' } } } })
      .then(() => null, (e) => e)
    assert.ok(err, '应拒绝非法 Schema')
    assertErrorShape(err, 400, 'SCHEMA_INVALID')
    assert.ok(Array.isArray(err.data?.errors) && err.data.errors.length > 0, 'data.errors 为字段级错误')
  })

  await test('发布 DRAFT：生成 1.0.0 并自动创建 0.2.0 草稿', async () => {
    setRoles(['MEMBER'])
    const versions = await tpl.listVersions(projectTemplateId, { pageSize: 100 })
    const draftId = versions.list.find((v) => v.status === 'DRAFT').id
    const res = await tpl.publishVersion(projectTemplateId, draftId, { changelog: '首次发布' })
    assert.equal(res.publishedVersion.version, '1.0.0')
    assert.equal(res.publishedVersion.status, 'PUBLISHED')
    assert.equal(res.newDraftVersion.version, '0.2.0', '发布后自动滚动草稿')
    assert.equal(res.newDraftVersion.status, 'DRAFT')

    const after = await tpl.listVersions(projectTemplateId, { pageSize: 100 })
    const published = after.list.find((v) => v.status === 'PUBLISHED')
    assert.equal(published.version, '1.0.0')
    const draft = after.list.find((v) => v.status === 'DRAFT')
    assert.equal(draft.version, '0.2.0')
  })

  await test('已发布版本不可编辑：409 TEMPLATE_VERSION_NOT_EDITABLE', async () => {
    setRoles(['MEMBER'])
    const versions = await tpl.listVersions(projectTemplateId, { pageSize: 100 })
    const publishedId = versions.list.find((v) => v.status === 'PUBLISHED').id
    await assert.rejects(
      () => tpl.updateDraftVersion(projectTemplateId, publishedId, { schemaJson: tpl.emptySchemaJson() }),
      (err) => { assertErrorShape(err, 409, 'TEMPLATE_VERSION_NOT_EDITABLE'); return true }
    )
  })

  await test('非草稿不可发布：409 TEMPLATE_VERSION_NOT_PUBLISHABLE', async () => {
    setRoles(['MEMBER'])
    const versions = await tpl.listVersions(projectTemplateId, { pageSize: 100 })
    const publishedId = versions.list.find((v) => v.status === 'PUBLISHED').id
    await assert.rejects(
      () => tpl.publishVersion(projectTemplateId, publishedId, {}),
      (err) => { assertErrorShape(err, 409, 'TEMPLATE_VERSION_NOT_PUBLISHABLE'); return true }
    )
  })

  await test('空 Schema 不可发布：400 SCHEMA_EMPTY', async () => {
    setRoles(['TEACHER'])
    // 模板 3 的草稿 31 为空 Schema
    await assert.rejects(
      () => tpl.publishVersion(3, 31, {}),
      (err) => { assertErrorShape(err, 400, 'SCHEMA_EMPTY'); return true }
    )
  })

  await test('废弃已发布版本：DEPRECATED 且不可恢复', async () => {
    setRoles(['MEMBER'])
    const versions = await tpl.listVersions(projectTemplateId, { pageSize: 100 })
    const publishedId = versions.list.find((v) => v.status === 'PUBLISHED').id
    const res = await tpl.deprecateVersion(projectTemplateId, publishedId, { reason: '有新版本替代' })
    assert.equal(res.status, 'DEPRECATED')
    assert.equal(res.deprecatedReason, '有新版本替代')

    await assert.rejects(
      () => tpl.deprecateVersion(projectTemplateId, publishedId, {}),
      (err) => { assertErrorShape(err, 409, 'TEMPLATE_VERSION_NOT_DEPRECATABLE'); return true }
    )
  })

  await test('草稿不可废弃：409 TEMPLATE_VERSION_NOT_DEPRECATABLE', async () => {
    setRoles(['MEMBER'])
    const versions = await tpl.listVersions(projectTemplateId, { pageSize: 100 })
    const draftId = versions.list.find((v) => v.status === 'DRAFT').id
    await assert.rejects(
      () => tpl.deprecateVersion(projectTemplateId, draftId, {}),
      (err) => { assertErrorShape(err, 409, 'TEMPLATE_VERSION_NOT_DEPRECATABLE'); return true }
    )
  })

  await test('模板范围不可修改：409 TEMPLATE_SCOPE_IMMUTABLE', async () => {
    setRoles(['TEACHER'])
    await assert.rejects(
      () => tpl.updateTemplate(1, { scope: 'PROJECT' }),
      (err) => { assertErrorShape(err, 409, 'TEMPLATE_SCOPE_IMMUTABLE'); return true }
    )
  })

  await test('编辑模板基本信息成功', async () => {
    setRoles(['TEACHER'])
    const res = await tpl.updateTemplate(1, { title: '电压测量实验（修订）' })
    assert.equal(res.title, '电压测量实验（修订）')
  })

  await test('数据校验接口：合法通过、非法定位字段', async () => {
    setRoles(['TEACHER'])
    const versions = await tpl.listVersions(1, { pageSize: 100 })
    const publishedId = versions.list.find((v) => v.status === 'PUBLISHED').id
    const ok = await tpl.validateVersionData(1, publishedId, { voltage: 5, test_type: '直流' })
    assert.equal(ok.valid, true)
    const bad = await tpl.validateVersionData(1, publishedId, { voltage: 999, test_type: '不存在' })
    assert.equal(bad.valid, false)
    assert.ok(bad.errors.some((e) => e.path === 'voltage'))
  })
})

// ========== 复制系统模板为项目模板（§2.6） ==========
await group('复制系统模板为项目模板（契约 §2.6）', async () => {
  await test('系统模板不可复制到无权限项目：403 TEMPLATE_OPERATION_DENIED', async () => {
    setRoles(['MEMBER'])
    // 项目 5 用户 2 仅为 MEMBER，无管理权限
    await assert.rejects(
      () => tpl.copyTemplate(1, { targetProjectId: 5, title: '复制到项目5' }),
      (err) => { assertErrorShape(err, 403, 'TEMPLATE_OPERATION_DENIED'); return true }
    )
  })

  await test('复制到有权限项目：生成 PROJECT 模板并复制最新已发布 Schema 为草稿', async () => {
    setRoles(['MEMBER'])
    const created = await tpl.copyTemplate(1, { targetProjectId: 10, title: '电压校准（项目）' })
    assertTemplateSummary(created)
    assert.equal(created.scope, 'PROJECT')
    assert.equal(created.projectId, 10)

    const versions = await tpl.listVersions(created.id, { pageSize: 100 })
    assert.equal(versions.list.length, 1)
    assert.equal(versions.list[0].status, 'DRAFT')
    assert.equal(versions.list[0].version, '0.1.0')
    // 模板 1 最新已发布 1.0.0 有 4 个字段
    assert.equal(versions.list[0].fieldCount, 4, '复制源最新已发布版本的 Schema')
  })

  await test('非系统模板不可复制：400 TEMPLATE_NOT_SYSTEM', async () => {
    setRoles(['TEACHER'])
    await assert.rejects(
      () => tpl.copyTemplate(4, { targetProjectId: 10, title: '非法复制' }),
      (err) => { assertErrorShape(err, 400, 'TEMPLATE_NOT_SYSTEM'); return true }
    )
  })

  await test('目标项目不存在：400 INVALID_PARAMETER', async () => {
    setRoles(['TEACHER'])
    await assert.rejects(
      () => tpl.copyTemplate(1, { targetProjectId: 999999, title: '目标不存在' }),
      (err) => { assertErrorShape(err, 400, 'INVALID_PARAMETER'); return true }
    )
  })
})

// ========== learning.js 单元模板关联（§5.4） ==========
await group('学习单元模板关联 additive 字段（契约 §5.4）', async () => {
  await test('已关联单元只返回契约字段，状态通过版本详情读取', async () => {
    setRoles(['MEMBER'])
    const stages = await learning.listStages(1)
    const unit = stages.flatMap((s) => s.units).find((u) => u.id === 104)
    assert.ok(unit, '单元 104 存在')
    assert.equal(unit.templateId, 1)
    assert.equal(unit.templateName, '电压测量实验（修订）')
    assert.equal(unit.templateVersion, '1.0.0')
    assert.equal(Object.hasOwn(unit, 'templateVersionStatus'), false)
    const version = await tpl.getVersionDetail(unit.templateId, unit.templateVersionId)
    assert.equal(version.status, 'PUBLISHED')
  })

  await test('已废弃版本关联保留，状态通过版本详情读取（§5.2）', async () => {
    // 单元 402 位于已归档路线 4（§2.5：归档路线仅管理角色可见），故以管理角色读取
    setRoles(['TEACHER'])
    const stages = await learning.listStages(4)
    const unit = stages.flatMap((s) => s.units).find((u) => u.id === 402)
    assert.ok(unit, '单元 402 存在')
    assert.equal(unit.templateVersionId, 41)
    assert.equal(Object.hasOwn(unit, 'templateVersionStatus'), false)
    const version = await tpl.getVersionDetail(unit.templateId, unit.templateVersionId)
    assert.equal(version.status, 'DEPRECATED', '已废弃版本关联保留且可通过版本接口读取状态')
  })

  await test('未关联单元各关联字段为 null', async () => {
    setRoles(['MEMBER'])
    const stages = await learning.listStages(1)
    const unit = stages.flatMap((s) => s.units).find((u) => u.id === 105)
    assert.equal(unit.templateId, null)
    assert.equal(unit.templateVersionId, null)
    assert.equal(unit.templateVersion, null)
    assert.equal(Object.hasOwn(unit, 'templateVersionStatus'), false)
  })

  await test('MEMBER 关联单元：403 LEARNING_OPERATION_DENIED', async () => {
    setRoles(['MEMBER'])
    await assert.rejects(
      () => learning.updateUnit(105, { templateVersionId: 2 }),
      (err) => { assertErrorShape(err, 403, 'LEARNING_OPERATION_DENIED'); return true }
    )
  })

  await test('只能关联 PUBLISHED 版本：草稿版本 400 INVALID_PARAMETER', async () => {
    setRoles(['TEACHER'])
    await assert.rejects(
      () => learning.updateUnit(105, { templateVersionId: 3 }),
      (err) => { assertErrorShape(err, 400, 'INVALID_PARAMETER'); return true }
    )
  })

  await test('关联 PUBLISHED 版本成功并可清除', async () => {
    setRoles(['TEACHER'])
    const res = await learning.updateUnit(105, { templateVersionId: 2 })
    assert.equal(res.templateVersionId, 2)
    assert.equal(Object.hasOwn(res, 'templateVersionStatus'), false)
    assert.equal((await tpl.getVersionDetail(res.templateId, res.templateVersionId)).status, 'PUBLISHED')
    const cleared = await learning.updateUnit(105, { templateVersionId: null })
    assert.equal(cleared.templateVersionId, null)
    assert.equal(cleared.templateName, null)
  })
})

// ========== apiError.js 实验模板错误码（§8） ==========
await group('实验模板错误码归一化（契约 §8）', async () => {
  await test('isTemplateNotFound 识别 404 与两类 reason', async () => {
    assert.equal(apiError.isTemplateNotFound({ code: 404 }), true)
    assert.equal(apiError.isTemplateNotFound({ reason: 'TEMPLATE_NOT_FOUND' }), true)
    assert.equal(apiError.isTemplateNotFound({ reason: 'TEMPLATE_VERSION_NOT_FOUND' }), true)
    assert.equal(apiError.isTemplateNotFound({ code: 200 }), false)
  })

  await test('isTemplateDenied 识别 403 与两类 reason', async () => {
    assert.equal(apiError.isTemplateDenied({ code: 403 }), true)
    assert.equal(apiError.isTemplateDenied({ reason: 'TEMPLATE_OPERATION_DENIED' }), true)
    assert.equal(apiError.isTemplateDenied({ reason: 'ACCESS_DENIED' }), true)
    assert.equal(apiError.isTemplateDenied({ code: 200 }), false)
  })

  await test('isSchemaInvalid 识别 SCHEMA_INVALID / SCHEMA_EMPTY', async () => {
    assert.equal(apiError.isSchemaInvalid({ reason: 'SCHEMA_INVALID' }), true)
    assert.equal(apiError.isSchemaInvalid({ reason: 'SCHEMA_EMPTY' }), true)
    assert.equal(apiError.isSchemaInvalid({ reason: 'INVALID_PARAMETER' }), false)
  })

  await test('状态机相关错误码识别', async () => {
    assert.equal(apiError.isTemplateNotSystem({ reason: 'TEMPLATE_NOT_SYSTEM' }), true)
    assert.equal(apiError.isTemplateScopeImmutable({ reason: 'TEMPLATE_SCOPE_IMMUTABLE' }), true)
    assert.equal(apiError.isTemplateVersionNotEditable({ reason: 'TEMPLATE_VERSION_NOT_EDITABLE' }), true)
    assert.equal(apiError.isTemplateVersionNotPublishable({ reason: 'TEMPLATE_VERSION_NOT_PUBLISHABLE' }), true)
    assert.equal(apiError.isTemplateVersionNotDeprecatable({ reason: 'TEMPLATE_VERSION_NOT_DEPRECATABLE' }), true)
    assert.equal(apiError.isTemplateVersionConflict({ reason: 'TEMPLATE_VERSION_CONFLICT' }), true)
  })

  await test('schemaErrors 提取字段级错误', async () => {
    const err = { reason: 'SCHEMA_INVALID', data: { errors: [{ path: 'properties.data.type', message: '不支持的类型：array' }] } }
    const list = apiError.schemaErrors(err)
    assert.equal(list.length, 1)
    assert.equal(list[0].path, 'properties.data.type')
    assert.deepEqual(apiError.schemaErrors({ reason: 'INVALID_PARAMETER' }), [])
  })
})

// ========== 页面结构静态检查（§5.8） ==========
await group('路由 / 侧栏 / 页面 / 选择器静态检查（§5.8）', async () => {
  const routerSrc = readFileSync(new URL('../../router/index.js', import.meta.url), 'utf8')
  const layoutSrc = readFileSync(new URL('../../layout/Index.vue', import.meta.url), 'utf8')
  const listSrc = readFileSync(new URL('../../views/experiment/TemplateList.vue', import.meta.url), 'utf8')
  const detailSrc = readFileSync(new URL('../../views/experiment/TemplateDetail.vue', import.meta.url), 'utf8')
  const editorSrc = readFileSync(new URL('../../views/experiment/TemplateEditor.vue', import.meta.url), 'utf8')
  const pickerSrc = readFileSync(new URL('../../views/learning/UnitTemplatePicker.vue', import.meta.url), 'utf8')
  const learningDetailSrc = readFileSync(new URL('../../views/learning/LearningDetail.vue', import.meta.url), 'utf8')

  await test('路由：列表 / 详情 / 编辑器三入口', async () => {
    assert.match(routerSrc, /path: 'experiment\/templates',/)
    assert.match(routerSrc, /path: 'experiment\/templates\/:id\/editor\/:versionId',/)
    assert.match(routerSrc, /path: 'experiment\/templates\/:id',/)
    assert.match(routerSrc, /component: TemplateList/)
    assert.match(routerSrc, /component: TemplateDetail/)
    assert.match(routerSrc, /component: TemplateEditor/)
  })

  await test('侧栏「学习实验台」含学习路线与实验模板子项', async () => {
    assert.match(layoutSrc, /index: '\/study',/)
    assert.match(layoutSrc, /title: '实验模板'/)
    assert.match(layoutSrc, /index: '\/experiment\/templates'/)
    assert.match(layoutSrc, /Collection/)
  })

  await test('列表页：系统/项目 tab + 筛选 + 创建/复制 + 空/权限态', async () => {
    assert.match(listSrc, /el-tabs/)
    assert.match(listSrc, /TEMPLATE_SCOPE\.SYSTEM/)
    assert.match(listSrc, /TEMPLATE_SCOPE\.PROJECT/)
    assert.match(listSrc, /createTemplate/)
    assert.match(listSrc, /copyTemplate/)
    assert.match(listSrc, /listProjects\(\{ page: 1, pageSize: 50 \}\)/)
    assert.match(listSrc, /state="empty"/)
    assert.match(listSrc, /state="permission"/)
    assert.match(listSrc, /grid-template-columns: repeat\(3, 1fr\)/)
  })

  await test('详情页：版本时间线 + 只读 Schema + 预览 + 发布/废弃/复制', async () => {
    assert.match(detailSrc, /VersionTimeline/)
    assert.match(detailSrc, /SchemaPreview/)
    assert.match(detailSrc, /raw-schema/)
    assert.match(detailSrc, /publishVersion/)
    assert.match(detailSrc, /deprecateVersion/)
    assert.match(detailSrc, /copyTemplate/)
    assert.match(detailSrc, /listProjects\(\{ page: 1, pageSize: 50 \}\)/)
  })

  await test('编辑器：字段列表 + 属性面板 + 拖拽排序 + 实时校验', async () => {
    assert.match(editorSrc, /draggable="true"/)
    assert.match(editorSrc, /handleDragStart/)
    assert.match(editorSrc, /moveField/)
    assert.match(editorSrc, /validateSchemaJson/)
    assert.match(editorSrc, /updateDraftVersion/)
    assert.match(editorSrc, /publishVersion/)
    assert.match(editorSrc, /mobile-steps/)
  })

  await test('单元模板选择器仅列出 PUBLISHED 版本', async () => {
    assert.match(pickerSrc, /listPublishedVersions/)
    assert.match(pickerSrc, /VersionStatusTag/)
    assert.match(pickerSrc, /status: 'PUBLISHED'/)
  })

  await test('学习详情页接入单元模板关联与废弃提示', async () => {
    assert.match(learningDetailSrc, /UnitTemplatePicker/)
    assert.match(learningDetailSrc, /unit-template/)
    assert.match(learningDetailSrc, /VersionStatusTag/)
    assert.match(learningDetailSrc, /templateVersionStatus === 'DEPRECATED'/)
    assert.match(learningDetailSrc, /getVersionDetail/)
    assert.match(learningDetailSrc, /enrichTemplateVersionStatuses/)
    assert.doesNotMatch(learningDetailSrc, /templateVersionStatus \|\| 'PUBLISHED'/)
    assert.doesNotMatch(pickerSrc, /templateVersionStatus \|\| 'PUBLISHED'/)
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
