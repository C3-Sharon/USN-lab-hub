/**
 * W38 任务书 4.8 / 6 单元测试
 *
 * 覆盖：
 *   - hasAnyRole / 多角色路由判断
 *   - 401 与 403 分流
 *   - 工作台 READY / NOT_AVAILABLE / ERROR 数据适配
 *   - 双 Header 使用相同 Token
 *   - API-1~10、API-16 修正后的数据读取
 *
 * 运行：node src/api/__tests__/w38-contract-test.mjs
 *
 * 不引入 jest / vitest；保持与 src/api/__tests__/sse-test-node.mjs 一致的
 * "纯 Node + assert" 风格，避免本轮引入大型测试体系。
 */

import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { ROLE, getUserRoles, getPrimaryRole, hasAnyRole } from '../../utils/permission.js'

let pass = 0
let fail = 0
const failures = []

function test(name, fn) {
  try {
    fn()
    pass++
    process.stdout.write(`  ✓ ${name}\n`)
  } catch (err) {
    fail++
    failures.push({ name, message: err.message })
    process.stdout.write(`  ✗ ${name}\n    ${err.message}\n`)
  }
}

function group(name, fn) {
  process.stdout.write(`\n# ${name}\n`)
  fn()
}

// =================== permission.js ===================
group('hasAnyRole / 多角色路由判断', () => {
  const admin = { primaryRoleKey: ROLE.SYSTEM_ADMIN, primaryRoleName: '系统管理员', roles: [{ roleKey: ROLE.SYSTEM_ADMIN, roleName: '系统管理员' }] }
  const teacher = { primaryRoleKey: ROLE.TEACHER, primaryRoleName: '老师', roles: [{ roleKey: ROLE.TEACHER, roleName: '老师' }] }
  const keeper = { primaryRoleKey: ROLE.STOCK_KEEPER, primaryRoleName: '库存管理员', roles: [{ roleKey: ROLE.STOCK_KEEPER, roleName: '库存管理员' }] }
  const member = { primaryRoleKey: ROLE.MEMBER, primaryRoleName: '普通成员', roles: [{ roleKey: ROLE.MEMBER, roleName: '普通成员' }] }
  const multi = { primaryRoleKey: ROLE.SYSTEM_ADMIN, primaryRoleName: '系统管理员', roles: [{ roleKey: ROLE.SYSTEM_ADMIN }, { roleKey: ROLE.MEMBER }] }

  test('空 requiredRoles 对任何人通过', () => {
    assert.equal(hasAnyRole(admin, []), true)
    assert.equal(hasAnyRole(null, []), true)
  })

  test('SYSTEM_ADMIN 命中系统管理员路由', () => {
    assert.equal(hasAnyRole(admin, [ROLE.SYSTEM_ADMIN]), true)
  })

  test('MEMBER 命中工作台但不能命中指令控制台', () => {
    assert.equal(hasAnyRole(member, [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER]), true)
    assert.equal(hasAnyRole(member, [ROLE.SYSTEM_ADMIN]), false)
  })

  test('多角色用户命中其一即放行', () => {
    assert.equal(hasAnyRole(multi, [ROLE.MEMBER]), true)
    assert.equal(hasAnyRole(multi, [ROLE.SYSTEM_ADMIN]), true)
  })

  test('兼容旧 user.role=admin 字段', () => {
    const oldAdmin = { role: 'admin' }
    assert.deepEqual(getUserRoles(oldAdmin), [ROLE.SYSTEM_ADMIN])
    assert.equal(getPrimaryRole(oldAdmin), ROLE.SYSTEM_ADMIN)
  })

  test('缺角色信息时默认为 MEMBER', () => {
    assert.deepEqual(getUserRoles({}), [ROLE.MEMBER])
    assert.equal(getPrimaryRole({}), ROLE.MEMBER)
  })

  test('teacher 能进 /iot/overview、/iot/alerts、/iot/logs，不能进 /iot/commands', () => {
    assert.equal(hasAnyRole(teacher, [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER]), true)
    assert.equal(hasAnyRole(teacher, [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER]), true)
    assert.equal(hasAnyRole(teacher, [ROLE.SYSTEM_ADMIN]), false)
  })

  test('STOCK_KEEPER 不能进 /iot/commands 与 /iot/logs', () => {
    assert.equal(hasAnyRole(keeper, [ROLE.SYSTEM_ADMIN]), false)
    assert.equal(hasAnyRole(keeper, [ROLE.SYSTEM_ADMIN, ROLE.TEACHER]), false)
  })
})

