package de.tudarmstadt.campus.admin.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis backed blacklist for explicit logout (spec section 4.2, FA-03).
 * <p>
 * Each entry lives exactly as long as the token it revokes, so the store cannot grow without bound.
 * Uses Spring Boot's {@link StringRedisTemplate}, which is the {@code RedisTemplate<String, String>}
 * with string serializers the specification asks for in section 3.
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    private static final String KEY_PREFIX = "jwt:bl:";
    private static final String VALUE = "1";

    private final StringRedisTemplate redis;

    public TokenBlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Revokes a token until it would have expired anyway. Already expired tokens are ignored. */
    public void blacklist(String jti, Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return;
        }
        redis.opsForValue().set(KEY_PREFIX + jti, VALUE, remaining);
    }

    /**
     * Fails closed: if Redis cannot be reached the token counts as revoked. A logout that silently stops
     * working would be worse than a login that has to be repeated.
     */
    public boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
        } catch (DataAccessException ex) {
            log.error("Redis is unreachable, rejecting token {} as a precaution", jti, ex);
            return true;
        }
    }
}
