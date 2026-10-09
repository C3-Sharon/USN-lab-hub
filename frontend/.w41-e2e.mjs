/**
 * M02-W01 三视口视觉证据采集脚本（学习实验域）
 *
 * 用途：用 Playwright 驱动本机 Microsoft Edge，对 mock 构建（VITE_USE_MOCK=true）
 *       的真实 Vue 应用做三视口截图，产出 docs/evidence/2026-W41/frontend/。
 *
 * 运行：
 *   1) 先构建 mock 版本：VITE_USE_MOCK=true npm run build
 *   2) 启动预览：npm run preview  （默认 http://localhost:4173）
 *   3) node .w41-e2e.mjs
 *
 * 环境变量：
 *   W41_BASE_URL  预览地址，默认 http://localhost:4173
 *   W41_EDGE      Edge 可执行文件路径
 *   W41_OUT       输出根目录，默认 ../docs/evidence/2026-W41/frontend
 *
 * 说明：登录态由 init script 注入 localStorage 模拟（mock 模式下不依赖真实后端）。
 *       注入角色为 TEACHER，可见全部路线（含草稿/归档），便于一次呈现难度与状态标签；
 *       当前用户 id=2（张同学）已加入路线 1、2，因此进度条与单元完成态可渲染。
 *       截图仅用于布局/权限/交互可见性验证，不冒充真实后端联调证据。
 */

import { chromium } from 'playwright-core'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const BASE = process.env.W41_BASE_URL || 'http://localhost:4173'
const EDGE =
  process.env.W41_EDGE || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const OUT_ROOT =
  process.env.W41_OUT || path.resolve(process.cwd(), '..', 'docs', 'evidence', '2026-W41', 'frontend')

const VIEWPORTS = [
  { name: '1440x900', width: 1440, height: 900 },
  { name: '1280x800', width: 1280, height: 800 },
  { name: '390x844', width: 390, height: 844 }
]

// 与 mock 数据对齐：id=2「张同学」已加入路线 1（进行中）、2（已完成）；
// 角色 TEACHER 可管理路线，故列表可见草稿与归档路线，状态筛选与创建入口可见。
const AUTH = {
  token: 'w41-mock-token',
  userInfo: {
    id: 2,
    memberId: '20260001',
    username: '张同学',
    name: '张同学',
    primaryRoleKey: 'TEACHER',
    primaryRoleName: '老师'
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

  // 1. 首页 learning 区域（统计 + 最近路线；紫色顶边，与项目/任务蓝色区分）
  await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.learning-region', { timeout: 15000 })
  await page.waitForTimeout(400)
  // learning 区域位于工作台第三行，整页截图以完整呈现（含与项目/任务区域的视觉区分）
  await shot(page, vp, 'workbench', { fullPage: true })

  // 2. 路线列表（卡片 + 封面占位 + 难度标签 + 进度条）
  await page.goto(`${BASE}/learning`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.roadmap-card', { timeout: 15000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'learning-list', { fullPage: vp.width <= 768 })

  // 3. 侧栏入口「学习实验台」（桌面端侧栏 / 移动端抽屉）
  if (vp.width <= 1024) {
    await page.locator('.mobile-menu-button').click()
    await page.waitForSelector('.mobile-nav-drawer', { timeout: 10000 })
    await page.waitForTimeout(400)
    await shot(page, vp, 'learning-nav')
    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)
  } else {
    await shot(page, vp, 'learning-nav')
  }

  // 4. 列表空状态（筛选无结果）
  await page.goto(`${BASE}/learning`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.roadmap-card', { timeout: 15000 })
  await page.locator('.filter-keyword input').fill('不存在的路线关键字')
  await page.locator('.filter-keyword input').press('Enter')
  await page.waitForSelector('.region-state--empty', { timeout: 10000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'learning-list-empty')

  // 5. 路线详情（左概览 + 右阶段时间线）
  await page.goto(`${BASE}/learning/1`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.timeline-item', { timeout: 15000 })
  await page.waitForTimeout(500)
  await shot(page, vp, 'learning-detail', { fullPage: true })

  // 6. 单元完成交互：勾选「手工焊接」，进度条与阶段计数同步更新
  const stage = page.locator('.timeline-item').filter({ hasText: '电路与焊接' })
  if (vp.width <= 767) {
    // 移动端阶段默认折叠，先展开目标阶段
    await stage.locator('.timeline-item__head').click()
    await page.waitForTimeout(300)
  }
  await stage.locator('.unit-item').filter({ hasText: '手工焊接' }).locator('.el-checkbox__inner').click()
  await page.waitForTimeout(800)
  await shot(page, vp, 'learning-detail-unit-done')

  // 7. 阶段折叠/展开交互（点击阶段头切换）
  await page.goto(`${BASE}/learning/1`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.timeline-item', { timeout: 15000 })
  await page.locator('.timeline-item').first().locator('.timeline-item__head').click()
  await page.waitForTimeout(400)
  await shot(page, vp, 'learning-timeline-toggle')

  // 8. 详情空状态（路线不存在或不可见）
  await page.goto(`${BASE}/learning/9999`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.region-state--empty', { timeout: 15000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'learning-detail-empty')

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