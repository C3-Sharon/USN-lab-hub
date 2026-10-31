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
}
