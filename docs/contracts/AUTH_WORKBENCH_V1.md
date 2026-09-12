# 全局角色、权限与个人工作台契约 V1

> 状态：REVIEW
> 版本：v1.0
> 最后确认周：第 2 周（2026-W38）
> 影响范围：API、权限、前端字段、数据库（新增角色枚举）
> 关联周文档：`docs/weekly/2026-W38.md`

## 1. 目标与非目标

### 目标

把当前 `admin/student` 两级角色升级为五档全局角色，统一鉴权 Header 和 401/403 错误格式，冻结个人工作台首页聚合接口与信息层级。不同角色登录后看到正确的首页区域和数据范围。本周只冻结契约，不实现项目成员、项目角色或新的业务模块。

### 非目标

- 不实现项目成员和项目角色（第 3 周）
- 不实现项目、任务、学习域的真实数据
- 不实现库存权限业务（第 9-10 周）
- 不实现动态二维码、请假或补签
- 不平台化多设备 IoT
- 不引入 Spring Security、OAuth、Redis 黑名单或微服务
- 不修改 MQTT、库存、RAG 或 Agent 契约
- 不把未开放模块做成 mock 数据冒充真实能力
- 不把 `/admin/**` 迁移到 `/api/admin/**`（本周保留现有路径）

## 2. 现状矛盾与决策

| 矛盾 | 现状 | 决策 |
|---|---|---|
| 角色档数 | DB 只有 admin/student 两档 | 扩展为五档全局角色，保留现有 role_id=1/2，直接更新 role_key |
| 配置与实际 Header 不一致 | 配置写 Authorization，拦截器读 `token` | 统一为 `Authorization: Bearer`，兼容旧 `token` Header 至第 4 周；两者不一致返回 401 |
| 前端同时发两个 Header | 同时发 Authorization 和 token | 第 2 周保留双发，第 4 周移除 token |
| 拦截范围不足 | 仅覆盖 `/usnhub/**` | 扩展到 `/usnhub/**`、`/admin/**` 和需要鉴权的 `/api/**`；明确放行清单 |
| 角色守卫 | 前端只有 `meta.role === 'admin'` | 扩展为 roles 数组，支持多角色判断 |
| 首页内容 | 只有考勤 | 聚合考勤 + 模块占位（未开放显示"尚未开放"） |
| 访客角色 | 未显式定义 | 定义为未登录状态，只能访问显式公开页面，不入库 |
| SSE 鉴权 | 前端 EventSource 无法附加 Header | 保留现有 `/api/iot/public/**` SSE 为公开演示接口，不被 `/api/**` 拦截误伤 |
| 指令权限 | 当前所有登录用户都能下发指令 | 硬件指令暂时仅限 SYSTEM_ADMIN（历史演示兼容，待第 16-17 周平台化后重新设计） |
| 禁用用户 Token | 无明确策略 | 立即失效，后端每次鉴权查询用户状态；本周不引入 Redis 黑名单 |

## 3. 全局角色枚举

### 3.1 角色定义

| roleKey | 中文名称 | 权限范围 | 说明 |
|---|---|---|---|
| `SYSTEM_ADMIN` | 系统管理员 | 账号管理、角色管理、系统配置、全局权限、安全设置、全部数据查看 | 最高权限角色 |
| `TEACHER` | 老师/实验室负责人 | 查看全部项目、全部人员、全部考勤统计、设备告警总览 | 可以查看全部成员数据，不能管理账号或角色 |
| `STOCK_KEEPER` | 库存管理员 | 采购入库、库存盘点、条件审批、库存修正、出入库流水 | 仅库存域管理权限（第 9 周起生效） |
| `MEMBER` | 普通成员 | 本人考勤、学习实验、参与项目、申请资产、领用物料 | 默认角色，绝大多数成员属于此类 |
| `GUEST` | 访客 | 仅访问显式公开的页面和接口 | 未登录用户，不需要数据库记录 |

### 3.2 数据库迁移方案

**原则**：保留现有 `role_id=1/2` 不变，直接更新 role_key，只新增 TEACHER 和 STOCK_KEEPER。GUEST 不创建数据库角色。

