package de.tudarmstadt.campus.admin.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Aktuelles Passwort darf nicht leer sein") String currentPassword,

        @NotBlank(message = "Neues Passwort darf nicht leer sein")
        @Size(min = 12, max = 200, message = "Neues Passwort muss mindestens 12 Zeichen lang sein")
        String newPassword) {
}
