package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.vo.procurement.InventoryBalanceVO;
import com.usn.labhub.user.domain.vo.procurement.InventoryBatchVO;
import com.usn.labhub.user.domain.vo.procurement.InventoryMovementVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementPageVO;
import com.usn.labhub.user.service.InventoryQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@RequireRoles({"SYSTEM_ADMIN", "TEACHER", "STOCK_KEEPER", "MEMBER"})
public class InventoryController {
    private final InventoryQueryService service;

    public InventoryController(InventoryQueryService service) {
        this.service = service;
    }

    @GetMapping("/components/{componentId}")
    public Result<InventoryBalanceVO> balance(@PathVariable Long componentId) {
        return Result.success(service.balance(componentId));
    }

    @GetMapping("/components/{componentId}/batches")
    public Result<ProcurementPageVO<InventoryBatchVO>> batches(
            @PathVariable Long componentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(service.batches(componentId, page, pageSize));
    }

    @GetMapping("/movements")
    public Result<ProcurementPageVO<InventoryMovementVO>> movements(
            @RequestParam(required = false) Long componentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(service.movements(componentId, page, pageSize));
    }
}
