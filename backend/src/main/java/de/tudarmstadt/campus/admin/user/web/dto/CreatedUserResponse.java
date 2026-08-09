package de.tudarmstadt.campus.admin.user.web.dto;

/**
 * Result of creating an account: the account itself plus the generated password, which is shown once and
 * never stored in plaintext.
 */
public record CreatedUserResponse(UserResponse user, String temporaryPassword) {
}
