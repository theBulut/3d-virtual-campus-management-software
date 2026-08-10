package de.tudarmstadt.campus.admin.publicapi;

import com.jayway.jsonpath.JsonPath;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.support.AbstractContentIT;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenarios S-15 and S-16: the anonymous interface shows published content and nothing else, and the
 * administrative interface stays closed without a token (FA-17, FA-14).
 */
class PublicApiIT extends AbstractContentIT {

    /** Exactly the fields {@code PublicPoiResponse} declares — see the note on that record. */
    private static final List<String> PUBLIC_POI_FIELDS = List.of(
            "id", "nameDe", "nameEn", "descriptionDe", "descriptionEn", "category", "buildingCode",
            "positionX", "positionY", "positionZ");

    /** S-15: only the published POI comes back, and it comes back without a token. */
    @Test
    void anonymousCallersSeeOnlyPublishedPois() throws Exception {
        AdminUser worker = account("s15_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        AdminUser lead = account("s15_leitung", RoleCode.PROJEKTLEITER);

        long published = poi(worker, "Freigegebener Lernraum");
        long inReview = poi(worker, "Eingereichter Lernraum");
        long draft = poi(worker, "Entwurf Lernraum");
        long archived = poi(worker, "Archivierter Lernraum");

        submit(worker, published);
        publish(lead, published);
        submit(worker, inReview);
        submit(worker, archived);
        publish(lead, archived);
        mockMvc.perform(post("/api/pois/" + archived + "/archive").with(as(lead)))
                .andExpect(status().isOk());
        flush();

        String body = mockMvc.perform(get("/api/public/pois"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(body, "$[*].nameDe");
        assertThat(names).contains("Freigegebener Lernraum");
        assertThat(names).doesNotContain("Eingereichter Lernraum", "Entwurf Lernraum",
                "Archivierter Lernraum");

        // Not just "no draft leaked" — no unpublished id is in the payload at all.
        List<Integer> ids = JsonPath.read(body, "$[*].id");
        assertThat(ids).doesNotContain((int) inReview, (int) draft, (int) archived);
    }

    /**
     * The data minimisation rule of spec section 5.5, checked on the wire: the public payload carries
     * exactly the declared fields. Status, author, assignee, review note and timestamps stay internal.
     */
    @Test
    void thePublicPayloadCarriesNoInternalFields() throws Exception {
        AdminUser worker = account("s15b_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        AdminUser lead = account("s15b_leitung", RoleCode.PROJEKTLEITER);
        long buildingId = building(lead, "P3|99", "Testgebäude", true);

        // Every optional field is filled: with default-property-inclusion=non_null an empty field would
        // simply be missing, and the check for extra fields would then say nothing.
        String created = mockMvc.perform(post("/api/pois").with(as(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameDe":"Sichtbarer Lernraum","nameEn":"Visible Study Room",
                                 "descriptionDe":"Beschreibung","descriptionEn":"Description",
                                 "category":"LIBRARY","buildingId":%d,
                                 "positionX":1.0,"positionY":0.0,"positionZ":2.0}"""
                                .formatted(buildingId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        submit(worker, id);
        publish(lead, id);
        flush();

        String body = mockMvc.perform(get("/api/public/pois"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> entries = JsonPath.read(body, "$[*]");
        assertThat(entries).isNotEmpty();
        for (Map<String, Object> entry : entries) {
            assertThat(entry.keySet())
                    .as("fields of a public POI")
                    .containsExactlyInAnyOrderElementsOf(PUBLIC_POI_FIELDS);
        }
    }

    /** S-16: the administrative list needs a token — anonymous is 401, not 403 and not 200. */
    @Test
    void theAdministrativeListIsClosedToAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/pois")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/buildings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/consultations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/export/pois.csv")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCallersSeeOnlyPublishedBuildings() throws Exception {
        AdminUser lead = account("s15c_leitung", RoleCode.PROJEKTLEITER);
        building(lead, "P1|99", "Sichtbares Gebäude", true);
        building(lead, "P2|99", "Verstecktes Gebäude", false);
        flush();

        String body = mockMvc.perform(get("/api/public/buildings"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> codes = JsonPath.read(body, "$[*].code");
        assertThat(codes).contains("P1|99").doesNotContain("P2|99");
    }

    @Test
    void anonymousCallersSeeOnlyPublishedConsultationsIncludingTheirSlots() throws Exception {
        AdminUser staff = account("s15d_personal", RoleCode.PERSONAL);
        AdminUser lead = account("s15d_leitung", RoleCode.PROJEKTLEITER);

        String offer = """
                {"titleDe":"Offene Sprechstunde","organisation":"Fachgebiet Test",
                 "contactEmail":"test@tu-darmstadt.de","published":false}""";
        String created = mockMvc.perform(post("/api/consultations").with(as(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(offer))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mockMvc.perform(post("/api/consultations/" + id + "/events").with(as(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":2,\"startTime\":\"10:00:00\",\"endTime\":\"12:00:00\"}"))
                .andExpect(status().isCreated());
        flush();

        // Unpublished: invisible.
        String hidden = mockMvc.perform(get("/api/public/consultations"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(hidden, "$[*].titleDe"))
                .doesNotContain("Offene Sprechstunde");

        mockMvc.perform(put("/api/consultations/" + id).with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titleDe":"Offene Sprechstunde","organisation":"Fachgebiet Test",
                                 "contactEmail":"test@tu-darmstadt.de","published":true}"""))
                .andExpect(status().isOk());
        flush();

        String body = mockMvc.perform(get("/api/public/consultations"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$[*].titleDe")).contains("Offene Sprechstunde");
        assertThat(JsonPath.<List<String>>read(body, "$[*].slots[*].startTime")).contains("10:00:00");
        // The account behind the entry is not part of the public view.
        assertThat(body).doesNotContain("responsible", "s15d_personal");
    }

    private long poi(AdminUser author, String nameDe) throws Exception {
        String created = mockMvc.perform(post("/api/pois").with(as(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameDe":"%s","category":"LIBRARY",
                                 "positionX":1.0,"positionY":0.0,"positionZ":2.0}""".formatted(nameDe)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(created, "$.id")).longValue();
    }

    private void submit(AdminUser author, long id) throws Exception {
        mockMvc.perform(post("/api/pois/" + id + "/submit").with(as(author)))
                .andExpect(status().isOk());
    }

    private void publish(AdminUser lead, long id) throws Exception {
        mockMvc.perform(post("/api/pois/" + id + "/publish").with(as(lead)))
                .andExpect(status().isOk());
    }

    private long building(AdminUser lead, String code, String nameDe, boolean published)
            throws Exception {
        String created = mockMvc.perform(post("/api/buildings").with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nameDe":"%s","published":%s}"""
                                .formatted(code, nameDe, published)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(created, "$.id")).longValue();
    }
}
