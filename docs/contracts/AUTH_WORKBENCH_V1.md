# 全局角色、权限与个人工作台契约 V1

> 状态：REVIEW
> 版本：v1.0
> 最后确认周：第 2 周（2026-W38）
> 影响范围：API、权限、前端字段、数据库（新增角色枚举）
> 关联周文档：`docs/weekly/2026-W38.md`

## 1. 目标与非目标

### 目标

把当前 `admin/student` 两级角色升级为五档全局角色，统一鉴权 Header 和错误响应格式，并冻结个人工作台首页的聚合接口与信息层级。不同角色登录后看到正确的首页区域和数据范围。

### 非目标

- 不实现项目成员和项目角色（第 3 周）
- 不实现项目、任务、学习域的真实数据
- 不实现库存权限业务（第 9-10 周）
- 不实现动态二维码、请假或补签
- 不平台化多设备 IoT
- 不引入 Spring Security、OAuth 或微服务

## 2. 现状矛盾与决策

| 矛盾 | 现状 | 决策 |
|---|---|---|
| 角色数量 | DB 只有 admin/student 两档 | 扩展为五档全局角色，兼容旧 roleKey |
| 配置与实际 Header 不一致 | 配置写 Authorization，拦截器读 `token` | 统一为 `Authorization: Bearer`，兼容旧 `token` Header |
| 前端同时发两个 Header | `Authorization: Bearer` + `token` | 保留双发两周，第 4 周移除 `token` |
| 拦截范围不足 | 只覆盖 `/usnhub/**` | 第 2 周扩展到 `/admin/**` 和需要鉴权的 `/api/**` |
| 角色守卫 | 前端只有 `meta.role === 'admin'` | 扩展为多角色权限矩阵 |
| 首页内容 | 只有考勤 | 聚合考勤 + 模块占位（未开放模块显示"尚未开放"） |
| 访客角色 | 未显式定义 | 定义为未登录状态，只能访问显式公开页面 |

## 3. 全局角色枚举

### 3.1 角色定义

| roleKey | 中文名称 | 权限范围 | 说明 |
|---|---|---|---|
| `SYSTEM_ADMIN` | 系统管理员 | 账号管理、系统配置、全局权限、安全设置、全部数据查看 | 最高权限角色 |
| `TEACHER` | 老师/实验室负责人 | 全部项目、全部人员、全部资源、全部考勤统计、设备告警总览 | 可以审批、查看全部成员数据 |
| `STOCK_KEEPER` | 库存管理员 | 采购入库、库存盘点、条件审批、库存修正、出入库流水 | 仅库存域管理权限 |
| `MEMBER` | 普通成员 | 本人考勤、学习实验、参与项目、申请资产、领用物料 | 默认角色，绝大多数成员属于此类 |
| `GUEST` | 访客 | 仅访问显式公开的页面和接口 | 未登录用户，不需要数据库记录 |

### 3.2 兼容迁移规则

现有 `admin` 和 `student` 两个 roleKey 需要平滑迁移：

| 旧 roleKey | 新 roleKey | 迁移策略 | 兼容期限 |
|---|---|---|---|
| `admin` | `SYSTEM_ADMIN` | 后端读取时自动映射，写入使用新值 | 至少保留到第 4 周 |
| `student` | `MEMBER` | 后端读取时自动映射，写入使用新值 | 至少保留到第 4 周 |

迁移步骤（后端执行）：

1. Flyway 新增迁移：向 `sys_role` 插入三个新角色（TEACHER、STOCK_KEEPER、MEMBER），更新 `admin` → `SYSTEM_ADMIN`、`student` → `MEMBER`
2. `sys_user_role` 关系表中的 role_id 自动对应到新的 role_id
3. JWT 签发使用新 roleKey，但解析时兼容旧值
4. 第 4 周评估是否移除兼容代码

### 3.3 角色与身份的区别

