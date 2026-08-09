package de.tudarmstadt.campus.admin.audit.service;

import de.tudarmstadt.campus.admin.audit.domain.AuditLog;
import de.tudarmstadt.campus.admin.audit.repository.AuditLogRepository;
import de.tudarmstadt.campus.admin.audit.web.dto.AuditLogResponse;
import de.tudarmstadt.campus.admin.common.dto.PageResponse;
import de.tudarmstadt.campus.admin.common.exception.NotFoundException;
import de.tudarmstadt.campus.admin.security.CampusUserDetails;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes and reads the audit log (spec section 4.6, FA-15).
 * <p>
 * Two guarantees matter here. Sensitive fields never reach the log, not even nested inside a state map —
 * the block list below is applied recursively. And a failure while auditing never breaks the business
 * operation: everything is caught and logged.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /**
     * Field names that are masked wherever they appear. Matching is case insensitive and also catches
     * snake_case, so {@code password_hash} and {@code passwordHash} are both covered.
     */
    private static final Set<String> MASKED_FIELDS = Set.of(
            "password", "passwordhash", "currentpassword", "newpassword", "temporarypassword",
            "token", "accesstoken", "refreshtoken", "secret", "jwtsecret");

    private static final String MASK = "***";

    /** Resources a holder of only AUDIT_READ_CONTENT may see (spec section 5.5). */
    public static final List<String> CONTENT_RESOURCE_TYPES =
            List.of("POI", "BUILDING", "CONSULTATION", "MEDIA");

    /**
     * A dedicated mapper on purpose: the audit representation must stay stable even if the HTTP
     * serialisation is reconfigured through {@code spring.jackson.*} (docs/DECISIONS.md D-31).
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AuditWriter writer;
    private final AuditLogRepository auditLogs;
    private final AdminUserRepository adminUsers;

    AuditService(AuditWriter writer, AuditLogRepository auditLogs, AdminUserRepository adminUsers) {
        this.writer = writer;
        this.auditLogs = auditLogs;
        this.adminUsers = adminUsers;
    }

    /** Successful operation, actor taken from the security context. */
    public void record(String action, String resourceType, String resourceId,
                       Map<String, Object> before, Map<String, Object> after) {
        record(action, resourceType, resourceId, true, null, before, after);
    }

    /**
     * A refused operation. Used for {@code ACCESS_DENIED} and for business rules that reject despite a
     * present authority, such as {@code ROLE_NOT_GRANTABLE} in scenario S-06 — those never reach Spring
     * Security's access denied handler (docs/DECISIONS.md D-10).
     */
    public void recordFailure(String action, String resourceType, String resourceId, String errorCode) {
        record(action, resourceType, resourceId, false, errorCode, null, null);
    }

    public void record(String action, String resourceType, String resourceId, boolean success,
                       String errorCode, Map<String, Object> before, Map<String, Object> after) {
        try {
            AuditLog entry = new AuditLog(action, resourceType, resourceId);
            applyActor(entry);
            applyRequestMetadata(entry);
            entry.setSuccess(success);
            entry.setErrorCode(errorCode);
            entry.setBeforeState(toJson(before));
            entry.setAfterState(toJson(after));
            writer.write(entry);
        } catch (RuntimeException ex) {
            // Never let auditing break the operation it observes (spec section 4.6).
            log.error("Could not write the audit entry {} for {} {}", action, resourceType, resourceId, ex);
        }
    }

    /** Records an authentication event for a username that may not belong to an account. */
    public void recordAuthEvent(String action, String username, boolean success, String errorCode) {
        try {
            AuditLog entry = new AuditLog(action, "AUTH", null);
            entry.setActorUsername(username);
            adminUsers.findByUsername(username).ifPresent(entry::setActor);
            applyRequestMetadata(entry);
            entry.setSuccess(success);
            entry.setErrorCode(errorCode);
            writer.write(entry);
        } catch (RuntimeException ex) {
            log.error("Could not write the authentication audit entry {} for '{}'", action, username, ex);
        }
    }

    /**
     * @param contentOnly true for callers holding only AUDIT_READ_CONTENT; the restriction is applied as
     *                    a filter, not by trimming the result afterwards
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(Long actorId, String action, String resourceType,
                                                 Instant from, Instant to, boolean contentOnly,
                                                 Pageable pageable) {
        Collection<String> resourceTypes = resolveResourceTypes(resourceType, contentOnly);
        Page<AuditLog> page = auditLogs.search(actorId, action, resourceTypes, from, to, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    @Transactional(readOnly = true)
    public AuditLogResponse findById(long id, boolean contentOnly) {
        AuditLog entry = auditLogs.findById(id)
                .orElseThrow(() -> new NotFoundException("AUDIT_ENTRY_NOT_FOUND",
                        "Der Audit-Eintrag wurde nicht gefunden."));
        if (contentOnly && !CONTENT_RESOURCE_TYPES.contains(entry.getResourceType())) {
            // Same answer as for a non-existent entry: the restricted view must not reveal that an
            // entry exists at all.
            throw new NotFoundException("AUDIT_ENTRY_NOT_FOUND",
                    "Der Audit-Eintrag wurde nicht gefunden.");
        }
        return toResponse(entry);
    }

    private static Collection<String> resolveResourceTypes(String requested, boolean contentOnly) {
        if (!contentOnly) {
            return requested == null || requested.isBlank() ? null : List.of(requested);
        }
        if (requested == null || requested.isBlank()) {
            return CONTENT_RESOURCE_TYPES;
        }
        // A content-only caller asking for USER gets an empty result, never a wider one.
        return CONTENT_RESOURCE_TYPES.contains(requested) ? List.of(requested) : List.of("__NONE__");
    }

    private void applyActor(AuditLog entry) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CampusUserDetails principal)) {
            return;
        }
        entry.setActorUsername(principal.getUsername());
        // Denormalised username plus a reference that survives as NULL when the account is deleted (D-9).
        adminUsers.findById(principal.getUserId()).ifPresent(entry::setActor);
    }

    private void applyRequestMetadata(AuditLog entry) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        entry.setIpAddress(clientIp(request));
        entry.setUserAgent(truncate(request.getHeader(HttpHeaders.USER_AGENT), 255));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // The frontend container proxies every call, so the first entry is the real client.
            return truncate(forwarded.split(",")[0].trim(), 45);
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private String toJson(Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(mask(state));
        } catch (RuntimeException ex) {
            log.warn("Could not serialise an audit state, storing a placeholder instead", ex);
            return "{\"error\":\"not serialisable\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (RuntimeException ex) {
            log.warn("Could not read a stored audit state", ex);
            return null;
        }
    }

    /** Masks recursively: a blocked field is hidden wherever it sits in the structure. */
    private static Object mask(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> masked = new LinkedHashMap<>();
            map.forEach((key, entry) -> {
                String name = String.valueOf(key);
                masked.put(name, isMasked(name) ? MASK : mask(entry));
            });
            return masked;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(AuditService::mask).toList();
        }
        return value;
    }

    private static boolean isMasked(String fieldName) {
        return MASKED_FIELDS.contains(fieldName.replace("_", "").toLowerCase());
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private AuditLogResponse toResponse(AuditLog entry) {
        AdminUser actor = entry.getActor();
        return new AuditLogResponse(
                entry.getId(),
                actor == null ? null : actor.getId(),
                entry.getActorUsername(),
                entry.getAction(),
                entry.getResourceType(),
                entry.getResourceId(),
                fromJson(entry.getBeforeState()),
                fromJson(entry.getAfterState()),
                entry.getIpAddress(),
                entry.getUserAgent(),
                entry.isSuccess(),
                entry.getErrorCode(),
                entry.getCreatedAt());
    }
}
