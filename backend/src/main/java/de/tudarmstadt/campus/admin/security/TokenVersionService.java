package de.tudarmstadt.campus.admin.security;

import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Resolves the current token versions of an account, cached in Redis so the filter chain stays free of
 * database round trips (spec section 4.2).
 * <p>
 * Two counters: {@code token_version} invalidates access tokens on a role change, deactivation or
 * password change, {@code refresh_version} only on a password change, reset or deactivation. That is
 * what lets a user keep refreshing after a role change and still lose every session when the password
 * changes (docs/DECISIONS.md D-3).
 */
@Service
public class TokenVersionService {

    private static final Logger log = LoggerFactory.getLogger(TokenVersionService.class);

    private static final String ACCESS_KEY_PREFIX = "user:ver:";
    private static final String REFRESH_KEY_PREFIX = "user:rver:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final AdminUserRepository adminUsers;

    public TokenVersionService(StringRedisTemplate redis, AdminUserRepository adminUsers) {
        this.redis = redis;
        this.adminUsers = adminUsers;
    }

    public int currentAccessVersion(long userId) {
        return current(ACCESS_KEY_PREFIX, userId, AdminUser::getTokenVersion);
    }

    public int currentRefreshVersion(long userId) {
        return current(REFRESH_KEY_PREFIX, userId, AdminUser::getRefreshVersion);
    }

    /** Drops both cached values; call after every write that bumps a counter. */
    public void invalidate(long userId) {
        try {
            redis.delete(ACCESS_KEY_PREFIX + userId);
            redis.delete(REFRESH_KEY_PREFIX + userId);
        } catch (DataAccessException ex) {
            log.error("Could not drop the cached token versions of user {}. Stale entries expire "
                    + "after {}.", userId, CACHE_TTL, ex);
        }
    }

    private int current(String keyPrefix, long userId, VersionAccessor accessor) {
        String key = keyPrefix + userId;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return Integer.parseInt(cached);
            }
        } catch (DataAccessException | NumberFormatException ex) {
            // Never fail open: fall through to the database rather than skipping the check.
            log.warn("Could not read {} from Redis, falling back to the database", key, ex);
        }

        int version = adminUsers.findById(userId).map(accessor::versionOf).orElse(-1);
        try {
            redis.opsForValue().set(key, String.valueOf(version), CACHE_TTL);
        } catch (DataAccessException ex) {
            log.warn("Could not cache {} in Redis", key, ex);
        }
        return version;
    }

    @FunctionalInterface
    private interface VersionAccessor {
        int versionOf(AdminUser user);
    }
}
