package com.usn.labhub.user.learning.template;

import java.util.List;

public record TemplateValidationResult(boolean valid, List<TemplateValidationError> errors) {

    public TemplateValidationResult {
        errors = List.copyOf(errors);
        valid = errors.isEmpty();
    }

    public static TemplateValidationResult of(List<TemplateValidationError> errors) {
        return new TemplateValidationResult(errors.isEmpty(), errors);
    }
}
