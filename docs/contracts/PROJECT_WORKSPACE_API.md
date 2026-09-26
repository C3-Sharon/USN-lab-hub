# 项目工作台 API 契约 V1

> 状态：FROZEN
> 版本：v1.0（第 3 周：项目档案与成员权限）
> 最后确认周：第 3 周（2026-W39）
> 影响范围：API、数据库、权限、前端字段
> 关联周文档：`docs/weekly/2026-W39.md`
> 前置契约：`docs/contracts/AUTH_WORKBENCH_V1.md`

## 1. 目标与非目标

### 目标

建立正式项目档案与项目成员权限体系，使成员可以从个人工作台进入自己参与的项目。冻结项目状态枚举、项目角色枚举、基础 CRUD 接口、成员管理接口和权限边界。

### 非目标

- 不实现里程碑和任务状态机（第 4 周）
- 不实现项目动态、归档交接和 Git 仓库同步
- 不实现图片上传和 MinIO，只保留可选媒体字段与默认占位
- 不修改现有 MQTT、IoT、考勤及鉴权契约
- 不把 `/iot/projects` 当作正式项目工作台（历史演示路径保留）
- 不引入微服务、Redis 缓存或复杂审批流
- 不实现项目退出、移除成员、角色变更和 OWNER 转让
- 不实现项目公开可见性开关（沿用现有 lab_project public_visible 但不作为本周重点）

## 2. 现状矛盾与决策

| 矛盾 | 现状 | 决策 |
|---|---|---|
| 项目表来源 | `lab_project` 服务首轮 IoT 演示 | 扩展 `lab_project` 为正式项目表，保留现有字段，新增状态/分类/封面等字段 |
| 项目成员 | 无项目成员表 | 新增 `lab_project_member` 多对多关系表，承载项目角色 |
| 项目归属 | `owner_id` 单字段 | 保留 owner_id 作为冗余字段，正式成员关系走 project_member 表，创建者自动成为 OWNER |
| 创建权限 | 未定义 | SYSTEM_ADMIN、TEACHER、MEMBER 可创建；STOCK_KEEPER、GUEST 不可创建 |
| 全局查看权限 | 未定义 | SYSTEM_ADMIN 可查看并管理全部项目；TEACHER 可只读查看全部项目；其他角色只看参与项目 |
| 项目编号 | `project_code` varchar(64) | 收紧为 3-32 位大写字母/数字/连字符，全局唯一 |
| 添加成员标识 | 未定义 | 使用 memberId（学工号） |
| 分类字段 | 无 | 使用可选的受限文本，匹配 `[a-z][a-z0-9_]{1,31}` |
| 工作台项目区 | state=NOT_AVAILABLE 占位 | 第 3 周切换为 state=READY，返回用户参与的真实项目列表 |

## 3. 项目状态枚举

| status | 中文名称 | 说明 | 可进行的操作 |
|---|---|---|---|
| `PREPARING` | 筹备中 | 项目刚创建，尚未正式开始 | 编辑信息、添加成员、删除项目 |
| `ACTIVE` | 进行中 | 项目正常推进 | 全部正常操作 |
| `PAUSED` | 已暂停 | 项目暂时停滞 | 查看、恢复为 ACTIVE |
| `COMPLETED` | 已完成 | 项目已完成 | 查看、归档 |
| `ARCHIVED` | 已归档 | 项目已归档，只读 | 仅查看；拒绝成员变更、状态变更 |

状态转换（本契约冻结允许的转换；第 3 周不提供状态变更接口）：

```
PREPARING → ACTIVE
ACTIVE ↔ PAUSED
ACTIVE → COMPLETED
COMPLETED → ARCHIVED
PAUSED → ARCHIVED（直接归档）
```

ARCHIVED 为终态，不能从 ARCHIVED 转回其他状态。

## 4. 项目角色枚举

项目角色定义在项目成员关系中，与全局角色独立。一个用户在不同项目中可以有不同的项目角色。

