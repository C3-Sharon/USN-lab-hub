package com.usn.labhub.user.domain.vo.procurement;

import java.util.List;

public record ProcurementPageVO<T>(long total, int page, int pageSize, List<T> list) {
}
