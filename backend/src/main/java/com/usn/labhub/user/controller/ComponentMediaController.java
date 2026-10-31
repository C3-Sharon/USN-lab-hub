package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.vo.component.MediaObjectVO;
import com.usn.labhub.user.service.ComponentMediaService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
