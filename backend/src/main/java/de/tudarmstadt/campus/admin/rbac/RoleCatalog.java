package de.tudarmstadt.campus.admin.rbac;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static de.tudarmstadt.campus.admin.rbac.PermissionCode.AUDIT_READ;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.AUDIT_READ_CONTENT;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.BUILDING_CREATE;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.BUILDING_DELETE;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.BUILDING_READ_ALL;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.BUILDING_READ_PUBLIC;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.BUILDING_UPDATE;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.CONSULTATION_CREATE;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.CONSULTATION_DELETE;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.CONSULTATION_READ_ALL;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.CONSULTATION_READ_PUBLIC;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.CONSULTATION_UPDATE_ANY;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.CONSULTATION_UPDATE_OWN;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.DATA_EXPORT;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.MEDIA_DELETE;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.MEDIA_UPLOAD;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.POI_ASSIGN;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.POI_CREATE;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.POI_DELETE;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.POI_PUBLISH;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.POI_READ_ALL;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.POI_READ_PUBLISHED;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.POI_SUBMIT_REVIEW;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.POI_UPDATE_ANY;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.POI_UPDATE_OWN;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.PROFILE_UPDATE_OWN;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.ROLE_ASSIGN;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.ROLE_READ;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.SYSTEM_HEALTH_READ;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.USER_ACTIVATE;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.USER_CREATE;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.USER_READ;
import static de.tudarmstadt.campus.admin.rbac.PermissionCode.USER_UPDATE;

/**
 * The permission matrix of spec section 1.3 and the grant rules of section 1.4, as a Java constant.
 * <p>
 * This class is the single source of truth. {@code V4__seed_rbac.sql} writes the same content into the
 * database and {@code RoleCatalogConsistencyIT} fails if the two ever drift apart. Adding a role is
 * therefore one entry here plus one migration — no controller is touched (NFA-08).
 */
public final class RoleCatalog {

    private static final Map<RoleCode, Set<PermissionCode>> PERMISSIONS = buildPermissions();
    private static final Map<RoleCode, Set<RoleCode>> GRANTS = buildGrants();

    private RoleCatalog() {
    }

    private static Map<RoleCode, Set<PermissionCode>> buildPermissions() {
        Map<RoleCode, Set<PermissionCode>> matrix = new EnumMap<>(RoleCode.class);

        // Full access: the matrix marks every permission for ADMIN.
        matrix.put(RoleCode.ADMIN, EnumSet.allOf(PermissionCode.class));

        // Everything except deleting accounts, resetting foreign passwords, managing roles and the
        // system-level reads. Restricted user management is enforced on top of USER_UPDATE and
        // USER_ACTIVATE by the grant set (spec section 1.3, footnote 1).
        matrix.put(RoleCode.PROJEKTLEITER, EnumSet.of(
                USER_READ, USER_CREATE, USER_UPDATE, USER_ACTIVATE,
                ROLE_READ, ROLE_ASSIGN, PROFILE_UPDATE_OWN,
                POI_READ_PUBLISHED, POI_READ_ALL, POI_CREATE, POI_UPDATE_OWN, POI_UPDATE_ANY,
                POI_DELETE, POI_SUBMIT_REVIEW, POI_PUBLISH, POI_ASSIGN,
                BUILDING_READ_PUBLIC, BUILDING_READ_ALL, BUILDING_CREATE, BUILDING_UPDATE, BUILDING_DELETE,
                CONSULTATION_READ_PUBLIC, CONSULTATION_READ_ALL, CONSULTATION_CREATE,
                CONSULTATION_UPDATE_OWN, CONSULTATION_UPDATE_ANY, CONSULTATION_DELETE,
                MEDIA_UPLOAD, MEDIA_DELETE, AUDIT_READ_CONTENT, DATA_EXPORT));

        // Contributes content but cannot publish it — the separation behind FA-11.
        matrix.put(RoleCode.PROJEKTMITARBEITER, EnumSet.of(
                PROFILE_UPDATE_OWN,
                POI_READ_PUBLISHED, POI_READ_ALL, POI_CREATE, POI_UPDATE_OWN, POI_SUBMIT_REVIEW,
                BUILDING_READ_PUBLIC, BUILDING_READ_ALL,
                CONSULTATION_READ_PUBLIC, CONSULTATION_READ_ALL,
                MEDIA_UPLOAD));

        // Maintains its own consultation hours and nothing else.
        matrix.put(RoleCode.PERSONAL, EnumSet.of(
                PROFILE_UPDATE_OWN,
                POI_READ_PUBLISHED,
                BUILDING_READ_PUBLIC, BUILDING_READ_ALL,
                CONSULTATION_READ_PUBLIC, CONSULTATION_READ_ALL, CONSULTATION_CREATE,
                CONSULTATION_UPDATE_OWN));

        // Least privilege as a deliberate counter-example: operations insight, no user data, no content.
        matrix.put(RoleCode.MAINTENANCE_DEV, EnumSet.of(
                ROLE_READ, PROFILE_UPDATE_OWN,
                AUDIT_READ, AUDIT_READ_CONTENT, SYSTEM_HEALTH_READ));

        // Every self-registered account. These three permissions carry the game: GET /api/game/scene
        // requires POI_READ_PUBLISHED, and how much of the scene comes back depends on whether the
        // caller additionally holds the _ALL variants (docs/DECISIONS.md D-42).
        matrix.put(RoleCode.EXTERNE_PERSON, EnumSet.of(
                POI_READ_PUBLISHED, BUILDING_READ_PUBLIC, CONSULTATION_READ_PUBLIC));

        matrix.replaceAll((role, permissions) -> Collections.unmodifiableSet(permissions));
        return Collections.unmodifiableMap(matrix);
    }

