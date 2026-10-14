package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.learning.ExperimentTemplateAccessRecord;
import com.usn.labhub.user.domain.entity.learning.ExperimentTemplateRecord;
import com.usn.labhub.user.domain.entity.learning.ExperimentTemplateVersionRecord;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplatePublishedVersionVO;
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

    long countVersions(@Param("templateId") Long templateId, @Param("status") String status,
                       @Param("includeDraft") boolean includeDraft);

    List<ExperimentTemplateVersionRecord> selectVersions(@Param("templateId") Long templateId,
                                                         @Param("status") String status,
                                                         @Param("includeDraft") boolean includeDraft,
                                                         @Param("offset") int offset,
                                                         @Param("pageSize") int pageSize);

    ExperimentTemplateVersionRecord selectVersion(@Param("templateId") Long templateId,
                                                   @Param("versionId") Long versionId);

    ExperimentTemplateVersionRecord selectPublishedVersionForAssociation(
            @Param("versionId") Long versionId);

    int updateDraft(@Param("templateId") Long templateId, @Param("versionId") Long versionId,
                    @Param("schemaJson") String schemaJson, @Param("fieldCount") int fieldCount,
                    @Param("changelog") String changelog, @Param("now") LocalDateTime now);

    int publishDraft(@Param("templateId") Long templateId, @Param("versionId") Long versionId,
                     @Param("version") String version, @Param("versionCode") int versionCode,
                     @Param("changelog") String changelog, @Param("publishedBy") Long publishedBy,
                     @Param("now") LocalDateTime now);

    int insertDraftVersion(ExperimentTemplateVersionRecord version);

    int updateTemplateAfterPublish(@Param("templateId") Long templateId,
                                   @Param("latestVersionId") Long latestVersionId,
                                   @Param("latestVersion") String latestVersion,
                                   @Param("now") LocalDateTime now);

    int deprecatePublished(@Param("templateId") Long templateId, @Param("versionId") Long versionId,
                           @Param("reason") String reason, @Param("deprecatedBy") Long deprecatedBy,
                           @Param("now") LocalDateTime now);

    ExperimentTemplateVersionRecord selectLatestPublished(@Param("templateId") Long templateId);

    int updateTemplateAfterDeprecate(@Param("templateId") Long templateId,
                                     @Param("latestVersionId") Long latestVersionId,
                                     @Param("latestVersion") String latestVersion,
                                     @Param("now") LocalDateTime now);

    List<ExperimentTemplatePublishedVersionVO> selectPublishedVersions(@Param("templateId") Long templateId);
}
