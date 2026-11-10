package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.component.MediaObjectRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementAttachmentRecord;
import com.usn.labhub.user.domain.vo.procurement.ProcurementAttachmentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProcurementAttachmentMapper {
    int insertMedia(MediaObjectRecord media);
    int insertAttachment(ProcurementAttachmentRecord attachment);
    ProcurementAttachmentRecord selectAttachment(@Param("id") Long id);
    ProcurementAttachmentRecord selectAttachmentForUpdate(@Param("id") Long id);
    ProcurementAttachmentRecord selectAttachmentByMedia(@Param("mediaId") Long mediaId);
    MediaObjectRecord selectMedia(@Param("mediaId") Long mediaId);
    MediaObjectRecord selectMediaForUpdate(@Param("mediaId") Long mediaId);
    ProcurementAttachmentVO selectView(@Param("id") Long id);
    List<ProcurementAttachmentVO> selectViews(@Param("ownerType") String ownerType,
                                              @Param("ownerId") Long ownerId);
    long countReady(@Param("ownerType") String ownerType, @Param("ownerId") Long ownerId,
                    @Param("purpose") String purpose);
    long countReadyByOwner(@Param("ownerType") String ownerType, @Param("ownerId") Long ownerId);
    int markMediaDeleted(@Param("mediaId") Long mediaId, @Param("version") Integer version,
                         @Param("actorId") Long actorId, @Param("now") LocalDateTime now);
    int replaceAttachmentMedia(@Param("id") Long id, @Param("version") Integer version,
                               @Param("mediaId") Long mediaId);
    int markAttachmentDeleted(@Param("id") Long id, @Param("version") Integer version,
                              @Param("actorId") Long actorId, @Param("now") LocalDateTime now);
}
