package de.tudarmstadt.campus.admin;

import de.tudarmstadt.campus.admin.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance test for phase 1: Flyway applies V1 to V3 against a fresh PostgreSQL and Hibernate accepts
 * the result with {@code ddl-auto=validate}.
 * <p>
 * The context starting at all is the validation proof — Hibernate compares every mapped entity against
 * the migrated schema during startup and aborts on any mismatch. The assertions below pin down the parts
 * of the schema the specification names explicitly.
 */
class SchemaValidationIT extends AbstractIntegrationTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "admin_user", "audit_log", "building", "consultation", "consultation_event",
            "game_state", "media_asset", "permission", "poi", "role", "role_grant", "role_permission",
            "user_role");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void everyMigrationHasBeenAppliedSuccessfully() {
        List<String> versions = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success order by installed_rank",
                String.class);

        // V1 to V3 build the schema, V4 seeds the RBAC catalogue, V5 opens the model for players.
        assertThat(versions).containsExactly("1", "2", "3", "4", "5");

        Integer failed = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where not success", Integer.class);
        assertThat(failed).isZero();
    }

    @Test
    void schemaHoldsExactlyTheTablesOfTheSpecification() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_name <> 'flyway_schema_history'
                order by table_name
                """, String.class);

        assertThat(tables).containsExactlyElementsOf(EXPECTED_TABLES);
    }

    @Test
    void adminUserCarriesBothTokenCounters() {
        assertThat(columnsOf("admin_user"))
                .contains("token_version", "refresh_version", "must_change_password");
    }

    @Test
    void poiStatusIsGuardedByACheckConstraint() {
        List<String> constraints = jdbcTemplate.queryForList("""
                select con.conname from pg_constraint con
                join pg_class rel on rel.oid = con.conrelid
                where rel.relname = 'poi' and con.contype = 'c'
                """, String.class);

        assertThat(constraints).contains("poi_status_known", "poi_category_known");
    }

    @Test
    void poiIsPublishedIsAGeneratedColumn() {
        String generated = jdbcTemplate.queryForObject("""
                select is_generated from information_schema.columns
                where table_name = 'poi' and column_name = 'is_published'
                """, String.class);

        assertThat(generated).isEqualTo("ALWAYS");
    }

    @Test
    void consultationCarriesBothDescriptionLanguages() {
        // NFA-09: user facing content exists in German and English.
        assertThat(columnsOf("consultation")).contains("description_de", "description_en");
    }

    @Test
    void auditStatesAreStoredAsJsonb() {
        List<String> types = jdbcTemplate.queryForList("""
                select data_type from information_schema.columns
                where table_name = 'audit_log' and column_name in ('before_state', 'after_state')
                """, String.class);

        assertThat(types).containsOnly("jsonb");
    }

    private List<String> columnsOf(String table) {
        return jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = ?",
                String.class, table);
    }
}