| projectRole | 中文名称 | 说明 |
|---|---|---|
| `OWNER` | 项目负责人 | 项目最高权限，管理成员、编辑项目、删除项目 |
| `MAINTAINER` | 项目维护者 | 后续可编辑项目信息和管理任务；第 3 周可受限添加 MEMBER/OBSERVER |
| `MEMBER` | 项目成员 | 参与项目、查看信息、完成分配的任务 |
| `OBSERVER` | 观察者 | 只读查看项目信息，不能修改任何内容 |

### 4.1 权限矩阵（项目内）

| 操作 | OWNER | MAINTAINER | MEMBER | OBSERVER | 非成员 |
|---|---|---|---|---|---|
| 查看项目详情 | 可 | 可 | 可 | 可 | 403 |
| 编辑项目基本信息 | 可 | 可（后续接口） | 不可 | 不可 | 403 |
| 更改项目状态 | 可 | 可（后续接口） | 不可 | 不可 | 403 |
| 添加成员 | 可 | 可，仅 MEMBER/OBSERVER | 不可 | 不可 | 403 |
| 移除成员 | 后续实现 | 后续实现 | 不可 | 不可 | 403 |
| 更改成员角色 | 后续实现 | 不可 | 不可 | 不可 | 403 |
| 转让 OWNER | 后续实现 | 不可 | 不可 | 不可 | 不可 |
| 退出项目 | 后续实现 | 后续实现 | 后续实现 | 后续实现 | — |
| 删除项目 | 可 | 不可 | 不可 | 不可 | 不可 |
| 创建里程碑 | 第 4 周 | 第 4 周 | 不可 | 不可 | 403 |
| 创建/分配任务 | 第 4 周 | 第 4 周 | 第 4 周 | 不可 | 403 |

第 3 周仅实现添加成员：OWNER 可添加 MAINTAINER、MEMBER、OBSERVER；MAINTAINER 只能添加 MEMBER、OBSERVER。编辑、状态变更、移除、角色变更和 OWNER 转让均不在本周接口范围。

### 4.2 全局角色与项目的关系

| 全局角色 | 可创建项目 | 可查看全部项目 | 说明 |
|---|---|---|---|
| SYSTEM_ADMIN | 可 | 可 | 可创建、查看全部项目并执行成员管理；未加入项目时 myRole 为 null |
| TEACHER | 可 | 可 | 可创建、只读查看全部项目；未加入项目时 myRole 为 null |
| STOCK_KEEPER | 不可 | 不可 | 只能查看自己参与的项目 |
| MEMBER | 可 | 不可 | 可自主发起项目，只能查看自己参与的项目 |
| GUEST | 不可 | 仅公开项目 | 访客只能访问显式公开的项目 |

全局权限是访问覆盖，不自动写入项目成员关系，也不把未加入项目的用户伪装成 OWNER。

## 5. 基础接口

### 5.1 创建项目

```
POST /api/projects
Status: FROZEN
鉴权：需要登录
```

**请求体：**

```json
{
  "code": "PROJ-001",
  "name": "智能气象站项目",
  "summary": "基于 ESP32 的校园气象监测站，采集温湿度、气压和光照数据。",
  "category": "hardware_project",
  "coverMediaId": null
}
```

**字段说明：**

