package com.usn.labhub.user.domain.vo.iot;

import lombok.Data;

@Data
public class IotOperationLogVO {
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String action;
    private String targetType;
    private String targetId;
    private String summary;
    private String createdAt;
}
