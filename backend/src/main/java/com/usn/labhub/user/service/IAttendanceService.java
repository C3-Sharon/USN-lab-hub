package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.vo.LoginVO;

public interface IAttendanceService {
    LoginVO.AttendanceInfo doAction(Long userId, Integer actionType);
    LoginVO.AttendanceInfo getOverview(Long userId);
}
