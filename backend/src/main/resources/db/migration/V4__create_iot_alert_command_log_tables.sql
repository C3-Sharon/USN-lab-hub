CREATE TABLE IF NOT EXISTS `iot_alert_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `device_id` bigint NOT NULL,
    `device_code` varchar(64) NOT NULL,
    `metric_key` varchar(64) NOT NULL,
    `level` varchar(32) NOT NULL,
    `message` varchar(500) NOT NULL,
    `trigger_value` decimal(18,4) NOT NULL,
    `threshold_value` decimal(18,4) NOT NULL,
    `status` varchar(32) NOT NULL,
    `created_at` datetime NOT NULL,
    `last_triggered_at` datetime NOT NULL,
    `handled_by` bigint DEFAULT NULL,
    `handled_at` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_iot_alert_device_status` (`device_id`, `status`, `created_at`),
    KEY `idx_iot_alert_dedup` (`device_id`, `metric_key`, `level`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT alert records';

CREATE TABLE IF NOT EXISTS `iot_recommendation` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `device_id` bigint NOT NULL,
    `device_code` varchar(64) NOT NULL,
    `alert_id` bigint NOT NULL,
    `source` varchar(32) NOT NULL,
    `title` varchar(100) NOT NULL,
    `content` varchar(1000) NOT NULL,
    `status` varchar(32) NOT NULL,
    `created_at` datetime NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iot_recommendation_alert` (`alert_id`),
    KEY `idx_iot_recommendation_device_status` (`device_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT rule recommendations';

CREATE TABLE IF NOT EXISTS `iot_command_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `command_id` varchar(64) NOT NULL,
    `device_id` bigint NOT NULL,
    `device_code` varchar(64) NOT NULL,
    `command` varchar(100) NOT NULL,
    `params_json` text NOT NULL,
    `status` varchar(32) NOT NULL,
    `message` varchar(500) DEFAULT NULL,
    `created_by` bigint DEFAULT NULL,
    `created_at` datetime NOT NULL,
    `sent_at` datetime DEFAULT NULL,
    `acked_at` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iot_command_id` (`command_id`),
    KEY `idx_iot_command_device_status` (`device_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT command records';

CREATE TABLE IF NOT EXISTS `iot_operation_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `device_id` bigint NOT NULL,
    `operator_id` bigint NOT NULL,
    `operator_name` varchar(100) NOT NULL,
    `action` varchar(64) NOT NULL,
    `target_type` varchar(64) NOT NULL,
    `target_id` varchar(64) NOT NULL,
    `summary` varchar(500) NOT NULL,
    `created_at` datetime NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_iot_log_device_created` (`device_id`, `created_at`),
    KEY `idx_iot_log_action_target` (`action`, `target_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT operation logs';
