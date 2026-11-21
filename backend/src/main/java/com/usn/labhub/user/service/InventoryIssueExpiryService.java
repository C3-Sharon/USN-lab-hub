package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.entity.inventory.InventoryIssueRequestRecord;
import com.usn.labhub.user.inventory.InventoryIssueApiException;
import com.usn.labhub.user.mapper.InventoryIssueMapper;
import com.usn.labhub.user.mapper.ProcurementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class InventoryIssueExpiryService {
    private static final Set<String> EXPIRABLE_STATUSES = Set.of("PENDING_APPROVAL", "APPROVED");
    private static final long SYSTEM_ACTOR_ID = 1L;

    private final InventoryIssueMapper mapper;
    private final ProcurementMapper procurementMapper;
    private final InventoryIssueReleaseSupport releaseSupport;

    public InventoryIssueExpiryService(InventoryIssueMapper mapper,
                                       ProcurementMapper procurementMapper,
                                       InventoryIssueReleaseSupport releaseSupport) {
        this.mapper = mapper;
        this.procurementMapper = procurementMapper;
        this.releaseSupport = releaseSupport;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireIfNeeded(Long id) {
        InventoryIssueRequestRecord issue = mapper.selectRequestForUpdate(id);
        if (issue == null || !EXPIRABLE_STATUSES.contains(issue.getStatus())) {
            return;
        }
        if (!releaseSupport.releaseExpiredLocked(issue)) {
            return;
        }
        LocalDateTime dbNow = mapper.selectCurrentTimestamp();
        if (mapper.expireRequest(id, issue.getVersion(), SYSTEM_ACTOR_ID, dbNow) != 1) {
            throw InventoryIssueApiException.concurrentConflict();
        }
        procurementMapper.insertAudit(UUID.randomUUID().toString(), SYSTEM_ACTOR_ID, "EXPIRE",
                "INVENTORY_ISSUE_REQUEST", id, issue.getStatus(), "EXPIRED", null, null, dbNow);
    }
}
