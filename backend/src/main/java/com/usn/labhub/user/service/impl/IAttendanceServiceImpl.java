package com.usn.labhub.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usn.labhub.user.common.utils.RedisUtils;
import com.usn.labhub.user.domain.dto.AttendanceQueryDTO;
import com.usn.labhub.user.domain.entity.AttendanceRecord;
import com.usn.labhub.user.domain.vo.AttendanceExportVO;
import com.usn.labhub.user.domain.vo.AttendanceQueryVO;
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
    private RedisUtils redisUtils;

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
        try {
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
                record.setSemester(semesterObj != null ? semesterObj.toString() : "2025-2");
                attendanceMapper.insert(record);
            } else if (actionType == 2) {
                AttendanceRecord record = attendanceMapper.selectCurrentUnfinishedRecord(userId);
                if (record == null) {
                    throw new RuntimeException("未找到有效的签到记录，可能已经签退或尚未签到");
                }
                LocalDateTime now = LocalDateTime.now();
                record.setCheckOutTime(now);
                record.setDurationMinutes((int) Duration.between(record.getCheckInTime(), now).toMinutes());
                attendanceMapper.updateById(record);
            } else {
                throw new RuntimeException("无效的动作类型");
            }
            redisUtils.del("attendance:overview:" + userId + ":" + LocalDate.now());
            return getOverview(userId);
        } finally {
            redisUtils.del(lockKey);
        }
    }

    @Override
    public LoginVO.AttendanceInfo getOverview(Long userId) {
        String cacheKey = "attendance:overview:" + userId + ":" + LocalDate.now();
        Object cachedData = redisUtils.get(cacheKey);
        if (cachedData != null) {
            return (LoginVO.AttendanceInfo) cachedData;
        }

        LoginVO.AttendanceInfo info = new LoginVO.AttendanceInfo();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        List<AttendanceRecord> todayRecordsList = attendanceMapper.selectTodayAllRecords(userId);

        if (todayRecordsList == null || todayRecordsList.isEmpty()) {
            info.setTodayStatus(0);
            info.setTodayRecords(new ArrayList<>());
        } else {
            AttendanceRecord firstRecord = todayRecordsList.get(0);
            AttendanceRecord lastRecord = todayRecordsList.get(todayRecordsList.size() - 1);

            info.setCheckInDate(firstRecord.getCheckInDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            info.setCheckInTime(firstRecord.getCheckInTime().format(timeFormatter));

            if (lastRecord.getCheckOutTime() == null) {
                info.setTodayStatus(1);
            } else {
                info.setTodayStatus(2);
                info.setCheckOutTime(lastRecord.getCheckOutTime().format(timeFormatter));
            }

            List<LoginVO.RecordDetail> details = new ArrayList<>();
            for (AttendanceRecord record : todayRecordsList) {
                LoginVO.RecordDetail detail = new LoginVO.RecordDetail();
                detail.setInTime(record.getCheckInTime().format(timeFormatter));
                if (record.getCheckOutTime() != null) {
                    detail.setOutTime(record.getCheckOutTime().format(timeFormatter));
                    detail.setDurationMins(record.getDurationMinutes());
                } else {
                    detail.setOutTime("进行中");
                    detail.setDurationMins(0);
                }
                details.add(detail);
            }
            info.setTodayRecords(details);
        }

        Object semesterObj = redisUtils.get("system:current_semester");
        String currentSemester = semesterObj != null ? semesterObj.toString() : "2025-2";
        Integer weekMins = attendanceMapper.sumWeekMinutes(userId);
        Integer semesterMins = attendanceMapper.sumSemesterMinutes(userId, currentSemester);
        info.setWeekHours(weekMins == null ? 0.0 : Math.round((weekMins / 60.0) * 10) / 10.0);
        info.setSemesterHours(semesterMins == null ? 0.0 : Math.round((semesterMins / 60.0) * 10) / 10.0);
        redisUtils.set(cacheKey, info);
        return info;
    }

    @Override
    public IPage<AttendanceQueryVO> pageAttendance(AttendanceQueryDTO queryDTO) {
        long pageNo = queryDTO.getPageNo() == null ? 1L : queryDTO.getPageNo();
        long pageSize = queryDTO.getPageSize() == null ? 10L : queryDTO.getPageSize();
        Page<AttendanceQueryVO> page = new Page<>(pageNo, pageSize);
        return attendanceMapper.selectAttendancePage(page, queryDTO);
    }

    @Override
    public List<AttendanceExportVO> listAttendanceExport(AttendanceQueryDTO queryDTO) {
        return attendanceMapper.selectAttendanceExportList(queryDTO);
    }
}
