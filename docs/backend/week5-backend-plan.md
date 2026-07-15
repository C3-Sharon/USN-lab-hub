# Week 5 Backend Delivery / 第五周后端交付说明

## 1. Goal / 目标

在第四周 PM-001 告警、建议、指令和 ACK 闭环上增加可解释健康评分与公开只读聚合接口：

```text
asset catalog + latest telemetry + power trend + OPEN WARNING
  -> health score -> public project API -> public/overview pages
```

## 2. Implemented Scope / 已实现范围

- 新增 `lab_project`、`iot_device`、`iot_device_metric` 最小只读资产目录和 PM-001 种子数据。
- 新增 PM-001 健康评分服务。
- 新增公开项目聚合接口。
- 支持 30 分钟默认趋势窗口与最多 60 个趋势点。
- 趋势点过多时等距采样，保留时间窗首尾变化。
- 项目不存在时返回 HTTP 404 和 `code=404`。
- 空遥测数据仍返回完整项目和设备结构，指标与趋势为空数组。
- 公开 VO 只包含产品白名单字段。

本周不实现项目/设备 CRUD、多设备评分、AI、WebSocket/SSE 或复杂权限矩阵。

## 3. Health Score / 健康评分

```text
初始分 100
设备非 ONLINE：-40
存在 OPEN WARNING：-30
```

等级：

```text
80-100 HEALTHY
60-79  ATTENTION
0-59   RISK
```

同类扣分只计算一次。告警数量大于零不会重复扣分。

## 4. API

```text
GET /api/iot/public/projects/{projectCode}
```

MVP 支持：

```text
projectCode=power-monitor
```

查询参数：

```text
trendMinutes：默认 30，最大 60
pointCount：默认 30，最大 60
```

小于等于 0 时使用默认值；超过 60 时截断为 60。

## 5. Database Migration / 数据库迁移

```text
backend/src/main/resources/db/migration/V5__create_iot_asset_catalog.sql
```

迁移创建：

- `lab_project`
- `iot_device`
- `iot_device_metric`

并初始化：

- `power-monitor`
- `PM-001`
- `voltage/current/power`

迁移不会修改第四周的遥测、告警、建议、指令或日志表。

## 6. Verification / 验证

目标测试：

```bash
cd backend
mvn "-Dtest=IotHealthServiceTest,IotWeek5PublicIntegrationTest" test
```

全量测试：

```bash
cd backend
mvn test
```

构建：

```bash
cd backend
mvn -DskipTests package
```

集成测试覆盖：

1. 无 telemetry 时 OFFLINE、60 分、空指标和空趋势。
2. 在线且无 OPEN 告警时 100 分。
3. 在线且存在 OPEN WARNING 时 70 分。
4. 四种健康评分组合。
5. `pointCount=1` 与超限参数截断。
6. 未登录直接请求公开接口。
7. 未知项目返回 HTTP 404。
8. 响应不包含 operator、command、payload 或 broker 字段。

## 7. Frontend Handoff / 前端交接

前端统一调用：

```text
GET /api/iot/public/projects/power-monitor
```

Axios 公共响应拦截器会解包 `data`，页面获得的对象从 `projectCode` 开始，不应再读取 `response.data.records`。

公开页和总览页可以共用该接口。健康分由后端计算，前端只映射：

```text
HEALTHY -> 健康
ATTENTION -> 需关注
RISK -> 风险
```

## 8. Known Boundaries / 已知边界

- 第五周只有一个公开项目和一台设备。
- 资产目录当前为 Flyway 种子数据，没有开放 CRUD。
- 公开接口无需 JWT；当前拦截器只保护 `/usnhub/**`，`/api/iot/public/**` 可匿名访问。
- MySQL 是正式运行存储；自动化接口测试使用 MySQL 模式的 H2 验证 SQL 与业务链路。
