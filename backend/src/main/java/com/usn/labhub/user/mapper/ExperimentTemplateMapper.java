package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.learning.ExperimentTemplateAccessRecord;
import com.usn.labhub.user.domain.entity.learning.ExperimentTemplateRecord;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplateVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExperimentTemplateMapper {
    int insertTemplate(ExperimentTemplateRecord template);

    int insertInitialDraft(@Param("templateId") Long templateId, @Param("createdBy") Long createdBy,
                           @Param("now") LocalDateTime now);

    long countVisible(@Param("userId") Long userId, @Param("globalManager") boolean globalManager,
                      @Param("scope") String scope, @Param("projectId") Long projectId,
                      @Param("category") String category, @Param("status") String status,
                      @Param("keyword") String keyword);

    List<ExperimentTemplateVO> selectVisible(@Param("userId") Long userId,
                                             @Param("globalManager") boolean globalManager,
                                             @Param("scope") String scope, @Param("projectId") Long projectId,
                                             @Param("category") String category, @Param("status") String status,
                                             @Param("keyword") String keyword,
                                             @Param("sortColumn") String sortColumn,
                                             @Param("sortOrder") String sortOrder,
                                             @Param("offset") int offset, @Param("pageSize") int pageSize);

    ExperimentTemplateVO selectVisibleDetail(@Param("templateId") Long templateId,
                                             @Param("userId") Long userId,
                                             @Param("globalManager") boolean globalManager);

    ExperimentTemplateAccessRecord selectAccess(@Param("templateId") Long templateId,
                                                 @Param("userId") Long userId);

    int updateMetadata(@Param("templateId") Long templateId, @Param("title") String title,
                       @Param("description") String description, @Param("category") String category,
                       @Param("now") LocalDateTime now);
}
