package de.tudarmstadt.campus.admin.user.service;

import de.tudarmstadt.campus.admin.config.AppProperties;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.support.AbstractIntegrationTest;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance test for the startup part of phase 2: after boot there is exactly one usable administrator,
 * and a restart neither duplicates nor resurrects one (spec section 7.1).
 */
class AppInitializerServiceIT extends AbstractIntegrationTest {

    @Autowired
    private AppInitializerService initializer;

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppProperties properties;

    @Test
    void startupCreatedAnActiveAdministrator() {
        assertThat(adminUsers.countActiveUsersWithRole(RoleCode.ADMIN.name())).isEqualTo(1);

        AdminUser admin = adminUsers.findByUsername(properties.admin().username()).orElseThrow();
        assertThat(admin.isActive()).isTrue();
        assertThat(roles.findRoleNamesByUserId(admin.getId())).containsExactly(RoleCode.ADMIN.name());
    }

    @Test
    void thePasswordIsStoredAsABcryptHashOnly() {
        AdminUser admin = adminUsers.findByUsername(properties.admin().username()).orElseThrow();

        // FA-02: never the plaintext, and verifiable through the encoder.
        assertThat(admin.getPasswordHash()).isNotEqualTo(properties.admin().password());
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(properties.admin().password(), admin.getPasswordHash())).isTrue();
    }

    /** Idempotent: running again must not add a second administrator. */
    @Test
    void runningAgainChangesNothing() {
        long before = adminUsers.count();

        initializer.createInitialAdminIfMissing();

        assertThat(adminUsers.count()).isEqualTo(before);
        assertThat(adminUsers.countActiveUsersWithRole(RoleCode.ADMIN.name())).isEqualTo(1);
    }

    /**
     * The test profile configures a real password, so no forced change is due. The flag exists for the
     * docker and prod profiles, where the default password would otherwise survive.
     */
    @Test
    void doesNotForceAPasswordChangeWhenAPasswordWasConfigured() {
        AdminUser admin = adminUsers.findByUsername(properties.admin().username()).orElseThrow();

        assertThat(properties.admin().password()).isNotEqualTo("admin");
        assertThat(admin.isMustChangePassword()).isFalse();
    }

    @Test
    void theSeededRolesAreAvailableToTheInitializer() {
        // Guards the ordering: the initializer runs after Flyway, otherwise ADMIN would be missing.
        assertThat(roles.findByName(RoleCode.ADMIN.name())).isPresent();
    }
}
