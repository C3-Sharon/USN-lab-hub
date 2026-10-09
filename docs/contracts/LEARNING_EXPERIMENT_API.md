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
| `sortOrder` | int | 是 | 排序序号 | 正整数，默认 0，值越小越靠前 |
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
Status: REVIEW
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
- 初始状态为 DRAFT
- title 同系统下不要求唯一（待确认）

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
Status: REVIEW
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
Status: REVIEW
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
Status: REVIEW
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
Status: REVIEW
鉴权：需要登录，需 SYSTEM_ADMIN 或 TEACHER 权限
```

可编辑字段：title、description、difficulty、estimatedHours、coverMediaId、sortOrder。

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
| `sortOrder` | int | 是 | 排序序号 | 正整数，默认 0 |
| `createTime` | datetime | 自动 | 创建时间 | ISO 8601 |
| `updateTime` | datetime | 自动 | 更新时间 | ISO 8601 |

### 3.2 创建阶段

```
POST /api/learning/roadmaps/{roadmapId}/stages
Status: REVIEW
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
- sortOrder 由前端传入，默认 0

### 3.3 获取阶段列表（含学习单元）

```
GET /api/learning/roadmaps/{roadmapId}/stages
Status: REVIEW
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

## 4. 学习单元

### 4.1 单元字段

| 字段 | 类型 | 必填 | 说明 | 约束 |
|---|---|---|---|---|
| `id` | long | 自动 | 单元 ID | 自增主键 |
| `stageId` | long | 是 | 所属阶段 ID | 外键 |
| `title` | string | 是 | 单元标题 | 2-120 字符 |
| `description` | string | 否 | 单元描述 | 最多 2000 字符 |
| `sortOrder` | int | 是 | 排序序号 | 正整数，默认 0 |
| `templateId` | long | 否 | 实验模板 ID | 第 6 周实现，本周为 null |
| `templateName` | string | 否 | 实验模板名称 | 冗余字段，本周为 null |
| `createTime` | datetime | 自动 | 创建时间 | ISO 8601 |
| `updateTime` | datetime | 自动 | 更新时间 | ISO 8601 |

### 4.2 创建学习单元

```
POST /api/learning/stages/{stageId}/units
Status: REVIEW
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

**业务规则：**
- templateId 本周不接收（传入将被忽略）
- 阶段所属路线必须非 ARCHIVED
- sortOrder 由前端传入，默认 0

### 4.3 编辑学习单元

```
PUT /api/learning/units/{unitId}
Status: REVIEW
鉴权：需要登录，需 SYSTEM_ADMIN 或 TEACHER 权限
```

可编辑字段：title、description、sortOrder。templateId 本周不可设置。

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
Status: REVIEW
鉴权：需要登录，需 MEMBER 或以上权限
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
Status: REVIEW
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
Status: REVIEW
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
Status: REVIEW
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

## 10. 第 5 周待确认项

### 待后端确认

1. **路线标题唯一性**：同系统下是否要求唯一？倾向不唯一。
2. **阶段名称同路线下唯一性**：倾向不唯一。
3. **进度计算精度**：向下取整还是四舍五入？倾向向下取整。
4. **completedAt 写入时机**：最后一个单元完成时自动写入，还是需要显式操作？倾向自动。
5. **COMPLETED 后取消单元**：状态回退为 IN_PROGRESS，清空 completedAt。请确认。
6. **唯一约束**：`(roadmapId, userId)` 和 `(unitId, userId)` 的数据库唯一约束方案。
7. **Flyway V9 表结构**：lab_learning_roadmap、lab_learning_stage、lab_learning_unit、lab_learning_record、lab_learning_unit_completion 五张表。请确认。
8. **预置数据**：零基础路线示例是否在 V9 迁移中预置？倾向是。
9. **路线编辑是否影响已有学习记录**：新增阶段/单元后，已有学习记录的 totalUnitCount 如何更新？倾向每次查询时动态计算。
10. **TEACHER 的学习管理范围**：TEACHER 能管理所有路线，还是只能管理自己创建的？倾向所有路线。

### 待前端确认

1. **路线列表卡片布局**：3 列 vs 2 列，卡片高度。
2. **路线详情时间线**：垂直时间线样式，阶段间的连接线。
3. **单元完成交互**：勾选框点击即完成，还是需要二次确认？
4. **进度条样式**：线性进度条还是环形？
5. **难度标签颜色**：BEGINNER/INTERMEDIATE/ADVANCED 配色。
6. **封面占位**：基于路线标题首字生成确定性占位？
7. **空状态设计**：无学习记录时首页和列表页的引导文案。
8. **移动端时间线**：折叠方式。
9. **学习与项目入口视觉区分**：首页两个区域的色彩和图标区分方案。
10. **侧栏菜单图标**：学习实验台使用什么图标？
11. **"继续学习"按钮**：点击后跳转到路线详情的哪个位置？
12. **路线详情页的单元展开/折叠**：默认展开还是折叠？

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
| `/api/learning/roadmaps/{id}/enroll` | POST | 开始学习（幂等） | MEMBER+ |
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
