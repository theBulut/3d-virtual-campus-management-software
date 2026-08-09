package de.tudarmstadt.campus.admin.user.web.dto;

import java.util.List;

/**
 * The caller's own account including effective roles and permissions — the basis for the permission
 * filtered menu in the frontend (spec section 5.1).
 * <p>
 * {@code permissions} is the authoritative list for the interface, but only for showing and hiding:
 * enforcement happens server side on every request (E-7).
 */
public record CurrentUserResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String organisation,
        boolean active,
        boolean mustChangePassword,
        List<String> roles,
        List<String> permissions) {
}
