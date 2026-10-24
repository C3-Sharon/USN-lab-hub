package com.usn.labhub.user.experiment;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.controller.ExperimentRecordController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ExperimentRecordController.class)
public class ExperimentExceptionHandler {
    @ExceptionHandler(ExperimentApiException.class)
    public ResponseEntity<Result<Object>> handleExperiment(ExperimentApiException exception) {
        Result<Object> result = Result.error(exception.getStatus().value(),
                exception.getMessage(), exception.getReason());
        Map<String, Object> data = new LinkedHashMap<>();
        if (exception.getData() instanceof Map<?, ?> details) {
            details.forEach((key, value) -> data.put(String.valueOf(key), value));
        }
        data.put("traceId", UUID.randomUUID().toString());
        result.setData(data);
        return ResponseEntity.status(exception.getStatus()).body(result);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public ResponseEntity<Result<Object>> handleInvalid(Exception exception) {
        Result<Object> result = Result.error(400, "参数错误", "INVALID_PARAMETER");
        result.setData(Map.of("traceId", UUID.randomUUID().toString()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }
}
