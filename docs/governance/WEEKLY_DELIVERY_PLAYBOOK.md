# 每周交付与联调手册

## 1. 固定节奏

| 时间 | 团队动作 | 必须产物 |
|---|---|---|
| 周一 | 产品冻结场景、字段、权限、样例和不做项 | 当周任务文档、产品 PR |
| 周二至周三 | 前端按契约 mock；后端确认执行台账后逐块实现 | 可独立运行的角色分支、后端 Draft PR |
| 周三或周四 | 契约校准和第一次真实联调 | 联调记录、缺陷清单 |
| 周四 | 三个主 PR 完成自测和自审 | PR 文案、测试证据 |
| 周五 | 评审、按顺序合并、最新 dev 总联调 | CURRENT_STATE、追踪矩阵 |

每人一周工作 4-5 天、每天不超过 8 小时。建议把 60% 时间用于实现，20% 用于测试和联调，10% 用于评审，10% 用于文档和缓冲。

## 2. 首次准备

```bash
git clone git@github.com:C3-Sharon/USN-lab-hub.git
cd USN-lab-hub
git fetch origin
git checkout dev
git pull --ff-only origin dev
```

检查 JDK 17+、Node.js 20+、MySQL 8、Redis 6+。个人数据库密码和 Token 只放环境变量，不提交仓库。

## 3. 每周建分支

先确保当前改动已提交或明确保存，再执行：

```bash
git checkout dev
git fetch origin
git pull --ff-only origin dev
git checkout -b feature/m01-w02-backend-dashboard
```

命名格式：

```text
feature/mNN-wNN-product-topic
feature/mNN-wNN-frontend-topic
feature/mNN-wNN-backend-topic
fix/mNN-wNN-role-topic
docs/mNN-wNN-topic
```

不要复用上周分支，不要在个人长期分支累积六个月提交。

## 4. 后端分块执行与 Draft PR

后端在产品契约冻结并合入 `dev` 后，必须先从 `docs/backend/WEEKLY_EXECUTION_TEMPLATE.md` 创建当周 `docs/backend/execution/2026-WNN.md`。每周拆成 3 至 6 个可独立验证的工作块，同一时间只能有一个 `IN_PROGRESS`。执行台账只在工作块开始、完成、阻塞或范围变化时更新，不记录逐条命令。

固定生命周期：

```text
冻结契约 -> 建分支 -> 创建并确认执行台账 -> 推送 Draft PR
-> 单块 IN_PROGRESS -> 定向验证 -> VERIFIED -> 提交推送
-> 判断自动继续/暂停确认 -> PR 前完整验证 -> Ready for review
```

首次推送以确认后的执行台账为主，并创建面向 `dev` 的 Draft PR。此后每个已验证工作块原则上对应一个有意义提交，持续推送到同一 PR；禁止拆空提交或为增加提交数制造无意义文档改动。每周仍然只有一个后端 PR。

满足以下全部条件时，Agent 简短汇报后自动继续下一块：

- 契约已冻结且实现不改变契约。
- 当前块是低或中风险的直接实现。
- 定向测试和必要相邻回归通过。
- 修改范围没有超出已确认边界。
- 下一块输入已经满足。

出现以下任一情况必须暂停相关实现，展示事实、方案和影响，等待用户明确确认：

- 新增或修改 Flyway 迁移。
- 修改全局或项目权限边界。
- 引入事务、锁、条件更新、幂等键或并发状态机的重要决策。
- 可能覆盖、删除或不可逆改变真实数据。
- 偏离冻结的 API、MQTT、状态枚举或错误语义。
- 引入新中间件、大型依赖、微服务或部署组件。
- 测试证明需求、现有数据或架构前提错误。
- 工作量或影响范围预计超过已确认计划的 25%。
- 遇到来源不明且影响当前任务的未提交修改。

高风险设计可以先做只读调查和测试设计，但确认前不得执行迁移、破坏性操作或扩大实现范围。所有块完成门禁后，执行一次 PR 前完整测试和一次跳过测试的打包，再将 Draft PR 转为 Ready for review。

