# 最终能力追踪矩阵

> 每周只填写已经合入 `dev` 并验证的 PR 和证据。计划 PR 不得预填为完成。

| 能力 | 目标周 | 产品 PR | 后端 PR | 前端 PR | 自动化证据 | 人工/演示证据 | 状态 |
|---|---:|---|---|---|---|---|---|
| 角色化个人首页与考勤 | 2 | - | - | - | - | - | PLANNED |
| 项目、成员、里程碑、任务 | 3-4 | - | - | - | - | - | PLANNED |
| 学习路线、模板、实验和反馈 | 5-7 | - | - | - | - | - | PLANNED |
| 采集计划、批次、文件/人工导入和实验快照 | 7 | - | - | - | - | - | PLANNED |
| 元器件目录、联网辅助和图片 | 8 | - | - | - | - | - | PLANNED |
| 采购、订单、发票和分批入库 | 9 | - | - | - | - | - | PLANNED |
| 领用、退回、损耗、审批和流水 | 10 | - | - | - | - | - | PLANNED |
| 资产借还、维护和状态 | 11 | - | - | - | - | - | PLANNED |
| 采购到领用资源闭环 | 12 | - | - | - | - | - | PLANNED |
| 硬件产品、版本和媒体 | 13 | - | - | - | - | - | PLANNED |
| BOM、打板和裸板批次 | 14 | - | - | - | - | - | PLANNED |
| 装配扣料、板卡实例和维修 | 15 | - | - | - | - | - | PLANNED |
| 多设备模板、动态指标和 SSE | 16 | - | - | - | - | - | PLANNED |
| MQTT/Serial/BLE 统一采集和数据质量 | 16-17 | - | - | - | - | - | PLANNED |
| 通用告警、指令、ACK 和审计 | 17 | - | - | - | - | - | PLANNED |
| 知识 CRUD、版本、删除恢复和 ACL | 18 | - | - | - | - | - | PLANNED |
| Knowledge Radar 来源、候选、审批和通知 | 18-21 | - | - | - | - | - | PLANNED |
| 摄取、失败恢复和 ACTIVE 切换 | 19 | - | - | - | - | - | PLANNED |
| Hybrid RAG、引用和无答案 | 20 | - | - | - | - | - | PLANNED |
| Inspector、黄金集和评测门 | 21 | - | - | - | - | - | PLANNED |
| GitHub/GitLab 与外部搜索 | 22 | - | - | - | - | - | PLANNED |
| 五种 Agent 专业模式与业务工具 | 23 | - | - | - | - | - | PLANNED |
| Pending Action 和受控硬件调控 | 24 | - | - | - | - | - | PLANNED |
| 双实例、缓存、性能和恢复 | 25 | - | - | - | - | - | PLANNED |
| 完整部署、E2E、文档和答辩 | 26 | - | - | - | - | - | PLANNED |

状态只允许：`PLANNED`、`IN_PROGRESS`、`BLOCKED`、`VERIFIED`。每个 `VERIFIED` 单元至少包含三个角色 PR 或明确的“不涉及该角色”理由、一个自动化证据和一个可复现人工证据。

## 已有基线证据

首轮 IoT 闭环已存在于基线 `4930b3a`：PM-001 telemetry、latest/history、告警、建议、指令、ACK、操作日志、健康、公开展示和 SSE。它是第 16-17 周平台化工作的输入，不等同于多设备平台化已经完成。

### 2026-W37 第一周基线清点

- 事实基线：`origin/dev` commit `f70fdcf`
- 产品 PR：#24（产品地图、范围矩阵、术语说明、三人任务分配）
- 后端 PR：#25（`docs/audits/2026-W37-backend-baseline.md`：85 个 Java 文件、9 个 Controller、16 张表、9 个测试套件，`mvn test` 27/27 通过）
- 前端 PR：#26（`docs/audits/2026-W37-frontend-baseline.md`：14 个业务页面、10 张 1440x900 基线截图，`npm run build` 成功，SSE 测试 23/23 通过）
- 截图证据：`docs/evidence/2026-W37/frontend/`（03-dashboard 至 12-admin-attendance 共 10 张）

第一周只验证现状基线，不等同于完成最终能力。第 2～26 周所有能力仍为 PLANNED，不得因基线清点完成而提前标记为 VERIFIED。

## 更新示例

```markdown
| 项目、成员、里程碑、任务 | 3-4 | #31 | #32 | #33 | `ProjectWorkflowIntegrationTest` 12/12 | `docs/evidence/m01/project-task-demo.md` | VERIFIED |
```
