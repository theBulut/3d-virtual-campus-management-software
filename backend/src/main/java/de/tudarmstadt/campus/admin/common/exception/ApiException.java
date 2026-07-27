package de.tudarmstadt.campus.admin.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all errors that carry a documented status and error code (spec section 4.7). The message
 * is German because {@code GlobalExceptionHandler} passes it straight to the client.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
