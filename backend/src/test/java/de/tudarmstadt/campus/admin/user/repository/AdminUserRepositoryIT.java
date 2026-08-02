package de.tudarmstadt.campus.admin.user.repository;

import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.domain.UserRole;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.rbac.repository.UserRoleRepository;
import de.tudarmstadt.campus.admin.support.AbstractIntegrationTest;
import de.tudarmstadt.campus.admin.support.TestEntities;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class AdminUserRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAnAccountWithItsDefaults() {
        AdminUser saved = adminUsers.save(TestEntities.user("defaults_user"));
        entityManager.flush();
        entityManager.clear();

        AdminUser reloaded = adminUsers.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.getTokenVersion()).isZero();
        assertThat(reloaded.getRefreshVersion()).isZero();
        assertThat(reloaded.isMustChangePassword()).isFalse();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(reloaded.getLastLoginAt()).isNull();
    }

    @Test
    void findsByUsernameAndEmailIgnoringCase() {
        adminUsers.save(TestEntities.user("lookup_user"));
        entityManager.flush();

        assertThat(adminUsers.findByUsername("lookup_user")).isPresent();
        assertThat(adminUsers.findByUsername("LOOKUP_USER")).isEmpty();
        assertThat(adminUsers.findByEmailIgnoreCase("LOOKUP_USER@TU-DARMSTADT.DE")).isPresent();
        assertThat(adminUsers.existsByUsernameIgnoreCase("Lookup_User")).isTrue();
        assertThat(adminUsers.existsByEmailIgnoreCaseAndIdNot("lookup_user@tu-darmstadt.de", -1L)).isTrue();
    }

    @Test
    void rejectsADuplicateUsername() {
        adminUsers.save(TestEntities.user("duplicate_user"));
        entityManager.flush();

        AdminUser clash = TestEntities.user("duplicate_user");
        clash.setEmail("someone.else@tu-darmstadt.de");

        assertThatThrownBy(() -> {
            adminUsers.save(clash);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    /** INV-1 needs to know how many active accounts still hold a role before letting one go. */
    @Test
    void countsOnlyActiveHoldersOfARole() {
        Role role = roles.save(TestEntities.role("COUNT_TEST_ROLE"));
        AdminUser active = adminUsers.save(TestEntities.user("count_active"));
        AdminUser alsoActive = adminUsers.save(TestEntities.user("count_active_two"));
        AdminUser disabled = TestEntities.user("count_disabled");
        disabled.setActive(false);
        disabled = adminUsers.save(disabled);

        userRoles.save(new UserRole(active, role, null));
        userRoles.save(new UserRole(alsoActive, role, null));
        userRoles.save(new UserRole(disabled, role, null));
        entityManager.flush();

        assertThat(adminUsers.countActiveUsersWithRole("COUNT_TEST_ROLE")).isEqualTo(2);
        assertThat(adminUsers.countActiveUsersWithRoleExcluding("COUNT_TEST_ROLE", active.getId()))
                .isEqualTo(1);
        assertThat(adminUsers.countActiveUsersWithRole("NOBODY_HAS_THIS")).isZero();
    }

    @Test
    void tracksTheTwoTokenCountersIndependently() {
        AdminUser user = adminUsers.save(TestEntities.user("counter_user"));
        entityManager.flush();

        // A role change invalidates access tokens but must leave refresh tokens usable (D-3).
        user.setTokenVersion(user.getTokenVersion() + 1);
        adminUsers.save(user);
        entityManager.flush();
        entityManager.clear();

        AdminUser reloaded = adminUsers.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getTokenVersion()).isEqualTo(1);
        assertThat(reloaded.getRefreshVersion()).isZero();
    }
}
