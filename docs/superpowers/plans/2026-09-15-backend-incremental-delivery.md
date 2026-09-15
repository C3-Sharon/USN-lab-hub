# Backend Incremental Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every weekly Java backend delivery observable and recoverable through a standard execution ledger, one Draft PR, bounded work blocks, mixed approval gates, and risk-based testing.

**Architecture:** Keep the product-owned weekly document as the source of scope and contracts, and add one backend-owned execution ledger as the source of implementation progress. Long-lived rules are distributed by responsibility across the team playbook, backend Agent guide, quality gates, and the short root Agent entry; a single template creates each future weekly ledger.

**Tech Stack:** Markdown governance documents, Git branches and Draft pull requests, repository-local evidence, `rg`, `git diff --check`, PowerShell path checks.

## Global Constraints

- Each backend week uses 3 to 6 work blocks, each normally budgeted for 2 to 6 hours.
- Only one work block may be `IN_PROGRESS` at a time.
- Work-block states are exactly `PLANNED`, `IN_PROGRESS`, `VERIFIED`, `BLOCKED`, and `SKIPPED`.
- Normal verified blocks continue automatically after a concise report; high-risk gates require explicit user confirmation.
- Each week still has one backend branch and one backend PR; the PR starts as Draft after the execution ledger is approved.
- Scope lives in `docs/weekly/`, contract fields live in `docs/contracts/`, execution status lives in the backend ledger, and verified system state lives in `CURRENT_STATE.md`.
- Full backend tests run once before PR readiness, followed by one package build with tests skipped; unchanged full suites are not repeated.
- Historical weekly plans are not rewritten or renamed.
- Do not introduce a second template, an issue-only workflow, or command-by-command logging.

---

### Task 1: Create The Backend Weekly Execution Template

**Files:**
- Create: `docs/backend/WEEKLY_EXECUTION_TEMPLATE.md`

**Interfaces:**
- Consumes: the approved design in `docs/superpowers/specs/2026-09-15-backend-incremental-delivery-design.md`.
- Produces: the only template for future `docs/backend/execution/2026-WNN.md` ledgers.

- [x] **Step 1: Verify the template does not already exist**

Run:

```powershell
Test-Path docs/backend/WEEKLY_EXECUTION_TEMPLATE.md
```

Expected: `False`.

- [x] **Step 2: Create the template with fixed metadata and checkpoint sections**

The document must start with this structure:

```markdown
# 2026-WNN 后端执行台账：主题

> 实际周编号：2026-WNN
> 路线周次：第 N 周
> 状态：PLANNING
> 分支：feature/mNN-wNN-backend-topic
> 基准 dev：commit
> 产品 PR：#NN
> 风险：LOW / MEDIUM / HIGH

## 1. 本周边界

- 业务结果：
- 冻结契约：
- 明确不做：
- 环境与真实依赖：

## 2. 当前检查点

- 当前工作块：尚未开始
- 最后完成：无
- 下一步：等待工作块计划确认
- 当前阻塞：无
- 用户确认：需要确认初始分块
- 恢复入口：AGENTS.md、本周文档、本文件当前检查点
```

Continue with a work-block table using the exact columns `ID`, `工作块`, `状态`, `预计工时`, `风险`, `进入条件`, and `提交`.

- [x] **Step 3: Add one reusable execution-card skeleton**

The template must include these exact headings for every copied block:

```markdown
### B1：可判定的工作块名称

- 状态：PLANNED
- 预计工时：2-6 小时
- 目标：
- 业务理由：
- 输入与依赖：
- 修改边界：
- 预期产物：
- 契约影响：
- 风险与失败表现：
- 定向验证：
- 人工验证：
- 完成条件：
- 下一块入口：
- 实际结果：尚未开始
```

- [x] **Step 4: Add decision, PR-readiness, and result sections**

Include:

```markdown
## 4. 决策与偏差记录
## 5. 测试与证据
## 6. PR 就绪检查
## 7. 最终交接
```

The PR checklist must cover work-block state, contract drift, targeted tests, one full test run, one skipped-test package build, real dependencies, secrets/generated files, startup/migration, rollback or recovery, frontend handoff, and unresolved blockers.

- [x] **Step 5: Verify required fields and prohibited placeholders**

Run:

```powershell
rg -n "当前检查点|工作块总表|决策与偏差|PR 就绪|下一块入口|实际结果" docs/backend/WEEKLY_EXECUTION_TEMPLATE.md
rg -n "TBD|TODO|完善功能|优化代码|补充测试" docs/backend/WEEKLY_EXECUTION_TEMPLATE.md
```

Expected: the first command finds every required section; the second command returns no matches.

- [x] **Step 6: Commit the template**

```bash
git add docs/backend/WEEKLY_EXECUTION_TEMPLATE.md
git commit -m "docs(backend): add weekly execution ledger template"
```

---

### Task 2: Add The Team Workflow And Draft PR Lifecycle

