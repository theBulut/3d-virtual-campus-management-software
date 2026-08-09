package de.tudarmstadt.campus.admin.rbac.service;

import de.tudarmstadt.campus.admin.common.exception.NotFoundException;
import de.tudarmstadt.campus.admin.rbac.domain.Permission;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.repository.PermissionRepository;
import de.tudarmstadt.campus.admin.rbac.repository.RoleGrantRepository;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.rbac.repository.UserRoleRepository;
import de.tudarmstadt.campus.admin.rbac.web.dto.PermissionResponse;
import de.tudarmstadt.campus.admin.rbac.web.dto.RoleMatrixResponse;
import de.tudarmstadt.campus.admin.rbac.web.dto.RoleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read access to the role model (spec section 5.3). Writing roles is deliberately absent — roles are
 * hard-coded (E-1), {@code ROLE_MANAGE} exists but no endpoint uses it.
 */
@Service
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RoleGrantRepository roleGrants;
    private final UserRoleRepository userRoles;

    public RoleService(RoleRepository roles, PermissionRepository permissions,
                       RoleGrantRepository roleGrants, UserRoleRepository userRoles) {
        this.roles = roles;
        this.permissions = permissions;
        this.roleGrants = roleGrants;
        this.userRoles = userRoles;
    }

    public List<RoleResponse> findAll() {
        return roles.findAllWithPermissions().stream()
                .sorted(Comparator.comparingInt(Role::getSortOrder))
                .map(role -> toResponse(role, userRoles.countByRoleName(role.getName())))
                .toList();
    }

    public RoleResponse findByName(String name) {
        Role role = roles.findByNameWithPermissions(name)
                .orElseThrow(() -> new NotFoundException("ROLE_NOT_FOUND",
                        "Die Rolle " + name + " existiert nicht."));
        return toResponse(role, userRoles.countByRoleName(role.getName()));
    }

    public List<PermissionResponse> findAllPermissions() {
        return permissions.findAllByOrderByResourceAscCodeAsc().stream()
                .map(RoleService::toResponse)
                .toList();
    }

    private static PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(permission.getCode(), permission.getResource(),
                permission.getAction(), permission.getDescription());
    }

    /**
     * The whole model in one response. Read from the database rather than from {@code RoleCatalog}, so
     * the answer reflects what is actually enforced.
     */
    public RoleMatrixResponse matrix() {
        List<Role> allRoles = roles.findAllWithPermissions().stream()
                .sorted(Comparator.comparingInt(Role::getSortOrder))
                .toList();

        Map<String, List<String>> assignments = new LinkedHashMap<>();
        Map<String, List<String>> grants = new LinkedHashMap<>();
        for (Role role : allRoles) {
            assignments.put(role.getName(), permissionCodes(role));
            grants.put(role.getName(), roleGrants.findGrantableRoleNames(List.of(role.getName())));
        }

        return new RoleMatrixResponse(
                allRoles.stream()
                        .map(role -> new RoleMatrixResponse.RoleSummary(role.getName(),
                                role.getDisplayName(), role.isAssignable(), role.getSortOrder()))
                        .toList(),
                findAllPermissions(),
                assignments,
                grants);
    }

    private static RoleResponse toResponse(Role role, long userCount) {
        return new RoleResponse(role.getName(), role.getDisplayName(), role.getDescription(),
                role.isSystem(), role.isAssignable(), role.getSortOrder(), userCount,
                permissionCodes(role));
    }

    private static List<String> permissionCodes(Role role) {
        return role.getPermissions().stream().map(Permission::getCode).sorted().toList();
    }
}
