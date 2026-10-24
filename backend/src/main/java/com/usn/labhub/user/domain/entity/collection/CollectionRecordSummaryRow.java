package com.usn.labhub.user.domain.entity.collection;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CollectionRecordSummaryRow {
    private Long recordCount;
    private Long rejectedCount;
    private LocalDateTime observedFrom;
    private LocalDateTime observedTo;
}
