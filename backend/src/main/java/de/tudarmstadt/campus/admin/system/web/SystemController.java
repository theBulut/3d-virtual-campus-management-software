package de.tudarmstadt.campus.admin.system.web;

import de.tudarmstadt.campus.admin.system.web.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "System", description = "Liveness and system information")
public class SystemController {

    /**
     * Liveness probe. One of the four deliberately unauthenticated endpoints and therefore without
     * {@code @PreAuthorize}; it is covered by the allowlist in {@code EndpointSecurityTest} (phase 4) and
     * by {@code SecurityConfig.PUBLIC_ENDPOINTS}.
     */
    @GetMapping("/health")
    @SecurityRequirements
    @Operation(summary = "Liveness probe", description = "Reachable without authentication.")
    public HealthResponse health() {
        return HealthResponse.up();
    }
}
