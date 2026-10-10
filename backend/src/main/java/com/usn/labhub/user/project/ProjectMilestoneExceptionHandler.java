package com.usn.labhub.user.project;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.controller.ProjectMilestoneController;
import com.usn.labhub.user.controller.ProjectTaskController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.format.DateTimeParseException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {ProjectMilestoneController.class, ProjectTaskController.class})
public class ProjectMilestoneExceptionHandler {

    @ExceptionHandler(ProjectApiException.class)
    public ResponseEntity<Result<Object>> handleProjectException(ProjectApiException exception) {
        Result<Object> result = Result.error(
                exception.getStatus().value(), exception.getMessage(), exception.getReason());
        result.setData(exception.getData());
        return ResponseEntity.status(exception.getStatus()).body(result);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof DateTimeParseException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Result.error(400, "截止日期格式错误", "INVALID_DUE_DATE"));
            }
            cause = cause.getCause();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "参数错误", "INVALID_PARAMETER"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Void>> handleInvalidRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "参数错误", "INVALID_PARAMETER"));
    }
}
