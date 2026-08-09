package de.tudarmstadt.campus.admin.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The request is well formed but asks for something the model does not allow, for example
 * {@code ROLE_NOT_ASSIGNABLE} (INV-4).
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
