/**
 * W39 任务书 7.3 / 9.2 契约测试：项目工作台
 *
 * 覆盖：
 *   - PROJECT_STATUS / PROJECT_ROLE 枚举与中文映射完整性
 *   - projects.js 四个端点路径与契约一致（§5.1~5.4）
 *   - createProject 成功响应字段 + 409 PROJECT_CODE_DUPLICATE
 *   - listProjects 分页结构 / status 过滤 / keyword 搜索
 *   - getProjectDetail 字段 + members 结构 + 404 PROJECT_NOT_FOUND
 *   - addProjectMember 成功 / 幂等(同角色 200) / 异角色 409 / 项目 404
 *   - workbench.js projects 区域 READY 结构（§7.1）
 *   - 路由 /projects、/projects/:id 与菜单注册（静态检查）
 *
 * 运行：node src/api/__tests__/w39-contract-test.mjs
 *
 * 不引入 jest / vitest；保持与 w38-contract-test.mjs 一致的
 * "纯 Node + assert" 风格。
 *
 * 说明：projects.js / workbench.js 依赖 '@/utils/request' 等别名与
 * import.meta.env，无法直接被 Node 加载。这里读取源码后把 import
 * 语句替换为测试桩（request 永远抛错 = 禁用真实网络，getMockEnabled
 * 恒 true = 走 mock 分支），写入临时文件再动态 import，从而对真实
 * mock 实现做动态契约校验，而不是静态正则猜测。
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

/**
 * 把使用了 @/ 别名的 API 模块转为可在 Node 中直接 import 的临时模块。
 * stubs: { 源码中的 import 语句（精确字符串）: 替换后的桩代码 }
 */
async function loadWithStubs(srcRelativeUrl, stubs) {
  const srcUrl = new URL(srcRelativeUrl, import.meta.url)
  let code = readFileSync(srcUrl, 'utf8')
  for (const [importStmt, stub] of Object.entries(stubs)) {
    assert.ok(code.includes(importStmt), `源码中应包含 ${importStmt}`)
    code = code.replace(importStmt, stub)
  }
  const tmpPath = path.join(here, `.tmp-${Date.now()}-${Math.random().toString(36).slice(2)}.mjs`)
  writeFileSync(tmpPath, code)
  tmpFiles.push(tmpPath)
  return import(pathToFileURL(tmpPath).href)
}

function cleanup() {
  for (const f of tmpFiles) {
    try { rmSync(f, { force: true }) } catch { /* 忽略清理失败 */ }
  }
}

// ========== 形状断言（对齐 PROJECT_WORKSPACE_API.md） ==========
const PROJECT_STATUSES = ['PREPARING', 'ACTIVE', 'PAUSED', 'COMPLETED', 'ARCHIVED']
const PROJECT_ROLES = ['OWNER', 'MAINTAINER', 'MEMBER', 'OBSERVER']

function assertProjectSummaryShape(p) {
  assert.equal(typeof p.id, 'number', 'id 为 number')
  assert.equal(typeof p.code, 'string', 'code 为 string')
  assert.match(p.code, /^[A-Z0-9-]{3,32}$/, 'code 为 3-32 位大写字母/数字/连字符')
  assert.equal(typeof p.name, 'string', 'name 为 string')
  assert.ok(p.name.length >= 2 && p.name.length <= 80, 'name 长度 2-80')
  assert.ok(p.summary === null || p.summary === undefined || typeof p.summary === 'string', 'summary 为 string 或空')
  assert.ok(p.category === null || p.category === undefined || typeof p.category === 'string', 'category 为 string 或空')
  assert.ok(PROJECT_STATUSES.includes(p.status), `status 属于五态枚举，实际 ${p.status}`)
  assert.ok(p.coverUrl === null || typeof p.coverUrl === 'string', 'coverUrl 为 null 或 string')
  assert.ok(PROJECT_ROLES.includes(p.myRole), `myRole 属于四档角色，实际 ${p.myRole}`)
  assert.equal(typeof p.memberCount, 'number', 'memberCount 为 number')
  assert.ok(!Number.isNaN(Date.parse(p.createTime)), 'createTime 可解析为时间')
  assert.ok(!Number.isNaN(Date.parse(p.updateTime)), 'updateTime 可解析为时间')
}

