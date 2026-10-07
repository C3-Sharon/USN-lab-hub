package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.project.TaskCreateDTO;
import com.usn.labhub.user.domain.dto.project.TaskStatusUpdateDTO;
import com.usn.labhub.user.domain.dto.project.TaskUpdateDTO;
import com.usn.labhub.user.domain.vo.project.TaskPageVO;
import com.usn.labhub.user.domain.vo.project.TaskStatusVO;
import com.usn.labhub.user.domain.vo.project.TaskVO;
import com.usn.labhub.user.service.ProjectTaskService;
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

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@Tag(name = "项目任务", description = "项目任务创建、查询、编辑与状态变更")
public class ProjectTaskController {

    private final ProjectTaskService taskService;

    public ProjectTaskController(ProjectTaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @Operation(summary = "创建任务")
    public Result<TaskVO> create(@PathVariable Long projectId, @Valid @RequestBody TaskCreateDTO request) {
        return Result.success(taskService.create(projectId, request));
    }

    @GetMapping
    @Operation(summary = "查询任务列表")
    public Result<TaskPageVO> list(@PathVariable Long projectId,
                                   @RequestParam(required = false) Long milestoneId,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) Long assigneeUserId,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int pageSize,
                                   @RequestParam(defaultValue = "createTime") String sortBy,
                                   @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.success(taskService.list(projectId, milestoneId, status, assigneeUserId,
                page, pageSize, sortBy, sortOrder));
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "查询任务详情")
    public Result<TaskVO> detail(@PathVariable Long projectId, @PathVariable Long taskId) {
        return Result.success(taskService.detail(projectId, taskId));
    }

    @PutMapping("/{taskId}")
    @Operation(summary = "编辑任务基本信息")
    public Result<TaskVO> update(@PathVariable Long projectId, @PathVariable Long taskId,
                                 @Valid @RequestBody TaskUpdateDTO request) {
        return Result.success(taskService.update(projectId, taskId, request));
    }

    @PutMapping("/{taskId}/status")
    @Operation(summary = "更新任务状态")
    public Result<TaskStatusVO> updateStatus(@PathVariable Long projectId, @PathVariable Long taskId,
                                             @Valid @RequestBody TaskStatusUpdateDTO request) {
        return Result.success(taskService.updateStatus(projectId, taskId, request));
    }
}
