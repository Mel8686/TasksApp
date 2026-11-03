package com.dsk.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One error contract for the whole API, RFC 9457 {@code ProblemDetail} shaped.
 *
 * <p>Rules: never return an exception message or stack trace to the caller;
 * give every 5xx a correlation id that appears in the log and in the response;
 * log every rejected request with enough context to investigate it later.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidationFailed(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        log.warn("Rejected request: validation failed fields={}", errors.keySet());

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        detail.setTitle("Invalid request");
        detail.setProperty("errors", errors);
        return detail;
    }

    /**
     * Constraints on {@code @RequestParam}/{@code @PathVariable} raise this in
     * Framework 6.1+. Mapped to the same {@code errors} shape as a bad body.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail onParameterValidationFailed(HandlerMethodValidationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            String field = name == null ? "parameter" : name;
            result.getResolvableErrors().forEach(error -> {
                String message = error.getDefaultMessage();
                errors.putIfAbsent(field, message == null ? "invalid value" : message);
            });
        });

        log.warn("Rejected request: invalid parameters={}", errors.keySet());

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        detail.setTitle("Invalid request");
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(ImportRejectedException.class)
    ProblemDetail onImportRejected(ImportRejectedException ex) {
        log.warn("Rejected import: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Import rejected");
        if (ex.getCount() > 0) {
            detail.setProperty("rejectedCount", ex.getCount());
            detail.setProperty("rejected", ex.getRejections());
        }
        return detail;
    }

    /**
     * The {@code If-Match} version was stale. The current row travels with the
     * error so the client can show what it would have overwritten.
     */
    @ExceptionHandler(TaskConflictException.class)
    ProblemDetail onTaskConflict(TaskConflictException ex) {
        log.warn("Rejected write: stale version for task {}", ex.getCurrent().getId());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.PRECONDITION_FAILED, ex.getMessage());
        detail.setTitle("Task changed");
        detail.setProperty("current", ex.getCurrent());
        return detail;
    }

    /**
     * Safety net for the read-then-write window: another writer committed
     * between the check and the flush. Same status as a stale {@code If-Match}.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail onOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Rejected write: optimistic lock failure");
        return ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED,
                "Task was modified by someone else; reload and try again");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail onUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Rejected upload: multipart limit exceeded");
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file is too large");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail onUnreadableBody(HttpMessageNotReadableException ex) {
        // The raw message can name internal types, so it is logged, not returned.
        log.warn("Rejected request: unreadable body ({})", ex.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Rejected request: parameter '{}' has the wrong type", ex.getName());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail onUnexpected(Exception ex) {
        // Framework exceptions already carry the right status (404 for a missing
        // task, for example). Preserve it instead of turning it into a 500.
        if (ex instanceof ErrorResponse errorResponse) {
            return ProblemDetail.forStatusAndDetail(
                    errorResponse.getStatusCode(),
                    errorResponse.getBody().getDetail() == null
                            ? "Request could not be completed"
                            : errorResponse.getBody().getDetail());
        }

        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled error id={}", errorId, ex);

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        detail.setTitle("Internal error");
        detail.setProperty("errorId", errorId);
        return detail;
    }
}
