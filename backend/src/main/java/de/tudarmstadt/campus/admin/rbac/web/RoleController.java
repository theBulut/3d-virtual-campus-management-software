package de.tudarmstadt.campus.admin.rbac.web;

import de.tudarmstadt.campus.admin.rbac.service.RoleService;
import de.tudarmstadt.campus.admin.rbac.web.dto.RoleMatrixResponse;
import de.tudarmstadt.campus.admin.rbac.web.dto.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read access to the role model (spec section 5.3).
 * <p>
 * Creating, changing and deleting roles is deliberately absent: the six roles are hard-coded (E-1). The
 * permission {@code ROLE_MANAGE} and the {@code role_permission} relation already exist, so adding that
 * later is an endpoint plus a matrix editor — not a redesign.
 */
@RestController
@RequestMapping("/api/roles")
@Tag(name = "Rollen und Berechtigungen", description = "Rollenkatalog und Berechtigungsmatrix")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "Alle Rollen", description = "Inklusive Anzahl der zugeordneten Konten.")
    public List<RoleResponse> findAll() {
        return roleService.findAll();
    }

    /**
     * Mapped before {@code /{name}} so the literal path wins over the variable.
     */
    @GetMapping("/matrix")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "Vollständige Berechtigungsmatrix",
            description = "Rollen, Berechtigungen, Zuordnungen und Vergaberegeln in einem Dokument. "
                    + "Wird aus der Datenbank gelesen und zeigt damit das tatsächlich durchgesetzte Modell.")
    public RoleMatrixResponse matrix() {
        return roleService.matrix();
    }

    @GetMapping("/{name}")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "Rolle mit vollständiger Berechtigungsliste")
    public RoleResponse findByName(@PathVariable String name) {
        return roleService.findByName(name);
    }
}
