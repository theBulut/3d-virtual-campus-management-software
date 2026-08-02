package de.tudarmstadt.campus.admin.user;

import de.tudarmstadt.campus.admin.audit.domain.AuditLog;
import de.tudarmstadt.campus.admin.audit.repository.AuditLogRepository;
import de.tudarmstadt.campus.admin.content.building.domain.Building;
import de.tudarmstadt.campus.admin.content.building.repository.BuildingRepository;
import de.tudarmstadt.campus.admin.content.poi.domain.Poi;
import de.tudarmstadt.campus.admin.content.poi.repository.PoiRepository;
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

/**
 * Guards the correction from docs/DECISIONS.md D-9.
 * <p>
 * The DDL in the specification omits the ON DELETE clause on every reference back to {@code admin_user}.
 * With that schema {@code DELETE /api/users/{id}} (spec section 5.2) fails with SQLState 23503 for any
 * account that ever granted a role or created content — which is nearly every account. ON DELETE SET NULL
 * keeps the history intact instead: the content survives without an author, and the audit trail keeps the
 * denormalised {@code actor_username}.
 */
@Transactional
class UserDeletionConstraintIT extends AbstractIntegrationTest {

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private PoiRepository pois;

    @Autowired
    private BuildingRepository buildings;

    @Autowired
    private AuditLogRepository auditLogs;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deletingAnAccountThatGrantedRolesAndCreatedContentLeavesTheHistoryIntact() {
        AdminUser granter = adminUsers.save(TestEntities.user("deletion_granter"));
        AdminUser target = adminUsers.save(TestEntities.user("deletion_target"));
        target.setCreatedBy(granter);
        Role role = roles.save(TestEntities.role("DELETION_TEST_ROLE"));
        userRoles.save(new UserRole(target, role, granter));

        Building building = new Building("S1|99", "Testgebäude");
        building.setCreatedBy(granter);
        buildings.save(building);

        Poi poi = TestEntities.poi("Audimax");
        poi.setCreatedBy(granter);
        poi.setAssignedTo(granter);
        poi.setPublishedBy(granter);
        pois.save(poi);

        AuditLog entry = new AuditLog("USER_CREATED", "USER", String.valueOf(target.getId()));
        entry.setActor(granter);
        entry.setActorUsername(granter.getUsername());
        auditLogs.save(entry);

        entityManager.flush();
        Long granterId = granter.getId();
        Long targetId = target.getId();
        Long poiId = poi.getId();
        Long buildingId = building.getId();
        Long entryId = entry.getId();

        // Detach everything before deleting. The database resolves the references via ON DELETE SET
        // NULL, but Hibernate refuses to flush while loaded entities still point at the deleted row —
        // it cannot know the database will null them out. In production the delete runs in its own
        // transaction with no content loaded, so this only has to be arranged here.
        entityManager.clear();

        adminUsers.delete(adminUsers.findById(granterId).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(adminUsers.findById(granterId)).isEmpty();

        Poi keptPoi = pois.findById(poiId).orElseThrow();
        assertThat(keptPoi.getCreatedBy()).isNull();
        assertThat(keptPoi.getAssignedTo()).isNull();
        assertThat(keptPoi.getPublishedBy()).isNull();
        assertThat(keptPoi.getNameDe()).isEqualTo("Audimax");

        assertThat(buildings.findById(buildingId).orElseThrow().getCreatedBy()).isNull();

        AuditLog keptEntry = auditLogs.findById(entryId).orElseThrow();
        assertThat(keptEntry.getActor()).isNull();
        assertThat(keptEntry.getActorUsername()).isEqualTo("deletion_granter");

        UserRole keptAssignment = userRoles
                .findByUserIdAndRoleName(targetId, "DELETION_TEST_ROLE").orElseThrow();
        assertThat(keptAssignment.getAssignedBy()).isNull();

        assertThat(adminUsers.findById(targetId).orElseThrow().getCreatedBy()).isNull();
    }

    @Test
    void deletingAnAccountRemovesItsOwnRoleAssignments() {
        AdminUser user = adminUsers.save(TestEntities.user("deletion_selfroles"));
        Role role = roles.save(TestEntities.role("DELETION_SELF_ROLE"));
        userRoles.save(new UserRole(user, role, null));
        entityManager.flush();
        Long userId = user.getId();
        entityManager.clear();

        adminUsers.delete(adminUsers.findById(userId).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(userRoles.findByUserId(userId)).isEmpty();
        // The role itself must not be taken down with the account.
        assertThat(roles.findByName("DELETION_SELF_ROLE")).isPresent();
    }
}
