package com.usn.labhub.user.service;

import com.usn.labhub.user.mapper.InventoryIssueMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryIssueExpiryScheduler {
    private final InventoryIssueMapper mapper;
    private final InventoryIssueExpiryService expiryService;
    private final int batchSize;

    public InventoryIssueExpiryScheduler(InventoryIssueMapper mapper,
                                         InventoryIssueExpiryService expiryService,
                                         @Value("${usnhub.inventory.issue.expiry-batch-size:100}")
                                         int batchSize) {
        this.mapper = mapper;
        this.expiryService = expiryService;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
    }

    @Scheduled(fixedDelayString = "${usnhub.inventory.issue.expiry-delay-ms:60000}")
    public void scan() {
        List<Long> ids = mapper.selectExpiredCandidateIds(batchSize);
        ids.forEach(expiryService::expireIfNeeded);
    }
}
