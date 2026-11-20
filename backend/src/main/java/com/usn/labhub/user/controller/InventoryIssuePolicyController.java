package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssuePolicyUpdateDTO;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssuePolicyVO;
import com.usn.labhub.user.service.InventoryIssuePolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/issue-policy")
@RequireRoles({"SYSTEM_ADMIN", "TEACHER", "STOCK_KEEPER", "MEMBER"})
public class InventoryIssuePolicyController {
    private final InventoryIssuePolicyService service;

    public InventoryIssuePolicyController(InventoryIssuePolicyService service) {
        this.service = service;
    }

    @GetMapping
    public Result<InventoryIssuePolicyVO> current() {
        return Result.success(service.current());
    }

    @GetMapping("/versions")
    public Result<List<InventoryIssuePolicyVO>> versions() {
        return Result.success(service.versions());
    }

    @PutMapping
    public Result<InventoryIssuePolicyVO> publish(@RequestBody InventoryIssuePolicyUpdateDTO request) {
        return Result.success(service.publish(request));
    }
}
