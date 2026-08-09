package de.tudarmstadt.campus.admin.audit;

import com.jayway.jsonpath.JsonPath;
import de.tudarmstadt.campus.admin.audit.domain.AuditLog;
import de.tudarmstadt.campus.admin.audit.repository.AuditLogRepository;
import de.tudarmstadt.campus.admin.audit.service.AuditService;
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

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance test for phase 5: a role assignment produces a {@code ROLE_ASSIGNED} entry with the correct
 * before and after state, a refused access produces {@code ACCESS_DENIED}, and no entry ever contains a
 * password hash.
 * <p>
 * Deliberately <b>not</b> {@code @Transactional}: audit entries are written with {@code REQUIRES_NEW},
 * and a test transaction that rolls everything back would hide exactly the behaviour under test. The
 * accounts are therefore given unique names and left behind.
 */
@AutoConfigureMockMvc
class AuditTrailIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogs;

    @Autowired
    private AuditService auditService;

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

    /** The acceptance criterion: correct before and after on a role assignment. */
    @Test
    void aRoleAssignmentIsLoggedWithBeforeAndAfter() throws Exception {
        AdminUser admin = account("audit_admin_assign", RoleCode.ADMIN);
        AdminUser target = account("audit_target_assign", RoleCode.PERSONAL);
        String token = accessTokenFor(admin.getUsername());

        mockMvc.perform(post("/api/users/" + target.getId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"PROJEKTMITARBEITER\"}"))
                .andExpect(status().isOk());

        AuditLog entry = latestEntry("ROLE_ASSIGNED", String.valueOf(target.getId()));
        assertThat(entry.isSuccess()).isTrue();
        assertThat(entry.getActorUsername()).isEqualTo("audit_admin_assign");
        assertThat(entry.getActor()).isNotNull();
        assertThat(entry.getResourceType()).isEqualTo("USER");
        assertThat(entry.getBeforeState()).contains("PERSONAL").doesNotContain("PROJEKTMITARBEITER");
        assertThat(entry.getAfterState()).contains("PERSONAL").contains("PROJEKTMITARBEITER");
    }

    @Test
    void aRoleRevocationIsLoggedWithBeforeAndAfter() throws Exception {
        AdminUser admin = account("audit_admin_revoke", RoleCode.ADMIN);
        AdminUser target = account("audit_target_revoke", RoleCode.PERSONAL);
        addRole(target, RoleCode.PROJEKTMITARBEITER);
        String token = accessTokenFor(admin.getUsername());

        mockMvc.perform(delete("/api/users/" + target.getId() + "/roles/PROJEKTMITARBEITER")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        AuditLog entry = latestEntry("ROLE_REVOKED", String.valueOf(target.getId()));
        assertThat(entry.getBeforeState()).contains("PROJEKTMITARBEITER");
        assertThat(entry.getAfterState()).doesNotContain("PROJEKTMITARBEITER");
    }

    /**
     * Scenario S-06. The caller holds ROLE_ASSIGN, so {@code @PreAuthorize} lets them through and the
     * refusal comes from the service — the entry has to be written even though the transaction rolled
     * back (docs/DECISIONS.md D-10).
     */
    @Test
    void aRefusedRoleAssignmentIsLoggedAsAFailure() throws Exception {
        AdminUser leitung = account("audit_leitung_denied", RoleCode.PROJEKTLEITER);
        AdminUser target = account("audit_target_denied", RoleCode.PERSONAL);
        String token = accessTokenFor(leitung.getUsername());

        mockMvc.perform(post("/api/users/" + target.getId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        AuditLog entry = latestEntry("ROLE_ASSIGNED", String.valueOf(target.getId()));
        assertThat(entry.isSuccess()).isFalse();
        assertThat(entry.getErrorCode()).isEqualTo("ROLE_NOT_GRANTABLE");
        assertThat(entry.getActorUsername()).isEqualTo("audit_leitung_denied");

        // The rejected change really did not happen.
        assertThat(roles.findRoleNamesByUserId(target.getId())).containsExactly("PERSONAL");
    }

    /** A missing authority is refused by {@code @PreAuthorize} and lands as ACCESS_DENIED. */
    @Test
    void aMissingAuthorityIsLoggedAsAccessDenied() throws Exception {
        AdminUser worker = account("audit_worker_denied", RoleCode.PROJEKTMITARBEITER);
        String token = accessTokenFor(worker.getUsername());

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        AuditLog entry = latestEntry("ACCESS_DENIED", "/api/users");
        assertThat(entry.isSuccess()).isFalse();
        assertThat(entry.getErrorCode()).isEqualTo("ACCESS_DENIED");
        assertThat(entry.getActorUsername()).isEqualTo("audit_worker_denied");
    }

    @Test
    void loginEventsAreLoggedForSuccessAndFailure() throws Exception {
        account("audit_login", RoleCode.PERSONAL);

        mockMvc.perform(login("audit_login", PASSWORD)).andExpect(status().isOk());
        assertThat(latestAuthEntry("LOGIN_SUCCESS", "audit_login").isSuccess()).isTrue();

        mockMvc.perform(login("audit_login", "falsch")).andExpect(status().isUnauthorized());
        AuditLog failed = latestAuthEntry("LOGIN_FAILED", "audit_login");
        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    /** An unknown username still produces an entry — otherwise a probing attempt would be invisible. */
    @Test
    void aLoginAttemptForAnUnknownAccountIsLogged() throws Exception {
        mockMvc.perform(login("audit_unknown_person", "irgendwas"))
                .andExpect(status().isUnauthorized());

        AuditLog entry = latestAuthEntry("LOGIN_FAILED", "audit_unknown_person");
        assertThat(entry.getActor()).as("no account, so no reference").isNull();
        assertThat(entry.getActorUsername()).isEqualTo("audit_unknown_person");
    }

    /** The acceptance criterion: no password hash anywhere in the log. */
    @Test
    void noEntryEverContainsASecret() throws Exception {
        AdminUser admin = account("audit_admin_secrets", RoleCode.ADMIN);
        String token = accessTokenFor(admin.getUsername());

        String created = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"audit.secretfree","email":"audit.secretfree@tu-darmstadt.de",
                                 "firstName":"Ohne","lastName":"Geheimnis","roles":["PERSONAL"]}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String temporaryPassword = JsonPath.read(created, "$.temporaryPassword");
        int newUserId = JsonPath.read(created, "$.user.id");

        mockMvc.perform(post("/api/users/" + newUserId + "/password-reset")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        List<AuditLog> entries = auditLogs.findAll();
        assertThat(entries).isNotEmpty();
        for (AuditLog entry : entries) {
            String states = String.valueOf(entry.getBeforeState()) + entry.getAfterState();
            assertThat(states)
                    .as("entry %s must not carry a secret", entry.getAction())
                    .doesNotContain(temporaryPassword)
                    .doesNotContain(PASSWORD)
                    .doesNotContain("$2a$");
        }
    }

    /** Masking is recursive: a blocked field is hidden wherever it sits in the structure. */
    @Test
    void maskingReachesNestedFields() {
        Map<String, Object> after = Map.of(
                "credentials", Map.of("password_hash", "$2a$12$secret", "username", "sichtbar"),
                "sessions", List.of(Map.of("refreshToken", "eyJhbGciOi", "device", "Laptop")));

        auditService.record("USER_UPDATED", "USER", "masking-test", true, null, null, after);

        AuditLog entry = latestEntry("USER_UPDATED", "masking-test");
        assertThat(entry.getAfterState())
                .contains("sichtbar")
                .contains("Laptop")
                .contains("***")
                .doesNotContain("$2a$12$secret")
                .doesNotContain("eyJhbGciOi");
    }

    private AuditLog latestEntry(String action, String resourceId) {
        entityManager.clear();
        List<AuditLog> entries = auditLogs.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
                "ACCESS_DENIED".equals(action) ? "AUTH" : "USER", resourceId);
        return entries.stream()
                .filter(entry -> entry.getAction().equals(action))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no audit entry " + action + " for " + resourceId));
    }

    private AuditLog latestAuthEntry(String action, String username) {
        entityManager.clear();
        return auditLogs.findAll().stream()
                .filter(entry -> action.equals(entry.getAction()))
                .filter(entry -> username.equals(entry.getActorUsername()))
                .max(Comparator.comparing(AuditLog::getCreatedAt))
                .orElseThrow(() -> new AssertionError(
                        "no audit entry " + action + " for '" + username + "'"));
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
        addRole(user, roleCode);
        return user;
    }

    private void addRole(AdminUser user, RoleCode roleCode) {
        Role role = roles.findByName(roleCode.name()).orElseThrow();
        userRoles.save(new UserRole(user, role, null));
    }
}
