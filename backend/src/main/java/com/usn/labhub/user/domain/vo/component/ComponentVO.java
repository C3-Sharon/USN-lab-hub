package com.usn.labhub.user.domain.vo.component;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ComponentVO {
    private Long id;
    private String catalogCode;
    private String name;
    private Long manufacturerId;
    private String manufacturerName;
    private String mpn;
    private String normalizedMpn;
    private Long categoryId;
    private String categoryName;
    private String topCategoryCode;
    private List<CategoryPathItemVO> categoryPath;
    private String packageType;
    private String baseUnit;
    private String description;
    private String status;
    private Long currentSpecSnapshotId;
    private Integer specVersion;
    private Long primaryMediaId;
    private String imageState;
    private String placeholderKey;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
