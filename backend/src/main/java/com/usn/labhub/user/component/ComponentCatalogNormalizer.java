package com.usn.labhub.user.component;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class ComponentCatalogNormalizer {
    public String display(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).replaceAll("\\s+", " ");
    }

    public String code(String value) {
        String normalized = display(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    public String normalizedName(String value) {
        return code(value);
    }

    public String normalizedMpn(String value) {
        return code(value);
    }

    public String optional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public String placeholderKey(String topCategoryCode, String packageType, String normalizedMpn) {
        String normalizedPackage = code(packageType);
        StringBuilder bucket = new StringBuilder();
        if (normalizedPackage != null) {
            for (int i = 0; i < normalizedPackage.length(); i++) {
                char value = normalizedPackage.charAt(i);
                if (!asciiAlphaNumeric(value)) {
                    break;
                }
                bucket.append(value);
            }
        }
        char first = 'X';
        if (normalizedMpn != null) {
            for (int i = 0; i < normalizedMpn.length(); i++) {
                char value = normalizedMpn.charAt(i);
                if (asciiAlphaNumeric(value)) {
                    first = value;
                    break;
                }
            }
        }
        return "component:" + topCategoryCode + ":"
                + (bucket.isEmpty() ? "OTHER" : bucket) + ":" + first;
    }

    private boolean asciiAlphaNumeric(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= '0' && value <= '9');
    }
}
