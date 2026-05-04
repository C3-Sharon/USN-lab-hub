package com.usn.labhub.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.usn.labhub.user.domain.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AttendanceMapper extends BaseMapper<AttendanceRecord> {

    // 查今天的第一条记录 (无论是否签退)
    @Select("SELECT * FROM attendance_record WHERE user_id = #{userId} AND check_in_date = CURDATE() LIMIT 1")
    AttendanceRecord selectTodayRecord(@Param("userId") Long userId);

    // 查今天已签到但【未签退】的记录
    @Select("SELECT * FROM attendance_record WHERE user_id = #{userId} AND check_in_date = CURDATE() AND check_out_time IS NULL LIMIT 1")
    AttendanceRecord selectTodayUnfinishedRecord(@Param("userId") Long userId);

    // 统计本周总分钟数 (MySQL YEARWEEK 函数：参数 1 表示周一为一周的第一天)
    @Select("SELECT SUM(duration_minutes) FROM attendance_record " +
            "WHERE user_id = #{userId} AND YEARWEEK(check_in_date, 1) = YEARWEEK(CURDATE(), 1)")
    Integer sumWeekMinutes(@Param("userId") Long userId);

    // 统计本学期总分钟数
    @Select("SELECT SUM(duration_minutes) FROM attendance_record " +
            "WHERE user_id = #{userId} AND semester = #{semester}")
    Integer sumSemesterMinutes(@Param("userId") Long userId, @Param("semester") String semester);
    // 查询今天所有的打卡记录（按时间先后排序）
    @Select("SELECT * FROM attendance_record WHERE user_id = #{userId} AND check_in_date = CURDATE() ORDER BY check_in_time ASC")
    List<AttendanceRecord> selectTodayAllRecords(@Param("userId") Long userId);

    // 修改：查当前【未签退】的记录 (防止一天有多次记录时，查到已经签退的旧数据)
    @Select("SELECT * FROM attendance_record WHERE user_id = #{userId} AND check_out_time IS NULL ORDER BY check_in_time DESC LIMIT 1")
    AttendanceRecord selectCurrentUnfinishedRecord(@Param("userId") Long userId);


}
