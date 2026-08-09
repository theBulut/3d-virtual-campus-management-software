package de.tudarmstadt.campus.admin.user.service;

import de.tudarmstadt.campus.admin.audit.AuditContext;
import de.tudarmstadt.campus.admin.audit.Audited;
import de.tudarmstadt.campus.admin.common.dto.PageResponse;
import de.tudarmstadt.campus.admin.common.exception.BadRequestException;
import de.tudarmstadt.campus.admin.common.exception.ConflictException;
import de.tudarmstadt.campus.admin.common.exception.ForbiddenException;
import de.tudarmstadt.campus.admin.common.exception.NotFoundException;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.domain.UserRole;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.rbac.repository.UserRoleRepository;
import de.tudarmstadt.campus.admin.rbac.service.RoleAssignmentService;
import de.tudarmstadt.campus.admin.security.TokenVersionService;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import de.tudarmstadt.campus.admin.user.web.dto.CreateUserRequest;
import de.tudarmstadt.campus.admin.user.web.dto.CreatedUserResponse;
import de.tudarmstadt.campus.admin.user.web.dto.UpdateUserRequest;
import de.tudarmstadt.campus.admin.user.web.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Account administration (spec section 5.2, FA-09).
 * <p>
 * Every write against a foreign account runs through
 * {@link RoleAssignmentService#assertCanManage(long, AdminUser)}: a PROJEKTLEITER administers only
 * accounts whose roles lie inside their own grant set, which is what makes their user management
 * "restricted" in the sense of footnote 1 of the matrix.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final AdminUserRepository adminUsers;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final RoleAssignmentService roleAssignments;
    private final PasswordService passwordService;
    private final TokenVersionService tokenVersions;

    public UserService(AdminUserRepository adminUsers, RoleRepository roles,
                       UserRoleRepository userRoles, RoleAssignmentService roleAssignments,
                       PasswordService passwordService, TokenVersionService tokenVersions) {
        this.adminUsers = adminUsers;
        this.roles = roles;
        this.userRoles = userRoles;
        this.roleAssignments = roleAssignments;
        this.passwordService = passwordService;
        this.tokenVersions = tokenVersions;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String query, String roleName, Boolean active,
                                             Pageable pageable) {
        Page<AdminUser> page = adminUsers.search(emptyToNull(query), emptyToNull(roleName), active, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(long userId) {
        return toResponse(loadUser(userId));
    }

    @Transactional(readOnly = true)
    public List<String> rolesOf(long userId) {
        loadUser(userId);
        return roles.findRoleNamesByUserId(userId);
    }

    /**
     * Creates the account together with its roles. Every requested role is checked against the caller's
     * grant set, so nobody can create an account more powerful than themselves.
     */
    @Audited(action = "USER_CREATED", resourceType = "USER")
    @Transactional
    public CreatedUserResponse create(long actorId, CreateUserRequest request) {
        if (adminUsers.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException("USERNAME_ALREADY_USED",
                    "Der Benutzername " + request.username() + " ist bereits vergeben.");
        }
        if (adminUsers.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("EMAIL_ALREADY_USED",
                    "Diese E-Mail-Adresse wird bereits verwendet.");
        }

        List<Role> requestedRoles = resolveGrantableRoles(actorId, request.roles());

        String temporaryPassword = passwordService.generateTemporaryPassword();
        AdminUser user = new AdminUser();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim());
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setOrganisation(trimToNull(request.organisation()));
        user.setPasswordHash(passwordService.hash(temporaryPassword));
        user.setActive(true);
        // The generated password is a transport secret, not a chosen one.
        user.setMustChangePassword(true);
        user.setCreatedBy(loadUser(actorId));
        AdminUser saved = adminUsers.save(user);

        AdminUser actor = loadUser(actorId);
        requestedRoles.forEach(role -> userRoles.save(new UserRole(saved, role, actor)));

        // The id only exists after the insert, so it cannot come from the annotation.
        AuditContext.resourceId(saved.getId());
        AuditContext.after("username", saved.getUsername());
        AuditContext.after("email", saved.getEmail());
        AuditContext.after("roles", request.roles());
        log.info("'{}' created account '{}' with roles {}", actor.getUsername(), saved.getUsername(),
                request.roles());
        return new CreatedUserResponse(toResponse(saved), temporaryPassword);
    }

    @Audited(action = "USER_UPDATED", resourceType = "USER", resourceId = "#userId")
    @Transactional
    public UserResponse update(long actorId, long userId, UpdateUserRequest request) {
        AdminUser user = loadUser(userId);
        roleAssignments.assertCanManage(actorId, user);

        AuditContext.before("email", user.getEmail());
        AuditContext.before("firstName", user.getFirstName());
        AuditContext.before("lastName", user.getLastName());
        AuditContext.before("organisation", user.getOrganisation());

        if (adminUsers.existsByEmailIgnoreCaseAndIdNot(request.email(), userId)) {
            throw new ConflictException("EMAIL_ALREADY_USED",
                    "Diese E-Mail-Adresse wird bereits von einem anderen Konto verwendet.");
        }

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(request.email().trim());
        user.setOrganisation(trimToNull(request.organisation()));
        AdminUser saved = adminUsers.save(user);
        AuditContext.after("email", saved.getEmail());
        AuditContext.after("firstName", saved.getFirstName());
        AuditContext.after("lastName", saved.getLastName());
        AuditContext.after("organisation", saved.getOrganisation());
        return toResponse(saved);
    }

    /**
     * Locking an account ends its sessions at once and is guarded by INV-1 and INV-2.
     */
    @Audited(action = "USER_DEACTIVATED", resourceType = "USER", resourceId = "#userId")
    @Transactional
    public UserResponse changeStatus(long actorId, long userId, boolean active) {
        assertNotSelf(actorId, userId, "Das eigene Konto kann nicht gesperrt werden.");
        AdminUser user = loadUser(userId);
        roleAssignments.assertCanManage(actorId, user);

        // Locking and unlocking are separate actions in the catalogue of spec section 4.6.
        AuditContext.action(active ? "USER_ACTIVATED" : "USER_DEACTIVATED");
        AuditContext.before("active", user.isActive());
        AuditContext.after("active", active);

        if (!active && user.isActive() && hasRole(userId, RoleCode.ADMIN)) {
            roleAssignments.assertAnotherActiveAdminRemains(userId);
        }

        user.setActive(active);
        if (!active) {
            // Both counters: a locked account keeps neither access nor refresh tokens (D-3).
            user.setTokenVersion(user.getTokenVersion() + 1);
            user.setRefreshVersion(user.getRefreshVersion() + 1);
        }
        AdminUser saved = adminUsers.save(user);
        tokenVersions.invalidate(userId);
        log.info("Set account '{}' to active={}", saved.getUsername(), active);
        return toResponse(saved);
    }

    @Audited(action = "USER_DELETED", resourceType = "USER", resourceId = "#userId")
    @Transactional
    public void delete(long actorId, long userId) {
        assertNotSelf(actorId, userId, "Das eigene Konto kann nicht gelöscht werden.");
        AdminUser user = loadUser(userId);
        roleAssignments.assertCanManage(actorId, user);

        if (user.isActive() && hasRole(userId, RoleCode.ADMIN)) {
            roleAssignments.assertAnotherActiveAdminRemains(userId);
        }

        AuditContext.before("username", user.getUsername());
        AuditContext.before("roles", roles.findRoleNamesByUserId(userId));
        adminUsers.delete(user);
        tokenVersions.invalidate(userId);
        log.info("Deleted account '{}'", user.getUsername());
    }

    /**
     * Sets a new generated password and returns it once. Ends every session of the account, because the
     * old password is gone (spec section 5.2).
     */
    @Audited(action = "PASSWORD_RESET", resourceType = "USER", resourceId = "#userId")
    @Transactional
    public String resetPassword(long actorId, long userId) {
        AdminUser user = loadUser(userId);
        roleAssignments.assertCanManage(actorId, user);

        String temporaryPassword = passwordService.generateTemporaryPassword();
        user.setPasswordHash(passwordService.hash(temporaryPassword));
        user.setMustChangePassword(true);
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setRefreshVersion(user.getRefreshVersion() + 1);
        adminUsers.save(user);
        tokenVersions.invalidate(userId);

        log.info("Reset the password of '{}'", user.getUsername());
        return temporaryPassword;
    }

    private List<Role> resolveGrantableRoles(long actorId, List<String> roleNames) {
        List<String> grantable = roleAssignments.grantableRoleNames(actorId);
        return roleNames.stream()
                .distinct()
                .map(name -> {
                    Role role = roles.findByName(name)
                            .orElseThrow(() -> new NotFoundException("ROLE_NOT_FOUND",
                                    "Die Rolle " + name + " existiert nicht."));
                    if (!role.isAssignable()) {
                        throw new BadRequestException("ROLE_NOT_ASSIGNABLE",
                                "Die Rolle " + name + " kann keinem Konto zugewiesen werden.");
                    }
                    if (!grantable.contains(name)) {
                        throw new ForbiddenException("ROLE_NOT_GRANTABLE",
                                "Die Rolle " + name + " darf von Ihnen nicht vergeben werden.");
                    }
                    return role;
                })
                .toList();
    }

    private boolean hasRole(long userId, RoleCode roleCode) {
        return userRoles.existsByUserIdAndRoleName(userId, roleCode.name());
    }

    private static void assertNotSelf(long actorId, long userId, String message) {
        if (actorId == userId) {
            throw new ConflictException("SELF_MODIFICATION_FORBIDDEN", message);
        }
    }

    private AdminUser loadUser(long userId) {
        return adminUsers.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "Das Konto wurde nicht gefunden."));
    }

    private UserResponse toResponse(AdminUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.getOrganisation(),
                user.isActive(), user.isMustChangePassword(),
                user.getLastLoginAt(), user.getCreatedAt(),
                roles.findRoleNamesByUserId(user.getId()));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
