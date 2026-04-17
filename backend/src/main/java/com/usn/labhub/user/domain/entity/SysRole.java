package com.usn.labhub.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 角色配置表
 * </p>
 *
 * @author 陈思瑞
 * @since 2026-04-16
 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 角色名称（管理员、学生）
     */
    @TableField("role_name")
    private String roleName;

    /**
     * 角色权限标识符（admin, student）
     */
    @TableField("role_key")
    private String roleKey;
}
