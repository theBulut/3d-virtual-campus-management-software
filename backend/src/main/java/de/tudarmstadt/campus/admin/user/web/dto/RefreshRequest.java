package de.tudarmstadt.campus.admin.user.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Refresh-Token darf nicht leer sein") String refreshToken) {
}
