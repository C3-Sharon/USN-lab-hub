# Week 5 Product Spec / 第五周产品冻结文档

USN Lab Hub IoT · 实验室硬件项目管理与智能运维平台

主题：完整业务闭环 → 健康评分 → 公开成果展示 → 演示脚本 → 老师审阅材料

---

## 1. 本周目标与定位

第五周在第四周已冻结的告警-建议-指令-日志闭环基础上，完成三件事：

1. 为 PM-001 定义一套可解释、可测试的最小健康评分。
2. 新增面向访客的公开项目展示页 `/iot/public`，只暴露安全数据。
3. 在登录后 IoT 总览页 `/iot/overview` 补充第五周所需的项目-设备-健康概览。

同时输出完整演示故事线和老师审阅版阶段成果说明，供第六周答辩准备使用。

---

## 2. 不做范围

第五周不引入以下内容：

- AI Agent 自动控制
- 多设备健康评分体系
- 可配置规则引擎
- WebSocket / SSE
- 消息队列或时序数据库
- 完整项目/设备 CRUD 重构
- 复杂权限矩阵
- PPT 最终视觉制作
- 新的高风险控制指令
- 推翻或修改第四周已冻结的 MQTT、API、数据库字段

---

## 3. 第四周能力基线

第四周已合并到 `dev` 的能力包括：

- MQTT telemetry 接入：设备上报 `voltage / current / power`，后端存入 MySQL。
- 最新数据接口：返回设备状态、上报时间、三项指标。
- 历史数据接口：返回功率历史曲线（默认），支持电压/电流切换。
- 告警：`power > 100W` 触发 `WARNING`，支持去重，`OPEN / HANDLED / IGNORED` 三态。
- 建议：告警关联建议，`PENDING / CONFIRMED / IGNORED` 三态。
- 指令：唯一指令 `SET_SAMPLE_INTERVAL`，固定参数 `intervalSeconds = 5`。
- ACK：MQTT `iot/power-monitor/PM-001/ack`，10 秒超时。
- 操作日志：记录 `HANDLE_ALERT / CONFIRM_RECOMMENDATION / SEND_COMMAND / RECEIVE_ACK`。

第五周只在此基础上增加健康评分和两类展示页，不修改上述闭环。

---

## 4. 健康评分规则

### 4.1 设计原则

- 只使用已经存在的第四周数据：设备在线状态、OPEN WARNING 告警。
- 不使用 AI、趋势预测或加权模型。
- 分数可人工用输入数据复算，保证可解释性。

### 4.2 计算公式

```text
初始分 = 100
离线或超过 15 秒未上报：-40
存在 OPEN 状态的 WARNING 告警：-30
最终分 = max(0, 100 - 离线扣分 - 告警扣分)
```

扣分项不叠加同类，只叠加不同类。例如：

- 离线且有一个 OPEN 告警：`100 - 40 - 30 = 30`
- 离线但没有 OPEN 告警：`100 - 40 = 60`
- 在线且有一个 OPEN 告警：`100 - 30 = 70`
- 在线且无 OPEN 告警：`100`

### 4.3 健康等级

| 分数区间 | 等级 | 含义 |
|---|---|---|
| 80 - 100 | HEALTHY | 健康 |
| 60 - 79 | ATTENTION | 需关注 |
| 0 - 59 | RISK | 风险 |

### 4.4 reasons 字段

`reasons` 为字符串数组，按扣分项写入可读原因：

- 离线：`设备离线或超过 15 秒未上报`
- OPEN WARNING：`存在未处理的功率告警`

无扣分项时返回空数组。

### 4.5 计算示例

