ALTER TABLE `lab_media_object`
    DROP CONSTRAINT `ck_media_owner_type`;

ALTER TABLE `lab_media_object`
    ADD CONSTRAINT `ck_media_owner_type`
        CHECK (`owner_type` IN ('COMPONENT', 'PROCUREMENT_ORDER', 'PROCUREMENT_RECEIPT'));

ALTER TABLE `lab_request_idempotency`
    MODIFY COLUMN `idempotency_key` varchar(128) NOT NULL;

CREATE TABLE `lab_procurement_policy_version` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `policy_version` int NOT NULL,
    `currency` varchar(8) NOT NULL DEFAULT 'CNY',
    `approval_threshold` decimal(19,2) NOT NULL,
    `valuable_unit_price_threshold` decimal(19,4) NOT NULL,
    `effective_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_policy_version` (`policy_version`),
    CONSTRAINT `ck_procurement_policy_currency` CHECK (`currency` = 'CNY'),
    CONSTRAINT `ck_procurement_policy_thresholds` CHECK (
        `approval_threshold` > 0 AND `valuable_unit_price_threshold` > 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Versioned procurement approval policies';

CREATE TABLE `lab_procurement_policy_forced_category` (
    `policy_id` bigint NOT NULL,
    `category_id` bigint NOT NULL,
    PRIMARY KEY (`policy_id`, `category_id`),
    KEY `idx_procurement_policy_category` (`category_id`, `policy_id`),
    CONSTRAINT `fk_procurement_policy_category_policy`
        FOREIGN KEY (`policy_id`) REFERENCES `lab_procurement_policy_version` (`id`),
    CONSTRAINT `fk_procurement_policy_category_category`
        FOREIGN KEY (`category_id`) REFERENCES `lab_component_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Categories forcing manual procurement approval';

CREATE TABLE `lab_procurement_order` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `purchase_no` varchar(32) NOT NULL,
    `title` varchar(120) NOT NULL,
    `requester_id` bigint NOT NULL,
    `requester_name` varchar(50) NOT NULL,
    `buyer_id` bigint DEFAULT NULL,
    `buyer_name` varchar(50) DEFAULT NULL,
    `project_id` bigint DEFAULT NULL,
    `project_name` varchar(120) DEFAULT NULL,
    `funding_source` varchar(120) DEFAULT NULL,
    `supplier_name` varchar(120) DEFAULT NULL,
    `platform_type` varchar(16) DEFAULT NULL,
    `platform_order_no` varchar(120) DEFAULT NULL,
    `normalized_platform_order_no` varchar(120) DEFAULT NULL,
    `currency` varchar(8) NOT NULL DEFAULT 'CNY',
    `item_subtotal` decimal(19,2) NOT NULL DEFAULT 0.00,
    `shipping_amount` decimal(19,2) NOT NULL DEFAULT 0.00,
    `discount_amount` decimal(19,2) NOT NULL DEFAULT 0.00,
    `payable_amount` decimal(19,2) NOT NULL DEFAULT 0.00,
    `actual_paid_amount` decimal(19,2) DEFAULT NULL,
    `payment_difference_note` varchar(500) DEFAULT NULL,
    `status` varchar(24) NOT NULL DEFAULT 'DRAFT',
    `approval_required` tinyint(1) DEFAULT NULL,
    `approval_policy_version` int DEFAULT NULL,
    `approval_reasons_json` text DEFAULT NULL,
    `approval_trigger_snapshot_json` text DEFAULT NULL,
    `ordered_at` datetime DEFAULT NULL,
    `completed_at` datetime DEFAULT NULL,
    `close_reason` varchar(500) DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by` bigint NOT NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_purchase_no` (`purchase_no`),
    UNIQUE KEY `uk_procurement_platform_order` (`platform_type`, `normalized_platform_order_no`),
    KEY `idx_procurement_requester_status` (`requester_id`, `status`, `update_time`, `id`),
    KEY `idx_procurement_buyer_status` (`buyer_id`, `status`, `update_time`, `id`),
    KEY `idx_procurement_project_status` (`project_id`, `status`, `update_time`, `id`),
    KEY `idx_procurement_status_time` (`status`, `update_time`, `id`),
    CONSTRAINT `ck_procurement_order_title` CHECK (CHAR_LENGTH(`title`) BETWEEN 2 AND 120),
    CONSTRAINT `ck_procurement_order_platform` CHECK (
        `platform_type` IS NULL OR `platform_type` IN ('TAOBAO', 'JD', 'PINDUODUO', '1688', 'OFFLINE', 'OTHER')
    ),
    CONSTRAINT `ck_procurement_order_currency` CHECK (`currency` = 'CNY'),
    CONSTRAINT `ck_procurement_order_amounts` CHECK (
        `item_subtotal` >= 0 AND `shipping_amount` >= 0 AND
        `discount_amount` >= 0 AND `payable_amount` >= 0 AND
        (`actual_paid_amount` IS NULL OR `actual_paid_amount` >= 0)
    ),
    CONSTRAINT `ck_procurement_order_status` CHECK (`status` IN (
        'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'ORDERED',
        'PARTIALLY_RECEIVED', 'RECEIVED', 'COMPLETED', 'REJECTED', 'CANCELED'
    )),
    CONSTRAINT `ck_procurement_order_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Procurement orders and workflow state';

CREATE TABLE `lab_procurement_order_line` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `order_id` bigint NOT NULL,
    `line_no` int NOT NULL,
    `component_id` bigint NOT NULL,
    `component_name` varchar(120) DEFAULT NULL,
    `manufacturer_name` varchar(120) DEFAULT NULL,
    `mpn` varchar(120) DEFAULT NULL,
    `component_spec_snapshot_id` bigint DEFAULT NULL,
    `spec_version` int DEFAULT NULL,
    `unit` varchar(16) NOT NULL,
    `ordered_quantity` decimal(19,3) NOT NULL,
    `unit_price` decimal(19,4) NOT NULL,
    `line_subtotal` decimal(19,2) NOT NULL,
    `allocated_net_amount` decimal(19,2) DEFAULT NULL,
    `effective_unit_cost` decimal(19,4) DEFAULT NULL,
    `received_quantity` decimal(19,3) NOT NULL DEFAULT 0.000,
    `canceled_quantity` decimal(19,3) NOT NULL DEFAULT 0.000,
    `version` int NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_order_line_no` (`order_id`, `line_no`),
    UNIQUE KEY `uk_procurement_order_component` (`order_id`, `component_id`),
    KEY `idx_procurement_line_component` (`component_id`, `order_id`, `id`),
    CONSTRAINT `fk_procurement_line_order`
        FOREIGN KEY (`order_id`) REFERENCES `lab_procurement_order` (`id`),
    CONSTRAINT `fk_procurement_line_component`
        FOREIGN KEY (`component_id`) REFERENCES `lab_component` (`id`),
    CONSTRAINT `fk_procurement_line_snapshot`
        FOREIGN KEY (`component_spec_snapshot_id`) REFERENCES `lab_component_spec_snapshot` (`id`),
    CONSTRAINT `ck_procurement_line_no` CHECK (`line_no` >= 1),
    CONSTRAINT `ck_procurement_line_unit` CHECK (`unit` IN ('PCS', 'METER', 'GRAM', 'MILLILITER', 'SET')),
    CONSTRAINT `ck_procurement_line_quantities` CHECK (
        `ordered_quantity` > 0 AND `unit_price` > 0 AND `line_subtotal` >= 0 AND
        `received_quantity` >= 0 AND `canceled_quantity` >= 0 AND
        `received_quantity` + `canceled_quantity` <= `ordered_quantity`
    ),
    CONSTRAINT `ck_procurement_line_allocated` CHECK (
        (`allocated_net_amount` IS NULL AND `effective_unit_cost` IS NULL)
        OR (`allocated_net_amount` IS NOT NULL AND `allocated_net_amount` >= 0
            AND `effective_unit_cost` IS NOT NULL AND `effective_unit_cost` >= 0)
    ),
    CONSTRAINT `ck_procurement_line_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Procurement component lines and frozen cost facts';

CREATE TABLE `lab_procurement_approval_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `order_id` bigint NOT NULL,
    `decision` varchar(24) NOT NULL,
    `policy_version` int NOT NULL,
    `trigger_snapshot_json` text NOT NULL,
    `actor_type` varchar(16) NOT NULL,
    `decided_by` bigint DEFAULT NULL,
    `decided_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `comment` varchar(500) DEFAULT NULL,
    `request_id` varchar(100) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_approval_request` (`request_id`),
    KEY `idx_procurement_approval_order` (`order_id`, `decided_at`, `id`),
    CONSTRAINT `fk_procurement_approval_order`
        FOREIGN KEY (`order_id`) REFERENCES `lab_procurement_order` (`id`),
    CONSTRAINT `ck_procurement_approval_decision` CHECK (`decision` IN ('AUTO_APPROVED', 'APPROVED', 'REJECTED')),
    CONSTRAINT `ck_procurement_approval_actor` CHECK (
        (`actor_type` = 'SYSTEM' AND `decision` = 'AUTO_APPROVED' AND `decided_by` IS NULL)
        OR (`actor_type` = 'USER' AND `decision` IN ('APPROVED', 'REJECTED') AND `decided_by` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable procurement approval decisions';

CREATE TABLE `lab_procurement_receipt` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `receipt_no` varchar(32) NOT NULL,
    `order_id` bigint NOT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
    `received_at` datetime NOT NULL,
    `receiver_id` bigint DEFAULT NULL,
    `receiver_name` varchar(50) DEFAULT NULL,
    `note` varchar(1000) DEFAULT NULL,
    `confirmed_at` datetime DEFAULT NULL,
    `canceled_at` datetime DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by` bigint NOT NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `active_draft_slot` tinyint DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_receipt_no` (`receipt_no`),
    UNIQUE KEY `uk_procurement_active_draft` (`order_id`, `active_draft_slot`),
    KEY `idx_procurement_receipt_order` (`order_id`, `create_time`, `id`),
    CONSTRAINT `fk_procurement_receipt_order`
        FOREIGN KEY (`order_id`) REFERENCES `lab_procurement_order` (`id`),
    CONSTRAINT `ck_procurement_receipt_status` CHECK (`status` IN ('DRAFT', 'CONFIRMED', 'CANCELED')),
    CONSTRAINT `ck_procurement_receipt_draft_slot` CHECK (
        (`status` = 'DRAFT' AND `active_draft_slot` = 1)
        OR (`status` <> 'DRAFT' AND `active_draft_slot` IS NULL)
    ),
    CONSTRAINT `ck_procurement_receipt_audit` CHECK (
        (`status` = 'DRAFT' AND `confirmed_at` IS NULL AND `canceled_at` IS NULL)
        OR (`status` = 'CONFIRMED' AND `confirmed_at` IS NOT NULL AND `canceled_at` IS NULL)
        OR (`status` = 'CANCELED' AND `confirmed_at` IS NULL AND `canceled_at` IS NOT NULL)
    ),
    CONSTRAINT `ck_procurement_receipt_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Physical procurement receipt drafts and confirmations';

CREATE TABLE `lab_procurement_receipt_line` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `receipt_id` bigint NOT NULL,
    `order_line_id` bigint NOT NULL,
    `component_id` bigint NOT NULL,
    `quantity` decimal(19,3) NOT NULL,
    `unit` varchar(16) NOT NULL,
    `supplier_lot_no` varchar(120) DEFAULT NULL,
    `production_date` date DEFAULT NULL,
    `expiry_date` date DEFAULT NULL,
    `location_note` varchar(200) DEFAULT NULL,
    `quality_note` varchar(500) DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_receipt_order_line` (`receipt_id`, `order_line_id`),
    KEY `idx_procurement_receipt_line_component` (`component_id`, `receipt_id`, `id`),
    CONSTRAINT `fk_procurement_receipt_line_receipt`
        FOREIGN KEY (`receipt_id`) REFERENCES `lab_procurement_receipt` (`id`),
    CONSTRAINT `fk_procurement_receipt_line_order_line`
        FOREIGN KEY (`order_line_id`) REFERENCES `lab_procurement_order_line` (`id`),
    CONSTRAINT `fk_procurement_receipt_line_component`
        FOREIGN KEY (`component_id`) REFERENCES `lab_component` (`id`),
    CONSTRAINT `ck_procurement_receipt_line_quantity` CHECK (`quantity` > 0),
    CONSTRAINT `ck_procurement_receipt_line_unit` CHECK (`unit` IN ('PCS', 'METER', 'GRAM', 'MILLILITER', 'SET')),
    CONSTRAINT `ck_procurement_receipt_line_dates` CHECK (
        `expiry_date` IS NULL OR `production_date` IS NULL OR `expiry_date` >= `production_date`
    ),
    CONSTRAINT `ck_procurement_receipt_line_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Component quantities in one physical receipt';

CREATE TABLE `lab_component_inventory_balance` (
    `component_id` bigint NOT NULL,
    `on_hand_quantity` decimal(19,3) NOT NULL DEFAULT 0.000,
    `unit` varchar(16) NOT NULL,
    `version` int NOT NULL DEFAULT 1,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`component_id`),
    CONSTRAINT `fk_inventory_balance_component`
        FOREIGN KEY (`component_id`) REFERENCES `lab_component` (`id`),
    CONSTRAINT `ck_inventory_balance_quantity` CHECK (`on_hand_quantity` >= 0),
    CONSTRAINT `ck_inventory_balance_unit` CHECK (`unit` IN ('PCS', 'METER', 'GRAM', 'MILLILITER', 'SET')),
    CONSTRAINT `ck_inventory_balance_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Rebuildable component inventory balance projection';

CREATE TABLE `lab_inventory_batch` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `batch_no` varchar(32) NOT NULL,
    `component_id` bigint NOT NULL,
    `source_receipt_line_id` bigint NOT NULL,
    `initial_quantity` decimal(19,3) NOT NULL,
    `current_quantity` decimal(19,3) NOT NULL,
    `unit` varchar(16) NOT NULL,
    `unit_cost` decimal(19,4) NOT NULL,
    `supplier_lot_no` varchar(120) DEFAULT NULL,
    `production_date` date DEFAULT NULL,
    `expiry_date` date DEFAULT NULL,
    `location_note` varchar(200) DEFAULT NULL,
    `quality_note` varchar(500) DEFAULT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'AVAILABLE',
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_batch_no` (`batch_no`),
    UNIQUE KEY `uk_inventory_batch_receipt_line` (`source_receipt_line_id`),
    KEY `idx_inventory_batch_component` (`component_id`, `status`, `create_time`, `id`),
    CONSTRAINT `fk_inventory_batch_component`
        FOREIGN KEY (`component_id`) REFERENCES `lab_component` (`id`),
    CONSTRAINT `fk_inventory_batch_receipt_line`
        FOREIGN KEY (`source_receipt_line_id`) REFERENCES `lab_procurement_receipt_line` (`id`),
    CONSTRAINT `ck_inventory_batch_quantities` CHECK (
        `initial_quantity` > 0 AND `current_quantity` >= 0 AND `current_quantity` <= `initial_quantity`
    ),
    CONSTRAINT `ck_inventory_batch_unit_cost` CHECK (`unit_cost` >= 0),
    CONSTRAINT `ck_inventory_batch_unit` CHECK (`unit` IN ('PCS', 'METER', 'GRAM', 'MILLILITER', 'SET')),
    CONSTRAINT `ck_inventory_batch_status` CHECK (`status` = 'AVAILABLE'),
    CONSTRAINT `ck_inventory_batch_dates` CHECK (
        `expiry_date` IS NULL OR `production_date` IS NULL OR `expiry_date` >= `production_date`
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Traceable procurement inventory batches';

CREATE TABLE `lab_inventory_movement` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `movement_no` varchar(32) NOT NULL,
    `movement_type` varchar(32) NOT NULL,
    `component_id` bigint NOT NULL,
    `batch_id` bigint NOT NULL,
    `source_type` varchar(32) NOT NULL,
    `source_id` bigint NOT NULL,
    `quantity_delta` decimal(19,3) NOT NULL,
    `balance_before` decimal(19,3) NOT NULL,
    `balance_after` decimal(19,3) NOT NULL,
    `unit` varchar(16) NOT NULL,
    `unit_cost` decimal(19,4) NOT NULL,
    `operator_id` bigint NOT NULL,
    `occurred_at` datetime NOT NULL,
    `request_id` varchar(100) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_movement_no` (`movement_no`),
    UNIQUE KEY `uk_inventory_movement_source` (`movement_type`, `source_type`, `source_id`),
    KEY `idx_inventory_movement_component` (`component_id`, `occurred_at`, `id`),
    KEY `idx_inventory_movement_batch` (`batch_id`, `id`),
    CONSTRAINT `fk_inventory_movement_component`
        FOREIGN KEY (`component_id`) REFERENCES `lab_component` (`id`),
    CONSTRAINT `fk_inventory_movement_batch`
        FOREIGN KEY (`batch_id`) REFERENCES `lab_inventory_batch` (`id`),
    CONSTRAINT `ck_inventory_movement_type` CHECK (`movement_type` = 'PROCUREMENT_RECEIPT_IN'),
    CONSTRAINT `ck_inventory_movement_source` CHECK (`source_type` = 'RECEIPT_LINE'),
    CONSTRAINT `ck_inventory_movement_quantities` CHECK (
        `quantity_delta` > 0 AND `balance_before` >= 0 AND
        `balance_after` = `balance_before` + `quantity_delta`
    ),
    CONSTRAINT `ck_inventory_movement_unit` CHECK (`unit` IN ('PCS', 'METER', 'GRAM', 'MILLILITER', 'SET')),
    CONSTRAINT `ck_inventory_movement_unit_cost` CHECK (`unit_cost` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only inventory quantity movements';

CREATE TABLE `lab_procurement_attachment` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `owner_type` varchar(32) NOT NULL,
    `owner_id` bigint NOT NULL,
    `media_id` bigint NOT NULL,
    `purpose` varchar(32) NOT NULL,
    `invoice_no` varchar(120) DEFAULT NULL,
    `note` varchar(500) DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    `status` varchar(16) NOT NULL DEFAULT 'READY',
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted_by` bigint DEFAULT NULL,
    `deleted_at` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_attachment_media` (`media_id`),
    KEY `idx_procurement_attachment_owner` (`owner_type`, `owner_id`, `status`, `create_time`, `id`),
    CONSTRAINT `fk_procurement_attachment_media`
        FOREIGN KEY (`media_id`) REFERENCES `lab_media_object` (`id`),
    CONSTRAINT `ck_procurement_attachment_owner` CHECK (
        (`owner_type` = 'PROCUREMENT_ORDER' AND `purpose` IN ('ORDER_SCREENSHOT', 'INVOICE'))
        OR (`owner_type` = 'PROCUREMENT_RECEIPT' AND `purpose` IN ('RECEIPT_PHOTO', 'PACKAGE_PHOTO'))
    ),
    CONSTRAINT `ck_procurement_attachment_status` CHECK (`status` IN ('READY', 'DELETED')),
    CONSTRAINT `ck_procurement_attachment_delete` CHECK (
        (`status` = 'READY' AND `deleted_by` IS NULL AND `deleted_at` IS NULL)
        OR (`status` = 'DELETED' AND `deleted_by` IS NOT NULL AND `deleted_at` IS NOT NULL)
    ),
    CONSTRAINT `ck_procurement_attachment_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Procurement attachment purpose and owner links';

CREATE TABLE `lab_procurement_audit_log` (
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
    UNIQUE KEY `uk_procurement_audit_request_action` (`request_id`, `action`, `target_type`, `target_id`),
    KEY `idx_procurement_audit_target` (`target_type`, `target_id`, `create_time`, `id`),
    KEY `idx_procurement_audit_actor` (`actor_id`, `create_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only procurement and inventory audit events';

INSERT INTO `lab_procurement_policy_version` (
    `policy_version`, `currency`, `approval_threshold`,
    `valuable_unit_price_threshold`, `effective_at`, `created_by`
)
SELECT 1, 'CNY', 500.00, 1000.0000, CURRENT_TIMESTAMP, 1
WHERE NOT EXISTS (
    SELECT 1 FROM `lab_procurement_policy_version` WHERE `policy_version` = 1
);
