package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateCreateDTO;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateUpdateDTO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplatePageVO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplateVO;
import com.usn.labhub.user.service.ExperimentTemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiment/templates")
public class ExperimentTemplateController {
    private final ExperimentTemplateService templateService;

    public ExperimentTemplateController(ExperimentTemplateService templateService) {
        this.templateService = templateService;
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
}
