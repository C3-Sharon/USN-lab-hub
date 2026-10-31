package com.usn.labhub.user.component;

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
                "spring.datasource.url=jdbc:h2:mem:w44-component-media-mutation;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ComponentMediaMutationApiIntegrationTest {
    private static final Path MEDIA_ROOT = Path.of("target", "test-media-b52").toAbsolutePath();

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) {
        registry.add("usnhub.media.storage-root", () -> MEDIA_ROOT.toString());
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private IAttendanceService attendanceService;
    private Long componentId;

    @BeforeEach
    void setUp() throws Exception {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayRecords(List.of());
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);
        jdbc.update("DELETE FROM lab_component_catalog_audit_log");
        jdbc.update("DELETE FROM lab_request_idempotency WHERE operation LIKE '%COMPONENT_MEDIA%'");
        jdbc.update("DELETE FROM lab_media_object");
        jdbc.update("DELETE FROM lab_component_spec_candidate");
        jdbc.update("DELETE FROM lab_component_spec_snapshot");
        jdbc.update("DELETE FROM lab_component");
        jdbc.update("DELETE FROM lab_component_manufacturer");
        jdbc.update("DELETE FROM lab_component_category");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=2 AND role_id IN (3,4)");
        clearMediaFiles();
        componentId = seedComponent();
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadsReplaysAndDeduplicatesWithoutOrphanFiles() throws Exception {
        String admin = token(login("admin", "admin123"));
        byte[] image = imageBytes(1);
        ResponseEntity<Map> first = upload(admin, "upload-1", image, "first.png", 1, 10);
        assertEquals(HttpStatus.OK, first.getStatusCode());
        Map<String, Object> firstData = (Map<String, Object>) first.getBody().get("data");
        Map<String, Object> media = (Map<String, Object>) firstData.get("media");
        Long mediaId = number(media.get("mediaId"));
        assertEquals(mediaId, number(firstData.get("primaryMediaId")));
        assertEquals("PRIMARY", media.get("role"));
        assertEquals(2, integer(firstData.get("componentVersion")));
        assertFileCounts(1);

        Map<String, Object> replay = data(upload(admin, "upload-1", image, "first.png", 1, 10));
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        assertEquals(mediaId, number(((Map<String, Object>) replay.get("media")).get("mediaId")));
        assertFileCounts(1);

        Map<String, Object> duplicate = data(upload(admin, "upload-duplicate", image, "first.png", 2, 20));
        assertEquals(mediaId, number(((Map<String, Object>) duplicate.get("media")).get("mediaId")));
        assertEquals(2, integer(duplicate.get("componentVersion")));
        assertEquals(1, readyCount());
        assertFileCounts(1);

        String member = token(login("20260001", "20260001"));
        ResponseEntity<Map> denied = upload(member, "member-upload", imageBytes(2), "denied.png", 2, 20);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("MEDIA_OPERATION_DENIED", denied.getBody().get("reason"));
        assertFileCounts(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replacesWithNewIdentityButRejectsAmbiguousDuplicates() throws Exception {
        String admin = token(login("admin", "admin123"));
        byte[] firstBytes = imageBytes(10);
        Map<String, Object> first = data(upload(admin, "replace-seed-1", firstBytes, "one.png", 1, 10));
        Long firstId = number(((Map<String, Object>) first.get("media")).get("mediaId"));

        Map<String, Object> same = data(replace(admin, "replace-same", firstId, firstBytes,
                "one.png", 2, 1));
        assertEquals(2, integer(same.get("componentVersion")));
        assertEquals(firstId, number(same.get("primaryMediaId")));
        assertFileCounts(1);

        byte[] secondBytes = imageBytes(20);
        Map<String, Object> second = data(upload(admin, "replace-seed-2", secondBytes, "two.png", 2, 20));
        Long secondId = number(((Map<String, Object>) second.get("media")).get("mediaId"));
        ResponseEntity<Map> duplicate = replace(admin, "replace-other-ready", firstId,
                secondBytes, "two.png", 3, 1);
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        assertEquals("MEDIA_DUPLICATE_READY", duplicate.getBody().get("reason"));
        assertEquals(2, readyCount());
        assertFileCounts(2);

        Map<String, Object> replaced = data(replace(admin, "replace-new", firstId,
                imageBytes(30), "three.png", 3, 1));
        Long newPrimary = number(replaced.get("primaryMediaId"));
        assertNotEquals(firstId, newPrimary);
        assertNotEquals(secondId, newPrimary);
        assertEquals(4, integer(replaced.get("componentVersion")));
        assertEquals(2, readyCount());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_media_object WHERE id=? AND status='DELETED'", Integer.class, firstId));
        assertEquals(10, jdbc.queryForObject(
                "SELECT sort_order FROM lab_media_object WHERE id=?", Integer.class, newPrimary));
        assertFileCounts(3);
    }

    @Test
    void compensatesVersionAndLimitFailures() throws Exception {
        String admin = token(login("admin", "admin123"));
        data(upload(admin, "limit-1", imageBytes(101), "1.png", 1, 10));

        ResponseEntity<Map> stale = upload(admin, "stale", imageBytes(102), "stale.png", 1, 20);
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());
        assertEquals("VERSION_CONFLICT", stale.getBody().get("reason"));
        assertFileCounts(1);

        for (int index = 2; index <= 5; index++) {
            data(upload(admin, "limit-" + index, imageBytes(100 + index), index + ".png", index, index * 10));
        }
        assertEquals(5, readyCount());
        assertFileCounts(5);

        ResponseEntity<Map> sixth = upload(admin, "limit-6", imageBytes(106), "6.png", 6, 60);
        assertEquals(HttpStatus.CONFLICT, sixth.getStatusCode());
        assertEquals("MEDIA_LIMIT_EXCEEDED", sixth.getBody().get("reason"));
        assertEquals(5, readyCount());
        assertFileCounts(5);

        ResponseEntity<Map> invalid = upload(admin, "invalid-type", "alert(1)".getBytes(), "bad.png", 6, 60);
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        assertEquals("MEDIA_TYPE_UNSUPPORTED", invalid.getBody().get("reason"));
        assertFileCounts(5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void switchesPrimaryAndAppliesCompleteOrderingSnapshot() throws Exception {
        String admin = token(login("admin", "admin123"));
        Long first = number(((Map<String, Object>) data(upload(admin, "order-1", imageBytes(201),
                "1.png", 1, 10)).get("media")).get("mediaId"));
        Long second = number(((Map<String, Object>) data(upload(admin, "order-2", imageBytes(202),
                "2.png", 2, 20)).get("media")).get("mediaId"));
        Long third = number(((Map<String, Object>) data(upload(admin, "order-3", imageBytes(203),
                "3.png", 3, 30)).get("media")).get("mediaId"));

        Map<String, Object> primary = data(putJson("/api/components/" + componentId + "/images/"
                + second + "/primary", admin, Map.of("componentVersion", 4, "mediaVersion", 1)));
        assertEquals(second, number(primary.get("primaryMediaId")));
        assertEquals(5, integer(primary.get("componentVersion")));
        List<Map<String, Object>> primaryImages = (List<Map<String, Object>>) primary.get("images");
        Map<Long, Map<String, Object>> byId = index(primaryImages);
        assertEquals("GALLERY", byId.get(first).get("role"));
        assertEquals(2, integer(byId.get(first).get("version")));
        assertEquals("PRIMARY", byId.get(second).get("role"));
        assertEquals(2, integer(byId.get(second).get("version")));

        Map<String, Object> orderBody = Map.of(
                "componentVersion", 5,
                "items", List.of(
                        Map.of("mediaId", first, "mediaVersion", 2, "sortOrder", 30),
                        Map.of("mediaId", second, "mediaVersion", 2, "sortOrder", 10),
                        Map.of("mediaId", third, "mediaVersion", 1, "sortOrder", 20)));
        Map<String, Object> ordered = data(putJson(
                "/api/components/" + componentId + "/images/order", admin, orderBody));
        assertEquals(6, integer(ordered.get("componentVersion")));
        List<Map<String, Object>> orderedImages = (List<Map<String, Object>>) ordered.get("images");
        assertEquals(List.of(second, third, first), orderedImages.stream()
                .map(item -> number(item.get("mediaId"))).toList());
        assertEquals(List.of(3, 2, 3), orderedImages.stream()
                .map(item -> integer(item.get("version"))).toList());

        ResponseEntity<Map> incomplete = putJson("/api/components/" + componentId + "/images/order", admin,
                Map.of("componentVersion", 6, "items", List.of(
                        Map.of("mediaId", second, "mediaVersion", 3, "sortOrder", 1))));
        assertEquals(HttpStatus.BAD_REQUEST, incomplete.getStatusCode());
        assertEquals("INVALID_PARAMETER", incomplete.getBody().get("reason"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletesLogicallyAndPromotesNextReadyImage() throws Exception {
        String admin = token(login("admin", "admin123"));
        Long first = number(((Map<String, Object>) data(upload(admin, "delete-1", imageBytes(211),
                "1.png", 1, 20)).get("media")).get("mediaId"));
        Long second = number(((Map<String, Object>) data(upload(admin, "delete-2", imageBytes(212),
                "2.png", 2, 10)).get("media")).get("mediaId"));

        Map<String, Object> firstDelete = data(deleteJson("/api/components/" + componentId + "/images/"
                + first, admin, Map.of("componentVersion", 3, "mediaVersion", 1, "reason", "更换主图")));
        assertEquals(4, integer(firstDelete.get("componentVersion")));
        assertEquals(second, number(firstDelete.get("primaryMediaId")));
        List<Map<String, Object>> remaining = (List<Map<String, Object>>) firstDelete.get("images");
        assertEquals(1, remaining.size());
        assertEquals("PRIMARY", remaining.get(0).get("role"));
        assertEquals(2, integer(remaining.get(0).get("version")));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_media_object WHERE id=? AND status='DELETED'", Integer.class, first));
        assertFileCounts(2);

        Map<String, Object> secondDelete = data(deleteJson("/api/components/" + componentId + "/images/"
                + second, admin, Map.of("componentVersion", 4, "mediaVersion", 2, "reason", "清空图库")));
        assertEquals(5, integer(secondDelete.get("componentVersion")));
        assertEquals("PLACEHOLDER", secondDelete.get("imageState"));
        assertEquals(null, secondDelete.get("primaryMediaId"));
        assertEquals(List.of(), secondDelete.get("images"));
        assertEquals(0, readyCount());
        assertFileCounts(2);

        String member = token(login("20260001", "20260001"));
        ResponseEntity<Map> denied = deleteJson("/api/components/" + componentId + "/images/" + first,
                member, Map.of("componentVersion", 5, "mediaVersion", 2, "reason", "越权"));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("MEDIA_OPERATION_DENIED", denied.getBody().get("reason"));
    }

    private Long seedComponent() {
        jdbc.update("""
                INSERT INTO lab_component_category(code,name,normalized_name,parent_scope_id,level,path,
                    status,sort_order,version,created_by,updated_by)
                VALUES ('MEDIA_MUT','媒体写测试','媒体写测试',0,1,'/1/','ACTIVE',0,1,1,1)
                """);
        Long categoryId = jdbc.queryForObject("SELECT id FROM lab_component_category WHERE code='MEDIA_MUT'", Long.class);
        jdbc.update("UPDATE lab_component_category SET path=? WHERE id=?", "/" + categoryId + "/", categoryId);
        jdbc.update("""
                INSERT INTO lab_component_manufacturer(code,name,normalized_name,status,version,created_by,updated_by)
                VALUES ('MEDIA_MUT_MFR','Media Mutation','media mutation','ACTIVE',1,1,1)
                """);
        Long manufacturerId = jdbc.queryForObject(
                "SELECT id FROM lab_component_manufacturer WHERE code='MEDIA_MUT_MFR'", Long.class);
        jdbc.update("""
                INSERT INTO lab_component(catalog_code,name,manufacturer_id,mpn,normalized_mpn,category_id,
                    package_type,base_unit,status,spec_version,version,created_by,updated_by)
                VALUES ('CMP-MEDIA-M','媒体写型号',?,'MEDIA-MUT-1','MEDIA-MUT-1',?,'SOT-23','PCS','DRAFT',0,1,1,1)
                """, manufacturerId, categoryId);
        return jdbc.queryForObject("SELECT id FROM lab_component WHERE normalized_mpn='MEDIA-MUT-1'", Long.class);
    }

    private ResponseEntity<Map> upload(String token, String key, byte[] bytes, String filename,
                                       int componentVersion, int sortOrder) {
        return multipart("/api/components/" + componentId + "/images", token, key, bytes, filename,
                Map.of("componentVersion", componentVersion, "sortOrder", sortOrder));
    }

    private ResponseEntity<Map> replace(String token, String key, Long mediaId, byte[] bytes, String filename,
                                        int componentVersion, int mediaVersion) {
        return multipart("/api/components/" + componentId + "/images/" + mediaId + "/replacement",
                token, key, bytes, filename,
                Map.of("componentVersion", componentVersion, "mediaVersion", mediaVersion));
    }

    private ResponseEntity<Map> multipart(String path, String token, String key, byte[] bytes, String filename,
                                          Map<String, Integer> fields) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.IMAGE_PNG);
        body.add("file", new HttpEntity<>(resource, fileHeaders));
        fields.forEach((name, value) -> body.add(name, value.toString()));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Idempotency-Key", key);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> putJson(String path, String token, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.PUT, jsonEntity(token, body), Map.class);
    }

    private ResponseEntity<Map> deleteJson(String path, String token, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.DELETE, jsonEntity(token, body), Map.class);
    }

    private HttpEntity<Map<String, Object>> jsonEntity(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private Map<Long, Map<String, Object>> index(List<Map<String, Object>> images) {
        Map<Long, Map<String, Object>> indexed = new java.util.HashMap<>();
        for (Map<String, Object> image : images) indexed.put(number(image.get("mediaId")), image);
        return indexed;
    }

    private byte[] imageBytes(int seed) throws Exception {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, new Color(seed % 255, (seed * 3) % 255, (seed * 7) % 255, 255).getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
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

    private int readyCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM lab_media_object WHERE status='READY'", Integer.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return (Map<String, Object>) response.getBody().get("data");
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
