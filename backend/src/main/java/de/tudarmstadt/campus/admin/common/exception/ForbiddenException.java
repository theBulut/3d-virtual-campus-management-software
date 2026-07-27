package de.tudarmstadt.campus.admin.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Domain level denial: the caller holds the required authority but the operation is out of scope for them,
 * for example {@code ROLE_NOT_GRANTABLE} or {@code TARGET_OUT_OF_SCOPE} (spec section 1.4).
 * <p>
 * Deliberately not Spring Security's {@code AccessDeniedException}: those denials come from
 * {@code @PreAuthorize} and are audited as {@code ACCESS_DENIED}, whereas these are business rule
 * violations that services audit themselves with {@code success = false} and the concrete error code.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
