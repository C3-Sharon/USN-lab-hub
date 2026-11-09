package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.procurement.ProcurementAttachmentDeleteDTO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementAttachmentVO;
import com.usn.labhub.user.service.ProcurementAttachmentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequireRoles({"SYSTEM_ADMIN", "STOCK_KEEPER", "TEACHER", "MEMBER"})
public class ProcurementAttachmentController {
    private final ProcurementAttachmentService service;

    public ProcurementAttachmentController(ProcurementAttachmentService service) {
        this.service = service;
    }

    @GetMapping("/procurement-orders/{id}/attachments")
    public Result<List<ProcurementAttachmentVO>> orderAttachments(@PathVariable Long id) {
        return Result.success(service.orderAttachments(id));
    }

    @PostMapping(value = "/procurement-orders/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> uploadOrder(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestPart("file") MultipartFile file,
            @RequestParam String purpose,
            @RequestParam Integer orderVersion,
            @RequestParam(required = false) String invoiceNo,
            @RequestParam(required = false) String note) {
        return Result.success(service.uploadOrder(id, key, file, purpose, orderVersion, invoiceNo, note));
    }

    @GetMapping("/procurement-receipts/{id}/attachments")
    public Result<List<ProcurementAttachmentVO>> receiptAttachments(@PathVariable Long id) {
        return Result.success(service.receiptAttachments(id));
    }

    @PostMapping(value = "/procurement-receipts/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> uploadReceipt(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestPart("file") MultipartFile file,
            @RequestParam String purpose,
            @RequestParam Integer receiptVersion,
            @RequestParam(required = false) String note) {
        return Result.success(service.uploadReceipt(id, key, file, purpose, receiptVersion, note));
    }

    @PostMapping(value = "/procurement-attachments/{id}/replacement",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> replace(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestPart("file") MultipartFile file,
            @RequestParam Integer attachmentVersion,
            @RequestParam Integer mediaVersion,
            @RequestParam Integer ownerVersion) {
        return Result.success(service.replace(id, key, file, attachmentVersion, mediaVersion, ownerVersion));
    }

    @DeleteMapping("/procurement-attachments/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id,
                                              @Valid @RequestBody ProcurementAttachmentDeleteDTO request) {
        return Result.success(service.delete(id, request));
    }
}