- **全局角色**（roleKey）：决定用户在整个平台中的权限边界，由管理员分配
- **身份**（identity）：本科/硕士/博士/老师，是人员档案属性，不直接决定权限
- 老师身份的用户不一定是 TEACHER 角色；TEACHER 角色也可以给实验室负责人（非老师身份）

## 4. 权限矩阵

### 4.1 页面/模块可见性

| 模块/页面 | SYSTEM_ADMIN | TEACHER | STOCK_KEEPER | MEMBER | GUEST |
|---|---|---|---|---|---|
| 个人工作台首页 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| 今日考勤（本人） | 可见 | 可见 | 可见 | 可见 | 不可见 |
| 考勤管理（全部） | 可见 | 可见 | 不可见 | 不可见 | 不可见 |
| 成员管理 | 可见 | 可见 | 不可见 | 不可见 | 不可见 |
| IoT 总览 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| IoT 项目列表 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| IoT 项目详情 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| IoT 设备列表 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| IoT 设备详情 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| 告警中心 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| 指令控制台 | 可见 | 可见 | 不可见 | 不可见 | 不可见 |
| 操作日志 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| PM-001 实时页 | 可见 | 可见 | 可见 | 可见 | 不可见 |
| 公开项目展示 | 可见 | 可见 | 可见 | 可见 | 可见 |
| 库存管理 | 第 9 周 | 第 9 周 | 第 9 周 | 第 10 周 | 不可见 |
| 项目工作台 | 第 3 周 | 第 3 周 | 第 3 周 | 第 3 周 | 不可见 |
| 学习实验台 | 第 5 周 | 第 5 周 | 第 5 周 | 第 5 周 | 不可见 |

第 2 周只实现已有页面的角色守卫。未实现的模块在首页显示"模块尚未开放"占位，不做路由入口。

### 4.2 数据范围

| 数据域 | SYSTEM_ADMIN | TEACHER | STOCK_KEEPER | MEMBER | GUEST |
|---|---|---|---|---|---|
| 考勤记录 | 全部成员 | 全部成员 | 不可见 | 仅本人 | 不可见 |
| 成员列表 | 全部 | 全部 | 不可见 | 不可见 | 不可见 |
| IoT 设备/项目 | 全部 | 全部 | 全部 | 全部（当前只有演示项目） | 仅公开 |
| 告警 | 全部 | 全部 | 全部 | 全部（当前只有演示设备） | 不可见 |
| 指令 | 可下发 | 可下发 | 不可见 | 不可见 | 不可见 |
| 操作日志 | 全部 | 全部 | 全部 | 全部（当前只有演示设备） | 不可见 |

### 4.3 操作权限

| 操作 | SYSTEM_ADMIN | TEACHER | STOCK_KEEPER | MEMBER |
|---|---|---|---|---|
| 新增/编辑/停用成员 | 可 | 可 | 不可 | 不可 |
| 考勤导出 | 可 | 可 | 不可 | 不可 |
| 本人签到/签退 | 可 | 可 | 可 | 可 |
| 下发指令 | 可 | 可 | 不可 | 不可 |
| 处理告警 | 可 | 可 | 不可 | 不可 |
| 确认建议 | 可 | 可 | 不可 | 不可 |

## 5. 鉴权与错误响应

### 5.1 Token Header

**标准 Header**：

```
Authorization: Bearer <jwt_token>
```

**兼容 Header**（第 2-3 周保留，第 4 周移除）：

```
token: <jwt_token>
```

后端读取规则：

1. 优先读取 `Authorization` Header，去掉 `Bearer ` 前缀后解析
2. 若 `Authorization` 不存在或为空，回退读取 `token` Header
3. 两者都不存在 → 401

前端发送规则（第 2 周）：

- 继续同时发送 `Authorization: Bearer ${token}` 和 `token: ${token}`
- 第 4 周产品确认后移除 `token` Header

### 5.2 401 未授权

