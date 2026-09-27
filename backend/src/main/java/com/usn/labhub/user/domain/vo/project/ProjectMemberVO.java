package com.usn.labhub.user.domain.vo.project;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectMemberVO {
    private Long userId;
    private String memberId;
    private String name;
    private String projectRole;
    private LocalDateTime joinedAt;
}
