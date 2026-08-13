package de.tudarmstadt.campus.admin.user.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-registration of a player (spec section 5.1).
 * <p>
 * Unlike {@link CreateUserRequest} this carries a password: the account chooses it, so there is no
 * temporary secret and no forced change afterwards. Roles are not part of the payload — everyone who
 * registers becomes {@code EXTERNE_PERSON}, and only an administration promotes from there.
 */
public record RegisterRequest(
        @NotBlank(message = "Benutzername darf nicht leer sein")
        @Size(min = 3, max = 64, message = "Benutzername muss zwischen 3 und 64 Zeichen lang sein")
        @Pattern(regexp = "[A-Za-z0-9._-]+",
                message = "Benutzername darf nur Buchstaben, Ziffern, Punkt, Unterstrich und "
                        + "Bindestrich enthalten")
        String username,

        @NotBlank(message = "E-Mail darf nicht leer sein")
        @Email(message = "E-Mail muss eine gültige Adresse sein")
        @Size(max = 255, message = "E-Mail darf höchstens 255 Zeichen lang sein")
        String email,

        @NotBlank(message = "Vorname darf nicht leer sein")
        @Size(max = 100, message = "Vorname darf höchstens 100 Zeichen lang sein")
        String firstName,

        @NotBlank(message = "Nachname darf nicht leer sein")
        @Size(max = 100, message = "Nachname darf höchstens 100 Zeichen lang sein")
        String lastName,

        @NotBlank(message = "Passwort darf nicht leer sein")
        @Size(min = 12, max = 200, message = "Passwort muss mindestens 12 Zeichen lang sein")
        String password) {
}
