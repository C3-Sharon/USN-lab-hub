/**
 * M02-W03 三视口视觉证据采集脚本（实验记录 / 采集批次 / 导师反馈 / 项目转换）
 *
 * 用途：用 Playwright 驱动本机 Microsoft Edge，对 mock 构建（VITE_USE_MOCK=true）
 *       的真实 Vue 应用做三视口截图，产出 docs/evidence/2026-W43/frontend/。
 *
 * 运行：
 *   1) 先构建 mock 版本：$env:VITE_USE_MOCK='true'; npm run build
 *   2) 启动预览：npm run preview  （默认 http://localhost:4173）
 *   3) node .w43-e2e.mjs
 *
 * 环境变量：
 *   W43_BASE_URL  预览地址，默认 http://localhost:4173
 *   W43_EDGE      Edge 可执行文件路径
 *   W43_OUT       输出根目录，默认 ../docs/evidence/2026-W43/frontend
 *
 * 说明：登录态由 init script 注入 localStorage 模拟（mock 模式下不依赖真实后端）。
 *       注入角色为 TEACHER（用户 id=2 张同学），既是项目 10 的 OWNER 可管理采集，
 *       又是记录作者可编辑/绑定快照/转项目。
 *       截图仅用于布局/权限/交互可见性验证，不冒充真实后端联调证据。
 */

import { chromium } from 'playwright-core'
import { mkdirSync, writeFileSync, createReadStream, existsSync, statSync } from 'node:fs'
import { createServer } from 'node:http'
import path from 'node:path'

const BASE = process.env.W43_BASE_URL || 'http://127.0.0.1:4173'
const EDGE =
  process.env.W43_EDGE || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const OUT_ROOT =
  process.env.W43_OUT || path.resolve(process.cwd(), '..', 'docs', 'evidence', '2026-W43', 'frontend')

const DIST = path.resolve(process.cwd(), 'dist')
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.woff': 'font/woff'
}

// 自托管 dist：避免依赖外部 preview 进程在工具调用结束后被回收。
const staticServer = createServer((req, res) => {
  const pathname = decodeURIComponent(new URL(req.url, BASE).pathname)
  let file = path.join(DIST, pathname)
  if (!file.startsWith(DIST)) {
    res.writeHead(403)
    res.end()
    return
  }
  if (!existsSync(file) || statSync(file).isDirectory()) file = path.join(DIST, 'index.html')
  res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' })
  createReadStream(file).pipe(res)
})
await new Promise((resolve) => staticServer.listen(4173, '127.0.0.1', resolve))

const VIEWPORTS = [
  { name: '1440x900', width: 1440, height: 900 },
  { name: '1280x800', width: 1280, height: 800 },
  { name: '390x844', width: 390, height: 844 }
]

