package com.usn.labhub.user.domain.entity.procurement;

import lombok.Data;

@Data
public class ProcurementComponentRecord {
    private Long id;
    private String name;
    private String manufacturerName;
    private String mpn;
    private String baseUnit;
    private String status;
    private Long currentSpecSnapshotId;
    private Integer specVersion;
}
