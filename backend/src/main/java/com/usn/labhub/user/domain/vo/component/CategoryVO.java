package com.usn.labhub.user.domain.vo.component;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryVO {
    private Long id;
    private String code;
    private String name;
    private Long parentId;
    private Integer level;
    private String path;
    private String status;
    private Integer sortOrder;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<CategoryVO> children = new ArrayList<>();
}
