CREATE TABLE `lab_experiment_template` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(200) NOT NULL,
    `description` varchar(500) DEFAULT NULL,
    `scope` varchar(20) NOT NULL,
    `project_id` bigint DEFAULT NULL,
    `category` varchar(50) DEFAULT NULL,
    `latest_version_id` bigint DEFAULT NULL,
    `latest_version` varchar(20) DEFAULT NULL,
    `published_version_count` int NOT NULL DEFAULT 0,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_experiment_template_scope_id` (`scope`, `id`),
    KEY `idx_experiment_template_project_id` (`project_id`, `id`),
    CONSTRAINT `ck_experiment_template_scope_project` CHECK (
        (`scope` = 'SYSTEM' AND `project_id` IS NULL)
        OR (`scope` = 'PROJECT' AND `project_id` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Experiment template metadata';

CREATE TABLE `lab_experiment_template_version` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `template_id` bigint NOT NULL,
    `version` varchar(20) NOT NULL,
    `version_code` int DEFAULT NULL,
    `status` varchar(20) NOT NULL,
    `draft_slot` tinyint DEFAULT NULL,
    `schema_json` text NOT NULL,
    `field_count` int NOT NULL DEFAULT 0,
    `changelog` varchar(500) DEFAULT NULL,
    `published_by` bigint DEFAULT NULL,
    `published_at` datetime DEFAULT NULL,
    `deprecated_reason` varchar(500) DEFAULT NULL,
    `deprecated_by` bigint DEFAULT NULL,
    `deprecated_at` datetime DEFAULT NULL,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_version` (`template_id`, `version`),
    UNIQUE KEY `uk_template_single_draft` (`template_id`, `draft_slot`),
    KEY `idx_template_version_status` (`template_id`, `status`),
    KEY `idx_template_version_code` (`template_id`, `version_code`),
    CONSTRAINT `ck_template_version_status` CHECK (`status` IN ('DRAFT', 'PUBLISHED', 'DEPRECATED')),
    CONSTRAINT `ck_template_version_draft_slot` CHECK (
        (`status` = 'DRAFT' AND `draft_slot` = 1 AND `version_code` IS NULL)
        OR (`status` IN ('PUBLISHED', 'DEPRECATED') AND `draft_slot` IS NULL AND `version_code` IS NOT NULL)
    ),
    CONSTRAINT `ck_template_version_field_count` CHECK (`field_count` BETWEEN 0 AND 50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable experiment template versions';

ALTER TABLE `lab_learning_unit`
    ADD COLUMN `template_version_id` bigint DEFAULT NULL AFTER `template_id`;

CREATE INDEX `idx_learning_unit_template_version`
    ON `lab_learning_unit` (`template_version_id`);
