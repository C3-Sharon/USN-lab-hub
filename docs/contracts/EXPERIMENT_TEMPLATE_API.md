# 实验模板与版本 API 契约 V1

> 状态：FROZEN
> 版本：v1.0（第 6 周：实验模板、JSON Schema 与模板版本）
> 最后确认周：第 6 周（2026-W42）
> 影响范围：API、数据库、权限、前端字段、学习单元模板关联
> 关联周文档：`docs/weekly/2026-W42.md`
> 前置契约：`docs/contracts/LEARNING_EXPERIMENT_API.md`、`docs/contracts/PROJECT_WORKSPACE_API.md`

---

## 1. 领域边界

实验模板是学习实验台和项目实验的共享基础。模板定义实验需要填写的字段结构，由 JSON Schema 描述。模板通过版本管理变化，已发布版本不可变，保证旧实验记录始终可以用发布时的 Schema 解释。

### 1.1 系统模板 vs 项目模板

| 维度 | 系统模板（SYSTEM） | 项目模板（PROJECT） |
|---|---|---|
| 归属 | 全局，属于实验室 | 属于某个项目 |
| 创建者 | SYSTEM_ADMIN / TEACHER | 项目 OWNER / MAINTAINER（以及 SYSTEM_ADMIN / TEACHER） |
| 可见范围 | 所有登录成员可见已发布版本 | 仅项目成员可见已发布版本；创建者/管理员可见草稿 |
| 用途 | 通用实验（电路基础、传感器测量等） | 项目特定实验（PM-001 校准、竞赛项目测试等） |
| 复制 | 可复制为项目模板 | 不可复制为系统模板 |
| 项目关联 | 无 | 有且仅有一个 projectId |

### 1.2 模板与版本关系

```
实验模板（Template）
  └─ 多个版本（TemplateVersion）
       ├─ DRAFT（草稿，可编辑，每个模板最多一个 DRAFT）
       ├─ PUBLISHED（已发布，不可变，可被引用）
       └─ DEPRECATED（已废弃，不可新引用，已有引用保留）
```

- 模板主记录承载元数据（标题、描述、归属、分类等）
- 版本承载 JSON Schema 和变更说明
- 已发布版本的 `schemaJson` 绝对不可修改
- 新版本从 DRAFT 创建，发布后生成新的 PUBLISHED 版本
- 废弃不删除数据，只阻止新引用

### 1.3 术语约定

- **实验模板（Experiment Template）**：定义一类实验的字段结构的模板主记录
- **模板版本（Template Version）**：模板的一个具体版本，包含 JSON Schema
- **JSON Schema**：描述实验记录字段的结构化 schema（本阶段只支持受控子集）
- **发布（Publish）**：将 DRAFT 版本转为 PUBLISHED，此后不可修改
- **废弃（Deprecate）**：将 PUBLISHED 版本标记为 DEPRECATED，不再允许新引用
- **Schema 校验**：验证 JSON Schema 本身是否符合本平台支持的子集
- **数据校验**：验证一份实验数据是否符合某个版本的 Schema

## 2. 实验模板

### 2.1 模板字段

| 字段 | 类型 | 必填 | 说明 | 约束 |
|---|---|---|---|---|
| `id` | long | 自动 | 模板 ID | 自增主键 |
| `title` | string | 是 | 模板标题 | 2-120 字符 |
| `description` | string | 否 | 模板描述 | 最多 500 字符 |
| `scope` | string | 是 | 模板范围 | SYSTEM / PROJECT |
| `projectId` | long | 条件 | 项目 ID | scope=PROJECT 时必填；scope=SYSTEM 时为 null |
| `projectName` | string | 否 | 项目名称 | 冗余字段，仅 PROJECT 范围返回 |
| `category` | string | 否 | 分类标签 | 如"电路测试""传感器""编程入门"，最多 50 字符 |
| `latestVersion` | string | 否 | 最新已发布版本号 | 无已发布版本时为 null |
| `latestVersionId` | long | 否 | 最新已发布版本 ID | 无已发布版本时为 null |
| `publishedVersionCount` | int | 是 | 已发布版本数 | 默认 0 |
| `createdBy` | long | 自动 | 创建人用户 ID | |
| `createdByName` | string | 否 | 创建人姓名 | 冗余字段 |
| `createTime` | datetime | 自动 | 创建时间 | ISO 8601 |
| `updateTime` | datetime | 自动 | 更新时间 | ISO 8601 |

### 2.2 创建模板

```
POST /api/experiment/templates
Status: FROZEN
鉴权：需要登录
权限：SYSTEM_ADMIN / TEACHER 可创建系统模板；项目 OWNER / MAINTAINER 可创建项目模板
```

**请求体：**

```json
{
  "title": "电压测量实验",
  "description": "基础直流电压测量实验模板",
  "scope": "SYSTEM",
  "projectId": null,
  "category": "电路测试"
}
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "title": "电压测量实验",
    "description": "基础直流电压测量实验模板",
    "scope": "SYSTEM",
    "projectId": null,
    "projectName": null,
    "category": "电路测试",
    "latestVersion": null,
    "latestVersionId": null,
    "publishedVersionCount": 0,
    "createdBy": 1,
    "createdByName": "管理员",
    "createTime": "2026-10-08T10:00:00",
    "updateTime": "2026-10-08T10:00:00"
  }
}
```

**业务规则：**
- 创建模板后自动创建一个 DRAFT 版本（version=0.1.0），schemaJson 为空对象
- scope=PROJECT 时 projectId 必填，且项目必须存在
- scope=SYSTEM 时 projectId 必须为 null
- title 在同一 scope + projectId 下不强制唯一（允许重名，通过 ID 区分）
- 模板创建后 scope 和 projectId 不可修改