**Files:**
- Modify: `docs/governance/WEEKLY_DELIVERY_PLAYBOOK.md`

**Interfaces:**
- Consumes: `docs/backend/WEEKLY_EXECUTION_TEMPLATE.md`.
- Produces: the authoritative team sequence from frozen product contract through backend Draft PR, block delivery, frontend integration, and closeout.

- [ ] **Step 1: Prove the current playbook lacks the new lifecycle**

Run:

```powershell
rg -n "Draft PR|当前检查点|高风险阶段门|自动继续" docs/governance/WEEKLY_DELIVERY_PLAYBOOK.md
```

Expected: no complete lifecycle is found.

- [ ] **Step 2: Extend the fixed weekly rhythm without replacing the three-role flow**

Add a backend row or note stating:

```markdown
后端在产品契约冻结后先提交执行台账并创建 Draft PR；每个已验证工作块提交并推送到同一 PR，全部门禁通过后转为 Ready。
```

Keep the existing product -> backend -> frontend merge order.

- [ ] **Step 3: Add a dedicated backend block-execution section**

The section must define this sequence exactly once:

```text
冻结契约 -> 建分支 -> 创建并确认执行台账 -> 推送 Draft PR
-> 单块 IN_PROGRESS -> 定向验证 -> VERIFIED -> 提交推送
-> 判断自动继续/暂停确认 -> PR 前完整验证 -> Ready for review
```

It must also say that each week uses 3 to 6 blocks, only one is in progress, and the ledger is updated only at start, completion, blocking, or scope change.

- [ ] **Step 4: Add mixed approval gates**

Define automatic continuation for direct contract-preserving work whose targeted tests pass. Define mandatory confirmation for Flyway migrations, permissions, transaction/concurrency decisions, irreversible data changes, contract deviations, new infrastructure, invalidated assumptions, scope growth above 25%, and unknown local changes.

- [ ] **Step 5: Add Draft PR and commit rules**

State that there is still one backend PR per week, one meaningful commit normally corresponds to one verified block, empty commits are forbidden, and GitHub outages do not invalidate the local ledger and commit history.

- [ ] **Step 6: Verify no workflow contradiction was introduced**

Run:

```powershell
rg -n "产品.*后端.*前端|Draft PR|3 至 6|IN_PROGRESS|25%|Ready for review" docs/governance/WEEKLY_DELIVERY_PLAYBOOK.md
```

Expected: all terms are present, and the original merge order remains present.

- [ ] **Step 7: Commit the playbook update**

```bash
git add docs/governance/WEEKLY_DELIVERY_PLAYBOOK.md
git commit -m "docs(governance): add incremental backend delivery flow"
```

---

