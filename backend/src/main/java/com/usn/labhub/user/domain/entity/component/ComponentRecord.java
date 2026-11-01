package com.usn.labhub.user.domain.entity.component;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ComponentRecord {
    private Long id;
    private String catalogCode;
    private String name;
    private Long manufacturerId;
    private String mpn;
    private String normalizedMpn;
    private Long categoryId;
    private String packageType;
    private String baseUnit;
    private String description;
    private String status;
    private Long currentSpecSnapshotId;
    private Integer specVersion;
    private Long primaryMediaId;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createTime;
    private Long updatedBy;
    private LocalDateTime updateTime;
}