**失败样例（权限不足）：**

```json
{
  "code": 403,
  "msg": "无模板创建权限",
  "reason": "TEMPLATE_OPERATION_DENIED",
  "data": null
}
```

### 2.3 获取模板列表

```
GET /api/experiment/templates?page=1&pageSize=20&scope=SYSTEM&status=PUBLISHED
Status: FROZEN
鉴权：需要登录
```

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | 1 | 页码 |
| `pageSize` | int | 否 | 20 | 每页条数，最大 100 |
| `scope` | string | 否 | （全部） | SYSTEM / PROJECT |
| `projectId` | long | 否 | — | 按项目过滤（仅 PROJECT scope 有效） |
| `category` | string | 否 | — | 按分类过滤 |
| `status` | string | 否 | PUBLISHED | DRAFT / PUBLISHED / DEPRECATED / ALL |
| `keyword` | string | 否 | — | 标题/描述模糊搜索 |
| `sortBy` | string | 否 | updateTime | createTime / updateTime / title |
| `sortOrder` | string | 否 | desc | asc / desc |

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 2,
    "page": 1,
    "pageSize": 20,
    "list": [
      {
        "id": 1,
        "title": "电压测量实验",
        "description": "基础直流电压测量实验模板",
        "scope": "SYSTEM",
        "projectId": null,
        "projectName": null,
        "category": "电路测试",
        "latestVersion": "1.0.0",
        "latestVersionId": 2,
        "publishedVersionCount": 1,
        "createdBy": 1,
        "createdByName": "管理员",
        "createTime": "2026-10-08T10:00:00",
        "updateTime": "2026-10-08T14:00:00"
      }
    ]
  }
}
```

**业务规则：**
- 普通成员（MEMBER/STOCK_KEEPER）只能看到 PUBLISHED 版本的模板（status 参数默认 PUBLISHED，且不能查询 DRAFT）
- SYSTEM_ADMIN/TEACHER 可查看所有状态的系统模板
- 项目 OWNER/MAINTAINER 可查看所有状态的项目模板
- 普通项目成员只能看到 PUBLISHED 状态的项目模板
- GUEST 不可访问（403 ACCESS_DENIED）
- status=ALL 时返回模板的最新状态（不区分版本状态），模板是否可见取决于有无 PUBLISHED 版本

**空样例：**

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

### 2.4 获取模板详情

```
GET /api/experiment/templates/{templateId}
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
    "title": "电压测量实验",
    "description": "基础直流电压测量实验模板",
    "scope": "SYSTEM",
    "projectId": null,
    "projectName": null,
    "category": "电路测试",
    "latestVersion": "1.0.0",
    "latestVersionId": 2,
    "publishedVersionCount": 1,
    "hasDraft": true,
    "createdBy": 1,
    "createdByName": "管理员",
    "createTime": "2026-10-08T10:00:00",
    "updateTime": "2026-10-08T14:00:00"
  }
}
```

**业务规则：**
- 普通成员访问无 PUBLISHED 版本的系统模板返回 404
- 非项目成员访问项目模板返回 404
- `hasDraft` 字段仅对有管理权限的用户返回 true/false，普通成员不返回该字段

**失败样例：**

```json
{
  "code": 404,
  "msg": "模板不存在或不可见",
  "reason": "TEMPLATE_NOT_FOUND",
  "data": null
}
```

### 2.5 编辑模板基本信息

```
PUT /api/experiment/templates/{templateId}
Status: FROZEN
鉴权：需要登录，需模板管理权限
```

**请求体：**

```json
{
  "title": "直流电压测量实验",
  "description": "更新后的描述",
  "category": "电路基础"
}
```

**业务规则：**
- 可编辑字段：title、description、category
- scope 和 projectId 不可修改
- 有管理权限者可编辑（见权限矩阵）
- 编辑模板基本信息不影响任何版本

### 2.6 复制系统模板为项目模板

```
POST /api/experiment/templates/{templateId}/copy
Status: FROZEN
鉴权：需要登录，需目标项目的 OWNER/MAINTAINER 权限
```

**请求体：**

```json
{
  "targetProjectId": 1,
  "title": "PM-001 电压校准实验",
  "description": "从系统模板复制的项目版本"
}
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "复制成功",
  "data": {
    "id": 5,
    "title": "PM-001 电压校准实验",
    "description": "从系统模板复制的项目版本",
    "scope": "PROJECT",
    "projectId": 1,
    "projectName": "PM-001 智能电表",
    "category": "电路测试",
    "latestVersion": null,
    "latestVersionId": null,
    "publishedVersionCount": 0,
    "createdBy": 3,
    "createdByName": "张同学",
    "createTime": "2026-10-09T09:00:00",
    "updateTime": "2026-10-09T09:00:00"
  }
}
```

**业务规则：**
- 只能从系统模板复制到项目（SYSTEM → PROJECT）
- 不支持 PROJECT → SYSTEM 或 PROJECT → PROJECT
- 复制后创建新的项目模板，包含一个 DRAFT 版本
- DRAFT 版本的 schemaJson 从源模板的最新 PUBLISHED 版本复制（如果有）
- 如果源模板没有 PUBLISHED 版本，schemaJson 为空对象
- 复制后新版本独立于源模板，后续互不影响
- title 可自定义，默认为源模板标题 + 项目名

**失败样例（源模板非系统模板）：**

```json
{
  "code": 400,
  "msg": "只能从系统模板复制",
  "reason": "TEMPLATE_NOT_SYSTEM",
  "data": null
}
```

## 3. 模板版本

### 3.1 版本字段

| 字段 | 类型 | 必填 | 说明 | 约束 |
|---|---|---|---|---|
| `id` | long | 自动 | 版本 ID | 自增主键 |
| `templateId` | long | 是 | 所属模板 ID | 外键 |
| `version` | string | 是 | 版本号 | 语义化版本，如 "1.0.0" |
| `versionCode` | int | 否 | 正式数字版本号 | PUBLISHED/DEPRECATED 使用，如 10000 代表 1.0.0；DRAFT 为 null |
| `status` | string | 是 | 版本状态 | DRAFT / PUBLISHED / DEPRECATED |
| `schemaJson` | object | 是 | JSON Schema 对象 | 见 §4 Schema 规范 |
| `fieldCount` | int | 是 | 字段数量 | 从 schemaJson 统计 |
| `changelog` | string | 否 | 版本变更说明 | 最多 500 字符；发布后不可修改 |
| `publishedBy` | long | 否 | 发布人 | PUBLISHED 时填写 |
| `publishedByName` | string | 否 | 发布人姓名 | 冗余字段 |
| `publishedAt` | datetime | 否 | 发布时间 | PUBLISHED 时填写 |
| `deprecatedReason` | string | 否 | 废弃原因 | DEPRECATED 时填写，最多 500 字符 |
| `deprecatedBy` | long | 否 | 废弃操作人 | DEPRECATED 时填写 |
| `deprecatedAt` | datetime | 否 | 废弃时间 | DEPRECATED 时填写 |
| `createdBy` | long | 自动 | 创建人 | |
| `createTime` | datetime | 自动 | 创建时间 | |
| `updateTime` | datetime | 自动 | 更新时间 | |

### 3.2 版本号规则

- DRAFT 内部版本号格式：`0.X.0`，如 `0.1.0`、`0.2.0`；X 是草稿修订序号
- PUBLISHED 版本号格式：`1.Y.0`，如 `1.0.0`、`1.1.0`、`1.2.0`
- 首个发布版本从 `1.0.0` 开始
- 发布时将当前 DRAFT 映射为下一个正式次版本；本周不提供主版本选择
- 发布后自动创建下一个 DRAFT 版本（草稿修订序号 +1）
  - 例如：从 `0.1.0` DRAFT 发布为 `1.0.0` PUBLISHED → 自动创建 `0.2.0` DRAFT
  - 例如：从 `0.2.0` DRAFT 发布为 `1.1.0` PUBLISHED → 自动创建 `0.3.0` DRAFT
- `versionCode` 只比较正式版本；DRAFT 的 `versionCode` 为 null
- 并发发布由事务内条件状态更新保证单次成功，数据库唯一约束作为最终兜底，详见 §3.8

### 3.3 版本状态转换

```
DRAFT → PUBLISHED  （发布）
PUBLISHED → DEPRECATED  （废弃）
```

- DRAFT：可编辑 schemaJson 和 changelog
- PUBLISHED：版本内容和发布事实不可修改；只允许执行 PUBLISHED → DEPRECATED 生命周期变更
- DEPRECATED：版本内容不可修改，不可被新引用，已有引用继续有效；废弃审计写入独立字段
- 不支持 PUBLISHED → DRAFT（已发布不可回退为草稿）
- 不支持 DEPRECATED → PUBLISHED（废弃后不可恢复发布）
- 每个模板同时最多一个 DRAFT 版本

### 3.4 创建新版本（从 DRAFT 派生）

DRAFT 版本在模板创建时自动生成。每次发布后自动创建新的 DRAFT 版本。不提供手动创建版本接口——编辑 DRAFT 即可，发布时自动滚动。

### 3.5 获取版本列表

```
GET /api/experiment/templates/{templateId}/versions?page=1&pageSize=20
Status: FROZEN
鉴权：需要登录
```

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | 1 | 页码 |
| `pageSize` | int | 否 | 20 | 每页条数，最大 100 |
| `status` | string | 否 | ALL | DRAFT / PUBLISHED / DEPRECATED / ALL |

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 3,
    "page": 1,
    "pageSize": 20,
    "list": [
      {
        "id": 3,
        "templateId": 1,
        "version": "0.2.0",
        "versionCode": null,
        "status": "DRAFT",
        "fieldCount": 5,
        "changelog": "增加电流字段",
        "publishedBy": null,
        "publishedByName": null,
        "publishedAt": null,
        "createdBy": 1,
        "createTime": "2026-10-08T15:00:00",
        "updateTime": "2026-10-08T16:00:00"
      },
      {
        "id": 2,
        "templateId": 1,
        "version": "1.0.0",
        "versionCode": 10000,
        "status": "PUBLISHED",
        "fieldCount": 4,
        "changelog": "初始发布版本",
        "publishedBy": 1,
        "publishedByName": "管理员",
        "publishedAt": "2026-10-08T14:00:00",
        "createdBy": 1,
        "createTime": "2026-10-08T10:00:00",
        "updateTime": "2026-10-08T14:00:00"
      }
    ]
  }
}
```

