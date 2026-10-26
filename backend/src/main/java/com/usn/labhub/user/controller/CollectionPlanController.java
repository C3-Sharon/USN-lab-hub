package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.collection.CollectionPlanCreateDTO;
import com.usn.labhub.user.domain.dto.collection.CollectionPlanUpdateDTO;
import com.usn.labhub.user.domain.dto.collection.CollectionVersionDTO;
import com.usn.labhub.user.service.CollectionPlanService;
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
@RequestMapping("/api")
public class CollectionPlanController {
    private final CollectionPlanService service;

    public CollectionPlanController(CollectionPlanService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/collection-plans")
    public Result<Map<String, Object>> create(@PathVariable Long projectId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CollectionPlanCreateDTO request) {
        return Result.success(service.create(projectId, key, request));
    }

    @GetMapping("/projects/{projectId}/collection-plans")
    public Result<Map<String, Object>> list(@PathVariable Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(service.list(projectId, status, sourceType, page, pageSize));
    }

    @GetMapping("/collection-plans/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(service.detail(id));
    }

    @PutMapping("/collection-plans/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id,
            @Valid @RequestBody CollectionPlanUpdateDTO request) {
        return Result.success(service.update(id, request));
    }

    @PostMapping("/collection-plans/{id}/ready")
    public Result<Map<String, Object>> ready(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CollectionVersionDTO request) {
        return Result.success(service.ready(id, key, request));
    }

    @PostMapping("/collection-plans/{id}/archive")
    public Result<Map<String, Object>> archive(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CollectionVersionDTO request) {
        return Result.success(service.archive(id, key, request));
    }

    @PostMapping("/collection-plans/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CollectionVersionDTO request) {
        return Result.success(service.cancel(id, key, request));
    }
}
