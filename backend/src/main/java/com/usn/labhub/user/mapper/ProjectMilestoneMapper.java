package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.project.MilestoneRecord;
import com.usn.labhub.user.domain.vo.project.MilestoneVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProjectMilestoneMapper {

    int insert(MilestoneRecord milestone);

    MilestoneVO selectById(@Param("projectId") Long projectId,
                           @Param("milestoneId") Long milestoneId);

    List<MilestoneVO> selectByProject(@Param("projectId") Long projectId,
                                      @Param("status") String status);

    int updateStatus(@Param("projectId") Long projectId,
                     @Param("milestoneId") Long milestoneId,
                     @Param("currentStatus") String currentStatus,
                     @Param("targetStatus") String targetStatus,
                     @Param("updateTime") LocalDateTime updateTime);
}
