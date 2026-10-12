package com.usn.labhub.user.learning.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentTemplateSchemaValidatorTest {

    private ExperimentTemplateSchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ExperimentTemplateSchemaValidator(new ObjectMapper());
    }

    @Test
    void acceptsFrozenSchemaSubsetAndMatchingData() {
        String schema = """
                {
                  "schemaVersion":"1.0",
                  "type":"object",
                  "properties":{
                    "voltage":{
                      "type":"number","title":"电压","unit":"V",
                      "minimum":0,"maximum":24,"default":5.0,
                      "displayOrder":1,"widget":"input-number"
                    },
                    "test_type":{
                      "type":"string","title":"测试类型",
                      "enum":["直流","交流"],"displayOrder":2,"widget":"select"
                    },
                    "is_pass":{
                      "type":"boolean","title":"是否通过",
                      "default":true,"displayOrder":3,"widget":"switch"
                    }
                  },
                  "required":["voltage","test_type"]
                }
                """;

        assertTrue(validator.validateSchema(schema).valid());
        assertTrue(validator.validateData(schema, """
                {"voltage":5.5,"test_type":"直流","is_pass":true}
                """).valid());
    }

    @Test
    void rejectsUnsupportedKeywordsNestedTypesAndInvalidFieldKeys() {
        String schema = """
                {
                  "schemaVersion":"1.0",
                  "type":"object",
                  "$ref":"https://example.com/schema.json",
                  "properties":{
                    "Bad-Key":{"type":"string","title":"错误字段","displayOrder":1},
                    "nested":{"type":"object","title":"嵌套对象","displayOrder":2},
                    "remarks":{"type":"string","title":"备注","pattern":".*","displayOrder":3}
                  }
                }
                """;

        TemplateValidationResult result = validator.validateSchema(schema);

        assertFalse(result.valid());
        assertTrue(paths(result).contains("$ref"));
        assertTrue(paths(result).contains("properties.Bad-Key"));
        assertTrue(paths(result).contains("properties.nested.type"));
        assertTrue(paths(result).contains("properties.remarks.pattern"));
    }

    @Test
    void validatesMetadataConstraintCompatibilityAndDefaults() {
        String schema = """
                {
                  "schemaVersion":"1.0",
                  "type":"object",
                  "properties":{
                    "name":{
                      "type":"string","title":"名","minLength":5,"maxLength":2,
                      "default":"x","displayOrder":1,"widget":"input-number"
                    },
                    "count":{
                      "type":"integer","title":"数量","minimum":10,"maximum":2,
                      "default":2.5,"displayOrder":2,"widget":"input-number"
                    },
                    "enabled":{
                      "type":"boolean","title":"是否启用","enum":[true,false],
                      "displayOrder":3,"widget":"switch"
                    }
                  },
                  "required":["missing","name","name"]
                }
                """;

        TemplateValidationResult result = validator.validateSchema(schema);

        assertFalse(result.valid());
        Set<String> paths = paths(result);
        assertTrue(paths.contains("properties.name.title"));
        assertTrue(paths.contains("properties.name.minLength"));
        assertTrue(paths.contains("properties.name.default"));
        assertTrue(paths.contains("properties.name.widget"));
        assertTrue(paths.contains("properties.count.minimum"));
        assertTrue(paths.contains("properties.count.default"));
        assertTrue(paths.contains("properties.enabled.enum"));
        assertTrue(paths.contains("required[0]"));
        assertTrue(paths.contains("required[2]"));
    }

    @Test
    void rejectsMoreThanFiftyFields() {
        String properties = IntStream.rangeClosed(1, 51)
                .mapToObj(index -> "\"field_" + index + "\":{\"type\":\"string\",\"title\":\"字段" + index
                        + "\",\"displayOrder\":" + index + "}")
                .collect(Collectors.joining(","));
        String schema = "{\"schemaVersion\":\"1.0\",\"type\":\"object\",\"properties\":{"
                + properties + "}}";

        TemplateValidationResult result = validator.validateSchema(schema);

        assertFalse(result.valid());
        assertTrue(paths(result).contains("properties"));
    }

    @Test
    void reportsStablePathsForRequiredTypeEnumRangeAndLengthFailures() {
        String schema = """
                {
                  "schemaVersion":"1.0",
                  "type":"object",
                  "properties":{
                    "voltage":{"type":"number","title":"电压","minimum":0,"maximum":24,"displayOrder":1},
                    "count":{"type":"integer","title":"次数","displayOrder":2},
                    "mode":{"type":"string","title":"模式","enum":["A","B"],"displayOrder":3},
                    "remarks":{"type":"string","title":"备注","minLength":2,"maxLength":5,"displayOrder":4},
                    "enabled":{"type":"boolean","title":"启用","displayOrder":5}
                  },
                  "required":["voltage","mode"]
                }
                """;

        TemplateValidationResult result = validator.validateData(schema, """
                {"count":1.5,"mode":"C","remarks":"toolong","enabled":"yes"}
                """);

        assertFalse(result.valid());
        Set<String> paths = paths(result);
        assertTrue(paths.contains("voltage"));
        assertTrue(paths.contains("count"));
        assertTrue(paths.contains("mode"));
        assertTrue(paths.contains("remarks"));
        assertTrue(paths.contains("enabled"));
    }

    @Test
    void reportsMalformedJsonAtRootPath() {
        TemplateValidationResult schemaResult = validator.validateSchema("{not-json}");
        TemplateValidationResult dataResult = validator.validateData("""
                {"schemaVersion":"1.0","type":"object","properties":{}}
                """, "[1,2,3]");

        assertFalse(schemaResult.valid());
        assertTrue(paths(schemaResult).contains("$"));
        assertFalse(dataResult.valid());
        assertTrue(paths(dataResult).contains("$"));
    }

    private Set<String> paths(TemplateValidationResult result) {
        return result.errors().stream()
                .map(TemplateValidationError::path)
                .collect(Collectors.toSet());
    }
}
