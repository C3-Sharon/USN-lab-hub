# 项目工作台 API 契约 V1

> 状态：FROZEN
> 版本：v1.1（第 3 周：项目档案与成员权限；第 4 周新增：里程碑、任务状态机、看板、乐观锁）
> 最后确认周：第 4 周（2026-W40）
> 影响范围：API、数据库、权限、前端字段
> 关联周文档：`docs/weekly/2026-W39.md`、`docs/weekly/2026-W40.md`
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

## 8. 里程碑

### 8.1 里程碑字段

| 字段 | 类型 | 必填 | 说明 | 约束 |
|---|---|---|---|---|
| `id` | long | 自动 | 里程碑 ID | 自增主键 |
| `projectId` | long | 是 | 所属项目 ID | 外键 |
| `name` | string | 是 | 里程碑名称 | 2-80 字符 |
| `description` | string | 否 | 里程碑描述 | 最多 500 字符 |
| `status` | string | 是 | 里程碑状态 | PLANNED / IN_PROGRESS / COMPLETED |
| `startDate` | string | 否 | 计划开始日期 | ISO 日期 `YYYY-MM-DD`，可为空 |
| `endDate` | string | 否 | 计划完成日期 | ISO 日期 `YYYY-MM-DD`，可为空 |
| `sortOrder` | int | 是 | 排序序号 | 正整数，默认 0，值越小越靠前 |
| `createTime` | datetime | 自动 | 创建时间 | ISO 8601 |
| `updateTime` | datetime | 自动 | 更新时间 | ISO 8601 |

### 8.2 里程碑状态

| status | 中文名称 | 说明 |
|---|---|---|
| `PLANNED` | 已规划 | 里程碑尚未开始 |
| `IN_PROGRESS` | 进行中 | 里程碑正在推进 |
| `COMPLETED` | 已完成 | 里程碑已完成 |

状态转换：

```
PLANNED → IN_PROGRESS → COMPLETED
```

COMPLETED 为终态，不能转回其他状态。

### 8.3 排序规则

- 里程碑列表默认按 `sortOrder ASC, id ASC` 排序
- 同 sortOrder 时按 id 升序保证稳定
- sortOrder 由前端/后端共同维护，第 4 周不提供拖拽重排接口

### 8.4 创建里程碑

```
POST /api/projects/{projectId}/milestones
Status: REVIEW
鉴权：需要登录，需 OWNER 或 MAINTAINER 权限
```

**请求体：**

```json
{
  "name": "原型设计与评审",
  "description": "完成硬件原型设计并组织评审",
  "status": "PLANNED",
  "startDate": "2026-09-26",
  "endDate": "2026-10-10",
  "sortOrder": 1
}
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "projectId": 10,
    "name": "原型设计与评审",
    "description": "完成硬件原型设计并组织评审",
    "status": "PLANNED",
    "startDate": "2026-09-26",
    "endDate": "2026-10-10",
    "sortOrder": 1,
    "createTime": "2026-09-25T10:00:00",
    "updateTime": "2026-09-25T10:00:00"
  }
}
```

**业务规则：**
- OWNER 和 MAINTAINER 可创建里程碑
- MEMBER、OBSERVER 不可创建（403 PROJECT_OPERATION_DENIED）
- 项目 ARCHIVED 时不可创建（409 PROJECT_ARCHIVED）
- 项目 PAUSED 时不可创建（409 PROJECT_PAUSED）
- 项目 COMPLETED 时不可创建（409 PROJECT_COMPLETED）
- name 同项目下建议唯一但不强制（第 4 周不做唯一约束）

### 8.5 获取里程碑列表

