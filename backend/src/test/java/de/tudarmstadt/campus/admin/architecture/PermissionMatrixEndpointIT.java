package de.tudarmstadt.campus.admin.architecture;

import de.tudarmstadt.campus.admin.rbac.PermissionCode;
import de.tudarmstadt.campus.admin.rbac.RoleCatalog;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.domain.UserRole;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.rbac.repository.UserRoleRepository;
import de.tudarmstadt.campus.admin.security.CampusUserDetails;
import de.tudarmstadt.campus.admin.support.AbstractIntegrationTest;
import de.tudarmstadt.campus.admin.support.TestEntities;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * The acceptance criterion of phase 4: for every cell of the permission matrix there is a check with and
 * without the required permission.
 * <p>
 * The expectation is derived from {@link RoleCatalog}, not written out by hand — a change to the matrix
 * automatically changes what this test demands. A role that lacks the permission must receive exactly
 * 403; a role that holds it must receive anything but 403 (the concrete status then depends on the data,
 * for example 409 when an invariant kicks in, which is authorisation working correctly).
 */
@AutoConfigureMockMvc
@Transactional
class PermissionMatrixEndpointIT extends AbstractIntegrationTest {

    /** One protected endpoint with the permission the specification demands for it (section 5.2, 5.3). */
    private record Endpoint(HttpMethod method, String pathTemplate, PermissionCode required, String body) {

        Endpoint(HttpMethod method, String pathTemplate, PermissionCode required) {
            this(method, pathTemplate, required, null);
        }

        @Override
        public String toString() {
            return method + " " + pathTemplate;
        }
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint(HttpMethod.GET, "/api/users", PermissionCode.USER_READ),
            new Endpoint(HttpMethod.GET, "/api/users/{id}", PermissionCode.USER_READ),
            new Endpoint(HttpMethod.GET, "/api/users/{id}/roles", PermissionCode.USER_READ),
            new Endpoint(HttpMethod.POST, "/api/users", PermissionCode.USER_CREATE, """
                    {"username":"matrix.created","email":"matrix.created@tu-darmstadt.de",
                     "firstName":"Neu","lastName":"Konto","roles":["PERSONAL"]}"""),
            new Endpoint(HttpMethod.PUT, "/api/users/{id}", PermissionCode.USER_UPDATE, """
                    {"firstName":"Neu","lastName":"Name","email":"matrix.updated@tu-darmstadt.de"}"""),
            new Endpoint(HttpMethod.PATCH, "/api/users/{id}/status", PermissionCode.USER_ACTIVATE,
                    "{\"active\":false}"),
            new Endpoint(HttpMethod.DELETE, "/api/users/{id}", PermissionCode.USER_DELETE),
            new Endpoint(HttpMethod.POST, "/api/users/{id}/password-reset",
                    PermissionCode.USER_PASSWORD_RESET),
            new Endpoint(HttpMethod.POST, "/api/users/{id}/roles", PermissionCode.ROLE_ASSIGN,
                    "{\"roleName\":\"PROJEKTMITARBEITER\"}"),
            new Endpoint(HttpMethod.DELETE, "/api/users/{id}/roles/PERSONAL", PermissionCode.ROLE_ASSIGN),
            new Endpoint(HttpMethod.GET, "/api/users/me/grantable-roles", PermissionCode.ROLE_ASSIGN),
            new Endpoint(HttpMethod.GET, "/api/roles", PermissionCode.ROLE_READ),
            new Endpoint(HttpMethod.GET, "/api/roles/matrix", PermissionCode.ROLE_READ),
            new Endpoint(HttpMethod.GET, "/api/roles/ADMIN", PermissionCode.ROLE_READ),
            new Endpoint(HttpMethod.GET, "/api/permissions", PermissionCode.ROLE_READ));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private EntityManager entityManager;

    static Stream<Arguments> cells() {
        List<Arguments> cells = new ArrayList<>();
        for (RoleCode role : RoleCode.values()) {
            if (role == RoleCode.EXTERNE_PERSON) {
                // Never held by an account; realised through permitAll on the public endpoints.
                continue;
            }
            for (Endpoint endpoint : ENDPOINTS) {
                cells.add(Arguments.of(role, endpoint));
            }
        }
        return cells.stream();
    }

    @ParameterizedTest(name = "{1} als {0}")
    @MethodSource("cells")
    void matrixCellIsEnforced(RoleCode role, Endpoint endpoint) throws Exception {
        AdminUser actor = account("matrix_actor_" + role.name().toLowerCase(), role);
        AdminUser target = account("matrix_target_" + role.name().toLowerCase(), RoleCode.PERSONAL);

        boolean permitted = RoleCatalog.hasPermission(role, endpoint.required());
        int status = perform(endpoint, actor, target.getId());

        if (permitted) {
            assertThat(status)
                    .as("%s holds %s and must not be refused", role, endpoint.required())
                    .isNotEqualTo(403);
            // A 500 would also satisfy "not 403" — that gap once hid a broken query behind a green
            // matrix, so the permitted case must reach the endpoint intact.
            assertThat(status)
                    .as("%s on %s must not fail on the server", role, endpoint)
                    .isLessThan(500);
        } else {
            assertThat(status)
                    .as("%s lacks %s and must be refused", role, endpoint.required())
                    .isEqualTo(403);
        }
    }

    private int perform(Endpoint endpoint, AdminUser actor, long targetId) throws Exception {
        String path = endpoint.pathTemplate().replace("{id}", String.valueOf(targetId));
        MockHttpServletRequestBuilder request = switch (endpoint.method().name()) {
            case "GET" -> MockMvcRequestBuilders.get(path);
            case "POST" -> MockMvcRequestBuilders.post(path);
            case "PUT" -> MockMvcRequestBuilders.put(path);
            case "PATCH" -> MockMvcRequestBuilders.patch(path);
            case "DELETE" -> MockMvcRequestBuilders.delete(path);
            default -> throw new IllegalArgumentException("Unsupported: " + endpoint.method());
        };
        if (endpoint.body() != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        return mockMvc.perform(request.with(authentication(authenticationFor(actor))))
                .andReturn().getResponse().getStatus();
    }

    /**
     * Builds the principal the JWT filter would build, without issuing a token — the test is about
     * authorisation, not about authentication.
     */
    private UsernamePasswordAuthenticationToken authenticationFor(AdminUser actor) {
        CampusUserDetails principal = new CampusUserDetails(
                actor.getId(), actor.getUsername(), null, true,
                roles.findRoleNamesByUserId(actor.getId()),
                roles.findPermissionCodesByUserId(actor.getId()));
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }

    private AdminUser account(String username, RoleCode roleCode) {
        AdminUser user = adminUsers.save(TestEntities.user(username));
        Role role = roles.findByName(roleCode.name()).orElseThrow();
        userRoles.save(new UserRole(user, role, null));
        entityManager.flush();
        return user;
    }
}
