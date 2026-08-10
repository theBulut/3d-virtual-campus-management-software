package de.tudarmstadt.campus.admin.export;

import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.support.AbstractContentIT;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CSV export (FA-22). The interesting part is not the happy path but the quoting: a separator inside
 * a field must not become a column boundary.
 */
class PoiExportIT extends AbstractContentIT {

    @Test
    void theExportIsQuotedAccordingToRfc4180() throws Exception {
        AdminUser lead = account("csv_leitung", RoleCode.PROJEKTLEITER);
        mockMvc.perform(post("/api/pois").with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameDe":"Hörsaal S1|03; Zugang \\"Nord\\"","category":"LECTURE_HALL",
                                 "positionX":1.0,"positionY":2.0,"positionZ":3.0}"""))
                .andExpect(status().isCreated());
        flush();

        String csv = mockMvc.perform(get("/api/export/pois.csv").with(as(lead)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("pois.csv")))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv.lines().findFirst()).hasValue(
                "\"id\";\"name_de\";\"name_en\";\"kategorie\";\"gebaeude\";\"status\";"
                        + "\"position_x\";\"position_y\";\"position_z\";\"veroeffentlicht_am\"");
        // The semicolon stays inside its field and the inner quotes are doubled.
        assertThat(csv).contains("\"Hörsaal S1|03; Zugang \"\"Nord\"\"\"");
        assertThat(csv).contains("\"DRAFT\"");
    }

    /** DATA_EXPORT belongs to the project lead and the administrator, not to the contributing role. */
    @Test
    void aContributorCannotExport() throws Exception {
        AdminUser worker = account("csv_mitarbeit", RoleCode.PROJEKTMITARBEITER);

        mockMvc.perform(get("/api/export/pois.csv").with(as(worker)))
                .andExpect(status().isForbidden());
    }
}
