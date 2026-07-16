package com.usn.labhub.user.controller.iot;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.vo.iot.IotPublicProjectVO;
import com.usn.labhub.user.service.iot.IotApiException;
import com.usn.labhub.user.service.iot.IotPublicProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/iot/public")
@Tag(name = "IoT 公开展示", description = "无需登录的只读项目展示接口")
public class IotPublicController {

    private final IotPublicProjectService publicProjectService;

    public IotPublicController(IotPublicProjectService publicProjectService) {
        this.publicProjectService = publicProjectService;
    }

    @GetMapping("/projects/{projectCode}")
    @Operation(summary = "查询公开项目、PM-001 最新数据、趋势和健康评分")
    public ResponseEntity<Result<?>> project(
            @PathVariable String projectCode,
            @RequestParam(required = false) Integer trendMinutes,
            @RequestParam(required = false) Integer pointCount) {
        try {
            IotPublicProjectVO project = publicProjectService.getProject(projectCode, trendMinutes, pointCount);
            return ResponseEntity.ok(Result.success(project));
        } catch (IotApiException e) {
            HttpStatus status = HttpStatus.resolve(e.getCode());
            return ResponseEntity.status(status == null ? HttpStatus.BAD_REQUEST : status)
                    .body(Result.error(e.getCode(), e.getMessage()));
        } catch (RuntimeException e) {
            log.error("公开项目聚合数据查询失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(500, "系统繁忙，请稍后重试"));
        }
    }
}
