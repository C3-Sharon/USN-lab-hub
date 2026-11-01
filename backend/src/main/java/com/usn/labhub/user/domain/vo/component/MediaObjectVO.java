package com.usn.labhub.user.domain.vo.component;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MediaObjectVO {
    private Long mediaId;
    private String ownerType;
    private Long ownerId;
    private String role;
    private String status;
    private String originalFileName;
    private String contentType;
    private Long size;
    private Integer width;
    private Integer height;
    private String sha256;
    private String contentPath;
    private Integer sortOrder;
    private Integer version;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
    private Long deletedBy;
    private LocalDateTime deletedAt;
}
