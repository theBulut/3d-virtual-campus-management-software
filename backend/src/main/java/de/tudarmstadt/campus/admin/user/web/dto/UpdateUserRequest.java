package de.tudarmstadt.campus.admin.user.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Master data of a foreign account. Roles and the active flag have their own endpoints, so a plain
 * update can never widen someone's permissions by accident.
 */
public record UpdateUserRequest(
        @NotBlank(message = "Vorname darf nicht leer sein")
        @Size(max = 100, message = "Vorname darf höchstens 100 Zeichen lang sein")
        String firstName,

        @NotBlank(message = "Nachname darf nicht leer sein")
        @Size(max = 100, message = "Nachname darf höchstens 100 Zeichen lang sein")
        String lastName,

        @NotBlank(message = "E-Mail darf nicht leer sein")
        @Email(message = "E-Mail muss eine gültige Adresse sein")
        @Size(max = 255, message = "E-Mail darf höchstens 255 Zeichen lang sein")
        String email,

        @Size(max = 150, message = "Einrichtung darf höchstens 150 Zeichen lang sein")
        String organisation) {
}
