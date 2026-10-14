/**
 * M02-W02 三视口视觉证据采集脚本（实验模板域）
 *
 * 用途：用 Playwright 驱动本机 Microsoft Edge，对 mock 构建（VITE_USE_MOCK=true）
 *       的真实 Vue 应用做三视口截图，产出 docs/evidence/2026-W42/frontend/。
 *
 * 运行：
 *   1) 先构建 mock 版本：VITE_USE_MOCK=true npm run build
 *   2) 启动预览：npm run preview  （默认 http://localhost:4173）
 *   3) node .w42-e2e.mjs
 *
 * 环境变量：
 *   W42_BASE_URL  预览地址，默认 http://localhost:4173
 *   W42_EDGE      Edge 可执行文件路径
 *   W42_OUT       输出根目录，默认 ../docs/evidence/2026-W42/frontend
 *
 * 说明：登录态由 init script 注入 localStorage 模拟（mock 模式下不依赖真实后端）。
 *       注入角色为 TEACHER（系统模板管理角色），可见全部模板（含草稿/废弃版本）与全部路线
 *       （含归档路线），便于一次呈现版本状态标签、废弃提示与管理入口。
 *       当前用户 id=2（张同学），已加入路线 1、2。
 *       截图仅用于布局/权限/交互可见性验证，不冒充真实后端联调证据。
 */

import { chromium } from 'playwright-core'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const BASE = process.env.W42_BASE_URL || 'http://localhost:4173'
const EDGE =
  process.env.W42_EDGE || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const OUT_ROOT =
  process.env.W42_OUT || path.resolve(process.cwd(), '..', 'docs', 'evidence', '2026-W42', 'frontend')

const VIEWPORTS = [
  { name: '1440x900', width: 1440, height: 900 },
  { name: '1280x800', width: 1280, height: 800 },
  { name: '390x844', width: 390, height: 844 }
]

