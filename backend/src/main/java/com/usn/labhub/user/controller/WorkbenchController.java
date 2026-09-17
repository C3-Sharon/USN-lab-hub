package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.vo.WorkbenchOverviewVO;
import com.usn.labhub.user.service.WorkbenchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workbench")
@Tag(name = "个人工作台", description = "登录用户首页聚合信息")
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    public WorkbenchController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @GetMapping("/overview")
    @Operation(summary = "查询个人工作台首页")
    public Result<WorkbenchOverviewVO> overview() {
        return Result.success(workbenchService.getOverview(UserContext.getUserId()));
    }
}