| 字段 | 类型 | 必填 | 说明 | 约束 |
|---|---|---|---|---|
| `code` | string | 是 | 项目编号 | 3-32 位，大写字母、数字或连字符，全局唯一 |
| `name` | string | 是 | 项目名称 | 2-80 字符 |
| `summary` | string | 否 | 项目简介 | 最多 500 字符 |
| `category` | string | 否 | 项目分类 | 2-32 位，匹配 `[a-z][a-z0-9_]{1,31}` |
| `coverMediaId` | long | 否 | 封面媒体 ID | 第 3 周暂不实现上传，传 null 使用默认占位 |

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 10,
    "code": "PROJ-001",
    "name": "智能气象站项目",
    "summary": "基于 ESP32 的校园气象监测站，采集温湿度、气压和光照数据。",
    "category": "hardware_project",
    "status": "PREPARING",
    "coverUrl": null,
    "myRole": "OWNER",
    "memberCount": 1,
    "createTime": "2026-09-21T10:30:00",
    "updateTime": "2026-09-21T10:30:00"
  }
}
```

**业务规则：**
- 创建者在同一事务中自动成为 OWNER
- 初始状态为 `PREPARING`
- `coverMediaId` 为 null 时，前端使用确定性默认占位（由 code 生成稳定视觉）
- `code` 全局唯一，重复返回 409

### 5.2 获取项目列表

```
GET /api/projects?page=1&pageSize=10&status=ACTIVE&keyword=
Status: FROZEN
鉴权：需要登录
```

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | 1 | 页码，从 1 开始 |
| `pageSize` | int | 否 | 10 | 每页条数，最大 50 |
| `status` | string | 否 | （全部） | 按状态过滤，枚举值见 §3 |
| `keyword` | string | 否 | （空） | 按项目编号或名称模糊搜索 |
| `sortBy` | string | 否 | updateTime | 排序字段：createTime / updateTime / name / code |
| `sortOrder` | string | 否 | desc | 排序方向：asc / desc |

默认按 `updateTime DESC, id DESC` 稳定排序。

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 2,
    "page": 1,
    "pageSize": 10,
    "list": [
      {
        "id": 10,
        "code": "PROJ-001",
        "name": "智能气象站项目",
        "summary": "基于 ESP32 的校园气象监测站...",
        "category": "hardware_project",
        "status": "PREPARING",
        "coverUrl": null,
        "myRole": "OWNER",
        "memberCount": 1,
        "createTime": "2026-09-21T10:30:00",
        "updateTime": "2026-09-21T10:30:00"
      },
      {
        "id": 5,
        "code": "PM-001",
        "name": "功率监测演示项目",
        "summary": "首轮 IoT 演示项目...",
        "category": "iot_demo",
        "status": "ACTIVE",
        "coverUrl": null,
        "myRole": "MEMBER",
        "memberCount": 3,
        "createTime": "2026-08-15T14:00:00",
        "updateTime": "2026-09-20T09:15:00"
      }
    ]
  }
}
```

**业务规则：**
- 默认返回当前用户参与的所有项目
- SYSTEM_ADMIN 可查看全部项目并管理成员，TEACHER 可只读查看全部项目；未显式加入时 myRole 为 null
- 空列表为合法状态（total=0, list=[]），不返回 mock 数据
- 默认不包含 ARCHIVED 项目；显式传入 `status=ARCHIVED` 时返回归档项目

### 5.3 获取项目详情

```
GET /api/projects/{id}
Status: FROZEN
鉴权：需要登录
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 10,
    "code": "PROJ-001",
    "name": "智能气象站项目",
    "summary": "基于 ESP32 的校园气象监测站，采集温湿度、气压和光照数据。",
    "category": "hardware_project",
    "status": "PREPARING",
    "coverUrl": null,
    "myRole": "OWNER",
    "memberCount": 2,
    "createTime": "2026-09-21T10:30:00",
    "updateTime": "2026-09-21T11:00:00",
    "members": [
      {
        "userId": 1,
        "memberId": "admin",
        "name": "系统管理员",
        "projectRole": "OWNER",
        "joinedAt": "2026-09-21T10:30:00"
      },
      {
        "userId": 2,
        "memberId": "20260001",
        "name": "张同学",
        "projectRole": "MEMBER",
        "joinedAt": "2026-09-21T11:00:00"
      }
    ]
  }
}
```

**业务规则：**
- 非成员访问统一返回 404 PROJECT_NOT_FOUND，不暴露项目存在性；SYSTEM_ADMIN/TEACHER 的全局查看权限除外
- ARCHIVED 项目成员仍可查看详情，但不可编辑
- members 第 3 周不分页，按 OWNER 优先、joinedAt 升序、userId 升序稳定返回

