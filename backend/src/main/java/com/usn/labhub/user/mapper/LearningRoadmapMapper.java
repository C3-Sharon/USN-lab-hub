package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.learning.LearningRoadmapRecord;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapCreateVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapDetailVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapSummaryVO;
import com.usn.labhub.user.domain.vo.learning.LearningStageSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LearningRoadmapMapper {

    int insert(LearningRoadmapRecord roadmap);

    LearningRoadmapRecord selectRecord(@Param("roadmapId") Long roadmapId);

    LearningRoadmapCreateVO selectCreated(@Param("roadmapId") Long roadmapId);

    long countRoadmaps(@Param("manager") boolean manager,
                       @Param("status") String status,
                       @Param("difficulty") String difficulty);

    List<LearningRoadmapSummaryVO> selectRoadmaps(@Param("manager") boolean manager,
                                                   @Param("status") String status,
                                                   @Param("difficulty") String difficulty,
                                                   @Param("sortColumn") String sortColumn,
                                                   @Param("sortOrder") String sortOrder,
                                                   @Param("offset") int offset,
                                                   @Param("pageSize") int pageSize);

    LearningRoadmapDetailVO selectDetail(@Param("roadmapId") Long roadmapId,
                                         @Param("manager") boolean manager);

    List<LearningStageSummaryVO> selectStageSummaries(@Param("roadmapId") Long roadmapId);

    int updateStatus(@Param("roadmapId") Long roadmapId,
                     @Param("currentStatus") String currentStatus,
                     @Param("targetStatus") String targetStatus,
                     @Param("updateTime") LocalDateTime updateTime);

    int updateBasics(LearningRoadmapRecord roadmap);

    LearningRoadmapSummaryVO selectSummary(@Param("roadmapId") Long roadmapId);
}