| 设备状态 | OPEN WARNING | 计算过程 | score | level | reasons |
|---|---|---|---|---|---|
| ONLINE，上报时间 2026-07-15 10:30:00 | 无 | 100 - 0 - 0 | 100 | HEALTHY | [] |
| ONLINE | 1 条 power > 100W | 100 - 0 - 30 | 70 | ATTENTION | ["存在未处理的功率告警"] |
| OFFLINE | 无 | 100 - 40 - 0 | 60 | ATTENTION | ["设备离线或超过 15 秒未上报"] |
| OFFLINE | 1 条 OPEN | 100 - 40 - 30 | 30 | RISK | ["设备离线或超过 15 秒未上报", "存在未处理的功率告警"] |

### 4.6 返回字段

健康评分对象固定返回以下字段：

```json
{
  "score": 100,
  "level": "HEALTHY",
  "reasons": [],
  "calculatedAt": "2026-07-15 10:30:01"
}
```

字段含义：

| Field | Type | 说明 |
|---|---|---|
| score | number | 0 - 100 的整数 |
| level | string | HEALTHY / ATTENTION / RISK |
| reasons | string[] | 扣分原因列表，可为空 |
| calculatedAt | string | 后端计算时间，格式 `yyyy-MM-dd HH:mm:ss` |

---

## 5. 公开项目展示页 /iot/public

### 5.1 页面定位

`/iot/public` 面向未登录访客，仅展示一个真实项目 `power-monitor` 和一台设备 `PM-001`。页面只读，不暴露管理能力、隐私信息和写接口。

### 5.2 可公开字段

- 项目编号 `projectCode`
- 项目名称 `projectName`
- 项目简介 `description`
- 项目状态 `status`
- 设备总数 `deviceCount`
- 在线设备数 `onlineDeviceCount`
- 设备编号 `deviceCode`
- 设备名称 `deviceName`
- 设备在线状态 `status`
- 最新上报时间 `reportTime`
- 最新指标 `voltage / current / power` 及单位
- 最近功率趋势 `powerTrend`
- 健康评分 `score / level / reasons / calculatedAt`
- 页面数据更新时间 `updatedAt`

### 5.3 禁止公开字段

- 用户 ID、负责人姓名、联系方式
- 原始 MQTT payload
- 内部操作日志
- 告警处理人、处理备注
- 指令控制按钮或指令历史
- MQTT Broker 地址、端口、凭证
- 管理接口和任何写操作入口

### 5.4 页面区域与文案

| 区域 | 文案/说明 |
|---|---|
| 顶部品牌栏 | `USN Lab Hub IoT · 公开项目展示` |
| 项目卡片 | 展示 `power-monitor` 项目编号、名称、简介、状态标签、设备数/在线数 |
| 设备卡片 | 展示 `PM-001` 设备编号、名称、在线状态、最新上报时间 |
| 指标卡片 | 三列：电压 V / 电流 A / 功率 W |
| 健康评分 | 大字体展示 `score`，标签展示 `level`，下方列出 `reasons` |
| 功率趋势 | 折线图，标题 `最近功率趋势`，默认近 30 分钟 |
| 页脚 | `USN Lab Hub IoT · 实验室硬件项目管理与智能运维平台` |
| 更新时间 | 页面右下角显示 `数据更新于 2026-07-15 10:30:01` |

### 5.5 交互要求

- 页面进入时自动加载一次公开数据。
- 每 30 秒自动刷新一次，刷新时显示 loading 但不整页闪动。
- 设备离线时，在线状态标签变为灰色或红色，功率趋势保留已缓存数据。
- 无数据时显示 `暂无公开项目数据`，不报错。

---

## 6. 登录后 IoT 总览页 /iot/overview

### 6.1 页面定位

`/iot/overview` 是登录后的 IoT 首页，面向管理员和老师。第五周仅补充与公开展示一致的核心摘要，不重构整个页面。

### 6.2 展示字段

- 当前激活项目数
- 当前在线设备数 / 设备总数
- `power-monitor` 项目卡片，可点击进入 PM-001 详情
- PM-001 最新 `voltage / current / power`
- PM-001 健康评分和等级
- 最近 24 小时内是否有未处理告警的提示
- 快捷入口：PM-001 实时页、公开展示页

