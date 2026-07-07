# Agent Task Template / Agent 任务模板

> 中文说明：每次给 agent 派任务时，建议复制本模板。它能帮助新手把任务范围、契约、测试和交接说清楚。

## 1. Generic Template / 通用模板

```text
请先阅读：
- docs/agent-guides/00_SHARED_AGENTS.md
- docs/agent-guides/<你的角色_AGENT>.md
- docs/agent-guides/04_API_CONTRACT.md
- docs/agent-guides/07_TESTING_AND_ACCEPTANCE.md

如果涉及 MQTT 或硬件，请同时阅读：
- docs/agent-guides/05_MQTT_CONTRACT.md

如果涉及 MVP 主链路，请同时阅读：
- docs/agent-guides/06_VERTICAL_SLICE_PLAN.md

任务目标：
实现/修改 xxx。

范围限制：
只修改 xxx。
不要修改 xxx。
不要新增超出 MVP 的功能。

输入契约：
接口/字段/Topic 如下：

完成标准：
1. 
2. 
3. 

必须输出：
1. 改动摘要
2. 涉及文件
3. 运行方法
4. 测试方法
5. 测试数据
6. 预期结果
7. 契约影响
8. 需要其他人配合的事项
```

## 2. Product Task Prompt / 产品任务 Prompt

```text
你是 USN Lab Hub IoT 项目的产品经理 agent。
请先阅读 docs/agent-guides/00_SHARED_AGENTS.md、01_PRODUCT_AGENT.md、04_API_CONTRACT.md、05_MQTT_CONTRACT.md、06_VERTICAL_SLICE_PLAN.md 和 07_TESTING_AND_ACCEPTANCE.md。

任务目标：
围绕一个半月 MVP 和 PM-001 最小纵向切片，输出 xxx。

请不要新增超出 MVP 的功能。
请明确字段、页面、验收标准和需要前后端/硬件组确认的事项。

必须输出：
- 本次产出
- 字段或页面清单
- 影响的 API/MQTT 契约
- 给前端的交接
- 给后端的交接
- 给硬件组的确认项
- 验收标准
```

## 3. Frontend Task Prompt / 前端任务 Prompt

```text
你是 USN Lab Hub IoT 项目的前端实现 agent。
请先阅读 docs/agent-guides/00_SHARED_AGENTS.md、02_FRONTEND_AGENT.md、04_API_CONTRACT.md、06_VERTICAL_SLICE_PLAN.md 和 07_TESTING_AND_ACCEPTANCE.md。

任务目标：
基于 Vue 3 + Element Plus 实现 xxx 页面/组件。

范围限制：
- 使用 API_CONTRACT 中的字段。
- 如果后端未完成，先使用与 API_CONTRACT 一致的 mock JSON。
- 不要擅自更改字段名。
- 不要修改无关页面。

完成标准：
1. 页面路径可访问。
2. 正常/空数据/错误/loading 状态可验证。
3. 输出测试步骤和预期结果。

必须输出：
- 改动摘要
- 涉及文件
- 页面路径
- 如何运行
- 如何测试
- 使用的 API 或 mock
- 契约影响
- 需要后端或产品配合的事项
```

## 4. Backend Task Prompt / 后端任务 Prompt

```text
你是 USN Lab Hub IoT 项目的后端实现 agent。
请先阅读 docs/agent-guides/00_SHARED_AGENTS.md、03_BACKEND_AGENT.md、04_API_CONTRACT.md、05_MQTT_CONTRACT.md、06_VERTICAL_SLICE_PLAN.md 和 07_TESTING_AND_ACCEPTANCE.md。

任务目标：
基于 Spring Boot + MyBatis Plus + MySQL + Redis + Flyway 实现 xxx。

范围限制：
- 所有新增接口使用 /api/iot/**。
- 返回字段必须符合 API_CONTRACT。
- 表结构变更必须通过 Flyway。
- 不要引入微服务或重型规则引擎。
- 不要修改无关的成员/考勤模块。

完成标准：
1. 有必要的数据库迁移。
2. API 能通过 curl/Apifox 测试。
3. 返回 JSON 与 API_CONTRACT 一致。
4. 输出异常测试。
5. 如涉及写操作，记录 operation log。

必须输出：
- 改动摘要
- 涉及文件
- 如何运行
- 数据库准备
- API 测试命令
- 预期 JSON
- 异常测试
- 契约影响
- 需要前端/产品/硬件组配合的事项
```

## 5. MQTT Task Prompt / MQTT 任务 Prompt

```text
请先阅读 docs/agent-guides/05_MQTT_CONTRACT.md、06_VERTICAL_SLICE_PLAN.md 和 07_TESTING_AND_ACCEPTANCE.md。

任务目标：
实现/验证 xxx MQTT 上报或指令回执。

范围限制：
- Topic 必须符合 MQTT_CONTRACT。
- Payload 字段必须符合 MQTT_CONTRACT。
- 必须保留可重复测试的 payload 示例。

必须输出：
- Broker 地址
- Topic
- Payload
- 发布/订阅方法
- 后端日志预期
- 数据库预期
- 前端预期
- 失败排查步骤
```

## 6. PR Description Template / PR 描述模板

```text
## Summary / 改动摘要
- 

## Scope / 范围
- 

## Tests / 测试
1. 
2. 

## Contract Impact / 契约影响
- API_CONTRACT：无/已更新
- MQTT_CONTRACT：无/已更新
- Database schema：无/已更新
- Frontend fields：无/已更新

## Coordination / 需要协调
- Product：
- Frontend：
- Backend：
- Hardware：

## Risks / 风险
- 
```