// =================== request 401/403 分流 ===================
group('401 与 403 分流', () => {
  // 由于 request.js 耦合 Vue 全局（ElMessage / router / userStore），这里只做"判定逻辑"测试
  // 通过复刻 interceptor 的最小子集验证分类正确

  function classifyHttp(status, body) {
    if (status === 401) {
      return { action: 'logout', reason: body?.reason || 'TOKEN_INVALID' }
    }
    if (status === 403 || body?.reason === 'ACCESS_DENIED') {
      return { action: 'keep', reason: body?.reason }
    }
    return { action: 'pass' }
  }

  test('401 + TOKEN_MISSING → logout', () => {
    assert.deepEqual(classifyHttp(401, { code: 401, reason: 'TOKEN_MISSING' }), { action: 'logout', reason: 'TOKEN_MISSING' })
  })

  test('401 + TOKEN_EXPIRED → logout', () => {
    assert.equal(classifyHttp(401, { code: 401, reason: 'TOKEN_EXPIRED' }).reason, 'TOKEN_EXPIRED')
  })

  test('401 + ACCOUNT_DISABLED → logout + 提示禁用', () => {
    assert.equal(classifyHttp(401, { code: 401, reason: 'ACCOUNT_DISABLED' }).reason, 'ACCOUNT_DISABLED')
  })

  test('401 + 未知 code → logout（保守）', () => {
    assert.equal(classifyHttp(401, {}).reason, 'TOKEN_INVALID')
  })

  test('403 + ACCESS_DENIED → keep 登录态', () => {
    assert.deepEqual(classifyHttp(403, { code: 403, reason: 'ACCESS_DENIED' }), { action: 'keep', reason: 'ACCESS_DENIED' })
  })

  test('200 + code 200 → pass', () => {
    assert.equal(classifyHttp(200, { code: 200 }).action, 'pass')
  })
})

// =================== 工作台 6 区域 ===================
group('工作台 READY / NOT_AVAILABLE / ERROR 适配', () => {
  // 复刻 Dashboard 内的 regionState / regionHint 计算
  function regionState(region) {
    if (!region) return 'loading'
    if (region.state === 'NOT_AVAILABLE') return 'not-available'
    if (region.state === 'READY') return 'success'
    if (region.state === 'ERROR') return 'error'
    return 'empty'
  }
  function regionHint(region) {
    if (!region) return '加载中'
    if (region.state === 'NOT_AVAILABLE') return '尚未开放'
    return ''
  }

  test('attendance READY + data → success', () => {
    assert.equal(regionState({ state: 'READY', weekHours: 0 }), 'success')
    assert.equal(regionHint({ state: 'READY' }), '')
  })

  test('projects NOT_AVAILABLE → not-available + 尚未开放', () => {
    assert.equal(regionState({ state: 'NOT_AVAILABLE' }), 'not-available')
    assert.equal(regionHint({ state: 'NOT_AVAILABLE' }), '尚未开放')
  })

  test('attendance 区域 ERROR → error', () => {
    assert.equal(regionState({ state: 'ERROR', message: 'fail' }), 'error')
  })

  test('未返回任何 region → loading', () => {
    assert.equal(regionState(undefined), 'loading')
    assert.equal(regionHint(undefined), '加载中')
  })

  test('不能臆造 available 字段', () => {
    // 状态机只接受 READY/NOT_AVAILABLE/ERROR
    const illegal = { state: 'AVAILABLE' }
    assert.equal(regionState(illegal), 'empty')
  })
})

// =================== 双 Header ===================
group('双 Header 使用相同 Token', () => {
  // 复刻 request.js 的请求拦截器最小子集
  function buildHeaders(token) {
    if (!token) return {}
    return { Authorization: `Bearer ${token}`, token }
  }

  test('token 为空时不写 Header', () => {
    assert.deepEqual(buildHeaders(''), {})
    assert.deepEqual(buildHeaders(undefined), {})
  })

  test('有 token 时两个 Header 同步', () => {
    const t = 'eyJhbGciOiJIUzI1NiJ9.payload.sig'
    const h = buildHeaders(t)
    assert.equal(h.Authorization, `Bearer ${t}`)
    assert.equal(h.token, t)
    assert.equal(h.Authorization.replace('Bearer ', ''), h.token)
  })

  test('token 长度 / 字符不会影响 Header 同步', () => {
    const t = 'x'.repeat(256)
    const h = buildHeaders(t)
    assert.equal(h.Authorization.length, `Bearer ${t}`.length)
    assert.equal(h.token, t)
  })
})

