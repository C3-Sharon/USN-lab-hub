package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.project.TaskRecord;
import com.usn.labhub.user.domain.vo.project.TaskVO;
import com.usn.labhub.user.domain.vo.project.TaskWorkbenchItemVO;
import com.usn.labhub.user.domain.vo.project.TaskWorkbenchStatsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProjectTaskMapper {
    int insert(TaskRecord task);
    TaskVO selectById(@Param("projectId") Long projectId, @Param("taskId") Long taskId);
    Integer selectVersionForUpdate(@Param("projectId") Long projectId, @Param("taskId") Long taskId);
    long count(@Param("projectId") Long projectId, @Param("milestoneId") Long milestoneId,
               @Param("status") String status, @Param("assigneeUserId") Long assigneeUserId,
               @Param("includeCanceled") boolean includeCanceled);
    List<TaskVO> selectPage(@Param("projectId") Long projectId, @Param("milestoneId") Long milestoneId,
                            @Param("status") String status, @Param("assigneeUserId") Long assigneeUserId,
                            @Param("includeCanceled") boolean includeCanceled,
                            @Param("sortColumn") String sortColumn, @Param("sortOrder") String sortOrder,
                            @Param("offset") int offset, @Param("pageSize") int pageSize);
    int updateDetails(@Param("task") TaskRecord task, @Param("expectedVersion") Integer expectedVersion);
    int updateStatus(@Param("projectId") Long projectId, @Param("taskId") Long taskId,
                     @Param("currentStatus") String currentStatus, @Param("targetStatus") String targetStatus,
                     @Param("blockReason") String blockReason, @Param("expectedVersion") Integer expectedVersion,
                     @Param("updateTime") LocalDateTime updateTime);
    TaskWorkbenchStatsVO selectWorkbenchStats(@Param("userId") Long userId,
                                              @Param("weekStart") LocalDateTime weekStart,
                                              @Param("weekEnd") LocalDateTime weekEnd);
    List<TaskWorkbenchItemVO> selectWorkbenchRecent(@Param("userId") Long userId);
}