触发条件：Token 缺失、格式错误、签名无效、已过期、用户被禁用

HTTP 状态：`401 Unauthorized`

响应体：

```json
{
  "code": 401,
  "msg": "登录状态已失效，请重新登录",
  "data": null
}
```

前端动作：清除本地登录态，跳转到 `/login` 并携带 redirect 参数

### 5.3 403 无权限

触发条件：Token 有效但角色/权限不足

HTTP 状态：`403 Forbidden`

响应体：

```json
{
  "code": 403,
  "msg": "无权访问该资源",
  "data": null
}
```

前端动作：显示权限不足提示，不跳转登录页；页面级路由守卫跳转到 `/dashboard`

### 5.4 被禁用用户

用户 `status = 0`（禁用）时：

- 已有 Token 继续有效直到过期
- 下一次登录直接失败，返回 401，msg："账号已被禁用，请联系管理员"
- 待后端确认：是否需要主动失效已有 Token（Redis 黑名单方案）

## 6. 登录响应

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
      "roleKey": "SYSTEM_ADMIN",
      "roleName": "系统管理员",
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

变化点：
- `user.role` 改为 `user.roleKey` + `user.roleName`（兼容旧字段至少两周）
- 新增 `user.id`（用户 ID）
- attendance 结构不变

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
      "todayStatus": 1,
      "checkInTime": "08:30",
      "checkOutTime": null,
      "weekHours": 12.5,
      "semesterHours": 48.3,
      "checkInDate": "2026-09-14",
      "todayRecords": []
    },
    "projects": {
      "total": 0,
      "active": 0,
      "list": [],
      "available": false
    },
    "tasks": {
      "todo": 0,
      "inProgress": 0,
      "review": 0,
      "done": 0,
      "list": [],
      "available": false
    },
    "learning": {
      "inProgress": 0,
      "list": [],
      "available": false
    },
    "notifications": {
      "pendingApproval": 0,
      "alerts": 0,
      "list": [],
      "available": false
    },
    "deviceReminder": {
      "onlineCount": 1,
      "alertCount": 0,
      "available": true
    }
  }
}
```

### 7.3 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `attendance` | object | 本人考勤信息，所有登录用户均有 |
| `projects.available` | boolean | 项目域是否已开放；第 2 周为 false |
| `projects.total` | number | 参与的项目总数；available=false 时为 0 |
| `tasks.available` | boolean | 任务域是否已开放；第 2 周为 false |
| `learning.available` | boolean | 学习域是否已开放；第 2 周为 false |
| `notifications.available` | boolean | 通知/待办是否已开放；第 2 周为 false |
| `deviceReminder.available` | boolean | 设备提醒是否可用；第 2 周为 true（已有 IoT 数据） |

### 7.4 设计原则

- 单聚合接口，不拆成多个独立接口
- 每个区域有 `available` 字段，前端据此决定显示真实数据还是"尚未开放"占位
- 部分区域失败不影响其他区域返回；失败区域返回 `available: false` 并在响应中标记
- 接口是读接口，不包含写操作

## 8. 首页信息层级

### 8.1 页面区域（从上到下）

1. **欢迎条**：头像、姓名、角色标签、今日日期、快捷操作（签到/签退按钮）
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
| 欢迎条 | 显示"系统管理员"标签 | 显示"老师"标签 | 显示"库存管理员"标签 | 显示"普通成员"标签 |
| 今日考勤 | 本人考勤 | 本人考勤 | 本人考勤 | 本人考勤 |
| 项目区 | 全部项目摘要 | 全部项目摘要 | 全部项目摘要（仅查看） | 我参与的项目 |
| 任务区 | 全部待办任务 | 全部待办任务 | 库存相关待办 | 我的任务 |
| 学习区 | 全部学习进度 | 全部学习进度 | — | 我的学习 |
| 待办/审批 | 全部待审批 | 全部待审批 | 领用审批 | 我的申请 |
| 设备提醒 | 全部设备摘要 | 全部设备摘要 | 全部设备摘要 | 全部设备摘要 |

第 2 周只有考勤和设备提醒是真实数据，其他区域显示"模块尚未开放"占位卡片。

## 9. 页面状态

### 9.1 状态枚举

| 状态 | 触发条件 | 展示 |
|---|---|---|
| `loading` | 首次加载或刷新中 | 骨架屏 / 加载占位 |
| `success` | 数据正常返回 | 完整首页内容 |
| `empty` | 数据返回但内容为空（如无项目、无任务） | 空状态插画 + 文案 + 引导操作 |
| `error` | 接口请求失败（非 401/403） | 错误提示 + 重试按钮 |
| `permission` | 403 无权限 | 权限不足提示 + 返回首页 |
| `offline` | 网络断开 | 离线提示 + 缓存数据（如有） |

### 9.2 部分失败策略

首页聚合接口支持部分区域失败：

- `attendance` 失败 → 整个首页 error 态（考勤是核心）
- 其他单个区域失败 → 该区域显示 error + 重试，不影响其他区域
- 多个区域失败 → 分别显示各自 error 态

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
    "projects": { "total": 0, "active": 0, "list": [], "available": false },
    "tasks": { "todo": 0, "inProgress": 0, "review": 0, "done": 0, "list": [], "available": false },
    "learning": { "inProgress": 0, "list": [], "available": false },
    "notifications": { "pendingApproval": 0, "alerts": 0, "list": [], "available": false },
    "deviceReminder": { "onlineCount": 1, "alertCount": 0, "available": true }
  }
}
```

