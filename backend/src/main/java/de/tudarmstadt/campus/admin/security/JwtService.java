package de.tudarmstadt.campus.admin.security;

import de.tudarmstadt.campus.admin.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and parses the HS256 tokens of spec section 4.1.
 * <p>
 * Access tokens carry the full permission set. The catalogue holds fewer than 40 entries, so
 * authorisation stays stateless and needs no database round trip per request; the price — permission
 * changes taking effect late — is paid by the {@code ver} claim (spec section 4.2).
 */
@Service
public class JwtService {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_VERSION = "ver";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "perms";

    private final SecretKey signingKey;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(AppProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = properties.jwt().accessTtl();
        this.refreshTtl = properties.jwt().refreshTtl();
    }

    public Duration accessTokenLifetime() {
        return accessTtl;
    }

    /**
     * @param version the account's {@code token_version}; a mismatch later makes the token stale
     */
    public String createAccessToken(long userId, String username, int version,
                                    List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TYPE, TokenType.ACCESS.claimValue())
                .claim(CLAIM_VERSION, version)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_PERMISSIONS, permissions)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Refresh tokens carry neither roles nor permissions — those are rebuilt from the database on every
     * refresh, which is what lets a reduced permission set take effect (scenario S-08).
     *
     * @param version the account's {@code refresh_version} (docs/DECISIONS.md D-3)
     */
    public String createRefreshToken(long userId, String username, int version) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TYPE, TokenType.REFRESH.claimValue())
                .claim(CLAIM_VERSION, version)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies the signature and expiry and maps the claims.
     *
     * @throws JwtException if the token is malformed, expired or not signed with our key
     */
    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new TokenClaims(
                claims.getSubject(),
                numberClaim(claims, CLAIM_USER_ID),
                TokenType.fromClaim(claims.get(CLAIM_TYPE, String.class)),
                (int) numberClaim(claims, CLAIM_VERSION),
                stringListClaim(claims, CLAIM_ROLES),
                stringListClaim(claims, CLAIM_PERMISSIONS),
                claims.getId(),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant());
    }

    private static long numberClaim(Claims claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new JwtException("Claim '" + name + "' is missing or not numeric");
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringListClaim(Claims claims, String name) {
        Object value = claims.get(name);
        return value instanceof List<?> list ? List.copyOf((List<String>) list) : List.of();
    }
}
