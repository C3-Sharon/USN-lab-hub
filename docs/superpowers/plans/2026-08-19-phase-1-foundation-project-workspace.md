# Foundation and Project Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在第 1 至 4 周交付角色化个人工作台、正式项目、项目成员、里程碑和轻量任务闭环，同时保持现有考勤与 IoT 能力可用。

**Architecture:** 保持现有 Spring Boot 模块化单体和 Vue 3 单页应用。新增 `project` 与 `workbench` 包，通过应用服务组合考勤、项目和任务摘要；项目成员关系承载项目级角色，全局角色继续来自 `sys_role`。每周产品契约先冻结，后端提供真实 API，前端最后同步 `dev` 联调。

**Tech Stack:** JDK 17, Spring Boot 3.1.5, MyBatis-Plus 3.5.5, MySQL 8, Flyway, JUnit 5, Vue 3.5, Vite 6, Element Plus 2.9, Axios, Vitest, Vue Test Utils.

## Global Constraints

- 三人每周各投入 12-20 小时，每周 4-5 天，每天不超过 8 小时。
- GitHub 是唯一开发主仓库；功能 PR 的 base 为 `dev`，`main` 只接收稳定发布。
- 每周从最新 `dev` 新建 `feature/mNN-wNN-role-topic`，合并后删除，不复用长期个人分支。
- 不重写已验证 PM-001 IoT 闭环，不修改其现有 API/MQTT 字段。
- 后端保持模块化单体，不引入微服务、Kafka、Kubernetes 或新搜索基础设施。
- Controller 不直接调用 Mapper；数据库变化只通过新的 Flyway 迁移。
- 前端遵守 `docs/design/UI_SPEC.md`，关键页面检查 1440x900、1280x800、390x844。
- 每项实现按失败测试、最小实现、通过测试、提交推进；满足 QUALITY_GATES 后停止测试。

---

## File Map

### Governance and contracts

- Create `docs/weekly/2026-W01.md`: 基线清点和阶段启动证据。
- Create `docs/weekly/2026-W02.md`: 个人工作台字段、角色和 API 契约。
- Create `docs/weekly/2026-W03.md`: 项目档案、成员和权限契约。
- Create `docs/weekly/2026-W04.md`: 里程碑、任务状态和验收契约。
- Create `docs/contracts/PROJECT_WORKSPACE_API.md`: 第 2-4 周统一 API 和错误样例。

### Backend

- Create `backend/src/main/resources/db/migration/V6__create_project_workspace_tables.sql`: 全局角色种子、项目、成员、里程碑和任务表。
- Modify `backend/src/main/java/com/usn/labhub/user/common/utils/UserContext.java`: 保存不可变当前用户与全局角色集合。
- Modify `backend/src/main/java/com/usn/labhub/user/common/interceptor/JwtInterceptor.java`: 将 token 角色放入上下文并保证清理。
- Modify `backend/src/main/java/com/usn/labhub/user/config/WebConfig.java`: 保护 workbench/project API，保留明确公开接口。
- Create `backend/src/main/java/com/usn/labhub/user/workbench/**`: 个人工作台 Controller、Service、VO。
- Create `backend/src/main/java/com/usn/labhub/user/project/**`: 项目、成员、里程碑、任务的 DTO、VO、Entity、Mapper、Service、Controller。
- Create `backend/src/test/java/com/usn/labhub/user/workbench/WorkbenchIntegrationTest.java`: 角色化摘要集成测试。
- Create `backend/src/test/java/com/usn/labhub/user/project/ProjectWorkspaceIntegrationTest.java`: 项目和成员集成测试。
- Create `backend/src/test/java/com/usn/labhub/user/project/TaskWorkflowServiceTest.java`: 任务状态机单元测试。
- Create `backend/src/test/java/com/usn/labhub/user/project/ProjectWorkspaceMigrationTest.java`: V6 迁移约束测试。

### Frontend

