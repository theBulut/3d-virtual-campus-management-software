package de.tudarmstadt.campus.admin.rbac;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins down the matrix of spec section 1.3 without touching the database. The counts come from the
 * table in the specification, so an accidental edit to {@link RoleCatalog} shows up here.
 */
class RoleCatalogTest {

    @Test
    void catalogueHasTheThirtySevenPermissionsOfTheSpecification() {
        assertThat(PermissionCode.values()).hasSize(37);
    }

    @Test
    void thereAreExactlySixRoles() {
        assertThat(RoleCode.values()).hasSize(6);
    }

    @ParameterizedTest
    @EnumSource(RoleCode.class)
    void everyRoleHasAnEntryInBothMatrices(RoleCode role) {
        assertThat(RoleCatalog.permissionsOf(role)).isNotNull();
        assertThat(RoleCatalog.grantableBy(role)).isNotNull();
    }

    @Test
    void adminHoldsEveryPermission() {
        assertThat(RoleCatalog.permissionsOf(RoleCode.ADMIN))
                .containsExactlyInAnyOrder(PermissionCode.values());
    }

    @Test
    void rolesHoldTheNumberOfPermissionsFromTheMatrix() {
        assertThat(RoleCatalog.permissionsOf(RoleCode.ADMIN)).hasSize(37);
        assertThat(RoleCatalog.permissionsOf(RoleCode.PROJEKTLEITER)).hasSize(31);
        assertThat(RoleCatalog.permissionsOf(RoleCode.PROJEKTMITARBEITER)).hasSize(11);
        assertThat(RoleCatalog.permissionsOf(RoleCode.PERSONAL)).hasSize(8);
        assertThat(RoleCatalog.permissionsOf(RoleCode.MAINTENANCE_DEV)).hasSize(5);
        assertThat(RoleCatalog.permissionsOf(RoleCode.EXTERNE_PERSON)).hasSize(3);
    }

    /** FA-11: contributing content and releasing it are different permissions. */
    @Test
    void projektmitarbeiterCanSubmitButNotPublish() {
        assertThat(RoleCatalog.hasPermission(RoleCode.PROJEKTMITARBEITER, PermissionCode.POI_CREATE)).isTrue();
        assertThat(RoleCatalog.hasPermission(RoleCode.PROJEKTMITARBEITER, PermissionCode.POI_SUBMIT_REVIEW)).isTrue();
        assertThat(RoleCatalog.hasPermission(RoleCode.PROJEKTMITARBEITER, PermissionCode.POI_PUBLISH)).isFalse();
        assertThat(RoleCatalog.hasPermission(RoleCode.PROJEKTLEITER, PermissionCode.POI_PUBLISH)).isTrue();
    }

    /** Least privilege, deliberately a counter-example in the matrix. */
    @Test
    void maintenanceDevSeesOperationsDataButNoUsersOrContent() {
        Set<PermissionCode> permissions = RoleCatalog.permissionsOf(RoleCode.MAINTENANCE_DEV);
        assertThat(permissions).contains(PermissionCode.AUDIT_READ, PermissionCode.SYSTEM_HEALTH_READ);
        assertThat(permissions).doesNotContain(PermissionCode.USER_READ, PermissionCode.POI_READ_ALL,
                PermissionCode.POI_CREATE, PermissionCode.ROLE_ASSIGN);
    }

    @Test
    void onlyAdminMayDeleteAccountsResetPasswordsAndManageRoles() {
        for (RoleCode role : RoleCode.values()) {
            boolean expected = role == RoleCode.ADMIN;
            assertThat(RoleCatalog.hasPermission(role, PermissionCode.USER_DELETE)).isEqualTo(expected);
            assertThat(RoleCatalog.hasPermission(role, PermissionCode.USER_PASSWORD_RESET)).isEqualTo(expected);
            assertThat(RoleCatalog.hasPermission(role, PermissionCode.ROLE_MANAGE)).isEqualTo(expected);
            assertThat(RoleCatalog.hasPermission(role, PermissionCode.SYSTEM_CONFIG)).isEqualTo(expected);
        }
    }