### Task 3: Make The Workflow Mandatory For Backend Agents

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/agent-guides/12_BACKEND_LONG_TERM_AGENT.md`

**Interfaces:**
- Consumes: the team workflow and backend template.
- Produces: a short mandatory entry rule plus detailed backend Agent behavior for kickoff, block reporting, pausing, resuming, and finishing.

- [ ] **Step 1: Verify neither entry currently requires a weekly execution ledger**

Run:

```powershell
rg -n "WEEKLY_EXECUTION_TEMPLATE|backend/execution|Draft PR|工作块" AGENTS.md docs/agent-guides/12_BACKEND_LONG_TERM_AGENT.md
```

Expected: the complete requirement is absent.

- [ ] **Step 2: Add the shortest enforceable rule to `AGENTS.md`**

Add a subsection under Agent behavior that requires Java backend Agents to:

```markdown
1. 编码前从 `docs/backend/WEEKLY_EXECUTION_TEMPLATE.md` 创建当周执行台账。
2. 先向用户确认 3-6 个工作块，再修改业务代码。
3. 同一时间只执行一个工作块，并在开始、完成、阻塞或范围变化时回填当前检查点。
4. 普通块验证后可继续；迁移、权限、事务/并发、不可逆数据、契约偏离和范围显著扩大必须暂停确认。
5. 每个已验证工作块提交并推送到同一个 Draft PR，全部质量门通过后才转为 Ready。
```

Keep this entry concise and link to the playbook and backend guide for details.

- [ ] **Step 3: Rewrite the backend start prompt around block execution**

Require its opening output to include the execution-ledger path, current checkpoint, block list, the current block's expected result, modification boundary, contract effect, test level, and whether its exit is automatic or approval-gated.

- [ ] **Step 4: Add detailed backend block behavior**

The backend guide must say:

```text
Do not implement the entire week after a single kickoff.
Only read the code and documents needed by the current block after baseline discovery.
At block completion, record actual files, test evidence, deviations, commit, and next gate.
On resume, read the ledger checkpoint before rescanning the repository.
```

Express these rules in Chinese and preserve all existing Java architecture, consistency, caching, RAG, acquisition, and testing guidance.

- [ ] **Step 5: Add the three fixed user updates**

Define `开始更新`, `完成更新`, and `下一步更新`, with concise fields matching the design. Clarify that long-running commands only report meaningful changes and full logs do not enter the ledger.

- [ ] **Step 6: Verify the root and role rules agree**

Run:

```powershell
rg -n "3.?6|同一时间|Draft PR|当前检查点|暂停确认|自动继续" AGENTS.md docs/agent-guides/12_BACKEND_LONG_TERM_AGENT.md
```

Expected: both files point to the same workflow, with detail concentrated in the backend guide.

- [ ] **Step 7: Commit Agent instruction updates**

```bash
git add AGENTS.md docs/agent-guides/12_BACKEND_LONG_TERM_AGENT.md
git commit -m "docs(agent): require checkpointed backend work blocks"
```

---

### Task 4: Align Quality Gates And Validate The Documentation Graph

**Files:**
- Modify: `docs/governance/QUALITY_GATES.md`
- Verify: `docs/superpowers/specs/2026-09-15-backend-incremental-delivery-design.md`
- Verify: `docs/backend/WEEKLY_EXECUTION_TEMPLATE.md`
- Verify: `docs/governance/WEEKLY_DELIVERY_PLAYBOOK.md`
- Verify: `docs/agent-guides/12_BACKEND_LONG_TERM_AGENT.md`
- Verify: `AGENTS.md`

**Interfaces:**
- Consumes: all prior governance edits.
- Produces: one consistent quality gate for per-block evidence and final PR readiness.

- [ ] **Step 1: Prove quality gates do not yet distinguish block evidence from PR-wide evidence**

Run:

```powershell
rg -n "工作块|执行台账|Draft PR" docs/governance/QUALITY_GATES.md
```

Expected: no complete work-block gate is found.

- [ ] **Step 2: Add work-block quality gates**

Add rules requiring each block to have a bounded result, direct evidence, affected-neighbor regression, diff review, an updated ledger result, and no unexplained failure before `VERIFIED`.

- [ ] **Step 3: Clarify high-risk gate timing**

State that high-risk design must be shown before applying migrations or external-state changes, and its real MySQL/MQTT/Redis or dual-instance evidence must be present before the PR becomes Ready. Missing credentials or infrastructure remains an explicit blocker, not a reported success.

- [ ] **Step 4: Preserve and connect the anti-infinite-test rule**

Add one reference from the work-block gate to the existing stop criteria. Do not duplicate the full anti-repetition section or weaken its five stopping conditions.

- [ ] **Step 5: Run final consistency checks**

Run:

```powershell
$paths = @(
  'AGENTS.md',
  'docs/governance/WEEKLY_DELIVERY_PLAYBOOK.md',
  'docs/governance/QUALITY_GATES.md',
  'docs/agent-guides/12_BACKEND_LONG_TERM_AGENT.md',
  'docs/backend/WEEKLY_EXECUTION_TEMPLATE.md',
  'docs/superpowers/specs/2026-09-15-backend-incremental-delivery-design.md'
)
$paths | ForEach-Object { if (-not (Test-Path $_)) { throw "Missing: $_" } }
rg -n "PLANNED|IN_PROGRESS|VERIFIED|BLOCKED|SKIPPED" docs/backend/WEEKLY_EXECUTION_TEMPLATE.md
rg -n "Draft PR|3.?6|当前检查点|完整.*测试.*一次|25%" AGENTS.md docs/governance docs/agent-guides/12_BACKEND_LONG_TERM_AGENT.md docs/backend/WEEKLY_EXECUTION_TEMPLATE.md
git diff --check
```

Expected: every path exists, all five states appear in the template, shared rules are discoverable, and `git diff --check` reports no errors.

- [ ] **Step 6: Review for duplicated authority and placeholders**

Run:

```powershell
rg -n "TBD|TODO|待定|之后补充|完善功能|优化代码|补充测试" AGENTS.md docs/governance/WEEKLY_DELIVERY_PLAYBOOK.md docs/governance/QUALITY_GATES.md docs/agent-guides/12_BACKEND_LONG_TERM_AGENT.md docs/backend/WEEKLY_EXECUTION_TEMPLATE.md
```

Expected: no new placeholder or vague work-block language. Mentions inside explicit prohibition examples are acceptable only when clearly described as forbidden wording.

- [ ] **Step 7: Commit quality-gate alignment**

```bash
git add docs/governance/QUALITY_GATES.md
git commit -m "docs(quality): add backend work block gates"
```

- [ ] **Step 8: Verify branch scope and push**

Run:

```powershell
git status --short --branch
git diff origin/dev...HEAD --stat
git log --oneline origin/dev..HEAD
```

Expected: only the approved specification, implementation plan, template, and four governance/Agent files differ from `origin/dev`; commits each have one reviewable purpose.

Then push:

```bash
git push -u origin codex/backend-execution-workflow
```
