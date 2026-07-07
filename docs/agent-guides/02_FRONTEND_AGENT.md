# Frontend Agent Guide / 前端 Agent 指南

> 中文说明：本文件给前端负责人和其 agent 使用。前端实现必须服从产品字段、API 契约和最小纵向切片。允许先 mock，但 mock 必须和 API 契约一致。

## 1. Role / 角色

You are the frontend implementation agent for USN Lab Hub IoT.

中文角色：你负责 Vue 3 + Element Plus 前端页面实现、接口联调和演示体验。

## 2. Required Reading / 必读文件

Before coding, read:

- `00_SHARED_AGENTS.md`
- `02_FRONTEND_AGENT.md`
- `04_API_CONTRACT.md`
- `06_VERTICAL_SLICE_PLAN.md`
- `07_TESTING_AND_ACCEPTANCE.md`

If a task involves MQTT behavior or command ACK display, also read:

- `05_MQTT_CONTRACT.md`

## 3. Tech Stack Rule / 技术栈规则

- Use Vue 3.
- Use Element Plus.
- Use Vue Router.
- Use Axios through a shared API wrapper.
- Use ECharts for charts if needed.
- Do not replace the frontend framework.
- Do not scatter raw axios calls inside view components.

中文：

- 使用现有 Vue 3 + Element Plus 技术栈。
- IoT API 统一封装在 `frontend/src/api/iot.js`。
- 图表优先使用 ECharts。
- 不更换框架，不在页面里散写 axios。

## 4. Frontend Scope / 页面范围

MVP pages:

- IoT overview / IoT 总览
- Project list / 项目列表
- Project detail / 项目详情
- Device list / 设备列表
- Device detail / 设备详情
- Telemetry dashboard / 数据看板
- Alert center / 告警中心
- Command console / 控制台
- Operation logs / 操作日志
- Visitor display / 访客展示

Recommended paths:

```text
/iot/overview
/iot/projects
/iot/projects/:id
/iot/devices
/iot/devices/:id
/iot/alerts
/iot/commands
/iot/logs
/iot/public
```

## 5. Mock First Rule / 先 Mock 规则

If backend APIs are not ready, use mock data that exactly matches `04_API_CONTRACT.md`.

中文：

- 后端未完成时可以先 mock。
- mock 字段必须来自 API 契约。
- 不允许为了页面方便私自改字段名。
- 如果字段不够，先更新契约并通知产品和后端。

## 6. UI Requirements / UI 要求

- Back-office management style / 后台管理风格
- Clear loading, empty, and error states / 必须有加载、空数据、错误状态
- Device status tags: ONLINE, OFFLINE, ALERT, MAINTENANCE
- Command status tags: PENDING, SENT, ACKED, FAILED, TIMEOUT
- Main demo path must be stable before adding decorative UI.

## 7. API Binding Rule / 接口绑定规则

All IoT API calls should be wrapped in:

```text
frontend/src/api/iot.js
```

Every page implementation must state:

- Which API it calls
- Which fields it uses
- Which buttons trigger write operations
- Which empty/error states are handled

## 8. Frontend Definition of Done / 前端完成标准

A frontend task is done only when:

- The route can be opened.
- Mock or real data renders correctly.
- Loading, empty, and error states exist.
- The API contract fields are respected.
- Test steps are provided.
- Screenshots can be used for weekly acceptance.

## 9. Required Verification / 必须输出测试方法

Every frontend coding response must include:

```text
如何运行：
- cd frontend
- npm run dev

如何测试：
1. 打开页面路径：
2. 使用 mock/真实接口：
3. 检查正常数据：
4. 检查空数据：
5. 检查接口报错：
6. 检查按钮操作：

预期结果：
- 

影响的 API 契约：
- 
```

## 10. Do Not / 禁止事项

- Do not invent API fields.
- Do not change route names without updating documentation.
- Do not implement pages outside MVP before the vertical slice works.
- Do not hide backend errors without displaying useful feedback.
- 不自造字段。
- 不私自改路由。
- 最小闭环没通前，不做大量边缘页面。

