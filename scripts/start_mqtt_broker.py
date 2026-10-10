#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
本地 MQTT Broker 启动脚本
用于 IoT MVP 开发阶段，为 PM-001 模拟器及前后端联调提供 MQTT 服务。

使用方法：
    python scripts/start_mqtt_broker.py

默认监听：127.0.0.1:1883，无用户名密码（仅本地开发使用）。
"""

import asyncio
import logging
from amqtt.broker import Broker

# 配置日志，方便查看连接和消息情况
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# amqtt 最小配置：仅本地访问，使用默认匿名认证插件
BROKER_CONFIG = {
    "listeners": {
        "default": {
            "type": "tcp",
            "bind": "127.0.0.1:1883",
        }
    },
}


async def main() -> None:
    broker = Broker(BROKER_CONFIG)
    await broker.start()
    logger.info("MQTT Broker 已启动：127.0.0.1:1883（按 Ctrl+C 停止）")

    # 保持运行，直到用户手动中断
    stop_event = asyncio.Event()
    try:
        await stop_event.wait()
    finally:
        logger.info("正在关闭 MQTT Broker...")
        await broker.shutdown()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        logger.info("Broker 已退出")
