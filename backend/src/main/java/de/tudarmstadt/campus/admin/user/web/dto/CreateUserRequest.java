package de.tudarmstadt.campus.admin.user.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * New account (spec section 5.2).
 * <p>
 * {@code roles} is mandatory: INV-3 requires every account to hold at least one role, and the
 * specification leaves the field unqualified (docs/DECISIONS.md D-27). Every entry is checked against
 * the caller's grant set. No password is supplied — the server generates a temporary one and returns it
 * once.
 */
public record CreateUserRequest(
        @NotBlank(message = "Benutzername darf nicht leer sein")
        @Size(max = 64, message = "Benutzername darf höchstens 64 Zeichen lang sein")
        @Pattern(regexp = "[A-Za-z0-9._-]+",
                message = "Benutzername darf nur Buchstaben, Ziffern, Punkt, Unterstrich und Bindestrich enthalten")
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

        @Size(max = 150, message = "Einrichtung darf höchstens 150 Zeichen lang sein")
        String organisation,

        @NotEmpty(message = "Mindestens eine Rolle muss angegeben werden")
        List<String> roles) {
}
