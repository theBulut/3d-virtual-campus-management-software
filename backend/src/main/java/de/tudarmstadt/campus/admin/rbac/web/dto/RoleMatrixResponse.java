package de.tudarmstadt.campus.admin.rbac.web.dto;

import java.util.List;
import java.util.Map;

/**
 * The complete role model in one document (spec section 5.3, FA-20).
 * <p>
 * Serves two purposes: it renders the matrix view of the admin interface, and it is the machine readable
 * source for the matrix figure in chapter 4 of the thesis. Because it is read from the database, it also
 * proves that the seeded model and the documented model are the same thing.
 *
 * @param assignments permission codes per role — the cells of the matrix in section 1.3
 * @param grants      which role may hand out which role — section 1.4
 */
public record RoleMatrixResponse(
        List<RoleSummary> roles,
        List<PermissionResponse> permissions,
        Map<String, List<String>> assignments,
        Map<String, List<String>> grants) {

    public record RoleSummary(String name, String displayName, boolean assignable, int sortOrder) {
    }
}
