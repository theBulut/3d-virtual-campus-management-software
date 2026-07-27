package de.tudarmstadt.campus.admin.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Violated invariant or conflicting state, for example {@code LAST_ADMIN_PROTECTED} (spec section 1.5).
 */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