### 6.3 与公开页的区别

| 维度 | 公开页 `/iot/public` | 总览页 `/iot/overview` |
|---|---|---|
| 访问权限 | 未登录可访问 | 登录后可访问 |
| 数据范围 | 仅 power-monitor / PM-001 | 可展示多个项目摘要 |
| 控制能力 | 无 | 提供进入 PM-001 管理页的入口 |
| 敏感信息 | 严格过滤 | 按角色展示负责人等内部信息 |

---

## 7. 公开信息白名单与黑名单

### 白名单

```text
projectCode / projectName / description / status
deviceCount / onlineDeviceCount
device.id / deviceCode / deviceName / device.status / reportTime
metrics.metricKey / metricName / value / unit
health.score / level / reasons / calculatedAt
powerTrend.time / value
updatedAt
```

### 黑名单

```text
operatorId / operatorName
原始 telemetry payload
createdBy / handledBy / handledNote
commandId / command / params / ack payload
broker host / port / username / password
内部 API 路径前缀以外的管理接口
```

---

## 8. API 契约

### 8.1 公开聚合接口

```text
GET /api/iot/public/projects/power-monitor
Status: changed
```

可选查询参数：

| Name | Type | Required | Description |
|---|---|---|---|
| trendMinutes | number | no | 功率趋势时间窗，默认 30，最大 60 |
| pointCount | number | no | 趋势点数，默认 30，最大 60 |

说明：

- 两个参数用于前端调整趋势图密度，后端按默认值兜底。
- 参数超限时后端自动截断到最大值，返回 200 而不是报错。

### 8.2 正常响应

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "projectCode": "power-monitor",
    "projectName": "实验室功耗监测",
    "description": "实验室功耗监测与 MQTT 联动演示项目",
    "status": "ACTIVE",
    "deviceCount": 1,
    "onlineDeviceCount": 1,
    "device": {
      "id": 1,
      "deviceCode": "PM-001",
      "deviceName": "实验室功耗监测仪 #1",
      "status": "ONLINE",
      "reportTime": "2026-07-15 10:30:00",
      "metrics": [
        {
          "metricKey": "voltage",
          "metricName": "电压",
          "value": 220.3,
          "unit": "V"
        },
        {
          "metricKey": "current",
          "metricName": "电流",
          "value": 0.42,
          "unit": "A"
        },
        {
          "metricKey": "power",
          "metricName": "功率",
          "value": 92.5,
          "unit": "W"
        }
      ],
      "health": {
        "score": 100,
        "level": "HEALTHY",
        "reasons": [],
        "calculatedAt": "2026-07-15 10:30:01"
      }
    },
    "powerTrend": [
      {
        "time": "2026-07-15 10:29:00",
        "value": 92.5
      },
      {
        "time": "2026-07-15 10:29:30",
        "value": 93.1
      },
      {
        "time": "2026-07-15 10:30:00",
        "value": 92.5
      }
    ],
    "updatedAt": "2026-07-15 10:30:01"
  }
}
```

### 8.3 空数据响应

项目存在但设备从未上报：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "projectCode": "power-monitor",
    "projectName": "实验室功耗监测",
    "description": "实验室功耗监测与 MQTT 联动演示项目",
    "status": "ACTIVE",
    "deviceCount": 1,
    "onlineDeviceCount": 0,
    "device": {
      "id": 1,
      "deviceCode": "PM-001",
      "deviceName": "实验室功耗监测仪 #1",
      "status": "OFFLINE",
      "reportTime": null,
      "metrics": [],
      "health": {
        "score": 60,
        "level": "ATTENTION",
        "reasons": ["设备离线或超过 15 秒未上报"],
        "calculatedAt": "2026-07-15 10:30:01"
      }
    },
    "powerTrend": [],
    "updatedAt": "2026-07-15 10:30:01"
  }
}
```

