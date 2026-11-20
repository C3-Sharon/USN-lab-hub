package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssueRequestCreateDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssueRequestUpdateDTO;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssuePageVO;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssueRequestVO;
import com.usn.labhub.user.service.InventoryIssueRequestService;
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

    public InventoryIssueRequestController(InventoryIssueRequestService service) {
        this.service = service;
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
}
