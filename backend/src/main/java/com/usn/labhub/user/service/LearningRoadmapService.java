package com.usn.labhub.user.service;

import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.dto.learning.LearningRoadmapCreateDTO;
import com.usn.labhub.user.domain.dto.learning.LearningRoadmapStatusUpdateDTO;
import com.usn.labhub.user.domain.dto.learning.LearningRoadmapUpdateDTO;
import com.usn.labhub.user.domain.dto.learning.LearningStageCreateDTO;
import com.usn.labhub.user.domain.dto.learning.LearningStageUpdateDTO;
import com.usn.labhub.user.domain.dto.learning.LearningUnitCreateDTO;
import com.usn.labhub.user.domain.dto.learning.LearningUnitUpdateDTO;
import com.usn.labhub.user.domain.entity.learning.LearningRoadmapRecord;
import com.usn.labhub.user.domain.entity.learning.LearningStageAccessRecord;
import com.usn.labhub.user.domain.entity.learning.LearningStageRecord;
import com.usn.labhub.user.domain.entity.learning.LearningUnitAccessRecord;
import com.usn.labhub.user.domain.entity.learning.LearningUnitRecord;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapCreateVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapDetailVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapPageVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapStatusVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapSummaryVO;
import com.usn.labhub.user.domain.vo.learning.LearningStageSummaryVO;
import com.usn.labhub.user.domain.vo.learning.LearningStageVO;
import com.usn.labhub.user.domain.vo.learning.LearningUnitVO;
import com.usn.labhub.user.learning.LearningApiException;
import com.usn.labhub.user.learning.LearningRoadmapStateMachine;
import com.usn.labhub.user.mapper.LearningRoadmapMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class LearningRoadmapService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> DIFFICULTIES = Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");
    private static final Set<String> LEARNING_ROLES = Set.of(
            "SYSTEM_ADMIN", "TEACHER", "MEMBER", "STOCK_KEEPER");
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "sortOrder", "r.sort_order",
            "createTime", "r.create_time",
            "updateTime", "r.update_time");

    private final LearningRoadmapMapper roadmapMapper;

    public LearningRoadmapService(LearningRoadmapMapper roadmapMapper) {
        this.roadmapMapper = roadmapMapper;
    }

    @Transactional
    public LearningRoadmapCreateVO create(LearningRoadmapCreateDTO request) {
        requireManager();
        if (request.getStatus() != null && !"DRAFT".equals(request.getStatus())) {
            throw LearningApiException.invalidParameter();
        }

        LocalDateTime now = LocalDateTime.now();
        LearningRoadmapRecord roadmap = new LearningRoadmapRecord();
        roadmap.setTitle(requiredTrimmed(request.getTitle()));
        roadmap.setDescription(trimToNull(request.getDescription()));
        roadmap.setStatus("DRAFT");
        roadmap.setDifficulty(defaultDifficulty(request.getDifficulty()));
        roadmap.setEstimatedHours(request.getEstimatedHours());
        roadmap.setCoverMediaId(trimToNull(request.getCoverMediaId()));
        roadmap.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        roadmap.setCreatedBy(requiredUserId());
        roadmap.setCreateTime(now);
        roadmap.setUpdateTime(now);
        roadmapMapper.insert(roadmap);
        return roadmapMapper.selectCreated(roadmap.getId());
    }

    public LearningRoadmapPageVO list(Integer page, Integer pageSize, String status,
                                       String difficulty, String sortBy, String sortOrder) {
        requireLearningAccess();
        if (page == null || page < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw LearningApiException.invalidParameter();
        }
        String normalizedStatus = trimToNull(status);
        String normalizedDifficulty = trimToNull(difficulty);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw LearningApiException.invalidParameter();
        }
        if (normalizedDifficulty != null && !DIFFICULTIES.contains(normalizedDifficulty)) {
            throw LearningApiException.invalidParameter();
        }
        String normalizedSortBy = StringUtils.hasText(sortBy) ? sortBy.trim() : "sortOrder";
        String sortColumn = SORT_COLUMNS.get(normalizedSortBy);
        String normalizedOrder = StringUtils.hasText(sortOrder) ? sortOrder.trim().toLowerCase() : "asc";
        if (sortColumn == null || !("asc".equals(normalizedOrder) || "desc".equals(normalizedOrder))) {
            throw LearningApiException.invalidParameter();
        }

        boolean manager = isManager();
        long total = roadmapMapper.countRoadmaps(manager, normalizedStatus, normalizedDifficulty);
        List<LearningRoadmapSummaryVO> list = total == 0 ? List.of() : roadmapMapper.selectRoadmaps(
                manager, normalizedStatus, normalizedDifficulty, sortColumn, normalizedOrder,
                (page - 1) * pageSize, pageSize);
        return new LearningRoadmapPageVO(total, page, pageSize, list);
    }

    public LearningRoadmapDetailVO detail(Long roadmapId) {
        requireLearningAccess();
        validateId(roadmapId);
        LearningRoadmapDetailVO detail = roadmapMapper.selectDetail(roadmapId, isManager());
        if (detail == null) {
            throw LearningApiException.roadmapNotFound();
        }
        detail.setStages(roadmapMapper.selectStageSummaries(roadmapId));
        return detail;
    }

    @Transactional
    public LearningRoadmapStatusVO updateStatus(Long roadmapId, LearningRoadmapStatusUpdateDTO request) {
        requireManager();
        validateId(roadmapId);
        LearningRoadmapRecord existing = requiredRecord(roadmapId);
        if (!LearningRoadmapStateMachine.canTransition(existing.getStatus(), request.getStatus())) {
            throw LearningApiException.invalidTransition();
        }
        LocalDateTime updateTime = LocalDateTime.now();
        int updated = roadmapMapper.updateStatus(
                roadmapId, existing.getStatus(), request.getStatus(), updateTime);
        if (updated != 1) {
            throw LearningApiException.invalidTransition();
        }
        return new LearningRoadmapStatusVO(roadmapId, request.getStatus(), updateTime);
    }

    @Transactional
    public LearningRoadmapSummaryVO update(Long roadmapId, LearningRoadmapUpdateDTO request) {
        requireManager();
        validateId(roadmapId);
        LearningRoadmapRecord existing = requiredRecord(roadmapId);
        if ("ARCHIVED".equals(existing.getStatus())) {
            throw LearningApiException.archived();
        }
        if (allUpdateFieldsNull(request)) {
            throw LearningApiException.invalidParameter();
        }

        LearningRoadmapRecord update = new LearningRoadmapRecord();
        update.setId(roadmapId);
        update.setTitle(request.getTitle() == null ? null : requiredTrimmed(request.getTitle()));
        update.setDescription(trimToNull(request.getDescription()));
        update.setDescriptionSpecified(request.isDescriptionSpecified());
        update.setDifficulty(request.getDifficulty());
        update.setEstimatedHours(request.getEstimatedHours());
        update.setCoverMediaId(trimToNull(request.getCoverMediaId()));
        update.setCoverMediaIdSpecified(request.isCoverMediaIdSpecified());
        update.setSortOrder(request.getSortOrder());
        update.setUpdateTime(LocalDateTime.now());
        if (roadmapMapper.updateBasics(update) != 1) {
            LearningRoadmapRecord concurrent = roadmapMapper.selectRecord(roadmapId);
            if (concurrent != null && "ARCHIVED".equals(concurrent.getStatus())) {
                throw LearningApiException.archived();
            }
            throw LearningApiException.roadmapNotFound();
        }
        return roadmapMapper.selectSummary(roadmapId);
    }

    @Transactional
    public LearningStageSummaryVO createStage(Long roadmapId, LearningStageCreateDTO request) {
        requireManager();
        validateId(roadmapId);
        LearningRoadmapRecord roadmap = requiredRecord(roadmapId);
        requireMutableRoadmap(roadmap.getStatus());

        LocalDateTime now = LocalDateTime.now();
        LearningStageRecord stage = new LearningStageRecord();
        stage.setRoadmapId(roadmapId);
        stage.setName(requiredTrimmed(request.getName()));
        stage.setDescription(trimToNull(request.getDescription()));
        stage.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        stage.setCreateTime(now);
        stage.setUpdateTime(now);
        roadmapMapper.insertStage(stage);
        return roadmapMapper.selectStageSummary(stage.getId());
    }

    public List<LearningStageVO> listStages(Long roadmapId) {
        requireLearningAccess();
        validateId(roadmapId);
        if (roadmapMapper.selectDetail(roadmapId, isManager()) == null) {
            throw LearningApiException.roadmapNotFound();
        }

        List<LearningStageVO> stages = roadmapMapper.selectStages(roadmapId);
        Map<Long, List<LearningUnitVO>> unitsByStage = new LinkedHashMap<>();
        for (LearningUnitVO unit : roadmapMapper.selectUnitsForRoadmap(roadmapId, requiredUserId())) {
            unitsByStage.computeIfAbsent(unit.getStageId(), ignored -> new java.util.ArrayList<>()).add(unit);
        }
        for (LearningStageVO stage : stages) {
            stage.setUnits(unitsByStage.getOrDefault(stage.getId(), List.of()));
        }
        return stages;
    }

    @Transactional
    public LearningStageSummaryVO updateStage(Long stageId, LearningStageUpdateDTO request) {
        requireManager();
        validateId(stageId);
        LearningStageAccessRecord access = requiredStageAccess(stageId);
        requireMutableRoadmap(access.getRoadmapStatus());
        if (request.getName() == null && !request.isDescriptionSpecified() && request.getSortOrder() == null) {
            throw LearningApiException.invalidParameter();
        }

        LearningStageRecord update = new LearningStageRecord();
        update.setId(stageId);
        update.setName(request.getName() == null ? null : requiredTrimmed(request.getName()));
        update.setDescription(trimToNull(request.getDescription()));
        update.setDescriptionSpecified(request.isDescriptionSpecified());
        update.setSortOrder(request.getSortOrder());
        update.setUpdateTime(LocalDateTime.now());
        if (roadmapMapper.updateStage(update) != 1) {
            LearningStageAccessRecord concurrent = roadmapMapper.selectStageAccess(stageId);
            if (concurrent != null && "ARCHIVED".equals(concurrent.getRoadmapStatus())) {
                throw LearningApiException.archived();
            }
            throw LearningApiException.stageNotFound();
        }
        return roadmapMapper.selectStageSummary(stageId);
    }

    @Transactional
    public LearningUnitVO createUnit(Long stageId, LearningUnitCreateDTO request) {
        requireManager();
        validateId(stageId);
        LearningStageAccessRecord access = requiredStageAccess(stageId);
        requireMutableRoadmap(access.getRoadmapStatus());

        LocalDateTime now = LocalDateTime.now();
        LearningUnitRecord unit = new LearningUnitRecord();
        unit.setStageId(stageId);
        unit.setTitle(requiredTrimmed(request.getTitle()));
        unit.setDescription(trimToNull(request.getDescription()));
        unit.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        unit.setTemplateId(null);
        unit.setCreateTime(now);
        unit.setUpdateTime(now);
        roadmapMapper.insertUnit(unit);
        if ("PUBLISHED".equals(access.getRoadmapStatus())) {
            roadmapMapper.recalibrateCompletedRecords(access.getRoadmapId(), now);
        }
        return roadmapMapper.selectUnitForUser(unit.getId(), requiredUserId());
    }

    @Transactional
    public LearningUnitVO updateUnit(Long unitId, LearningUnitUpdateDTO request) {
        requireManager();
        validateId(unitId);
        LearningUnitAccessRecord access = requiredUnitAccess(unitId);
        requireMutableRoadmap(access.getRoadmapStatus());
        if (request.getTitle() == null && !request.isDescriptionSpecified() && request.getSortOrder() == null) {
            throw LearningApiException.invalidParameter();
        }

        LearningUnitRecord update = new LearningUnitRecord();
        update.setId(unitId);
        update.setTitle(request.getTitle() == null ? null : requiredTrimmed(request.getTitle()));
        update.setDescription(trimToNull(request.getDescription()));
        update.setDescriptionSpecified(request.isDescriptionSpecified());
        update.setSortOrder(request.getSortOrder());
        update.setUpdateTime(LocalDateTime.now());
        if (roadmapMapper.updateUnit(update) != 1) {
            LearningUnitAccessRecord concurrent = roadmapMapper.selectUnitAccess(unitId);
            if (concurrent != null && "ARCHIVED".equals(concurrent.getRoadmapStatus())) {
                throw LearningApiException.archived();
            }
            throw LearningApiException.unitNotFound();
        }
        return roadmapMapper.selectUnitForUser(unitId, requiredUserId());
    }

    private LearningRoadmapRecord requiredRecord(Long roadmapId) {
        LearningRoadmapRecord record = roadmapMapper.selectRecord(roadmapId);
        if (record == null) {
            throw LearningApiException.roadmapNotFound();
        }
        return record;
    }

    private LearningStageAccessRecord requiredStageAccess(Long stageId) {
        LearningStageAccessRecord access = roadmapMapper.selectStageAccess(stageId);
        if (access == null) {
            throw LearningApiException.stageNotFound();
        }
        return access;
    }

    private LearningUnitAccessRecord requiredUnitAccess(Long unitId) {
        LearningUnitAccessRecord access = roadmapMapper.selectUnitAccess(unitId);
        if (access == null) {
            throw LearningApiException.unitNotFound();
        }
        return access;
    }

    private void requireMutableRoadmap(String status) {
        if ("ARCHIVED".equals(status)) {
            throw LearningApiException.archived();
        }
    }

    private void requireManager() {
        if (!isManager()) {
            throw LearningApiException.operationDenied();
        }
    }

    private void requireLearningAccess() {
        if (UserContext.getRoles().stream().noneMatch(LEARNING_ROLES::contains)) {
            throw LearningApiException.accessDenied();
        }
    }

    private boolean isManager() {
        Set<String> roles = UserContext.getRoles();
        return roles.contains("SYSTEM_ADMIN") || roles.contains("TEACHER");
    }

    private Long requiredUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw LearningApiException.accessDenied();
        }
        return userId;
    }

    private void validateId(Long id) {
        if (id == null || id < 1) {
            throw LearningApiException.roadmapNotFound();
        }
    }

    private String defaultDifficulty(String difficulty) {
        return difficulty == null ? "BEGINNER" : difficulty;
    }

    private String requiredTrimmed(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null || trimmed.length() < 2) {
            throw LearningApiException.invalidParameter();
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean allUpdateFieldsNull(LearningRoadmapUpdateDTO request) {
        return request.getTitle() == null
                && !request.isDescriptionSpecified()
                && request.getDifficulty() == null
                && request.getEstimatedHours() == null
                && !request.isCoverMediaIdSpecified()
                && request.getSortOrder() == null;
    }
}
