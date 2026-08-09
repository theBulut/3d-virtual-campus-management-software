package de.tudarmstadt.campus.admin.user.web;

import de.tudarmstadt.campus.admin.common.dto.PageResponse;
import de.tudarmstadt.campus.admin.rbac.service.RoleAssignmentService;
import de.tudarmstadt.campus.admin.security.CampusUserDetails;
import de.tudarmstadt.campus.admin.user.service.UserService;
import de.tudarmstadt.campus.admin.user.web.dto.AssignRoleRequest;
import de.tudarmstadt.campus.admin.user.web.dto.CreateUserRequest;
import de.tudarmstadt.campus.admin.user.web.dto.CreatedUserResponse;
import de.tudarmstadt.campus.admin.user.web.dto.TemporaryPasswordResponse;
import de.tudarmstadt.campus.admin.user.web.dto.UpdateStatusRequest;
import de.tudarmstadt.campus.admin.user.web.dto.UpdateUserRequest;
import de.tudarmstadt.campus.admin.user.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Account and role administration (spec section 5.2).
 * <p>
 * Every method carries a {@code @PreAuthorize} on a permission authority; {@code EndpointSecurityTest}
 * fails the build if one is missing. The authority alone is not the whole rule — which accounts a caller
 * may reach is decided by the grant set inside the services.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Nutzerverwaltung", description = "Konten anlegen, bearbeiten, sperren und Rollen vergeben")
public class UserController {

    private final UserService userService;
    private final RoleAssignmentService roleAssignments;

    public UserController(UserService userService, RoleAssignmentService roleAssignments) {
        this.userService = userService;
        this.roleAssignments = roleAssignments;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Konten auflisten",
            description = "Filter: q (Name, Benutzername, E-Mail), role, active.")
    public PageResponse<UserResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) String role,
                                           @RequestParam(required = false) Boolean active,
                                           @PageableDefault(size = 20) Pageable pageable) {
        return userService.search(q, role, active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Konto lesen")
    public UserResponse findById(@PathVariable long id) {
        return userService.findById(id);
    }

    /**
     * The generated password is part of the response and appears exactly once — the prototype has no
     * mail delivery (spec section 8).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Konto anlegen",
            description = "Jede angegebene Rolle wird gegen die Vergabemenge des Aufrufers geprüft. "
                    + "Das erzeugte Passwort wird einmalig zurückgegeben.")
    public CreatedUserResponse create(@AuthenticationPrincipal CampusUserDetails principal,
                                      @Valid @RequestBody CreateUserRequest request) {
        return userService.create(principal.getUserId(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Stammdaten ändern",
            description = "Nur für Konten innerhalb des eigenen Verwaltungsbereichs.")
    public UserResponse update(@AuthenticationPrincipal CampusUserDetails principal,
                               @PathVariable long id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(principal.getUserId(), id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_ACTIVATE')")
    @Operation(summary = "Konto sperren oder entsperren",
            description = "Sperren beendet alle Sitzungen des Kontos. Der letzte aktive Administrator "
                    + "kann nicht gesperrt werden.")
    public UserResponse changeStatus(@AuthenticationPrincipal CampusUserDetails principal,
                                     @PathVariable long id,
                                     @Valid @RequestBody UpdateStatusRequest request) {
        return userService.changeStatus(principal.getUserId(), id, request.active());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    @Operation(summary = "Konto löschen",
            description = "Erzeugte Inhalte und Audit-Einträge bleiben erhalten, verlieren aber ihren Urheber.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CampusUserDetails principal,
                                       @PathVariable long id) {
        userService.delete(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/password-reset")
    @PreAuthorize("hasAuthority('USER_PASSWORD_RESET')")
    @Operation(summary = "Passwort zurücksetzen",
            description = "Erzeugt ein temporäres Passwort, gibt es einmalig zurück und beendet alle "
                    + "Sitzungen des Kontos.")
    public TemporaryPasswordResponse resetPassword(@AuthenticationPrincipal CampusUserDetails principal,
                                                   @PathVariable long id) {
        return new TemporaryPasswordResponse(userService.resetPassword(principal.getUserId(), id));
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Rollen eines Kontos")
    public List<String> rolesOf(@PathVariable long id) {
        return userService.rolesOf(id);
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    @Operation(summary = "Rolle zuweisen",
            description = "Nur Rollen aus der eigenen Vergabemenge. Die Zuweisung entwertet sofort alle "
                    + "Access-Tokens des Zielkontos.")
    public UserResponse assignRole(@AuthenticationPrincipal CampusUserDetails principal,
                                   @PathVariable long id,
                                   @Valid @RequestBody AssignRoleRequest request) {
        roleAssignments.assign(principal.getUserId(), id, request.roleName());
        return userService.findById(id);
    }

    @DeleteMapping("/{id}/roles/{roleName}")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    @Operation(summary = "Rolle entziehen",
            description = "Die letzte Rolle eines Kontos und die letzte aktive ADMIN-Rolle im System "
                    + "können nicht entzogen werden.")
    public UserResponse revokeRole(@AuthenticationPrincipal CampusUserDetails principal,
                                   @PathVariable long id,
                                   @PathVariable String roleName) {
        roleAssignments.revoke(principal.getUserId(), id, roleName);
        return userService.findById(id);
    }

    /** Fills the role dropdown in the interface; the same set the server enforces on assignment. */
    @GetMapping("/me/grantable-roles")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    @Operation(summary = "Vergebbare Rollen des Aufrufers")
    public List<String> grantableRoles(@AuthenticationPrincipal CampusUserDetails principal) {
        return roleAssignments.grantableRoleNames(principal.getUserId());
    }
}
