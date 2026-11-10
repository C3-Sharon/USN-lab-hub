package com.usn.labhub.user.procurement;

import com.usn.labhub.user.UsnHubApplication;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.service.IAttendanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:w45-procurement-attachment;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ProcurementAttachmentApiIntegrationTest {
    private static final Path MEDIA_ROOT = Path.of("target", "test-media-w45-attachment").toAbsolutePath();

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) {
        registry.add("usnhub.media.storage-root", () -> MEDIA_ROOT.toString());
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private IAttendanceService attendanceService;

    @BeforeEach
    void setUp() throws Exception {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayRecords(List.of());
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);
        jdbc.update("DELETE FROM lab_procurement_attachment");
        jdbc.update("DELETE FROM lab_media_object");
        jdbc.update("DELETE FROM lab_procurement_audit_log");
        jdbc.update("DELETE FROM lab_request_idempotency WHERE operation LIKE 'POST:/api/procurement%'");
        jdbc.update("DELETE FROM lab_procurement_receipt_line");
        jdbc.update("DELETE FROM lab_procurement_receipt");
        jdbc.update("DELETE FROM lab_procurement_approval_record");
        jdbc.update("DELETE FROM lab_procurement_order_line");
        jdbc.update("DELETE FROM lab_procurement_order");
        setRole(2L, 2);
        clearMediaFiles();
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadsReplaysReplacesAndDeletesWithOwnerVersions() throws Exception {
        Long orderId = seedOrder(1L, "DRAFT", "ATT-ORDER-1");
        String admin = token(login("admin", "admin123"));
        byte[] firstImage = imageBytes(11);

        Map<String, Object> uploaded = data(uploadOrder(orderId, admin, "attachment-upload-01",
                firstImage, "invoice.png", "INVOICE", 1, "INV-1"));
        Map<String, Object> attachment = (Map<String, Object>) uploaded.get("attachment");
        Long attachmentId = number(attachment.get("id"));
        Long firstMediaId = number(attachment.get("mediaId"));
        assertEquals(2, integer(uploaded.get("ownerVersion")));
        assertEquals("INV-1", attachment.get("invoiceNo"));
        assertFileCounts(1);

        Map<String, Object> replay = data(uploadOrder(orderId, admin, "attachment-upload-01",
                firstImage, "invoice.png", "INVOICE", 1, "INV-1"));
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        assertEquals(firstMediaId, number(((Map<String, Object>) replay.get("attachment")).get("mediaId")));
        assertFileCounts(1);

        assertReason(uploadOrder(orderId, admin, "attachment-upload-01", firstImage,
                "invoice.png", "INVOICE", 1, "INV-CHANGED"),
                HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
        assertFileCounts(1);

        ResponseEntity<Map> stale = uploadOrder(orderId, admin, "attachment-upload-stale",
                imageBytes(12), "stale.png", "ORDER_SCREENSHOT", 1, null);
        assertReason(stale, HttpStatus.CONFLICT, "VERSION_CONFLICT");
        assertFileCounts(1);

        Map<String, Object> replaced = data(replace(attachmentId, admin, "attachment-replace-01",
                jpegBytes(13), "replacement.jpg", 1, 1, 2));
        Map<String, Object> replacedAttachment = (Map<String, Object>) replaced.get("attachment");
        Long secondMediaId = number(replacedAttachment.get("mediaId"));
        assertNotEquals(firstMediaId, secondMediaId);
        assertEquals(2, integer(replacedAttachment.get("attachmentVersion")));
        assertEquals(3, integer(replaced.get("ownerVersion")));
        assertFileCounts(2);

        Map<String, Object> metadata = data(get("/api/media/" + secondMediaId, admin));
        assertEquals(secondMediaId, number(metadata.get("mediaId")));
        assertEquals("PROCUREMENT_ORDER", metadata.get("ownerType"));

        Map<String, Object> deleted = data(delete(attachmentId, admin,
                Map.of("attachmentVersion", 2, "mediaVersion", 1, "ownerVersion", 3, "reason", "发票重传")));
        assertEquals(4, integer(deleted.get("ownerVersion")));
        assertEquals(Boolean.TRUE, deleted.get("deleted"));
        assertReason(get("/api/media/" + secondMediaId, admin), HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND");
        assertFileCounts(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void enforcesReceiptWriteAclAndMediaZeroLeakage() throws Exception {
        Long orderId = seedOrder(2L, "ORDERED", "ATT-ORDER-2");
        Long receiptId = seedReceipt(orderId, "ATT-RECEIPT-1");
        String admin = token(login("admin", "admin123"));
        String member = token(login("20260001", "20260001"));

        Map<String, Object> memberOrderUpload = data(uploadOrder(orderId, member, "member-order-upload",
                imageBytes(20), "order.png", "ORDER_SCREENSHOT", 1, null));
        assertEquals(2, integer(memberOrderUpload.get("ownerVersion")));
        assertReason(uploadReceipt(receiptId, member, "receipt-member-upload", imageBytes(21),
                "receipt.png", "RECEIPT_PHOTO", 1), HttpStatus.FORBIDDEN, "PROCUREMENT_RECEIPT_DENIED");
        assertFileCounts(1);

        setRole(2L, 3);
        String teacher = token(login("20260001", "20260001"));
        Map<String, Object> teacherOrderUpload = data(uploadOrder(orderId, teacher, "teacher-order-upload",
                imageBytes(21), "invoice.png", "INVOICE", 2, "INV-T"));
        assertEquals(3, integer(teacherOrderUpload.get("ownerVersion")));
        assertReason(uploadReceipt(receiptId, teacher, "receipt-teacher-upload", imageBytes(22),
                "receipt.png", "RECEIPT_PHOTO", 1), HttpStatus.FORBIDDEN, "PROCUREMENT_RECEIPT_DENIED");
        assertFileCounts(2);

        setRole(2L, 4);
        String stockKeeper = token(login("20260001", "20260001"));
        Map<String, Object> uploaded = data(uploadReceipt(receiptId, stockKeeper, "receipt-stock-upload",
                imageBytes(23), "receipt.png", "PACKAGE_PHOTO", 1));
        Long mediaId = number(((Map<String, Object>) uploaded.get("attachment")).get("mediaId"));
        assertEquals(2, integer(uploaded.get("ownerVersion")));
        assertFileCounts(3);
        assertEquals(HttpStatus.OK, getBytes("/api/media/" + mediaId + "/content", admin).getStatusCode());

        jdbc.update("UPDATE lab_procurement_order SET requester_id=1,requester_name='系统管理员' WHERE id=?", orderId);
        setRole(2L, 2);
        String hiddenMember = token(login("20260001", "20260001"));
        ResponseEntity<Map> hiddenMetadata = get("/api/media/" + mediaId, hiddenMember);
        assertReason(hiddenMetadata, HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND");
        assertReason(get("/api/media/" + mediaId + "/content", hiddenMember),
                HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND");
        assertReason(get("/api/procurement-receipts/" + receiptId + "/attachments", hiddenMember),
                HttpStatus.NOT_FOUND, "PROCUREMENT_RECEIPT_NOT_FOUND");
        assertEquals(false, String.valueOf(hiddenMetadata.getBody()).contains("receipt.png"));
    }

    @Test
    void enforcesPurposeLimitsWithoutLeavingFiles() throws Exception {
        Long orderId = seedOrder(1L, "DRAFT", "ATT-ORDER-3");
        String admin = token(login("admin", "admin123"));
        int version = 1;
        for (int index = 1; index <= 5; index++) {
            Map<String, Object> result = data(uploadOrder(orderId, admin, "screenshot-limit-0" + index,
                    imageBytes(30 + index), index + ".png", "ORDER_SCREENSHOT", version, null));
            version = integer(result.get("ownerVersion"));
        }
        assertFileCounts(5);
        assertReason(uploadOrder(orderId, admin, "screenshot-limit-06", imageBytes(36), "6.png",
                "ORDER_SCREENSHOT", version, null), HttpStatus.CONFLICT, "ATTACHMENT_LIMIT_EXCEEDED");
        assertFileCounts(5);
    }

    private Long seedOrder(Long requesterId, String status, String purchaseNo) {
        jdbc.update("""
                INSERT INTO lab_procurement_order(purchase_no,title,requester_id,requester_name,buyer_id,
                    buyer_name,currency,item_subtotal,shipping_amount,discount_amount,payable_amount,
                    actual_paid_amount,status,version,created_by,updated_by)
                VALUES(?, '附件测试采购', ?, '测试申请人', 1, '系统管理员', 'CNY', 10.00, 0.00,
                    0.00, 10.00, 10.00, ?, 1, ?, ?)
                """, purchaseNo, requesterId, status, requesterId, requesterId);
        return jdbc.queryForObject("SELECT id FROM lab_procurement_order WHERE purchase_no=?", Long.class, purchaseNo);
    }

    private Long seedReceipt(Long orderId, String receiptNo) {
        jdbc.update("""
                INSERT INTO lab_procurement_receipt(receipt_no,order_id,status,received_at,version,
                    created_by,updated_by,active_draft_slot)
                VALUES(?,?,'DRAFT',CURRENT_TIMESTAMP,1,1,1,1)
                """, receiptNo, orderId);
        return jdbc.queryForObject("SELECT id FROM lab_procurement_receipt WHERE receipt_no=?", Long.class, receiptNo);
    }

    private void setRole(Long userId, int roleId) {
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", userId, roleId);
    }

    private ResponseEntity<Map> uploadOrder(Long orderId, String token, String key, byte[] bytes,
                                             String filename, String purpose, int version, String invoiceNo) {
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("purpose", purpose); fields.put("orderVersion", String.valueOf(version));
        if (invoiceNo != null) fields.put("invoiceNo", invoiceNo);
        return multipart("/api/procurement-orders/" + orderId + "/attachments", token, key,
                bytes, filename, fields);
    }

    private ResponseEntity<Map> uploadReceipt(Long receiptId, String token, String key, byte[] bytes,
                                               String filename, String purpose, int version) {
        return multipart("/api/procurement-receipts/" + receiptId + "/attachments", token, key,
                bytes, filename, Map.of("purpose", purpose, "receiptVersion", String.valueOf(version)));
    }

    private ResponseEntity<Map> replace(Long attachmentId, String token, String key, byte[] bytes,
                                         String filename, int attachmentVersion, int mediaVersion, int ownerVersion) {
        return multipart("/api/procurement-attachments/" + attachmentId + "/replacement", token, key,
                bytes, filename, Map.of("attachmentVersion", String.valueOf(attachmentVersion),
                        "mediaVersion", String.valueOf(mediaVersion), "ownerVersion", String.valueOf(ownerVersion)));
    }

    private ResponseEntity<Map> multipart(String path, String token, String key, byte[] bytes,
                                           String filename, Map<String, String> fields) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(filename.endsWith(".jpg") ? MediaType.IMAGE_JPEG : MediaType.IMAGE_PNG);
        body.add("file", new HttpEntity<>(resource, fileHeaders));
        fields.forEach(body::add);
        HttpHeaders headers = headers(token);
        headers.set("Idempotency-Key", key);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), Map.class);
    }

    private ResponseEntity<byte[]> getBytes(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), byte[].class);
    }

    private ResponseEntity<Map> delete(Long attachmentId, String token, Map<String, Object> body) {
        HttpHeaders headers = headers(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/procurement-attachments/" + attachmentId, HttpMethod.DELETE,
                new HttpEntity<>(body, headers), Map.class);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private byte[] imageBytes(int seed) throws Exception {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, new Color(seed % 255, seed * 3 % 255, seed * 7 % 255, 255).getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }

    private byte[] jpegBytes(int seed) throws Exception {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, new Color(seed % 255, seed * 3 % 255, seed * 7 % 255).getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "jpg", output));
        return output.toByteArray();
    }

    private void clearMediaFiles() throws Exception {
        for (String directory : List.of("staging", "original", "display")) {
            Path path = MEDIA_ROOT.resolve(directory);
            Files.createDirectories(path);
            try (var files = Files.list(path)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) Files.delete(file);
            }
        }
    }

    private void assertFileCounts(int expected) throws Exception {
        assertEquals(expected, fileCount(MEDIA_ROOT.resolve("original")));
        assertEquals(expected, fileCount(MEDIA_ROOT.resolve("display")));
        assertEquals(0, fileCount(MEDIA_ROOT.resolve("staging")));
    }

    private long fileCount(Path path) throws Exception {
        try (var files = Files.list(path)) { return files.filter(Files::isRegularFile).count(); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode(), String.valueOf(response.getBody()));
        return (Map<String, Object>) response.getBody().get("data");
    }

    private void assertReason(ResponseEntity<Map> response, HttpStatus status, String reason) {
        assertEquals(status, response.getStatusCode(), String.valueOf(response.getBody()));
        assertEquals(reason, response.getBody().get("reason"));
    }

    private ResponseEntity<Map> login(String memberId, String password) {
        return rest.postForEntity("/usnhub/user/login", Map.of("memberId", memberId, "password", password), Map.class);
    }

    @SuppressWarnings("unchecked")
    private String token(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return ((Map<String, Object>) response.getBody().get("data")).get("token").toString();
    }

    private Long number(Object value) { return ((Number) value).longValue(); }
    private int integer(Object value) { return ((Number) value).intValue(); }
}
