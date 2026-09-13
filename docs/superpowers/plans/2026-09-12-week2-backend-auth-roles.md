# Week 2 Backend Auth And Roles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: execute this plan task-by-task with a test-first cycle and review each task before continuing.

**Goal:** Implement the frozen Week 2 global-role, JWT, authorization, disabled-account, and personal-workbench backend contract.

**Architecture:** Keep the Spring Boot modular monolith and the existing MVC interceptor. Resolve the authenticated account and all current roles from MySQL on every protected request, store them only in the request thread context, and enforce sensitive operations through a small method-level role annotation. Build the workbench as an application service that reuses attendance and IoT services and isolates optional device-reminder failures.

**Tech Stack:** Java 17, Spring Boot 3.1.5, Spring MVC, MyBatis-Plus/MyBatis, MySQL 8, Flyway, JJWT, JUnit 5, Mockito, H2 in MySQL compatibility mode for HTTP contract tests.

## Global Constraints

- Follow `docs/contracts/AUTH_WORKBENCH_V1.md` exactly; it is FROZEN.
- Do not add Spring Security, OAuth, Redis token blacklists, microservices, or formal SSE authentication.
- Keep `/admin/**` paths and keep `GET /api/iot/public/**` public.
- Preserve `sys_role.id` values 1 and 2; GUEST is not stored in the database.
- Treat `state` as the only workbench region-state field.
- Query current user status and roles on every authenticated request.
- Stop testing once each new risk class and adjacent regression has direct evidence.

---

### Task 1: Role Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__expand_global_roles.sql`
- Modify: `backend/src/test/java/com/usn/labhub/user/controller/iot/IotWeek6ReadinessIntegrationTest.java`
- Create: `backend/src/test/java/com/usn/labhub/user/auth/GlobalRoleMigrationTest.java`

**Interfaces:**
- Produces role keys `SYSTEM_ADMIN`, `TEACHER`, `STOCK_KEEPER`, and `MEMBER` while preserving existing user-role links.

- [x] Add an H2 Flyway test that checks four roles, IDs 1/2, and existing `sys_user_role` rows.
- [x] Add V6 with two updates followed by two inserts.
- [x] Update the existing migration-version regression from `[1,2,4,5]` to `[1,2,4,5,6]`.
- [x] Run the two migration-focused tests.

### Task 2: Authentication Context And Error Contract

**Files:**
- Create: `backend/src/main/java/com/usn/labhub/user/common/auth/AuthReason.java`
- Create: `backend/src/main/java/com/usn/labhub/user/common/auth/AuthException.java`
- Create: `backend/src/main/java/com/usn/labhub/user/common/auth/RequireRoles.java`
- Create: `backend/src/main/java/com/usn/labhub/user/common/auth/AuthenticatedAccount.java`
- Create: `backend/src/main/java/com/usn/labhub/user/common/auth/AuthenticationService.java`
- Create: `backend/src/main/java/com/usn/labhub/user/common/auth/AuthResponseWriter.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/common/result/Result.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/common/utils/UserContext.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/common/interceptor/JwtInterceptor.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/common/exception/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/config/WebConfig.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/mapper/SysUserMapper.java`
- Modify: `backend/src/main/resources/mapper/SysUserMapper.xml`
- Create: `backend/src/test/java/com/usn/labhub/user/auth/JwtInterceptorTest.java`

**Interfaces:**
- Produces HTTP 401/403 bodies with `code`, `msg`, `reason`, and null `data`.
- Produces `UserContext.getRoles()` containing current database roles.

- [x] Test missing, invalid, expired, legacy-header, Bearer-header, conflicting-header, disabled-account, and insufficient-role cases.
- [x] Implement precise token parsing and JSON error responses.
- [x] Load account status and roles by JWT user ID and verify the member ID matches.
- [x] Expand interception to `/usnhub/**`, `/admin/**`, and `/api/**` with the frozen public exclusions.
- [x] Run the interceptor test class.

### Task 3: Login Multi-Role Contract

**Files:**
- Create: `backend/src/main/java/com/usn/labhub/user/domain/vo/RoleInfoVO.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/domain/vo/LoginVO.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/service/impl/ISysUserServiceImpl.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/mapper/SysUserMapper.java`
- Modify: `backend/src/main/resources/mapper/SysUserMapper.xml`
- Create: `backend/src/test/java/com/usn/labhub/user/service/UserLoginServiceTest.java`

**Interfaces:**
- Produces `roles`, `primaryRoleKey`, `primaryRoleName`, `roleKey`, and `role` in `LoginVO.UserInfo`.

- [x] Test disabled login, multiple roles, primary-role priority, and MEMBER assignment for a new member.
- [x] Replace the arbitrary single-role login join with one account/profile query plus an ordered role query.
- [x] Sign the JWT with the primary role for legacy compatibility while authorization continues to use database roles.
- [x] Resolve the MEMBER role ID by key when creating members.
- [x] Run the login service test class.

### Task 4: Endpoint Authorization

**Files:**
- Modify: `backend/src/main/java/com/usn/labhub/user/controller/AdminMemberController.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/controller/AdminAttendanceController.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/controller/iot/IotOperationsController.java`
- Create: `backend/src/test/java/com/usn/labhub/user/controller/Week2AuthorizationIntegrationTest.java`

**Interfaces:**
- Uses `@RequireRoles` to enforce the frozen operation matrix after authentication.

- [x] Test the role gates for TEACHER reads, TEACHER member-write denial, MEMBER admin denial, and SYSTEM_ADMIN-only command access.
- [x] Annotate member writes for SYSTEM_ADMIN only and member/attendance reads for SYSTEM_ADMIN or TEACHER.
- [x] Annotate IoT alert/recommendation actions for SYSTEM_ADMIN or TEACHER and command creation for SYSTEM_ADMIN only.
- [x] Run the authorization integration test.

### Task 5: Workbench Overview

**Files:**
- Create: `backend/src/main/java/com/usn/labhub/user/domain/vo/WorkbenchOverviewVO.java`
- Create: `backend/src/main/java/com/usn/labhub/user/service/WorkbenchService.java`
- Create: `backend/src/main/java/com/usn/labhub/user/controller/WorkbenchController.java`
- Create: `backend/src/test/java/com/usn/labhub/user/service/WorkbenchServiceTest.java`
- Create: `backend/src/test/java/com/usn/labhub/user/controller/Week2WorkbenchIntegrationTest.java`

**Interfaces:**
- Produces `GET /api/workbench/overview` with six regions and `READY`, `NOT_AVAILABLE`, or `ERROR` state.

- [x] Test normal overview, device-reminder degradation, and fatal attendance failure.
- [x] Map current attendance into a READY section.
- [x] Return deterministic NOT_AVAILABLE sections for project, task, learning, and notification domains.
- [x] Derive device online and open-warning counts from existing IoT services, converting optional failures to ERROR.
- [x] Run workbench unit and HTTP contract tests.

### Task 6: Verification And Handoff

**Files:**
- Create: `docs/backend/2026-W38-backend-auth-workbench.md`

**Interfaces:**
- Produces reproducible migration, startup, login, error, authorization, and workbench verification instructions.

- [x] Run the complete backend test suite once.
- [x] Run the package build once without repeating unchanged tests.
- [ ] Validate V6 against the configured MySQL instance and record the result without committing credentials.
- [x] Verify the public SSE endpoint remains reachable without a token.
- [x] Review the final diff for contract drift, secrets, generated files, and unrelated changes.
- [x] Commit and push `feature/m01-w02-backend-auth-roles`.
