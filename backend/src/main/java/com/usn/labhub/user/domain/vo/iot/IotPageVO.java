package com.usn.labhub.user.domain.vo.iot;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class IotPageVO<T> {
    private long total;
    private List<T> list;
}
