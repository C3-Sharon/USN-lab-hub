package com.usn.labhub.user.domain.vo.learning;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExperimentTemplateVersionActionVO {
    private Long id;
    private String version;
    private String status;
    private Long publishedBy;
    private String publishedByName;
    private LocalDateTime publishedAt;
}
