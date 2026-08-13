package de.tudarmstadt.campus.admin.user;

import com.jayway.jsonpath.JsonPath;
import de.tudarmstadt.campus.admin.rbac.PermissionCode;
import de.tudarmstadt.campus.admin.rbac.RoleCatalog;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.domain.UserRole;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.rbac.repository.UserRoleRepository;
import de.tudarmstadt.campus.admin.support.AbstractIntegrationTest;
import de.tudarmstadt.campus.admin.support.TestEntities;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import de.tudarmstadt.campus.admin.user.service.PasswordService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The evaluation scenarios of chapter 5 that belong to phase 4: S-05 to S-08 and S-20.
 */
@AutoConfigureMockMvc
@Transactional
class UserManagementIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private EntityManager entityManager;

    /** S-05: the project lead creates an account and assigns PROJEKTMITARBEITER. */
    @Test
    void aProjektleiterCreatesAnAccountAndAssignsARole() throws Exception {
        account("s05_leitung", RoleCode.PROJEKTLEITER);
        String token = accessTokenFor("s05_leitung");

        String created = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"s05.neu","email":"s05.neu@tu-darmstadt.de",
                                 "firstName":"Neue","lastName":"Kraft","roles":["PROJEKTMITARBEITER"]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("s05.neu"))
                .andExpect(jsonPath("$.user.roles[0]").value("PROJEKTMITARBEITER"))
                // The generated password is shown once and is not the stored hash.
                .andExpect(jsonPath("$.temporaryPassword").isString())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();

        int newUserId = JsonPath.read(created, "$.user.id");
        mockMvc.perform(post("/api/users/" + newUserId + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"PERSONAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasSize(2)));
    }

    /** S-06: the permission is there, the role is not — 403 with a distinct code. */
    @Test
    void aProjektleiterCannotHandOutAdmin() throws Exception {
        account("s06_leitung", RoleCode.PROJEKTLEITER);
        AdminUser target = account("s06_ziel", RoleCode.PERSONAL);
        String token = accessTokenFor("s06_leitung");

        mockMvc.perform(post("/api/users/" + target.getId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_GRANTABLE"));
    }

    /** S-06 variant: an account outside the own scope stays untouchable. */
    @Test
    void aProjektleiterCannotEditAnAdminAccount() throws Exception {
        account("s06b_leitung", RoleCode.PROJEKTLEITER);
        AdminUser admin = account("s06b_admin", RoleCode.ADMIN);
        String token = accessTokenFor("s06b_leitung");

        mockMvc.perform(put("/api/users/" + admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Fremd","lastName":"Zugriff",
                                 "email":"s06b.fremd@tu-darmstadt.de"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TARGET_OUT_OF_SCOPE"));
    }

    /**
     * S-08: a role is revoked while the account is logged in. The old access token dies immediately,
     * and a refresh yields a token with the reduced permission set — without a new login (FA-19).
     */
    @Test
    void revokingARoleTakesEffectImmediatelyAndSurvivesARefresh() throws Exception {
        account("s08_admin", RoleCode.ADMIN);
        AdminUser worker = account("s08_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        addRole(worker, RoleCode.PERSONAL);
        String adminToken = accessTokenFor("s08_admin");

        String session = login("s08_mitarbeit");
        String workerAccess = JsonPath.read(session, "$.accessToken");
        String workerRefresh = JsonPath.read(session, "$.refreshToken");
        List<String> before = JsonPath.read(session, "$.user.permissions");
        assertThat(before).contains(PermissionCode.POI_CREATE.name());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + workerAccess))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/" + worker.getId() + "/roles/PROJEKTMITARBEITER")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("PERSONAL"));

        // The token issued before the change is refused at once.
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + workerAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_STALE"));

        // The refresh token still works and returns the reduced permissions (D-3).
        String refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + workerRefresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> after = JsonPath.read(refreshed, "$.user.permissions");
        assertThat(after)
                .doesNotContain(PermissionCode.POI_CREATE.name())
                .containsExactlyInAnyOrderElementsOf(
                        RoleCatalog.permissionsOf(RoleCode.PERSONAL).stream().map(Enum::name).toList());
    }

    /** Locking an account ends its sessions and blocks the next login. */
    @Test
    void lockingAnAccountEndsItsSessions() throws Exception {
        account("s08b_admin", RoleCode.ADMIN);
        AdminUser target = account("s08b_ziel", RoleCode.PERSONAL);
        String adminToken = accessTokenFor("s08b_admin");
        String targetToken = JsonPath.read(login("s08b_ziel"), "$.accessToken");

        mockMvc.perform(patch("/api/users/" + target.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + targetToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"s08b_ziel\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    /** S-20: the matrix served by the API is the matrix of the specification. */
    @Test
    void theMatrixEndpointMirrorsTheCatalogue() throws Exception {
        account("s20_admin", RoleCode.ADMIN);
        String token = accessTokenFor("s20_admin");

        String matrix = mockMvc.perform(get("/api/roles/matrix")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasSize(6)))
                .andExpect(jsonPath("$.permissions", hasSize(37)))
                .andReturn().getResponse().getContentAsString();

        for (RoleCode role : RoleCode.values()) {
            List<String> served = JsonPath.read(matrix, "$.assignments." + role.name());
            assertThat(served)
                    .as("permissions of %s", role)
                    .containsExactlyInAnyOrderElementsOf(
                            RoleCatalog.permissionsOf(role).stream().map(Enum::name).toList());

            List<String> grants = JsonPath.read(matrix, "$.grants." + role.name());
            assertThat(grants)
                    .as("grant set of %s", role)
                    .containsExactlyInAnyOrderElementsOf(
                            RoleCatalog.grantableBy(role).stream().map(Enum::name).toList());
        }
    }

    @Test
    void theGrantableRolesEndpointMatchesWhatIsEnforced() throws Exception {
        account("s20b_leitung", RoleCode.PROJEKTLEITER);
        String token = accessTokenFor("s20b_leitung");

        mockMvc.perform(get("/api/users/me/grantable-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", containsInAnyOrder(
                        "PERSONAL", "PROJEKTMITARBEITER", "EXTERNE_PERSON")));
    }

    private String login(String username) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String accessTokenFor(String username) throws Exception {
        return JsonPath.read(login(username), "$.accessToken");
    }

    private AdminUser account(String username, RoleCode roleCode) {
        AdminUser user = TestEntities.user(username);
        user.setPasswordHash(passwordService.hash(PASSWORD));
        user = adminUsers.save(user);
        addRole(user, roleCode);
        return user;
    }

    private void addRole(AdminUser user, RoleCode roleCode) {
        Role role = roles.findByName(roleCode.name()).orElseThrow();
        userRoles.save(new UserRole(user, role, null));
        entityManager.flush();
    }
}
