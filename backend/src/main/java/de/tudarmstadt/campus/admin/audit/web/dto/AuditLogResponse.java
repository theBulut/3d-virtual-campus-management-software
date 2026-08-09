package de.tudarmstadt.campus.admin.audit.web.dto;

import java.time.Instant;
import java.util.Map;

/**
 * One audit entry as the API exposes it (spec section 5.5).
 * <p>
 * The two states are nested objects rather than JSON strings, so a client can render a field by field
 * comparison without parsing twice. They are already masked at write time — {@code AuditService} never
 * lets a password hash or a token into the log in the first place, so there is nothing left to hide when
 * reading.
 *
 * @param actorId null once the account has been deleted; {@code actorUsername} survives it
 */
public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorUsername,
        String action,
        String resourceType,
        String resourceId,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        String ipAddress,
        String userAgent,
        boolean success,
        String errorCode,
        Instant createdAt) {
}
