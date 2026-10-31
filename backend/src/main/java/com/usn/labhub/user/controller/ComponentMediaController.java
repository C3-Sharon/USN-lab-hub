package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.component.MediaDeleteDTO;
import com.usn.labhub.user.domain.dto.component.MediaOrderDTO;
import com.usn.labhub.user.domain.dto.component.MediaVersionDTO;
import com.usn.labhub.user.domain.vo.component.MediaObjectVO;
import com.usn.labhub.user.service.ComponentMediaService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequireRoles({"SYSTEM_ADMIN", "STOCK_KEEPER", "TEACHER", "MEMBER"})
public class ComponentMediaController {
    private final ComponentMediaService service;

    public ComponentMediaController(ComponentMediaService service) {
        this.service = service;
    }

    @GetMapping("/components/{componentId}/images")
    public Result<List<MediaObjectVO>> images(@PathVariable Long componentId) {
        return Result.success(service.images(componentId));
    }

    @PostMapping(value = "/components/{componentId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> upload(
            @PathVariable Long componentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestPart("file") MultipartFile file,
            @RequestParam Integer componentVersion,
            @RequestParam Integer sortOrder) {
        return Result.success(service.upload(componentId, idempotencyKey, file, componentVersion, sortOrder));
    }

    @PostMapping(value = "/components/{componentId}/images/{mediaId}/replacement",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> replace(
            @PathVariable Long componentId,
            @PathVariable Long mediaId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestPart("file") MultipartFile file,
            @RequestParam Integer componentVersion,
            @RequestParam Integer mediaVersion) {
        return Result.success(service.replace(componentId, mediaId, idempotencyKey,
                file, componentVersion, mediaVersion));
    }

    @PutMapping("/components/{componentId}/images/{mediaId}/primary")
    public Result<Map<String, Object>> primary(@PathVariable Long componentId,
                                               @PathVariable Long mediaId,
                                               @Valid @RequestBody MediaVersionDTO request) {
        return Result.success(service.setPrimary(componentId, mediaId, request));
    }

    @PutMapping("/components/{componentId}/images/order")
    public Result<Map<String, Object>> order(@PathVariable Long componentId,
                                             @Valid @RequestBody MediaOrderDTO request) {
        return Result.success(service.reorder(componentId, request));
    }

    @DeleteMapping("/components/{componentId}/images/{mediaId}")
    public Result<Map<String, Object>> delete(@PathVariable Long componentId,
                                              @PathVariable Long mediaId,
                                              @Valid @RequestBody MediaDeleteDTO request) {
        return Result.success(service.delete(componentId, mediaId, request));
    }

    @GetMapping("/media/{mediaId}")
    public Result<MediaObjectVO> metadata(@PathVariable Long mediaId) {
        return Result.success(service.metadata(mediaId));
    }

    @GetMapping("/media/{mediaId}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable Long mediaId) {
        ComponentMediaService.MediaContent content = service.content(mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(new InputStreamResource(content.input()));
    }
}