```
GET /api/projects/{projectId}/milestones
Status: REVIEW
鉴权：需要登录，需项目成员权限
```

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `status` | string | 否 | （全部） | 按状态过滤 |

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "id": 1,
      "projectId": 10,
      "name": "原型设计与评审",
      "description": "完成硬件原型设计并组织评审",
      "status": "PLANNED",
      "startDate": "2026-09-26",
      "endDate": "2026-10-10",
      "sortOrder": 1,
      "taskCount": 0,
      "taskDone": 0,
      "createTime": "2026-09-25T10:00:00",
      "updateTime": "2026-09-25T10:00:00"
    }
  ]
}
```

**附加统计字段：**
- `taskCount`：该里程碑下任务总数
- `taskDone`：该里程碑下状态为 DONE 的任务数

**业务规则：**
- 非成员返回 404 PROJECT_NOT_FOUND（同项目详情规则）
- SYSTEM_ADMIN/TEACHER 全局查看权限同样适用
- 默认按 sortOrder ASC, id ASC 排序

### 8.6 更新里程碑状态

```
PUT /api/projects/{projectId}/milestones/{milestoneId}/status
Status: REVIEW
鉴权：需要登录，需 OWNER 或 MAINTAINER 权限
```

**请求体：**

```json
{
  "status": "IN_PROGRESS"
}
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "status": "IN_PROGRESS",
    "updateTime": "2026-09-26T09:00:00"
  }
}
```

**业务规则：**
- 只允许 PLANNED → IN_PROGRESS → COMPLETED 正向转换
- 非法转换返回 409 reason=MILESTONE_INVALID_TRANSITION
- ARCHIVED 项目不能更新里程碑状态（409 PROJECT_ARCHIVED）
- MEMBER 及以下角色不可更新（403 PROJECT_OPERATION_DENIED）

## 9. 任务

### 9.1 任务字段

| 字段 | 类型 | 必填 | 说明 | 约束 |
|---|---|---|---|---|
| `id` | long | 自动 | 任务 ID | 自增主键 |
| `projectId` | long | 是 | 所属项目 ID | 外键 |
| `milestoneId` | long | 否 | 所属里程碑 ID | 可为空（未归类任务） |
| `title` | string | 是 | 任务标题 | 2-120 字符 |
| `description` | string | 否 | 任务说明 | 最多 2000 字符 |
| `status` | string | 是 | 任务状态 | TODO / IN_PROGRESS / BLOCKED / DONE / CANCELED |
| `assigneeUserId` | long | 否 | 负责人用户 ID | 可为空（未分配） |
| `assigneeName` | string | 否 | 负责人姓名 | 返回时冗余 |
| `priority` | string | 否 | 优先级 | LOW / MEDIUM / HIGH，默认 MEDIUM |
| `dueDate` | string | 否 | 截止日期 | ISO 日期 `YYYY-MM-DD`，可为空 |
| `blockReason` | string | 否 | 阻塞原因 | 最多 500 字符，BLOCKED 状态时必填 |
| `version` | int | 自动 | 乐观锁版本号 | 每次更新自增 1 |
| `createdBy` | long | 自动 | 创建人用户 ID | |
| `createTime` | datetime | 自动 | 创建时间 | ISO 8601 |
| `updateTime` | datetime | 自动 | 更新时间 | ISO 8601 |

### 9.2 任务状态

| status | 中文名称 | 说明 |
|---|---|---|
| `TODO` | 待开始 | 任务已创建，尚未开始 |
| `IN_PROGRESS` | 进行中 | 任务正在执行 |
| `BLOCKED` | 已阻塞 | 任务因外部因素阻塞 |
| `DONE` | 已完成 | 任务已由负责人提交完成 |
| `CANCELED` | 已取消 | 任务已取消，不再执行 |

### 9.3 状态转换规则

```
TODO → IN_PROGRESS
TODO → CANCELED
IN_PROGRESS → BLOCKED
IN_PROGRESS → DONE
IN_PROGRESS → CANCELED
BLOCKED → IN_PROGRESS
BLOCKED → CANCELED
```

**终态**：DONE、CANCELED 为终态，不能转换为其他状态。

**DONE 语义**：DONE 表示任务负责人提交完成，不需要负责人额外验收。本周采用简单闭环语义：提交即完成。后续需要验收流程时再增加 REVIEW 状态（不破坏现有状态）。

**BLOCKED 规则**：
- 进入 BLOCKED 状态时，`blockReason` 必填
- 从 BLOCKED 转出到 IN_PROGRESS 时，blockReason 不自动清空（保留历史阻塞记录）
- 前端展示时，BLOCKED 状态任务始终显示阻塞原因

**CANCELED 规则**：
- CANCELED 为终态，不允许恢复
- 取消后任务保留在看板的历史筛选中
- 只有 OWNER/MAINTAINER 可以取消任务（待确认是否允许负责人取消自己的任务）

### 9.4 创建任务

```
POST /api/projects/{projectId}/tasks
Status: REVIEW
鉴权：需要登录，需 OWNER 或 MAINTAINER 权限
```

**请求体：**

```json
{
  "title": "设计温湿度传感器电路",
  "description": "完成 DHT22 传感器的信号调理和 ADC 采集电路设计",
  "milestoneId": 1,
  "assigneeUserId": 2,
  "priority": "MEDIUM",
  "dueDate": "2026-10-05"
}
```

**字段说明：**

| 字段 | 必填 | 说明 |
|---|---|---|
| `title` | 是 | 任务标题，2-120 字符 |
| `description` | 否 | 任务说明，最多 2000 字符 |
| `milestoneId` | 否 | 所属里程碑，可为空 |
| `assigneeUserId` | 否 | 负责人，必须是项目成员；为空表示未分配 |
| `priority` | 否 | LOW/MEDIUM/HIGH，默认 MEDIUM |
| `dueDate` | 否 | 截止日期，ISO 格式 |

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "projectId": 10,
    "milestoneId": 1,
    "title": "设计温湿度传感器电路",
    "description": "完成 DHT22 传感器的信号调理和 ADC 采集电路设计",
    "status": "TODO",
    "assigneeUserId": 2,
    "assigneeName": "张同学",
    "priority": "MEDIUM",
    "dueDate": "2026-10-05",
    "blockReason": null,
    "version": 1,
    "createdBy": 1,
    "createTime": "2026-09-25T10:30:00",
    "updateTime": "2026-09-25T10:30:00"
  }
}
```

