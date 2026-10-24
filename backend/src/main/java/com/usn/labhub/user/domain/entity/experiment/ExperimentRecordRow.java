package com.usn.labhub.user.domain.entity.experiment;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperimentRecordRow {
    private Long id;
    private String recordCode;
    private String title;
    private String summary;
    private Long authorId;
    private String authorName;
    private Long projectId;
    private String projectName;
    private String projectStatus;
    private String projectRole;
    private Long learningUnitId;
    private String learningUnitTitle;
    private Long templateId;
    private Long templateVersionId;
    private String templateVersion;
    private String templateScope;
    private Long templateProjectId;
    private String schemaJson;
    private String status;
    private Long currentRevisionId;
    private Long publishedRevisionId;
    private Long convertedProjectId;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