Flyway 迁移步骤：

```sql
-- 1. 更新已有角色的 role_key（保留 id 不变）
UPDATE sys_role SET role_key = 'SYSTEM_ADMIN', role_name = '系统管理员' WHERE role_key = 'admin';
UPDATE sys_role SET role_key = 'MEMBER', role_name = '普通成员' WHERE role_key = 'student';

-- 2. 新增两个角色
INSERT INTO sys_role (role_name, role_key) VALUES ('老师/实验室负责人', 'TEACHER');
INSERT INTO sys_role (role_name, role_key) VALUES ('库存管理员', 'STOCK_KEEPER');
```

验证要点：
- 既有 `sys_user_role` 关系不丢失（因为 role_id 不变）
- `role_key` 唯一键不冲突（先更新旧值，再插入新值，顺序不能反）
- GUEST 不写入 `sys_role`，是逻辑角色

### 3.3 多角色与主角色

- `sys_user_role` 为多对多关系，一个用户可以拥有多个全局角色
- 登录响应和首页用户信息以 `roles` 数组为正式字段
- 提供 `primaryRoleKey` 作为展示用主角色（按 SYSTEM_ADMIN > TEACHER > STOCK_KEEPER > MEMBER 优先级确定）
- 旧 `role` / `roleKey` 字段暂时兼容，值等于 `primaryRoleKey`，第 4 周评估移除
- JWT 鉴权不依赖 Token 中存储的角色声明；后端每次鉴权从数据库查询当前用户的有效角色列表

### 3.4 角色与身份的区别

- **全局角色**（roleKey）：决定用户在整个平台中的权限边界，由管理员分配
- **身份**（identity）：本科/硕士/博士/老师，是人员档案属性，不直接决定权限
- 老师身份的用户不一定是 TEACHER 角色；TEACHER 角色也可以给实验室负责人（非老师身份）

## 4. 鉴权与错误响应

### 4.1 Token Header

**标准 Header**：

```
Authorization: Bearer <jwt_token>
```

**兼容 Header**（第 2-3 周保留，第 4 周移除）：

```
token: <jwt_token>
```

后端读取规则：

1. 同时检查 `Authorization` 和 `token` 两个 Header
2. 两者都不存在 → 401（`reason: TOKEN_MISSING`）
3. 两者都存在但值不一致 → 401（`reason: TOKEN_INVALID`）
4. 任一存在且有效 → 通过

前端发送规则（第 2 周）：

- 继续同时发送 `Authorization: Bearer ${token}` 和 `token: ${token}`
- 第 4 周产品确认后移除 `token` Header

### 4.2 拦截范围

**需要鉴权的路径**：

- `/usnhub/**`（登录接口除外）
- `/admin/**`
- `/api/**`（公开接口除外）

**放行清单**：

- `POST /usnhub/user/login`（登录）
- `OPTIONS /**`（预检请求）
- `GET /api/iot/public/**`（公开项目展示、公开 SSE）
- `/iot/public` 页面（前端路由）
- `/login` 页面（前端路由）

本周不迁移 `/admin/**` 到 `/api/admin/**`，保持现有路径。

### 4.3 SSE 公开接口

现有 `/api/iot/public/devices/1/telemetry/stream` 为公开演示 SSE 接口，前端使用原生 EventSource 无法附加 Authorization Header。

- 该接口保留为公开只读，不被 `/api/**` 鉴权拦截
- 正式 SSE 鉴权（第 16-17 周平台化后）单独设计，可采用 token query 参数或独立公开端点策略
- 本周不改变 SSE 接口路径和行为

### 4.4 401 未授权

HTTP 状态：`401 Unauthorized`

响应体：

```json
{
  "code": 401,
  "msg": "登录状态已失效，请重新登录",
  "reason": "TOKEN_EXPIRED",
  "data": null
}
```

`reason` 枚举：

