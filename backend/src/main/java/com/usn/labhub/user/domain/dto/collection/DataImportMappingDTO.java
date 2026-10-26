package com.usn.labhub.user.domain.dto.collection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class DataImportMappingDTO {
    @NotNull @Min(1)
    private Integer version;
    @NotNull @Min(0)
    private Integer importVersion;
    @NotNull @Valid
    private Mapping mapping;

    @Data
    public static class Mapping {
        private String delimiter;
        private String encoding;
        private Integer headerRow;
        @Valid private TimeMapping timeMapping;
        @Valid private List<ColumnMapping> columns;
        private List<String> ignoredColumns;
    }

    @Data
    public static class TimeMapping {
        private String sourceColumn;
        private String format;
        private String timezone;
    }

    @Data
    public static class ColumnMapping {
        private String sourceColumn;
        private String metricCode;
        private String valueType;
        private String sourceUnit;
        private String targetUnit;
    }
}