function assertMemberShape(m) {
  assert.equal(typeof m.userId, 'number', 'userId 为 number')
  assert.equal(typeof m.memberId, 'string', 'memberId（学工号）为 string')
  assert.equal(typeof m.name, 'string', 'name 为 string')
  assert.ok(PROJECT_ROLES.includes(m.projectRole), `projectRole 属于四档角色，实际 ${m.projectRole}`)
  assert.ok(!Number.isNaN(Date.parse(m.joinedAt)), 'joinedAt 可解析为时间')
}

function assertPageShape(data) {
  assert.equal(typeof data.total, 'number', 'total 为 number')
  assert.equal(typeof data.page, 'number', 'page 为 number')
  assert.equal(typeof data.pageSize, 'number', 'pageSize 为 number')
  assert.ok(Array.isArray(data.list), 'list 为数组')
}

/**
 * mock 模式下 projects.js 的 mockResponse 直接 resolve data
 * （与 request.js 拦截器解包行为一致），因此断言目标对象本身。
 * 错误响应仍 reject { code, msg, reason }。
 */
function assertErrorShape(err, code, reason) {
  assert.equal(err.code, code, `错误 code=${code}`)
  assert.equal(typeof err.msg, 'string', 'msg 为 string')
  assert.equal(err.reason, reason, `reason=${reason}`)
}

// ========== 加载被测模块 ==========
const REQUEST_STUB = `const request = {
  get() { return Promise.reject(new Error('contract-test: 禁止真实网络')) },
  post() { return Promise.reject(new Error('contract-test: 禁止真实网络')) }
}`
const MOCK_STUB = `const getMockEnabled = () => true`

const projectsApi = await loadWithStubs('../projects.js', {
  "import request from '@/utils/request'": REQUEST_STUB,
  "import { getMockEnabled } from '@/utils/mock'": MOCK_STUB
})

const workbenchApi = await loadWithStubs('../workbench.js', {
  "import request from '@/utils/request'": REQUEST_STUB,
  "import { getMockEnabled } from '@/utils/mock'": MOCK_STUB,
  "import { userStore } from '@/store/user'": `const userStore = { todayAttendance: null }`
})

const { PROJECT_STATUS, PROJECT_STATUS_META, PROJECT_ROLE, PROJECT_ROLE_META } = projectsApi

// ========== 枚举与映射 ==========
await group('枚举与中文映射（契约 §3 / §4）', async () => {
  await test('项目状态五态齐全', async () => {
    assert.deepEqual(Object.values(PROJECT_STATUS).sort(), [...PROJECT_STATUSES].sort())
  })

  await test('项目角色四档齐全', async () => {
    assert.deepEqual(Object.values(PROJECT_ROLE).sort(), [...PROJECT_ROLES].sort())
  })

  await test('状态 META 覆盖全部状态且含 label/tagType', async () => {
    for (const s of PROJECT_STATUSES) {
      assert.ok(PROJECT_STATUS_META[s], `缺少 ${s} 的 META`)
      assert.equal(typeof PROJECT_STATUS_META[s].label, 'string')
      assert.equal(typeof PROJECT_STATUS_META[s].tagType, 'string')
    }
  })

  await test('角色 META 覆盖全部角色且含 label/tagType', async () => {
    for (const r of PROJECT_ROLES) {
      assert.ok(PROJECT_ROLE_META[r], `缺少 ${r} 的 META`)
      assert.equal(typeof PROJECT_ROLE_META[r].label, 'string')
      assert.equal(typeof PROJECT_ROLE_META[r].tagType, 'string')
    }
  })
})

// ========== createProject（§5.1） ==========
await group('POST /api/projects 创建项目', async () => {
  await test('创建成功：字段齐全，status=PREPARING，myRole=OWNER，memberCount=1', async () => {
    const project = await projectsApi.createProject({
      code: 'TEST-W39',
      name: '契约测试项目',
      summary: '验证创建契约',
      category: 'hardware_project',
      coverMediaId: null
    })
    assertProjectSummaryShape(project)
    assert.equal(project.code, 'TEST-W39')
    assert.equal(project.status, 'PREPARING', '初始状态 PREPARING')
    assert.equal(project.myRole, 'OWNER', '创建者自动成为 OWNER')
    assert.equal(project.memberCount, 1, '初始成员数 1')
    assert.equal(project.coverUrl, null, 'coverMediaId 为 null 时 coverUrl 为 null')
  })

  await test('编号重复：409 PROJECT_CODE_DUPLICATE', async () => {
    await assert.rejects(
      () => projectsApi.createProject({ code: 'TEST-W39', name: '重复编号项目' }),
      (err) => { assertErrorShape(err, 409, 'PROJECT_CODE_DUPLICATE'); return true }
    )
  })
})

