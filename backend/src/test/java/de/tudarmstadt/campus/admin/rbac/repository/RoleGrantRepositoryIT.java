package de.tudarmstadt.campus.admin.rbac.repository;

import de.tudarmstadt.campus.admin.rbac.domain.Permission;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.domain.RoleGrant;
import de.tudarmstadt.campus.admin.rbac.domain.UserRole;
import de.tudarmstadt.campus.admin.support.AbstractIntegrationTest;
import de.tudarmstadt.campus.admin.support.TestEntities;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The grant rules of spec section 1.4 live in a table, not in code. This exercises the shape of that
 * table with the real hierarchy — the seed itself follows in phase 2.
 */
@Transactional
class RoleGrantRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleGrantRepository roleGrants;

    @Autowired
    private PermissionRepository permissions;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private EntityManager entityManager;

    private Role admin;
    private Role projektleiter;
    private Role projektmitarbeiter;
    private Role personal;

    @BeforeEach
    void seedRoleHierarchy() {
        admin = roles.save(TestEntities.role("ADMIN"));
        projektleiter = roles.save(TestEntities.role("PROJEKTLEITER"));
        projektmitarbeiter = roles.save(TestEntities.role("PROJEKTMITARBEITER"));
        personal = roles.save(TestEntities.role("PERSONAL"));
        Role maintenance = roles.save(TestEntities.role("MAINTENANCE_DEV"));

        // ADMIN grants everything except EXTERNE_PERSON, PROJEKTLEITER only the two content roles.
        roleGrants.save(new RoleGrant(admin, admin));
        roleGrants.save(new RoleGrant(admin, projektleiter));
        roleGrants.save(new RoleGrant(admin, projektmitarbeiter));
        roleGrants.save(new RoleGrant(admin, personal));
        roleGrants.save(new RoleGrant(admin, maintenance));
        roleGrants.save(new RoleGrant(projektleiter, projektmitarbeiter));
        roleGrants.save(new RoleGrant(projektleiter, personal));
        entityManager.flush();
    }

    @Test
    void adminMayGrantEveryAssignableRole() {
        assertThat(roleGrants.findGrantableRoleNames(List.of("ADMIN")))
                .containsExactly("ADMIN", "MAINTENANCE_DEV", "PERSONAL", "PROJEKTLEITER",
                        "PROJEKTMITARBEITER");
    }

    @Test
    void projektleiterMayGrantOnlyTheTwoContentRoles() {
        assertThat(roleGrants.findGrantableRoleNames(List.of("PROJEKTLEITER")))
                .containsExactly("PERSONAL", "PROJEKTMITARBEITER");
    }

    @Test
    void rolesWithoutGrantRightsGrantNothing() {
        assertThat(roleGrants.findGrantableRoleNames(List.of("PROJEKTMITARBEITER", "PERSONAL")))
                .isEmpty();
    }

    /** The effective grant set of a user is the union over all of their roles (spec section 1.4). */
    @Test
    void grantSetsOfSeveralRolesAreUnioned() {
        assertThat(roleGrants.findGrantableRoleNames(List.of("PROJEKTLEITER", "MAINTENANCE_DEV")))
                .containsExactly("PERSONAL", "PROJEKTMITARBEITER");
        assertThat(roleGrants.findGrantableRoleNames(List.of("ADMIN", "PROJEKTLEITER")))
                .hasSize(5)
                .doesNotHaveDuplicates();
    }

    @Test
    void answersTheGrantabilityQuestionDirectly() {
        assertThat(roleGrants.canGrant(List.of("PROJEKTLEITER"), "PROJEKTMITARBEITER")).isTrue();
        // The check behind the ROLE_NOT_GRANTABLE error of scenario S-06.
        assertThat(roleGrants.canGrant(List.of("PROJEKTLEITER"), "ADMIN")).isFalse();
        assertThat(roleGrants.canGrant(List.of("PERSONAL"), "PERSONAL")).isFalse();
    }

    @Test
    void resolvesPermissionCodesAndRoleNamesOfAUser() {
        Permission publish = permissions.save(
                new Permission("POI_PUBLISH", "POI", "PUBLISH", "Freigeben, zurückweisen, archivieren"));
        Permission read = permissions.save(
                new Permission("POI_READ_ALL", "POI", "READ", "Alle POIs inkl. Entwürfe lesen"));
        // A mutable set: Hibernate wraps and manages the collection itself.
        projektleiter.setPermissions(new LinkedHashSet<>(List.of(publish, read)));
        roles.save(projektleiter);

        AdminUser user = adminUsers.save(TestEntities.user("grant_user"));
        userRoles.save(new UserRole(user, projektleiter, null));
        entityManager.flush();
        entityManager.clear();

        assertThat(roles.findPermissionCodesByUserId(user.getId()))
                .containsExactly("POI_PUBLISH", "POI_READ_ALL");
        assertThat(roles.findRoleNamesByUserId(user.getId())).containsExactly("PROJEKTLEITER");
    }
}
