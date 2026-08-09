package de.tudarmstadt.campus.admin.rbac.service;

import de.tudarmstadt.campus.admin.common.exception.BadRequestException;
import de.tudarmstadt.campus.admin.common.exception.ConflictException;
import de.tudarmstadt.campus.admin.common.exception.ForbiddenException;
import de.tudarmstadt.campus.admin.common.exception.NotFoundException;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.domain.UserRole;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.rbac.repository.UserRoleRepository;
import de.tudarmstadt.campus.admin.support.AbstractIntegrationTest;
import de.tudarmstadt.campus.admin.support.TestEntities;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One test per invariant of spec section 1.5, plus the two guards of the assignment sequence in
 * section 1.4. This is the acceptance evidence for FA-07 and FA-08.
 */
@Transactional
class RoleAssignmentInvariantsIT extends AbstractIntegrationTest {

    @Autowired
    private RoleAssignmentService roleAssignments;

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private EntityManager entityManager;

    // --- the assignment sequence of section 1.4 -------------------------------------------------

    @Test
    void aProjektleiterMayGrantTheTwoContentRoles() {
        AdminUser leitung = account("inv_leitung", RoleCode.PROJEKTLEITER);
        AdminUser target = account("inv_target", RoleCode.PERSONAL);

        roleAssignments.assign(leitung.getId(), target.getId(), RoleCode.PROJEKTMITARBEITER.name());
        entityManager.flush();

        assertThat(roles.findRoleNamesByUserId(target.getId()))
                .containsExactlyInAnyOrder("PERSONAL", "PROJEKTMITARBEITER");
    }

