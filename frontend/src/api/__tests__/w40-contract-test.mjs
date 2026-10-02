/**
 * W40 契约测试：里程碑 / 任务 / 乐观锁 / 首页 tasks 区域
 *
 * 契约来源：docs/contracts/PROJECT_WORKSPACE_API.md §8~§13（FROZEN）
 *           docs/weekly/2026-W40.md §5
 *
 * 覆盖：
 *   - TASK_STATUS / TASK_PRIORITY / MILESTONE_STATUS 枚举与中文映射完整性
 *   - 任务状态机（§9.3）与里程碑单向状态机（§8.2）
 *   - tasks.js 五个端点、milestones.js 三个端点路径与契约一致
 *   - listTasks 分页/筛选/排序（§9.5）
 *   - getTaskDetail 404（§9.7）
 *   - createTask 成功 + 400/403/404（§9.4）
 *   - updateTaskStatus 合法转换 / 非法转换 / VERSION_CONFLICT / BLOCKED 校验（§9.6）
 *   - updateTask 乐观锁 + 角色限制（§9.8）
 *   - projectPermission.js 权限矩阵（§13）
 *   - apiError.js 错误归一化（§6 / §10）
 *   - workbench tasks 区域 READY 结构与最近 5 条（§11）
 *   - 项目详情页 Tab / 看板 / 首页 tasks 区域静态检查（§12）
 *
 * 运行：node src/api/__tests__/w40-contract-test.mjs
 *
 * 不引入 jest / vitest；保持与 w39-contract-test.mjs 一致的
 * "纯 Node + assert" 风格：读取源码替换 @/ 别名为桩或临时模块绝对路径后动态 import，
 * 对真实 mock 实现做动态契约校验。
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
 * 把使用了 @/ 别名的模块转为可在 Node 中直接 import 的临时模块。
 * 返回 { mod, url }，url 可被后续模块作为 import 目标复用。
 */
