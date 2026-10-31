CREATE TABLE `lab_component_category` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `code` varchar(40) NOT NULL,
    `name` varchar(80) NOT NULL,
    `normalized_name` varchar(80) NOT NULL,
    `parent_id` bigint DEFAULT NULL,
    `parent_scope_id` bigint NOT NULL DEFAULT 0,
    `level` int NOT NULL,
    `path` varchar(255) NOT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
    `sort_order` int NOT NULL DEFAULT 0,
    `version` int NOT NULL DEFAULT 1,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by` bigint NOT NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_component_category_code` (`code`),
    UNIQUE KEY `uk_component_category_parent_name` (`parent_scope_id`, `normalized_name`),
    KEY `idx_component_category_parent_status` (`parent_id`, `status`, `sort_order`, `id`),
    KEY `idx_component_category_path` (`path`),
    CONSTRAINT `ck_component_category_parent_scope` CHECK (
        (`parent_id` IS NULL AND `parent_scope_id` = 0 AND `level` = 1)
        OR (`parent_id` IS NOT NULL AND `parent_scope_id` = `parent_id` AND `level` BETWEEN 2 AND 3)
    ),
    CONSTRAINT `ck_component_category_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT `ck_component_category_sort_order` CHECK (`sort_order` BETWEEN 0 AND 9999),
    CONSTRAINT `ck_component_category_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Three-level component category tree';

CREATE TABLE `lab_component_manufacturer` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `code` varchar(40) NOT NULL,
    `name` varchar(120) NOT NULL,
    `normalized_name` varchar(120) NOT NULL,
    `website` varchar(2048) DEFAULT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
    `version` int NOT NULL DEFAULT 1,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by` bigint NOT NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_component_manufacturer_code` (`code`),
    UNIQUE KEY `uk_component_manufacturer_name` (`normalized_name`),
    KEY `idx_component_mfr_status_name` (`status`, `name`, `id`),
    CONSTRAINT `ck_component_manufacturer_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT `ck_component_manufacturer_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reusable component manufacturers';

