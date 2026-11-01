package com.usn.labhub.user.service;

import com.usn.labhub.user.component.ComponentCatalogAccessPolicy;
import com.usn.labhub.user.component.ComponentCatalogApiException;
import com.usn.labhub.user.component.ComponentCatalogNormalizer;
import com.usn.labhub.user.component.ComponentCatalogSupport;
import com.usn.labhub.user.domain.dto.component.CategoryCreateDTO;
import com.usn.labhub.user.domain.dto.component.CategoryUpdateDTO;
import com.usn.labhub.user.domain.dto.component.ComponentCreateDTO;
import com.usn.labhub.user.domain.dto.component.ComponentUpdateDTO;
import com.usn.labhub.user.domain.dto.component.ManufacturerCreateDTO;
import com.usn.labhub.user.domain.dto.component.ManufacturerUpdateDTO;
import com.usn.labhub.user.domain.dto.component.StatusChangeDTO;
import com.usn.labhub.user.domain.dto.component.VersionActionDTO;
import com.usn.labhub.user.domain.entity.component.ComponentCategoryRecord;
import com.usn.labhub.user.domain.entity.component.ComponentManufacturerRecord;
import com.usn.labhub.user.domain.entity.component.ComponentRecord;
import com.usn.labhub.user.domain.vo.component.CatalogPageVO;
import com.usn.labhub.user.domain.vo.component.CategoryVO;
import com.usn.labhub.user.domain.vo.component.ComponentVO;
import com.usn.labhub.user.domain.vo.component.ManufacturerVO;
import com.usn.labhub.user.mapper.ComponentCatalogMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ComponentCatalogService {
    private static final Set<String> CATEGORY_STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> COMPONENT_STATUSES = Set.of("DRAFT", "ACTIVE", "INACTIVE");
    private static final Set<String> BASE_UNITS = Set.of("PCS", "METER", "GRAM", "MILLILITER", "SET");
    private static final Map<String, String> MANUFACTURER_SORTS = Map.of(
            "updateTime", "update_time", "createTime", "create_time", "name", "name");
    private static final Map<String, String> COMPONENT_SORTS = Map.of(
            "updateTime", "c.update_time", "createTime", "c.create_time", "mpn", "c.normalized_mpn",
            "name", "c.name", "manufacturerName", "m.name");

    private final ComponentCatalogMapper mapper;
    private final ComponentCatalogAccessPolicy access;
    private final ComponentCatalogNormalizer normalizer;
    private final ComponentCatalogSupport support;

    public ComponentCatalogService(ComponentCatalogMapper mapper,
                                   ComponentCatalogAccessPolicy access,
                                   ComponentCatalogNormalizer normalizer,
                                   ComponentCatalogSupport support) {
        this.mapper = mapper;
        this.access = access;
        this.normalizer = normalizer;
        this.support = support;
    }

    @Transactional
    public Map<String, Object> createCategory(String key, CategoryCreateDTO request) {
        access.requireManage();
        return support.idempotent("CREATE_COMPONENT_CATEGORY", key, request,
                () -> createCategoryValue(key, request));
    }

    public List<CategoryVO> categories() {
        access.requireAccess();
        List<CategoryVO> rows = mapper.selectCategories(access.canReadAllStates());
        Map<Long, CategoryVO> byId = new LinkedHashMap<>();
        rows.forEach(row -> {
            row.setChildren(new ArrayList<>());
            byId.put(row.getId(), row);
        });
        List<CategoryVO> roots = new ArrayList<>();
        for (CategoryVO row : rows) {
            if (row.getParentId() == null || !byId.containsKey(row.getParentId())) {
                roots.add(row);
            } else {
                byId.get(row.getParentId()).getChildren().add(row);
            }
        }
        return roots;
    }

    @Transactional
    public CategoryVO updateCategory(Long id, CategoryUpdateDTO request) {
        access.requireManage();
        ComponentCategoryRecord current = requiredCategoryForUpdate(id);
        requireVersion(current.getVersion(), request.getVersion());
        String name = requiredDisplay(request.getName());
        String normalizedName = normalizer.normalizedName(name);
        if (mapper.countCategoryName(current.getParentScopeId(), normalizedName, id) > 0) {
            throw ComponentCatalogApiException.categoryDuplicateName();
        }
        current.setName(name);
        current.setNormalizedName(normalizedName);
        current.setSortOrder(request.getSortOrder());
        current.setUpdatedBy(support.requiredUserId());
        current.setUpdateTime(LocalDateTime.now());
        if (mapper.updateCategory(current) != 1) {
            throw versionConflictCategory(id);
        }
        support.audit(UUID.randomUUID().toString(), "UPDATE", "COMPONENT_CATEGORY", id,
                current.getStatus(), current.getStatus(), null, Map.of("name", name));
        return categoryView(id);
    }

    @Transactional
    public Map<String, Object> deactivateCategory(Long id, String key, StatusChangeDTO request) {
        access.requireManage();
        return support.idempotent("DEACTIVATE_COMPONENT_CATEGORY:" + id, key, request,
                () -> changeCategoryStatus(id, key, request, "ACTIVE", "INACTIVE"));
    }

    @Transactional
    public Map<String, Object> restoreCategory(Long id, String key, StatusChangeDTO request) {
        access.requireManage();
        return support.idempotent("RESTORE_COMPONENT_CATEGORY:" + id, key, request,
                () -> changeCategoryStatus(id, key, request, "INACTIVE", "ACTIVE"));
    }

    @Transactional
    public Map<String, Object> createManufacturer(String key, ManufacturerCreateDTO request) {
        access.requireManage();
        return support.idempotent("CREATE_COMPONENT_MANUFACTURER", key, request,
                () -> createManufacturerValue(key, request));
    }

    public CatalogPageVO<ManufacturerVO> manufacturers(int page, int pageSize, String status,
                                                        String keyword, String sortBy, String sortOrder) {
        access.requireAccess();
        validatePage(page, pageSize);
        String normalizedStatus = enumOrNull(status, CATEGORY_STATUSES);
        if (!access.canReadAllStates() && normalizedStatus != null && !"ACTIVE".equals(normalizedStatus)) {
            return new CatalogPageVO<>(0, page, pageSize, List.of());
        }
        String normalizedKeyword = keyword(keyword);
        String column = sortColumn(sortBy, MANUFACTURER_SORTS, "updateTime");
        String order = sortOrder(sortOrder);
        long total = mapper.countManufacturers(access.canReadAllStates(), normalizedStatus, normalizedKeyword);
        List<ManufacturerVO> list = mapper.selectManufacturers(access.canReadAllStates(), normalizedStatus,
                normalizedKeyword, column, order, (page - 1) * pageSize, pageSize);
        return new CatalogPageVO<>(total, page, pageSize, list);
    }

    public ManufacturerVO manufacturer(Long id) {
        access.requireAccess();
        ManufacturerVO view = mapper.selectManufacturerView(id, access.canReadAllStates());
        if (view == null) {
            throw ComponentCatalogApiException.manufacturerNotFound();
        }
        return view;
    }

    @Transactional
    public ManufacturerVO updateManufacturer(Long id, ManufacturerUpdateDTO request) {
        access.requireManage();
        ComponentManufacturerRecord current = requiredManufacturerForUpdate(id);
        requireVersion(current.getVersion(), request.getVersion());
        String name = requiredDisplay(request.getName());
        String normalizedName = normalizer.normalizedName(name);
        if (mapper.countManufacturerName(normalizedName, id) > 0) {
            throw ComponentCatalogApiException.manufacturerDuplicate();
        }
        current.setName(name);
        current.setNormalizedName(normalizedName);
        current.setWebsite(normalizer.optional(request.getWebsite()));
        current.setUpdatedBy(support.requiredUserId());
        current.setUpdateTime(LocalDateTime.now());
        if (mapper.updateManufacturer(current) != 1) {
            throw versionConflictManufacturer(id);
        }
        support.audit(UUID.randomUUID().toString(), "UPDATE", "COMPONENT_MANUFACTURER", id,
                current.getStatus(), current.getStatus(), null, Map.of("name", name));
        return manufacturer(id);
    }

    @Transactional
    public Map<String, Object> deactivateManufacturer(Long id, String key, StatusChangeDTO request) {
        access.requireManage();
        return support.idempotent("DEACTIVATE_COMPONENT_MANUFACTURER:" + id, key, request,
                () -> changeManufacturerStatus(id, key, request, "ACTIVE", "INACTIVE"));
    }

    @Transactional
    public Map<String, Object> restoreManufacturer(Long id, String key, StatusChangeDTO request) {
        access.requireManage();
        return support.idempotent("RESTORE_COMPONENT_MANUFACTURER:" + id, key, request,
                () -> changeManufacturerStatus(id, key, request, "INACTIVE", "ACTIVE"));
    }

    @Transactional
    public Map<String, Object> createComponent(String key, ComponentCreateDTO request) {
        access.requireManage();
        return support.idempotent("CREATE_COMPONENT", key, request,
                () -> createComponentValue(key, request));
    }

    public CatalogPageVO<ComponentVO> components(int page, int pageSize, Long categoryId,
                                                  boolean includeDescendants, String status,
                                                  Long manufacturerId, String packageType,
                                                  String baseUnit, String keyword,
                                                  String sortBy, String sortOrder) {
        access.requireAccess();
        validatePage(page, pageSize);
        String normalizedStatus = enumOrNull(status, COMPONENT_STATUSES);
        if (!access.canReadAllStates() && normalizedStatus != null && !"ACTIVE".equals(normalizedStatus)) {
            return new CatalogPageVO<>(0, page, pageSize, List.of());
        }
        String normalizedUnit = enumOrNull(baseUnit, BASE_UNITS);
        String normalizedKeyword = keyword(keyword);
        String normalizedPackage = normalizer.optional(packageType);
        String categoryPath = null;
        if (categoryId != null) {
            ComponentCategoryRecord category = mapper.selectCategory(categoryId);
            if (category == null || (!access.canReadAllStates() && !"ACTIVE".equals(category.getStatus()))) {
                throw ComponentCatalogApiException.categoryNotFound();
            }
            categoryPath = category.getPath();
        }
        String column = sortColumn(sortBy, COMPONENT_SORTS, "updateTime");
        String order = sortOrder(sortOrder);
        long total = mapper.countComponents(access.canReadAllStates(), normalizedStatus, categoryId,
                categoryPath, includeDescendants, manufacturerId, normalizedPackage,
                normalizedUnit, normalizedKeyword);
        List<ComponentVO> list = mapper.selectComponents(access.canReadAllStates(), normalizedStatus,
                categoryId, categoryPath, includeDescendants, manufacturerId, normalizedPackage,
                normalizedUnit, normalizedKeyword, column, order, (page - 1) * pageSize, pageSize);
        list.forEach(this::decorateComponent);
        return new CatalogPageVO<>(total, page, pageSize, list);
    }

    public ComponentVO component(Long id) {
        access.requireAccess();
        ComponentVO view = mapper.selectComponentView(id, access.canReadAllStates());
        if (view == null) {
            throw ComponentCatalogApiException.componentNotFound();
        }
        decorateComponent(view);
        return view;
    }

    @Transactional
    public ComponentVO updateComponent(Long id, ComponentUpdateDTO request) {
        access.requireManage();
        ComponentRecord current = requiredComponentForUpdate(id);
        requireVersion(current.getVersion(), request.getVersion());
        if ("INACTIVE".equals(current.getStatus())) {
            throw ComponentCatalogApiException.invalidTransition();
        }
        requireActiveReferences(request.getCategoryId(), request.getManufacturerId());
        String normalizedMpn = normalizer.normalizedMpn(request.getMpn());
        ComponentRecord duplicate = mapper.selectDuplicateComponent(
                request.getManufacturerId(), normalizedMpn, id);
        if (duplicate != null) {
            throw ComponentCatalogApiException.duplicateModel(duplicate.getId(), duplicate.getStatus());
        }
        applyComponentFields(current, request.getName(), request.getManufacturerId(), request.getMpn(),
                normalizedMpn, request.getCategoryId(), request.getPackageType(), request.getBaseUnit(),
                request.getDescription());
        current.setUpdatedBy(support.requiredUserId());
        current.setUpdateTime(LocalDateTime.now());
        if (mapper.updateComponent(current) != 1) {
            throw versionConflictComponent(id);
        }
        support.audit(UUID.randomUUID().toString(), "UPDATE", "COMPONENT", id,
                current.getStatus(), current.getStatus(), null,
                Map.of("manufacturerId", current.getManufacturerId(), "normalizedMpn", normalizedMpn));
        return component(id);
    }

    @Transactional
    public Map<String, Object> activateComponent(Long id, String key, VersionActionDTO request) {
        access.requireManage();
        return support.idempotent("ACTIVATE_COMPONENT:" + id, key, request,
                () -> changeComponentStatus(id, key, request.getVersion(), null, "DRAFT", "ACTIVE"));
    }

    @Transactional
    public Map<String, Object> deactivateComponent(Long id, String key, StatusChangeDTO request) {
        access.requireManage();
        return support.idempotent("DEACTIVATE_COMPONENT:" + id, key, request,
                () -> {
                    ComponentRecord current = requiredComponentForUpdate(id);
                    requireVersion(current.getVersion(), request.getVersion());
                    if (!Set.of("DRAFT", "ACTIVE").contains(current.getStatus())) {
                        throw ComponentCatalogApiException.invalidTransition();
                    }
                    return persistComponentStatus(current, key, request.getReason(), "INACTIVE");
                });
    }

    @Transactional
    public Map<String, Object> restoreComponent(Long id, String key, StatusChangeDTO request) {
        access.requireManage();
        return support.idempotent("RESTORE_COMPONENT:" + id, key, request,
                () -> changeComponentStatus(id, key, request.getVersion(), request.getReason(),
                        "INACTIVE", "ACTIVE"));
    }

    private Map<String, Object> createCategoryValue(String key, CategoryCreateDTO request) {
        String code = normalizer.code(request.getCode());
        String name = requiredDisplay(request.getName());
        String normalizedName = normalizer.normalizedName(name);
        if (mapper.selectCategoryByCode(code) != null) {
            throw ComponentCatalogApiException.categoryDuplicateCode();
        }
        ComponentCategoryRecord parent = null;
        int level = 1;
        long parentScopeId = 0;
        if (request.getParentId() != null) {
            parent = requiredCategoryForUpdate(request.getParentId());
            if (!"ACTIVE".equals(parent.getStatus())) {
                throw ComponentCatalogApiException.categoryParentInactive();
            }
            level = parent.getLevel() + 1;
            parentScopeId = parent.getId();
            if (level > 3) {
                throw ComponentCatalogApiException.categoryDepthExceeded();
            }
        }
        if (mapper.countCategoryName(parentScopeId, normalizedName, null) > 0) {
            throw ComponentCatalogApiException.categoryDuplicateName();
        }
        LocalDateTime now = LocalDateTime.now();
        ComponentCategoryRecord row = new ComponentCategoryRecord();
        row.setCode(code);
        row.setName(name);
        row.setNormalizedName(normalizedName);
        row.setParentId(parent == null ? null : parent.getId());
        row.setParentScopeId(parentScopeId);
        row.setLevel(level);
        row.setPath("");
        row.setStatus("ACTIVE");
        row.setSortOrder(request.getSortOrder());
        row.setVersion(1);
        row.setCreatedBy(support.requiredUserId());
        row.setUpdatedBy(row.getCreatedBy());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        try {
            mapper.insertCategory(row);
        } catch (DuplicateKeyException exception) {
            if (mapper.selectCategoryByCode(code) != null) {
                throw ComponentCatalogApiException.categoryDuplicateCode();
            }
            throw ComponentCatalogApiException.categoryDuplicateName();
        }
        String path = (parent == null ? "/" : parent.getPath()) + row.getId() + "/";
        mapper.updateCategoryPath(row.getId(), path);
        support.audit(key.trim(), "CREATE", "COMPONENT_CATEGORY", row.getId(),
                null, "ACTIVE", null, Map.of("code", code));
        return support.asMap(categoryView(row.getId()));
    }

    private Map<String, Object> changeCategoryStatus(Long id, String key, StatusChangeDTO request,
                                                      String currentStatus, String targetStatus) {
        ComponentCategoryRecord current = requiredCategoryForUpdate(id);
        requireVersion(current.getVersion(), request.getVersion());
        if (!currentStatus.equals(current.getStatus())) {
            throw ComponentCatalogApiException.versionConflict(current.getVersion());
        }
        if ("INACTIVE".equals(targetStatus)) {
            if (mapper.countActiveChildren(id) > 0 || mapper.countBlockingComponentsByCategory(id) > 0) {
                throw ComponentCatalogApiException.categoryInUse();
            }
        } else if (current.getParentId() != null) {
            ComponentCategoryRecord parent = mapper.selectCategoryForUpdate(current.getParentId());
            if (parent == null || !"ACTIVE".equals(parent.getStatus())) {
                throw ComponentCatalogApiException.categoryParentInactive();
            }
        }
        if (mapper.updateCategoryStatus(id, request.getVersion(), currentStatus, targetStatus,
                support.requiredUserId(), LocalDateTime.now()) != 1) {
            throw versionConflictCategory(id);
        }
        support.audit(key.trim(), targetStatus, "COMPONENT_CATEGORY", id,
                currentStatus, targetStatus, request.getReason(), null);
        return support.asMap(categoryView(id));
    }

    private Map<String, Object> createManufacturerValue(String key, ManufacturerCreateDTO request) {
        String code = normalizer.code(request.getCode());
        String name = requiredDisplay(request.getName());
        String normalizedName = normalizer.normalizedName(name);
        if (mapper.selectManufacturerByCode(code) != null
                || mapper.countManufacturerName(normalizedName, null) > 0) {
            throw ComponentCatalogApiException.manufacturerDuplicate();
        }
        LocalDateTime now = LocalDateTime.now();
        ComponentManufacturerRecord row = new ComponentManufacturerRecord();
        row.setCode(code);
        row.setName(name);
        row.setNormalizedName(normalizedName);
        row.setWebsite(normalizer.optional(request.getWebsite()));
        row.setStatus("ACTIVE");
        row.setVersion(1);
        row.setCreatedBy(support.requiredUserId());
        row.setUpdatedBy(row.getCreatedBy());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        try {
            mapper.insertManufacturer(row);
        } catch (DuplicateKeyException exception) {
            throw ComponentCatalogApiException.manufacturerDuplicate();
        }
        support.audit(key.trim(), "CREATE", "COMPONENT_MANUFACTURER", row.getId(),
                null, "ACTIVE", null, Map.of("code", code));
        return support.asMap(manufacturer(row.getId()));
    }

    private Map<String, Object> changeManufacturerStatus(Long id, String key, StatusChangeDTO request,
                                                          String currentStatus, String targetStatus) {
        ComponentManufacturerRecord current = requiredManufacturerForUpdate(id);
        requireVersion(current.getVersion(), request.getVersion());
        if (!currentStatus.equals(current.getStatus())) {
            throw ComponentCatalogApiException.versionConflict(current.getVersion());
        }
        if ("INACTIVE".equals(targetStatus)
                && mapper.countBlockingComponentsByManufacturer(id) > 0) {
            throw ComponentCatalogApiException.manufacturerInUse();
        }
        if (mapper.updateManufacturerStatus(id, request.getVersion(), currentStatus, targetStatus,
                support.requiredUserId(), LocalDateTime.now()) != 1) {
            throw versionConflictManufacturer(id);
        }
        support.audit(key.trim(), targetStatus, "COMPONENT_MANUFACTURER", id,
                currentStatus, targetStatus, request.getReason(), null);
        return support.asMap(manufacturer(id));
    }

    private Map<String, Object> createComponentValue(String key, ComponentCreateDTO request) {
        requireActiveReferences(request.getCategoryId(), request.getManufacturerId());
        String normalizedMpn = normalizer.normalizedMpn(request.getMpn());
        ComponentRecord duplicate = mapper.selectDuplicateComponent(
                request.getManufacturerId(), normalizedMpn, null);
        if (duplicate != null) {
            throw ComponentCatalogApiException.duplicateModel(duplicate.getId(), duplicate.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        ComponentRecord row = new ComponentRecord();
        applyComponentFields(row, request.getName(), request.getManufacturerId(), request.getMpn(),
                normalizedMpn, request.getCategoryId(), request.getPackageType(), request.getBaseUnit(),
                request.getDescription());
        row.setStatus("DRAFT");
        row.setSpecVersion(0);
        row.setVersion(1);
        row.setCreatedBy(support.requiredUserId());
        row.setUpdatedBy(row.getCreatedBy());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        try {
            mapper.insertComponent(row);
        } catch (DuplicateKeyException exception) {
            ComponentRecord concurrent = mapper.selectDuplicateComponent(
                    request.getManufacturerId(), normalizedMpn, null);
            if (concurrent != null) {
                throw ComponentCatalogApiException.duplicateModel(concurrent.getId(), concurrent.getStatus());
            }
            throw exception;
        }
        String catalogCode = "CMP-%08d".formatted(row.getId());
        if (catalogCode.length() != 12 || mapper.updateCatalogCode(row.getId(), catalogCode) != 1) {
            throw new IllegalStateException("Cannot allocate component catalog code");
        }
        support.audit(key.trim(), "CREATE", "COMPONENT", row.getId(),
                null, "DRAFT", null,
                Map.of("catalogCode", catalogCode, "normalizedMpn", normalizedMpn));
        return support.asMap(component(row.getId()));
    }

    private Map<String, Object> changeComponentStatus(Long id, String key, Integer version, String reason,
                                                       String currentStatus, String targetStatus) {
        ComponentRecord current = requiredComponentForUpdate(id);
        requireVersion(current.getVersion(), version);
        if (!currentStatus.equals(current.getStatus())) {
            throw ComponentCatalogApiException.invalidTransition();
        }
        if ("ACTIVE".equals(targetStatus)) {
            requireActiveReferences(current.getCategoryId(), current.getManufacturerId());
        }
        if ("ACTIVE".equals(targetStatus) && current.getCurrentSpecSnapshotId() == null) {
            throw ComponentCatalogApiException.specRequired();
        }
        return persistComponentStatus(current, key, reason, targetStatus);
    }

    private Map<String, Object> persistComponentStatus(ComponentRecord current, String key,
                                                        String reason, String targetStatus) {
        if (mapper.updateComponentStatus(current.getId(), current.getVersion(), current.getStatus(),
                targetStatus, support.requiredUserId(), LocalDateTime.now()) != 1) {
            throw versionConflictComponent(current.getId());
        }
        support.audit(key.trim(), targetStatus, "COMPONENT", current.getId(),
                current.getStatus(), targetStatus, reason, null);
        return support.asMap(component(current.getId()));
    }

    private void requireActiveReferences(Long categoryId, Long manufacturerId) {
        ComponentCategoryRecord category = mapper.selectCategoryForUpdate(categoryId);
        ComponentManufacturerRecord manufacturer = mapper.selectManufacturerForUpdate(manufacturerId);
        if (category == null) {
            throw ComponentCatalogApiException.categoryNotFound();
        }
        if (manufacturer == null) {
            throw ComponentCatalogApiException.manufacturerNotFound();
        }
        if (!"ACTIVE".equals(category.getStatus()) || !"ACTIVE".equals(manufacturer.getStatus())) {
            throw ComponentCatalogApiException.referenceInactive();
        }
    }

    private void applyComponentFields(ComponentRecord row, String name, Long manufacturerId, String mpn,
                                      String normalizedMpn, Long categoryId, String packageType,
                                      String baseUnit, String description) {
        row.setName(requiredDisplay(name));
        row.setManufacturerId(manufacturerId);
        row.setMpn(requiredDisplay(mpn));
        row.setNormalizedMpn(normalizedMpn);
        row.setCategoryId(categoryId);
        row.setPackageType(requiredDisplay(packageType));
        row.setBaseUnit(baseUnit);
        row.setDescription(normalizer.optional(description));
    }

    private ComponentCategoryRecord requiredCategoryForUpdate(Long id) {
        ComponentCategoryRecord row = id == null ? null : mapper.selectCategoryForUpdate(id);
        if (row == null) {
            throw ComponentCatalogApiException.categoryNotFound();
        }
        return row;
    }

    private ComponentManufacturerRecord requiredManufacturerForUpdate(Long id) {
        ComponentManufacturerRecord row = id == null ? null : mapper.selectManufacturerForUpdate(id);
        if (row == null) {
            throw ComponentCatalogApiException.manufacturerNotFound();
        }
        return row;
    }

    private ComponentRecord requiredComponentForUpdate(Long id) {
        ComponentRecord row = id == null ? null : mapper.selectComponentForUpdate(id);
        if (row == null) {
            throw ComponentCatalogApiException.componentNotFound();
        }
        return row;
    }

    private CategoryVO categoryView(Long id) {
        return mapper.selectCategories(true).stream().filter(row -> row.getId().equals(id)).findFirst()
                .orElseThrow(ComponentCatalogApiException::categoryNotFound);
    }

    private void decorateComponent(ComponentVO view) {
        view.setCategoryPath(mapper.selectCategoryPath(view.getCategoryId()));
        if (view.getPrimaryMediaId() == null) {
            view.setImageState("PLACEHOLDER");
            view.setPlaceholderKey(normalizer.placeholderKey(
                    view.getTopCategoryCode(), view.getPackageType(), view.getNormalizedMpn()));
        } else {
            view.setImageState("READY");
            view.setPlaceholderKey(null);
        }
    }

    private ComponentCatalogApiException versionConflictCategory(Long id) {
        ComponentCategoryRecord latest = mapper.selectCategory(id);
        return latest == null ? ComponentCatalogApiException.categoryNotFound()
                : ComponentCatalogApiException.versionConflict(latest.getVersion());
    }

    private ComponentCatalogApiException versionConflictManufacturer(Long id) {
        ComponentManufacturerRecord latest = mapper.selectManufacturer(id);
        return latest == null ? ComponentCatalogApiException.manufacturerNotFound()
                : ComponentCatalogApiException.versionConflict(latest.getVersion());
    }

    private ComponentCatalogApiException versionConflictComponent(Long id) {
        ComponentRecord latest = mapper.selectComponent(id);
        return latest == null ? ComponentCatalogApiException.componentNotFound()
                : ComponentCatalogApiException.versionConflict(latest.getVersion());
    }

    private void requireVersion(Integer current, Integer requested) {
        if (!current.equals(requested)) {
            throw ComponentCatalogApiException.versionConflict(current);
        }
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

    private String keyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 80) {
            throw ComponentCatalogApiException.invalid();
        }
        return normalized;
    }

    private String sortColumn(String value, Map<String, String> allowed, String defaultValue) {
        String normalized = StringUtils.hasText(value) ? value.trim() : defaultValue;
        String column = allowed.get(normalized);
        if (column == null) {
            throw ComponentCatalogApiException.invalid();
        }
        return column;
    }

    private String sortOrder(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "desc";
        if (!Set.of("asc", "desc").contains(normalized)) {
            throw ComponentCatalogApiException.invalid();
        }
        return normalized;
    }

    private String requiredDisplay(String value) {
        String normalized = normalizer.display(value);
        if (normalized == null) {
            throw ComponentCatalogApiException.invalid();
        }
        return normalized;
    }
}
