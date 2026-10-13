package com.usn.labhub.user.domain.vo.learning;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExperimentTemplateVersionVO {
    private Long id;
    private Long templateId;
    private String version;
    private Integer versionCode;
    private String status;
    private JsonNode schemaJson;
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