**业务规则：**
- 管理者视图中 DRAFT 固定置顶，其余版本按 versionCode 降序排列；普通成员只看到正式版本
- 普通成员只能看到 PUBLISHED 和 DEPRECATED 版本（DRAFT 不可见）
- 有管理权限者可看到所有状态
- 列表不返回 schemaJson（通过详情接口获取）

### 3.6 获取版本详情（含 Schema）

```
GET /api/experiment/templates/{templateId}/versions/{versionId}
Status: FROZEN
鉴权：需要登录
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 2,
    "templateId": 1,
    "version": "1.0.0",
    "versionCode": 10000,
    "status": "PUBLISHED",
    "schemaJson": {
      "schemaVersion": "1.0",
      "type": "object",
      "properties": {
        "voltage": {
          "type": "number",
          "title": "电压",
          "description": "测量的直流电压值",
          "unit": "V",
          "minimum": 0,
          "maximum": 24,
          "default": 5.0,
          "displayOrder": 1,
          "widget": "input-number"
        },
        "testType": {
          "type": "string",
          "title": "测试类型",
          "enum": ["直流", "交流", "脉冲"],
          "default": "直流",
          "displayOrder": 2,
          "widget": "select"
        },
        "isPass": {
          "type": "boolean",
          "title": "是否通过",
          "default": false,
          "displayOrder": 3,
          "widget": "switch"
        },
        "remarks": {
          "type": "string",
          "title": "备注",
          "maxLength": 500,
          "displayOrder": 4,
          "widget": "textarea"
        }
      },
      "required": ["voltage", "testType"]
    },
    "fieldCount": 4,
    "changelog": "初始发布版本",
    "publishedBy": 1,
    "publishedByName": "管理员",
    "publishedAt": "2026-10-08T14:00:00",
    "createdBy": 1,
    "createTime": "2026-10-08T10:00:00",
    "updateTime": "2026-10-08T14:00:00"
  }
}
```

