package com.usn.labhub.user.domain.dto.learning;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LearningStageUpdateDTO {
    @Size(min = 2, max = 80)
    private String name;

    @Size(max = 500)
    private String description;
    private boolean descriptionSpecified;

    @Min(0)
    private Integer sortOrder;

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionSpecified = true;
    }
}
