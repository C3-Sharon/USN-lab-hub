# 生效契约索引

## 1. 使用规则

契约是三人并行开发的边界。产品负责人冻结业务语义和样例，后端负责人确认可实现性和错误语义，前端负责人确认页面状态和字段消费。契约 PR 合入 `dev` 后才生效。

任何契约变化必须：

1. 在当周文档中写变化原因和兼容策略。
2. 修改对应契约文件和样例。
3. 在 PR 中标注 API、MQTT、数据库、前端字段、权限是否受影响。
4. 后端提供契约或集成测试，前端至少用真实响应联调一次。
5. 更新本索引的状态、版本和最后确认周。

## 2. 当前生效契约

| 契约 | 文件 | 状态 | 用途 |
|---|---|---|---|
| 首轮 IoT API | `docs/agent-guides/04_API_CONTRACT.md` | ACTIVE_LEGACY | PM-001 latest/history/告警/指令等基线 |
| 首轮 MQTT | `docs/agent-guides/05_MQTT_CONTRACT.md` | ACTIVE_LEGACY | PM-001 telemetry/command/ack |
| 首轮测试验收 | `docs/agent-guides/07_TESTING_AND_ACCEPTANCE.md` | REFERENCE | 已完成 IoT 阶段测试参考 |
| 总体产品与架构 | `docs/superpowers/specs/2026-08-19-usn-lab-hub-platform-design.md` | ACTIVE | 六个月目标和不可突破边界 |
| 周业务契约 | `docs/weekly/2026-WNN.md` | WEEK_SCOPED | 当周字段、样例、错误和验收 |

`ACTIVE_LEGACY` 表示当前运行代码仍遵守该契约，但它不能直接代表第 16-17 周通用 IoT 目标已完成。通用契约建立后必须记录替代关系和迁移窗口。

## 3. 后续契约目录

按阶段新增，禁止提前创建空壳文件：

- 项目与权限：第 2-4 周。
- 学习实验和模板 Schema：第 5-7 周。
- 采购、库存和资产状态机：第 8-12 周。
- 硬件版本、BOM、装配和通用 MQTT：第 13-17 周。
- Knowledge Resource、摄取、RAG Trace：第 18-21 周。
- Git Adapter、Agent Tool、Pending Action：第 22-24 周。

契约文件应包含：目标、鉴权、请求、响应、枚举、状态转换、错误、幂等键、权限矩阵、正常样例、空状态、失败样例和版本兼容说明。

## 4. 冲突处理

- 运行代码与生效契约不同：停止合并，修正代码或通过产品 PR 正式变更契约。
- 两份文档定义同一字段：以本索引标记 ACTIVE 且最后确认周更新者为准。
- mock 与真实响应不同：真实响应不是自动正确，先对照生效契约定位责任。
- 产品需求无法满足一致性或安全：后端提交风险与替代方案，三人确认后改契约。
- 外部硬件无法按契约发送：优先适配边缘网关，不能让单设备特殊字段污染通用模型。