### 11.2 401 响应

```json
{
  "code": 401,
  "msg": "登录状态已失效，请重新登录",
  "data": null
}
```

### 11.3 403 响应

```json
{
  "code": 403,
  "msg": "无权访问该资源",
  "data": null
}
```

### 11.4 角色切换样例

| 角色 | 欢迎条标签 | 首页主要差异 |
|---|---|---|
| SYSTEM_ADMIN | 系统管理员 | 全部数据范围，显示系统概览入口 |
| TEACHER | 老师 | 全部项目/人员/考勤摘要 |
| STOCK_KEEPER | 库存管理员 | 库存相关待办（第 9 周开放） |
| MEMBER | 普通成员 | 仅本人数据，项目/任务显示"我参与的" |

## 12. 待后端确认

1. 被禁用用户的已有 Token 是否立即失效（Redis 黑名单 vs 等待自然过期）
2. 首页聚合接口的部分失败实现方式（try-catch 各区域 vs 全有或全无）
3. `/admin/**` 路径下接口是否统一迁移到 `/api/admin/**` 命名空间
4. 旧 `token` Header 的兼容期限确认

## 13. 待前端确认

1. 首页区域卡片的"尚未开放"占位视觉样式（参考 UI_SPEC 空状态规范）
2. 角色标签颜色映射（SYSTEM_ADMIN/TEACHER/STOCK_KEEPER/MEMBER 各用什么 tag type）
3. 三视口下首页各区域的具体信息密度和字号
4. 首页骨架屏的粒度（整页骨架 vs 分区域骨架）
5. 侧栏菜单根据角色过滤的实现方式（路由 meta 扩展为 roles 数组）

## 14. 版本兼容

| 版本 | 变更 | 兼容策略 |
|---|---|---|
| v1.0（第 2 周） | 五档角色、Authorization Header、首页聚合接口、401/403 格式 | 兼容旧 admin/student roleKey 和旧 token Header |
| 第 4 周（计划） | 移除旧 roleKey 兼容代码、移除旧 token Header | 提前两周在前端发出 deprecation 警告 |

## 15. 变更记录

| 日期 | 变更 | 状态 |
|---|---|---|
| 2026-09-14 | 初始草案：五档角色、权限矩阵、鉴权 Header、401/403、首页聚合 | REVIEW |
