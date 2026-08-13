package de.tudarmstadt.campus.admin.rbac.service;

import de.tudarmstadt.campus.admin.audit.AuditContext;
import de.tudarmstadt.campus.admin.audit.Audited;
import de.tudarmstadt.campus.admin.common.exception.BadRequestException;
import de.tudarmstadt.campus.admin.common.exception.ConflictException;
import de.tudarmstadt.campus.admin.common.exception.ForbiddenException;
import de.tudarmstadt.campus.admin.common.exception.NotFoundException;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.domain.UserRole;
import de.tudarmstadt.campus.admin.rbac.repository.RoleGrantRepository;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.rbac.repository.UserRoleRepository;
import de.tudarmstadt.campus.admin.security.TokenVersionService;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Assigning and revoking roles — the central RBAC administration function of the prototype
 * (spec section 1.4, FA-07, FA-08).
 * <p>
 * Two rules run on every change: the caller may only hand out roles from their own grant set
 * ({@code role_grant}), and they may only touch accounts that stay inside that set. Both live in the
 * database rather than in code, which is what makes the model documentable and testable.
 */
@Service
public class RoleAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(RoleAssignmentService.class);

    private final AdminUserRepository adminUsers;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final RoleGrantRepository roleGrants;
    private final TokenVersionService tokenVersions;

    public RoleAssignmentService(AdminUserRepository adminUsers, RoleRepository roles,
                                 UserRoleRepository userRoles, RoleGrantRepository roleGrants,
                                 TokenVersionService tokenVersions) {
        this.adminUsers = adminUsers;
        this.roles = roles;
        this.userRoles = userRoles;
        this.roleGrants = roleGrants;
        this.tokenVersions = tokenVersions;
    }

    /** Roles the caller may hand out: the union over all of their own roles. */
    @Transactional(readOnly = true)
    public List<String> grantableRoleNames(long actorId) {
        List<String> actorRoles = roles.findRoleNamesByUserId(actorId);
        return actorRoles.isEmpty() ? List.of() : roleGrants.findGrantableRoleNames(actorRoles);
    }

    /**
     * Follows the sequence of spec section 1.4 step by step.
     */
    @Audited(action = "ROLE_ASSIGNED", resourceType = "USER", resourceId = "#targetUserId")
    @Transactional
    public void assign(long actorId, long targetUserId, String roleName) {
        AdminUser target = loadUser(targetUserId);
        Role role = loadRole(roleName);

        AuditContext.before("roles", roles.findRoleNamesByUserId(targetUserId));

        // The is_assignable flag stays as the mechanism, but no role carries it any more:
        // EXTERNE_PERSON became assignable when self-registration arrived (D-40). The check remains the
        // single place where a future non-assignable role would be refused.
        if (!role.isAssignable()) {
            throw new BadRequestException("ROLE_NOT_ASSIGNABLE",
                    "Die Rolle " + roleName + " kann keinem Konto zugewiesen werden.");
        }
        // INV-2: nobody changes their own permissions.
        assertNotSelf(actorId, targetUserId);
        assertGrantable(actorId, roleName);
        assertCanManage(actorId, target);

        if (userRoles.existsByUserIdAndRoleName(targetUserId, roleName)) {
            throw new ConflictException("ROLE_ALREADY_ASSIGNED",
                    "Das Konto besitzt die Rolle " + roleName + " bereits.");
        }

        AdminUser actor = loadUser(actorId);
        userRoles.save(new UserRole(target, role, actor));
        invalidateSessions(target);
        AuditContext.after("roles", roles.findRoleNamesByUserId(targetUserId));
        log.info("'{}' assigned role {} to '{}'", actor.getUsername(), roleName, target.getUsername());
    }

    /**
     * Revoking additionally guards INV-1 and INV-3: the system keeps at least one active administrator,
     * and every account keeps at least one role.
     */
    @Audited(action = "ROLE_REVOKED", resourceType = "USER", resourceId = "#targetUserId")
    @Transactional
    public void revoke(long actorId, long targetUserId, String roleName) {
        AdminUser target = loadUser(targetUserId);
        loadRole(roleName);

        AuditContext.before("roles", roles.findRoleNamesByUserId(targetUserId));

        assertNotSelf(actorId, targetUserId);
        assertGrantable(actorId, roleName);
        assertCanManage(actorId, target);

        UserRole assignment = userRoles.findByUserIdAndRoleName(targetUserId, roleName)
                .orElseThrow(() -> new NotFoundException("ROLE_NOT_ASSIGNED",
                        "Das Konto besitzt die Rolle " + roleName + " nicht."));

        // INV-3
        if (userRoles.countByUserId(targetUserId) <= 1) {
            throw new ConflictException("LAST_ROLE_PROTECTED",
                    "Die letzte Rolle eines Kontos kann nicht entzogen werden.");
        }
        // INV-1
        if (RoleCode.ADMIN.name().equals(roleName)) {
            assertAnotherActiveAdminRemains(targetUserId);
        }

        userRoles.delete(assignment);
        invalidateSessions(target);
        AuditContext.after("roles", roles.findRoleNamesByUserId(targetUserId));
        log.info("Revoked role {} from '{}'", roleName, target.getUsername());
    }

    /**
     * INV-1: at least one active account must hold ADMIN at all times. Also used by
     * {@code UserService} before a deletion or a deactivation.
     */
    @Transactional(readOnly = true)
    public void assertAnotherActiveAdminRemains(long excludedUserId) {
        if (adminUsers.countActiveUsersWithRoleExcluding(RoleCode.ADMIN.name(), excludedUserId) == 0) {
            throw new ConflictException("LAST_ADMIN_PROTECTED",
                    "Der letzte aktive Administrator kann nicht entfernt oder gesperrt werden.");
        }
    }

    /**
     * Footnote 1 of the matrix: an account may only be managed when all of its roles are inside the
     * caller's grant set. A PROJEKTLEITER therefore never reaches an ADMIN or a MAINTENANCE_DEV.
     */
    @Transactional(readOnly = true)
    public void assertCanManage(long actorId, AdminUser target) {
        List<String> targetRoles = roles.findRoleNamesByUserId(target.getId());
        List<String> grantable = grantableRoleNames(actorId);

        // A role-less account is not "manageable by everyone" — it is out of scope until it has roles.
        if (targetRoles.isEmpty() || !grantable.containsAll(targetRoles)) {
            throw new ForbiddenException("TARGET_OUT_OF_SCOPE",
                    "Dieses Konto liegt außerhalb Ihres Verwaltungsbereichs.");
        }
    }

    private void assertGrantable(long actorId, String roleName) {
        if (!grantableRoleNames(actorId).contains(roleName)) {
            throw new ForbiddenException("ROLE_NOT_GRANTABLE",
                    "Die Rolle " + roleName + " darf von Ihnen nicht vergeben werden.");
        }
    }

    /**
     * INV-2. The specification only names revoking one's own ADMIN role; forbidding every self change is
     * the simpler rule and leaves no gap (docs/DECISIONS.md D-26).
     */
    private static void assertNotSelf(long actorId, long targetUserId) {
        if (actorId == targetUserId) {
            throw new ConflictException("SELF_MODIFICATION_FORBIDDEN",
                    "Die eigenen Rollen können nicht selbst geändert werden.");
        }
    }

    /** INV-6: a permission change takes effect immediately, not when the token happens to expire. */
    private void invalidateSessions(AdminUser target) {
        target.setTokenVersion(target.getTokenVersion() + 1);
        adminUsers.save(target);
        tokenVersions.invalidate(target.getId());
    }

    private AdminUser loadUser(long userId) {
        return adminUsers.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "Das Konto wurde nicht gefunden."));
    }

    private Role loadRole(String roleName) {
        return roles.findByName(roleName)
                .orElseThrow(() -> new NotFoundException("ROLE_NOT_FOUND",
                        "Die Rolle " + roleName + " existiert nicht."));
    }
}
