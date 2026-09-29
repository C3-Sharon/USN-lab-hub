import { chromium } from 'playwright-core'
import fs from 'node:fs'
import path from 'node:path'

const BASE = 'http://localhost:5173'
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const OUT = path.resolve(process.cwd(), '..', 'docs', 'evidence', '2026-W39', 'frontend-real')
const VIEWPORTS = [
  { name: '1440x900', width: 1440, height: 900 },
  { name: '1280x800', width: 1280, height: 800 },
  { name: '390x844', width: 390, height: 844 }
]

const code = 'W39-UI-' + Date.now().toString().slice(-6)
const results = []
const consoleErrors = []
const httpErrors = []

function rec(name, pass, detail) {
  results.push({ check: name, pass: !!pass, detail })
  console.log(`[${pass ? 'PASS' : 'FAIL'}] ${name} :: ${detail}`)
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function shoot(page, vp, name) {
  await page.setViewportSize({ width: vp.width, height: vp.height })
  await sleep(500)
  const dir = path.join(OUT, vp.name)
  fs.mkdirSync(dir, { recursive: true })
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth + 1
  )
  await page.screenshot({ path: path.join(dir, `${name}.png`) })
  return overflow
}

const browser = await chromium.launch({ executablePath: EDGE, headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await context.newPage()

page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(m.text())
})
page.on('response', (r) => {
  if (r.status() >= 400) httpErrors.push(`${r.status()} ${r.request().method()} ${r.url()}`)
})

