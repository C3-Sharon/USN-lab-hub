package com.usn.labhub.user.domain.vo.component;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ManufacturerVO {
    private Long id;
    private String code;
    private String name;
    private String website;
    private String status;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
