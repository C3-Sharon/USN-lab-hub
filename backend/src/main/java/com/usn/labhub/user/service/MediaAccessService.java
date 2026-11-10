package com.usn.labhub.user.service;

import com.usn.labhub.user.component.ComponentCatalogApiException;
import com.usn.labhub.user.domain.entity.component.MediaObjectRecord;
import com.usn.labhub.user.domain.vo.component.MediaObjectVO;
import com.usn.labhub.user.mapper.ProcurementAttachmentMapper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Set;

@Service
public class MediaAccessService {
    private static final Set<String> PROCUREMENT_OWNERS = Set.of("PROCUREMENT_ORDER", "PROCUREMENT_RECEIPT");
    private final ComponentMediaService componentMedia;
    private final ProcurementAttachmentService procurementAttachment;
    private final ProcurementAttachmentMapper attachmentMapper;

    public MediaAccessService(ComponentMediaService componentMedia,
                              ProcurementAttachmentService procurementAttachment,
                              ProcurementAttachmentMapper attachmentMapper) {
        this.componentMedia = componentMedia;
        this.procurementAttachment = procurementAttachment;
        this.attachmentMapper = attachmentMapper;
    }

    public MediaObjectVO metadata(Long mediaId) {
        return "COMPONENT".equals(ownerType(mediaId))
                ? componentMedia.metadata(mediaId) : procurementAttachment.metadata(mediaId);
    }

    public MediaContent content(Long mediaId) {
        String ownerType = ownerType(mediaId);
        if ("COMPONENT".equals(ownerType)) {
            ComponentMediaService.MediaContent content = componentMedia.content(mediaId);
            return new MediaContent(content.input(), content.contentType());
        }
        ProcurementAttachmentService.MediaContent content = procurementAttachment.content(mediaId);
        return new MediaContent(content.input(), content.contentType());
    }

    private String ownerType(Long mediaId) {
        MediaObjectRecord media = mediaId == null ? null : attachmentMapper.selectMedia(mediaId);
        if (media == null || !"READY".equals(media.getStatus())) throw ComponentCatalogApiException.mediaNotFound();
        if ("COMPONENT".equals(media.getOwnerType()) || PROCUREMENT_OWNERS.contains(media.getOwnerType())) {
            return media.getOwnerType();
        }
        throw ComponentCatalogApiException.mediaNotFound();
    }

    public record MediaContent(InputStream input, String contentType) { }
}