**业务规则：**
- OWNER 和 MAINTAINER 可创建任务
- MEMBER 不能创建任务（待确认：是否允许成员创建任务并自行负责？）
- OBSERVER 不能创建任务
- assigneeUserId 非空时，该用户必须是项目成员，否则返回 400 reason=ASSIGNEE_NOT_MEMBER
- milestoneId 非空时，该里程碑必须属于同一项目，否则返回 400 reason=MILESTONE_NOT_FOUND
- 初始状态为 TODO，初始 version 为 1
- 项目 ARCHIVED/PAUSED/COMPLETED 时不可创建任务

**幂等语义：**
- 创建任务不做幂等，相同标题可以创建多个任务
- 如果后续需要批量导入或防重，另加业务幂等键

### 9.5 获取任务列表（看板）

```
GET /api/projects/{projectId}/tasks?milestoneId=1&status=TODO&page=1&pageSize=20
Status: REVIEW
鉴权：需要登录，需项目成员权限
```

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `milestoneId` | long | 否 | （全部） | 按里程碑过滤，0 或不传表示全部 |
| `status` | string | 否 | （全部） | 按状态过滤 |
| `assigneeUserId` | long | 否 | （全部） | 按负责人过滤 |
| `page` | int | 否 | 1 | 页码 |
| `pageSize` | int | 否 | 20 | 每页条数，最大 100 |
| `sortBy` | string | 否 | createTime | createTime / dueDate / priority / updateTime |
| `sortOrder` | string | 否 | desc | asc / desc |

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 5,
    "page": 1,
    "pageSize": 20,
    "list": [
      {
        "id": 1,
        "projectId": 10,
        "milestoneId": 1,
        "milestoneName": "原型设计与评审",
        "title": "设计温湿度传感器电路",
        "description": "完成 DHT22 传感器的信号调理和 ADC 采集电路设计",
        "status": "TODO",
        "assigneeUserId": 2,
        "assigneeName": "张同学",
        "priority": "MEDIUM",
        "dueDate": "2026-10-05",
        "blockReason": null,
        "version": 1,
        "createdBy": 1,
        "createTime": "2026-09-25T10:30:00",
        "updateTime": "2026-09-25T10:30:00"
      }
    ]
  }
}
```

**看板视图规则：**
- 前端看板按 status 分组展示 TODO、IN_PROGRESS、BLOCKED、DONE 四列
- CANCELED 任务不显示在默认看板中，需要显式筛选"已取消"
- 每列内部按 sortBy/sortOrder 排序
- 移动端：四列改为纵向堆叠的四个分组区块

### 9.6 任务状态变更

```
PUT /api/projects/{projectId}/tasks/{taskId}/status
Status: REVIEW
鉴权：需要登录
```

**请求体：**

```json
{
  "status": "IN_PROGRESS",
  "version": 1,
  "blockReason": null
}
```

**字段说明：**

| 字段 | 必填 | 说明 |
|---|---|---|
| `status` | 是 | 目标状态 |
| `version` | 是 | 当前任务版本号，用于乐观锁 |
| `blockReason` | 条件必填 | 目标状态为 BLOCKED 时必填 |

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "status": "IN_PROGRESS",
    "version": 2,
    "updateTime": "2026-09-25T14:00:00"
  }
}
```

