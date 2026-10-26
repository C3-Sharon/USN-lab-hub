package com.usn.labhub.user.collection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.entity.experiment.IdempotencyRow;
import com.usn.labhub.user.mapper.CollectionMapper;
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
public class CollectionSupport {
    private final CollectionMapper mapper;
    private final ObjectMapper objectMapper;

    public CollectionSupport(CollectionMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public <T> Map<String, Object> idempotent(String operation, String key, T request,
                                              Supplier<Map<String, Object>> action) {
        if (!StringUtils.hasText(key) || key.trim().length() > 100) {
            throw CollectionApiException.invalid();
        }
        Long actorId = requiredUserId();
        String normalizedKey = key.trim();
        String digest = digest(request);
        LocalDateTime now = LocalDateTime.now();
        if (mapper.claimIdempotency(actorId, operation, normalizedKey, digest, now) == 0) {
            IdempotencyRow existing = mapper.selectIdempotency(actorId, operation, normalizedKey);
            if (existing == null || !digest.equals(existing.getRequestDigest())) {
                throw CollectionApiException.idempotencyReused();
            }
            if (!"COMPLETED".equals(existing.getStatus())) {
                throw CollectionApiException.invalid();
            }
            Map<String, Object> replay = new LinkedHashMap<>(readMap(existing.getResponseJson()));
            replay.put("idempotentReplay", true);
            return replay;
        }
        Map<String, Object> result = action.get();
        mapper.completeIdempotency(actorId, operation, normalizedKey, writeJson(result), LocalDateTime.now());
        return result;
    }

    public void audit(String requestId, String action, String targetType, Long targetId, Long projectId,
                      String from, String to, String reason) {
        mapper.insertAudit(requestId, requiredUserId(), action, targetType, targetId, projectId,
                from, to, reason, null, LocalDateTime.now());
    }

    public Long requiredUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw CollectionApiException.invalid();
        }
        return userId;
    }

    public boolean systemAdmin() {
        return UserContext.getRoles().contains("SYSTEM_ADMIN");
    }

    public boolean globalReader() {
        return systemAdmin() || UserContext.getRoles().contains("TEACHER");
    }

    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize collection data", exception);
        }
    }

    public <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read collection data", exception);
        }
    }

    public String digestValue(Object value) {
        return digest(value);
    }

    private String digest(Object request) {
        try {
            byte[] source = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot create collection digest", exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        return readJson(value, new TypeReference<>() { });
    }
}