CREATE TABLE `lab_component` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `catalog_code` varchar(12) DEFAULT NULL,
    `name` varchar(120) NOT NULL,
    `manufacturer_id` bigint NOT NULL,
    `mpn` varchar(120) NOT NULL,
    `normalized_mpn` varchar(120) NOT NULL,
    `category_id` bigint NOT NULL,
    `package_type` varchar(80) NOT NULL,
    `base_unit` varchar(16) NOT NULL,
    `description` varchar(2000) DEFAULT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
    `current_spec_snapshot_id` bigint DEFAULT NULL,
    `spec_version` int NOT NULL DEFAULT 0,
    `primary_media_id` bigint DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by` bigint NOT NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_component_catalog_code` (`catalog_code`),
    UNIQUE KEY `uk_component_manufacturer_mpn` (`manufacturer_id`, `normalized_mpn`),
    KEY `idx_component_category_status` (`category_id`, `status`, `update_time`, `id`),
    KEY `idx_component_by_mfr_status` (`manufacturer_id`, `status`, `update_time`, `id`),
    KEY `idx_component_status_name` (`status`, `name`, `id`),
    CONSTRAINT `ck_component_base_unit` CHECK (`base_unit` IN ('PCS', 'METER', 'GRAM', 'MILLILITER', 'SET')),
    CONSTRAINT `ck_component_status` CHECK (`status` IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT `ck_component_spec_pointer` CHECK (
        (`current_spec_snapshot_id` IS NULL AND `spec_version` = 0)
        OR (`current_spec_snapshot_id` IS NOT NULL AND `spec_version` >= 1)
    ),
    CONSTRAINT `ck_component_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Component catalog models with immutable specification pointer';

CREATE TABLE `lab_component_spec_candidate` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `component_id` bigint NOT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'PENDING',
    `source_type` varchar(40) NOT NULL,
    `source_url` varchar(2048) DEFAULT NULL,
    `source_title` varchar(200) DEFAULT NULL,
    `captured_at` datetime DEFAULT NULL,
    `raw_specs_json` mediumtext NOT NULL,
    `normalized_proposal_json` mediumtext NOT NULL,
    `submitted_by` bigint NOT NULL,
    `submitted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `decided_by` bigint DEFAULT NULL,
    `decided_at` datetime DEFAULT NULL,
    `decision_note` varchar(1000) DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    KEY `idx_component_candidate_status` (`component_id`, `status`, `submitted_at`, `id`),
    KEY `idx_component_candidate_source` (`source_type`, `captured_at`, `id`),
    CONSTRAINT `ck_component_candidate_status` CHECK (`status` IN ('PENDING', 'CONFIRMED', 'REJECTED', 'SUPERSEDED')),
    CONSTRAINT `ck_component_candidate_source_type` CHECK (`source_type` IN (
        'MANUFACTURER_WEBSITE', 'MANUFACTURER_DATASHEET', 'AUTHORIZED_DISTRIBUTOR',
        'OTHER_PUBLIC_URL', 'MANUAL'
    )),
    CONSTRAINT `ck_component_candidate_source_fields` CHECK (
        (`source_type` = 'MANUAL' AND `source_url` IS NULL)
        OR (`source_type` <> 'MANUAL' AND `source_url` IS NOT NULL AND `captured_at` IS NOT NULL)
    ),
    CONSTRAINT `ck_component_candidate_decision` CHECK (
        (`status` = 'PENDING' AND `decided_by` IS NULL AND `decided_at` IS NULL)
        OR (`status` <> 'PENDING' AND `decided_by` IS NOT NULL AND `decided_at` IS NOT NULL)
    ),
    CONSTRAINT `ck_component_candidate_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Human-reviewed external specification candidates';

CREATE TABLE `lab_component_spec_snapshot` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `component_id` bigint NOT NULL,
    `snapshot_version` int NOT NULL,
    `source_type` varchar(40) NOT NULL,
    `source_url` varchar(2048) DEFAULT NULL,
    `source_title` varchar(200) DEFAULT NULL,
    `captured_at` datetime DEFAULT NULL,
    `candidate_id` bigint DEFAULT NULL,
    `source_note` varchar(1000) DEFAULT NULL,
    `raw_specs_json` mediumtext NOT NULL,
    `specifications_json` mediumtext NOT NULL,
    `change_summary` varchar(500) NOT NULL,
    `checksum` char(64) NOT NULL,
    `confirmed_by` bigint NOT NULL,
    `confirmed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_component_snapshot_version` (`component_id`, `snapshot_version`),
    KEY `idx_component_snapshot_candidate` (`candidate_id`, `id`),
    KEY `idx_component_snapshot_time` (`component_id`, `confirmed_at`, `id`),
    CONSTRAINT `ck_component_snapshot_source_type` CHECK (`source_type` IN (
        'MANUFACTURER_WEBSITE', 'MANUFACTURER_DATASHEET', 'AUTHORIZED_DISTRIBUTOR',
        'OTHER_PUBLIC_URL', 'MANUAL'
    )),
    CONSTRAINT `ck_component_snapshot_source_fields` CHECK (
        (`source_type` = 'MANUAL' AND `source_url` IS NULL AND `source_note` IS NOT NULL)
        OR (`source_type` <> 'MANUAL' AND `source_url` IS NOT NULL AND `captured_at` IS NOT NULL)
    ),
    CONSTRAINT `ck_component_snapshot_version` CHECK (`snapshot_version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only immutable component specification snapshots';

CREATE TABLE `lab_media_object` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `owner_type` varchar(32) NOT NULL,
    `owner_id` bigint NOT NULL,
    `role` varchar(16) NOT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'READY',
    `original_file_name` varchar(255) NOT NULL,
    `content_type` varchar(32) NOT NULL,
    `size_bytes` bigint NOT NULL,
    `width` int NOT NULL,
    `height` int NOT NULL,
    `sha256` char(64) NOT NULL,
    `original_object_key` varchar(500) NOT NULL,
    `display_object_key` varchar(500) NOT NULL,
    `sort_order` int NOT NULL DEFAULT 0,
    `ready_dedup_slot` tinyint DEFAULT 1,
    `ready_primary_slot` tinyint DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    `uploaded_by` bigint NOT NULL,
    `uploaded_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted_by` bigint DEFAULT NULL,
    `deleted_at` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_media_ready_owner_sha` (`owner_type`, `owner_id`, `sha256`, `ready_dedup_slot`),
    UNIQUE KEY `uk_media_ready_primary` (`owner_type`, `owner_id`, `ready_primary_slot`),
    UNIQUE KEY `uk_media_original_object_key` (`original_object_key`),
    UNIQUE KEY `uk_media_display_object_key` (`display_object_key`),
    KEY `idx_media_owner_status_order` (`owner_type`, `owner_id`, `status`, `sort_order`, `id`),
    KEY `idx_media_owner_role` (`owner_type`, `owner_id`, `role`, `status`, `id`),
    CONSTRAINT `ck_media_owner_type` CHECK (`owner_type` = 'COMPONENT'),
    CONSTRAINT `ck_media_role` CHECK (`role` IN ('PRIMARY', 'GALLERY')),
    CONSTRAINT `ck_media_status` CHECK (`status` IN ('READY', 'DELETED', 'FAILED')),
    CONSTRAINT `ck_media_content_type` CHECK (`content_type` IN ('image/jpeg', 'image/png')),
    CONSTRAINT `ck_media_dimensions` CHECK (`size_bytes` > 0 AND `width` BETWEEN 1 AND 4096 AND `height` BETWEEN 1 AND 4096),
    CONSTRAINT `ck_media_sort_order` CHECK (`sort_order` BETWEEN 0 AND 9999),
    CONSTRAINT `ck_media_ready_dedup_slot` CHECK (
        (`status` = 'READY' AND `ready_dedup_slot` = 1)
        OR (`status` <> 'READY' AND `ready_dedup_slot` IS NULL)
    ),
    CONSTRAINT `ck_media_ready_primary_slot` CHECK (
        (`status` = 'READY' AND `role` = 'PRIMARY' AND `ready_primary_slot` = 1)
        OR ((`status` <> 'READY' OR `role` <> 'PRIMARY') AND `ready_primary_slot` IS NULL)
    ),
    CONSTRAINT `ck_media_delete_audit` CHECK (
        (`status` = 'DELETED' AND `deleted_by` IS NOT NULL AND `deleted_at` IS NOT NULL)
        OR (`status` <> 'DELETED' AND `deleted_by` IS NULL AND `deleted_at` IS NULL)
    ),
    CONSTRAINT `ck_media_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Provider-neutral media identities and internal storage references';

CREATE TABLE `lab_component_catalog_audit_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `request_id` varchar(100) NOT NULL,
    `actor_id` bigint NOT NULL,
    `action` varchar(100) NOT NULL,
    `target_type` varchar(50) NOT NULL,
    `target_id` bigint NOT NULL,
    `previous_status` varchar(32) DEFAULT NULL,
    `new_status` varchar(32) DEFAULT NULL,
    `reason` varchar(500) DEFAULT NULL,
    `details_json` text DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_component_audit_request_action` (`request_id`, `action`, `target_type`, `target_id`),
    KEY `idx_component_audit_target_time` (`target_type`, `target_id`, `create_time`, `id`),
    KEY `idx_component_audit_actor_time` (`actor_id`, `create_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only component catalog and media audit events';