async function loadModule(srcRelativeUrl, stubs) {
  const srcUrl = new URL(srcRelativeUrl, import.meta.url)
  let code = readFileSync(srcUrl, 'utf8')
  for (const [importStmt, stub] of Object.entries(stubs)) {
    assert.ok(code.includes(importStmt), `源码中应包含 ${importStmt}`)
    code = code.replace(importStmt, stub)
  }
  const tmpPath = path.join(here, `.tmp-w40-${Date.now()}-${Math.random().toString(36).slice(2)}.mjs`)
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
  put() { return Promise.reject(new Error('contract-test: 禁止真实网络')) }
}`
const MOCK_STUB = `const getMockEnabled = () => true`
const USER_STUB = `const userStore = { userInfo: { id: 2 }, todayAttendance: null }`

// ========== 加载被测模块（按依赖顺序串接临时模块路径） ==========
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

const workbenchLoaded = await loadModule('../workbench.js', {
  "import request from '@/utils/request'": REQUEST_STUB,
  "import { getMockEnabled } from '@/utils/mock'": MOCK_STUB,
  "import { userStore } from '@/store/user'": USER_STUB,
  "import { buildMockWorkbenchTasks } from '@/api/tasks'": `import { buildMockWorkbenchTasks } from '${tasksLoaded.url}'`
})

const apiError = (await loadModule('../../utils/apiError.js', {
  "import { ElMessage, ElMessageBox } from 'element-plus'": `const ElMessage = { error() {} }; const ElMessageBox = { confirm() { return Promise.resolve() } }`
})).mod

const permission = (await loadModule('../../utils/projectPermission.js', {
  "import { userStore, currentRoles } from '@/store/user'": `const userStore = { userInfo: { id: 2 } }; const currentRoles = () => []`,
  "import { ROLE } from '@/utils/permission'": `const ROLE = { SYSTEM_ADMIN: 'SYSTEM_ADMIN', TEACHER: 'TEACHER', STOCK_KEEPER: 'STOCK_KEEPER', MEMBER: 'MEMBER' }`,
  "import { PROJECT_ROLE } from '@/api/projects'": `import { PROJECT_ROLE } from '${projectsLoaded.url}'`,
  "import { TASK_STATUS, TASK_TRANSITIONS, MEMBER_TRANSITIONS } from '@/api/tasks'": `import { TASK_STATUS, TASK_TRANSITIONS, MEMBER_TRANSITIONS } from '${tasksLoaded.url}'`
})).mod

const milestonesApi = milestonesLoaded.mod
const tasks = tasksLoaded.mod
const workbench = workbenchLoaded.mod

const {
  TASK_STATUS,
  TASK_STATUS_META,
  TASK_PRIORITY,
  TASK_PRIORITY_META,
  BOARD_STATUSES,
  TASK_TRANSITIONS,
  MEMBER_TRANSITIONS,
  canTransitionTask,
  isTerminalTaskStatus
} = tasks

const { MILESTONE_STATUS, MILESTONE_STATUS_META, canTransitionMilestone, nextMilestoneStatus } = milestonesApi

// ========== 形状断言 ==========
const TASK_STATUSES = ['TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE', 'CANCELED']
const TASK_PRIORITIES = ['LOW', 'MEDIUM', 'HIGH']
const MILESTONE_STATUSES = ['PLANNED', 'IN_PROGRESS', 'COMPLETED']

function assertTaskShape(t) {
  assert.equal(typeof t.id, 'number', 'id 为 number')
  assert.equal(typeof t.projectId, 'number', 'projectId 为 number')
  assert.ok(t.milestoneId === null || typeof t.milestoneId === 'number', 'milestoneId 为 number 或 null')
  assert.equal(typeof t.title, 'string', 'title 为 string')
  assert.ok(t.title.length >= 2 && t.title.length <= 120, 'title 长度 2-120')
  assert.ok(TASK_STATUSES.includes(t.status), `status 属于五态枚举，实际 ${t.status}`)
  assert.ok(t.assigneeUserId === null || typeof t.assigneeUserId === 'number', 'assigneeUserId 为 number 或 null')
  assert.ok(TASK_PRIORITIES.includes(t.priority), `priority 属于三档，实际 ${t.priority}`)
  assert.ok(t.dueDate === null || /^\d{4}-\d{2}-\d{2}$/.test(t.dueDate), 'dueDate 为 YYYY-MM-DD 或 null')
  assert.ok(t.blockReason === null || typeof t.blockReason === 'string', 'blockReason 为 string 或 null')
  assert.equal(typeof t.version, 'number', 'version 为 number')
  assert.ok(!Number.isNaN(Date.parse(t.createTime)), 'createTime 可解析为时间')
  assert.ok(!Number.isNaN(Date.parse(t.updateTime)), 'updateTime 可解析为时间')
}

function assertErrorShape(err, code, reason) {
  assert.equal(err.code, code, `错误 code=${code}`)
  assert.equal(typeof err.msg, 'string', 'msg 为 string')
  assert.equal(err.reason, reason, `reason=${reason}`)
}

// ========== 枚举与映射（§9.2 / §9.1） ==========
await group('枚举与中文映射（契约 §8.2 / §9.1~9.2）', async () => {
  await test('任务状态五态齐全', async () => {
    assert.deepEqual(Object.values(TASK_STATUS).sort(), [...TASK_STATUSES].sort())
  })

  await test('任务优先级三档齐全', async () => {
    assert.deepEqual(Object.values(TASK_PRIORITY).sort(), [...TASK_PRIORITIES].sort())
  })

  await test('看板默认四列，不含 CANCELED', async () => {
    assert.deepEqual([...BOARD_STATUSES], ['TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE'])
    assert.ok(!BOARD_STATUSES.includes('CANCELED'))
  })

  await test('任务状态 META 覆盖全部状态且含 label/tagType', async () => {
    for (const s of TASK_STATUSES) {
      assert.ok(TASK_STATUS_META[s], `缺少 ${s} 的 META`)
      assert.equal(typeof TASK_STATUS_META[s].label, 'string')
      assert.equal(typeof TASK_STATUS_META[s].tagType, 'string')
    }
  })

  await test('优先级 META 覆盖全部档位且含 label/tagType/rank', async () => {
    for (const p of TASK_PRIORITIES) {
      assert.ok(TASK_PRIORITY_META[p], `缺少 ${p} 的 META`)
      assert.equal(typeof TASK_PRIORITY_META[p].label, 'string')
      assert.equal(typeof TASK_PRIORITY_META[p].tagType, 'string')
      assert.equal(typeof TASK_PRIORITY_META[p].rank, 'number')
    }
  })

  await test('里程碑三态齐全且 META 完整', async () => {
    assert.deepEqual(Object.values(MILESTONE_STATUS).sort(), [...MILESTONE_STATUSES].sort())
    for (const s of MILESTONE_STATUSES) {
      assert.ok(MILESTONE_STATUS_META[s], `缺少 ${s} 的 META`)
      assert.equal(typeof MILESTONE_STATUS_META[s].label, 'string')
      assert.equal(typeof MILESTONE_STATUS_META[s].tagType, 'string')
    }
  })
})

// ========== 状态机（§9.3 / §8.2） ==========
await group('状态机（契约 §9.3 / §8.2）', async () => {
  await test('任务状态转换矩阵与 §9.3 完全一致', async () => {
    assert.deepEqual(TASK_TRANSITIONS[TASK_STATUS.TODO], ['IN_PROGRESS', 'CANCELED'])
    assert.deepEqual(TASK_TRANSITIONS[TASK_STATUS.IN_PROGRESS], ['BLOCKED', 'DONE', 'CANCELED'])
    assert.deepEqual(TASK_TRANSITIONS[TASK_STATUS.BLOCKED], ['IN_PROGRESS', 'CANCELED'])
    assert.deepEqual(TASK_TRANSITIONS[TASK_STATUS.DONE], [])
    assert.deepEqual(TASK_TRANSITIONS[TASK_STATUS.CANCELED], [])
  })

  await test('DONE / CANCELED 为终态', async () => {
    assert.equal(isTerminalTaskStatus('DONE'), true)
    assert.equal(isTerminalTaskStatus('CANCELED'), true)
    assert.equal(isTerminalTaskStatus('TODO'), false)
    assert.equal(isTerminalTaskStatus('IN_PROGRESS'), false)
  })

  await test('canTransitionTask 合法/非法判定', async () => {
    assert.equal(canTransitionTask('TODO', 'IN_PROGRESS'), true)
    assert.equal(canTransitionTask('IN_PROGRESS', 'DONE'), true)
    assert.equal(canTransitionTask('BLOCKED', 'IN_PROGRESS'), true)
    assert.equal(canTransitionTask('TODO', 'DONE'), false)
    assert.equal(canTransitionTask('DONE', 'IN_PROGRESS'), false)
    assert.equal(canTransitionTask('CANCELED', 'TODO'), false)
  })

  await test('MEMBER（负责人）转换不含 CANCELED', async () => {
    assert.deepEqual(MEMBER_TRANSITIONS[TASK_STATUS.TODO], ['IN_PROGRESS'])
    assert.deepEqual(MEMBER_TRANSITIONS[TASK_STATUS.IN_PROGRESS], ['BLOCKED', 'DONE'])
    assert.deepEqual(MEMBER_TRANSITIONS[TASK_STATUS.BLOCKED], ['IN_PROGRESS'])
    assert.ok(!MEMBER_TRANSITIONS[TASK_STATUS.TODO].includes('CANCELED'))
    assert.ok(!MEMBER_TRANSITIONS[TASK_STATUS.IN_PROGRESS].includes('CANCELED'))
  })

  await test('里程碑单向状态机 PLANNED→IN_PROGRESS→COMPLETED', async () => {
    assert.equal(canTransitionMilestone('PLANNED', 'IN_PROGRESS'), true)
    assert.equal(canTransitionMilestone('IN_PROGRESS', 'COMPLETED'), true)
    assert.equal(canTransitionMilestone('PLANNED', 'COMPLETED'), false)
    assert.equal(canTransitionMilestone('COMPLETED', 'IN_PROGRESS'), false)
    assert.equal(nextMilestoneStatus('PLANNED'), 'IN_PROGRESS')
    assert.equal(nextMilestoneStatus('IN_PROGRESS'), 'COMPLETED')
    assert.equal(nextMilestoneStatus('COMPLETED'), null)
  })
})

// ========== 端点路径静态检查（§9 / §8 / §6） ==========
await group('端点路径与契约一致', async () => {
  const tasksSrc = readFileSync(new URL('../tasks.js', import.meta.url), 'utf8')
  const milestonesSrc = readFileSync(new URL('../milestones.js', import.meta.url), 'utf8')
  const requestSrc = readFileSync(new URL('../../utils/request.js', import.meta.url), 'utf8')

  await test('POST /api/projects/{id}/tasks 创建任务', async () => {
    assert.match(tasksSrc, /request\.post\(`\/api\/projects\/\$\{projectId\}\/tasks`, data\)/)
  })

  await test('GET /api/projects/{id}/tasks 任务列表', async () => {
    assert.match(tasksSrc, /request\.get\(`\/api\/projects\/\$\{projectId\}\/tasks`, \{ params \}\)/)
  })

  await test('GET /api/projects/{id}/tasks/{tid} 任务详情', async () => {
    assert.match(tasksSrc, /request\.get\(`\/api\/projects\/\$\{projectId\}\/tasks\/\$\{taskId\}`\)/)
  })

  await test('PUT /api/projects/{id}/tasks/{tid} 编辑任务', async () => {
    assert.match(tasksSrc, /request\.put\(`\/api\/projects\/\$\{projectId\}\/tasks\/\$\{taskId\}`, data\)/)
  })

  await test('PUT /api/projects/{id}/tasks/{tid}/status 状态变更', async () => {
    assert.match(tasksSrc, /request\.put\(`\/api\/projects\/\$\{projectId\}\/tasks\/\$\{taskId\}\/status`, payload\)/)
  })

  await test('POST /api/projects/{id}/milestones 创建里程碑', async () => {
    assert.match(milestonesSrc, /request\.post\(`\/api\/projects\/\$\{projectId\}\/milestones`, data\)/)
  })

  await test('GET /api/projects/{id}/milestones 里程碑列表', async () => {
    assert.match(milestonesSrc, /request\.get\(`\/api\/projects\/\$\{projectId\}\/milestones`, \{ params \}\)/)
  })

  await test('PUT /api/projects/{id}/milestones/{mid}/status 里程碑状态', async () => {
    assert.match(milestonesSrc, /request\.put\(`\/api\/projects\/\$\{projectId\}\/milestones\/\$\{milestoneId\}\/status`, \{ status \}\)/)
  })

  await test('拦截器对 VERSION_CONFLICT 静默（交由页面提示）', async () => {
    assert.match(requestSrc, /SILENT_REASONS = new Set\(\['VERSION_CONFLICT'\]\)/)
  })
})

// ========== listTasks（§9.5） ==========
await group('GET /api/projects/{id}/tasks 任务列表', async () => {
  await test('默认分页结构 { total, page, pageSize, list }', async () => {
    const page = await tasks.listTasks(10)
    assert.equal(typeof page.total, 'number')
    assert.equal(typeof page.page, 'number')
    assert.equal(typeof page.pageSize, 'number')
    assert.ok(Array.isArray(page.list))
    assert.equal(page.total, 6, '项目 10 mock 共 6 条任务（含 CANCELED）')
    for (const t of page.list) assertTaskShape(t)
  })

  await test('status=TODO 过滤', async () => {
    const page = await tasks.listTasks(10, { status: 'TODO' })
    assert.ok(page.list.length > 0)
    for (const t of page.list) assert.equal(t.status, 'TODO')
  })

  await test('milestoneId 过滤', async () => {
    const page = await tasks.listTasks(10, { milestoneId: 101 })
    assert.equal(page.list.length, 4)
    for (const t of page.list) assert.equal(t.milestoneId, 101)
  })

  await test('assigneeUserId 过滤（负责人）', async () => {
    const page = await tasks.listTasks(10, { assigneeUserId: 2 })
    assert.equal(page.list.length, 4)
    for (const t of page.list) assert.equal(t.assigneeUserId, 2)
  })

  await test('keyword 模糊搜索标题', async () => {
    const page = await tasks.listTasks(10, { keyword: 'pcb' })
    assert.equal(page.list.length, 1)
    assert.match(page.list[0].title, /PCB/i)
  })

  await test('默认按 createTime 降序（契约默认排序）', async () => {
    const page = await tasks.listTasks(10)
    for (let i = 1; i < page.list.length; i++) {
      assert.ok(
        Date.parse(page.list[i - 1].createTime) >= Date.parse(page.list[i].createTime),
        '默认排序应按 createTime 降序'
      )
    }
  })

  await test('返回冗余字段 milestoneName / assigneeName', async () => {
    const page = await tasks.listTasks(10)
    const withMilestone = page.list.find((t) => t.milestoneId === 101)
    assert.equal(withMilestone.milestoneName, '原型设计与评审')
    const assigned = page.list.find((t) => t.assigneeUserId === 2)
    assert.equal(assigned.assigneeName, '张同学')
  })

  await test('项目不存在：404 PROJECT_NOT_FOUND', async () => {
    await assert.rejects(
      () => tasks.listTasks(999999),
      (err) => { assertErrorShape(err, 404, 'PROJECT_NOT_FOUND'); return true }
    )
  })
})

// ========== getTaskDetail（§9.7） ==========
await group('GET /api/projects/{id}/tasks/{tid} 任务详情', async () => {
  await test('返回完整任务对象 + milestoneName', async () => {
    const detail = await tasks.getTaskDetail(10, 1001)
    assertTaskShape(detail)
    assert.equal(detail.id, 1001)
    assert.equal(detail.milestoneName, '原型设计与评审')
  })

  await test('任务不存在：404 TASK_NOT_FOUND', async () => {
    await assert.rejects(
      () => tasks.getTaskDetail(10, 999999),
      (err) => { assertErrorShape(err, 404, 'TASK_NOT_FOUND'); return true }
    )
  })

  await test('项目不存在：404 PROJECT_NOT_FOUND', async () => {
    await assert.rejects(
      () => tasks.getTaskDetail(999999, 1),
      (err) => { assertErrorShape(err, 404, 'PROJECT_NOT_FOUND'); return true }
    )
  })
})

// ========== 首页 tasks 区域（§11） ==========
await group('GET /api/workbench/overview tasks 区域 READY', async () => {
  await test('state=READY 且统计字段齐全', async () => {
    const data = await workbench.fetchWorkbenchOverview()
    const region = data.tasks
    assert.ok(region, 'overview 含 tasks 区域')
    assert.equal(region.state, 'READY', '第 4 周起 tasks 区域为 READY')
    for (const key of ['todo', 'inProgress', 'blocked', 'doneThisWeek']) {
      assert.equal(typeof region[key], 'number', `${key} 为 number`)
    }
    assert.ok(Array.isArray(region.list))
  })

  await test('list 为最近任务，最多 5 条，按 updateTime 降序', async () => {
    const data = await workbench.fetchWorkbenchOverview()
    const list = data.tasks.list
    assert.ok(list.length <= 5, '最近任务最多 5 条')
    assert.ok(list.length > 0, 'mock 下应有负责人任务')
    for (let i = 1; i < list.length; i++) {
      assert.ok(
        Date.parse(list[i - 1].updateTime) >= Date.parse(list[i].updateTime),
        'list 应按 updateTime 降序'
      )
    }
  })

  await test('list 项字段符合 §11.2 摘要结构', async () => {
    const data = await workbench.fetchWorkbenchOverview()
    for (const item of data.tasks.list) {
      assert.equal(typeof item.id, 'number')
      assert.equal(typeof item.projectId, 'number')
      assert.equal(typeof item.projectCode, 'string')
      assert.equal(typeof item.projectName, 'string')
      assert.ok(TASK_STATUSES.includes(item.status))
      assert.ok(TASK_PRIORITIES.includes(item.priority))
      assert.ok(!Number.isNaN(Date.parse(item.updateTime)))
      assert.equal(item.description, undefined, '摘要不含 description')
    }
  })

  await test('buildMockWorkbenchTasks 统计口径与 list 一致', async () => {
    const region = tasks.buildMockWorkbenchTasks()
    assert.equal(region.state, 'READY')
    assert.equal(typeof region.todo, 'number')
    assert.ok(region.list.length <= 5)
  })
})

// ========== projectPermission.js（§13） ==========
await group('项目内权限判定（契约 §13）', async () => {
  await test('SYSTEM_ADMIN 覆盖为 OWNER，TEACHER 覆盖为 OBSERVER', async () => {
    assert.equal(permission.effectiveProjectRole('MEMBER', ['SYSTEM_ADMIN']), 'OWNER')
    assert.equal(permission.effectiveProjectRole('MEMBER', ['TEACHER']), 'OBSERVER')
    assert.equal(permission.effectiveProjectRole('MEMBER', ['MEMBER']), 'MEMBER')
  })

  await test('OWNER/MAINTAINER 为管理者，MEMBER/OBSERVER 不是', async () => {
    assert.equal(permission.isProjectManager('OWNER'), true)
    assert.equal(permission.isProjectManager('MAINTAINER'), true)
    assert.equal(permission.isProjectManager('MEMBER'), false)
    assert.equal(permission.isProjectManager('OBSERVER'), false)
  })

  await test('创建任务/编辑任务/取消任务仅管理者可执行', async () => {
    for (const fn of ['canCreateTask', 'canEditTask', 'canCancelTask', 'canCreateMilestone']) {
      assert.equal(permission[fn]('OWNER'), true, `OWNER 应可 ${fn}`)
      assert.equal(permission[fn]('MAINTAINER'), true, `MAINTAINER 应可 ${fn}`)
      assert.equal(permission[fn]('MEMBER'), false, `MEMBER 不应可 ${fn}`)
      assert.equal(permission[fn]('OBSERVER'), false, `OBSERVER 不应可 ${fn}`)
    }
  })

  await test('管理者可用全部合法转换（含 CANCELED）', async () => {
    const own = { status: 'TODO', assigneeUserId: 2 }
    assert.deepEqual(
      permission.allowedTaskTransitions({ projectRole: 'OWNER', globalRoles: [], task: own, currentUserId: 2 }),
      ['IN_PROGRESS', 'CANCELED']
    )
  })

  await test('MEMBER 仅可操作自己的任务，且不能取消', async () => {
    const own = { status: 'IN_PROGRESS', assigneeUserId: 2 }
    assert.deepEqual(
      permission.allowedTaskTransitions({ projectRole: 'MEMBER', globalRoles: [], task: own, currentUserId: 2 }),
      ['BLOCKED', 'DONE']
    )
    const others = { status: 'IN_PROGRESS', assigneeUserId: 99 }
    assert.deepEqual(
      permission.allowedTaskTransitions({ projectRole: 'MEMBER', globalRoles: [], task: others, currentUserId: 2 }),
      []
    )
  })

  await test('OBSERVER 无任何状态转换', async () => {
    const task = { status: 'TODO', assigneeUserId: 2 }
    assert.deepEqual(
      permission.allowedTaskTransitions({ projectRole: 'OBSERVER', globalRoles: [], task, currentUserId: 2 }),
      []
    )
  })

  await test('终态任务锁定，无可用转换', async () => {
    assert.equal(permission.isTaskLocked({ status: 'DONE' }), true)
    assert.equal(permission.isTaskLocked({ status: 'CANCELED' }), true)
    assert.equal(permission.isTaskLocked({ status: 'TODO' }), false)
    assert.deepEqual(
      permission.allowedTaskTransitions({ projectRole: 'OWNER', globalRoles: [], task: { status: 'DONE' } }),
      []
    )
  })
})

// ========== apiError.js（§6 / §10） ==========
await group('错误归一化（契约 §6 / §10）', async () => {
  await test('归一化 mock 直接 reject 的错误对象', async () => {
    const norm = apiError.normalizeApiError({ code: 409, msg: '版本冲突', reason: 'VERSION_CONFLICT' })
    assert.equal(norm.code, 409)
    assert.equal(norm.reason, 'VERSION_CONFLICT')
    assert.equal(norm.msg, '版本冲突')
  })

  await test('归一化 axios 错误（response.data 优先）', async () => {
    const norm = apiError.normalizeApiError({
      message: 'Request failed',
      response: { status: 403, data: { code: 403, msg: '无权', reason: 'PROJECT_OPERATION_DENIED' } }
    })
    assert.equal(norm.code, 403)
    assert.equal(norm.reason, 'PROJECT_OPERATION_DENIED')
    assert.equal(norm.msg, '无权')
  })

  await test('isVersionConflict 识别乐观锁冲突', async () => {
    assert.equal(apiError.isVersionConflict({ code: 409, reason: 'VERSION_CONFLICT' }), true)
    assert.equal(apiError.isVersionConflict({ code: 409, reason: 'TASK_INVALID_TRANSITION' }), false)
  })

  await test('isProjectNotFound 识别 404 与项目相关 reason', async () => {
    assert.equal(apiError.isProjectNotFound({ code: 404 }), true)
    assert.equal(apiError.isProjectNotFound({ code: 404, reason: 'PROJECT_NOT_FOUND' }), true)
    assert.equal(apiError.isProjectNotFound({ code: 404, reason: 'PROJECT_ACCESS_DENIED' }), true)
    assert.equal(apiError.isProjectNotFound({ code: 409, reason: 'VERSION_CONFLICT' }), false)
  })

  await test('notifyApiError 对 VERSION_CONFLICT 静默且不抛错', async () => {
    assert.doesNotThrow(() => apiError.notifyApiError({ code: 409, reason: 'VERSION_CONFLICT', msg: 'x' }))
    assert.doesNotThrow(() => apiError.notifyApiError({ response: {}, code: 500, msg: 'x' }))
  })
})

// ========== createTask（§9.4） ==========
await group('POST /api/projects/{id}/tasks 创建任务', async () => {
  await test('创建成功：status=TODO，version=1，冗余负责人/里程碑名', async () => {
    const created = await tasks.createTask(10, {
      title: '契约测试任务',
      description: '验证创建契约',
      milestoneId: 101,
      assigneeUserId: 2,
      priority: 'HIGH',
      dueDate: '2026-10-12'
    })
    assertTaskShape(created)
    assert.equal(created.status, 'TODO', '初始状态 TODO')
    assert.equal(created.version, 1, '初始 version 1')
    assert.equal(created.assigneeName, '张同学')
    assert.equal(created.milestoneName, '原型设计与评审')
    assert.equal(created.priority, 'HIGH')
  })

  await test('标题过短：400 INVALID_PARAMETER', async () => {
    await assert.rejects(
      () => tasks.createTask(10, { title: 'x' }),
      (err) => { assertErrorShape(err, 400, 'INVALID_PARAMETER'); return true }
    )
  })

  await test('负责人非项目成员：400 ASSIGNEE_NOT_MEMBER', async () => {
    await assert.rejects(
      () => tasks.createTask(10, { title: '分配越界任务', assigneeUserId: 999 }),
      (err) => { assertErrorShape(err, 400, 'ASSIGNEE_NOT_MEMBER'); return true }
    )
  })

  await test('里程碑不存在：400 MILESTONE_NOT_FOUND', async () => {
    await assert.rejects(
      () => tasks.createTask(10, { title: '错误里程碑任务', milestoneId: 99999 }),
      (err) => { assertErrorShape(err, 400, 'MILESTONE_NOT_FOUND'); return true }
    )
  })

  await test('MEMBER 角色项目：403 PROJECT_OPERATION_DENIED', async () => {
    await assert.rejects(
      () => tasks.createTask(5, { title: '越权创建任务' }),
      (err) => { assertErrorShape(err, 403, 'PROJECT_OPERATION_DENIED'); return true }
    )
  })

  await test('项目不存在：404 PROJECT_NOT_FOUND', async () => {
    await assert.rejects(
      () => tasks.createTask(999999, { title: '无项目任务' }),
      (err) => { assertErrorShape(err, 404, 'PROJECT_NOT_FOUND'); return true }
    )
  })
})

// ========== updateTaskStatus（§9.6） ==========
await group('PUT /api/projects/{id}/tasks/{tid}/status 状态变更', async () => {
  await test('非法转换：409 TASK_INVALID_TRANSITION', async () => {
    await assert.rejects(
      () => tasks.updateTaskStatus(10, 1001, { status: 'DONE', version: 1 }),
      (err) => { assertErrorShape(err, 409, 'TASK_INVALID_TRANSITION'); return true }
    )
  })

  await test('TODO → IN_PROGRESS 成功，version 自增', async () => {
    const res = await tasks.updateTaskStatus(10, 1001, { status: 'IN_PROGRESS', version: 1 })
    assert.equal(res.status, 'IN_PROGRESS')
    assert.equal(res.version, 2)
  })

  await test('进入 BLOCKED 缺 blockReason：400 BLOCK_REASON_REQUIRED', async () => {
    await assert.rejects(
      () => tasks.updateTaskStatus(10, 1001, { status: 'BLOCKED', version: 2 }),
      (err) => { assertErrorShape(err, 400, 'BLOCK_REASON_REQUIRED'); return true }
    )
  })

  await test('IN_PROGRESS → BLOCKED 成功并记录 blockReason', async () => {
    const res = await tasks.updateTaskStatus(10, 1001, {
      status: 'BLOCKED',
      version: 2,
      blockReason: '等待物料到货'
    })
    assert.equal(res.status, 'BLOCKED')
    assert.equal(res.version, 3)
    const detail = await tasks.getTaskDetail(10, 1001)
    assert.equal(detail.blockReason, '等待物料到货')
  })

  await test('BLOCKED → IN_PROGRESS 恢复，blockReason 保留', async () => {
    const res = await tasks.updateTaskStatus(10, 1001, { status: 'IN_PROGRESS', version: 3 })
    assert.equal(res.status, 'IN_PROGRESS')
    const detail = await tasks.getTaskDetail(10, 1001)
    assert.equal(detail.blockReason, '等待物料到货', '转出后保留历史阻塞记录')
  })

  await test('IN_PROGRESS → DONE 成功', async () => {
    const res = await tasks.updateTaskStatus(10, 1001, { status: 'DONE', version: 4 })
    assert.equal(res.status, 'DONE')
    assert.equal(res.version, 5)
  })

  await test('终态 DONE 不可再转出：409 TASK_INVALID_TRANSITION', async () => {
    await assert.rejects(
      () => tasks.updateTaskStatus(10, 1001, { status: 'IN_PROGRESS', version: 5 }),
      (err) => { assertErrorShape(err, 409, 'TASK_INVALID_TRANSITION'); return true }
    )
  })

  await test('版本不一致：409 VERSION_CONFLICT（不静默覆盖）', async () => {
    await assert.rejects(
      () => tasks.updateTaskStatus(10, 1002, { status: 'DONE', version: 999 }),
      (err) => {
        assertErrorShape(err, 409, 'VERSION_CONFLICT')
        assert.equal(err.data.currentVersion, 2, '返回当前 version 供前端刷新')
        return true
      }
    )
  })

  await test('MEMBER 负责人：可开始自己的任务，不能取消', async () => {
    await assert.rejects(
      () => tasks.updateTaskStatus(5, 5001, { status: 'CANCELED', version: 2 }),
      (err) => { assertErrorShape(err, 403, 'PROJECT_OPERATION_DENIED'); return true }
    )
    const res = await tasks.updateTaskStatus(5, 5001, { status: 'DONE', version: 2 })
    assert.equal(res.status, 'DONE')
  })

  await test('管理者可操作未分配任务（MEMBER 非负责人场景由权限工具覆盖）', async () => {
    const created = await tasks.createTask(10, { title: '未分配任务', assigneeUserId: null })
    const res = await tasks.updateTaskStatus(10, created.id, { status: 'IN_PROGRESS', version: 1 })
    assert.equal(res.status, 'IN_PROGRESS')
    assert.equal(res.version, 2)
  })
})

// ========== updateTask（§9.8） ==========
await group('PUT /api/projects/{id}/tasks/{tid} 编辑任务', async () => {
  await test('编辑成功：更新字段并自增 version', async () => {
    const updated = await tasks.updateTask(10, 1002, {
      version: 2,
      priority: 'HIGH',
      dueDate: '2026-10-10',
      description: '更新后的描述'
    })
    assert.equal(updated.priority, 'HIGH')
    assert.equal(updated.dueDate, '2026-10-10')
    assert.equal(updated.version, 3)
  })

  await test('版本不一致：409 VERSION_CONFLICT', async () => {
    await assert.rejects(
      () => tasks.updateTask(10, 1002, { version: 999, title: '冲突标题' }),
      (err) => { assertErrorShape(err, 409, 'VERSION_CONFLICT'); return true }
    )
  })

  await test('MEMBER 角色：403 PROJECT_OPERATION_DENIED', async () => {
    await assert.rejects(
      () => tasks.updateTask(5, 5002, { version: 2, title: '越权编辑' }),
      (err) => { assertErrorShape(err, 403, 'PROJECT_OPERATION_DENIED'); return true }
    )
  })
})

// ========== 页面结构静态检查（§12） ==========
await group('项目详情页 / 看板 / 首页 tasks 区域', async () => {
  const detailSrc = readFileSync(new URL('../../views/projects/ProjectDetail.vue', import.meta.url), 'utf8')
  const kanbanSrc = readFileSync(new URL('../../views/projects/TaskKanban.vue', import.meta.url), 'utf8')
  const dashboardSrc = readFileSync(new URL('../../views/Dashboard.vue', import.meta.url), 'utf8')
  const workbenchSrc = readFileSync(new URL('../workbench.js', import.meta.url), 'utf8')

  await test('项目详情页含四个 Tab（概览/里程碑/任务看板/成员）', async () => {
    assert.match(detailSrc, /const TAB_NAMES = \['overview', 'milestones', 'kanban', 'members'\]/)
    assert.match(detailSrc, /label="里程碑"/)
    assert.match(detailSrc, /label="任务看板"/)
  })

  await test('详情页集成 MilestonePanel 与 TaskKanban', async () => {
    assert.match(detailSrc, /import MilestonePanel from '\.\/MilestonePanel\.vue'/)
    assert.match(detailSrc, /import TaskKanban from '\.\/TaskKanban\.vue'/)
  })

  await test('详情页支持 ?tab= 深链定位', async () => {
    assert.match(detailSrc, /route\.query\.tab/)
  })

  await test('看板默认四列且移动端纵向堆叠', async () => {
    assert.match(kanbanSrc, /kanban-board--stacked/)
    assert.match(kanbanSrc, /BOARD_STATUSES|columns/)
  })

  await test('首页 tasks 区域渲染统计与最近任务并跳转看板', async () => {
    assert.match(dashboardSrc, /tasksSummary/)
    assert.match(dashboardSrc, /tasksList/)
    assert.match(dashboardSrc, /tab: 'kanban'/)
  })

  await test('工作台 mock tasks 区域走 buildMockWorkbenchTasks', async () => {
    assert.match(workbenchSrc, /tasks: buildMockWorkbenchTasks\(\)/)
    assert.doesNotMatch(workbenchSrc, /tasks: notAvailable\(\)/)
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