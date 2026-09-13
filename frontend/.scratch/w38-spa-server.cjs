// W38 三视口截图 SPA server
// - 静态服务 frontend/dist
// - history 模式 SPA fallback
// - /seed-* 路由直接返回注入 token + userInfo 的 HTML
const http = require('http')
const fs = require('fs')
const path = require('path')

const distDir = path.resolve(__dirname, '../dist')
const port = Number(process.env.SPA_PORT || 4174)
const log = (...a) => process.stdout.write('[spa] ' + a.join(' ') + '\n')

function sendFile(res, filePath) {
  const ext = path.extname(filePath).toLowerCase()
  const types = { '.html': 'text/html; charset=UTF-8', '.js': 'application/javascript; charset=UTF-8', '.css': 'text/css; charset=UTF-8', '.png': 'image/png', '.svg': 'image/svg+xml' }
  res.setHeader('Content-Type', types[ext] || 'application/octet-stream')
  fs.createReadStream(filePath).pipe(res)
}

function seedHtml(role, label) {
  // role: admin/teacher/member/keeper/public —— 简化 key，
  // permission.js 的兼容层会把 { role: 'admin' } 转成 ROLE.SYSTEM_ADMIN
  return `<!doctype html><html><head><meta charset="utf-8"><title>seed ${label}</title></head><body><script>
    const roleMap = { admin: 'SYSTEM_ADMIN', teacher: 'TEACHER', keeper: 'STOCK_KEEPER', member: 'MEMBER' };
    const rk = roleMap['${role}'] || 'MEMBER';
    const auth = {
      token: 'M01-W02-' + rk + '-SEED-TOKEN',
      userInfo: {
        id: 1,
        username: '${role}',
        primaryRoleKey: rk,
        primaryRoleName: '${label}',
        roles: [rk]
      },
      todayAttendance: { todayStatus: 1, weekHours: 12.5, semesterHours: 156, todayRecords: [{ inTime: '09:01', outTime: null, durationMins: 0 }] }
    };
    localStorage.setItem('usn_lab_hub_auth', JSON.stringify(auth));
    localStorage.setItem('token', auth.token);
    setTimeout(() => { window.location.replace('/dashboard'); }, 200);
  </script>Seeding ${role}...</body></html>`
}

const server = http.createServer((req, res) => {
  const rawUrl = req.url || '/'
  const url = decodeURIComponent(rawUrl.split('?')[0])

  if (url.startsWith('/seed-')) {
    const role = url.replace('/seed-', '').replace(/\.html$/, '')
    const map = { admin: '系统管理员', teacher: '老师', keeper: '库存管理员', member: '普通成员' }
    res.setHeader('Content-Type', 'text/html; charset=UTF-8')
    res.end(seedHtml(role, map[role] || role))
    return
  }

  const filePath = path.join(distDir, url)
  if (url === '/' || !fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
    res.setHeader('Content-Type', 'text/html; charset=UTF-8')
    fs.createReadStream(path.join(distDir, 'index.html')).pipe(res)
    return
  }
  sendFile(res, filePath)
})

server.listen(port, () => log(`listening on http://127.0.0.1:${port} (dist=${distDir})`))
