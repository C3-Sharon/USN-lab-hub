package com.usn.labhub.user.learning.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ExperimentTemplateSchemaValidator {

    private static final int MAX_FIELDS = 50;
    private static final Pattern FIELD_KEY = Pattern.compile("[a-z][a-z0-9_]{0,49}");
    private static final Set<String> ROOT_KEYS = Set.of("schemaVersion", "type", "properties", "required");
    private static final Set<String> PROPERTY_KEYS = Set.of(
            "type", "title", "description", "unit", "default", "displayOrder", "widget",
            "enum", "minimum", "maximum", "minLength", "maxLength"
    );
    private static final Set<String> TYPES = Set.of("string", "number", "integer", "boolean");
    private static final Set<String> WIDGETS = Set.of("input", "input-number", "select", "switch", "textarea");

    private final ObjectMapper objectMapper;

    public ExperimentTemplateSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TemplateValidationResult validateSchema(String schemaJson) {
        JsonNode schema;
        try {
            schema = objectMapper.readTree(schemaJson);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return invalidRoot("Schema 不是合法 JSON");
        }
        return validateSchema(schema);
    }

    public TemplateValidationResult validateSchema(JsonNode schema) {
        List<TemplateValidationError> errors = new ArrayList<>();
        if (schema == null || !schema.isObject()) {
            add(errors, "$", "Schema 根节点必须是对象");
            return TemplateValidationResult.of(errors);
        }

        rejectUnknownKeys(schema, ROOT_KEYS, "", errors);
        requireText(schema, "schemaVersion", "1.0", errors);
        requireText(schema, "type", "object", errors);

        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject()) {
            add(errors, "properties", "properties 必须是对象");
            return TemplateValidationResult.of(errors);
        }
        if (properties.size() > MAX_FIELDS) {
            add(errors, "properties", "单个模板版本最多支持 50 个字段");
        }

        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            validateProperty(field.getKey(), field.getValue(), errors);
        }
        validateRequired(schema.get("required"), properties, errors);
        return TemplateValidationResult.of(errors);
    }

    public TemplateValidationResult validateData(String schemaJson, String dataJson) {
        JsonNode schema;
        JsonNode data;
        try {
            schema = objectMapper.readTree(schemaJson);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return invalidRoot("Schema 不是合法 JSON");
        }
        TemplateValidationResult schemaResult = validateSchema(schema);
        if (!schemaResult.valid()) {
            return schemaResult;
        }
        try {
            data = objectMapper.readTree(dataJson);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return invalidRoot("实验数据不是合法 JSON");
        }
        return validateData(schema, data);
    }

    public TemplateValidationResult validateData(JsonNode schema, JsonNode data) {
        TemplateValidationResult schemaResult = validateSchema(schema);
        if (!schemaResult.valid()) {
            return schemaResult;
        }

        List<TemplateValidationError> errors = new ArrayList<>();
        if (data == null || !data.isObject()) {
            add(errors, "$", "实验数据根节点必须是对象");
            return TemplateValidationResult.of(errors);
        }

        JsonNode properties = schema.get("properties");
        Set<String> required = requiredFields(schema.get("required"));
        for (String field : required) {
            if (!data.has(field) || data.get(field).isNull()) {
                add(errors, field, field + " 是必填字段");
            }
        }

        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = data.get(field.getKey());
            if (value == null || value.isNull()) {
                continue;
            }
            validateValue(field.getKey(), value, field.getValue(), errors);
        }
        return TemplateValidationResult.of(errors);
    }

    private void validateProperty(String key, JsonNode property, List<TemplateValidationError> errors) {
        String path = "properties." + key;
        if (!FIELD_KEY.matcher(key).matches()) {
            add(errors, path, "字段 key 只能由小写字母、数字和下划线组成，并以小写字母开头");
        }
        if (!property.isObject()) {
            add(errors, path, "字段定义必须是对象");
            return;
        }

        rejectUnknownKeys(property, PROPERTY_KEYS, path, errors);
        String type = textValue(property, "type");
        if (type == null || !TYPES.contains(type)) {
            add(errors, path + ".type", "不支持的类型：" + (type == null ? "null" : type));
            return;
        }

        validateTextLength(property.get("title"), path + ".title", 2, 40, true, errors);
        validateTextLength(property.get("description"), path + ".description", 0, 200, false, errors);
        validateTextLength(property.get("unit"), path + ".unit", 0, 20, false, errors);
        validateDisplayOrder(property.get("displayOrder"), path + ".displayOrder", errors);
        validateWidget(property, type, path, errors);
        validateTypeConstraints(property, type, path, errors);

        JsonNode defaultValue = property.get("default");
        if (defaultValue != null) {
            validateValue(path + ".default", defaultValue, property, errors);
        }
    }

    private void validateTypeConstraints(JsonNode property,
                                         String type,
                                         String path,
                                         List<TemplateValidationError> errors) {
        Set<String> typeConstraints = switch (type) {
            case "string" -> Set.of("enum", "minLength", "maxLength");
            case "number", "integer" -> Set.of("enum", "minimum", "maximum");
            default -> Set.of();
        };
        for (String constraint : List.of("enum", "minimum", "maximum", "minLength", "maxLength")) {
            if (property.has(constraint) && !typeConstraints.contains(constraint)) {
                add(errors, path + "." + constraint, type + " 类型不支持 " + constraint);
            }
        }

        if ("string".equals(type)) {
            Integer min = nonNegativeInteger(property.get("minLength"), path + ".minLength", errors);
            Integer max = nonNegativeInteger(property.get("maxLength"), path + ".maxLength", errors);
            if (min != null && max != null && min > max) {
                add(errors, path + ".minLength", "minLength 不能大于 maxLength");
            }
        } else if ("number".equals(type) || "integer".equals(type)) {
            BigDecimal min = decimal(property.get("minimum"), path + ".minimum", errors);
            BigDecimal max = decimal(property.get("maximum"), path + ".maximum", errors);
            if (min != null && max != null && min.compareTo(max) > 0) {
                add(errors, path + ".minimum", "minimum 不能大于 maximum");
            }
        }

        JsonNode enumNode = property.get("enum");
        if (enumNode != null && typeConstraints.contains("enum")) {
            validateEnum(enumNode, type, path + ".enum", errors);
        }
    }

    private void validateEnum(JsonNode enumNode,
                              String type,
                              String path,
                              List<TemplateValidationError> errors) {
        if (!enumNode.isArray() || enumNode.isEmpty()) {
            add(errors, path, "enum 必须是非空数组");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < enumNode.size(); index++) {
            JsonNode item = enumNode.get(index);
            if (!matchesType(item, type)) {
                add(errors, path + "[" + index + "]", "枚举值类型必须与字段类型一致");
            }
            if (!seen.add(item.toString())) {
                add(errors, path + "[" + index + "]", "enum 不能包含重复值");
            }
        }
    }

    private void validateRequired(JsonNode required,
                                  JsonNode properties,
                                  List<TemplateValidationError> errors) {
        if (required == null) {
            return;
        }
        if (!required.isArray()) {
            add(errors, "required", "required 必须是字段 key 数组");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < required.size(); index++) {
            JsonNode item = required.get(index);
            String path = "required[" + index + "]";
            if (!item.isTextual()) {
                add(errors, path, "required 元素必须是字符串");
                continue;
            }
            String field = item.textValue();
            if (!properties.has(field)) {
                add(errors, path, "required 引用了不存在的字段：" + field);
            }
            if (!seen.add(field)) {
                add(errors, path, "required 不能包含重复字段：" + field);
            }
        }
    }

    private void validateValue(String path,
                               JsonNode value,
                               JsonNode property,
                               List<TemplateValidationError> errors) {
        String type = property.path("type").asText();
        if (!matchesType(value, type)) {
            add(errors, path, "值类型必须是 " + type);
            return;
        }

        if ("string".equals(type)) {
            int length = value.textValue().length();
            if (property.path("minLength").isIntegralNumber()
                    && length < property.get("minLength").intValue()) {
                add(errors, path, "字符串长度不能小于 " + property.get("minLength").intValue());
            }
            if (property.path("maxLength").isIntegralNumber()
                    && length > property.get("maxLength").intValue()) {
                add(errors, path, "字符串长度不能大于 " + property.get("maxLength").intValue());
            }
        } else if ("number".equals(type) || "integer".equals(type)) {
            BigDecimal valueDecimal = value.decimalValue();
            if (property.path("minimum").isNumber()
                    && valueDecimal.compareTo(property.get("minimum").decimalValue()) < 0) {
                add(errors, path, "数值不能小于 " + property.get("minimum").asText());
            }
            if (property.path("maximum").isNumber()
                    && valueDecimal.compareTo(property.get("maximum").decimalValue()) > 0) {
                add(errors, path, "数值不能大于 " + property.get("maximum").asText());
            }
        }

        JsonNode enumNode = property.get("enum");
        if (enumNode != null && enumNode.isArray() && !containsEnumValue(enumNode, value, type)) {
            add(errors, path, "值不在允许的 enum 范围内");
        }
    }

    private boolean containsEnumValue(JsonNode enumNode, JsonNode value, String type) {
        for (JsonNode item : enumNode) {
            if (("number".equals(type) || "integer".equals(type)) && item.isNumber() && value.isNumber()) {
                if (item.decimalValue().compareTo(value.decimalValue()) == 0) {
                    return true;
                }
            } else if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private void validateWidget(JsonNode property,
                                String type,
                                String path,
                                List<TemplateValidationError> errors) {
        JsonNode widgetNode = property.get("widget");
        if (widgetNode == null) {
            return;
        }
        if (!widgetNode.isTextual() || !WIDGETS.contains(widgetNode.textValue())) {
            add(errors, path + ".widget", "不支持的 widget");
            return;
        }
        String widget = widgetNode.textValue();
        boolean compatible = switch (widget) {
            case "input", "textarea" -> "string".equals(type);
            case "input-number" -> "number".equals(type) || "integer".equals(type);
            case "select" -> "string".equals(type) && property.has("enum");
            case "switch" -> "boolean".equals(type);
            default -> false;
        };
        if (!compatible) {
            add(errors, path + ".widget", "widget 与字段类型或约束不兼容");
        }
    }

    private void validateDisplayOrder(JsonNode value,
                                      String path,
                                      List<TemplateValidationError> errors) {
        if (value == null || !value.isIntegralNumber()) {
            add(errors, path, "displayOrder 必须是整数");
        }
    }

    private void validateTextLength(JsonNode value,
                                    String path,
                                    int min,
                                    int max,
                                    boolean required,
                                    List<TemplateValidationError> errors) {
        if (value == null) {
            if (required) {
                add(errors, path, "字段必填");
            }
            return;
        }
        if (!value.isTextual()) {
            add(errors, path, "必须是字符串");
            return;
        }
        int length = value.textValue().length();
        if (length < min || length > max) {
            add(errors, path, "长度必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private Integer nonNegativeInteger(JsonNode value,
                                       String path,
                                       List<TemplateValidationError> errors) {
        if (value == null) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            add(errors, path, "必须是非负整数");
            return null;
        }
        return value.intValue();
    }

    private BigDecimal decimal(JsonNode value,
                               String path,
                               List<TemplateValidationError> errors) {
        if (value == null) {
            return null;
        }
        if (!value.isNumber()) {
            add(errors, path, "必须是数值");
            return null;
        }
        return value.decimalValue();
    }

    private boolean matchesType(JsonNode value, String type) {
        return switch (type) {
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            default -> false;
        };
    }

    private void requireText(JsonNode node,
                             String field,
                             String expected,
                             List<TemplateValidationError> errors) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || !expected.equals(value.textValue())) {
            add(errors, field, field + " 必须是 " + expected);
        }
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private Set<String> requiredFields(JsonNode required) {
        Set<String> fields = new HashSet<>();
        if (required == null || !required.isArray()) {
            return fields;
        }
        required.forEach(item -> fields.add(item.textValue()));
        return fields;
    }

    private void rejectUnknownKeys(JsonNode node,
                                   Set<String> allowed,
                                   String prefix,
                                   List<TemplateValidationError> errors) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                add(errors, prefix.isEmpty() ? field : prefix + "." + field,
                        "不支持的字段或约束：" + field);
            }
        });
    }

    private TemplateValidationResult invalidRoot(String message) {
        return TemplateValidationResult.of(List.of(new TemplateValidationError("$", message)));
    }

    private void add(List<TemplateValidationError> errors, String path, String message) {
        errors.add(new TemplateValidationError(path, message));
    }
}
