package de.tudarmstadt.campus.admin.rbac.web;

import de.tudarmstadt.campus.admin.rbac.service.RoleService;
import de.tudarmstadt.campus.admin.rbac.web.dto.PermissionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The permission catalogue (spec section 5.3). Its own controller because the base path differs from
 * {@code /api/roles}.
 */
@RestController
@RequestMapping("/api/permissions")
@Tag(name = "Rollen und Berechtigungen", description = "Rollenkatalog und Berechtigungsmatrix")
public class PermissionController {

    private final RoleService roleService;

    public PermissionController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "Berechtigungskatalog",
            description = "Gruppiert nach Ressource, wie in der Matrix-Ansicht dargestellt.")
    public Map<String, List<PermissionResponse>> findAllGroupedByResource() {
        return roleService.findAllPermissions().stream()
                .collect(Collectors.groupingBy(PermissionResponse::resource,
                        LinkedHashMap::new, Collectors.toList()));
    }
}
