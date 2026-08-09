package de.tudarmstadt.campus.admin.security;

import java.time.Instant;
import java.util.List;

/**
 * The claims of a parsed token (spec section 4.1), decoupled from the JJWT types so the rest of the
 * application does not depend on the library.
 *
 * @param version {@code ver} — compared against {@code token_version} for access tokens and against
 *                {@code refresh_version} for refresh tokens (docs/DECISIONS.md D-3)
 */
public record TokenClaims(
        String username,
        long userId,
        TokenType type,
        int version,
        List<String> roles,
        List<String> permissions,
        String jti,
        Instant issuedAt,
        Instant expiresAt) {

    public boolean isAccessToken() {
        return type == TokenType.ACCESS;
    }

    public boolean isRefreshToken() {
        return type == TokenType.REFRESH;
    }
}