| reason | 说明 | 前端动作 |
|---|---|---|
| `TOKEN_MISSING` | Token 缺失 | 清除登录态，跳转登录页 |
| `TOKEN_INVALID` | 签名无效、格式错误、双 Header 值不一致 | 清除登录态，跳转登录页 |
| `TOKEN_EXPIRED` | Token 已过期 | 清除登录态，跳转登录页 |
| `ACCOUNT_DISABLED` | 账号已被禁用 | 清除登录态，跳转登录页，显示禁用提示 |

前端统一动作：清除本地登录态，跳转到 `/login` 并携带 redirect 参数。`ACCOUNT_DISABLED` 时登录页额外显示禁用原因。

### 4.5 403 无权限

HTTP 状态：`403 Forbidden`

响应体：

```json
{
  "code": 403,
  "msg": "无权访问该资源",
  "reason": "ACCESS_DENIED",
  "data": null
}
```

`reason` 枚举：

| reason | 说明 | 前端动作 |
|---|---|---|
| `ACCESS_DENIED` | 角色权限不足 | 显示权限不足提示，不跳转登录页 |
| `RESOURCE_NOT_FOUND` | 资源不存在或无权限（不暴露存在性） | 显示资源不存在或无权限 |

页面级路由守卫跳转到 `/dashboard`；接口级请求显示错误提示。

### 4.6 禁用用户 Token 失效策略

- **策略**：立即失效
- **实现方式**：后端每次鉴权时查询当前用户状态（`status` 字段），禁用用户返回 401（`reason: ACCOUNT_DISABLED`）
- **本周方案**：直接查库，不引入 Redis 黑名单
- **角色变更**：同样在每次鉴权时查询当前有效角色列表，角色变更即时生效
- **后续优化**：如增加缓存，必须同时实现用户禁用和角色变更时的主动失效机制（缓存失效 + 版本号），不能只依赖自然过期

## 5. 登录响应

登录接口保持 `POST /usnhub/user/login`，响应结构升级：

