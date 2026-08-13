package de.tudarmstadt.campus.admin.rbac.repository;

import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.domain.UserRole;
import de.tudarmstadt.campus.admin.support.AbstractIntegrationTest;
import de.tudarmstadt.campus.admin.support.TestEntities;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query behaviour of the grant rules against the real seed of V4. Whether the seed matches the Java
 * catalogue is the job of {@code RoleCatalogConsistencyIT}; here it is about how the repository resolves
 * a caller's effective grant set (spec section 1.4).
 */
@Transactional
class RoleGrantRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private RoleGrantRepository roleGrants;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private EntityManager entityManager;

    @Test
    void adminMayGrantEveryRole() {
        assertThat(roleGrants.findGrantableRoleNames(List.of(RoleCode.ADMIN.name())))
                .containsExactly("ADMIN", "EXTERNE_PERSON", "MAINTENANCE_DEV", "PERSONAL",
                        "PROJEKTLEITER", "PROJEKTMITARBEITER");
    }

    @Test
    void projektleiterMayGrantTheContentRolesAndReachPlayers() {
        // EXTERNE_PERSON is in the set so a self-registered account is inside the scope of a project
        // lead at all — assertCanManage checks every role of the target (docs/DECISIONS.md D-40).
        assertThat(roleGrants.findGrantableRoleNames(List.of(RoleCode.PROJEKTLEITER.name())))
                .containsExactly("EXTERNE_PERSON", "PERSONAL", "PROJEKTMITARBEITER");
    }

    @Test
    void rolesWithoutGrantRightsGrantNothing() {
        assertThat(roleGrants.findGrantableRoleNames(
                List.of(RoleCode.PROJEKTMITARBEITER.name(), RoleCode.PERSONAL.name(),
                        RoleCode.MAINTENANCE_DEV.name())))
                .isEmpty();
    }

    /** The effective grant set of a user is the union over all of their roles. */
    @Test
    void grantSetsOfSeveralRolesAreUnioned() {
        assertThat(roleGrants.findGrantableRoleNames(
                List.of(RoleCode.PROJEKTLEITER.name(), RoleCode.MAINTENANCE_DEV.name())))
                .containsExactly("EXTERNE_PERSON", "PERSONAL", "PROJEKTMITARBEITER");

        assertThat(roleGrants.findGrantableRoleNames(
                List.of(RoleCode.ADMIN.name(), RoleCode.PROJEKTLEITER.name())))
                .hasSize(6)
                .doesNotHaveDuplicates();
    }

    @Test
    void answersTheGrantabilityQuestionDirectly() {
        assertThat(roleGrants.canGrant(List.of(RoleCode.PROJEKTLEITER.name()),
                RoleCode.PROJEKTMITARBEITER.name())).isTrue();
        // The check behind the ROLE_NOT_GRANTABLE error of scenario S-06.
        assertThat(roleGrants.canGrant(List.of(RoleCode.PROJEKTLEITER.name()),
                RoleCode.ADMIN.name())).isFalse();
        assertThat(roleGrants.canGrant(List.of(RoleCode.PERSONAL.name()),
                RoleCode.PERSONAL.name())).isFalse();
    }

    @Test
    void grantSetOfAnUnknownRoleIsEmpty() {
        assertThat(roleGrants.findGrantableRoleNames(List.of("DOES_NOT_EXIST"))).isEmpty();
        assertThat(roleGrants.canGrant(List.of("DOES_NOT_EXIST"), RoleCode.PERSONAL.name())).isFalse();
    }

    /**
     * Resolves what a concrete account may do — the input for the {@code perms} and {@code roles} claims
     * of the access token (spec section 4.1).
     */
    @Test
    void resolvesPermissionCodesAndRoleNamesOfAUser() {
        Role personal = roles.findByName(RoleCode.PERSONAL.name()).orElseThrow();
        AdminUser user = adminUsers.save(TestEntities.user("grant_resolution_user"));
        userRoles.save(new UserRole(user, personal, null));
        entityManager.flush();
        entityManager.clear();

        assertThat(roles.findRoleNamesByUserId(user.getId())).containsExactly("PERSONAL");
        assertThat(roles.findPermissionCodesByUserId(user.getId())).containsExactly(
                "BUILDING_READ_ALL", "BUILDING_READ_PUBLIC",
                "CONSULTATION_CREATE", "CONSULTATION_READ_ALL", "CONSULTATION_READ_PUBLIC",
                "CONSULTATION_UPDATE_OWN",
                "POI_READ_PUBLISHED", "PROFILE_UPDATE_OWN");
    }

    @Test
    void permissionCodesOfSeveralRolesAreUnionedWithoutDuplicates() {
        Role personal = roles.findByName(RoleCode.PERSONAL.name()).orElseThrow();
        Role mitarbeit = roles.findByName(RoleCode.PROJEKTMITARBEITER.name()).orElseThrow();
        AdminUser user = adminUsers.save(TestEntities.user("grant_multi_role_user"));
        userRoles.save(new UserRole(user, personal, null));
        userRoles.save(new UserRole(user, mitarbeit, null));
        entityManager.flush();
        entityManager.clear();

        // PROFILE_UPDATE_OWN and the read permissions are in both roles and must appear once.
        assertThat(roles.findPermissionCodesByUserId(user.getId()))
                .doesNotHaveDuplicates()
                .contains("PROFILE_UPDATE_OWN", "POI_CREATE", "CONSULTATION_CREATE");
    }
}