    /** Scenario S-06: the permission is there, the role is not in the caller's grant set. */
    @Test
    void aProjektleiterCannotGrantAdmin() {
        AdminUser leitung = account("inv_leitung_admin", RoleCode.PROJEKTLEITER);
        AdminUser target = account("inv_target_admin", RoleCode.PERSONAL);

        assertThatThrownBy(() -> roleAssignments.assign(leitung.getId(), target.getId(),
                RoleCode.ADMIN.name()))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "ROLE_NOT_GRANTABLE");
    }

    /** Footnote 1 of the matrix: the target must lie inside the caller's grant set. */
    @Test
    void aProjektleiterCannotTouchAnAdminAccount() {
        AdminUser leitung = account("inv_leitung_scope", RoleCode.PROJEKTLEITER);
        AdminUser admin = account("inv_admin_scope", RoleCode.ADMIN);

        assertThatThrownBy(() -> roleAssignments.assign(leitung.getId(), admin.getId(),
                RoleCode.PERSONAL.name()))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "TARGET_OUT_OF_SCOPE");
    }

    @Test
    void assigningTheSameRoleTwiceIsRejected() {
        AdminUser admin = account("inv_admin_dup", RoleCode.ADMIN);
        AdminUser target = account("inv_target_dup", RoleCode.PERSONAL);

        assertThatThrownBy(() -> roleAssignments.assign(admin.getId(), target.getId(),
                RoleCode.PERSONAL.name()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "ROLE_ALREADY_ASSIGNED");
    }

    @Test
    void unknownAccountsAndRolesYieldNotFound() {
        AdminUser admin = account("inv_admin_404", RoleCode.ADMIN);
        AdminUser target = account("inv_target_404", RoleCode.PERSONAL);

        assertThatThrownBy(() -> roleAssignments.assign(admin.getId(), -1L, RoleCode.PERSONAL.name()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> roleAssignments.assign(admin.getId(), target.getId(), "GIBT_ES_NICHT"))
                .isInstanceOf(NotFoundException.class);
    }

    // --- INV-1 to INV-4 -------------------------------------------------------------------------

    /**
     * INV-1, tested on the guard itself rather than through a call sequence.
     * <p>
     * The invariant cannot be reached through the API: revoking, deleting or locking an ADMIN requires
     * the caller to hold ADMIN, which makes them a second active administrator — and the single
     * administrator case is already caught by INV-2. The guard is defence in depth for the day a bulk
     * operation or a relaxed INV-2 arrives (docs/DECISIONS.md D-29).
     */
    @Test
    void theLastActiveAdminIsProtected() {
        // AppInitializerService created exactly one active administrator.
        AdminUser initialAdmin = adminUsers.findByUsername("admin").orElseThrow();

        assertThatThrownBy(() -> roleAssignments.assertAnotherActiveAdminRemains(initialAdmin.getId()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "LAST_ADMIN_PROTECTED");

        // With a second active administrator the same call is fine.
        account("inv_second_admin", RoleCode.ADMIN);
        roleAssignments.assertAnotherActiveAdminRemains(initialAdmin.getId());
    }

    /** An inactive administrator does not count towards INV-1. */
    @Test
    void aLockedAdministratorDoesNotSatisfyTheInvariant() {
        AdminUser initialAdmin = adminUsers.findByUsername("admin").orElseThrow();
        AdminUser locked = account("inv_locked_admin", RoleCode.ADMIN);
        locked.setActive(false);
        adminUsers.save(locked);
        entityManager.flush();

        assertThatThrownBy(() -> roleAssignments.assertAnotherActiveAdminRemains(initialAdmin.getId()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "LAST_ADMIN_PROTECTED");
    }

    /** INV-2 — deliberately stricter than the specification: no self change at all (D-26). */
    @Test
    void nobodyChangesTheirOwnRoles() {
        AdminUser admin = account("inv_self", RoleCode.ADMIN);

        assertThatThrownBy(() -> roleAssignments.revoke(admin.getId(), admin.getId(),
                RoleCode.ADMIN.name()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_MODIFICATION_FORBIDDEN");
        assertThatThrownBy(() -> roleAssignments.assign(admin.getId(), admin.getId(),
                RoleCode.PERSONAL.name()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_MODIFICATION_FORBIDDEN");
    }

    /** INV-3 */
    @Test
    void theLastRoleOfAnAccountCannotBeRevoked() {
        AdminUser admin = account("inv_admin_last_role", RoleCode.ADMIN);
        AdminUser target = account("inv_target_last_role", RoleCode.PERSONAL);

        assertThatThrownBy(() -> roleAssignments.revoke(admin.getId(), target.getId(),
                RoleCode.PERSONAL.name()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "LAST_ROLE_PROTECTED");
    }

    /** INV-4 */
    @Test
    void externePersonCannotBeAssigned() {
        AdminUser admin = account("inv_admin_extern", RoleCode.ADMIN);
        AdminUser target = account("inv_target_extern", RoleCode.PERSONAL);

        assertThatThrownBy(() -> roleAssignments.assign(admin.getId(), target.getId(),
                RoleCode.EXTERNE_PERSON.name()))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", "ROLE_NOT_ASSIGNABLE");
    }

    /** INV-6: a role change invalidates the outstanding access tokens right away. */
    @Test
    void aRoleChangeRaisesTheTokenVersion() {
        AdminUser admin = account("inv_admin_version", RoleCode.ADMIN);
        AdminUser target = account("inv_target_version", RoleCode.PERSONAL);
        int before = target.getTokenVersion();

        roleAssignments.assign(admin.getId(), target.getId(), RoleCode.PROJEKTMITARBEITER.name());
        entityManager.flush();
        entityManager.clear();

        AdminUser reloaded = adminUsers.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getTokenVersion()).isEqualTo(before + 1);
        // Refresh tokens survive a role change, otherwise S-08 would force a new login (D-3).
        assertThat(reloaded.getRefreshVersion()).isZero();
    }

    private AdminUser account(String username, RoleCode roleCode) {
        AdminUser user = adminUsers.save(TestEntities.user(username));
        Role role = roles.findByName(roleCode.name()).orElseThrow();
        userRoles.save(new UserRole(user, role, null));
        entityManager.flush();
        return user;
    }
}
