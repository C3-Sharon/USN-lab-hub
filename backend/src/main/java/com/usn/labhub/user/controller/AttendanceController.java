package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.service.IAttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/usnhub/attendance")
@Tag(name= "考勤动作接口")
public class AttendanceController {
@Autowired
    private IAttendanceService attendanceService;

    @PostMapping("/action")
    @Operation(summary = "执行签到或签退")
    public Result<LoginVO.AttendanceInfo> doAction(@RequestBody Map<String, Integer> params) {
        Integer actionType = params.get("actionType");
        Long userId= UserContext.getUserId();
        LoginVO.AttendanceInfo newInfo=attendanceService.doAction(userId,actionType);
        return Result.success(newInfo);
    }
}

