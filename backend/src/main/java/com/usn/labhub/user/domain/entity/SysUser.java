package com.usn.labhub.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 用户核心信息表
 * </p>
 *
 * @author 陈思瑞
 * @since 2026-04-16
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 真实姓名
     */
    @TableField("username")
    private String username;

    /**
     * 学号/工号（登录唯一账号）
     */
    @TableField("member_id")
    private String memberId;

    /**
     * BCrypt加密密码
     */
    @TableField("password")
    private String password;

    /**
     * 关联sys_identity.id
     */
    @TableField("identity_id")
    private Integer identityId;

    /**
     * 关联sys_group.id
     */
    @TableField("group_id")
    private Integer groupId;

    /**
     * 关联sys_faculty_major.id
     */
    @TableField("faculty_id")
    private Integer facultyId;

    /**
     * 1正常 0禁用
     */
    @TableField("status")
    private Byte status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
