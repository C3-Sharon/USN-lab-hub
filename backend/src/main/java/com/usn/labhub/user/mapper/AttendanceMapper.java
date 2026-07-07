package com.usn.labhub.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usn.labhub.user.domain.dto.AttendanceQueryDTO;
import com.usn.labhub.user.domain.entity.AttendanceRecord;
import com.usn.labhub.user.domain.vo.AttendanceExportVO;
import com.usn.labhub.user.domain.vo.AttendanceQueryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AttendanceMapper extends BaseMapper<AttendanceRecord> {

    IPage<AttendanceQueryVO> selectAttendancePage(Page<AttendanceQueryVO> page, @Param("query") AttendanceQueryDTO query);

    List<AttendanceExportVO> selectAttendanceExportList(@Param("query") AttendanceQueryDTO query);

    @Select("SELECT * FROM attendance_record WHERE user_id = #{userId} AND check_in_date = CURDATE() LIMIT 1")
    AttendanceRecord selectTodayRecord(@Param("userId") Long userId);

    @Select("SELECT * FROM attendance_record WHERE user_id = #{userId} AND check_in_date = CURDATE() AND check_out_time IS NULL LIMIT 1")
    AttendanceRecord selectTodayUnfinishedRecord(@Param("userId") Long userId);

    @Select("SELECT SUM(duration_minutes) FROM attendance_record " +
            "WHERE user_id = #{userId} AND YEARWEEK(check_in_date, 1) = YEARWEEK(CURDATE(), 1)")
    Integer sumWeekMinutes(@Param("userId") Long userId);

    @Select("SELECT SUM(duration_minutes) FROM attendance_record " +
            "WHERE user_id = #{userId} AND semester = #{semester}")
    Integer sumSemesterMinutes(@Param("userId") Long userId, @Param("semester") String semester);

    @Select("SELECT * FROM attendance_record WHERE user_id = #{userId} AND check_in_date = CURDATE() ORDER BY check_in_time ASC")
    List<AttendanceRecord> selectTodayAllRecords(@Param("userId") Long userId);

    @Select("SELECT * FROM attendance_record WHERE user_id = #{userId} AND check_out_time IS NULL ORDER BY check_in_time DESC LIMIT 1")
    AttendanceRecord selectCurrentUnfinishedRecord(@Param("userId") Long userId);
}