**业务规则：**
- DRAFT 版本仅对有管理权限者可见
- PUBLISHED / DEPRECATED 版本对有权查看模板的用户可见
- schemaJson 可能很大，但本周字段数有限（预计 < 50 字段）

### 3.7 编辑 DRAFT 版本（更新 Schema）

```
PUT /api/experiment/templates/{templateId}/versions/{versionId}
Status: FROZEN
鉴权：需要登录，需模板管理权限
```

**请求体：**

```json
{
  "schemaJson": {
    "schemaVersion": "1.0",
    "type": "object",
    "properties": {
      "voltage": {
        "type": "number",
        "title": "电压",
        "unit": "V",
        "minimum": 0,
        "maximum": 24,
        "displayOrder": 1,
        "widget": "input-number"
      },
      "current": {
        "type": "number",
        "title": "电流",
        "unit": "mA",
        "minimum": 0,
        "maximum": 1000,
        "displayOrder": 2,
        "widget": "input-number"
      }
    },
    "required": ["voltage"]
  },
  "changelog": "增加电流测量字段"
}
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 3,
    "templateId": 1,
    "version": "0.2.0",
    "versionCode": null,
    "status": "DRAFT",
    "fieldCount": 2,
    "changelog": "增加电流测量字段",
    "updateTime": "2026-10-09T10:00:00"
  }
}
```

**业务规则：**
- 只能编辑 DRAFT 版本
- PUBLISHED / DEPRECATED 版本不可编辑（返回 409 TEMPLATE_VERSION_NOT_EDITABLE）
- 编辑前后端会校验 schemaJson 是否符合支持的子集（见 §4）
- 校验失败返回 400 SCHEMA_INVALID，带错误路径和原因
- 可编辑字段：schemaJson、changelog

**失败样例（Schema 不合法）：**

```json
{
  "code": 400,
  "msg": "Schema 校验失败",
  "reason": "SCHEMA_INVALID",
  "data": {
    "errors": [
      {
        "path": "properties.voltage.type",
        "message": "不支持的类型：array。本阶段仅支持 string、number、integer、boolean"
      },
      {
        "path": "properties.voltage.$ref",
        "message": "不支持 $ref 远程引用"
      }
    ]
  }
}
```

### 3.8 发布版本

```
PUT /api/experiment/templates/{templateId}/versions/{versionId}/publish
Status: FROZEN
鉴权：需要登录，需模板管理权限
```

**请求体：**

```json
{
  "changelog": "首次发布，包含电压和测试类型字段"
}
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "发布成功",
  "data": {
    "publishedVersion": {
      "id": 2,
      "version": "1.0.0",
      "status": "PUBLISHED",
      "publishedBy": 1,
      "publishedByName": "管理员",
      "publishedAt": "2026-10-08T14:00:00"
    },
    "newDraftVersion": {
      "id": 3,
      "version": "0.2.0",
      "status": "DRAFT"
    }
  }
}
```

**业务规则：**
- 只能发布 DRAFT 版本
- 发布前必须校验 schemaJson 合法且非空（至少有 1 个字段）
- 发布时 DRAFT 版本号 `0.X.0` 转为正式版本号：
  - 首次发布：`0.1.0` → `1.0.0`
  - 小版本更新：`0.2.0` → `1.1.0`、`0.3.0` → `1.2.0`
- 本周固定为 `1.Y.0` 次版本递增，不提供 major/minor 选择；主版本升级留待后续契约
- 发布后自动创建新的 DRAFT 版本（小版本号 +1），schemaJson 从刚发布版本复制
- 发布后更新模板的 latestVersion、latestVersionId、publishedVersionCount
- 发布必须在单个数据库事务内完成：先以 `id + template_id + status=DRAFT` 条件更新当前版本；并发竞争导致影响行数为 0 时返回 409 `TEMPLATE_VERSION_CONFLICT`
- 条件更新成功后，在同一事务内写入发布事实、创建下一个 DRAFT，并更新模板汇总字段；任一步失败整体回滚
- 数据库唯一约束 `(template_id, version)` 与“每模板最多一个 DRAFT”的约束负责兜底；本周不引入分布式锁，也不在服务端静默重试发布
- changelog 在发布时可以更新，作为版本发布说明

