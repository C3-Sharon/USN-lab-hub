# 最终能力追踪矩阵

> 每周只填写已经合入 `dev` 并验证的 PR 和证据。计划 PR 不得预填为完成。

| 能力 | 目标周 | 产品 PR | 后端 PR | 前端 PR | 自动化证据 | 人工/演示证据 | 状态 |
|---|---:|---|---|---|---|---|---|
| 角色化个人首页与考勤 | 2 | - | - | - | - | - | PLANNED |
| 项目、成员、里程碑、任务 | 3-4 | - | - | - | - | - | PLANNED |
| 学习路线、模板、实验和反馈 | 5-7 | - | - | - | - | - | PLANNED |
| 元器件目录、联网辅助和图片 | 8 | - | - | - | - | - | PLANNED |
| 采购、订单、发票和分批入库 | 9 | - | - | - | - | - | PLANNED |
| 领用、退回、损耗、审批和流水 | 10 | - | - | - | - | - | PLANNED |
| 资产借还、维护和状态 | 11 | - | - | - | - | - | PLANNED |
| 采购到领用资源闭环 | 12 | - | - | - | - | - | PLANNED |
| 硬件产品、版本和媒体 | 13 | - | - | - | - | - | PLANNED |
| BOM、打板和裸板批次 | 14 | - | - | - | - | - | PLANNED |
| 装配扣料、板卡实例和维修 | 15 | - | - | - | - | - | PLANNED |
| 多设备模板、动态指标和 SSE | 16 | - | - | - | - | - | PLANNED |
| 通用告警、指令、ACK 和审计 | 17 | - | - | - | - | - | PLANNED |
| 知识 CRUD、版本、删除恢复和 ACL | 18 | - | - | - | - | - | PLANNED |
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

## 更新示例

```markdown
| 项目、成员、里程碑、任务 | 3-4 | #31 | #32 | #33 | `ProjectWorkflowIntegrationTest` 12/12 | `docs/evidence/m01/project-task-demo.md` | VERIFIED |
```
