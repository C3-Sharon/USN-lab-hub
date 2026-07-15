package com.usn.labhub.user.common.exception;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.service.iot.IotApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IotApiException.class)
    public Result<String> handleIotApiException(IotApiException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 捕获所有的 RuntimeException
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常捕获: ", e); // 在后台打出完整的错误堆栈，方便你 debug
        //  返回包装后的 Result 对象，这样前端拿到的永远是 200 OK 的 HTTP 状态码，但内容里写着错误原因
        return Result.error(e.getMessage());
    }

    /**
     * 捕获所有未知的 Exception
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("系统未知异常: ", e);
        return Result.error("服务器开小差了，请联系管理员");
    }
}
