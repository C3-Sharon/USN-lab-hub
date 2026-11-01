package com.usn.labhub.user.domain.vo.component;

import java.util.List;

public record CatalogPageVO<T>(long total, int page, int pageSize, List<T> list) {
}
