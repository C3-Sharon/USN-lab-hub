package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.learning.LearningStageCreateDTO;
import com.usn.labhub.user.domain.dto.learning.LearningStageUpdateDTO;
import com.usn.labhub.user.domain.dto.learning.LearningUnitCreateDTO;
import com.usn.labhub.user.domain.dto.learning.LearningUnitUpdateDTO;
import com.usn.labhub.user.domain.vo.learning.LearningStageSummaryVO;
import com.usn.labhub.user.domain.vo.learning.LearningStageVO;
import com.usn.labhub.user.domain.vo.learning.LearningUnitVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/learning")
@Tag(name = "学习路线结构", description = "学习阶段和学习单元管理")
public class LearningStructureController {

    private final LearningRoadmapService roadmapService;

    public LearningStructureController(LearningRoadmapService roadmapService) {
        this.roadmapService = roadmapService;
    }

    @PostMapping("/roadmaps/{roadmapId}/stages")
    @Operation(summary = "创建学习阶段")
    public Result<LearningStageSummaryVO> createStage(
            @PathVariable Long roadmapId,
            @Valid @RequestBody LearningStageCreateDTO request) {
        return Result.success(roadmapService.createStage(roadmapId, request));
    }

    @GetMapping("/roadmaps/{roadmapId}/stages")
    @Operation(summary = "查询阶段和单元")
    public Result<List<LearningStageVO>> listStages(@PathVariable Long roadmapId) {
        return Result.success(roadmapService.listStages(roadmapId));
    }

    @PutMapping("/stages/{stageId}")
    @Operation(summary = "编辑学习阶段")
    public Result<LearningStageSummaryVO> updateStage(
            @PathVariable Long stageId,
            @Valid @RequestBody LearningStageUpdateDTO request) {
        return Result.success(roadmapService.updateStage(stageId, request));
    }

    @PostMapping("/stages/{stageId}/units")
    @Operation(summary = "创建学习单元")
    public Result<LearningUnitVO> createUnit(
            @PathVariable Long stageId,
            @Valid @RequestBody LearningUnitCreateDTO request) {
        return Result.success(roadmapService.createUnit(stageId, request));
    }

    @PutMapping("/units/{unitId}")
    @Operation(summary = "编辑学习单元")
    public Result<LearningUnitVO> updateUnit(
            @PathVariable Long unitId,
            @Valid @RequestBody LearningUnitUpdateDTO request) {
        return Result.success(roadmapService.updateUnit(unitId, request));
    }
}