**乐观锁规则：**
- 请求必须携带当前 version
- 数据库中 version 与请求 version 一致时，更新成功，version 自增 1
- 不一致时返回 409 reason=VERSION_CONFLICT，前端应刷新数据后再操作
- 不能静默覆盖

**权限规则（谁可以切换哪些状态）：**

| 操作 | OWNER | MAINTAINER | MEMBER（负责人） | MEMBER（非负责人） | OBSERVER |
|---|---|---|---|---|---|
| TODO → IN_PROGRESS | 可 | 可 | 可（自己的任务） | 不可 | 不可 |
| TODO → CANCELED | 可 | 可 | 待确认 | 不可 | 不可 |
| IN_PROGRESS → BLOCKED | 可 | 可 | 可（自己的任务） | 不可 | 不可 |
| IN_PROGRESS → DONE | 可 | 可 | 可（自己的任务） | 不可 | 不可 |
| IN_PROGRESS → CANCELED | 可 | 可 | 待确认 | 不可 | 不可 |
| BLOCKED → IN_PROGRESS | 可 | 可 | 可（自己的任务） | 不可 | 不可 |
| BLOCKED → CANCELED | 可 | 可 | 不可 | 不可 | 不可 |

> **待确认**：MEMBER 是否可以取消自己的任务。倾向：不可以，取消需 OWNER/MAINTAINER 操作。

**BLOCKED 校验：**
- 目标状态为 BLOCKED 时，blockReason 必填（2-500 字符）
- blockReason 为空或不足返回 400 reason=BLOCK_REASON_REQUIRED

### 9.7 任务详情

```
GET /api/projects/{projectId}/tasks/{taskId}
Status: REVIEW
鉴权：需要登录，需项目成员权限
```

返回完整任务对象（同创建成功响应结构）。

### 9.8 编辑任务（基本信息）

```
PUT /api/projects/{projectId}/tasks/{taskId}
Status: REVIEW
鉴权：需要登录，需 OWNER 或 MAINTAINER 权限
```

可编辑字段：title、description、milestoneId、assigneeUserId、priority、dueDate。

**请求体：**

```json
{
  "title": "更新后的任务标题",
  "description": "更新后的描述",
  "milestoneId": 2,
  "assigneeUserId": 3,
  "priority": "HIGH",
  "dueDate": "2026-10-08",
  "version": 1
}
```

**业务规则：**
- 同样使用 version 乐观锁
- 只有 OWNER/MAINTAINER 可以编辑任务基本信息
- 负责人不能编辑任务基本信息，只能变更自己任务的状态
- 重新分配 assignee 时，新负责人必须是项目成员

### 9.9 项目状态对任务操作的限制

