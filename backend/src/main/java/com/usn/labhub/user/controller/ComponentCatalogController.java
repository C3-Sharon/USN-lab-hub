package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.component.CategoryCreateDTO;
import com.usn.labhub.user.domain.dto.component.CategoryUpdateDTO;
import com.usn.labhub.user.domain.dto.component.ComponentCreateDTO;
import com.usn.labhub.user.domain.dto.component.ComponentUpdateDTO;
import com.usn.labhub.user.domain.dto.component.ManufacturerCreateDTO;
import com.usn.labhub.user.domain.dto.component.ManufacturerUpdateDTO;
import com.usn.labhub.user.domain.dto.component.StatusChangeDTO;
import com.usn.labhub.user.domain.dto.component.VersionActionDTO;
import com.usn.labhub.user.domain.vo.component.CatalogPageVO;
import com.usn.labhub.user.domain.vo.component.CategoryVO;
import com.usn.labhub.user.domain.vo.component.ComponentVO;
import com.usn.labhub.user.domain.vo.component.ManufacturerVO;
import com.usn.labhub.user.service.ComponentCatalogService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequireRoles({"SYSTEM_ADMIN", "STOCK_KEEPER", "TEACHER", "MEMBER"})
public class ComponentCatalogController {
    private final ComponentCatalogService service;

    public ComponentCatalogController(ComponentCatalogService service) {
        this.service = service;
    }

    @PostMapping("/component-categories")
    public Result<Map<String, Object>> createCategory(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CategoryCreateDTO request) {
        return Result.success(service.createCategory(idempotencyKey, request));
    }

    @GetMapping("/component-categories")
    public Result<List<CategoryVO>> categories() {
        return Result.success(service.categories());
    }

    @PutMapping("/component-categories/{id}")
    public Result<CategoryVO> updateCategory(@PathVariable Long id,
                                             @Valid @RequestBody CategoryUpdateDTO request) {
        return Result.success(service.updateCategory(id, request));
    }

    @PostMapping("/component-categories/{id}/deactivate")
    public Result<Map<String, Object>> deactivateCategory(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StatusChangeDTO request) {
        return Result.success(service.deactivateCategory(id, idempotencyKey, request));
    }

    @PostMapping("/component-categories/{id}/restore")
    public Result<Map<String, Object>> restoreCategory(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StatusChangeDTO request) {
        return Result.success(service.restoreCategory(id, idempotencyKey, request));
    }

    @PostMapping("/component-manufacturers")
    public Result<Map<String, Object>> createManufacturer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ManufacturerCreateDTO request) {
        return Result.success(service.createManufacturer(idempotencyKey, request));
    }

    @GetMapping("/component-manufacturers")
    public Result<CatalogPageVO<ManufacturerVO>> manufacturers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(service.manufacturers(page, pageSize, status, keyword, sortBy, sortOrder));
    }

    @GetMapping("/component-manufacturers/{id}")
    public Result<ManufacturerVO> manufacturer(@PathVariable Long id) {
        return Result.success(service.manufacturer(id));
    }

    @PutMapping("/component-manufacturers/{id}")
    public Result<ManufacturerVO> updateManufacturer(@PathVariable Long id,
                                                     @Valid @RequestBody ManufacturerUpdateDTO request) {
        return Result.success(service.updateManufacturer(id, request));
    }

    @PostMapping("/component-manufacturers/{id}/deactivate")
    public Result<Map<String, Object>> deactivateManufacturer(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StatusChangeDTO request) {
        return Result.success(service.deactivateManufacturer(id, idempotencyKey, request));
    }

    @PostMapping("/component-manufacturers/{id}/restore")
    public Result<Map<String, Object>> restoreManufacturer(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StatusChangeDTO request) {
        return Result.success(service.restoreManufacturer(id, idempotencyKey, request));
    }

    @PostMapping("/components")
    public Result<Map<String, Object>> createComponent(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ComponentCreateDTO request) {
        return Result.success(service.createComponent(idempotencyKey, request));
    }

    @GetMapping("/components")
    public Result<CatalogPageVO<ComponentVO>> components(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "true") boolean includeDescendants,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long manufacturerId,
            @RequestParam(required = false) String packageType,
            @RequestParam(required = false) String baseUnit,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(service.components(page, pageSize, categoryId, includeDescendants, status,
                manufacturerId, packageType, baseUnit, keyword, sortBy, sortOrder));
    }

    @GetMapping("/components/{id}")
    public Result<ComponentVO> component(@PathVariable Long id) {
        return Result.success(service.component(id));
    }

    @PutMapping("/components/{id}")
    public Result<ComponentVO> updateComponent(@PathVariable Long id,
                                               @Valid @RequestBody ComponentUpdateDTO request) {
        return Result.success(service.updateComponent(id, request));
    }

    @PostMapping("/components/{id}/activate")
    public Result<Map<String, Object>> activateComponent(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody VersionActionDTO request) {
        return Result.success(service.activateComponent(id, idempotencyKey, request));
    }

    @PostMapping("/components/{id}/deactivate")
    public Result<Map<String, Object>> deactivateComponent(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StatusChangeDTO request) {
        return Result.success(service.deactivateComponent(id, idempotencyKey, request));
    }

    @PostMapping("/components/{id}/restore")
    public Result<Map<String, Object>> restoreComponent(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StatusChangeDTO request) {
        return Result.success(service.restoreComponent(id, idempotencyKey, request));
    }
}