**失败样例（Schema 为空）：**

```json
{
  "code": 400,
  "msg": "Schema 为空，无法发布",
  "reason": "SCHEMA_EMPTY",
  "data": null
}
```

### 3.9 废弃版本

```
PUT /api/experiment/templates/{templateId}/versions/{versionId}/deprecate
Status: FROZEN
鉴权：需要登录，需模板管理权限
```

**请求体：**

```json
{
  "reason": "有新版本替代"
}
```

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "已废弃",
  "data": {
    "id": 2,
    "version": "1.0.0",
    "status": "DEPRECATED",
    "deprecatedReason": "有新版本替代",
    "deprecatedBy": 1,
    "deprecatedAt": "2026-10-10T09:00:00",
    "updateTime": "2026-10-10T09:00:00"
  }
}
```

**业务规则：**
- 只能废弃 PUBLISHED 版本
- DEPRECATED 版本不可被新引用（学习单元不能新关联到 DEPRECATED 版本）
- 已有的引用（学习单元、实验记录）不受影响，仍可读取和解释
- 废弃是单向操作，不可恢复为 PUBLISHED
- 如果废弃的是最新版本，模板的 latestVersion 回退到上一个 PUBLISHED 版本
- 没有其他 PUBLISHED 版本时 latestVersion 为 null
- reason 写入独立的 `deprecated_reason`，并同时记录 `deprecated_by`、`deprecated_at`；不得改写已发布的 changelog

## 4. JSON Schema 支持规范

### 4.1 支持的 Schema 子集

本阶段**只支持扁平的单层对象结构**，不支持嵌套对象、数组和条件 Schema。

```json
{
  "schemaVersion": "1.0",
  "type": "object",
  "properties": {
    "<fieldKey>": {
      "type": "<string|number|integer|boolean>",
      "title": "<显示名称>",
      "description": "<字段说明，可选>",
      "unit": "<单位，可选>",
      "default": "<默认值，可选>",
      "displayOrder": <数字，显示顺序>,
      "widget": "<input|input-number|select|switch|textarea>"
    }
  },
  "required": ["<必填字段key>", "..."]
}
```

### 4.2 字段类型

| type | 说明 | 支持的附加约束 |
|---|---|---|
| `string` | 字符串 | `minLength`、`maxLength`、`enum`、`default` |
| `number` | 浮点数 | `minimum`、`maximum`、`enum`、`default` |
| `integer` | 整数 | `minimum`、`maximum`、`enum`、`default` |
| `boolean` | 布尔值 | `default` |

### 4.3 扩展元数据（x- 前缀改为直接字段）

以下字段为平台自定义扩展，直接放在属性对象中：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `title` | string | 是 | 字段显示名称，2-40 字符 |
| `description` | string | 否 | 字段说明，最多 200 字符 |
| `unit` | string | 否 | 单位（如 V、mA、℃），最多 20 字符 |
| `displayOrder` | int | 是 | 显示顺序，从小到大排列 |
| `widget` | string | 否 | 建议的前端控件，见下表 |

支持的 widget：

| widget | 适用类型 | 说明 |
|---|---|---|
| `input` | string | 单行文本输入 |
| `input-number` | number / integer | 数字输入框 |
| `select` | string（需配合 enum） | 下拉选择 |
| `switch` | boolean | 开关 |
| `textarea` | string | 多行文本 |

### 4.4 明确不支持

以下 JSON Schema 特性**不支持**，后端会拒绝包含这些特性的 Schema：

- 嵌套对象（`type: object` 内部属性中再包含 object）
- 数组（`type: array`）
- `$ref` 远程引用或内部引用
- `oneOf`、`anyOf`、`allOf`
- `if` / `then` / `else` 条件 Schema
- `pattern` 正则表达式
- `format` 验证器（date-time、email 等）
- `additionalProperties`
- `patternProperties`
- 脚本表达式、自定义验证函数
- 任意代码执行

### 4.5 Schema 校验错误定位

校验失败时返回错误列表，每个错误包含 `path`（JSON 路径）和 `message`（错误说明）。

```json
{
  "code": 400,
  "msg": "Schema 校验失败",
  "reason": "SCHEMA_INVALID",
  "data": {
    "errors": [
      {
        "path": "properties.data.type",
        "message": "不支持的类型：object。本阶段仅支持扁平结构"
      },
      {
        "path": "properties.voltage.pattern",
        "message": "不支持 pattern 正则验证"
      }
    ]
  }
}
```

### 4.6 字段数量限制

- 单个模板版本最多支持 50 个字段
- 字段 key 只能包含小写字母、数字和下划线，首字母必须小写，最长 50 字符
- 字段 key 在同一模板版本内唯一

### 4.7 后端校验实现约束

- `schema_json` 使用 MySQL `TEXT` 存储，由 Jackson 解析为树结构
- 后端实现面向本契约受控子集的结构校验和数据校验，不引入完整 JSON Schema 引擎
- 校验错误必须返回稳定的字段路径和消息；禁止执行脚本、解析远程引用或自动纠正未知字段
- 单版本最多 50 个字段，超限返回 400 `SCHEMA_INVALID`

### 4.8 数据校验（预览用）

```
POST /api/experiment/templates/{templateId}/versions/{versionId}/validate-data
Status: FROZEN
鉴权：需要登录
```

校验一份实验数据是否符合当前版本 Schema。用于前端预览和测试。

**请求体：**

```json
{
  "voltage": 5.5,
  "testType": "直流",
  "isPass": true,
  "remarks": "测试正常"
}
```

**成功响应（200，校验通过）：**

```json
{
  "code": 200,
  "msg": "校验通过",
  "data": {
    "valid": true,
    "errors": []
  }
}
```

**成功响应（200，校验失败）：**

```json
{
  "code": 200,
  "msg": "校验完成",
  "data": {
    "valid": false,
    "errors": [
      {
        "path": "voltage",
        "message": "电压必须在 0 到 24 之间"
      },
      {
        "path": "testType",
        "message": "testType 是必填字段"
      }
    ]
  }
}
```

**业务规则：**
- 只校验类型、必填、枚举、范围、长度
- 不做业务逻辑校验
- 返回 200 表示校验操作成功，valid 字段表示数据是否合法

## 5. 学习单元模板关联

### 5.1 第 5 周预留字段校准

第 5 周学习单元（Learning Unit）预留了 `templateId` 和 `templateName` 字段。本周明确：

- `templateId`：关联**模板主记录**的 ID
- `templateName`：模板标题的冗余显示字段
- 新增 `templateVersionId`：关联**具体版本**的 ID（PUBLISHED 版本）
- 新增 `templateVersion`：版本号字符串的响应字段，由版本记录关联查询得到，不单独持久化

### 5.2 关联规则

- 学习单元只能关联 PUBLISHED 状态的模板版本
- 不能关联 DRAFT 或 DEPRECATED 版本
- 关联时需指定 `templateVersionId`，后端校验并持久化对应 `templateId`；`templateName`、`templateVersion` 查询时关联返回
- 模板发布新版本后，**学习单元不会自动升级**
- 老师可以手动将单元升级到新版本（在学习路线编辑中操作）
- 模板版本被废弃后，已关联的单元仍可查看和使用，但会显示"该版本已废弃"提示
- 本周不提供模板删除 API；模板主记录和版本均不得物理删除，生命周期通过版本废弃表达

### 5.3 学习单元字段更新（向后兼容）

第 5 周单元字段新增以下字段（additive，不破坏旧接口）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `templateVersionId` | long | 关联的模板版本 ID，未关联时为 null |
| `templateVersion` | string | 关联的模板版本号，未关联时为 null |

第 5 周已有字段保持不变：

| 字段 | 类型 | 说明 |
|---|---|---|
| `templateId` | long | 关联的模板主记录 ID，未关联时为 null |
| `templateName` | string | 模板标题冗余字段，未关联时为 null |

**向后兼容保证：**
- 旧前端忽略新增字段，不影响显示
- 旧 API 调用不传递新字段时，新字段为 null
- 第 5 周接口的响应结构保持不变，只增加字段

### 5.4 获取模板版本摘要（用于学习单元选择）

```
GET /api/experiment/templates/{templateId}/versions/published
Status: FROZEN
鉴权：需要登录
```

返回该模板所有 PUBLISHED 版本的简要列表，供学习单元关联时选择。

**成功响应（200）：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "id": 5,
      "version": "1.1.0",
      "status": "PUBLISHED",
      "fieldCount": 6,
      "changelog": "新增电流字段",
      "publishedAt": "2026-10-10T10:00:00"
    },
    {
      "id": 2,
      "version": "1.0.0",
      "status": "PUBLISHED",
      "fieldCount": 4,
      "changelog": "初始版本",
      "publishedAt": "2026-10-08T14:00:00"
    }
  ]
}
```

