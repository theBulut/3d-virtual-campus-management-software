package de.tudarmstadt.campus.admin.user.web.dto;

/**
 * A generated password, returned exactly once (spec section 5.2). The prototype has no mail delivery —
 * the specification names that as a deliberate simplification in section 8.
 */
public record TemporaryPasswordResponse(String temporaryPassword) {
}
