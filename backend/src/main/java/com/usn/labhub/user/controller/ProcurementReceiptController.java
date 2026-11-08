package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.procurement.ProcurementReceiptCommandDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementReceiptUpdateDTO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementReceiptVO;
import com.usn.labhub.user.service.ProcurementReceiptService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/procurement-receipts")
@RequireRoles({"SYSTEM_ADMIN", "TEACHER", "STOCK_KEEPER", "MEMBER"})
public class ProcurementReceiptController {
    private final ProcurementReceiptService service;

    public ProcurementReceiptController(ProcurementReceiptService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public Result<ProcurementReceiptVO> receipt(@PathVariable Long id) {
        return Result.success(service.receipt(id));
    }

    @PutMapping("/{id}")
    public Result<ProcurementReceiptVO> update(@PathVariable Long id,
                                               @RequestBody ProcurementReceiptUpdateDTO request) {
        return Result.success(service.update(id, request));
    }

    @PostMapping("/{id}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable Long id,
                                               @RequestHeader("Idempotency-Key") String key,
                                               @RequestBody ProcurementReceiptCommandDTO request) {
        return Result.success(service.confirm(id, key, request));
    }

    @PostMapping("/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable Long id,
                                              @RequestHeader("Idempotency-Key") String key,
                                              @RequestBody ProcurementReceiptCommandDTO request) {
        return Result.success(service.cancel(id, key, request));
    }
}
