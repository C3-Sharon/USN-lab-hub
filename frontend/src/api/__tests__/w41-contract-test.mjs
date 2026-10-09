/**
 * W41 契约测试：学习路线 / 阶段 / 单元 / 成员学习 / 首页 learning 区域
 *
 * 契约来源：docs/contracts/LEARNING_EXPERIMENT_API.md v1.0（FROZEN）
 *           docs/weekly/2026-W41.md §5
 *
 * 覆盖：
 *   - ROADMAP_STATUS / LEARNING_DIFFICULTY / LEARNING_STATUS 枚举与中文映射完整性
 *   - 路线状态机（§2.2）
 *   - learning.js 14 个端点路径与契约一致（§11）
 *   - listRoadmaps 可见性/筛选/分页（§2.5）
 *   - getRoadmapDetail / listStages（§2.6 / §3.3）
 *   - enroll / complete / cancel 幂等 + 进度计算（§5.3~5.6）
 *   - my-roadmaps（§5.7）
 *   - 路线/阶段/单元 CRUD 权限与错误码（§2.4 / §2.7 / §2.8 / §7）
 *   - workbench learning 区域 READY 结构（§8）
 *   - apiError.js 学习错误码归一化（§7）
 *   - 路由 / 侧栏 / 列表页 / 详情页 / 首页 learning 区域静态检查（§9）
 *
 * 运行：node src/api/__tests__/w41-contract-test.mjs
 *
 * 不引入 jest / vitest；保持与 w40-contract-test.mjs 一致的
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
  const tmpPath = path.join(here, `.tmp-w41-${Date.now()}-${Math.random().toString(36).slice(2)}.mjs`)
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
const USER_STUB = `const userStore = { userInfo: { id: 2 }, todayAttendance: null }
const currentRoles = () => globalThis.__W41_ROLES || ['MEMBER']`
const ROLE_STUB = `const ROLE = { SYSTEM_ADMIN: 'SYSTEM_ADMIN', TEACHER: 'TEACHER', STOCK_KEEPER: 'STOCK_KEEPER', MEMBER: 'MEMBER' }`

// ========== 加载被测模块 ==========
const projectsLoaded = await loadModule('../projects.js', {
  "import request from '@/utils/request'": REQUEST_STUB,
  "import { getMockEnabled } from '@/utils/mock'": MOCK_STUB
})

const milestonesLoaded = await loadModule('../milestones.js', {
  "import request from '@/utils/request'": REQUEST_STUB,
  "import { getMockEnabled } from '@/utils/mock'": MOCK_STUB,
  "import { findMockProject } from '@/api/projects'": `import { findMockProject } from '${projectsLoaded.url}'`
})

const tasksLoaded = await loadModule('../tasks.js', {
  "import request from '@/utils/request'": REQUEST_STUB,
  "import { getMockEnabled } from '@/utils/mock'": MOCK_STUB,
  "import { userStore } from '@/store/user'": USER_STUB,
  "import { findMockProject, getMockProjectMembers } from '@/api/projects'": `import { findMockProject, getMockProjectMembers } from '${projectsLoaded.url}'`,
  "import { findMockMilestone, recomputeMilestoneStats } from '@/api/milestones'": `import { findMockMilestone, recomputeMilestoneStats } from '${milestonesLoaded.url}'`
})

const learningLoaded = await loadModule('../learning.js', {
  "import request from '@/utils/request'": REQUEST_STUB,
  "import { getMockEnabled } from '@/utils/mock'": MOCK_STUB,
  "import { userStore, currentRoles } from '@/store/user'": USER_STUB,
  "import { ROLE } from '@/utils/permission'": ROLE_STUB
})

const workbenchLoaded = await loadModule('../workbench.js', {
  "import request from '@/utils/request'": REQUEST_STUB,
  "import { getMockEnabled } from '@/utils/mock'": MOCK_STUB,
  "import { userStore } from '@/store/user'": USER_STUB,
  "import { buildMockWorkbenchTasks } from '@/api/tasks'": `import { buildMockWorkbenchTasks } from '${tasksLoaded.url}'`,
  "import { buildMockWorkbenchLearning } from '@/api/learning'": `import { buildMockWorkbenchLearning } from '${learningLoaded.url}'`
})

const apiError = (await loadModule('../../utils/apiError.js', {
  "import { ElMessage, ElMessageBox } from 'element-plus'": `const ElMessage = { error() {} }; const ElMessageBox = { confirm() { return Promise.resolve() } }`
})).mod

const learning = learningLoaded.mod
const workbench = workbenchLoaded.mod

const {
  ROADMAP_STATUS,
  ROADMAP_STATUS_META,
  LEARNING_DIFFICULTY,
  LEARNING_DIFFICULTY_META,
  LEARNING_STATUS,
  LEARNING_STATUS_META,
  ROADMAP_TRANSITIONS,
  canTransitionRoadmap,
  isTerminalRoadmapStatus
} = learning

// ========== 形状断言 ==========
const ROADMAP_STATUSES = ['DRAFT', 'PUBLISHED', 'ARCHIVED']
const DIFFICULTIES = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED']
const LEARNING_STATUSES = ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED']

function assertErrorShape(err, code, reason) {
  assert.equal(err.code, code, `错误 code=${code}`)
  assert.equal(typeof err.msg, 'string', 'msg 为 string')
  assert.equal(err.reason, reason, `reason=${reason}`)
}

function assertRoadmapSummary(r) {
  assert.equal(typeof r.id, 'number', 'id 为 number')
  assert.equal(typeof r.title, 'string', 'title 为 string')
  assert.ok(ROADMAP_STATUSES.includes(r.status), `status 合法，实际 ${r.status}`)
  assert.ok(DIFFICULTIES.includes(r.difficulty), `difficulty 合法，实际 ${r.difficulty}`)
  assert.equal(typeof r.sortOrder, 'number', 'sortOrder 为 number')
  assert.equal(typeof r.stageCount, 'number', 'stageCount 为 number')
  assert.equal(typeof r.learnerCount, 'number', 'learnerCount 为 number')
  assert.ok(!Number.isNaN(Date.parse(r.createTime)), 'createTime 可解析')
  assert.ok(!Number.isNaN(Date.parse(r.updateTime)), 'updateTime 可解析')
}

function assertUnitShape(u) {
  assert.equal(typeof u.id, 'number', 'unit.id 为 number')
  assert.equal(typeof u.stageId, 'number', 'unit.stageId 为 number')
  assert.equal(typeof u.title, 'string', 'unit.title 为 string')
  assert.equal(typeof u.sortOrder, 'number', 'unit.sortOrder 为 number')
  assert.equal(typeof u.completed, 'boolean', 'unit.completed 为 boolean')
  assert.equal(u.templateId, null, '本周 templateId 为 null')
}

// ========== 枚举与映射（§2.2 / §5.2） ==========
await group('枚举与中文映射（契约 §2.2 / §5.2）', async () => {
  await test('路线状态三态齐全', async () => {
    assert.deepEqual(Object.values(ROADMAP_STATUS).sort(), [...ROADMAP_STATUSES].sort())
  })

  await test('难度三档齐全', async () => {
    assert.deepEqual(Object.values(LEARNING_DIFFICULTY).sort(), [...DIFFICULTIES].sort())
  })

  await test('学习状态三态齐全', async () => {
    assert.deepEqual(Object.values(LEARNING_STATUS).sort(), [...LEARNING_STATUSES].sort())
  })

  await test('路线状态 META 覆盖全部且含 label/tagType', async () => {
    for (const s of ROADMAP_STATUSES) {
      assert.ok(ROADMAP_STATUS_META[s], `缺少 ${s} 的 META`)
      assert.equal(typeof ROADMAP_STATUS_META[s].label, 'string')
      assert.equal(typeof ROADMAP_STATUS_META[s].tagType, 'string')
    }
  })

  await test('难度 META 覆盖全部且含 label/tone（紫色系识别）', async () => {
    for (const d of DIFFICULTIES) {
      assert.ok(LEARNING_DIFFICULTY_META[d], `缺少 ${d} 的 META`)
      assert.equal(typeof LEARNING_DIFFICULTY_META[d].label, 'string')
      assert.equal(typeof LEARNING_DIFFICULTY_META[d].tone, 'string')
    }
  })

  await test('学习状态 META 覆盖全部且含 label/tagType', async () => {
    for (const s of LEARNING_STATUSES) {
      assert.ok(LEARNING_STATUS_META[s], `缺少 ${s} 的 META`)
      assert.equal(typeof LEARNING_STATUS_META[s].label, 'string')
      assert.equal(typeof LEARNING_STATUS_META[s].tagType, 'string')
    }
  })
})

// ========== 路线状态机（§2.2） ==========
await group('路线状态机（契约 §2.2）', async () => {
  await test('转换矩阵与 §2.2 完全一致', async () => {
    assert.deepEqual(ROADMAP_TRANSITIONS[ROADMAP_STATUS.DRAFT], ['PUBLISHED', 'ARCHIVED'])
    assert.deepEqual(ROADMAP_TRANSITIONS[ROADMAP_STATUS.PUBLISHED], ['ARCHIVED'])
    assert.deepEqual(ROADMAP_TRANSITIONS[ROADMAP_STATUS.ARCHIVED], [])
  })

  await test('ARCHIVED 为终态', async () => {
    assert.equal(isTerminalRoadmapStatus('ARCHIVED'), true)
    assert.equal(isTerminalRoadmapStatus('DRAFT'), false)
    assert.equal(isTerminalRoadmapStatus('PUBLISHED'), false)
  })

  await test('canTransitionRoadmap 合法/非法判定', async () => {
    assert.equal(canTransitionRoadmap('DRAFT', 'PUBLISHED'), true)
    assert.equal(canTransitionRoadmap('DRAFT', 'ARCHIVED'), true)
    assert.equal(canTransitionRoadmap('PUBLISHED', 'ARCHIVED'), true)
    assert.equal(canTransitionRoadmap('PUBLISHED', 'DRAFT'), false)
    assert.equal(canTransitionRoadmap('ARCHIVED', 'PUBLISHED'), false)
  })
})

// ========== 端点路径静态检查（§11） ==========
await group('14 个端点路径与契约一致（§11）', async () => {
  const src = readFileSync(new URL('../learning.js', import.meta.url), 'utf8')

  await test('POST /api/learning/roadmaps 创建路线', async () => {
    assert.match(src, /request\.post\('\/api\/learning\/roadmaps', data\)/)
  })
  await test('GET /api/learning/roadmaps 路线列表', async () => {
    assert.match(src, /request\.get\('\/api\/learning\/roadmaps', \{ params \}\)/)
  })
  await test('GET /api/learning/roadmaps/{id} 路线详情', async () => {
    assert.match(src, /request\.get\(`\/api\/learning\/roadmaps\/\$\{roadmapId\}`\)/)
  })
  await test('PUT /api/learning/roadmaps/{id} 编辑路线', async () => {
    assert.match(src, /request\.put\(`\/api\/learning\/roadmaps\/\$\{roadmapId\}`, data\)/)
  })
  await test('PUT /api/learning/roadmaps/{id}/status 更新状态', async () => {
    assert.match(src, /request\.put\(`\/api\/learning\/roadmaps\/\$\{roadmapId\}\/status`, payload\)/)
  })
  await test('POST /api/learning/roadmaps/{id}/stages 创建阶段', async () => {
    assert.match(src, /request\.post\(`\/api\/learning\/roadmaps\/\$\{roadmapId\}\/stages`, data\)/)
  })
  await test('GET /api/learning/roadmaps/{id}/stages 阶段列表', async () => {
    assert.match(src, /request\.get\(`\/api\/learning\/roadmaps\/\$\{roadmapId\}\/stages`\)/)
  })
  await test('PUT /api/learning/stages/{id} 编辑阶段', async () => {
    assert.match(src, /request\.put\(`\/api\/learning\/stages\/\$\{stageId\}`, data\)/)
  })
  await test('POST /api/learning/stages/{id}/units 创建单元', async () => {
    assert.match(src, /request\.post\(`\/api\/learning\/stages\/\$\{stageId\}\/units`, data\)/)
  })
  await test('PUT /api/learning/units/{id} 编辑单元', async () => {
    assert.match(src, /request\.put\(`\/api\/learning\/units\/\$\{unitId\}`, data\)/)
  })
  await test('POST /api/learning/roadmaps/{id}/enroll 开始学习', async () => {
    assert.match(src, /request\.post\(`\/api\/learning\/roadmaps\/\$\{roadmapId\}\/enroll`\)/)
  })
  await test('POST /api/learning/units/{id}/complete 标记完成', async () => {
    assert.match(src, /request\.post\(`\/api\/learning\/units\/\$\{unitId\}\/complete`\)/)
  })
  await test('DELETE /api/learning/units/{id}/complete 取消完成', async () => {
    assert.match(src, /request\.delete\(`\/api\/learning\/units\/\$\{unitId\}\/complete`\)/)
  })
  await test('GET /api/learning/my-roadmaps 我的路线', async () => {
    assert.match(src, /request\.get\('\/api\/learning\/my-roadmaps', \{ params \}\)/)
  })
})

// ========== listRoadmaps（§2.5） ==========
await group('GET /api/learning/roadmaps 路线列表', async () => {
  await test('默认分页结构 + 成员仅见 PUBLISHED', async () => {
    globalThis.__W41_ROLES = ['MEMBER']
    const page = await learning.listRoadmaps()
    assert.equal(typeof page.total, 'number')
    assert.equal(typeof page.page, 'number')
    assert.equal(typeof page.pageSize, 'number')
    assert.ok(Array.isArray(page.list))
    assert.ok(page.list.length > 0, 'mock 预置 PUBLISHED 路线')
    for (const r of page.list) {
      assertRoadmapSummary(r)
      assert.equal(r.status, 'PUBLISHED', '成员默认仅见已发布路线')
    }
  })

  await test('成员请求 status=DRAFT 不可见未发布路线', async () => {
    globalThis.__W41_ROLES = ['MEMBER']
    const page = await learning.listRoadmaps({ status: 'DRAFT' })
    assert.equal(page.list.length, 0, '成员不可见草稿')
  })

  await test('管理员可见 DRAFT 路线', async () => {
    globalThis.__W41_ROLES = ['SYSTEM_ADMIN']
    const page = await learning.listRoadmaps({ status: 'DRAFT' })
    assert.ok(page.list.length > 0, '管理员可见草稿')
    for (const r of page.list) assert.equal(r.status, 'DRAFT')
    globalThis.__W41_ROLES = ['MEMBER']
  })

  await test('difficulty 过滤', async () => {
    globalThis.__W41_ROLES = ['MEMBER']
    const page = await learning.listRoadmaps({ difficulty: 'BEGINNER' })
    assert.ok(page.list.length > 0)
    for (const r of page.list) assert.equal(r.difficulty, 'BEGINNER')
  })

  await test('默认按 sortOrder 升序', async () => {
    const page = await learning.listRoadmaps()
    for (let i = 1; i < page.list.length; i++) {
      assert.ok(page.list[i - 1].sortOrder <= page.list[i].sortOrder, '应按 sortOrder 升序')
    }
  })
})

// ========== 详情与阶段（§2.6 / §3.3） ==========
await group('路线详情与阶段列表', async () => {
  await test('getRoadmapDetail 含阶段统计（无 units 明细）', async () => {
    globalThis.__W41_ROLES = ['MEMBER']
    const detail = await learning.getRoadmapDetail(1)
    assertRoadmapSummary(detail)
    assert.ok(Array.isArray(detail.stages), 'stages 为数组')
    assert.ok(detail.stages.length > 0)
    for (const s of detail.stages) {
      assert.equal(typeof s.id, 'number')
      assert.equal(typeof s.name, 'string')
      assert.equal(typeof s.unitCount, 'number')
      assert.equal(s.units, undefined, '详情阶段不含 units 明细')
    }
  })

  await test('listStages 含单元且带 completed 布尔', async () => {
    const stages = await learning.listStages(1)
    assert.ok(stages.length > 0)
    const units = stages.flatMap((s) => s.units)
    assert.ok(units.length > 0)
    for (const u of units) assertUnitShape(u)
  })

  await test('路线不存在：404 LEARNING_ROADMAP_NOT_FOUND', async () => {
    await assert.rejects(
      () => learning.getRoadmapDetail(999999),
      (err) => { assertErrorShape(err, 404, 'LEARNING_ROADMAP_NOT_FOUND'); return true }
    )
  })

  await test('成员访问 DRAFT 路线：404（不暴露存在性）', async () => {
    globalThis.__W41_ROLES = ['MEMBER']
    await assert.rejects(
      () => learning.getRoadmapDetail(3),
      (err) => { assertErrorShape(err, 404, 'LEARNING_ROADMAP_NOT_FOUND'); return true }
    )
  })
})

// ========== 成员学习（§5.3~5.7） ==========
await group('成员学习：开始 / 完成 / 取消 / 进度', async () => {
  await test('enroll 幂等：重复返回同一记录，不重复创建', async () => {
    globalThis.__W41_ROLES = ['MEMBER']
    const first = await learning.enrollRoadmap(1)
    const second = await learning.enrollRoadmap(1)
    assert.equal(first.id, second.id, '重复开始返回同一学习记录')
    assert.equal(typeof first.progress, 'number')
    assert.equal(typeof first.totalUnitCount, 'number')
    assert.equal(first.roadmapId, 1)
  })

  await test('enroll 未发布路线：404 LEARNING_ROADMAP_NOT_FOUND', async () => {
    await assert.rejects(
      () => learning.enrollRoadmap(3),
      (err) => { assertErrorShape(err, 404, 'LEARNING_ROADMAP_NOT_FOUND'); return true }
    )
  })

  await test('enroll 已归档路线：409 LEARNING_ARCHIVED', async () => {
    await assert.rejects(
      () => learning.enrollRoadmap(4),
      (err) => { assertErrorShape(err, 409, 'LEARNING_ARCHIVED'); return true }
    )
  })

  await test('complete 未开始路线：400 LEARNING_NOT_ENROLLED', async () => {
    // 单元 301 属于 DRAFT 路线 3，用户 2 未开始
    globalThis.__W41_ROLES = ['SYSTEM_ADMIN']
    const notEnrolled = await learning.completeUnit(301).then(
      () => null,
      (err) => err
    )
    // 管理员同样未开始该路线，应命中 NOT_ENROLLED（路线 3 非归档）
    assert.ok(notEnrolled, '应拒绝')
    assertErrorShape(notEnrolled, 400, 'LEARNING_NOT_ENROLLED')
    globalThis.__W41_ROLES = ['MEMBER']
  })

  await test('complete 成功：进度 = floor(completed/total*100)', async () => {
    const res = await learning.completeUnit(106)
    assert.equal(res.completed, true)
    assert.equal(res.unitId, 106)
    const rp = res.roadmapProgress
    assert.equal(rp.progress, Math.floor((rp.completedUnitCount / rp.totalUnitCount) * 100))
  })

  await test('complete 幂等：重复标记不改变进度', async () => {
    const first = await learning.completeUnit(106)
    const second = await learning.completeUnit(106)
    assert.equal(first.roadmapProgress.completedUnitCount, second.roadmapProgress.completedUnitCount)
    assert.equal(first.roadmapProgress.progress, second.roadmapProgress.progress)
  })

  await test('cancel 后进度回退', async () => {
    const res = await learning.cancelUnitComplete(106)
    assert.equal(res.completed, false)
    const rp = res.roadmapProgress
    assert.equal(rp.progress, Math.floor((rp.completedUnitCount / rp.totalUnitCount) * 100))
  })

  await test('complete 单元不存在：404 LEARNING_UNIT_NOT_FOUND', async () => {
    await assert.rejects(
      () => learning.completeUnit(999999),
      (err) => { assertErrorShape(err, 404, 'LEARNING_UNIT_NOT_FOUND'); return true }
    )
  })

  await test('listStages 反映当前用户完成态', async () => {
    const stages = await learning.listStages(1)
    const unit101 = stages.flatMap((s) => s.units).find((u) => u.id === 101)
    assert.equal(unit101.completed, true, '用户 2 已完成单元 101')
  })

  await test('my-roadmaps 结构 + 按 updateTime 降序', async () => {
    const page = await learning.listMyRoadmaps()
    assert.equal(typeof page.total, 'number')
    assert.ok(page.list.length > 0)
    for (const item of page.list) {
      assert.equal(typeof item.roadmapId, 'number')
      assert.equal(typeof item.roadmapTitle, 'string')
      assert.ok(DIFFICULTIES.includes(item.roadmapDifficulty))
      assert.ok(LEARNING_STATUSES.includes(item.status))
      assert.equal(typeof item.progress, 'number')
      assert.equal(typeof item.completedUnitCount, 'number')
      assert.equal(typeof item.totalUnitCount, 'number')
    }
    for (let i = 1; i < page.list.length; i++) {
      assert.ok(Date.parse(page.list[i - 1].updateTime) >= Date.parse(page.list[i].updateTime), '应按 updateTime 降序')
    }
  })
})

// ========== 路线/阶段/单元 CRUD 与权限（§2.4 / §2.7 / §2.8 / §7） ==========
await group('路线 CRUD、状态转换与权限', async () => {
  await test('非管理员创建路线：403 LEARNING_OPERATION_DENIED', async () => {
    globalThis.__W41_ROLES = ['MEMBER']
    await assert.rejects(
      () => learning.createRoadmap({ title: '越权路线' }),
      (err) => { assertErrorShape(err, 403, 'LEARNING_OPERATION_DENIED'); return true }
    )
  })

  await test('标题过短：400 INVALID_PARAMETER', async () => {
    globalThis.__W41_ROLES = ['SYSTEM_ADMIN']
    await assert.rejects(
      () => learning.createRoadmap({ title: 'x' }),
      (err) => { assertErrorShape(err, 400, 'INVALID_PARAMETER'); return true }
    )
  })

  let createdId = null

  await test('管理员创建路线：初始 DRAFT', async () => {
    const created = await learning.createRoadmap({
      title: '契约测试学习路线',
      description: 'W41 契约测试',
      difficulty: 'BEGINNER',
      estimatedHours: 10,
      sortOrder: 9
    })
    assertRoadmapSummary(created)
    assert.equal(created.status, 'DRAFT')
    assert.equal(created.difficulty, 'BEGINNER')
    createdId = created.id
  })

  await test('创建阶段与单元，完成全流程后置为 COMPLETED', async () => {
    const stage = await learning.createStage(createdId, { name: '测试阶段', description: '契约测试阶段', sortOrder: 1 })
    assert.equal(stage.unitCount, 0)
    const u1 = await learning.createUnit(stage.id, { title: '测试单元一', description: 'A', sortOrder: 1 })
    const u2 = await learning.createUnit(stage.id, { title: '测试单元二', description: 'B', sortOrder: 2 })
    assertUnitShape(u1)
    assertUnitShape(u2)

    // 发布后才能开始学习
    await learning.updateRoadmapStatus(createdId, { status: 'PUBLISHED' })
    globalThis.__W41_ROLES = ['MEMBER']
    const enrolled = await learning.enrollRoadmap(createdId)
    assert.equal(enrolled.status, 'NOT_STARTED')
    assert.equal(enrolled.totalUnitCount, 2)

    const r1 = await learning.completeUnit(u1.id)
    assert.equal(r1.roadmapProgress.status, 'IN_PROGRESS')
    assert.equal(r1.roadmapProgress.progress, 50)

    const r2 = await learning.completeUnit(u2.id)
    assert.equal(r2.roadmapProgress.status, 'COMPLETED')
    assert.equal(r2.roadmapProgress.progress, 100)

    const my = await learning.listMyRoadmaps()
    const record = my.list.find((i) => i.roadmapId === createdId)
    assert.equal(record.status, 'COMPLETED')
    assert.ok(record.completedAt, '全部完成写入 completedAt')
  })

  await test('COMPLETED 后取消单元：回退 IN_PROGRESS 并清空 completedAt', async () => {
    const stages = await learning.listStages(createdId)
    const unit = stages[0].units[0]
    const res = await learning.cancelUnitComplete(unit.id)
    assert.equal(res.roadmapProgress.status, 'IN_PROGRESS')
    const my = await learning.listMyRoadmaps()
    const record = my.list.find((i) => i.roadmapId === createdId)
    assert.equal(record.status, 'IN_PROGRESS')
    assert.equal(record.completedAt, null, '取消后清空 completedAt')
  })

  await test('非法状态转换：409 LEARNING_INVALID_TRANSITION', async () => {
    globalThis.__W41_ROLES = ['SYSTEM_ADMIN']
    await assert.rejects(
      () => learning.updateRoadmapStatus(createdId, { status: 'DRAFT' }),
      (err) => { assertErrorShape(err, 409, 'LEARNING_INVALID_TRANSITION'); return true }
    )
  })

  await test('ARCHIVED 后不可编辑：409 LEARNING_ARCHIVED', async () => {
    await learning.updateRoadmapStatus(createdId, { status: 'ARCHIVED' })
    await assert.rejects(
      () => learning.updateRoadmap(createdId, { title: '归档后编辑' }),
      (err) => { assertErrorShape(err, 409, 'LEARNING_ARCHIVED'); return true }
    )
    await assert.rejects(
      () => learning.createStage(createdId, { name: '归档后阶段' }),
      (err) => { assertErrorShape(err, 409, 'LEARNING_ARCHIVED'); return true }
    )
    globalThis.__W41_ROLES = ['MEMBER']
  })
})

// ========== 首页 learning 区域（§8） ==========
await group('GET /api/workbench/overview learning 区域 READY', async () => {
  await test('state=READY 且统计字段齐全', async () => {
    globalThis.__W41_ROLES = ['MEMBER']
    const data = await workbench.fetchWorkbenchOverview()
    const region = data.learning
    assert.ok(region, 'overview 含 learning 区域')
    assert.equal(region.state, 'READY', '第 5 周起 learning 区域为 READY')
    assert.equal(typeof region.inProgressCount, 'number')
    assert.equal(typeof region.completedCount, 'number')
    assert.ok(Array.isArray(region.list))
  })

  await test('list 为最近路线，最多 3 条，按 updateTime 降序', async () => {
    const data = await workbench.fetchWorkbenchOverview()
    const list = data.learning.list
    assert.ok(list.length <= 3, '最近学习路线最多 3 条')
    for (let i = 1; i < list.length; i++) {
      assert.ok(Date.parse(list[i - 1].updateTime) >= Date.parse(list[i].updateTime), '应按 updateTime 降序')
    }
  })

  await test('list 项字段符合 §8.1 摘要结构', async () => {
    const data = await workbench.fetchWorkbenchOverview()
    for (const item of data.learning.list) {
      assert.equal(typeof item.roadmapId, 'number')
      assert.equal(typeof item.roadmapTitle, 'string')
      assert.ok(DIFFICULTIES.includes(item.roadmapDifficulty))
      assert.ok(LEARNING_STATUSES.includes(item.status))
      assert.equal(typeof item.progress, 'number')
      assert.equal(typeof item.completedUnitCount, 'number')
      assert.equal(typeof item.totalUnitCount, 'number')
    }
  })

  await test('buildMockWorkbenchLearning 与契约结构一致', async () => {
    const region = learning.buildMockWorkbenchLearning()
    assert.equal(region.state, 'READY')
    assert.ok(region.list.length <= 3)
    assert.equal(typeof region.inProgressCount, 'number')
    assert.equal(typeof region.completedCount, 'number')
  })
})

// ========== apiError.js 学习错误码（§7） ==========
await group('学习错误码归一化（契约 §7）', async () => {
  await test('isLearningNotFound 识别 404 与三类 reason', async () => {
    assert.equal(apiError.isLearningNotFound({ code: 404 }), true)
    assert.equal(apiError.isLearningNotFound({ reason: 'LEARNING_ROADMAP_NOT_FOUND' }), true)
    assert.equal(apiError.isLearningNotFound({ reason: 'LEARNING_STAGE_NOT_FOUND' }), true)
    assert.equal(apiError.isLearningNotFound({ reason: 'LEARNING_UNIT_NOT_FOUND' }), true)
    assert.equal(apiError.isLearningNotFound({ reason: 'LEARNING_ARCHIVED' }), false)
  })

  await test('isLearningArchived / isLearningNotEnrolled / isLearningEmptyRoadmap / isLearningInvalidTransition', async () => {
    assert.equal(apiError.isLearningArchived({ reason: 'LEARNING_ARCHIVED' }), true)
    assert.equal(apiError.isLearningNotEnrolled({ reason: 'LEARNING_NOT_ENROLLED' }), true)
    assert.equal(apiError.isLearningEmptyRoadmap({ reason: 'LEARNING_EMPTY_ROADMAP' }), true)
    assert.equal(apiError.isLearningInvalidTransition({ reason: 'LEARNING_INVALID_TRANSITION' }), true)
  })

  await test('isLearningDenied 识别 403 与两类 reason', async () => {
    assert.equal(apiError.isLearningDenied({ code: 403 }), true)
    assert.equal(apiError.isLearningDenied({ reason: 'LEARNING_OPERATION_DENIED' }), true)
    assert.equal(apiError.isLearningDenied({ reason: 'ACCESS_DENIED' }), true)
    assert.equal(apiError.isLearningDenied({ code: 200 }), false)
  })
})

// ========== 页面结构静态检查（§9） ==========
await group('路由 / 侧栏 / 页面 / 首页区域（§9）', async () => {
  const routerSrc = readFileSync(new URL('../../router/index.js', import.meta.url), 'utf8')
  const layoutSrc = readFileSync(new URL('../../layout/Index.vue', import.meta.url), 'utf8')
  const listSrc = readFileSync(new URL('../../views/learning/LearningList.vue', import.meta.url), 'utf8')
  const detailSrc = readFileSync(new URL('../../views/learning/LearningDetail.vue', import.meta.url), 'utf8')
  const dashboardSrc = readFileSync(new URL('../../views/Dashboard.vue', import.meta.url), 'utf8')
  const workbenchSrc = readFileSync(new URL('../workbench.js', import.meta.url), 'utf8')

  await test('路由 /learning 与 /learning/:id', async () => {
    assert.match(routerSrc, /path: 'learning',/)
    assert.match(routerSrc, /path: 'learning\/:id',/)
    assert.match(routerSrc, /component: LearningList/)
    assert.match(routerSrc, /component: LearningDetail/)
  })

  await test('侧栏一级入口「学习实验台」使用 Reading 图标', async () => {
    assert.match(layoutSrc, /index: '\/learning',/)
    assert.match(layoutSrc, /title: '学习实验台'/)
    assert.match(layoutSrc, /Reading/)
  })

  await test('列表页：卡片 + 难度标签 + 进度条 + 空状态', async () => {
    assert.match(listSrc, /roadmap-card/)
    assert.match(listSrc, /DifficultyTag/)
    assert.match(listSrc, /ProgressBar/)
    assert.match(listSrc, /state="empty"/)
    assert.match(listSrc, /grid-template-columns: repeat\(3, 1fr\)/)
  })

  await test('详情页：时间线 + 单元勾选 + 进度条', async () => {
    assert.match(detailSrc, /roadmap-timeline/)
    assert.match(detailSrc, /el-checkbox/)
    assert.match(detailSrc, /ProgressBar/)
    assert.match(detailSrc, /toggleUnit/)
  })

  await test('首页 learning 区域渲染统计与最近路线', async () => {
    assert.match(dashboardSrc, /learningSummary/)
    assert.match(dashboardSrc, /learningList/)
    assert.match(dashboardSrc, /\/learning\/\$\{item\.roadmapId\}/)
  })

  await test('工作台 mock learning 区域走 buildMockWorkbenchLearning', async () => {
    assert.match(workbenchSrc, /learning: buildMockWorkbenchLearning\(\)/)
    assert.doesNotMatch(workbenchSrc, /learning: notAvailable\(\)/)
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