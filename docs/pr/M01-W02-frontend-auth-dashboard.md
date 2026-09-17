# M01-W02 Frontend：全局角色、权限与个人工作台

## 改了什么

- 同步最新 `dev`，接入第二周产品契约和后端实现。
- 登录后统一进入个人工作台，支持 `roles: [{ roleKey, roleName }]` 多角色响应。
- 请求同时发送 `Authorization: Bearer` 与兼容 `token` Header。
- 按响应 `reason` 区分 401、账号禁用和 403，不把数字 `code` 当作原因。
- 使用 `GET /api/workbench/overview` 加载六个区域，按后端扁平 `state` 结构展示 READY、NOT_AVAILABLE 和 ERROR。
- 移除不存在的 `/api/workbench/device-reminder` 请求，设备提醒从聚合响应读取。
- 默认关闭 mock；仅显式设置 `VITE_USE_MOCK=true` 时启用。
- 侧栏和路由按角色过滤，硬件指令入口仅 SYSTEM_ADMIN 可见。
- TEACHER 可以查看成员与考勤页面，但成员新增、编辑、启用和禁用入口不可见。
- 前端管理页面调整为 `/members`、`/attendance`，避免与后端 `/admin/**` 代理冲突。
- 落地 UI Token、区域状态组件和姓名/学工号首字头像占位。
- 清理一次性截图脚本和人工 SVG，改为 9 张真实浏览器 PNG 证据。

## 如何验证

~~~powershell
cd frontend
npm ci
node src/api/__tests__/w38-contract-test.mjs
node src/api/__tests__/sse-test-node.mjs
npm run build
~~~

验证结果：

- W38 契约测试：36/36 通过。
- SSE 回归测试：23/23 通过。
- Vite 生产构建：通过。
- 真实浏览器检查：1440×900、1280×800、390×844 均无横向溢出。
- TEACHER 三个视口下成员写操作按钮均为 0。

视觉证据见 `docs/evidence/2026-W38/frontend/README.md`。

## 契约影响

- API：不新增接口；按冻结契约消费 `GET /api/workbench/overview`。
- 鉴权：兼容双 Header；401/403 按 `reason` 处理。
- 权限：按 SYSTEM_ADMIN / TEACHER / STOCK_KEEPER / MEMBER 过滤路由和菜单。
- 前端路由：成员与考勤页面从 `/admin/*` 调整为 `/members`、`/attendance`，后端 `/admin/**` API 不变。
- MQTT、数据库：无变化。

## 联调重点

1. 用 SYSTEM_ADMIN、TEACHER、STOCK_KEEPER、MEMBER 分别登录，确认角色标签、菜单和首页区域。
2. 验证 TEACHER 可以查看成员列表，但不能看到或调用成员写操作。
3. 验证 TOKEN_MISSING、TOKEN_INVALID、TOKEN_EXPIRED、ACCOUNT_DISABLED 和 ACCESS_DENIED。
4. 验证首页直接消费后端 `state` 扁平结构，设备提醒不再发第二个请求。
5. 验证 `/api/iot/public/**` SSE 保持公开可用。

## 已知风险

- 构建产物主 JS 约 2.47 MB，Vite 有 chunk size 警告；不阻塞本周契约联调，后续按页面拆分异步路由。
- 浏览器截图使用契约同形的请求拦截数据，只证明布局和权限可见性；真实后端联调仍需按周任务清单执行。
