package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.procurement.ProcurementOrderCreateDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementOrderUpdateDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementApprovalDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementCancelDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementMarkOrderedDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementOrderCommandDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementReceiptCreateDTO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementOrderPageVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementOrderVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementReceiptVO;
import com.usn.labhub.user.service.ProcurementOrderService;
import com.usn.labhub.user.service.ProcurementWorkflowService;
import com.usn.labhub.user.service.ProcurementReceiptService;
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
import java.util.List;

@RestController
@RequestMapping("/api/procurement-orders")
@RequireRoles({"SYSTEM_ADMIN", "TEACHER", "STOCK_KEEPER", "MEMBER"})
public class ProcurementOrderController {
    private final ProcurementOrderService service;
    private final ProcurementWorkflowService workflowService;
    private final ProcurementReceiptService receiptService;

    public ProcurementOrderController(ProcurementOrderService service,
                                      ProcurementWorkflowService workflowService,
                                      ProcurementReceiptService receiptService) {
        this.service = service;
        this.workflowService = workflowService;
        this.receiptService = receiptService;
    }

    @PostMapping
    public Result<Map<String, Object>> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ProcurementOrderCreateDTO request) {
        return Result.success(service.create(idempotencyKey, request));
    }

    @GetMapping
    public Result<ProcurementOrderPageVO> orders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long requesterId,
            @RequestParam(required = false) String supplierKeyword,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(service.orders(page, pageSize, status, projectId, requesterId,
                supplierKeyword, keyword, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<ProcurementOrderVO> order(@PathVariable Long id) {
        return Result.success(service.order(id));
    }

    @PutMapping("/{id}")
    public Result<ProcurementOrderVO> update(@PathVariable Long id,
                                             @Valid @RequestBody ProcurementOrderUpdateDTO request) {
        return Result.success(service.update(id, request));
    }

    @PostMapping("/{id}/submit")
    public Result<Map<String, Object>> submit(@PathVariable Long id,
                                              @RequestHeader("Idempotency-Key") String key,
                                              @RequestBody ProcurementOrderCommandDTO request) {
        return Result.success(workflowService.submit(id, key, request));
    }

    @PostMapping("/{id}/approve")
    public Result<Map<String, Object>> approve(@PathVariable Long id,
                                               @RequestHeader("Idempotency-Key") String key,
                                               @RequestBody ProcurementApprovalDTO request) {
        return Result.success(workflowService.approve(id, key, request));
    }

    @PostMapping("/{id}/reject")
    public Result<Map<String, Object>> reject(@PathVariable Long id,
                                              @RequestHeader("Idempotency-Key") String key,
                                              @RequestBody ProcurementApprovalDTO request) {
        return Result.success(workflowService.reject(id, key, request));
    }

    @PostMapping("/{id}/mark-ordered")
    public Result<Map<String, Object>> markOrdered(@PathVariable Long id,
                                                   @RequestHeader("Idempotency-Key") String key,
                                                   @RequestBody ProcurementMarkOrderedDTO request) {
        return Result.success(workflowService.markOrdered(id, key, request));
    }

    @PostMapping("/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable Long id,
                                              @RequestHeader("Idempotency-Key") String key,
                                              @RequestBody ProcurementCancelDTO request) {
        return Result.success(workflowService.cancel(id, key, request));
    }

    @PostMapping("/{id}/cancel-remaining")
    public Result<Map<String, Object>> cancelRemaining(@PathVariable Long id,
                                                       @RequestHeader("Idempotency-Key") String key,
                                                       @RequestBody ProcurementCancelDTO request) {
        return Result.success(workflowService.cancelRemaining(id, key, request));
    }

    @PostMapping("/{id}/complete")
    public Result<Map<String, Object>> complete(@PathVariable Long id,
                                                @RequestHeader("Idempotency-Key") String key,
                                                @RequestBody ProcurementOrderCommandDTO request) {
        return Result.success(workflowService.complete(id, key, request));
    }

    @PostMapping("/{id}/receipts")
    public Result<Map<String, Object>> createReceipt(@PathVariable Long id,
                                                      @RequestHeader("Idempotency-Key") String key,
                                                      @RequestBody ProcurementReceiptCreateDTO request) {
        return Result.success(receiptService.create(id, key, request));
    }

    @GetMapping("/{id}/receipts")
    public Result<List<ProcurementReceiptVO>> receipts(@PathVariable Long id) {
        return Result.success(receiptService.receipts(id));
    }
}
