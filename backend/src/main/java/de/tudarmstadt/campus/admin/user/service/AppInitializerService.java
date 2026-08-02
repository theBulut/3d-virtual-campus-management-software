package de.tudarmstadt.campus.admin.user.service;

import de.tudarmstadt.campus.admin.config.AppProperties;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.domain.UserRole;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.rbac.repository.UserRoleRepository;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

/**
 * Creates the initial administrator on startup when no active {@code ADMIN} exists (spec section 7.1).
 * <p>
 * Runs as an {@link ApplicationRunner} so Flyway has already applied the RBAC seed of V4.
 */
@Service
public class AppInitializerService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppInitializerService.class);

    private static final String DEFAULT_PASSWORD = "admin";
    private static final String DEV_PROFILE = "dev";

    private final AdminUserRepository adminUsers;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Environment environment;

    public AppInitializerService(AdminUserRepository adminUsers, RoleRepository roles,
                                 UserRoleRepository userRoles, PasswordEncoder passwordEncoder,
                                 AppProperties properties, Environment environment) {
        this.adminUsers = adminUsers;
        this.roles = roles;
        this.userRoles = userRoles;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        createInitialAdminIfMissing();
    }

    /**
     * Idempotent: as soon as one active account holds ADMIN, nothing happens. That keeps a restart from
     * resurrecting an administrator that was deliberately removed.
     */
    @Transactional
    public void createInitialAdminIfMissing() {
        if (adminUsers.countActiveUsersWithRole(RoleCode.ADMIN.name()) > 0) {
            return;
        }

        AppProperties.InitialAdmin configured = properties.admin();
        if (adminUsers.existsByUsernameIgnoreCase(configured.username())) {
            log.warn("No active ADMIN exists, but the username '{}' is already taken. "
                            + "Assign the ADMIN role manually or configure a different CAMPUS_ADMIN_USERNAME.",
                    configured.username());
            return;
        }

        Role adminRole = roles.findByName(RoleCode.ADMIN.name())
                .orElseThrow(() -> new IllegalStateException(
                        "Role ADMIN is missing. The RBAC seed of V4__seed_rbac.sql has not been applied."));

        boolean usesDefaultPassword = DEFAULT_PASSWORD.equals(configured.password());

        AdminUser admin = new AdminUser();
        admin.setUsername(configured.username());
        admin.setEmail(configured.email());
        admin.setPasswordHash(passwordEncoder.encode(configured.password()));
        admin.setFirstName("Campus");
        admin.setLastName("Administrator");
        admin.setActive(true);
        // Outside dev a default password must be replaced before the account is usable (spec 7.1).
        admin.setMustChangePassword(usesDefaultPassword && !isDevProfileActive());
        admin = adminUsers.save(admin);

        userRoles.save(new UserRole(admin, adminRole, null));

        log.info("Created the initial administrator '{}'.", admin.getUsername());
        if (usesDefaultPassword) {
            log.warn("The initial administrator still uses the default password. "
                    + "Set CAMPUS_ADMIN_PASSWORD before exposing this instance.");
        }
    }

    private boolean isDevProfileActive() {
        return Arrays.asList(environment.getActiveProfiles()).contains(DEV_PROFILE);
    }
}