// ========== listProjects（§5.2） ==========
await group('GET /api/projects 项目列表', async () => {
  await test('默认分页结构 { total, page, pageSize, list }', async () => {
    const page = await projectsApi.listProjects()
    assertPageShape(page)
    assert.ok(page.list.length > 0, 'mock 下应返回演示项目')
    for (const p of page.list) assertProjectSummaryShape(p)
  })

  await test('status=ACTIVE 过滤', async () => {
    const page = await projectsApi.listProjects({ status: 'ACTIVE' })
    assertPageShape(page)
    assert.ok(page.list.length > 0)
    for (const p of page.list) assert.equal(p.status, 'ACTIVE')
  })

  await test('keyword 模糊搜索编号/名称', async () => {
    const page = await projectsApi.listProjects({ keyword: '气象' })
    assertPageShape(page)
    assert.ok(page.list.length > 0, '应命中"智能气象站项目"')
    for (const p of page.list) {
      assert.ok(p.code.includes('气象') || p.name.includes('气象'), '命中项需包含关键字')
    }
  })

  await test('空列表是合法状态（total=0, list=[]）', async () => {
    const page = await projectsApi.listProjects({ keyword: '不存在的关键字xyz' })
    assertPageShape(page)
    assert.equal(page.total, 0)
    assert.deepEqual(page.list, [])
  })
})

// ========== getProjectDetail（§5.3） ==========
await group('GET /api/projects/{id} 项目详情', async () => {
  await test('详情字段 + members 成员结构', async () => {
    const page = await projectsApi.listProjects()
    const target = page.list.find((p) => p.code === 'PROJ-001')
    assert.ok(target, 'mock 应包含 PROJ-001')
    const detail = await projectsApi.getProjectDetail(target.id)
    assertProjectSummaryShape(detail)
    assert.ok(Array.isArray(detail.members), '详情含 members 数组')
    assert.ok(detail.members.length > 0)
    for (const m of detail.members) assertMemberShape(m)
  })

  await test('详情含 OWNER 成员', async () => {
    const detail = await projectsApi.getProjectDetail(10)
    assert.ok(detail.members.some((m) => m.projectRole === 'OWNER'), '项目至少一名 OWNER')
  })

  await test('项目不存在：404 PROJECT_NOT_FOUND', async () => {
    await assert.rejects(
      () => projectsApi.getProjectDetail(999999),
      (err) => { assertErrorShape(err, 404, 'PROJECT_NOT_FOUND'); return true }
    )
  })
})

// ========== addProjectMember（§5.4） ==========
await group('POST /api/projects/{id}/members 添加成员', async () => {
  await test('添加成功：返回成员结构', async () => {
    const member = await projectsApi.addProjectMember(10, { memberId: '20260002', projectRole: 'OBSERVER' })
    assertMemberShape(member)
    assert.equal(member.memberId, '20260002')
    assert.equal(member.projectRole, 'OBSERVER')
  })

  await test('幂等：同成员同角色重复添加返回 200', async () => {
    const member = await projectsApi.addProjectMember(10, { memberId: '20260001', projectRole: 'MEMBER' })
    assertMemberShape(member)
    assert.equal(member.projectRole, 'MEMBER')
  })

  await test('冲突：同成员不同角色 409 ALREADY_MEMBER_DIFFERENT_ROLE', async () => {
    await assert.rejects(
      () => projectsApi.addProjectMember(10, { memberId: '20260001', projectRole: 'OWNER' }),
      (err) => { assertErrorShape(err, 409, 'ALREADY_MEMBER_DIFFERENT_ROLE'); return true }
    )
  })

  await test('项目不存在：404 PROJECT_NOT_FOUND', async () => {
    await assert.rejects(
      () => projectsApi.addProjectMember(999999, { memberId: '20260003', projectRole: 'MEMBER' }),
      (err) => { assertErrorShape(err, 404, 'PROJECT_NOT_FOUND'); return true }
    )
  })
})

