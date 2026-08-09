package de.tudarmstadt.campus.admin.audit.repository;

import de.tudarmstadt.campus.admin.audit.domain.AuditLog;
import de.tudarmstadt.campus.admin.support.AbstractIntegrationTest;
import de.tudarmstadt.campus.admin.support.TestEntities;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AuditLogRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private AuditLogRepository auditLogs;

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * The before and after states must reach the database as real jsonb, not as a JSON encoded string.
     * Querying with the {@code ->>} operator only works if PostgreSQL sees an object.
     */
    @Test
    void storesBeforeAndAfterStateAsQueryableJson() {
        AuditLog entry = new AuditLog("ROLE_ASSIGNED", "USER", "42");
        entry.setBeforeState("{\"roles\":[\"PERSONAL\"]}");
        entry.setAfterState("{\"roles\":[\"PERSONAL\",\"PROJEKTMITARBEITER\"]}");
        auditLogs.save(entry);
        entityManager.flush();
        entityManager.clear();

        String firstRoleAfter = jdbcTemplate.queryForObject(
                "select after_state -> 'roles' ->> 1 from audit_log where id = ?",
                String.class, entry.getId());
        assertThat(firstRoleAfter).isEqualTo("PROJEKTMITARBEITER");

        Integer rolesBefore = jdbcTemplate.queryForObject(
                "select jsonb_array_length(before_state -> 'roles') from audit_log where id = ?",
                Integer.class, entry.getId());
        assertThat(rolesBefore).isEqualTo(1);

        AuditLog reloaded = auditLogs.findById(entry.getId()).orElseThrow();
        assertThat(reloaded.getBeforeState()).contains("PERSONAL");
        assertThat(reloaded.getAfterState()).contains("PROJEKTMITARBEITER");
    }

    @Test
    void keepsEntriesWithoutStatesAndDefaultsToSuccess() {
        AuditLog entry = new AuditLog("LOGIN_SUCCESS", "AUTH", null);
        auditLogs.save(entry);
        entityManager.flush();
        entityManager.clear();

        AuditLog reloaded = auditLogs.findById(entry.getId()).orElseThrow();
        assertThat(reloaded.isSuccess()).isTrue();
        assertThat(reloaded.getBeforeState()).isNull();
        assertThat(reloaded.getAfterState()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    /**
     * A denied domain operation is recorded as a failed entry with its error code — the mechanism behind
     * scenario S-06, where {@code @PreAuthorize} passes but the service refuses.
     */
    @Test
    void recordsFailedOperationsWithAnErrorCode() {
        AdminUser actor = adminUsers.save(TestEntities.user("audit_actor"));
        AuditLog entry = new AuditLog("ROLE_ASSIGNED", "USER", "7");
        entry.setActor(actor);
        entry.setActorUsername(actor.getUsername());
        entry.setSuccess(false);
        entry.setErrorCode("ROLE_NOT_GRANTABLE");
        auditLogs.save(entry);
        entityManager.flush();
        entityManager.clear();

        AuditLog reloaded = auditLogs.findById(entry.getId()).orElseThrow();
        assertThat(reloaded.isSuccess()).isFalse();
        assertThat(reloaded.getErrorCode()).isEqualTo("ROLE_NOT_GRANTABLE");
        assertThat(reloaded.getActorUsername()).isEqualTo("audit_actor");
    }

    @Test
    void findsEntriesOfAResource() {
        auditLogs.save(new AuditLog("POI_CREATED", "POI", "100"));
        auditLogs.save(new AuditLog("POI_PUBLISHED", "POI", "100"));
        auditLogs.save(new AuditLog("POI_CREATED", "POI", "200"));
        entityManager.flush();

        assertThat(auditLogs.findByResourceTypeAndResourceIdOrderByCreatedAtDesc("POI", "100"))
                .hasSize(2);
    }

    /**
     * Holders of AUDIT_READ_CONTENT only may see content resources; the restriction is applied as a
     * filter rather than by trimming the result afterwards (spec section 5.5).
     */
    @Test
    void filtersByResourceTypeForContentOnlyReaders() {
        auditLogs.save(new AuditLog("USER_CREATED", "USER", "1"));
        auditLogs.save(new AuditLog("POI_CREATED", "POI", "2"));
        auditLogs.save(new AuditLog("BUILDING_CREATED", "BUILDING", "3"));
        entityManager.flush();

        // No exact totals here: audit entries are written with REQUIRES_NEW and therefore survive the
        // rollback of other tests. What must hold is the property, not a count.
        List<String> contentTypes = List.of("POI", "BUILDING", "CONSULTATION", "MEDIA");
        assertThat(auditLogs.search(null, null, contentTypes, null, null, PageRequest.of(0, 200)))
                .extracting(AuditLog::getResourceType)
                .isSubsetOf(contentTypes)
                .contains("POI", "BUILDING");

        assertThat(auditLogs.search(null, null, null, null, null, PageRequest.of(0, 200)))
                .extracting(AuditLog::getResourceType)
                .as("without the filter the USER entry is visible too")
                .contains("USER");
    }
}
