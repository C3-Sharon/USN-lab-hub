package com.usn.labhub.user.domain.entity.component;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ComponentCategoryRecord {
    private Long id;
    private String code;
    private String name;
    private String normalizedName;
    private Long parentId;
    private Long parentScopeId;
    private Integer level;
    private String path;
    private String status;
    private Integer sortOrder;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createTime;
    private Long updatedBy;
    private LocalDateTime updateTime;
}
