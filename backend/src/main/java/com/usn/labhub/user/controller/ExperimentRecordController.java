package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.experiment.ExperimentFeedbackDTO;
import com.usn.labhub.user.domain.dto.experiment.ExperimentPublishDTO;
import com.usn.labhub.user.domain.dto.experiment.ExperimentRecordCreateDTO;
import com.usn.labhub.user.domain.dto.experiment.ExperimentRevisionCreateDTO;
import com.usn.labhub.user.domain.dto.experiment.ExperimentRevisionUpdateDTO;
import com.usn.labhub.user.domain.dto.experiment.ExperimentSubmitDTO;
import com.usn.labhub.user.service.ExperimentRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/experiment-records")
@Tag(name = "实验记录", description = "实验记录、内容修订与导师反馈")
public class ExperimentRecordController {
    private final ExperimentRecordService service;

    public ExperimentRecordController(ExperimentRecordService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "创建实验记录和首个修订")
    public Result<Map<String, Object>> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExperimentRecordCreateDTO request) {
        return Result.success(service.create(idempotencyKey, request));
    }

    @GetMapping
    @Operation(summary = "分页查询可见实验记录")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(service.list(projectId, status, templateId, authorId, page, pageSize));
    }

    @GetMapping("/{recordId}")
    @Operation(summary = "查询实验记录详情")
    public Result<Map<String, Object>> detail(@PathVariable Long recordId) {
        return Result.success(service.detail(recordId));
    }

    @PutMapping("/{recordId}/revisions/{revisionId}")
    @Operation(summary = "保存可编辑修订")
    public Result<Map<String, Object>> updateRevision(
            @PathVariable Long recordId, @PathVariable Long revisionId,
            @Valid @RequestBody ExperimentRevisionUpdateDTO request) {
        return Result.success(service.updateRevision(recordId, revisionId, request));
    }

    @GetMapping("/{recordId}/revisions")
    @Operation(summary = "分页查询修订历史")
    public Result<Map<String, Object>> revisions(
            @PathVariable Long recordId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(service.revisions(recordId, page, pageSize));
    }

    @GetMapping("/{recordId}/revisions/{revisionId}")
    @Operation(summary = "查询修订详情")
    public Result<Map<String, Object>> revisionDetail(
            @PathVariable Long recordId, @PathVariable Long revisionId) {
        return Result.success(service.revisionDetail(recordId, revisionId));
    }

    @PostMapping("/{recordId}/submit")
    @Operation(summary = "提交当前修订")
    public Result<Map<String, Object>> submit(
            @PathVariable Long recordId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExperimentSubmitDTO request) {
        return Result.success(service.submit(recordId, idempotencyKey, request));
    }

    @PostMapping("/{recordId}/feedback")
    @Operation(summary = "添加导师反馈")
    public Result<Map<String, Object>> feedback(
            @PathVariable Long recordId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExperimentFeedbackDTO request) {
        return Result.success(service.feedback(recordId, idempotencyKey, request));
    }

    @GetMapping("/{recordId}/feedback")
    @Operation(summary = "分页查询可见反馈")
    public Result<Map<String, Object>> feedbackList(
            @PathVariable Long recordId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(service.feedbackList(recordId, page, pageSize));
    }

    @PostMapping("/{recordId}/publish")
    @Operation(summary = "发布已批准修订")
    public Result<Map<String, Object>> publish(
            @PathVariable Long recordId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExperimentPublishDTO request) {
        return Result.success(service.publish(recordId, idempotencyKey, request));
    }

    @PostMapping("/{recordId}/revisions")
    @Operation(summary = "从发布版创建新修订")
    public Result<Map<String, Object>> createRevision(
            @PathVariable Long recordId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExperimentRevisionCreateDTO request) {
        return Result.success(service.createRevision(recordId, idempotencyKey, request));
    }
}
