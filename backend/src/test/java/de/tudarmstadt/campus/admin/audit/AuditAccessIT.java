package de.tudarmstadt.campus.admin.audit;

import de.tudarmstadt.campus.admin.audit.domain.AuditLog;
import de.tudarmstadt.campus.admin.audit.repository.AuditLogRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two views on the audit log (spec section 5.5): AUDIT_READ sees everything, AUDIT_READ_CONTENT only
 * entries about content resources.
 */
@AutoConfigureMockMvc
@Transactional
class AuditAccessIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogs;

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private EntityManager entityManager;

    private long userEntryId;

    @BeforeEach
    void seedEntries() {
        userEntryId = auditLogs.save(new AuditLog("USER_CREATED", "USER", "4711")).getId();
        auditLogs.save(new AuditLog("POI_CREATED", "POI", "1"));
        auditLogs.save(new AuditLog("BUILDING_UPDATED", "BUILDING", "2"));
        auditLogs.save(new AuditLog("LOGIN_FAILED", "AUTH", null));
        entityManager.flush();
    }

    @Test
    void anAdminSeesEveryResourceType() throws Exception {
        mockMvc.perform(get("/api/audit").param("size", "100")
                        .with(authentication(actorWith(RoleCode.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.resourceType == 'USER')]").exists())
                .andExpect(jsonPath("$.content[?(@.resourceType == 'AUTH')]").exists())
                .andExpect(jsonPath("$.content[?(@.resourceType == 'POI')]").exists());
    }

    /** MAINTENANCE_DEV holds AUDIT_READ as well, which is the point of the least privilege example. */
    @Test
    void maintenanceDevAlsoSeesEverything() throws Exception {
        mockMvc.perform(get("/api/audit").param("size", "100")
                        .with(authentication(actorWith(RoleCode.MAINTENANCE_DEV))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.resourceType == 'USER')]").exists());
    }

    /** PROJEKTLEITER holds only AUDIT_READ_CONTENT and must never see a USER or AUTH entry. */
    @Test
    void aContentOnlyReaderSeesContentResourcesOnly() throws Exception {
        mockMvc.perform(get("/api/audit").param("size", "100")
                        .with(authentication(actorWith(RoleCode.PROJEKTLEITER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].resourceType",
                        everyItem(is(in(java.util.List.of("POI", "BUILDING", "CONSULTATION", "MEDIA"))))))
                .andExpect(jsonPath("$.content[?(@.resourceType == 'USER')]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.resourceType == 'AUTH')]").doesNotExist());
    }

    /** Asking explicitly for a forbidden type must narrow the result, never widen it. */
    @Test
    void aContentOnlyReaderCannotRequestAForbiddenResourceType() throws Exception {
        mockMvc.perform(get("/api/audit").param("resourceType", "USER")
                        .with(authentication(actorWith(RoleCode.PROJEKTLEITER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** The detail view answers 404, not 403 — a restricted reader learns nothing about the entry. */
    @Test
    void aContentOnlyReaderCannotOpenAUserEntry() throws Exception {
        mockMvc.perform(get("/api/audit/" + userEntryId)
                        .with(authentication(actorWith(RoleCode.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("USER"));

        mockMvc.perform(get("/api/audit/" + userEntryId)
                        .with(authentication(actorWith(RoleCode.PROJEKTLEITER))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUDIT_ENTRY_NOT_FOUND"));
    }

    @Test
    void aRoleWithoutAnyAuditPermissionIsRefused() throws Exception {
        mockMvc.perform(get("/api/audit").with(authentication(actorWith(RoleCode.PROJEKTMITARBEITER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/audit").with(authentication(actorWith(RoleCode.PERSONAL))))
                .andExpect(status().isForbidden());
    }

    @Test
    void theActionFilterWorks() throws Exception {
        mockMvc.perform(get("/api/audit").param("action", "POI_CREATED").param("size", "100")
                        .with(authentication(actorWith(RoleCode.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action", everyItem(is("POI_CREATED"))));
    }

    private UsernamePasswordAuthenticationToken actorWith(RoleCode roleCode) {
        AdminUser user = adminUsers.save(TestEntities.user("audit_reader_" + roleCode.name().toLowerCase()
                + "_" + System.nanoTime()));
        Role role = roles.findByName(roleCode.name()).orElseThrow();
        userRoles.save(new UserRole(user, role, null));
        entityManager.flush();

        CampusUserDetails principal = new CampusUserDetails(user.getId(), user.getUsername(), null, true,
                roles.findRoleNamesByUserId(user.getId()),
                roles.findPermissionCodesByUserId(user.getId()));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities());
    }
}
