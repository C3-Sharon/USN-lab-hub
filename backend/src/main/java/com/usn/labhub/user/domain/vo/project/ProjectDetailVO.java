package com.usn.labhub.user.domain.vo.project;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectDetailVO extends ProjectSummaryVO {
    private List<ProjectMemberVO> members = new ArrayList<>();
}
