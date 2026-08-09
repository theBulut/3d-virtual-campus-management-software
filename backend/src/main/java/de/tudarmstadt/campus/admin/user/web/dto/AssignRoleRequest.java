package de.tudarmstadt.campus.admin.user.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignRoleRequest(
        @NotBlank(message = "Rollenname darf nicht leer sein") String roleName) {
}