// 与 mock 数据对齐：id=2「张同学」，primaryRoleKey=TEACHER。
// TEACHER 为系统模板管理角色，可管理全部模板；同时可见归档路线，便于展示单元关联废弃版本。
const AUTH = {
  token: 'w42-mock-token',
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

// mock 数据锚点：
//   模板 1「电压测量实验」SYSTEM，PUBLISHED 1.0.0 + DRAFT 0.2.0，DRAFT 版本 id=3
//   模板 4「PM-001 功率校准实验」PROJECT，DRAFT 0.3.0 + PUBLISHED 1.1.0 + DEPRECATED 1.0.0
//   路线 1 单元 104「电路基础」关联模板 1 的 PUBLISHED 版本（id=2）
//   路线 4（已归档）单元 402「成本评估」关联模板 4 的 DEPRECATED 版本（id=41）
const TEMPLATE_SYSTEM = 1
const TEMPLATE_PROJECT = 4
const DRAFT_VERSION = 3

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

const fullPageOnMobile = (vp) => vp.width <= 768

/** 移动端展开指定阶段（默认全部折叠）；桌面端阶段默认展开 */
async function ensureStageExpanded(page, vp, text) {
  if (vp.width > 767) return
  const stage = page.locator('.timeline-item').filter({ hasText: text })
  const isOpen = await stage
    .locator('.timeline-item__caret')
    .first()
    .evaluate((el) => el.classList.contains('is-open'))
  if (isOpen) return
  await stage.locator('.timeline-item__head').click()
  await page.waitForTimeout(400)
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

  // 1. 侧栏入口「学习实验台 > 实验模板」（桌面端侧栏 / 移动端抽屉）
  await page.goto(`${BASE}/experiment/templates`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.template-card', { timeout: 15000 })
  await page.waitForTimeout(400)
  if (vp.width <= 1024) {
    await page.locator('.mobile-menu-button').click()
    await page.waitForSelector('.mobile-nav-drawer', { timeout: 10000 })
    await page.waitForTimeout(400)
    await shot(page, vp, 'template-nav')
    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)
  } else {
    await shot(page, vp, 'template-nav')
  }

  // 2. 系统模板列表（tab + 筛选 + 卡片 + 创建入口）
  await page.goto(`${BASE}/experiment/templates`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.template-card', { timeout: 15000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'template-list', { fullPage: fullPageOnMobile(vp) })

  // 3. 项目模板 tab
  await page.locator('.scope-tabs .el-tabs__item').filter({ hasText: '项目模板' }).click()
  await page.waitForTimeout(700)
  await shot(page, vp, 'template-list-project', { fullPage: fullPageOnMobile(vp) })

  // 4. 列表空状态（搜索无结果）
  await page.goto(`${BASE}/experiment/templates`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.template-card', { timeout: 15000 })
  await page.locator('.filter-keyword input').fill('不存在的模板关键字')
  await page.locator('.filter-keyword input').press('Enter')
  await page.waitForSelector('.region-state--empty', { timeout: 10000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'template-list-empty')

  // 5. 模板详情（版本历史时间线 + 字段结构，默认选中草稿版本）
  await page.goto(`${BASE}/experiment/templates/${TEMPLATE_SYSTEM}`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.version-item', { timeout: 15000 })
  await page.waitForTimeout(600)
  await shot(page, vp, 'template-detail', { fullPage: fullPageOnMobile(vp) })

  // 6. 已废弃版本详情（时间线删除线 + 废弃告警）
  await page.goto(`${BASE}/experiment/templates/${TEMPLATE_PROJECT}`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.version-item--deprecated', { timeout: 15000 })
  await page.locator('.version-item--deprecated').first().click()
  await page.waitForTimeout(600)
  await shot(page, vp, 'template-detail-deprecated', { fullPage: fullPageOnMobile(vp) })

  // 7. 详情空状态（模板不存在或不可见）
  await page.goto(`${BASE}/experiment/templates/9999`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.region-state--empty', { timeout: 15000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'template-detail-empty')

  // 8. 发布确认对话框（草稿版本「发布」按钮 → 确认弹窗）
  await page.goto(`${BASE}/experiment/templates/${TEMPLATE_SYSTEM}`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.version-item--draft', { timeout: 15000 })
  await page
    .locator('.version-item--draft')
    .locator('button')
    .filter({ hasText: '发布' })
    .first()
    .click()
  await page.waitForSelector('.el-message-box', { timeout: 10000 })
  await page.waitForTimeout(500)
  await shot(page, vp, 'template-publish-confirm')
  await page.locator('.el-message-box__btns button').first().click()
  await page.waitForTimeout(400)

  // 9. 编辑器：字段列表 + 属性面板（移动端为字段列表步骤）
  await page.goto(`${BASE}/experiment/templates/${TEMPLATE_SYSTEM}/editor/${DRAFT_VERSION}`, {
    waitUntil: 'networkidle'
  })
  await page.waitForSelector('.field-item', { timeout: 15000 })
  await page.waitForTimeout(500)
  await shot(page, vp, 'template-editor', { fullPage: fullPageOnMobile(vp) })

  // 10. 编辑器：Schema 预检（实时校验 + 原始 Schema 只读）
  if (vp.width <= 1024) {
    await page.locator('.mobile-steps .el-radio-button').filter({ hasText: '预检' }).click()
  } else {
    await page.locator('.panel-tabs .el-tabs__item').filter({ hasText: 'Schema 预检' }).click()
  }
  await page.waitForSelector('.raw-schema', { timeout: 10000 })
  await page.waitForTimeout(500)
  await shot(page, vp, 'template-editor-schema', { fullPage: fullPageOnMobile(vp) })

  // 11. 编辑器：预览填写（按当前 Schema 实时渲染）
  if (vp.width <= 1024) {
    await page.locator('.mobile-steps .el-radio-button').filter({ hasText: '预览' }).click()
  } else {
    await page.locator('.panel-tabs .el-tabs__item').filter({ hasText: '预览填写' }).click()
  }
  await page.waitForTimeout(700)
  await shot(page, vp, 'template-editor-preview', { fullPage: fullPageOnMobile(vp) })

  // 12. 编辑器只读守卫（已发布版本不可编辑）
  await page.goto(`${BASE}/experiment/templates/${TEMPLATE_SYSTEM}/editor/2`, {
    waitUntil: 'networkidle'
  })
  await page.waitForSelector('.region-state--not-available', { timeout: 15000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'template-editor-readonly')

  // 13. 学习单元模板关联展示（PUBLISHED 版本）
  await page.goto(`${BASE}/learning/1`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.timeline-item', { timeout: 15000 })
  await ensureStageExpanded(page, vp, '电路与焊接')
  const unit104 = page.locator('.unit-item').filter({ hasText: '电路基础' })
  await unit104.locator('.unit-template').first().waitFor({ state: 'visible', timeout: 10000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'learning-unit-template', { fullPage: fullPageOnMobile(vp) })

  // 14. 学习单元关联已废弃版本提示（归档路线 4 的单元 402）
  await page.goto(`${BASE}/learning/4`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.timeline-item', { timeout: 15000 })
  await ensureStageExpanded(page, vp, '传感器选型')
  const unit402 = page.locator('.unit-item').filter({ hasText: '成本评估' })
  await unit402.locator('.unit-template__warn').first().waitFor({ state: 'visible', timeout: 10000 })
  await page.waitForTimeout(400)
  await shot(page, vp, 'learning-unit-deprecated', { fullPage: fullPageOnMobile(vp) })

  // 15. 单元模板版本选择器（仅列出 PUBLISHED 版本）
  await page.goto(`${BASE}/learning/1`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.timeline-item', { timeout: 15000 })
  await ensureStageExpanded(page, vp, '电路与焊接')
  const unit = page.locator('.unit-item').filter({ hasText: '电路基础' })
  await unit.locator('button').filter({ hasText: '更换模板' }).first().click()
  await page.waitForSelector('.el-dialog', { timeout: 10000 })
  await page.waitForTimeout(900)
  await shot(page, vp, 'template-picker')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)

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
mkdirSync(OUT_ROOT, { recursive: true })
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
