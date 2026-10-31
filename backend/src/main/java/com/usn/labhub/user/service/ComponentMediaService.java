package com.usn.labhub.user.service;

import com.usn.labhub.user.component.ComponentCatalogAccessPolicy;
import com.usn.labhub.user.component.ComponentCatalogApiException;
import com.usn.labhub.user.component.ComponentCatalogSupport;
import com.usn.labhub.user.domain.entity.component.ComponentRecord;
import com.usn.labhub.user.domain.entity.component.MediaObjectRecord;
import com.usn.labhub.user.domain.vo.component.MediaObjectVO;
import com.usn.labhub.user.mapper.ComponentCatalogMapper;
import com.usn.labhub.user.mapper.ComponentMediaMapper;
import com.usn.labhub.user.media.CommittedMedia;
import com.usn.labhub.user.media.MediaStorageException;
import com.usn.labhub.user.media.MediaStoragePort;
import com.usn.labhub.user.media.MediaUploadDescriptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ComponentMediaService {
    private final ComponentMediaMapper mediaMapper;
    private final ComponentCatalogMapper catalogMapper;
    private final ComponentCatalogAccessPolicy accessPolicy;
    private final ComponentCatalogSupport support;
    private final MediaStoragePort storage;
    private final TransactionTemplate transactions;

    public ComponentMediaService(ComponentMediaMapper mediaMapper, ComponentCatalogMapper catalogMapper,
                                 ComponentCatalogAccessPolicy accessPolicy, ComponentCatalogSupport support,
                                 MediaStoragePort storage, TransactionTemplate transactions) {
        this.mediaMapper = mediaMapper;
        this.catalogMapper = catalogMapper;
        this.accessPolicy = accessPolicy;
        this.support = support;
        this.storage = storage;
        this.transactions = transactions;
    }

    public List<MediaObjectVO> images(Long componentId) {
        requireVisibleComponent(componentId);
        return mediaMapper.selectReadyByComponent(componentId);
    }

    public MediaObjectVO metadata(Long mediaId) {
        MediaObjectRecord media = requireVisibleMedia(mediaId);
        MediaObjectVO view = mediaMapper.selectReadyMediaView(media.getId());
        if (view == null) throw ComponentCatalogApiException.mediaNotFound();
        return view;
    }

    public MediaContent content(Long mediaId) {
        MediaObjectRecord media = requireVisibleMedia(mediaId);
        try {
            return new MediaContent(storage.open(media.getDisplayObjectKey()), media.getContentType());
        } catch (MediaStorageException exception) {
            throw ComponentCatalogApiException.mediaStorageFailed();
        }
    }

    public Map<String, Object> upload(Long componentId, String key, MultipartFile file,
                                      Integer componentVersion, Integer sortOrder) {
        accessPolicy.requireMediaManage();
        validateMutation(componentId, componentVersion, sortOrder);
        PreparedMedia prepared = prepare(file);
        boolean[] retained = {false};
        try {
            Map<String, Object> result = transactions.execute(status -> support.idempotent(
                    "UPLOAD_COMPONENT_MEDIA:" + componentId, key,
                    mutationDigest(componentVersion, null, sortOrder, prepared),
                    () -> createUpload(componentId, componentVersion, sortOrder, prepared, key, retained)));
            rollbackUnlessRetained(prepared, retained[0]);
            return result;
        } catch (RuntimeException exception) {
            storage.rollbackNewObject(prepared.committed());
            throw exception;
        }
    }

    public Map<String, Object> replace(Long componentId, Long mediaId, String key, MultipartFile file,
                                       Integer componentVersion, Integer mediaVersion) {
        accessPolicy.requireMediaManage();
        validateMutation(componentId, componentVersion, 0);
        if (mediaId == null || mediaVersion == null || mediaVersion < 1) {
            throw ComponentCatalogApiException.invalid();
        }
        PreparedMedia prepared = prepare(file);
        boolean[] retained = {false};
        try {
            Map<String, Object> result = transactions.execute(status -> support.idempotent(
                    "REPLACE_COMPONENT_MEDIA:" + componentId + ":" + mediaId, key,
                    mutationDigest(componentVersion, mediaVersion, null, prepared),
                    () -> replaceReady(componentId, mediaId, componentVersion, mediaVersion,
                            prepared, key, retained)));
            rollbackUnlessRetained(prepared, retained[0]);
            return result;
        } catch (RuntimeException exception) {
            storage.rollbackNewObject(prepared.committed());
            throw exception;
        }
    }

    private Map<String, Object> createUpload(Long componentId, Integer componentVersion, Integer sortOrder,
                                              PreparedMedia prepared, String requestId, boolean[] retained) {
        ComponentRecord component = requireWritableComponentForUpdate(componentId, componentVersion);
        MediaObjectRecord duplicate = mediaMapper.selectReadyBySha(componentId, prepared.committed().sha256(), null);
        if (duplicate != null) {
            return uploadResponse(component, mediaMapper.selectReadyMediaView(duplicate.getId()));
        }
        long readyCount = mediaMapper.countReadyByComponent(componentId);
        if (readyCount >= 5) throw ComponentCatalogApiException.mediaLimitExceeded();
        boolean primary = readyCount == 0;
        MediaObjectRecord row = newMedia(componentId, primary ? "PRIMARY" : "GALLERY", sortOrder, prepared);
        mediaMapper.insertMedia(row);
        Long primaryMediaId = primary ? row.getId() : component.getPrimaryMediaId();
        updateComponentMediaState(component, primaryMediaId);
        support.audit(requestId, "UPLOAD_COMPONENT_MEDIA", "MEDIA", row.getId(), null, "READY", null,
                auditDetails(row));
        retained[0] = true;
        component.setVersion(component.getVersion() + 1);
        component.setPrimaryMediaId(primaryMediaId);
        return uploadResponse(component, mediaMapper.selectReadyMediaView(row.getId()));
    }

    private Map<String, Object> replaceReady(Long componentId, Long mediaId, Integer componentVersion,
                                              Integer mediaVersion, PreparedMedia prepared,
                                              String requestId, boolean[] retained) {
        ComponentRecord component = requireWritableComponentForUpdate(componentId, componentVersion);
        MediaObjectRecord old = mediaMapper.selectReadyMediaForUpdate(mediaId);
        if (old == null || !componentId.equals(old.getOwnerId())) throw ComponentCatalogApiException.mediaNotFound();
        if (!mediaVersion.equals(old.getVersion())) throw ComponentCatalogApiException.versionConflict(old.getVersion());
        if (old.getSha256().equals(prepared.committed().sha256())) return imageState(component);
        if (mediaMapper.selectReadyBySha(componentId, prepared.committed().sha256(), mediaId) != null) {
            throw ComponentCatalogApiException.mediaDuplicateReady();
        }
        LocalDateTime now = LocalDateTime.now();
        Long actorId = support.requiredUserId();
        if (mediaMapper.markDeleted(mediaId, mediaVersion, actorId, now) != 1) {
            throw ComponentCatalogApiException.versionConflict(old.getVersion());
        }
        MediaObjectRecord replacement = newMedia(componentId, old.getRole(), old.getSortOrder(), prepared);
        mediaMapper.insertMedia(replacement);
        Long primaryMediaId = "PRIMARY".equals(old.getRole()) ? replacement.getId() : component.getPrimaryMediaId();
        updateComponentMediaState(component, primaryMediaId);
        support.audit(requestId, "REPLACE_COMPONENT_MEDIA", "MEDIA", replacement.getId(), "READY", "READY",
                null, replacementAudit(old, replacement));
        retained[0] = true;
        component.setVersion(component.getVersion() + 1);
        component.setPrimaryMediaId(primaryMediaId);
        return imageState(component);
    }

    private MediaObjectRecord requireVisibleMedia(Long mediaId) {
        MediaObjectRecord media = mediaId == null ? null : mediaMapper.selectReadyMedia(mediaId);
        if (media == null) throw ComponentCatalogApiException.mediaNotFound();
        ComponentRecord component = catalogMapper.selectComponent(media.getOwnerId());
        if (component == null || (!accessPolicy.canReadAllStates() && !"ACTIVE".equals(component.getStatus()))) {
            throw ComponentCatalogApiException.mediaNotFound();
        }
        return media;
    }

    private ComponentRecord requireVisibleComponent(Long componentId) {
        ComponentRecord component = componentId == null ? null : catalogMapper.selectComponent(componentId);
        if (component == null || (!accessPolicy.canReadAllStates() && !"ACTIVE".equals(component.getStatus()))) {
            throw ComponentCatalogApiException.componentNotFound();
        }
        return component;
    }

    private ComponentRecord requireWritableComponentForUpdate(Long componentId, Integer expectedVersion) {
        ComponentRecord component = catalogMapper.selectComponentForUpdate(componentId);
        if (component == null) throw ComponentCatalogApiException.componentNotFound();
        if (!expectedVersion.equals(component.getVersion())) {
            throw ComponentCatalogApiException.versionConflict(component.getVersion());
        }
        if ("INACTIVE".equals(component.getStatus())) throw ComponentCatalogApiException.invalidTransition();
        return component;
    }

    private void updateComponentMediaState(ComponentRecord component, Long primaryMediaId) {
        if (mediaMapper.updateComponentMediaState(component.getId(), component.getVersion(), primaryMediaId,
                support.requiredUserId(), LocalDateTime.now()) != 1) {
            throw ComponentCatalogApiException.versionConflict(component.getVersion());
        }
    }

    private MediaObjectRecord newMedia(Long componentId, String role, Integer sortOrder, PreparedMedia prepared) {
        CommittedMedia file = prepared.committed();
        MediaObjectRecord row = new MediaObjectRecord();
        row.setOwnerType("COMPONENT"); row.setOwnerId(componentId); row.setRole(role); row.setStatus("READY");
        row.setOriginalFileName(prepared.originalFilename()); row.setContentType(file.contentType());
        row.setSizeBytes(file.sizeBytes()); row.setWidth(file.width()); row.setHeight(file.height());
        row.setSha256(file.sha256()); row.setOriginalObjectKey(file.originalObjectKey());
        row.setDisplayObjectKey(file.displayObjectKey()); row.setSortOrder(sortOrder); row.setReadyDedupSlot(1);
        row.setReadyPrimarySlot("PRIMARY".equals(role) ? 1 : null); row.setVersion(1);
        row.setUploadedBy(support.requiredUserId()); row.setUploadedAt(LocalDateTime.now());
        return row;
    }

    private PreparedMedia prepare(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) throw ComponentCatalogApiException.invalid();
        try {
            var staged = storage.stage(file.getInputStream());
            CommittedMedia committed = storage.commit(staged,
                    new MediaUploadDescriptor(file.getOriginalFilename(), file.getContentType()));
            return new PreparedMedia(file.getOriginalFilename().trim(), committed);
        } catch (MediaStorageException exception) {
            throw mapStorageException(exception);
        } catch (IOException exception) {
            throw ComponentCatalogApiException.mediaStorageFailed();
        }
    }

    private ComponentCatalogApiException mapStorageException(MediaStorageException exception) {
        return switch (exception.getReason()) {
            case TOO_LARGE -> ComponentCatalogApiException.mediaTooLarge();
            case TYPE_UNSUPPORTED -> ComponentCatalogApiException.mediaTypeUnsupported();
            case DECODE_FAILED -> ComponentCatalogApiException.mediaDecodeFailed();
            case PROCESSING_FAILED -> ComponentCatalogApiException.mediaProcessingFailed();
            default -> ComponentCatalogApiException.mediaStorageFailed();
        };
    }

    private void validateMutation(Long componentId, Integer componentVersion, Integer sortOrder) {
        if (componentId == null || componentVersion == null || componentVersion < 1
                || sortOrder == null || sortOrder < 0 || sortOrder > 9999) {
            throw ComponentCatalogApiException.invalid();
        }
    }

    private Map<String, Object> mutationDigest(Integer componentVersion, Integer mediaVersion,
                                                Integer sortOrder, PreparedMedia prepared) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("componentVersion", componentVersion); digest.put("mediaVersion", mediaVersion);
        digest.put("sortOrder", sortOrder); digest.put("originalFilename", prepared.originalFilename());
        digest.put("contentType", prepared.committed().contentType());
        digest.put("sha256", prepared.committed().sha256());
        return digest;
    }

    private Map<String, Object> uploadResponse(ComponentRecord component, MediaObjectVO media) {
        Map<String, Object> response = baseState(component); response.put("media", media); return response;
    }

    private Map<String, Object> imageState(ComponentRecord component) {
        Map<String, Object> response = baseState(component);
        response.put("images", mediaMapper.selectReadyByComponent(component.getId())); return response;
    }

    private Map<String, Object> baseState(ComponentRecord component) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("componentId", component.getId()); response.put("componentVersion", component.getVersion());
        List<MediaObjectVO> images = mediaMapper.selectReadyByComponent(component.getId());
        response.put("imageState", images.isEmpty() ? "PLACEHOLDER" : "READY");
        response.put("primaryMediaId", component.getPrimaryMediaId()); return response;
    }

    private Map<String, Object> auditDetails(MediaObjectRecord row) {
        return Map.of("originalFileName", row.getOriginalFileName(), "contentType", row.getContentType(),
                "size", row.getSizeBytes(), "width", row.getWidth(), "height", row.getHeight(),
                "sha256", row.getSha256());
    }

    private Map<String, Object> replacementAudit(MediaObjectRecord old, MediaObjectRecord replacement) {
        Map<String, Object> details = new LinkedHashMap<>(auditDetails(replacement));
        details.put("replacedMediaId", old.getId()); return details;
    }

    private void rollbackUnlessRetained(PreparedMedia prepared, boolean retained) {
        if (!retained) storage.rollbackNewObject(prepared.committed());
    }

    public record MediaContent(InputStream input, String contentType) { }
    private record PreparedMedia(String originalFilename, CommittedMedia committed) { }
}
