package de.tudarmstadt.campus.admin.security;

import de.tudarmstadt.campus.admin.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * A counter per key and time window, kept in Redis (spec section 7, NFA-06).
 * <p>
 * Needed since registration became open to everyone: an endpoint that creates accounts without a brake
 * is an invitation. The same mechanism protects the login against password guessing.
 * <p>
 * Deliberately <b>fails open</b> — the opposite of {@link TokenBlacklistService}. A revoked token that
 * silently starts working again would be a security hole; a rate limit that stops counting while Redis
 * is down only removes a safeguard from an endpoint that is protected by validation and password
 * checks anyway. Locking every visitor out of registration because a cache is unavailable would be the
 * worse failure.
 */
@Service
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private static final String KEY_PREFIX = "rl:";

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Counts one attempt and throws once the limit is exceeded within the window.
     *
     * @param bucket what is being limited, for example {@code login} or {@code register}
     * @param key    who is being limited, for example a username or a client address
     */
    public void check(String bucket, String key, int limit, Duration window) {
        String redisKey = KEY_PREFIX + bucket + ":" + key.toLowerCase();
        long attempts;
        try {
            Long counted = redis.opsForValue().increment(redisKey);
            attempts = counted == null ? 1 : counted;
            if (attempts == 1) {
                // The window starts with the first attempt and is not extended by later ones, so a
                // steady stream of requests cannot keep the key alive forever.
                redis.expire(redisKey, window);
            }
        } catch (DataAccessException ex) {
            log.error("Redis is unreachable, rate limit for {} '{}' is not enforced", bucket, key, ex);
            return;
        }

        if (attempts > limit) {
            log.warn("Rate limit exceeded for {} '{}': {} attempts within {}", bucket, key, attempts,
                    window);
            throw new TooManyRequestsException(
                    "Zu viele Versuche. Bitte versuchen Sie es später erneut.");
        }
    }

    /** Clears the counter, for example after a successful login. */
    public void reset(String bucket, String key) {
        try {
            redis.delete(KEY_PREFIX + bucket + ":" + key.toLowerCase());
        } catch (DataAccessException ex) {
            log.warn("Could not reset the rate limit counter for {} '{}'", bucket, key, ex);
        }
    }

    /** 429, the one status the error catalogue of spec section 4.7 did not need before. */
    public static class TooManyRequestsException extends ApiException {

        public TooManyRequestsException(String message) {
            super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", message);
        }
    }
}
