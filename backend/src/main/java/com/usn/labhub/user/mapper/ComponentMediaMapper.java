package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.component.MediaObjectRecord;
import com.usn.labhub.user.domain.vo.component.MediaObjectVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ComponentMediaMapper {
    List<MediaObjectVO> selectReadyByComponent(@Param("componentId") Long componentId);
    MediaObjectRecord selectReadyMedia(@Param("mediaId") Long mediaId);
    MediaObjectVO selectReadyMediaView(@Param("mediaId") Long mediaId);
    MediaObjectRecord selectReadyMediaForUpdate(@Param("mediaId") Long mediaId);
    MediaObjectRecord selectReadyBySha(@Param("componentId") Long componentId,
                                       @Param("sha256") String sha256,
                                       @Param("excludeMediaId") Long excludeMediaId);
    long countReadyByComponent(@Param("componentId") Long componentId);
    int insertMedia(MediaObjectRecord row);
    int markDeleted(@Param("mediaId") Long mediaId, @Param("version") Integer version,
                    @Param("deletedBy") Long deletedBy,
                    @Param("deletedAt") java.time.LocalDateTime deletedAt);
    int updateComponentMediaState(@Param("componentId") Long componentId,
                                  @Param("componentVersion") Integer componentVersion,
                                  @Param("primaryMediaId") Long primaryMediaId,
                                  @Param("updatedBy") Long updatedBy,
                                  @Param("updatedAt") java.time.LocalDateTime updatedAt);
    List<MediaObjectRecord> selectReadyRecordsForUpdate(@Param("componentId") Long componentId);
    MediaObjectRecord selectPrimaryForUpdate(@Param("componentId") Long componentId);
    MediaObjectRecord selectFirstReadyForUpdate(@Param("componentId") Long componentId);
    int updateMediaRole(@Param("mediaId") Long mediaId, @Param("version") Integer version,
                        @Param("role") String role, @Param("primarySlot") Integer primarySlot);
    int updateMediaSortOrder(@Param("mediaId") Long mediaId, @Param("version") Integer version,
                             @Param("sortOrder") Integer sortOrder);
}
