#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PM-001 功耗监测设备模拟器
用于 IoT MVP 开发阶段，在本地 MQTT Broker 上模拟设备遥测、指令接收与 ACK 回执。

前置条件：
    先启动 MQTT Broker：python scripts/start_mqtt_broker.py

使用方法：
    python scripts/pm001_simulator.py

行为说明：
    - 每 5 秒向 iot/power-monitor/PM-001/telemetry 发布电压、电流、功率
    - 订阅 iot/power-monitor/PM-001/command 接收控制指令
    - 收到指令后向 iot/power-monitor/PM-001/ack 回复执行结果
    - 每 10 次上报中有 3 次功率会超过 100W，触发 WARNING 告警场景
"""

import json
import logging
import random
import threading
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# 设备与项目配置
PROJECT_CODE = "power-monitor"
DEVICE_CODE = "PM-001"
BROKER_HOST = "127.0.0.1"
BROKER_PORT = 1883
TELEMETRY_INTERVAL = 5  # 秒

# MQTT Topic
TOPIC_TELEMETRY = f"iot/{PROJECT_CODE}/{DEVICE_CODE}/telemetry"
TOPIC_COMMAND = f"iot/{PROJECT_CODE}/{DEVICE_CODE}/command"
TOPIC_ACK = f"iot/{PROJECT_CODE}/{DEVICE_CODE}/ack"
TOPIC_STATUS = f"iot/{PROJECT_CODE}/{DEVICE_CODE}/status"

# 当前告警阈值（单位：W），可被 set_threshold 指令修改
power_threshold = 100


def get_current_timestamp() -> str:
    """生成带时区的时间戳字符串。"""
    return datetime.now(timezone.utc).astimezone().isoformat()


def generate_telemetry(counter: int) -> dict:
    """生成一条遥测数据；每 10 次中有 3 次功率超过阈值，用于触发告警。"""
    if counter % 10 in (0, 3, 7):
        # 高功率场景
        voltage = round(random.uniform(218.0, 225.0), 1)
        current = round(random.uniform(0.55, 0.75), 2)
        power = round(voltage * current, 1)
    else:
        # 正常功率场景
        voltage = round(random.uniform(218.0, 225.0), 1)
        current = round(random.uniform(0.15, 0.35), 2)
        power = round(voltage * current, 1)

    return {
        "projectCode": PROJECT_CODE,
        "deviceCode": DEVICE_CODE,
        "timestamp": get_current_timestamp(),
        "metrics": {
            "voltage": voltage,
            "current": current,
            "power": power,
        },
    }


def handle_command(payload: dict) -> dict:
    """处理来自后端的控制指令，返回 ACK  payload。"""
    global power_threshold

    action = payload.get("action")
    params = payload.get("params", {})
    ack = {
        "projectCode": PROJECT_CODE,
        "deviceCode": DEVICE_CODE,
        "timestamp": get_current_timestamp(),
        "commandId": payload.get("commandId"),
        "action": action,
        "status": "SUCCESS",
        "message": "指令已执行",
    }

    if action == "restart":
        logger.info("收到 restart 指令，模拟设备重启...")
        # 模拟重启耗时
        time.sleep(0.5)
        ack["message"] = "设备重启完成"

    elif action == "set_threshold":
        new_threshold = params.get("threshold")
        if isinstance(new_threshold, (int, float)) and new_threshold > 0:
            power_threshold = int(new_threshold)
            ack["message"] = f"告警阈值已设置为 {power_threshold}W"
            logger.info("告警阈值已设置为 %dW", power_threshold)
        else:
            ack["status"] = "FAILED"
            ack["message"] = "threshold 参数无效"
            logger.warning("set_threshold 参数无效：%s", new_threshold)

    else:
        ack["status"] = "FAILED"
        ack["message"] = f"不支持的指令：{action}"
        logger.warning("收到不支持的指令：%s", action)

    return ack


def on_connect(client, userdata, flags, rc, properties=None):
    """MQTT 连接成功回调。"""
    if rc == 0:
        logger.info("PM-001 已连接到 MQTT Broker：%s:%d", BROKER_HOST, BROKER_PORT)
        client.subscribe(TOPIC_COMMAND)
        logger.info("已订阅指令 Topic：%s", TOPIC_COMMAND)

        # 上报一次在线状态
        status = {
            "projectCode": PROJECT_CODE,
            "deviceCode": DEVICE_CODE,
            "timestamp": get_current_timestamp(),
            "status": "ONLINE",
        }
        client.publish(TOPIC_STATUS, json.dumps(status))
    else:
        logger.error("连接失败，返回码：%d", rc)


def on_message(client, userdata, msg):
    """收到指令后处理并回复 ACK。"""
    try:
        payload = json.loads(msg.payload.decode("utf-8"))
        logger.info("收到指令：%s", payload)
        ack = handle_command(payload)
        client.publish(TOPIC_ACK, json.dumps(ack))
        logger.info("已回复 ACK：%s", ack)
    except json.JSONDecodeError:
        logger.error("指令 JSON 解析失败：%s", msg.payload)
    except Exception as e:
        logger.error("处理指令时出错：%s", e)


def on_disconnect(client, userdata, rc, properties=None):
    """断开连接回调。"""
    logger.warning("PM-001 与 Broker 断开连接，返回码：%s", rc)


def publish_telemetry_loop(client: mqtt.Client):
    """后台线程：定时发布遥测数据。"""
    counter = 0
    while True:
        try:
            telemetry = generate_telemetry(counter)
            client.publish(TOPIC_TELEMETRY, json.dumps(telemetry))
            logger.info("已上报遥测：power=%sW", telemetry["metrics"]["power"])
            counter += 1
        except Exception as e:
            logger.error("上报遥测时出错：%s", e)
        time.sleep(TELEMETRY_INTERVAL)


def main():
    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"{DEVICE_CODE}-simulator"
    )
    client.on_connect = on_connect
    client.on_message = on_message
    client.on_disconnect = on_disconnect

    logger.info("正在连接 MQTT Broker：%s:%d", BROKER_HOST, BROKER_PORT)
    client.connect(BROKER_HOST, BROKER_PORT, keepalive=60)

    # 使用 loop_start 在后台处理网络事件
    client.loop_start()

    # 在主线程中定时发布遥测
    try:
        publish_telemetry_loop(client)
    except KeyboardInterrupt:
        logger.info("模拟器收到停止信号")
    finally:
        client.loop_stop()
        client.disconnect()
        logger.info("PM-001 模拟器已退出")


if __name__ == "__main__":
    main()
