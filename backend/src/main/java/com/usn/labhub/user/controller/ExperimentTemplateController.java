package com.usn.labhub.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateCreateDTO;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateCopyDTO;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateUpdateDTO;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateVersionDeprecateDTO;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateVersionPublishDTO;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateVersionUpdateDTO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplateDeprecatedVersionVO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplatePublishResultVO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplatePublishedVersionVO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplatePageVO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplateVersionPageVO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplateVersionVO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplateVO;
import com.usn.labhub.user.learning.template.TemplateValidationResult;
import com.usn.labhub.user.service.ExperimentTemplateService;
import com.usn.labhub.user.service.ExperimentTemplateVersionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/experiment/templates")
public class ExperimentTemplateController {
    private final ExperimentTemplateService templateService;
    private final ExperimentTemplateVersionService versionService;

    public ExperimentTemplateController(ExperimentTemplateService templateService,
                                        ExperimentTemplateVersionService versionService) {
        this.templateService = templateService;
        this.versionService = versionService;
    }

    @PostMapping
    public Result<ExperimentTemplateVO> create(@Valid @RequestBody ExperimentTemplateCreateDTO request) {
        return Result.success(templateService.create(request));
    }

    @GetMapping
    public Result<ExperimentTemplatePageVO> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "PUBLISHED") String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "updateTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.success(templateService.list(page, pageSize, scope, projectId, category,
                status, keyword, sortBy, sortOrder));
    }

    @GetMapping("/{templateId}")
    public Result<ExperimentTemplateVO> detail(@PathVariable Long templateId) {
        return Result.success(templateService.detail(templateId));
    }

    @PutMapping("/{templateId}")
    public Result<ExperimentTemplateVO> update(@PathVariable Long templateId,
                                               @Valid @RequestBody ExperimentTemplateUpdateDTO request) {
        return Result.success(templateService.update(templateId, request));
    }

    @PostMapping("/{templateId}/copy")
    public Result<ExperimentTemplateVO> copy(@PathVariable Long templateId,
                                             @Valid @RequestBody ExperimentTemplateCopyDTO request) {
        Result<ExperimentTemplateVO> result = Result.success(templateService.copy(templateId, request));
        result.setMsg("复制成功");
        return result;
    }

    @GetMapping("/{templateId}/versions")
    public Result<ExperimentTemplateVersionPageVO> versions(
            @PathVariable Long templateId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(defaultValue = "ALL") String status) {
        return Result.success(versionService.list(templateId, page, pageSize, status));
    }

    @GetMapping("/{templateId}/versions/published")
    public Result<List<ExperimentTemplatePublishedVersionVO>> publishedVersions(@PathVariable Long templateId) {
        return Result.success(versionService.published(templateId));
    }

    @GetMapping("/{templateId}/versions/{versionId}")
    public Result<ExperimentTemplateVersionVO> versionDetail(@PathVariable Long templateId,
                                                             @PathVariable Long versionId) {
        return Result.success(versionService.detail(templateId, versionId));
    }

    @PutMapping("/{templateId}/versions/{versionId}")
    public Result<ExperimentTemplateVersionVO> updateVersion(
            @PathVariable Long templateId,
            @PathVariable Long versionId,
            @Valid @RequestBody ExperimentTemplateVersionUpdateDTO request) {
        return Result.success(versionService.updateDraft(templateId, versionId, request));
    }

    @PutMapping("/{templateId}/versions/{versionId}/publish")
    public Result<ExperimentTemplatePublishResultVO> publishVersion(
            @PathVariable Long templateId,
            @PathVariable Long versionId,
            @Valid @RequestBody ExperimentTemplateVersionPublishDTO request) {
        Result<ExperimentTemplatePublishResultVO> result = Result.success(
                versionService.publish(templateId, versionId, request));
        result.setMsg("发布成功");
        return result;
    }

    @PutMapping("/{templateId}/versions/{versionId}/deprecate")
    public Result<ExperimentTemplateDeprecatedVersionVO> deprecateVersion(
            @PathVariable Long templateId,
            @PathVariable Long versionId,
            @Valid @RequestBody ExperimentTemplateVersionDeprecateDTO request) {
        Result<ExperimentTemplateDeprecatedVersionVO> result = Result.success(
                versionService.deprecate(templateId, versionId, request));
        result.setMsg("已废弃");
        return result;
    }

    @PostMapping("/{templateId}/versions/{versionId}/validate-data")
    public Result<TemplateValidationResult> validateData(@PathVariable Long templateId,
                                                         @PathVariable Long versionId,
                                                         @RequestBody JsonNode data) {
        TemplateValidationResult validation = versionService.validateData(templateId, versionId, data);
        Result<TemplateValidationResult> result = Result.success(validation);
        result.setMsg(validation.valid() ? "校验通过" : "校验完成");
        return result;
    }
}
