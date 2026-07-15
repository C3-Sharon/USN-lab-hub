package com.usn.labhub.user.domain.vo.iot;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "PM-001 可解释健康评分")
public class IotHealthVO {

    private int score;
    private String level;
    private List<String> reasons;
    private String calculatedAt;
}