## 6. 第 7 周实验记录引用规则（预留）

### 6.1 固定引用版本

第 7 周实验记录必须**固定引用具体模板版本 ID**（`templateVersionId`），而不是模板主记录 ID。

```
实验记录
  └─ templateVersionId → 指向具体 PUBLISHED 版本（不可变）
  └─ templateVersionSnapshot → Schema 的冗余快照？
```

### 6.2 不可重解释保证

- 模板更新发布新版本后，旧实验记录的结构**绝对不变**
- 实验记录创建时使用的 Schema 版本永远有效
- 旧记录始终可以用创建时的 Schema 验证和展示
- 模板版本被废弃后，已有实验记录不受影响

### 6.3 Schema 快照策略

**不保存重复快照。** 因为已发布版本的 schemaJson 不可变，实验记录只需保存 `templateVersionId`。版本记录本身就是 Schema 的永久快照。

如果未来模板版本记录被物理删除（不允许），才需要考虑冗余快照。本周明确：模板版本**禁止物理删除**，只允许废弃。

### 6.4 与采集批次的关系

- 第 7 周实验记录可以关联 0 个或 1 个采集批次
- 采集批次的数据结构由设备/采集计划决定，不由实验模板决定
- 实验模板定义的是实验**记录**的结构化字段（如结论、参数设置、人工测量值），而非采集数据本身
- 实验记录的模板字段 + 关联采集批次的引用，共同构成完整实验证据

## 7. 权限矩阵

