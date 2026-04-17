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
 * 学院专业配置表
 * </p>
 *
 * @author 陈思瑞
 * @since 2026-04-16
 */
@Getter
@Setter
@TableName("sys_faculty_major")
public class SysFacultyMajor implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 学院名称（如：计算机学院）
     */
    @TableField("college_name")
    private String collegeName;

    /**
     * 专业名称（如：物联网工程）
     */
    @TableField("major_name")
    private String majorName;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
