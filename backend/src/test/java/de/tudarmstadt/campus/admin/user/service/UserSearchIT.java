package de.tudarmstadt.campus.admin.user.service;

import de.tudarmstadt.campus.admin.common.dto.PageResponse;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.domain.UserRole;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.rbac.repository.UserRoleRepository;
import de.tudarmstadt.campus.admin.support.AbstractIntegrationTest;
import de.tudarmstadt.campus.admin.support.TestEntities;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import de.tudarmstadt.campus.admin.user.web.dto.UserResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every filter combination of {@code GET /api/users} (spec section 5.2).
 * <p>
 * Exists because the endpoint failed for every call without a {@code q} while the whole suite stayed
 * green: an untyped null parameter reaches PostgreSQL as {@code bytea} and {@code lower(bytea)} does not
 * exist. A filter that is never exercised with an empty value is not tested.
 */
@Transactional
class UserSearchIT extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedAccounts() {
        account("search_leitung", RoleCode.PROJEKTLEITER, true, "Eileen", "Projektleitung");
        account("search_mitarbeit", RoleCode.PROJEKTMITARBEITER, true, "Story", "Team");
        account("search_gesperrt", RoleCode.PERSONAL, false, "Gesperrtes", "Konto");
    }

    @Test
    void withoutAnyFilterEveryAccountIsReturned() {
        PageResponse<UserResponse> page = search(null, null, null);

        assertThat(page.content()).extracting(UserResponse::username)
                .contains("search_leitung", "search_mitarbeit", "search_gesperrt");
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void anEmptyQueryBehavesLikeNoQuery() {
        // The controller passes through what arrives, so "?q=" must not change the result.
        assertThat(search("   ", null, null).totalElements())
                .isEqualTo(search(null, null, null).totalElements());
    }

    @Test
    void theQueryMatchesUsernameEmailAndBothNames() {
        assertThat(search("search_leitung", null, null).content())
                .extracting(UserResponse::username).containsExactly("search_leitung");
        assertThat(search("SEARCH_LEITUNG", null, null).content())
                .as("case insensitive")
                .extracting(UserResponse::username).containsExactly("search_leitung");
        assertThat(search("eileen", null, null).content())
                .as("first name")
                .extracting(UserResponse::username).containsExactly("search_leitung");
        assertThat(search("Team", null, null).content())
                .as("last name")
                .extracting(UserResponse::username).containsExactly("search_mitarbeit");
        assertThat(search("search_leitung@tu-darmstadt.de", null, null).content())
                .as("email")
                .extracting(UserResponse::username).containsExactly("search_leitung");
    }

    @Test
    void theRoleFilterRestrictsToHoldersOfThatRole() {
        assertThat(search(null, RoleCode.PROJEKTLEITER.name(), null).content())
                .extracting(UserResponse::username).contains("search_leitung")
                .doesNotContain("search_mitarbeit", "search_gesperrt");
        assertThat(search(null, "GIBT_ES_NICHT", null).content()).isEmpty();
    }

    @Test
    void theActiveFilterSeparatesLockedAccounts() {
        assertThat(search(null, null, false).content())
                .extracting(UserResponse::username).contains("search_gesperrt")
                .doesNotContain("search_leitung");
        assertThat(search(null, null, true).content())
                .extracting(UserResponse::username).contains("search_leitung")
                .doesNotContain("search_gesperrt");
    }

    @Test
    void filtersCombine() {
        assertThat(search("search", RoleCode.PERSONAL.name(), false).content())
                .extracting(UserResponse::username).containsExactly("search_gesperrt");
        assertThat(search("search", RoleCode.PERSONAL.name(), true).content()).isEmpty();
    }

    @Test
    void theResultIsPagedAndSortable() {
        PageResponse<UserResponse> firstPage = userService.search("search", null, null,
                PageRequest.of(0, 2, Sort.by("username")));

        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.page()).isZero();
        assertThat(firstPage.size()).isEqualTo(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.content()).extracting(UserResponse::username)
                .containsExactly("search_gesperrt", "search_leitung");
    }

    @Test
    void theResultCarriesNoSecrets() {
        UserResponse user = search("search_leitung", null, null).content().getFirst();

        assertThat(user.roles()).containsExactly("PROJEKTLEITER");
        assertThat(user.username()).isEqualTo("search_leitung");
        // UserResponse has no field for the hash or the token counters at all — this pins that down.
        assertThat(UserResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("passwordHash", "tokenVersion", "refreshVersion");
    }

    private PageResponse<UserResponse> search(String query, String role, Boolean active) {
        return userService.search(query, role, active, PageRequest.of(0, 50, Sort.by("username")));
    }

    private void account(String username, RoleCode roleCode, boolean active,
                         String firstName, String lastName) {
        AdminUser user = TestEntities.user(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setActive(active);
        user = adminUsers.save(user);
        Role role = roles.findByName(roleCode.name()).orElseThrow();
        userRoles.save(new UserRole(user, role, null));
        entityManager.flush();
    }
}