let projectId = null
try {
  // ---------- 1. real UI login ----------
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="账号 / 学工号"]', 'admin')
  await page.fill('input[placeholder="密码"]', 'admin123')
  await page.getByRole('button', { name: '登录系统' }).click()
  await page.waitForURL('**/dashboard', { timeout: 20000 })
  await page.waitForSelector('.region--projects', { timeout: 20000 })
  const roleTag = (await page.locator('.role-tag').first().innerText().catch(() => '')) || ''
  rec('real-login', true, `url=${page.url()} roleTag=${roleTag.trim()}`)

  // ---------- 2. workbench projects region from real API ----------
  await page.waitForSelector('.project-mini-card', { timeout: 20000 })
  const miniCount = await page.locator('.project-mini-card').count()
  rec('workbench-real-projects', miniCount > 0, `miniCards=${miniCount}`)

  // ---------- 3. project list from real API ----------
  await page.goto(`${BASE}/projects`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.project-card', { timeout: 20000 })
  const listCount = await page.locator('.project-card').count()
  rec('project-list-real', listCount > 0, `cards=${listCount}`)

  // ---------- 4. create project through the UI (real write) ----------
  await page.getByRole('button', { name: '创建项目', exact: true }).click()
  const createDialog = page.locator('.el-dialog').filter({ hasText: '创建项目' }).first()
  await createDialog.waitFor({ state: 'visible', timeout: 10000 })
  await createDialog.locator('input[placeholder^="如 PROJ-001"]').fill(code)
  await createDialog.locator('input[placeholder="2-80 个字符"]').fill('W39 前端联调项目')
  await createDialog.locator('textarea').fill('真实后端联调：前端创建项目')
  await createDialog.locator('input[placeholder^="如 hardware_project"]').fill('hardware_project')
  await createDialog.getByRole('button', { name: '创建', exact: true }).click()
  await page.waitForURL(/\/projects\/\d+$/, { timeout: 20000 })
  await page.waitForSelector('.page-title', { timeout: 20000 })
  projectId = Number(page.url().match(/\/projects\/(\d+)$/)[1])
  const title = (await page.locator('.page-title').first().innerText()).trim()
  rec('create-project-via-ui', !!projectId, `id=${projectId} code=${code} title=${title}`)

  // ---------- 5. add member through the UI (real write) ----------
  await page.getByRole('button', { name: '添加成员', exact: true }).click()
  const memberDialog = page.locator('.el-dialog').filter({ hasText: '添加项目成员' }).first()
  await memberDialog.waitFor({ state: 'visible', timeout: 10000 })
  await memberDialog.locator('input[placeholder="输入成员学工号"]').fill('20260001')
  await memberDialog.getByRole('button', { name: '添加', exact: true }).click()
  await page.waitForSelector('.el-message--success', { timeout: 15000 })
  await sleep(800)
  const rowTexts = await page.locator('.el-table__row').allInnerTexts()
  const hasMember = rowTexts.some((t) => t.includes('20260001'))
  rec('add-member-via-ui', hasMember, `rows=${rowTexts.length} contains20260001=${hasMember}`)

  const memberCountText = (await page.locator('.detail-card').nth(1).innerText().catch(() => '')) || ''
  rec('detail-member-table', /2\s*人/.test(memberCountText), `memberCardText="${memberCountText.replace(/\s+/g, ' ').trim().slice(0, 80)}"`)

  // ---------- 6. three-viewport captures ----------
  const overflows = []
  for (const vp of VIEWPORTS) {
    await page.setViewportSize({ width: vp.width, height: vp.height })

    await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.region--projects', { timeout: 20000 })
    await sleep(600)
    overflows.push(`workbench@${vp.name}=${await shoot(page, vp, 'workbench')}`)

    await page.goto(`${BASE}/projects`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.project-card', { timeout: 20000 })
    await sleep(600)
    overflows.push(`list@${vp.name}=${await shoot(page, vp, 'project-list')}`)

    await page.goto(`${BASE}/projects/${projectId}`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.page-title', { timeout: 20000 })
    await sleep(600)
    overflows.push(`detail@${vp.name}=${await shoot(page, vp, 'project-detail')}`)

    await page.getByRole('button', { name: '创建项目', exact: true }).click().catch(async () => {
      await page.goto(`${BASE}/projects`, { waitUntil: 'networkidle' })
      await page.getByRole('button', { name: '创建项目', exact: true }).click()
    })
    await page.locator('.el-dialog').filter({ hasText: '创建项目' }).first().waitFor({ state: 'visible', timeout: 10000 })
    await sleep(400)
    overflows.push(`create-dialog@${vp.name}=${await shoot(page, vp, 'project-create-dialog')}`)
    await page.keyboard.press('Escape')
    await sleep(400)

    await page.goto(`${BASE}/projects/${projectId}`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.page-title', { timeout: 20000 })
    await page.getByRole('button', { name: '添加成员', exact: true }).click()
    await page.locator('.el-dialog').filter({ hasText: '添加项目成员' }).first().waitFor({ state: 'visible', timeout: 10000 })
    await sleep(400)
    overflows.push(`add-member@${vp.name}=${await shoot(page, vp, 'project-add-member')}`)
    await page.keyboard.press('Escape')
    await sleep(300)
  }

  const anyOverflow = overflows.some((o) => o.endsWith('=true'))
  rec('no-horizontal-overflow', !anyOverflow, overflows.join(' '))
} catch (err) {
  rec('script-execution', false, `ERROR: ${err.message}`)
} finally {
  const apiErrors = httpErrors.filter((e) => e.includes('/api/'))
  rec('no-api-http-errors', apiErrors.length === 0, apiErrors.length ? apiErrors.join(' | ') : 'none')
  fs.mkdirSync(OUT, { recursive: true })
  fs.writeFileSync(
    path.join(OUT, 'e2e-result.json'),
    JSON.stringify(
      { code, projectId, pass: results.filter((r) => r.pass).length, total: results.length, results, consoleErrors, httpErrors },
      null,
      2
    ),
    'utf8'
  )
  console.log('\n==== SUMMARY ====')
  console.log(`PASS=${results.filter((r) => r.pass).length} / ${results.length}`)
  console.log(`PROJECT_ID=${projectId} CODE=${code}`)
  console.log(`HTTP_ERRORS=${httpErrors.length} CONSOLE_ERRORS=${consoleErrors.length}`)
  await browser.close()
}