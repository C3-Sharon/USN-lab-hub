package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.project.ProjectCreateDTO;
import com.usn.labhub.user.domain.dto.project.ProjectMemberAddDTO;
import com.usn.labhub.user.domain.vo.project.ProjectDetailVO;
import com.usn.labhub.user.domain.vo.project.ProjectPageVO;
import com.usn.labhub.user.domain.vo.project.ProjectMemberVO;
import com.usn.labhub.user.domain.vo.project.ProjectSummaryVO;
import com.usn.labhub.user.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "项目工作台", description = "项目档案、列表与详情")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @RequireRoles({"SYSTEM_ADMIN", "TEACHER", "MEMBER"})
    @Operation(summary = "创建项目")
    public Result<ProjectSummaryVO> create(@Valid @RequestBody ProjectCreateDTO request) {
        return Result.success(projectService.create(request));
    }

    @GetMapping
    @Operation(summary = "分页查询可见项目")
    public Result<ProjectPageVO> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "updateTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.success(projectService.list(page, pageSize, status, keyword, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询项目详情")
    public Result<ProjectDetailVO> detail(@PathVariable("id") Long projectId) {
        return Result.success(projectService.detail(projectId));
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "添加项目成员")
    public Result<ProjectMemberVO> addMember(@PathVariable("id") Long projectId,
                                             @Valid @RequestBody ProjectMemberAddDTO request) {
        Result<ProjectMemberVO> result = Result.success(projectService.addMember(projectId, request));
        result.setMsg("成员添加成功");
        return result;
    }
}
