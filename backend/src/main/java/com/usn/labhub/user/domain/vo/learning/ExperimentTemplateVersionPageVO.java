package com.usn.labhub.user.domain.vo.learning;

import java.util.List;

public record ExperimentTemplateVersionPageVO(long total, int page, int pageSize,
                                              List<ExperimentTemplateVersionVO> list) {
}
