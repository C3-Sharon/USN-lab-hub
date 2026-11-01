package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.component.ManualSpecSnapshotDTO;
import com.usn.labhub.user.domain.dto.component.SpecCandidateConfirmDTO;
import com.usn.labhub.user.domain.dto.component.SpecCandidateCreateDTO;
import com.usn.labhub.user.domain.dto.component.SpecCandidateRejectDTO;
import com.usn.labhub.user.domain.vo.component.CatalogPageVO;
import com.usn.labhub.user.domain.vo.component.SpecCandidateVO;
import com.usn.labhub.user.domain.vo.component.SpecSnapshotVO;
import com.usn.labhub.user.service.ComponentSpecificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequireRoles({"SYSTEM_ADMIN", "STOCK_KEEPER", "TEACHER", "MEMBER"})
public class ComponentSpecificationController {
    private final ComponentSpecificationService service;

    public ComponentSpecificationController(ComponentSpecificationService service) {
        this.service = service;
    }

    @PostMapping("/components/{componentId}/spec-candidates")
    public Result<Map<String, Object>> createCandidate(
            @PathVariable Long componentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SpecCandidateCreateDTO request) {
        return Result.success(service.createCandidate(componentId, idempotencyKey, request));
    }

    @GetMapping("/components/{componentId}/spec-candidates")
    public Result<CatalogPageVO<SpecCandidateVO>> candidates(
            @PathVariable Long componentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(service.candidates(componentId, page, pageSize, status));
    }

    @GetMapping("/component-spec-candidates/{candidateId}")
    public Result<SpecCandidateVO> candidate(@PathVariable Long candidateId) {
        return Result.success(service.candidate(candidateId));
    }

    @PostMapping("/component-spec-candidates/{candidateId}/confirm")
    public Result<Map<String, Object>> confirmCandidate(
            @PathVariable Long candidateId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SpecCandidateConfirmDTO request) {
        return Result.success(service.confirmCandidate(candidateId, idempotencyKey, request));
    }

    @PostMapping("/component-spec-candidates/{candidateId}/reject")
    public Result<Map<String, Object>> rejectCandidate(
            @PathVariable Long candidateId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SpecCandidateRejectDTO request) {
        return Result.success(service.rejectCandidate(candidateId, idempotencyKey, request));
    }

    @PostMapping("/components/{componentId}/spec-snapshots/manual")
    public Result<Map<String, Object>> createManualSnapshot(
            @PathVariable Long componentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ManualSpecSnapshotDTO request) {
        return Result.success(service.createManualSnapshot(componentId, idempotencyKey, request));
    }

    @GetMapping("/components/{componentId}/spec-snapshots")
    public Result<CatalogPageVO<SpecSnapshotVO>> snapshots(
            @PathVariable Long componentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(service.snapshots(componentId, page, pageSize));
    }

    @GetMapping("/component-spec-snapshots/{snapshotId}")
    public Result<SpecSnapshotVO> snapshot(@PathVariable Long snapshotId) {
        return Result.success(service.snapshot(snapshotId));
    }
}
