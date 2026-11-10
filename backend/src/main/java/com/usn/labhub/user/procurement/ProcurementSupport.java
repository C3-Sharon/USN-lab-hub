package com.usn.labhub.user.procurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.domain.entity.experiment.IdempotencyRow;
import com.usn.labhub.user.mapper.ProcurementMapper;
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
public class ProcurementSupport {
    private final ProcurementMapper mapper;
    private final ProcurementAccessPolicy access;
    private final ObjectMapper objectMapper;

    public ProcurementSupport(ProcurementMapper mapper, ProcurementAccessPolicy access, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.access = access;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> idempotent(String operation, String key, Object request,
                                          Supplier<Map<String, Object>> action) {
        if (!StringUtils.hasText(key) || key.trim().length() < 8 || key.trim().length() > 128) {
            throw ProcurementApiException.invalid();
        }
        Long actorId = access.userId();
        String normalizedKey = key.trim();
        String digest = digest(request);
        LocalDateTime now = LocalDateTime.now();
        if (mapper.claimIdempotency(actorId, operation, normalizedKey, digest, now) == 0) {
            IdempotencyRow existing = mapper.selectIdempotency(actorId, operation, normalizedKey);
            if (existing == null || !digest.equals(existing.getRequestDigest())) {
                throw ProcurementApiException.idempotencyReused();
            }
            if (!"COMPLETED".equals(existing.getStatus())) {
                throw ProcurementApiException.invalid();
            }
            Map<String, Object> replay = new LinkedHashMap<>(readMap(existing.getResponseJson()));
            replay.put("idempotentReplay", true);
            return replay;
        }
        Map<String, Object> result = action.get();
        mapper.completeIdempotency(actorId, operation, normalizedKey, writeJson(result), LocalDateTime.now());
        return result;
    }

    public Map<String, Object> asMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<>() { });
    }

    public void audit(String requestId, String action, Long targetId, Object details) {
        audit(requestId, action, targetId, "DRAFT", "DRAFT", null, details);
    }

    public void audit(String requestId, String action, Long targetId, String previousStatus,
                      String newStatus, String reason, Object details) {
        audit(requestId, action, "PROCUREMENT_ORDER", targetId, previousStatus, newStatus, reason, details);
    }

    public void audit(String requestId, String action, String targetType, Long targetId,
                      String previousStatus, String newStatus, String reason, Object details) {
        mapper.insertAudit(requestId, access.userId(), action, targetType, targetId,
                previousStatus, newStatus, reason,
                details == null ? null : writeJson(details), LocalDateTime.now());
    }

    public String json(Object value) {
        return writeJson(value);
    }

    private String digest(Object value) {
        try {
            byte[] source = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot create procurement digest", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize procurement data", exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read procurement data", exception);
        }
    }
}
