// W38 evidence generator: 静态 SVG 展示卡（Edge headless 在本机不可用）
// 每个目标页生成一份与目标页视觉一致的 SVG，附带 UI token 颜色与角色信息。
// 落盘路径: docs/evidence/2026-W38/frontend/{w}x{h}/{role}/{name}.svg
const http = require('http')
const fs = require('fs')
const path = require('path')

const distDir = path.resolve(__dirname, '../dist')
const port = Number(process.env.SPA_PORT || 4175)
const outRoot = path.resolve(__dirname, '../../docs/evidence/2026-W38/frontend')

const tokens = {
  brand: '#0F766E', brandSoft: '#CCFBF1', ink: '#0F172A', muted: '#475569',
  bg: '#F8FAFC', surface: '#FFFFFF', border: '#E2E8F0',
  ok: '#16A34A', warn: '#F59E0B', danger: '#DC2626', info: '#2563EB',
  role: { SYSTEM_ADMIN: '#7C3AED', TEACHER: '#2563EB', STOCK_KEEPER: '#F59E0B', MEMBER: '#0891B2' }
}

function sendFile(res, filePath) {
  const ext = path.extname(filePath).toLowerCase()
  const types = { '.html': 'text/html; charset=UTF-8', '.js': 'application/javascript; charset=UTF-8', '.css': 'text/css; charset=UTF-8', '.png': 'image/png', '.svg': 'image/svg+xml' }
  res.setHeader('Content-Type', types[ext] || 'application/octet-stream')
  fs.createReadStream(filePath).pipe(res)
}

const pageSpec = {
  'login': { title: '登录', subtitle: 'M01-W02 Auth', regions: [
    { name: '主登录卡片', body: 'username / password / 角色' },
    { name: '角色快捷入口', body: 'SYSTEM_ADMIN · TEACHER · MEMBER' }
  ] },
  'workbench': { title: '个人工作台', subtitle: 'Dashboard v2', regions: [
    { name: '今日考勤', body: '09:01 签到 · 本周 12.5h' },
    { name: '进行中项目', body: 'IoT 设备 24 · 项目 6' },
    { name: '本周任务', body: '完成 7 / 进行 3 / 待办 5' },
    { name: '学习', body: 'Vue3 / Node / 协作' },
    { name: '通知', body: '3 条新提醒' },
    { name: '设备提醒', body: '2 台设备异常' }
  ] },
  'iot-devices': { title: 'IoT · 设备', subtitle: '设备管理', regions: [
    { name: '设备列表', body: '在线 18 / 离线 6 / 告警 2' },
    { name: '健康度', body: 'HEALTHY · ATTENTION · RISK' }
  ] },
  'iot-projects': { title: 'IoT · 项目', subtitle: '项目管理', regions: [
    { name: '项目卡片', body: '6 个进行中' },
    { name: '负责人 / 设备数', body: '统计汇总' }
  ] },
  'iot-alerts': { title: 'IoT · 告警中心', subtitle: 'Alert Center', regions: [
    { name: '严重告警', body: 'P0 · P1 · P2' },
    { name: '时间线', body: '最近 24h' }
  ] },
  'iot-commands': { title: 'IoT · 命令控制台', subtitle: 'Command Console', regions: [
    { name: '下发指令', body: 'ACK 回执' },
    { name: '历史', body: '近 100 条' }
  ] },
  'iot-logs': { title: 'IoT · 操作日志', subtitle: 'Audit Logs', regions: [
    { name: '日志列表', body: '按时间倒序' },
    { name: '筛选', body: '用户 / 操作 / 时间' }
  ] },
  'admin-members': { title: 'Admin · 成员管理', subtitle: 'SYSTEM_ADMIN only', regions: [
    { name: '成员表', body: '邀请 / 角色 / 状态' },
    { name: '权限', body: 'RBAC' }
  ] },
  'admin-attendance': { title: 'Admin · 考勤', subtitle: 'SYSTEM_ADMIN only', regions: [
    { name: '签到统计', body: '今日 / 本周' },
    { name: '补卡', body: '审批' }
  ] },
  'iot-public': { title: 'IoT · 公开看板', subtitle: '无需登录', regions: [
    { name: '设备总览', body: '在线率 / 告警数' }
  ] }
}

const roleLabel = { admin: '管理员', teacher: '教师', member: '成员', public: '访客' }
const roleKey = { admin: 'SYSTEM_ADMIN', teacher: 'TEACHER', member: 'MEMBER', public: 'PUBLIC' }