// ========== 端点路径静态检查 ==========
await group('端点路径与契约 §5 一致', async () => {
  const src = readFileSync(new URL('../projects.js', import.meta.url), 'utf8')

  await test('mock 成功响应直接 resolve data（与拦截器解包一致）', async () => {
    const fnSrc = src.match(/function mockResponse\(data\) \{[\s\S]*?\}/)?.[0] || ''
    assert.match(fnSrc, /Promise\.resolve\(data\)/, 'mockResponse 应 resolve data 而非信封')
    assert.doesNotMatch(fnSrc, /code:\s*200/, 'mockResponse 不应包 code:200 信封')
  })

  await test('POST /api/projects', async () => {
    assert.match(src, /request\.post\('\/api\/projects', data\)/)
  })

  await test('GET /api/projects（带 params）', async () => {
    assert.match(src, /request\.get\('\/api\/projects', \{ params \}\)/)
  })

  await test('GET /api/projects/{id}', async () => {
    assert.match(src, /request\.get\(`\/api\/projects\/\$\{id\}`\)/)
  })

  await test('POST /api/projects/{id}/members', async () => {
    assert.match(src, /request\.post\(`\/api\/projects\/\$\{projectId\}\/members`, data\)/)
  })

  await test('不残留未实现的 /iot 旧端点改写', async () => {
    assert.doesNotMatch(src, /\/api\/iot\/projects/)
  })
})

// ========== 工作台 projects 区域（§7.1） ==========
await group('GET /api/workbench/overview projects 区域 READY', async () => {
  await test('state=READY 且结构符合 §7.1', async () => {
    const data = await workbenchApi.fetchWorkbenchOverview()
    const region = data.projects
    assert.ok(region, 'overview 含 projects 区域')
    assert.equal(region.state, 'READY', '第 3 周起 projects 区域为 READY')
    assert.equal(typeof region.total, 'number')
    assert.equal(typeof region.active, 'number')
    assert.ok(Array.isArray(region.list))
    assert.ok(region.list.length > 0, 'mock 下应返回参与项目')
  })

  await test('区域列表项字段为摘要结构（无 members）', async () => {
    const data = await workbenchApi.fetchWorkbenchOverview()
    for (const item of data.projects.list) {
      assert.equal(typeof item.id, 'number')
      assert.equal(typeof item.code, 'string')
      assert.equal(typeof item.name, 'string')
      assert.ok(PROJECT_STATUSES.includes(item.status))
      assert.ok(PROJECT_ROLES.includes(item.myRole))
      assert.ok(item.coverUrl === null || typeof item.coverUrl === 'string')
      assert.ok(!Number.isNaN(Date.parse(item.updateTime)))
      assert.equal(item.members, undefined, '摘要不含 members 列表')
    }
  })
})

// ========== 路由与菜单注册（静态检查） ==========
await group('路由与菜单注册', async () => {
  const routerSrc = readFileSync(new URL('../../router/index.js', import.meta.url), 'utf8')
  const layoutSrc = readFileSync(new URL('../../layout/Index.vue', import.meta.url), 'utf8')
  const dashboardSrc = readFileSync(new URL('../../views/Dashboard.vue', import.meta.url), 'utf8')

  await test('/projects 列表路由已注册', async () => {
    assert.match(routerSrc, /path:\s*'projects'/)
    assert.match(routerSrc, /name:\s*'ProjectList'/)
  })

  await test('/projects/:id 详情路由已注册', async () => {
    assert.match(routerSrc, /path:\s*'projects\/:id'/)
    assert.match(routerSrc, /name:\s*'ProjectDetail'/)
  })

  await test('旧 /iot/projects 路径保留', async () => {
    assert.match(routerSrc, /path:\s*'iot\/projects'/)
  })

  await test('侧栏菜单包含项目工作台入口', async () => {
    assert.match(layoutSrc, /\/projects/)
    assert.match(layoutSrc, /项目工作台/)
  })

  await test('工作台点击项目卡片跳转 /projects/:id', async () => {
    assert.match(dashboardSrc, /\/projects\/\$\{item\.id\}/)
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
