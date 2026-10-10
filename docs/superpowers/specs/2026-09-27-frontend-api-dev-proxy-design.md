# 前端开发环境 API 代理修复设计

## 背景

第 3 周前端新增 `/api/projects` 和 `/api/workbench/overview` 请求，但 Vite 开发服务器目前只代理 `/api/iot`。在 `VITE_USE_MOCK=false` 且 `VITE_API_BASE_URL` 为空时，新请求会落到 Vite 服务而不能转发至 Java 后端。

## 设计

将 `frontend/vite.config.js` 中的 `/api/iot` 代理入口扩大为 `/api`，并保留 `changeOrigin: true`。`/api/iot` 是 `/api` 的子路径，因此旧 IoT 开发代理继续生效。

三组开发代理 `/api`、`/usnhub`、`/admin` 统一读取 `VITE_API_PROXY_TARGET`，未配置时默认使用 `http://localhost:8080`。本机同时运行 AgentPlatform 时，将 USN 后端设为 8081，并在启动前端时设置 `VITE_API_PROXY_TARGET=http://localhost:8081`。

不新增后端 CORS，不设置硬编码的生产 API 地址，也不修改 API、MQTT、数据库、权限或 UI 字段契约。

## 数据流与错误处理

浏览器请求 `/api/**`、`/usnhub/**` 或 `/admin/**`，由 Vite 开发服务器转发至配置的本机后端。后端不可用或返回业务错误时，沿用现有 Axios 拦截器和页面错误态，不由代理伪造响应。

## 验证

1. 静态确认 `/api`、`/usnhub`、`/admin` 均使用同一个可配置代理目标，默认值为 `http://localhost:8080`。
2. 运行前端契约测试，确认 mock 和字段适配未受影响。
3. 执行 `npm run build`，确认生产构建成功。
4. 真实联调时设置 `VITE_USE_MOCK=false` 和 `VITE_API_PROXY_TARGET=http://localhost:8081`，由浏览器 Network 确认登录、`/api/projects` 和 `/api/workbench/overview` 均返回 USN 后端响应。
