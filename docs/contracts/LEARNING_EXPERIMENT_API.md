# 学习实验域 API 契约 V1

> 状态：FROZEN
> 版本：v1.0（第 5 周：入门学习路线与学习实验台入口）
> 最后确认周：第 5 周（2026-W41）
> 影响范围：API、数据库、权限、前端字段、首页 learning 区域
> 关联周文档：`docs/weekly/2026-W41.md`
> 前置契约：`docs/contracts/AUTH_WORKBENCH_V1.md`、`docs/contracts/PROJECT_WORKSPACE_API.md`

---

## 1. 领域边界

学习实验台与项目工作台是并列的两个域，服务不同场景：

| 维度 | 学习实验台 | 项目工作台 |
|---|---|---|
| 目的 | 零基础成员按路线学习，掌握基础技能 | 正式项目协作，完成真实任务 |
| 入口 | 首页 learning 区域 → 学习路线列表 | 首页 projects 区域 → 项目列表 |
| 角色 | 全局角色决定查看/维护权限 | 项目角色决定操作权限 |
| 创建者 | SYSTEM_ADMIN / TEACHER 创建学习路线 | OWNER 创建项目 |
| 成员关系 | 成员"开始学习"路线，无审批 | OWNER/MAINTAINER 邀请成员 |
| 数据来源 | 学习路线、阶段、学习单元 | 项目、里程碑、任务 |

术语约定：
- **学习路线（Learning Roadmap）**：一组有序的学习阶段，面向零基础成员。
- **学习阶段（Learning Stage）**：路线内的一个学习阶段，包含若干学习单元。
- **学习单元（Learning Unit）**：阶段内的最小学习节点，本周可含可选的实验模板关联字段（第 6 周实现）。
- **成员学习记录（Member Learning）**：成员与路线的多对多关系，记录学习状态和进度。

## 2. 学习路线

### 2.1 路线字段

| 字段 | 类型 | 必填 | 说明 | 约束 |
|---|---|---|---|---|
| `id` | long | 自动 | 路线 ID | 自增主键 |
| `title` | string | 是 | 路线标题 | 2-80 字符 |
| `description` | string | 否 | 路线描述 | 最多 500 字符 |
| `status` | string | 是 | 路线状态 | DRAFT / PUBLISHED / ARCHIVED |
| `difficulty` | string | 否 | 难度标签 | BEGINNER / INTERMEDIATE / ADVANCED，默认 BEGINNER |
| `estimatedHours` | int | 否 | 预计学习时长（小时） | 正整数，可为空 |
| `coverMediaId` | string | 否 | 封面媒体 ID | 本周不实现上传，使用确定性默认占位 |
| `sortOrder` | int | 是 | 排序序号 | 非负整数，默认 0，值越小越靠前 |
| `createdBy` | long | 自动 | 创建人用户 ID | |
| `createTime` | datetime | 自动 | 创建时间 | ISO 8601 |
| `updateTime` | datetime | 自动 | 更新时间 | ISO 8601 |

### 2.2 路线状态

| status | 中文名称 | 说明 |
|---|---|---|
| `DRAFT` | 草稿 | 仅创建者和管理员可见，普通成员不可见 |
| `PUBLISHED` | 已发布 | 所有登录成员可见，可开始学习 |
| `ARCHIVED` | 已归档 | 不可开始新学习，已有学习记录保留 |

状态转换：

```
DRAFT → PUBLISHED
PUBLISHED → ARCHIVED
DRAFT → ARCHIVED（直接归档废弃）
```

ARCHIVED 为终态，不能转回其他状态。

### 2.3 零基础路线示例

```
实验室安全 → 电路与焊接 → STM32/ESP32 → 传感器 → MQTT → 原理图/PCB → 联网硬件
```

该路线为系统预置示例数据，Flyway 迁移时创建，不依赖手工录入。

### 2.4 创建学习路线

```
POST /api/learning/roadmaps
Status: FROZEN
鉴权：需要登录，需 SYSTEM_ADMIN 或 TEACHER 权限
```

**请求体：**

