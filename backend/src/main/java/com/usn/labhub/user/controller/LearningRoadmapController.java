package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.learning.LearningRoadmapCreateDTO;
import com.usn.labhub.user.domain.dto.learning.LearningRoadmapStatusUpdateDTO;
import com.usn.labhub.user.domain.dto.learning.LearningRoadmapUpdateDTO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapCreateVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapDetailVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapPageVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapStatusVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapSummaryVO;
import com.usn.labhub.user.service.LearningRoadmapService;
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
@RequestMapping("/api/learning/roadmaps")
@Tag(name = "学习路线", description = "学习路线创建、查询、编辑与状态管理")
public class LearningRoadmapController {

    private final LearningRoadmapService roadmapService;

    public LearningRoadmapController(LearningRoadmapService roadmapService) {
        this.roadmapService = roadmapService;
    }

    @PostMapping
    @Operation(summary = "创建学习路线")
    public Result<LearningRoadmapCreateVO> create(
            @Valid @RequestBody LearningRoadmapCreateDTO request) {
        return Result.success(roadmapService.create(request));
    }

    @GetMapping
    @Operation(summary = "分页查询学习路线")
    public Result<LearningRoadmapPageVO> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "sortOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder) {
        return Result.success(roadmapService.list(
                page, pageSize, status, difficulty, sortBy, sortOrder));
    }

    @GetMapping("/{roadmapId}")
    @Operation(summary = "查询学习路线详情")
    public Result<LearningRoadmapDetailVO> detail(@PathVariable Long roadmapId) {
        return Result.success(roadmapService.detail(roadmapId));
    }

    @PutMapping("/{roadmapId}/status")
    @Operation(summary = "更新学习路线状态")
    public Result<LearningRoadmapStatusVO> updateStatus(
            @PathVariable Long roadmapId,
            @Valid @RequestBody LearningRoadmapStatusUpdateDTO request) {
        return Result.success(roadmapService.updateStatus(roadmapId, request));
    }

    @PutMapping("/{roadmapId}")
    @Operation(summary = "编辑学习路线基本信息")
    public Result<LearningRoadmapSummaryVO> update(
            @PathVariable Long roadmapId,
            @Valid @RequestBody LearningRoadmapUpdateDTO request) {
        return Result.success(roadmapService.update(roadmapId, request));
    }
}
