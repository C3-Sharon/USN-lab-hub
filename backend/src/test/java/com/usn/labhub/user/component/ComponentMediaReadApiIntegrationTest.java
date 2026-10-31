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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:w44-component-media-read;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false"
        }
)
class ComponentMediaReadApiIntegrationTest {

    private static final Path MEDIA_ROOT = Path.of("target", "test-media-b51").toAbsolutePath();
    private static final byte[] DISPLAY_BYTES = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4};

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) {
        registry.add("usnhub.media.storage-root", () -> MEDIA_ROOT.toString());
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private IAttendanceService attendanceService;

    private Long activeComponentId;
    private Long draftComponentId;
    private Long activeMediaId;
    private Long draftMediaId;
    private Long deletedMediaId;

    @BeforeEach
    void setUp() throws Exception {
        LoginVO.AttendanceInfo attendance = new LoginVO.AttendanceInfo();
        attendance.setTodayRecords(List.of());
        when(attendanceService.getOverview(anyLong())).thenReturn(attendance);

        jdbc.update("DELETE FROM lab_media_object");
        jdbc.update("DELETE FROM lab_component_spec_candidate");
        jdbc.update("DELETE FROM lab_component_spec_snapshot");
        jdbc.update("DELETE FROM lab_component");
        jdbc.update("DELETE FROM lab_component_manufacturer");
        jdbc.update("DELETE FROM lab_component_category");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=2 AND role_id IN (3,4)");
        clearMediaFiles();
        seedMediaFixtures();
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsSafeMetadataListAndDisplayContentWithoutStorageKeys() {
        String admin = token(login("admin", "admin123"));

        ResponseEntity<Map> listResponse = getMap("/api/components/" + activeComponentId + "/images", admin);
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        List<Map<String, Object>> images = (List<Map<String, Object>>) listResponse.getBody().get("data");
        assertEquals(1, images.size());
        Map<String, Object> image = images.get(0);
        assertEquals(activeMediaId.longValue(), ((Number) image.get("mediaId")).longValue());
        assertEquals("/api/media/" + activeMediaId + "/content", image.get("contentPath"));
        assertFalse(image.containsKey("originalObjectKey"));
        assertFalse(image.containsKey("displayObjectKey"));
        assertFalse(image.containsKey("absolutePath"));

        ResponseEntity<Map> metadata = getMap("/api/media/" + activeMediaId, admin);
        assertEquals(HttpStatus.OK, metadata.getStatusCode());
        assertFalse(metadata.getBody().toString().contains("display/"));

        ResponseEntity<byte[]> content = rest.exchange("/api/media/" + activeMediaId + "/content",
                HttpMethod.GET, authorized(admin), byte[].class);
        assertEquals(HttpStatus.OK, content.getStatusCode());
        assertEquals("image/png", content.getHeaders().getContentType().toString());
        assertArrayEquals(DISPLAY_BYTES, content.getBody());
    }

    @Test
    void enforcesOwnerVisibilityAndHidesDeletedMedia() {
        String member = token(login("20260001", "20260001"));
        assertEquals(HttpStatus.OK,
                getMap("/api/components/" + activeComponentId + "/images", member).getStatusCode());
        ResponseEntity<Map> hiddenDraft = getMap("/api/media/" + draftMediaId, member);
        assertEquals(HttpStatus.NOT_FOUND, hiddenDraft.getStatusCode());
        assertEquals("MEDIA_NOT_FOUND", hiddenDraft.getBody().get("reason"));

        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (2,3)");
        try {
            assertEquals(HttpStatus.OK, getMap("/api/media/" + draftMediaId, member).getStatusCode());
            assertEquals(HttpStatus.OK,
                    getMap("/api/components/" + draftComponentId + "/images", member).getStatusCode());
        } finally {
            jdbc.update("DELETE FROM sys_user_role WHERE user_id=2 AND role_id=3");
        }

        ResponseEntity<Map> deleted = getMap("/api/media/" + deletedMediaId, token(login("admin", "admin123")));
        assertEquals(HttpStatus.NOT_FOUND, deleted.getStatusCode());
        assertEquals("MEDIA_NOT_FOUND", deleted.getBody().get("reason"));

        ResponseEntity<Map> guest = rest.getForEntity("/api/media/" + activeMediaId, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, guest.getStatusCode());
    }

    private void seedMediaFixtures() throws Exception {
        jdbc.update("""
                INSERT INTO lab_component_category(code,name,normalized_name,parent_scope_id,level,path,
                    status,sort_order,version,created_by,updated_by)
                VALUES ('MEDIA_TEST','媒体测试','媒体测试',0,1,'/1/','ACTIVE',0,1,1,1)
                """);
        Long categoryId = jdbc.queryForObject("SELECT id FROM lab_component_category WHERE code='MEDIA_TEST'", Long.class);
        jdbc.update("UPDATE lab_component_category SET path=? WHERE id=?", "/" + categoryId + "/", categoryId);
        jdbc.update("""
                INSERT INTO lab_component_manufacturer(code,name,normalized_name,status,version,created_by,updated_by)
                VALUES ('MEDIA_MFR','Media Mfr','media mfr','ACTIVE',1,1,1)
                """);
        Long manufacturerId = jdbc.queryForObject(
                "SELECT id FROM lab_component_manufacturer WHERE code='MEDIA_MFR'", Long.class);
        activeComponentId = insertComponent(categoryId, manufacturerId, "MEDIA-ACTIVE", "ACTIVE");
        draftComponentId = insertComponent(categoryId, manufacturerId, "MEDIA-DRAFT", "DRAFT");
        activeMediaId = insertMedia(activeComponentId, "READY", "PRIMARY", 10);
        draftMediaId = insertMedia(draftComponentId, "READY", "PRIMARY", 10);
        deletedMediaId = insertMedia(activeComponentId, "DELETED", "GALLERY", 20);
        jdbc.update("UPDATE lab_component SET primary_media_id=? WHERE id=?", activeMediaId, activeComponentId);
        jdbc.update("UPDATE lab_component SET primary_media_id=? WHERE id=?", draftMediaId, draftComponentId);
    }

    private Long insertComponent(Long categoryId, Long manufacturerId, String mpn, String status) {
        jdbc.update("""
                INSERT INTO lab_component(catalog_code,name,manufacturer_id,mpn,normalized_mpn,category_id,
                    package_type,base_unit,status,spec_version,version,created_by,updated_by)
                VALUES (?, '媒体测试型号', ?, ?, ?, ?, 'SOT-23', 'PCS', ?, 0, 1, 1, 1)
                """, "CMP-" + mpn.substring(mpn.length() - 6), manufacturerId, mpn, mpn, categoryId, status);
        return jdbc.queryForObject("SELECT id FROM lab_component WHERE normalized_mpn=?", Long.class, mpn);
    }

    private Long insertMedia(Long componentId, String status, String role, int sortOrder) throws Exception {
        String suffix = UUID.randomUUID().toString();
        String originalKey = "original/" + suffix + ".png";
        String displayKey = "display/" + UUID.randomUUID() + ".png";
        Integer dedupSlot = "READY".equals(status) ? 1 : null;
        Integer primarySlot = "READY".equals(status) && "PRIMARY".equals(role) ? 1 : null;
        jdbc.update("""
                INSERT INTO lab_media_object(owner_type,owner_id,role,status,original_file_name,content_type,
                    size_bytes,width,height,sha256,original_object_key,display_object_key,sort_order,
                    ready_dedup_slot,ready_primary_slot,version,uploaded_by,deleted_by,deleted_at)
                VALUES ('COMPONENT',?,?,?,?, 'image/png',8,2,2,?,?,?,?,?,?,1,1,?,?)
                """, componentId, role, status, "board.png", suffix.replace("-", "") + "0".repeat(32),
                originalKey, displayKey, sortOrder, dedupSlot, primarySlot,
                "DELETED".equals(status) ? 1L : null,
                "DELETED".equals(status) ? java.time.LocalDateTime.now() : null);
        Long id = jdbc.queryForObject("SELECT id FROM lab_media_object WHERE original_object_key=?", Long.class, originalKey);
        if ("READY".equals(status)) {
            Path display = MEDIA_ROOT.resolve(displayKey);
            Files.createDirectories(display.getParent());
            Files.write(display, DISPLAY_BYTES);
        }
        return id;
    }

    private void clearMediaFiles() throws Exception {
        for (String directory : List.of("staging", "original", "display")) {
            Path path = MEDIA_ROOT.resolve(directory);
            Files.createDirectories(path);
            try (var files = Files.list(path)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    private ResponseEntity<Map> login(String memberId, String password) {
        return rest.postForEntity("/usnhub/user/login",
                Map.of("memberId", memberId, "password", password), Map.class);
    }

    @SuppressWarnings("unchecked")
    private String token(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return ((Map<String, Object>) response.getBody().get("data")).get("token").toString();
    }

    private ResponseEntity<Map> getMap(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, authorized(token), Map.class);
    }

    private HttpEntity<Void> authorized(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
