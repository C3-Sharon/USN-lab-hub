# USN Lab Hub 当前状态

> 事实基线：`origin/dev` commit `4930b3a`
> 更新规则：仅在功能合入 `dev` 且完成验证后更新
> 禁止：把计划、mock、未合并 PR 或口头完成写成已完成

## 已验证能力

### 人员与考勤

- 用户登录和 JWT 鉴权。
- 管理员成员查询、新增、更新和状态管理。
- 学生签到、签退、今日记录、时长统计。
- 管理员考勤分页和导出。
- 默认演示账号及 Flyway 初始化。

### 单项目、单设备 IoT 闭环

- PM-001 使用 `iot/power-monitor/PM-001/telemetry` 上报 MQTT telemetry。
- 保存原始 payload 与 voltage/current/power 结构化指标。
- latest、history 和 15 秒 ONLINE/OFFLINE 状态。
- 阈值告警、规则建议、建议确认或忽略。
- 指令创建、MQTT 发布、ACK、超时和操作日志。
- 设备健康评分、公开项目展示和登录后 IoT 总览。
- SSE 最新遥测推送、断线重连和轮询降级。
- Python PM-001 模拟器用于契约和联调，不是生产接口的事实源。

### 工程基础

- Spring Boot 3.1.5、JDK 17、MyBatis-Plus、MySQL 8、Redis、Flyway。
- Vue 3、Vite、Element Plus、Vue Router、Axios。
- 后端单元/集成测试和前端 SSE Node 测试已有基础。
- GitHub `dev` 集成、PR 协作和 GitLab 单向归档约定。

## 已知局限

- `lab_project`、设备和指标结构仍服务首轮 IoT 演示，尚未成为正式项目域和多设备模板。
- 当前权限主要是全局管理员/普通用户，尚未实现全局角色与项目角色组合授权。
- 个人首页以考勤为主，尚未聚合项目、任务、学习、审批和设备提醒。
- 项目成员、里程碑、任务状态机尚未实现。
- 学习实验、采购库存、资产、自研硬件、知识/RAG、Agent 尚未实现。
- MinIO、OpenSearch、双实例、Outbox、监控和恢复体系尚未进入正式实现。
- 前端自动化测试较少，设计 Token 和固定视口截图流程尚未建立。
- 旧 README 的长期 `feature/iot-*` 分支规则已经过期，下一阶段使用每周短分支。

## 当前阶段

进入 26 周路线第 1 周：冻结范围、清点基线、建立治理和可持续 Agent 上下文。第 1 周不新增业务 API，不重构已验证 IoT 闭环。

## 更新格式

每周总联调通过后，由产品负责人或当周集成人更新：

```markdown
### 2026-WNN
- 合入 PR：#<product>、#<backend>、#<frontend>
- 新增已验证能力：
- 验证证据：命令、截图或报告路径
- 新增限制或债务：
- 下一周输入：
```