| 项目状态 | 创建任务 | 编辑任务 | 状态变更 | 查看 |
|---|---|---|---|---|
| PREPARING | 可 | 可 | 可 | 可 |
| ACTIVE | 可 | 可 | 可 | 可 |
| PAUSED | 不可 | 不可 | 不可（仅查看） | 可 |
| COMPLETED | 不可 | 不可 | 不可（仅查看） | 可 |
| ARCHIVED | 不可 | 不可 | 不可（仅查看） | 可 |

违反限制返回 409 reason=PROJECT_ARCHIVED / PROJECT_PAUSED / PROJECT_COMPLETED。

## 10. 错误响应扩展（里程碑与任务）

沿用第 6 节格式，新增以下 reason：

### 10.1 400 参数错误

HTTP 状态：`400 Bad Request`

```json
{
  "code": 400,
  "msg": "参数错误",
  "reason": "INVALID_PARAMETER",
  "data": null
}
```

| reason | 说明 | 触发场景 |
|---|---|---|
| `INVALID_PARAMETER` | 参数校验失败 | 字段缺失、格式错误、长度超限 |
| `BLOCK_REASON_REQUIRED` | 阻塞原因必填 | 切换到 BLOCKED 状态时未提供 blockReason |
| `ASSIGNEE_NOT_MEMBER` | 负责人不是项目成员 | 创建或编辑任务时 assignee 不在项目中 |
| `MILESTONE_NOT_FOUND` | 里程碑不存在或不属于该项目 | 创建任务时 milestoneId 无效 |
| `INVALID_DUE_DATE` | 截止日期非法 | dueDate 格式错误或早于今天（待确认是否限制） |

### 10.2 404 资源不存在

新增：

| reason | 说明 |
|---|---|
| `MILESTONE_NOT_FOUND` | 里程碑不存在或不属于该项目 |
| `TASK_NOT_FOUND` | 任务不存在或不属于该项目 |

### 10.3 409 冲突

新增：

| reason | 说明 | 触发场景 |
|---|---|---|
| `VERSION_CONFLICT` | 乐观锁版本冲突 | 任务更新时 version 不匹配 |
| `TASK_INVALID_TRANSITION` | 非法任务状态转换 | 不在允许的转换路径中 |
| `MILESTONE_INVALID_TRANSITION` | 非法里程碑状态转换 | 不在允许的转换路径中 |
| `PROJECT_PAUSED` | 项目已暂停，禁止修改 | 暂停项目创建/编辑任务或里程碑 |
| `PROJECT_COMPLETED` | 项目已完成，禁止修改 | 完成项目创建/编辑任务或里程碑 |
| `TASK_ALREADY_DONE` | 任务已完成，不能修改 | （不单独使用，用终态转换规则覆盖） |

**VERSION_CONFLICT 响应示例：**

```json
{
  "code": 409,
  "msg": "任务已被他人更新，请刷新后重试",
  "reason": "VERSION_CONFLICT",
  "data": {
    "currentVersion": 2,
    "taskId": 1
  }
}
```

## 11. 首页 tasks 区域

### 11.1 从 NOT_AVAILABLE 切换为 READY

第 4 周起，首页 `tasks` 区域从 `state: NOT_AVAILABLE` 切换为 `state: READY`，返回当前用户作为负责人的任务统计和最近任务列表。

Workbench overview 中 tasks 区域结构：

```json
{
  "tasks": {
    "state": "READY",
    "todo": 2,
    "inProgress": 1,
    "blocked": 0,
    "doneThisWeek": 3,
    "list": [
      {
        "id": 1,
        "projectId": 10,
        "projectCode": "PROJ-001",
        "projectName": "智能气象站项目",
        "milestoneName": "原型设计与评审",
        "title": "设计温湿度传感器电路",
        "status": "TODO",
        "priority": "MEDIUM",
        "dueDate": "2026-10-05",
        "updateTime": "2026-09-25T10:30:00"
      },
      {
        "id": 3,
        "projectId": 10,
        "projectCode": "PROJ-001",
        "projectName": "智能气象站项目",
        "milestoneName": "原型设计与评审",
        "title": "PCB 布局布线",
        "status": "IN_PROGRESS",
        "priority": "HIGH",
        "dueDate": "2026-10-01",
        "updateTime": "2026-09-26T08:00:00"
      }
    ]
  }
}
```

