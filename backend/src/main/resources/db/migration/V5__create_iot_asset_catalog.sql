CREATE TABLE IF NOT EXISTS `lab_project` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `project_code` varchar(64) NOT NULL,
    `project_name` varchar(100) NOT NULL,
    `description` varchar(500) DEFAULT NULL,
    `owner_id` bigint DEFAULT NULL,
    `status` varchar(32) NOT NULL,
    `public_visible` tinyint NOT NULL DEFAULT 0,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lab_project_code` (`project_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验室硬件项目';

CREATE TABLE IF NOT EXISTS `iot_device` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `project_id` bigint NOT NULL,
    `device_code` varchar(64) NOT NULL,
    `device_name` varchar(100) NOT NULL,
    `device_type` varchar(64) NOT NULL,
    `protocol` varchar(32) NOT NULL,
    `location` varchar(100) DEFAULT NULL,
    `owner_id` bigint DEFAULT NULL,
    `status` varchar(32) NOT NULL,
    `last_seen_at` datetime DEFAULT NULL,
    `description` varchar(500) DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iot_device_code` (`device_code`),
    KEY `idx_iot_device_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT 设备资产';

CREATE TABLE IF NOT EXISTS `iot_device_metric` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `device_id` bigint NOT NULL,
    `metric_key` varchar(64) NOT NULL,
    `metric_name` varchar(100) NOT NULL,
    `unit` varchar(32) NOT NULL,
    `warning_threshold` decimal(18,4) DEFAULT NULL,
    `enabled` tinyint NOT NULL DEFAULT 1,
    `sort_order` int NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iot_device_metric` (`device_id`, `metric_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT 设备指标定义';

INSERT INTO `lab_project`
    (`id`, `project_code`, `project_name`, `description`, `owner_id`, `status`, `public_visible`)
VALUES
    (1, 'power-monitor', '实验室功耗监测', '实验室功耗监测与 MQTT 联动演示项目', 1, 'ACTIVE', 1)
ON DUPLICATE KEY UPDATE
    `project_name` = VALUES(`project_name`),
    `description` = VALUES(`description`),
    `status` = VALUES(`status`),
    `public_visible` = VALUES(`public_visible`);

INSERT INTO `iot_device`
    (`id`, `project_id`, `device_code`, `device_name`, `device_type`, `protocol`, `location`,
     `owner_id`, `status`, `description`)
VALUES
    (1, 1, 'PM-001', '实验室功耗监测仪 #1', 'POWER_METER', 'MQTT', '实验室',
     1, 'OFFLINE', 'PM-001 最小纵向切片演示设备')
ON DUPLICATE KEY UPDATE
    `project_id` = VALUES(`project_id`),
    `device_name` = VALUES(`device_name`),
    `device_type` = VALUES(`device_type`),
    `protocol` = VALUES(`protocol`),
    `description` = VALUES(`description`);

INSERT INTO `iot_device_metric`
    (`id`, `device_id`, `metric_key`, `metric_name`, `unit`, `warning_threshold`, `enabled`, `sort_order`)
VALUES
    (1, 1, 'voltage', '电压', 'V', NULL, 1, 1),
    (2, 1, 'current', '电流', 'A', NULL, 1, 2),
    (3, 1, 'power', '功率', 'W', 100.0000, 1, 3)
ON DUPLICATE KEY UPDATE
    `metric_name` = VALUES(`metric_name`),
    `unit` = VALUES(`unit`),
    `warning_threshold` = VALUES(`warning_threshold`),
    `enabled` = VALUES(`enabled`),
    `sort_order` = VALUES(`sort_order`);
