package com.usn.labhub.user.domain.entity.component;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ComponentManufacturerRecord {
    private Long id;
    private String code;
    private String name;
    private String normalizedName;
    private String website;
    private String status;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createTime;
    private Long updatedBy;
    private LocalDateTime updateTime;
}