```json
{
  "title": "嵌入式硬件入门",
  "description": "从零开始学习嵌入式硬件开发",
  "status": "DRAFT",
  "difficulty": "BEGINNER",
  "estimatedHours": 40,
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
    "title": "嵌入式硬件入门",
    "description": "从零开始学习嵌入式硬件开发",
    "status": "DRAFT",
    "difficulty": "BEGINNER",
    "estimatedHours": 40,
    "coverMediaId": null,
    "sortOrder": 1,
    "stageCount": 0,
    "createdBy": 1,
    "createTime": "2026-10-06T10:00:00",
    "updateTime": "2026-10-06T10:00:00"
  }
}
```

**业务规则：**
- SYSTEM_ADMIN 和 TEACHER 可创建学习路线
- MEMBER、STOCK_KEEPER、GUEST 不可创建（403 LEARNING_OPERATION_DENIED）
- 初始状态固定为 DRAFT；请求未传 status 时按 DRAFT 处理，传入其他状态返回 400 INVALID_PARAMETER
- title 同系统下不要求唯一，不建立业务唯一约束

**空样例（缺失 title）：**

```json
{
  "code": 400,
  "msg": "参数错误",
  "reason": "INVALID_PARAMETER",
  "data": null
}
```

**失败样例（权限不足）：**

```json
{
  "code": 403,
  "msg": "无学习路线管理权限",
  "reason": "LEARNING_OPERATION_DENIED",
  "data": null
}
```

### 2.5 获取路线列表

```
GET /api/learning/roadmaps?page=1&pageSize=20&status=PUBLISHED&difficulty=BEGINNER
Status: FROZEN
鉴权：需要登录
```

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | 1 | 页码 |
| `pageSize` | int | 否 | 20 | 每页条数，最大 100 |
| `status` | string | 否 | PUBLISHED（普通成员） | 按状态过滤 |
| `difficulty` | string | 否 | （全部） | 按难度过滤 |
| `sortBy` | string | 否 | sortOrder | sortOrder / createTime / updateTime |
| `sortOrder` | string | 否 | asc | asc / desc |

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 1,
    "page": 1,
    "pageSize": 20,
    "list": [
      {
        "id": 1,
        "title": "嵌入式硬件入门",
        "description": "从零开始学习嵌入式硬件开发",
        "status": "PUBLISHED",
        "difficulty": "BEGINNER",
        "estimatedHours": 40,
        "coverMediaId": null,
        "sortOrder": 1,
        "stageCount": 7,
        "learnerCount": 15,
        "createdBy": 1,
        "createdByName": "管理员",
        "createTime": "2026-10-06T10:00:00",
        "updateTime": "2026-10-06T12:00:00"
      }
    ]
  }
}
```

**业务规则：**
- 普通成员（MEMBER、STOCK_KEEPER）默认只看到 PUBLISHED 路线
- SYSTEM_ADMIN、TEACHER 可通过 `status=DRAFT` 或 `status=ARCHIVED` 查看未发布路线
- GUEST 不可访问（403 ACCESS_DENIED）
- 默认按 sortOrder ASC, id ASC 排序
- 附加统计字段：stageCount（阶段数）、learnerCount（已开始学习人数）

**空样例（无已发布路线）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 0,
    "page": 1,
    "pageSize": 20,
    "list": []
  }
}
```

### 2.6 获取路线详情

```
GET /api/learning/roadmaps/{roadmapId}
Status: FROZEN
鉴权：需要登录
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "title": "嵌入式硬件入门",
    "description": "从零开始学习嵌入式硬件开发",
    "status": "PUBLISHED",
    "difficulty": "BEGINNER",
    "estimatedHours": 40,
    "coverMediaId": null,
    "sortOrder": 1,
    "stages": [
      {
        "id": 1,
        "roadmapId": 1,
        "name": "实验室安全",
        "description": "实验室安全规范和基本操作",
        "sortOrder": 1,
        "unitCount": 3,
        "createTime": "2026-10-06T10:00:00",
        "updateTime": "2026-10-06T10:00:00"
      }
    ],
    "createdBy": 1,
    "createdByName": "管理员",
    "createTime": "2026-10-06T10:00:00",
    "updateTime": "2026-10-06T12:00:00"
  }
}
```

**业务规则：**
- 普通成员请求 DRAFT 路线返回 404 LEARNING_ROADMAP_NOT_FOUND（不暴露存在性）
- SYSTEM_ADMIN/TEACHER 可查看 DRAFT 路线
- 返回路线下的阶段列表（含单元统计）

**失败样例（路线不存在）：**

