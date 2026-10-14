package com.usn.labhub.user.domain.vo.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperimentTemplatePublishedVersionVO {
    private Long id;
    private String version;
    private String status;
    private Integer fieldCount;
    private String changelog;
    private LocalDateTime publishedAt;
}