- Modify `frontend/package.json`: 增加 Vitest、Vue Test Utils 和 test scripts。
- Create `frontend/src/styles/tokens.css`: UI_SPEC Token。
- Modify `frontend/src/styles/global.css`: 引用 Token 和统一工作台基础样式。
- Modify `frontend/src/layout/Index.vue`: 响应式应用外壳和新导航。
- Modify `frontend/src/router/index.js`: project 路由和权限 meta。
- Create `frontend/src/api/workbench.js`: 首页摘要 API。
- Create `frontend/src/api/project.js`: 项目、成员、里程碑和任务 API。
- Replace `frontend/src/views/Dashboard.vue`: 角色化个人工作台。
- Create `frontend/src/views/project/ProjectList.vue`: 项目列表和空状态。
- Create `frontend/src/views/project/ProjectDetail.vue`: 项目概览与成员。
- Create `frontend/src/views/project/ProjectBoard.vue`: 里程碑和任务看板。
- Create `frontend/src/components/media/DeterministicPlaceholder.vue`: 稳定默认封面。
- Create `frontend/src/api/__tests__/workbench.test.js`: 首页数据适配测试。
- Create `frontend/src/views/project/__tests__/project-state.test.js`: 项目/任务状态测试。

## Shared Interfaces

所有 API 使用现有 `Result<T>` 外壳。第 2 周首页响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "user": {"id": 2, "name": "测试学生", "memberId": "20260001", "globalRoles": ["MEMBER"]},
    "attendance": {"todayStatus": "NOT_CHECKED_IN", "checkInTime": null, "checkOutTime": null, "weekHours": 0.0},
    "projects": [],
    "tasks": {"todo": 0, "inProgress": 0, "blocked": 0, "doneThisWeek": 0},
    "pendingItems": [],
    "deviceAlerts": []
  }
}
```

项目状态固定为 `PREPARING`, `ACTIVE`, `PAUSED`, `COMPLETED`, `ARCHIVED`。项目角色固定为 `OWNER`, `MAINTAINER`, `MEMBER`, `OBSERVER`。任务状态固定为 `TODO`, `IN_PROGRESS`, `BLOCKED`, `DONE`, `CANCELED`。

---

### Task 1: Week 1 Baseline and Contract Freeze

**Files:**
- Create: `docs/weekly/2026-W01.md`
- Modify: `docs/governance/CURRENT_STATE.md`
- Modify: `docs/governance/TRACEABILITY_MATRIX.md`

**Interfaces:**
- Consumes: baseline commit and approved design spec.
- Produces: one agreed inventory of existing behavior and one Phase 1 scope statement.

- [ ] **Step 1: Create the Week 1 product brief**

Copy `docs/weekly/TEMPLATE.md`. Set goal to “三人能从同一事实基线开始第 2 周”，risk `LOW`, status `FROZEN`; list current routes, API groups, database migrations and existing tests. Explicitly state that Week 1 adds no business API.

- [ ] **Step 2: Record role-specific inventories**

Product lists terminology and final acceptance links; frontend lists routes, views, global styles and missing test tooling; backend lists controllers, tables, Flyway versions and risks including `/api/**` authentication coverage. Each inventory links exact paths.

- [ ] **Step 3: Verify the baseline once**

Run on Windows:

```bash
cd backend
mvnw.cmd test
mvnw.cmd -DskipTests package
cd ../frontend
npm ci
npm run build
```

Expected: Maven tests pass, backend jar is created, Vite build exits 0. Record exact counts and any pre-existing warning; do not repeat unchanged full suites.

- [ ] **Step 4: Commit the three role PRs**

```bash
git commit -m "docs(product): freeze phase 1 scope"
git commit -m "docs(frontend): inventory current application shell"
git commit -m "docs(backend): inventory project workspace baseline"
```

Merge product, backend, frontend to `dev`; mark Week 1 `VERIFIED` only after the combined baseline commands pass.

---

### Task 2: Week 2 Product Contract and Frontend Test Harness

**Files:**
- Create: `docs/weekly/2026-W02.md`
- Create: `docs/contracts/PROJECT_WORKSPACE_API.md`
- Modify: `frontend/package.json`
- Create: `frontend/vitest.config.js`
- Create: `frontend/src/test/setup.js`

**Interfaces:**
- Consumes: Shared Interfaces in this plan.
- Produces: frozen `GET /api/workbench/summary` contract and `npm test` command.

- [ ] **Step 1: Freeze the Week 2 response and permission examples**

Document the exact JSON above plus teacher/admin examples. Define `401` as missing/invalid token, `403` as authenticated but forbidden, and `200` with empty arrays as a valid no-project state. The endpoint must never require IoT availability.

- [ ] **Step 2: Add a failing frontend adapter test**

Create `frontend/src/api/__tests__/workbench.test.js`:

```js
import { describe, expect, it } from 'vitest'
import { normalizeWorkbench } from '../workbench'

describe('normalizeWorkbench', () => {
  it('keeps a stable empty state', () => {
    expect(normalizeWorkbench({ user: { globalRoles: ['MEMBER'] } })).toEqual({
      user: { globalRoles: ['MEMBER'] },
      attendance: { todayStatus: 'NOT_CHECKED_IN' },
      projects: [],
      tasks: { todo: 0, inProgress: 0, blocked: 0, doneThisWeek: 0 },
      pendingItems: [],
      deviceAlerts: []
    })
  })
})
```

- [ ] **Step 3: Configure Vitest**

Add scripts `"test": "vitest run"` and `"test:watch": "vitest"`; add dev dependencies `vitest`, `jsdom`, `@vue/test-utils`. Configure jsdom and `@` alias in `frontend/vitest.config.js` using `fileURLToPath(new URL('./src', import.meta.url))`.

- [ ] **Step 4: Run the test and commit**

Run `npm test -- workbench.test.js`; expected failure is unresolved `../workbench`. Commit product contract separately from frontend harness.

---

### Task 3: Week 2 Role-Aware Workbench Backend

**Files:**
- Modify: `backend/src/main/java/com/usn/labhub/user/common/utils/UserContext.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/common/interceptor/JwtInterceptor.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/config/WebConfig.java`
- Create: `backend/src/main/java/com/usn/labhub/user/workbench/WorkbenchController.java`
- Create: `backend/src/main/java/com/usn/labhub/user/workbench/WorkbenchService.java`
- Create: `backend/src/main/java/com/usn/labhub/user/workbench/WorkbenchSummaryVO.java`
- Test: `backend/src/test/java/com/usn/labhub/user/workbench/WorkbenchIntegrationTest.java`

**Interfaces:**
- Consumes: authenticated `UserContext.getUserId()` and existing attendance overview.
- Produces: `WorkbenchService#getSummary(long userId)` and `GET /api/workbench/summary`.

- [ ] **Step 1: Write failing integration tests**

Use MockMvc with a valid test token. Assert `200` and exact empty collections for a member; assert `401` without token; assert attendance remains present when project repositories return empty. Do not start MQTT in this test profile.

- [ ] **Step 2: Define the immutable response contract**

Create `WorkbenchSummaryVO` as Java records:

```java
public record WorkbenchSummaryVO(
    UserSummary user,
    AttendanceSummary attendance,
    List<ProjectSummary> projects,
    TaskSummary tasks,
    List<PendingItem> pendingItems,
    List<DeviceAlert> deviceAlerts) {
  public record UserSummary(Long id, String name, String memberId, Set<String> globalRoles) {}
  public record AttendanceSummary(String todayStatus, String checkInTime, String checkOutTime, double weekHours) {}
  public record ProjectSummary(Long id, String code, String name, String role, String status, int progress) {}
  public record TaskSummary(long todo, long inProgress, long blocked, long doneThisWeek) {}
  public record PendingItem(String type, Long targetId, String title) {}
  public record DeviceAlert(Long deviceId, String deviceCode, String severity, String title) {}
}
```

- [ ] **Step 3: Implement authenticated summary**

Add `@GetMapping("/api/workbench/summary")`; read user ID from UserContext and delegate to WorkbenchService. In Week 2, project/task/pending/device collections are empty adapters, not hard-coded sample projects. Map existing attendance status `0/1/2` to `NOT_CHECKED_IN/CHECKED_IN/CHECKED_OUT`.

- [ ] **Step 4: Protect the route**

Extend `WebConfig` protected patterns with `/api/workbench/**` and later `/api/projects/**`. Preserve `/api/iot/public/**`; document existing IoT endpoint authentication separately rather than changing all `/api/iot/**` in this task.

- [ ] **Step 5: Run and commit**

Run `mvnw.cmd -Dtest=WorkbenchIntegrationTest test`; expected PASS. Then run existing attendance tests and `mvnw.cmd -DskipTests package`. Commit `feat(workbench): add role-aware summary endpoint`.

---

### Task 4: Week 2 Application Shell and Personal Workbench

**Files:**
- Create: `frontend/src/styles/tokens.css`
- Modify: `frontend/src/styles/global.css`
- Modify: `frontend/src/layout/Index.vue`
- Create: `frontend/src/api/workbench.js`
- Replace: `frontend/src/views/Dashboard.vue`
- Create: `frontend/src/components/media/DeterministicPlaceholder.vue`
- Test: `frontend/src/api/__tests__/workbench.test.js`

**Interfaces:**
- Consumes: `GET /api/workbench/summary`.
- Produces: role-aware first screen with attendance, projects, tasks and stable empty states.

- [ ] **Step 1: Implement the data adapter**

Export `getWorkbenchSummary()` using the existing request helper and `normalizeWorkbench(raw)` with the exact defaults asserted in Task 2. Run `npm test -- workbench.test.js`; expected PASS.

- [ ] **Step 2: Implement tokens and shell**

Copy UI_SPEC token values into `tokens.css`, import it before global styles, change the 232px dark sidebar to a 224px restrained shell, add mobile drawer behavior, and rename navigation “个人考勤”为“个人工作台”. Do not remove existing IoT routes.

- [ ] **Step 3: Implement the dashboard states**

Dashboard loads summary on mount and renders: compact attendance action, current projects, task counts, pending items and device alerts. Provide skeleton loading, retry error, no-project empty state and role-aware labels. Reuse the existing attendance action and refresh summary after success.

- [ ] **Step 4: Add deterministic project placeholder**

`DeterministicPlaceholder` accepts `kind`, `code`, `status`, `aspectRatio`; derive a stable blue/indigo accent from the code character sum. It must not call a network or AI service and must have an accessible label.

- [ ] **Step 5: Verify screenshots and commit**

Run `npm test`, `npm run build`; inspect 1440x900, 1280x800, 390x844 for normal, empty and error states. Record screenshots under `docs/evidence/m01-w02/`. Commit `feat(frontend): add personal lab workbench`.

---

### Task 5: Week 3 Project and Membership Backend

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__create_project_workspace_tables.sql`
- Create: `backend/src/main/java/com/usn/labhub/user/project/domain/Project.java`
- Create: `backend/src/main/java/com/usn/labhub/user/project/domain/ProjectMember.java`
- Create: `backend/src/main/java/com/usn/labhub/user/project/ProjectMapper.java`
- Create: `backend/src/main/java/com/usn/labhub/user/project/ProjectService.java`
- Create: `backend/src/main/java/com/usn/labhub/user/project/ProjectController.java`
- Test: `backend/src/test/java/com/usn/labhub/user/project/ProjectWorkspaceIntegrationTest.java`
- Test: `backend/src/test/java/com/usn/labhub/user/project/ProjectWorkspaceMigrationTest.java`

**Interfaces:**
- Produces: `POST /api/projects`, `GET /api/projects`, `GET /api/projects/{id}`, `POST /api/projects/{id}/members`.
- Authorization: creator becomes OWNER; OWNER manages members; any project member reads; archived project rejects membership mutation.

- [ ] **Step 1: Freeze Week 3 contract**

Create `docs/weekly/2026-W03.md` with exact create request `{code,name,summary,category,coverMediaId}` and project/member response. Code is uppercase letters, digits and hyphens, 3-32 characters. Name is 2-80 characters. Cover is optional.

- [ ] **Step 2: Write migration tests then V6 migration**

V6 creates `lab_project_workspace` with unique `code`, status, version, timestamps; `lab_project_member` with unique `(project_id,user_id)`, project_role and joined_at; it also creates empty milestone/task tables for Task 7. Assert duplicate project code and duplicate membership are rejected by MySQL.

- [ ] **Step 3: Write project integration tests**

Test creator OWNER membership in the same transaction, duplicate code returns conflict, non-member receives 403, OWNER can add MEMBER, duplicate add is idempotent, archived project rejects add. Use explicit database cleanup per test.

- [ ] **Step 4: Implement application service**

Expose `createProject(CreateProjectCommand command, CurrentActor actor)`, `addMember(long projectId, AddProjectMemberCommand command, CurrentActor actor)`, `getProject(long projectId, CurrentActor actor)`, `listMyProjects(CurrentActor actor)`. Use a transaction for project plus OWNER membership and unique constraints for races.

- [ ] **Step 5: Integrate workbench and commit**

Replace Week 2 empty project adapter with `ProjectQueryService.listWorkbenchProjects(userId)`. Run migration, project integration, workbench and existing IoT readiness tests. Commit `feat(project): add project and membership workspace`.

---

### Task 6: Week 3 Project Frontend

**Files:**
- Create: `frontend/src/api/project.js`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/layout/Index.vue`
- Create: `frontend/src/views/project/ProjectList.vue`
- Create: `frontend/src/views/project/ProjectDetail.vue`
- Test: `frontend/src/views/project/__tests__/project-state.test.js`

**Interfaces:**
- Consumes: Task 5 endpoints and project status/role enums.
- Produces: create, list, open and member-view flows.

- [ ] **Step 1: Test status and permission helpers**

Assert OWNER/MAINTAINER can see management entry, MEMBER/OBSERVER cannot; ARCHIVED disables mutation; unknown status renders “未知状态” without crashing.

- [ ] **Step 2: Implement API and routes**

Add `/projects`, `/projects/:id`, `/projects/:id/board`; keep old `/iot/projects` routes available but label them IoT demonstrations until Week 16 migration.

- [ ] **Step 3: Implement list and create flow**

List supports loading, no-project, request error and cards with optional cover/placeholder. Create form validates code/name and submits exact contract. Successful create routes to details.

- [ ] **Step 4: Implement details and membership**

Display project metadata, current role, status and members. OWNER sees add-member action; other roles see read-only state. Show 403 and archived state distinctly from 404.

- [ ] **Step 5: Real integration and commit**

Create a project as admin, add student, login as student and verify visibility/permissions. Run tests/build and fixed viewport screenshots. Commit `feat(frontend): add project workspace`.

---

### Task 7: Week 4 Milestone and Task Workflow Backend

**Files:**
- Create: `backend/src/main/java/com/usn/labhub/user/project/domain/ProjectMilestone.java`
- Create: `backend/src/main/java/com/usn/labhub/user/project/domain/ProjectTask.java`
- Create: `backend/src/main/java/com/usn/labhub/user/project/TaskWorkflow.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/project/ProjectService.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/project/ProjectController.java`
- Test: `backend/src/test/java/com/usn/labhub/user/project/TaskWorkflowServiceTest.java`
- Test: `backend/src/test/java/com/usn/labhub/user/project/ProjectWorkspaceIntegrationTest.java`

**Interfaces:**
- Produces: milestone create/list; task create/list/update/transition.
- State transitions: TODO -> IN_PROGRESS/CANCELED; IN_PROGRESS -> BLOCKED/DONE/CANCELED; BLOCKED -> IN_PROGRESS/CANCELED; terminal DONE/CANCELED cannot transition.

- [ ] **Step 1: Freeze Week 4 contract**

Create `docs/weekly/2026-W04.md`. A task has title, description, assigneeUserId, milestoneId, priority `LOW/MEDIUM/HIGH`, dueDate, status and version. Only OWNER/MAINTAINER creates milestones; OWNER/MAINTAINER or assignee changes a task within allowed transitions.

- [ ] **Step 2: Unit test TaskWorkflow**

Enumerate every allowed transition and representative forbidden transitions. Assert forbidden transition throws a domain exception containing old and requested states. This is a finite table test, not random testing.

- [ ] **Step 3: Integration test optimistic updates**

Create two updates with the same version; assert exactly one succeeds and the other returns conflict. Verify non-member 403, observer cannot mutate, task assignee can move own task, and audit fields record actor/time.

- [ ] **Step 4: Implement workflow and queries**

Use conditional `UPDATE ... WHERE id=? AND version=? AND status=?`; increment version on success. Query board by project and milestone in stable `(sort_order,id)` order. Workbench task counts derive from memberships and assignee.

- [ ] **Step 5: Run and commit**

Run task unit tests, project/workbench integration and full backend tests once. Commit `feat(project): add milestone and task workflow`.

---

### Task 8: Week 4 Board, Integration, and v0.2 Release

**Files:**
- Create: `frontend/src/views/project/ProjectBoard.vue`
- Modify: `frontend/src/api/project.js`
- Modify: `frontend/src/views/project/ProjectDetail.vue`
- Modify: `frontend/src/views/Dashboard.vue`
- Test: `frontend/src/views/project/__tests__/project-state.test.js`
- Modify: `docs/governance/CURRENT_STATE.md`
- Modify: `docs/governance/TRACEABILITY_MATRIX.md`

**Interfaces:**
- Consumes: Task 7 milestone/task endpoints.
- Produces: project board, task transition UI and Phase 1 verification evidence.

- [ ] **Step 1: Test board state mapping**

Assert every backend status maps to one stable column and allowed actions match role plus assignee. Stale version conflict must show refresh guidance rather than silently overwriting.

- [ ] **Step 2: Implement the board**

Use fixed columns TODO, IN_PROGRESS, BLOCKED, DONE; CANCELED appears in filtered history. On mobile, render columns as vertically stacked sections. Do not add drag-and-drop in Phase 1; use explicit status menu to keep permission/error behavior clear.

- [ ] **Step 3: Connect workbench task counts**

Refresh dashboard from the real summary endpoint after task changes. Verify member sees assigned task counts and project OWNER sees project role, without adding manager-wide analytics.

- [ ] **Step 4: Run Phase 1 real integration**

From clean `dev`: migrate MySQL; login admin; create project; add student; create milestone/task; login student; start and finish task; return dashboard; verify attendance and IoT routes still work. Capture desktop/mobile screenshots and API evidence.

- [ ] **Step 5: Run release gates**

Run full backend tests once, frontend tests/build, then the manual script. Record exact results in `docs/weekly/2026-W04.md`. Update CURRENT_STATE and mark only verified Phase 1 rows in TRACEABILITY_MATRIX.

- [ ] **Step 6: Commit and release**

Commit frontend as `feat(frontend): add project task board`; commit evidence as `docs(release): verify phase 1 project workspace`. Open `dev -> main` PR titled `release: v0.2 project workspace`. Tag only after PR checks and manual script pass.

## Phase 1 Exit Gate

Phase 1 completes only when all of the following are true:

- Admin and member logins preserve attendance behavior.
- Personal workbench uses real summary data and handles empty/error states.
- A project can be created, a member added, a milestone/task created, and an assignee can complete it.
- Project roles prevent observer/non-member mutation.
- Concurrent stale task update is rejected, not silently overwritten.
- Existing PM-001 latest/history/operations/SSE regression remains green.
- Three fixed viewport screenshots have no overlap or overflow.
- Week 1-4 PRs, tests and evidence are linked in the traceability matrix.
