package de.tudarmstadt.campus.admin.user.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * An account as the API exposes it. The password hash and both token counters stay inside the service
 * layer on purpose — they are neither useful nor safe outside it.
 * <p>
 * Plain data: the translation from the entity lives in {@code UserService}, so no class in the web layer
 * ever sees an entity (spec section 3).
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String organisation,
        boolean active,
        boolean mustChangePassword,
        Instant lastLoginAt,
        Instant createdAt,
        List<String> roles) {
}
