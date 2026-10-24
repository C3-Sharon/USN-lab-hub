package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.collection.CollectionBatchCreateDTO;
import com.usn.labhub.user.domain.dto.collection.CollectionVersionDTO;
import com.usn.labhub.user.service.CollectionBatchService;
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
public class CollectionBatchController {
    private final CollectionBatchService service;

    public CollectionBatchController(CollectionBatchService service) {
        this.service = service;
    }

    @PostMapping("/collection-plans/{planId}/batches")
    public Result<Map<String, Object>> create(@PathVariable Long planId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CollectionBatchCreateDTO request) {
        return Result.success(service.create(planId, key, request));
    }

    @GetMapping("/collection-plans/{planId}/batches")
    public Result<Map<String, Object>> list(@PathVariable Long planId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(service.list(planId, status, page, pageSize));
    }

    @GetMapping("/collection-batches/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(service.detail(id));
    }

    @PostMapping("/collection-batches/{id}/complete")
    public Result<Map<String, Object>> complete(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CollectionVersionDTO request) {
        return Result.success(service.complete(id, key, request));
    }

    @PostMapping("/collection-batches/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CollectionVersionDTO request) {
        return Result.success(service.cancel(id, key, request));
    }

    @PostMapping("/collection-batches/{id}/archive")
    public Result<Map<String, Object>> archive(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CollectionVersionDTO request) {
        return Result.success(service.archive(id, key, request));
    }
}
