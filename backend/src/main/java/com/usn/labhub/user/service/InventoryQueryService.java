package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.vo.procurement.InventoryBalanceVO;
import com.usn.labhub.user.domain.vo.procurement.InventoryBatchVO;
import com.usn.labhub.user.domain.vo.procurement.InventoryMovementVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementPageVO;
import com.usn.labhub.user.mapper.ProcurementMapper;
import com.usn.labhub.user.procurement.ProcurementAccessPolicy;
import com.usn.labhub.user.procurement.ProcurementApiException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryQueryService {
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

    public ProcurementPageVO<InventoryMovementVO> movements(Long componentId, int page, int pageSize) {
        access.requireInventoryDetail();
        validatePage(page, pageSize);
        return page(mapper.countInventoryMovements(componentId), page, pageSize,
                mapper.selectInventoryMovements(componentId, (page - 1) * pageSize, pageSize));
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw ProcurementApiException.invalid();
        }
    }

    private <T> ProcurementPageVO<T> page(long total, int page, int pageSize, List<T> list) {
        return new ProcurementPageVO<>(total, page, pageSize, list);
    }
}
