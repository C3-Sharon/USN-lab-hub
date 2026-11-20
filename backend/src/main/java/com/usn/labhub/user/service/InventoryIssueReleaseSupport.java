package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.entity.inventory.InventoryIssueLineRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueRequestRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueReservationAllocationRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueReservationRecord;
import com.usn.labhub.user.inventory.InventoryIssueApiException;
import com.usn.labhub.user.mapper.InventoryIssueMapper;
import com.usn.labhub.user.mapper.ProcurementMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class InventoryIssueReleaseSupport {
    private final InventoryIssueMapper mapper;
    private final ProcurementMapper procurementMapper;

    public InventoryIssueReleaseSupport(InventoryIssueMapper mapper,
                                        ProcurementMapper procurementMapper) {
        this.mapper = mapper;
        this.procurementMapper = procurementMapper;
    }

    public void releaseLocked(InventoryIssueRequestRecord issue, String releaseReason,
                              LocalDateTime now) {
        LockedContext context = lock(issue.getId());
        releaseActive(context, releaseReason, now);
    }

    public boolean releaseExpiredLocked(InventoryIssueRequestRecord issue) {
        LockedContext context = lock(issue.getId());
        LocalDateTime dbNow = mapper.selectCurrentTimestamp();
        boolean expired = context.reservations().stream()
                .anyMatch(reservation -> "ACTIVE".equals(reservation.getStatus())
                        && reservation.getExpiresAt() != null
                        && reservation.getExpiresAt().isBefore(dbNow));
        if (!expired) {
            return false;
        }
        releaseActive(context, "EXPIRED", dbNow);
        return true;
    }

    private LockedContext lock(Long requestId) {
        List<InventoryIssueLineRecord> lines = mapper.selectLinesForUpdate(requestId);
        List<Long> componentIds = lines.stream().map(InventoryIssueLineRecord::getComponentId)
                .distinct().sorted().toList();
        if (!componentIds.isEmpty()) {
            procurementMapper.selectInventoryBalancesForUpdate(componentIds);
        }
        List<InventoryIssueReservationAllocationRecord> allocations =
                mapper.selectAllocationsForRequest(requestId);
        List<Long> batchIds = allocations.stream()
                .map(InventoryIssueReservationAllocationRecord::getBatchId)
                .distinct().sorted().toList();
        if (!batchIds.isEmpty()) {
            mapper.selectBatchesForUpdateByIds(batchIds);
        }
        List<InventoryIssueReservationRecord> reservations =
                mapper.selectReservationsForUpdate(requestId);
        return new LockedContext(reservations, allocations);
    }

    private void releaseActive(LockedContext context, String releaseReason, LocalDateTime now) {
        List<InventoryIssueReservationRecord> reservations = context.reservations();
        List<InventoryIssueReservationAllocationRecord> allocations = context.allocations();
        List<InventoryIssueReservationRecord> active = reservations.stream()
                .filter(reservation -> "ACTIVE".equals(reservation.getStatus())).toList();
        if (active.isEmpty()) {
            return;
        }
        Map<Long, List<InventoryIssueReservationAllocationRecord>> byReservation = allocations.stream()
                .collect(Collectors.groupingBy(InventoryIssueReservationAllocationRecord::getReservationId));

        Map<Long, BigDecimal> releaseByComponent = new HashMap<>();
        for (InventoryIssueReservationRecord reservation : active) {
            releaseByComponent.merge(reservation.getComponentId(), reservation.getQuantity(),
                    BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> entry : releaseByComponent.entrySet()) {
            if (mapper.releaseBalance(entry.getKey(), entry.getValue(), now) != 1) {
                throw InventoryIssueApiException.concurrentConflict();
            }
        }
        String reservationStatus = "EXPIRED".equals(releaseReason) ? "EXPIRED" : "RELEASED";
        for (InventoryIssueReservationRecord reservation : active) {
            for (InventoryIssueReservationAllocationRecord allocation :
                    byReservation.getOrDefault(reservation.getId(), List.of())) {
                if (mapper.releaseBatch(allocation.getBatchId(), allocation.getQuantity()) != 1) {
                    throw InventoryIssueApiException.concurrentConflict();
                }
            }
            if (mapper.releaseReservation(reservation.getId(), reservationStatus, releaseReason) != 1) {
                throw InventoryIssueApiException.concurrentConflict();
            }
            if (mapper.releaseLine(reservation.getRequestLineId(), reservation.getQuantity()) != 1) {
                throw InventoryIssueApiException.concurrentConflict();
            }
        }
    }

    private record LockedContext(List<InventoryIssueReservationRecord> reservations,
                                 List<InventoryIssueReservationAllocationRecord> allocations) {
    }
}
