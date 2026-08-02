package de.tudarmstadt.campus.admin.support;

import de.tudarmstadt.campus.admin.content.poi.domain.Poi;
import de.tudarmstadt.campus.admin.content.poi.domain.PoiCategory;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;

/**
 * Unpersisted fixtures. The RBAC seed only arrives in phase 2, so tests build the rows they need.
 */
public final class TestEntities {

    /** Not a usable credential: the tests never authenticate, they only satisfy NOT NULL. */
    private static final String PLACEHOLDER_HASH = "$2a$12$0000000000000000000000000000000000000000000000000000";

    private TestEntities() {
    }

    public static AdminUser user(String username) {
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setEmail(username + "@tu-darmstadt.de");
        user.setPasswordHash(PLACEHOLDER_HASH);
        user.setFirstName("Test");
        user.setLastName(username);
        return user;
    }

    public static Role role(String name) {
        return new Role(name, name, "Rolle " + name, true, 0);
    }

    public static Poi poi(String nameDe) {
        return new Poi(nameDe, PoiCategory.LECTURE_HALL, 1.0, 2.0, 3.0);
    }
}
