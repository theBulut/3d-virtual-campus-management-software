package de.tudarmstadt.campus.admin.user.service;

import de.tudarmstadt.campus.admin.audit.service.AuditService;
import de.tudarmstadt.campus.admin.common.exception.ConflictException;
import de.tudarmstadt.campus.admin.config.AppProperties;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.domain.UserRole;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.rbac.repository.UserRoleRepository;
import de.tudarmstadt.campus.admin.security.RateLimiter;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import de.tudarmstadt.campus.admin.user.web.dto.RegisterRequest;
import de.tudarmstadt.campus.admin.user.web.dto.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-registration of players (FA-23).
 * <p>
 * Everyone who registers receives exactly the role {@code EXTERNE_PERSON} — the client cannot ask for
 * anything else, because the request has no roles field. Only an administration promotes from there, and
 * the role stays in place when it does: an account is the union of its roles, so a project lead remains
 * a player (docs/DECISIONS.md D-40).
 * <p>
 * Unlike {@link UserService#create}, which hands out a temporary password, the account chooses its own
 * password here. {@code must_change_password} therefore stays false — there is nothing to change.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final AdminUserRepository adminUsers;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordService passwordService;
    private final AuthService authService;
    private final AuditService auditService;
    private final RateLimiter rateLimiter;
    private final AppProperties.RateLimits limits;

    public RegistrationService(AdminUserRepository adminUsers, RoleRepository roles,
                               UserRoleRepository userRoles, PasswordService passwordService,
                               AuthService authService, AuditService auditService,
                               RateLimiter rateLimiter, AppProperties properties) {
        this.adminUsers = adminUsers;
        this.roles = roles;
        this.userRoles = userRoles;
        this.passwordService = passwordService;
        this.authService = authService;
        this.auditService = auditService;
        this.rateLimiter = rateLimiter;
        this.limits = properties.rateLimit();
    }

    /**
     * Creates the account and signs it in straight away, so the client can go to the game without a
     * second form.
     *
     * @param clientAddress who is registering, for the rate limit — the endpoint is open to everyone
     */
    @Transactional
    public TokenResponse register(RegisterRequest request, String clientAddress) {
        rateLimiter.check("register", clientAddress, limits.registrationsPerAddress(),
                limits.registrationWindow());

        String username = request.username().trim();
        String email = request.email().trim();

        if (adminUsers.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("USERNAME_ALREADY_USED",
                    "Der Benutzername " + username + " ist bereits vergeben.");
        }
        if (adminUsers.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_ALREADY_USED",
                    "Diese E-Mail-Adresse wird bereits verwendet.");
        }

        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPasswordHash(passwordService.hash(request.password()));
        user.setActive(true);
        user.setMustChangePassword(false);
        // No createdBy: nobody created this account but the person behind it.
        AdminUser saved = adminUsers.save(user);

        Role externalRole = roles.findByName(RoleCode.EXTERNE_PERSON.name())
                .orElseThrow(() -> new IllegalStateException(
                        "Rolle EXTERNE_PERSON fehlt in der Datenbank"));
        userRoles.save(new UserRole(saved, externalRole, null));

        // recordAuthEvent rather than @Audited: nobody is signed in during a registration, so the aspect
        // would write an entry without an actor. This way the new account is named in the trail.
        auditService.recordAuthEvent("USER_REGISTERED", saved.getUsername(), true, null);
        log.info("New account '{}' registered itself", saved.getUsername());

        // Signing in through the regular path keeps one implementation of token issuing, last-login and
        // the LOGIN_SUCCESS entry.
        return authService.login(username, request.password());
    }
}
