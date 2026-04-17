package com.usn.labhub.user.common.result;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code; // 200成功，其他失败
    private String msg;   // 提示消息
    private T data;       // 实际的数据负载（泛型，可以装任何 VO）

    // 快捷成功方法
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.msg = "操作成功";
        result.data = data;
        return result;
    }

    // 快捷失败方法
    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.code = 500;
        result.msg = msg;
        return result;
    }
}