```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": 1,
      "username": "系统管理员",
      "memberId": "admin",
      "roles": [
        { "roleKey": "SYSTEM_ADMIN", "roleName": "系统管理员" }
      ],
      "primaryRoleKey": "SYSTEM_ADMIN",
      "primaryRoleName": "系统管理员",
      "roleKey": "SYSTEM_ADMIN",
      "role": "SYSTEM_ADMIN",
      "identity": "老师",
      "groupName": "实验室管理组",
      "facultyName": "计算机学院",
      "majorName": "物联网工程"
    },
    "attendance": {
      "todayStatus": 1,
      "checkInTime": "08:30",
      "checkOutTime": null,
      "weekHours": 12.5,
      "semesterHours": 48.3,
      "checkInDate": "2026-09-14",
      "todayRecords": [
        { "inTime": "08:30", "outTime": null, "durationMins": null }
      ]
    }
  }
}
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| `user.roles` | array | 用户拥有的全部全局角色列表（正式字段） |
| `user.primaryRoleKey` | string | 展示用主角色 key（按优先级取最高） |
| `user.primaryRoleName` | string | 展示用主角色名称 |
| `user.roleKey` | string | 兼容字段，等于 primaryRoleKey（第 4 周评估移除） |
| `user.role` | string | 兼容旧前端的字段，等于 primaryRoleKey（第 4 周评估移除） |

## 6. 权限矩阵

### 6.1 页面/模块可见性

| 模块/页面 | SYSTEM_ADMIN | TEACHER | STOCK_KEEPER | MEMBER | GUEST |
|---|---|---|---|---|---|
| 个人工作台首页 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| 今日考勤（本人） | 可见 | 可见 | 可见 | 可见 | 不可见 |
| 考勤管理（全部） | 可见 | 可见 | 不可见 | 不可见 | 不可见 |
| 成员管理（查看） | 可见 | 可见 | 不可见 | 不可见 | 不可见 |
| 成员管理（新增/编辑/停用） | 可见 | 不可见 | 不可见 | 不可见 | 不可见 |
| 角色管理 | 可见 | 不可见 | 不可见 | 不可见 | 不可见 |
| IoT 总览 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| IoT 项目列表 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| IoT 项目详情 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| IoT 设备列表 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| IoT 设备详情 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| 告警中心 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| 指令控制台 | 可见 | 不可见 | 不可见 | 不可见 | 不可见 |
| 操作日志 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| PM-001 实时页 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| 公开项目展示 | 可见 | 可见 | 可见 | 可见 | 可见 |
| 库存管理 | 第 9 周 | 第 9 周 | 第 9 周 | 第 10 周 | 不可见 |
| 项目工作台 | 第 3 周 | 第 3 周 | 第 3 周 | 第 3 周 | 不可见 |
| 学习实验台 | 第 5 周 | 第 5 周 | 第 5 周 | 第 5 周 | 不可见 |

硬件指令（`POST /api/iot/devices/{id}/commands`）本周仅限 SYSTEM_ADMIN 可下发。TEACHER 不能只凭全局角色获得硬件调控权限。这是历史演示兼容策略，待第 16-17 周平台化后按项目成员 + 设备归属 + 审批链重新设计。

### 6.2 数据范围

| 数据域 | SYSTEM_ADMIN | TEACHER | STOCK_KEEPER | MEMBER | GUEST |
|---|---|---|---|---|---|
| 考勤记录 | 全部成员 | 全部成员 | 不可见 | 仅本人 | 不可见 |
| 成员列表 | 全部 | 全部（只读） | 不可见 | 不可见 | 不可见 |
| 成员新增/编辑/停用 | 可操作 | 不可操作 | 不可操作 | 不可操作 | 不可操作 |
| 角色管理 | 可操作 | 不可操作 | 不可操作 | 不可操作 | 不可操作 |
| IoT 设备/项目 | 全部 | 全部 | 全部 | 全部（当前只有演示项目） | 仅公开 |
| 告警 | 全部 | 全部 | 全部 | 全部（当前只有演示设备） | 不可见 |
| 指令下发 | 可下发 | 不可下发 | 不可下发 | 不可下发 | 不可见 |
| 操作日志 | 全部 | 全部 | 全部 | 全部（当前只有演示设备） | 不可见 |

### 6.3 操作权限

| 操作 | SYSTEM_ADMIN | TEACHER | STOCK_KEEPER | MEMBER |
|---|---|---|---|---|
| 新增/编辑/停用成员 | 可 | 不可 | 不可 | 不可 |
| 角色分配与管理 | 可 | 不可 | 不可 | 不可 |
| 考勤导出 | 可 | 可 | 不可 | 不可 |
| 本人签到/签退 | 可 | 可 | 可 | 可 |
| 下发指令 | 可（历史演示兼容） | 不可 | 不可 | 不可 |
| 处理告警 | 可 | 可 | 不可 | 不可 |
| 确认建议 | 可 | 可 | 不可 | 不可 |

## 7. 个人工作台首页聚合接口

### 7.1 接口定义

```
GET /api/workbench/overview
Status: REVIEW
Used by: /dashboard
鉴权：需要登录
```

### 7.2 响应结构

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "attendance": {
      "state": "READY",
      "todayStatus": 1,
      "checkInTime": "08:30",
      "checkOutTime": null,
      "weekHours": 12.5,
      "semesterHours": 48.3,
      "checkInDate": "2026-09-14",
      "todayRecords": []
    },
    "projects": {
      "state": "NOT_AVAILABLE",
      "total": 0,
      "active": 0,
      "list": []
    },
    "tasks": {
      "state": "NOT_AVAILABLE",
      "todo": 0,
      "inProgress": 0,
      "review": 0,
      "done": 0,
      "list": []
    },
    "learning": {
      "state": "NOT_AVAILABLE",
      "inProgress": 0,
      "list": []
    },
    "notifications": {
      "state": "NOT_AVAILABLE",
      "pendingApproval": 0,
      "alerts": 0,
      "list": []
    },
    "deviceReminder": {
      "state": "READY",
      "onlineCount": 1,
      "alertCount": 0
    }
  }
}
```

### 7.3 区域状态

每个区域包含 `state` 字段，三态枚举：

