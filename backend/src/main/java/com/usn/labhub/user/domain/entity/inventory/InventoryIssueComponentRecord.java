package com.usn.labhub.user.domain.entity.inventory;

import lombok.Data;

@Data
public class InventoryIssueComponentRecord {
    private Long id;
    private String name;
    private String manufacturerName;
    private String mpn;
    private String baseUnit;
    private String status;
    private Long categoryId;
    private String categoryPath;
}