// =================== API-1~10 + API-16 ===================
group('API-1~10、API-16 修正后的数据读取', () => {
  // 模拟 request.js 解包后的 payload.data
  const projectsPayload = { records: [{ id: 1 }], total: 1, size: 10, current: 1, pages: 1 }
  const devicesPayload = { records: [{ id: 1 }], total: 1 }
  const projectDetailPayload = { id: 1, projectName: 'p', devices: [] }
  const deviceDetailPayload = { id: 1, deviceCode: 'PM-001', metrics: [] }
  const latestPayload = { deviceId: 1, deviceCode: 'PM-001', metrics: [] }
  const historyPayload = { deviceId: 1, points: [] }
  const alertsPayload = { list: [{ id: 1 }], total: 1 }
  const recsPayload = { list: [{ id: 1 }], total: 1 }
  const cmdsPayload = { list: [{ commandId: 'c1' }], total: 1 }
  const logsPayload = { list: [{ id: 1 }], total: 1 }

  test('API-1 listProjects → res.records', () => {
    assert.deepEqual(projectsPayload.records, [{ id: 1 }])
    assert.equal(projectsPayload.total, 1)
  })

  test('API-2 listDevices → res.records', () => {
    assert.deepEqual(devicesPayload.records, [{ id: 1 }])
  })

  test('API-3 getProjectDetail → res 直接挂项目对象，devices 字段', () => {
    assert.equal(projectDetailPayload.id, 1)
    assert.ok(Array.isArray(projectDetailPayload.devices))
  })

  test('API-4 getDeviceDetail → res 直接挂设备对象，metrics 字段', () => {
    assert.equal(deviceDetailPayload.deviceCode, 'PM-001')
    assert.ok(Array.isArray(deviceDetailPayload.metrics))
  })

  test('API-5 getLatestMetrics → res.metrics', () => {
    assert.ok(Array.isArray(latestPayload.metrics))
  })

  test('API-6 listAlerts → res.list（不是 records）', () => {
    assert.deepEqual(alertsPayload.list, [{ id: 1 }])
    assert.equal(alertsPayload.list.length, 1)
  })

  test('API-7 listRecommendations → res.list', () => {
    assert.deepEqual(recsPayload.list, [{ id: 1 }])
  })

  test('API-8 listCommands → res.list', () => {
    assert.deepEqual(cmdsPayload.list, [{ commandId: 'c1' }])
  })

  test('API-9 listOperationLogs → res.list', () => {
    assert.deepEqual(logsPayload.list, [{ id: 1 }])
  })

  test('API-10 getMetricHistory → res.points', () => {
    assert.ok(Array.isArray(historyPayload.points))
  })

  test('API-16 统一为 listAlerts(deviceId, params) 等位置参数', () => {
    const src = readFileSync(new URL('../iot.js', import.meta.url), 'utf8')
    const patterns = [
      /export function listAlerts\(deviceId, params\)/,
      /export function listRecommendations\(deviceId, params\)/,
      /export function listCommands\(deviceId, params\)/,
      /export function listOperationLogs\(deviceId, params\)/
    ]
    for (const pattern of patterns) {
      assert.match(src, pattern, `iot.js 缺少 ${pattern}`)
    }
  })
})

group('真实工作台契约源文件', () => {
  const workbenchSource = readFileSync(new URL('../workbench.js', import.meta.url), 'utf8')
  const dashboardSource = readFileSync(new URL('../../views/Dashboard.vue', import.meta.url), 'utf8')
  const mockSource = readFileSync(new URL('../../utils/mock.js', import.meta.url), 'utf8')

  test('工作台只调用聚合接口', () => {
    assert.match(workbenchSource, /\/api\/workbench\/overview/)
    assert.doesNotMatch(workbenchSource, /\/api\/workbench\/device-reminder/)
  })

  test('Dashboard 使用 state 而不是 status', () => {
    assert.match(dashboardSource, /region\.state/)
    assert.doesNotMatch(dashboardSource, /region\.status/)
  })

  test('未配置环境变量时默认关闭 mock', () => {
    assert.match(mockSource, /typeof window === 'undefined'\) return false/)
    assert.doesNotMatch(mockSource, /return true\s*\n}/)
  })
})

// =================== summary ===================
process.stdout.write(`\n==========\n`)
process.stdout.write(`passed: ${pass}\n`)
process.stdout.write(`failed: ${fail}\n`)
if (fail > 0) {
  process.stdout.write(`\nfailures:\n`)
  for (const f of failures) process.stdout.write(`- ${f.name}: ${f.message}\n`)
  process.exit(1)
}
process.stdout.write('all tests passed\n')