| state | 说明 | available 含义 | 显示方式 |
|---|---|---|---|
| `READY` | 模块已开放且数据加载成功 | 模块已开放 | 展示真实数据 |
| `NOT_AVAILABLE` | 模块尚未开放（业务未实现） | 模块未开放 | 显示"模块尚未开放"占位卡片 |
| `ERROR` | 模块已开放但本次调用失败 | 模块已开放 | 显示错误提示 + 重试按钮 |

`available` 字段仅表示模块是否已开放（对应 NOT_AVAILABLE vs READY/ERROR），不表示调用结果。调用失败用 `ERROR` 状态 + `errorCode`/`message` 表示。

ERROR 状态附加字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `errorCode` | string | 错误码，如 `ATTENDANCE_LOAD_FAILED` |
| `message` | string | 错误描述（中文） |
| `retryable` | boolean | 是否可以重试 |

ERROR 状态示例：

```json
{
  "deviceReminder": {
    "state": "ERROR",
    "errorCode": "DEVICE_REMINDER_LOAD_FAILED",
    "message": "设备提醒数据加载失败",
    "retryable": true
  }
}
```

### 7.4 部分失败策略

- `attendance` 区域失败 → 整个首页 error 态（考勤是首页核心）
- 其他单个区域失败 → 该区域显示 ERROR 态 + 重试，不影响其他区域
- 多个区域失败 → 分别显示各自 ERROR 态
- 后端实现：try-catch 包裹各区域查询，独立失败不抛出到顶层

## 8. 首页信息层级

### 8.1 页面区域（从上到下）

1. **欢迎条**：头像、姓名、主角色标签、今日日期、快捷操作（签到/签退按钮）
2. **今日考勤卡**：今日状态、签到时间、签退时间、本周学时、本学期学时
3. **项目与任务区**（双列）：
   - 左：我参与的项目（未开放时显示占位）
   - 右：本周任务（未开放时显示占位）
4. **学习与待办区**（双列）：
   - 左：学习实验（未开放时显示占位）
   - 右：待处理事项（未开放时显示占位）
5. **设备提醒区**：在线设备数、告警数、快速入口到 IoT 总览

### 8.2 角色差异化

| 区域 | SYSTEM_ADMIN | TEACHER | STOCK_KEEPER | MEMBER |
|---|---|---|---|---|
| 欢迎条 | "系统管理员"标签 | "老师/实验室负责人"标签 | "库存管理员"标签 | "普通成员"标签 |
| 今日考勤 | 本人考勤 | 本人考勤 | 本人考勤 | 本人考勤 |
| 项目区 | 全部项目摘要 | 全部项目摘要 | 全部项目摘要（仅查看） | 我参与的项目 |
| 任务区 | 全部待办任务 | 全部待办任务 | 库存相关待办 | 我的任务 |
| 学习区 | 全部学习进度 | 全部学习进度 | — | 我的学习 |
| 待办/审批 | 全部待审批 | 全部待审批 | 领用审批 | 我的申请 |
| 设备提醒 | 全部设备摘要 | 全部设备摘要 | 全部设备摘要 | 全部设备摘要 |

第 2 周只有考勤和设备提醒是真实数据（state: READY），其他区域 state 为 NOT_AVAILABLE，显示"模块尚未开放"占位卡片。

## 9. 页面状态

### 9.1 状态枚举

| 状态 | 触发条件 | 展示 |
|---|---|---|
| `loading` | 首次加载或刷新中 | 骨架屏 / 加载占位 |
| `success` | 数据正常返回 | 完整首页内容 |
| `empty` | 数据返回但内容为空（如无项目、无任务） | 空状态插画 + 文案 + 引导操作 |
| `error` | 核心接口（attendance）请求失败 | 错误提示 + 重试按钮 |
| `permission` | 403 无权限 | 权限不足提示 + 返回首页 |
| `offline` | 网络断开 | 离线提示 + 缓存数据（如有） |

### 9.2 部分失败策略

- `attendance` 失败 → 整个首页 error 态
- 其他单个区域失败 → 该区域显示 ERROR 态 + 重试，不影响其他区域

## 10. 响应式与视口

三个固定视口：`1440x900`、`1280x800`、`390x844`

