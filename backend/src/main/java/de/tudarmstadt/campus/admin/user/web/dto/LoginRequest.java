package de.tudarmstadt.campus.admin.user.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Credentials for {@code POST /api/auth/login}.
 * <p>
 * The field keeps the name {@code username} for compatibility, but the value may also be a mail address:
 * self-registered players remember the address they signed up with, not the username they picked once.
 */
public record LoginRequest(
        @NotBlank(message = "Benutzername oder E-Mail darf nicht leer sein") String username,
        @NotBlank(message = "Passwort darf nicht leer sein") String password) {
}
