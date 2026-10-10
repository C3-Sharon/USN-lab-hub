package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.project.MilestoneCreateDTO;
import com.usn.labhub.user.domain.dto.project.MilestoneStatusUpdateDTO;
import com.usn.labhub.user.domain.vo.project.MilestoneStatusVO;
import com.usn.labhub.user.domain.vo.project.MilestoneVO;
import com.usn.labhub.user.service.ProjectMilestoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/projects/{projectId}/milestones")
@Tag(name = "项目里程碑", description = "项目里程碑创建、列表与状态")
public class ProjectMilestoneController {

    private final ProjectMilestoneService milestoneService;

    public ProjectMilestoneController(ProjectMilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    @PostMapping
    @Operation(summary = "创建里程碑")
    public Result<MilestoneVO> create(@PathVariable Long projectId,
                                      @Valid @RequestBody MilestoneCreateDTO request) {
        return Result.success(milestoneService.create(projectId, request));
    }

    @GetMapping
    @Operation(summary = "查询里程碑列表")
    public Result<List<MilestoneVO>> list(@PathVariable Long projectId,
                                          @RequestParam(required = false) String status) {
        return Result.success(milestoneService.list(projectId, status));
    }

    @PutMapping("/{milestoneId}/status")
    @Operation(summary = "更新里程碑状态")
    public Result<MilestoneStatusVO> updateStatus(@PathVariable Long projectId,
                                                   @PathVariable Long milestoneId,
                                                   @Valid @RequestBody MilestoneStatusUpdateDTO request) {
        return Result.success(milestoneService.updateStatus(projectId, milestoneId, request));
    }
}