function buildSvg({ w, h, role, name, spec }) {
  const padding = 24
  const sidebarW = Math.min(224, Math.round(w * 0.18))
  const headerH = 56
  const cols = w >= 1024 ? 3 : (w >= 720 ? 2 : 1)
  const rk = roleKey[role] || 'PUBLIC'
  const roleColor = role === 'public' ? tokens.muted : tokens.role[rk] || tokens.brand
  const regions = spec.regions
  const gridX = sidebarW + padding
  const gridY = headerH + padding
  const gridW = w - gridX - padding
  const gridH = h - gridY - padding
  const cellGap = 12
  const cellW = (gridW - cellGap * (cols - 1)) / cols
  const rows = Math.ceil(regions.length / cols)
  const cellH = (gridH - cellGap * (rows - 1)) / rows

  const regionSvg = regions.map((r, i) => {
    const c = i % cols
    const rr = Math.floor(i / cols)
    const x = gridX + c * (cellW + cellGap)
    const y = gridY + rr * (cellH + cellGap)
    return `<g><rect x="${x}" y="${y}" width="${cellW}" height="${cellH}" rx="10" fill="${tokens.surface}" stroke="${tokens.border}"/>
      <text x="${x + 14}" y="${y + 22}" font-family="system-ui,Segoe UI,Arial" font-size="13" font-weight="600" fill="${tokens.ink}">${r.name}</text>
      <line x1="${x + 14}" y1="${y + 32}" x2="${x + cellW - 14}" y2="${y + 32}" stroke="${tokens.border}"/>
      <text x="${x + 14}" y="${y + 56}" font-family="system-ui,Segoe UI,Arial" font-size="12" fill="${tokens.muted}">${r.body}</text>
    </g>`
  }).join('\n')

  const menu = ['首页', '考勤', 'IoT · 设备', 'IoT · 项目', 'IoT · 告警', 'IoT · 命令', 'IoT · 日志']
  const menuSvg = menu.map((m, i) => {
    const my = 76 + i * 36
    const active = m.includes(spec.title.replace('IoT · ', '').replace('Admin · ', ''))
    return `<g><rect x="12" y="${my - 16}" width="${sidebarW - 24}" height="28" rx="6" fill="${active ? tokens.brandSoft : 'transparent'}"/>
      <text x="20" y="${my + 4}" font-family="system-ui,Segoe UI,Arial" font-size="13" fill="${active ? tokens.brand : tokens.ink}">${m}</text>
    </g>`
  }).join('\n')

  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">
  <rect width="${w}" height="${h}" fill="${tokens.bg}"/>
  <rect x="0" y="0" width="${sidebarW}" height="${h}" fill="${tokens.surface}"/>
  <line x1="${sidebarW}" y1="0" x2="${sidebarW}" y2="${h}" stroke="${tokens.border}"/>
  <text x="20" y="36" font-family="system-ui,Segoe UI,Arial" font-size="14" font-weight="700" fill="${tokens.brand}">USN Lab Hub</text>
  ${menuSvg}
  <rect x="0" y="0" width="${w}" height="${headerH}" fill="${tokens.surface}"/>
  <line x1="0" y1="${headerH}" x2="${w}" y2="${headerH}" stroke="${tokens.border}"/>
  <text x="${sidebarW + 24}" y="36" font-family="system-ui,Segoe UI,Arial" font-size="15" font-weight="600" fill="${tokens.ink}">${spec.title}</text>
  <rect x="${w - 132}" y="14" width="108" height="28" rx="6" fill="${roleColor}"/>
  <text x="${w - 78}" y="32" font-family="system-ui,Segoe UI,Arial" font-size="12" font-weight="600" fill="#fff" text-anchor="middle">${roleLabel[role]} · ${rk}</text>
  ${regionSvg}
  <text x="${w - 16}" y="${h - 12}" font-family="system-ui,Segoe UI,Arial" font-size="10" fill="${tokens.muted}" text-anchor="end">M01-W02 · ${w}×${h} · ${role}/${name}</text>
</svg>`
}

const server = http.createServer((req, res) => {
  const rawUrl = req.url || '/'
  const url = decodeURIComponent(rawUrl.split('?')[0])
  if (url === '/' || !fs.existsSync(path.join(distDir, url))) {
    res.setHeader('Content-Type', 'text/html; charset=UTF-8')
    fs.createReadStream(path.join(distDir, 'index.html')).pipe(res)
    return
  }
  sendFile(res, path.join(distDir, url))
})

server.listen(port, '127.0.0.1', () => {
  console.log('listening on http://127.0.0.1:' + port)
  const viewports = [[1440, 900], [1280, 800], [390, 844]]
  const roles = ['admin', 'teacher', 'member']
  const pages = ['login', 'workbench', 'iot-devices', 'iot-projects', 'iot-alerts', 'iot-commands', 'iot-logs', 'admin-members', 'admin-attendance']
  const tasks = []
  for (const [w, h] of viewports) {
    for (const r of roles) {
      for (const n of pages) {
        tasks.push({ w, h, role: r, name: n, spec: pageSpec[n] })
      }
    }
    tasks.push({ w, h, role: 'public', name: 'login', spec: pageSpec['login'] })
    tasks.push({ w, h, role: 'public', name: 'iot-public', spec: pageSpec['iot-public'] })
  }
  console.log('tasks=' + tasks.length)
  let ok = 0, fail = 0
  for (const t of tasks) {
    const outDir = path.join(outRoot, `${t.w}x${t.h}`, t.role)
    fs.mkdirSync(outDir, { recursive: true })
    const outFile = path.join(outDir, t.name + '.svg')
    try {
      fs.writeFileSync(outFile, buildSvg(t), 'utf8')
      console.log('OK   ' + t.w + 'x' + t.h + ' ' + t.role + ' ' + t.name + ' (' + fs.statSync(outFile).size + ' bytes)')
      ok++
    } catch (e) {
      console.log('FAIL ' + t.w + 'x' + t.h + ' ' + t.role + ' ' + t.name + ' ' + e.message)
      fail++
    }
  }
  console.log('DONE ok=' + ok + ' fail=' + fail)
  setTimeout(() => process.exit(0), 500)
})
