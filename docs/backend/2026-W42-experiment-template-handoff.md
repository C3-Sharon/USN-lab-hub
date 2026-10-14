# 2026-W42 实验模板后端联调交接

## 1. 可联调能力

- SYSTEM_ADMIN、TEACHER 可创建系统模板或项目模板，编辑 DRAFT Schema，发布和废弃版本。
- 项目 OWNER、MAINTAINER 可管理本项目模板；普通成员只读可见的正式版本。
- 发布操作将当前 DRAFT 固化为不可编辑的 PUBLISHED 版本，并生成下一份 DRAFT。
- 系统模板可复制为项目模板；副本拥有独立 Schema 和版本生命周期。
- 学习单元通过 `templateVersionId` 固定关联 PUBLISHED 版本；发布新版本不会自动升级旧单元。
- 学习单元编辑时省略 `templateVersionId` 保持原关联，显式传 null 解除关联。
- DRAFT、DEPRECATED 或不存在的版本不能建立新关联；既有版本随后废弃时，历史关联仍可读取。

本周不包含实验记录、导师反馈、采集计划/批次、模板删除、复杂 JSON Schema、RAG、Agent 或 MQTT 改动。

## 2. 启动准备

使用 JDK 17、MySQL 8 和 Redis 6+，从最新后端周分支启动。Flyway 自动迁移至 V10；已经共享的 V1-V10 迁移不得修改。

```powershell
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/usn_hub?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="<本机密码>"
$env:IOT_MQTT_ENABLED="false"
cd backend
.\mvnw.cmd spring-boot:run
```

登录账号使用仓库种子数据：管理员 `admin/admin123`，成员 `20260001/20260001`。密码只用于本地联调，不写入配置或提交。

## 3. 联调顺序

完整验收以 `docs/weekly/2026-W42.md` 第 10 节十步脚本为准，建议按以下顺序执行：

1. 分别登录管理员和成员；由成员创建验收项目，记录实际 `PROJECT_ID`。
2. 管理员创建 SYSTEM 模板，记录实际 `TEMPLATE_ID` 和初始 `DRAFT_VERSION_ID`。
3. 编辑 DRAFT Schema；另传一次不支持的 array，确认返回 400 `SCHEMA_INVALID` 和字段路径。
4. 发布版本，记录 `PUBLISHED_VERSION_ID`，确认生成下一 DRAFT。
5. 尝试编辑已发布版本，确认返回 409 `TEMPLATE_VERSION_NOT_EDITABLE`。
6. 成员查看 PUBLISHED 列表并执行数据预校验，确认看不到 DRAFT 内容。
7. 项目 OWNER 将系统模板复制到项目，确认副本 scope、projectId 和独立 DRAFT。
8. 管理员在学习路线中创建单元并传 `templateVersionId`，确认返回四个关联字段。
9. 发布新版本后重新查询学习单元，确认仍引用原版本；废弃原版本后既有引用仍可读取。
10. 无 Token 访问模板接口应返回 401。

所有 ID 必须从响应中取得，不能依赖数据库自增值。真实联调时前端必须关闭业务 mock。

## 4. 前端接入重点

- 创建/编辑学习单元只提交 `templateVersionId`；`templateId` 由后端反查并保存。
- 单元响应新增 `templateVersionId`、`templateVersion`，原有 `templateId`、`templateName` 保持不变。
- 模板选择器只列出当前用户可见的 PUBLISHED 版本。
- 版本发布响应同时包含 `publishedVersion` 和 `newDraftVersion`，发布后应刷新模板详情与时间线。
- 错误态至少覆盖 `SCHEMA_INVALID`、`TEMPLATE_NOT_FOUND`、`TEMPLATE_VERSION_NOT_FOUND`、`TEMPLATE_VERSION_CONFLICT` 和权限拒绝。
- 冻结的学习单元响应没有 `templateVersionStatus`。已废弃视觉提示需通过模板版本接口获得状态；如产品希望单元接口直接返回状态，必须先校准契约，前后端不能各自猜字段。

## 5. 已验证结果

- 完整后端测试：总计 124 项，实际执行 118 项且全部通过，0 失败，0 错误；6 项真实环境条件测试按设计跳过。
- 真实 MySQL 8：V1→V10 迁移与约束测试 2 项通过；并发发布测试 1 项通过，无跳过。
- 真实 MySQL API 冒烟：登录、创建模板、编辑 Schema、发布 1.0.0、创建学习单元固定关联、复制项目模板全部返回 200，副本只有一个独立 DRAFT。
- 跳过测试打包成功，生成可运行 Spring Boot JAR。
- 临时验证库已删除，临时后端进程已停止；没有提交口令、构建产物或临时日志。

完整测试首次发现历史 readiness 测试仍固定断言最高迁移为 V9；更新为 V10 后定向测试 2/2 通过，完整测试总计 124 项、实际执行 118 项且全部通过，另有 6 项真实环境条件测试按设计跳过。MySQL 8 对旧迁移中的 `VALUES()` 用法给出弃用警告，但不影响本周迁移和运行；不得为消除警告修改已经共享的历史迁移。

三方真实页面联调完成前，不把第 6 周能力写入 `CURRENT_STATE.md` 或 `TRACEABILITY_MATRIX.md` 的已完成项。
