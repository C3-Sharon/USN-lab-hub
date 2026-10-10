package com.usn.labhub.user.domain.dto.learning;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LearningUnitUpdateDTO {
    @Size(min = 2, max = 120)
    private String title;

    @Size(max = 2000)
    private String description;
    private boolean descriptionSpecified;

    @Min(0)
    private Integer sortOrder;

    private Long templateId;

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionSpecified = true;
    }
}
