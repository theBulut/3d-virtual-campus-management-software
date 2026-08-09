package de.tudarmstadt.campus.admin.rbac.web.dto;

import java.util.List;

/**
 * A role with its permissions (spec section 5.3).
 *
 * @param userCount    number of accounts holding the role, so the interface can warn before a change
 * @param system       true for all six roles; they can be neither renamed nor deleted (INV-5)
 * @param assignable   false for EXTERNE_PERSON (INV-4)
 * @param permissions  full permission list, empty when the role was loaded without them
 */
public record RoleResponse(
        String name,
        String displayName,
        String description,
        boolean system,
        boolean assignable,
        int sortOrder,
        long userCount,
        List<String> permissions) {
}
