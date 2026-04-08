package com.realteeh.api.job.presentation.handler;

import com.realteeh.api.job.application.exception.ErrorCode;
import com.realteeh.api.job.application.exception.JobException;
import com.realteeh.api.job.domain.exception.InvalidJobStatusTransitionException;
import com.realteeh.api.job.presentation.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String DEFAULT_VALIDATION_MESSAGE = "요청 검증에 실패했습니다.";
    private static final String INVALID_REQUEST_BODY_MESSAGE = "요청 본문 형식이 올바르지 않습니다.";
    private static final String INVALID_CONTENT_TYPE_MESSAGE = "요청 Content-Type이 올바르지 않습니다.";
    private static final String OPTIMISTIC_LOCK_MESSAGE = "동시성 충돌이 발생했습니다. 잠시 후 다시 시도해주세요.";
    private static final String INTERNAL_ERROR_MESSAGE = "내부 서버 오류가 발생했습니다.";

    @ExceptionHandler(JobException.class)
    public ResponseEntity<ErrorResponse> handleJobException(final JobException e) {
        log.warn("비즈니스 예외가 발생했습니다. code={}, message={}", e.errorCode(), e.getMessage());
        return errorResponse(resolveHttpStatus(e.errorCode()), e.errorCode(), e.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleValidationException(final Exception e) {
        final String message = extractValidationMessage(e);
        log.warn("요청 검증에 실패했습니다. message={}", message);
        return errorResponse(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestBodyException(final HttpMessageNotReadableException e) {
        log.warn("요청 본문 파싱에 실패했습니다. message={}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, INVALID_REQUEST_BODY_MESSAGE);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleInvalidContentTypeException(final HttpMediaTypeNotSupportedException e) {
        log.warn("요청 Content-Type이 지원되지 않습니다. message={}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, INVALID_CONTENT_TYPE_MESSAGE);
    }

    @ExceptionHandler(InvalidJobStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransitionException(final InvalidJobStatusTransitionException e) {
        log.warn("작업 상태 전이에 실패했습니다. message={}", e.getMessage());
        return errorResponse(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE_TRANSITION, e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(final ObjectOptimisticLockingFailureException e) {
        log.warn("낙관적 락 충돌이 발생했습니다. message={}", e.getMessage());
        return errorResponse(HttpStatus.CONFLICT, ErrorCode.OPTIMISTIC_LOCK_CONFLICT, OPTIMISTIC_LOCK_MESSAGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknownException(final Exception e) {
        log.error("처리되지 않은 예외가 발생했습니다.", e);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE);
    }

    private String extractValidationMessage(final Exception e) {
        if (e instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            return extractMethodArgumentNotValidMessage(methodArgumentNotValidException);
        }

        if (e instanceof MethodArgumentTypeMismatchException methodArgumentTypeMismatchException) {
            return "요청 파라미터 형식이 올바르지 않습니다. name=%s, value=%s"
                    .formatted(methodArgumentTypeMismatchException.getName(), methodArgumentTypeMismatchException.getValue());
        }

        if (e instanceof MissingServletRequestParameterException missingServletRequestParameterException) {
            return "필수 요청 파라미터가 누락되었습니다. name=%s".formatted(missingServletRequestParameterException.getParameterName());
        }

        if (e instanceof ConstraintViolationException constraintViolationException) {
            return extractConstraintViolationMessage(constraintViolationException);
        }

        return defaultValidationMessage(e.getMessage());
    }

    private String extractMethodArgumentNotValidMessage(final MethodArgumentNotValidException e) {
        if (e.getBindingResult().getFieldError() == null) {
            return defaultValidationMessage(e.getMessage());
        }

        return defaultValidationMessage(e.getBindingResult().getFieldError().getDefaultMessage());
    }

    private String extractConstraintViolationMessage(final ConstraintViolationException e) {
        return e.getConstraintViolations().stream()
                .findFirst()
                .map(constraintViolation -> defaultValidationMessage(constraintViolation.getMessage()))
                .orElseGet(() -> defaultValidationMessage(e.getMessage()));
    }

    private String defaultValidationMessage(final String message) {
        return message == null || message.isBlank() ? DEFAULT_VALIDATION_MESSAGE : message;
    }

    private HttpStatus resolveHttpStatus(final ErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case JOB_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case IDEMPOTENCY_KEY_CONFLICT, INVALID_STATE_TRANSITION, OPTIMISTIC_LOCK_CONFLICT -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private ResponseEntity<ErrorResponse> errorResponse(
            final HttpStatus httpStatus,
            final ErrorCode errorCode,
            final String message
    ) {
        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(errorCode.name(), message));
    }
}
