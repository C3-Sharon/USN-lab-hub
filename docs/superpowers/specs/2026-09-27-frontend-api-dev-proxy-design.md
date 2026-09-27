# 前端开发环境 API 代理修复设计

## 背景

第 3 周前端新增 `/api/projects` 和 `/api/workbench/overview` 请求，但 Vite 开发服务器目前只代理 `/api/iot`。在 `VITE_USE_MOCK=false` 且 `VITE_API_BASE_URL` 为空时，新请求会落到 Vite 服务而不能转发至 Java 后端。

## 设计

将 `frontend/vite.config.js` 中的 `/api/iot` 代理入口扩大为 `/api`，目标仍为 `http://localhost:8080`，并保留 `changeOrigin: true`。`/api/iot` 是 `/api` 的子路径，因此旧 IoT 开发代理继续生效；`/usnhub` 和 `/admin` 保持不变。

不新增后端 CORS，不设置硬编码的生产 API 地址，也不修改 API、MQTT、数据库、权限或 UI 字段契约。

## 数据流与错误处理

浏览器请求 `/api/**`，由 Vite 开发服务器转发至本机 8080 端口。后端不可用或返回业务错误时，沿用现有 Axios 拦截器和页面错误态，不由代理伪造响应。

## 验证

1. 静态确认 `/api`、`/usnhub`、`/admin` 均指向本机后端。
2. 运行前端契约测试，确认 mock 和字段适配未受影响。
3. 执行 `npm run build`，确认生产构建成功。
4. 真实联调时设置 `VITE_USE_MOCK=false`，由浏览器 Network 确认 `/api/projects` 和 `/api/workbench/overview` 返回后端响应。
