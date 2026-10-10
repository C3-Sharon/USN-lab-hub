package com.usn.labhub.user.domain.vo.learning;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MyLearningRoadmapPageVO {
    private Long total;
    private Integer page;
    private Integer pageSize;
    private List<MyLearningRoadmapVO> list;
}
