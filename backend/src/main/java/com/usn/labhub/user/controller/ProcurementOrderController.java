package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.procurement.ProcurementOrderCreateDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementOrderUpdateDTO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementOrderPageVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementOrderVO;
import com.usn.labhub.user.service.ProcurementOrderService;
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
@RequestMapping("/api/procurement-orders")
@RequireRoles({"SYSTEM_ADMIN", "TEACHER", "STOCK_KEEPER", "MEMBER"})
public class ProcurementOrderController {
    private final ProcurementOrderService service;

    public ProcurementOrderController(ProcurementOrderService service) {
        this.service = service;
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
}
