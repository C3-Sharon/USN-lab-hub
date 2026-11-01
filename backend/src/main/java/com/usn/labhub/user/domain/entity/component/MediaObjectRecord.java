package com.usn.labhub.user.domain.entity.component;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MediaObjectRecord {
    private Long id;
    private String ownerType;
    private Long ownerId;
    private String role;
    private String status;
    private String originalFileName;
    private String contentType;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private String sha256;
    private String originalObjectKey;
    private String displayObjectKey;
    private Integer sortOrder;
    private Integer readyDedupSlot;
    private Integer readyPrimarySlot;
    private Integer version;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
    private Long deletedBy;
    private LocalDateTime deletedAt;
}
