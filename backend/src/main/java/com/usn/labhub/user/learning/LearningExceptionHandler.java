package com.usn.labhub.user.learning;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.controller.LearningRoadmapController;
import com.usn.labhub.user.controller.LearningStructureController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {LearningRoadmapController.class, LearningStructureController.class})
public class LearningExceptionHandler {

    @ExceptionHandler(LearningApiException.class)
    public ResponseEntity<Result<Void>> handleLearningException(LearningApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Result.error(
                        exception.getStatus().value(), exception.getMessage(), exception.getReason()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> handleInvalidRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "参数错误", "INVALID_PARAMETER"));
    }
}
