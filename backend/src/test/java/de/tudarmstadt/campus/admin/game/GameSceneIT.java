package de.tudarmstadt.campus.admin.game;

import com.jayway.jsonpath.JsonPath;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.support.AbstractContentIT;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenario S-23: the same scene, seen by different roles.
 * <p>
 * This is the acceptance evidence for the changed specification. Everything else in the project shows
 * the permission model in a management interface; here it shapes the product itself — a student walks
 * through the published campus, a project lead walks through the same campus and sees what is still in
 * preparation (docs/DECISIONS.md D-42).
 */
class GameSceneIT extends AbstractContentIT {

    /** Prepares one published and one unpublished object of each kind. */
    private long[] prepareCampus(AdminUser lead, AdminUser worker) throws Exception {
        long visibleBuilding = building(lead, "G1|01", "Sichtbares Gebäude", true);
        long hiddenBuilding = building(lead, "G2|02", "Verstecktes Gebäude", false);

        long publishedPoi = poi(worker, "Freigegebener Würfel", visibleBuilding);
        mockMvc.perform(post("/api/pois/" + publishedPoi + "/submit").with(as(worker)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/pois/" + publishedPoi + "/publish").with(as(lead)))
                .andExpect(status().isOk());

        long draftPoi = poi(worker, "Würfel im Entwurf", visibleBuilding);
        flush();
        return new long[]{visibleBuilding, hiddenBuilding, publishedPoi, draftPoi};
    }

    /** A registered player sees the published campus — and no trace of anything else. */
    @Test
    void aPlayerSeesOnlyPublishedContent() throws Exception {
        AdminUser lead = account("scene_leitung", RoleCode.PROJEKTLEITER);
        AdminUser worker = account("scene_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        AdminUser player = account("scene_studi", RoleCode.EXTERNE_PERSON);
        prepareCampus(lead, worker);

        String body = mockMvc.perform(get("/api/game/scene").with(as(player)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.pois[*].nameDe"))
                .contains("Freigegebener Würfel")
                .doesNotContain("Würfel im Entwurf");
        assertThat(JsonPath.<List<String>>read(body, "$.buildings[*].code"))
                .contains("G1|01")
                .doesNotContain("G2|02");
        // Without the wider permission the status carries no information and is left out entirely.
        assertThat(JsonPath.<List<String>>read(body, "$.pois[*].status")).isEmpty();
        assertThat(JsonPath.<List<Boolean>>read(body, "$.buildings[*].published")).isEmpty();
    }

    /** The same call as a project lead: more content, and each object says which state it is in. */
    @Test
    void aProjectLeadSeesDraftsAsWell() throws Exception {
        AdminUser lead = account("scene2_leitung", RoleCode.PROJEKTLEITER);
        AdminUser worker = account("scene2_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        prepareCampus(lead, worker);

        String body = mockMvc.perform(get("/api/game/scene").with(as(lead)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.pois[*].nameDe"))
                .contains("Freigegebener Würfel", "Würfel im Entwurf");
        assertThat(JsonPath.<List<String>>read(body, "$.buildings[*].code"))
                .contains("G1|01", "G2|02");
        assertThat(JsonPath.<List<String>>read(body, "$.pois[*].status"))
                .contains("PUBLISHED", "DRAFT");
    }

    /**
     * The point of the whole design in one assertion: one URL, two accounts, two answers — and the
     * larger answer belongs to the account with the wider permissions.
     */
    @Test
    void theSameUrlAnswersDifferentlyPerRole() throws Exception {
        AdminUser lead = account("scene3_leitung", RoleCode.PROJEKTLEITER);
        AdminUser worker = account("scene3_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        AdminUser player = account("scene3_studi", RoleCode.EXTERNE_PERSON);
        prepareCampus(lead, worker);

        int seenByPlayer = poiCount(mockMvc.perform(get("/api/game/scene").with(as(player)))
                .andReturn().getResponse().getContentAsString());
        int seenByLead = poiCount(mockMvc.perform(get("/api/game/scene").with(as(lead)))
                .andReturn().getResponse().getContentAsString());

        assertThat(seenByLead).isGreaterThan(seenByPlayer);
    }

    /** Least privilege stays least privilege: operations has no content permissions and cannot play. */
    @Test
    void maintenanceDevCannotEnterTheScene() throws Exception {
        AdminUser devops = account("scene_devops", RoleCode.MAINTENANCE_DEV);

        mockMvc.perform(get("/api/game/scene").with(as(devops)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theSceneIsClosedWithoutASession() throws Exception {
        mockMvc.perform(get("/api/game/scene")).andExpect(status().isUnauthorized());
    }

    /** Consultation offers travel with their slots, so the client needs no second request. */
    @Test
    void consultationsCarryTheirSlots() throws Exception {
        AdminUser staff = account("scene_personal", RoleCode.PERSONAL);
        AdminUser lead = account("scene4_leitung", RoleCode.PROJEKTLEITER);
        AdminUser player = account("scene4_studi", RoleCode.EXTERNE_PERSON);

        String created = mockMvc.perform(post("/api/consultations").with(as(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titleDe":"Studienberatung","organisation":"Fachgebiet Test",
                                 "room":"B302","contactEmail":"test@tu-darmstadt.de","published":false}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mockMvc.perform(post("/api/consultations/" + id + "/events").with(as(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":2,\"startTime\":\"10:00:00\",\"endTime\":\"12:00:00\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/consultations/" + id).with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titleDe":"Studienberatung","organisation":"Fachgebiet Test",
                                 "room":"B302","contactEmail":"test@tu-darmstadt.de","published":true}"""))
                .andExpect(status().isOk());
        flush();

        mockMvc.perform(get("/api/game/scene").with(as(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultations[0].titleDe").value("Studienberatung"))
                .andExpect(jsonPath("$.consultations[0].slots[0].startTime").value("10:00:00"))
                // The slot inherits the room of its offer when it does not override it.
                .andExpect(jsonPath("$.consultations[0].slots[0].room").value("B302"));
    }

    private static int poiCount(String body) {
        return JsonPath.<List<Object>>read(body, "$.pois[*]").size();
    }

    private long poi(AdminUser author, String nameDe, long buildingId) throws Exception {
        String created = mockMvc.perform(post("/api/pois").with(as(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameDe":"%s","category":"OTHER","buildingId":%d,
                                 "positionX":1.5,"positionY":0.0,"positionZ":2.5}"""
                                .formatted(nameDe, buildingId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(created, "$.id")).longValue();
    }

    private long building(AdminUser lead, String code, String nameDe, boolean published)
            throws Exception {
        String created = mockMvc.perform(post("/api/buildings").with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nameDe":"%s","modelRef":"models/test.glb",
                                 "positionX":10.0,"positionY":0.0,"positionZ":20.0,"rotationY":90.0,
                                 "published":%s}""".formatted(code, nameDe, published)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.positionX").value(10.0))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(created, "$.id")).longValue();
    }
}
