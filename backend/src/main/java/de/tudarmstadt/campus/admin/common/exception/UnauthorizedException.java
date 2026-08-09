package de.tudarmstadt.campus.admin.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Missing or no longer valid authentication, for example {@code INVALID_CREDENTIALS} or
 * {@code TOKEN_STALE} (spec sections 4.7 and 1.5, INV-6).
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }
}
