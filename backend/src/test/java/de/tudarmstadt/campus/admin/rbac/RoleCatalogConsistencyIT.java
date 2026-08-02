package de.tudarmstadt.campus.admin.rbac;

import de.tudarmstadt.campus.admin.rbac.domain.Permission;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.repository.PermissionRepository;
import de.tudarmstadt.campus.admin.rbac.repository.RoleGrantRepository;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance test for phase 2: the Java catalogue and {@code V4__seed_rbac.sql} describe the same model.
 * <p>
 * Two representations of the permission matrix exist because the seed has to be reproducible in the
 * database while the application needs the matrix in memory. This test is what keeps them from drifting
 * apart — and it is the evidence for FA-04 and FA-05.
 */
class RoleCatalogConsistencyIT extends AbstractIntegrationTest {

    @Autowired
    private RoleRepository roles;

    @Autowired
    private PermissionRepository permissions;

    @Autowired
    private RoleGrantRepository roleGrants;

    @Test
    void seedContainsExactlyTheSixRolesOfTheCatalogue() {
        List<String> seeded = roles.findAllByOrderBySortOrderAsc().stream().map(Role::getName).toList();

        assertThat(seeded).containsExactly("ADMIN", "PROJEKTLEITER", "PROJEKTMITARBEITER",
                "PERSONAL", "MAINTENANCE_DEV", "EXTERNE_PERSON");
    }

    @Test
    void seedContainsExactlyThePermissionCatalogue() {
        List<String> seeded = permissions.findAllByOrderByResourceAscCodeAsc().stream()
                .map(Permission::getCode).toList();

        assertThat(seeded).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(PermissionCode.values()).map(Enum::name).toList());
    }

    @ParameterizedTest
    @EnumSource(RoleCode.class)
    void roleMetadataMatchesTheCatalogue(RoleCode expected) {
        Role seeded = roles.findByName(expected.name()).orElseThrow();

        assertThat(seeded.getDisplayName()).isEqualTo(expected.displayName());
        assertThat(seeded.getDescription()).isEqualTo(expected.description());
        assertThat(seeded.isAssignable()).isEqualTo(expected.assignable());
        assertThat(seeded.getSortOrder()).isEqualTo(expected.sortOrder());
        // INV-5: all six are system roles and can be neither renamed nor deleted.
        assertThat(seeded.isSystem()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(PermissionCode.class)
    void permissionMetadataMatchesTheCatalogue(PermissionCode expected) {
        Permission seeded = permissions.findByCode(expected.name()).orElseThrow();

        assertThat(seeded.getResource()).isEqualTo(expected.resource().name());
        assertThat(seeded.getAction()).isEqualTo(expected.action());
        assertThat(seeded.getDescription()).isEqualTo(expected.description());
    }

    /** The core of the phase: every cell of the matrix in section 1.3, in both directions. */
    @ParameterizedTest
    @EnumSource(RoleCode.class)
    void permissionAssignmentsMatchTheMatrix(RoleCode role) {
        Role seeded = roles.findByNameWithPermissions(role.name()).orElseThrow();
        Set<String> seededCodes = seeded.getPermissions().stream()
                .map(Permission::getCode).collect(Collectors.toSet());
        Set<String> expected = RoleCatalog.permissionsOf(role).stream()
                .map(Enum::name).collect(Collectors.toSet());

        assertThat(seededCodes)
                .as("permissions of %s", role)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @ParameterizedTest
    @EnumSource(RoleCode.class)
    void grantRulesMatchTheCatalogue(RoleCode role) {
        List<String> seeded = roleGrants.findGrantableRoleNames(List.of(role.name()));
        List<String> expected = RoleCatalog.grantableBy(role).stream().map(Enum::name).sorted().toList();

        assertThat(seeded).as("grant set of %s", role).containsExactlyElementsOf(expected);
    }

    @Test
    void everyPermissionIsHeldByAtLeastOneRole() {
        for (PermissionCode permission : PermissionCode.values()) {
            boolean held = Arrays.stream(RoleCode.values())
                    .anyMatch(role -> RoleCatalog.hasPermission(role, permission));
            assertThat(held).as("%s belongs to no role", permission).isTrue();
        }
    }

    @Test
    void externePersonIsSeededButNotAssignable() {
        Role external = roles.findByName(RoleCode.EXTERNE_PERSON.name()).orElseThrow();

        assertThat(external.isAssignable()).isFalse();
        assertThat(roleGrants.findGrantableRoleNames(
                Arrays.stream(RoleCode.values()).map(Enum::name).toList()))
                .doesNotContain(RoleCode.EXTERNE_PERSON.name());
    }
}
