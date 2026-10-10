package com.usn.labhub.user.domain.dto.learning;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LearningRoadmapUpdateDTO {

    @Size(min = 2, max = 80)
    private String title;

    @Size(max = 500)
    private String description;
    private boolean descriptionSpecified;

    @Pattern(regexp = "^(BEGINNER|INTERMEDIATE|ADVANCED)$")
    private String difficulty;

    @Min(1)
    private Integer estimatedHours;

    @Size(max = 64)
    private String coverMediaId;
    private boolean coverMediaIdSpecified;

    @Min(0)
    private Integer sortOrder;

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionSpecified = true;
    }

    @JsonSetter("coverMediaId")
    public void setCoverMediaId(String coverMediaId) {
        this.coverMediaId = coverMediaId;
        this.coverMediaIdSpecified = true;
    }
}
