package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.procurement.ProcurementPolicyUpdateDTO;
import com.usn.labhub.user.domain.entity.procurement.ProcurementPolicyRecord;
import com.usn.labhub.user.service.ProcurementPolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/procurement/policy")
@RequireRoles({"SYSTEM_ADMIN", "TEACHER", "STOCK_KEEPER", "MEMBER"})
public class ProcurementPolicyController {
    private final ProcurementPolicyService service;

    public ProcurementPolicyController(ProcurementPolicyService service) {
        this.service = service;
    }

    @GetMapping
    public Result<ProcurementPolicyRecord> current() {
        return Result.success(service.current());
    }

    @GetMapping("/versions")
    public Result<List<ProcurementPolicyRecord>> versions() {
        return Result.success(service.versions());
    }

    @PutMapping
    public Result<ProcurementPolicyRecord> publish(@RequestBody ProcurementPolicyUpdateDTO request) {
        return Result.success(service.publish(request));
    }
}
