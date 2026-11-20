package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.inventory.InventoryReturnCommandDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryReturnCreateDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryWriteOffDTO;
import com.usn.labhub.user.service.InventoryIssueWorkflowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequireRoles({"SYSTEM_ADMIN", "TEACHER", "STOCK_KEEPER", "MEMBER"})
public class InventoryReturnWriteOffController {
    private final InventoryIssueWorkflowService workflowService;

    public InventoryReturnWriteOffController(InventoryIssueWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/api/inventory-fulfillments/{id}/returns")
    public Result<Map<String, Object>> initiateReturn(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InventoryReturnCreateDTO request) {
        return Result.success(workflowService.initiateReturn(id, idempotencyKey, request));
    }

    @GetMapping("/api/inventory-fulfillments/{id}/returns")
    public Result<List<Map<String, Object>>> listReturns(@PathVariable Long id) {
        return Result.success(workflowService.listReturns(id));
    }

    @PostMapping("/api/inventory-returns/{id}/confirm")
    public Result<Map<String, Object>> confirmReturn(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InventoryReturnCommandDTO request) {
        return Result.success(workflowService.confirmReturn(id, idempotencyKey, request));
    }

    @PostMapping("/api/inventory-returns/{id}/reject")
    public Result<Map<String, Object>> rejectReturn(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InventoryReturnCommandDTO request) {
        return Result.success(workflowService.rejectReturn(id, idempotencyKey, request));
    }

    @PostMapping("/api/inventory/batches/{batchId}/losses")
    public Result<Map<String, Object>> registerLoss(
            @PathVariable Long batchId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InventoryWriteOffDTO request) {
        return Result.success(workflowService.writeOff(batchId, idempotencyKey, "LOSS", request));
    }

    @PostMapping("/api/inventory/batches/{batchId}/scraps")
    public Result<Map<String, Object>> registerScrap(
            @PathVariable Long batchId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InventoryWriteOffDTO request) {
        return Result.success(workflowService.writeOff(batchId, idempotencyKey, "SCRAP", request));
    }
}
