CREATE TABLE `lab_learning_roadmap` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(80) NOT NULL,
    `description` varchar(500) DEFAULT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
    `difficulty` varchar(32) NOT NULL DEFAULT 'BEGINNER',
    `estimated_hours` int DEFAULT NULL,
    `cover_media_id` varchar(64) DEFAULT NULL,
    `sort_order` int NOT NULL DEFAULT 0,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_learning_roadmap_status_sort` (`status`, `sort_order`, `id`),
    KEY `idx_learning_roadmap_difficulty_status` (`difficulty`, `status`),
    KEY `idx_learning_roadmap_creator_updated` (`created_by`, `update_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Learning roadmaps';

CREATE TABLE `lab_learning_stage` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `roadmap_id` bigint NOT NULL,
    `name` varchar(80) NOT NULL,
    `description` varchar(500) DEFAULT NULL,
    `sort_order` int NOT NULL DEFAULT 0,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_learning_stage_roadmap_sort` (`roadmap_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ordered stages in a learning roadmap';

CREATE TABLE `lab_learning_unit` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `stage_id` bigint NOT NULL,
    `title` varchar(120) NOT NULL,
    `description` varchar(2000) DEFAULT NULL,
    `sort_order` int NOT NULL DEFAULT 0,
    `template_id` bigint DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_learning_unit_stage_sort` (`stage_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Learning units within a stage';

CREATE TABLE `lab_learning_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `roadmap_id` bigint NOT NULL,
    `user_id` bigint NOT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'NOT_STARTED',
    `started_at` datetime DEFAULT NULL,
    `completed_at` datetime DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_learning_record_roadmap_user` (`roadmap_id`, `user_id`),
    KEY `idx_learning_record_user_status_updated` (`user_id`, `status`, `update_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Member enrollment and roadmap progress state';

CREATE TABLE `lab_learning_unit_completion` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `unit_id` bigint NOT NULL,
    `user_id` bigint NOT NULL,
    `completed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_learning_completion_unit_user` (`unit_id`, `user_id`),
    KEY `idx_learning_completion_user_unit` (`user_id`, `unit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-user learning unit completion facts';

INSERT INTO `lab_learning_roadmap`
    (`title`, `description`, `status`, `difficulty`, `estimated_hours`, `sort_order`, `created_by`)
SELECT
    '嵌入式硬件入门',
    '从零开始学习嵌入式硬件开发',
    'PUBLISHED',
    'BEGINNER',
    28,
    0,
    MIN(ur.`user_id`)
FROM `sys_user_role` ur
INNER JOIN `sys_role` r ON r.`id` = ur.`role_id`
INNER JOIN `sys_user` u ON u.`id` = ur.`user_id` AND u.`status` = 1
WHERE r.`role_key` = 'SYSTEM_ADMIN';

INSERT INTO `lab_learning_stage` (`roadmap_id`, `name`, `description`, `sort_order`)
SELECT r.`id`, seed.`name`, seed.`description`, seed.`sort_order`
FROM `lab_learning_roadmap` r
CROSS JOIN (
    SELECT '实验室安全' AS `name`, '认识实验室规范并建立安全操作意识' AS `description`, 10 AS `sort_order`
    UNION ALL SELECT '电路与焊接', '掌握基础电路知识和焊接操作', 20
    UNION ALL SELECT 'STM32/ESP32', '认识常用微控制器和基本开发流程', 30
    UNION ALL SELECT '传感器', '完成常见传感器的连接与数据读取', 40
    UNION ALL SELECT 'MQTT', '理解设备消息上报和主题设计', 50
    UNION ALL SELECT '原理图/PCB', '完成基础原理图和 PCB 设计练习', 60
    UNION ALL SELECT '联网硬件', '串联采集、通信和平台展示形成闭环', 70
) seed
WHERE r.`title` = '嵌入式硬件入门';

INSERT INTO `lab_learning_unit` (`stage_id`, `title`, `description`, `sort_order`)
SELECT
    s.`id`,
    CASE s.`name`
        WHEN '实验室安全' THEN '完成实验室安全入门'
        WHEN '电路与焊接' THEN '焊接一个基础练习电路'
        WHEN 'STM32/ESP32' THEN '点亮开发板并完成串口输出'
        WHEN '传感器' THEN '读取一个传感器的数据'
        WHEN 'MQTT' THEN '向测试主题发布遥测消息'
        WHEN '原理图/PCB' THEN '绘制并检查一张基础原理图'
        WHEN '联网硬件' THEN '完成端到端联网硬件演示'
    END,
    CASE s.`name`
        WHEN '实验室安全' THEN '阅读规范并通过导师确认后再操作设备。'
        WHEN '电路与焊接' THEN '完成元器件识别、焊接和通断检查。'
        WHEN 'STM32/ESP32' THEN '建立工程、烧录程序并观察串口日志。'
        WHEN '传感器' THEN '连接传感器，读取并解释至少一种测量值。'
        WHEN 'MQTT' THEN '使用约定 Topic 发布一条结构化遥测消息。'
        WHEN '原理图/PCB' THEN '完成原理图规则检查并记录设计说明。'
        WHEN '联网硬件' THEN '将采集数据上报平台并验证页面展示。'
    END,
    10
FROM `lab_learning_stage` s
INNER JOIN `lab_learning_roadmap` r ON r.`id` = s.`roadmap_id`
WHERE r.`title` = '嵌入式硬件入门';
