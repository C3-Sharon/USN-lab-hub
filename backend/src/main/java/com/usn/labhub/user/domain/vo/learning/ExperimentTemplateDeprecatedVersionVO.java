package com.usn.labhub.user.domain.vo.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperimentTemplateDeprecatedVersionVO {
    private Long id;
    private String version;
    private String status;
    private String deprecatedReason;
    private Long deprecatedBy;
    private LocalDateTime deprecatedAt;
    private LocalDateTime updateTime;
}
