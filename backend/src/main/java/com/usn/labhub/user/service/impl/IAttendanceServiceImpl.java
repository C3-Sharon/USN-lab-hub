package com.usn.labhub.user.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.usn.labhub.user.common.utils.RedisUtils;
import com.usn.labhub.user.domain.entity.AttendanceRecord;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.mapper.AttendanceMapper;
import com.usn.labhub.user.service.IAttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class IAttendanceServiceImpl implements IAttendanceService {
@Autowired
  private RedisUtils  redisUtils;
    @Autowired
    private AttendanceMapper attendanceMapper;

    @Override
    @Transactional
    public LoginVO.AttendanceInfo doAction(Long userId, Integer actionType) {
        String lockKey = "lock:attendance:action:" + userId;
        boolean isLock = redisUtils.setIfAbsent(lockKey, "processing", 5);

        if (!isLock) {
            throw new RuntimeException("操作太快了，请稍后再试");
        }
        try{
        if (actionType == 1) {
            AttendanceRecord unfinished = attendanceMapper.selectCurrentUnfinishedRecord(userId);
            if (unfinished != null) {
                throw new RuntimeException("当前已有未签退的记录，请勿重复签到");
            }
            AttendanceRecord record = new AttendanceRecord();
            record.setUserId(userId);
            record.setCheckInTime(LocalDateTime.now());
            record.setCheckInDate(LocalDate.now());
            record.setSource("human");
            Object semesterObj = redisUtils.get("system:current_semester");
            String currentSemester = semesterObj != null ? semesterObj.toString() : "2025-2";
            record.setSemester(currentSemester);
            attendanceMapper.insert(record);
        } else if (actionType == 2) {
            AttendanceRecord record = attendanceMapper.selectCurrentUnfinishedRecord(userId);
            if (record == null) {
                throw new RuntimeException("未找到有效的签到记录,可能已经签退或尚未签到");
            }
            LocalDateTime now = LocalDateTime.now();
            record.setCheckOutTime(now);
            // 计算分钟差 (Java 8 Duration API)
            long minutes = Duration.between(record.getCheckInTime(), now).toMinutes();
            record.setDurationMinutes((int) minutes);

            attendanceMapper.updateById(record);

        } else {
            throw new RuntimeException("无效的动作类型");
        }
            redisUtils.del("attendance:overview:" + userId + ":" + LocalDate.now());

        return this.getOverview(userId);
        }finally {
            redisUtils.del(lockKey);
        }
    }

    @Override
    public LoginVO.AttendanceInfo getOverview(Long userId) {
        // 1. 定义缓存 Key：包含用户 ID 和今天的日期
        String cacheKey = "attendance:overview:" + userId + ":" + LocalDate.now();

        // 2. 尝试从缓存中获取
        Object cachedData = redisUtils.get(cacheKey);
        if (cachedData != null) {
            // 如果使用了自定义的 RedisTemplate 且配置了 JSON 序列化，这里可以直接强转
            return (LoginVO.AttendanceInfo) cachedData;
        }

        // 3. 缓存没命中，执行原本复杂的数据库查询逻辑
        LoginVO.AttendanceInfo info = new LoginVO.AttendanceInfo();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        // 获取今天所有的记录
        List<AttendanceRecord> todayRecordsList = attendanceMapper.selectTodayAllRecords(userId);

        if (todayRecordsList == null || todayRecordsList.isEmpty()) {
            info.setTodayStatus(0); // 完全没签到过
            info.setTodayRecords(new ArrayList<>());
        } else {
            // 1. 设置当天的首尾时间 (取第一条的首时间，和最后一条的尾时间)
            AttendanceRecord firstRecord = todayRecordsList.get(0);
            AttendanceRecord lastRecord = todayRecordsList.get(todayRecordsList.size() - 1);

            info.setCheckInDate(firstRecord.getCheckInDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            info.setCheckInTime(firstRecord.getCheckInTime().format(timeFormatter));

            // 2. 判断当前总体状态（看最后一条记录是否已签退）
            if (lastRecord.getCheckOutTime() == null) {
                info.setTodayStatus(1); // 还在实验室中
            } else {
                info.setTodayStatus(2); // 已经离开了
                info.setCheckOutTime(lastRecord.getCheckOutTime().format(timeFormatter));
            }

            // 3. 封装明细列表给前端渲染
            List<LoginVO.RecordDetail> details = new ArrayList<>();
            for (AttendanceRecord r : todayRecordsList) {
                LoginVO.RecordDetail detail = new LoginVO.RecordDetail();
                detail.setInTime(r.getCheckInTime().format(timeFormatter));
                if (r.getCheckOutTime() != null) {
                    detail.setOutTime(r.getCheckOutTime().format(timeFormatter));
                    detail.setDurationMins(r.getDurationMinutes());
                } else {
                    detail.setOutTime("进行中");
                    detail.setDurationMins(0);
                }
                details.add(detail);
            }
            info.setTodayRecords(details);
        }

        // 统计时长逻辑不变
        Object semesterObj = redisUtils.get("system:current_semester");
        String currentSemester = semesterObj != null ? semesterObj.toString() : "2025-2";
        Integer weekMins = attendanceMapper.sumWeekMinutes(userId);
        Integer semesterMins = attendanceMapper.sumSemesterMinutes(userId, currentSemester);

        info.setWeekHours(weekMins == null ? 0.0 : Math.round((weekMins / 60.0) * 10) / 10.0);
        info.setSemesterHours(semesterMins == null ? 0.0 : Math.round((semesterMins / 60.0) * 10) / 10.0);
       redisUtils.set(cacheKey, info);
        return info;
    }
}
