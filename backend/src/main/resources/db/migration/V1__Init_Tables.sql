-- USN Lab Hub initial schema and seed data.

CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` int NOT NULL AUTO_INCREMENT,
    `role_name` varchar(20) NOT NULL COMMENT '角色名称',
    `role_key` varchar(50) NOT NULL COMMENT '角色标识：admin/student',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色配置表';

CREATE TABLE IF NOT EXISTS `sys_identity` (
    `id` int NOT NULL AUTO_INCREMENT,
    `identity_name` varchar(20) NOT NULL COMMENT '身份名称',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='身份配置表';

CREATE TABLE IF NOT EXISTS `sys_group` (
    `id` int NOT NULL AUTO_INCREMENT,
    `group_name` varchar(50) NOT NULL COMMENT '组名',
    `description` varchar(255) DEFAULT NULL COMMENT '组描述',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作组配置表';

CREATE TABLE IF NOT EXISTS `sys_faculty_major` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
    `college_name` varchar(50) NOT NULL COMMENT '学院名称',
    `major_name` varchar(50) NOT NULL COMMENT '专业名称',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_college` (`college_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学院专业配置表';

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `username` varchar(50) NOT NULL COMMENT '真实姓名',
    `member_id` varchar(20) NOT NULL COMMENT '学号/工号，登录账号',
    `password` varchar(100) NOT NULL COMMENT 'BCrypt 加密密码',
    `identity_id` int DEFAULT NULL COMMENT '关联 sys_identity.id',
    `group_id` int DEFAULT NULL COMMENT '关联 sys_group.id',
    `faculty_id` int DEFAULT NULL COMMENT '关联 sys_faculty_major.id',
    `status` tinyint DEFAULT '1' COMMENT '1 正常，0 禁用',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_id` (`member_id`),
    KEY `idx_identity` (`identity_id`),
    KEY `idx_group` (`group_id`),
    KEY `idx_faculty` (`faculty_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户核心信息表';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `user_id` bigint NOT NULL COMMENT '用户 ID',
    `role_id` int NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关系表';

CREATE TABLE IF NOT EXISTS `attendance_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '关联 sys_user.id',
    `check_in_time` datetime NOT NULL COMMENT '签到时间',
    `check_in_date` date NOT NULL COMMENT '签到日期',
    `check_out_time` datetime DEFAULT NULL COMMENT '签退时间',
    `duration_minutes` int DEFAULT NULL COMMENT '本次考勤时长，单位分钟',
    `semester` varchar(20) NOT NULL COMMENT '学期标识，如 2025-2',
    `source` varchar(20) DEFAULT 'student' COMMENT '来源：student/admin/human',
    `operator_id` bigint DEFAULT NULL COMMENT '操作人 ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `check_in_date`),
    KEY `idx_semester` (`semester`),
    KEY `idx_check_in_time` (`check_in_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤记录表';

INSERT INTO `sys_role` (`id`, `role_name`, `role_key`) VALUES
    (1, '管理员', 'admin'),
    (2, '学生', 'student')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `role_key` = VALUES(`role_key`);

INSERT INTO `sys_identity` (`id`, `identity_name`) VALUES
    (1, '老师'),
    (2, '本科生'),
    (3, '研究生')
ON DUPLICATE KEY UPDATE `identity_name` = VALUES(`identity_name`);

INSERT INTO `sys_group` (`id`, `group_name`, `description`) VALUES
    (1, '实验室管理组', '系统管理员与实验室老师'),
    (2, '物联网开发组', '默认学生工作组')
ON DUPLICATE KEY UPDATE `group_name` = VALUES(`group_name`), `description` = VALUES(`description`);

INSERT INTO `sys_faculty_major` (`id`, `college_name`, `major_name`) VALUES
    (1, '计算机学院', '物联网工程'),
    (2, '计算机学院', '计算机科学与技术')
ON DUPLICATE KEY UPDATE `college_name` = VALUES(`college_name`), `major_name` = VALUES(`major_name`);

INSERT INTO `sys_user`
    (`id`, `username`, `member_id`, `password`, `identity_id`, `group_id`, `faculty_id`, `status`)
VALUES
    (1, '系统管理员', 'admin', '$2a$10$.D/2kDKLyVtgEABJGOO4dODj1TRUq9ZSwQ3jlAYSfbbwbR3FhLGLO', 1, 1, 1, 1),
    (2, '测试学生', '20260001', '$2a$10$/WSg8oPuptxjST7enw4yUOqYXcMkPB8R6z11LzfgWRXZRuwn6v8Qi', 2, 2, 1, 1)
ON DUPLICATE KEY UPDATE
    `username` = VALUES(`username`),
    `password` = VALUES(`password`),
    `identity_id` = VALUES(`identity_id`),
    `group_id` = VALUES(`group_id`),
    `faculty_id` = VALUES(`faculty_id`),
    `status` = VALUES(`status`);

INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`) VALUES
    (1, 1),
    (2, 2);