```json
{
  "code": 404,
  "msg": "学习路线不存在",
  "reason": "LEARNING_ROADMAP_NOT_FOUND",
  "data": null
}
```

### 2.7 更新路线状态

```
PUT /api/learning/roadmaps/{roadmapId}/status
Status: FROZEN
鉴权：需要登录，需 SYSTEM_ADMIN 或 TEACHER 权限
```

**请求体：**

```json
{
  "status": "PUBLISHED"
}
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "status": "PUBLISHED",
    "updateTime": "2026-10-06T12:00:00"
  }
}
```

**业务规则：**
- 只允许 DRAFT → PUBLISHED → ARCHIVED 和 DRAFT → ARCHIVED
- 非法转换返回 409 LEARNING_INVALID_TRANSITION
- 普通成员不可更新（403 LEARNING_OPERATION_DENIED）
- ARCHIVED 路线不能转回其他状态

### 2.8 编辑路线基本信息

```
PUT /api/learning/roadmaps/{roadmapId}
Status: FROZEN
鉴权：需要登录，需 SYSTEM_ADMIN 或 TEACHER 权限
```

可编辑字段：title、description、difficulty、estimatedHours、coverMediaId、sortOrder。

**请求体样例：**

```json
{
  "title": "嵌入式硬件入门（2026）",
  "estimatedHours": 48,
  "sortOrder": 1
}
```

**成功响应（200）：** `data` 返回更新后的完整路线摘要，字段与 2.5 列表项一致，包括 stageCount、learnerCount、createdBy、createdByName、createTime 和 updateTime。

**业务规则：**
- ARCHIVED 路线不可编辑（409 LEARNING_ARCHIVED）
- DRAFT 和 PUBLISHED 路线可编辑

## 3. 学习阶段

### 3.1 阶段字段

| 字段 | 类型 | 必填 | 说明 | 约束 |
|---|---|---|---|---|
| `id` | long | 自动 | 阶段 ID | 自增主键 |
| `roadmapId` | long | 是 | 所属路线 ID | 外键 |
| `name` | string | 是 | 阶段名称 | 2-80 字符 |
| `description` | string | 否 | 阶段描述 | 最多 500 字符 |
| `sortOrder` | int | 是 | 排序序号 | 非负整数，默认 0 |
| `createTime` | datetime | 自动 | 创建时间 | ISO 8601 |
| `updateTime` | datetime | 自动 | 更新时间 | ISO 8601 |

### 3.2 创建阶段

```
POST /api/learning/roadmaps/{roadmapId}/stages
Status: FROZEN
鉴权：需要登录，需 SYSTEM_ADMIN 或 TEACHER 权限
```

**请求体：**

```json
{
  "name": "电路与焊接",
  "description": "基础电路知识和焊接技能",
  "sortOrder": 2
}
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 2,
    "roadmapId": 1,
    "name": "电路与焊接",
    "description": "基础电路知识和焊接技能",
    "sortOrder": 2,
    "unitCount": 0,
    "createTime": "2026-10-06T10:30:00",
    "updateTime": "2026-10-06T10:30:00"
  }
}
```

**业务规则：**
- 只有 SYSTEM_ADMIN 和 TEACHER 可创建阶段
- 路线必须存在且非 ARCHIVED（409 LEARNING_ARCHIVED）
- DRAFT 和 PUBLISHED 路线均可添加阶段
- 同一路线下阶段名称不要求唯一，不建立业务唯一约束
- sortOrder 由前端传入，默认 0

### 3.3 获取阶段列表（含学习单元）

```
GET /api/learning/roadmaps/{roadmapId}/stages
Status: FROZEN
鉴权：需要登录
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "id": 1,
      "roadmapId": 1,
      "name": "实验室安全",
      "description": "实验室安全规范和基本操作",
      "sortOrder": 1,
      "units": [
        {
          "id": 1,
          "stageId": 1,
          "title": "安全规范阅读",
          "description": "阅读实验室安全规范文档",
          "sortOrder": 1,
          "templateId": null,
          "templateName": null,
          "completed": false,
          "createTime": "2026-10-06T10:00:00",
          "updateTime": "2026-10-06T10:00:00"
        }
      ],
      "unitCount": 1,
      "createTime": "2026-10-06T10:00:00",
      "updateTime": "2026-10-06T10:00:00"
    }
  ]
}
```

