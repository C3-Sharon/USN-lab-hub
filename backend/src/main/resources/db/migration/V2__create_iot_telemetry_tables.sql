CREATE TABLE IF NOT EXISTS `iot_telemetry_raw` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `device_code` varchar(64) DEFAULT NULL,
    `topic` varchar(255) NOT NULL,
    `payload` text NOT NULL,
    `parse_status` varchar(32) NOT NULL,
    `error_message` varchar(500) DEFAULT NULL,
    `received_at` datetime NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_iot_raw_device_received` (`device_code`, `received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT raw telemetry records';

CREATE TABLE IF NOT EXISTS `iot_metric_data` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `device_id` bigint NOT NULL,
    `device_code` varchar(64) NOT NULL,
    `metric_key` varchar(64) NOT NULL,
    `metric_value` decimal(18,4) NOT NULL,
    `unit` varchar(32) NOT NULL,
    `reported_at` datetime NOT NULL,
    `received_at` datetime NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_iot_metric_latest` (`device_code`, `reported_at`),
    KEY `idx_iot_metric_history` (`device_id`, `metric_key`, `reported_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT parsed metric data';
