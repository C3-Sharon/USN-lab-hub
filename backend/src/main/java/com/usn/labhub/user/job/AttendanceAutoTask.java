package com.usn.labhub.user.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.usn.labhub.user.domain.entity.AttendanceRecord;
import com.usn.labhub.user.mapper.AttendanceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class AttendanceAutoTask {

    @Autowired
    private AttendanceMapper attendanceMapper;

    /**
     * 每天凌晨 00:05 执行一次
     * 找出所有 checkOutTime 为空，且 checkInDate 已经不是今天的记录，自动强行签退到当天的 23:59:59
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void autoCheckOut() {
        log.info("开始执行凌晨自动签退任务...");

        // 1. 查出所有未签退的记录（借助 MyBatis-Plus）
        QueryWrapper<AttendanceRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.isNull("check_out_time");
        List<AttendanceRecord> unfinishedList = attendanceMapper.selectList(queryWrapper);

        LocalDateTime currentNow = LocalDateTime.now();
        int updateCount = 0;

        for (AttendanceRecord record : unfinishedList) {
            // 如果这条记录的签到日期还在今天，说明它是刚才（0点到0点5分之间）签的，跳过
            if (record.getCheckInDate().isEqual(currentNow.toLocalDate())) {
                continue;
            }

            // 构造签到当天的 23:59:59 作为强行签退时间
            LocalDateTime autoOutTime = record.getCheckInDate().atTime(23, 59, 59);
            record.setCheckOutTime(autoOutTime);

            // 计算时长并更新
            long minutes = Duration.between(record.getCheckInTime(), autoOutTime).toMinutes();
            record.setDurationMinutes((int) minutes);

            record.setSource("system_auto");

            attendanceMapper.updateById(record);
            updateCount++;
        }

        log.info("自动签退任务执行完毕，共处理 {} 条遗留记录", updateCount);
    }
}
