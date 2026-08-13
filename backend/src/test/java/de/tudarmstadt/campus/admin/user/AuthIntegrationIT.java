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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance test for phase 3: login returns tokens, a protected endpoint answers 200 with and 401
 * without a token, 401 after logout, and a refresh token is refused as an access token.
 */
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationIT extends AbstractIntegrationTest {

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

    @Test
    void loginReturnsATokenPairAndTheAccount() throws Exception {
        AdminUser user = account("login_ok", RoleCode.PROJEKTLEITER);

        mockMvc.perform(login(user.getUsername(), PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.username").value("login_ok"))
                .andExpect(jsonPath("$.user.roles[0]").value("PROJEKTLEITER"))
                .andExpect(jsonPath("$.user.permissions").isNotEmpty())
                // The hash must never leave the service layer.
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    /** Same answer for both, so the endpoint cannot be used to enumerate usernames. */
    @Test
    void wrongPasswordAndUnknownAccountAreIndistinguishable() throws Exception {
        account("login_wrong", RoleCode.PERSONAL);

        mockMvc.perform(login("login_wrong", "not-the-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(login("nobody_at_all", "not-the-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void aDisabledAccountCannotLogIn() throws Exception {
        AdminUser user = account("login_disabled", RoleCode.PERSONAL);
        user.setActive(false);
        adminUsers.save(user);
        entityManager.flush();

        mockMvc.perform(login("login_disabled", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void aProtectedEndpointNeedsAToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void aProtectedEndpointAcceptsAValidToken() throws Exception {
        account("me_reader", RoleCode.PROJEKTMITARBEITER);
        String accessToken = accessTokenFor("me_reader");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("me_reader"))
                .andExpect(jsonPath("$.roles[0]").value("PROJEKTMITARBEITER"))
                .andExpect(jsonPath("$.permissions").isNotEmpty());
    }

    /** Spec section 4.3, step 3: the two token types are not interchangeable. */
    @Test
    void aRefreshTokenIsRefusedAsAnAccessToken() throws Exception {
        account("type_confusion", RoleCode.PERSONAL);
        String refreshToken = JsonPath.read(
                mockMvc.perform(login("type_confusion", PASSWORD)).andReturn()
                        .getResponse().getContentAsString(), "$.refreshToken");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageInTheAuthorizationHeaderIsRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheAccessTokenImmediately() throws Exception {
        account("logout_user", RoleCode.PERSONAL);
        String accessToken = accessTokenFor("logout_user");
        String bearer = "Bearer " + accessToken;

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_STALE"));
    }

    /**
     * The line between the two functions: a plain logout ends this session and nothing else.
     */
    @Test
    void aPlainLogoutLeavesOtherSessionsAlone() throws Exception {
        account("logout_one_device", RoleCode.PERSONAL);
        String deviceA = accessTokenFor("logout_one_device");
        String deviceB = accessTokenFor("logout_one_device");

        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceB))
                .andExpect(status().isOk());
    }

    /**
     * Logging out everywhere raises both version counters, so tokens the server has never seen die too.
     * That is why this needs no request body at all.
     */
    @Test
    void loggingOutEverywhereEndsEverySession() throws Exception {
        account("logout_all_devices", RoleCode.PERSONAL);
        String deviceA = accessTokenFor("logout_all_devices");

        String sessionB = mockMvc.perform(login("logout_all_devices", PASSWORD)).andReturn()
                .getResponse().getContentAsString();
        String accessB = JsonPath.read(sessionB, "$.accessToken");
        String refreshB = JsonPath.read(sessionB, "$.refreshToken");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessB))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_STALE"));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessB))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_STALE"));
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshB + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_STALE"));

        // The account itself stays usable; only the old sessions are gone.
        mockMvc.perform(login("logout_all_devices", PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void loggingOutEverywhereRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/logout-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void refreshRotatesThePairAndRevokesTheOldRefreshToken() throws Exception {
        account("refresh_user", RoleCode.PERSONAL);
        String firstRefresh = JsonPath.read(
                mockMvc.perform(login("refresh_user", PASSWORD)).andReturn()
                        .getResponse().getContentAsString(), "$.refreshToken");

        String rotated = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn().getResponse().getContentAsString();

        String newAccess = JsonPath.read(rotated, "$.accessToken");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess))
                .andExpect(status().isOk());

        // Reusing the consumed refresh token must fail.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    /** INV-6: after a password change no previously issued token works any more. */
    @Test
    void changingThePasswordInvalidatesEveryOutstandingToken() throws Exception {
        account("password_changer", RoleCode.PERSONAL);
        String response = mockMvc.perform(login("password_changer", PASSWORD))
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(response, "$.accessToken");
        String refreshToken = JsonPath.read(response, "$.refreshToken");

        mockMvc.perform(post("/api/auth/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD
                                + "\",\"newPassword\":\"an-even-longer-new-password\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_STALE"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_STALE"));

        mockMvc.perform(login("password_changer", "an-even-longer-new-password"))
                .andExpect(status().isOk());
    }

    @Test
    void aTooShortPasswordIsRejectedWithFieldErrors() throws Exception {
        account("short_password", RoleCode.PERSONAL);
        String accessToken = accessTokenFor("short_password");

        mockMvc.perform(post("/api/auth/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"kurz\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }

    @Test
    void theOwnProfileCanBeUpdated() throws Exception {
        account("profile_editor", RoleCode.PERSONAL);
        String accessToken = accessTokenFor("profile_editor");

        mockMvc.perform(put("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Mehmet","lastName":"Bulut",
                                 "email":"mehmet.bulut@tu-darmstadt.de","organisation":"AG Serious Games"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Mehmet"))
                .andExpect(jsonPath("$.organisation").value("AG Serious Games"));
    }

    /**
     * D-21 end to end: while the initial password is in place, the account may do exactly one thing.
     * <p>
     * The second half of this test is the one that matters. The restriction was implemented on the token
     * but not on the account representation, so login and {@code /api/auth/me} still advertised the full
     * permission set of the role. An interface that builds its menu from that list — which is precisely
     * what the specification asks for in section 6 — would have offered pages whose every call ends in
     * 403.
     */
    @Test
    void anAccountOnItsInitialPasswordIsRestrictedInTokenAndInResponse() throws Exception {
        AdminUser user = account("must_change", RoleCode.PROJEKTMITARBEITER);
        user.setMustChangePassword(true);
        adminUsers.save(user);
        entityManager.flush();

        String body = mockMvc.perform(login("must_change", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();

        List<String> advertised = JsonPath.read(body, "$.user.permissions");
        assertThat(advertised).containsExactly(PermissionCode.PROFILE_UPDATE_OWN.name());

        String accessToken = JsonPath.read(body, "$.accessToken");
        // The role would grant POI_READ_ALL; the restricted token does not carry it.
        mockMvc.perform(get("/api/pois").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasSize(1)));

        mockMvc.perform(post("/api/auth/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD
                                + "\",\"newPassword\":\"ein-langes-neues-passwort\"}"))
                .andExpect(status().isNoContent());

        // After the change the next login carries the full set of the role again.
        String after = mockMvc.perform(login("must_change", "ein-langes-neues-passwort"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString();
        List<String> full = JsonPath.read(after, "$.user.permissions");
        assertThat(full).containsExactlyInAnyOrderElementsOf(
                RoleCatalog.permissionsOf(RoleCode.PROJEKTMITARBEITER).stream().map(Enum::name).toList());
    }

    private MockHttpServletRequestBuilder login(String username, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private String accessTokenFor(String username) throws Exception {
        String body = mockMvc.perform(login(username, PASSWORD)).andReturn()
                .getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private AdminUser account(String username, RoleCode roleCode) {
        AdminUser user = TestEntities.user(username);
        user.setPasswordHash(passwordService.hash(PASSWORD));
        user = adminUsers.save(user);

        Role role = roles.findByName(roleCode.name()).orElseThrow();
        userRoles.save(new UserRole(user, role, null));
        entityManager.flush();
        return user;
    }
}
