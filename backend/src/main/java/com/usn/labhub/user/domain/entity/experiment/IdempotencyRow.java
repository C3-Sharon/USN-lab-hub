package com.usn.labhub.user.domain.entity.experiment;

import lombok.Data;

@Data
public class IdempotencyRow {
    private String requestDigest;
    private String status;
    private String responseJson;
}
