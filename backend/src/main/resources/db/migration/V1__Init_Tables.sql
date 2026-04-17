-- 1. 角色表 (sys_role)
CREATE TABLE `sys_role` (
                            `id` int NOT NULL AUTO_INCREMENT,
                            `role_name` varchar(20) NOT NULL COMMENT '角色名称（管理员、学生）',
                            `role_key` varchar(50) NOT NULL COMMENT '角色权限标识符（admin, student）',
                            PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色配置表';

-- 2. 身份表 (sys_identity)
CREATE TABLE `sys_identity` (
                                `id` int NOT NULL AUTO_INCREMENT,
                                `identity_name` varchar(20) NOT NULL COMMENT '身份名称（本科、硕士、博士、老师）',
                                PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='身份配置表';

-- 3. 工作组表 (sys_group)
CREATE TABLE `sys_group` (
                             `id` int NOT NULL AUTO_INCREMENT,
                             `group_name` varchar(50) NOT NULL COMMENT '组名',
                             `description` varchar(255) DEFAULT NULL COMMENT '组描述',
                             PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作组配置表';

-- 4. 用户基础信息表 (sys_user)
CREATE TABLE `sys_user` (
                            `id` bigint NOT NULL AUTO_INCREMENT,
                            `username` varchar(50) NOT NULL COMMENT '真实姓名',
                            `member_id` varchar(20) NOT NULL COMMENT '学号/工号（登录唯一账号）',
                            `password` varchar(100) NOT NULL COMMENT 'BCrypt加密密码',
                            `identity_id` int DEFAULT NULL COMMENT '关联sys_identity.id',
                            `group_id` int DEFAULT NULL COMMENT '关联sys_group.id',
                            `faculty_id` int DEFAULT NULL COMMENT '关联sys_faculty_major.id',
                                `status` tinyint DEFAULT '1' COMMENT '1正常 0禁用',
                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                            `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_member_id` (`member_id`),
                            KEY `idx_identity` (`identity_id`),
                            KEY `idx_group` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户核心信息表';

-- 5. 用户-角色关联表 (sys_user_role)
-- 考虑到以后一个用户可能有多个角色（例如：既是学生又是管理员），采用中间表关联
CREATE TABLE `sys_user_role` (
                                 `user_id` bigint NOT NULL COMMENT '用户ID',
                                 `role_id` int NOT NULL COMMENT '角色ID',
                                 PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关系表';

-- 6. 考勤表 (基本保持不变，关联sys_user.id)
CREATE TABLE `attendance_record` (
                                     `id` bigint NOT NULL AUTO_INCREMENT,
                                     `user_id` bigint NOT NULL,
                                     `check_in_time` datetime NOT NULL,
                                     `check_in_date` date NOT NULL,
                                     `check_out_time` datetime DEFAULT NULL,
                                     `duration_minutes` int DEFAULT NULL,
                                     `semester` varchar(20) NOT NULL,
                                     `source` varchar(20) DEFAULT 'student',
                                     `operator_id` bigint DEFAULT NULL,
                                     `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                     PRIMARY KEY (`id`),
                                     KEY `idx_user_date` (`user_id`, `check_in_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤记录表';

-- 7. 学院专业表
CREATE TABLE `sys_faculty_major` (
                                     `id` int NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
                                     `college_name` varchar(50) NOT NULL COMMENT '学院名称（如：计算机学院）',
                                     `major_name` varchar(50) NOT NULL COMMENT '专业名称（如：物联网工程）',
                                     `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                     PRIMARY KEY (`id`),
                                     KEY `idx_college` (`college_name`) -- 方便按学院快速筛选
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学院专业配置表';

-- 8. 考勤表
CREATE TABLE IF NOT EXISTS `attendance_record` (
                                                   `id` bigint NOT NULL AUTO_INCREMENT,
                                                   `user_id` bigint NOT NULL COMMENT '关联user.id',
                                                   `check_in_time` datetime NOT NULL COMMENT '签到时间',
                                                   `check_in_date` date NOT NULL COMMENT '签到日期（冗余，用于快速按天/周统计）',
                                                   `check_out_time` datetime DEFAULT NULL COMMENT '签退时间',
                                                   `duration_minutes` int DEFAULT NULL COMMENT '本次考勤时长（分钟）',
                                                   `semester` varchar(20) NOT NULL COMMENT '学期标识，如“2025-2”',
                                                   `source` varchar(20) DEFAULT 'student' COMMENT '来源：student/admin',
                                                   `operator_id` bigint DEFAULT NULL COMMENT '补签操作人id',
                                                   `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                                   PRIMARY KEY (`id`),
                                                   KEY `idx_user_date` (`user_id`, `check_in_date`),
                                                   KEY `idx_semester` (`semester`),
                                                   KEY `idx_check_in_time` (`check_in_time`)
);