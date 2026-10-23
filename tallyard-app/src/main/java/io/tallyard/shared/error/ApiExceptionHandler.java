package io.tallyard.shared.error;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Maps exceptions to RFC 9457 problem details. Every response carries a stable {@code code}
 * extension so clients can branch on it without parsing messages.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE) // ahead of Spring's ProblemDetailsExceptionHandler so every error carries a code
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String TYPE_BASE = "https://tallyard.io/problems/";

    @ExceptionHandler(TallyardException.class)
    ResponseEntity<ProblemDetail> handleDomain(TallyardException ex) {
        ProblemDetail problem = problem(ex.status(), ex.code(), ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof ServiceUnavailableException sue) {
            headers.add(
                    HttpHeaders.RETRY_AFTER,
                    Long.toString(Math.max(1, sue.retryAfter().toSeconds())));
        } else if (ex instanceof RateLimitedException rle) {
            headers.add(
                    HttpHeaders.RETRY_AFTER,
                    Long.toString(Math.max(1, rle.retryAfter().toSeconds())));
        }
        return ResponseEntity.status(ex.status()).headers(headers).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    /** Thrown for constraints on method parameters and nested collection elements (e.g. {@code List<@Valid Event>}). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException ex) {
        List<Map<String, String>> errors = new java.util.ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String prefix = result.getContainerIndex() == null ? "" : "[" + result.getContainerIndex() + "].";
            if (result instanceof ParameterErrors pe) {
                pe.getFieldErrors()
                        .forEach(fe -> errors.add(Map.of(
                                "field", prefix + fe.getField(), "message", String.valueOf(fe.getDefaultMessage()))));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors()
                        .forEach(e -> errors.add(Map.of(
                                "field",
                                prefix + (name == null ? "?" : name),
                                "message",
                                String.valueOf(e.getDefaultMessage()))));
            }
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "malformed_request", "Request body could not be read"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", String.valueOf(v.getPropertyPath()), "message", v.getMessage()))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(
                        HttpStatus.CONFLICT,
                        "concurrent_modification",
                        "The resource was modified concurrently; retry the request"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.debug("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(HttpStatus.CONFLICT, "integrity_violation", "The request conflicts with existing data"));
    }

    /** Thrown by {@code @PreAuthorize}; must not be swallowed by the catch-all below. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problem(HttpStatus.FORBIDDEN, "forbidden", "The API key lacks the required scope"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse er) {
            // Spring MVC's own 4xx family (405, 406, 415, 404 for static resources, …): keep the
            // status and add our code extension instead of masking them as 500.
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            String detail = er.getBody().getDetail() == null
                    ? status.getReasonPhrase()
                    : er.getBody().getDetail();
            String code = status.name().toLowerCase(java.util.Locale.ROOT);
            if (status.is5xxServerError()) {
                log.error("Unhandled exception", ex);
            }
            return ResponseEntity.status(status).headers(er.getHeaders()).body(problem(status, code, detail));
        }
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred"));
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