### 5.4 添加项目成员

```
POST /api/projects/{id}/members
Status: FROZEN
鉴权：需要登录，需 OWNER、受限 MAINTAINER 或 SYSTEM_ADMIN 权限
```

**请求体：**

```json
{
  "memberId": "20260001",
  "projectRole": "MEMBER"
}
```

**字段说明：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `memberId` | string | 是 | 成员学工号 |
| `projectRole` | string | 是 | 项目角色：OWNER / MAINTAINER / MEMBER / OBSERVER |

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "成员添加成功",
  "data": {
    "userId": 2,
    "memberId": "20260001",
    "name": "张同学",
    "projectRole": "MEMBER",
    "joinedAt": "2026-09-21T11:00:00"
  }
}
```

**幂等规则：**
- 若用户已是该项目成员，且角色相同 → 返回 200，不报错，不重复添加
- 若用户已是该项目成员，但角色不同 → 返回 409（reason=ALREADY_MEMBER_DIFFERENT_ROLE），提示"该成员已在项目中，角色不同，请使用角色变更功能"
- 幂等键：`(project_id, user_id)` 唯一约束

**业务规则：**
- OWNER 可添加 MAINTAINER、MEMBER、OBSERVER；MAINTAINER 只能添加 MEMBER、OBSERVER；SYSTEM_ADMIN 可执行成员管理覆盖
- 不能添加不存在的用户（返回 404 reason=USER_NOT_FOUND）
- ARCHIVED 项目不能添加成员（返回 409 reason=PROJECT_ARCHIVED）
- 本周不提供移除、角色变更或 OWNER 转让接口，因此不会产生最后一个 OWNER 被移除的问题

## 6. 错误响应

沿用 `AUTH_WORKBENCH_V1.md` 中定义的 401/403 格式，扩展项目相关的 409 和 404 reason。

### 6.1 401 未授权

见 `AUTH_WORKBENCH_V1.md` §4.4。

### 6.2 403 无权限

HTTP 状态：`403 Forbidden`

```json
{
  "code": 403,
  "msg": "无权访问该项目",
  "reason": "PROJECT_ACCESS_DENIED",
  "data": null
}
```

项目相关 reason 扩展：

| reason | 说明 | 前端动作 |
|---|---|---|
| `PROJECT_ACCESS_DENIED` | 不是项目成员，无权访问 | 显示无权访问提示 |
| `PROJECT_OPERATION_DENIED` | 是成员但角色权限不足 | 显示无权操作提示 |

### 6.3 404 资源不存在

HTTP 状态：`404 Not Found`

```json
{
  "code": 404,
  "msg": "项目不存在",
  "reason": "PROJECT_NOT_FOUND",
  "data": null
}
```

| reason | 说明 |
|---|---|
| `PROJECT_NOT_FOUND` | 项目不存在（或无权限时不暴露存在性，也可用 403） |
| `USER_NOT_FOUND` | 添加成员时目标用户不存在 |

非成员统一返回 404，避免泄露项目存在；已是成员但操作权限不足返回 403 PROJECT_OPERATION_DENIED。

### 6.4 409 冲突

HTTP 状态：`409 Conflict`

```json
{
  "code": 409,
  "msg": "项目编号已存在",
  "reason": "PROJECT_CODE_DUPLICATE",
  "data": null
}
```

| reason | 说明 | 触发场景 |
|---|---|---|
| `PROJECT_CODE_DUPLICATE` | 项目编号已存在 | 创建项目时 code 重复 |
| `ALREADY_MEMBER` | 用户已是项目成员且角色相同 | 重复添加成员（幂等，建议返回 200 而非 409） |
| `ALREADY_MEMBER_DIFFERENT_ROLE` | 用户已是项目成员但角色不同 | 重复添加成员但角色不同 |
| `PROJECT_ARCHIVED` | 项目已归档，禁止成员变更 | 归档项目添加/移除成员 |
| `LAST_OWNER_CANNOT_REMOVE` | 不能移除最后一个 OWNER | 移除或降级最后一个 OWNER |

重复添加成员且角色相同时返回 200 和已有成员信息；角色不同时返回 409 ALREADY_MEMBER_DIFFERENT_ROLE。

## 7. 与个人工作台的集成

### 7.1 首页 projects 区域切换

第 3 周起，首页 `projects` 区域从 `state: NOT_AVAILABLE` 切换为 `state: READY`，返回当前用户参与的项目列表摘要。

Workbench overview 中 projects 区域结构：

```json
{
  "projects": {
    "state": "READY",
    "total": 2,
    "active": 1,
    "list": [
      {
        "id": 10,
        "code": "PROJ-001",
        "name": "智能气象站项目",
        "status": "PREPARING",
        "myRole": "OWNER",
        "coverUrl": null,
        "updateTime": "2026-09-21T10:30:00"
      },
      {
        "id": 5,
        "code": "PM-001",
        "name": "功率监测演示项目",
        "status": "ACTIVE",
        "myRole": "MEMBER",
        "coverUrl": null,
        "updateTime": "2026-09-20T09:15:00"
      }
    ]
  }
}
```

### 7.2 空状态

用户没有参与任何项目时，projects 区域返回 state=READY 但 list 为空，前端显示"暂无项目"空状态 + 创建项目入口（有权限时）。

### 7.3 与旧 IoT 项目页的关系

- 现有 `/iot/projects` 和 `/iot/projects/:id` 保留为历史演示路径
- 第 3 周新增 `/projects` 和 `/projects/:id` 作为正式项目工作台路径
- 两条路径指向的底层数据可以复用，但正式项目页不展示 IoT 演示特定的布局
- 第 16 周平台化后统一迁移

## 8. 已确认项与前端待确认项

### 后端确认结论

后端实现决策已于 2026-09-26 确认并写入第 2-7 节。第 3 周只实现创建、列表、详情和添加成员，不把接口范围误称为完整 CRUD，也不实现项目状态流转。

兼容说明：V5 中现有 IoT 演示项目的 `project_code` 是 `power-monitor`，而 `PM-001` 是设备编号。迁移必须保留该项目编号和旧公开路径；3-32 位大写编号规则只校验第 3 周起的新建项目，不能用数据库 CHECK 约束破坏历史数据。

### 待前端确认

1. 项目列表卡片布局（封面 + 编号 + 名称 + 状态 + 角色 + 成员数）
2. 项目详情页信息层级（基本信息 + 成员列表 + 里程碑/任务占位）
3. 创建项目表单设计（字段排列、验证提示）
4. 添加成员交互（输入学工号 + 角色选择 + 搜索建议）
5. 项目状态标签颜色映射（PREPARING/ACTIVE/PAUSED/COMPLETED/ARCHIVED）
6. 项目角色标签颜色映射（OWNER/MAINTAINER/MEMBER/OBSERVER）
7. 确定性默认封面样式（基于 code 字符生成稳定视觉）
8. 工作台 projects 区域切换为真实数据后的展示形式（卡片列表还是图标网格）
9. 空状态设计（无项目时的引导文案和创建入口）

## 9. 版本兼容

| 版本 | 变更 | 兼容策略 |
|---|---|---|
| v1.0（第 3 周） | 项目档案、成员管理、项目角色、基础 CRUD | 新增表和接口，不影响现有 IoT 演示项目路径 |
| 第 4 周（计划） | 里程碑、任务状态机 | 在现有项目接口基础上扩展，不破坏第 3 周字段 |

## 10. 变更记录

| 日期 | 变更 | 状态 |
|---|---|---|
| 2026-09-21 | 初始草案：项目状态、项目角色、基础 CRUD、成员管理、错误响应 | FROZEN（第 3 周产品 PR，前后端确认后实现） |
