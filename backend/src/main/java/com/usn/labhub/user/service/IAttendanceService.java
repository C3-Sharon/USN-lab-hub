package com.usn.labhub.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.usn.labhub.user.domain.dto.AttendanceQueryDTO;
import com.usn.labhub.user.domain.vo.AttendanceExportVO;
import com.usn.labhub.user.domain.vo.AttendanceQueryVO;
import com.usn.labhub.user.domain.vo.LoginVO;

import java.util.List;

public interface IAttendanceService {

    LoginVO.AttendanceInfo doAction(Long userId, Integer actionType);

    LoginVO.AttendanceInfo getOverview(Long userId);

    IPage<AttendanceQueryVO> pageAttendance(AttendanceQueryDTO queryDTO);

    List<AttendanceExportVO> listAttendanceExport(AttendanceQueryDTO queryDTO);
}
