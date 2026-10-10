package com.usn.labhub.user.domain.vo.project;

import java.time.LocalDateTime;

public record TaskStatusVO(Long id, String status, Integer version, LocalDateTime updateTime) {
}
