package com.usn.labhub.user.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.domain.dto.component.RawSpecificationDTO;
import com.usn.labhub.user.domain.dto.component.SpecificationDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ComponentSpecificationCodec {
    private final ObjectMapper objectMapper;

    public ComponentSpecificationCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String rawJson(List<RawSpecificationDTO> values) {
        List<Map<String, Object>> canonical = new ArrayList<>();
        for (RawSpecificationDTO value : values) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", value.getName().trim());
            item.put("value", value.getValue().trim());
            item.put("unit", optional(value.getUnit()));
            canonical.add(item);
        }
        return write(canonical);
    }

    public String specificationJson(List<SpecificationDTO> values) {
        Set<String> codes = new HashSet<>();
        List<Map<String, Object>> canonical = new ArrayList<>();
        for (SpecificationDTO value : values) {
            if (!codes.add(value.getSpecCode())) {
                throw ComponentCatalogApiException.specInvalid();
            }
            validateValue(value);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("specCode", value.getSpecCode());
            item.put("label", value.getLabel().trim());
            item.put("valueType", value.getValueType());
            item.put("rawValue", optional(value.getRawValue()));
            item.put("normalizedValue", normalizedValue(value));
            item.put("unit", optional(value.getUnit()));
            item.put("sortOrder", value.getSortOrder());
            canonical.add(item);
        }
        return write(canonical);
    }

    public String checksum(String specificationsJson) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(specificationsJson.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public List<Map<String, Object>> readList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read component specification JSON", exception);
        }
    }

    private void validateValue(SpecificationDTO value) {
        Object normalized = value.getNormalizedValue();
        boolean valid = switch (value.getValueType()) {
            case "STRING" -> normalized instanceof String text && !text.isBlank();
            case "DECIMAL" -> normalized instanceof Number;
            case "INTEGER" -> normalized instanceof Number
                    && new BigDecimal(normalized.toString()).stripTrailingZeros().scale() <= 0;
            case "BOOLEAN" -> normalized instanceof Boolean;
            default -> false;
        };
        if (!valid) {
            throw ComponentCatalogApiException.specInvalid();
        }
    }

    private Object normalizedValue(SpecificationDTO value) {
        if ("DECIMAL".equals(value.getValueType())) {
            return new BigDecimal(value.getNormalizedValue().toString()).stripTrailingZeros();
        }
        return value.getNormalizedValue() instanceof String text ? text.trim() : value.getNormalizedValue();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot write component specification JSON", exception);
        }
    }
}
