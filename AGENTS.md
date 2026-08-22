# USN Lab Hub Agent Instructions

本文件是产品、前端和后端 Agent 的统一入口。任何 Agent 在修改代码或文档前都必须先读取本文件。

## 1. 项目目标

USN Lab Hub 是面向中国高校小型硬件实验室的项目协作、学习实验、考勤、采购库存、自研硬件追溯、IoT、知识库和受控智能调控平台。六个月目标是完成 `docs/superpowers/specs/2026-08-19-usn-lab-hub-platform-design.md` 第 19 节验收矩阵，不得把阶段 MVP 描述为最终成品。

## 2. 开工必读

所有角色按以下顺序读取：

1. `docs/governance/PROJECT_CHARTER.md`
2. `docs/governance/CURRENT_STATE.md`
3. `docs/governance/ROADMAP_26_WEEKS.md`
4. 当前 `docs/weekly/` 周任务文档
5. 自己的长期角色指南
6. `docs/contracts/CONTRACT_INDEX.md` 指向的相关契约
7. `docs/governance/QUALITY_GATES.md`

涉及第 18-23 周知识、RAG、来源同步或 Agent 建议时，额外必读 `docs/superpowers/specs/2026-08-21-knowledge-radar-design.md`。该专项规格与总体规格共同约束实现。

涉及采集计划、实验数据、MQTT v2、Serial/Bluetooth、Edge Collector、文件导入或数据质量时，额外必读 `docs/superpowers/specs/2026-08-22-data-acquisition-design.md`。

角色指南：

- 产品：`docs/agent-guides/10_PRODUCT_LONG_TERM_AGENT.md`
- 前端兼边缘工具：`docs/agent-guides/11_FRONTEND_LONG_TERM_AGENT.md`
- Java 后端：`docs/agent-guides/12_BACKEND_LONG_TERM_AGENT.md`

当文件冲突时，优先级为：已批准总体规格 > 当前周任务 > 生效契约 > CURRENT_STATE > 角色指南 > README > 历史 00-09 指南。发现矛盾必须停止相关实现并在 PR 中列出，不得自行猜测字段。

## 3. Git 规则

- GitHub 是日常唯一主仓库；实验室 GitLab 只从 GitHub 单向镜像或归档。
- `main` 是月度稳定演示分支，`dev` 是日常集成分支，二者均不直接开发。
- 每周从最新 `dev` 创建短分支，格式为 `feature/mNN-wNN-role-topic`。
- 每个角色每周提交一个有意义 PR 到 `dev`；禁止空改动凑 PR 数。
- 契约变化通常按产品 PR、后端 PR、前端 PR 顺序合并。
- 合并后删除周分支；下一周重新从最新 `dev` 创建分支。
- 不覆盖他人未提交修改，不提交密码、Token、私钥、构建产物和个人 IDE 配置。

旧 README 中的 `feature/iot-product`、`feature/iot-frontend`、`feature/iot-backend` 是首轮 IoT 阶段历史做法。下一阶段不得把它们当作长期个人分支。

## 4. Agent 行为约束

每次调用必须先：

1. 输出当前分支、工作区状态和读取过的文档。
2. 用一句话复述本周目标和明确不做项。
3. 列出将修改的文件、契约影响和测试层级。
4. 发现未提交改动时区分本人改动与已有改动，不能擅自还原。

每次调用结束必须输出：

1. 实际完成内容与未完成内容。
2. 修改文件清单。
3. 运行命令、测试结果、构建结果和人工检查结果。
4. API、MQTT、数据库、权限、UI 字段、知识来源和检索范围的契约影响。
5. 联调步骤、已知风险和需要另一角色确认的事项。
6. 建议提交信息和 PR 文案要点。

Agent 不得：

- 把 mock、未合并分支、计划功能写入 CURRENT_STATE 的已完成功能。
- 未经产品契约修改接口字段、状态枚举、页面术语或权限范围。
- 用微服务、Kafka、Kubernetes、GraphRAG 等扩大范围来代替完成当前业务。
- 让模型直接连接 MQTT Broker 或绕过后端控制权限。
- 让 Agent 自行启用知识来源、批准候选、下载资料、发布知识或扩大可见范围。
- 把未审核候选、网页内容或公众号文章直接放入正式 RAG 检索。
- 自动监控/批量抓取微信公众号，或绕过登录、验证码、robots 和来源访问限制。
- 让 Vue 浏览器承担正式的长期串口/蓝牙采集，或让 Edge Collector 直接写数据库和执行业务审批。
- 把设备在线、Collector 在线、采集批次运行和数据质量正常混为同一个状态。
- 静默修正未知单位、伪造缺失记录、覆盖完成批次或把不断变化的 latest 作为实验固定证据。
- 在测试输入和代码未变化时反复运行同一全量套件。
- 用截图文件哈希完全相等判断 UI 正确。

## 5. 完成标准

一项工作只有同时满足以下条件才算完成：

- 与周任务和生效契约一致。
- 受影响行为有自动化测试或明确的人工检阅证据。
- 相关构建通过，无未解释失败或跳过。
- 真实前后端或设备契约已联调；mock 只作为并行开发工具。
- 文档、迁移、示例、启动方法同步更新。
- PR 可由另一名成员在干净环境中复现。

详细规则见 `docs/governance/QUALITY_GATES.md`。
