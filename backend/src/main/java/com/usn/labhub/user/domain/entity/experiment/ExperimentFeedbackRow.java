package com.usn.labhub.user.domain.entity.experiment;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperimentFeedbackRow {
    private Long id;
    private Long recordId;
    private Long revisionId;
    private Integer revisionNo;
    private String type;
    private String content;
    private String visibility;
    private Long reviewerId;
    private String reviewerName;
    private String requestId;
    private LocalDateTime createTime;
}