### 11.2 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `state` | string | READY / NOT_AVAILABLE / ERROR |
| `todo` | int | 待开始任务数（负责人为当前用户） |
| `inProgress` | int | 进行中任务数 |
| `blocked` | int | 已阻塞任务数 |
| `doneThisWeek` | int | 本周（周一至周日）完成的任务数 |
| `list` | array | 最近任务列表，默认按 updateTime 降序，最多 5 条 |

### 11.3 空状态

用户没有任何任务时：
- state=READY
- todo=0, inProgress=0, blocked=0, doneThisWeek=0
- list=[]
- 前端显示"暂无任务"空状态 + "查看全部任务"入口（跳转到项目列表）

### 11.4 SYSTEM_ADMIN / TEACHER 的首页任务

- SYSTEM_ADMIN / TEACHER 的首页 tasks 区域同样显示自己作为负责人的任务
- 不显示全项目任务统计（避免信息过载）
- 全项目任务视图在项目详情页的看板中提供

## 12. 信息层级与响应式

### 12.1 项目详情页布局

从上到下：
1. **项目头部**：封面、编号、名称、状态标签、角色标签、成员数
2. **Tab 导航**：概览 / 里程碑 / 任务看板 / 成员 / 设置（OWNER/MAINTAINER 可见）
3. **概览 Tab**：基本信息、项目简介、最近活动（第 4 周可简化为基本信息 + 里程碑进度摘要）
4. **里程碑 Tab**：里程碑列表卡片，含名称、日期范围、进度条（任务完成数/总数）
5. **任务看板 Tab**：四列看板（TODO / IN_PROGRESS / BLOCKED / DONE）
6. **成员 Tab**：成员列表（第 3 周已实现，第 4 周无变化）

### 12.2 任务看板布局

**桌面端（1440x900 / 1280x800）：**
- 四列等宽布局，列标题显示状态名 + 任务数
- 每列内任务卡片纵向堆叠
- 任务卡片：标题（加粗）、优先级标签、负责人头像+姓名、截止日期、所属里程碑
- 卡片点击打开任务详情弹窗/抽屉
- 状态变更通过卡片菜单操作，不做拖拽
- 顶部筛选：里程碑筛选、负责人筛选、搜索

**移动端（390x844）：**
- 四列改为纵向堆叠的四个分组
- 每个分组有可折叠标题（状态名 + 任务数）
- 任务卡片简化：标题、优先级、截止日期
- 状态变更通过底部操作菜单
- 筛选折叠到顶部筛选按钮

### 12.3 任务详情

弹窗或抽屉形式展示：
- 标题、状态标签、优先级标签
- 所属里程碑、负责人、截止日期
- 任务描述
- 阻塞原因（BLOCKED 状态时突出显示）
- 操作按钮：状态变更菜单、编辑（有权限时）
- 创建时间、更新时间、创建人

## 13. 权限矩阵汇总（里程碑与任务）

| 操作 | OWNER | MAINTAINER | MEMBER（负责人） | MEMBER（非负责人） | OBSERVER |
|---|---|---|---|---|---|
| 查看里程碑 | 可 | 可 | 可 | 可 | 可 |
| 创建里程碑 | 可 | 可 | 不可 | 不可 | 不可 |
| 更新里程碑状态 | 可 | 可 | 不可 | 不可 | 不可 |
| 查看任务/看板 | 可 | 可 | 可 | 可 | 可 |
| 创建任务 | 可 | 可 | 待确认 | 不可 | 不可 |
| 编辑任务信息 | 可 | 可 | 不可 | 不可 | 不可 |
| 分配/更换负责人 | 可 | 可 | 不可 | 不可 | 不可 |
| 开始任务（TODO→IN_PROGRESS） | 可 | 可 | 可（自己的任务） | 不可 | 不可 |
| 阻塞任务（→BLOCKED） | 可 | 可 | 可（自己的任务） | 不可 | 不可 |
| 完成任务（→DONE） | 可 | 可 | 可（自己的任务） | 不可 | 不可 |
| 取消任务（→CANCELED） | 可 | 可 | 不可 | 不可 | 不可 |
| 从 BLOCKED 恢复 | 可 | 可 | 可（自己的任务） | 不可 | 不可 |