**用户态字段规则：**
- `units[].completed` 表示当前登录用户是否存在该单元的有效完成记录
- 当前用户未加入该路线、尚未完成该单元，或管理者仅查看路线结构时均返回 `false`
- 该字段是查询投影，不在 `lab_learning_unit` 中持久化；后端必须按当前登录用户计算
- 前端只消费该字段展示勾选状态，不得自行根据 progress 推断单个单元是否完成

### 3.4 编辑阶段基本信息

```
PUT /api/learning/stages/{stageId}
Status: FROZEN
鉴权：需要登录，需 SYSTEM_ADMIN 或 TEACHER 权限
```

**请求体样例：**

```json
{
  "name": "电路与焊接基础",
  "description": "基础电路、安全焊接与工具使用",
  "sortOrder": 2
}
```

**成功响应（200）：** `data` 返回更新后的阶段摘要，字段为 id、roadmapId、name、description、sortOrder、unitCount、createTime、updateTime。

**业务规则：**
- 可编辑字段为 name、description、sortOrder
- 阶段所属路线为 ARCHIVED 时返回 409 LEARNING_ARCHIVED
- 阶段不存在时返回 404 LEARNING_STAGE_NOT_FOUND
- 同一路线下阶段名称不要求唯一

## 4. 学习单元

### 4.1 单元字段

| 字段 | 类型 | 必填 | 说明 | 约束 |
|---|---|---|---|---|
| `id` | long | 自动 | 单元 ID | 自增主键 |
| `stageId` | long | 是 | 所属阶段 ID | 外键 |
| `title` | string | 是 | 单元标题 | 2-120 字符 |
| `description` | string | 否 | 单元描述 | 最多 2000 字符 |
| `sortOrder` | int | 是 | 排序序号 | 非负整数，默认 0 |
| `templateId` | long | 否 | 实验模板 ID | 第 6 周实现，本周为 null |
| `templateName` | string | 否 | 实验模板名称 | 冗余字段，本周为 null |
| `completed` | boolean | 自动 | 当前登录用户是否已完成该单元 | 仅查询响应字段，不持久化；未加入路线或无完成记录时为 false |
| `createTime` | datetime | 自动 | 创建时间 | ISO 8601 |
| `updateTime` | datetime | 自动 | 更新时间 | ISO 8601 |

### 4.2 创建学习单元

```
POST /api/learning/stages/{stageId}/units
Status: FROZEN
鉴权：需要登录，需 SYSTEM_ADMIN 或 TEACHER 权限
```

**请求体：**

```json
{
  "title": "安全规范阅读",
  "description": "阅读实验室安全规范文档",
  "sortOrder": 1
}
```

**成功响应（200）：** `data` 返回新建单元，字段为 id、stageId、title、description、sortOrder、templateId、templateName、completed、createTime、updateTime；新建响应的 completed 固定为 false。

**业务规则：**
- templateId 本周不接收（传入将被忽略）
- 阶段所属路线必须非 ARCHIVED
- sortOrder 由前端传入，默认 0
- 在 PUBLISHED 路线新增单元后，totalUnitCount 立即按最新结构重算；原 COMPLETED 记录若不再满足全部完成，则在同一事务内回退为 IN_PROGRESS 并清空 completedAt

### 4.3 编辑学习单元

```
PUT /api/learning/units/{unitId}
Status: FROZEN
鉴权：需要登录，需 SYSTEM_ADMIN 或 TEACHER 权限
```

可编辑字段：title、description、sortOrder。templateId 本周不可设置。

**请求体样例：**

```json
{
  "title": "安全规范阅读与测验",
  "description": "阅读规范并完成自检",
  "sortOrder": 1
}
```

**成功响应（200）：** `data` 返回更新后的完整单元字段；completed 按当前登录用户计算。

**业务规则：**
- 单元所属路线为 ARCHIVED 时返回 409 LEARNING_ARCHIVED
- 单元不存在时返回 404 LEARNING_UNIT_NOT_FOUND
- 编辑标题、描述和排序不改变任何成员的单元完成记录

## 5. 成员学习

### 5.1 学习记录字段

