# Frontend API Development Proxy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all USN development requests reach a configurable local Java backend while preserving existing IoT behavior and the default port.

**Architecture:** Keep browser requests same-origin, widen the existing Vite proxy context from `/api/iot` to `/api`, and resolve one proxy target from `VITE_API_PROXY_TARGET` with an 8080 fallback. `/api`、`/usnhub` and `/admin` share the target so authentication and business requests cannot split across applications.

**Tech Stack:** Vue 3, Vite 6, Node.js built-in test helpers, npm.

## Global Constraints

- Do not change API fields, MQTT topics, database structures, permissions, UI fields, or production API addresses.
- Keep `/usnhub` and `/admin` paths, but make all three proxy entries use the same target variable.
- `VITE_USE_MOCK=false` remains the real-interface mode; mock is not enabled by default.
- Run only the affected W39 contract test and one production build.

---

### Task 1: Cover and widen the Vite API proxy

**Files:**
- Modify: `frontend/src/api/__tests__/w39-contract-test.mjs`
- Modify: `frontend/vite.config.js`
- Modify: `frontend/.env.example`

**Interfaces:**
- Consumes: browser requests whose path begins with `/api`.
- Produces: Vite development proxy forwarding all USN paths to `VITE_API_PROXY_TARGET`, defaulting to `http://localhost:8080`.

- [ ] **Step 1: Add a failing regression assertion**

Read `../../../vite.config.js` beside the existing static source fixtures and add this test:

```js
const viteConfigSource = readFileSync(new URL('../../../vite.config.js', import.meta.url), 'utf8')

test('Vite 开发代理覆盖全部 /api 接口', () => {
  assert.match(viteConfigSource, /['"]\/api['"]\s*:\s*\{/)
  assert.doesNotMatch(viteConfigSource, /['"]\/api\/iot['"]\s*:\s*\{/)
})
```

- [ ] **Step 2: Verify the assertion fails against the old configuration**

Run:

```powershell
cd frontend
node src/api/__tests__/w39-contract-test.mjs
```

Expected: `30 passed, 1 failed`; the new proxy assertion reports that `/api` is absent.

- [ ] **Step 3: Widen the proxy context**

In `frontend/vite.config.js`, replace only the proxy key:

```js
'/api': {
  target: 'http://localhost:8080',
  changeOrigin: true
}
```

Do not add a second `/api/iot` entry because `/api` already covers that path.

- [ ] **Step 4: Run the focused contract test**

Run:

```powershell
cd frontend
node src/api/__tests__/w39-contract-test.mjs
```

Expected: `31 passed, 0 failed`.

- [ ] **Step 5: Add a failing configurable-target assertion**

Extend the same static test group to require `loadEnv`, `VITE_API_PROXY_TARGET`, the 8080 fallback, and three `target: apiProxyTarget` entries. Run the W39 test and expect `31 passed, 1 failed` against the fixed but still hard-coded proxy.

- [ ] **Step 6: Make the target configurable**

Load Vite environment values by mode, define:

```js
const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080'
```

Use `target: apiProxyTarget` for `/api`、`/usnhub` and `/admin`, and add `VITE_API_PROXY_TARGET=http://localhost:8080` to `frontend/.env.example`.

- [ ] **Step 7: Run the focused contract test**

Expected: `32 passed, 0 failed`.

- [ ] **Step 8: Build the frontend once**

Run:

```powershell
cd frontend
npm run build
```

Expected: Vite exits successfully and writes the production bundle to `frontend/dist`; build output remains ignored by Git.

- [ ] **Step 9: Review and commit the implementation**

Run:

```powershell
git diff --check
git status --short
git add frontend/.env.example frontend/vite.config.js frontend/src/api/__tests__/w39-contract-test.mjs docs/superpowers/specs/2026-09-27-frontend-api-dev-proxy-design.md docs/superpowers/plans/2026-09-27-frontend-api-dev-proxy.md
git commit -m "fix(frontend): make development API target configurable"
```

Expected: only the three frontend configuration/test files and the two proxy design documents are included.
