# USN Lab Hub IoT - Shared Agent Guide

> 中文说明：所有参与本项目的同学和 coding agent 都应先阅读本文件。它定义共同目标、技术栈、范围边界、协作规则、测试要求和契约优先级。任何角色的 agent 都不能绕开这些约束。

## 1. Project Mission / 项目使命

Build **USN Lab Hub IoT** as a lightweight lab hardware project management and intelligent operations platform.

中文目标：在现有 `USN-lab-hub` 成员管理系统基础上，建设面向实验室硬件项目的轻量级 IoT 管理平台。平台以“项目-设备-数据-告警-建议-控制-日志”为主线，通过 MQTT 接入真实硬件数据，并在一个半月内交付可演示的完整业务闭环。

Core business loop / 核心闭环：

```text
People -> Project -> Device -> Telemetry -> Alert -> Recommendation -> Command -> ACK -> Operation Log
人员 -> 项目 -> 设备 -> 数据 -> 告警 -> 建议 -> 控制指令 -> 硬件回执 -> 操作日志
```

## 2. One-and-a-half-month MVP / 一个半月交付边界

Must deliver / 必须交付：

- Project management / 项目管理
- Device asset management / 设备资产管理
- MQTT telemetry ingest / MQTT 数据接入
- Real hardware or simulator telemetry / 真实硬件或模拟器上报
- Latest values and history chart / 最新数据和历史曲线
- Alert records / 告警
- Rule-based recommendations / 规则建议
- Command issuing / 指令下发
- Command ACK handling / 指令回执
- Operation logs / 操作日志
- Visitor or public project display / 访客或公开项目展示页

Do not do in MVP / MVP 阶段不要做：

- Do not rebuild the existing member, login, attendance modules.
- Do not migrate to a heavy IoT platform such as ThingsBoard or JetLinks as the main system.
- Do not split into microservices.
- Do not implement fully autonomous Agent control of hardware.
- Do not introduce heavy infrastructure unless it is required for the vertical slice.
- 不重做现有成员、登录、考勤模块。
- 不把 ThingsBoard/JetLinks 作为主系统。
- 不做微服务拆分。
- 不做 Agent 自动直接控制硬件；Agent 方向只作为长期路线。

## 3. Current Tech Stack / 当前技术栈

Backend / 后端：

- Spring Boot
- MyBatis Plus
- MySQL
- Redis
- Flyway
- Swagger/OpenAPI
- JWT

Frontend / 前端：

- Vue 3
- Element Plus
- Vue Router
- Axios
- ECharts may be added for charts / 可新增 ECharts

IoT / 物联网接入：

- MQTT first / 优先 MQTT
- EMQX or Mosquitto for broker / Broker 推荐 EMQX 或 Mosquitto
- HTTP mock/report endpoint may exist only as test fallback / HTTP mock 接口仅作为测试兜底

## 4. Repository Strategy / 仓库策略

During summer remote collaboration, GitHub can be the primary collaboration repository because the lab GitLab is only accessible on lab WiFi.

暑假远程阶段建议：

- GitHub: primary remote collaboration repository / 主协作仓库
- Lab GitLab: later mirror or official archive after connecting to lab WiFi / 返校或连实验室 WiFi 后同步归档

Rules / 规则：

- Work on GitHub during remote development.
- Do not exchange zip files as the main collaboration method.
- Later sync GitHub `main` and `dev` into GitLab.
- GitHub and GitLab must not both accept independent code changes at the same time.
- 远程开发期间以 GitHub 为准。
- 不用 QQ/微信压缩包作为主交付方式。
- 后续把 GitHub 的 `main` 和 `dev` 同步到实验室 GitLab。
- 不允许 GitHub 和 GitLab 同时各自独立演化。

## 5. Branch Rules / 分支规则

Recommended branches / 推荐分支：

```text
main                  stable demo branch / 稳定演示分支
dev                   integration branch / 日常集成分支
feature/iot-product   product docs and requirements / 产品与文档
feature/iot-frontend  frontend implementation / 前端实现
feature/iot-backend   backend implementation / 后端实现
feature/iot-mqtt-demo MQTT simulator and hardware test scripts / MQTT 模拟器与联调脚本
```

Merge rules / 合并规则：

- Feature branches may receive direct commits from their owner.
- `dev` should be updated by Pull Request.
- `main` should only receive stable versions from `dev`.
- Every PR must include test instructions.
- 每个功能分支由负责人维护。
- 合并到 `dev` 要走 Pull Request。
- `main` 只接收稳定可演示版本。
- 每个 PR 必须包含测试方法。

## 6. Naming Rules / 命名规则

- All new IoT backend APIs must use `/api/iot/**`.
- IoT tables should use `iot_` prefix.
- Lab project table should use `lab_project`.
- Frontend IoT views should be placed under `frontend/src/views/iot`.
- Frontend IoT API wrapper should be `frontend/src/api/iot.js`.
- Do not modify existing member/attendance APIs unless the task explicitly requires it.
- 新增 IoT 后端接口统一使用 `/api/iot/**`。
- IoT 数据表统一使用 `iot_` 前缀。
- 项目表使用 `lab_project`。
- 前端 IoT 页面放在 `frontend/src/views/iot`。
- 前端 IoT API 封装放在 `frontend/src/api/iot.js`。
- 不随意改动现有成员和考勤接口。

## 7. Contract Priority / 契约优先级

Before coding, read the relevant contract files:

```text
00_SHARED_AGENTS.md
role-specific agent guide
04_API_CONTRACT.md
05_MQTT_CONTRACT.md if hardware/MQTT is involved
06_VERTICAL_SLICE_PLAN.md for MVP work
07_TESTING_AND_ACCEPTANCE.md
```

中文规则：

- 产品字段必须先进入字段字典或契约，前后端才能使用。
- 前端 mock JSON 必须来自 API 契约，不能自造字段。
- 后端返回 JSON 必须匹配 API 契约。
- MQTT 消息必须匹配 MQTT 契约。
- 任意字段变更必须同步相关契约文档。

## 8. Verification Required / 必须验证

Do not finish a coding task without a verification section.

任何编码任务都不能只交代码。每次 agent 完成任务后，必须输出：

- What changed / 改了什么
- Files changed / 涉及文件
- How to run / 如何运行
- How to test / 如何测试
- Expected result / 预期结果
- Contract impact / 契约影响
- Who needs to coordinate / 需要谁配合

See `07_TESTING_AND_ACCEPTANCE.md` and `08_AGENT_TASK_TEMPLATE.md`.

## 9. Definition of Done / 完成标准

A task is done only when:

- The code or document is committed in the correct scope.
- The relevant contract is still correct.
- A test method is provided.
- Expected results are documented.
- The change can be connected to the vertical slice or a clearly stated later milestone.
- 代码或文档已按范围完成。
- 相关契约仍然正确。
- 已提供测试方法。
- 已写明预期结果。
- 能连接到最小纵向切片或明确的后续里程碑。