| 字段 | 类型 | 必填 | 说明 | 约束 |
|---|---|---|---|---|
| `id` | long | 自动 | 记录 ID | 自增主键 |
| `roadmapId` | long | 是 | 路线 ID | 外键 |
| `userId` | long | 是 | 用户 ID | 外键 |
| `status` | string | 是 | 学习状态 | NOT_STARTED / IN_PROGRESS / COMPLETED |
| `startedAt` | datetime | 否 | 开始时间 | 开始学习时写入 |
| `completedAt` | datetime | 否 | 完成时间 | 全部单元完成时写入 |
| `progress` | int | 是 | 进度百分比 | 0-100，由后端计算 |
| `completedUnitCount` | int | 是 | 已完成单元数 | 由后端统计 |
| `totalUnitCount` | int | 是 | 总单元数 | 由后端统计 |
| `createTime` | datetime | 自动 | 创建时间 | ISO 8601 |
| `updateTime` | datetime | 自动 | 更新时间 | ISO 8601 |

### 5.2 学习状态

| status | 中文名称 | 说明 |
|---|---|---|
| `NOT_STARTED` | 未开始 | 已加入路线但尚未开始任何单元 |
| `IN_PROGRESS` | 进行中 | 至少完成 1 个单元但未全部完成 |
| `COMPLETED` | 已完成 | 全部单元已完成 |

### 5.3 进度计算规则

- `progress = completedUnitCount / totalUnitCount * 100`（向下取整）
- `totalUnitCount` 为路线下所有阶段的单元总数
- `completedUnitCount` 为当前用户在该路线下已标记完成的单元数
- 空路线（totalUnitCount=0）的进度为 0，不可标记完成
- 进度由后端计算，前端不得伪造百分比

### 5.4 开始学习

```
POST /api/learning/roadmaps/{roadmapId}/enroll
Status: FROZEN
鉴权：需要登录；SYSTEM_ADMIN、TEACHER、MEMBER、STOCK_KEEPER 可调用，GUEST 不可调用
```

**请求体：** 无

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "roadmapId": 1,
    "userId": 2,
    "status": "NOT_STARTED",
    "startedAt": "2026-10-06T14:00:00",
    "completedAt": null,
    "progress": 0,
    "completedUnitCount": 0,
    "totalUnitCount": 10,
    "createTime": "2026-10-06T14:00:00",
    "updateTime": "2026-10-06T14:00:00"
  }
}
```

**幂等语义：**
- 重复开始同一路线返回 200，返回已有学习记录（幂等）
- 不返回 409，不创建重复记录
- 唯一约束：`(roadmapId, userId)` 唯一

**业务规则：**
- 只有 PUBLISHED 路线可以开始学习
- DRAFT 路线对普通成员不可见（404 LEARNING_ROADMAP_NOT_FOUND）
- ARCHIVED 路线不可开始（409 LEARNING_ARCHIVED）
- GUEST 不可开始学习（403 ACCESS_DENIED）
- 已 COMPLETED 的路线重新开始返回 200（幂等，不重置进度）

**失败样例（未发布路线）：**

```json
{
  "code": 404,
  "msg": "学习路线不存在",
  "reason": "LEARNING_ROADMAP_NOT_FOUND",
  "data": null
}
```

**失败样例（归档路线）：**

```json
{
  "code": 409,
  "msg": "学习路线已归档",
  "reason": "LEARNING_ARCHIVED",
  "data": null
}
```

### 5.5 标记单元完成

```
POST /api/learning/units/{unitId}/complete
Status: FROZEN
鉴权：需要登录
```

**请求体：** 无

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "unitId": 1,
    "completed": true,
    "roadmapProgress": {
      "roadmapId": 1,
      "status": "IN_PROGRESS",
      "progress": 10,
      "completedUnitCount": 1,
      "totalUnitCount": 10
    }
  }
}
```

**幂等语义：**
- 重复标记同一单元完成返回 200（幂等）
- 不创建重复记录
- 唯一约束：`(unitId, userId)` 唯一

**业务规则：**
- 用户必须已开始学习该单元所属路线，否则返回 400 LEARNING_NOT_ENROLLED
- 单元所属路线必须非 ARCHIVED
- 标记完成后，后端重新计算路线进度
- 最后一个单元完成时，学习状态自动转为 COMPLETED，写入 completedAt
- 空路线（totalUnitCount=0）不可标记任何单元完成

### 5.6 取消单元完成

