package com.usn.labhub.user.service;

import com.usn.labhub.user.component.ComponentCatalogAccessPolicy;
import com.usn.labhub.user.component.ComponentCatalogApiException;
import com.usn.labhub.user.component.ComponentCatalogNormalizer;
import com.usn.labhub.user.component.ComponentCatalogSupport;
import com.usn.labhub.user.component.ComponentSpecificationCodec;
import com.usn.labhub.user.domain.dto.component.ManualSpecSnapshotDTO;
import com.usn.labhub.user.domain.dto.component.SpecCandidateConfirmDTO;
import com.usn.labhub.user.domain.dto.component.SpecCandidateCreateDTO;
import com.usn.labhub.user.domain.dto.component.SpecCandidateRejectDTO;
import com.usn.labhub.user.domain.entity.component.ComponentRecord;
import com.usn.labhub.user.domain.entity.component.ComponentSpecCandidateRecord;
import com.usn.labhub.user.domain.entity.component.ComponentSpecSnapshotRecord;
import com.usn.labhub.user.domain.vo.component.CatalogPageVO;
import com.usn.labhub.user.domain.vo.component.SpecCandidateVO;
import com.usn.labhub.user.domain.vo.component.SpecSnapshotVO;
import com.usn.labhub.user.mapper.ComponentCatalogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ComponentSpecificationService {
    private static final Set<String> SOURCE_TYPES = Set.of(
            "MANUFACTURER_WEBSITE", "MANUFACTURER_DATASHEET",
            "AUTHORIZED_DISTRIBUTOR", "OTHER_PUBLIC_URL", "MANUAL");
    private static final Set<String> CANDIDATE_STATUSES = Set.of(
            "PENDING", "CONFIRMED", "REJECTED", "SUPERSEDED");

    private final ComponentCatalogMapper mapper;
    private final ComponentCatalogAccessPolicy access;
    private final ComponentCatalogSupport support;
    private final ComponentCatalogNormalizer normalizer;
    private final ComponentSpecificationCodec codec;

    public ComponentSpecificationService(ComponentCatalogMapper mapper,
                                         ComponentCatalogAccessPolicy access,
                                         ComponentCatalogSupport support,
                                         ComponentCatalogNormalizer normalizer,
                                         ComponentSpecificationCodec codec) {
        this.mapper = mapper;
        this.access = access;
        this.support = support;
        this.normalizer = normalizer;
        this.codec = codec;
    }

    @Transactional
    public Map<String, Object> createCandidate(Long componentId, String key,
                                                SpecCandidateCreateDTO request) {
        access.requireManage();
        return support.idempotent("CREATE_COMPONENT_SPEC_CANDIDATE:" + componentId, key, request,
                () -> createCandidateValue(componentId, key, request));
    }

    public CatalogPageVO<SpecCandidateVO> candidates(Long componentId, int page, int pageSize,
                                                      String status) {
        access.requireAccess();
        access.requireCandidateRead();
        validatePage(page, pageSize);
        requiredReadableComponent(componentId);
        String normalizedStatus = enumOrNull(status, CANDIDATE_STATUSES);
        long total = mapper.countSpecCandidates(componentId, normalizedStatus);
        List<SpecCandidateVO> list = mapper.selectSpecCandidates(componentId, normalizedStatus,
                        (page - 1) * pageSize, pageSize).stream()
                .map(this::candidateView).toList();
        return new CatalogPageVO<>(total, page, pageSize, list);
    }

    public SpecCandidateVO candidate(Long candidateId) {
        access.requireAccess();
        access.requireCandidateRead();
        ComponentSpecCandidateRecord row = mapper.selectSpecCandidate(candidateId);
        if (row == null) {
            throw ComponentCatalogApiException.candidateNotFound();
        }
        requiredReadableComponent(row.getComponentId());
        return candidateView(row);
    }

    @Transactional
    public Map<String, Object> confirmCandidate(Long candidateId, String key,
                                                 SpecCandidateConfirmDTO request) {
        access.requireSpecManage();
        return support.idempotent("CONFIRM_COMPONENT_SPEC_CANDIDATE:" + candidateId, key, request,
                () -> confirmCandidateValue(candidateId, key, request));
    }

    @Transactional
    public Map<String, Object> rejectCandidate(Long candidateId, String key,
                                                SpecCandidateRejectDTO request) {
        access.requireSpecManage();
        return support.idempotent("REJECT_COMPONENT_SPEC_CANDIDATE:" + candidateId, key, request,
                () -> rejectCandidateValue(candidateId, key, request));
    }

    @Transactional
    public Map<String, Object> createManualSnapshot(Long componentId, String key,
                                                     ManualSpecSnapshotDTO request) {
        access.requireSpecManage();
        return support.idempotent("CREATE_MANUAL_COMPONENT_SPEC_SNAPSHOT:" + componentId, key, request,
                () -> createManualSnapshotValue(componentId, key, request));
    }

    public CatalogPageVO<SpecSnapshotVO> snapshots(Long componentId, int page, int pageSize) {
        access.requireAccess();
        validatePage(page, pageSize);
        ComponentRecord component = requiredReadableComponent(componentId);
        Long visibleSnapshotId = access.canReadAllStates() ? null : component.getCurrentSpecSnapshotId();
        if (!access.canReadAllStates() && visibleSnapshotId == null) {
            return new CatalogPageVO<>(0, page, pageSize, List.of());
        }
        long total = mapper.countSpecSnapshots(componentId, visibleSnapshotId);
        List<SpecSnapshotVO> list = mapper.selectSpecSnapshots(componentId, visibleSnapshotId,
                        (page - 1) * pageSize, pageSize).stream()
                .map(this::snapshotView).toList();
        return new CatalogPageVO<>(total, page, pageSize, list);
    }

    public SpecSnapshotVO snapshot(Long snapshotId) {
        access.requireAccess();
        ComponentSpecSnapshotRecord row = mapper.selectSpecSnapshot(snapshotId);
        if (row == null) {
            throw ComponentCatalogApiException.snapshotNotFound();
        }
        ComponentRecord component = requiredReadableComponent(row.getComponentId());
        if (!access.canReadAllStates() && !snapshotId.equals(component.getCurrentSpecSnapshotId())) {
            throw ComponentCatalogApiException.snapshotNotFound();
        }
        return snapshotView(row);
    }

    private Map<String, Object> createCandidateValue(Long componentId, String key,
                                                      SpecCandidateCreateDTO request) {
        ComponentRecord component = requiredComponentForUpdate(componentId);
        requireVersion(component.getVersion(), request.getComponentVersion());
        requireMutable(component);
        SourceMetadata source = sourceMetadata(request.getSourceType(), request.getSourceUrl(),
                request.getSourceTitle(), request.getCapturedAt());
        LocalDateTime now = LocalDateTime.now();
        ComponentSpecCandidateRecord row = new ComponentSpecCandidateRecord();
        row.setComponentId(componentId);
        row.setStatus("PENDING");
        row.setSourceType(source.type());
        row.setSourceUrl(source.url());
        row.setSourceTitle(source.title());
        row.setCapturedAt(source.capturedAt());
        row.setRawSpecsJson(codec.rawJson(request.getRawSpecs()));
        row.setNormalizedProposalJson(codec.specificationJson(request.getNormalizedProposal()));
        row.setSubmittedBy(support.requiredUserId());
        row.setSubmittedAt(now);
        row.setVersion(1);
        mapper.insertSpecCandidate(row);
        if (mapper.incrementComponentVersion(componentId, component.getVersion(), row.getSubmittedBy(), now) != 1) {
            throw versionConflictComponent(componentId);
        }
        support.audit(key.trim(), "CREATE_SPEC_CANDIDATE", "COMPONENT_SPEC_CANDIDATE", row.getId(),
                null, "PENDING", null, Map.of("componentId", componentId, "sourceType", source.type()));
        Map<String, Object> result = new LinkedHashMap<>(support.asMap(candidateView(row)));
        result.put("componentVersion", component.getVersion() + 1);
        return result;
    }

    private Map<String, Object> confirmCandidateValue(Long candidateId, String key,
                                                       SpecCandidateConfirmDTO request) {
        ComponentSpecCandidateRecord observed = mapper.selectSpecCandidate(candidateId);
        if (observed == null) {
            throw ComponentCatalogApiException.candidateNotFound();
        }
        ComponentRecord component = requiredComponentForUpdate(observed.getComponentId());
        ComponentSpecCandidateRecord candidate = requiredCandidateForUpdate(candidateId);
        requireCandidateRelation(candidate, component.getId());
        requirePending(candidate);
        requireVersion(component.getVersion(), request.getComponentVersion());
        requireVersion(candidate.getVersion(), request.getCandidateVersion());
        requireMutable(component);

        String specificationsJson = codec.specificationJson(request.getSpecifications());
        ComponentSpecSnapshotRecord snapshot = newSnapshot(component, candidate.getSourceType(),
                candidate.getSourceUrl(), candidate.getSourceTitle(), candidate.getCapturedAt(), candidate.getId(),
                null, candidate.getRawSpecsJson(), specificationsJson, request.getChangeSummary());
        mapper.insertSpecSnapshot(snapshot);
        LocalDateTime now = snapshot.getConfirmedAt();
        if (mapper.decideSpecCandidate(candidateId, candidate.getVersion(), "CONFIRMED",
                support.requiredUserId(), now, request.getChangeSummary()) != 1) {
            throw currentCandidateConflict(candidateId);
        }
        if (mapper.updateComponentSnapshotPointer(component.getId(), component.getVersion(),
                component.getSpecVersion(), snapshot.getId(), snapshot.getSnapshotVersion(),
                support.requiredUserId(), now) != 1) {
            throw versionConflictComponent(component.getId());
        }
        support.audit(key.trim(), "CONFIRM_SPEC_CANDIDATE", "COMPONENT_SPEC_CANDIDATE", candidateId,
                "PENDING", "CONFIRMED", request.getChangeSummary(),
                Map.of("componentId", component.getId(), "snapshotId", snapshot.getId(),
                        "snapshotVersion", snapshot.getSnapshotVersion(), "checksum", snapshot.getChecksum()));
        return confirmationResult(candidateId, snapshot, component.getVersion() + 1, "CONFIRMED");
    }

    private Map<String, Object> rejectCandidateValue(Long candidateId, String key,
                                                      SpecCandidateRejectDTO request) {
        ComponentSpecCandidateRecord observed = mapper.selectSpecCandidate(candidateId);
        if (observed == null) {
            throw ComponentCatalogApiException.candidateNotFound();
        }
        ComponentRecord component = requiredComponentForUpdate(observed.getComponentId());
        ComponentSpecCandidateRecord candidate = requiredCandidateForUpdate(candidateId);
        requireCandidateRelation(candidate, component.getId());
        requirePending(candidate);
        requireVersion(candidate.getVersion(), request.getCandidateVersion());
        LocalDateTime now = LocalDateTime.now();
        String note = normalizer.optional(request.getDecisionNote());
        if (mapper.decideSpecCandidate(candidateId, candidate.getVersion(), "REJECTED",
                support.requiredUserId(), now, note) != 1) {
            throw currentCandidateConflict(candidateId);
        }
        support.audit(key.trim(), "REJECT_SPEC_CANDIDATE", "COMPONENT_SPEC_CANDIDATE", candidateId,
                "PENDING", "REJECTED", note, Map.of("componentId", component.getId()));
        Map<String, Object> result = new LinkedHashMap<>(support.asMap(candidateView(
                mapper.selectSpecCandidate(candidateId))));
        result.put("componentVersion", component.getVersion());
        return result;
    }

    private Map<String, Object> createManualSnapshotValue(Long componentId, String key,
                                                           ManualSpecSnapshotDTO request) {
        ComponentRecord component = requiredComponentForUpdate(componentId);
        requireVersion(component.getVersion(), request.getComponentVersion());
        requireMutable(component);
        if (!"MANUAL".equals(request.getSourceType()) || request.getSourceUrl() != null) {
            throw ComponentCatalogApiException.sourceMetadataInvalid();
        }
        String rawJson = codec.rawJson(request.getRawSpecs());
        String specificationsJson = codec.specificationJson(request.getSpecifications());
        ComponentSpecSnapshotRecord snapshot = newSnapshot(component, "MANUAL", null, null,
                null, null, request.getSourceNote().trim(), rawJson, specificationsJson,
                request.getChangeSummary());
        mapper.insertSpecSnapshot(snapshot);
        if (mapper.updateComponentSnapshotPointer(componentId, component.getVersion(), component.getSpecVersion(),
                snapshot.getId(), snapshot.getSnapshotVersion(), support.requiredUserId(),
                snapshot.getConfirmedAt()) != 1) {
            throw versionConflictComponent(componentId);
        }
        support.audit(key.trim(), "CREATE_MANUAL_SPEC_SNAPSHOT", "COMPONENT_SPEC_SNAPSHOT", snapshot.getId(),
                null, "CONFIRMED", request.getChangeSummary(),
                Map.of("componentId", componentId, "snapshotVersion", snapshot.getSnapshotVersion(),
                        "checksum", snapshot.getChecksum()));
        Map<String, Object> result = new LinkedHashMap<>(support.asMap(snapshotView(snapshot)));
        result.put("componentVersion", component.getVersion() + 1);
        result.put("specVersion", snapshot.getSnapshotVersion());
        return result;
    }

    private ComponentSpecSnapshotRecord newSnapshot(ComponentRecord component, String sourceType,
                                                      String sourceUrl, String sourceTitle,
                                                      LocalDateTime capturedAt, Long candidateId,
                                                      String sourceNote, String rawJson,
                                                      String specificationsJson, String changeSummary) {
        ComponentSpecSnapshotRecord row = new ComponentSpecSnapshotRecord();
        row.setComponentId(component.getId());
        row.setSnapshotVersion(component.getSpecVersion() + 1);
        row.setSourceType(sourceType);
        row.setSourceUrl(sourceUrl);
        row.setSourceTitle(sourceTitle);
        row.setCapturedAt(capturedAt);
        row.setCandidateId(candidateId);
        row.setSourceNote(sourceNote);
        row.setRawSpecsJson(rawJson);
        row.setSpecificationsJson(specificationsJson);
        row.setChangeSummary(changeSummary.trim());
        row.setChecksum(codec.checksum(specificationsJson));
        row.setConfirmedBy(support.requiredUserId());
        row.setConfirmedAt(LocalDateTime.now());
        return row;
    }

    private Map<String, Object> confirmationResult(Long candidateId, ComponentSpecSnapshotRecord snapshot,
                                                    int componentVersion, String candidateStatus) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidateId", candidateId);
        result.put("candidateStatus", candidateStatus);
        result.put("snapshotId", snapshot.getId());
        result.put("snapshotVersion", snapshot.getSnapshotVersion());
        result.put("componentId", snapshot.getComponentId());
        result.put("componentVersion", componentVersion);
        result.put("specVersion", snapshot.getSnapshotVersion());
        return result;
    }

    private SourceMetadata sourceMetadata(String type, String url, String title, OffsetDateTime capturedAt) {
        if (!SOURCE_TYPES.contains(type)) {
            throw ComponentCatalogApiException.sourceMetadataInvalid();
        }
        String normalizedUrl = normalizer.optional(url);
        String normalizedTitle = normalizer.optional(title);
        if ("MANUAL".equals(type)) {
            if (normalizedUrl != null || capturedAt != null) {
                throw ComponentCatalogApiException.sourceMetadataInvalid();
            }
            return new SourceMetadata(type, null, normalizedTitle, null);
        }
        if (normalizedUrl == null || capturedAt == null || capturedAt.isAfter(OffsetDateTime.now())) {
            throw ComponentCatalogApiException.sourceMetadataInvalid();
        }
        try {
            URI parsed = new URI(normalizedUrl);
            if (!("http".equalsIgnoreCase(parsed.getScheme()) || "https".equalsIgnoreCase(parsed.getScheme()))
                    || parsed.getHost() == null) {
                throw ComponentCatalogApiException.sourceMetadataInvalid();
            }
        } catch (URISyntaxException exception) {
            throw ComponentCatalogApiException.sourceMetadataInvalid();
        }
        return new SourceMetadata(type, normalizedUrl, normalizedTitle, capturedAt.toLocalDateTime());
    }

    private ComponentRecord requiredReadableComponent(Long componentId) {
        ComponentRecord row = mapper.selectComponent(componentId);
        if (row == null || (!access.canReadAllStates() && !"ACTIVE".equals(row.getStatus()))) {
            throw ComponentCatalogApiException.componentNotFound();
        }
        return row;
    }

    private ComponentRecord requiredComponentForUpdate(Long componentId) {
        ComponentRecord row = mapper.selectComponentForUpdate(componentId);
        if (row == null) {
            throw ComponentCatalogApiException.componentNotFound();
        }
        return row;
    }

    private ComponentSpecCandidateRecord requiredCandidateForUpdate(Long candidateId) {
        ComponentSpecCandidateRecord row = mapper.selectSpecCandidateForUpdate(candidateId);
        if (row == null) {
            throw ComponentCatalogApiException.candidateNotFound();
        }
        return row;
    }

    private void requireCandidateRelation(ComponentSpecCandidateRecord candidate, Long componentId) {
        if (!componentId.equals(candidate.getComponentId())) {
            throw ComponentCatalogApiException.candidateNotFound();
        }
    }

    private void requirePending(ComponentSpecCandidateRecord candidate) {
        if (!"PENDING".equals(candidate.getStatus())) {
            throw ComponentCatalogApiException.candidateAlreadyDecided();
        }
    }

    private void requireMutable(ComponentRecord component) {
        if ("INACTIVE".equals(component.getStatus())) {
            throw ComponentCatalogApiException.invalidTransition();
        }
    }

    private void requireVersion(Integer current, Integer requested) {
        if (!current.equals(requested)) {
            throw ComponentCatalogApiException.versionConflict(current);
        }
    }

    private ComponentCatalogApiException versionConflictComponent(Long componentId) {
        ComponentRecord current = mapper.selectComponent(componentId);
        return current == null ? ComponentCatalogApiException.componentNotFound()
                : ComponentCatalogApiException.versionConflict(current.getVersion());
    }

    private ComponentCatalogApiException currentCandidateConflict(Long candidateId) {
        ComponentSpecCandidateRecord current = mapper.selectSpecCandidate(candidateId);
        if (current == null) {
            return ComponentCatalogApiException.candidateNotFound();
        }
        return "PENDING".equals(current.getStatus())
                ? ComponentCatalogApiException.versionConflict(current.getVersion())
                : ComponentCatalogApiException.candidateAlreadyDecided();
    }

    private SpecCandidateVO candidateView(ComponentSpecCandidateRecord row) {
        SpecCandidateVO view = new SpecCandidateVO();
        view.setId(row.getId());
        view.setComponentId(row.getComponentId());
        view.setStatus(row.getStatus());
        view.setSourceType(row.getSourceType());
        view.setSourceUrl(row.getSourceUrl());
        view.setSourceTitle(row.getSourceTitle());
        view.setCapturedAt(row.getCapturedAt());
        view.setRawSpecs(codec.readList(row.getRawSpecsJson()));
        view.setNormalizedProposal(codec.readList(row.getNormalizedProposalJson()));
        view.setSubmittedBy(row.getSubmittedBy());
        view.setSubmittedAt(row.getSubmittedAt());
        view.setDecidedBy(row.getDecidedBy());
        view.setDecidedAt(row.getDecidedAt());
        view.setDecisionNote(row.getDecisionNote());
        view.setVersion(row.getVersion());
        return view;
    }

    private SpecSnapshotVO snapshotView(ComponentSpecSnapshotRecord row) {
        SpecSnapshotVO view = new SpecSnapshotVO();
        view.setId(row.getId());
        view.setComponentId(row.getComponentId());
        view.setSnapshotVersion(row.getSnapshotVersion());
        view.setSourceType(row.getSourceType());
        view.setSourceUrl(row.getSourceUrl());
        view.setSourceTitle(row.getSourceTitle());
        view.setCapturedAt(row.getCapturedAt());
        view.setCandidateId(row.getCandidateId());
        view.setSourceNote(row.getSourceNote());
        view.setRawSpecs(codec.readList(row.getRawSpecsJson()));
        view.setSpecifications(codec.readList(row.getSpecificationsJson()));
        view.setChangeSummary(row.getChangeSummary());
        view.setChecksum(row.getChecksum());
        view.setConfirmedBy(row.getConfirmedBy());
        view.setConfirmedAt(row.getConfirmedAt());
        return view;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw ComponentCatalogApiException.invalid();
        }
    }

    private String enumOrNull(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw ComponentCatalogApiException.invalid();
        }
        return normalized;
    }

    private record SourceMetadata(String type, String url, String title, LocalDateTime capturedAt) {
    }
}
