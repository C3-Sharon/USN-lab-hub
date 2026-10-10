# Week 6 Backend Delivery / 第六周后端部署与验收说明

## 1. 本周范围

第六周只做 MVP 收尾、验证和部署说明，不新增业务接口或 MQTT 消息。

本次后端交付包括：

- 验证 Flyway 迁移版本 `V1 / V2 / V4 / V5` 可在空库按顺序执行。
- 提供只暴露状态的 `GET /actuator/health` 健康检查。
- 保持公开接口、遥测、告警、建议、指令、ACK 和日志契约不变。
- 统一演示数据库名称为 `usn_hub`。
- 允许通过环境变量覆盖 MQTT Broker、客户端编号和三个 Topic。

## 2. 环境要求

| 组件 | 建议版本 | 默认地址 |
|---|---|---|
| JDK | 17 或 21 | `JAVA_HOME` |
| MySQL | 8.x | `localhost:3306/usn_hub` |
| MQTT Broker | EMQX 或 Mosquitto | `tcp://127.0.0.1:1883` |
| 后端 | Spring Boot 3.1.5 | `http://localhost:8080` |

Redis 不是 PM-001 第六周演示闭环的启动前置条件，健康检查不会因为本机 Redis 未启动而返回失败。

## 3. 配置项

不要把个人密码写入仓库。需要覆盖默认值时，在启动后端的同一终端设置环境变量。

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring Profile |
| `DB_URL` | `jdbc:mysql://localhost:3306/usn_hub?...` | MySQL JDBC URL |
| `DB_USERNAME` | `root` | MySQL 用户名 |
| `DB_PASSWORD` | 空 | MySQL 密码 |
| `FLYWAY_ENABLED` | `true` | 是否执行迁移 |
| `IOT_MQTT_ENABLED` | `true` | 是否启动 MQTT 订阅 |
| `IOT_MQTT_BROKER_URL` | `tcp://127.0.0.1:1883` | Broker 地址 |
| `IOT_MQTT_CLIENT_ID` | `usn-lab-hub-backend` | 后端订阅客户端编号 |
| `IOT_MQTT_TELEMETRY_TOPIC` | `iot/power-monitor/PM-001/telemetry` | 遥测 Topic |
| `IOT_MQTT_COMMAND_TOPIC` | `iot/power-monitor/PM-001/command` | 指令 Topic |
| `IOT_MQTT_ACK_TOPIC` | `iot/power-monitor/PM-001/ack` | ACK Topic |

PowerShell 示例：

```powershell
$env:DB_USERNAME='root'
$env:DB_PASSWORD='你的本机密码'
$env:IOT_MQTT_BROKER_URL='tcp://127.0.0.1:1883'
```

## 4. 首次数据库准备

默认数据库名与 README、开发配置保持一致，统一为 `usn_hub`。

```sql
CREATE DATABASE IF NOT EXISTS usn_hub
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

后端启动时 Flyway 自动执行：

```text
V1  成员、角色和考勤基础表
V2  IoT 原始遥测和指标表
V4  告警、建议、指令和操作日志表
V5  项目、设备和指标目录
```

版本号没有 `V3` 是历史版本安排，不代表漏执行迁移。不要手工修改 `flyway_schema_history`。

## 5. 启动顺序

```text
MQTT Broker -> MySQL -> 后端 -> PM-001 模拟器/硬件 -> 前端
```

后端启动：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

启动成功后检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/api/iot/public/projects/power-monitor
```

预期：

- 健康检查返回 HTTP 200 和 `{"status":"UP"}`，不暴露数据库或主机细节。
- 公开接口返回 HTTP 200、`code=200` 和 `projectCode=power-monitor`。
- 项目存在但尚未上报时，PM-001 为 `OFFLINE`，指标和趋势为空。

## 6. 后端测试

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
```

第六周新增就绪测试验证：

1. Flyway 在空的 H2 MySQL 兼容数据库执行全部 MVP 迁移。
2. 应用启动后 `/actuator/health` 可访问且只返回总体状态。
3. 未登录可以访问公开项目接口。
4. 公开响应不包含 payload、Broker 或操作者信息。

## 7. 真实环境验收

1. 启动 Broker、MySQL 和后端。
2. 确认后端日志出现 Flyway 成功信息和 MQTT 订阅成功信息。
3. 启动 `python scripts/pm001_simulator.py`。
4. 查询 `iot_telemetry_raw`，确认新增一条原始 payload。
5. 查询 `iot_metric_data`，确认新增 voltage/current/power 三条指标。
6. 调用 latest、history 和 public 接口并核对页面字段。
7. 触发 `power=110.5`，完成告警、建议、指令、ACK、日志闭环。

## 8. 故障定位

| 现象 | 优先检查 | 处理 |
|---|---|---|
| 后端无法启动 | MySQL 地址、账号、密码 | 修正 `DB_*` 环境变量后重启 |
| Flyway 校验失败 | 是否手工改过表或迁移文件 | 使用备份；开发空库可重建，禁止改历史记录蒙混通过 |
| 健康检查 `DOWN` | MySQL 是否可连接 | 先恢复数据库；Redis 不影响本端点 |
| MQTT 无数据 | Broker、Topic、客户端编号 | 核对三个 `IOT_MQTT_*_TOPIC` 与契约 |
| 指令不变为 ACKED | 模拟器是否订阅 command 并发布 ack | 核对 `commandId` 和 ACK Topic |

## 9. 契约影响

- API_CONTRACT：无业务字段变化；新增运维端点 `/actuator/health`。
- MQTT_CONTRACT：无变化。
- 数据库表：无变化。
- 前端字段：无变化。
