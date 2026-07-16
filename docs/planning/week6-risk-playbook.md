# Week 6 Risk Playbook / 第六周演示故障预案

USN Lab Hub IoT · 一个半月 MVP 答辩与演示兜底

---

## 1. 使用方式

本预案用于答辩或老师审阅前 30 分钟的快速排查。每个故障按现象、原因、检查命令、兜底动作四列描述。若无法在 5 分钟内修复，立即切换到兜底动作，保证演示继续。

---

## 2. Broker 无法连接

| 维度 | 内容 |
|---|---|
| 现象 | 后端日志报 MQTT connect failed；`mosquitto_sub` 无法订阅；页面无实时数据 |
| 常见原因 | Broker 未启动；端口被占用；防火墙拦截 |
| 检查命令 | `netstat -ano \| findstr 1883` / `mosquitto -v` |
| 兜底动作 | 重新启动 Broker；若端口占用，修改模拟器和后端配置为其他端口并重启两者 |

快速重启：

```bash
# Windows 使用 mosquitto
net stop mosquitto
net start mosquitto

# 或使用 Docker
docker run -d --name mosquitto -p 1883:1883 eclipse-mosquitto
```

---

## 3. MySQL 迁移失败

| 维度 | 内容 |
|---|---|
| 现象 | 后端启动报错 `FlywayException` 或表不存在 |
| 常见原因 | 数据库未创建；迁移脚本冲突；已有脏数据 |
| 检查命令 | `mysql -h localhost -u root -p -e "show tables;" usn_lab_hub` |
| 兜底动作 | 在本地开发环境重建数据库：`DROP DATABASE usn_lab_hub; CREATE DATABASE usn_lab_hub;` 后重启后端；演示前备份生产数据 |

注意：重建数据库会清空数据，仅用于本地演示环境。

---

## 4. 真实硬件离线

| 维度 | 内容 |
|---|---|
| 现象 | 页面显示 PM-001 OFFLINE；无新数据 |
| 常见原因 | 硬件未通电；网络异常；固件未运行；Broker 不可达 |
| 检查命令 | 检查硬件指示灯；在硬件端 ping Broker；查看硬件串口日志 |
| 兜底动作 | 立即启动 `scripts/pm001_simulator.py` 替代真实硬件；模拟器与真实硬件使用相同 Topic 和 JSON 协议，演示效果一致 |

---

## 5. 模拟器未上报

| 维度 | 内容 |
|---|---|
| 现象 | `mosquitto_sub` 收不到 telemetry；数据库无新记录 |
| 常见原因 | 模拟器未启动；Broker 地址错误；Topic 拼写错误；端口错误 |
| 检查命令 | `python scripts/pm001_simulator.py` 控制台输出；`mosquitto_sub -h localhost -p 1883 -t 'iot/power-monitor/PM-001/telemetry' -v` |
| 兜底动作 | 检查并修正 `scripts/pm001_simulator.py` 顶部的 `BROKER_HOST` 和 `BROKER_PORT`；重新启动模拟器 |

---

## 6. 页面接口报错

| 维度 | 内容 |
|---|---|
| 现象 | 前端页面白屏或显示接口错误；浏览器 F12 看到 500/404/CORS 错误 |
| 常见原因 | 后端未启动；后端报错；前端代理配置错误；跨域未放行 |
| 检查命令 | `curl http://localhost:8080/api/iot/public/projects/power-monitor`；查看后端日志 |
| 兜底动作 | 重启后端；检查 `frontend/vite.config.js` 代理配置；确认后端允许跨域或前端代理指向正确端口 |

---

## 7. 指令未收到 ACK

| 维度 | 内容 |
|---|---|
| 现象 | 指令状态停留在 `SENT` 超过 10 秒，最终变为 `TIMEOUT` |
| 常见原因 | 模拟器未订阅 command Topic；模拟器解析 command 失败；模拟器未发布 ack；网络丢包 |
| 检查命令 | `mosquitto_sub -h localhost -p 1883 -t 'iot/power-monitor/PM-001/ack' -v`；查看模拟器日志 |
| 兜底动作 | 重启模拟器；检查模拟器是否正确订阅 `iot/power-monitor/PM-001/command`；手动在 MQTT 客户端发布 ack 消息完成演示 |

手动发布 ack（仅用于本地兜底）：

```bash
mosquitto_pub -h localhost -p 1883 -t 'iot/power-monitor/PM-001/ack' -m '{
  "commandId": "cmd-xxx",
  "command": "SET_SAMPLE_INTERVAL",
  "status": "ACKED",
  "result": {"intervalSeconds": 5},
  "ackedAt": "2026-07-15 14:35:10"
}'
```

---

## 8. 健康评分异常

| 维度 | 内容 |
|---|---|
| 现象 | 公开页健康评分与预期不符，例如在线无告警却显示 60 分 |
| 常见原因 | 后端在线判断逻辑错误；OPEN 告警未正确过滤；扣分项重复计算 |
| 检查命令 | 调用 `GET /api/iot/public/projects/power-monitor` 查看 `device.status` 和 `health.reasons`；查询 `iot_alert_record` 中 OPEN 告警数量 |
| 兜底动作 | 按 `week5-product-spec.md` 重新核对计算逻辑；如时间紧迫，前端可临时写死展示值，但需记录为待修复缺陷 |

---

## 9. 公开展示页泄露敏感信息

| 维度 | 内容 |
|---|---|
| 现象 | 公开接口响应中出现 `operatorName`、`commandId`、`payload`、`broker` 等字段 |
| 常见原因 | 后端 VO 组装错误；测试分支误合入 |
| 检查命令 | 用 curl 调用公开接口并检查响应 JSON |
| 兜底动作 | 立即修复后端 VO，移除黑名单字段；若无法立即修复，演示时关闭公开页，只展示登录后页面 |

---

## 10. 演示时间不足

| 维度 | 内容 |
|---|---|
| 现象 | 老师只给 5 分钟或设备临时故障 |
| 常见原因 | 日程压缩或现场意外 |
| 检查命令 | 无 |
| 兜底动作 | 切换到快速验收路径：公开页 30 秒 → PM-001 页 30 秒 → 触发告警 30 秒 → 下发指令 1 分钟 → ACK 30 秒 → 返回公开页 30 秒，总计约 4 分钟 |

---

## 11. 现场无网络

| 维度 | 内容 |
|---|---|
| 现象 | 无法访问 GitHub、无法下载依赖、无法远程演示 |
| 常见原因 | 答辩场地网络限制 |
| 检查命令 | `ping github.com` |
| 兜底动作 | 提前在本地安装所有依赖并打包；准备离线 Docker 镜像；不依赖外部网络运行 |

---

## 12. 通用兜底原则

1. **先让演示能继续，再排错。**
2. **模拟器优先。** 任何硬件问题都可以用模拟器替代。
3. **重启大法。** 80% 的本地问题通过重启 Broker、MySQL、后端、模拟器、前端解决。
4. **预留 5 分钟缓冲。** 演示前 5 分钟做最后一次全流程检查。
5. **准备录屏。** 现场演示不顺利时，可直接播放预录的完整演示视频。
