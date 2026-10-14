package com.usn.labhub.user.domain.vo.learning;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperimentTemplateVO {
    private Long id;
    private String title;
    private String description;
    private String scope;
    private Long projectId;
    private String projectName;
    private String category;
    private String latestVersion;
    private Long latestVersionId;
    private Integer publishedVersionCount;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean hasDraft;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
