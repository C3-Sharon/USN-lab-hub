-- Expand the legacy admin/student roles while preserving role IDs and assignments.

UPDATE `sys_role`
SET `role_key` = 'SYSTEM_ADMIN', `role_name` = '系统管理员'
WHERE `id` = 1 AND `role_key` = 'admin';

UPDATE `sys_role`
SET `role_key` = 'MEMBER', `role_name` = '普通成员'
WHERE `id` = 2 AND `role_key` = 'student';

INSERT INTO `sys_role` (`id`, `role_name`, `role_key`) VALUES
    (3, '老师/实验室负责人', 'TEACHER'),
    (4, '库存管理员', 'STOCK_KEEPER');
