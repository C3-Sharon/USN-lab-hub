package com.usn.labhub.user.service;

import com.usn.labhub.user.component.ComponentCatalogAccessPolicy;
import com.usn.labhub.user.component.ComponentCatalogApiException;
import com.usn.labhub.user.domain.entity.component.ComponentRecord;
import com.usn.labhub.user.domain.entity.component.MediaObjectRecord;
import com.usn.labhub.user.domain.vo.component.MediaObjectVO;
import com.usn.labhub.user.mapper.ComponentCatalogMapper;
import com.usn.labhub.user.mapper.ComponentMediaMapper;
import com.usn.labhub.user.media.MediaStorageException;
import com.usn.labhub.user.media.MediaStoragePort;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
public class ComponentMediaService {
    private final ComponentMediaMapper mediaMapper;
    private final ComponentCatalogMapper catalogMapper;
    private final ComponentCatalogAccessPolicy accessPolicy;
    private final MediaStoragePort storage;

    public ComponentMediaService(ComponentMediaMapper mediaMapper,
                                 ComponentCatalogMapper catalogMapper,
                                 ComponentCatalogAccessPolicy accessPolicy,
                                 MediaStoragePort storage) {
        this.mediaMapper = mediaMapper;
        this.catalogMapper = catalogMapper;
        this.accessPolicy = accessPolicy;
        this.storage = storage;
    }

    public List<MediaObjectVO> images(Long componentId) {
        requireVisibleComponent(componentId);
        return mediaMapper.selectReadyByComponent(componentId);
    }

    public MediaObjectVO metadata(Long mediaId) {
        MediaObjectRecord media = requireVisibleMedia(mediaId);
        MediaObjectVO view = mediaMapper.selectReadyMediaView(media.getId());
        if (view == null) {
            throw ComponentCatalogApiException.mediaNotFound();
        }
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

    private MediaObjectRecord requireVisibleMedia(Long mediaId) {
        MediaObjectRecord media = mediaId == null ? null : mediaMapper.selectReadyMedia(mediaId);
        if (media == null) {
            throw ComponentCatalogApiException.mediaNotFound();
        }
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

    public record MediaContent(InputStream input, String contentType) {
    }
}
