package com.usn.labhub.user.domain.vo.project;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ProjectPageVO {
    private Long total;
    private Integer page;
    private Integer pageSize;
    private List<ProjectSummaryVO> list;
}
