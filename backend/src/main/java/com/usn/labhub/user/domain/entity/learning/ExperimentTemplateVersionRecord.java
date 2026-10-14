package com.usn.labhub.user.domain.entity.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperimentTemplateVersionRecord {
    private Long id;
    private Long templateId;
    private String version;
    private Integer versionCode;
    private String status;
    private String schemaJson;
    private Integer fieldCount;
    private String changelog;
    private Long publishedBy;
    private String publishedByName;
    private LocalDateTime publishedAt;
    private String deprecatedReason;
    private Long deprecatedBy;
    private LocalDateTime deprecatedAt;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