```
DELETE /api/learning/units/{unitId}/complete
Status: FROZEN
鉴权：需要登录
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "unitId": 1,
    "completed": false,
    "roadmapProgress": {
      "roadmapId": 1,
      "status": "IN_PROGRESS",
      "progress": 0,
      "completedUnitCount": 0,
      "totalUnitCount": 10
    }
  }
}
```

**业务规则：**
- 幂等：重复取消返回 200
- 取消后重新计算路线进度
- 如果路线之前为 COMPLETED，取消一个单元后状态回退为 IN_PROGRESS，清空 completedAt

### 5.7 我的学习路线

```
GET /api/learning/my-roadmaps?page=1&pageSize=20
Status: FROZEN
鉴权：需要登录
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 1,
    "page": 1,
    "pageSize": 20,
    "list": [
      {
        "roadmapId": 1,
        "roadmapTitle": "嵌入式硬件入门",
        "roadmapDifficulty": "BEGINNER",
        "status": "IN_PROGRESS",
        "progress": 30,
        "completedUnitCount": 3,
        "totalUnitCount": 10,
        "startedAt": "2026-10-06T14:00:00",
        "completedAt": null,
        "updateTime": "2026-10-07T09:00:00"
      }
    ]
  }
}
```

**业务规则：**
- 返回当前用户已开始学习的所有路线（包括 ARCHIVED）
- 按 updateTime 降序排列
- 包含路线基本信息和学习进度

