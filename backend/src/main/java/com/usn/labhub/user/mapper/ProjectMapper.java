package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.project.ProjectRecord;
import com.usn.labhub.user.domain.vo.project.ProjectDetailVO;
import com.usn.labhub.user.domain.vo.project.ProjectMemberVO;
import com.usn.labhub.user.domain.vo.project.ProjectSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProjectMapper {

    int insertProject(ProjectRecord project);

    int insertMember(@Param("projectId") Long projectId,
                     @Param("userId") Long userId,
                     @Param("projectRole") String projectRole,
                     @Param("joinedAt") LocalDateTime joinedAt);

    long countProjects(@Param("userId") Long userId,
                       @Param("viewAll") boolean viewAll,
                       @Param("publicOnly") boolean publicOnly,
                       @Param("status") String status,
                       @Param("keyword") String keyword);

    List<ProjectSummaryVO> selectProjects(@Param("userId") Long userId,
                                          @Param("viewAll") boolean viewAll,
                                          @Param("publicOnly") boolean publicOnly,
                                          @Param("status") String status,
                                          @Param("keyword") String keyword,
                                          @Param("sortColumn") String sortColumn,
                                          @Param("sortOrder") String sortOrder,
                                          @Param("offset") int offset,
                                          @Param("pageSize") int pageSize);

    ProjectDetailVO selectProjectDetail(@Param("projectId") Long projectId,
                                        @Param("userId") Long userId,
                                        @Param("viewAll") boolean viewAll,
                                        @Param("publicOnly") boolean publicOnly);

    List<ProjectMemberVO> selectMembers(@Param("projectId") Long projectId);
}