### 1440x900 / 1280x800（桌面）

- 侧栏 224px，顶栏 56px
- 主区最大宽度 1600px，居中
- 项目/任务、学习/待办为双列布局
- 设备提醒为单行卡片

### 390x844（移动）

- 侧栏变抽屉，默认隐藏
- 顶栏 56px，标题简化
- 所有双列改单列
- 卡片纵向堆叠
- 签到/签退按钮保持底部可达

## 11. 样例

### 11.1 正常态 - 普通成员（第 2 周）

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "attendance": {
      "state": "READY",
      "todayStatus": 1,
      "checkInTime": "09:15",
      "checkOutTime": null,
      "weekHours": 6.5,
      "semesterHours": 24.0,
      "checkInDate": "2026-09-14",
      "todayRecords": [
        { "inTime": "09:15", "outTime": null, "durationMins": null }
      ]
    },
    "projects": { "state": "NOT_AVAILABLE", "total": 0, "active": 0, "list": [] },
    "tasks": { "state": "NOT_AVAILABLE", "todo": 0, "inProgress": 0, "review": 0, "done": 0, "list": [] },
    "learning": { "state": "NOT_AVAILABLE", "inProgress": 0, "list": [] },
    "notifications": { "state": "NOT_AVAILABLE", "pendingApproval": 0, "alerts": 0, "list": [] },
    "deviceReminder": { "state": "READY", "onlineCount": 1, "alertCount": 0 }
  }
}
```

### 11.2 401 响应 - Token 过期

```json
{
  "code": 401,
  "msg": "登录状态已失效，请重新登录",
  "reason": "TOKEN_EXPIRED",
  "data": null
}
```

### 11.3 401 响应 - 账号禁用

```json
{
  "code": 401,
  "msg": "账号已被禁用，请联系管理员",
  "reason": "ACCOUNT_DISABLED",
  "data": null
}
```

### 11.4 403 响应

```json
{
  "code": 403,
  "msg": "无权访问该资源",
  "reason": "ACCESS_DENIED",
  "data": null
}
```

### 11.5 角色切换样例

| 角色 | 欢迎条标签 | 首页主要差异 | 指令控制台 |
|---|---|---|---|
| SYSTEM_ADMIN | 系统管理员 | 全部数据范围，角色管理入口 | 可下发 |
| TEACHER | 老师/实验室负责人 | 全部项目/人员/考勤摘要 | 不可见 |
| STOCK_KEEPER | 库存管理员 | 库存相关待办（第 9 周开放） | 不可见 |
| MEMBER | 普通成员 | 仅本人数据，项目/任务显示"我参与的" | 不可见 |

## 12. 待确认项

### 待前端确认

1. "尚未开放"（NOT_AVAILABLE）占位卡片的视觉样式（参考 UI_SPEC 空状态规范）
2. 角色标签颜色映射（SYSTEM_ADMIN/TEACHER/STOCK_KEEPER/MEMBER 各用什么 tag type）
3. 三视口下首页各区域的具体信息密度和字号
4. 骨架屏粒度（整页骨架 vs 分区域骨架）
5. 侧栏菜单根据 roles 数组过滤的实现方式（路由 meta 扩展为 roles 数组）
6. 区域 ERROR 态的重试交互设计（按钮位置、重试范围）

## 13. 版本兼容

| 版本 | 变更 | 兼容策略 |
|---|---|---|
| v1.0（第 2 周） | 五档角色、多角色 roles 数组、Authorization Header、401/403 reason、首页 state 三态、禁用立即失效 | 兼容旧 admin/student roleKey、旧 role/roleKey 字段、旧 token Header |
| 第 4 周（计划） | 评估移除旧 role/roleKey 兼容字段和旧 token Header | 提前两周在前端发出 deprecation 警告 |

## 14. 变更记录

| 日期 | 变更 | 状态 |
|---|---|---|
| 2026-09-14 | 初始草案：五档角色、权限矩阵、鉴权 Header、401/403、首页聚合 | REVIEW（第 2 周产品 PR，待后端/前端确认） |
