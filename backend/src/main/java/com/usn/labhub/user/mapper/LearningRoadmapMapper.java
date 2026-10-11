package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.learning.LearningRoadmapRecord;
import com.usn.labhub.user.domain.entity.learning.LearningRecord;
import com.usn.labhub.user.domain.entity.learning.LearningStageAccessRecord;
import com.usn.labhub.user.domain.entity.learning.LearningStageRecord;
import com.usn.labhub.user.domain.entity.learning.LearningUnitAccessRecord;
import com.usn.labhub.user.domain.entity.learning.LearningUnitRecord;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapCreateVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapDetailVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapSummaryVO;
import com.usn.labhub.user.domain.vo.learning.LearningStageSummaryVO;
import com.usn.labhub.user.domain.vo.learning.LearningStageVO;
import com.usn.labhub.user.domain.vo.learning.LearningUnitVO;
import com.usn.labhub.user.domain.vo.learning.LearningEnrollmentVO;
import com.usn.labhub.user.domain.vo.learning.LearningWorkbenchItemVO;
import com.usn.labhub.user.domain.vo.learning.LearningWorkbenchStatsVO;
import com.usn.labhub.user.domain.vo.learning.MyLearningRoadmapVO;
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

    int insertStage(LearningStageRecord stage);

    LearningStageAccessRecord selectStageAccess(@Param("stageId") Long stageId);

    LearningStageSummaryVO selectStageSummary(@Param("stageId") Long stageId);

    int updateStage(LearningStageRecord stage);

    List<LearningStageVO> selectStages(@Param("roadmapId") Long roadmapId);

    List<LearningUnitVO> selectUnitsForRoadmap(@Param("roadmapId") Long roadmapId,
                                                @Param("userId") Long userId);

    int insertUnit(LearningUnitRecord unit);

    LearningUnitAccessRecord selectUnitAccess(@Param("unitId") Long unitId);

    LearningUnitVO selectUnitForUser(@Param("unitId") Long unitId,
                                     @Param("userId") Long userId);

    int updateUnit(LearningUnitRecord unit);

    int recalibrateCompletedRecords(@Param("roadmapId") Long roadmapId,
                                    @Param("updateTime") LocalDateTime updateTime);

    int insertEnrollmentIgnore(LearningRecord record);

    LearningRecord selectEnrollmentForUpdate(@Param("roadmapId") Long roadmapId,
                                             @Param("userId") Long userId);

    LearningEnrollmentVO selectEnrollmentView(@Param("roadmapId") Long roadmapId,
                                              @Param("userId") Long userId);

    int insertCompletionIgnore(@Param("unitId") Long unitId,
                               @Param("userId") Long userId,
                               @Param("completedAt") LocalDateTime completedAt);

    int deleteCompletion(@Param("unitId") Long unitId,
                         @Param("userId") Long userId);

    int updateLearningState(@Param("recordId") Long recordId,
                            @Param("status") String status,
                            @Param("completedAt") LocalDateTime completedAt,
                            @Param("updateTime") LocalDateTime updateTime);

    long countMyRoadmaps(@Param("userId") Long userId);

    List<MyLearningRoadmapVO> selectMyRoadmaps(@Param("userId") Long userId,
                                                @Param("offset") int offset,
                                                @Param("pageSize") int pageSize);

    LearningWorkbenchStatsVO selectWorkbenchStats(@Param("userId") Long userId);

    List<LearningWorkbenchItemVO> selectWorkbenchRecent(@Param("userId") Long userId);
}
