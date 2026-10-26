ALTER TABLE `lab_project`
    ADD COLUMN `source_experiment_record_id` bigint DEFAULT NULL COMMENT 'Immutable source experiment record';

ALTER TABLE `lab_project`
    ADD COLUMN `source_published_revision_id` bigint DEFAULT NULL COMMENT 'Immutable published revision used for conversion';

CREATE UNIQUE INDEX `uk_project_source_experiment`
    ON `lab_project` (`source_experiment_record_id`);

CREATE TABLE `lab_experiment_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `record_code` varchar(32) NOT NULL,
    `title` varchar(120) NOT NULL,
    `summary` varchar(500) DEFAULT NULL,
    `author_id` bigint NOT NULL,
    `project_id` bigint DEFAULT NULL,
    `learning_unit_id` bigint DEFAULT NULL,
    `template_id` bigint NOT NULL,
    `template_version_id` bigint NOT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
    `current_revision_id` bigint DEFAULT NULL,
    `published_revision_id` bigint DEFAULT NULL,
    `converted_project_id` bigint DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_experiment_record_code` (`record_code`),
    UNIQUE KEY `uk_experiment_converted_project` (`converted_project_id`),
    KEY `idx_experiment_author_status_updated` (`author_id`, `status`, `update_time`, `id`),
    KEY `idx_experiment_project_status_updated` (`project_id`, `status`, `update_time`, `id`),
    KEY `idx_experiment_learning_status` (`learning_unit_id`, `status`, `id`),
    KEY `idx_experiment_template_version` (`template_version_id`, `id`),
    CONSTRAINT `ck_experiment_record_context` CHECK (`project_id` IS NOT NULL OR `learning_unit_id` IS NOT NULL),
    CONSTRAINT `ck_experiment_record_status` CHECK (`status` IN (
        'DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'PUBLISHED', 'REVISING'
    )),
    CONSTRAINT `ck_experiment_record_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Experiment record aggregate';

CREATE TABLE `lab_experiment_revision` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `record_id` bigint NOT NULL,
    `revision_no` int NOT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'EDITABLE',
    `content_json` text NOT NULL,
    `purpose` varchar(1000) DEFAULT NULL,
    `environment` varchar(1000) DEFAULT NULL,
    `steps` text DEFAULT NULL,
    `result_text` text DEFAULT NULL,
    `anomaly` text DEFAULT NULL,
    `conclusion` text DEFAULT NULL,
    `hardware_version_ref` varchar(100) DEFAULT NULL,
    `device_instance_ref` varchar(100) DEFAULT NULL,
    `firmware_version` varchar(100) DEFAULT NULL,
    `bom_version_ref` varchar(100) DEFAULT NULL,
    `collection_snapshot_id` bigint DEFAULT NULL,
    `change_summary` varchar(500) DEFAULT NULL,
    `previous_revision_id` bigint DEFAULT NULL,
    `created_by` bigint NOT NULL,
    `submitted_by` bigint DEFAULT NULL,
    `submitted_at` datetime DEFAULT NULL,
    `approved_by` bigint DEFAULT NULL,
    `approved_at` datetime DEFAULT NULL,
    `published_by` bigint DEFAULT NULL,
    `published_at` datetime DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_experiment_revision_no` (`record_id`, `revision_no`),
    KEY `idx_experiment_revision_status` (`record_id`, `status`, `revision_no`),
    KEY `idx_experiment_revision_snapshot` (`collection_snapshot_id`),
    CONSTRAINT `ck_experiment_revision_status` CHECK (`status` IN (
        'EDITABLE', 'SUBMITTED', 'CHANGES_REQUESTED', 'APPROVED', 'PUBLISHED', 'SUPERSEDED'
    )),
    CONSTRAINT `ck_experiment_revision_no` CHECK (`revision_no` >= 1),
    CONSTRAINT `ck_experiment_revision_version` CHECK (`version` >= 1),
    CONSTRAINT `ck_experiment_revision_change` CHECK (`revision_no` = 1 OR `change_summary` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only experiment content revisions';

CREATE TABLE `lab_experiment_feedback` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `record_id` bigint NOT NULL,
    `revision_id` bigint NOT NULL,
    `feedback_type` varchar(32) NOT NULL,
    `content` varchar(2000) DEFAULT NULL,
    `visibility` varchar(32) NOT NULL DEFAULT 'AUTHOR_AND_REVIEWERS',
    `reviewer_id` bigint NOT NULL,
    `reviewer_name` varchar(100) NOT NULL,
    `request_id` varchar(64) NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_experiment_feedback_request` (`request_id`),
    KEY `idx_experiment_feedback_record_time` (`record_id`, `create_time`, `id`),
    KEY `idx_experiment_feedback_revision` (`revision_id`, `id`),
    CONSTRAINT `ck_experiment_feedback_type` CHECK (`feedback_type` IN ('COMMENT', 'CHANGE_REQUEST', 'APPROVAL')),
    CONSTRAINT `ck_experiment_feedback_visibility` CHECK (`visibility` IN ('AUTHOR_AND_REVIEWERS', 'PROJECT_MEMBERS')),
    CONSTRAINT `ck_experiment_feedback_content` CHECK (
        `feedback_type` = 'APPROVAL' OR (`content` IS NOT NULL AND CHAR_LENGTH(`content`) >= 1)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable mentor feedback events';

CREATE TABLE `lab_collection_plan` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `plan_code` varchar(32) NOT NULL,
    `project_id` bigint NOT NULL,
    `name` varchar(120) NOT NULL,
    `purpose` varchar(1000) NOT NULL,
    `owner_id` bigint NOT NULL,
    `source_type` varchar(16) NOT NULL,
    `metric_definitions_json` text NOT NULL,
    `linked_experiment_record_id` bigint DEFAULT NULL,
    `retention_note` varchar(500) DEFAULT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
    `version` int NOT NULL DEFAULT 1,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_collection_plan_code` (`plan_code`),
    KEY `idx_collection_plan_project_status` (`project_id`, `status`, `update_time`, `id`),
    KEY `idx_collection_plan_owner_status` (`owner_id`, `status`, `id`),
    CONSTRAINT `ck_collection_plan_source` CHECK (`source_type` IN ('FILE', 'MANUAL')),
    CONSTRAINT `ck_collection_plan_status` CHECK (`status` IN ('DRAFT', 'READY', 'ARCHIVED', 'CANCELED')),
    CONSTRAINT `ck_collection_plan_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Project collection plans for file and manual sources';

CREATE TABLE `lab_collection_batch` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `batch_code` varchar(40) NOT NULL,
    `plan_id` bigint NOT NULL,
    `project_id` bigint NOT NULL,
    `source_type` varchar(16) NOT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'CREATED',
    `operator_id` bigint NOT NULL,
    `started_at` datetime DEFAULT NULL,
    `completed_at` datetime DEFAULT NULL,
    `record_count` bigint NOT NULL DEFAULT 0,
    `rejected_count` bigint NOT NULL DEFAULT 0,
    `observed_from` datetime DEFAULT NULL,
    `observed_to` datetime DEFAULT NULL,
    `source_revision_id` bigint DEFAULT NULL,
    `completion_reason` varchar(500) DEFAULT NULL,
    `checksum` char(64) DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_collection_batch_code` (`batch_code`),
    KEY `idx_collection_batch_plan_time` (`plan_id`, `create_time`, `id`),
    KEY `idx_collection_batch_project_status` (`project_id`, `status`, `id`),
    KEY `idx_collection_batch_operator_status` (`operator_id`, `status`, `id`),
    CONSTRAINT `ck_collection_batch_source` CHECK (`source_type` IN ('FILE', 'MANUAL')),
    CONSTRAINT `ck_collection_batch_status` CHECK (`status` IN (
        'CREATED', 'PROCESSING', 'REVIEW_REQUIRED', 'COMPLETED', 'FAILED', 'CANCELED', 'ARCHIVED'
    )),
    CONSTRAINT `ck_collection_batch_counts` CHECK (`record_count` >= 0 AND `rejected_count` >= 0),
    CONSTRAINT `ck_collection_batch_version` CHECK (`version` >= 1),
    CONSTRAINT `ck_collection_batch_source_revision` CHECK (
        (`source_type` = 'FILE') OR (`source_type` = 'MANUAL' AND `source_revision_id` IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='One execution of a collection plan';

CREATE TABLE `lab_data_import` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `batch_id` bigint NOT NULL,
    `object_key` varchar(500) NOT NULL,
    `original_file_name` varchar(255) NOT NULL,
    `mime_type` varchar(100) NOT NULL,
    `file_size` bigint NOT NULL,
    `file_sha256` char(64) NOT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'UPLOADED',
    `current_revision_id` bigint DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    `uploaded_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_data_import_batch_sha` (`batch_id`, `file_sha256`),
    UNIQUE KEY `uk_data_import_object_key` (`object_key`),
    KEY `idx_data_import_batch_status` (`batch_id`, `status`, `id`),
    CONSTRAINT `ck_data_import_status` CHECK (`status` IN ('UPLOADED', 'MAPPING_REQUIRED', 'CONFIRMED', 'FAILED')),
    CONSTRAINT `ck_data_import_size` CHECK (`file_size` > 0),
    CONSTRAINT `ck_data_import_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable uploaded file aggregate';

CREATE TABLE `lab_data_import_revision` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `data_import_id` bigint NOT NULL,
    `import_revision_no` int NOT NULL,
    `mapping_json` text NOT NULL,
    `mapping_digest` char(64) NOT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'MAPPING_REQUIRED',
    `record_count` bigint NOT NULL DEFAULT 0,
    `rejected_count` bigint NOT NULL DEFAULT 0,
    `previous_revision_id` bigint DEFAULT NULL,
    `created_by` bigint NOT NULL,
    `confirmed_by` bigint DEFAULT NULL,
    `confirmed_at` datetime DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_data_import_revision_no` (`data_import_id`, `import_revision_no`),
    UNIQUE KEY `uk_data_import_mapping_digest` (`data_import_id`, `mapping_digest`),
    KEY `idx_data_import_revision_status` (`data_import_id`, `status`, `import_revision_no`),
    CONSTRAINT `ck_data_import_revision_status` CHECK (`status` IN (
        'MAPPING_REQUIRED', 'CONFIRMED', 'SUPERSEDED', 'FAILED'
    )),
    CONSTRAINT `ck_data_import_revision_no` CHECK (`import_revision_no` >= 1),
    CONSTRAINT `ck_data_import_revision_counts` CHECK (`record_count` >= 0 AND `rejected_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only CSV mapping and parsing revisions';

CREATE TABLE `lab_manual_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `batch_id` bigint NOT NULL,
    `metric_code` varchar(64) NOT NULL,
    `current_revision_id` bigint DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_manual_record_batch_metric` (`batch_id`, `metric_code`, `id`),
    CONSTRAINT `ck_manual_record_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Manual observation aggregate';

CREATE TABLE `lab_manual_record_revision` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `manual_record_id` bigint NOT NULL,
    `revision_no` int NOT NULL,
    `value_type` varchar(16) NOT NULL,
    `value_text` varchar(2000) NOT NULL,
    `unit` varchar(50) NOT NULL,
    `observed_at` datetime NOT NULL,
    `method` varchar(500) NOT NULL,
    `instrument` varchar(500) NOT NULL,
    `note` varchar(1000) DEFAULT NULL,
    `change_reason` varchar(500) DEFAULT NULL,
    `previous_revision_id` bigint DEFAULT NULL,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_manual_record_revision_no` (`manual_record_id`, `revision_no`),
    KEY `idx_manual_revision_observed` (`manual_record_id`, `observed_at`, `id`),
    CONSTRAINT `ck_manual_revision_value_type` CHECK (`value_type` IN ('DECIMAL', 'INTEGER', 'BOOLEAN', 'STRING')),
    CONSTRAINT `ck_manual_revision_no` CHECK (`revision_no` >= 1),
    CONSTRAINT `ck_manual_revision_reason` CHECK (`revision_no` = 1 OR `change_reason` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only manual observation revisions';

CREATE TABLE `lab_collection_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `batch_id` bigint NOT NULL,
    `data_import_revision_id` bigint DEFAULT NULL,
    `manual_record_revision_id` bigint DEFAULT NULL,
    `source_type` varchar(16) NOT NULL,
    `metric_code` varchar(64) NOT NULL,
    `value_type` varchar(16) NOT NULL,
    `value_text` varchar(2000) NOT NULL,
    `unit` varchar(50) NOT NULL,
    `observed_at` datetime NOT NULL,
    `recorded_by` bigint DEFAULT NULL,
    `method` varchar(500) DEFAULT NULL,
    `instrument` varchar(500) DEFAULT NULL,
    `raw_row_no` bigint DEFAULT NULL,
    `quality_status` varchar(16) NOT NULL DEFAULT 'GOOD',
    `parser_version` varchar(50) DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_collection_record_batch_cursor` (`batch_id`, `observed_at`, `id`),
    KEY `idx_collection_record_import_revision` (`data_import_revision_id`, `id`),
    KEY `idx_collection_record_manual_revision` (`manual_record_revision_id`, `id`),
    CONSTRAINT `ck_collection_record_source` CHECK (
        (`source_type` = 'FILE' AND `data_import_revision_id` IS NOT NULL AND `manual_record_revision_id` IS NULL)
        OR (`source_type` = 'MANUAL' AND `data_import_revision_id` IS NULL AND `manual_record_revision_id` IS NOT NULL)
    ),
    CONSTRAINT `ck_collection_record_value_type` CHECK (`value_type` IN ('DECIMAL', 'INTEGER', 'BOOLEAN', 'STRING')),
    CONSTRAINT `ck_collection_record_quality` CHECK (`quality_status` IN ('GOOD', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Normalized file and manual collection records';

CREATE TABLE `lab_collection_snapshot` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `snapshot_code` varchar(40) NOT NULL,
    `batch_id` bigint NOT NULL,
    `snapshot_type` varchar(32) NOT NULL DEFAULT 'FULL_BATCH',
    `source_revision_id` bigint DEFAULT NULL,
    `record_count` bigint NOT NULL,
    `rejected_count` bigint NOT NULL,
    `observed_from` datetime DEFAULT NULL,
    `observed_to` datetime DEFAULT NULL,
    `batch_checksum` char(64) NOT NULL,
    `mapping_digest` char(64) DEFAULT NULL,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_collection_snapshot_code` (`snapshot_code`),
    UNIQUE KEY `uk_collection_snapshot_batch_type` (`batch_id`, `snapshot_type`),
    KEY `idx_collection_snapshot_source_revision` (`source_revision_id`, `id`),
    CONSTRAINT `ck_collection_snapshot_type` CHECK (`snapshot_type` = 'FULL_BATCH'),
    CONSTRAINT `ck_collection_snapshot_counts` CHECK (`record_count` >= 0 AND `rejected_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable completed-batch snapshots';

CREATE TABLE `lab_snapshot_manual_revision` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `snapshot_id` bigint NOT NULL,
    `manual_record_id` bigint NOT NULL,
    `manual_revision_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_snapshot_manual_record` (`snapshot_id`, `manual_record_id`),
    KEY `idx_snapshot_manual_revision` (`manual_revision_id`, `snapshot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Frozen effective manual revisions in a snapshot';

CREATE TABLE `lab_request_idempotency` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `actor_id` bigint NOT NULL,
    `operation` varchar(120) NOT NULL,
    `idempotency_key` varchar(100) NOT NULL,
    `request_digest` char(64) NOT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'PROCESSING',
    `response_code` int DEFAULT NULL,
    `response_json` text DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_idempotency` (`actor_id`, `operation`, `idempotency_key`),
    KEY `idx_request_idempotency_updated` (`status`, `update_time`, `id`),
    CONSTRAINT `ck_request_idempotency_status` CHECK (`status` IN ('PROCESSING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Committed API idempotency results';

CREATE TABLE `lab_experiment_audit_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `request_id` varchar(64) NOT NULL,
    `actor_id` bigint NOT NULL,
    `action` varchar(100) NOT NULL,
    `target_type` varchar(50) NOT NULL,
    `target_id` bigint NOT NULL,
    `project_id` bigint DEFAULT NULL,
    `previous_status` varchar(32) DEFAULT NULL,
    `new_status` varchar(32) DEFAULT NULL,
    `reason` varchar(500) DEFAULT NULL,
    `details_json` text DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_experiment_audit_request_action` (`request_id`, `action`, `target_type`, `target_id`),
    KEY `idx_experiment_audit_target_time` (`target_type`, `target_id`, `create_time`, `id`),
    KEY `idx_experiment_audit_project_time` (`project_id`, `create_time`, `id`),
    KEY `idx_experiment_audit_actor_time` (`actor_id`, `create_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only experiment and collection audit events';
