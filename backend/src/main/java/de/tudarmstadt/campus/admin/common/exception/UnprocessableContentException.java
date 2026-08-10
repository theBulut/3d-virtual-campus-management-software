package de.tudarmstadt.campus.admin.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The request is understood but the model refuses it in the current state — above all
 * {@code INVALID_STATUS_TRANSITION} in the review workflow.
 * <p>
 * Spec section 4.5 names 409 for this while section 4.7 reserves 422; 422 wins because 4.7 is the
 * normative error code catalogue and 409 stays with the invariants (docs/DECISIONS.md D-13).
 * <p>
 * Named after RFC 9110's "Unprocessable Content" rather than the older "Unprocessable Entity" — in a
 * codebase full of JPA entities the old wording reads like something else entirely.
 */
public class UnprocessableContentException extends ApiException {

    public UnprocessableContentException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
}
