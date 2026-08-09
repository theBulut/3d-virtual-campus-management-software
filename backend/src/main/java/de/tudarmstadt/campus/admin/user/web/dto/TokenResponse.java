package de.tudarmstadt.campus.admin.user.web.dto;

/**
 * Result of a login or refresh (spec section 5.1).
 *
 * @param expiresIn lifetime of the access token in seconds, so the client can refresh ahead of time
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        CurrentUserResponse user) {
}
