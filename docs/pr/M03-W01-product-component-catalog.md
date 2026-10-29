# PR：第 8 周元器件目录与通用媒体契约 REVIEW

## 建议标题

`docs(product): review W44 component catalog and media contract`

## 背景

第 8 周需要先冻结元器件目录、外部规格人工确认、不可变规格快照和通用媒体身份，后端与前端才能从同一边界开工。本 PR 仅提交 REVIEW 版，D1-D9 获得明确确认前不生效、不标记 FROZEN。

最新 `dev@83c1252` 已包含对象存储边界调整。W43 前后端 PR 已合入，但仓库内仍缺少可复现的关闭 mock 前端真实 FILE/MANUAL 页面联调报告，因此本 PR 不修改 W43、CURRENT_STATE、路线状态或追踪矩阵。

## 本 PR 完成

- 新增 `docs/weekly/2026-W44.md`：目标、范围、非目标、三人矩阵、同源提示词、验收与合并顺序。
- 新增 `docs/contracts/COMPONENT_CATALOG_MEDIA_API.md`：字段、状态、权限、错误、幂等、并发、分页、页面状态、媒体边界与兼容策略。
- 更新 `docs/contracts/CONTRACT_INDEX.md`，以 REVIEW 状态登记新契约。
- 新增 `scripts/acceptance/w44-component-catalog-integration.ps1`，作为后续真实 API 联调的可重复验收入口。

## 待确认的高风险决策

1. D1：分类、制造商、型号、封装、基础单位、关键规格与 `DRAFT/ACTIVE/INACTIVE`。
2. D2：`manufacturerId + normalizedMpn` 全状态唯一、分类最多三级、只停用/恢复、不物理删除。
3. D3：SYSTEM_ADMIN/STOCK_KEEPER 写；TEACHER 全局只读；MEMBER 只读 ACTIVE；GUEST 无权。
4. D4：外部候选与正式规格分离，保存来源与原始/标准化值，W44 不服务端抓取 URL。
5. D5：外部候选必须人工确认；来源不可用允许带说明的手工正式快照。
6. D6：正式快照追加且不可变，原子更新当前指针，使用 version、幂等键和单胜者并发规则。
7. D7：每型号最多 5 图；JPEG/PNG、单图 5 MiB；替换/删除逻辑留痕；无图使用确定性占位。
8. D8：写操作统一 version/Idempotency-Key，固定 401/403/404/409 语义。
9. D9：固定分页上限、筛选、关键字字段和排序白名单。

## 明确不做

- 采购、金额、发票、入库、库存、领用、BOM、装配、资产和硬件实例。
- 自动抓取网页、绕过访问控制、自动接受规格、RAG、Knowledge Radar 或 Agent 建议。
- MinIO、AIStor、SeaweedFS、AWS S3 等最终对象存储选型、集群部署和历史迁移。
- 后端或前端实现。

## 契约影响

- API：新增分类、制造商、元器件、规格候选/快照、图片和受控媒体接口。
- 数据库：后续实现需要唯一键、版本列、不可变快照、候选、媒体元数据和审计记录。
- 权限：新增目录读取/维护、候选确认、媒体维护权限边界。
- UI：新增目录列表/详情/编辑、候选对比、历史快照、图片与占位状态。
- 媒体：业务 API 只暴露 `mediaId` 与受控元数据，不暴露路径、对象键、bucket、provider、endpoint 或签名 URL。
- MQTT/知识检索：无影响。

## 兼容说明

- W43：复用不可变对象、SHA-256、路径隐藏和 ACL 原则，不改变实验与采集事实。
- 第 9 周：发票/订单附件扩展 ownerType 与 ACL，复用 mediaId 和逻辑对象键。
- 第 13 周：硬件版本图片复用 PRIMARY/GALLERY、占位和媒体访问语义。
- 第 18 周：实测并冻结正式对象存储；切换不能改变 mediaId、逻辑对象键、业务 API 或权限。

## 评审与验证

1. 逐项确认 D1-D9，确认后再将契约和周文档状态改为 FROZEN。
2. 检查所有媒体响应都不包含基础设施地址。
3. 检查 MEMBER 对 DRAFT/INACTIVE 的不可见语义和 404 防枚举规则。
4. 检查重复 MPN、过期 version 和并发候选确认的 409 语义。
5. 检查规格历史不可覆盖、图片删除后占位键稳定。
6. 当前只运行 `git diff --check` 与 PowerShell Parser；真实 API 验收待后端与前端完成后执行。

## 合并顺序

```text
本 Draft PR REVIEW
→ 用户确认 D1-D9
→ 契约 FROZEN、PR Ready
→ 产品 PR 合入 dev
→ 后端/前端从最新 dev 开工
→ 后端 PR
→ 前端同步 dev 并关闭 mock 真实联调
→ 前端 PR
→ W44 验收
```

## 风险

- W43 真实前端联调证据仍需独立收口，不能由本 PR 替代。
- 媒体原件保留、逻辑删除与后续物理清理之间必须保持审计链，第 18 周再冻结正式存储与清理策略。
- 厂商 MPN 规范化若后续改变会影响唯一键，必须迁移并版本化，不能静默重算。
