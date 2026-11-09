package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.dto.procurement.ProcurementAttachmentDeleteDTO;
import com.usn.labhub.user.domain.entity.component.MediaObjectRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementAttachmentRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementOrderRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementReceiptRecord;
import com.usn.labhub.user.domain.vo.component.MediaObjectVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementAttachmentVO;
import com.usn.labhub.user.mapper.ProcurementAttachmentMapper;
import com.usn.labhub.user.mapper.ProcurementMapper;
import com.usn.labhub.user.media.CommittedMedia;
import com.usn.labhub.user.media.MediaStorageException;
import com.usn.labhub.user.media.MediaStoragePort;
import com.usn.labhub.user.media.MediaUploadDescriptor;
import com.usn.labhub.user.procurement.ProcurementAccessPolicy;
import com.usn.labhub.user.procurement.ProcurementApiException;
import com.usn.labhub.user.procurement.ProcurementSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProcurementAttachmentService {
    private static final String ORDER = "PROCUREMENT_ORDER";
    private static final String RECEIPT = "PROCUREMENT_RECEIPT";
    private static final Set<String> ORDER_PURPOSES = Set.of("ORDER_SCREENSHOT", "INVOICE");
    private static final Set<String> RECEIPT_PURPOSES = Set.of("RECEIPT_PHOTO", "PACKAGE_PHOTO");

    private final ProcurementAttachmentMapper attachmentMapper;
    private final ProcurementMapper procurementMapper;
    private final ProcurementAccessPolicy access;
    private final ProcurementSupport support;
    private final MediaStoragePort storage;
    private final TransactionTemplate transactions;

    public ProcurementAttachmentService(ProcurementAttachmentMapper attachmentMapper,
                                        ProcurementMapper procurementMapper,
                                        ProcurementAccessPolicy access,
                                        ProcurementSupport support,
                                        MediaStoragePort storage,
                                        TransactionTemplate transactions) {
        this.attachmentMapper = attachmentMapper;
        this.procurementMapper = procurementMapper;
        this.access = access;
        this.support = support;
        this.storage = storage;
        this.transactions = transactions;
    }

    public List<ProcurementAttachmentVO> orderAttachments(Long orderId) {
        requireVisibleOrder(orderId);
        return attachmentMapper.selectViews(ORDER, orderId);
    }

    public List<ProcurementAttachmentVO> receiptAttachments(Long receiptId) {
        requireVisibleReceipt(receiptId);
        return attachmentMapper.selectViews(RECEIPT, receiptId);
    }

    public Map<String, Object> uploadOrder(Long orderId, String key, MultipartFile file, String purpose,
                                            Integer ownerVersion, String invoiceNo, String note) {
        validateUpload(ORDER, orderId, purpose, ownerVersion, invoiceNo, note);
        ProcurementOrderRecord order = requireVisibleOrder(orderId);
        access.requireOrderAttachmentWrite(order);
        return upload(ORDER, orderId, key, file, purpose, ownerVersion, invoiceNo, note);
    }

    public Map<String, Object> uploadReceipt(Long receiptId, String key, MultipartFile file, String purpose,
                                              Integer ownerVersion, String note) {
        validateUpload(RECEIPT, receiptId, purpose, ownerVersion, null, note);
        requireVisibleReceipt(receiptId);
        access.requireReceiptAttachmentWrite();
        return upload(RECEIPT, receiptId, key, file, purpose, ownerVersion, null, note);
    }

    public Map<String, Object> replace(Long attachmentId, String key, MultipartFile file,
                                       Integer attachmentVersion, Integer mediaVersion, Integer ownerVersion) {
        if (attachmentId == null || attachmentVersion == null || attachmentVersion < 1
                || mediaVersion == null || mediaVersion < 1 || ownerVersion == null || ownerVersion < 1) {
            throw ProcurementApiException.invalid();
        }
        ProcurementAttachmentRecord visible = requireVisibleAttachment(attachmentId, true);
        PreparedMedia prepared = prepare(file);
        boolean[] retained = {false};
        try {
            Map<String, Object> result = transactions.execute(status -> support.idempotent(
                    "POST:/api/procurement-attachments/" + attachmentId + "/replacement", key,
                    mutationDigest(visible.getPurpose(), attachmentVersion, mediaVersion, ownerVersion,
                            null, null, prepared),
                    () -> replaceLocked(visible, attachmentVersion, mediaVersion, ownerVersion,
                            prepared, key, retained)));
            rollbackUnlessRetained(prepared, retained[0]);
            return result;
        } catch (RuntimeException exception) {
            rollbackUnlessRetained(prepared, retained[0]);
            throw exception;
        }
    }

    public Map<String, Object> delete(Long attachmentId, ProcurementAttachmentDeleteDTO request) {
        if (attachmentId == null || request == null) {
            throw ProcurementApiException.invalid();
        }
        ProcurementAttachmentRecord visible = requireVisibleAttachment(attachmentId, true);
        return transactions.execute(status -> {
            OwnerLock owner = lockOwner(visible.getOwnerType(), visible.getOwnerId(), request.ownerVersion(), true);
            ProcurementAttachmentRecord attachment = attachmentMapper.selectAttachmentForUpdate(attachmentId);
            requireSameReadyAttachment(visible, attachment, request.attachmentVersion());
            MediaObjectRecord media = attachmentMapper.selectMediaForUpdate(attachment.getMediaId());
            requireReadyMedia(attachment, media, request.mediaVersion());
            LocalDateTime now = LocalDateTime.now();
            Long actorId = access.userId();
            if (attachmentMapper.markMediaDeleted(media.getId(), media.getVersion(), actorId, now) != 1
                    || attachmentMapper.markAttachmentDeleted(attachment.getId(), attachment.getVersion(), actorId, now) != 1) {
                throw ProcurementApiException.versionConflict(attachment.getVersion());
            }
            int newOwnerVersion = incrementOwner(owner, actorId, now);
            support.audit(UUID.randomUUID().toString(), "DELETE_PROCUREMENT_ATTACHMENT", "PROCUREMENT_ATTACHMENT", attachmentId,
                    "READY", "DELETED", request.reason().trim(), Map.of("mediaId", media.getId()));
            return Map.of("attachmentId", attachmentId, "ownerVersion", newOwnerVersion, "deleted", true);
        });
    }

    public MediaObjectVO metadata(Long mediaId) {
        VisibleMedia visible = requireVisibleMedia(mediaId);
        return mediaView(visible.media());
    }

    public MediaContent content(Long mediaId) {
        VisibleMedia visible = requireVisibleMedia(mediaId);
        try {
            return new MediaContent(storage.open(visible.media().getDisplayObjectKey()), visible.media().getContentType());
        } catch (MediaStorageException exception) {
            throw ProcurementApiException.mediaStorageFailed();
        }
    }

    private Map<String, Object> upload(String ownerType, Long ownerId, String key, MultipartFile file,
                                       String purpose, Integer ownerVersion, String invoiceNo, String note) {
        PreparedMedia prepared = prepare(file);
        boolean[] retained = {false};
        try {
            Map<String, Object> result = transactions.execute(status -> support.idempotent(
                    "POST:/api/" + (ORDER.equals(ownerType) ? "procurement-orders/" : "procurement-receipts/")
                            + ownerId + "/attachments", key,
                    mutationDigest(purpose, null, null, ownerVersion, invoiceNo, note, prepared),
                    () -> uploadLocked(ownerType, ownerId, purpose, ownerVersion, invoiceNo, note,
                            prepared, key, retained)));
            rollbackUnlessRetained(prepared, retained[0]);
            return result;
        } catch (RuntimeException exception) {
            rollbackUnlessRetained(prepared, retained[0]);
            throw exception;
        }
    }

    private Map<String, Object> uploadLocked(String ownerType, Long ownerId, String purpose,
                                              Integer ownerVersion, String invoiceNo, String note,
                                              PreparedMedia prepared, String requestId, boolean[] retained) {
        OwnerLock owner = lockOwner(ownerType, ownerId, ownerVersion, true);
        checkLimit(ownerType, ownerId, purpose);
        MediaObjectRecord media = newMedia(ownerType, ownerId, prepared);
        attachmentMapper.insertMedia(media);
        ProcurementAttachmentRecord attachment = newAttachment(ownerType, ownerId, media.getId(), purpose,
                invoiceNo, note);
        attachmentMapper.insertAttachment(attachment);
        int newOwnerVersion = incrementOwner(owner, access.userId(), LocalDateTime.now());
        support.audit(requestId, "UPLOAD_PROCUREMENT_ATTACHMENT", "PROCUREMENT_ATTACHMENT", attachment.getId(),
                null, "READY", null, auditDetails(attachment, media));
        retained[0] = true;
        return response(attachment.getId(), newOwnerVersion);
    }

    private Map<String, Object> replaceLocked(ProcurementAttachmentRecord visible,
                                               Integer attachmentVersion, Integer mediaVersion,
                                               Integer ownerVersion, PreparedMedia prepared,
                                               String requestId, boolean[] retained) {
        OwnerLock owner = lockOwner(visible.getOwnerType(), visible.getOwnerId(), ownerVersion, true);
        ProcurementAttachmentRecord attachment = attachmentMapper.selectAttachmentForUpdate(visible.getId());
        requireSameReadyAttachment(visible, attachment, attachmentVersion);
        MediaObjectRecord old = attachmentMapper.selectMediaForUpdate(attachment.getMediaId());
        requireReadyMedia(attachment, old, mediaVersion);
        LocalDateTime now = LocalDateTime.now();
        Long actorId = access.userId();
        if (attachmentMapper.markMediaDeleted(old.getId(), old.getVersion(), actorId, now) != 1) {
            throw ProcurementApiException.versionConflict(old.getVersion());
        }
        MediaObjectRecord replacement = newMedia(attachment.getOwnerType(), attachment.getOwnerId(), prepared);
        attachmentMapper.insertMedia(replacement);
        if (attachmentMapper.replaceAttachmentMedia(attachment.getId(), attachment.getVersion(), replacement.getId()) != 1) {
            throw ProcurementApiException.versionConflict(attachment.getVersion());
        }
        int newOwnerVersion = incrementOwner(owner, actorId, now);
        support.audit(requestId, "REPLACE_PROCUREMENT_ATTACHMENT", "PROCUREMENT_ATTACHMENT", attachment.getId(),
                "READY", "READY", null, Map.of("replacedMediaId", old.getId(), "mediaId", replacement.getId()));
        retained[0] = true;
        return response(attachment.getId(), newOwnerVersion);
    }

    private OwnerLock lockOwner(String ownerType, Long ownerId, Integer expectedVersion, boolean write) {
        if (ORDER.equals(ownerType)) {
            ProcurementOrderRecord order = procurementMapper.selectOrderForUpdate(ownerId);
            if (order == null || !access.canView(order)) throw ProcurementApiException.mediaNotFound();
            if (write) access.requireOrderAttachmentWrite(order);
            if (!expectedVersion.equals(order.getVersion())) throw ProcurementApiException.versionConflict(order.getVersion());
            return new OwnerLock(ownerType, ownerId, order.getVersion());
        }
        ProcurementReceiptRecord receipt = procurementMapper.selectReceiptForUpdate(ownerId);
        if (receipt == null) throw ProcurementApiException.mediaNotFound();
        ProcurementOrderRecord order = procurementMapper.selectOrder(receipt.getOrderId());
        if (order == null || !access.canView(order)) throw ProcurementApiException.mediaNotFound();
        if (write) access.requireReceiptAttachmentWrite();
        if (!expectedVersion.equals(receipt.getVersion())) throw ProcurementApiException.versionConflict(receipt.getVersion());
        return new OwnerLock(ownerType, ownerId, receipt.getVersion());
    }

    private int incrementOwner(OwnerLock owner, Long actorId, LocalDateTime now) {
        int changed = ORDER.equals(owner.ownerType())
                ? procurementMapper.incrementOrderVersion(owner.ownerId(), owner.version(), actorId, now)
                : procurementMapper.incrementReceiptVersion(owner.ownerId(), owner.version(), actorId, now);
        if (changed != 1) throw ProcurementApiException.versionConflict(owner.version());
        return owner.version() + 1;
    }

    private ProcurementOrderRecord requireVisibleOrder(Long orderId) {
        ProcurementOrderRecord order = orderId == null ? null : procurementMapper.selectOrder(orderId);
        if (order == null || !access.canView(order)) throw ProcurementApiException.orderNotFound();
        return order;
    }

    private ProcurementReceiptRecord requireVisibleReceipt(Long receiptId) {
        ProcurementReceiptRecord receipt = receiptId == null ? null : procurementMapper.selectReceipt(receiptId);
        if (receipt == null) throw ProcurementApiException.receiptNotFound();
        ProcurementOrderRecord order = procurementMapper.selectOrder(receipt.getOrderId());
        if (order == null || !access.canView(order)) throw ProcurementApiException.receiptNotFound();
        return receipt;
    }

    private ProcurementAttachmentRecord requireVisibleAttachment(Long id, boolean write) {
        ProcurementAttachmentRecord attachment = id == null ? null : attachmentMapper.selectAttachment(id);
        if (attachment == null || !"READY".equals(attachment.getStatus())) throw ProcurementApiException.mediaNotFound();
        if (ORDER.equals(attachment.getOwnerType())) {
            ProcurementOrderRecord order = procurementMapper.selectOrder(attachment.getOwnerId());
            if (order == null || !access.canView(order)) throw ProcurementApiException.mediaNotFound();
            if (write) access.requireOrderAttachmentWrite(order);
        } else if (RECEIPT.equals(attachment.getOwnerType())) {
            ProcurementReceiptRecord receipt = procurementMapper.selectReceipt(attachment.getOwnerId());
            ProcurementOrderRecord order = receipt == null ? null : procurementMapper.selectOrder(receipt.getOrderId());
            if (order == null || !access.canView(order)) throw ProcurementApiException.mediaNotFound();
            if (write) access.requireReceiptAttachmentWrite();
        } else {
            throw ProcurementApiException.mediaNotFound();
        }
        return attachment;
    }

    private VisibleMedia requireVisibleMedia(Long mediaId) {
        MediaObjectRecord media = mediaId == null ? null : attachmentMapper.selectMedia(mediaId);
        ProcurementAttachmentRecord attachment = media == null ? null : attachmentMapper.selectAttachmentByMedia(mediaId);
        if (media == null || attachment == null || !"READY".equals(media.getStatus())
                || !"READY".equals(attachment.getStatus())) throw ProcurementApiException.mediaNotFound();
        requireVisibleAttachment(attachment.getId(), false);
        return new VisibleMedia(attachment, media);
    }

    private void checkLimit(String ownerType, Long ownerId, String purpose) {
        long count = ORDER.equals(ownerType)
                ? attachmentMapper.countReady(ownerType, ownerId, purpose)
                : attachmentMapper.countReadyByOwner(ownerType, ownerId);
        if (count >= (ORDER.equals(ownerType) ? 5 : 10)) throw ProcurementApiException.attachmentLimitExceeded();
    }

    private void validateUpload(String ownerType, Long ownerId, String purpose, Integer ownerVersion,
                                String invoiceNo, String note) {
        boolean purposeValid = ORDER.equals(ownerType) ? ORDER_PURPOSES.contains(purpose) : RECEIPT_PURPOSES.contains(purpose);
        if (ownerId == null || ownerVersion == null || ownerVersion < 1 || !purposeValid
                || (invoiceNo != null && invoiceNo.trim().length() > 120)
                || (note != null && note.trim().length() > 500)
                || (!"INVOICE".equals(purpose) && StringUtils.hasText(invoiceNo))) {
            throw ProcurementApiException.invalid();
        }
    }

    private void requireSameReadyAttachment(ProcurementAttachmentRecord expected,
                                             ProcurementAttachmentRecord actual, Integer version) {
        if (actual == null || !"READY".equals(actual.getStatus())
                || !expected.getOwnerType().equals(actual.getOwnerType())
                || !expected.getOwnerId().equals(actual.getOwnerId())) throw ProcurementApiException.mediaNotFound();
        if (!version.equals(actual.getVersion())) throw ProcurementApiException.versionConflict(actual.getVersion());
    }

    private void requireReadyMedia(ProcurementAttachmentRecord attachment, MediaObjectRecord media, Integer version) {
        if (media == null || !"READY".equals(media.getStatus())
                || !attachment.getOwnerType().equals(media.getOwnerType())
                || !attachment.getOwnerId().equals(media.getOwnerId())) throw ProcurementApiException.mediaNotFound();
        if (!version.equals(media.getVersion())) throw ProcurementApiException.versionConflict(media.getVersion());
    }

    private PreparedMedia prepare(MultipartFile file) {
        if (file == null || !StringUtils.hasText(file.getOriginalFilename())) throw ProcurementApiException.invalid();
        try {
            var staged = storage.stage(file.getInputStream());
            CommittedMedia committed = storage.commit(staged,
                    new MediaUploadDescriptor(file.getOriginalFilename(), file.getContentType()));
            return new PreparedMedia(file.getOriginalFilename().trim(), committed);
        } catch (MediaStorageException exception) {
            throw switch (exception.getReason()) {
                case TOO_LARGE -> ProcurementApiException.mediaTooLarge();
                case TYPE_UNSUPPORTED -> ProcurementApiException.mediaTypeUnsupported();
                case DECODE_FAILED -> ProcurementApiException.mediaDecodeFailed();
                case PROCESSING_FAILED -> ProcurementApiException.mediaProcessingFailed();
                default -> ProcurementApiException.mediaStorageFailed();
            };
        } catch (IOException exception) {
            throw ProcurementApiException.mediaStorageFailed();
        }
    }

    private MediaObjectRecord newMedia(String ownerType, Long ownerId, PreparedMedia prepared) {
        CommittedMedia file = prepared.committed();
        MediaObjectRecord media = new MediaObjectRecord();
        media.setOwnerType(ownerType); media.setOwnerId(ownerId); media.setRole("GALLERY"); media.setStatus("READY");
        media.setOriginalFileName(prepared.originalFilename()); media.setContentType(file.contentType());
        media.setSizeBytes(file.sizeBytes()); media.setWidth(file.width()); media.setHeight(file.height());
        media.setSha256(file.sha256()); media.setOriginalObjectKey(file.originalObjectKey());
        media.setDisplayObjectKey(file.displayObjectKey()); media.setSortOrder(0); media.setReadyDedupSlot(1);
        media.setVersion(1); media.setUploadedBy(access.userId()); media.setUploadedAt(LocalDateTime.now());
        return media;
    }

    private ProcurementAttachmentRecord newAttachment(String ownerType, Long ownerId, Long mediaId,
                                                        String purpose, String invoiceNo, String note) {
        ProcurementAttachmentRecord attachment = new ProcurementAttachmentRecord();
        attachment.setOwnerType(ownerType); attachment.setOwnerId(ownerId); attachment.setMediaId(mediaId);
        attachment.setPurpose(purpose); attachment.setInvoiceNo(trimToNull(invoiceNo)); attachment.setNote(trimToNull(note));
        attachment.setVersion(1); attachment.setStatus("READY"); attachment.setCreatedBy(access.userId());
        attachment.setCreateTime(LocalDateTime.now());
        return attachment;
    }

    private Map<String, Object> response(Long attachmentId, int ownerVersion) {
        ProcurementAttachmentVO view = attachmentMapper.selectView(attachmentId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ownerVersion", ownerVersion); response.put("attachment", view);
        return response;
    }

    private Map<String, Object> mutationDigest(String purpose, Integer attachmentVersion,
                                                Integer mediaVersion, Integer ownerVersion,
                                                String invoiceNo, String note, PreparedMedia prepared) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("purpose", purpose); digest.put("attachmentVersion", attachmentVersion);
        digest.put("mediaVersion", mediaVersion); digest.put("ownerVersion", ownerVersion);
        digest.put("invoiceNo", trimToNull(invoiceNo)); digest.put("note", trimToNull(note));
        digest.put("originalFilename", prepared.originalFilename());
        digest.put("contentType", prepared.committed().contentType()); digest.put("sha256", prepared.committed().sha256());
        return digest;
    }

    private Map<String, Object> auditDetails(ProcurementAttachmentRecord attachment, MediaObjectRecord media) {
        return Map.of("purpose", attachment.getPurpose(), "mediaId", media.getId(),
                "originalFileName", media.getOriginalFileName(), "contentType", media.getContentType(),
                "size", media.getSizeBytes(), "sha256", media.getSha256());
    }

    private MediaObjectVO mediaView(MediaObjectRecord media) {
        MediaObjectVO view = new MediaObjectVO();
        view.setMediaId(media.getId()); view.setOwnerType(media.getOwnerType()); view.setOwnerId(media.getOwnerId());
        view.setRole(media.getRole()); view.setStatus(media.getStatus()); view.setOriginalFileName(media.getOriginalFileName());
        view.setContentType(media.getContentType()); view.setSize(media.getSizeBytes()); view.setWidth(media.getWidth());
        view.setHeight(media.getHeight()); view.setSha256(media.getSha256());
        view.setContentPath("/api/media/" + media.getId() + "/content"); view.setSortOrder(media.getSortOrder());
        view.setVersion(media.getVersion()); view.setUploadedBy(media.getUploadedBy()); view.setUploadedAt(media.getUploadedAt());
        return view;
    }

    private void rollbackUnlessRetained(PreparedMedia prepared, boolean retained) {
        if (!retained) storage.rollbackNewObject(prepared.committed());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record MediaContent(InputStream input, String contentType) { }
    private record PreparedMedia(String originalFilename, CommittedMedia committed) { }
    private record OwnerLock(String ownerType, Long ownerId, Integer version) { }
    private record VisibleMedia(ProcurementAttachmentRecord attachment, MediaObjectRecord media) { }
}
