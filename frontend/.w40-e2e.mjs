/**
 * M01-W04 三视口视觉证据采集脚本
 *
 * 用途：用 Playwright 驱动本机 Microsoft Edge，对 mock 构建（VITE_USE_MOCK=true）
 *       的真实 Vue 应用做三视口截图，产出 docs/evidence/2026-W40/frontend/。
 *
 * 运行：
 *   1) 先构建 mock 版本：VITE_USE_MOCK=true npm run build
 *   2) 启动预览：npm run preview  （默认 http://localhost:4173）
 *   3) node .w40-e2e.mjs
 *
 * 环境变量：
 *   W40_BASE_URL  预览地址，默认 http://localhost:4173
 *   W40_EDGE      Edge 可执行文件路径
 *   W40_OUT       输出根目录，默认 ../docs/evidence/2026-W40/frontend
 *
 * 说明：登录态由 init script 注入 localStorage 模拟（mock 模式下不依赖真实后端）。
 *       截图仅用于布局/权限/交互可见性验证，不冒充真实后端联调证据。
 */

import { chromium } from 'playwright-core'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const BASE = process.env.W40_BASE_URL || 'http://localhost:4173'
const EDGE =
  process.env.W40_EDGE || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const OUT_ROOT =
  process.env.W40_OUT || path.resolve(process.cwd(), '..', 'docs', 'evidence', '2026-W40', 'frontend')

const VIEWPORTS = [
  { name: '1440x900', width: 1440, height: 900 },
  { name: '1280x800', width: 1280, height: 800 },
  { name: '390x844', width: 390, height: 844 }
]

// 与 mock 项目 10（智能气象站项目）数据对齐：id=2 为「张同学」，是多数任务的负责人，
// 因此首页 tasks 区域有统计与最近任务；项目 myRole=OWNER，看板/里程碑操作按钮可见。
const AUTH = {
  token: 'w40-mock-token',
  userInfo: {
    id: 2,
    memberId: '20260001',
    name: '张同学',
    primaryRoleKey: 'MEMBER',
    primaryRoleName: '普通成员'
  },
  todayAttendance: null
}

const results = []

async function shot(page, vp, name, { fullPage = false } = {}) {
  const dir = path.join(OUT_ROOT, vp.name)
  mkdirSync(dir, { recursive: true })
  await page.screenshot({ path: path.join(dir, `${name}.png`), fullPage })
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1
  )
  results.push({ viewport: vp.name, shot: name, overflow })
  process.stdout.write(`  [${vp.name}] ${name}.png${overflow ? '  ⚠ 横向溢出' : ''}\n`)
}