### 8.4 离线但有历史数据

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "projectCode": "power-monitor",
    "projectName": "实验室功耗监测",
    "status": "ACTIVE",
    "deviceCount": 1,
    "onlineDeviceCount": 0,
    "device": {
      "id": 1,
      "deviceCode": "PM-001",
      "deviceName": "实验室功耗监测仪 #1",
      "status": "OFFLINE",
      "reportTime": "2026-07-15 10:25:00",
      "metrics": [
        { "metricKey": "voltage", "metricName": "电压", "value": 220.1, "unit": "V" },
        { "metricKey": "current", "metricName": "电流", "value": 0.41, "unit": "A" },
        { "metricKey": "power", "metricName": "功率", "value": 90.2, "unit": "W" }
      ],
      "health": {
        "score": 60,
        "level": "ATTENTION",
        "reasons": ["设备离线或超过 15 秒未上报"],
        "calculatedAt": "2026-07-15 10:30:01"
      }
    },
    "powerTrend": [
      { "time": "2026-07-15 10:24:00", "value": 89.8 },
      { "time": "2026-07-15 10:24:30", "value": 90.0 },
      { "time": "2026-07-15 10:25:00", "value": 90.2 }
    ],
    "updatedAt": "2026-07-15 10:30:01"
  }
}
```

### 8.5 异常状态

| 场景 | HTTP | code | msg |
|---|---|---|---|
| 项目不存在 | 404 | 404 | 项目不存在 |
| 后端异常 | 500 | 500 | 系统繁忙，请稍后重试 |

说明：公开页接口不校验登录态，但会被限流或置于白名单路由。

---

## 9. 前端交互要求

### 9.1 公开页 `/iot/public`

- 不依赖登录态，不展示左侧菜单或顶部用户头像。
- 使用卡片式布局，宽屏下指标三列，小屏自动堆叠。
- 健康评分使用颜色区分：HEALTHY 绿色、ATTENTION 橙色、RISK 红色。
- 功率趋势使用 ECharts 折线图，x 轴只显示 `HH:mm`，y 轴单位 `W`。
- 自动刷新期间保留旧数据，避免白屏。

### 9.2 总览页 `/iot/overview`

- 保持现有布局框架，新增第五周数据摘要区。
- 摘要区包含：项目数、在线设备数、PM-001 健康评分、未处理告警提示。
- 提供两个按钮：
  - `查看 PM-001 实时页` → 跳转 `/iot/pm001`
  - `预览公开展示页` → 新标签打开 `/iot/public`

### 9.3 数据映射

| 前端字段 | 后端来源 |
|---|---|
| projectName / description | `lab_project` |
| deviceCount / onlineDeviceCount | `iot_device` 统计 |
| device.status / reportTime | 最近 telemetry 与 15 秒规则 |
| metrics.value / unit | 最近 telemetry + `iot_device_metric` |
| health | 实时计算 |
| powerTrend | `iot_metric_data` 近 N 分钟功率 |
| updatedAt | 接口响应生成时间 |

---

## 10. 完整演示脚本

### 场景一：访客公开页

1. 未登录打开 `/iot/public`。
2. 页面展示 `power-monitor` 项目和 `PM-001` 设备卡片。
3. 展示最新电压、电流、功率和健康评分。
4. 展示最近功率趋势折线图。
5. 说明：该页面只读，不暴露管理入口和隐私信息。

### 场景二：管理员闭环操作

6. 管理员登录，进入 `/iot/pm001`。
7. 查看最新数据、历史曲线、告警、建议、指令、日志均正常加载。
8. 启动 `scripts/pm001_simulator.py` 或真实硬件，上报 `power = 110.5W`。
9. 页面告警区出现 `OPEN` 告警：功率超过 100W。
10. 建议区出现规则建议：建议缩短采样间隔。
11. 点击建议卡片上的 `确认并下发指令`。
12. 指令区新增 `SET_SAMPLE_INTERVAL` 记录，状态 `PENDING → SENT`。
13. 模拟器收到 MQTT command 并回复 ack。
14. 指令状态变为 `ACKED`。
15. 操作日志出现 `SEND_COMMAND` 和 `RECEIVE_ACK` 记录。

### 场景三：返回公开页验证边界

16. 回到 `/iot/public`。
17. 此时功率已超过 100W，健康评分变为 70，`level` 为 `ATTENTION`，`reasons` 显示存在未处理功率告警。
18. 公开页仍不展示告警处理按钮、指令控制按钮、操作日志和负责人信息。

---

## 11. 验收标准

### 11.1 健康评分验收

- 输入 ONLINE + 无告警 → 返回 `score=100 / HEALTHY / reasons=[]`。
- 输入 ONLINE + 1 条 OPEN WARNING → 返回 `score=70 / ATTENTION / reasons=["存在未处理的功率告警"]`。
- 输入 OFFLINE + 无告警 → 返回 `score=60 / ATTENTION / reasons=["设备离线或超过 15 秒未上报"]`。
- 输入 OFFLINE + 1 条 OPEN WARNING → 返回 `score=30 / RISK / reasons=[...]`。

### 11.2 公开接口验收

- 返回字段与 `week5-product-spec.md` 一致。
- 不返回任何黑名单字段。
- 空数据时返回 200，结构完整，`metrics` 和 `powerTrend` 为空数组。
- 项目不存在时返回 404。

### 11.3 页面验收

- `/iot/public` 在未登录状态下可访问，布局符合第 5 节区域划分。
- `/iot/overview` 登录后可见 PM-001 摘要和健康评分。
- 自动刷新不导致页面闪烁或数据丢失。

### 11.4 演示验收

- 演示脚本三步可在 10 分钟内完成。
- 每一步都有可观察的页面变化或数据变化。
- 模拟器可独立支撑演示，不依赖真实硬件在线。

---

## 12. 三方交接要求

### 12.1 后端交接

需要新增：

- `GET /api/iot/public/projects/power-monitor` 聚合接口。
- 健康评分计算服务：输入 `deviceId`，输出 `score / level / reasons / calculatedAt`。
- 公开接口放行/白名单配置，避免被 JWT 拦截。
- 可复用已有 `latest / history` 查询能力组装公开数据。

数据依赖：

- `lab_project`：项目编号、名称、简介、状态。
- `iot_device`：设备编号、名称、状态、所属项目。
- `iot_metric_data`：最新指标、功率趋势。
- `iot_alert_record`：OPEN WARNING 告警数量。

### 12.2 前端交接

需要新增/调整：

- `/iot/public` 页面：按第 5 节区域和文案实现。
- `/iot/overview` 页面：按第 6 节补充摘要区。
- 新增公开接口调用方法 `getPublicProject`。
- 复用已有的健康等级颜色、指标卡片、ECharts 折线图组件。

### 12.3 硬件/模拟器交接

无需修改。继续复用 `scripts/pm001_simulator.py` 上报 telemetry 和回复 command ack 的能力。

---

## 13. 风险与兜底

| 风险 | 影响 | 兜底方案 |
|---|---|---|
| 健康评分规则太简单，老师质疑 | 演示说服力不足 | 明确解释这是 MVP 最小口径，长期会扩展为加权模型 |
| 公开接口被扫描滥用 | 数据泄露风险 | 接口只返回白名单字段，必要时加 IP/频率限制 |
| 真实硬件离线导致公开页分数低 | 演示效果差 | 演示前 5 分钟启动模拟器，保持设备在线 |
| 功率趋势数据不足 | 折线图空白 | 演示前让模拟器运行 5 分钟以上积累数据 |
| 公开页与总览页字段重复 | 维护成本 | 两页共用同一公开接口，总览页只取摘要 |

---

## 14. 变更记录

| Date | Change | Owner |
|---|---|---|
| 2026-07-15 | 初始冻结第五周健康评分、公开页、总览页、演示脚本 | Product |