    @Test
    void grantSetsFollowSectionOneFour() {
        assertThat(RoleCatalog.grantableBy(RoleCode.ADMIN))
                .containsExactlyInAnyOrder(RoleCode.ADMIN, RoleCode.PROJEKTLEITER,
                        RoleCode.PROJEKTMITARBEITER, RoleCode.PERSONAL, RoleCode.MAINTENANCE_DEV,
                        RoleCode.EXTERNE_PERSON);
        assertThat(RoleCatalog.grantableBy(RoleCode.PROJEKTLEITER))
                .containsExactlyInAnyOrder(RoleCode.PROJEKTMITARBEITER, RoleCode.PERSONAL,
                        RoleCode.EXTERNE_PERSON);
        assertThat(RoleCatalog.grantableBy(RoleCode.PROJEKTMITARBEITER)).isEmpty();
        assertThat(RoleCatalog.grantableBy(RoleCode.PERSONAL)).isEmpty();
        assertThat(RoleCatalog.grantableBy(RoleCode.MAINTENANCE_DEV)).isEmpty();
        assertThat(RoleCatalog.grantableBy(RoleCode.EXTERNE_PERSON)).isEmpty();
    }

    /**
     * Since self-registration exists, EXTERNE_PERSON is a role like any other (docs/DECISIONS.md D-40).
     * <p>
     * It has to sit in the grant sets of both administering roles, and not so it can be handed out
     * manually: assertCanManage demands that every role of a target account lies inside the caller's
     * grant set. Without it, a self-registered account could never be promoted.
     */
    @Test
    void externePersonIsAssignableAndReachableForBothAdministeringRoles() {
        assertThat(RoleCode.EXTERNE_PERSON.assignable()).isTrue();
        assertThat(RoleCatalog.grantableBy(RoleCode.ADMIN)).contains(RoleCode.EXTERNE_PERSON);
        assertThat(RoleCatalog.grantableBy(RoleCode.PROJEKTLEITER)).contains(RoleCode.EXTERNE_PERSON);

        // The roles without any administrative reach stay empty.
        assertThat(RoleCatalog.grantableBy(RoleCode.PROJEKTMITARBEITER)).isEmpty();
        assertThat(RoleCatalog.grantableBy(RoleCode.PERSONAL)).isEmpty();
        assertThat(RoleCatalog.grantableBy(RoleCode.MAINTENANCE_DEV)).isEmpty();
        assertThat(RoleCatalog.grantableBy(RoleCode.EXTERNE_PERSON)).isEmpty();
    }

    /** The role a registered player holds is what makes the game reachable at all. */
    @Test
    void externePersonMayReadPublishedContent() {
        assertThat(RoleCatalog.permissionsOf(RoleCode.EXTERNE_PERSON))
                .containsExactlyInAnyOrder(PermissionCode.POI_READ_PUBLISHED,
                        PermissionCode.BUILDING_READ_PUBLIC, PermissionCode.CONSULTATION_READ_PUBLIC);
    }

    @Test
    void everyRoleIsAssignable() {
        // The is_assignable flag stays as the mechanism, but no role carries it any more. A future role
        // that must not be handed out would be refused in one place, RoleAssignmentService.assign.
        for (RoleCode role : RoleCode.values()) {
            assertThat(role.assignable()).as("%s", role).isTrue();
        }
    }

    @Test
    void permissionCodesFollowTheResourceActionScheme() {
        for (PermissionCode permission : PermissionCode.values()) {
            assertThat(permission.name())
                    .as("%s must start with its resource", permission)
                    .startsWith(permission.resource().name() + "_");
            assertThat(permission.description()).isNotBlank();
            assertThat(permission.action()).isNotBlank();
        }
    }

    @Test
    void theMatrixCannotBeModifiedFromOutside() {
        assertThatThrownBy(() -> RoleCatalog.permissionsOf(RoleCode.PERSONAL).add(PermissionCode.USER_DELETE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
