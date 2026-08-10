package de.tudarmstadt.campus.admin.support;

import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.rbac.domain.Role;
import de.tudarmstadt.campus.admin.rbac.domain.UserRole;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.rbac.repository.UserRoleRepository;
import de.tudarmstadt.campus.admin.security.CampusUserDetails;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Base class for the content tests of phase 6: an account with a role, and a request signed as that
 * account.
 * <p>
 * The principal is built the way the JWT filter would build it, but no token is issued — these tests are
 * about authorisation and the workflow, not about authentication, which {@code AuthIntegrationIT} covers.
 * <p>
 * {@code @Transactional} rolls every test back, which keeps the classes independent but means the audit
 * writer — it commits in its own transaction (REQUIRES_NEW) — cannot see the accounts created here and
 * logs a failed insert. That is a property of the harness, not of the application: auditing is covered by
 * {@code AuditTrailIT}, which deliberately runs without a test transaction for exactly this reason.
 */
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractContentIT extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private EntityManager entityManager;

    protected AdminUser account(String username, RoleCode roleCode) {
        AdminUser user = adminUsers.save(TestEntities.user(username));
        Role role = roles.findByName(roleCode.name()).orElseThrow();
        userRoles.save(new UserRole(user, role, null));
        entityManager.flush();
        return user;
    }

    /** Signs the request as the given account, with exactly the authorities its roles carry. */
    protected RequestPostProcessor as(AdminUser user) {
        CampusUserDetails principal = new CampusUserDetails(
                user.getId(), user.getUsername(), null, true,
                roles.findRoleNamesByUserId(user.getId()),
                roles.findPermissionCodesByUserId(user.getId()));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    }

    /** Makes pending changes visible to a query that runs in its own read-only transaction. */
    protected void flush() {
        entityManager.flush();
    }
}
