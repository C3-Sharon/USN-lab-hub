-- W46 additive migration: inventory issuance, reservation and controlled movement.
-- Reuses lab_request_idempotency and lab_procurement_audit_log from V11/V13.

CREATE TABLE `lab_inventory_issue_policy_version` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `policy_version` int NOT NULL,
    `currency` varchar(8) NOT NULL DEFAULT 'CNY',
    `direct_amount_threshold` decimal(19,2) NOT NULL,
    `valuable_unit_cost_threshold` decimal(19,4) NOT NULL,
    `default_direct_quantity_limit` decimal(19,3) NOT NULL,
    `low_stock_ratio` decimal(5,2) NOT NULL,
    `reservation_ttl_hours` int NOT NULL,
    `effective_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_issue_policy_version` (`policy_version`),
    CONSTRAINT `ck_inventory_issue_policy_currency` CHECK (`currency` = 'CNY'),
    CONSTRAINT `ck_inventory_issue_policy_thresholds` CHECK (
        `direct_amount_threshold` > 0 AND `valuable_unit_cost_threshold` > 0
        AND `default_direct_quantity_limit` > 0
        AND `low_stock_ratio` BETWEEN 0 AND 100
        AND `reservation_ttl_hours` BETWEEN 1 AND 168
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable inventory issuance approval policies';

CREATE TABLE `lab_inventory_issue_policy_forced_category` (
    `policy_id` bigint NOT NULL,
    `category_id` bigint NOT NULL,
    PRIMARY KEY (`policy_id`, `category_id`),
    KEY `idx_inventory_issue_policy_category` (`category_id`, `policy_id`),
    CONSTRAINT `fk_inventory_issue_policy_category_policy`
        FOREIGN KEY (`policy_id`) REFERENCES `lab_inventory_issue_policy_version` (`id`),
    CONSTRAINT `fk_inventory_issue_policy_category_category`
        FOREIGN KEY (`category_id`) REFERENCES `lab_component_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Categories forcing manual issuance approval';

CREATE TABLE `lab_inventory_issue_policy_component_override` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `policy_id` bigint NOT NULL,
    `component_id` bigint NOT NULL,
    `direct_quantity_limit` decimal(19,3) NOT NULL,
    `component_name` varchar(120) NOT NULL,
    `unit_snapshot` varchar(16) NOT NULL,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_issue_policy_component` (`policy_id`, `component_id`),
    CONSTRAINT `fk_inventory_issue_override_policy`
        FOREIGN KEY (`policy_id`) REFERENCES `lab_inventory_issue_policy_version` (`id`),
    CONSTRAINT `fk_inventory_issue_override_component`
        FOREIGN KEY (`component_id`) REFERENCES `lab_component` (`id`),
    CONSTRAINT `ck_inventory_issue_override_limit` CHECK (`direct_quantity_limit` > 0),
    CONSTRAINT `ck_inventory_issue_override_unit` CHECK (
        `unit_snapshot` IN ('PCS', 'METER', 'GRAM', 'MILLILITER', 'SET')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Component-level direct quantity limits per policy version';

CREATE TABLE `lab_inventory_issue_request` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `request_no` varchar(32) NOT NULL,
    `title` varchar(120) NOT NULL,
    `purpose` varchar(500) NOT NULL,
    `requester_id` bigint NOT NULL,
    `requester_name` varchar(50) NOT NULL,
    `project_id` bigint DEFAULT NULL,
    `project_name` varchar(120) DEFAULT NULL,
    `status` varchar(24) NOT NULL DEFAULT 'DRAFT',
    `approval_required` tinyint(1) DEFAULT NULL,
    `approval_reasons_json` text DEFAULT NULL,
    `policy_version` int DEFAULT NULL,
    `estimated_amount` decimal(19,2) DEFAULT NULL,
    `submitted_at` datetime DEFAULT NULL,
    `approved_at` datetime DEFAULT NULL,
    `issued_at` datetime DEFAULT NULL,
    `expires_at` datetime DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by` bigint NOT NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_issue_request_no` (`request_no`),
    KEY `idx_inventory_issue_requester` (`requester_id`, `status`, `update_time`, `id`),
    KEY `idx_inventory_issue_project` (`project_id`, `status`, `update_time`, `id`),
    KEY `idx_inventory_issue_status` (`status`, `update_time`, `id`),
    CONSTRAINT `ck_inventory_issue_request_title` CHECK (CHAR_LENGTH(`title`) BETWEEN 2 AND 120),
    CONSTRAINT `ck_inventory_issue_request_purpose` CHECK (CHAR_LENGTH(`purpose`) BETWEEN 2 AND 500),
    CONSTRAINT `ck_inventory_issue_request_status` CHECK (`status` IN (
        'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'ISSUED', 'CANCELED', 'EXPIRED'
    )),
    CONSTRAINT `ck_inventory_issue_request_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Inventory issuance requests and workflow state';

CREATE TABLE `lab_inventory_issue_line` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `request_id` bigint NOT NULL,
    `line_no` int NOT NULL,
    `component_id` bigint NOT NULL,
    `component_name` varchar(120) DEFAULT NULL,
    `manufacturer_name` varchar(120) DEFAULT NULL,
    `mpn` varchar(120) DEFAULT NULL,
    `unit` varchar(16) NOT NULL,
    `requested_quantity` decimal(19,3) NOT NULL,
    `reserved_quantity` decimal(19,3) NOT NULL DEFAULT 0.000,
    `issued_quantity` decimal(19,3) NOT NULL DEFAULT 0.000,
    `returned_quantity` decimal(19,3) NOT NULL DEFAULT 0.000,
    `estimated_amount` decimal(19,2) DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_issue_line_no` (`request_id`, `line_no`),
    UNIQUE KEY `uk_inventory_issue_line_component` (`request_id`, `component_id`),
    KEY `idx_inventory_issue_line_component` (`component_id`, `request_id`, `id`),
    CONSTRAINT `fk_inventory_issue_line_request`
        FOREIGN KEY (`request_id`) REFERENCES `lab_inventory_issue_request` (`id`),
    CONSTRAINT `fk_inventory_issue_line_component`
        FOREIGN KEY (`component_id`) REFERENCES `lab_component` (`id`),
    CONSTRAINT `ck_inventory_issue_line_no` CHECK (`line_no` >= 1),
    CONSTRAINT `ck_inventory_issue_line_unit` CHECK (
        `unit` IN ('PCS', 'METER', 'GRAM', 'MILLILITER', 'SET')
    ),
    CONSTRAINT `ck_inventory_issue_line_quantities` CHECK (
        `requested_quantity` > 0 AND `reserved_quantity` >= 0
        AND `issued_quantity` >= 0 AND `returned_quantity` >= 0
        AND `returned_quantity` <= `issued_quantity`
    ),
    CONSTRAINT `ck_inventory_issue_line_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Inventory issuance request component lines';

CREATE TABLE `lab_inventory_issue_approval_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `request_id` bigint NOT NULL,
    `decision` varchar(24) NOT NULL,
    `actor_type` varchar(16) NOT NULL,
    `decided_by` bigint DEFAULT NULL,
    `decided_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `policy_version` int NOT NULL,
    `trigger_snapshot_json` text NOT NULL,
    `comment` varchar(500) DEFAULT NULL,
    `request_idempotency_id` varchar(100) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_inventory_issue_approval_request` (`request_id`, `decided_at`, `id`),
    CONSTRAINT `fk_inventory_issue_approval_request`
        FOREIGN KEY (`request_id`) REFERENCES `lab_inventory_issue_request` (`id`),
    CONSTRAINT `ck_inventory_issue_approval_decision` CHECK (
        `decision` IN ('AUTO_APPROVED', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT `ck_inventory_issue_approval_actor` CHECK (
        (`actor_type` = 'SYSTEM' AND `decision` = 'AUTO_APPROVED' AND `decided_by` IS NULL)
        OR (`actor_type` = 'USER' AND `decision` IN ('APPROVED', 'REJECTED') AND `decided_by` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only inventory issuance approval decisions';

CREATE TABLE `lab_inventory_reservation` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `reservation_no` varchar(32) NOT NULL,
    `request_id` bigint NOT NULL,
    `request_line_id` bigint NOT NULL,
    `component_id` bigint NOT NULL,
    `quantity` decimal(19,3) NOT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
    `expires_at` datetime NOT NULL,
    `release_reason` varchar(24) DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_reservation_no` (`reservation_no`),
    UNIQUE KEY `uk_inventory_reservation_request_line` (`request_line_id`),
    KEY `idx_inventory_reservation_component` (`component_id`, `status`, `id`),
    KEY `idx_inventory_reservation_expiry` (`status`, `expires_at`, `id`),
    CONSTRAINT `fk_inventory_reservation_request`
        FOREIGN KEY (`request_id`) REFERENCES `lab_inventory_issue_request` (`id`),
    CONSTRAINT `fk_inventory_reservation_line`
        FOREIGN KEY (`request_line_id`) REFERENCES `lab_inventory_issue_line` (`id`),
    CONSTRAINT `fk_inventory_reservation_component`
        FOREIGN KEY (`component_id`) REFERENCES `lab_component` (`id`),
    CONSTRAINT `ck_inventory_reservation_status` CHECK (
        `status` IN ('ACTIVE', 'CONSUMED', 'RELEASED', 'EXPIRED')
    ),
    CONSTRAINT `ck_inventory_reservation_quantity` CHECK (`quantity` > 0),
    CONSTRAINT `ck_inventory_reservation_release_reason` CHECK (
        (`status` IN ('RELEASED', 'EXPIRED') AND `release_reason` IS NOT NULL)
        OR (`status` IN ('ACTIVE', 'CONSUMED') AND `release_reason` IS NULL)
    ),
    CONSTRAINT `ck_inventory_reservation_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Active inventory reservations per request line';

CREATE TABLE `lab_inventory_reservation_allocation` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `reservation_id` bigint NOT NULL,
    `batch_id` bigint NOT NULL,
    `quantity` decimal(19,3) NOT NULL,
    `unit_cost_snapshot` decimal(19,4) NOT NULL,
    `version` int NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_reservation_batch` (`reservation_id`, `batch_id`),
    CONSTRAINT `fk_inventory_allocation_reservation`
        FOREIGN KEY (`reservation_id`) REFERENCES `lab_inventory_reservation` (`id`),
    CONSTRAINT `fk_inventory_allocation_batch`
        FOREIGN KEY (`batch_id`) REFERENCES `lab_inventory_batch` (`id`),
    CONSTRAINT `ck_inventory_allocation_quantity` CHECK (`quantity` > 0),
    CONSTRAINT `ck_inventory_allocation_cost` CHECK (`unit_cost_snapshot` >= 0),
    CONSTRAINT `ck_inventory_allocation_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Batch allocations backing each reservation';

CREATE TABLE `lab_inventory_fulfillment` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `fulfillment_no` varchar(32) NOT NULL,
    `request_id` bigint NOT NULL,
    `operator_id` bigint NOT NULL,
    `operator_name` varchar(50) NOT NULL,
    `recipient_id` bigint NOT NULL,
    `recipient_name` varchar(50) NOT NULL,
    `issued_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `note` varchar(500) DEFAULT NULL,
    `request_idempotency_id` varchar(100) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_fulfillment_no` (`fulfillment_no`),
    UNIQUE KEY `uk_inventory_fulfillment_request` (`request_id`),
    CONSTRAINT `fk_inventory_fulfillment_request`
        FOREIGN KEY (`request_id`) REFERENCES `lab_inventory_issue_request` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Confirmed physical inventory issuance';

CREATE TABLE `lab_inventory_fulfillment_line` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `fulfillment_id` bigint NOT NULL,
    `request_line_id` bigint NOT NULL,
    `reservation_allocation_id` bigint NOT NULL,
    `batch_id` bigint NOT NULL,
    `quantity` decimal(19,3) NOT NULL,
    `unit_cost_snapshot` decimal(19,4) NOT NULL,
    `movement_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_fulfillment_line` (`fulfillment_id`, `request_line_id`),
    CONSTRAINT `fk_inventory_fulfillment_line_fulfillment`
        FOREIGN KEY (`fulfillment_id`) REFERENCES `lab_inventory_fulfillment` (`id`),
    CONSTRAINT `fk_inventory_fulfillment_line_request_line`
        FOREIGN KEY (`request_line_id`) REFERENCES `lab_inventory_issue_line` (`id`),
    CONSTRAINT `fk_inventory_fulfillment_line_allocation`
        FOREIGN KEY (`reservation_allocation_id`) REFERENCES `lab_inventory_reservation_allocation` (`id`),
    CONSTRAINT `fk_inventory_fulfillment_line_batch`
        FOREIGN KEY (`batch_id`) REFERENCES `lab_inventory_batch` (`id`),
    CONSTRAINT `fk_inventory_fulfillment_line_movement`
        FOREIGN KEY (`movement_id`) REFERENCES `lab_inventory_movement` (`id`),
    CONSTRAINT `ck_inventory_fulfillment_line_quantity` CHECK (`quantity` > 0),
    CONSTRAINT `ck_inventory_fulfillment_line_cost` CHECK (`unit_cost_snapshot` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Issuance lines linking allocation, batch and movement';

CREATE TABLE `lab_inventory_return` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `return_no` varchar(32) NOT NULL,
    `fulfillment_id` bigint NOT NULL,
    `request_id` bigint NOT NULL,
    `status` varchar(24) NOT NULL DEFAULT 'PENDING_CONFIRMATION',
    `reason` varchar(500) NOT NULL,
    `requested_by` bigint NOT NULL,
    `confirmed_by` bigint DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by` bigint NOT NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_return_no` (`return_no`),
    KEY `idx_inventory_return_request` (`request_id`, `create_time`, `id`),
    CONSTRAINT `fk_inventory_return_fulfillment`
        FOREIGN KEY (`fulfillment_id`) REFERENCES `lab_inventory_fulfillment` (`id`),
    CONSTRAINT `fk_inventory_return_request`
        FOREIGN KEY (`request_id`) REFERENCES `lab_inventory_issue_request` (`id`),
    CONSTRAINT `ck_inventory_return_status` CHECK (
        `status` IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'REJECTED', 'CANCELED')
    ),
    CONSTRAINT `ck_inventory_return_reason` CHECK (CHAR_LENGTH(`reason`) BETWEEN 2 AND 500),
    CONSTRAINT `ck_inventory_return_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Controlled returns to the original batch';

CREATE TABLE `lab_inventory_return_line` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `return_id` bigint NOT NULL,
    `fulfillment_line_id` bigint NOT NULL,
    `batch_id` bigint NOT NULL,
    `quantity` decimal(19,3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_return_line` (`return_id`, `fulfillment_line_id`),
    CONSTRAINT `fk_inventory_return_line_return`
        FOREIGN KEY (`return_id`) REFERENCES `lab_inventory_return` (`id`),
    CONSTRAINT `fk_inventory_return_line_fulfillment_line`
        FOREIGN KEY (`fulfillment_line_id`) REFERENCES `lab_inventory_fulfillment_line` (`id`),
    CONSTRAINT `fk_inventory_return_line_batch`
        FOREIGN KEY (`batch_id`) REFERENCES `lab_inventory_batch` (`id`),
    CONSTRAINT `ck_inventory_return_line_quantity` CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Return quantities linked to original issuance lines';

CREATE TABLE `lab_inventory_write_off_command` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `command_no` varchar(32) NOT NULL,
    `type` varchar(16) NOT NULL,
    `component_id` bigint NOT NULL,
    `batch_id` bigint NOT NULL,
    `quantity` decimal(19,3) NOT NULL,
    `reason` varchar(500) NOT NULL,
    `operator_id` bigint NOT NULL,
    `occurred_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `movement_id` bigint NOT NULL,
    `request_id` varchar(100) NOT NULL,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_write_off_no` (`command_no`),
    UNIQUE KEY `uk_inventory_write_off_movement` (`movement_id`),
    KEY `idx_inventory_write_off_component` (`component_id`, `occurred_at`, `id`),
    CONSTRAINT `fk_inventory_write_off_component`
        FOREIGN KEY (`component_id`) REFERENCES `lab_component` (`id`),
    CONSTRAINT `fk_inventory_write_off_batch`
        FOREIGN KEY (`batch_id`) REFERENCES `lab_inventory_batch` (`id`),
    CONSTRAINT `fk_inventory_write_off_movement`
        FOREIGN KEY (`movement_id`) REFERENCES `lab_inventory_movement` (`id`),
    CONSTRAINT `ck_inventory_write_off_type` CHECK (`type` IN ('LOSS', 'SCRAP')),
    CONSTRAINT `ck_inventory_write_off_quantity` CHECK (`quantity` > 0),
    CONSTRAINT `ck_inventory_write_off_reason` CHECK (CHAR_LENGTH(`reason`) BETWEEN 1 AND 500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only loss and scrap write-off commands';

ALTER TABLE `lab_component_inventory_balance`
    ADD COLUMN `reserved_quantity` decimal(19,3) NOT NULL DEFAULT 0.000 AFTER `on_hand_quantity`;

ALTER TABLE `lab_component_inventory_balance`
    DROP CONSTRAINT `ck_inventory_balance_quantity`;

ALTER TABLE `lab_component_inventory_balance`
    ADD CONSTRAINT `ck_inventory_balance_quantity` CHECK (
        `on_hand_quantity` >= 0 AND `reserved_quantity` >= 0
        AND `on_hand_quantity` >= `reserved_quantity`
    );

ALTER TABLE `lab_inventory_batch`
    ADD COLUMN `reserved_quantity` decimal(19,3) NOT NULL DEFAULT 0.000 AFTER `current_quantity`;

ALTER TABLE `lab_inventory_batch`
    DROP CONSTRAINT `ck_inventory_batch_quantities`;

ALTER TABLE `lab_inventory_batch`
    ADD CONSTRAINT `ck_inventory_batch_quantities` CHECK (
        `initial_quantity` > 0 AND `current_quantity` >= 0
        AND `reserved_quantity` >= 0
        AND `current_quantity` >= `reserved_quantity`
        AND `current_quantity` <= `initial_quantity`
    );

ALTER TABLE `lab_inventory_batch`
    ADD KEY `idx_inventory_batch_fefo` (`component_id`, `status`, `expiry_date`, `create_time`, `id`);

ALTER TABLE `lab_inventory_movement`
    ADD COLUMN `reason` varchar(500) DEFAULT NULL AFTER `unit_cost`;

ALTER TABLE `lab_inventory_movement`
    DROP CONSTRAINT `ck_inventory_movement_type`;

ALTER TABLE `lab_inventory_movement`
    DROP CONSTRAINT `ck_inventory_movement_source`;

ALTER TABLE `lab_inventory_movement`
    DROP CONSTRAINT `ck_inventory_movement_quantities`;

ALTER TABLE `lab_inventory_movement`
    ADD CONSTRAINT `ck_inventory_movement_type` CHECK (`movement_type` IN (
        'PROCUREMENT_RECEIPT_IN', 'ISSUE_OUT', 'RETURN_IN', 'LOSS_OUT', 'SCRAP_OUT'
    ));

ALTER TABLE `lab_inventory_movement`
    ADD CONSTRAINT `ck_inventory_movement_source` CHECK (
        (`movement_type` = 'PROCUREMENT_RECEIPT_IN' AND `source_type` = 'RECEIPT_LINE')
        OR (`movement_type` = 'ISSUE_OUT' AND `source_type` = 'FULFILLMENT_LINE')
        OR (`movement_type` = 'RETURN_IN' AND `source_type` = 'RETURN_LINE')
        OR (`movement_type` IN ('LOSS_OUT', 'SCRAP_OUT') AND `source_type` = 'WRITE_OFF_COMMAND')
    );

ALTER TABLE `lab_inventory_movement`
    ADD CONSTRAINT `ck_inventory_movement_quantities` CHECK (
        `balance_before` >= 0 AND `balance_after` >= 0
        AND `balance_after` = `balance_before` + `quantity_delta`
        AND (
            (`movement_type` IN ('PROCUREMENT_RECEIPT_IN', 'RETURN_IN') AND `quantity_delta` > 0)
            OR (`movement_type` IN ('ISSUE_OUT', 'LOSS_OUT', 'SCRAP_OUT') AND `quantity_delta` < 0)
        )
    );

ALTER TABLE `lab_inventory_movement`
    ADD CONSTRAINT `ck_inventory_movement_reason_required` CHECK (
        `movement_type` NOT IN ('LOSS_OUT', 'SCRAP_OUT') OR `reason` IS NOT NULL
    );

ALTER TABLE `lab_inventory_movement`
    ADD CONSTRAINT `ck_inventory_movement_reason_length` CHECK (
        `reason` IS NULL OR CHAR_LENGTH(`reason`) BETWEEN 1 AND 500
    );

ALTER TABLE `lab_inventory_movement`
    DROP INDEX `uk_inventory_movement_source`;

ALTER TABLE `lab_inventory_movement`
    ADD UNIQUE KEY `uk_inventory_movement_source` (
        `movement_type`, `source_type`, `source_id`, `batch_id`
    );
