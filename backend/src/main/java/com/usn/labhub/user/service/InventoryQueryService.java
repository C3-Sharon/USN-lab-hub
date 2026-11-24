package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.vo.procurement.InventoryBalanceVO;
import com.usn.labhub.user.domain.vo.procurement.InventoryBatchVO;
import com.usn.labhub.user.domain.vo.procurement.InventoryMovementVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementPageVO;
import com.usn.labhub.user.mapper.ProcurementMapper;
import com.usn.labhub.user.procurement.ProcurementAccessPolicy;
import com.usn.labhub.user.procurement.ProcurementApiException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class InventoryQueryService {
    private static final Set<String> MOVEMENT_TYPES = Set.of(
            "PROCUREMENT_RECEIPT_IN", "ISSUE_OUT", "RETURN_IN", "LOSS_OUT", "SCRAP_OUT");
    private static final Set<String> SOURCE_TYPES = Set.of(
            "RECEIPT_LINE", "FULFILLMENT_LINE", "RETURN_LINE", "WRITE_OFF_COMMAND");

    private final ProcurementMapper mapper;
    private final ProcurementAccessPolicy access;

    public InventoryQueryService(ProcurementMapper mapper, ProcurementAccessPolicy access) {
        this.mapper = mapper;
        this.access = access;
    }

    public InventoryBalanceVO balance(Long componentId) {
        access.requireCreate();
        InventoryBalanceVO balance = mapper.selectInventoryBalance(componentId);
        if (balance == null || (!access.canViewAll() && !"ACTIVE".equals(balance.getStatus()))) {
            throw ProcurementApiException.componentInvalid();
        }
        return balance;
    }

    public ProcurementPageVO<InventoryBatchVO> batches(Long componentId, int page, int pageSize) {
        access.requireInventoryDetail();
        validatePage(page, pageSize);
        if (mapper.selectInventoryBalance(componentId) == null) {
            throw ProcurementApiException.componentInvalid();
        }
        return page(mapper.countInventoryBatches(componentId), page, pageSize,
                mapper.selectInventoryBatches(componentId, (page - 1) * pageSize, pageSize));
    }

    public ProcurementPageVO<InventoryMovementVO> movements(Long componentId, String movementType,
                                                            String sourceType, Long operatorId,
                                                            String batchNo, LocalDateTime occurredFrom,
                                                            LocalDateTime occurredTo,
                                                            int page, int pageSize) {
        access.requireInventoryDetail();
        validatePage(page, pageSize);
        String normalizedType = enumOrNull(movementType, MOVEMENT_TYPES);
        String normalizedSource = enumOrNull(sourceType, SOURCE_TYPES);
        String normalizedBatchNo = keyword(batchNo, 80);
        return page(mapper.countInventoryMovements(componentId, normalizedType, normalizedSource,
                        operatorId, normalizedBatchNo, occurredFrom, occurredTo),
                page, pageSize,
                mapper.selectInventoryMovements(componentId, normalizedType, normalizedSource,
                        operatorId, normalizedBatchNo, occurredFrom, occurredTo,
                        (page - 1) * pageSize, pageSize));
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw ProcurementApiException.invalid();
        }
    }

    private <T> ProcurementPageVO<T> page(long total, int page, int pageSize, List<T> list) {
        return new ProcurementPageVO<>(total, page, pageSize, list);
    }

    private String enumOrNull(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw ProcurementApiException.invalid();
        }
        return normalized;
    }

    private String keyword(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw ProcurementApiException.invalid();
        }
        return normalized;
    }
}
