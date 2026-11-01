package com.usn.labhub.user.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.entity.experiment.IdempotencyRow;
import com.usn.labhub.user.mapper.ComponentCatalogMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class ComponentCatalogSupport {
    private final ComponentCatalogMapper mapper;
    private final ObjectMapper objectMapper;

    public ComponentCatalogSupport(ComponentCatalogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> idempotent(String operation, String key, Object request,
                                          Supplier<Map<String, Object>> action) {
        if (!StringUtils.hasText(key) || key.trim().length() > 100) {
            throw ComponentCatalogApiException.invalid();
        }
        Long actorId = requiredUserId();
        String normalizedKey = key.trim();
        String digest = digest(request);
        LocalDateTime now = LocalDateTime.now();
        if (mapper.claimIdempotency(actorId, operation, normalizedKey, digest, now) == 0) {
            IdempotencyRow existing = mapper.selectIdempotency(actorId, operation, normalizedKey);
            if (existing == null || !digest.equals(existing.getRequestDigest())) {
                throw ComponentCatalogApiException.idempotencyReused();
            }
            if (!"COMPLETED".equals(existing.getStatus())) {
                throw ComponentCatalogApiException.invalid();
            }
            Map<String, Object> replay = new LinkedHashMap<>(readMap(existing.getResponseJson()));
            replay.put("idempotentReplay", true);
            return replay;
        }
        Map<String, Object> result = action.get();
        mapper.completeIdempotency(actorId, operation, normalizedKey,
                writeJson(result), LocalDateTime.now());
        return result;
    }

    public void audit(String requestId, String action, String targetType, Long targetId,
                      String previousStatus, String newStatus, String reason, Object details) {
        mapper.insertAudit(requestId, requiredUserId(), action, targetType, targetId,
                previousStatus, newStatus, reason,
                details == null ? null : writeJson(details), LocalDateTime.now());
    }

    public Long requiredUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw ComponentCatalogApiException.invalid();
        }
        return userId;
    }

    public Map<String, Object> asMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<>() { });
    }

    private String digest(Object request) {
        try {
            byte[] source = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot create component catalog digest", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize component catalog data", exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read component catalog data", exception);
        }
    }
}