SYSTEM_ADMIN 全局覆盖项目 OWNER 权限；TEACHER 全局覆盖项目只读权限（同 OBSERVER 全局可见）。

---

## 14. 第 4 周待确认项

### 待后端确认

1. **MEMBER 能否创建任务**：倾向 OWNER/MAINTAINER 创建，MEMBER 不创建。是否允许 MEMBER 创建并自行负责的任务？
2. **MEMBER 能否取消自己的任务**：倾向不可以，取消需 OWNER/MAINTAINER。请确认。
3. **dueDate 校验规则**：是否禁止早于今天？是否需要与里程碑日期范围校验？
4. **任务分页性能**：看板四列是一次拉取全量再前端分组，还是每列独立分页？建议首次全量（上限 200 条），超过后走分页。
5. **任务列表默认排序**：createTime DESC 还是 priority + dueDate？
6. **doneThisWeek 统计口径**：自然周（周一 00:00 至周日 23:59）还是滚动 7 天？
7. **BLOCKED 转出后 blockReason 是否保留**：当前约定保留历史记录。是否需要额外的 blockedHistory 字段？
8. **里程碑 sortOrder 维护方式**：前端传入还是后端自动计算？第 4 周不做拖拽，前端传值即可。
9. **CANCELED 任务在看板的默认展示**：完全隐藏还是灰显？当前约定默认隐藏，需显式筛选。
10. **任务标题同项目下是否唯一**：倾向不唯一。请确认。
11. **事务边界**：创建任务 + 分配负责人 + 乐观锁初始化是否同一事务？
12. **SYSTEM_ADMIN 对任务的操作**：是否直接拥有 OWNER 级别权限？

### 待前端确认

1. **看板四列布局**：固定宽度还是弹性？任务卡片高度是否自适应？
2. **任务卡片信息密度**：桌面端卡片显示哪些字段？移动端简化到什么程度？
3. **状态变更交互**：卡片右键菜单、卡片内下拉按钮、还是详情弹窗内操作？
4. **任务详情承载形式**：弹窗还是右侧抽屉？
5. **优先级视觉**：颜色标签还是图标？HIGH/MEDIUM/LOW 配色？
6. **里程碑进度条**：百分比数字 + 进度条样式？
7. **乐观锁冲突提示**：检测到 409 VERSION_CONFLICT 后如何提示？自动刷新还是手动刷新？
8. **看板空状态**：每列空时显示什么文案和引导？
9. **移动端纵向布局**：四列分组的折叠/展开默认状态？
10. **首页 tasks 区域卡片样式**：与 projects 区域卡片风格是否统一？
11. **截止日期临近/逾期的视觉提示**：红色/橙色标注？
12. **创建任务表单**：弹窗内表单还是独立页面？字段排列？

## 15. 版本兼容

| 版本 | 变更 | 兼容策略 |
|---|---|---|
| v1.0（第 3 周） | 项目档案、成员管理、项目角色、基础 CRUD | 新增表和接口，不影响现有 IoT 演示项目路径 |
| v1.1（第 4 周） | 里程碑、任务状态机、乐观锁、首页 tasks 区域 | 在现有项目接口基础上扩展，不破坏 v1.0 字段和接口 |

## 16. 变更记录

| 日期 | 变更 | 状态 |
|---|---|---|
| 2026-09-21 | 初始草案 v1.0：项目状态、项目角色、基础 CRUD、成员管理、错误响应 | FROZEN（第 3 周产品 PR，前后端确认后实现） |
| 2026-09-28 | 扩展 v1.1：里程碑、任务状态机、乐观锁、看板、首页 tasks 区域 | FROZEN（第 4 周产品 PR，前后端确认后实现） |
