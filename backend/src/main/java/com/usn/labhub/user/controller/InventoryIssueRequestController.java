package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssueCommandDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssueConfirmIssueDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssueReallocateDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssueRequestCreateDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssueRequestUpdateDTO;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssuePageVO;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssueRequestVO;
import com.usn.labhub.user.service.InventoryIssueRequestService;
import com.usn.labhub.user.service.InventoryIssueWorkflowService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory-issue-requests")
@RequireRoles({"SYSTEM_ADMIN", "TEACHER", "STOCK_KEEPER", "MEMBER"})
public class InventoryIssueRequestController {
    private final InventoryIssueRequestService service;
    private final InventoryIssueWorkflowService workflowService;

    public InventoryIssueRequestController(InventoryIssueRequestService service,
                                           InventoryIssueWorkflowService workflowService) {
        this.service = service;
        this.workflowService = workflowService;
    }

    @PostMapping
    public Result<Map<String, Object>> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InventoryIssueRequestCreateDTO request) {
        return Result.success(service.create(idempotencyKey, request));
    }

    @GetMapping
    public Result<InventoryIssuePageVO> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long requesterId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long componentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime submittedTo,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(service.list(page, pageSize, status, requesterId, projectId,
                componentId, submittedFrom, submittedTo, keyword, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<InventoryIssueRequestVO> detail(@PathVariable Long id) {
        return Result.success(service.detail(id));
    }

    @PutMapping("/{id}")
    public Result<InventoryIssueRequestVO> update(@PathVariable Long id,
                                                  @Valid @RequestBody InventoryIssueRequestUpdateDTO request) {
        return Result.success(service.update(id, request));
    }

    @PostMapping("/{id}/submit")
    public Result<Map<String, Object>> submit(@PathVariable Long id,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              @Valid @RequestBody InventoryIssueCommandDTO request) {
        return Result.success(workflowService.submit(id, idempotencyKey, request));
    }

    @PostMapping("/{id}/approve")
    public Result<Map<String, Object>> approve(@PathVariable Long id,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @Valid @RequestBody InventoryIssueCommandDTO request) {
        return Result.success(workflowService.approve(id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    public Result<Map<String, Object>> reject(@PathVariable Long id,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              @Valid @RequestBody InventoryIssueCommandDTO request) {
        return Result.success(workflowService.reject(id, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable Long id,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              @Valid @RequestBody InventoryIssueCommandDTO request) {
        return Result.success(workflowService.cancel(id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reallocate")
    public Result<Map<String, Object>> reallocate(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InventoryIssueReallocateDTO request) {
        return Result.success(workflowService.reallocate(id, idempotencyKey, request));
    }

    @PostMapping("/{id}/confirm-issue")
    public Result<Map<String, Object>> confirmIssue(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-USN-Test-Fault", required = false) String testFaultPoint,
            @Valid @RequestBody InventoryIssueConfirmIssueDTO request) {
        return Result.success(workflowService.confirmIssue(id, idempotencyKey, testFaultPoint, request));
    }
}
