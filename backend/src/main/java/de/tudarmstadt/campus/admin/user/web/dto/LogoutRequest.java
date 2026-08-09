package de.tudarmstadt.campus.admin.user.web.dto;

/**
 * Optional body of a logout. The access token is revoked from the {@code Authorization} header; the
 * refresh token has to be supplied here because its {@code jti} is not part of the access token
 * (docs/DECISIONS.md D-19).
 */
public record LogoutRequest(String refreshToken) {
}