GitHub 暂时不可访问时，本地执行台账和提交历史继续作为事实源。网络恢复后按原提交顺序推送，不重新生成或压缩成无法审阅的一次性结果。

## 5. 日常提交

开始前先查看分支和工作区。提交时只暂存本次相关文件：

```bash
git status --short --branch
git diff
git add docs/weekly/2026-W02.md
git commit -m "docs(product): freeze week 2 dashboard contract"
git push -u origin feature/m01-w02-product-dashboard
```

提交信息采用 `type(scope): summary`：`feat`、`fix`、`test`、`docs`、`refactor`、`chore`。一个提交应有可解释的单一目的。

## 6. PR 创建

GitHub 页面选择：

```text
base: dev
compare: feature/m01-w02-backend-dashboard
```

PR 文案：

```text
本周目标：
- 对应路线周次和验收场景

完成内容：
-

明确未做：
-

契约影响：
- API：是/否，文档路径
- MQTT：是/否，文档路径
- 数据库：是/否，迁移版本
- 前端字段：是/否
- 权限：是/否

如何运行：
-

如何测试：
1.

实际结果：
- 命令、通过数量、截图或报告

风险与回滚：
-

需要谁配合：
-
```

至少一名队友审阅。审阅者必须检查范围、契约、测试和可复现性，不能只回复“看起来可以”。

后端 PR 初始创建为 Draft，并在描述顶部链接当周执行台账、标明当前工作块。只有台账中的 PR 就绪检查全部满足，且没有未解释阻塞时，才转为 Ready for review。

## 7. 合并顺序

有字段、状态、权限或协议变化时：

1. 产品 PR 先合入 `dev`，冻结契约和样例。
2. 后端分支同步最新 `dev`，解决冲突，运行测试，更新后端 PR。
3. 后端 PR 合入 `dev`。
4. 前端分支同步最新 `dev`，切换真实 API 并完成联调。
5. 前端 PR 合入 `dev`。
6. 三人从最新 `dev` 完成总场景验收。

无契约变化时可以并行合并，但最后合入的角色仍需同步最新 `dev` 并验证相邻功能。

同步命令：

```bash
git checkout feature/m01-w02-frontend-dashboard
git fetch origin
git merge origin/dev
```

发生冲突后逐文件理解并处理，禁止用整文件覆盖方式吃掉另一人的改动。处理后重新运行受影响测试，再提交合并结果。

## 8. 总联调

```bash
git checkout dev
git fetch origin
git pull --ff-only origin dev
```

依次验证：数据库迁移、后端测试和启动、前端构建和启动、真实接口主流程、权限差异、错误/空状态、固定视口截图。MQTT 周还要使用模拟器和至少一次真实设备上报。

联调通过后：

- 更新 `CURRENT_STATE.md`，只写已验证事实。
- 在 `TRACEABILITY_MATRIX.md` 填 PR 和证据。
- 在周任务文档末尾填写验收结果和遗留项。
- 删除已合并远程分支；下一周重新从 `dev` 建分支。

## 9. GitLab 同步

GitHub 是唯一可写主线。连接实验室 WiFi 后由指定维护者同步稳定分支：

```bash
git remote add gitlab <实验室 GitLab 仓库地址>
git fetch origin
git push gitlab origin/main:main
git push gitlab origin/dev:dev
```

若远端已存在则使用 `git remote set-url gitlab <地址>`。GitLab 不接受独立开发提交；同步失败只记录时间和错误，不影响 GitHub 主线。

## 10. 线上沟通

- 周一 30-45 分钟：逐字段确认本周文档。
- 周中 20-30 分钟：前端展示请求/响应，后端展示 Swagger 或真实响应。
- 周末 45-60 分钟：共享屏幕执行总联调和验收脚本。
- 口头决定必须在当天写回周任务或契约；聊天记录不能成为唯一依据。
- 阻塞超过半个工作日即在群中说明：现象、已尝试、需要谁、最晚答复时间。
