package de.tudarmstadt.campus.admin.audit.web;

import de.tudarmstadt.campus.admin.audit.service.AuditService;
import de.tudarmstadt.campus.admin.audit.web.dto.AuditLogResponse;
import de.tudarmstadt.campus.admin.common.dto.PageResponse;
import de.tudarmstadt.campus.admin.rbac.PermissionCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Read access to the audit log (spec section 5.5).
 * <p>
 * Two permissions open this endpoint, and they see different things: {@code AUDIT_READ} the whole log,
 * {@code AUDIT_READ_CONTENT} only entries about content resources. The narrowing is a filter inside the
 * service, not a trimmed result — a restricted caller never learns that other entries exist.
 */
@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit-Log", description = "Revisionssichere Protokollierung aller schreibenden Zugriffe")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('AUDIT_READ', 'AUDIT_READ_CONTENT')")
    @Operation(summary = "Audit-Log durchsuchen",
            description = "Filter: actorId, action, resourceType, from, to. Wer nur AUDIT_READ_CONTENT "
                    + "besitzt, sieht ausschließlich Einträge zu POI, BUILDING, CONSULTATION und MEDIA.")
    public PageResponse<AuditLogResponse> search(
            Authentication authentication,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return auditService.search(actorId, action, resourceType, from, to,
                isContentOnly(authentication), pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('AUDIT_READ', 'AUDIT_READ_CONTENT')")
    @Operation(summary = "Einzelner Eintrag inklusive Vorher- und Nachher-Zustand")
    public AuditLogResponse findById(Authentication authentication, @PathVariable long id) {
        return auditService.findById(id, isContentOnly(authentication));
    }

    /** Full access wins: a caller holding both permissions sees everything. */
    private static boolean isContentOnly(Authentication authentication) {
        boolean fullAccess = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PermissionCode.AUDIT_READ.name()::equals);
        return !fullAccess;
    }
}
