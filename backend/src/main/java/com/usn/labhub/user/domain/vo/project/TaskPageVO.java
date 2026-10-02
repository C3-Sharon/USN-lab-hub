package com.usn.labhub.user.domain.vo.project;

import java.util.List;

public record TaskPageVO(long total, int page, int pageSize, List<TaskVO> list) {
}
