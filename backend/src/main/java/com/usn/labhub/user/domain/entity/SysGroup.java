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
 * 工作组配置表
 * </p>
 *
 * @author 陈思瑞
 * @since 2026-04-16
 */
@Getter
@Setter
@TableName("sys_group")
public class SysGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 组名
     */
    @TableField("group_name")
    private String groupName;

    /**
     * 组描述
     */
    @TableField("description")
    private String description;
}