    private static Map<RoleCode, Set<RoleCode>> buildGrants() {
        Map<RoleCode, Set<RoleCode>> grants = new EnumMap<>(RoleCode.class);

        // EXTERNE_PERSON belongs in both sets, and not for the sake of handing it out: assertCanManage
        // requires every role of a target account to lie inside the caller's grant set. Without it, a
        // self-registered account would be out of scope for every administrator and could never be
        // promoted (docs/DECISIONS.md D-40).
        grants.put(RoleCode.ADMIN, EnumSet.of(RoleCode.ADMIN, RoleCode.PROJEKTLEITER,
                RoleCode.PROJEKTMITARBEITER, RoleCode.PERSONAL, RoleCode.MAINTENANCE_DEV,
                RoleCode.EXTERNE_PERSON));
        grants.put(RoleCode.PROJEKTLEITER, EnumSet.of(RoleCode.PROJEKTMITARBEITER, RoleCode.PERSONAL,
                RoleCode.EXTERNE_PERSON));
        grants.put(RoleCode.PROJEKTMITARBEITER, EnumSet.noneOf(RoleCode.class));
        grants.put(RoleCode.PERSONAL, EnumSet.noneOf(RoleCode.class));
        grants.put(RoleCode.MAINTENANCE_DEV, EnumSet.noneOf(RoleCode.class));
        grants.put(RoleCode.EXTERNE_PERSON, EnumSet.noneOf(RoleCode.class));

        grants.replaceAll((role, grantable) -> Collections.unmodifiableSet(grantable));
        return Collections.unmodifiableMap(grants);
    }

    public static Set<PermissionCode> permissionsOf(RoleCode role) {
        return PERMISSIONS.get(role);
    }

    public static Set<RoleCode> grantableBy(RoleCode role) {
        return GRANTS.get(role);
    }

    public static boolean hasPermission(RoleCode role, PermissionCode permission) {
        return PERMISSIONS.get(role).contains(permission);
    }

    /** The full matrix, for the consistency test and for {@code GET /api/roles/matrix} (phase 4). */
    public static Map<RoleCode, Set<PermissionCode>> permissionMatrix() {
        return PERMISSIONS;
    }

    public static Map<RoleCode, Set<RoleCode>> grantMatrix() {
        return GRANTS;
    }
}