// mock 锚点：用户 id=2「张同学」，primaryRoleKey=TEACHER，项目 10 OWNER。
const AUTH = {
  token: 'w43-mock-token',
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
//   记录 301 DRAFT（作者 2）、302 IN_REVIEW、303 CHANGES_REQUESTED（作者 3）、
//   305 PUBLISHED（可转项目）、306 已转项目、307 REVISING（旧发布版仍有效）、308 学习草稿
//   计划 401 FILE READY、402 MANUAL READY、404 ARCHIVED
//   批次 601 COMPLETED（含快照）、602 REVIEW_REQUIRED、603 MANUAL COMPLETED、604 FAILED
//   导入 701 CONFIRMED、702 MAPPING_REQUIRED
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

async function goto(page, url, selector, timeout = 15000) {
  await page.goto(`${BASE}${url}`, { waitUntil: 'networkidle' })
  await page.waitForSelector(selector, { timeout })
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

  // 1. 侧栏入口「学习实验台 > 实验记录」
  await goto(page, '/experiment/records', '.record-card')
  if (vp.width <= 1024) {
    await page.locator('.mobile-menu-button').click()
    await page.waitForSelector('.mobile-nav-drawer', { timeout: 10000 })
    await page.waitForTimeout(400)
    await shot(page, vp, 'record-nav')
    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)
  } else {
    await shot(page, vp, 'record-nav')
  }

  // 2. 记录列表（范围/状态/项目/关键字筛选 + 卡片 + 分页）
  await goto(page, '/experiment/records', '.record-card')
  await shot(page, vp, 'record-list', { fullPage: fullPageOnMobile(vp) })

  // 3. 列表 loading（mockState=loading，骨架屏）
  await page.goto(`${BASE}/experiment/records?mockState=loading`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.region-state--loading', { timeout: 5000 })
  await shot(page, vp, 'record-list-loading')

  // 4. 列表空状态（mockState=empty）
  await goto(page, '/experiment/records?mockState=empty', '.region-state--empty')
  await shot(page, vp, 'record-list-empty')

  // 5. 列表错误状态（mockState=error，含 traceId）
  await goto(page, '/experiment/records?mockState=error', '.region-state--error')
  await shot(page, vp, 'record-list-error')

  // 5. 详情：已发布记录（发布版 + 反馈 + 快照 + 转项目入口）
  await goto(page, '/experiment/records/305', '.record-detail-page .page-title')
  await page.waitForTimeout(500)
  await shot(page, vp, 'record-detail-published', { fullPage: fullPageOnMobile(vp) })

  // 6. 详情：待修改记录（不可变原修订 + 新可编辑修订 + 只读提示）
  await goto(page, '/experiment/records/303', '.record-detail-page .page-title')
  await page.waitForTimeout(500)
  await shot(page, vp, 'record-detail-changes', { fullPage: fullPageOnMobile(vp) })

  // 7. 详情：修订中（旧发布版仍可读 + 当前 EDITABLE + 绑定快照）
  await goto(page, '/experiment/records/307', '.record-detail-page .page-title')
  await page.waitForTimeout(500)
  await shot(page, vp, 'record-detail-revising', { fullPage: fullPageOnMobile(vp) })

  // 8. 详情权限态（mockState=permission → 404 隐藏资源）
  await goto(page, '/experiment/records/301?mockState=permission', '.region-state--permission')
  await shot(page, vp, 'record-detail-permission')

  // 9. 编辑器：固定模板版本 + 结构化字段 + 实验上下文
  await goto(page, '/experiment/records/308/edit/910', '.record-editor-page')
  await page.waitForTimeout(600)
  await shot(page, vp, 'record-editor', { fullPage: fullPageOnMobile(vp) })

  // 10. 新建记录（模板版本选择 + 空表单）
  await goto(page, '/experiment/records/new', '.record-editor-page')
  await page.waitForTimeout(600)
  await shot(page, vp, 'record-create', { fullPage: fullPageOnMobile(vp) })

  // 11. 保存冲突对话框（mockState=conflict + 保存草稿 → 409 提示刷新）
  await goto(page, '/experiment/records/308/edit/910?mockState=conflict', '.record-editor-page')
  await page.waitForTimeout(400)
  await page.locator('button').filter({ hasText: '保存草稿' }).first().click()
  await page.waitForSelector('.el-message-box', { timeout: 10000 })
  await page.waitForTimeout(500)
  await shot(page, vp, 'record-save-conflict')
  await page.locator('.el-message-box__btns button').first().click()
  await page.waitForTimeout(300)

  // 12. 转项目草案对话框（已发布记录 305）
  await goto(page, '/experiment/records/305', '.record-detail-page .page-title')
  await page.locator('button').filter({ hasText: '转为项目草案' }).first().click()
  await page.waitForSelector('.convert-dialog', { timeout: 10000 })
  await page.waitForTimeout(500)
  await shot(page, vp, 'record-convert-dialog')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)

  // 13. 绑定采集快照对话框（修订中记录 307）
  await goto(page, '/experiment/records/307', '.record-detail-page .page-title')
  await page.locator('button').filter({ hasText: '绑定采集快照' }).first().click()
  await page.waitForSelector('.snapshot-dialog', { timeout: 10000 })
  await page.waitForTimeout(700)
  await shot(page, vp, 'record-snapshot-dialog')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)

  // 14. 项目数据采集入口（计划列表：来源/负责人/状态/最近批次）
  await goto(page, '/projects/10/collection', '.plan-card')
  await shot(page, vp, 'collection-plans', { fullPage: fullPageOnMobile(vp) })

  // 15. 计划列表空状态
  await goto(page, '/projects/10/collection?mockState=empty', '.region-state--empty')
  await shot(page, vp, 'collection-plans-empty')

  // 16. 计划详情（指标定义 + 批次列表 + 状态动作）
  await goto(page, '/collection/plans/401', '.detail-grid')
  await page.waitForTimeout(500)
  await shot(page, vp, 'collection-plan-detail', { fullPage: fullPageOnMobile(vp) })

  // 17. 批次详情：待复核（CSV 原件、记录摘要、完成批次入口）
  await goto(page, '/collection/batches/602', '.detail-grid')
  await page.waitForTimeout(500)
  await shot(page, vp, 'collection-batch-review', { fullPage: fullPageOnMobile(vp) })

  // 18. 批次详情：已完成（checksum 冻结 + FULL_BATCH 快照）
  await goto(page, '/collection/batches/601', '.detail-grid')
  await page.waitForTimeout(500)
  await shot(page, vp, 'collection-batch-completed', { fullPage: fullPageOnMobile(vp) })

  // 19. 批次详情：人工观测（方法与仪器 + 只读证据）
  await goto(page, '/collection/batches/603', '.detail-grid')
  await page.waitForTimeout(500)
  await shot(page, vp, 'collection-batch-manual', { fullPage: fullPageOnMobile(vp) })

  // 20. 人工观测修改历史（追加修订：旧值/新值/原因/操作者/时间）
  await page.locator('button').filter({ hasText: '修改历史' }).first().click()
  const historyDialog = page.locator('.el-dialog').filter({ hasText: '人工观测修改历史' })
  await historyDialog.waitFor({ state: 'visible', timeout: 10000 })
  await page.waitForTimeout(600)
  await shot(page, vp, 'collection-manual-history')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)

  // 21. CSV 导入：前 50 行预览 + 列/单位/时间映射 + 确认
  await goto(page, '/collection/imports/702', '.import-steps')
  await page.waitForTimeout(600)
  await shot(page, vp, 'collection-import-mapping', { fullPage: fullPageOnMobile(vp) })

  // 22. 新增人工观测对话框（MANUAL 计划新建批次 → 录入方法/仪器/单位/时间）
  await goto(page, '/collection/plans/402', '.detail-grid')
  await page.locator('button').filter({ hasText: '新建批次' }).first().click()
  await page.waitForSelector('.el-message-box', { timeout: 10000 })
  await page.waitForTimeout(400)
  await page.locator('.el-message-box__btns .el-button--primary').first().click()
  await page.waitForURL(/\/collection\/batches\/\d+/, { timeout: 15000 })
  await page.waitForSelector('.detail-grid', { timeout: 15000 })
  await page.locator('button').filter({ hasText: '新增人工观测' }).first().click()
  await page.waitForSelector('.manual-dialog', { timeout: 10000 })
  await page.waitForTimeout(600)
  await shot(page, vp, 'collection-manual-dialog')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)

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
staticServer.close()

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
