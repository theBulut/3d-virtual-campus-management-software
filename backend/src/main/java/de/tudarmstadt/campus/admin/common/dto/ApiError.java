package de.tudarmstadt.campus.admin.common.dto;

import org.springframework.http.HttpStatusCode;

import java.time.Instant;
import java.util.Map;

/**
 * The single error shape of the API (spec section 4.7). Stacktraces and internal details never reach the
 * client; {@code message} is German because it is shown to end users.
 *
 * @param fieldErrors validation failures per field, {@code null} unless the error is a validation error
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(HttpStatusCode status, String code, String message, String path) {
        return of(status, code, message, path, null);
    }

    public static ApiError of(HttpStatusCode status, String code, String message, String path,
                              Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status.value(), reasonPhrase(status), code, message, path, fieldErrors);
    }

    private static String reasonPhrase(HttpStatusCode status) {
        return status instanceof org.springframework.http.HttpStatus resolved
                ? resolved.getReasonPhrase()
                : String.valueOf(status.value());
    }
}
