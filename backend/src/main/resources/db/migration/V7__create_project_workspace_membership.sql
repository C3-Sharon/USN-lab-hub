-- Extend the legacy IoT project table without rewriting existing identifiers.
ALTER TABLE `lab_project`
    ADD COLUMN `category` varchar(32) DEFAULT NULL COMMENT 'Project category key';

ALTER TABLE `lab_project`
    ADD COLUMN `cover_media_id` bigint DEFAULT NULL COMMENT 'Optional media reference';

CREATE TABLE `lab_project_member` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `project_id` bigint NOT NULL,
    `user_id` bigint NOT NULL,
    `project_role` varchar(32) NOT NULL,
    `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_member_user` (`project_id`, `user_id`),
    KEY `idx_project_member_user_project` (`user_id`, `project_id`),
    KEY `idx_project_member_project_role` (`project_id`, `project_role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Project membership and project-scoped role';

UPDATE `lab_project`
SET `category` = 'iot_demo'
WHERE `project_code` = 'power-monitor' AND `category` IS NULL;

INSERT INTO `lab_project_member` (`project_id`, `user_id`, `project_role`, `joined_at`)
SELECT p.`id`, p.`owner_id`, 'OWNER', COALESCE(p.`create_time`, CURRENT_TIMESTAMP)
FROM `lab_project` p
INNER JOIN `sys_user` u ON u.`id` = p.`owner_id`
WHERE p.`owner_id` IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM `lab_project_member` pm
      WHERE pm.`project_id` = p.`id` AND pm.`user_id` = p.`owner_id`
  );
