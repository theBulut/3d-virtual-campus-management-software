package de.tudarmstadt.campus.admin;

import de.tudarmstadt.campus.admin.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance test for phase 0: the context starts against a fresh PostgreSQL, Flyway is wired, and the
 * API is closed by default with {@code /api/health} as the only reachable endpoint.
 */
@AutoConfigureMockMvc
class ApplicationContextIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAgainstPostgresAndRedis() {
        assertThat(jdbcTemplate.queryForObject("select 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void flywayHasCreatedItsSchemaHistory() {
        Integer tables = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name = 'flyway_schema_history'
                """, Integer.class);
        assertThat(tables).isEqualTo(1);
    }

    @Test
    void hibernateRunsInValidateMode() {
        // No entities and no migrations exist yet, so validate must simply pass. The point of the
        // assertion is that ddl-auto never creates tables behind Flyway's back (NFA-04).
        Integer businessTables = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name <> 'flyway_schema_history'
                """, Integer.class);
        assertThat(businessTables).isZero();
    }

    @Test
    void healthIsReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void everythingElseRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/system/info"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocumentIsReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }
}