| 操作 | SYSTEM_ADMIN | TEACHER | 项目 OWNER | 项目 MAINTAINER | MEMBER | STOCK_KEEPER | GUEST |
|---|---|---|---|---|---|---|---|
| 创建系统模板 | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |
| 编辑系统模板基本信息 | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |
| 编辑系统模板 DRAFT Schema | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |
| 发布系统模板版本 | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |
| 废弃系统模板版本 | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |
| 创建项目模板 | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ |
| 编辑项目模板基本信息 | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ |
| 编辑项目模板 DRAFT Schema | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ |
| 发布项目模板版本 | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ |
| 废弃项目模板版本 | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ |
| 复制系统模板为项目模板 | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ |
| 查看系统模板列表（已发布） | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ |
| 查看项目模板列表（已发布） | ✓ | ✓ | ✓（本项目） | ✓（本项目） | ✓（本项目） | ✓（本项目） | ✗ |
| 查看模板详情（已发布版本） | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ |
| 查看模板 DRAFT 版本 | ✓ | ✓ | ✓（本项目） | ✓（本项目） | ✗ | ✗ | ✗ |
| 查看版本列表 | ✓ | ✓ | ✓（本项目） | ✓（本项目） | ✓（仅 SYSTEM 或所在项目的 PUBLISHED/DEPRECATED） | ✓（仅 SYSTEM 或所在项目的 PUBLISHED/DEPRECATED） | ✗ |
| 预览模板（Schema 和数据校验） | ✓ | ✓ | ✓（本项目） | ✓（本项目） | ✓（SYSTEM 或所在项目） | ✓（SYSTEM 或所在项目） | ✗ |
| 学习单元关联模板 | ✓ | ✓ | — | — | — | — | — |

注：MEMBER/STOCK_KEEPER 可以只读查看系统模板，以及自己所属项目的已发布或已废弃版本；项目模板的任何详情和版本查询都必须先校验项目成员关系。SYSTEM_ADMIN/TEACHER 具有全局监督权限。学习单元关联模板的操作由 TEACHER/SYSTEM_ADMIN 在编辑学习路线时完成。

## 8. 错误响应

### 8.1 400 参数错误

| reason | 说明 | 触发场景 |
|---|---|---|
| `INVALID_PARAMETER` | 参数校验失败 | 字段缺失、格式错误、长度超限 |
| `SCHEMA_INVALID` | JSON Schema 不符合支持子集 | 包含不支持的类型、$ref、嵌套等 |
| `SCHEMA_EMPTY` | Schema 为空 | 发布时空模板 |
| `TEMPLATE_NOT_SYSTEM` | 源模板非系统模板 | 从非系统模板执行复制 |
| `TEMPLATE_HAS_NO_DRAFT` | 模板没有 DRAFT 版本 | 理论上不会发生，防御性检查 |

### 8.2 403 权限不足

| reason | 说明 |
|---|---|
| `TEMPLATE_OPERATION_DENIED` | 无模板管理权限 |
| `ACCESS_DENIED` | GUEST 不可访问 |

### 8.3 404 资源不存在

| reason | 说明 |
|---|---|
| `TEMPLATE_NOT_FOUND` | 模板不存在或不可见（权限不足时也返回 404 不暴露存在性） |
| `TEMPLATE_VERSION_NOT_FOUND` | 模板版本不存在或不可见 |

### 8.4 409 冲突

| reason | 说明 | 触发场景 |
|---|---|---|
| `TEMPLATE_VERSION_NOT_EDITABLE` | 版本不可编辑 | 尝试编辑 PUBLISHED/DEPRECATED 版本 |
| `TEMPLATE_VERSION_NOT_PUBLISHABLE` | 版本不可发布 | 尝试发布非 DRAFT 版本 |
| `TEMPLATE_VERSION_NOT_DEPRECATABLE` | 版本不可废弃 | 尝试废弃非 PUBLISHED 版本 |
| `TEMPLATE_VERSION_CONFLICT` | 版本号冲突 | 并发发布导致唯一约束冲突 |
| `TEMPLATE_DRAFT_EXISTS` | 已有 DRAFT 版本 | 理论上不会发生，防御性检查 |
| `TEMPLATE_SCOPE_IMMUTABLE` | 模板范围不可修改 | 尝试修改 scope 或 projectId |

## 9. 前端交互要求

### 9.1 模板列表页

- 路径：`/experiment/templates`（学习实验台内的子页面）
- 顶部：筛选（scope、分类、搜索框）
- 列表：卡片或表格，展示标题、分类、最新版本、版本数、创建人、更新时间
- 系统模板和项目模板通过 tab 切换
- 空状态："暂无模板" + 创建按钮（有权限时）
- 管理入口：创建模板按钮（有权限时可见）

### 9.2 模板详情页

- 顶部：标题、描述、分类、归属范围
- 版本历史：版本列表（新版本在前），显示版本号、状态、发布时间、变更说明
- 当前 DRAFT 版本高亮显示
- PUBLISHED 版本有"已发布"标签，DEPRECATED 有"已废弃"标签

### 9.3 模板编辑器（DRAFT 版本）

- 左侧：字段列表（可拖拽排序）
- 右侧：字段属性编辑面板
- 支持添加字段、删除字段、编辑字段属性
- 字段属性：字段 key、显示名称、类型、单位、默认值、约束、显示顺序、控件类型
- enum 类型支持编辑选项列表
- 底部：保存草稿、预览、发布按钮
- 实时 Schema 校验：有错误时显示错误列表，点击定位到字段

### 9.4 模板预览

- 右侧或弹窗中展示实际填写效果
- 使用默认值填充
- 支持手动输入测试数据并校验
- 校验错误实时显示在对应字段旁

### 9.5 发布确认

- 点击发布后弹出确认对话框
- 显示当前 DRAFT 版本号 → 将发布的版本号
- 要求填写变更说明
- 确认后发布，自动跳转到新版本详情

### 9.6 版本历史

- 时间线形式展示所有版本
- 每个版本：版本号、状态、发布人、发布时间、变更说明
- 点击版本查看详情（含 Schema）
- PUBLISHED 版本可查看 Schema 和预览
- DRAFT 版本可编辑
- DEPRECATED 版本显示废弃原因

### 9.7 JSON Schema 原始模式

