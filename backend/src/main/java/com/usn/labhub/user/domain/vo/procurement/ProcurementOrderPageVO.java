package com.usn.labhub.user.domain.vo.procurement;

import java.util.List;

public record ProcurementOrderPageVO(long total, int page, int pageSize, List<ProcurementOrderVO> list) {
}
