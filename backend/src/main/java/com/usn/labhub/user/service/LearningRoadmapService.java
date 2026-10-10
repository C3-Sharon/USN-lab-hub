package com.usn.labhub.user.service;

import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.dto.learning.LearningRoadmapCreateDTO;
import com.usn.labhub.user.domain.dto.learning.LearningRoadmapStatusUpdateDTO;
import com.usn.labhub.user.domain.dto.learning.LearningRoadmapUpdateDTO;
import com.usn.labhub.user.domain.entity.learning.LearningRoadmapRecord;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapCreateVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapDetailVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapPageVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapStatusVO;
import com.usn.labhub.user.domain.vo.learning.LearningRoadmapSummaryVO;
import com.usn.labhub.user.learning.LearningApiException;
import com.usn.labhub.user.learning.LearningRoadmapStateMachine;
import com.usn.labhub.user.mapper.LearningRoadmapMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
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

    private LearningRoadmapRecord requiredRecord(Long roadmapId) {
        LearningRoadmapRecord record = roadmapMapper.selectRecord(roadmapId);
        if (record == null) {
            throw LearningApiException.roadmapNotFound();
        }
        return record;
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
