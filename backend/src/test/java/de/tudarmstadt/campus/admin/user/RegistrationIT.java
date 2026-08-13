package de.tudarmstadt.campus.admin.user;

import com.jayway.jsonpath.JsonPath;
import de.tudarmstadt.campus.admin.rbac.PermissionCode;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.support.AbstractContentIT;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenarios S-21 and S-22: a student registers, plays, and is later promoted by an administration.
 * <p>
 * This is the acceptance evidence for the changed role model (docs/DECISIONS.md D-40): registration
 * hands out {@code EXTERNE_PERSON}, the account reaches the game and nothing else, and promoting it
 * works — which it did not before, because the role was in nobody's grant set.
 */
class RegistrationIT extends AbstractContentIT {

    private static final String PASSWORD = "ein-langes-testpasswort";

    private static String registration(String username) {
        return """
                {"username":"%s","email":"%s@stud.tu-darmstadt.de","firstName":"Internationale",
                 "lastName":"Studentin","password":"%s"}"""
                .formatted(username, username, PASSWORD);
    }

    /** S-21: registering yields a usable session with exactly the playing role. */
    @Test
    void registeringYieldsAPlayerAccount() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("s21.studi")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.user.username").value("s21.studi"))
                .andExpect(jsonPath("$.user.roles", hasSize(1)))
                .andExpect(jsonPath("$.user.roles[0]").value("EXTERNE_PERSON"))
                // No temporary password and therefore nothing to change on first login.
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString();

        List<String> permissions = JsonPath.read(body, "$.user.permissions");
        assertThat(permissions).containsExactlyInAnyOrder(
                PermissionCode.POI_READ_PUBLISHED.name(),
                PermissionCode.BUILDING_READ_PUBLIC.name(),
                PermissionCode.CONSULTATION_READ_PUBLIC.name());
    }

    /** The role cannot be talked up: the payload has no roles field, and an added one is ignored. */
    @Test
    void nobodyRegistersThemselvesIntoAPrivilegedRole() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"s21.sneaky","email":"sneaky@example.org",
                                 "firstName":"Nice","lastName":"Try","password":"%s",
                                 "roles":["ADMIN"]}""".formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.user.roles")).containsExactly("EXTERNE_PERSON");
    }

    /** A player reaches the game and nothing else — the administration stays closed. */
    @Test
    void aPlayerReachesTheGameButNotTheAdministration() throws Exception {
        String token = tokenOf(register("s21.player"));

        mockMvc.perform(get("/api/game/scene").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/pois").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/audit").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /**
     * S-22: the promotion. Before D-40 this failed with TARGET_OUT_OF_SCOPE, because a self-registered
     * account held a role that was in nobody's grant set.
     */
    @Test
    void anAdministrationPromotesARegisteredPlayer() throws Exception {
        String registered = register("s22.player");
        long playerId = ((Number) JsonPath.read(registered, "$.user.id")).longValue();
        AdminUser lead = account("s22_leitung", RoleCode.PROJEKTLEITER);

        mockMvc.perform(post("/api/users/" + playerId + "/roles").with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"PROJEKTMITARBEITER\"}"))
                .andExpect(status().isOk())
                // The playing role stays: a contributor is a player as well.
                .andExpect(jsonPath("$.roles", hasSize(2)));

        // The old token is dead after a role change (INV-6); the new one carries the wider set.
        String promoted = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"s22.player\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(promoted, "$.user.permissions"))
                .contains(PermissionCode.POI_CREATE.name(), PermissionCode.POI_READ_ALL.name());
        mockMvc.perform(get("/api/pois")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOf(promoted)))
                .andExpect(status().isOk());
    }

    @Test
    void aTakenUsernameOrMailIsRefused() throws Exception {
        register("s21.taken");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("s21.taken")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_USED"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"s21.other","email":"s21.taken@stud.tu-darmstadt.de",
                                 "firstName":"A","lastName":"B","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void aShortPasswordIsRefusedWithAFieldError() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"s21.short","email":"short@example.org",
                                 "firstName":"A","lastName":"B","password":"kurz"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    /** Students remember the address they signed up with, not the username they picked once. */
    @Test
    void theMailAddressWorksAsALoginName() throws Exception {
        register("s21.bymail");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"s21.bymail@stud.tu-darmstadt.de","password":"%s"}"""
                                .formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("s21.bymail"));
    }

    private String register(String username) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static String tokenOf(String sessionBody) {
        return JsonPath.read(sessionBody, "$.accessToken");
    }
}