- 提供"查看原始 Schema"按钮
- 以只读方式展示 JSON 格式的 Schema
- 不提供直接编辑原始 JSON 的功能（本周通过可视化编辑器操作）
- 便于高级用户和调试

### 9.8 三视口

| 视口 | 模板列表 | 模板详情/编辑器 | 版本历史 |
|---|---|---|---|
| 1440x900 | 3 列卡片或表格 | 左右分栏（字段列表 + 属性面板） | 时间线在左侧 |
| 1280x800 | 2 列卡片或表格 | 左右分栏 | 时间线在顶部横向 |
| 390x844 | 纵向列表 | 上下布局，字段列表折叠 | 纵向时间线 |

### 9.9 状态展示

- loading：骨架屏
- empty：空状态插图 + 引导文案
- error：错误提示 + 重试按钮
- permission：无权限时不显示管理操作，列表只显示可读内容
- deprecated：已废弃版本用灰色标签和删除线样式

## 10. API 接口汇总

| 接口 | 方法 | 说明 | 鉴权要求 |
|---|---|---|---|
| `/api/experiment/templates` | POST | 创建模板 | 管理权限 |
| `/api/experiment/templates` | GET | 模板列表（分页、筛选） | 登录 |
| `/api/experiment/templates/{id}` | GET | 模板详情 | 登录 |
| `/api/experiment/templates/{id}` | PUT | 编辑模板基本信息 | 管理权限 |
| `/api/experiment/templates/{id}/copy` | POST | 复制系统模板为项目模板 | 项目管理权限 |
| `/api/experiment/templates/{id}/versions` | GET | 版本列表 | 登录 |
| `/api/experiment/templates/{id}/versions/{vid}` | GET | 版本详情（含 Schema） | 登录 |
| `/api/experiment/templates/{id}/versions/{vid}` | PUT | 编辑 DRAFT 版本 | 管理权限 |
| `/api/experiment/templates/{id}/versions/{vid}/publish` | PUT | 发布版本 | 管理权限 |
| `/api/experiment/templates/{id}/versions/{vid}/deprecate` | PUT | 废弃版本 | 管理权限 |
| `/api/experiment/templates/{id}/versions/published` | GET | 已发布版本列表（简要） | 登录 |
| `/api/experiment/templates/{id}/versions/{vid}/validate-data` | POST | 数据校验（预览用） | 登录 |

共 12 个接口。

## 11. 数据库逻辑模型

### lab_experiment_template

| 列名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 自增主键 |
| title | VARCHAR(200) | 模板标题 |
| description | VARCHAR(500) | 描述 |
| scope | VARCHAR(20) | SYSTEM / PROJECT |
| project_id | BIGINT NULL | 项目 ID（PROJECT 时） |
| category | VARCHAR(50) NULL | 分类 |
| latest_version_id | BIGINT NULL | 最新已发布版本 ID |
| latest_version | VARCHAR(20) NULL | 最新已发布版本号 |
| published_version_count | INT | 已发布版本数 |
| created_by | BIGINT | 创建人 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

索引：scope + id、project_id + id

### lab_experiment_template_version

| 列名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 自增主键 |
| template_id | BIGINT | 模板 ID |
| version | VARCHAR(20) | 版本号字符串 |
| version_code | INT NULL | 正式版本数字码；DRAFT 为 null |
| status | VARCHAR(20) | DRAFT / PUBLISHED / DEPRECATED |
| schema_json | TEXT | JSON Schema |
| field_count | INT | 字段数量 |
| changelog | VARCHAR(500) NULL | 变更说明 |
| published_by | BIGINT NULL | 发布人 |
| published_at | DATETIME NULL | 发布时间 |
| deprecated_reason | VARCHAR(500) NULL | 废弃原因 |
| deprecated_by | BIGINT NULL | 废弃操作人 |
| deprecated_at | DATETIME NULL | 废弃时间 |
| created_by | BIGINT | 创建人 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

唯一约束：(template_id, version)
约束：每个 template_id 同时最多一个 DRAFT（迁移采用可被唯一索引约束的生成列或等价方案）
索引：template_id + status、template_id + version_code DESC

### lab_learning_unit 增量

- V9 已存在 `template_id`；V10 只新增 `template_version_id BIGINT NULL`
- `templateName`、`templateVersion` 均为 API 响应字段，通过关联查询得到，不新增冗余列

Flyway 迁移版本：V10（第 6 周新增）

## 12. 版本兼容

| 版本 | 变更 | 兼容策略 |
|---|---|---|
| v1.0（第 6 周） | 实验模板、版本管理、JSON Schema 子集、学习单元模板版本关联、系统/项目模板区分 | 新增表和接口；学习单元持久化新增 template_version_id，API 响应新增 templateVersionId/templateVersion（additive，不破坏 W5 接口） |

**与第 5 周兼容性：**
- `LEARNING_EXPERIMENT_API.md` 中的单元接口新增字段为 additive
- 旧前端忽略新字段，不影响功能
- 旧数据 templateId/templateVersionId 均为 null，不产生错误

**为第 7 周预留：**
- 实验记录表将有 `template_version_id` 字段，引用 `lab_experiment_template_version.id`
- 模板版本不可变性保证实验记录结构稳定
- 禁止物理删除模板版本

## 13. 变更记录

| 日期 | 变更 | 状态 |
|---|---|---|
| 2026-10-11 | 初始草案 v1.0：模板、版本、JSON Schema 子集、学习单元关联、第 7 周引用规则 | REVIEW |
| 2026-10-11 | 冻结校准：固定次版本规则、事务化并发发布、独立废弃审计、权限边界、V10 持久化和受控校验实现 | FROZEN |
