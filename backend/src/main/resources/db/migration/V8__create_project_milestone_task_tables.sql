CREATE TABLE `lab_milestone` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `project_id` bigint NOT NULL,
    `name` varchar(80) NOT NULL,
    `description` varchar(500) DEFAULT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'PLANNED',
    `start_date` date DEFAULT NULL,
    `end_date` date DEFAULT NULL,
    `sort_order` int NOT NULL DEFAULT 0,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_milestone_project_sort` (`project_id`, `sort_order`, `id`),
    KEY `idx_milestone_project_status` (`project_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Project milestones';

CREATE TABLE `lab_task` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `project_id` bigint NOT NULL,
    `milestone_id` bigint DEFAULT NULL,
    `title` varchar(120) NOT NULL,
    `description` varchar(2000) DEFAULT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'TODO',
    `assignee_user_id` bigint DEFAULT NULL,
    `priority` varchar(16) NOT NULL DEFAULT 'MEDIUM',
    `due_date` date DEFAULT NULL,
    `block_reason` varchar(500) DEFAULT NULL,
    `version` int NOT NULL DEFAULT 1,
    `created_by` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_task_project_status_created` (`project_id`, `status`, `create_time`, `id`),
    KEY `idx_task_project_milestone` (`project_id`, `milestone_id`),
    KEY `idx_task_project_assignee_status` (`project_id`, `assignee_user_id`, `status`),
    KEY `idx_task_assignee_status_updated` (`assignee_user_id`, `status`, `update_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Project lightweight tasks';
