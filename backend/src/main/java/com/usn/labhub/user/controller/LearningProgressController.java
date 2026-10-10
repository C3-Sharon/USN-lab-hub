package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.vo.learning.LearningEnrollmentVO;
import com.usn.labhub.user.domain.vo.learning.LearningUnitCompletionVO;
import com.usn.labhub.user.domain.vo.learning.MyLearningRoadmapPageVO;
import com.usn.labhub.user.service.LearningRoadmapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning")
@Tag(name = "学习进度", description = "路线加入、单元完成与个人学习进度")
public class LearningProgressController {

    private final LearningRoadmapService roadmapService;

    public LearningProgressController(LearningRoadmapService roadmapService) {
        this.roadmapService = roadmapService;
    }

    @PostMapping("/roadmaps/{roadmapId}/enroll")
    @Operation(summary = "开始学习路线")
    public Result<LearningEnrollmentVO> enroll(@PathVariable Long roadmapId) {
        return Result.success(roadmapService.enroll(roadmapId));
    }

    @PostMapping("/units/{unitId}/complete")
    @Operation(summary = "标记学习单元完成")
    public Result<LearningUnitCompletionVO> complete(@PathVariable Long unitId) {
        return Result.success(roadmapService.completeUnit(unitId));
    }

    @DeleteMapping("/units/{unitId}/complete")
    @Operation(summary = "取消学习单元完成")
    public Result<LearningUnitCompletionVO> uncomplete(@PathVariable Long unitId) {
        return Result.success(roadmapService.uncompleteUnit(unitId));
    }

    @GetMapping("/my-roadmaps")
    @Operation(summary = "查询我的学习路线")
    public Result<MyLearningRoadmapPageVO> myRoadmaps(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(roadmapService.myRoadmaps(page, pageSize));
    }
}
