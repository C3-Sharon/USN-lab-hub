package com.usn.labhub.user.domain.vo.inventory;

import java.util.List;

public record InventoryIssuePageVO(long total, int page, int pageSize, List<InventoryIssueRequestVO> items) {
}
