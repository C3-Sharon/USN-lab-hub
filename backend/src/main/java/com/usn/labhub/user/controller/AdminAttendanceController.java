package com.usn.labhub.user.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.AttendanceQueryDTO;
import com.usn.labhub.user.domain.vo.AttendanceExportVO;
import com.usn.labhub.user.domain.vo.AttendanceQueryVO;
import com.usn.labhub.user.service.IAttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/admin/attendance")
@Tag(name = "考勤总列表", description = "考勤分页多条件检索与 Excel 导出")
public class AdminAttendanceController {

    @Autowired
    private IAttendanceService attendanceService;

    @PostMapping("/page")
    @Operation(summary = "分页多条件查询考勤记录")
    public Result<IPage<AttendanceQueryVO>> page(@Valid @RequestBody AttendanceQueryDTO queryDTO) {
        return Result.success(attendanceService.pageAttendance(queryDTO));
    }

    @PostMapping("/export")
    @Operation(summary = "导出考勤记录 Excel")
    public Result<Void> export(@Valid @RequestBody AttendanceQueryDTO queryDTO, HttpServletResponse response) throws IOException {
        List<AttendanceExportVO> records = attendanceService.listAttendanceExport(queryDTO);
        String fileName = URLEncoder.encode("考勤总列表-" + LocalDate.now(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), AttendanceExportVO.class)
                .sheet("考勤总列表")
                .doWrite(records);
        return null;
    }
}
