package de.tudarmstadt.campus.admin.common.exception;

import de.tudarmstadt.campus.admin.audit.service.AuditService;
import de.tudarmstadt.campus.admin.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates every exception into the {@link ApiError} shape of spec section 4.7. Internal details never
 * reach the client (NFA-07).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditService auditService;

    public GlobalExceptionHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Die übermittelten Daten sind unvollständig oder ungültig.", request, fieldErrors);
    }

    /**
     * Denials raised by {@code @PreAuthorize} arrive here as {@code AuthorizationDeniedException}, a
     * subclass of {@link AccessDeniedException}. This handler must exist: a {@code @ControllerAdvice}
     * intercepts the exception before Spring Security's {@code ExceptionTranslationFilter} can, so
     * without it every 403 would be reported as 500 by the catch-all below.
     * <p>
     * It is also the only place that sees these denials, which is why the {@code ACCESS_DENIED} audit
     * entry is written here rather than in {@code RestAccessDeniedHandler} (docs/DECISIONS.md D-10).
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        auditService.record("ACCESS_DENIED", "AUTH", request.getRequestURI(), false,
                "ACCESS_DENIED", null, null);
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Für diese Aktion fehlt Ihnen die erforderliche Berechtigung.", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Bitte melden Sie sich an.", request, null);
    }

    /**
     * Spring MVC's own exceptions (unknown path, wrong method, unreadable body, …) implement
     * {@link ErrorResponse} and already carry the correct status. They are mapped into our shape rather
     * than masked as 500; anything else is an unexpected failure.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return build(status, codeFor(status), messageFor(status), request, null);
        }
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Es ist ein unerwarteter Fehler aufgetreten.", request, null);
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "BAD_REQUEST";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "REQUEST_FAILED";
        };
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Die Anfrage konnte nicht verarbeitet werden.";
            case 404 -> "Die angeforderte Ressource wurde nicht gefunden.";
            case 405 -> "Diese HTTP-Methode ist für die Ressource nicht zulässig.";
            case 406 -> "Das angeforderte Antwortformat wird nicht unterstützt.";
            case 415 -> "Das Format der Anfrage wird nicht unterstützt.";
            default -> "Die Anfrage konnte nicht verarbeitet werden.";
        };
    }

    private static ResponseEntity<ApiError> build(HttpStatusCode status, String code, String message,
                                                 HttpServletRequest request, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status, code, message, request.getRequestURI(), fieldErrors));
    }
}