**空样例（未开始任何路线）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 0,
    "page": 1,
    "pageSize": 20,
    "list": []
  }
}
```

## 6. 权限矩阵

| 操作 | SYSTEM_ADMIN | TEACHER | MEMBER | STOCK_KEEPER | GUEST |
|---|---|---|---|---|---|
| 查看已发布路线 | 可 | 可 | 可 | 可 | 不可 |
| 查看未发布路线 | 可 | 可 | 不可 | 不可 | 不可 |
| 创建/编辑路线 | 可 | 可 | 不可 | 不可 | 不可 |
| 更新路线状态 | 可 | 可 | 不可 | 不可 | 不可 |
| 创建/编辑阶段 | 可 | 可 | 不可 | 不可 | 不可 |
| 创建/编辑单元 | 可 | 可 | 不可 | 不可 | 不可 |
| 开始学习 | 可 | 可 | 可 | 可 | 不可 |
| 标记单元完成 | 可 | 可 | 可（自己的） | 可（自己的） | 不可 |
| 取消单元完成 | 可 | 可 | 可（自己的） | 可（自己的） | 不可 |
| 查看我的学习 | 可 | 可 | 可 | 可 | 不可 |

## 7. 错误响应

### 7.1 400 参数错误

| reason | 说明 | 触发场景 |
|---|---|---|
| `INVALID_PARAMETER` | 参数校验失败 | 字段缺失、格式错误、长度超限 |
| `LEARNING_NOT_ENROLLED` | 未开始学习该路线 | 标记单元完成但未开始学习所属路线 |

### 7.2 403 权限不足

| reason | 说明 |
|---|---|
| `LEARNING_OPERATION_DENIED` | 无学习路线管理权限 |
| `ACCESS_DENIED` | GUEST 不可访问学习域 |

**失败样例（越权修改）：**

```json
{
  "code": 403,
  "msg": "无学习路线管理权限",
  "reason": "LEARNING_OPERATION_DENIED",
  "data": null
}
```

### 7.3 404 资源不存在

| reason | 说明 |
|---|---|
| `LEARNING_ROADMAP_NOT_FOUND` | 学习路线不存在或不可见 |
| `LEARNING_STAGE_NOT_FOUND` | 学习阶段不存在 |
| `LEARNING_UNIT_NOT_FOUND` | 学习单元不存在 |

### 7.4 409 冲突

| reason | 说明 | 触发场景 |
|---|---|---|
| `LEARNING_INVALID_TRANSITION` | 非法路线状态转换 | 不在允许的转换路径中 |
| `LEARNING_ARCHIVED` | 路线已归档 | 归档路线尝试修改或开始学习 |
| `LEARNING_EMPTY_ROADMAP` | 空路线无法操作 | 无单元的路线尝试标记完成 |

## 8. 首页 learning 区域

### 8.1 从 NOT_AVAILABLE 切换为 READY

第 5 周起，首页 `learning` 区域从 `state: NOT_AVAILABLE` 切换为 `state: READY`，返回当前用户的学习进度摘要和最近学习路线。

Workbench overview 中 learning 区域结构：

```json
{
  "learning": {
    "state": "READY",
    "inProgressCount": 1,
    "completedCount": 0,
    "list": [
      {
        "roadmapId": 1,
        "roadmapTitle": "嵌入式硬件入门",
        "roadmapDifficulty": "BEGINNER",
        "status": "IN_PROGRESS",
        "progress": 30,
        "completedUnitCount": 3,
        "totalUnitCount": 10,
        "updateTime": "2026-10-07T09:00:00"
      }
    ]
  }
}
```

### 8.2 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `state` | string | READY / NOT_AVAILABLE / ERROR |
| `inProgressCount` | int | 进行中路线数 |
| `completedCount` | int | 已完成路线数 |
| `list` | array | 最近学习路线，按 updateTime 降序，最多 3 条 |

### 8.3 空状态

用户没有任何学习记录时：
- state=READY
- inProgressCount=0, completedCount=0
- list=[]
- 前端显示"暂无学习路线"空状态 + "浏览学习路线"入口

### 8.4 降级规则

- learning 区域独立降级，`LEARNING_LOAD_FAILED` 不影响考勤、项目、设备和任务区域
- TEACHER 无学习记录时，learning 区域显示空状态 + "管理学习路线"入口
- STOCK_KEEPER 无学习记录时，learning 区域显示空状态

## 9. 信息层级与视觉区分

### 9.1 导航区分

- 首页 learning 区域卡片视觉风格与 projects 区域不同：使用紫色调标签（知识/学习语义色）
- 路线列表页路径：`/learning`（不与 `/projects` 混淆）
- 路线详情页路径：`/learning/{roadmapId}`
- 侧栏菜单：学习实验台独立于项目工作台

### 9.2 路线列表页

- 卡片布局：封面占位 + 标题 + 难度标签 + 预计时长 + 阶段数 + 学习人数 + 进度条（已有学习记录时）
- "开始学习"按钮（PUBLISHED 路线，未开始时）
- "继续学习"按钮（已有学习记录时）

### 9.3 路线详情页

- 路线头部：标题、描述、难度、预计时长、状态标签
- 阶段时间线：垂直列表，每阶段含阶段名 + 描述 + 单元列表
- 单元项：标题、描述、完成状态勾选框（可交互）
- 进度条：路线整体进度

### 9.4 移动端（390x844）

- 路线列表改为纵向卡片
- 路线详情的阶段时间线保持纵向，但单元项简化为标题 + 勾选
- 首页 learning 区域改为纵向卡片

### 9.5 三视口

| 视口 | 路线列表 | 路线详情 | 首页 learning |
|---|---|---|---|
| 1440x900 | 3 列卡片 | 左侧时间线 + 右侧详情 | 双列内左侧 |
| 1280x800 | 2 列卡片 | 左侧时间线 + 右侧详情 | 双列内左侧 |
| 390x844 | 纵向卡片 | 纵向时间线 | 纵向卡片 |

## 10. 第 5 周最终确认结论

### 10.1 后端实现结论

1. 路线标题不要求系统内唯一；阶段名称不要求路线内唯一。两者只做长度和非空校验。
2. progress 使用 `floor(completedUnitCount * 100 / totalUnitCount)`；totalUnitCount=0 时固定为 0。
3. 完成最后一个单元时自动把学习记录更新为 COMPLETED 并写入 completedAt。
4. COMPLETED 后取消任一单元完成状态时，学习记录回退为 IN_PROGRESS 并清空 completedAt。
5. `lab_learning_record` 对 `(roadmap_id, user_id)` 建数据库唯一约束；`lab_learning_unit_completion` 对 `(unit_id, user_id)` 建数据库唯一约束。重复请求依赖唯一约束和事务实现幂等，不采用先查后插作为唯一保障。
6. Flyway V9 新增 `lab_learning_roadmap`、`lab_learning_stage`、`lab_learning_unit`、`lab_learning_record`、`lab_learning_unit_completion` 五张表，不修改既有迁移。
7. V9 预置一条 PUBLISHED 的“嵌入式硬件入门”路线及其有序阶段、单元，固定业务数据不得依赖自增 ID 写死关联。
8. totalUnitCount 每次查询按当前路线结构计算。向 PUBLISHED 路线新增单元后，在同一事务内校准既有学习记录；不再满足全部完成的记录回退为 IN_PROGRESS 并清空 completedAt。编辑标题、描述和排序不改变完成记录。
9. TEACHER 可管理全部学习路线，不限制为自己创建的路线；操作仍记录 createdBy 和当前操作者，便于后续审计扩展。
10. `GET /api/learning/roadmaps/{id}/stages` 的每个 `units[]` 元素必须返回 boolean `completed`，按当前登录用户计算；未加入路线或无完成记录时为 false。

### 10.2 前端交互结论

1. 路线列表在 1440px 使用三列、1280px 使用两列、390px 使用单列，卡片保持稳定高度。
2. 路线详情采用垂直阶段时间线；桌面端默认展开，移动端默认折叠。
3. 单元使用勾选框即时完成或取消，不增加二次确认；请求失败时恢复原显示并呈现 reason 对应提示。
4. 进度采用线性进度条；进度值只消费后端响应。
5. 难度使用小面积语义标签；紫色仅作为学习域识别色，不形成整页单色主题。
6. 未上传封面时按路线标题首字生成确定性默认占位，本周不实现上传。
7. 空状态复用统一 RegionState；首页提供“浏览学习路线”入口。
8. 侧栏使用 Reading 图标和“学习实验台”文案，与“项目工作台”保持一级并列。
9. “继续学习”进入路线详情并定位到第一个未完成阶段；全部完成时停留在路线概览。

## 11. API 接口汇总

| 接口 | 方法 | 说明 | 鉴权 |
|---|---|---|---|
| `/api/learning/roadmaps` | POST | 创建学习路线 | SYSTEM_ADMIN/TEACHER |
| `/api/learning/roadmaps` | GET | 路线列表（分页、筛选） | 登录 |
| `/api/learning/roadmaps/{id}` | GET | 路线详情（含阶段和单元） | 登录 |
| `/api/learning/roadmaps/{id}` | PUT | 编辑路线基本信息 | SYSTEM_ADMIN/TEACHER |
| `/api/learning/roadmaps/{id}/status` | PUT | 更新路线状态 | SYSTEM_ADMIN/TEACHER |
| `/api/learning/roadmaps/{id}/stages` | POST | 创建学习阶段 | SYSTEM_ADMIN/TEACHER |
| `/api/learning/roadmaps/{id}/stages` | GET | 阶段列表（含单元） | 登录 |
| `/api/learning/stages/{id}` | PUT | 编辑阶段基本信息 | SYSTEM_ADMIN/TEACHER |
| `/api/learning/stages/{id}/units` | POST | 创建学习单元 | SYSTEM_ADMIN/TEACHER |
| `/api/learning/units/{id}` | PUT | 编辑单元基本信息 | SYSTEM_ADMIN/TEACHER |
| `/api/learning/roadmaps/{id}/enroll` | POST | 开始学习（幂等） | SYSTEM_ADMIN/TEACHER/MEMBER/STOCK_KEEPER |
| `/api/learning/units/{id}/complete` | POST | 标记单元完成（幂等） | 登录 |
| `/api/learning/units/{id}/complete` | DELETE | 取消单元完成（幂等） | 登录 |
| `/api/learning/my-roadmaps` | GET | 我的学习路线 | 登录 |

## 12. 版本兼容

| 版本 | 变更 | 兼容策略 |
|---|---|---|
| v1.0（第 5 周） | 学习路线、阶段、单元、成员学习、首页 learning 区域 | 新增表和接口，不影响现有项目、考勤和 IoT 契约 |

第 6 周计划（PLANNED，不生效）：
- 实验模板字段、JSON Schema、模板版本
- 学习单元的 templateId 字段已预留，但本周不实现

第 7 周计划（PLANNED，不生效）：
- 实验记录、采集计划、采集批次、实验快照
- 导师反馈
- 项目草案转换

## 13. 变更记录

| 日期 | 变更 | 状态 |
|---|---|---|
| 2026-10-06 | 初始草案 v1.0：学习路线、阶段、单元、成员学习、首页 learning 区域 | FROZEN（第 5 周产品 PR，前后端确认后实现） |
| 2026-10-10 | 统一接口冻结状态，固化后端/前端结论，新增 `units[].completed` 查询字段和路线结构变化后的进度校准语义 | FROZEN |
