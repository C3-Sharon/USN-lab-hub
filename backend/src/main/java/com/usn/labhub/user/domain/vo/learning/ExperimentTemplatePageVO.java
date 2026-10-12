package com.usn.labhub.user.domain.vo.learning;

import java.util.List;

public record ExperimentTemplatePageVO(long total, int page, int pageSize,
                                       List<ExperimentTemplateVO> list) {
}
