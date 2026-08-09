package de.tudarmstadt.campus.admin.security;

import de.tudarmstadt.campus.admin.config.AppProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token creation and parsing without Spring or a database (spec section 4.1).
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-with-far-more-than-32-characters";
    private static final String OTHER_SECRET = "a-completely-different-secret-of-sufficient-length";

    private final JwtService jwtService = serviceWith(SECRET, Duration.ofMinutes(15));

    private static JwtService serviceWith(String secret, Duration accessTtl) {
        return new JwtService(new AppProperties(
                new AppProperties.Jwt(secret, accessTtl, Duration.ofDays(7)),
                new AppProperties.InitialAdmin("admin", "admin", "admin@localhost"),
                false, "./data/media"));
    }

    @Test
    void accessTokenCarriesTheClaimsOfTheSpecification() {
        String token = jwtService.createAccessToken(42L, "mbulut", 3,
                List.of("PROJEKTLEITER"), List.of("USER_READ", "POI_PUBLISH"));

        TokenClaims claims = jwtService.parse(token);

        assertThat(claims.username()).isEqualTo("mbulut");
        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.type()).isEqualTo(TokenType.ACCESS);
        assertThat(claims.version()).isEqualTo(3);
        assertThat(claims.roles()).containsExactly("PROJEKTLEITER");
        assertThat(claims.permissions()).containsExactly("USER_READ", "POI_PUBLISH");
        assertThat(claims.jti()).isNotBlank();
        assertThat(claims.expiresAt()).isAfter(claims.issuedAt());
    }

    /** Refresh tokens stay minimal: no roles, no permissions (spec section 4.1). */
    @Test
    void refreshTokenCarriesNoAuthorisationData() {
        TokenClaims claims = jwtService.parse(jwtService.createRefreshToken(42L, "mbulut", 1));

        assertThat(claims.type()).isEqualTo(TokenType.REFRESH);
        assertThat(claims.isRefreshToken()).isTrue();
        assertThat(claims.isAccessToken()).isFalse();
        assertThat(claims.roles()).isEmpty();
        assertThat(claims.permissions()).isEmpty();
        assertThat(claims.version()).isEqualTo(1);
    }

    @Test
    void everyTokenGetsItsOwnIdentifier() {
        String first = jwtService.createAccessToken(1L, "a", 0, List.of(), List.of());
        String second = jwtService.createAccessToken(1L, "a", 0, List.of(), List.of());

        assertThat(jwtService.parse(first).jti()).isNotEqualTo(jwtService.parse(second).jti());
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() {
        String foreign = serviceWith(OTHER_SECRET, Duration.ofMinutes(15))
                .createAccessToken(1L, "attacker", 0, List.of(), List.of("USER_DELETE"));

        assertThatThrownBy(() -> jwtService.parse(foreign)).isInstanceOf(SignatureException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        JwtService expiring = serviceWith(SECRET, Duration.ofSeconds(-10));
        String token = expiring.createAccessToken(1L, "mbulut", 0, List.of(), List.of());

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsAManipulatedToken() {
        String token = jwtService.createAccessToken(1L, "mbulut", 0, List.of(), List.of("USER_READ"));
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "Zm9yZ2Vk";

        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(SignatureException.class);
    }

    @Test
    void exposesTheConfiguredAccessTokenLifetime() {
        assertThat(jwtService.accessTokenLifetime()).isEqualTo(Duration.ofMinutes(15));
    }
}