async function openProjectTab(page, tab) {
  await page.goto(`${BASE}/projects/10?tab=${tab}`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.project-tabs', { timeout: 15000 })
}

async function runViewport(browser, vp) {
  process.stdout.write(`\n=== ${vp.name} ===\n`)
  const context = await browser.newContext({
    viewport: { width: vp.width, height: vp.height },
    deviceScaleFactor: 1
  })
  await context.addInitScript((auth) => {
    localStorage.setItem('usn_lab_hub_auth', JSON.stringify(auth))
    localStorage.setItem('token', auth.token)
  }, AUTH)
  const page = await context.newPage()

  // 1. 首页 tasks 区域（统计 + 最近 5 条）
  await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.task-region', { timeout: 15000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'workbench', { fullPage: vp.width <= 768 })

  // 2. 项目详情 · 概览 Tab
  await openProjectTab(page, 'overview')
  await page.waitForSelector('.summary-item', { timeout: 15000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'project-overview', { fullPage: vp.width <= 768 })

  // 3. 里程碑 Tab
  await openProjectTab(page, 'milestones')
  await page.waitForSelector('.milestone-card', { timeout: 15000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'project-milestones', { fullPage: vp.width <= 768 })

  // 4. 创建里程碑弹窗
  await page.locator('.milestone-panel__toolbar').getByRole('button', { name: '创建里程碑' }).click()
  await page.waitForSelector('.el-dialog__body', { timeout: 10000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'project-milestone-create')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)

  // 5. 任务看板（四列；移动端纵向分组，用整页截图呈现完整纵向结构）
  await openProjectTab(page, 'kanban')
  await page.waitForSelector('.task-card', { timeout: 15000 })
  await page.waitForTimeout(500)
  await shot(page, vp, 'project-kanban', { fullPage: vp.width <= 768 })

  // 6. 卡片状态操作菜单
  await page
    .locator('.task-card')
    .filter({ hasText: '设计温湿度传感器电路' })
    .locator('.task-card__menu button')
    .click()
  await page.waitForSelector('.el-dropdown-menu__item', { timeout: 10000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'project-kanban-status-menu')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)

  // 7. 任务详情抽屉（含阻塞原因展示）
  await page.locator('.task-card').filter({ hasText: '传感器采购到货确认' }).click()
  await page.waitForSelector('.el-drawer__body', { timeout: 10000 })
  await page.waitForTimeout(500)
  await shot(page, vp, 'project-task-detail')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(500)

  // 8. 创建任务表单
  await page.locator('.kanban-toolbar__actions').getByRole('button', { name: '创建任务' }).click()
  await page.waitForSelector('.task-form__row', { timeout: 10000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'project-task-create')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(500)

  // 9. 乐观锁冲突提示（409 VERSION_CONFLICT）
  //    抽屉 → 编辑，使表单持有旧 version；随后在弹窗背后真实触发一次状态变更，
  //    令 mock 中该任务 version 自增；此时提交表单即触发 409。
  await openProjectTab(page, 'kanban')
  await page.waitForSelector('.task-card', { timeout: 15000 })
  await page.locator('.task-card').filter({ hasText: 'PCB 布局布线' }).click()
  await page.waitForSelector('.el-drawer__body', { timeout: 10000 })
  await page.waitForTimeout(400)
  await page.locator('.el-drawer__footer').getByRole('button', { name: '编辑' }).click()
  await page.waitForSelector('.task-form__row', { timeout: 10000 })
  await page.waitForTimeout(400)
  await page.evaluate(() => {
    const card = [...document.querySelectorAll('.task-card')].find((c) =>
      c.textContent.includes('PCB 布局布线')
    )
    card?.querySelector('.task-card__menu button')?.click()
  })
  await page.waitForTimeout(500)
  await page.evaluate(() => {
    ;[...document.querySelectorAll('.el-dropdown-menu__item')]
      .find((i) => i.textContent.includes('已完成'))
      ?.click()
  })
  await page.waitForTimeout(800)
  await page.evaluate(() => {
    ;[...document.querySelectorAll('.el-dialog__footer button')]
      .find((b) => b.textContent.trim() === '保存')
      ?.click()
  })
  await page.waitForSelector('.el-message-box', { timeout: 10000 })
  await page.waitForTimeout(500)
  await shot(page, vp, 'project-version-conflict')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)

  // 10. 成员 Tab
  await openProjectTab(page, 'members')
  await page.waitForSelector('.el-table__row', { timeout: 15000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'project-members', { fullPage: vp.width <= 768 })

  await context.close()
}

const browser = await chromium.launch({ executablePath: EDGE, headless: true })
try {
  for (const vp of VIEWPORTS) {
    await runViewport(browser, vp)
  }
} finally {
  await browser.close()
}

const overflowCount = results.filter((r) => r.overflow).length
writeFileSync(
  path.join(OUT_ROOT, 'e2e-result.json'),
  JSON.stringify(
    {
      base: BASE,
      generatedAt: new Date().toISOString(),
      viewports: VIEWPORTS.map((v) => v.name),
      shots: results.length,
      horizontalOverflow: overflowCount,
      results
    },
    null,
    2
  )
)

process.stdout.write(`\n==========\n`)
process.stdout.write(`截图 ${results.length} 张，横向溢出 ${overflowCount} 处\n`)
process.stdout.write(`输出目录：${OUT_ROOT}\n`)