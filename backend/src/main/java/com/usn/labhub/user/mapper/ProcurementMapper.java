package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.experiment.IdempotencyRow;
import com.usn.labhub.user.domain.entity.procurement.ProcurementComponentRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementOrderRecord;
import com.usn.labhub.user.domain.vo.procurement.ProcurementOrderLineVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProcurementMapper {
    int insertOrder(ProcurementOrderRecord order);

    int updateDraftOrder(ProcurementOrderRecord order);

    int insertLine(@Param("orderId") Long orderId, @Param("lineNo") int lineNo,
                   @Param("component") ProcurementComponentRecord component,
                   @Param("quantity") java.math.BigDecimal quantity,
                   @Param("unitPrice") java.math.BigDecimal unitPrice,
                   @Param("lineSubtotal") java.math.BigDecimal lineSubtotal);

    int deleteLines(@Param("orderId") Long orderId);

    ProcurementOrderRecord selectOrder(@Param("id") Long id);

    ProcurementOrderVO selectOrderView(@Param("id") Long id);

    List<ProcurementOrderLineVO> selectOrderLines(@Param("orderId") Long orderId);

    ProcurementComponentRecord selectComponentForPurchase(@Param("id") Long id);

    long countOrders(@Param("userId") Long userId, @Param("viewAll") boolean viewAll,
                     @Param("status") String status, @Param("projectId") Long projectId,
                     @Param("requesterId") Long requesterId, @Param("supplierKeyword") String supplierKeyword,
                     @Param("keyword") String keyword);

    List<ProcurementOrderVO> selectOrders(@Param("userId") Long userId, @Param("viewAll") boolean viewAll,
                                          @Param("status") String status, @Param("projectId") Long projectId,
                                          @Param("requesterId") Long requesterId,
                                          @Param("supplierKeyword") String supplierKeyword,
                                          @Param("keyword") String keyword,
                                          @Param("sortColumn") String sortColumn,
                                          @Param("sortOrder") String sortOrder,
                                          @Param("offset") int offset, @Param("pageSize") int pageSize);

    int claimIdempotency(@Param("actorId") Long actorId, @Param("operation") String operation,
                         @Param("key") String key, @Param("digest") String digest,
                         @Param("now") LocalDateTime now);

    IdempotencyRow selectIdempotency(@Param("actorId") Long actorId,
                                     @Param("operation") String operation, @Param("key") String key);

    int completeIdempotency(@Param("actorId") Long actorId, @Param("operation") String operation,
                            @Param("key") String key, @Param("responseJson") String responseJson,
                            @Param("now") LocalDateTime now);

    int insertAudit(@Param("requestId") String requestId, @Param("actorId") Long actorId,
                    @Param("action") String action, @Param("targetType") String targetType,
                    @Param("targetId") Long targetId, @Param("previousStatus") String previousStatus,
                    @Param("newStatus") String newStatus, @Param("detailsJson") String detailsJson,
                    @Param("now") LocalDateTime now);
}
